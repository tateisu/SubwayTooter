package jp.juggler.colorSpace

import jp.juggler.colorSpace.OkLch.Companion.mixHue
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
            val lch1 = OkLch( l= 0.5f, c = 0.5f, h = h1)
            val lch2 = OkLch( l= 0.5f, c = 0.5f, h = h2)
            val actual = mixHue(lch1,lch2)
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
    @Test
    fun testMixHueNoChroma() {
        fun t(
            hChroma: Float,
            hIgnored: Float,
        ) {
            // 1が無彩色
            run{
                val lch1 = OkLch( l= 0.5f, c = 0f, h = hIgnored)
                val lch2 = OkLch( l= 0.5f, c = 0.5f, h = hChroma)
                val actual = mixHue(lch1,lch2)
                assertEquals(
                    hChroma,
                    actual,
                    "hChroma=$hChroma, hIgnored=$hIgnored",
                )
            }
            // 2が無彩色
            run{
                val lch1 = OkLch( l= 0.5f, c = 0.5f, h = hChroma)
                val lch2 = OkLch( l= 0.5f, c = 0f, h = hIgnored)
                val actual = mixHue(lch1,lch2)
                assertEquals(
                    hChroma,
                    actual,
                    "hChroma=$hChroma, hIgnored=$hIgnored",
                )
            }
        }
        for( hChroma in 0 .. 360 step 30){
            for( hIgnored in 0 .. 360 step 30){
                t(hChroma= hChroma.toFloat(), hIgnored = hIgnored.toFloat())
            }
        }
    }
}
