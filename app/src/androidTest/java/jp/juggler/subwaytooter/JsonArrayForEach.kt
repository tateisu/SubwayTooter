package jp.juggler.subwaytooter

import androidx.test.runner.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import jp.juggler.util.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see [Testing documentation](http://d.android.com/tools/testing)
 */
@RunWith(AndroidJUnit4::class)
class JsonArrayForEach {
	
	@Test
	@Throws(Exception::class)
	fun test(){
		val array = JSONArray().apply{
			put("a")
			put("b")
			put( null)
			put( JSONObject.NULL)
		}
		
		var count = 0
		
		array.forEach {
			println("JSONArray.forEach $it")
			++count
		}
		
		array.forEachIndexed { i,v->
			println("JSONArray.forEachIndexed $i $v")
			++count
		}
		
		array.downForEach {
			println("JSONArray.downForEach $it")
			++count
		}
		
		array.downForEachIndexed { i,v->
			println("JSONArray.downForEachIndexed $i $v")
			++count
		}
		
		for( o in array.iterator() ){
			println("JSONArray.iterator $o")
			++count
		}
		for( o in array.reverseIterator() ){
			println("JSONArray.reverseIterator $o")
			++count
		}
		
		assertEquals(count,24)
	}
}