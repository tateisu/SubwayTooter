package jp.juggler.util.coroutine

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showError
import jp.juggler.util.log.showToast
import jp.juggler.util.ui.ProgressDialogEx
import jp.juggler.util.ui.dismissSafe
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private val log = LogCategory("EmptyScope")

// kotlinx.coroutines 1.5.0 で GlobalScopeがdeprecated になったが、
// プロセスが生きてる間ずっと動いててほしいものや特にキャンセルのタイミングがないコルーチンでは使い続けたい
object EmptyScope : CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = EmptyCoroutineContext
}

// メインスレッド上で動作するコルーチンを起動して、終了を待たずにリターンする。
// 起動されたアクティビティのライフサイクルに関わらず中断しない。
fun launchMain(block: suspend CoroutineScope.() -> Unit): Job =
    EmptyScope.launch(context = AppDispatchers.mainImmediate) {
        try {
            block()
        } catch (ex: Throwable) {
            if (ex is CancellationException) {
                log.w("lainchMain cancelled.")
            } else {
                log.e(ex, "launchMain failed.")
            }
        }
    }

// Default Dispatcherで動作するコルーチンを起動して、終了を待たずにリターンする。
// 起動されたアクティビティのライフサイクルに関わらず中断しない。
fun launchDefault(block: suspend CoroutineScope.() -> Unit): Job =
    EmptyScope.launch(context = AppDispatchers.default) {
        try {
            block()
        } catch (ex: Throwable) {
            log.e(ex, "launchDefault failed.")
        }
    }

// IOスレッド上で動作するコルーチンを起動して、終了を待たずにリターンする。
// 起動されたアクティビティのライフサイクルに関わらず中断しない。
fun launchIO(block: suspend CoroutineScope.() -> Unit): Job =
    EmptyScope.launch(context = AppDispatchers.io) {
        try {
            block()
        } catch (ex: Throwable) {
            log.e(ex, "launchIO failed.")
        }
    }

fun AppCompatActivity.launchAndShowError(
    errorCaption: String? = null,
    block: suspend CoroutineScope.() -> Unit,
): Job = lifecycleScope.launch {
    try {
        block()
    } catch (ex: Throwable) {
        showError(ex, errorCaption)
    }
}

/////////////////////////////////////////////////////////////////////////

fun <T : Any?> AppCompatActivity.launchProgress(
    caption: String,
    doInBackground: suspend CoroutineScope.(ProgressDialogEx) -> T,
    afterProc: suspend CoroutineScope.(result: T) -> Unit = {},
    progressInitializer: suspend CoroutineScope.(ProgressDialogEx) -> Unit = {},
    preProc: suspend CoroutineScope.() -> Unit = {},
    postProc: suspend CoroutineScope.() -> Unit = {},
) {
    val activity = this
    EmptyScope.launch {
        val progress = ProgressDialogEx(activity)
        try {
            progress.setCancelable(true)
            progress.isIndeterminateEx = true
            progress.setMessageEx("$caption…")
            progressInitializer(progress)
            try {
                preProc()
            } catch (ex: Throwable) {
                log.e(ex, "launchProgress: preProc failed.")
            }
            val result = supervisorScope {
                val task = async(AppDispatchers.io) {
                    doInBackground(progress)
                }
                progress.setOnCancelListener { task.cancel() }
                progress.show()
                task.await()
            }
            if (result != null) afterProc(result)
        } catch (ex: Throwable) {
            log.e(ex, "launchProgress: $caption failed.")
            showToast(ex, "$caption failed.")
        } finally {
            progress.dismissSafe()
            try {
                postProc()
            } catch (ex: Throwable) {
                log.e(ex, "launchProgress: postProc failed.")
            }
        }
    }
}
