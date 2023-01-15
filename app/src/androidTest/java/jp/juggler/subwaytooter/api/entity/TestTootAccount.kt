package jp.juggler.subwaytooter.api.entity

import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.juggler.subwaytooter.util.LinkHelper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestTootAccount {

    @Test
    @Throws(Exception::class)
    fun testFindHostFromUrl() {

        val emptyHost = Host.EMPTY

        // all null
        var pair = TootAccount.findHostFromUrl(null, null, null)
        assertEquals(null, pair.first)

        // find from acct
        pair = TootAccount.findHostFromUrl("", null, null)
        assertEquals(Host.UNKNOWN, pair.first)
        assertEquals(null, TootAccount.findHostFromUrl("user", null, null).first)
        assertEquals(emptyHost, TootAccount.findHostFromUrl("user@", null, null).first)
        assertEquals(
            "host",
            TootAccount.findHostFromUrl("user@HOST", null, null)
                .first?.ascii
        )

        // find from accessHost

        assertEquals(
            emptyHost,
            TootAccount.findHostFromUrl(null, LinkHelper.create(emptyHost), null).first
        )
        val testHost = Host.parse("any string is allowed")
        assertEquals(
            testHost,
            TootAccount.findHostFromUrl(
                null,
                LinkHelper.create(testHost),
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
            Host.parse("mastodon.juggler.jp"),
            TootAccount.findHostFromUrl(null, null, "https://MASTODON.juggler.jp/@tateisu").first
        )
        assertEquals(
            Host.parse("mastodon.juggler.jp"),
            TootAccount.findHostFromUrl(null, null, "https://mastodon.juggler.jp/").first
        )
        assertEquals(
            Host.parse("mastodon.juggler.jp"),
            TootAccount.findHostFromUrl(null, null, "https://mastodon.juggler.jp").first
        )
    }
}
