package jp.juggler

import jp.juggler.crypt.toByteRange
import jp.juggler.util.data.decodeBase64
import jp.juggler.util.data.encodeBase64
import jp.juggler.util.data.encodeBase64Url
import org.apache.commons.codec.binary.Base64.encodeBase64String
import org.apache.commons.codec.binary.Base64.encodeBase64URLSafeString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ByteRangeTest {

    @Test
    fun testByteRangeBase64() {
        for (len in 0..300) {
            val src = ByteArray(len) { it.toByte() }
            run {
                val encodedByApacheCodec = encodeBase64URLSafeString(src)
                val encodeByByteRange = src.toByteRange().encodeBase64Url()
                val encodeByUtils = src.encodeBase64Url()
                val decodedByUtils = encodeByUtils.decodeBase64()
                assertEquals(
                    "len=$len",
                    encodedByApacheCodec,
                    encodeByByteRange,
                )
                assertEquals(
                    "len=$len",
                    encodedByApacheCodec,
                    encodeByUtils,
                )
                assertArrayEquals(
                    "len=$len encoded=$encodeByUtils",
                    src,
                    decodedByUtils,
                )
            }
            run {
                val encodedByApacheCodec = encodeBase64String(src)
                val encodeByByteRange = src.toByteRange().encodeBase64()
                val encodeByUtils = src.encodeBase64()
                val decodedByUtils = encodeByUtils.decodeBase64()
                assertEquals(
                    "len=$len",
                    encodedByApacheCodec,
                    encodeByByteRange,
                )
                assertEquals(
                    "len=$len",
                    encodedByApacheCodec,
                    encodeByUtils,
                )
                assertArrayEquals(
                    "len=$len",
                    src,
                    decodedByUtils,
                )
            }
        }
    }
}
