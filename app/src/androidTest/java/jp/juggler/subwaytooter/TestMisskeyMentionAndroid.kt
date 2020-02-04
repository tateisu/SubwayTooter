package jp.juggler.subwaytooter

import androidx.test.runner.AndroidJUnit4
import jp.juggler.util.asciiPattern
import jp.juggler.util.asciiPatternInternal
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

// Android instrumentation test は run configuration を編集しないと Empty tests とかいうエラーになります
@RunWith(AndroidJUnit4::class)
class TestMisskeyMentionAndroid {
	
	//	@Test
	//	@Throws(Exception::class)
	//	fun test1() {
	//		fun findMention(str:String):String?{
	//			val m = TootAccount.reMention.matcher(str)
	//			return if(m.find()) m.group(0) else null
	//		}
	//		assertEquals(null, findMention(""))
	//		assertEquals(null, findMention("tateisu"))
	//		assertEquals("@tateisu", findMention("@tateisu"))
	//		assertEquals("@tateisu", findMention("@tateisuほげ"))
	//		assertEquals(
	//			"@tateisu@mastodon.juggler.jp",
	//			findMention("@tateisu@mastodon.juggler.jp")
	//		)
	//		assertEquals(
	//			"@tateisu@mastodon.juggler.jp",
	//			findMention("@tateisu@mastodon.juggler.jpほげ")
	//		)
	//		assertEquals("@tateisu", findMention("@tateisu@マストドン3.juggler.jp"))
	//		assertEquals(
	//			"@tateisu@xn--3-pfuzbe6htf.juggler.jp",
	//			findMention("@tateisu@xn--3-pfuzbe6htf.juggler.jp")
	//		)
	//	}
	
	@Test
	@Throws(Exception::class)
	fun testAsciiPatternInternal() {
		// \w \d \W \D 以外の文字は素通しする
		assertEquals("""ab\c\\""", """ab\c\\""".asciiPatternInternal())
		assertEquals("""[A-Za-z0-9_]""", """\w""".asciiPatternInternal())
		assertEquals("""[A-Za-z0-9_-]""", """[\w-]""".asciiPatternInternal())
		assertEquals("""[^A-Za-z0-9_]""", """\W""".asciiPatternInternal())
		assertEquals("""[0-9]""", """\d""".asciiPatternInternal())
		assertEquals("""[0-9:-]""", """[\d:-]""".asciiPatternInternal())
		assertEquals("""[^0-9]""", """\D""".asciiPatternInternal())
		
		// 文字セットの中の \W \D は変換できないので素通しする
		assertEquals("""[\W]""", """[\W]""".asciiPatternInternal())
		assertEquals("""[\D]""", """[\D]""".asciiPatternInternal())
		
		// エスケープ文字の後に何もない場合も素通しする
		assertEquals("""\""", """\""".asciiPatternInternal())
	}
	
	@Test
	@Throws(Exception::class)
	fun test2() {
		// val pu = Pattern.compile("""\w+""",Pattern.UNICODE_CHARACTER_CLASS)
		// on Android: java.lang.IllegalArgumentException: Unsupported flags: 256
		
		fun matchOrNull(pattern : String, input : String) : String? {
			// no UNICODE_CHARACTER_CLASS
			val m = pattern.asciiPattern().matcher(input)
			return if(m.find()) m.group(0) else null
		}
		assertEquals(null, matchOrNull("\\w+", "-"))
		assertEquals(null, matchOrNull("\\w+", "あ"))
		assertEquals("a", matchOrNull("\\w+", "a"))
		assertEquals("a", matchOrNull("\\w+", "aあ"))
		
		assertEquals("0", matchOrNull("\\w+", "0"))
		assertEquals(null, matchOrNull("\\w+", "０"))
		
		assertEquals("0", matchOrNull("\\d+", "0"))
		assertEquals(null, matchOrNull("\\d+", "０"))
	}
	
}