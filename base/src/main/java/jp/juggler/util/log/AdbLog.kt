package jp.juggler.util.log

import android.util.Log
import jp.juggler.base.BuildConfig
import jp.juggler.util.data.notEmpty
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.math.min

object AdbLog {
    private const val APP_TAG = "AppTag_PushReceiver"
    private const val MAX_LOG_LENGTH = 4000

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

    private fun printlnOrWtf(priority: Int, message: String) {
        try {
            if (priority == Log.ASSERT) {
                Log.wtf(APP_TAG, message)
            } else {
                Log.println(priority, APP_TAG, message)
            }
        } catch (ignored: Throwable) {
            // 単体テストで呼ばれた?
            println(message)
        }
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

    fun log(
        priority: Int,
        ex: Throwable? = null,
        messageArg: String? = null,
        prefixArg: String? = null,
    ) {
        if (priority < Log.INFO && !BuildConfig.DEBUG) return

        // 本文の先頭に付与するprefix
        val tag = (prefixArg ?: callStackTag)
        val prefix = when {
            tag.isBlank() -> ""
            else -> "$tag: "
        }

        val stackTrace = ex?.dump()

        // 本文とスタックトレース
        val sb = StringBuilder()
        val message = messageArg?.takeIf { it.isNotEmpty() }?.also { sb.append(it) }
        if (stackTrace != null) {
            if (message != null) sb.append("\n")
            sb.append(stackTrace)
        }

        splitLines(prefix, sb) {
            printlnOrWtf(priority, it.toString())
        }
//        LogEntity.saveLog(priority, tag, message, stackTrace)
    }

    @JvmStatic
    fun v(msg: String) = log(Log.VERBOSE, messageArg = msg)

    @JvmStatic
    fun d(msg: String) = log(Log.DEBUG, messageArg = msg)

    @JvmStatic
    fun i(msg: String) = log(Log.INFO, messageArg = msg)

    @JvmStatic
    fun w(msg: String) = log(Log.WARN, messageArg = msg)

    @JvmStatic
    fun e(msg: String) = log(Log.ERROR, messageArg = msg)

    @JvmStatic
    fun wtf(msg: String) = log(Log.ASSERT, messageArg = msg)

    @JvmStatic
    fun recordFirebaseCrashlytics(ex: Throwable?) {
        try {
            ex ?: return
//            FirebaseCrashlytics.getInstance().recordException(ex)
        } catch (ignored: Throwable) {
            // 単体テストで FirebaseCrashlytics.getInstance() が例外を出す
        }
    }

    @JvmStatic
    @JvmOverloads
    fun i(ex: Throwable?, msg: String? = null) {
        recordFirebaseCrashlytics(ex)
        log(Log.INFO, ex = ex, messageArg = msg)
    }

    @JvmStatic
    @JvmOverloads
    fun w(ex: Throwable?, msg: String? = null) {
        recordFirebaseCrashlytics(ex)
        log(Log.WARN, ex = ex, messageArg = msg)
    }

    @JvmStatic
    @JvmOverloads
    fun e(ex: Throwable?, msg: String? = null) {
        recordFirebaseCrashlytics(ex)
        log(Log.ERROR, ex = ex, messageArg = msg)
    }

    @JvmStatic
    @JvmOverloads
    fun wtf(ex: Throwable?, msg: String? = null) {
        recordFirebaseCrashlytics(ex)
        log(Log.ASSERT, ex = ex, messageArg = msg)
    }
}
