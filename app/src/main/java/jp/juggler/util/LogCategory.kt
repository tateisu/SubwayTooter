package jp.juggler.util

import android.content.res.Resources
import android.util.Log
import androidx.annotation.StringRes

fun Throwable.withCaption(caption: String? = null) =
    when {
        caption.isNullOrBlank() -> "${javaClass.simpleName} $message"
        else -> "$caption :${javaClass.simpleName} $message"
    }

fun Throwable.withCaption(resources: Resources, stringId: Int, vararg args: Any) =
    "${resources.getString(stringId, *args)}: ${javaClass.simpleName} $message"

// cause 付きの IllegalStateException を投げる
fun errorEx(cause: Throwable, caption: String): Nothing =
    throw IllegalStateException(caption, cause)

class LogCategory(val category: String) {

    companion object {
        private const val TAG = "SubwayTooter"
    }

    ///////////////////////////////
    // string

    private fun msg(priority: Int, msg: String): Boolean {
        Log.println(priority, TAG, "$category/$msg")
        return false
    }

    fun e(msg: String) = msg(Log.ERROR, msg)
    fun w(msg: String) = msg(Log.WARN, msg)
    fun i(msg: String) = msg(Log.INFO, msg)
    fun d(msg: String) = msg(Log.DEBUG, msg)
    fun v(msg: String) = msg(Log.VERBOSE, msg)

    ///////////////////////////////
    // Resources.getString()

    private fun msg(
        priority: Int,
        res: Resources,
        @StringRes stringId: Int,
        args: Array<out Any?>,
    ) = msg(priority, res.getString(stringId, *args))

    fun e(res: Resources, @StringRes stringId: Int, vararg args: Any?) =
        msg(Log.ERROR, res, stringId, args)

    fun w(res: Resources, @StringRes stringId: Int, vararg args: Any?) =
        msg(Log.WARN, res, stringId, args)

    fun i(res: Resources, @StringRes stringId: Int, vararg args: Any?) =
        msg(Log.INFO, res, stringId, args)

    fun d(res: Resources, @StringRes stringId: Int, vararg args: Any?) =
        msg(Log.DEBUG, res, stringId, args)

    fun v(res: Resources, @StringRes stringId: Int, vararg args: Any?) =
        msg(Log.VERBOSE, res, stringId, args)

    ///////////////////////////////
    // stack trace

    fun e(ex: Throwable, caption: String?) =
        false.also { Log.e(TAG, "$category/${ex.withCaption(caption)}", ex) }

    fun w(ex: Throwable, caption: String?) =
        false.also { Log.w(TAG, "$category/${ex.withCaption(caption)}", ex) }
}
