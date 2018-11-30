package jp.juggler.subwaytooter.api.entity

import android.support.test.runner.AndroidJUnit4
import android.test.mock.MockContext
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.notEmptyOrThrow
import jp.juggler.util.parseLong
import jp.juggler.util.toJsonObject
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.runner.RunWith

import org.junit.Test

@RunWith(AndroidJUnit4::class)
class TestEntityUtils {
	
	class TestEntity(val s : String, val l : Long) : Mappable<String> {
		constructor(src : JSONObject) : this(
			s = src.notEmptyOrThrow("s"),
			l = src.parseLong("l") ?: -1L
		)
		
		@Suppress("UNUSED_PARAMETER")
		constructor(parser : TootParser, src : JSONObject) : this(
			s = src.notEmptyOrThrow("s"),
			l = src.parseLong("l") ?: -1L
		)
		
		override val mapKey : String
			get() = s
	}
	
	@Test
	fun testParseItem() {
		assertEquals(null, parseItem(::TestEntity, null))
		
		run {
			val src = """{"s":null,"l":"100"}""".toJsonObject()
			val item = parseItem(::TestEntity, src)
			assertNull(item)
		}
		run {
			val src = """{"s":"","l":"100"}""".toJsonObject()
			val item = parseItem(::TestEntity, src)
			assertNull(item)
		}
		run {
			val src = """{"s":"A","l":null}""".toJsonObject()
			val item = parseItem(::TestEntity, src)
			assertNotNull(item)
			assertEquals(src.optString("s"), item?.s)
			assertEquals(src.optLong("l"), item?.l)
		}
		run {
			val src = """{"s":"A","l":""}""".toJsonObject()
			val item = parseItem(::TestEntity, src)
			assertNotNull(item)
			assertEquals(src.optString("s"), item?.s)
			assertEquals(src.optLong("l"), item?.l)
		}
		run {
			val src = """{"s":"A","l":100}""".toJsonObject()
			val item = parseItem(::TestEntity, src)
			assertNotNull(item)
			assertEquals(src.optString("s"), item?.s)
			assertEquals(src.optLong("l"), item?.l)
		}
		run {
			val src ="""{"s":"A","l":"100"}""".toJsonObject()
			val item = parseItem(::TestEntity, src)
			assertNotNull(item)
			assertEquals(src.optString("s"), item?.s)
			assertEquals(src.optLong("l"), item?.l)
		}
	}
	
	@Test
	fun testParseList() {
		assertEquals(0, parseList(::TestEntity, null).size)
		
		val src = JSONArray()
		assertEquals(0, parseList(::TestEntity, src).size)
		
		src.put("""{"s":"A","l":"100"}""".toJsonObject())
		assertEquals(1, parseList(::TestEntity, src).size)
		
		src.put("""{"s":"A","l":"100"}""".toJsonObject())
		assertEquals(2, parseList(::TestEntity, src).size)
		
		// error
		src.put("""{"s":"","l":"100"}""".toJsonObject())
		assertEquals(2, parseList(::TestEntity, src).size)
		
	}
	
	@Test
	fun testParseListOrNull() {
		assertEquals(null, parseListOrNull(::TestEntity, null))
		
		val src = JSONArray()
		assertEquals(null, parseListOrNull(::TestEntity, src))
		
		src.put("""{"s":"A","l":"100"}""".toJsonObject())
		assertEquals(1, parseListOrNull(::TestEntity, src)?.size)
		
		src.put("""{"s":"A","l":"100"}""".toJsonObject())
		assertEquals(2, parseListOrNull(::TestEntity, src)?.size)
		
		// error
		src.put("""{"s":"","l":"100"}""".toJsonObject())
		assertEquals(2, parseListOrNull(::TestEntity, src)?.size)
		
	}
	
	@Test
	fun testParseMap() {
		assertEquals(0, parseMap(::TestEntity, null).size)
		
		val src = JSONArray()
		assertEquals(0, parseMap(::TestEntity, src).size)
		
		src.put("""{"s":"A","l":"100"}""".toJsonObject())
		assertEquals(1, parseMap(::TestEntity, src).size)
		
		src.put("""{"s":"B","l":"100"}""".toJsonObject())
		assertEquals(2, parseMap(::TestEntity, src).size)
		
		// error
		src.put("""{"s":"","l":"100"}""".toJsonObject())
		assertEquals(2, parseMap(::TestEntity, src).size)
		
	}
	
	@Test
	fun testParseMapOrNull() {
		assertEquals(null, parseMapOrNull(::TestEntity, null))
		
		val src = JSONArray()
		assertEquals(null, parseMapOrNull(::TestEntity, src))
		
		src.put("""{"s":"A","l":"100"}""".toJsonObject())
		assertEquals(1, parseMapOrNull(::TestEntity, src)?.size)
		
		src.put("""{"s":"B","l":"100"}""".toJsonObject())
		assertEquals(2, parseMapOrNull(::TestEntity, src)?.size)
		
		// error
		src.put("""{"s":"","l":"100"}""".toJsonObject())
		assertEquals(2, parseMapOrNull(::TestEntity, src)?.size)
		
	}
	
	private val parser = TootParser(MockContext(), SavedAccount.na)
	
	@Test
	fun testParseItemWithParser() {
		
		assertEquals(null, parseItem(::TestEntity, parser, null))
		
		run {
			val src ="""{"s":null,"l":"100"}""".toJsonObject()
			val item = parseItem(::TestEntity, parser, src)
			assertNull(item)
		}
		run {
			val src = """{"s":"","l":"100"}""".toJsonObject()
			val item = parseItem(::TestEntity, parser, src)
			assertNull(item)
		}
		run {
			val src = """{"s":"A","l":null}""".toJsonObject()
			val item = parseItem(::TestEntity, parser, src)
			assertNotNull(item)
			assertEquals(src.optString("s"), item?.s)
			assertEquals(src.optLong("l"), item?.l)
		}
		run {
			val src = """{"s":"A","l":""}""".toJsonObject()
			val item = parseItem(::TestEntity, parser, src)
			assertNotNull(item)
			assertEquals(src.optString("s"), item?.s)
			assertEquals(src.optLong("l"), item?.l)
		}
		run {
			val src = """{"s":"A","l":100}""".toJsonObject()
			val item = parseItem(::TestEntity, parser, src)
			assertNotNull(item)
			assertEquals(src.optString("s"), item?.s)
			assertEquals(src.optLong("l"), item?.l)
		}
		run {
			val src = """{"s":"A","l":"100"}""".toJsonObject()
			val item = parseItem(::TestEntity, parser, src)
			assertNotNull(item)
			assertEquals(src.optString("s"), item?.s)
			assertEquals(src.optLong("l"), item?.l)
		}
	}
	
	@Test
	fun testParseListWithParser() {
		assertEquals(0, parseList(::TestEntity, parser, null).size)
		
		val src = JSONArray()
		assertEquals(0, parseList(::TestEntity, parser, src).size)
		
		src.put("""{"s":"A","l":"100"}""".toJsonObject())
		assertEquals(1, parseList(::TestEntity, parser, src).size)
		
		src.put("""{"s":"A","l":"100"}""".toJsonObject())
		assertEquals(2, parseList(::TestEntity, parser, src).size)
		
		// error
		src.put("""{"s":"","l":"100"}""".toJsonObject())
		assertEquals(2, parseList(::TestEntity, parser, src).size)
		
	}
	
	@Test
	fun testParseListOrNullWithParser() {
		assertEquals(null, parseListOrNull(::TestEntity, parser, null))
		
		val src = JSONArray()
		assertEquals(null, parseListOrNull(::TestEntity, parser, src))
		
		src.put("""{"s":"A","l":"100"}""".toJsonObject())
		assertEquals(1, parseListOrNull(::TestEntity, parser, src)?.size)
		
		src.put("""{"s":"A","l":"100"}""".toJsonObject())
		assertEquals(2, parseListOrNull(::TestEntity, parser, src)?.size)
		
		// error
		src.put("""{"s":"","l":"100"}""".toJsonObject())
		assertEquals(2, parseListOrNull(::TestEntity, parser, src)?.size)
		
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
		println("""{"param1":null}""".toJsonObject().notEmptyOrThrow("param1"))
	}
	
	@Test(expected = RuntimeException::class)
	fun testNotEmptyOrThrow5() {
		println("""{"param1":""}""".toJsonObject().notEmptyOrThrow("param1"))
	}
	
	@Test
	fun testNotEmptyOrThrow6() {
		assertEquals("A", """{"param1":"A"}""".toJsonObject().notEmptyOrThrow("param1"))
	}
}