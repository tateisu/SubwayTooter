package jp.juggler.subwaytooter.api

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.api.entity.TootAccount.Companion.tootAccount
import jp.juggler.subwaytooter.api.entity.TootAccountRef.Companion.tootAccountRef
import jp.juggler.subwaytooter.api.entity.TootNotification.Companion.tootNotification
import jp.juggler.subwaytooter.api.entity.TootStatus.Companion.tootStatus
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestDuplicateMap {

    private val parser = TootParser(
        InstrumentationRegistry.getInstrumentation().targetContext,
        SavedAccount(
            db_id = 1,
            acctArg = "user1@host1",
            apiHostArg = null
        )
    )

    private fun genStatus(
        parser: TootParser,
        accountJson: JsonObject,
        statusId: String,
        uri: String,
        url: String?,
    ): TootStatus {
        val itemJson = buildJsonObject {
            put("account", accountJson)
            put("id", statusId)
            put("uri", uri)
            if (url != null) put("url", url)
        }

        return tootStatus(parser, itemJson)
    }

    private fun testDuplicateStatus(): ArrayList<TimelineItem> {
        val generatedItems = ArrayList<TimelineItem>()
        fun checkStatus(
            map: DuplicateMap,
            parser: TootParser,
            accountJson: JsonObject,
            statusId: String,
            uri: String,
            url: String?,
        ) {
            val item = genStatus(parser, accountJson, statusId, uri, url)
            assertNotNull(item)
            generatedItems.add(item)
            assertEquals(false, map.isDuplicate(item))
            assertEquals(true, map.isDuplicate(item))
        }

        val account1Json = buildJsonObject {
            put("username", "user1")
            put("acct", "user1")
            put("id", 1L)
            put("url", "http://${parser.apiHost}/@user1")
        }

        val account1 = tootAccount(parser, account1Json)
        assertNotNull(account1)

        val map = DuplicateMap()

        // 普通のステータス
        checkStatus(
            map,
            parser,
            account1Json,
            "s1",
            "http://${parser.apiHost}/@${account1.username}/1",
            "http://${parser.apiHost}/@${account1.username}/1"
        )
        // 別のステータス
        checkStatus(
            map,
            parser,
            account1Json,
            "s2",
            "http://${parser.apiHost}/@${account1.username}/2",
            "http://${parser.apiHost}/@${account1.username}/2"
        )
        // URIは必須になったので、URIなしのテストは行わない
        // 今度はURLがない
        checkStatus(
            map,
            parser,
            account1Json,
            "s4",
            "http://${parser.apiHost}/@${account1.username}/4",
            null //"http://${parser.apiHost}/@${account1.username}/4"
        )
        // 今度はIDがおかしい
        checkStatus(
            map,
            parser,
            account1Json,
            "",
            "http://${parser.apiHost}/@${account1.username}/5",
            "http://${parser.apiHost}/@${account1.username}/5"
        )
        return generatedItems
    }

    private fun testDuplicateNotification(): ArrayList<TimelineItem> {
        val generatedItems = ArrayList<TimelineItem>()
        fun checkNotification(
            map: DuplicateMap,
            parser: TootParser,
            id: String,
        ) {
            val itemJson = JsonObject()

            itemJson.apply {
                put("type", NotificationType.Mention.code)
                put("id", id)
            }

            val item = tootNotification(parser, itemJson)
            assertNotNull(item)
            generatedItems.add(item)
            assertEquals(false, map.isDuplicate(item))
            assertEquals(true, map.isDuplicate(item))
        }

        val map = DuplicateMap()
        checkNotification(map, parser, "n0")
        checkNotification(map, parser, "n1")
        checkNotification(map, parser, "n2")
        checkNotification(map, parser, "n3")
        return generatedItems
    }

    private fun testDuplicateReport(): ArrayList<TimelineItem> {
        val generatedItems = ArrayList<TimelineItem>()
        fun checkReport(
            map: DuplicateMap,
            id: String,
        ) {
            val item = TootReport(JsonObject().apply {
                put("id", id)
                put("action_taken", "eat")
            })

            assertNotNull(item)
            generatedItems.add(item)
            assertEquals(false, map.isDuplicate(item))
            assertEquals(true, map.isDuplicate(item))
        }

        val map = DuplicateMap()
        checkReport(map, "r0")
        checkReport(map, "r1")
        checkReport(map, "r2")
        checkReport(map, "r3")
        return generatedItems
    }

    private fun testDuplicateAccount(): ArrayList<TimelineItem> {
        val generatedItems = ArrayList<TimelineItem>()
        fun checkAccount(
            map: DuplicateMap,
            parser: TootParser,
            id: String,
        ) {

            val itemJson = JsonObject()
            itemJson.apply {
                put("username", "user$id")
                put("acct", "user$id")
                put("id", id)
                put("url", "http://${parser.apiHost}/@user$id")
            }

            val item = tootAccountRef(parser, tootAccount(parser, itemJson))
            assertNotNull(item)
            generatedItems.add(item)
            assertEquals(false, map.isDuplicate(item))
            assertEquals(true, map.isDuplicate(item)) // 二回目はtrueになる
        }

        val map = DuplicateMap()
        checkAccount(map, parser, "a0")
        checkAccount(map, parser, "a1")
        checkAccount(map, parser, "a2")
        checkAccount(map, parser, "a3")
        return generatedItems
    }

    @Test
    fun testFilterList() {
        val statusItems = testDuplicateStatus()
        val notiItems = testDuplicateNotification()
        val reportItems = testDuplicateReport()
        val accountItems = testDuplicateAccount()

        var map = DuplicateMap()
        var dst = map.filterDuplicate(statusItems)
        assertEquals("statusItems", statusItems.size, dst.size)

        map = DuplicateMap()
        dst = map.filterDuplicate(notiItems)
        assertEquals("notiItems", notiItems.size, dst.size)

        map = DuplicateMap()
        dst = map.filterDuplicate(reportItems)
        assertEquals("reportItems", reportItems.size, dst.size)

        map = DuplicateMap()
        dst = map.filterDuplicate(accountItems)
        assertEquals("accountItems", accountItems.size, dst.size)

        val totalItems = ArrayList<TimelineItem>()
        totalItems.addAll(statusItems)
        totalItems.addAll(notiItems)
        totalItems.addAll(reportItems)
        totalItems.addAll(accountItems)
        map = DuplicateMap()
        dst = map.filterDuplicate(totalItems)
        assertEquals("totalItems", totalItems.size, dst.size)

        val dst2 = map.filterDuplicate(totalItems)
        assertEquals(0, dst2.size)
    }
}
