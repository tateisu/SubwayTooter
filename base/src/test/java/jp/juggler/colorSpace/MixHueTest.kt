package jp.juggler.colorSpace

import jp.juggler.util.colorSpace.OkLch.Companion.mixHue
import org.junit.Test
import kotlin.test.assertEquals

class MixHueTest {
    @Test
    fun testMixHue() {
        fun t(
            h1: Float,
            h2: Float,
            expect: Float,
        ) {
            val actual = mixHue(h1, h2)
            assertEquals(
                expect,
                actual,
                "h1=$h1, h2=$h2, expect=$expect",
            )
        }
        // 0 start
        t(h1 = 0f, h2 = 0f, expect = 0f)
        t(h1 = 0f, h2 = 30f, expect = 15f)
        t(h1 = 0f, h2 = 180f, expect = 90f)
        t(h1 = 0f, h2 = 181f, expect = 270.5f)
        // 90 start
        t(h1 = 90f, h2 = 90f, expect = 90f)
        t(h1 = 90f, h2 = 90f + 30f, expect = 90f + 15f)
        t(h1 = 90f, h2 = 90f + 180f, expect = 90f + 90f)
        t(h1 = 90f, h2 = 90f + 181f, expect = 0.5f)
        // 180f start
        t(h1 = 180f, h2 = 180f, expect = 180f)
        t(h1 = 180f, h2 = 180f + 30f, expect = 180f + 15f)
        t(h1 = 180f, h2 = 180f + 180f, expect = 90f) // round
        t(h1 = 180f, h2 = 180f + 181f, expect = 90.5f)
        // 270f start
        t(h1 = 270f, h2 = 270f, expect = 270f)
        t(h1 = 270f, h2 = 270f + 30f, expect = 270f + 15f)
        t(h1 = 270f, h2 = 270f + 180f, expect = 180f) // not round
        t(h1 = 270f, h2 = 270f + 181f, expect = 180.5f)
    }
}
