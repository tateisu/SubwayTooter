package jp.juggler.subwaytooter.notification

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.await
import com.google.firebase.messaging.FirebaseMessaging
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.notification.MessageNotification.removeMessageNotification
import jp.juggler.subwaytooter.notification.ServerTimeoutNotification.createServerTimeoutNotification
import jp.juggler.subwaytooter.pref.PrefDevice
import jp.juggler.subwaytooter.table.NotificationCache
import jp.juggler.subwaytooter.table.NotificationTracking
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.PrivacyPolicyChecker
import jp.juggler.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import okhttp3.Request
import ru.gildor.coroutines.okhttp.await
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

private val log = LogCategory("PollingUtils")

const val APP_SERVER = "https://mastodon-msg.juggler.jp"

class InstallIdException(ex: Throwable?, message: String) :
    RuntimeException(message, ex)

/**
 * アプリ設定インポート中に通知関連の動作を阻害する
 */
val importProtector = AtomicBoolean(false)

/**
 * アプリ設定インポートの最中にセットされる
 * - 有効な間は通知関連のバックグラウンド動作を行わない
 * -
 */
suspend fun setImportProtector(context: Context, newProtect: Boolean) {
    importProtector.set(newProtect)
    if (newProtect) {
        cancelAllWorkAndJoin(context)
        PollingChecker.cancelAllChecker()
    } else {
        restartAllWorker(context)
    }
}

suspend fun loadFirebaseMessagingToken(context: Context): String =
    PollingChecker.commonMutex.withLock {
        val prefDevice = PrefDevice.from(context)

        // 設定ファイルに保持されていたらそれを使う
        prefDevice.getString(PrefDevice.KEY_DEVICE_TOKEN, null)
            ?.notEmpty()?.let { return it }

        // 古い形式
        // return FirebaseInstanceId.getInstance().getToken(FCM_SENDER_ID, FCM_SCOPE)

        // com.google.firebase:firebase-messaging.20.3.0 以降
        // implementation "org.jetbrains.kotlinx:kotlinx-coroutines-play-services:$kotlinx_coroutines_version"
        val sv = FirebaseMessaging.getInstance().token.await()
        if (sv.isNullOrBlank()) {
            error("loadFirebaseMessagingToken: device token is null or empty.")
        }
        return sv.also {
            prefDevice.edit()
                .putString(PrefDevice.KEY_DEVICE_TOKEN, it)
                .apply()
        }
    }

// インストールIDを生成する前に、各データの通知登録キャッシュをクリアする
// トークンがまだ生成されていない場合、このメソッドは null を返します。
@Suppress("BlockingMethodInNonBlockingContext")
suspend fun loadInstallId(
    context: Context,
    account: SavedAccount,
    deviceToken: String,
    progress: suspend (SavedAccount, PollingState) -> Unit,
): String = PollingChecker.commonMutex.withLock {
    // インストールIDを生成する
    // インストールID生成時にSavedAccountテーブルを操作することがあるので
    // アカウントリストの取得より先に行う
    if (!PrivacyPolicyChecker(context).agreed) {
        cancelAllWorkAndJoin(context)
        throw InstallIdException(null,
            "the user not agreed to privacy policy.")
    }

    val prefDevice = PrefDevice.from(context)

    prefDevice.getString(PrefDevice.KEY_INSTALL_ID, null)
        ?.notEmpty()?.let { return it }

    progress(account, PollingState.PrepareInstallId)

    SavedAccount.clearRegistrationCache()

    val request = Request.Builder()
        .url("$APP_SERVER/counter")
        .build()

    val response = App1.ok_http_client.newCall(request).await()
    val body = response.body?.string()
    if (!response.isSuccessful || body?.isEmpty() != false) {
        TootApiClient.formatResponse(
            response,
            "loadInstallId: get/counter failed."
        ).let { throw InstallIdException(null, it) }
    }
    (deviceToken + UUID.randomUUID() + body).digestSHA256Base64Url()
        .also { prefDevice.edit().putString(PrefDevice.KEY_INSTALL_ID, it).apply() }
}

fun resetNotificationTracking(account: SavedAccount) {
    if (importProtector.get()) {
        log.w("resetNotificationTracking: abort by importProtector.")
        return
    }
    launchDefault {
        PollingChecker.accountMutex(account.db_id).withLock {
            NotificationTracking.resetTrackingState(account.db_id)
        }
    }
}

/**
 * アプリ設定インポート時など、全てのWorkをキャンセル済みであることを確認する
 * 全てのワーカーをキャンセルする
 */
suspend fun cancelAllWorkAndJoin(context: Context) {
    val workManager = WorkManager.getInstance(context)
    repeat(3) {
        while (true) {
            workManager.pruneWork().await()
            val workQuery = WorkQuery.Builder.fromStates(
                listOf(
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.RUNNING,
                    WorkInfo.State.SUCCEEDED,
                    WorkInfo.State.FAILED,
                    WorkInfo.State.BLOCKED,
                    WorkInfo.State.CANCELLED,
                )
            ).build()
            val list = workManager.getWorkInfos(workQuery).await()
            if (list.isEmpty()) break
            list.forEach {
                workManager.cancelWorkById(it.id).await()
            }
            delay(333L)
        }
        delay(1000L)
    }
}

fun restartAllWorker(context: Context) {
    EndlessScope.launch {
        try {
            if (importProtector.get()) {
                log.w("restartAllWorker: abort by importProtector.")
                return@launch
            }
            NotificationTracking.resetPostAll()
            App1.prepare(context, "restartAllWorker")
            PollingWorker2.enqueuePolling(context)
        } catch (ex: Throwable) {
            log.trace(ex, "restartAllWorker failed.")
        }
    }
}

suspend fun onNotificationCleared(context: Context, accountDbId: Long) {
    if (importProtector.get()) {
        log.w("onNotificationCleared: abort by importProtector.")
        return
    }
    PollingChecker.accountMutex(accountDbId).withLock {
        log.d("deleteCacheData: db_id=$accountDbId")
        SavedAccount.loadAccount(context, accountDbId) ?: return
        NotificationCache.deleteCache(accountDbId)
    }
}

suspend fun onNotificationDeleted(dbId: Long, typeName: String) {
    if (importProtector.get()) {
        log.w("onNotificationDeleted: abort by importProtector.")
        return
    }
    PollingChecker.accountMutex(dbId).withLock {
        NotificationTracking.updateRead(dbId, typeName)
    }
}

fun injectData(
    context: Context,
    account: SavedAccount,
    src: List<TootNotification>,
) = checkNotificationImmediate(context, account.db_id, src)

/**
 * すぐにアカウントの通知をチェックする
 */
fun checkNotificationImmediate(
    context: Context,
    accountDbId: Long,
    injectData: List<TootNotification> = emptyList(),
) {
    EndlessScope.launch {
        try {
            if (importProtector.get()) {
                log.w("checkNotificationImmediate: abort by importProtector.")
                return@launch
            }
            PollingChecker(
                context = context,
                accountDbId = accountDbId,
                injectData = injectData,
            ).check { account, state ->
                log.i("(Immediate)[${account.acct.pretty}]${state.desc}")
            }
        } catch (ex: Throwable) {
            log.trace(ex, "checkNotificationImmediate failed.")
        }
    }
}

// K,Vのマップを V,List<K>のマップにする
private fun <K, V> Map<K, V>.trans() = HashMap<V, ArrayList<K>>()
    .also { dst ->
        entries.forEach { (k, v) ->
            dst.getOrPut(v) { ArrayList() }.add(k)
        }
    }

/**
 * 全アカウントの通知チェックを行う
 * -
 */
suspend fun checkNoticifationAll(
    context: Context,
    logPrefix: String,
    onlySubscription: Boolean = false,
    progress: suspend (Map<PollingState, List<String>>) -> Unit = {},
) {
    CheckerWakeLocks.checkerWakeLocks(context).checkConnection()

    var nextPollingRequired = false
    var hasError = false
    val timeoutAccounts = HashSet<String>()
    val statusMap = HashMap<String, PollingState>()

    // 進捗表示
    // 複数アカウントの状態をマップにまとめる
    suspend fun updateStatus(a: SavedAccount, s: PollingState) {
        log.i("$logPrefix[${a.acct.pretty}]${s.desc}")
        when (s) {
            PollingState.CheckNotifications,
            -> nextPollingRequired = true

            PollingState.Cancelled,
            PollingState.Error,
            -> hasError = true

            PollingState.Timeout,
            -> {
                hasError = true
                timeoutAccounts.add(a.acct.pretty)
            }

            else -> Unit
        }
        try {
            val tmpMap = synchronized(statusMap) {
                statusMap[a.acct.pretty] = s
                statusMap.trans()
            }
            progress(tmpMap)
        } catch (ex: Throwable) {
            log.trace(ex)
        }
    }

    SavedAccount.loadAccountList(context).mapNotNull { sa ->
        when {
            sa.isPseudo || !sa.isConfirmed -> null
            else -> EndlessScope.launch(Dispatchers.Default) {
                try {
                    PollingChecker(
                        context = context,
                        accountDbId = sa.db_id,
                    ).check(
                        checkNetwork = false,
                        onlySubscription = onlySubscription,
                    ) { a, s -> updateStatus(a, s) }
                    updateStatus(sa, PollingState.Complete)
                } catch (ex: Throwable) {
                    log.trace(ex)
                    val s = when (ex) {
                        is CancellationException -> PollingState.Cancelled
                        else -> PollingState.Error
                    }
                    updateStatus(sa, s)
                }
            }
        }
    }.toTypedArray().let { joinAll(*it) }

    try {
        val tmpMap = statusMap.trans()
        progress(tmpMap)
    } catch (ex: Throwable) {
        log.trace(ex)
    }

    if (timeoutAccounts.isNotEmpty()) {
        createServerTimeoutNotification(context,
            timeoutAccounts.sorted().joinToString(", ").ellipsizeDot3(256))
    }
    if (!hasError && !nextPollingRequired) {
        PollingWorker2.cancelPolling(context)
    }
}

/**
 * メイン画面のonCreate時に全ての通知をチェックする
 */
fun checkNotificationImmediateAll(context: Context, onlySubscription: Boolean = false) {
    EndlessScope.launch {
        try {
            if (importProtector.get()) {
                log.w("checkNotificationImmediateAll: abort by importProtector.")
                return@launch
            }
            App1.prepare(context, "checkNotificationImmediateAll")
            checkNoticifationAll(
                context,
                "(ImmediateAll)",
                onlySubscription = onlySubscription
            )
        } catch (ex: Throwable) {
            log.trace(ex, "checkNotificationImmediateAll failed.")
        }
    }
}

fun recycleClickedNotification(context: Context, uri: Uri) {
    if (importProtector.get()) {
        log.w("recycleClickedNotification: abort by importProtector.")
        return
    }

    val dbId = uri.getQueryParameter("db_id")?.toLongOrNull()
    val type = TrackingType.parseStr(uri.getQueryParameter("type"))
    val typeName = type.typeName
    val id = uri.getQueryParameter("notificationId")?.notEmpty()
    log.d("recycleClickedNotification: db_id=$dbId,type=$type,id=$id")
    if (dbId == null) return

    val notificationManager = systemService<NotificationManager>(context)
    if (notificationManager == null) {
        log.e("missing NotificationManager system service")
        return
    }

    // 通知をキャンセル
    notificationManager.removeMessageNotification(
        id = id,
        tag = when (typeName) {
            "" -> "$dbId/_"
            else -> "$dbId/$typeName"
        },
    )

    // DB更新処理
    launchDefault {
        try {
            if (importProtector.get()) {
                log.w("recycleClickedNotification: abort by importProtector.")
                return@launchDefault
            }
            PollingChecker.accountMutex(dbId).withLock {
                NotificationTracking.updateRead(dbId, typeName)
            }
        } catch (ex: Throwable) {
            log.trace(ex)
        }
    }
}
