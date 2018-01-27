@file:Suppress("JoinDeclarationAndAssignment")

package jp.juggler.apng

import jp.juggler.apng.util.StreamTokenizer
import java.util.zip.CRC32

internal class ApngChunk(crc32:CRC32,tokenizer: StreamTokenizer) {
    val size: Int
    val type: String

    init {
        size = tokenizer.readInt32()
        val typeBytes = tokenizer.readBytes(4)
        type = typeBytes.toString(Charsets.UTF_8)

        crc32.update(typeBytes)
    }

    fun readBody(crc32:CRC32,tokenizer: StreamTokenizer): ByteArray {
        val bytes = tokenizer.readBytes(size)
        val crcExpect = tokenizer.readUInt32()

        crc32.update(bytes, 0, size)
        val crcActual = crc32.value
        if (crcActual != crcExpect) throw ParseError("CRC not match.")

        return bytes
    }

    fun skipBody(tokenizer: StreamTokenizer) {
        tokenizer.skipBytes((size + 4).toLong())
    }

    fun checkCRC(tokenizer: StreamTokenizer, crcActual: Long) {
        val crcExpect = tokenizer.readUInt32()
        if (crcActual != crcExpect) throw ParseError("CRC not match.")
    }
}
