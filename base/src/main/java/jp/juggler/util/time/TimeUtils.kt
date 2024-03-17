/**
 * Java8 Time API を使った日時関連のユーティリティ
 * - api "org.jetbrains.kotlinx:kotlinx-datetime:0.4.0"
 * - desugar で Java 8 の日時APIを使えるようにする必要がある
 * - https://developer.android.com/studio/write/java8-support?hl=ja
 * - coreLibraryDesugaringEnabled true
 * - coreLibraryDesugaring "com.android.tools:desugar_jdk_libs:1.2.2"
 */
package jp.juggler.util.time

import jp.juggler.util.log.LogCategory
import java.time.Instant
import java.time.ZoneId

private val log = LogCategory("TimeUtils")

/**
 * kotlinx-datetime の Instant.parse は ISO8601フォーマットを受け付ける
 */
fun String.parseTimeIso8601() =
    when {
        isBlank() -> null
        else -> try {
            Instant.parse(this).toEpochMilli()
        } catch (ex: Throwable) {
            log.w("parseTime failed. $this")
            null
        }
    }

/**
 * UI表示用。フォーマットは適当。
 */
fun Long.formatLocalTime(): String {
    val tz = ZoneId.systemDefault()
    val zdt = Instant.ofEpochMilli(this).atZone(tz)
    return "%d/%02d/%02d %02d:%02d:%02d.%03d".format(
        zdt.year,
        zdt.monthValue,
        zdt.dayOfMonth,
        zdt.hour,
        zdt.minute,
        zdt.second,
        zdt.nano / 1_000_000,
    )
}
