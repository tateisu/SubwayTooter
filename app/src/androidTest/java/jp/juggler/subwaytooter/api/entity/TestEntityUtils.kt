package jp.juggler.subwaytooter.api.entity

import android.support.test.runner.AndroidJUnit4
import android.test.mock.MockContext
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.Utils
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
			l = Utils.optLongX(src, "l")
		)
		
		@Suppress("UNUSED_PARAMETER")
		constructor(parser : TootParser, src : JSONObject) : this(
			s = src.notEmptyOrThrow("s"),
			l = Utils.optLongX(src, "l")
		)
		
		override val mapKey : String
			get() = s
	}
	
	@Test
	fun testParseItem() {
		assertEquals(null, parseItem(::TestEntity, null))
		
		run {
			val src = JSONObject("""{"s":null,"l":"100"}""")
			val item = parseItem(::TestEntity, src)
			assertNull(item)
		}
		run {
			val src = JSONObject("""{"s":"","l":"100"}""")
			val item = parseItem(::TestEntity, src)
			assertNull(item)
		}
		run {
			val src = JSONObject("""{"s":"A","l":null}""")
			val item = parseItem(::TestEntity, src)
			assertNotNull(item)
			assertEquals(src.optString("s"), item?.s)
			assertEquals(src.optLong("l"), item?.l)
		}
		run {
			val src = JSONObject("""{"s":"A","l":""}""")
			val item = parseItem(::TestEntity, src)
			assertNotNull(item)
			assertEquals(src.optString("s"), item?.s)
			assertEquals(src.optLong("l"), item?.l)
		}
		run {
			val src = JSONObject("""{"s":"A","l":100}""")
			val item = parseItem(::TestEntity, src)
			assertNotNull(item)
			assertEquals(src.optString("s"), item?.s)
			assertEquals(src.optLong("l"), item?.l)
		}
		run {
			val src = JSONObject("""{"s":"A","l":"100"}""")
			val item = parseItem(::TestEntity, src)
			assertNotNull(item)
			assertEquals(src.optString("s"), item?.s)
			assertEquals(src.optLong("l"), item?.l)
		}
	}
	
	@Test
	fun TestParseList() {
		assertEquals(0, parseList(::TestEntity, null).size)
		
		val src = JSONArray()
		assertEquals(0, parseList(::TestEntity, src).size)
		
		src.put(JSONObject("""{"s":"A","l":"100"}"""))
		assertEquals(1, parseList(::TestEntity, src).size)
		
		src.put(JSONObject("""{"s":"A","l":"100"}"""))
		assertEquals(2, parseList(::TestEntity, src).size)
		
		// error
		src.put(JSONObject("""{"s":"","l":"100"}"""))
		assertEquals(2, parseList(::TestEntity, src).size)
		
	}
	
	@Test
	fun TestParseListOrNull() {
		assertEquals(null, parseListOrNull(::TestEntity, null))
		
		val src = JSONArray()
		assertEquals(null, parseListOrNull(::TestEntity, src))
		
		src.put(JSONObject("""{"s":"A","l":"100"}"""))
		assertEquals(1, parseListOrNull(::TestEntity, src)?.size)
		
		src.put(JSONObject("""{"s":"A","l":"100"}"""))
		assertEquals(2, parseListOrNull(::TestEntity, src)?.size)
		
		// error
		src.put(JSONObject("""{"s":"","l":"100"}"""))
		assertEquals(2, parseListOrNull(::TestEntity, src)?.size)
		
	}
	
	@Test
	fun TestParseMap() {
		assertEquals(0, parseMap(::TestEntity, null).size)
		
		val src = JSONArray()
		assertEquals(0, parseMap(::TestEntity, src).size)
		
		src.put(JSONObject("""{"s":"A","l":"100"}"""))
		assertEquals(1, parseMap(::TestEntity, src).size)
		
		src.put(JSONObject("""{"s":"B","l":"100"}"""))
		assertEquals(2, parseMap(::TestEntity, src).size)
		
		// error
		src.put(JSONObject("""{"s":"","l":"100"}"""))
		assertEquals(2, parseMap(::TestEntity, src).size)
		
	}
	
	@Test
	fun TestParseMapOrNull() {
		assertEquals(null, parseMapOrNull(::TestEntity, null))
		
		val src = JSONArray()
		assertEquals(null, parseMapOrNull(::TestEntity, src))
		
		src.put(JSONObject("""{"s":"A","l":"100"}"""))
		assertEquals(1, parseMapOrNull(::TestEntity, src)?.size)
		
		src.put(JSONObject("""{"s":"B","l":"100"}"""))
		assertEquals(2, parseMapOrNull(::TestEntity, src)?.size)
		
		// error
		src.put(JSONObject("""{"s":"","l":"100"}"""))
		assertEquals(2, parseMapOrNull(::TestEntity, src)?.size)
		
	}
	
	private val parser = TootParser(MockContext(), SavedAccount.na)
	
	@Test
	fun testParseItemWithParser() {
		
		assertEquals(null, parseItem(::TestEntity, parser, null))
		
		run {
			val src = JSONObject("""{"s":null,"l":"100"}""")
			val item = parseItem(::TestEntity, parser, src)
			assertNull(item)
		}
		run {
			val src = JSONObject("""{"s":"","l":"100"}""")
			val item = parseItem(::TestEntity, parser, src)
			assertNull(item)
		}
		run {
			val src = JSONObject("""{"s":"A","l":null}""")
			val item = parseItem(::TestEntity, parser, src)
			assertNotNull(item)
			assertEquals(src.optString("s"), item?.s)
			assertEquals(src.optLong("l"), item?.l)
		}
		run {
			val src = JSONObject("""{"s":"A","l":""}""")
			val item = parseItem(::TestEntity, parser, src)
			assertNotNull(item)
			assertEquals(src.optString("s"), item?.s)
			assertEquals(src.optLong("l"), item?.l)
		}
		run {
			val src = JSONObject("""{"s":"A","l":100}""")
			val item = parseItem(::TestEntity, parser, src)
			assertNotNull(item)
			assertEquals(src.optString("s"), item?.s)
			assertEquals(src.optLong("l"), item?.l)
		}
		run {
			val src = JSONObject("""{"s":"A","l":"100"}""")
			val item = parseItem(::TestEntity, parser, src)
			assertNotNull(item)
			assertEquals(src.optString("s"), item?.s)
			assertEquals(src.optLong("l"), item?.l)
		}
	}
	
	@Test
	fun TestParseListWithParser() {
		assertEquals(0, parseList(::TestEntity, parser, null).size)
		
		val src = JSONArray()
		assertEquals(0, parseList(::TestEntity, parser, src).size)
		
		src.put(JSONObject("""{"s":"A","l":"100"}"""))
		assertEquals(1, parseList(::TestEntity, parser, src).size)
		
		src.put(JSONObject("""{"s":"A","l":"100"}"""))
		assertEquals(2, parseList(::TestEntity, parser, src).size)
		
		// error
		src.put(JSONObject("""{"s":"","l":"100"}"""))
		assertEquals(2, parseList(::TestEntity, parser, src).size)
		
	}
	
	@Test
	fun TestParseListOrNullWithParser() {
		assertEquals(null, parseListOrNull(::TestEntity, parser, null))
		
		val src = JSONArray()
		assertEquals(null, parseListOrNull(::TestEntity, parser, src))
		
		src.put(JSONObject("""{"s":"A","l":"100"}"""))
		assertEquals(1, parseListOrNull(::TestEntity, parser, src)?.size)
		
		src.put(JSONObject("""{"s":"A","l":"100"}"""))
		assertEquals(2, parseListOrNull(::TestEntity, parser, src)?.size)
		
		// error
		src.put(JSONObject("""{"s":"","l":"100"}"""))
		assertEquals(2, parseListOrNull(::TestEntity, parser, src)?.size)
		
	}
	
	@Test(expected = RuntimeException::class)
	fun TestNotEmptyOrThrow1() {
		println(notEmptyOrThrow("param1", null))
	}
	
	@Test(expected = RuntimeException::class)
	fun TestNotEmptyOrThrow2() {
		println(notEmptyOrThrow("param1", ""))
	}
	
	@Test
	fun TestNotEmptyOrThrow3() {
		assertEquals("A", notEmptyOrThrow("param1", "A"))
	}
	
	@Test(expected = RuntimeException::class)
	fun TestNotEmptyOrThrow4() {
		println(JSONObject("""{"param1":null}""").notEmptyOrThrow("param1"))
	}
	
	@Test(expected = RuntimeException::class)
	fun TestNotEmptyOrThrow5() {
		println(JSONObject("""{"param1":""}""").notEmptyOrThrow("param1"))
	}
	
	@Test
	fun TestNotEmptyOrThrow6() {
		assertEquals("A", JSONObject("""{"param1":"A"}""").notEmptyOrThrow("param1"))
	}
}