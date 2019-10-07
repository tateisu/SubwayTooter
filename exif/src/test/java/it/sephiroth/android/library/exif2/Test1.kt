package it.sephiroth.android.library.exif2

import android.util.Log
import android.util.SparseIntArray
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class Test1 {
	
	@Test
	fun testLog() {
		Log.v("TEST", "test")
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
	
	private fun getOrientation(fileName : String) : Pair<Int?, Throwable?> =
		try {
			val o = FileInputStream(getFile(fileName)).use { inStream ->
				ExifInterface()
					.readExif(
						inStream,
						ExifInterface.Options.OPTION_IFD_0
							or ExifInterface.Options.OPTION_IFD_1
							or ExifInterface.Options.OPTION_IFD_EXIF
					)
					.getTagIntValue(ExifInterface.TAG_ORIENTATION)
			}
			Pair(o, null)
		} catch(ex : Throwable) {
			Pair(null, ex)
		}
	
	private fun getThumbnailBytes(fileName : String) : Pair<ByteArray?, Throwable?> =
		try {
			val o = FileInputStream(getFile(fileName)).use { inStream ->
				ExifInterface()
					.readExif(
						inStream,
						ExifInterface.Options.OPTION_IFD_0
							or ExifInterface.Options.OPTION_IFD_1
							or ExifInterface.Options.OPTION_IFD_EXIF
					)
					.thumbnailBytes
			}
			Pair(o, null)
		} catch(ex : Throwable) {
			Pair(null, ex)
		}
	
	@Test
	fun testNotJpeg() {
		fun testNotJpegSub(fileName : String) {
			val (o, ex) = getOrientation(fileName)
			assertTrue("testNotJpegSub", o == null && ex != null)
		}
		testNotJpegSub("test.gif")
		testNotJpegSub("test.png")
		testNotJpegSub("test.webp")
	}
	
	@Test
	fun testJpeg() {
		var fileName : String
		var rvO : Pair<Int?, Throwable?>
		var rvT : Pair<ByteArray?, Throwable?>
		
		// this file has orientation 6.
		fileName = "test3.jpg"
		rvO = getOrientation(fileName)
		assertEquals(fileName, 6, rvO.first)
		rvT = getThumbnailBytes(fileName)
		assertNull(fileName, rvT.first)
		
		// this file has orientation 1
		fileName = "test1.jpg"
		rvO = getOrientation(fileName)
		assertEquals(fileName, 1, rvO.first)
		rvT = getThumbnailBytes(fileName)
		assertNull(fileName, rvT.first)
		
		// this file has no orientation, it raises exception.
		fileName = "test2.jpg"
		rvO = getOrientation(fileName)
		assertNotNull(
			fileName,
			rvO.second
		) // <java.lang.IllegalStateException: stop before hitting compressed data>
		
		rvT = getThumbnailBytes(fileName)
		assertNotNull(
			fileName,
			rvT.second
		) // <java.lang.IllegalStateException: stop before hitting compressed data>
		
	}
	
}