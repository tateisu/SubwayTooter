package jp.juggler.subwaytooter.util

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.TootMention
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.mfm.MisskeyMarkdownDecoder
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.span.*
import jp.juggler.subwaytooter.table.HighlightWord
import jp.juggler.subwaytooter.table.daoAcctColor
import jp.juggler.subwaytooter.table.daoHighlightWord
import jp.juggler.util.data.*
import jp.juggler.util.log.LogCategory
import jp.juggler.util.ui.fontSpan
import java.util.regex.Pattern
import kotlin.math.min

object HTMLDecoder {

    private val log = LogCategory("HTMLDecoder")

    private const val DEBUG_HTML_PARSER = false

    private enum class OpenType {
        Open,
        Close,
        OpenClose,
    }

    private const val TAG_TEXT = "<>text"
    private const val TAG_END = "<>end"

    private val reTag = "<(/?)(\\w+)".asciiPattern()
    private val reTagEnd = "(/?)>$".asciiPattern()
    private val reHref = "\\bhref=\"([^\"]*)\"".asciiPattern()
    private val reAttribute = "\\s+([A-Za-z0-9:_-]+)\\s*=([\"'])([^>]*?)\\2".asciiPattern()
    private val reShortcode = ":[A-Za-z0-9_-]+:".asciiPattern()
    private val reNotestockEmojiAlt = """\A:[^:]+:\z""".toRegex()
    private val reUrlStart = """\Ahttps?://""".toRegex()

    // Block-level Elements
    // https://developer.mozilla.org/en-US/docs/Web/HTML/Block-level_elements
    // https://www.w3schools.com/html/html_blocks.asp
    private val blockLevelElements = arrayOf(
        "address",
        "article",
        "aside",
        "blockquote",
        "body",
        "canvas",
        "caption",
        "col",
        "colgroup",
        "dd",
        "div",
        "dl",
        "dt",
        "embed",
        "fieldset",
        "figcaption",
        "figure",
        "footer",
        "form",
        "h1",
        "h2",
        "h3",
        "h4",
        "h5",
        "h6",
        "header",
        "hgroup",
        "hr",
        "li",
        "main",
        "map",
        "nav",
        "noscript",
        "object",
        "ol",
        "p",
        "pre",
        "progress",
        "section",
        "textarea",
        "table",
        "tbody",
        "tfoot",
        "thead",
        "tr",
        "ul",
        "video"
    ).toHashSet()

    // Empty element
    // https://developer.mozilla.org/en-US/docs/Glossary/Empty_element
    // elements that cannot have any child nodes (i.e., nested elements or text nodes).
    // In HTML, using a closing tag on an empty element is usually invalid.
    private val emptyElements = arrayOf(
        "area",
        "base",
        "br",
        "col",
        "embed",
        "hr",
        "img",
        "input",
        "keygen", //(HTML 5.2 Draft removed)
        "link",
        "meta",
        "param",
        "source",
        "track",
        "wbr"
    ).toHashSet()

    private val reEntity = "&(#?)(\\w+);".asciiPattern()
    private val entity_map = HashMap<String, Char>()
    private fun defineEntity(s: String, c: Char) {
        entity_map[s] = c
    }

    private fun chr(num: Int): Char {
        return num.toChar()
    }

    fun decodeEntity(src: String?): String {
        src ?: return ""
        var sb: StringBuilder? = null
        val m = reEntity.matcher(src)
        var last_end = 0
        while (m.find()) {
            if (sb == null) sb = StringBuilder()
            val start = m.start()
            val end = m.end()
            try {
                if (start > last_end) {
                    sb.append(src.substring(last_end, start))
                }
                val is_numeric = m.groupEx(1)!!.isNotEmpty()
                val part = m.groupEx(2)!!
                if (!is_numeric) {
                    val c = entity_map[part]
                    if (c != null) {
                        sb.append(c)
                        continue
                    }
                } else {
                    try {
                        val cp = when {
                            part[0] == 'x' -> part.substring(1).toInt(16)
                            else -> part.toInt(10)
                        }
                        when {
                            Character.isBmpCodePoint(cp) -> sb.append(cp.toChar())

                            Character.isValidCodePoint(cp) -> {
                                sb.append(Character.highSurrogate(cp))
                                sb.append(Character.lowSurrogate(cp))
                            }

                            else -> sb.append('?')
                        }
                        continue
                    } catch (ex: Throwable) {
                        log.e(ex, "decodeEntity failed.")
                    }
                }
                sb.append(src.substring(start, end))
            } finally {
                last_end = end
            }
        }

        // 全くマッチしなかった
        sb ?: return src

        val end = src.length
        if (end > last_end) {
            sb.append(src.substring(last_end, end))
        }
        return sb.toString()
    }

    fun encodeEntity(src: String): String {
        val size = src.length
        val sb = StringBuilder()
        sb.ensureCapacity(size)
        for (i in 0 until size) {
            when (val c = src[i]) {
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&#039;")
                '&' -> sb.append("&amp;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    //////////////////////////////////////////////////////////////////////////////////////

    private val reDoctype = """\A\s*<!doctype[^>]*>""".asciiPattern(Pattern.CASE_INSENSITIVE)
    private val reComment = """<!--.*?-->""".asciiPattern(Pattern.DOTALL)

    private fun String.quoteMeta() = Pattern.quote(this)

    private class TokenParser(srcArg: String) {

        val src: String
        var next: Int = 0

        var open_type = OpenType.OpenClose
        var tag = ""
        var text = ""

        init {
            this.src = srcArg
                .replaceFirst(reDoctype, "")
                .replaceAll(reComment, " ")
            eat()
        }

        fun eat() {
            // end?
            if (next >= src.length) {
                tag = TAG_END
                open_type = OpenType.OpenClose
                return
            }

            // text ?
            var end = src.indexOf('<', next)
            if (end == -1) end = src.length
            if (end > next) {
                this.text = src.substring(next, end)
                this.tag = TAG_TEXT
                this.open_type = OpenType.OpenClose
                next = end
                return
            }

            // tag ?
            end = src.indexOf('>', next)
            if (end == -1) {
                end = src.length
            } else {
                ++end
            }
            text = src.substring(next, end)

            next = end

            val m = reTag.matcher(text)
            if (m.find()) {
                val is_close = m.groupEx(1)!!.isNotEmpty()
                tag = m.groupEx(2)!!.lowercase()

                val m2 = reTagEnd.matcher(text)
                val is_openclose = when {
                    m2.find() -> m2.groupEx(1)!!.isNotEmpty()
                    else -> false
                }

                open_type = when {
                    is_close -> OpenType.Close
                    is_openclose || emptyElements.contains(tag) -> OpenType.OpenClose
                    else -> OpenType.Open
                }
            } else {
                tag = TAG_TEXT
                this.open_type = OpenType.OpenClose
            }
        }
    }

    // 末尾の改行を数える
    private fun SpannableStringBuilder.lastBrCount(): Int {
        var count = 0
        var pos = length - 1
        while (pos > 0) {
            val c = this[pos--]
            when {
                c == '\n' -> {
                    ++count
                    continue
                }
                Character.isWhitespace(c) -> continue
                else -> break
            }
        }
        return count
    }

    private val listMarkers = arrayOf("●", "-", "*", "・")

    private enum class ListType {
        None,
        Ordered,
        Unordered,
        Definition,
        Quote
    }

    private class ListContext(
        val type: ListType,
        val nestLevelOrdered: Int,
        val nestLevelUnordered: Int,
        val nestLevelDefinition: Int,
        val nestLevelQuote: Int,
        var order: Int = 0,
    ) {
        fun subOrdered() = ListContext(
            type = ListType.Ordered,
            nestLevelOrdered + 1,
            nestLevelUnordered,
            nestLevelDefinition,
            nestLevelQuote
        )

        fun subUnordered() = ListContext(
            type = ListType.Unordered,
            nestLevelOrdered,
            nestLevelUnordered + 1,
            nestLevelDefinition,
            nestLevelQuote
        )

        fun subDefinition() = ListContext(
            type = ListType.Definition,
            nestLevelOrdered,
            nestLevelUnordered,
            nestLevelDefinition + 1,
            nestLevelQuote
        )

        fun subQuote() = ListContext(
            type = ListType.Quote,
            nestLevelOrdered,
            nestLevelUnordered,
            nestLevelDefinition,
            nestLevelQuote + 1
        )

        fun increment() = when (type) {
            ListType.Ordered -> "${++order}. "
            ListType.Unordered -> "${listMarkers[nestLevelUnordered % listMarkers.size]} "
            ListType.Definition -> ""
            else -> ""
        }

        fun inList() = nestLevelOrdered + nestLevelUnordered + nestLevelDefinition > 0

        fun quoteColor(): Int {
            val quoteNestColors = MisskeyMarkdownDecoder.quoteNestColors
            return quoteNestColors[nestLevelQuote % quoteNestColors.size]
        }
    }

    // SpannableStringBuilderを行ごとに分解する
    // 行末の改行文字は各行の末尾に残る
    // 最終行の長さが0(改行文字もなし)だった場合は出力されない
    fun SpannableStringBuilder.splitLines() =
        ArrayList<SpannableStringBuilder>().also { dst ->
            // 入力の末尾のtrim
            var end = this.length
            while (end > 0 && CharacterGroup.isWhitespace(this[end - 1].code)) --end

            // 入力の最初の非空白文字の位置を調べておく
            var firstNonSpace = 0
            while (firstNonSpace < end && CharacterGroup.isWhitespace(this[firstNonSpace].code)) ++firstNonSpace

            var i = 0
            while (i < end) {
                val lineStart = i
                while (i < end && this[i] != '\n') ++i
                val lineEnd = if (i >= end) end else i + 1
                ++i

                // 行頭の空白を削る
//                while (lineStart < lineEnd &&
//                    this[lineStart] != '\n' &&
//                    CharacterGroup.isWhitespace(this[lineStart].toInt())
//                ) ++lineStart

                // 最初の非空白文字以降の行を出力する
                if (lineEnd > firstNonSpace) {
                    dst.add(this.subSequence(lineStart, lineEnd) as SpannableStringBuilder)
                }
            }
            if (dst.isEmpty()) {
                // ブロック要素は最低1行は存在するので、1行だけの要素を作る
                dst.add(SpannableStringBuilder())
            }
        }

    private val reLastLine = """(?:\A|\n)([^\n]*)\z""".toRegex()

    private class Node {

        val child_nodes = ArrayList<Node>()

        val tag: String
        val text: String

        private val href: String?
            get() {
                val m = reHref.matcher(text)
                if (m.find()) {
                    val href = decodeEntity(m.groupEx(1))
                    if (href.isNotEmpty()) {
                        return href
                    }
                }
                return null
            }

        constructor() {
            tag = "<>root"
            text = ""
        }

        constructor(t: TokenParser) {
            this.tag = t.tag
            this.text = t.text
        }

        fun addChild(t: TokenParser, indent: String) {
            if (DEBUG_HTML_PARSER) log.d("addChild: $indent($tag")
            while (t.tag != TAG_END) {

                // 閉じるタグ
                if (t.open_type == OpenType.Close) {
                    if (t.tag != this.tag) {
                        // 閉じるタグが現在の階層とマッチしないなら無視する
                        log.w("unexpected close tag! ${t.tag}")
                        t.eat()
                        continue
                    }
                    // この階層の終端
                    t.eat()
                    break
                }

                val open_type = t.open_type
                val child = Node(t)
                child_nodes.add(child)
                t.eat()

                if (DEBUG_HTML_PARSER) {
                    log.d("addChild: $indent|${child.tag} $open_type [${child.text.quoteMeta()}]")
                }

                if (open_type == OpenType.Open) {
                    child.addChild(t, "$indent--")
                }
            }
            if (DEBUG_HTML_PARSER) log.d("addChild: $indent)$tag")
        }

        fun String.tagsCanRemoveNearSpaces() = when (this) {
            "li", "ol", "ul", "dl", "dt", "dd", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6",
            "table", "tbody", "thead", "tfoot", "tr", "td", "th",
            -> true
            else -> false
        }

        fun canSkipEncode(
            isBlockParent: Boolean,
            curr: Node,
            parent: Node,
            prev: Node?,
            next: Node?,
        ) = when {
            !isBlockParent -> false
            curr.tag != TAG_TEXT -> false
            curr.text.isNotBlank() -> false
            else -> when {
                prev?.tag?.tagsCanRemoveNearSpaces() == true -> true
                next?.tag?.tagsCanRemoveNearSpaces() == true -> true
                parent.tag.tagsCanRemoveNearSpaces() && (prev == null || next == null) -> true
                else -> false
            }
        }

        fun encodeText(options: DecodeOptions, sb: SpannableStringBuilder) {
            if (options.context != null && options.decodeEmoji) {
                sb.append(options.decodeEmoji(decodeEntity(text)))
            } else {
                sb.append(decodeEntity(text))
            }
        }

        fun encodeImage(options: DecodeOptions, sb: SpannableStringBuilder) {
            val attrs = parseAttributes(text)

            if (options.unwrapEmojiImageTag) {
                val cssClass = attrs["class"]
                val title = attrs["title"]
                val url = attrs["src"]
                val alt = attrs["alt"]
                if (cssClass != null &&
                    title != null &&
                    cssClass.contains("emojione") &&
                    reShortcode.matcher(title).find()
                ) {
                    sb.append(options.decodeEmoji(title))
                    return
                } else if (cssClass == "emoji" && url != null && alt != null && reNotestockEmojiAlt.matches(
                        alt
                    )
                ) {
                    // notestock custom emoji
                    sb.run {
                        val start = length
                        append(alt)
                        val end = length
                        setSpan(
                            NetworkEmojiSpan(
                                url,
                                scale = options.enlargeCustomEmoji,
                                sizeMode = options.emojiSizeMode
                            ),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    return
                }
            }

            sb.append("<img ")
            val url = attrs["src"] ?: ""
            val caption = attrs["alt"] ?: ""
            if (caption.isNotEmpty() || url.isNotEmpty()) {
                val start = sb.length
                sb.append(caption.notEmpty() ?: url)
                if (reUrlStart.find(url) != null) {
                    val span =
                        MyClickableSpan(
                            LinkInfo(
                                url = url,
                                ac = null,
                                tag = null,
                                caption = caption,
                                mention = null
                            )
                        )
                    sb.setSpan(span, start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                sb.append(" ")
            }
            sb.append("/>")
        }

        class EncodeSpanEnv(
            val options: DecodeOptions,
            val listContext: ListContext,
            val tag: String,
            val sb: SpannableStringBuilder,
            val sbTmp: SpannableStringBuilder,
            val spanStart: Int,
        )

        val originalFlusher: EncodeSpanEnv.() -> Unit = {
            when (tag) {
                "s", "strike", "del" -> {
                    sb.setSpan(
                        StrikethroughSpan(),
                        spanStart,
                        sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                "em" -> {
                    sb.setSpan(
                        fontSpan(Typeface.defaultFromStyle(Typeface.ITALIC)),
                        spanStart,
                        sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                "strong" -> {
                    sb.setSpan(
                        StyleSpan(Typeface.BOLD),
                        spanStart,
                        sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                "tr" -> {
                    sb.append("|")
                }

                "style", "script" -> {
                    // sb_tmpにレンダリングした分は読み捨てる
                }
                "h1" -> {
                    sb.setSpan(
                        StyleSpan(Typeface.BOLD),
                        spanStart,
                        sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    sb.setSpan(
                        RelativeSizeSpan(1.8f),
                        spanStart,
                        sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                "h2" -> {
                    sb.setSpan(
                        StyleSpan(Typeface.BOLD),
                        spanStart,
                        sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    sb.setSpan(
                        RelativeSizeSpan(1.6f),
                        spanStart,
                        sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                "h3" -> {
                    sb.setSpan(
                        StyleSpan(Typeface.BOLD),
                        spanStart,
                        sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    sb.setSpan(
                        RelativeSizeSpan(1.4f),
                        spanStart,
                        sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                "h4" -> {
                    sb.setSpan(
                        StyleSpan(Typeface.BOLD),
                        spanStart,
                        sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    sb.setSpan(
                        RelativeSizeSpan(1.2f),
                        spanStart,
                        sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                "h5" -> {
                    sb.setSpan(
                        StyleSpan(Typeface.BOLD),
                        spanStart,
                        sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    sb.setSpan(
                        RelativeSizeSpan(1.0f),
                        spanStart,
                        sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                "h6" -> {
                    sb.setSpan(
                        StyleSpan(Typeface.BOLD),
                        spanStart,
                        sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    sb.setSpan(
                        RelativeSizeSpan(0.8f),
                        spanStart,
                        sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                "pre" -> {
                    sb.setSpan(
                        BackgroundColorSpan(0x40808080),
                        spanStart,
                        sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    sb.setSpan(
                        RelativeSizeSpan(0.7f),
                        spanStart,
                        sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    sb.setSpan(
                        fontSpan(Typeface.MONOSPACE),
                        spanStart,
                        sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                "code" -> {
                    sb.setSpan(
                        BackgroundColorSpan(0x40808080),
                        spanStart,
                        sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    sb.setSpan(
                        fontSpan(Typeface.MONOSPACE),
                        spanStart,
                        sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                "hr" -> sb.append("----------")
            }
        }

        val tmpFlusher = HashMap<String, EncodeSpanEnv.() -> Unit>().apply {

            fun add(vararg tags: String, block: EncodeSpanEnv.() -> Unit) {
                for (tag in tags) this[tag] = block
            }

            add("a") {
                val linkInfo = formatLinkCaption(options, sbTmp, href ?: "")
                val caption = linkInfo.caption
                if (caption.isNotEmpty()) {
                    val start = sb.length
                    sb.append(linkInfo.caption)
                    val end = sb.length
                    if (linkInfo.url.isNotEmpty()) {
                        val span = MyClickableSpan(linkInfo)
                        sb.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }

                    // リンクスパンを設定した後に色をつける
                    val list = options.highlightTrie?.matchList(sb, start, end)
                    if (list != null) {
                        for (range in list) {
                            val word = daoHighlightWord.load(range.word) ?: continue
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
                }
            }

            add("style", "script") {
                // 読み捨てる
                // 最適化によりtmpFlusherOriginalとこのラムダが同一オブジェクトにならないようにする
            }

            add("blockquote") {
                val bg_color = listContext.quoteColor()

                // TextView の文字装飾では「ブロック要素の入れ子」を表現できない
                // 内容の各行の始端に何か追加するというのがまずキツい
                // しかし各行の頭に引用マークをつけないと引用のネストで意味が通じなくなってしまう

                val startItalic = sb.length
                sbTmp.splitLines().forEach { line ->
                    val lineStart = sb.length
                    sb.append("> ")
                    sb.setSpan(
                        BackgroundColorSpan(bg_color),
                        lineStart,
                        lineStart + 1,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    sb.append(line)
                }
                sb.setSpan(
                    fontSpan(Typeface.defaultFromStyle(Typeface.ITALIC)),
                    startItalic,
                    sb.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            add("li") {
                val lineHeader1 = listContext.increment()
                val lineHeader2 = " ".repeat(lineHeader1.length)
                sbTmp.splitLines().forEachIndexed { i, line ->
                    sb.append(if (i == 0) lineHeader1 else lineHeader2)
                    sb.append(line)
                }
            }

            add("dt") {
                val prefix = listContext.increment()
                val startBold = sb.length
                sbTmp.splitLines().forEach { line ->
                    sb.append(prefix)
                    sb.append(line)
                }
                sb.setSpan(
                    fontSpan(Typeface.defaultFromStyle(Typeface.BOLD)),
                    startBold,
                    sb.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            add("dd") {
                val prefix = listContext.increment() + "　　"
                sbTmp.splitLines().forEach { line ->
                    sb.append(prefix)
                    sb.append(line)
                }
            }
        }

        fun childListContext(tag: String, outerContext: ListContext) = when (tag) {
            "ol" -> outerContext.subOrdered()
            "ul" -> outerContext.subUnordered()
            "dl" -> outerContext.subDefinition()
            "blockquote" -> outerContext.subQuote()
            else -> outerContext
        }

        fun encodeSpan(
            options: DecodeOptions,
            sb: SpannableStringBuilder,
            listContext: ListContext,
        ) {
            val isBlock = blockLevelElements.contains(tag)
            when (tag) {
                TAG_TEXT -> {
                    encodeText(options, sb)
                    return
                }
                "img" -> {
                    encodeImage(options, sb)
                    return
                }

                "script", "style" -> return

                "th", "td" -> sb.append("|")

                else -> if (isBlock) {
                    val lastLine = reLastLine.find(sb)?.groupValues?.firstOrNull() ?: ""
                    if (CharacterGroup.reNotWhitespace.matcher(lastLine).find()) {
                        sb.append("\n")
                    }
                }
            }

            var flusher = this.tmpFlusher[tag]
            val encodeSpanEnv = if (flusher != null) {
                // 一時的なバッファに子要素を出力して、後で何か処理する
                EncodeSpanEnv(
                    options = options,
                    listContext = listContext,
                    tag = tag,
                    sb = sb,
                    sbTmp = SpannableStringBuilder(),
                    spanStart = 0,
                )
            } else {
                // 現在のバッファに出力する
                flusher = originalFlusher
                EncodeSpanEnv(
                    options = options,
                    listContext = listContext,
                    tag = tag,
                    sb = sb,
                    sbTmp = sb,
                    spanStart = sb.length
                )
            }

            val childListContext = childListContext(tag, listContext)

            child_nodes.forEachIndexed { i, child ->
                if (!canSkipEncode(
                        isBlock,
                        curr = child,
                        parent = this,
                        prev = child_nodes.elementAtOrNull(i - 1),
                        next = child_nodes.elementAtOrNull(i + 1)
                    )
                ) {
                    child.encodeSpan(options, encodeSpanEnv.sbTmp, childListContext)
                }
            }

            flusher(encodeSpanEnv)

            if (isBlock) {
                // ブロック要素
                // 末尾の改行が２文字未満なら改行を追加する
                var appendCount = 2 - sb.lastBrCount()
                if (listContext.inList()) appendCount = min(1, appendCount)
                when (tag) {
                    "tr" -> appendCount = min(1, appendCount)
                    "thead", "tfoot", "tbody" -> appendCount = 0
                }
                repeat(appendCount) { sb.append("\n") }
            } else {
                // インライン要素で改行タグでテキストがカラでないなら、改行を追加する
                if ("br" == tag && sb.isNotEmpty()) sb.append('\n')
            }
        }
    }

    // split attributes
    private fun parseAttributes(text: String): HashMap<String, String> {
        val dst = HashMap<String, String>()
        val m = reAttribute.matcher(text)
        while (m.find()) {
            val name = m.groupEx(1)!!.lowercase()
            val value = decodeEntity(m.groupEx(3))
            dst[name] = value
        }
        return dst
    }

    fun decodeHTML(options: DecodeOptions, src: String?): SpannableStringBuilder {

        if (options.linkHelper?.isMisskey == true && !options.forceHtml) {
            return MisskeyMarkdownDecoder.decodeMarkdown(options, src)
        }

        val sb = SpannableStringBuilder()

        try {
            if (src != null) {
                // parse HTML node tree
                val tracker = TokenParser(src)
                val rootNode = Node()
                while (TAG_END != tracker.tag) {
                    rootNode.addChild(tracker, "")
                }

                // encode to SpannableStringBuilder
                rootNode.encodeSpan(options, sb, ListContext(type = ListType.None, 0, 0, 0, 0))

                // 末尾の空白を取り除く
                sb.removeEndWhitespaces()
            }
        } catch (ex: Throwable) {
            log.e(ex, "decodeHTML failed.")
        }

        return sb
    }

    fun decodeMentions(
        parser: TootParser,
        status: TootStatus,
    ): Spannable? {
        val linkHelper = parser.linkHelper
        val mentionList: List<TootMention>? = status.mentions
        val link_tag: Any = status

        if (mentionList == null || mentionList.isEmpty()) return null

        val sb = SpannableStringBuilder()
        for (item in mentionList) {
            if (sb.isNotEmpty()) sb.append(" ")

            val fullAcct = getFullAcctOrNull(
                item.acct,
                item.url,
                linkHelper,
                status.account
            )

            val linkInfo = when (fullAcct) {
                null -> LinkInfo(
                    url = item.url,
                    caption = "@${item.acct.pretty}",
                    ac = null,
                    mention = item,
                    tag = link_tag
                )
                else -> LinkInfo(
                    url = item.url,
                    caption = "@${(if (PrefB.bpMentionFullAcct.value) fullAcct else item.acct).pretty}",
                    ac = daoAcctColor.load(fullAcct),
                    mention = item,
                    tag = link_tag
                )
            }

            val start = sb.length
            sb.append(linkInfo.caption)
            val end = sb.length
            if (end > start) sb.setSpan(
                MyClickableSpan(linkInfo),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return sb
    }

    private val reNormalLink = """\A(\w+://)[^/]*""".asciiPattern()

    // URLの表記を短くする
    // Punycode のデコードはサーバ側で行われる?ので、ここでは元リンクの表示テキストを元にURL短縮を試みる
    fun shortenUrl(originalUrl: CharSequence): CharSequence {
        try {

            val m = reNormalLink.matcher(originalUrl)
            if (m.find()) return SpannableStringBuilder().apply {
                // 文字装飾をそのまま残したいのでsubSequenceを返す

                // WebUIでは非表示スパンに隠れているが、
                // 通常のリンクなら スキーマ名 + :// が必ず出現する
                val schema = m.groupEx(1)
                val start = if (schema?.startsWith("http") == true) {
                    // http,https の場合はスキーマ表記を省略する
                    schema.length
                } else {
                    // その他のスキーマもMastodonでは許容される
                    // この場合はスキーマ名を省略しない
                    // https://github.com/tootsuite/mastodon/pull/7810
                    0
                }

                val length = originalUrl.length
                val limit = m.end() + 10 // 10 characters for ( path + query + fragment )
                if (length > limit) {
                    append(originalUrl.subSequence(start, limit))
                    append('…')
                } else {
                    append(originalUrl.subSequence(start, length))
                }
            }
        } catch (ex: Throwable) {
            log.e(ex, "shortenUrl failed.")
        }

        return originalUrl
    }

    private val reNicodic = """\Ahttps?://dic.nicovideo.jp/a/([^?#/]+)""".asciiPattern()

    private fun formatLinkCaption(
        options: DecodeOptions,
        originalCaption: CharSequence,
        href: String,
    ) = LinkInfo(
        caption = originalCaption,
        url = href,
        tag = options.linkTag
    ).also { linkInfo ->
        when (originalCaption.firstOrNull()) {

            // #hashtag は変更しない
            '#' -> {
            }

            // @mention
            '@' -> {

                fun afterFullAcctResolved(fullAcct: Acct) {
                    linkInfo.ac = daoAcctColor.load(fullAcct)
                    if (options.mentionFullAcct || PrefB.bpMentionFullAcct.value) {
                        linkInfo.caption = "@${fullAcct.pretty}"
                    }
                }

                // https://github.com/tateisu/SubwayTooter/issues/108
                // check mentions to skip getAcctFromUrl
                val mention = options.mentions?.find { it.url == href }
                if (mention != null) {
                    getFullAcctOrNull(
                        mention.acct,
                        href,
                        options.authorDomain,
                        options.linkHelper,
                    )?.let { afterFullAcctResolved(it) }
                } else {

                    // case A
                    // Account.note does not have mentions metadata.
                    // fallback to resolve acct by mention URL.

                    // case B
                    // https://mastodon.juggler.jp/@tateisu/104897039191509317
                    // リモートのMisskeyからMastodonに流れてきた投稿をSTで見ると
                    // (元タンスでの)ローカルメンションに対して間違って閲覧者のドメインが補われる
                    // STのバグかと思ったけど、データみたらmentionsメタデータに一つ目のメンションのURLが含まれてない。
                    // 閲覧サーバがメンションに含まれるアカウントを解決できなかった際に発生するらしい。

                    // メンション情報がない場合がありうる。
                    // acctのドメイン部分を補う際、閲覧者のドメインや投稿者のドメインへの変換を試みる

                    val rawAcct = Acct.parse(originalCaption.toString().substring(1))
                    getFullAcctOrNull(
                        rawAcct,
                        href,
                        options.authorDomain,
                        options.linkHelper,
                    )?.let { fullAcct ->

                        // mentionメタデータを捏造する
                        linkInfo.mention = TootMention(
                            id = EntityId.DEFAULT,
                            url = href,
                            acct = fullAcct,
                            username = rawAcct.username
                        )

                        afterFullAcctResolved(fullAcct)
                    }
                }
            }

            else -> {

                val context = options.context

                when {

                    context == null || !options.short || href.isEmpty() -> {
                    }

                    options.isMediaAttachment(href) -> {
                        // 添付メディアのURLなら絵文字に変換する
                        linkInfo.caption = SpannableString(href).apply {
                            setSpan(
                                SvgEmojiSpan(context, "emj_1f5bc.svg", scale = 1f),
                                0,
                                length,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                        return@also
                    }

                    else -> {
                        // ニコニコ大百科のURLを変える
                        val m = reNicodic.matcher(href)
                        when {
                            m.find() -> {
                                linkInfo.caption =
                                    SpannableString(
                                        "${
                                            m.groupEx(1)!!.decodePercent()
                                        }:nicodic:"
                                    ).apply {
                                        setSpan(
                                            EmojiImageSpan(context, R.drawable.nicodic),
                                            length - 9,
                                            length,
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                    }
                                return@also
                            }

                            else -> linkInfo.caption = shortenUrl(originalCaption)
                        }
                    }
                }
            }
        }
    }

    private fun init1() {
        defineEntity("amp", '&') // ampersand
        defineEntity("gt", '>') // greater than
        defineEntity("lt", '<') // less than
        defineEntity("quot", '"') // double quote
        defineEntity("apos", '\'') // single quote
        defineEntity("AElig", chr(198)) // capital AE diphthong (ligature)
        defineEntity("Aacute", chr(193)) // capital A, acute accent
        defineEntity("Acirc", chr(194)) // capital A, circumflex accent
        defineEntity("Agrave", chr(192)) // capital A, grave accent
        defineEntity("Aring", chr(197)) // capital A, ring
        defineEntity("Atilde", chr(195)) // capital A, tilde
        defineEntity("Auml", chr(196)) // capital A, dieresis or umlaut mark
        defineEntity("Ccedil", chr(199)) // capital C, cedilla
        defineEntity("ETH", chr(208)) // capital Eth, Icelandic
        defineEntity("Eacute", chr(201)) // capital E, acute accent
        defineEntity("Ecirc", chr(202)) // capital E, circumflex accent
        defineEntity("Egrave", chr(200)) // capital E, grave accent
        defineEntity("Euml", chr(203)) // capital E, dieresis or umlaut mark
        defineEntity("Iacute", chr(205)) // capital I, acute accent
        defineEntity("Icirc", chr(206)) // capital I, circumflex accent
        defineEntity("Igrave", chr(204)) // capital I, grave accent
        defineEntity("Iuml", chr(207)) // capital I, dieresis or umlaut mark
        defineEntity("Ntilde", chr(209)) // capital N, tilde
        defineEntity("Oacute", chr(211)) // capital O, acute accent
        defineEntity("Ocirc", chr(212)) // capital O, circumflex accent
        defineEntity("Ograve", chr(210)) // capital O, grave accent
        defineEntity("Oslash", chr(216)) // capital O, slash
        defineEntity("Otilde", chr(213)) // capital O, tilde
        defineEntity("Ouml", chr(214)) // capital O, dieresis or umlaut mark
        defineEntity("THORN", chr(222)) // capital THORN, Icelandic
        defineEntity("Uacute", chr(218)) // capital U, acute accent
        defineEntity("Ucirc", chr(219)) // capital U, circumflex accent
        defineEntity("Ugrave", chr(217)) // capital U, grave accent
        defineEntity("Uuml", chr(220)) // capital U, dieresis or umlaut mark
        defineEntity("Yacute", chr(221)) // capital Y, acute accent
        defineEntity("aacute", chr(225)) // small a, acute accent
        defineEntity("acirc", chr(226)) // small a, circumflex accent
        defineEntity("aelig", chr(230)) // small ae diphthong (ligature)
        defineEntity("agrave", chr(224)) // small a, grave accent
        defineEntity("aring", chr(229)) // small a, ring
        defineEntity("atilde", chr(227)) // small a, tilde
        defineEntity("auml", chr(228)) // small a, dieresis or umlaut mark
        defineEntity("ccedil", chr(231)) // small c, cedilla
        defineEntity("eacute", chr(233)) // small e, acute accent
        defineEntity("ecirc", chr(234)) // small e, circumflex accent
        defineEntity("egrave", chr(232)) // small e, grave accent
        defineEntity("eth", chr(240)) // small eth, Icelandic
        defineEntity("euml", chr(235)) // small e, dieresis or umlaut mark
        defineEntity("iacute", chr(237)) // small i, acute accent
        defineEntity("icirc", chr(238)) // small i, circumflex accent
        defineEntity("igrave", chr(236)) // small i, grave accent
        defineEntity("iuml", chr(239)) // small i, dieresis or umlaut mark
        defineEntity("ntilde", chr(241)) // small n, tilde
        defineEntity("oacute", chr(243)) // small o, acute accent
        defineEntity("ocirc", chr(244)) // small o, circumflex accent
        defineEntity("ograve", chr(242)) // small o, grave accent
        defineEntity("oslash", chr(248)) // small o, slash
        defineEntity("otilde", chr(245)) // small o, tilde
        defineEntity("ouml", chr(246)) // small o, dieresis or umlaut mark
        defineEntity("szlig", chr(223)) // small sharp s, German (sz ligature)
        defineEntity("thorn", chr(254)) // small thorn, Icelandic
        defineEntity("uacute", chr(250)) // small u, acute accent
        defineEntity("ucirc", chr(251)) // small u, circumflex accent
        defineEntity("ugrave", chr(249)) // small u, grave accent
        defineEntity("uuml", chr(252)) // small u, dieresis or umlaut mark
        defineEntity("yacute", chr(253)) // small y, acute accent
        defineEntity("yuml", chr(255)) // small y, dieresis or umlaut mark
        defineEntity("copy", chr(169)) // copyright sign
        defineEntity("reg", chr(174)) // registered sign
        defineEntity("nbsp", chr(160)) // non breaking space
        defineEntity("iexcl", chr(161))
        defineEntity("cent", chr(162))
        defineEntity("pound", chr(163))
        defineEntity("curren", chr(164))
        defineEntity("yen", chr(165))
        defineEntity("brvbar", chr(166))
        defineEntity("sect", chr(167))
        defineEntity("uml", chr(168))
        defineEntity("ordf", chr(170))
        defineEntity("laquo", chr(171))
        defineEntity("not", chr(172))
        defineEntity("shy", chr(173))
        defineEntity("macr", chr(175))
        defineEntity("deg", chr(176))
        defineEntity("plusmn", chr(177))
        defineEntity("sup1", chr(185))
        defineEntity("sup2", chr(178))
        defineEntity("sup3", chr(179))
        defineEntity("acute", chr(180))
        defineEntity("micro", chr(181))
        defineEntity("para", chr(182))
        defineEntity("middot", chr(183))
        defineEntity("cedil", chr(184))
        defineEntity("ordm", chr(186))
        defineEntity("raquo", chr(187))
        defineEntity("frac14", chr(188))
        defineEntity("frac12", chr(189))
        defineEntity("frac34", chr(190))
        defineEntity("iquest", chr(191))
        defineEntity("times", chr(215))
    }

    private fun init2() {
        defineEntity("divide", chr(247))
        defineEntity("OElig", chr(338))
        defineEntity("oelig", chr(339))
        defineEntity("Scaron", chr(352))
        defineEntity("scaron", chr(353))
        defineEntity("Yuml", chr(376))
        defineEntity("fnof", chr(402))
        defineEntity("circ", chr(710))
        defineEntity("tilde", chr(732))
        defineEntity("Alpha", chr(913))
        defineEntity("Beta", chr(914))
        defineEntity("Gamma", chr(915))
        defineEntity("Delta", chr(916))
        defineEntity("Epsilon", chr(917))
        defineEntity("Zeta", chr(918))
        defineEntity("Eta", chr(919))
        defineEntity("Theta", chr(920))
        defineEntity("Iota", chr(921))
        defineEntity("Kappa", chr(922))
        defineEntity("Lambda", chr(923))
        defineEntity("Mu", chr(924))
        defineEntity("Nu", chr(925))
        defineEntity("Xi", chr(926))
        defineEntity("Omicron", chr(927))
        defineEntity("Pi", chr(928))
        defineEntity("Rho", chr(929))
        defineEntity("Sigma", chr(931))
        defineEntity("Tau", chr(932))
        defineEntity("Upsilon", chr(933))
        defineEntity("Phi", chr(934))
        defineEntity("Chi", chr(935))
        defineEntity("Psi", chr(936))
        defineEntity("Omega", chr(937))
        defineEntity("alpha", chr(945))
        defineEntity("beta", chr(946))
        defineEntity("gamma", chr(947))
        defineEntity("delta", chr(948))
        defineEntity("epsilon", chr(949))
        defineEntity("zeta", chr(950))
        defineEntity("eta", chr(951))
        defineEntity("theta", chr(952))
        defineEntity("iota", chr(953))
        defineEntity("kappa", chr(954))
        defineEntity("lambda", chr(955))
        defineEntity("mu", chr(956))
        defineEntity("nu", chr(957))
        defineEntity("xi", chr(958))
        defineEntity("omicron", chr(959))
        defineEntity("pi", chr(960))
        defineEntity("rho", chr(961))
        defineEntity("sigmaf", chr(962))
        defineEntity("sigma", chr(963))
        defineEntity("tau", chr(964))
        defineEntity("upsilon", chr(965))
        defineEntity("phi", chr(966))
        defineEntity("chi", chr(967))
        defineEntity("psi", chr(968))
        defineEntity("omega", chr(969))
        defineEntity("thetasym", chr(977))
        defineEntity("upsih", chr(978))
        defineEntity("piv", chr(982))
        defineEntity("ensp", chr(8194))
        defineEntity("emsp", chr(8195))
        defineEntity("thinsp", chr(8201))
        defineEntity("zwnj", chr(8204))
        defineEntity("zwj", chr(8205))
        defineEntity("lrm", chr(8206))
        defineEntity("rlm", chr(8207))
        defineEntity("ndash", chr(8211))
        defineEntity("mdash", chr(8212))
        defineEntity("lsquo", chr(8216))
        defineEntity("rsquo", chr(8217))
        defineEntity("sbquo", chr(8218))
        defineEntity("ldquo", chr(8220))
        defineEntity("rdquo", chr(8221))
        defineEntity("bdquo", chr(8222))
        defineEntity("dagger", chr(8224))
        defineEntity("Dagger", chr(8225))
        defineEntity("bull", chr(8226))
        defineEntity("hellip", chr(8230))
        defineEntity("permil", chr(8240))
        defineEntity("prime", chr(8242))
        defineEntity("Prime", chr(8243))
        defineEntity("lsaquo", chr(8249))
        defineEntity("rsaquo", chr(8250))
        defineEntity("oline", chr(8254))
        defineEntity("frasl", chr(8260))
        defineEntity("euro", chr(8364))
        defineEntity("image", chr(8465))
        defineEntity("weierp", chr(8472))
        defineEntity("real", chr(8476))
        defineEntity("trade", chr(8482))
        defineEntity("alefsym", chr(8501))
        defineEntity("larr", chr(8592))
        defineEntity("uarr", chr(8593))
        defineEntity("rarr", chr(8594))
        defineEntity("darr", chr(8595))
        defineEntity("harr", chr(8596))
        defineEntity("crarr", chr(8629))
        defineEntity("lArr", chr(8656))
    }

    private fun init3() {
        defineEntity("uArr", chr(8657))
        defineEntity("rArr", chr(8658))
        defineEntity("dArr", chr(8659))
        defineEntity("hArr", chr(8660))
        defineEntity("forall", chr(8704))
        defineEntity("part", chr(8706))
        defineEntity("exist", chr(8707))
        defineEntity("empty", chr(8709))
        defineEntity("nabla", chr(8711))
        defineEntity("isin", chr(8712))
        defineEntity("notin", chr(8713))
        defineEntity("ni", chr(8715))
        defineEntity("prod", chr(8719))
        defineEntity("sum", chr(8721))
        defineEntity("minus", chr(8722))
        defineEntity("lowast", chr(8727))
        defineEntity("radic", chr(8730))
        defineEntity("prop", chr(8733))
        defineEntity("infin", chr(8734))
        defineEntity("ang", chr(8736))
        defineEntity("and", chr(8743))
        defineEntity("or", chr(8744))
        defineEntity("cap", chr(8745))
        defineEntity("cup", chr(8746))
        defineEntity("int", chr(8747))
        defineEntity("there4", chr(8756))
        defineEntity("sim", chr(8764))
        defineEntity("cong", chr(8773))
        defineEntity("asymp", chr(8776))
        defineEntity("ne", chr(8800))
        defineEntity("equiv", chr(8801))
        defineEntity("le", chr(8804))
        defineEntity("ge", chr(8805))
        defineEntity("sub", chr(8834))
        defineEntity("sup", chr(8835))
        defineEntity("nsub", chr(8836))
        defineEntity("sube", chr(8838))
        defineEntity("supe", chr(8839))
        defineEntity("oplus", chr(8853))
        defineEntity("otimes", chr(8855))
        defineEntity("perp", chr(8869))
        defineEntity("sdot", chr(8901))
        defineEntity("lceil", chr(8968))
        defineEntity("rceil", chr(8969))
        defineEntity("lfloor", chr(8970))
        defineEntity("rfloor", chr(8971))
        defineEntity("lang", chr(9001))
        defineEntity("rang", chr(9002))
        defineEntity("loz", chr(9674))
        defineEntity("spades", chr(9824))
        defineEntity("clubs", chr(9827))
        defineEntity("hearts", chr(9829))
        defineEntity("diams", chr(9830))
    }

    init {
        init1()
        init2()
        init3()
    }
}
