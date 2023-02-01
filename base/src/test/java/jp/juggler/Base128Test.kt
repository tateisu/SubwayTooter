package jp.juggler.pushreceiverapp

import jp.juggler.util.data.Base128.decodeBase128
import jp.juggler.util.data.Base128.encodeBase128
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

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
                assertArrayEquals(
                    "len=$len,i=$i",
                    orig,
                    decoded
                )
            }
        }
    }
}
