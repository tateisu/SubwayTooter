package jp.juggler.subwaytooter.api.entity

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestEntityUtils {

    class TestEntity(val s: String, val l: Long) : Mappable<String> {
        constructor(src: JsonObject) : this(
            s = src.stringOrThrow("s"),
            l = src.long("l") ?: 0L
        )

        @Suppress("UNUSED_PARAMETER")
        constructor(parser: TootParser, src: JsonObject) : this(
            s = src.stringOrThrow("s"),
            l = src.long("l") ?: 0L
        )

        override val mapKey: String
            get() = s
    }

    @Test
    fun testParseItem() {
        assertEquals(null, parseItem(null, ::TestEntity))

        run {
            val src = """{"s":null,"l":"100"}""".decodeJsonObject()
            val item = parseItem(src, ::TestEntity)
            assertNull(item)
        }
        run {
            val src = """{"s":"","l":"100"}""".decodeJsonObject()
            val item = parseItem(src, ::TestEntity)
            assertNull(item)
        }
        run {
            val src = """{"s":"A","l":null}""".decodeJsonObject()
            val item = parseItem(src, ::TestEntity)
            assertNotNull(item)
            assertEquals(src.optString("s"), item?.s)
            assertEquals(src.optLong("l"), item?.l)
        }
        run {
            val src = """{"s":"A","l":""}""".decodeJsonObject()
            val item = parseItem(src, ::TestEntity)
            assertNotNull(item)
            assertEquals(src.optString("s"), item?.s)
            assertEquals(src.optLong("l"), item?.l)
        }
        run {
            val src = """{"s":"A","l":100}""".decodeJsonObject()
            val item = parseItem(src, ::TestEntity)
            assertNotNull(item)
            assertEquals(src.optString("s"), item?.s)
            assertEquals(src.optLong("l"), item?.l)
        }
        run {
            val src = """{"s":"A","l":"100"}""".decodeJsonObject()
            val item = parseItem(src, ::TestEntity)
            assertNotNull(item)
            assertEquals(src.optString("s"), item?.s)
            assertEquals(src.optLong("l"), item?.l)
        }
    }

    @Test
    fun testParseList() {
        assertEquals(0, parseList(null, ::TestEntity).size)

        val src = JsonArray()
        assertEquals(0, parseList(src, ::TestEntity).size)

        src.add("""{"s":"A","l":"100"}""".decodeJsonObject())
        assertEquals(1, parseList(src, ::TestEntity).size)

        src.add("""{"s":"A","l":"100"}""".decodeJsonObject())
        assertEquals(2, parseList(src, ::TestEntity).size)

        // error
        src.add("""{"s":"","l":"100"}""".decodeJsonObject())
        assertEquals(2, parseList(src, ::TestEntity).size)
    }

    @Test
    fun testParseListOrNull() {
        assertEquals(null, parseListOrNull(null, ::TestEntity))

        val src = JsonArray()
        assertEquals(null, parseListOrNull(src, ::TestEntity))

        src.add("""{"s":"A","l":"100"}""".decodeJsonObject())
        assertEquals(1, parseListOrNull(src, ::TestEntity)?.size)

        src.add("""{"s":"A","l":"100"}""".decodeJsonObject())
        assertEquals(2, parseListOrNull(src, ::TestEntity)?.size)

        // error
        src.add("""{"s":"","l":"100"}""".decodeJsonObject())
        assertEquals(2, parseListOrNull(src, ::TestEntity)?.size)
    }

    @Test
    fun testParseMap() {
        assertEquals(0, parseMap(null, ::TestEntity).size)

        val src = JsonArray()
        assertEquals(0, parseMap(null, ::TestEntity).size)

        src.add("""{"s":"A","l":"100"}""".decodeJsonObject())
        assertEquals(1, parseMap(src, ::TestEntity).size)

        src.add("""{"s":"B","l":"100"}""".decodeJsonObject())
        assertEquals(2, parseMap(src, ::TestEntity).size)

        // error
        src.add("""{"s":"","l":"100"}""".decodeJsonObject())
        assertEquals(2, parseMap(src, ::TestEntity).size)
    }

    @Test
    fun testParseMapOrNull() {
        assertEquals(null, parseMapOrNull(null, ::TestEntity))

        val src = JsonArray()
        assertEquals(null, parseMapOrNull(src, ::TestEntity))

        src.add("""{"s":"A","l":"100"}""".decodeJsonObject())
        assertEquals(1, parseMapOrNull(src, ::TestEntity)?.size)

        src.add("""{"s":"B","l":"100"}""".decodeJsonObject())
        assertEquals(2, parseMapOrNull(src, ::TestEntity)?.size)

        // error
        src.add("""{"s":"","l":"100"}""".decodeJsonObject())
        assertEquals(2, parseMapOrNull(src, ::TestEntity)?.size)
    }

    private val parser by lazy {
        TootParser(
            InstrumentationRegistry.getInstrumentation().targetContext,
            SavedAccount.na,
        )
    }

    @Test
    fun testParseItemWithParser() {

        assertEquals(null, parseItem(null) { TestEntity(parser, it) })

        run {
            val src = """{"s":null,"l":"100"}""".decodeJsonObject()
            val item = parseItem(src) { TestEntity(parser, it) }
            assertNull(item)
        }
        run {
            val src = """{"s":"","l":"100"}""".decodeJsonObject()
            val item = parseItem(src) { TestEntity(parser, it) }
            assertNull(item)
        }
        run {
            val src = """{"s":"A","l":null}""".decodeJsonObject()
            val item = parseItem(src) { TestEntity(parser, it) }
            assertNotNull(item)
            assertEquals(src.optString("s"), item?.s)
            assertEquals(src.optLong("l"), item?.l)
        }
        run {
            val src = """{"s":"A","l":""}""".decodeJsonObject()
            val item = parseItem(src) { TestEntity(parser, it) }
            assertNotNull(item)
            assertEquals(src.optString("s"), item?.s)
            assertEquals(src.optLong("l"), item?.l)
        }
        run {
            val src = """{"s":"A","l":100}""".decodeJsonObject()
            val item = parseItem(src) { TestEntity(parser, it) }
            assertNotNull(item)
            assertEquals(src.optString("s"), item?.s)
            assertEquals(src.optLong("l"), item?.l)
        }
        run {
            val src = """{"s":"A","l":"100"}""".decodeJsonObject()
            val item = parseItem(src) { TestEntity(parser, it) }
            assertNotNull(item)
            assertEquals(src.optString("s"), item?.s)
            assertEquals(src.optLong("l"), item?.l)
        }
    }

    @Test
    fun testParseListWithParser() {
        assertEquals(0, parseList(null) { TestEntity(parser, it) }.size)

        val src = JsonArray()
        assertEquals(0, parseList(src) { TestEntity(parser, it) }.size)

        src.add("""{"s":"A","l":"100"}""".decodeJsonObject())
        assertEquals(1, parseList(src) { TestEntity(parser, it) }.size)

        src.add("""{"s":"A","l":"100"}""".decodeJsonObject())
        assertEquals(2, parseList(src) { TestEntity(parser, it) }.size)

        // error
        src.add("""{"s":"","l":"100"}""".decodeJsonObject())
        assertEquals(2, parseList(src) { TestEntity(parser, it) }.size)
    }

    @Test
    fun testParseListOrNullWithParser() {
        assertEquals(null, parseListOrNull(null) { TestEntity(parser, it) })

        val src = JsonArray()
        assertEquals(null, parseListOrNull(src) { TestEntity(parser, it) })

        src.add("""{"s":"A","l":"100"}""".decodeJsonObject())
        assertEquals(1, parseListOrNull(src) { TestEntity(parser, it) }?.size)

        src.add("""{"s":"A","l":"100"}""".decodeJsonObject())
        assertEquals(2, parseListOrNull(src) { TestEntity(parser, it) }?.size)

        // error
        src.add("""{"s":"","l":"100"}""".decodeJsonObject())
        assertEquals(2, parseListOrNull(src) { TestEntity(parser, it)  }?.size)
    }

    @Test(expected = RuntimeException::class)
    fun testNotEmptyOrThrow1() {
        println(notEmptyOrThrow("param1", null))
    }

    @Test(expected = RuntimeException::class)
    fun testNotEmptyOrThrow2() {
        println(notEmptyOrThrow("param1", ""))
    }

    @Test
    fun testNotEmptyOrThrow3() {
        assertEquals("A", notEmptyOrThrow("param1", "A"))
    }

    @Test(expected = RuntimeException::class)
    fun testNotEmptyOrThrow4() {
        println("""{"param1":null}""".decodeJsonObject().stringOrThrow("param1"))
    }

    @Test(expected = RuntimeException::class)
    fun testNotEmptyOrThrow5() {
        println("""{"param1":""}""".decodeJsonObject().stringOrThrow("param1"))
    }

    @Test
    fun testNotEmptyOrThrow6() {
        assertEquals("A", """{"param1":"A"}""".decodeJsonObject().stringOrThrow("param1"))
    }
}