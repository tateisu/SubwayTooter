package jp.juggler.subwaytooter

import jp.juggler.subwaytooter.util.PostImpl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestPostImplTagAscii {
    @Test
    fun testPostImplTagAscii() {
        val reTagNumber = """[0-9]""".toRegex()
        for (cp in 0..<0x100) {
            val str = "" + cp.toChar()
            when {
                cp >= 0x80 -> {
                    assertTrue(PostImpl.reTagNonAscii.containsMatchIn(str))
                    assertFalse(PostImpl.reTagAsciiNotNumber.containsMatchIn(str))
                    assertFalse(reTagNumber.containsMatchIn(str))
                }

                cp in '0'.code..'9'.code -> {
                    assertFalse(PostImpl.reTagNonAscii.containsMatchIn(str))
                    assertFalse(PostImpl.reTagAsciiNotNumber.containsMatchIn(str))
                    assertTrue(reTagNumber.containsMatchIn(str))
                }

                else -> {
                    assertFalse(PostImpl.reTagNonAscii.containsMatchIn(str))
                    assertTrue(PostImpl.reTagAsciiNotNumber.containsMatchIn(str))
                    assertFalse(reTagNumber.containsMatchIn(str))
                }
            }
        }
    }
}
