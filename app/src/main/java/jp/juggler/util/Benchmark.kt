package jp.juggler.util

import jp.juggler.subwaytooter.BuildConfig

import android.os.SystemClock

private val log = LogCategory("Benchmark")

val benchmarkLimitDefault = if (BuildConfig.DEBUG) 10L else 100L
fun <T : Any?> benchmark(
    caption: String,
    limit: Long = benchmarkLimitDefault,
    block: () -> T,
): T {
    val start = SystemClock.elapsedRealtime()
    val rv = block()
    val duration = SystemClock.elapsedRealtime() - start
    if (duration >= limit) log.w("benchmark: ${duration}ms : $caption")
    return rv
}
