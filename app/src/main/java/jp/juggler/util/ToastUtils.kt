package jp.juggler.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.PopupWindow
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.databinding.PopupToastBinding
import kotlinx.coroutines.*
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
        withTimeout(duration + 333L) {
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

private fun showPopup(activity: Activity, bLong: Boolean, message: String) {
    val rootView = activity.findViewById<View?>(android.R.id.content)?.rootView
        ?: error("missing rootView")

    val views = PopupToastBinding.inflate(activity.layoutInflater)
    views.tvMessage.text = message

    val popupWindow = PopupWindow(
        views.root,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        false
    )

    // タップ時に他のViewでキャッチされないための設定
    popupWindow.isFocusable = false
    popupWindow.isTouchable = false
    popupWindow.isOutsideTouchable = false

    try {
        lastPopup?.get()?.dismiss()
    } catch (ex: Throwable) {
        log.trace(ex, "dismiss failed.")
    }

    lastPopup = null
    popupWindow.showAtLocation(rootView, Gravity.CENTER, 0, 0)
    lastPopup = WeakReference(popupWindow)

    launchMain {

        // fade in
        AlphaAnimation(0.1f, 1f)
            .startAndAwait(333L, views.tvMessage)

        // keep
        val keepDuration = when {
            bLong -> 4000L
            else -> 2000L
        }
        delay(keepDuration)

        // fade out
        AlphaAnimation(1f, 0f)
            .startAndAwait(333L, views.tvMessage)

        // dismiss
        try {
            popupWindow.dismiss()
        } catch (ex: Throwable) {
            log.e(ex, "dismiss failed.")
        }
    }
}

internal fun showToastImpl(context: Context, bLong: Boolean, message: String): Boolean {
    runOnMainLooper {

        // Android 12以降はトーストを全文表示しないので、何か画面が表示中ならポップアップウィンドウを使う
        lastActivity?.get()?.let {
            try {
                showPopup(it, bLong, message)
                return@runOnMainLooper
            } catch (ex: Throwable) {
                log.trace(ex, "showPopup failed.")
            }
        }
        // 画面がない、または失敗したら普通のトーストにフォールバック

        // 前回のトーストの表示を終了する
        try {
            refToast?.get()?.cancel()
        } catch (ex: Throwable) {
            log.trace(ex)
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
            log.trace(ex)
        }

        // コールスタックの外側でエラーになる…
        // android.view.WindowManager$BadTokenException:
        // at android.view.ViewRootImpl.setView (ViewRootImpl.java:679)
        // at android.view.WindowManagerGlobal.addView (WindowManagerGlobal.java:342)
        // at android.view.WindowManagerImpl.addView (WindowManagerImpl.java:94)
        // at android.widget.Toast$TN.handleShow (Toast.java:435)
        // at android.widget.Toast$TN$2.handleMessage (Toast.java:345)
    }
    return false
}

fun Context.showToast(bLong: Boolean, caption: String?): Boolean =
    showToastImpl(this, bLong, caption ?: "(null)")

fun Context.showToast(ex: Throwable, caption: String? = null): Boolean =
    showToastImpl(this, true, ex.withCaption(caption))

fun Context.showToast(bLong: Boolean, stringId: Int, vararg args: Any): Boolean =
    showToastImpl(this, bLong, getString(stringId, *args))

fun Context.showToast(ex: Throwable, stringId: Int, vararg args: Any): Boolean =
    showToastImpl(this, true, ex.withCaption(resources, stringId, *args))

fun AppCompatActivity.showError(ex: Throwable, caption: String? = null) {
    log.e(ex, caption ?: "(showError)")

    // キャンセル例外はUIに表示しない
    if (ex is CancellationException) return

    try {
        AlertDialog.Builder(this)
            .setTitle(R.string.error)
            .setMessage(
                listOf(
                    caption,
                    when (ex) {
                        is IllegalStateException -> null
                        else -> ex.javaClass.simpleName
                    },
                    ex.message,
                )
                    .filter { !it.isNullOrBlank() }
                    .joinToString("\n")
            )
            .setPositiveButton(R.string.ok, null)
            .show()
    } catch (ignored: Throwable) {
        showToast(ex, caption)
    }
}

fun Context.errorString(@StringRes stringId: Int, vararg args: Any?): Nothing =
    error(getString(stringId, *args))
