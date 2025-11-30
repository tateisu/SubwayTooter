package jp.juggler.util.coroutine

import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showError
import jp.juggler.util.log.showToast
import jp.juggler.util.ui.ProgressDialogEx
import jp.juggler.util.ui.dismissSafe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private val log = LogCategory("EmptyScope")

// kotlinx.coroutines 1.5.0 で GlobalScopeがdeprecated になったが、
// プロセスが生きてる間ずっと動いててほしいものや特にキャンセルのタイミングがないコルーチンでは使い続けたい
object EmptyScope : CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = EmptyCoroutineContext + AppDispatchers.MainImmediate
}

// メインスレッド上で動作するコルーチンを起動して、終了を待たずにリターンする。
// 起動されたアクティビティのライフサイクルに関わらず中断しない。
fun launchMain(block: suspend CoroutineScope.() -> Unit): Job =
    EmptyScope.launch {
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
    EmptyScope.launch(context = AppDispatchers.DEFAULT) {
        try {
            block()
        } catch (ex: Throwable) {
            log.e(ex, "launchDefault failed.")
        }
    }

// IOスレッド上で動作するコルーチンを起動して、終了を待たずにリターンする。
// 起動されたアクティビティのライフサイクルに関わらず中断しない。
fun launchIO(block: suspend CoroutineScope.() -> Unit): Job =
    EmptyScope.launch(context = AppDispatchers.IO) {
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
        when (ex) {
            is CancellationException -> {
                log.w(errorCaption ?: "launchAndShowError cancelled.")
            }

            else -> {
                log.e(ex, errorCaption ?: "launchAndShowError failed.")
                showError(ex, errorCaption)
            }
        }
    }
}
fun ComponentActivity.launchAndShowError(
    errorCaption: String? = null,
    block: suspend CoroutineScope.() -> Unit,
): Job = lifecycleScope.launch {
    try {
        block()
    } catch (ex: Throwable) {
        when (ex) {
            is CancellationException -> {
                log.w(errorCaption ?: "launchAndShowError cancelled.")
            }

            else -> {
                log.e(ex, errorCaption ?: "launchAndShowError failed.")
                showError(ex, errorCaption)
            }
        }
    }
}

/////////////////////////////////////////////////////////////////////////

suspend fun <T : Any?> AppCompatActivity.withProgress(
    caption: String,
    progressInitializer: suspend (ProgressDialogEx) -> Unit = {},
    block: suspend (progress: ProgressDialogEx) -> T,
): T {
    val activity = this
    var progress: ProgressDialogEx? = null
    try {
        progress = ProgressDialogEx(activity)
        progress.setCancelable(true)
        progress.isIndeterminateEx = true
        progress.setMessageEx(caption)
        progressInitializer(progress)
        progress.show()
        return supervisorScope {
            val task = async(AppDispatchers.MainImmediate) { block(progress) }
            progress.setOnCancelListener { task.cancel() }
            task.await()
        }
    } finally {
        progress?.dismissSafe()
    }
}

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
                val task = async(AppDispatchers.IO) {
                    doInBackground(progress)
                }
                progress.setOnCancelListener { task.cancel() }
                progress.show()
                task.await()
            }
            if (result != null) afterProc(result)
        } catch (ex: Throwable) {
            log.e(ex, "launchProgress: $caption failed.")
            if (ex !is CancellationException) {
                showToast(ex, "$caption failed.")
            }
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
