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
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.TootMention
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.mfm.MisskeyMarkdownDecoder
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.pref.lazyContext
import jp.juggler.subwaytooter.span.BlockCodeSpan
import jp.juggler.subwaytooter.span.BlockQuoteSpan
import jp.juggler.subwaytooter.span.DdSpan
import jp.juggler.subwaytooter.span.HighlightSpan
import jp.juggler.subwaytooter.span.HrSpan
import jp.juggler.subwaytooter.span.InlineCodeSpan
import jp.juggler.subwaytooter.span.LinkInfo
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.span.NetworkEmojiSpan
import jp.juggler.subwaytooter.span.OrderedListItemSpan
import jp.juggler.subwaytooter.span.SvgEmojiSpan
import jp.juggler.subwaytooter.span.UnorderedListItemSpan
import jp.juggler.subwaytooter.table.HighlightWord
import jp.juggler.subwaytooter.table.daoAcctColor
import jp.juggler.subwaytooter.table.daoHighlightWord
import jp.juggler.util.data.CharacterGroup
import jp.juggler.util.data.asciiPattern
import jp.juggler.util.data.groupEx
import jp.juggler.util.data.notEmpty
import jp.juggler.util.data.removeEndWhitespaces
import jp.juggler.util.data.replaceAll
import jp.juggler.util.data.replaceFirst
import jp.juggler.util.data.toHashSet
import jp.juggler.util.log.LogCategory
import jp.juggler.util.ui.FontSpan
import java.util.regex.Pattern
import kotlin.Any
import kotlin.Boolean
import kotlin.Char
import kotlin.CharSequence
import kotlin.Int
import kotlin.String
import kotlin.Throwable
import kotlin.Unit
import kotlin.also
import kotlin.apply
import kotlin.arrayOf
import kotlin.code
import kotlin.let
import kotlin.math.min
import kotlin.repeat
import kotlin.run

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

    private val entity_map by lazy {
        buildMap<String, Char> {
            fun chr(code: Int): Char = code.toChar()
            put("AElig", chr(198)) // capital AE diphthong (ligature)
            put("Aacute", chr(193)) // capital A, acute accent
            put("Acirc", chr(194)) // capital A, circumflex accent
            put("Agrave", chr(192)) // capital A, grave accent
            put("Alpha", chr(913))
            put("Aring", chr(197)) // capital A, ring
            put("Atilde", chr(195)) // capital A, tilde
            put("Auml", chr(196)) // capital A, dieresis or umlaut mark
            put("Beta", chr(914))
            put("Ccedil", chr(199)) // capital C, cedilla
            put("Chi", chr(935))
            put("Dagger", chr(8225))
            put("Delta", chr(916))
            put("ETH", chr(208)) // capital Eth, Icelandic
            put("Eacute", chr(201)) // capital E, acute accent
            put("Ecirc", chr(202)) // capital E, circumflex accent
            put("Egrave", chr(200)) // capital E, grave accent
            put("Epsilon", chr(917))
            put("Eta", chr(919))
            put("Euml", chr(203)) // capital E, dieresis or umlaut mark
            put("Gamma", chr(915))
            put("Iacute", chr(205)) // capital I, acute accent
            put("Icirc", chr(206)) // capital I, circumflex accent
            put("Igrave", chr(204)) // capital I, grave accent
            put("Iota", chr(921))
            put("Iuml", chr(207)) // capital I, dieresis or umlaut mark
            put("Kappa", chr(922))
            put("Lambda", chr(923))
            put("Mu", chr(924))
            put("Ntilde", chr(209)) // capital N, tilde
            put("Nu", chr(925))
            put("OElig", chr(338))
            put("Oacute", chr(211)) // capital O, acute accent
            put("Ocirc", chr(212)) // capital O, circumflex accent
            put("Ograve", chr(210)) // capital O, grave accent
            put("Omega", chr(937))
            put("Omicron", chr(927))
            put("Oslash", chr(216)) // capital O, slash
            put("Otilde", chr(213)) // capital O, tilde
            put("Ouml", chr(214)) // capital O, dieresis or umlaut mark
            put("Phi", chr(934))
            put("Pi", chr(928))
            put("Prime", chr(8243))
            put("Psi", chr(936))
            put("Rho", chr(929))
            put("Scaron", chr(352))
            put("Sigma", chr(931))
            put("THORN", chr(222)) // capital THORN, Icelandic
            put("Tau", chr(932))
            put("Theta", chr(920))
            put("Uacute", chr(218)) // capital U, acute accent
            put("Ucirc", chr(219)) // capital U, circumflex accent
            put("Ugrave", chr(217)) // capital U, grave accent
            put("Upsilon", chr(933))
            put("Uuml", chr(220)) // capital U, dieresis or umlaut mark
            put("Xi", chr(926))
            put("Yacute", chr(221)) // capital Y, acute accent
            put("Yuml", chr(376))
            put("Zeta", chr(918))
            put("aacute", chr(225)) // small a, acute accent
            put("acirc", chr(226)) // small a, circumflex accent
            put("acute", chr(180))
            put("aelig", chr(230)) // small ae diphthong (ligature)
            put("agrave", chr(224)) // small a, grave accent
            put("alefsym", chr(8501))
            put("alpha", chr(945))
            put("amp", '&') // ampersand
            put("and", chr(8743))
            put("ang", chr(8736))
            put("apos", '\'') // single quote
            put("aring", chr(229)) // small a, ring
            put("asymp", chr(8776))
            put("atilde", chr(227)) // small a, tilde
            put("auml", chr(228)) // small a, dieresis or umlaut mark
            put("bdquo", chr(8222))
            put("beta", chr(946))
            put("brvbar", chr(166))
            put("bull", chr(8226))
            put("cap", chr(8745))
            put("ccedil", chr(231)) // small c, cedilla
            put("cedil", chr(184))
            put("cent", chr(162))
            put("chi", chr(967))
            put("circ", chr(710))
            put("clubs", chr(9827))
            put("cong", chr(8773))
            put("copy", chr(169)) // copyright sign
            put("crarr", chr(8629))
            put("cup", chr(8746))
            put("curren", chr(164))
            put("dArr", chr(8659))
            put("dagger", chr(8224))
            put("darr", chr(8595))
            put("deg", chr(176))
            put("delta", chr(948))
            put("diams", chr(9830))
            put("divide", chr(247))
            put("eacute", chr(233)) // small e, acute accent
            put("ecirc", chr(234)) // small e, circumflex accent
            put("egrave", chr(232)) // small e, grave accent
            put("empty", chr(8709))
            put("emsp", chr(8195))
            put("ensp", chr(8194))
            put("epsilon", chr(949))
            put("equiv", chr(8801))
            put("eta", chr(951))
            put("eth", chr(240)) // small eth, Icelandic
            put("euml", chr(235)) // small e, dieresis or umlaut mark
            put("euro", chr(8364))
            put("exist", chr(8707))
            put("fnof", chr(402))
            put("forall", chr(8704))
            put("frac12", chr(189))
            put("frac14", chr(188))
            put("frac34", chr(190))
            put("frasl", chr(8260))
            put("gamma", chr(947))
            put("ge", chr(8805))
            put("gt", '>') // greater than
            put("hArr", chr(8660))
            put("harr", chr(8596))
            put("hearts", chr(9829))
            put("hellip", chr(8230))
            put("iacute", chr(237)) // small i, acute accent
            put("icirc", chr(238)) // small i, circumflex accent
            put("iexcl", chr(161))
            put("igrave", chr(236)) // small i, grave accent
            put("image", chr(8465))
            put("infin", chr(8734))
            put("int", chr(8747))
            put("iota", chr(953))
            put("iquest", chr(191))
            put("isin", chr(8712))
            put("iuml", chr(239)) // small i, dieresis or umlaut mark
            put("kappa", chr(954))
            put("lArr", chr(8656))
            put("lambda", chr(955))
            put("lang", chr(9001))
            put("laquo", chr(171))
            put("larr", chr(8592))
            put("lceil", chr(8968))
            put("ldquo", chr(8220))
            put("le", chr(8804))
            put("lfloor", chr(8970))
            put("lowast", chr(8727))
            put("loz", chr(9674))
            put("lrm", chr(8206))
            put("lsaquo", chr(8249))
            put("lsquo", chr(8216))
            put("lt", '<') // less than
            put("macr", chr(175))
            put("mdash", chr(8212))
            put("micro", chr(181))
            put("middot", chr(183))
            put("minus", chr(8722))
            put("mu", chr(956))
            put("nabla", chr(8711))
            put("nbsp", chr(160)) // non breaking space
            put("ndash", chr(8211))
            put("ne", chr(8800))
            put("ni", chr(8715))
            put("not", chr(172))
            put("notin", chr(8713))
            put("nsub", chr(8836))
            put("ntilde", chr(241)) // small n, tilde
            put("nu", chr(957))
            put("oacute", chr(243)) // small o, acute accent
            put("ocirc", chr(244)) // small o, circumflex accent
            put("oelig", chr(339))
            put("ograve", chr(242)) // small o, grave accent
            put("oline", chr(8254))
            put("omega", chr(969))
            put("omicron", chr(959))
            put("oplus", chr(8853))
            put("or", chr(8744))
            put("ordf", chr(170))
            put("ordm", chr(186))
            put("oslash", chr(248)) // small o, slash
            put("otilde", chr(245)) // small o, tilde
            put("otimes", chr(8855))
            put("ouml", chr(246)) // small o, dieresis or umlaut mark
            put("para", chr(182))
            put("part", chr(8706))
            put("permil", chr(8240))
            put("perp", chr(8869))
            put("phi", chr(966))
            put("pi", chr(960))
            put("piv", chr(982))
            put("plusmn", chr(177))
            put("pound", chr(163))
            put("prime", chr(8242))
            put("prod", chr(8719))
            put("prop", chr(8733))
            put("psi", chr(968))
            put("quot", '"') // double quote
            put("rArr", chr(8658))
            put("radic", chr(8730))
            put("rang", chr(9002))
            put("raquo", chr(187))
            put("rarr", chr(8594))
            put("rceil", chr(8969))
            put("rdquo", chr(8221))
            put("real", chr(8476))
            put("reg", chr(174)) // registered sign
            put("rfloor", chr(8971))
            put("rho", chr(961))
            put("rlm", chr(8207))
            put("rsaquo", chr(8250))
            put("rsquo", chr(8217))
            put("sbquo", chr(8218))
            put("scaron", chr(353))
            put("sdot", chr(8901))
            put("sect", chr(167))
            put("shy", chr(173))
            put("sigma", chr(963))
            put("sigmaf", chr(962))
            put("sim", chr(8764))
            put("spades", chr(9824))
            put("sub", chr(8834))
            put("sube", chr(8838))
            put("sum", chr(8721))
            put("sup", chr(8835))
            put("sup1", chr(185))
            put("sup2", chr(178))
            put("sup3", chr(179))
            put("supe", chr(8839))
            put("szlig", chr(223)) // small sharp s, German (sz ligature)
            put("tau", chr(964))
            put("there4", chr(8756))
            put("theta", chr(952))
            put("thetasym", chr(977))
            put("thinsp", chr(8201))
            put("thorn", chr(254)) // small thorn, Icelandic
            put("tilde", chr(732))
            put("times", chr(215))
            put("trade", chr(8482))
            put("uArr", chr(8657))
            put("uacute", chr(250)) // small u, acute accent
            put("uarr", chr(8593))
            put("ucirc", chr(251)) // small u, circumflex accent
            put("ugrave", chr(249)) // small u, grave accent
            put("uml", chr(168))
            put("upsih", chr(978))
            put("upsilon", chr(965))
            put("uuml", chr(252)) // small u, dieresis or umlaut mark
            put("weierp", chr(8472))
            put("xi", chr(958))
            put("yacute", chr(253)) // small y, acute accent
            put("yen", chr(165))
            put("yuml", chr(255)) // small y, dieresis or umlaut mark
            put("zeta", chr(950))
            put("zwj", chr(8205))
            put("zwnj", chr(8204))
        }
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
        val listOrders: List<String>? = null,
    ) {
        fun subOrdered(listOrders: List<String>) = ListContext(
            type = ListType.Ordered,
            nestLevelOrdered + 1,
            nestLevelUnordered,
            nestLevelDefinition,
            nestLevelQuote,
            listOrders = listOrders,
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

        fun increment() = ++order

        fun inList() = nestLevelOrdered + nestLevelUnordered + nestLevelDefinition > 0

        fun quoteColor(): Int {
            val quoteNestColors = MisskeyMarkdownDecoder.quoteNestColors
            return quoteNestColors[nestLevelQuote % quoteNestColors.size]
        }
    }

    private val reLastLine = """(?:\A|\n)([^\n]*)\z""".toRegex()

    private class Node {

        val child_nodes = ArrayList<Node>()

        val tag: String
        var text: String

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
            val node: Node,
            val sb: SpannableStringBuilder,
            val sbTmp: SpannableStringBuilder,
            val spanStart: Int,
        ) {
            val tag = node.tag
        }

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
                        FontSpan(Typeface.defaultFromStyle(Typeface.ITALIC)),
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

                "code" -> {
                    // インラインコード用の装飾
//                    sb.setSpan(
//                        BackgroundColorSpan(0x40808080),
//                        spanStart,
//                        sb.length,
//                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
//                    )
                    sb.setSpan(
                        InlineCodeSpan(),
                        spanStart,
                        sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                "hr" -> {
                    val start = sb.length
                    sb.append(" ")
                    sb.setSpan(
                        HrSpan(lazyContext),
                        start,
                        sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }

        val tmpFlusher = HashMap<String, EncodeSpanEnv.() -> Unit>().apply {

            fun add(vararg tags: String, block: EncodeSpanEnv.() -> Unit) {
                for (tag in tags) this[tag] = block
            }

            fun SpannableStringBuilder.deleteLastSpaces() {
                // 最低でも1文字は残す
                var last = length - 1
                while (last > 0) {
                    if (!CharacterGroup.isWhitespace(get(last).code)) break
                    --last
                }
                // 末尾の空白を除去
                if (last != length - 1) delete(last + 1, length)
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
                val start = sb.length
                sbTmp.deleteLastSpaces()
                sb.append(sbTmp)
                sb.setSpan(
                    BlockQuoteSpan(
                        context = lazyContext,
                        blockQuoteColor = listContext.quoteColor()
                    ),
                    start,
                    sb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                sb.setSpan(
                    FontSpan(Typeface.defaultFromStyle(Typeface.ITALIC)),
                    start,
                    sb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            add("pre") {
                val start = sb.length
                sbTmp.deleteLastSpaces()
                // インラインコード用の装飾を除去する
                sbTmp.getSpans(0, sbTmp.length, Any::class.java).forEach { span ->
                    if (span is BackgroundColorSpan && span.backgroundColor == 0x40808080) {
                        sbTmp.removeSpan(span)
                    } else if (span is InlineCodeSpan) {
                        sbTmp.removeSpan(span)
                    }
                }
                sb.append(sbTmp)
                sb.setSpan(
                    BlockCodeSpan(),
                    start,
                    sb.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            add("li") {
                sbTmp.deleteLastSpaces()
                val start = sb.length
                sb.append(sbTmp)
                when (listContext.type) {
                    ListType.Unordered -> {
                        sb.setSpan(
                            UnorderedListItemSpan(
                                level = listContext.nestLevelOrdered,
                            ),
                            start,
                            sb.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }

                    ListType.Ordered -> {
                        sb.setSpan(
                            OrderedListItemSpan(
                                order = node.text,
                                orders = listContext.listOrders ?: listOf(node.text),
                            ),
                            start,
                            sb.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }

                    else -> Unit
                }
            }

            add("dt") {
                sbTmp.deleteLastSpaces()
                val start = sb.length
                sb.append(sbTmp)
                sb.setSpan(
                    DdSpan(lazyContext, marginDp = 3f),
                    start,
                    sb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                sb.setSpan(
                    FontSpan(Typeface.defaultFromStyle(Typeface.BOLD)),
                    start,
                    sb.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            add("dd") {
                sbTmp.deleteLastSpaces()
                val start = sb.length
                sb.append(sbTmp)
                sb.setSpan(
                    DdSpan(lazyContext, marginDp = 24f),
                    start,
                    sb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        fun childListContext(node: Node, outerContext: ListContext) = when (node.tag) {
            "ol" -> {
                var n = 1
                val reversed = false
                val listItems = node.child_nodes.filter { it.tag == "li" }
                (if (reversed) listItems.reversed() else listItems).forEach { v ->
                    v.text = (n++).toString()
                }
                outerContext.subOrdered(listItems.map { it.text })
            }

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
                    node = this,
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
                    node = this,
                    sb = sb,
                    sbTmp = sb,
                    spanStart = sb.length
                )
            }

            val childListContext = childListContext(this, listContext)

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
                    context == null || !options.short || href.isEmpty() -> Unit

                    // 添付メディアのURLなら絵文字に変換する
                    options.isMediaAttachment(href) ->
                        linkInfo.caption = SpannableString(href).apply {
                            setSpan(
                                SvgEmojiSpan(context, "emj_1f5bc.svg", scale = 1f),
                                0,
                                length,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }

                    else -> linkInfo.caption = shortenUrl(originalCaption)
                }
            }
        }
    }
}
