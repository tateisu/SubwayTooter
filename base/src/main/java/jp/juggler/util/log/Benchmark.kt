package jp.juggler.util.log

import android.os.SystemClock
import jp.juggler.base.BuildConfig

val benchmarkLog = LogCategory("Benchmark")

val benchmarkLimitDefault = if (BuildConfig.DEBUG) 10L else 100L

inline fun <T : Any?> benchmark(
    caption: String,
    limit: Long = benchmarkLimitDefault,
    block: () -> T,
): T {
    val start = SystemClock.elapsedRealtime()
    val rv = block()
    val duration = SystemClock.elapsedRealtime() - start
    if (duration >= limit) benchmarkLog.w("benchmark: ${duration}ms : $caption")
    return rv
}

class Benchmark(
    val log: LogCategory,
    val caption: String,
    private val minMs: Long = 33L,
) {
    private val timeStart = SystemClock.elapsedRealtime()

    fun report() {
        val duration = SystemClock.elapsedRealtime() - timeStart
        if (duration >= minMs) log.d("$caption ${duration}ms")
    }
}
