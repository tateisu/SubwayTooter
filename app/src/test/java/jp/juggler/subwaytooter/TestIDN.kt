package jp.juggler.subwaytooter

import java.net.IDN
import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("MemberNameEqualsClassName")
class TestIDN {

    @Test
    @Throws(Exception::class)
    fun testIDN() {
        // normal conversion
        assertEquals(
            expected = "xn--3-pfuzbe6htf.juggler.jp",
            actual = IDN.toASCII("マストドン3.juggler.jp", IDN.ALLOW_UNASSIGNED)
        )
        assertEquals(
            expected = "マストドン3.juggler.jp",
            actual = IDN.toUnicode("xn--3-pfuzbe6htf.juggler.jp", IDN.ALLOW_UNASSIGNED)
        )

        // not IDN domain
        assertEquals(
            expected = "mastodon.juggler.jp",
            actual = IDN.toASCII("mastodon.juggler.jp", IDN.ALLOW_UNASSIGNED)
        )
        assertEquals(
            "mastodon.juggler.jp",
            actual = IDN.toUnicode("mastodon.juggler.jp", IDN.ALLOW_UNASSIGNED)
        )

        // 既に変換済みの引数
        assertEquals(
            expected = "xn--3-pfuzbe6htf.juggler.jp",
            actual = IDN.toASCII("xn--3-pfuzbe6htf.juggler.jp", IDN.ALLOW_UNASSIGNED)
        )
        assertEquals(
            expected = "マストドン3.juggler.jp",
            actual = IDN.toUnicode("マストドン3.juggler.jp", IDN.ALLOW_UNASSIGNED)
        )

        // 複数のpunycode
        assertEquals(
            expected = "թութ.հայ",
            actual = IDN.toUnicode("xn--69aa8bzb.xn--y9a3aq", IDN.ALLOW_UNASSIGNED)
        )

        // ?
        assertEquals(
            expected = "?",
            actual = IDN.toASCII("?", IDN.ALLOW_UNASSIGNED),
        )
        assertEquals(
            expected = "?",
            actual = IDN.toUnicode("?", IDN.ALLOW_UNASSIGNED),
        )
    }
}
