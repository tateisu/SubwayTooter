package jp.juggler.subwaytooter

import android.graphics.Color
import androidx.test.runner.AndroidJUnit4
import com.jrummyapps.android.colorpicker.parseColorString
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestColorString {
    @Test
    fun testColorString() {
        fun a(s: String, expect: Int) {
            assertEquals(s, expect, parseColorString(s))
            assertEquals("#$s", expect, parseColorString("#$s"))
        }
        a("", Color.BLACK)
        a("8", Color.BLACK or 0x88_88_88)
        a("56", Color.BLACK or 0x55_66_80)
        a("123", Color.BLACK or 0x11_22_33)
        a("1234", 0x11_22_33_44)
        a("12345", Color.BLACK or 0x12_34_55)
        a("123456", Color.BLACK or 0x12_34_56)
        a("1234567", 0x12_34_56_77)
        a("12345678", 0x12_34_56_78)
        a("123456789", Color.WHITE)
    }
}
