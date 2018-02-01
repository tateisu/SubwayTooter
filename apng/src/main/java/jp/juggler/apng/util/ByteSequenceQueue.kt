package jp.juggler.apng.util

import java.util.*

internal class ByteSequenceQueue(private val bufferRecycler : (ByteSequence) -> Unit) {
	
	private val list = LinkedList<ByteSequence>()
	
	val remain : Int
		get() = list.sumBy { it.length }
	
	fun add(range : ByteSequence) =list.add(range)
	
	fun clear() = list.also{ it.forEach(bufferRecycler) }.clear()
	
	fun readBytes(dst : ByteArray, offset : Int, length : Int) : Int {
		var dstOffset = offset
		var dstRemain = length
		while(dstRemain > 0 && list.isNotEmpty()) {
			val item = list.first()
			if(item.length <= 0) {
				bufferRecycler(item)
				list.removeFirst()
			} else {
				val delta = Math.min(item.length, dstRemain)
				System.arraycopy(item.array, item.offset, dst, dstOffset, delta)
				dstOffset += delta
				dstRemain -= delta
				item.offset += delta
				item.length -= delta
			}
		}
		return length - dstRemain
	}
}
