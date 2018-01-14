package jp.juggler.subwaytooter.util

import android.support.test.runner.AndroidJUnit4

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@Suppress("MemberVisibilityCanPrivate")
@RunWith(AndroidJUnit4::class)
class TestBucketList {
	@Test fun test1(){
		val list = BucketList<String>(bucketCapacity=2)
		assertEquals(true,list.isEmpty())
		list.addAll( listOf("A","B","C"))
		list.addAll( 3, listOf("a","b","c"))
		list.addAll( 1, listOf("a","b","c"))
		list.removeAt(7)
		assertEquals(8,list.size)
		listOf("A","a","b","c","B","C","a","c").forEachIndexed { i,v->
			assertEquals( v,list[i])
		}
	}
}