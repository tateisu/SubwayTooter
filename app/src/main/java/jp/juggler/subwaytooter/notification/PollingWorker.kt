package jp.juggler.subwaytooter.notification

import android.content.Context
import androidx.work.*
import jp.juggler.subwaytooter.R
import jp.juggler.util.LogCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit

/*
- WorkManagerのWorker。
- アカウント別にuniqueWorkNameを持つ。
- アプリが背面にいる間は進捗表示を通知で行う。
*/
class PollingWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {

    companion object {
        private val log = LogCategory("PollingWorker")
        private const val KEY_ACCOUNT_DB_ID = "accountDbId"

        private const val NOTIFICATION_ID_POLLING_WORKER = 2

        private fun workName(accountDbId: Long) =
            "PollingForegrounder-$accountDbId"

        suspend fun cancelPolling(context: Context, accountDbId: Long) {
            val isOk = WorkManager.getInstance(context)
                .cancelUniqueWork(workName(accountDbId)).await()
            log.i("cancelPolling $accountDbId isOk=$isOk")
        }

        suspend fun enqueuePolling(
            context: Context,
            accountDbId: Long,
            existingPeriodicWorkPolicy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.REPLACE,
        ) {
            val uniqueWorkName = workName(accountDbId)

            val data = Data.Builder().apply {
                putLong(KEY_ACCOUNT_DB_ID, accountDbId)
            }.build()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<PollingWorker>(
                PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS,
                // flexTimeInterval
                // 決まった周期の間の末尾からflexTimeIntervalを引いた時刻の間の何処かで処理が実行される
                // (周期より短い範囲で)大きい値の方が「より早いタイミングで」実行されてテストに良い
                // (また、setInitialDelayはその何処かの範囲にないと効果がない)
                PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS - 1000L,
                TimeUnit.MILLISECONDS,
            )
                .setInitialDelay(1000L, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .setInputData(data)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    uniqueWorkName,
                    existingPeriodicWorkPolicy,
                    workRequest
                ).await()
        }
    }

    private suspend fun showMessage(text: String) =
        CheckerNotification.showMessage(applicationContext, text) {
            setForeground(ForegroundInfo(NOTIFICATION_ID_POLLING_WORKER, it))
        }

    override suspend fun doWork(): Result = coroutineScope {
        try {
            val accountDbId = inputData.getLong(KEY_ACCOUNT_DB_ID, -1L)
            log.i("doWork start. accountDbId=$accountDbId")

            val context = applicationContext
            showMessage(context.getString(R.string.loading_notification_title))

            PollingChecker(
                context = context,
                accountDbId = accountDbId,
            ) { showMessage(it) }.check()

            Result.success()
        } catch (ex: Throwable) {
            if (ex is CancellationException) {
                log.e("doWork cancelled.")
            } else {
                log.trace(ex, "doWork failed.")
            }
            Result.success()
        }
    }
}
