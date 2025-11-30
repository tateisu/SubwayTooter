package jp.juggler.util.log

import android.os.SystemClock
import jp.juggler.base.IS_DEBUG_BUILD

val benchmarkLog = LogCategory("Benchmark")

val benchmarkLimitDefault = if (IS_DEBUG_BUILD) 10L else 100L

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
    private var timeStart = SystemClock.elapsedRealtime()

    fun report() {
        val duration = SystemClock.elapsedRealtime() - timeStart
        if (duration >= minMs) log.d("$caption ${duration}ms")
    }

    fun start() {
        timeStart = SystemClock.elapsedRealtime()
    }

    inline fun <ReturnType:Any?> bench(block: ()-> ReturnType): ReturnType {
        start()
        val returnValue = block()
        report()
        return returnValue
    }

}

inline fun <ReturnType:Any?> LogCategory.bench(
    caption: String,
    minMs: Long = 33L,
    block: ()-> ReturnType,
): ReturnType {
    val b = Benchmark(this,caption=caption,minMs=minMs)
    val returnValue = block()
    b.report()
    return returnValue
}
