package jp.juggler.subwaytooter;

import org.junit.Test;


import jp.juggler.subwaytooter.util.WordTrieTree;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
	@Test
	public void addition_isCorrect() throws Exception{
		assertEquals( 4, 2 + 2 );
	}
	
	@Test
	public void checkWordTrieTree() throws Exception{
		WordTrieTree wtt = new WordTrieTree();
		
		assertEquals( false, wtt.containsWord(  null) );
		assertEquals( false, wtt.containsWord( "") );
		assertEquals( false, wtt.containsWord(  "1") );
		
		wtt.add("abc");
		wtt.add("abd");
		wtt.add("def");
		assertEquals( false, wtt.containsWord( null) );
		assertEquals( false, wtt.containsWord( "") );
		assertEquals( false, wtt.containsWord( "1") );
		
		assertEquals( false, wtt.containsWord( "a") );
		assertEquals( false, wtt.containsWord( "ab") );
		assertEquals( true, wtt.containsWord( "abc") );
		assertEquals( true, wtt.containsWord( "   abc   ") );
		assertEquals( true, wtt.containsWord( "abd") );
		assertEquals( true, wtt.containsWord( "   abd   ") );
		assertEquals( false, wtt.containsWord( "abe") );
		assertEquals( false, wtt.containsWord( "   abe   ") );
		
		assertEquals( false, wtt.containsWord( "d") );
		assertEquals( false, wtt.containsWord( "de") );
		assertEquals( true, wtt.containsWord( "def") );
		
	}
}