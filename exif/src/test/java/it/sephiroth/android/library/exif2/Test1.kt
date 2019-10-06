package it.sephiroth.android.library.exif2

import android.util.Log
import android.util.SparseIntArray
import junit.framework.Assert.fail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class Test1 {
	
	@Test
	fun testLog() {
		assertTrue("using android.util.Log", true)
	}
	
	@Test
	fun testSparseIntArray() {
		val a = SparseIntArray()
		a.put(1, 2)
		assertTrue("get existing value", a[1] == 2)
		assertTrue("fallback to default value ", a.get(0, - 1) == - 1)
	}
	
	// get File object from files in src/test/resources/
	private fun getFile(fileName : String) : File {
		return when(val resource = this.javaClass.classLoader !!.getResource(fileName)) {
			null -> error("missing file $fileName")
			else -> File(resource.path)
		}
	}
	
	private fun getOrientation(fileName : String) : Pair<Int?,Throwable?> =
		try{
		val o = FileInputStream(getFile(fileName)).use { inStream ->
			ExifInterface()
				.apply {
					readExif(
						inStream,
						ExifInterface.Options.OPTION_IFD_0
							or ExifInterface.Options.OPTION_IFD_1
							or ExifInterface.Options.OPTION_IFD_EXIF
					)
				}
				.getTagIntValue(ExifInterface.TAG_ORIENTATION)
		}
		Pair(o,null)
	}catch(ex:Throwable){
		Pair(null,ex)
	}
	
	private fun testNotJpegSub(fileName : String) {
		val(o,ex) = getOrientation(fileName)
		assertTrue("testNotJpegSub",o ==null && ex!=null)
		if( ex!= null) println("exception raised: ${ex::class.java} ${ex.message}")
	}
	
	@Test
	fun testNotJpeg() {
		testNotJpegSub("test.gif")
		testNotJpegSub("test.png")
		testNotJpegSub("test.webp")
	}
	
	@Test
	fun testJpeg() {
		var fileName :String
		var rv : Pair<Int?,Throwable?>

		// this file has orientation 1
		fileName = "test1.jpg"
		rv = getOrientation(fileName)
		assertEquals(fileName,1,rv.first)
		
		// this file has no orientation, raises exception.
		fileName = "test2.jpg"
		rv = getOrientation(fileName)
		assertTrue(fileName,rv.second != null)
		
		// this file has orientation 6.
		fileName = "test3.jpg"
		rv = getOrientation(fileName)
		assertEquals(fileName,rv.first , 6)
	}
	
}