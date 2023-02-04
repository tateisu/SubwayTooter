/**
 * kotlinx-datetime を使った日時関連のユーティリティ
 * - api "org.jetbrains.kotlinx:kotlinx-datetime:0.4.0"
 * - desugar で Java 8 の日時APIを使えるようにする必要がある
 * - https://developer.android.com/studio/write/java8-support?hl=ja
 * - coreLibraryDesugaringEnabled true
 * - coreLibraryDesugaring "com.android.tools:desugar_jdk_libs:1.2.2"
 */
package jp.juggler.util.time

import jp.juggler.util.log.LogCategory
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val log = LogCategory("TimeUtils")

/**
 * kotlinx-datetime の Instant.parse は ISO8601フォーマットを受け付ける
 */
fun String.parseTimeIso8601() =
    when {
        isBlank() -> null
        else -> try {
            Instant.parse(this).toEpochMilliseconds()
        } catch (ex: Throwable) {
            log.w("parseTime failed. $this")
            null
        }
    }

/**
 * UI表示用。フォーマットは適当。
 */
fun Long.formatLocalTime(): String {
    val tz = TimeZone.currentSystemDefault()
    val lt = Instant.fromEpochMilliseconds(this).toLocalDateTime(tz)
    return "%d/%02d/%02d %02d:%02d:%02d.%03d".format(
        lt.year,
        lt.monthNumber,
        lt.dayOfMonth,
        lt.hour,
        lt.minute,
        lt.second,
        lt.nanosecond / 1_000_000,
    )
}
