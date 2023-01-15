package jp.juggler.subwaytooter.notification

import android.app.ActivityManager
import android.content.Context
import androidx.work.*
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.pref.PrefDevice
import jp.juggler.subwaytooter.pref.PrefS
import jp.juggler.util.log.LogCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit
import kotlin.math.max

/*
- WorkManagerのWorker。
- アカウント別にuniqueWorkNameを持つ。
- アプリが背面にいる間は進捗表示を通知で行う。
*/
class PollingWorker2(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {

    companion object {
        private val log = LogCategory("PollingWorker")
        private const val KEY_ACCOUNT_DB_ID = "accountDbId"

        private const val NOTIFICATION_ID_POLLING_WORKER = 2

        private const val WORK_NAME = "PollingWorker2"

        suspend fun cancelPolling(context: Context) {
            val isOk = WorkManager.getInstance(context)
                .cancelUniqueWork(WORK_NAME).await()
            log.i("cancelPolling isOk=$isOk")
        }

        suspend fun enqueuePolling(
            context: Context,
        ) {
            val workManager = WorkManager.getInstance(context)

            val prefDevice = PrefDevice.from(context)

            val newInterval = max(
                PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS,
                (PrefS.spPullNotificationCheckInterval().toLongOrNull() ?: 0L) * 60000L,
            )

            // すでに同じインターバルのが存在するなら何もしない
            if (workManager.getWorkInfosForUniqueWork(WORK_NAME).await().any {
                    val oldInterval =
                        prefDevice.getLong(PrefDevice.KEY_POLLING_WORKER2_INTERVAL, 0L)
                    oldInterval == newInterval && !it.state.isFinished
                }) {
                return
            }

            // 登録し直す

            val data = Data.Builder().build()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<PollingWorker2>(
                newInterval,
                TimeUnit.MILLISECONDS,
                // flexTimeInterval
                // 決まった周期の間の末尾からflexTimeIntervalを引いた時刻の間の何処かで処理が実行される
                // (周期より短い範囲で)大きい値の方が「より早いタイミングで」実行されてテストに良い
                // (また、setInitialDelayはその何処かの範囲にないと効果がない)
                newInterval - 1000L,
                TimeUnit.MILLISECONDS,
            )
                .setInitialDelay(1000L, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .setInputData(data)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            ).await()

            prefDevice.edit().putLong(PrefDevice.KEY_POLLING_WORKER2_INTERVAL, newInterval).apply()
        }
    }

    private fun isAppForehround(): Boolean {
        val processInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(processInfo)
        return processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private suspend fun showMessage(text: String) =
        CheckerNotification.showMessage(applicationContext, text) {
            try {
                setForegroundAsync(ForegroundInfo(NOTIFICATION_ID_POLLING_WORKER, it))
                    .await()
            } catch (ex: Throwable) {
                log.e(ex, "showMessage failed.")
            }
        }

    private fun stateMapToString(map: Map<PollingState, List<String>>) =
        StringBuilder().apply {
            for (state in PollingState.valuesCache) {
                val list = map[state] ?: continue
                if (isNotEmpty()) append(" |")
                append(state.desc)
                append(": ")
                if (list.size <= 2) {
                    append(list.sorted().joinToString(", "))
                } else {
                    append("${list.size}")
                }
            }
        }.toString()

    private suspend fun workImpl() {
        val context = applicationContext
        coroutineScope {
            if (importProtector.get()) {
                log.w("abort by importProtector.")
                return@coroutineScope
            }

            App1.prepare(context, "doWork")
            showMessage(context.getString(R.string.loading_notification_title))

            checkNoticifationAll(context, "") { map ->
                showMessage(stateMapToString(map))
            }
        }
    }

    override suspend fun doWork(): Result {
        try {
            workImpl()
        } catch (ex: Throwable) {
            when (ex) {
                is CancellationException -> log.e("doWork cancelled.")
                else -> log.e(ex, "doWork failed.")
            }
        }
        return Result.success()
    }
}
