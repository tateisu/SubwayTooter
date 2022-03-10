package jp.juggler.util

import android.content.Context
import android.widget.Toast
import me.drakeet.support.toast.ToastCompat
import java.lang.ref.WeakReference

object ToastUtils {

    private val log = LogCategory("ToastUtils")
    private var refToast: WeakReference<Toast>? = null

    internal fun showToastImpl(context: Context, bLong: Boolean, message: String): Boolean {
        runOnMainLooper {

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
}

fun Context.showToast(bLong: Boolean, caption: String?): Boolean =
    ToastUtils.showToastImpl(this, bLong, caption ?: "(null)")

fun Context.showToast(ex: Throwable, caption: String = "error."): Boolean =
    ToastUtils.showToastImpl(this, true, ex.withCaption(caption))

fun Context.showToast(bLong: Boolean, stringId: Int, vararg args: Any): Boolean =
    ToastUtils.showToastImpl(this, bLong, getString(stringId, *args))

fun Context.showToast(ex: Throwable, stringId: Int, vararg args: Any): Boolean =
    ToastUtils.showToastImpl(this, true, ex.withCaption(resources, stringId, *args))
