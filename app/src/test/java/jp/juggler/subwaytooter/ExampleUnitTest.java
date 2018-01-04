package jp.juggler.subwaytooter;

import org.junit.Test;

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
	
	/*
		test は開発環境側で行われて、実機が必要なAPIは"not mocked"と怒られて失敗する。
		SparseIntArray等を利用できない。
		
		androidTest の方は実機かエミュレータで動作する
	 */
}