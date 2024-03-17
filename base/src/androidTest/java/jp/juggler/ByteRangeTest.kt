package jp.juggler

import jp.juggler.crypt.toByteRange
import jp.juggler.util.data.decodeBase64
import jp.juggler.util.data.encodeBase64
import jp.juggler.util.data.encodeBase64Url
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class ByteRangeTest {

    /**
     * ByteRangeや StringUtilsのBase64が、kotlin.io.encoding.Base64 の出力結果と一致するか調べる。
     */
    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun testByteRangeBase64() {
        for (len in 0..300) {
            val src = ByteArray(len) { it.toByte() }
            run {

                val kotlinBase64UrlSafe = Base64.UrlSafe

                // kotlin.io の Base64.UrlSafe は 末尾の = パディングを残すので後から除去する必要がある
                val encodedByKotlinIo = kotlinBase64UrlSafe.encode(src).trimEnd { it == '=' }
                // ByteRange().encodeBase64Url() はパディングを含まない
                val encodeByByteRange = src.toByteRange().encodeBase64Url()
                // StringUtils の encodeBase64Url() はパディングを含まない
                val encodeByStringUtils = src.encodeBase64Url()
                // もちろんStringUtilsの decodeBase64() でデコードできる
                val decodedByUtils = encodeByStringUtils.decodeBase64()
                assertEquals(
                    "len=$len",
                    encodedByKotlinIo,
                    encodeByByteRange,
                )
                assertEquals(
                    "len=$len",
                    encodedByKotlinIo,
                    encodeByStringUtils,
                )
                assertArrayEquals(
                    "len=$len encoded=$encodeByStringUtils",
                    src,
                    decodedByUtils,
                )
            }
            run {
                val kotinBase64 = Base64.Default
                val encodedByKotlinIo = kotinBase64.encode(src)
                val encodeByByteRange = src.toByteRange().encodeBase64()
                val encodeByUtils = src.encodeBase64()
                val decodedByUtils = encodeByUtils.decodeBase64()
                assertEquals(
                    "len=$len",
                    encodedByKotlinIo,
                    encodeByByteRange,
                )
                assertEquals(
                    "len=$len",
                    encodedByKotlinIo,
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
