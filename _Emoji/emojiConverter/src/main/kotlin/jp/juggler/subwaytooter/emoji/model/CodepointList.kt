package jp.juggler.subwaytooter.emoji.model

import jp.juggler.subwaytooter.emoji.cast
import jp.juggler.subwaytooter.emoji.notEmpty

/*
	絵文字はコードポイントのリストで表現される。
	コードポイントはUTF-16の1ワードに収まらない場合がある。

	絵文字の末尾にはvariation selectorがつく場合がある。
	- variation selectorなし
	- TPVS U+FE0E VARIATION SELECTOR-15
	- EPVS U+FE0F VARIATION SELECTOR-16

	末尾にFE0F(EPVS)をつけると絵文字として表示する場合が多いが、
	なくてもEmojiとして表示するものもある。

	EmojiOneのデータはvariation selectorを含まない
	noto-Emojiのデータはvariation selectorを含まない
	twemojiのデータはvariation selectorを含む。
	emoji-dataのデータはvariation selector を含む。

 */

//private const val hexChars = "0123456789abcdef"
//
//var utf16_max_length = 0


// list of codepoints
class CodepointList(
    val from: String,
    val list: IntArray
) : Comparable<CodepointList> {

    override fun equals(other: Any?): Boolean =
        list.contentEquals(other.cast<CodepointList>()?.list)

    override fun hashCode(): Int {
        var code = 0
        for (v in list) code = code.shl(2).xor(v)
        return code
    }

    override fun compareTo(other: CodepointList): Int {
        val la = this.list
        val lb = other.list
        var i = 0
        do {
            val a = la.elementAtOrNull(i)
            val b = lb.elementAtOrNull(i)

            val r = if (a == null) {
                if (b == null) break
                -1
            } else if (b == null) {
                1
            } else {
                a.compareTo(b)
            }
            if (r != 0) return r
            ++i
        } while (true)
        return 0
    }

    // make string like as "uuuu-uuuu-uuuu-uuuu"
    // cp値の余分な0は除去される
    // 常に小文字である
    fun toHex() = StringBuilder(list.size * 5).also {
        list.forEachIndexed { i, v ->
            if (i > 0) it.append('-')
            it.append("%x".format(v).lowercase())
        }
    }.toString()

    // make raw string
    fun toRawString() = StringBuilder(list.size + 10).also { sb ->
        for (cp in list) {
            sb.appendCodePoint(cp)
        }
    }.toString()

    fun toResourceId() = "emj_${toHex().replace("-", "_")}"

    override fun toString() = "${toHex()},$from"

//	fun makeUtf16(): String {
//		// java の文字列にする
//
//		// UTF-16にエンコード
//		val bytesUtf16 = toRawString().toByteArray(Charsets.UTF_16BE)
//
//		// UTF-16の符号単位数
//		val length = bytesUtf16.size / 2
//		if (length > utf16_max_length) {
//			utf16_max_length = length
//		}
//
//		val sb = StringBuilder()
//		for (i in bytesUtf16.indices step 2) {
//			val v0 = bytesUtf16[i].toInt()
//			val v1 = bytesUtf16[i + 1].toInt()
//			sb.append("\\u")
//			sb.append(hexChars[v0.and(255).shr(4)])
//			sb.append(hexChars[v0.and(15)])
//			sb.append(hexChars[v1.and(255).shr(4)])
//			sb.append(hexChars[v1.and(15)])
//		}
//		return sb.toString()
//	}

    fun toKey(from: String) =
        list.filter { it != 0xfe0f && it != 0xfe0e && it != 0x200d }
            .toIntArray().toCodepointList(from)


    fun getToneCode(from: String): CodepointList? {
        val used = HashSet<Int>()
        return list
            .filter { skinToneModifiers.containsKey(it) }
            .mapNotNull {
                if (used.contains(it)) {
                    null
                } else {
                    used.add(it)
                    it
                }
            }.toIntArray().toCodepointList(from)
    }
}

fun IntArray.isAsciiEmoji() =
    size == 1 && first() < 0xae

fun IntArray.toCodepointList(from: String) = if (isEmpty()) null else CodepointList(from, this)

private val reHex = """([0-9A-Fa-f]+)""".toRegex()

// cp-cp-cp-cp => CodepointList
fun String.toCodepointList(from: String) =
    reHex.findAll(this)
        .map { mr -> mr.groupValues[1].toInt(16) }
        .toList().notEmpty()
        ?.toIntArray()
        ?.toCodepointList(from)
