package jp.juggler.apng.util

import java.util.LinkedList

internal class BufferPool(private val arraySize: Int) {
    private val list: LinkedList<ByteArray> = LinkedList()
    fun obtain(): ByteArray = if (list.isEmpty()) ByteArray(arraySize) else list.removeFirst()
    fun recycle(array: ByteArray?) = array?.let { list.add(it) }
}
