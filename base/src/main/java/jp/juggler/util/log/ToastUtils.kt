package jp.juggler.util.log

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.widget.PopupWindow
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import jp.juggler.util.coroutine.AppDispatchers.withTimeoutSafe
import jp.juggler.util.coroutine.runOnMainLooper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import me.drakeet.support.toast.ToastCompat
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

private val log = LogCategory("ToastUtils")
private var refToast: WeakReference<Toast>? = null
private var oldApplication: WeakReference<Application>? = null
private var lastActivity: WeakReference<Activity>? = null
private var lastPopup: WeakReference<PopupWindow>? = null

private val activityCallback = object : Application.ActivityLifecycleCallbacks {

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    }

    override fun onActivityStarted(activity: Activity) {
        lastActivity = WeakReference(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        lastActivity = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (lastActivity?.get() == activity) {
            lastActivity = null
        }
    }

    override fun onActivityStopped(activity: Activity) {
        if (lastActivity?.get() == activity) {
            lastActivity = null
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (lastActivity?.get() == activity) {
            lastActivity = null
        }
    }
}

/**
 * App1.onCreateから呼ばれる
 */
fun initializeToastUtils(app: Application) {
    try {
        oldApplication?.get()?.unregisterActivityLifecycleCallbacks(activityCallback)
    } catch (ex: Throwable) {
        Log.e("SubwayTooter", "unregisterActivityLifecycleCallbacks failed.", ex)
    }
    try {
        app.registerActivityLifecycleCallbacks(activityCallback)
    } catch (ex: Throwable) {
        Log.e("SubwayTooter", "registerActivityLifecycleCallbacks failed.", ex)
    }
    oldApplication = WeakReference(app)
}

/**
 * Animationを開始して終了を非同期待機する
 */
suspend fun Animation.startAndAwait(duration: Long, v: View) =
    try {
        withTimeoutSafe(duration + 333L) {
            suspendCancellableCoroutine { cont ->
                v.clearAnimation()
                this@startAndAwait.duration = duration
                this@startAndAwait.fillAfter = true
                setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {}
                    override fun onAnimationRepeat(animation: Animation?) {}
                    override fun onAnimationEnd(animation: Animation?) {
                        cont.resume(Unit)
                    }
                })
                v.startAnimation(this@startAndAwait)
            }
        }
    } catch (ex: TimeoutCancellationException) {
        log.w(ex, "startAndAwait timeout.")
        Unit
    }

internal fun showToastImpl(
    context: Context,
    bLong: Boolean,
    message: String,
    forceToast: Boolean = false,
): Boolean {
    runOnMainLooper {
        if (!forceToast && (message.length >= 32 || message.count { it == '\n' } > 1)) {
            // Android 12以降はトーストを全文表示しない
            // 長いメッセージの場合は可能ならダイアログを使う
            lastActivity?.get()?.let {
                try {
                    AlertDialog.Builder(it)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    return@runOnMainLooper
                } catch (ex: Throwable) {
                    log.e(ex, "showPopup failed.")
                }
            }
            // 画面がない、または失敗したら普通のトーストにフォールバック
        }

        // 前回のトーストの表示を終了する
        try {
            refToast?.get()?.cancel()
        } catch (ex: Throwable) {
            log.e(ex, "toast cancel failed.")
        } finally {
            refToast = null
        }

        // 新しいトーストを作る
        try {
            val duration = if (bLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            val t = ToastCompat.makeText(context, message, duration)
            t.setBadTokenListener { }
            t.show()
            refToast = WeakReference(t)
        } catch (ex: Throwable) {
            log.e(ex, "showToastImpl failed.")
        }
    }
    return false
}

fun Context.showToast(
    bLong: Boolean,
    caption: String?,
    forceToast: Boolean = false,
): Boolean = showToastImpl(this, bLong, caption ?: "(null)", forceToast = forceToast)

fun Context.showToast(ex: Throwable, caption: String? = null): Boolean =
    showToastImpl(this, true, ex.withCaption(caption))

fun Context.showToast(bLong: Boolean, @StringRes stringId: Int, vararg args: Any): Boolean =
    showToastImpl(this, bLong, getString(stringId, *args))

fun Context.showToast(ex: Throwable, @StringRes stringId: Int, vararg args: Any): Boolean =
    showToastImpl(this, true, ex.withCaption(resources, stringId, *args))

fun Activity.dialogOrToast(message: String?) {
    if (message.isNullOrBlank()) return
    try {
        android.app.AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    } catch (_: Throwable) {
        showToast(true, message)
    }
}

fun Activity.dialogOrToast(@StringRes stringId: Int, vararg args: Any) =
    dialogOrToast(getString(stringId, *args))

fun Activity.showError(ex: Throwable, caption: String? = null) {
    log.e(ex, caption ?: "(showError)")

    // キャンセル例外はUIに表示しない
    if (ex is CancellationException) return

    try {
        val text = listOf(
            caption,
            when (ex) {
                is IllegalStateException -> null
                else -> ex.javaClass.simpleName
            },
            ex.message,
        )
            .filter { !it.isNullOrBlank() }
            .joinToString("\n")
        if (text.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setMessage(text)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
    } catch (ignored: Throwable) {
    }
    showToast(ex, caption)
}

fun Context.errorString(@StringRes stringId: Int, vararg args: Any?): Nothing =
    error(getString(stringId, *args))
