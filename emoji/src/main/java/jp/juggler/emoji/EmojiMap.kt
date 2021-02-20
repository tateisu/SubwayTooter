package jp.juggler.emoji

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.collection.SparseArrayCompat
import java.io.EOFException
import java.io.InputStream
import java.util.*

object EmojiMap {

    const val CATEGORY_PEOPLE = 0
    const val CATEGORY_NATURE = 1
    const val CATEGORY_FOODS = 2
    const val CATEGORY_ACTIVITY = 3
    const val CATEGORY_PLACES = 4
    const val CATEGORY_OBJECTS = 5
    const val CATEGORY_SYMBOLS = 6
    const val CATEGORY_FLAGS = 7
    const val CATEGORY_OTHER = 8

    class Category{
        val emoji_list = ArrayList<String>()
    }

    class EmojiResource(
        // SVGの場合はasset resourceの名前
        val assetsName: String? = null,
        // PNGの場合はdrawable id
        @DrawableRes val drawableId: Int =0,
    ){
        val isSvg: Boolean
            get() = assetsName != null
    }

    class EmojiInfo(val unified: String, val er: EmojiResource)

    // 表示に使う。絵文字のユニコードシーケンスから画像リソースIDへのマップ
    val utf16ToEmojiResource = HashMap<String, EmojiResource>()
    val utf16Trie = EmojiTrie<EmojiResource>()

    // 表示と投稿に使う。絵文字のショートコードから画像リソースIDとユニコードシーケンスへのマップ
    val shortNameToEmojiInfo = HashMap<String, EmojiInfo>()

    // 入力補完に使う。絵文字のショートコードのソートされたリスト
    val shortNameList = ArrayList<String>()

    // ピッカーに使う。カテゴリのリスト
    val categoryMap = SparseArrayCompat<Category>()

    /////////////////////////////////////////////////////////////////

    private fun readStream(assetManager:AssetManager, inStream:InputStream){

        val categoryNameMap = HashMap<String,Category>().apply{
            fun a(@StringRes id:Int,name:String){
                val c = categoryMap.get(id)
                    ?: Category().also{ categoryMap.put(id,it)}
                put(name, c)
            }
            a(CATEGORY_PEOPLE,"CATEGORY_PEOPLE")
            a(CATEGORY_NATURE,"CATEGORY_NATURE")
            a(CATEGORY_FOODS,"CATEGORY_FOODS")
            a(CATEGORY_ACTIVITY,"CATEGORY_ACTIVITY")
            a(CATEGORY_PLACES,"CATEGORY_PLACES")
            a(CATEGORY_OBJECTS,"CATEGORY_OBJECTS")
            a(CATEGORY_SYMBOLS,"CATEGORY_SYMBOLS")
            a(CATEGORY_FLAGS,"CATEGORY_FLAGS")
            a(CATEGORY_OTHER,"CATEGORY_OTHER")
        }

        // 素の数字とcopyright,registered, trademark は絵文字にしない
        fun isIgnored(code: String): Boolean {
            val c = code[0].toInt()
            return code.length == 1 && c <= 0xae || c == 0x2122
        }

        fun addCode(code: String, er:EmojiResource) {
            if (isIgnored(code)) return
            utf16ToEmojiResource[code] = er
            utf16Trie.append(code, 0, er)
        }

//    private fun addCode(code: String,  @DrawableRes resId:Int ) =
//        addCode(code,EmojiResource(resId))
//
//    private fun addCode(code: String, assetsName: String) =
//        addCode(code,EmojiResource(assetsName))

        fun addName(name: String, unified: String) {
            if (isIgnored(unified)) return
            val er = utf16ToEmojiResource[unified]
                ?: throw IllegalStateException("missing emoji for code $unified")
            shortNameToEmojiInfo[name] = EmojiInfo(unified, er)
            shortNameList.add(name)
        }

        fun addCategoryItem(name: String,category:Category) {
            category.emoji_list.add(name)
        }


        val reComment="""\s*//.*""".toRegex()
        val reLineHeader="""\A(\w+):""".toRegex()
        val assetsSet = assetManager.list("")!!.toSet()
        var lastEmoji : EmojiResource? = null
        var lastCategory:Category? = null
        var lno = 0
        fun readEmojiDataLine(rawLine:String){
            ++lno
            var line = rawLine.replace(reComment, "").trim()
            val head = reLineHeader.find(line)?.groupValues?.elementAtOrNull(1)
                ?: error("missing line header. line=$lno $line")
            line = line.substring(head.length + 1)
            try {
                when (head) {
                    "s1" -> {
                        if (!assetsSet.contains(line)) error("missing assets. line=$lno $line")
                        lastEmoji = EmojiResource(assetsName = line)
                    }
                    "s2" -> addCode(
                        line,
                        lastEmoji
                            ?: error("missing lastEmoji. line=$lno $line")
                    )
                    "n" -> {
                        val cols = line.split(",", limit = 2)
                        if (cols.size != 2) error("invalid name,code. line=$lno $line")
                        addName(cols[0], cols[1])
                    }
                    "c1" -> {
                        lastCategory = categoryNameMap[line]
                            ?: error("missing category name. line=$lno $line")
                    }
                    "c2" -> addCategoryItem(
                        line,
                        lastCategory
                            ?: error("missing lastCategory. line=$lno $line")
                    )
                }
            }catch(ex:Throwable){
                Log.e("SubwayTooter", "EmojiMap load error: lno=$lno line=$line",ex)
            }
        }

        val lineFeed = 0x0a.toByte()
        val buffer = ByteArray(4096)
        var used = inStream.read(buffer,0,buffer.size)
        if(used <=0) throw EOFException("unexpected EOF")
        while(true) {
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

    fun load(appContext: Context){
        val assetManager = appContext.assets !!
        assetManager.open("emoji_map.txt").use{
            readStream(assetManager,it)
        }
        shortNameList.sort()
    }

    //////////////////////////////////////////////////////

    fun isStartChar(c: Char): Boolean {
        return utf16Trie.hasNext(c)
    }
}