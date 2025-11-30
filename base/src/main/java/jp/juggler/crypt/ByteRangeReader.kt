package jp.juggler.crypt

class ByteRangeReader(
    private val src: ByteRange,
) {
    private val end = src.size
    private var pos = 0

    fun skip(length: Int) {
        pos += length
    }

    fun readBytes(size: Int = end - pos): ByteRange {
        if (pos + size > end) error("unexpected end.")
        val rv = src.subRange(pos, pos + size)
        pos += size
        return rv
    }

    /**
     * 残り全部
     */
    fun remainBytes() = readBytes()

    fun readUInt8(): Int {
        if (pos >= end) error("unexpected end.")
        val b = src[pos++]
        return b.toInt().and(255)
    }

    fun readUInt16(): Int {
        if (pos + 2 > end) error("unexpected end.")
        // Big Endian
        val b0 = src[pos].toInt().and(255).shl(8)
        val b1 = src[pos + 1].toInt().and(255)
        pos += 2
        return b0.or(b1)
    }

    fun readUInt32(): Int {
        if (pos + 4 > end) error("unexpected end.")
        // Big Endian
        val b0 = src[pos].toInt().and(255).shl(24)
        val b1 = src[pos + 1].toInt().and(255).shl(16)
        val b2 = src[pos + 2].toInt().and(255).shl(8)
        val b3 = src[pos + 3].toInt().and(255)
        pos += 4
        return b0.or(b1).or(b2).or(b3)
    }
}

fun ByteRange.byteRangeReader() = ByteRangeReader(this)
fun ByteArray.byteRangeReader() = ByteRangeReader(this.toByteRange())
