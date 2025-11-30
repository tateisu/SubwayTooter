package jp.juggler.util.data

import java.io.ByteArrayOutputStream

/**
 * Base128 エンコーディング
 *
 * FCMで何文字送れるか試験したところ、文字種により送れる文字数に差異はあった。
 * n=4050 c=a 1bytes
 * n=4050 c=\u0000 1bytes
 * n=4050 c=\u000a 1bytes
 * n=2025 c=\u00a9 2bytes
 * n=1350 c=\u82b1 3bytes
 *
 * - jsonのエスケープなどは考慮しなくてよさそう。
 * - UTF-8表現でバイト数が増えると送れる文字数が減る。
 *
 * UTF-8の長い表現を使うと使えるビットがむしろ減るので、
 * UTF-8の 0x00-0x7f の範囲の文字にエンコードするのが効率が良さそうだった。
 *
 */
object Base128 {

    fun String.decodeBase128(): ByteArray =
        ByteArrayOutputStream(this.length).also {
            var bits = 0
            var bitsUsed = 0
            for (c in this) {
                bits = bits.shl(7).or(c.code.and(0x7f))
                bitsUsed += 7
                if (bitsUsed >= 8) {
                    val outByte = bits.shr(bitsUsed - 8).and(0xff)
                    it.write(outByte)
                    bitsUsed -= 8
                }
            }
            // bitsUsedに8未満のbitが残ることがあるが、末尾のパディングなので読み捨てる
        }.toByteArray()

    fun ByteArray.encodeBase128(): String =
        StringBuilder(this.size).also {
            var bits = 0
            var bitsUsed = 0
            for (inByte in this) {
                bits = bits.shl(8).or(inByte.toInt().and(255))
                bitsUsed += 8
                while (bitsUsed >= 7) {
                    val outBits = bits.shr(bitsUsed - 7).and(0x7f)
                    bitsUsed -= 7
                    it.append(outBits.toChar())
                }
            }
            if (bitsUsed > 0) {
                val outBits = bits.shl(7 - bitsUsed).and(0x7f)
                it.append(outBits.toChar())
            }
        }.toString()
}
