package jp.juggler.subwaytooter.emoji

import android.content.Context
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
        EmojiMapLoader(appContext, this).readStream(inStream)
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
