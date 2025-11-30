package jp.juggler.subwaytooter.push

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.*
import jp.juggler.subwaytooter.ActMain
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

        private val ncPushWorker = NotificationChannels.PushWorker

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

        fun enqueueUpEndpoint(context: Context, endpoint: String) =
            workDataOf(
                KEY_ACTION to ACTION_UP_ENDPOINT,
                KEY_ENDPOINT to endpoint,
            ).launchPushWorker(context, ACTION_UP_ENDPOINT)

        fun enqueueRegisterEndpoint(context: Context, keepAliveMode: Boolean = false) =
            workDataOf(
                KEY_ACTION to ACTION_REGISTER_ENDPOINT,
                KEY_KEEP_ALIVE_MODE to keepAliveMode,
            ).launchPushWorker(context, ACTION_REGISTER_ENDPOINT)

        fun enqueuePushMessage(context: Context, messageId: Long) =
            workDataOf(
                KEY_ACTION to ACTION_MESSAGE,
                KEY_MESSAGE_ID to messageId,
            ).launchPushWorker(context, ACTION_MESSAGE)

        fun Data.launchPushWorker(context: Context, tag: String? = null): Operation {
            // EXPEDITED だと制約の種類が限られる
            // すぐ起動してほしいので制約は少なめにする
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<PushWorker>().apply {
                setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                setConstraints(constraints)
                setInputData(this@launchPushWorker)
                tag?.let { addTag(it) }
            }

            val operation = WorkManager.getInstance(context).enqueue(request.build())
            log.i("enqueued!")
            return operation
        }
    }

    override suspend fun doWork(): Result = try {
        createForegroundInfo()?.let { setForegroundAsync(it) }
        withContext(AppDispatchers.IO) {
            val pushRepo = applicationContext.pushRepo
            when (val action = inputData.getString(KEY_ACTION)) {
                ACTION_UP_ENDPOINT -> {
                    timeStartUpEndpoint.set(System.currentTimeMillis())
                    try {
                        val endpoint = inputData.getString(KEY_ENDPOINT)
                            ?.notEmpty() ?: error("missing endpoint.")
                        pushRepo.newUpEndpoint(endpoint)
                    } finally {
                        timeEndUpEndpoint.set(System.currentTimeMillis())
                    }
                }
                ACTION_REGISTER_ENDPOINT -> {
                    timeStartRegisterEndpoint.set(System.currentTimeMillis())
                    try {
                        val keepAliveMode = inputData.getBoolean(KEY_KEEP_ALIVE_MODE, false)
                        pushRepo.registerEndpoint(keepAliveMode)
                    } finally {
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

    /**
     * 時々OSに呼ばれる
     * Android 11 moto g31 で発生
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(force = true)!!
    }

    private fun createForegroundInfo(force: Boolean = false): ForegroundInfo? {
        val context = applicationContext

        // 通知タップ時のPendingIntent
        val iTap = Intent(context, ActMain::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val piTap = PendingIntent.getActivity(
            context,
            ncPushWorker.pircTap,
            iTap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return ncPushWorker.createForegroundInfo(
            context,
            piTap = piTap,
            force = force,
        )
    }
}
