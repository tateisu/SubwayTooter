package jp.juggler.subwaytooter.util

import java.util.AbstractList
import java.util.ArrayList
import java.util.NoSuchElementException
import java.util.RandomAccess

class BucketList<E> constructor(
    val bucketCapacity: Int = 1024
) : AbstractList<E>(), MutableIterable<E>, RandomAccess {

    companion object {
        private val pos_internal = object : ThreadLocal<BucketPos>() {
            override fun initialValue(): BucketPos {
                return BucketPos()
            }
        }
    }

    override var size: Int = 0

    override fun isEmpty(): Boolean {
        return 0 == size
    }

    private class Bucket<E>(
        capacity: Int,
        var totalStart: Int = 0,
        var totalEnd: Int = 0
    ) : ArrayList<E>(capacity)

    private val groups = ArrayList<Bucket<E>>()

    override fun clear() {
        groups.clear()
        size = 0
    }

    private fun updateIndex() {
        var n = 0
        for (bucket in groups) {
            bucket.totalStart = n
            n += bucket.size
            bucket.totalEnd = n
        }
        size = n
    }

    internal class BucketPos(
        var groupIndex: Int = 0,
        var bucketIndex: Int = 0
    ) {
        internal fun set(groupIndex: Int, bucketIndex: Int): BucketPos {
            this.groupIndex = groupIndex
            this.bucketIndex = bucketIndex
            return this
        }
    }

    // allocated を指定しない場合は BucketPosを生成します
    private fun findPos(
        totalIndex: Int,
        result: BucketPos = pos_internal.get()!!
    ): BucketPos {

        if (totalIndex < 0 || totalIndex >= size) {
            throw IndexOutOfBoundsException("findPos: bad index=$totalIndex, size=$size")
        }

        // binary search
        var gs = 0
        var ge = groups.size
        while (true) {
            val gi = (gs + ge) shr 1
            val group = groups[gi]
            when {
                totalIndex < group.totalStart -> ge = gi
                totalIndex >= group.totalEnd -> gs = gi + 1
                else -> {
                    return result.set(gi, totalIndex - group.totalStart)
                }
            }
        }
    }

    override fun get(index: Int): E {
        val pos = findPos(index)
        return groups[pos.groupIndex][pos.bucketIndex]
    }

    override fun set(index: Int, element: E): E {
        val pos = findPos(index)
        return groups[pos.groupIndex].set(pos.bucketIndex, element)
    }

    // 末尾への追加
    override fun addAll(elements: Collection<E>): Boolean {
        val cSize = elements.size
        if (cSize == 0) return false

        // 最後のバケツに収まるなら、最後のバケツの中に追加する
        if (groups.isNotEmpty()) {
            val bucket = groups[groups.size - 1]
            if (bucket.size + cSize <= bucketCapacity) {
                bucket.addAll(elements)
                bucket.totalEnd += cSize
                size += cSize
                return true
            }
        }
        // 新しいバケツを作って、そこに追加する
        val bucket = Bucket<E>(bucketCapacity)
        bucket.addAll(elements)
        bucket.totalStart = size
        bucket.totalEnd = size + cSize
        size += cSize
        groups.add(bucket)
        return true
    }

    // 位置を指定して挿入
    override fun addAll(index: Int, elements: Collection<E>): Boolean {

        // indexが終端なら、終端に追加する
        // バケツがカラの場合もここ
        if (index >= size) {
            return addAll(elements)
        }

        val cSize = elements.size
        if (cSize == 0) return false

        val pos = findPos(index)
        var bucket = groups[pos.groupIndex]

        // 挿入位置がバケツの先頭ではないか、バケツのサイズに問題がないなら
        if (pos.bucketIndex > 0 || bucket.size + cSize <= bucketCapacity) {
            // バケツの中に挿入する
            bucket.addAll(pos.bucketIndex, elements)
        } else {
            // 新しいバケツを作って、そこに追加する
            bucket = Bucket(bucketCapacity)
            bucket.addAll(elements)
            groups.add(pos.groupIndex, bucket)
        }

        updateIndex()
        return true
    }

    override fun removeAt(index: Int): E {
        val pos = findPos(index)
        val bucket = groups[pos.groupIndex]
        val data = bucket.removeAt(pos.bucketIndex)
        if (bucket.isEmpty()) {
            groups.removeAt(pos.groupIndex)
        }
        updateIndex()
        return data
    }

    inner class MyIterator internal constructor() : MutableIterator<E> {

        private val pos: BucketPos // indicates next read point

        init {
            pos = BucketPos(0, 0)
        }

        override fun hasNext(): Boolean {
            while (true) {
                if (pos.groupIndex >= groups.size) {
                    return false
                }
                val bucket = groups[pos.groupIndex]
                if (pos.bucketIndex >= bucket.size) {
                    pos.bucketIndex = 0
                    ++pos.groupIndex
                    continue
                }
                return true
            }
        }

        override fun next(): E {
            while (true) {
                if (pos.groupIndex >= groups.size) {
                    throw NoSuchElementException()
                }
                val bucket = groups[pos.groupIndex]
                if (pos.bucketIndex >= bucket.size) {
                    pos.bucketIndex = 0
                    ++pos.groupIndex
                    continue
                }
                return bucket[pos.bucketIndex++]
            }
        }

        override fun remove() {
            throw NotImplementedError()
        }
    }

    override fun iterator(): MutableIterator<E> {
        return MyIterator()
    }
}
