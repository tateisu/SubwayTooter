package jp.juggler.util.log

import android.content.res.Resources
import android.util.Log
import androidx.annotation.StringRes
import jp.juggler.util.data.notEmpty
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

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
        private const val MAX_LOG_LENGTH = 4000

        var hook: ((Int, String, String) -> Unit)? = null

        private val hookBusy = AtomicBoolean(false)

        @Suppress("RegExpSimplifiable")
        val reAnonymousClass = """(\.\$[0-9]+)+$""".toRegex()

        private val callStackTag: String
            get() {
                // DO NOT switch this to Thread.getCurrentThread().getStackTrace(). The test will pass
                // because Robolectric runs them on the JVM but on Android the elements are different.
                @Suppress("ThrowingExceptionsWithoutMessageOrCause")
                val trace = Throwable().stackTrace
                val stackTraceElement = trace.elementAtOrNull(4)
                    ?: error("callStackTag: stacktrace didn't have enough elements: are you using proguard?")

                stackTraceElement.fileName.notEmpty()
                    ?.let {
                        when (val lastDotPos = it.lastIndexOf('.')) {
                            -1 -> it
                            else -> it.substring(0, lastDotPos)
                        }
                    }
                    ?.let { return it }

                return reAnonymousClass.replace(stackTraceElement.className, "")
                    .let { it.substring(it.lastIndexOf('.') + 1) }
            }

        private fun Throwable.dump(): String {
            // Don't replace this with Log.getStackTraceString() - it hides
            // UnknownHostException, which is not what we want.
            val sw = StringWriter(256)
            val pw = PrintWriter(sw, false)
            this.printStackTrace(pw)
            pw.flush()
            return sw.toString()
        }

        private inline fun splitLines(
            prefix: String,
            message: CharSequence,
            block: (CharSequence) -> Unit,
        ) {
            val limit = MAX_LOG_LENGTH - prefix.length
            if (message.length < limit) {
                block(prefix + message)
            } else {
                // Split by line, then ensure each line can fit into Log's maximum length.
                val length = message.length
                var i = 0
                while (i < length) {
                    val newline = message.indexOf('\n', i).takeIf { it >= 0 } ?: length
                    do {
                        val end = min(newline, i + limit)
                        val part = message.subSequence(i, end)
                        block(prefix + part)
                        i = end
                    } while (i < newline)
                    // skip \n
                    ++i
                }
            }
        }

        private fun printlnOrWtf(priority: Int, message: String) {
            try {
                if (priority == Log.ASSERT) {
                    Log.wtf(TAG, message)
                } else {
                    Log.println(priority, TAG, message)
                }
            } catch (ignored: Throwable) {
                // 単体テストで呼ばれた?
                println(message)
            }
        }

        fun log(
            category: String,
            priority: Int,
            messageArg: String? = null,
            ex: Throwable? = null,
        ): Boolean {

            // 本文とスタックトレース
            val sb = StringBuilder().apply {
                messageArg?.trim()?.notEmpty()?.also { append(it) }

                ex?.dump()?.let {
                    if (isNotEmpty()) append("\n")
                    append(it)
                }
            }

            // 本文の先頭に付与するprefix
            val prefix = "$category:$callStackTag:"
            splitLines(prefix, sb) {
                val line = sb.toString()
                printlnOrWtf(priority, line)
                if (hookBusy.compareAndSet(false, true)) {
                    try {
                        hook?.invoke(priority, category, line)
                    } finally {
                        hookBusy.compareAndSet(true, false)
                    }
                }
            }

            return false
        }

        private fun log(
            category: String,
            priority: Int,
            res: Resources,
            @StringRes stringId: Int,
            args: Array<out Any?>,
        ) = log(category, priority, res.getString(stringId, *args))
    }

    ///////////////////////////////

    fun e(msg: String) = log(category, Log.ERROR, msg)
    fun w(msg: String) = log(category, Log.WARN, msg)
    fun i(msg: String) = log(category, Log.INFO, msg)
    fun d(msg: String) = log(category, Log.DEBUG, msg)
    fun v(msg: String) = log(category, Log.VERBOSE, msg)

    fun e(res: Resources, @StringRes stringId: Int, vararg args: Any?) =
        log(category, Log.ERROR, res, stringId, args)

    fun w(res: Resources, @StringRes stringId: Int, vararg args: Any?) =
        log(category, Log.WARN, res, stringId, args)

    fun i(res: Resources, @StringRes stringId: Int, vararg args: Any?) =
        log(category, Log.INFO, res, stringId, args)

    fun d(res: Resources, @StringRes stringId: Int, vararg args: Any?) =
        log(category, Log.DEBUG, res, stringId, args)

    fun v(res: Resources, @StringRes stringId: Int, vararg args: Any?) =
        log(category, Log.VERBOSE, res, stringId, args)

    ///////////////////////////////
    // stack trace

    fun e(ex: Throwable, caption: String?) =
        log(category, Log.ERROR, ex.withCaption(caption), ex)

    fun w(ex: Throwable, caption: String?) =
        log(category, Log.WARN, ex.withCaption(caption), ex)
}
