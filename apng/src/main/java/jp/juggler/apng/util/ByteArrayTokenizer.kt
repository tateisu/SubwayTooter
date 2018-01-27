package jp.juggler.apng.util

import jp.juggler.apng.ParseError


internal class ByteArrayTokenizer(ba: ByteArray) {
    private val array: ByteArray = ba
    private val arraySize: Int = ba.size
    private var pos = 0

    val size: Int
        get()= arraySize

    val remain: Int
        get()= arraySize -pos

    fun skipBytes(size: Int) {
        pos += size
    }

    fun readBytes(size: Int): ByteArrayRange {
        if (pos + size > arraySize) {
            throw ParseError("readBytes: unexpected EoS")
        }
        val result = ByteArrayRange(array, pos, size)
        pos+=size
        return result
    }

    private fun readByte(): Int {
        if (pos >=  arraySize) {
            throw ParseError("readBytes: unexpected EoS")
        }
        return array[pos++].toInt() and 0xff
    }

    fun readInt32(): Int {
        val b0 = readByte()
        val b1 = readByte()
        val b2 = readByte()
        val b3 = readByte()

        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    fun readUInt16(): Int {
        val b0 = readByte()
        val b1 = readByte()
        return (b0 shl 8) or b1
    }

    fun readUInt8() = readByte()


}