package jp.juggler.apng.util

import java.util.*

internal class BufferPool(private val arraySize:Int){
    private val list =LinkedList<ByteArray>()

    fun recycle(array: ByteArray) {
        list.add( array)
    }

    fun obtain(): ByteArray {
        return if( list.isEmpty() ) ByteArray(arraySize) else list.removeFirst()
    }
}