package jp.juggler.subwaytooter.api

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.dialog.ProgressDialogEx
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.*
import kotlinx.coroutines.*
import java.lang.Runnable
import java.lang.ref.WeakReference
import java.text.NumberFormat

/*
	APIクライアントを必要とする非同期タスク(TootTask)を実行します。
	- ProgressDialogを表示します。抑制することも可能です。
	- TootApiClientの初期化を行います
	- TootApiClientからの進捗イベントをProgressDialogに伝達します。
*/
interface ApiTask {
    val isActive: Any
    companion object {
        val defaultProgressSetupCallback: (progress: ProgressDialogEx) -> Unit = { }

        const val PROGRESS_NONE = -1
        const val PROGRESS_SPINNER = ProgressDialogEx.STYLE_SPINNER
        const val PROGRESS_HORIZONTAL = ProgressDialogEx.STYLE_HORIZONTAL
    }
}

private class TootTaskRunner(
    context: Context,
    private val progress_style: Int = ApiTask.PROGRESS_SPINNER,
    private val progress_prefix: String? = null,
    private val progressSetupCallback: (progress: ProgressDialogEx) -> Unit = ApiTask.defaultProgressSetupCallback
) : TootApiCallback, ApiTask {

    companion object {

        // private val log = LogCategory("TootTaskRunner")

        // caller will be in launchMain{} coroutine.
        suspend fun <A : Context> runApiTask(
            context: A,
            accessInfo: SavedAccount? = null,
            apiHost: Host? = null,
            progressStyle: Int = ApiTask.PROGRESS_SPINNER,
            progressPrefix: String? = null,
            progressSetup: (progress: ProgressDialogEx) -> Unit = ApiTask.defaultProgressSetupCallback,
            backgroundBlock: suspend A.(client: TootApiClient) -> TootApiResult?,
        ): TootApiResult? {
            if (!isMainThread) error("runApiTask: not main thread")
            val runner = TootTaskRunner(
                context = context,
                progress_style = progressStyle,
                progress_prefix = progressPrefix,
                progressSetupCallback = progressSetup
            )
            return runner.run {
                accessInfo?.let { client.account = it }
                apiHost?.let { client.apiHost = it }
                withContext(SupervisorJob() + Dispatchers.Main) {
                    try {
                        openProgress()
                        asyncIO{
                            backgroundBlock(context, client)
                        }.also {
                            task = it
                        }.await()
                    } catch (ex: CancellationException) {
                        null
                    } catch (ex: Throwable) {
                        TootApiResult(ex.withCaption("error"))
                    } finally {
                        dismissProgress()
                    }
                }
            }
        }

        private val percent_format: NumberFormat by lazy {
            val v = NumberFormat.getPercentInstance()
            v.maximumFractionDigits = 0
            v
        }
    }

    private class ProgressInfo {

        // HORIZONTALスタイルの場合、初期メッセージがないと後からメッセージを指定しても表示されない
        var message = " "
        var isIndeterminate = true
        var value = 0
        var max = 1
    }

    val client = TootApiClient(context, callback = this)

    private val handler = App1.getAppState(context, "TootTaskRunner.ctor").handler
    private val info = ProgressInfo()
    private var progress: ProgressDialogEx? = null
    private var task: Deferred<TootApiResult?>? = null
    private val refContext = WeakReference(context)
    private var last_message_shown: Long = 0

    private val proc_progress_message = Runnable {
        if (progress?.isShowing == true) showProgressMessage()
    }

    override val isActive: Boolean
        get() = task?.isActive ?: true // nullはまだ開始してないのでアクティブということにする

    fun cancel() {
        task?.cancel()
    }

    //////////////////////////////////////////////////////
    // implements TootApiClient.Callback

    override val isApiCancelled: Boolean
        get() = task?.isActive == false

    override suspend fun publishApiProgress(s: String) {
        synchronized(this) {
            info.message = s
            info.isIndeterminate = true
        }
        delayProgressMessage()
    }

    override suspend fun publishApiProgressRatio(value: Int, max: Int) {
        synchronized(this) {
            info.isIndeterminate = false
            info.value = value
            info.max = max
        }
        delayProgressMessage()
    }

    //////////////////////////////////////////////////////
    // ProgressDialog

    private fun openProgress() {
        // open progress
        if (progress_style != ApiTask.PROGRESS_NONE) {
            val context = refContext.get()
            if (context != null && context is Activity) {
                val progress = ProgressDialogEx(context)
                this.progress = progress
                progress.setCancelable(true)
                progress.setOnCancelListener { task?.cancel() }
                @Suppress("DEPRECATION")
                progress.setProgressStyle(progress_style)
                progressSetupCallback(progress)
                showProgressMessage()
                progress.show()
            }
        }
    }

    // ダイアログを閉じる
    private fun dismissProgress() {
        progress?.dismissSafe()
        progress = null
    }

    // ダイアログのメッセージを更新する
    // 初期化時とメッセージ更新時に呼ばれる
    @Suppress("DEPRECATION")
    private fun showProgressMessage() {
        val progress = this.progress ?: return

        synchronized(this) {
            val message = info.message.trim { it <= ' ' }
            val progress_prefix = this.progress_prefix
            progress.setMessageEx(
                when {
                    progress_prefix?.isNotEmpty() != true -> message
                    message.isEmpty() -> progress_prefix
                    else -> "$progress_prefix\n$message"
                }
            )

            progress.isIndeterminateEx = info.isIndeterminate
            if (info.isIndeterminate) {
                progress.setProgressNumberFormat(null)
                progress.setProgressPercentFormat(null)
            } else {
                progress.progress = info.value
                progress.max = info.max
                progress.setProgressNumberFormat("%1$,d / %2$,d")
                progress.setProgressPercentFormat(percent_format)
            }

            last_message_shown = SystemClock.elapsedRealtime()
        }
    }

    // 少し後にダイアログのメッセージを更新する
    // あまり頻繁に更新せず、しかし繰り返し呼ばれ続けても時々は更新したい
    // どのスレッドから呼ばれるか分からない
    private fun delayProgressMessage() {
        var wait = 100L + last_message_shown - SystemClock.elapsedRealtime()
        wait = wait.clip(0L, 100L)

        synchronized(this) {
            handler.removeCallbacks(proc_progress_message)
            handler.postDelayed(proc_progress_message, wait)
        }
    }
}

suspend fun <A : Context> A.runApiTask(
    accessInfo: SavedAccount,
    progressStyle: Int = ApiTask.PROGRESS_SPINNER,
    progressPrefix: String? = null,
    progressSetup: (progress: ProgressDialogEx) -> Unit = ApiTask.defaultProgressSetupCallback,
    backgroundBlock: suspend A.(client: TootApiClient) -> TootApiResult?,
) = TootTaskRunner.runApiTask(this, accessInfo, null, progressStyle, progressPrefix, progressSetup, backgroundBlock)

suspend fun <A : Context> A.runApiTask(
    apiHost: Host,
    progressStyle: Int = ApiTask.PROGRESS_SPINNER,
    progressPrefix: String? = null,
    progressSetup: (progress: ProgressDialogEx) -> Unit = ApiTask.defaultProgressSetupCallback,
    backgroundBlock: suspend A.(client: TootApiClient) -> TootApiResult?,
) = TootTaskRunner.runApiTask(this, null, apiHost, progressStyle, progressPrefix, progressSetup,  backgroundBlock)

suspend fun <A : Context> A.runApiTask(
    progressStyle: Int = ApiTask.PROGRESS_SPINNER,
    progressPrefix: String? = null,
    progressSetup: (progress: ProgressDialogEx) -> Unit = ApiTask.defaultProgressSetupCallback,
    backgroundBlock: suspend A.(client: TootApiClient) -> TootApiResult?,
) = TootTaskRunner.runApiTask(this, null, null, progressStyle, progressPrefix, progressSetup, backgroundBlock)
