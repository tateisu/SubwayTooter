package jp.juggler.subwaytooter

import org.junit.Test
import java.net.IDN
import kotlin.test.assertEquals

class TestIDN {
	
	@Test
	@Throws(Exception::class)
	fun testIDN() {
		// normal conversion
		assertEquals("xn--3-pfuzbe6htf.juggler.jp", IDN.toASCII("マストドン3.juggler.jp",IDN.ALLOW_UNASSIGNED))
		assertEquals("マストドン3.juggler.jp", IDN.toUnicode("xn--3-pfuzbe6htf.juggler.jp",IDN.ALLOW_UNASSIGNED))
		
		// not IDN domain
		assertEquals("mastodon.juggler.jp", IDN.toASCII("mastodon.juggler.jp",IDN.ALLOW_UNASSIGNED))
		assertEquals("mastodon.juggler.jp", IDN.toUnicode("mastodon.juggler.jp",IDN.ALLOW_UNASSIGNED))
		
		// 既に変換済みの引数
		assertEquals("xn--3-pfuzbe6htf.juggler.jp", IDN.toASCII("xn--3-pfuzbe6htf.juggler.jp",IDN.ALLOW_UNASSIGNED))
		assertEquals("マストドン3.juggler.jp", IDN.toUnicode("マストドン3.juggler.jp",IDN.ALLOW_UNASSIGNED))
		
		// 複数のpunycode
		assertEquals("թութ.հայ", IDN.toUnicode("xn--69aa8bzb.xn--y9a3aq",IDN.ALLOW_UNASSIGNED))
		
	}
}