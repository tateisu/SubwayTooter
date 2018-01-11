package jp.juggler.subwaytooter.api.entity

import android.support.test.runner.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.assertEquals


@RunWith(AndroidJUnit4::class)
class TestTootAccount {
	
	@Test
	@Throws(Exception::class)
	fun testFindHostFromUrl() {

		// all null
		assertEquals(null,TootAccount.findHostFromUrl(null,null,null))

		// find from acct
		assertEquals(null,TootAccount.findHostFromUrl("",null,null))
		assertEquals(null,TootAccount.findHostFromUrl("user",null,null))
		assertEquals(null,TootAccount.findHostFromUrl("user@",null,null))
		assertEquals("host",TootAccount.findHostFromUrl("user@HOST",null,null))
		
		// find from accessHost
		assertEquals("",TootAccount.findHostFromUrl(null,"",null))
		assertEquals("any string is allowed",TootAccount.findHostFromUrl(null,"any string is allowed",null))
		
		// find from url
		assertEquals(null,TootAccount.findHostFromUrl(null,null,""))
		assertEquals(null,TootAccount.findHostFromUrl(null,null,"xxx"))
		assertEquals(null,TootAccount.findHostFromUrl(null,null,"mailto:tateisu@gmail.com"))
		assertEquals(
			"mastodon.juggler.jp"
			,TootAccount.findHostFromUrl(null,null,"https://MASTODON.juggler.jp/@tateisu")
		)
		assertEquals(
			"mastodon.juggler.jp",
			TootAccount.findHostFromUrl(null,null,"https://mastodon.juggler.jp/")
		)
		assertEquals(
			"mastodon.juggler.jp",
			TootAccount.findHostFromUrl(null,null,"https://mastodon.juggler.jp")
		)
	}
}
