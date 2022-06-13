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
import jp.juggler.subwaytooter.pref.PrefDevice
import jp.juggler.subwaytooter.table.NotificationCache
import jp.juggler.subwaytooter.table.NotificationTracking
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.PrivacyPolicyChecker
import jp.juggler.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import okhttp3.Request
import ru.gildor.coroutines.okhttp.await
import java.util.*

private val log = LogCategory("PollingUtils")

const val APP_SERVER = "https://mastodon-msg.juggler.jp"

class InstallIdException(ex: Throwable?, message: String) :
    RuntimeException(message, ex)

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
            error("getFirebaseMessagingToken: device token is null or empty.")
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
    deviceToken: String,
    progress: suspend (String) -> Unit,
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

    progress("preparing install id…")

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
    launchDefault {
        PollingChecker.accountMutex(account.db_id).withLock {
            NotificationTracking.resetTrackingState(account.db_id)
        }
    }
}

/**
 * アプリ設定インポート時など、全てのWorkをキャンセル済みであることを確認する
 */
suspend fun cancelAllWorkAndJoin(context: Context) {
    val workManager = WorkManager.getInstance(context)
    repeat(3) {
        while (true) {
            workManager.pruneWork()
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
                workManager.cancelWorkById(it.id)
            }
            delay(333L)
        }
        delay(1000L)
    }
}

fun restartAllWorker(context: Context) {
    NotificationTracking.resetPostAll()
    App1.prepare(context, "restartAllWorker")
    EndlessScope.launch {
        for (it in SavedAccount.loadAccountList(context)) {
            try {
                if (it.isPseudo || !it.isConfirmed) continue
                PollingWorker.enqueuePolling(context, it.db_id)
            } catch (ex: Throwable) {
                log.trace(ex, "restartAllWorker failed.")
            }
        }
    }
}

suspend fun onNotificationCleared(context: Context, accountDbId: Long) {
    PollingChecker.accountMutex(accountDbId).withLock {
        log.d("deleteCacheData: db_id=$accountDbId")
        SavedAccount.loadAccount(context, accountDbId) ?: return
        NotificationCache.deleteCache(accountDbId)
    }
}

suspend fun onNotificationDeleted(dbId: Long, typeName: String) {
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
            PollingChecker(
                context = context,
                accountDbId = accountDbId,
                injectData = injectData,
            ) { log.i(it) }.check()
        } catch (ex: Throwable) {
            log.trace(ex, "checkNotificationImmediate failed.")
        }
    }
}

/**
 * メイン画面のonCreate時に全ての通知をチェックする
 */
fun checkNotificationImmediateAll(context: Context) {
    EndlessScope.launch {
        try {
            App1.prepare(context, "checkNotificationImmediateAll")
            for (sa in SavedAccount.loadAccountList(context)) {
                if (sa.isPseudo || !sa.isConfirmed) continue
                PollingChecker(
                    context = context,
                    accountDbId = sa.db_id,
                ) { log.i(it) }.check()
            }
        } catch (ex: Throwable) {
            log.trace(ex, "checkNotificationImmediateAll failed.")
        }
    }
}

fun recycleClickedNotification(context: Context, uri: Uri) {
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
            PollingChecker.accountMutex(dbId).withLock {
                NotificationTracking.updateRead(dbId, typeName)
            }
        } catch (ex: Throwable) {
            log.trace(ex)
        }
    }
}
