package jp.juggler.apng.util

import java.util.*

internal class ByteSequenceQueue(private val bufferRecycler :(ByteSequence)->Unit) {

    private val list = LinkedList<ByteSequence>()

    val remain: Int
        get() = list.sumBy { it.length }

    fun add(range: ByteSequence) {
        list.add(range)
    }

    fun clear() {
        for( item in list ){
            bufferRecycler(item)
        }
        list.clear()
    }

    fun readBytes(dst: ByteArray, offset: Int, length: Int): Int {
        var nRead = 0
        while (nRead < length && list.isNotEmpty()) {
            val item = list.first()
            if (item.length <= 0) {
                bufferRecycler(item)
                list.removeFirst()
                continue
            }
            val delta = Math.min(item.length, length - nRead)
            System.arraycopy(item.array, item.offset, dst, offset + nRead, delta)
            item.offset += delta
            item.length -= delta
            nRead += delta
        }
        return nRead
    }
}
