package jp.juggler.subwaytooter;

import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

import jp.juggler.subwaytooter.util.CharacterGroup;
import jp.juggler.subwaytooter.util.WordTrieTree;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Instrumentation test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@SuppressWarnings({ "PointlessArithmeticExpression", "UnnecessaryLocalVariable" }) @RunWith(AndroidJUnit4.class)
public class WordTrieTreeTest {
	
	private static final char[] whitespace_chars = new char[]{
		0x0009,
		0x000A,
		0x000B,
		0x000C,
		0x000D,
		0x001C,
		0x001D,
		0x001E,
		0x001F,
		0x0020,
		0x0085,
		0x00A0,
		0x1680,
		0x180E,
		0x2000,
		0x2001,
		0x2002,
		0x2003,
		0x2004,
		0x2005,
		0x2006,
		0x2007,
		0x2008,
		0x2009,
		0x200A,
		0x200B,
		0x200C,
		0x200D,
		0x2028,
		0x2029,
		0x202F,
		0x205F,
		0x2060,
		0x3000,
		0x3164,
		0xFEFF,
	};
	
	@Test public void testCharacterGroupTokenizer() throws Exception{
		
		CharacterGroup cg = new CharacterGroup();
		
		String whitespace = new String( whitespace_chars );
		final int whitespace_len = whitespace.length();
		int id;
		
		{
			// トークナイザーに空白だけの文字列を与えたら、next() 一回で終端になる。offsetは0のままである。
			String strTest = whitespace;
			CharacterGroup.Tokenizer tokenizer = cg.tokenizer( strTest, 0, strTest.length() );
			id = tokenizer.next();
			assertEquals( CharacterGroup.END, id );
			assertEquals( 0, tokenizer.offset );
		}
		
		{
			// トークナイザーに 空白+ABC+空白 を与えたら、A,B,C,終端になる。
			String strTest = whitespace + "ABC" + whitespace;
			CharacterGroup.Tokenizer tokenizer = cg.tokenizer( strTest, 0, strTest.length() );
			//
			id = tokenizer.next();
			assertEquals( 'A', id );
			assertEquals( whitespace_len + 1, tokenizer.offset ); // offset は Aの次の位置になる
			//
			id = tokenizer.next();
			assertEquals( 'B', id );
			assertEquals( whitespace_len + 2, tokenizer.offset );
			//
			id = tokenizer.next();
			assertEquals( 'C', id );
			assertEquals( whitespace_len + 3, tokenizer.offset );
			//
			id = tokenizer.next();
			assertEquals( whitespace_len + 3, tokenizer.offset ); // offsetはCの次の位置のまま
			assertEquals( CharacterGroup.END, id );
		}
		
		{
			// トークナイザーに 空白+abc+空白 を与えたら、A,B,C,終端になる。
			String strTest = whitespace + "abc" + whitespace;
			CharacterGroup.Tokenizer tokenizer = cg.tokenizer( strTest, 0, strTest.length() );
			//
			id = tokenizer.next();
			assertEquals( whitespace_len + 1, tokenizer.offset );
			assertEquals( 'A', id );
			//
			id = tokenizer.next();
			assertEquals( whitespace_len + 2, tokenizer.offset );
			assertEquals( 'B', id );
			//
			id = tokenizer.next();
			assertEquals( whitespace_len + 3, tokenizer.offset );
			assertEquals( 'C', id );
			//
			id = tokenizer.next();
			assertEquals( whitespace_len + 3, tokenizer.offset );
			assertEquals( CharacterGroup.END, id );
		}
		
		{
			// トークナイザーに 空白+ＡＢＣ+空白 を与えたら、A,B,C,終端になる。
			String strTest = whitespace + "ＡＢＣ" + whitespace;
			CharacterGroup.Tokenizer tokenizer = cg.tokenizer( strTest, 0, strTest.length() );
			//
			id = tokenizer.next();
			assertEquals( whitespace_len + 1, tokenizer.offset );
			assertEquals( 'A', id );
			//
			id = tokenizer.next();
			assertEquals( whitespace_len + 2, tokenizer.offset );
			assertEquals( 'B', id );
			//
			id = tokenizer.next();
			assertEquals( whitespace_len + 3, tokenizer.offset );
			assertEquals( 'C', id );
			//
			id = tokenizer.next();
			assertEquals( whitespace_len + 3, tokenizer.offset );
			assertEquals( CharacterGroup.END, id );
		}
		
		{
			// トークナイザーに 空白+ａｂｃ+空白 を与えたら、A,B,C,終端になる。
			String strTest = whitespace + "ａｂｃ" + whitespace;
			CharacterGroup.Tokenizer tokenizer = cg.tokenizer( strTest, 0, strTest.length() );
			//
			id = tokenizer.next();
			assertEquals( whitespace_len + 1, tokenizer.offset );
			assertEquals( 'A', id );
			//
			id = tokenizer.next();
			assertEquals( whitespace_len + 2, tokenizer.offset );
			assertEquals( 'B', id );
			//
			id = tokenizer.next();
			assertEquals( whitespace_len + 3, tokenizer.offset );
			assertEquals( 'C', id );
			//
			id = tokenizer.next();
			assertEquals( whitespace_len + 3, tokenizer.offset );
			assertEquals( CharacterGroup.END, id );
		}
	}
	
	@Test public void testWordTrieTree() throws Exception{
		
		String strTest;
		
		{
			WordTrieTree trie = new WordTrieTree();
			trie.add( "" );
			trie.add( " " );  // 単語の側に空白があっても無視される
			trie.add( "ABC" );
			trie.add( "abc" );
			trie.add( "abcdef" );
			trie.add( "bbb" );
			trie.add( "C C C" ); // 単語の側に空白があっても無視される
			trie.add( "ccc" );
			
			// 空文字列や空白を登録してもマッチしない
			// 登録していない単語にマッチしない
			assertEquals( false, trie.matchShort( "ZZZ" ) );
			
			// 登録した文字列にマッチする
			assertEquals( true, trie.matchShort( "abc" ) );
			assertEquals( true, trie.matchShort( "abcdef" ) );
			
			// 単語の間に空白があってもマッチする
			strTest = "///abcdef///a b c///bb b///c cc  ";
			ArrayList< WordTrieTree.Match > list = trie.matchList( strTest);
			assertNotNull( list );
			assertEquals( 4, list.size() );
			assertEquals( "abcdef", list.get( 0 ).word ); // abcよりもabcdefを優先してマッチする
			assertEquals( 3, list.get( 0 ).start ); // 元テキスト中でマッチした位置を取得できる
			assertEquals( 9, list.get( 0 ).end );
			assertEquals( "ABC", list.get( 1 ).word ); // 文字種が違っても同一とみなす単語の場合、先に登録した方にマッチする
			assertEquals( "bbb", list.get( 2 ).word );
			assertEquals( "C C C", list.get( 3 ).word ); // 文字種が違っても同一とみなす単語の場合、先に登録した方にマッチする
			assertEquals( 27, list.get( 3 ).start ); // 元テキスト中でマッチした位置を取得できる
			assertEquals( 31, list.get( 3 ).end );
			assertEquals( 33, strTest.length() ); // 末尾の空白はマッチ範囲には含まれない
		}
	}
}
