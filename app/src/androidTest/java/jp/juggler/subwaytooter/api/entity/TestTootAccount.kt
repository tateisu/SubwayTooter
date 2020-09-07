package jp.juggler.subwaytooter.api.entity

import androidx.test.runner.AndroidJUnit4
import jp.juggler.subwaytooter.util.LinkHelper

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.assertEquals

@RunWith(AndroidJUnit4::class)
class TestTootAccount {
	
	@Test
	@Throws(Exception::class)
	fun testFindHostFromUrl() {
		
		// all null
		assertEquals(null, TootAccount.findHostFromUrl(null, null, null).first)
		
		// find from acct
		assertEquals(null, TootAccount.findHostFromUrl("", null, null).first)
		assertEquals(null, TootAccount.findHostFromUrl("user", null, null).first)
		assertEquals(null, TootAccount.findHostFromUrl("user@", null, null).first)
		assertEquals("host", TootAccount.findHostFromUrl("user@HOST", null, null).first)
		
		// find from accessHost
		assertEquals(
			"",
			TootAccount.findHostFromUrl(null, LinkHelper.newLinkHelper(Host.parse("")), null).first
		)
		assertEquals(
			"any string is allowed",
			TootAccount.findHostFromUrl(
				null,
				LinkHelper.newLinkHelper(Host.parse("any string is allowed")),
				null
			).first
		)
		
		// find from url
		assertEquals(null, TootAccount.findHostFromUrl(null, null, "").first)
		assertEquals(null, TootAccount.findHostFromUrl(null, null, "xxx").first)
		assertEquals(
			null,
			TootAccount.findHostFromUrl(null, null, "mailto:tateisu@gmail.com").first
		)
		assertEquals(
			"mastodon.juggler.jp",
			TootAccount.findHostFromUrl(null, null, "https://MASTODON.juggler.jp/@tateisu").first
		)
		assertEquals(
			"mastodon.juggler.jp",
			TootAccount.findHostFromUrl(null, null, "https://mastodon.juggler.jp/").first
		)
		assertEquals(
			"mastodon.juggler.jp",
			TootAccount.findHostFromUrl(null, null, "https://mastodon.juggler.jp").first
		)
	}
}
