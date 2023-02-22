package jp.juggler.subwaytooter.util

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.util.SparseBooleanArray
import androidx.annotation.DrawableRes
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.emoji.CustomEmoji
import jp.juggler.subwaytooter.emoji.EmojiMap
import jp.juggler.subwaytooter.emoji.UnicodeEmoji
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.span.EmojiImageSpan
import jp.juggler.subwaytooter.span.HighlightSpan
import jp.juggler.subwaytooter.span.NetworkEmojiSpan
import jp.juggler.subwaytooter.span.createSpan
import jp.juggler.subwaytooter.table.HighlightWord
import jp.juggler.subwaytooter.table.daoHighlightWord
import jp.juggler.util.data.asciiPattern
import jp.juggler.util.data.codePointBefore
import jp.juggler.util.log.LogCategory
import java.util.regex.Pattern
import kotlin.math.min

object EmojiDecoder {

    private val log = LogCategory("EmojiDecoder")

    private const val cpColon = ':'.code

    private const val cpZwsp = '\u200B'.code

    var useTwemoji = true

    fun customEmojiSeparator() =
        if (PrefB.bpCustomEmojiSeparatorZwsp.value) {
            '\u200B'
        } else {
            ' '
        }

    // タンス側が落ち着いたら [^[:almun:]_] から [:space:]に切り替える
    //	private fun isHeadOrAfterWhitespace( s:CharSequence,index:Int):Boolean {
    //		val cp = s.codePointBefore(index)
    //		return cp == -1 || CharacterGroup.isWhitespace(cp)
    //	}

    fun canStartShortCode(s: CharSequence, index: Int): Boolean {
        return when (val cp = s.codePointBefore(index)) {
            -1 -> true
            cpColon -> false
            cpZwsp -> true
            // rubyの (Letter | Mark | Decimal_Number) はNG
            // ftp://unicode.org/Public/5.1.0/ucd/UCD.html#General_Category_Values
            else -> when (Character.getType(cp).toByte()) {
                // Letter
                // LCはエイリアスなので文字から得られることはないはず
                Character.UPPERCASE_LETTER,
                Character.LOWERCASE_LETTER,
                Character.TITLECASE_LETTER,
                Character.MODIFIER_LETTER,
                Character.OTHER_LETTER,
                -> false
                // Mark
                Character.NON_SPACING_MARK,
                Character.COMBINING_SPACING_MARK,
                Character.ENCLOSING_MARK,
                -> false
                // Decimal_Number
                Character.DECIMAL_DIGIT_NUMBER -> false

                else -> true
            }
        }
        // https://mastodon.juggler.jp/@tateisu/99727683089280157
        // https://github.com/tootsuite/mastodon/pull/5570 がマージされたらこっちに切り替える
        // return cp == -1 || CharacterGroup.isWhitespace(cp)
    }

    fun canStartHashtag(s: CharSequence, index: Int): Boolean {
        val cp = s.codePointBefore(index)
        // HASHTAG_RE = /(?:^|[^\/\)\w])#(#{HASHTAG_NAME_RE})/i
        return if (cp >= 0x80) {
            true
        } else when (cp.toChar()) {
            '/' -> false
            ')' -> false
            '_' -> false
            in 'a'..'z' -> false
            in 'A'..'Z' -> false
            in '0'..'9' -> false
            else -> true
        }
    }

    private class EmojiStringBuilder(val options: DecodeOptions) {

        val sb = SpannableStringBuilder()
        var normal_char_start = -1

        private fun openNormalText() {
            if (normal_char_start == -1) {
                normal_char_start = sb.length
            }
        }

        fun closeNormalText() {
            if (normal_char_start != -1) {
                val end = sb.length
                applyHighlight(normal_char_start, end)
                normal_char_start = -1
            }
        }

        private fun applyHighlight(start: Int, end: Int) {
            val list = options.highlightTrie?.matchList(sb, start, end) ?: return
            for (range in list) {
                val word = daoHighlightWord.load(range.word)
                    ?: continue
                sb.setSpan(
                    HighlightSpan(word.color_fg, word.color_bg),
                    range.start,
                    range.end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                if (word.sound_type != HighlightWord.SOUND_TYPE_NONE) {
                    if (options.highlightSound == null) options.highlightSound = word
                }

                if (word.speech != 0) {
                    if (options.highlightSpeech == null) options.highlightSpeech = word
                }

                if (options.highlightAny == null) options.highlightAny = word
            }
        }

        fun addNetworkEmojiSpan(text: String, url: String, initialAspect: Float?) {
            closeNormalText()
            val start = sb.length
            sb.append(text)
            val end = sb.length
            sb.setSpan(
                NetworkEmojiSpan(
                    url,
                    scale = options.enlargeCustomEmoji,
                    sizeMode = options.emojiSizeMode,
                    initialAspect = initialAspect,
                ),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        fun addImageSpan(text: String, @DrawableRes resId: Int) {
            val context = options.context
            if (context == null) {
                openNormalText()
                sb.append(text)
            } else {
                closeNormalText()
                val start = sb.length
                sb.append(text)
                val end = sb.length
                sb.setSpan(
                    EmojiImageSpan(context, resId, scale = options.enlargeEmoji),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        fun addImageSpan(text: String, emoji: UnicodeEmoji) {
            val context = options.context
            when {
                context == null -> {
                    openNormalText()
                    sb.append(text)
                }
                PrefB.bpUseTwemoji.value -> {
                    closeNormalText()
                    val start = sb.length
                    sb.append(text)
                    val end = sb.length
                    sb.setSpan(
                        emoji.createSpan(context, scale = options.enlargeEmoji),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                else -> {
                    openNormalText()
                    sb.append(emoji.unifiedCode)
                }
            }
        }

        fun addUnicodeString(s: String) {

            if (!useTwemoji) {
                openNormalText()
                sb.append(s)
                return
            }

            var i = 0
            val end = s.length

            // 絵文字ではない部分をコピーする
            fun normalCopy(initialJ: Int): Boolean {
                var j = initialJ
                while (j < end && !EmojiMap.isStartChar(s[j])) {
                    j += min(end - j, Character.charCount(s.codePointAt(j)))
                }
                if (j <= i) return false
                // https://github.com/tateisu/SubwayTooter/issues/69
                val text = s.substring(i, j).replace('\u00AD', '-')
                openNormalText()
                sb.append(text)
                i = j
                return true
            }

            while (i < end) {
                // 絵文字ではない部分をコピーする
                if (normalCopy(i) && i >= end) break

                // 絵文字コードを探索
                val result = EmojiMap.unicodeTrie.get(s, i, end)
                if (result == null) {
                    // 見つからなかったら、通常テキストを1文字以上コピーする
                    normalCopy(i + min(end - i, Character.charCount(s.codePointAt(i))))
                    continue
                }

                val nextChar = if (result.endPos >= end) null else s[result.endPos].code

                // 絵文字バリエーション・シーケンス（EVS）のU+FE0E（VS-15）が直後にある場合
                // その文字を絵文字化しない
                if (nextChar == 0xFE0E) {
                    normalCopy(result.endPos + 1)
                    continue
                }

                val emoji = if (nextChar == 0xFE0F && s[result.endPos - 1].code != 0xFE0F) {
                    // 絵文字の最後が 0xFE0F でない
                    // 直後にU+0xFE0F (絵文字バリエーション・シーケンスEVSのVS-16）がある
                    // 直後のそれまで含めて絵文字として表示する
                    s.substring(i, result.endPos + 1)
                } else {
                    s.substring(i, result.endPos)
                }
                addImageSpan(emoji, result.data)
                i += emoji.length
            }
        }
    }

    private const val codepointColon = ':'.code
    // private const val codepointAtmark = '@'.toInt()

    private val shortCodeCharacterSet =
        SparseBooleanArray().apply {
            for (c in 'A'..'Z') put(c.code, true)
            for (c in 'a'..'z') put(c.code, true)
            for (c in '0'..'9') put(c.code, true)
            for (c in "+-_@:") put(c.code, true)
            for (c in ".") put(c.code, true)
        }

    private interface ShortCodeSplitterCallback {
        fun onString(part: String) // shortcode以外の文字列
        fun onShortCode(
            prevCodePoint: Int,
            part: String,
            name: String,
        ) // part : ":shortcode:", name : "shortcode"
    }

    private val reUrl = """https?://[\w/:%#@${'$'}&?!()\[\]~.=+\-]+"""
        .asciiPattern()

    private fun splitShortCode(
        s: String,
        callback: ShortCodeSplitterCallback,
    ) {
        val urlList = ArrayList<IntRange>().apply {
            val m = reUrl.matcher(s)
            while (m.find()) {
                add(m.start()..m.end())
            }
        }

        val end = s.length
        var i = 0
        while (i < end) {

            // ":"以外を読み飛ばす
            // URL中のコロンも読み飛ばす
            var start = i
            loop@ while (i < end) {
                val c = s.codePointAt(i)
                if (c == codepointColon && urlList.none { i in it }) break@loop
                i += Character.charCount(c)
            }
            if (i > start) callback.onString(s.substring(start, i))

            if (i >= end) break

            start = i++ // start=コロンの位置 i=その次の位置

            // 閉じるコロンを探す
            var posEndColon = -1
            loop@ while (i < end) {
                val cp = s.codePointAt(i)
                when {
                    cp == codepointColon -> {
                        posEndColon = i
                        break@loop
                    }

                    !shortCodeCharacterSet.get(cp, false) ->
                        break@loop
                }

                i += Character.charCount(cp)
            }

            // 閉じるコロンが見つからないか、shortcodeが短すぎるなら
            // startの位置のコロンだけを処理して残りは次のループで処理する
            if (posEndColon == -1 || posEndColon - start < 2) {
                callback.onString(":")
                i = start + 1
                continue
            }

            val prevCodePoint = when {
                start <= 0 -> 0x20
                else -> s.codePointBefore(start)
            }

            callback.onShortCode(
                prevCodePoint,
                s.substring(start, posEndColon + 1), // ":shortcode:"
                s.substring(start + 1, posEndColon) // "shortcode"
            )

            i = posEndColon + 1 // コロンの次の位置
        }
    }

    private val reNicoru = """\Anicoru\d*\z""".asciiPattern(Pattern.CASE_INSENSITIVE)
    private val reHohoemi = """\Ahohoemi\d*\z""".asciiPattern(Pattern.CASE_INSENSITIVE)

    fun decodeEmoji(options: DecodeOptions, s: String): SpannableStringBuilder {

        val builder = EmojiStringBuilder(options)

        val emojiMapCustom = options.emojiMapCustom
        val emojiMapProfile = options.emojiMapProfile

        val useEmojioneShortcode = PrefB.bpEmojioneShortcode.value
        val disableEmojiAnimation = PrefB.bpDisableEmojiAnimation.value

        // カスタム絵文字のアニメーション切り替え
        fun CustomEmoji.customEmojiToUrl(): String = when {
            disableEmojiAnimation && staticUrl?.isNotEmpty() == true -> staticUrl
            else -> this.url
        }

        fun findEmojiMisskey13(name: String): String? {
            val cols = name.split("@", limit = 2)
            val apiHostAscii = options.linkHelper?.apiHost?.ascii

            // @以降にあるホスト名か、投稿者のホスト名か、閲覧先サーバのホスト名
            val userHost = cols.elementAtOrNull(1)
                ?: options.authorDomain?.apiHost?.ascii
                ?: apiHostAscii
            log.i(
                "decodeEmoji Misskey13 c0=${cols.elementAtOrNull(0)} c1=${
                    cols.elementAtOrNull(1)
                } apiHostAscii=$apiHostAscii, userHost=$userHost"
            )

            when {
                // 絵文字プロクシを利用できない
                apiHostAscii == null -> {
                    log.w("decodeEmoji Misskey13 missing apiHostAscii")
                }
                userHost != null && userHost != "." && userHost != apiHostAscii -> {
                    // 投稿者のホスト名を使う
                    return "https://$apiHostAscii/emoji/${
                        cols.elementAtOrNull(0)
                    }@$userHost.webp"
                }
                else -> {
                    // 存在確認せずに絵文字プロキシのURLを返す
                    // 閲覧先サーバの絵文字を探す
                    return "https://${apiHostAscii}/emoji/${cols.elementAtOrNull(0)}.webp"
                }
            }
            return null
        }


        splitShortCode(s, callback = object : ShortCodeSplitterCallback {
            override fun onString(part: String) {
                builder.addUnicodeString(part)
            }

            override fun onShortCode(prevCodePoint: Int, part: String, name: String) {
                // フレニコのプロフ絵文字
                if (emojiMapProfile != null && name.length >= 2 && name[0] == '@') {
                    val emojiProfile = emojiMapProfile[name] ?: emojiMapProfile[name.substring(1)]
                    if (emojiProfile != null) {
                        val url = emojiProfile.url
                        if (url.isNotEmpty()) {
                            builder.addNetworkEmojiSpan(part, url, initialAspect = null)
                            return
                        }
                    }
                }

                emojiMapCustom?.get(name)?.let {
                    val url = it.customEmojiToUrl()
                    builder.addNetworkEmojiSpan(part, url, initialAspect = it.aspect)
                    return
                }
                val url = if (options.linkHelper?.isMisskey == true) {
                    findEmojiMisskey13(name = name)
                } else {
                    null
                }
                if (url != null) {
                    builder.addNetworkEmojiSpan(part, url, initialAspect = null)
                    return
                }

                // 通常の絵文字
                when {
                    reHohoemi.matcher(name).find() ->
                        builder.addImageSpan(part, R.drawable.emoji_hohoemi)
                    reNicoru.matcher(name).find() ->
                        builder.addImageSpan(part, R.drawable.emoji_nicoru)
                    else -> {
                        // EmojiOneのショートコード
                        val emoji = when {
                            useEmojioneShortcode ->
                                EmojiMap.shortNameMap[name.lowercase().replace('-', '_')]
                            else -> null
                        }
                        when (emoji) {
                            null -> builder.addUnicodeString(part)
                            else -> builder.addImageSpan(part, emoji)
                        }
                    }
                }
            }
        })

        builder.closeNormalText()

        return builder.sb
    }

    // 投稿などの際、表示は不要だがショートコード=>Unicodeの解決を行いたい場合がある
    // カスタム絵文字の変換も行わない
    fun decodeShortCode(
        s: String,
        emojiMapCustom: HashMap<String, CustomEmoji>? = null,
    ): String {
        val decodeEmojioneShortcode = PrefB.bpEmojioneShortcode.value

        val sb = StringBuilder()

        splitShortCode(s, callback = object : ShortCodeSplitterCallback {
            override fun onString(part: String) {
                sb.append(part)
            }

            override fun onShortCode(prevCodePoint: Int, part: String, name: String) {
                // カスタム絵文字にマッチするなら変換しない
                // カスタム絵文字ではなく通常の絵文字のショートコードなら絵文字に変換する
                val emoji = when {
                    decodeEmojioneShortcode &&
                            emojiMapCustom?.get(name) == null ->
                        EmojiMap.shortNameMap[name.lowercase().replace('-', '_')]
                    else -> null
                }
                sb.append(emoji?.unifiedCode ?: part)
            }
        })

        return sb.toString()
    }

    // 入力補完用。絵文字ショートコード一覧を部分一致で絞り込む
    internal fun searchShortCode(
        context: Context,
        prefix: String,
        limit: Int,
    ): ArrayList<CharSequence> {
        val dst = ArrayList<CharSequence>()
        for (shortCode in EmojiMap.shortNameList) {
            if (dst.size >= limit) break
            if (!shortCode.contains(prefix)) continue

            val emoji = EmojiMap.shortNameMap[shortCode] ?: continue

            val sb = SpannableStringBuilder()

            if (PrefB.bpUseTwemoji.value) {
                val start = 0
                sb.append(' ')
                val end = sb.length

                sb.setSpan(
                    emoji.createSpan(context),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                sb.append(emoji.unifiedCode)
            }

            sb.append(' ')
                .append(':')
                .append(shortCode)
                .append(':')

            dst.add(sb)
        }
        return dst
    }
}
