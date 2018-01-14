package jp.juggler.subwaytooter.util

import java.util.AbstractList
import java.util.ArrayList
import java.util.NoSuchElementException
import java.util.RandomAccess

class BucketList<E> constructor(private val bucketCapacity : Int = 1024) : AbstractList<E>(), MutableIterable<E>, RandomAccess {
	
	companion object {
		
		private val pos_internal = object : ThreadLocal<BucketPos>() {
			override fun initialValue() : BucketPos {
				return BucketPos()
			}
		}
	}
	
	override var size : Int = 0
	
	override fun isEmpty() : Boolean {
		return 0 == size
	}
	
	private class Bucket<E> internal constructor(capacity : Int) : ArrayList<E>(capacity) {
		internal var total_start : Int = 0
		internal var total_end : Int = 0
	}
	
	private val groups = ArrayList<Bucket<E>>()
	
	override fun clear() {
		groups.clear()
		size = 0
	}
	
	private fun updateIndex() {
		var n = 0
		for(bucket in groups) {
			bucket.total_start = n
			n += bucket.size
			bucket.total_end = n
		}
		size = n
	}
	
	private class BucketPos(var group_index : Int = 0, var bucket_index : Int = 0) {
		internal fun update(group_index : Int, bucket_index : Int) : BucketPos {
			this.group_index = group_index
			this.bucket_index = bucket_index
			return this
		}
	}
	
	// allocalted を指定しない場合は BucketPosを生成します
	private fun findPos(total_index : Int, allocated : BucketPos? = pos_internal.get()) : BucketPos {
		if(total_index < 0 || total_index >= size) {
			throw IndexOutOfBoundsException("findPos: bad index=$total_index, size=$size")
		}
		
		// binary search
		val groups_size = groups.size
		var gs = 0
		var ge = groups_size
		while(true) {
			val gi = (gs + ge) shr 1
			val group = groups[gi]
			when {
				total_index < group.total_start -> ge = gi
				total_index >= group.total_end -> gs = gi + 1
				else -> {
					return (allocated ?: BucketPos())
						.update(gi, total_index - group.total_start)
				}
			}
		}
	}
	
	override fun get(index : Int) : E {
		val pos = findPos(index)
		return groups[pos.group_index][pos.bucket_index]
	}
	
	// 末尾への追加
	override fun addAll(elements : Collection<E>) : Boolean {
		val c_size = elements.size
		if(c_size == 0) return false
		
		// 最後のバケツに収まるなら、最後のバケツの中に追加する
		if(groups.size > 0) {
			val bucket = groups[groups.size - 1]
			if(bucket.size + c_size <= bucketCapacity) {
				bucket.addAll(elements)
				bucket.total_end += c_size
				size += c_size
				return true
			}
		}
		// 新しいバケツを作って、そこに追加する
		val bucket = Bucket<E>(bucketCapacity)
		bucket.addAll(elements)
		bucket.total_start = size
		bucket.total_end = size + c_size
		size += c_size
		groups.add(bucket)
		return true
	}
	
	// 位置を指定して挿入
	override fun addAll(index : Int, elements : Collection<E>) : Boolean {
		
		// indexが終端なら、終端に追加する
		// バケツがカラの場合もここ
		if(index == size) {
			return addAll(elements)
		}
		
		val c_size = elements.size
		if(c_size == 0) return false
		
		val pos = findPos(index)
		var bucket = groups[pos.group_index]
		
		// 挿入位置がバケツの先頭ではないか、バケツのサイズに問題がないなら
		if(pos.bucket_index > 0 || bucket.size + c_size <= bucketCapacity) {
			// バケツの中に挿入する
			bucket.addAll(pos.bucket_index, elements)
		} else {
			// 新しいバケツを作って、そこに追加する
			bucket = Bucket(bucketCapacity)
			bucket.addAll(elements)
			groups.add(pos.group_index, bucket)
		}
		
		updateIndex()
		return true
	}
	
	override fun removeAt(index : Int) : E {
		val pos = findPos(index)
		val bucket = groups[pos.group_index]
		val data = bucket.removeAt(pos.bucket_index)
		if(bucket.isEmpty()) {
			groups.removeAt(pos.group_index)
		}
		updateIndex()
		return data
	}
	
	inner class MyIterator internal constructor() : MutableIterator<E> {
		
		private val pos : BucketPos // indicates next read point
		
		init {
			pos = BucketPos(0, 0)
		}
		
		override fun hasNext() : Boolean {
			while(true) {
				if(pos.group_index >= groups.size) {
					return false
				}
				val bucket = groups[pos.group_index]
				if(pos.bucket_index >= bucket.size) {
					pos.bucket_index = 0
					++ pos.group_index
					continue
				}
				return true
			}
		}
		
		override fun next() : E {
			while(true) {
				if(pos.group_index >= groups.size) {
					throw NoSuchElementException()
				}
				val bucket = groups[pos.group_index]
				if(pos.bucket_index >= bucket.size) {
					pos.bucket_index = 0
					++ pos.group_index
					continue
				}
				return bucket[pos.bucket_index ++]
			}
		}
		
		override fun remove() {
			throw NotImplementedError()
		}
	}
	
	override fun iterator() : MutableIterator<E> {
		return MyIterator()
	}
	
}
