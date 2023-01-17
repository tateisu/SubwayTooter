package jp.juggler.subwaytooter.api

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.data.clip
import jp.juggler.util.ui.ProgressDialogEx
import jp.juggler.util.ui.dismissSafe
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
interface ApiTask2 {
    val isActive: Any

    companion object {
        val defaultProgressSetupCallback: (progress: ProgressDialogEx) -> Unit = { }

        const val PROGRESS_NONE = -1
        const val PROGRESS_SPINNER = ProgressDialogEx.STYLE_SPINNER
        const val PROGRESS_HORIZONTAL = ProgressDialogEx.STYLE_HORIZONTAL
    }
}

private class TootTaskRunner2<ReturnType : Any?>(
    context: Context,
    private val progressStyle: Int = ApiTask.PROGRESS_SPINNER,
    private val progressPrefix: String? = null,
    private val progressSetupCallback: (progress: ProgressDialogEx) -> Unit = ApiTask.defaultProgressSetupCallback,
) : TootApiCallback, ApiTask {

    companion object {

        // private val log = LogCategory("TootTaskRunner")

        // caller will be in launchMain{} coroutine.
        suspend fun <T : Any?, A : Context> runApiTask(
            context: A,
            accessInfo: SavedAccount? = null,
            apiHost: Host? = null,
            progressStyle: Int = ApiTask.PROGRESS_SPINNER,
            progressPrefix: String? = null,
            progressSetup: (progress: ProgressDialogEx) -> Unit = ApiTask.defaultProgressSetupCallback,
            backgroundBlock: suspend A.(client: TootApiClient) -> T,
        ) = withContext(AppDispatchers.MainImmediate) {
            TootTaskRunner2<T>(
                context = context,
                progressStyle = progressStyle,
                progressPrefix = progressPrefix,
                progressSetupCallback = progressSetup
            ).run {
                accessInfo?.let { client.account = it }
                apiHost?.let { client.apiHost = it }
                try {
                    openProgress()
                    supervisorScope {
                        async(AppDispatchers.IO) {
                            backgroundBlock(context, client)
                        }.also {
                            task = it
                        }.await()
                    }
                } finally {
                    dismissProgress()
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
    private var task: Deferred<ReturnType>? = null
    private val refContext = WeakReference(context)
    private var lastMessageShown = 0L

    private val procProgressMessage = Runnable {
        if (progress?.isShowing == true) showProgressMessage()
    }

    override val isActive: Boolean
        get() = task?.isActive ?: true // nullはまだ開始してないのでアクティブということにする

    fun cancel() {
        task?.cancel()
    }

    //////////////////////////////////////////////////////
    // implements TootApiClient.Callback

    override suspend fun isApiCancelled() = task?.isActive == false

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
        if (progressStyle != ApiTask.PROGRESS_NONE) {
            val context = refContext.get()
            if (context != null && context is Activity) {
                val progress = ProgressDialogEx(context)
                this.progress = progress
                progress.setCancelable(true)
                progress.setOnCancelListener { task?.cancel() }
                @Suppress("DEPRECATION")
                progress.setProgressStyle(progressStyle)
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
            val progressPrefix = this.progressPrefix
            progress.setMessageEx(
                when {
                    progressPrefix?.isNotEmpty() != true -> message
                    message.isEmpty() -> progressPrefix
                    else -> "$progressPrefix\n$message"
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

            lastMessageShown = SystemClock.elapsedRealtime()
        }
    }

    // 少し後にダイアログのメッセージを更新する
    // あまり頻繁に更新せず、しかし繰り返し呼ばれ続けても時々は更新したい
    // どのスレッドから呼ばれるか分からない
    private fun delayProgressMessage() {
        var wait = 100L + lastMessageShown - SystemClock.elapsedRealtime()
        wait = wait.clip(0L, 100L)

        synchronized(this) {
            handler.removeCallbacks(procProgressMessage)
            handler.postDelayed(procProgressMessage, wait)
        }
    }
}

suspend fun <T : Any?,A : Context> A.runApiTask2(
    accessInfo: SavedAccount,
    progressStyle: Int = ApiTask.PROGRESS_SPINNER,
    progressPrefix: String? = null,
    progressSetup: (progress: ProgressDialogEx) -> Unit = ApiTask.defaultProgressSetupCallback,
    backgroundBlock: suspend A.(client: TootApiClient) -> T,
) = TootTaskRunner2.runApiTask(
    this,
    accessInfo,
    null,
    progressStyle,
    progressPrefix,
    progressSetup,
    backgroundBlock
)

suspend fun <T : Any?, A : Context> A.runApiTask2(
    apiHost: Host,
    progressStyle: Int = ApiTask.PROGRESS_SPINNER,
    progressPrefix: String? = null,
    progressSetup: (progress: ProgressDialogEx) -> Unit = ApiTask.defaultProgressSetupCallback,
    backgroundBlock: suspend A.(client: TootApiClient) -> T,
) = TootTaskRunner2.runApiTask(
    this,
    null,
    apiHost,
    progressStyle,
    progressPrefix,
    progressSetup,
    backgroundBlock
)

suspend fun <T : Any?, A : Context> A.runApiTask2(
    progressStyle: Int = ApiTask.PROGRESS_SPINNER,
    progressPrefix: String? = null,
    progressSetup: (progress: ProgressDialogEx) -> Unit = ApiTask.defaultProgressSetupCallback,
    backgroundBlock: suspend A.(client: TootApiClient) -> T,
) = TootTaskRunner2.runApiTask(
    this,
    null,
    null,
    progressStyle,
    progressPrefix,
    progressSetup,
    backgroundBlock
)
