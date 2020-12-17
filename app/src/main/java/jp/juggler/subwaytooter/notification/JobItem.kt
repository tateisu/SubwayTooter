package jp.juggler.subwaytooter.notification

import android.app.job.JobParameters
import android.app.job.JobService
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.table.*
import jp.juggler.util.JsonObject
import jp.juggler.util.LogCategory
import jp.juggler.util.WordTrieTree
import jp.juggler.util.runOnMainLooper
import kotlinx.coroutines.*
import okhttp3.Call
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/*
    JobSchedulerに登録する & アプリ内部でも保持するジョブのリスト。
    アプリ内部で保持するのは主にサービス完了通知のせい
* */
class JobItem(
    val jobId: JobId,
    val jobParams: JobParameters? = null,
    val refJobService: WeakReference<JobService>? = null,
) {
    companion object {
        private val log = LogCategory("JobItem")

        private var workerStatus = ""
            set(value) {
                field = value
                PollingWorker.workerStatus = value
            }
    }

    val mJobCancelled_ = AtomicBoolean()
    val mReschedule = AtomicBoolean()
    val mWorkerAttached = AtomicBoolean()

    val bPollingRequired = AtomicBoolean(false)
    lateinit var muted_app: HashSet<String>
    lateinit var muted_word: WordTrieTree
    lateinit var favMuteSet: HashSet<Acct>
    var bPollingComplete = false
    var install_id: String? = null

    var currentCall: WeakReference<Call>? = null

    val isJobCancelled: Boolean
        get() = mJobCancelled_.get()

    // 通知データインジェクションを行ったアカウント
    val injectedAccounts = HashSet<Long>()

    private var pollingWorker: PollingWorker? = null

    fun cancel(bReschedule: Boolean) {
        mJobCancelled_.set(true)
        mReschedule.set(bReschedule)
        currentCall?.get()?.cancel()
        pollingWorker?.notifyWorker()
    }

    suspend fun run(pollingWorker: PollingWorker) = coroutineScope {

        this@JobItem.pollingWorker = pollingWorker

        try {
            log.d("(JobItem.run jobId=${jobId}")

            workerStatus = "check network status.."

            var connectionState: String? = null
            try {
                withTimeout(10000L) {
                    while (true) {
                        if (isJobCancelled) throw JobCancelledException()
                        connectionState = pollingWorker.appState
                            .networkTracker.connectionState
                            ?: break // null if connected
                        delay(333L)
                    }
                }
            } catch (ex: TimeoutCancellationException) {
                log.d("network state timeout. $connectionState")
            }

            muted_app = MutedApp.nameSet
            muted_word = MutedWord.nameSet
            favMuteSet = FavMute.acctSet

            // タスクがあれば処理する

            while (true) {
                if (isJobCancelled) throw JobCancelledException()
                val data = PollingWorker.task_list.next(pollingWorker.context) ?: break
                val taskIdInt = data.optInt(PollingWorker.EXTRA_TASK_ID, -1)
                val taskId = TaskId.from(taskIdInt)
                if (taskId == null) {
                    log.e("JobItem.run(): unknown taskId ${taskIdInt}")
                    continue
                }
                // アプリデータのインポート処理が開始したらジョブを全て削除して処理を中断する
                // アプリデータのインポート処理中は他のジョブは実行されない。
                when {
                    !pollingWorker.onStartTask(taskId) -> return@coroutineScope
                    else -> TaskRunner(pollingWorker, this@JobItem, taskId, data).runTask()
                }
            }

            if (!isJobCancelled && !bPollingComplete && JobId.Polling == jobId) {
                // タスクがなかった場合でも定期実行ジョブからの実行ならポーリングを行う
                TaskRunner(pollingWorker, this@JobItem, TaskId.Polling, JsonObject()).runTask()
            }

            log.w("pollingComplete=${bPollingComplete},isJobCancelled=${isJobCancelled},bPollingRequired=${bPollingRequired.get()}")
            if (!isJobCancelled && bPollingComplete) {
                // ポーリングが完了した
                pollingWorker.onPollingComplete(bPollingRequired.get())
            }
        } catch (ex: JobCancelledException) {
            log.w("job execution cancelled.")
        } catch (ex: Throwable) {
            log.trace(ex)
            log.e(ex, "job execution failed.")
        }

        log.d(")JobItem.run jobId=${jobId}, cancel=${isJobCancelled}")

        // メインスレッドで後処理を行う
        runOnMainLooper {
            if (isJobCancelled) return@runOnMainLooper
            pollingWorker.onJobComplete(this@JobItem)
        }
    }
}
