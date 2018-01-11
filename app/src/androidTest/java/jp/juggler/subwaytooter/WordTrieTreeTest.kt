package jp.juggler.subwaytooter

import android.support.test.runner.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import jp.juggler.subwaytooter.util.CharacterGroup
import jp.juggler.subwaytooter.util.WordTrieTree

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

/**
 * Instrumentation test, which will execute on an Android device.
 *
 * @see [Testing documentation](http://d.android.com/tools/testing)
 */
@RunWith(AndroidJUnit4::class)
class WordTrieTreeTest {
	
	@Test
	@Throws(Exception::class)
	fun testCharacterGroupTokenizer() {
		
		val cg = CharacterGroup()
		
		val whitespace = String(whitespace_chars)
		val whitespace_len = whitespace.length
		var id : Int
		
		run {
			// トークナイザーに空白だけの文字列を与えたら、next() 一回で終端になる。offsetは0のままである。
			val tokenizer = cg.tokenizer().reset(whitespace, 0, whitespace.length)
			id = tokenizer.next()
			assertEquals(CharacterGroup.END, id)
			assertEquals(0, tokenizer.offset.toLong())
		}
		
		run {
			// トークナイザーに 空白+ABC+空白 を与えたら、A,B,C,終端になる。
			val strTest = whitespace + "ABC" + whitespace
			val tokenizer = cg.tokenizer().reset(strTest, 0, strTest.length)
			//
			id = tokenizer.next()
			assertEquals('A'.toInt(), id)
			assertEquals((whitespace_len + 1), tokenizer.offset) // offset は Aの次の位置になる
			//
			id = tokenizer.next()
			assertEquals('B'.toInt(), id)
			assertEquals((whitespace_len + 2).toLong(), tokenizer.offset.toLong())
			//
			id = tokenizer.next()
			assertEquals('C'.toInt(), id)
			assertEquals((whitespace_len + 3).toLong(), tokenizer.offset.toLong())
			//
			id = tokenizer.next()
			assertEquals((whitespace_len + 3).toLong(), tokenizer.offset.toLong()) // offsetはCの次の位置のまま
			assertEquals(CharacterGroup.END, id)
		}
		
		run {
			// トークナイザーに 空白+abc+空白 を与えたら、A,B,C,終端になる。
			val strTest = whitespace + "abc" + whitespace
			val tokenizer = cg.tokenizer().reset(strTest, 0, strTest.length)
			//
			id = tokenizer.next()
			assertEquals((whitespace_len + 1).toLong(), tokenizer.offset.toLong())
			assertEquals('A'.toInt(), id)
			//
			id = tokenizer.next()
			assertEquals((whitespace_len + 2).toLong(), tokenizer.offset.toLong())
			assertEquals('B'.toInt(), id)
			//
			id = tokenizer.next()
			assertEquals((whitespace_len + 3).toLong(), tokenizer.offset.toLong())
			assertEquals('C'.toInt(), id)
			//
			id = tokenizer.next()
			assertEquals((whitespace_len + 3).toLong(), tokenizer.offset.toLong())
			assertEquals(CharacterGroup.END, id)
		}
		
		run {
			// トークナイザーに 空白+ＡＢＣ+空白 を与えたら、A,B,C,終端になる。
			val strTest = whitespace + "ＡＢＣ" + whitespace
			val tokenizer = cg.tokenizer().reset(strTest, 0, strTest.length)
			//
			id = tokenizer.next()
			assertEquals((whitespace_len + 1).toLong(), tokenizer.offset.toLong())
			assertEquals('A'.toInt(), id)
			//
			id = tokenizer.next()
			assertEquals((whitespace_len + 2).toLong(), tokenizer.offset.toLong())
			assertEquals('B'.toInt(), id)
			//
			id = tokenizer.next()
			assertEquals((whitespace_len + 3).toLong(), tokenizer.offset.toLong())
			assertEquals('C'.toInt(), id)
			//
			id = tokenizer.next()
			assertEquals((whitespace_len + 3).toLong(), tokenizer.offset.toLong())
			assertEquals(CharacterGroup.END, id)
		}
		
		run {
			// トークナイザーに 空白+ａｂｃ+空白 を与えたら、A,B,C,終端になる。
			val strTest = whitespace + "ａｂｃ" + whitespace
			val tokenizer = cg.tokenizer().reset(strTest, 0, strTest.length)
			//
			id = tokenizer.next()
			assertEquals((whitespace_len + 1).toLong(), tokenizer.offset.toLong())
			assertEquals('A'.toInt(), id)
			//
			id = tokenizer.next()
			assertEquals((whitespace_len + 2).toLong(), tokenizer.offset.toLong())
			assertEquals('B'.toInt(), id)
			//
			id = tokenizer.next()
			assertEquals((whitespace_len + 3).toLong(), tokenizer.offset.toLong())
			assertEquals('C'.toInt(), id)
			//
			id = tokenizer.next()
			assertEquals((whitespace_len + 3).toLong(), tokenizer.offset.toLong())
			assertEquals(CharacterGroup.END, id)
		}
	}
	
	@Test
	@Throws(Exception::class)
	fun testWordTrieTree() {
		
		var strTest : String
		
		run {
			val trie = WordTrieTree()
			trie.add("")
			trie.add(" ")  // 単語の側に空白があっても無視される
			trie.add("ABC")
			trie.add("abc")
			trie.add("abcdef")
			trie.add("bbb")
			trie.add("C C C") // 単語の側に空白があっても無視される
			trie.add("ccc")
			
			// 空文字列や空白を登録してもマッチしない
			// 登録していない単語にマッチしない
			assertEquals(false, trie.matchShort("ZZZ"))
			
			// 登録した文字列にマッチする
			assertEquals(true, trie.matchShort("abc"))
			assertEquals(true, trie.matchShort("abcdef"))
			
			// 単語の間に空白があってもマッチする
			strTest = "///abcdef///a b c///bb b///c cc  "
			val list = trie.matchList(strTest)
			assertNotNull(list)
			assertEquals(4, list !!.size.toLong())
			assertEquals("abcdef", list[0].word) // abcよりもabcdefを優先してマッチする
			assertEquals(3, list[0].start.toLong()) // 元テキスト中でマッチした位置を取得できる
			assertEquals(9, list[0].end.toLong())
			assertEquals("ABC", list[1].word) // 文字種が違っても同一とみなす単語の場合、先に登録した方にマッチする
			assertEquals("bbb", list[2].word)
			assertEquals("C C C", list[3].word) // 文字種が違っても同一とみなす単語の場合、先に登録した方にマッチする
			assertEquals(27, list[3].start.toLong()) // 元テキスト中でマッチした位置を取得できる
			assertEquals(31, list[3].end.toLong())
			assertEquals(33, strTest.length.toLong()) // 末尾の空白はマッチ範囲には含まれない
		}
	}
	
	companion object {
		
		private val whitespace_chars = charArrayOf(0x0009.toChar(), 0x000A.toChar(), 0x000B.toChar(), 0x000C.toChar(), 0x000D.toChar(), 0x001C.toChar(), 0x001D.toChar(), 0x001E.toChar(), 0x001F.toChar(), 0x0020.toChar(), 0x0085.toChar(), 0x00A0.toChar(), 0x1680.toChar(), 0x180E.toChar(), 0x2000.toChar(), 0x2001.toChar(), 0x2002.toChar(), 0x2003.toChar(), 0x2004.toChar(), 0x2005.toChar(), 0x2006.toChar(), 0x2007.toChar(), 0x2008.toChar(), 0x2009.toChar(), 0x200A.toChar(), 0x200B.toChar(), 0x200C.toChar(), 0x200D.toChar(), 0x2028.toChar(), 0x2029.toChar(), 0x202F.toChar(), 0x205F.toChar(), 0x2060.toChar(), 0x3000.toChar(), 0x3164.toChar(), 0xFEFF.toChar())
	}
}
