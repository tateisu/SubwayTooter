package jp.juggler.subwaytooter.push

import android.content.Context
import androidx.work.*
import jp.juggler.subwaytooter.notification.NotificationChannels
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

class PushWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        private val log = LogCategory("PushWorker")

        const val KEY_ACTION = "action"
        const val KEY_ENDPOINT = "endpoint"
        const val KEY_MESSAGE_ID = "messageId"
        const val KEY_KEEP_ALIVE_MODE = "keepAliveMode"

        const val ACTION_UP_ENDPOINT = "upEndpoint"
        const val ACTION_MESSAGE = "message"
        const val ACTION_REGISTER_ENDPOINT = "endpointRegister"

        val timeStartUpEndpoint = AtomicLong(0L)
        val timeEndUpEndpoint = AtomicLong(0L)
        val timeStartRegisterEndpoint = AtomicLong(0L)
        val timeEndRegisterEndpoint = AtomicLong(0L)

        fun enqueueUpEndpoint(context: Context, endpoint: String) {
            workDataOf(
                PushWorker.KEY_ACTION to PushWorker.ACTION_UP_ENDPOINT,
                PushWorker.KEY_ENDPOINT to endpoint,
            ).launchPushWorker(context)
        }

        fun enqueueRegisterEndpoint(context: Context, keepAliveMode: Boolean = false) {
            workDataOf(
                KEY_ACTION to ACTION_REGISTER_ENDPOINT,
                KEY_KEEP_ALIVE_MODE to keepAliveMode,
            ).launchPushWorker(context)
        }

        fun enqueuePushMessage(context: Context, messageId: Long) {
            workDataOf(
                PushWorker.KEY_ACTION to PushWorker.ACTION_MESSAGE,
                PushWorker.KEY_MESSAGE_ID to messageId,
            ).launchPushWorker(context)
        }

        fun Data.launchPushWorker(context: Context) {
            // EXPEDITED だと制約の種類が限られる
            // すぐ起動してほしいので制約は少なめにする
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()

            val request = OneTimeWorkRequestBuilder<PushWorker>().apply {
                setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                setConstraints(constraints)
                setInputData(this@launchPushWorker)
            }
            WorkManager.getInstance(context).enqueue(request.build())
            log.i("enqueued!")
        }
    }

    override suspend fun doWork(): Result = try {
        NotificationChannels.PushWorker.createForegroundInfo(
            applicationContext,
        )?.let{setForegroundAsync(it)}
        withContext(AppDispatchers.IO) {
            val pushRepo = applicationContext.pushRepo
            when (val action = inputData.getString(KEY_ACTION)) {
                ACTION_UP_ENDPOINT -> {
                    timeStartUpEndpoint.set(System.currentTimeMillis())
                    try {
                        val endpoint = inputData.getString(KEY_ENDPOINT)
                            ?.notEmpty() ?: error("missing endpoint.")
                        pushRepo.newUpEndpoint(endpoint)
                    }finally{
                        timeEndUpEndpoint.set(System.currentTimeMillis())
                    }
                }
                ACTION_REGISTER_ENDPOINT -> {
                    timeStartRegisterEndpoint.set(System.currentTimeMillis())
                    try {
                        val keepAliveMode = inputData.getBoolean(KEY_KEEP_ALIVE_MODE, false)
                        pushRepo.registerEndpoint(keepAliveMode)
                    }finally{
                        timeEndRegisterEndpoint.set(System.currentTimeMillis())
                    }
                }
                ACTION_MESSAGE -> {
                    val messageId = inputData.getLong(KEY_MESSAGE_ID, 0L)
                        .takeIf { it != 0L } ?: error("missing message id.")
                    pushRepo.updateMessage(messageId)
                }
                else -> error("invalid action $action")
            }
        }
        Result.success()
    } catch (ex: Throwable) {
        log.e(ex, "doWork failed.")
        Result.failure()
    }
}
