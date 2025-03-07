package jp.juggler.subwaytooter.push

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.await
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import jp.juggler.crypt.*
import jp.juggler.subwaytooter.ActCallback
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.NotificationType.Companion.toNotificationType
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.api.push.ApiPushAppServer
import jp.juggler.subwaytooter.api.push.ApiPushMastodon
import jp.juggler.subwaytooter.api.push.ApiPushMisskey
import jp.juggler.subwaytooter.dialog.SuspendProgress
import jp.juggler.subwaytooter.notification.NotificationChannels
import jp.juggler.subwaytooter.notification.NotificationDeleteReceiver.Companion.intentNotificationDelete
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.pref.PrefDevice
import jp.juggler.subwaytooter.pref.prefDevice
import jp.juggler.subwaytooter.push.PushWorker.Companion.enqueuePushMessage
import jp.juggler.subwaytooter.push.PushWorker.Companion.enqueueRegisterEndpoint
import jp.juggler.subwaytooter.table.*
import jp.juggler.subwaytooter.util.loadIcon
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.coroutine.EmptyScope
import jp.juggler.util.data.*
import jp.juggler.util.data.Base128.decodeBase128
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.withCaption
import jp.juggler.util.os.applicationContextSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.unifiedpush.android.connector.UnifiedPush
import java.lang.ref.WeakReference
import java.security.Provider
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min

private val log = LogCategory("PushRepo")

private val defaultOkHttp by lazy {
    OkHttpClient.Builder().apply {
        connectTimeout(60, TimeUnit.SECONDS)
        writeTimeout(60, TimeUnit.SECONDS)
        readTimeout(60, TimeUnit.SECONDS)
    }.build()
}

val Context.pushRepo: PushRepo
    get() {
        val okHttp = defaultOkHttp
        val appDatabase = appDatabase
        return PushRepo(
            context = applicationContextSafe,
            apiAppServer = ApiPushAppServer(okHttp),
            apiMastodon = ApiPushMastodon(okHttp),
            apiMisskey = ApiPushMisskey(okHttp),
            daoSavedAccount = SavedAccount.Access(appDatabase, this),
            daoPushMessage = PushMessage.Access(appDatabase),
            daoStatus = AccountNotificationStatus.Access(appDatabase),
            provider = defaultSecurityProvider,
            prefDevice = prefDevice,
            fcmHandler = fcmHandler,
        )
    }

class PushRepo(
    private val context: Context,
    private val apiMastodon: ApiPushMastodon,
    private val apiMisskey: ApiPushMisskey,
    private val apiAppServer: ApiPushAppServer,
    private val daoSavedAccount: SavedAccount.Access,
    private val daoPushMessage: PushMessage.Access,
    private val daoStatus: AccountNotificationStatus.Access,
    private val provider: Provider,
    private val prefDevice: PrefDevice,
    private val fcmHandler: FcmHandler,
) {
    companion object {
        @Suppress("RegExpSimplifiable")
        private val reTailDigits = """([0-9]+)\z""".toRegex()

        private val ncPushMessage = NotificationChannels.PushMessage

        var refReporter: WeakReference<(String) -> Unit>? = null

        var subscriptionMutex = Mutex()

        private suspend inline fun <R> ListenableFuture<R>.await(): R {
            // Fast path
            if (isDone) {
                try {
                    return get()
                } catch (e: ExecutionException) {
                    throw e.cause ?: e
                }
            }
            return suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { cancel(false) }
                addListener(
                    Runnable {
                        try {
                            cont.resume(get())
                        } catch (throwable: Throwable) {
                            val cause = throwable.cause ?: throwable
                            when (throwable) {
                                is CancellationException -> cont.cancel(cause)
                                else -> cont.resumeWithException(cause)
                            }
                        }
                    },
                    directExecutor(),
                )
            }
        }
    }

    private val pushMisskey by lazy {
        PushMisskey(
            context = context,
            api = apiMisskey,
            provider = provider,
            prefDevice = prefDevice,
            daoStatus = daoStatus,
        )
    }

    private val pushMastodon by lazy {
        PushMastodon(
            context = context,
            api = apiMastodon,
            provider = provider,
            prefDevice = prefDevice,
            daoStatus = daoStatus,
        )
    }

    /**
     * UPでプッシュサービスを選ぶと呼ばれる
     */
    suspend fun switchDistributor(
        pushDistributor: String?,
        reporter: SuspendProgress.Reporter,
    ) {
        val timeSwitchStart = System.currentTimeMillis()

        val workState = AtomicReference<String>(null)
        val progress = AtomicReference<String>(null)
        refReporter = WeakReference { progress.set(it) }
        fun showProgress() {
            val text = arrayOf(
                workState.get(),
                progress.get()
            ).mapNotNull { it?.notEmpty() }.joinToString("\n")
            reporter.setMessage(text)
        }

        fun showProgress(text: String) {
            workState.set(text)
            showProgress()
        }

        log.i("switchDistributor: pushDistributor=$pushDistributor")
        prefDevice.pushDistributor = pushDistributor

        var operation: Operation? = withContext(Dispatchers.IO) {
            showProgress(context.getString(R.string.removing_old_distributer))

            // WorkManagerの完了済みのジョブを捨てる
            WorkManager.getInstance(context).pruneWork().await()

            // Unified購読の削除
            // 後でブロードキャストを受け取るかもしれない
            UnifiedPush.unregisterApp(context)

            // FCMトークンの削除。これでこの端末のこのアプリへの古いエンドポイント登録はgoneになり消えるはず
            fcmHandler.deleteFcmToken(context)

            when (pushDistributor) {
                null, "" -> {
                    // 特に変更しない(アクセストークン更新時に呼ばれる
                    showProgress("enqueueRegisterEndpoint for ${pushDistributor}…")
                    enqueueRegisterEndpoint(context)
                }

                PrefDevice.PUSH_DISTRIBUTOR_NONE -> {
                    // 購読解除

                    showProgress("enqueueRegisterEndpoint for ${pushDistributor}…")
                    enqueueRegisterEndpoint(context)
                }

                PrefDevice.PUSH_DISTRIBUTOR_FCM -> {
                    // 特にイベントは来ないので、プッシュ購読をやりなおす
                    showProgress("enqueueRegisterEndpoint for ${pushDistributor}…")
                    enqueueRegisterEndpoint(context)
                }

                else -> {
                    showProgress("UnifiedPush.saveDistributor")
                    UnifiedPush.saveDistributor(context, pushDistributor)
                    // 何らかの理由で登録は壊れることがあるため、登録し直す
                    showProgress("UnifiedPush.registerApp")
                    UnifiedPush.registerApp(context)
                    // 少し後にonNewEndpointが発生するので、続きはそこで
                    null
                }
            }
        }

        while (true) {
            if (PushWorker.timeEndRegisterEndpoint.get() >= timeSwitchStart ||
                PushWorker.timeEndUpEndpoint.get() >= timeSwitchStart
            ) {
                // complete
                break
            }
            val lines = ArrayList<String>()
            if (operation != null) {
                log.i("enqueue operation: ${operation.state.value}")
                if (operation.state.value is Operation.State.SUCCESS) {
                    operation = null
                } else {
                    lines.add("operation enqueing. ${operation.state.value}")
                }
            }
            val works = arrayOf(
                PushWorker.ACTION_UP_ENDPOINT,
                PushWorker.ACTION_REGISTER_ENDPOINT
            ).map {
                WorkManager.getInstance(context)
                    .getWorkInfosByTag(it).await()
            }.flatten().filter { !it.state.isFinished }

            works.map {
                "work tag=${
                    it.tags.filter { t -> !t.startsWith(PushWorker::class.java.name) }
                        .joinToString("/")
                } state=${it.state} id=${it.id}"
            }.let { lines.addAll(it) }
            showProgress(lines.sorted().joinToString("\n"))

            val timeout = when {
                works.isEmpty() -> TimeUnit.MINUTES.toMillis(2)
                else -> TimeUnit.MINUTES.toMillis(30)
            }

            val remain = min(1000L, timeSwitchStart + timeout - System.currentTimeMillis())
            if (remain <= 0) {
                showProgress("timeout")
                delay(666L)
                break
            }
            delay(remain)
        }
    }

    /**
     * switchDistributor が UnifiedPush.registerAppする
     * ↓
     * UpMessageReceiver の onNewEndpoint が呼ばれる
     * ↓
     * PushWorker の ACTION_UP_ENDPOINT が登録される
     * ↓
     * ワーカーからnewUpEndpoint()が呼ばれる
     */
    suspend fun newUpEndpoint(upEndpoint: String) {
        refReporter?.get()
            ?.invoke(context.getString(R.string.unified_push_got_new_endpoint_url))

        val upPackageName = UnifiedPush.getDistributor(context).notEmpty()
            ?: error("missing upPackageName")

        if (upPackageName != prefDevice.pushDistributor) {
            log.w("newEndpoint: race condition detected!")
        }

        // 古いエンドポイントを別プロパティに覚えておく
        prefDevice.upEndpoint
            ?.takeIf { it.isNotEmpty() && it != upEndpoint }
            ?.let { prefDevice.upEndpointExpired = it }

        prefDevice.upEndpoint = upEndpoint

        // 購読の更新
        registerEndpoint(keepAliveMode = false)
    }

    /**
     * - PushWorkerのACTION_UP_ENDPOINTの実行中に呼ばれる
     * - PushWorkerのACTION_REGISTER_ENDPOINTの実行中に呼ばれる
     */
    suspend fun registerEndpoint(
        keepAliveMode: Boolean,
    ) {
        if (subscriptionMutex.isLocked) {
            refReporter?.get()?.invoke("registerEndpoint: waiting mutex…")
        }
        subscriptionMutex.withLock {
            refReporter?.get()?.invoke("registerEndpoint for ${prefDevice.pushDistributor}…")
            log.i("registerEndpoint: keepAliveMode=$keepAliveMode")

            // 古いFCMトークンの情報はアプリサーバ側で勝手に消えるはず
            try {
                // 期限切れのUPエンドポイントがあればそれ経由の中継を解除する
                prefDevice.fcmTokenExpired.notEmpty()?.let {
                    refReporter?.get()
                        ?.invoke(context.getString(R.string.removing_old_fcm_token))
                    log.i("remove fcmTokenExpired")
                    apiAppServer.endpointRemove(fcmToken = it)
                    prefDevice.fcmTokenExpired = null
                }
            } catch (ex: Throwable) {
                log.w(ex, "can't forgot fcmTokenExpired")
            }

            try {
                // 期限切れのUPエンドポイントがあればそれ経由の中継を解除する
                prefDevice.upEndpointExpired.notEmpty()?.let {
                    refReporter?.get()
                        ?.invoke(context.getString(R.string.removing_old_unified_push_url))
                    log.i("remove upEndpointExpired")
                    apiAppServer.endpointRemove(upUrl = it)
                    prefDevice.upEndpointExpired = null
                }
            } catch (ex: Throwable) {
                log.w(ex, "can't forgot upEndpointExpired")
            }

            val realAccounts = daoSavedAccount.loadRealAccounts()
                .filter { !it.isPseudo && it.isConfirmed }

            val accts = realAccounts.map { it.acct }

            // map of acctHash to account
            val acctHashMap = daoStatus.updateAcctHash(accts)
            if (acctHashMap.isEmpty()) {
                log.w("acctHashMap is empty. no need to update register endpoint")
                return
            }

            if (keepAliveMode) {
                val lastUpdated = prefDevice.timeLastEndpointRegister
                val now = System.currentTimeMillis()
                if (now - lastUpdated < TimeUnit.DAYS.toMillis(3)) {
                    log.i("lazeMode: skip re-registration.")
                    return
                }
            }
            var willRemoveSubscription = false

            val hasFcm = fcmHandler.hasFcm(context)

            if (!hasFcm && prefDevice.pushDistributor == PrefDevice.PUSH_DISTRIBUTOR_FCM) {
                log.w("fmc selected, but this is noFcm build. unset distributer.")
                prefDevice.pushDistributor = null
            }

            // アプリサーバにendpointを登録する
            refReporter?.get()
                ?.invoke(context.getString(R.string.sending_push_distributor_info_to_app_server))

            val acctHashList = acctHashMap.keys.toList()

            val json = when (prefDevice.pushDistributor) {
                null, "" -> when {
                    hasFcm -> {
                        log.i("registerEndpoint dist=FCM(default), acctHashList=${acctHashList.size}")
                        registerEndpointFcm(acctHashList)
                    }

                    else -> {
                        log.w("pushDistributor not selected. but can't select default distributor from background service.")
                        return
                    }
                }

                PrefDevice.PUSH_DISTRIBUTOR_NONE -> {
                    log.i("push distrobuter 'none' is selected. it will remove subscription.")
                    willRemoveSubscription = true
                    null
                }

                PrefDevice.PUSH_DISTRIBUTOR_FCM -> {
                    log.i("registerEndpoint dist=FCM, acctHashList=${acctHashList.size}")
                    registerEndpointFcm(acctHashList)
                }

                else -> {
                    log.i("registerEndpoint dist=${prefDevice.pushDistributor}, acctHashList=${acctHashList.size}")
                    registerEndpointUnifiedPush(acctHashList)
                }
            }

            when {
                json.isNullOrEmpty() ->
                    log.i("no information of appServerHash.")

                else -> {
                    // acctHash => appServerHash のマップが返ってくる
                    // ステータスに覚える
                    var saveCount = 0
                    for (acctHash in json.keys) {
                        val acct = acctHashMap[acctHash] ?: continue
                        val appServerHash = json.string(acctHash) ?: continue
                        ++saveCount
                        val status = daoStatus.loadOrCreate(acct)
                        if (status.appServerHash == appServerHash) continue
                        daoStatus.saveAppServerHash(status.id, appServerHash)
                    }
                    log.i("appServerHash updated. saveCount=$saveCount")
                }
            }

            realAccounts.forEach { account ->
                val subLog = object : PushBase.SubscriptionLogger {
                    override val context = this@PushRepo.context
                    override fun i(msg: String) {
                        log.i("[${account.acct}]$msg")
                        refReporter?.get()?.invoke("[${account.acct}]$msg")
                    }

                    override fun e(msg: String) {
                        log.e("[${account.acct}]$msg")
                        refReporter?.get()?.invoke("[${account.acct}]$msg")
                        daoAccountNotificationStatus.updateSubscriptionError(
                            account.acct,
                            msg
                        )
                    }

                    override fun e(ex: Throwable, msg: String) {
                        log.e(ex, "[${account.acct}]$msg")
                        refReporter?.get()?.invoke("[${account.acct}]$msg")
                        daoAccountNotificationStatus.updateSubscriptionError(
                            account.acct,
                            ex.withCaption(msg)
                        )
                    }
                }
                try {
                    subLog.i("check account subscription…")
                    val errMsg = pushBase(account).updateSubscription(
                        subLog = subLog,
                        account = account,
                        willRemoveSubscription = willRemoveSubscription || !account.isRequiredPushSubscription(),
                        forceUpdate = false,
                    )
                    daoAccountNotificationStatus.updateSubscriptionError(account.acct, errMsg)
                    if (errMsg != null) {
                        subLog.e(errMsg)
                    }
                    subLog.i("check account subscription end.")
                } catch (ex: Throwable) {
                    log.e(ex, "updateSubscription failed.")
                    val errMsg = ex.withCaption()
                    subLog.e(errMsg)
                    daoAccountNotificationStatus.updateSubscriptionError(account.acct, errMsg)
                }
            }
            refReporter?.get()?.invoke("all accounts checked.")
            prefDevice.timeLastEndpointRegister = System.currentTimeMillis()
        }
    }

    private suspend fun registerEndpointUnifiedPush(acctHashList: List<String>) =
        when (val upEndpoint = prefDevice.upEndpoint) {
            null, "" -> {
                log.w("missing upEndpoint. can't register endpoint.")
                null
            }

            else -> {
                apiAppServer.endpointUpsert(
                    upUrl = upEndpoint,
                    fcmToken = null,
                    acctHashList = acctHashList
                )
            }
        }

    private suspend fun registerEndpointFcm(acctHashList: List<String>) =
        when (val fcmToken = fcmHandler.loadFcmToken()) {
            null, "" -> {
                log.w("missing fcmToken. can't register endpoint.")
                null
            }

            else -> {
                apiAppServer.endpointUpsert(
                    upUrl = null,
                    fcmToken = fcmToken,
                    acctHashList = acctHashList
                )
            }
        }

    /**
     * アカウント設定から、SNSサーバに購読を行う
     *
     * willRemoveSubscription=trueの場合、購読を削除する。
     * アクセストークン更新やアカウント削除の際に古い購読を捨てたい場合に使う。
     */
    suspend fun updateSubscription(
        subLog: PushBase.SubscriptionLogger,
        account: SavedAccount,
        willRemoveSubscription: Boolean,
        forceUpdate: Boolean = false,
    ) {
        subscriptionMutex.withLock {
            try {
                val errMsg = pushBase(account).updateSubscription(
                    subLog = subLog,
                    account = account,
                    willRemoveSubscription = willRemoveSubscription || !account.isRequiredPushSubscription(),
                    forceUpdate = forceUpdate,
                )
                daoAccountNotificationStatus.updateSubscriptionError(account.acct, errMsg)
                if (errMsg != null) {
                    subLog.e(errMsg)
                }
            } catch (ex: Throwable) {
                log.e(ex, "updateSubscription failed.")
                val errMsg = ex.withCaption()
                subLog.e(errMsg)
                daoAccountNotificationStatus.updateSubscriptionError(account.acct, errMsg)
            }
        }
    }

    private fun pushBase(account: SavedAccount) = when {
        account.isMisskey -> pushMisskey
        else -> pushMastodon
    }

    //////////////////////////////////////////////////////////////////////////////
    // メッセージの処理

    /**
     * FcmHandlerから呼ばれる。
     */
    fun handleFcmMessage(data: Map<String, String>) {
        data["d"]?.decodeBase128()?.let { saveRawMessage(it) }
    }

    /**
     * UpMessageReceiverから呼ばれる。
     */
    fun saveUpMessage(message: ByteArray) {
        saveRawMessage(message)
    }

    /**
     * 受信した生データを保存して、後はワーカーに任せる
     */
    private fun saveRawMessage(bytes: ByteArray) {
        val pm = PushMessage(rawBody = bytes)
        daoPushMessage.save(pm)
        enqueuePushMessage(context, pm.id)
    }

    /**
     * UIで再解読を選択した
     *
     * - 実際のアプリでは解読できたものだけを保存したいが、これは試験アプリなので…
     */
    suspend fun reprocess(pm: PushMessage) {
        withContext(AppDispatchers.IO) {
            updateMessage(pm.id, allowDupilicateNotification = true)
        }
    }

    /**
     * UpWorkerから呼ばれる。
     * 保存データを解釈して通知を出す。
     */
    suspend fun updateMessage(
        messageId: Long,
        allowDupilicateNotification: Boolean = false,
    ) {
        // DBからロード
        val pm = daoPushMessage.find(messageId)
            ?: error("missing pushMessage")

        try {
            // rawBodyをBinPackMapにデコード
            var map = pm.rawBody?.decodeBinPackMap()
                ?: error("binPack decode failed.")

            // ペイロードがなくてURLが付与されたメッセージは
            // アプリサーバから読み直す
            if (map["b"] == null) {
                map.string("l")?.let { largeObjectId ->
                    apiAppServer.getLargeObject(largeObjectId)
                        ?.let {
                            map = it.decodeBinPack() as? BinPackMap
                                ?: error("binPack decode failed.")
                            pm.rawBody = it
                            daoPushMessage.save(pm)
                        }
                }
            }

            // acctHashがある
            val acctHash = map.string("a") ?: error("missing a.")

            val status = daoStatus.findByAcctHash(acctHash)
                ?: error("missing status for acctHash $acctHash")

            val acct = status.acct.takeIf { it.isValidFull }
                ?: error("empty acct.")

            pm.loginAcct = status.acct

            val account = daoSavedAccount.loadAccountByAcct(acct)
                ?: error("missing account for acct ${status.acct}")

            if (account.isMisskey && !PrefB.bpEnableDeprecatedSomething.value) {
                error(context.getString(R.string.misskey_support_end))
            }

            decodeMessageContent(status, pm, map)
            val messageJson = pm.messageJson

            if (messageJson == null) {
                // デコード失敗
                // 古い鍵で行った購読だろう。
                // メッセージに含まれるappServerHashを指定してendpoint登録を削除する
                // するとアプリサーバはSNSサーバに対してgoneを返すようになり掃除が適切に行われるはず
                map.string("c").notEmpty()?.let {
                    val count = apiAppServer.endpointRemove(hashId = it).int("count")
                    log.w("endpointRemove $count hashId=$it")
                }

                error("can't decode WebPush message to JSON.")
            }

            // Mastodonはなぜかアクセストークンが書いてあるので危険…
            val messageJsonFiltered = messageJson.toString()
                .replace(
                    """"access_token":"[^"]+"""".toRegex(),
                    """"access_token":"***""""
                )
            log.i("${status.acct} $messageJsonFiltered")

            // ミュート用データを時々読む
            TootStatus.updateMuteData()

            // messageJsonを解釈して通知に出す内容を決める
            pushBase(account).formatPushMessage(account, pm)

            val notificationId = pm.notificationId
            if (notificationId.isNullOrEmpty()) {
                log.w("can't show notification. missing notificationId.")
                return
            }

            if (!account.isNotificationEnabled(pm.notificationType?.toNotificationType())) {
                log.w("notificationType ${pm.notificationType} is disabled.")
                return
            }

            if (!allowDupilicateNotification &&
                daoNotificationShown.duplicateOrPut(acct, notificationId)
            ) {
                log.w("can't show notification. it's duplicate. $acct $notificationId")
                return
            }

            showPushNotification(pm, account, notificationId)
        } catch (ex: Throwable) {
            log.e(ex, "updateMessage failed.")
            pm.formatError = ex.withCaption()
        } finally {
            daoPushMessage.save(pm)
        }
    }

    /**
     * プッシュされたデータを解読してDB上の項目を更新する
     *
     * - 実際のアプリでは解読できたものだけを保存したいが、これは試験アプリなので…
     */
    private fun decodeMessageContent(
        status: AccountNotificationStatus,
        pm: PushMessage,
        map: BinPackMap,
    ) {
        val encryptedBody = map.bytes("b") ?: error("missing encryptedBody")
        val headers = map.map("h") ?: error("missing headers")

        pm.headerJson = buildJsonObject {
            headers.entries.forEach { e ->
                put(e.key.toString(), e.value.toString())
            }
        }

        // ヘッダを探すときは小文字化
        fun header(name: String): String? = headers.string(name.lowercase())

        // log.i("headerJson.keys=${headerJson.keys.joinToString(",")}")
        //        headerJson={
        //            "Digest":"SHA-256=nnn",
        //            "Content-Encoding":"aesgcm",
        //            "Encryption":"salt=75n4Si2vAVv2xZFXnIh5Ww",
        //            "Crypto-Key":"dh=XXX;p256ecdsa=XXX",
        //            "Authorization":"WebPush XXX.XXX.XXX"
        //        }

        try {
            if (header("Content-Encoding")?.trim() == "aes128gcm") {
                Aes128GcmDecoder(encryptedBody.byteRangeReader(), provider).run {
                    deriveKeyWebPush(
                        // receiver private key in X509 format
                        receiverPrivateBytes = status.pushKeyPrivate
                            ?: error("missing pushKeyPrivate"),
                        // receiver public key in 65bytes X9.62 uncompressed format
                        receiverPublicBytes = status.pushKeyPublic
                            ?: error("missing pushKeyPublic"),
                        // auth secrets created at subscription
                        authSecret = status.pushAuthSecret ?: error("missing pushAuthSecret"),
                    )
                    decode()
                }
            } else {
                // Crypt-Key から dh と p256ecdsa を見る
                val cryptKeys = header("Crypto-Key")
                    ?.parseSemicolon() ?: error("missing Crypto-Key")

                AesGcmDecoder(
                    receiverPrivateBytes = status.pushKeyPrivate ?: error("missing pushKeyPrivate"),
                    receiverPublicBytes = status.pushKeyPublic ?: error("missing pushKeyPublic"),
                    senderPublicBytes = cryptKeys["dh"]?.decodeBase64()
                        ?: status.pushServerKey ?: error("missing pushServerKey"),
                    authSecret = status.pushAuthSecret ?: error("missing pushAuthSecret"),
                    saltBytes = header("Encryption")?.parseSemicolon()
                        ?.get("salt")?.decodeBase64()
                        ?: error("missing Encryption.salt"),
                    provider = provider
                ).run {
                    deriveKey()
                    decode(encryptedBody.toByteRange())
                }
            }
        } catch (ex: Throwable) {
            // クライアント側の鍵が異なる等でデコードできない場合がある
            log.e(ex.withCaption("message decipher failed."))
            null
        }?.decodeUTF8()?.decodeJsonObject()?.let {
            pm.messageJson = it
            daoPushMessage.save(pm)
        }
    }

    /**
     * SNSからの通知を表示する
     */
    private suspend fun showPushNotification(
        pm: PushMessage,
        account: SavedAccount,
        notificationId: String,
    ) {
        if (ncPushMessage.isDisabled(context)) {
            log.w("ncPushMessage isDisabled.")
            return
        }

        val density = context.resources.displayMetrics.density
        val notificationType = pm.notificationType?.toNotificationType()

        val iconAndColor = notificationType.pushMessageIconAndColor()

        val targetName = daoAcctColor.getNicknameWithColor(account.acct)

        suspend fun PushMessage.loadSmallIcon(context: Context): IconCompat {
            iconSmall?.notEmpty()
                ?.let { context.loadIcon(pm.iconSmall, (24f * density + 0.5f).toInt()) }
                ?.let { return IconCompat.createWithBitmap(it) }
            val iconId = iconAndColor.iconId
            return IconCompat.createWithResource(context, iconId)
        }

        val iconSmall = pm.loadSmallIcon(context)
        val iconBitmapLarge = context.loadIcon(pm.iconLarge, (48f * density + 0.5f).toInt())

        val params = listOf(
            "db_id" to account.db_id.toString(),
            // URIをユニークにするため。参照されない
            "type" to "v2push", // "type" to trackingType.str,
            // URIをユニークにするため。参照されない
            "notificationId" to notificationId,
        ).joinToString("&") {
            "${it.first.encodePercent()}=${it.second.encodePercent()}"
        }

        val iTap = Intent(context, ActCallback::class.java).apply {
            data = "${ncPushMessage.uriPrefixTap}?$params".toUri()
            // FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY を付与してはいけない
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val piTap = PendingIntent.getActivity(
            context,
            ncPushMessage.pircTap,
            iTap,
            PendingIntent.FLAG_IMMUTABLE
        )

        val group = context.packageName + ":" + account.acct.ascii
        val urlDeleteSummary = "${ncPushMessage.uriPrefixDelete}/summary/${account.acct.ascii}"
        val urlDelete = "${ncPushMessage.uriPrefixDelete}/${pm.id}"
        val iDelete = context.intentNotificationDelete(urlDelete.toUri())
        val piDelete = PendingIntent.getBroadcast(
            context,
            ncPushMessage.pircDelete,
            iDelete,
            PendingIntent.FLAG_IMMUTABLE
        )

        // val iTap = intentActMessage(pm.messageDbId)
        // val piTap = PendingIntent.getActivity(this, nc.pircTap, iTap, PendingIntent.FLAG_IMMUTABLE)

        ncPushMessage.notify(context, urlDelete) {
            color = iconAndColor.colorRes.notZero()
                ?.let { ContextCompat.getColor(context, it) }
                ?: account.notificationAccentColor.notZero()
                        ?: ContextCompat.getColor(context, R.color.colorOsNotificationAccent)
            setSmallIcon(iconSmall)
            iconBitmapLarge?.let { setLargeIcon(it) }
            setContentTitle(targetName)
            setContentText(pm.text)
            setWhen(pm.timestamp)
            setContentIntent(piTap)
            setDeleteIntent(piDelete)
            setAutoCancel(true)
            pm.textExpand.notEmpty()?.let {
                setStyle(NotificationCompat.BigTextStyle().bigText(it))
            }
            setGroup(group)
        }

        ncPushMessage.notify(context, urlDeleteSummary) {
            setContentTitle(targetName)
            setContentText("notifications")
            setWhen(pm.timestamp)
            setSmallIcon(R.drawable.ic_notification)
            color = account.notificationAccentColor.notZero()
                ?: ContextCompat.getColor(context, R.color.colorOsNotificationAccent)
            setContentIntent(piTap)
            setAutoCancel(true)
            setGroup(group)
            setGroupSummary(true)
        }
    }

    /**
     * 通知を消す
     *
     * - 試験アプリなのであまり積極的に消さない…
     */
    fun deleteSnsNotification(messageDbId: Long) {
        try {
            ncPushMessage.cancel(context, "${ncPushMessage.uriPrefixDelete}/${messageDbId}")
        } catch (ex: Throwable) {
            log.e(ex, "deleteSnsNotification failed. messageDbId=$messageDbId")
        }
    }

    /**
     * 通知をスワイプして削除した。
     * - URLからDB上の項目のIDを取得
     * - timeDismissを更新する
     */
    fun onDeleteNotification(uri: String) {
        val messageDbId = reTailDigits.find(uri)?.groupValues?.elementAtOrNull(0)
            ?.toLongOrNull()
            ?: error("missing messageDbId in $uri")
        daoPushMessage.dismiss(messageDbId)
    }

    /**
     * 通知タップのインテントをメイン画面が受け取った
     */

    fun onTapNotification(account: SavedAccount) {
        EmptyScope.launch(AppDispatchers.IO) {
            try {
                daoPushMessage.dismissByAcct(account.acct)
            } catch (ex: Throwable) {
                log.e(ex, "onTapNotification failed.")
            }
        }
    }
}
