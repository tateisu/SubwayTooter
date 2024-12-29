package jp.juggler.subwaytooter.notification

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.await
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.notification.PullNotification.removeMessageNotification
import jp.juggler.subwaytooter.table.*
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.coroutine.EmptyScope
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.coroutine.launchDefault
import jp.juggler.util.data.ellipsizeDot3
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory
import jp.juggler.util.systemService
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.withLock
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

fun AppCompatActivity.resetNotificationTracking(account: SavedAccount) {
    if (importProtector.get()) {
        log.w("resetNotificationTracking: abort by importProtector.")
        return
    }
    launchAndShowError {
        withContext(AppDispatchers.IO) {
            daoNotificationShown.cleayByAcct(account.acct)
            PollingChecker.accountMutex(account.db_id).withLock {
                daoNotificationTracking.resetTrackingState(account.db_id)
            }
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
    EmptyScope.launch {
        try {
            if (importProtector.get()) {
                log.w("restartAllWorker: abort by importProtector.")
                return@launch
            }
            daoNotificationTracking.resetPostAll()
            App1.prepare(context, "restartAllWorker")
            PollingWorker2.enqueuePolling(context)
        } catch (ex: Throwable) {
            log.e(ex, "restartAllWorker failed.")
        }
    }
}

fun onNotificationCleared(accountDbId: Long) {
    EmptyScope.launch {
        try {
            if (importProtector.get()) {
                log.w("onNotificationCleared: abort by importProtector.")
                return@launch
            }
            PollingChecker.accountMutex(accountDbId).withLock {
                log.d("deleteCacheData: db_id=$accountDbId")
                daoSavedAccount.loadAccount(accountDbId) ?: return@withLock
                daoNotificationCache.deleteCache(accountDbId)
            }
        } catch (ex: Throwable) {
            log.e(ex, "onNotificationCleared failed.")
        }
    }
}

suspend fun onNotificationDeleted(dbId: Long, typeName: String) {
    if (importProtector.get()) {
        log.w("onNotificationDeleted: abort by importProtector.")
        return
    }
    PollingChecker.accountMutex(dbId).withLock {
        daoSavedAccount.loadAccount(dbId)?.let {
            daoNotificationTracking.updateRead(dbId, typeName)
        }
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
    EmptyScope.launch {
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
            log.e(ex, "checkNotificationImmediate failed.")
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
 *
 * @param onlyEnqueue Workerの定期実行ON/OFFの更新だけを行う
 */
suspend fun checkNoticifationAll(
    context: Context,
    logPrefix: String,
    onlyEnqueue: Boolean = false,
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
        if (s != PollingState.Complete) log.i("$logPrefix[${a.acct.pretty}]${s.desc}")
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
            log.e(ex, "updateStatus")
        }
    }

    daoSavedAccount.loadRealAccounts().mapNotNull { sa ->
        when {
            sa.isPseudo || !sa.isConfirmed -> null
            !sa.isRequiredPullCheck() -> {
                // 通知チェックの定期実行が不要なら
                // 通知表示のエラーをクリアする
                daoAccountNotificationStatus.updateNotificationError(
                    sa.acct,
                    null
                )
                null
            }

            else -> EmptyScope.launch(AppDispatchers.DEFAULT) {
                try {
                    PollingChecker(
                        context = context,
                        accountDbId = sa.db_id,
                    ).check(
                        checkNetwork = false,
                        onlyEnqueue = onlyEnqueue,
                    ) { a, s -> updateStatus(a, s) }
                    updateStatus(sa, PollingState.Complete)
                } catch (ex: Throwable) {
                    log.e(ex, "updateStatus failed.")
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
        log.e(ex, "checkNoticifationAll failed.")
    }

    if (timeoutAccounts.isNotEmpty()) {
        createServerTimeoutNotification(
            context,
            timeoutAccounts.sorted().joinToString(", ").ellipsizeDot3(256)
        )
    }
    if (!hasError && !nextPollingRequired) {
        PollingWorker2.cancelPolling(context)
    }
}

/**
 * メイン画面のonCreate時に全ての通知をチェックする
 *
 * @param onlyEnqueue Workerの定期実行ON/OFFの更新だけを行う
 */
fun checkNotificationImmediateAll(context: Context, onlyEnqueue: Boolean = false) {
    EmptyScope.launch {
        try {
            if (importProtector.get()) {
                log.w("checkNotificationImmediateAll: abort by importProtector.")
                return@launch
            }
            App1.prepare(context, "checkNotificationImmediateAll")
            checkNoticifationAll(
                context,
                "(ImmediateAll)",
                onlyEnqueue = onlyEnqueue
            )
        } catch (ex: Throwable) {
            log.e(ex, "checkNotificationImmediateAll failed.")
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

            // アカウントの存在確認
            daoSavedAccount.loadAccount(dbId)?.acct
                ?: error("missing account. dbId=$dbId")

            PollingChecker.accountMutex(dbId).withLock {
                daoNotificationTracking.updateRead(dbId, typeName)
            }
        } catch (ex: Throwable) {
            log.e(ex, "recycleClickedNotification failed.")
        }
    }
}
