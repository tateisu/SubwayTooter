package jp.juggler.apng.util

import jp.juggler.apng.ParseError
import java.io.InputStream
import java.util.zip.CRC32

internal class StreamTokenizer(val inStream: InputStream) {

    fun skipBytes(size: Long) {
        var nRead = 0L
        while (true) {
            val remain = size - nRead
            if (remain <= 0) break
            val delta = inStream.skip(size - nRead)
            if (delta <= 0) throw ParseError("skipBytes: unexpected EoS")
            nRead += delta
        }
    }

    fun readBytes(size: Int): ByteArray {
        val dst = ByteArray(size)
        var nRead = 0
        while (true) {
            val remain = size - nRead
            if (remain <= 0) break
            val delta = inStream.read(dst, nRead, size - nRead)
            if (delta < 0) throw ParseError("readBytes: unexpected EoS")
            nRead += delta
        }
        return dst
    }

    private fun readByte(): Int {
        val b = inStream.read()
        if( b == -1 ) throw ParseError("readBytes: unexpected EoS")
        return b and 0xff
    }

    fun readInt32(): Int {
        val b0 = readByte()
        val b1 = readByte()
        val b2 = readByte()
        val b3 = readByte()

        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    fun readInt32(crc32: CRC32): Int {
        val ba = readBytes(4)
        crc32.update(ba)
        val b0 = ba[0].toInt() and 255
        val b1 = ba[1].toInt() and 255
        val b2 = ba[2].toInt() and 255
        val b3 = ba[3].toInt() and 255

        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    fun readUInt32(): Long {
        val b0 = readByte()
        val b1 = readByte()
        val b2 = readByte()
        val b3 = readByte()
        return (b0.toLong() shl 24) or ((b1 shl 16) or (b2 shl 8) or b3).toLong()
    }
}