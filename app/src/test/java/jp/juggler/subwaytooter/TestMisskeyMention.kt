package jp.juggler.subwaytooter

import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.util.asciiPattern
import jp.juggler.util.asciiPatternInternal
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class TestMisskeyMention {
	
	//	@Test
	//	fun test1(){
	//		fun findMention(str:String):String?{
	//			val m = TootAccount.reMention.matcher(str)
	//			return if(m.find()) m.group(0) else null
	//		}
	//		assertEquals(null,findMention(""))
	//		assertEquals(null,findMention("tateisu"))
	//		assertEquals("@tateisu",findMention("@tateisu"))
	//		assertEquals("@tateisu",findMention("@tateisuほげ"))
	//		assertEquals("@tateisu@mastodon.juggler.jp",findMention("@tateisu@mastodon.juggler.jp"))
	//		assertEquals("@tateisu@mastodon.juggler.jp",findMention("@tateisu@mastodon.juggler.jpほげ"))
	//		assertEquals("@tateisu",findMention("@tateisu@マストドン3.juggler.jp"))
	//		assertEquals("@tateisu@xn--3-pfuzbe6htf.juggler.jp",findMention("@tateisu@xn--3-pfuzbe6htf.juggler.jp"))
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
}