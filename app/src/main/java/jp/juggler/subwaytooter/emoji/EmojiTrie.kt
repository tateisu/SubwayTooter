package jp.juggler.subwaytooter.emoji

import androidx.collection.SparseArrayCompat

class EmojiTrie<T> {

    data class Result<T>(val data: T, val endPos: Int)

    var data: T? = null
    val map = SparseArrayCompat<EmojiTrie<T>>()

    fun append(src: String, offset: Int, data: T) {
        if (offset >= src.length) {
            if (this.data != null) error("EmojiTrie.append: duplicate: $src")
            this.data = data
            return
        }
        val c = src[offset].code
        val next = map[c] ?: EmojiTrie<T>().also { map.put(c, it) }
        next.append(src, offset + 1, data)
    }

    fun hasNext(c: Char) = map.containsKey(c.code)

    fun get(src: String, offset: Int, end: Int): Result<T>? {
        // 長い方を優先するので、先に子を調べる
        if (offset < end) {
            map[src[offset].code]?.get(src, offset + 1, end)?.let { return it }
        }
        return this.data?.let { Result(it, offset) }
    }
}
