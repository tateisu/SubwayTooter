package jp.juggler.subwaytooter.notification

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import com.google.firebase.messaging.FirebaseMessaging
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.table.*
import jp.juggler.subwaytooter.util.*
import jp.juggler.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.tasks.await
import okhttp3.Request
import ru.gildor.coroutines.okhttp.await
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlin.math.max


class PollingWorker private constructor(contextArg: Context) {

    companion object {

        val log = LogCategory("PollingWorker")

        // private const val FCM_SENDER_ID = "433682361381"
        // private const val FCM_SCOPE = "FCM"

        const val NOTIFICATION_ID = 1
        const val NOTIFICATION_ID_ERROR = 3

        val mBusyAppDataImportBefore = AtomicBoolean(false)
        val mBusyAppDataImportAfter = AtomicBoolean(false)

        const val EXTRA_DB_ID = "db_id"
        const val EXTRA_TAG = "tag"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_NOTIFICATION_TYPE = "notification_type"
        const val EXTRA_NOTIFICATION_ID = "notificationId"

        const val APP_SERVER = "https://mastodon-msg.juggler.jp"

        val inject_queue = ConcurrentLinkedQueue<InjectData>()


        @SuppressLint("StaticFieldLeak")
        private var sInstance: PollingWorker? = null

        fun getInstance(applicationContext: Context): PollingWorker {
            synchronized(this) {
                return sInstance ?: PollingWorker(applicationContext).also { sInstance = it }
            }
        }

        suspend fun getFirebaseMessagingToken(context: Context): String? {
            val prefDevice = PrefDevice.prefDevice(context)
            // 設定ファイルに保持されていたらそれを使う
            prefDevice
                .getString(PrefDevice.KEY_DEVICE_TOKEN, null)
                ?.notEmpty()?.let { return it }

            // 古い形式
            // return FirebaseInstanceId.getInstance().getToken(FCM_SENDER_ID, FCM_SCOPE)

            // com.google.firebase:firebase-messaging.20.3.0 以降
            // implementation "org.jetbrains.kotlinx:kotlinx-coroutines-play-services:$kotlinx_coroutines_version"
            try {
                val sv = FirebaseMessaging.getInstance().token.await()
                return if (sv.isNullOrBlank()) {
                    log.e("getFirebaseMessagingToken: missing device token.")
                    null
                } else {
                    prefDevice
                        .edit()
                        .putString(PrefDevice.KEY_DEVICE_TOKEN, sv)
                        .apply()
                    sv
                }
            } catch (ex: Throwable) {
                log.trace(ex, "getFirebaseMessagingToken: could not get device token.")
                return null
            }
        }


        // インストールIDを生成する前に、各データの通知登録キャッシュをクリアする
        // トークンがまだ生成されていない場合、このメソッドは null を返します。
        @Suppress("BlockingMethodInNonBlockingContext")
        suspend fun prepareInstallId(
            context: Context,
            job: JobItem? = null
        ): String? {
            val prefDevice = PrefDevice.prefDevice(context)

            var sv = prefDevice.getString(PrefDevice.KEY_INSTALL_ID, null)
            if (sv?.isNotEmpty() == true) return sv

            SavedAccount.clearRegistrationCache()

            try {
                val device_token = getFirebaseMessagingToken(context)
                    ?: return null

                val request = Request.Builder()
                    .url("$APP_SERVER/counter")
                    .build()

                val call = App1.ok_http_client.newCall(request)
                job?.currentCall = WeakReference(call)
                val response = call.await()

                val body = response.body?.string()

                if (!response.isSuccessful || body?.isEmpty() != false) {
                    log.e(
                        TootApiClient.formatResponse(
                            response,
                            "getInstallId: get/counter failed."
                        )
                    )
                    return null
                }

                sv = (device_token + UUID.randomUUID() + body).digestSHA256Base64Url()
                prefDevice.edit().putString(PrefDevice.KEY_INSTALL_ID, sv).apply()

                return sv

            } catch (ex: Throwable) {
                log.trace(ex, "prepareInstallId failed.")
            }
            return null
        }

        //////////////////////////////////////////////////////////////////////
        // タスクの管理

        val task_list = TaskList()

        private fun scheduleJob(context: Context, jobId: JobId) {

            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE)
                as? JobScheduler
                ?: throw NotImplementedError("missing JobScheduler system service")

            val component = ComponentName(context, PollingService::class.java)

            val builder = JobInfo.Builder(jobId.int, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)

            if (jobId == JobId.Polling) {

                val minute = 60000L

                val intervalMillis = max(
                    minute * 5L,
                    minute * Pref.spPullNotificationCheckInterval.toInt(context.pref())
                )

                val flexMillis = max(
                    minute,
                    intervalMillis shr 1
                )

                fun JobInfo.Builder.setPeriodicCompat(intervalMillis: Long, flexMillis: Long) =
                    this.apply {
                        if (Build.VERSION.SDK_INT >= 24) {
                            builder.setPeriodic(intervalMillis, flexMillis)
                        } else {
                            builder.setPeriodic(intervalMillis)
                        }
                    }

                builder
                    .setPeriodicCompat(intervalMillis, flexMillis)
                    .setPersisted(true)

            } else {
                builder
                    .setMinimumLatency(0)
                    .setOverrideDeadline(60000L)
            }
            val jobInfo = builder.build()

            val rv = scheduler.schedule(jobInfo)
            if (rv != JobScheduler.RESULT_SUCCESS) {
                log.w("scheduler.schedule failed. rv=$rv")
            }
        }

        // タスクの追加
        private fun addTask(
            context: Context,
            removeOld: Boolean,
            taskId: TaskId,
            taskDataInitializer: JsonObject.() -> Unit = {}
        ) {
            try {
                task_list.addLast(
                    context,
                    removeOld,
                    jsonObject {
                        taskDataInitializer()
                        put(EXTRA_TASK_ID, taskId.int)
                    }
                )
                scheduleJob(context, JobId.Task)
            } catch (ex: Throwable) {
                log.trace(ex)
            }

        }

        fun queueUpdateNotification(context: Context) {
            addTask(context, true, TaskId.AccountUpdated)
        }

        fun resetNotificationTracking(context: Context, account: SavedAccount) {
            addTask(context, false, TaskId.ResetTrackingState) {
                put(EXTRA_DB_ID, account.db_id)
            }
        }


        fun injectData(
            context: Context,
            account: SavedAccount,
            src: List<TootNotification>
        ) {

            if (src.isEmpty()) return

            inject_queue.add(InjectData(account.db_id, src))
            addTask(context, true, TaskId.DataInjected)
        }

        fun queueNotificationCleared(context: Context, db_id: Long) {
            addTask(context, true, TaskId.Clear) {
                put(EXTRA_DB_ID, db_id)
            }
        }

        private fun JsonObject.decodeNotificationUri(uri: Uri) {
            putNotNull(
                EXTRA_DB_ID,
                uri.getQueryParameter("db_id")?.toLongOrNull()
            )
            putNotNull(
                EXTRA_NOTIFICATION_TYPE,
                uri.getQueryParameter("type")?.notEmpty()
            )
            putNotNull(
                EXTRA_NOTIFICATION_ID,
                uri.getQueryParameter("notificationId")?.notEmpty()
            )
        }

        fun queueNotificationDeleted(context: Context, uri: Uri?) {
            if (uri != null) {
                addTask(context, false, TaskId.NotificationDelete) {
                    decodeNotificationUri(uri)
                }
            }
        }

        fun queueNotificationClicked(context: Context, uri: Uri?) {
            if (uri != null) {
                addTask(context, true, TaskId.NotificationClick) {
                    decodeNotificationUri(uri)
                }
            }
        }

        fun queueAppDataImportBefore(context: Context) {
            mBusyAppDataImportBefore.set(true)
            mBusyAppDataImportAfter.set(true)
            addTask(context, false, TaskId.AppDataImportBefore)
        }

        fun queueAppDataImportAfter(context: Context) {
            addTask(context, false, TaskId.AppDataImportAfter)
        }

        fun queueFCMTokenUpdated(context: Context) {
            addTask(context, true, TaskId.FcmDeviceToken)
        }

        fun queueBootCompleted(context: Context) {
            addTask(context, true, TaskId.BootCompleted)
        }

        fun queuePackageReplaced(context: Context) {
            addTask(context, true, TaskId.PackageReplaced)
        }

        private val job_status = AtomicReference<String>(null)

        var workerStatus: String
            get() = job_status.get()
            set(x) {
                log.d("workerStatus:$x")
                job_status.set(x)
            }

        // IntentServiceが作ったスレッドから呼ばれる
        suspend fun handleFCMMessage(
            context: Context,
            tag: String?,
            progress: (String) -> Unit
        ) {
            log.d("handleFCMMessage: start. tag=$tag")

            val time_start = SystemClock.elapsedRealtime()

            // この呼出でIntentServiceがstartForegroundする
            progress("=>")

            // タスクを追加
            task_list.addLast(
                context,
                true,
                JsonObject().apply {
                    put(EXTRA_TASK_ID, TaskId.FcmMessage.int)
                    if (tag != null) put(EXTRA_TAG, tag)
                }
            )

            progress("==>")

            // 疑似ジョブを開始
            val pw = getInstance(context)

            pw.addJobFCM()

            // 疑似ジョブが終了するまで待機する
            while (true) {
                // ジョブが完了した？
                val now = SystemClock.elapsedRealtime()
                if (!pw.hasJob(JobId.Push)) {
                    log.d("handleFCMMessage: JOB_FCM completed. time=${String.format("%.2f", (now - time_start) / 1000f)}")
                    break
                }

                // ジョブの状況を通知する
                progress(job_status.get() ?: "(null)")

                // 少し待機
                delay(50L)
            }
        }

        fun onAppSettingStop(context: Context) {
            try {
                scheduleJob(context, JobId.Polling)
            } catch (ex: Throwable) {
                ActAppSetting.log.trace(ex, "PollingWorker.scheduleJob failed.")
            }
        }
    }


    val context: Context
    val appState: AppState
    val pref: SharedPreferences
    private val connectivityManager: ConnectivityManager
    val notification_manager: NotificationManager
    private val scheduler: JobScheduler
    private val power_manager: PowerManager?
    private val power_lock: PowerManager.WakeLock
    private val wifi_manager: WifiManager?
    private val wifi_lock: WifiManager.WifiLock

    private val startedJobList = LinkedList<JobItem>()

    private val workerNotifier = Channel<Unit>(capacity = Channel.CONFLATED)

    fun notifyWorker() =
        workerNotifier.trySend(Unit)

    init {
        log.d("init")

        val context = contextArg.applicationContext

        this.context = context

        // クラッシュレポートによると App1.onCreate より前にここを通る場合がある
        // データベースへアクセスできるようにする
        this.appState = App1.prepare(context, "PollingWorker.init")
        this.pref = App1.pref

        this.connectivityManager = systemService(context)
            ?: error("missing ConnectivityManager system service")


        this.notification_manager = systemService(context)
            ?: error("missing NotificationManager system service")

        this.scheduler = systemService(context)
            ?: error("missing JobScheduler system service")

        this.power_manager = systemService(context)
            ?: error("missing PowerManager system service")

        // WifiManagerの取得時はgetApplicationContext を使わないとlintに怒られる
        this.wifi_manager = systemService(context.applicationContext)
            ?: error("missing WifiManager system service")

        power_lock = power_manager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            PollingWorker::class.java.name
        )
        power_lock.setReferenceCounted(false)

        wifi_lock = if (Build.VERSION.SDK_INT >= 29) {
            wifi_manager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                PollingWorker::class.java.name
            )
        } else {
            @Suppress("DEPRECATION")
            wifi_manager.createWifiLock(PollingWorker::class.java.name)
        }

        wifi_lock.setReferenceCounted(false)

        launchDefault { worker() }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquirePowerLock() {
        log.d("acquire power lock...")
        try {
            if (!power_lock.isHeld) {
                power_lock.acquire()
            }
        } catch (ex: Throwable) {
            log.trace(ex)
        }

        try {
            if (!wifi_lock.isHeld) {
                wifi_lock.acquire()
            }
        } catch (ex: Throwable) {
            log.trace(ex)
        }
    }

    private fun releasePowerLock() {
        log.d("release power lock...")
        try {
            if (power_lock.isHeld) {
                power_lock.release()
            }
        } catch (ex: Throwable) {
            log.trace(ex)
        }

        try {
            if (wifi_lock.isHeld) {
                wifi_lock.release()
            }
        } catch (ex: Throwable) {
            log.trace(ex)
        }
    }

    private suspend fun worker() {
        workerStatus = "worker start."
        try {
            suspend fun isActive() = coroutineContext[Job]?.isActive == true
            while (isActive()) {
                while (true) {
                    handleJobItem(synchronized(startedJobList) {
                        for (ji in startedJobList) {
                            if (ji.mJobCancelled_.get()) continue
                            if (ji.mWorkerAttached.compareAndSet(false, true)) {
                                return@synchronized ji
                            }
                        }
                        null
                    } ?: break)
                }
                try {
                    workerNotifier.receive()
                } catch (ex: ClosedReceiveChannelException) {
                }
            }
        } finally {
            workerStatus = "worker end."
        }
    }

    private suspend fun handleJobItem(item: JobItem) {
        try {
            workerStatus = "start job ${item.jobId}"
            acquirePowerLock()
            try {
                item.run(this@PollingWorker)
            } finally {
                releasePowerLock()
            }
        } catch (ex: Throwable) {
            log.trace(ex)
        } finally {
            workerStatus = "end job ${item.jobId}"
        }
    }

    //////////////////////////////////////////////////////////////////////
    // ジョブの管理

    // JobService#onDestroy から呼ばれる
    fun onJobServiceDestroy() {
        log.d("onJobServiceDestroy")

        synchronized(startedJobList) {
            val it = startedJobList.iterator()
            while (it.hasNext()) {
                val item = it.next()
                if (item.jobId == JobId.Push) continue
                it.remove()
                item.cancel(false)
            }
        }
    }

    // JobService#onStartJob から呼ばれる
    fun onStartJob(jobService: JobService, params: JobParameters): Boolean {
        return when (val jobId = JobId.from(params.jobId)) {
            null -> {
                log.e("onStartJob: unknown jobId $params.jobId")
                false
            }
            else -> {
                val item = JobItem(jobId, params, WeakReference(jobService))
                addStartedJob(item, true)
                // return True if your context needs to process the work (on a separate thread).
                // return False if there's no more work to be done for this job.
                true
            }
        }
    }

    // FCMメッセージイベントから呼ばれる
    private fun hasJob(@Suppress("SameParameterValue") jobId: JobId): Boolean {
        synchronized(startedJobList) {
            return startedJobList.any { it.jobId == jobId }
        }
    }

    // FCMメッセージイベントから呼ばれる
    private fun addJobFCM() {
        addStartedJob(JobItem(JobId.Push), false)
    }

    // onStartJobから呼ばれる
    private fun addStartedJob(item: JobItem, bRemoveOld: Boolean) {
        val jobId = item.jobId

        // 同じジョブ番号がジョブリストにあるか？
        synchronized(startedJobList) {
            if (bRemoveOld) {
                val it = startedJobList.iterator()
                while (it.hasNext()) {
                    val itemOld = it.next()
                    if (itemOld.jobId == jobId) {
                        log.w("addJob: jobId=$jobId, old job cancelled.")
                        // 同じジョブをすぐに始めるのだからrescheduleはfalse
                        itemOld.cancel(false)
                        it.remove()
                    }
                }
            }
            log.d("addJob: jobId=$jobId, add to list.")
            startedJobList.add(item)
        }

        workerNotifier.trySend(Unit)
    }


    // JobService#onStopJob から呼ばれる
    // return True to indicate to the JobManager whether you'd like to reschedule this job based on the retry criteria provided at job creation-time.
    // return False to drop the job. Regardless of the value returned, your job must stop executing.
    fun onStopJob(params: JobParameters): Boolean {
        val jobId = JobId.from(params.jobId)

        // 同じジョブ番号がジョブリストにあるか？
        synchronized(startedJobList) {
            startedJobList.removeFirst { it.jobId == jobId }?.let { item ->
                log.w("onStopJob: jobId=${jobId}, set cancel flag.")
                // リソースがなくてStopされるのだからrescheduleはtrue
                item.cancel(true)
                return true // reschedule
            }
        }

        // 該当するジョブを依頼されていない
        log.w("onStopJob: jobId=${jobId}, not started..")
        return false
    }

    fun processInjectedData(injectedAccounts: HashSet<Long>) {
        while (true) {
            val data = inject_queue.poll() ?: break
            val account = SavedAccount.loadAccount(context, data.account_db_id) ?: continue
            val list = data.list
            log.d("${account.acct} processInjectedData +${list.size}")
            if (list.isNotEmpty()) injectedAccounts.add(account.db_id)
            NotificationCache(data.account_db_id).apply {
                load()
                inject(account, list)
            }
        }
    }


    // ポーリングが完了した
    fun onPollingComplete(requiredNextPolling: Boolean) {
        when (requiredNextPolling) {
            // まだスケジュールされてないなら登録する
            true -> if (!scheduler.allPendingJobs.any { it.id == JobId.Polling.int }) {
                log.d("registering next polling…")
                scheduleJob(context, JobId.Polling)
            }
            // Pull通知を必要とするアカウントが存在しないなら、スケジュール登録を解除する
            else -> try {
                log.d("polling job is no longer required.")
                scheduler.cancel(JobId.Polling.int)
            } catch (ex: Throwable) {
                log.trace(ex)
            }
        }
    }

    // ジョブ完了後にメインスレッドで呼ばれる
    fun onJobComplete(item: JobItem) {

        synchronized(startedJobList) {
            startedJobList.remove(item)
        }

        // ジョブ終了報告
        item.refJobService?.get()?.let { jobService ->
            try {
                val willReschedule = item.mReschedule.get()
                log.d("sending jobFinished. willReschedule=$willReschedule")
                jobService.jobFinished(item.jobParams, willReschedule)
            } catch (ex: Throwable) {
                log.trace(ex, "jobFinished failed.")
            }
        }
    }

    // return false if app data import started.
    fun onStartTask(taskId: TaskId): Boolean {
        @Suppress("NON_EXHAUSTIVE_WHEN")
        when (taskId) {
            TaskId.AppDataImportBefore -> {

                // フォアグラウンドサービスの通知は消されないらしい
                notification_manager.cancelAll()

                scheduler.cancelAll()

                mBusyAppDataImportBefore.set(false)
                return false
            }

            TaskId.AppDataImportAfter -> {
                mBusyAppDataImportAfter.set(false)
                mBusyAppDataImportBefore.set(false)
                NotificationTracking.resetPostAll()
                // fall
            }
        }


        // アプリデータのインポート処理がビジーな間、他のジョブは実行されない
        return when {
            mBusyAppDataImportBefore.get() || mBusyAppDataImportAfter.get() -> false
            else -> true
        }
    }
}