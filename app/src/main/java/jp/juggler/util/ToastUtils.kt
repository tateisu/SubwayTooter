package jp.juggler.util

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.R
import kotlinx.coroutines.CancellationException
import me.drakeet.support.toast.ToastCompat
import java.lang.ref.WeakReference

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
    error(getString(stringId, args))
