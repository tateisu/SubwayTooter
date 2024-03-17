package jp.juggler

import jp.juggler.util.data.Base128.decodeBase128
import jp.juggler.util.data.Base128.encodeBase128
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals

class Base128Test {

    @Test
    fun useBase128() {
        for (len in 0..20) {
            for (i in 0 until 256) {
                val orig = ByteArrayOutputStream(32)
                    .apply {
                        repeat(len) {
                            write(i)
                        }
                    }.toByteArray()
                val encoded = orig.encodeBase128()
                val decoded = encoded.decodeBase128()
                assertContentEquals(
                    orig,
                    decoded,
                    "len=$len,i=$i",
                )
            }
        }
    }
}
