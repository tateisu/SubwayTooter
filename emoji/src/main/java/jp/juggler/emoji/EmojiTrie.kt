package jp.juggler.emoji

import androidx.collection.SparseArrayCompat

data class EmojiTrieResult<T>(val data: T, val endPos: Int)

class EmojiTrie<T> {
    var data: T? = null
    val map = SparseArrayCompat<EmojiTrie<T>>()

    fun append(src: String, offset: Int, data: T) {
        if (offset >= src.length) {
            if (this.data != null) error("EmojiTrie.append: duplicate: $src")
            this.data = data
            return
        }
        val c = src[offset].toInt()
        val next = map[c] ?: EmojiTrie<T>().also { map.put(c, it) }
        next.append(src, offset + 1, data)
    }

    fun hasNext(c: Char) = map.containsKey(c.toInt())

    fun get(src: String, offset: Int, end: Int): EmojiTrieResult<T>? {
        // 長い方を優先するので、先に子を調べる
        if (offset < end)
            map[src[offset].toInt()]?.get(src, offset + 1, end)
                ?.let { return it }
        return this.data?.let { EmojiTrieResult(it, offset) }
    }


}