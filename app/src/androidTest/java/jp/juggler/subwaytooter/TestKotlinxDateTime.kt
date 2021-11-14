package jp.juggler.subwaytooter

import androidx.test.platform.app.InstrumentationRegistry
import jp.juggler.subwaytooter.api.entity.TootStatus
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class TestKotlinxDateTime {
    @Test
    fun kotlinInstantEpoch() {
        assertEquals(
            "epoch is zero",
            0L,
            Instant.parse("1970-01-01T00:00:00.000Z").toEpochMilliseconds()
        )
    }

    @Test
    fun kotlinInstant() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tzTokyo = java.util.TimeZone.getTimeZone("Asia/Tokyo")
        TootStatus.dateFormatFull.timeZone = tzTokyo
        fun a(src: String, l1Adj:Long, formatted: String) {
            // using kotlinx.datetime
            val l2 = TootStatus.parseTime2(src)
            // using old utc onlt
            val l1 = TootStatus.parseTime1(src)
            assertEquals(src, l2, l1+ l1Adj)

            val formattedActual = TootStatus.formatTime(
                context = context,
                t = l2,
                bAllowRelative = false,
                onlyDate = false,
            )
            assertEquals("src formatted", formatted, formattedActual)
        }

        val adjUtcToTokyo = 1000L * 3600L * -9L

        a("1970-01-01T00:00:00.000Z", 0,"1970-01-01 09:00:00")
        a("2017-08-26T00:00:00.000Z", 0,"2017-08-26 09:00:00")
        a("2021-11-14T00:00:00.000Z", 0,"2021-11-14 09:00:00")
        a("2021-11-14T11:40:45.086Z", 0,"2021-11-14 20:40:45")
        a("2021-11-13T05:30:39.188+00:00", 0,"2021-11-13 14:30:39")

        a("2021-11-14T12:34:56.0+0900", adjUtcToTokyo,"2021-11-14 12:34:56")
    }
}
