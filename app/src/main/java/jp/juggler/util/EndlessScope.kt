package jp.juggler.util

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import jp.juggler.subwaytooter.dialog.ProgressDialogEx
import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private val log = LogCategory("EndlessScope")

val <T : Any> T.wrapWeakReference: WeakReference<T>
    get() = WeakReference(this)

// kotlinx.coroutines 1.5.0 で GlobalScopeがdeprecated になったが、
// プロセスが生きてる間ずっと動いててほしいものや特にキャンセルのタイミングがないコルーチンでは使い続けたい
object EndlessScope : CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = EmptyCoroutineContext
}

// メインスレッド上で動作するコルーチンを起動して、終了を待たずにリターンする。
// 起動されたアクティビティのライフサイクルに関わらず中断しない。
fun launchMain(block: suspend CoroutineScope.() -> Unit): Job =
    EndlessScope.launch(context = Dispatchers.Main.immediate) {
        try {
            block()
        } catch (ex: CancellationException) {
            log.trace(ex, "launchMain: cancelled.")
        }
    }

// Default Dispatcherで動作するコルーチンを起動して、終了を待たずにリターンする。
// 起動されたアクティビティのライフサイクルに関わらず中断しない。
fun launchDefault(block: suspend CoroutineScope.() -> Unit): Job =
    EndlessScope.launch(context = Dispatchers.Default) {
        try {
            block()
        } catch (ex: CancellationException) {
            log.trace(ex, "launchDefault: cancelled.")
        }
    }

// IOスレッド上で動作するコルーチンを起動して、終了を待たずにリターンする。
// 起動されたアクティビティのライフサイクルに関わらず中断しない。
fun launchIO(block: suspend CoroutineScope.() -> Unit): Job =
    EndlessScope.launch(context = Dispatchers.IO) {
        try {
            block()
        } catch (ex: CancellationException) {
            log.trace(ex, "launchIO: cancelled.")
        }
    }

// IOスレッド上で動作するコルーチンを起動して、終了を待たずにリターンする。
// 起動されたアクティビティのライフサイクルに関わらず中断しない。
// asyncの場合キャンセル例外のキャッチは呼び出し側で行う必要がある
@Suppress("DeferredIsResult")
fun <T : Any?> asyncIO(block: suspend CoroutineScope.() -> T): Deferred<T> =
    EndlessScope.async(block = block, context = Dispatchers.IO)

fun AppCompatActivity.launchAndShowError(
    errorCaption: String? = null,
    block: suspend CoroutineScope.() -> Unit,
): Job = lifecycleScope.launch() {
    try {
        block()
    } catch (ex: Throwable) {
        showError(ex, errorCaption)
    }
}

/////////////////////////////////////////////////////////////////////////

suspend fun <T : Any?> AppCompatActivity.runWithProgress(
    caption: String,
    doInBackground: suspend CoroutineScope.(ProgressDialogEx) -> T,
    afterProc: suspend CoroutineScope.(result: T) -> Unit = {},
    progressInitializer: suspend CoroutineScope.(ProgressDialogEx) -> Unit = {},
    preProc: suspend CoroutineScope.() -> Unit = {},
    postProc: suspend CoroutineScope.() -> Unit = {},
) {
    coroutineScope {
        if (!isMainThread) error("runWithProgress: not main thread.")

        val progress = ProgressDialogEx(this@runWithProgress)

        val task = async(Dispatchers.IO) {
            doInBackground(progress)
        }

        launch(Dispatchers.Main) {

            try {
                preProc()
            } catch (ex: Throwable) {
                log.trace(ex)
            }

            progress.setCancelable(true)
            progress.setOnCancelListener { task.cancel() }
            progress.isIndeterminateEx = true
            progress.setMessageEx("$caption…")
            progressInitializer(progress)
            progress.show()

            try {
                val result = try {
                    task.await()
                } catch (ignored: CancellationException) {
                    null
                }
                if (result != null) afterProc(result)
            } catch (ex: Throwable) {
                showToast(ex, "$caption failed.")
            } finally {
                progress.dismissSafe()
                try {
                    postProc()
                } catch (ex: Throwable) {
                    log.trace(ex)
                }
            }
        }
    }
}
