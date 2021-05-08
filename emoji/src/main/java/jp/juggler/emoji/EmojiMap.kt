package jp.juggler.emoji

import android.content.Context
import android.util.Log
import java.io.EOFException
import java.io.InputStream
import java.util.*

object EmojiMap {

    // 表示に使う。絵文字のユニコードシーケンスから画像リソースIDへのマップ
    val unicodeMap = HashMap<String, UnicodeEmoji>()
    val unicodeTrie = EmojiTrie<UnicodeEmoji>()

    // 表示と投稿に使う。絵文字のショートコードから画像リソースIDとユニコードシーケンスへのマップ
    val shortNameMap = HashMap<String, UnicodeEmoji>()

    // 入力補完に使う。絵文字のショートコードのソートされたリスト
    val shortNameList = ArrayList<String>()

    /////////////////////////////////////////////////////////////////

    private fun readStream(appContext: Context, inStream: InputStream) {
        val assetManager = appContext.assets!!
        val resources = appContext.resources!!
        val packageName = appContext.packageName!!

        fun getDrawableId(name: String) =
            resources.getIdentifier(name, "drawable", packageName).takeIf { it != 0 }

        val categoryNameMap = HashMap<String, EmojiCategory>().apply {
            EmojiCategory.values().forEach { put(it.name, it) }
        }

        // 素の数字とcopyright,registered, trademark は絵文字にしない
        fun isIgnored(code: String): Boolean {
            val c = code[0].code
            return code.length == 1 && c <= 0xae
        }

        fun addCode(emoji: UnicodeEmoji, code: String) {
            if (isIgnored(code)) return
            unicodeMap[code] = emoji
            unicodeTrie.append(code, 0, emoji)
        }


        fun addName(emoji: UnicodeEmoji, name: String) {
            shortNameMap[name] = emoji
            shortNameList.add(name)
        }


        val reComment = """\s*//.*""".toRegex()
        val reLineHeader = """\A(\w+):""".toRegex()
        val assetsSet = assetManager.list("")!!.toSet()
        var lastEmoji: UnicodeEmoji? = null
        var lastCategory: EmojiCategory? = null
        var lno = 0
        fun readEmojiDataLine(rawLine: String) {
            ++lno
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
                        val parent = unicodeMap[cols[0]]
                            ?: error("missing tone parent. line=$lno $line")
                        val toneCode = cols[1].takeIf { it.isNotEmpty() }
                            ?: error("missing tone code. line=$lno $line")
                        val child = unicodeMap[cols[2]]
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
                        val emoji = unicodeMap[line] ?: error("missing emoji.")
//                        if (emoji == null) {
//                            Log.w("SubwayTooter", "missing emoji. lno=$lno line=$rawLine")
//                        } else
                        if (!category.emoji_list.contains(emoji)) {
                            category.emoji_list.add(emoji)
                        }
                    }
                    else -> error("unknown header $head")
                }
            } catch (ex: Throwable) {
                Log.e("SubwayTooter", "EmojiMap load error.", ex)
                error("EmojiMap load error: ${ex.javaClass.simpleName} ${ex.message} lno=$lno line=$rawLine")
            }
        }

        val lineFeed = 0x0a.toByte()
        val buffer = ByteArray(4096)
        var used = inStream.read(buffer, 0, buffer.size)
        if (used <= 0) throw EOFException("unexpected EOF")
        while (true) {
            var lineStart = 0
            while (lineStart < used) {
                var i = lineStart
                while (i < used && buffer[i] != lineFeed) ++i
                if (i >= used) break
                if (i > lineStart) {
                    val line = String(buffer, lineStart, i - lineStart, Charsets.UTF_8)
                    readEmojiDataLine(line)
                }
                lineStart = i + 1
            }
            buffer.copyInto(buffer, 0, lineStart, used)
            used -= lineStart
            val nRead = inStream.read(buffer, used, buffer.size - used)
            if (nRead <= 0) {
                if (used > 0) throw EOFException("unexpected EOF")
                break
            }
            used += nRead
        }

    }

    fun load(appContext: Context) {
        appContext.assets!!.open("emoji_map.txt").use {
            readStream(appContext, it)
        }
        shortNameList.sort()
        for (emoji in unicodeMap.values) {
            if (emoji.unifiedCode.isEmpty()) error("missing unifiedCode. ${emoji.assetsName ?: emoji.drawableId.toString()}")
            if (emoji.unifiedName.isEmpty()) error("missing unifiedName. ${emoji.assetsName ?: emoji.drawableId.toString()}")
        }
    }

    //////////////////////////////////////////////////////

    fun isStartChar(c: Char): Boolean {
        return unicodeTrie.hasNext(c)
    }
}