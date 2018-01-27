package jp.juggler.apng.util

import java.util.*

internal class ByteArrayQueue(private val bufferRecycler :(ByteArrayRange)->Unit) {

    private val list = LinkedList<ByteArrayRange>()

    val remain: Int
        get() = list.sumBy { it.remain }

    fun add(range: ByteArrayRange) {
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
            if (item.remain <= 0) {
                bufferRecycler(item)
                list.removeFirst()
            } else {
                val delta = Math.min(item.remain, length - nRead)
                System.arraycopy(item.array, item.start, dst, offset + nRead, delta)
                item.start += delta
                item.remain -= delta
                nRead += delta
            }
        }
        return nRead
    }
}
