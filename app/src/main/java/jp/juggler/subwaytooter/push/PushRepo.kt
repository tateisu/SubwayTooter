package jp.juggler.subwaytooter.push

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import androidx.work.WorkManager
import androidx.work.await
import jp.juggler.crypt.*
import jp.juggler.subwaytooter.ActCallback
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.push.ApiPushAppServer
import jp.juggler.subwaytooter.api.push.ApiPushMastodon
import jp.juggler.subwaytooter.api.push.ApiPushMisskey
import jp.juggler.subwaytooter.dialog.SuspendProgress
import jp.juggler.subwaytooter.notification.NotificationChannels
import jp.juggler.subwaytooter.notification.NotificationDeleteReceiver.Companion.intentNotificationDelete
import jp.juggler.subwaytooter.notification.notificationIconAndColor
import jp.juggler.subwaytooter.pref.PrefDevice
import jp.juggler.subwaytooter.pref.prefDevice
import jp.juggler.subwaytooter.push.*
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
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.unifiedpush.android.connector.UnifiedPush
import java.lang.ref.WeakReference
import java.security.Provider
import java.util.concurrent.TimeUnit

private val log = LogCategory("PushRepo")

val Context.pushRepo: PushRepo
    get() {
        val okHttp = OkHttpClient.Builder().apply {
            connectTimeout(60, TimeUnit.SECONDS)
            writeTimeout(60, TimeUnit.SECONDS)
            readTimeout(60, TimeUnit.SECONDS)
        }.build()
        val appDatabase = appDatabase
        return PushRepo(
            context = applicationContextSafe,
            apiPushAppServer = ApiPushAppServer(okHttp),
            apiPushMastodon = ApiPushMastodon(okHttp),
            apiPushMisskey = ApiPushMisskey(okHttp),
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
    private val apiPushMastodon: ApiPushMastodon,
    private val apiPushMisskey: ApiPushMisskey,
    private val apiPushAppServer: ApiPushAppServer,
    private val daoSavedAccount: SavedAccount.Access,
    private val daoPushMessage: PushMessage.Access,
    private val daoStatus: AccountNotificationStatus.Access,
    private val provider: Provider,
    private val prefDevice: PrefDevice,
    private val fcmHandler: FcmHandler,
) {
    companion object {
        private val reHttp = """https?://""".toRegex()

        @Suppress("RegExpSimplifiable")
        private val reTailDigits = """([0-9]+)\z""".toRegex()

        const val JSON_CAME_FROM = "<>cameFrom"
        const val CAME_FROM_UNIFIED_PUSH = "unifiedPush"
        const val CAME_FROM_FCM = "fcm"

        var refReporter: WeakReference<SuspendProgress.Reporter>? = null

        val ncPushMessage = NotificationChannels.PushMessage

        fun String?.followDomain(apiHost: Host) = when {
            isNullOrEmpty() -> null
            reHttp.containsMatchIn(this) -> this
            this[0] == '/' -> "https://$apiHost$this"
            else -> "https://$apiHost/$this"
        }
    }

    private val pushMisskey by lazy {
        PushMisskey(
            api = apiPushMisskey,
            provider = provider,
            prefDevice = prefDevice,
            daoStatus = daoStatus,
        )
    }
    private val pushMastodon by lazy {
        PushMastodon(
            api = apiPushMastodon,
            provider = provider,
            prefDevice = prefDevice,
            daoStatus = daoStatus,
        )
    }

    /**
     * UPでプッシュサービスを選ぶと呼ばれる
     */
    suspend fun switchDistributor(
        pushDistributor: String,
        reporter: SuspendProgress.Reporter,
    ) {
        val timeSwitchStart = System.currentTimeMillis()
        refReporter = WeakReference(reporter)

        log.i("switchDistributor: pushDistributor=$pushDistributor")
        prefDevice.pushDistributor = pushDistributor

        withContext(Dispatchers.IO) {
            reporter.setMessage(context.getString(R.string.removing_old_distributer))

            // WorkManagerの完了済みのジョブを捨てる
            WorkManager.getInstance(context).pruneWork().await()

            // Unified購読の削除
            // 後でブロードキャストを受け取るかもしれない
            UnifiedPush.unregisterApp(context)

            // FCMトークンの削除。これでこの端末のこのアプリへの古いエンドポイント登録はgoneになり消えるはず
            fcmHandler.deleteFcmToken()

            when (pushDistributor) {
                PrefDevice.PUSH_DISTRIBUTOR_NONE -> {
                    // 購読解除
                    reporter.setMessage("SubscriptionUpdateService.launch")
                    enqueueRegisterEndpoint(context)
                }
                PrefDevice.PUSH_DISTRIBUTOR_FCM -> {
                    // 特にイベントは来ないので、プッシュ購読をやりなおす
                    reporter.setMessage("SubscriptionUpdateService.launch")
                    enqueueRegisterEndpoint(context)
                }
                else -> {
                    reporter.setMessage("UnifiedPush.saveDistributor")
                    UnifiedPush.saveDistributor(context, pushDistributor)
                    // 何らかの理由で登録は壊れることがあるため、登録し直す
                    reporter.setMessage("UnifiedPush.registerApp")
                    UnifiedPush.registerApp(context)
                    // 少し後にonNewEndpointが発生するので、続きはそこで
                }
            }
        }
        val timeout = timeSwitchStart + TimeUnit.SECONDS.toMillis(20)
        while (true) {
            val now = System.currentTimeMillis()
            if (now >= timeout) {
                reporter.setMessage("timeout")
                delay(888L)
                break
            }
            if (PushWorker.timeEndRegisterEndpoint.get() >= timeSwitchStart ||
                PushWorker.timeEndUpEndpoint.get() >= timeSwitchStart
            ) {
                reporter.setMessage("complete")
                delay(888L)
                break
            }
            delay(1000L)
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
        refReporter?.get()?.setMessage("新しい UnifiedPush endpoint URL を取得しました")

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
        log.i("registerEndpoint: keepAliveMode=$keepAliveMode")

        // 古いFCMトークンの情報はアプリサーバ側で勝手に消えるはず
        try {
            // 期限切れのUPエンドポイントがあればそれ経由の中継を解除する
            prefDevice.fcmTokenExpired.notEmpty()?.let {
                refReporter?.get()?.setMessage("期限切れのFCMデバイストークンをアプリサーバから削除しています")
                log.i("remove fcmTokenExpired")
                apiPushAppServer.endpointRemove(fcmToken = it)
                prefDevice.fcmTokenExpired = null
            }
        } catch (ex: Throwable) {
            log.w(ex, "can't forgot fcmTokenExpired")
        }

        try {
            // 期限切れのUPエンドポイントがあればそれ経由の中継を解除する
            prefDevice.upEndpointExpired.notEmpty()?.let {
                refReporter?.get()?.setMessage("期限切れのUnifiedPushエンドポイントをアプリサーバから削除しています")
                log.i("remove upEndpointExpired")
                apiPushAppServer.endpointRemove(upUrl = it)
                prefDevice.upEndpointExpired = null
            }
        } catch (ex: Throwable) {
            log.w(ex, "can't forgot upEndpointExpired")
        }

        val realAccounts = daoSavedAccount.loadAccountList()
            .filter { !it.isPseudo }

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
            }
        }

        var willRemoveSubscription = false

        // アプリサーバにendpointを登録する
        refReporter?.get()?.setMessage("アプリサーバにプッシュサービスの情報を送信しています")
        log.i("pushDistributor=${prefDevice.pushDistributor}")
        val acctHashList = acctHashMap.keys.toList()
        val json = when (prefDevice.pushDistributor) {
            null, "" -> when {
                fcmHandler.hasFcm -> registerEndpointFcm(acctHashList)
                else -> {
                    log.w("pushDistributor not selected. but can't select default distributor from background service.")
                    null
                }
            }
            PrefDevice.PUSH_DISTRIBUTOR_NONE -> {
                willRemoveSubscription = true
                null
            }
            PrefDevice.PUSH_DISTRIBUTOR_FCM -> registerEndpointFcm(acctHashList)
            else -> registerEndpointUnifiedPush(acctHashList)
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

        realAccounts.forEach { a ->
            val subLog = object : PushBase.SubscriptionLogger {
                override val context = this@PushRepo.context
                override fun i(msg: String) {
                    log.i("[${a.acct}]$msg")
                }

                override fun e(msg: String) {
                    log.e("[${a.acct}]$msg")
                    daoAccountNotificationStatus.updateSubscriptionError(
                        a.acct,
                        msg
                    )
                }

                override fun e(ex: Throwable, msg: String) {
                    log.e(ex, "[${a.acct}]$msg")
                    daoAccountNotificationStatus.updateSubscriptionError(
                        a.acct,
                        ex.withCaption(msg)
                    )
                }
            }
            try {
                refReporter?.get()?.setMessage("${a.acct.pretty} のWebPush購読を更新しています")
                daoAccountNotificationStatus.updateSubscriptionError(
                    a.acct,
                    null
                )
                pushBase(a).updateSubscription(
                    subLog = subLog,
                    a = a,
                    willRemoveSubscription = willRemoveSubscription,
                    forceUpdate = false,
                )
            } catch (ex: Throwable) {
                subLog.e(ex, "updateSubscription failed.")
            }
        }
        prefDevice.timeLastEndpointRegister = System.currentTimeMillis()
    }

    private suspend fun registerEndpointUnifiedPush(acctHashList: List<String>) =
        when (val upEndpoint = prefDevice.upEndpoint) {
            null, "" -> {
                log.w("missing upEndpoint. can't register endpoint.")
                null
            }
            else -> {
                log.i("endpointUpsert up ")
                apiPushAppServer.endpointUpsert(
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
                log.i("endpointUpsert fcm ")
                apiPushAppServer.endpointUpsert(
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
        a: SavedAccount,
        willRemoveSubscription: Boolean,
        forceUpdate: Boolean = false,
    ) {
        pushBase(a).updateSubscription(
            subLog = subLog,
            a = a,
            willRemoveSubscription = willRemoveSubscription,
            forceUpdate = forceUpdate,
        )
    }

    private fun pushBase(a: SavedAccount) = when {
        a.isMisskey -> pushMisskey
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
    suspend fun reDecode(pm: PushMessage) {
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

        // rawBodyをBinPackMapにデコード
        var map = pm.rawBody?.decodeBinPackMap()
            ?: error("binPack decode failed.")

        // ペイロードがなくてURLが付与されたメッセージは
        // アプリサーバから読み直す
        if (map["b"] == null) {
            map.string("l")?.let { largeObjectId ->
                apiPushAppServer.getLargeObject(largeObjectId)
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

        val account = daoSavedAccount.loadAccountByAcct(Acct.parse(status.acct))
            ?: error("missing account for acct ${status.acct}")

        pm.loginAcct = status.acct

        decodeMessageContent(status, pm, map)

        if (pm.messageJson == null) {
            // デコード失敗
            // 古い鍵で行った購読だろう。
            // メッセージに含まれるappServerHashを指定してendpoint登録を削除する
            // するとアプリサーバはSNSサーバに対してgoneを返すようになり掃除が適切に行われるはず
            map.string("c").notEmpty()?.let {
                val count = apiPushAppServer.endpointRemove(hashId = it)
                    .int("count")
                log.w("endpointRemove $count hashId=$it")
            }
            error("can't decode WebPush message to JSON.")
        }
        // Mastodonはなぜかアクセストークンが書いてあるので危険…
        val censored = pm.messageJson.toString()
            .replace(""""access_token":"[^"]+"""".toRegex(), """"access_token":"***"""")
        log.i("${status.acct} $censored")

        // messageJsonを解釈して通知に出す内容を決める
        try {
            pushBase(account).formatPushMessage(account, pm)
        } catch (ex: Throwable) {
            log.e(ex, "formatPushMessage failed.")
            return
        }

        daoPushMessage.save(pm)

        val acct = pm.loginAcct
        if (acct.isNullOrEmpty()) {
            log.e("can't show notification. missing acct.")
            return
        }
        val notificationId = pm.notificationId
        if (notificationId.isNullOrEmpty()) {
            log.e("can't show notification. missing notificationId.")
            return
        }
        if (!allowDupilicateNotification &&
            daoNotificationShown.duplicateOrPut(acct, notificationId)
        ) {
            log.w("can't show notification. it's duplicate. $acct $notificationId")
            return
        }

        // 解読できた(例外が出なかった)なら通知を出す
        showPushNotification(pm, account, notificationId)
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
        if (ncPushMessage.isDissabled(context)) {
            log.w("ncPushMessage isDissabled.")
            return
        }

        val density = context.resources.displayMetrics.density
        val iconAndColor = pm.notificationIconAndColor()

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
            data = "subwaytooter://notification_click/?$params".toUri()
            // FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY を付与してはいけない
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val piTap = PendingIntent.getActivity(
            context,
            ncPushMessage.pircTap,
            iTap,
            PendingIntent.FLAG_IMMUTABLE
        )

        val urlDelete = "${ncPushMessage.uriPrefixDelete}/${pm.id}"
        val iDelete = context.intentNotificationDelete(urlDelete.toUri())
        val piDelete =
            PendingIntent.getBroadcast(
                context,
                ncPushMessage.pircDelete,
                iDelete,
                PendingIntent.FLAG_IMMUTABLE
            )

        //  val iTap = intentActMessage(pm.messageDbId)
        // val piTap = PendingIntent.getActivity(this, nc.pircTap, iTap, PendingIntent.FLAG_IMMUTABLE)

        ncPushMessage.notify(context, urlDelete) {
            color = iconAndColor.color
            setSmallIcon(iconSmall)
            iconBitmapLarge?.let { setLargeIcon(it) }
            setContentTitle(pm.loginAcct)
            setContentText(pm.text)
            setWhen(pm.timestamp)
            setContentIntent(piTap)
            setDeleteIntent(piDelete)
            setAutoCancel(true)
            pm.textExpand.notEmpty()?.let {
                setStyle(NotificationCompat.BigTextStyle().bigText(it))
            }
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
