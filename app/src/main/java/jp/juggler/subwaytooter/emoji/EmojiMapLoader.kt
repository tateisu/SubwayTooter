package jp.juggler.subwaytooter.emoji

import android.annotation.SuppressLint
import android.content.Context
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.errorEx
import java.io.EOFException
import java.io.InputStream

class EmojiMapLoader(
    appContext: Context,
    private val dst: EmojiMap,
) {
    // このクラスは起動時に1回だけ使うため、companion objectに永続的に何か保持することはない
    private val log = LogCategory("EmojiMapLoader")
    private val reComment = """\s*//.*""".toRegex()
    private val reLineHeader = """\A(\w+):""".toRegex()

    private val packageName = appContext.packageName!!
    private val assetsSet = appContext.assets.list("")!!.toSet()
    private val resources = appContext.resources!!

    private val categoryNameMap = HashMap<String, EmojiCategory>().apply {
        EmojiCategory.values().forEach { put(it.name, it) }
    }

    private var lastEmoji: UnicodeEmoji? = null
    private var lastCategory: EmojiCategory? = null

    @SuppressLint("DiscouragedApi")
    private fun getDrawableId(name: String) =
        resources.getIdentifier(name, "drawable", packageName).takeIf { it != 0 }

    // 素の数字とcopyright,registered, trademark は絵文字にしない
    private fun isIgnored(code: String): Boolean {
        val c = code[0].code
        return code.length == 1 && c <= 0xae
    }

    private fun addCode(emoji: UnicodeEmoji, code: String) {
        if (isIgnored(code)) return
        dst.unicodeMap[code] = emoji
        dst.unicodeTrie.append(code, 0, emoji)
    }

    private fun addName(emoji: UnicodeEmoji, name: String) {
        dst.shortNameMap[name] = emoji
        dst.shortNameList.add(name)
        emoji.namesLower.add(name.lowercase())
    }

    private fun readEmojiDataLine(lno: Int, rawLine: String) {
        var line = rawLine.replace(reComment, "").trim()
        val head = reLineHeader.find(line)?.groupValues?.elementAtOrNull(1)
            ?: error("missing line header. line=$lno $line")
        line = line.substring(head.length + 1)
        try {
            when (head) {
                "svg" -> {
                    if (!assetsSet.contains(line)) error("missing assets.")
                    lastEmoji = UnicodeEmoji(assetsName = line)
                }
                "drawable" -> {
                    val drawableId = getDrawableId(line) ?: error("missing drawable.")
                    lastEmoji = UnicodeEmoji(drawableId = drawableId)
                }
                "un" -> {
                    val emoji = lastEmoji ?: error("missing lastEmoji.")
                    addCode(emoji, line)
                    emoji.unifiedCode = line
                }
                "u" -> {
                    val emoji = lastEmoji ?: error("missing lastEmoji.")
                    addCode(emoji, line)
                }
                "sn" -> {
                    val emoji = lastEmoji ?: error("missing lastEmoji.")
                    addName(emoji, line)
                    emoji.unifiedName = line
                }
                "s" -> {
                    val emoji = lastEmoji ?: error("missing lastEmoji.")
                    addName(emoji, line)
                }
                "t" -> {
                    val cols = line.split(",", limit = 3)
                    if (cols.size != 3) error("invalid tone spec. line=$lno $line")
                    val parent = dst.unicodeMap[cols[0]]
                        ?: error("missing tone parent. line=$lno $line")
                    val toneCode = cols[1].takeIf { it.isNotEmpty() }
                        ?: error("missing tone code. line=$lno $line")
                    val child = dst.unicodeMap[cols[2]]
                        ?: error("missing tone child. line=$lno $line")
                    parent.toneChildren.add(Pair(toneCode, child))
                    child.toneParent = parent
                }

                "cn" -> {
                    lastCategory = categoryNameMap[line]
                        ?: error("missing category name.")
                }
                "c" -> {
                    val category = lastCategory
                        ?: error("missing lastCategory.")
                    val emoji = dst.unicodeMap[line] ?: error("missing emoji.")
//                        if (emoji == null) {
//                            Log.w("SubwayTooter", "missing emoji. lno=$lno line=$rawLine")
//                        } else
                    if (!category.emojiList.contains(emoji)) {
                        category.emojiList.add(emoji)
                    }
                }
                else -> error("unknown header $head")
            }
        } catch (ex: Throwable) {
            log.e(
                ex,
                "readEmojiDataLine: ${ex.javaClass.simpleName} ${ex.message} lno=$lno line=$rawLine"
            )
            // 行番号の情報をつけて投げ直す
            errorEx(
                ex,
                "readEmojiDataLine: ${ex.javaClass.simpleName} ${ex.message} lno=$lno line=$rawLine"
            )
        }
    }

    private fun ByteArray.indexOf(key: Byte, start: Int = 0): Int? {
        var i = start
        val end = this.size
        while (i < end) {
            if (this[i] == key) return i
            ++i
        }
        return null
    }

    private fun InputStream.eachLine(block: (Int, String) -> Unit) {
        val lineFeed = 0x0a.toByte()
        val buffer = ByteArray(4096)
        // バッファに読む
        var end = read(buffer, 0, buffer.size)
        if (end <= 0) throw EOFException("unexpected EOF")

        var lno = 0
        while (true) {
            var lineStart = 0
            while (lineStart < end) {
                // 行末記号を見つける
                val feedPos = buffer.indexOf(lineFeed, lineStart) ?: break
                ++lno
                if (feedPos > lineStart) {
                    // 1行分をUTF-8デコードして処理する
                    val line = String(buffer, lineStart, feedPos - lineStart, Charsets.UTF_8)
                    block(lno, line)
                }
                lineStart = feedPos + 1
            }
            // 最後の行末より後のデータをバッファ先頭に移動する
            buffer.copyInto(buffer, 0, lineStart, end)
            end -= lineStart
            // ストリームから継ぎ足す
            val nRead = read(buffer, end, buffer.size - end)
            if (nRead <= 0) {
                if (end > 0) throw EOFException("unexpected EOF")
                break
            }
            end += nRead
        }
    }

    fun readStream(inStream: InputStream) {
        inStream.eachLine { lno, line -> readEmojiDataLine(lno, line) }
    }
}
