package jp.juggler.subwaytooter

import android.os.SystemClock

val benchmarkLimitDefault = if (BuildConfig.DEBUG) 10L else 100L
fun <T : Any?> benchmark(
    caption: String,
    limit: Long = benchmarkLimitDefault,
    block: () -> T,
): T {
    val start = SystemClock.elapsedRealtime()
    val rv = block()
    val duration = SystemClock.elapsedRealtime() - start
    if (duration >= limit) ActMain.log.w("benchmark: ${duration}ms : $caption")
    return rv
}
