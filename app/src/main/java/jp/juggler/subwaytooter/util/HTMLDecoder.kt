package jp.juggler.subwaytooter.util

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.*
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.span.*
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.HighlightWord
import jp.juggler.util.*
import java.util.*
import java.util.regex.Pattern
import kotlin.math.max
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
    private fun _addEntity(s: String, c: Char) {
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
                        log.trace(ex)
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
                tag = m.groupEx(2)!!.toLowerCase(Locale.JAPAN)

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
		var order: Int = 0
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

        val indent: String
            get() = " ".repeat(2 * max(0, nestLevelOrdered + nestLevelUnordered + nestLevelDefinition - 1))

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
			while (end > 0 && CharacterGroup.isWhitespace(this[end - 1].toInt())) --end

			// 入力の最初の非空白文字の位置を調べておく
			var firstNonSpace = 0
			while (firstNonSpace <end && CharacterGroup.isWhitespace(this[firstNonSpace].toInt())) ++firstNonSpace

            var i = 0
            while (i < end) {
                var lineStart = i
                while (i < end && this[i] != '\n') ++i
                val lineEnd = if (i >= end) end else i + 1
				++i

                // 行頭の空白を削る
//                while (lineStart < lineEnd &&
//                    this[lineStart] != '\n' &&
//                    CharacterGroup.isWhitespace(this[lineStart].toInt())
//                ) ++lineStart

				// 最初の非空白文字以降の行を出力する
				if(lineEnd > firstNonSpace) {
					dst.add(this.subSequence(lineStart, lineEnd) as SpannableStringBuilder)
				}
            }
			if(dst.isEmpty()){
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

                if (DEBUG_HTML_PARSER)
                    log.d("addChild: $indent|${child.tag} $open_type [${child.text.quoteMeta()}]")

                if (open_type == OpenType.Open) {
                    child.addChild(t, "$indent--")
                }
            }
            if (DEBUG_HTML_PARSER) log.d("addChild: $indent)$tag")
        }

        fun encodeSpan(
			options: DecodeOptions,
			sb: SpannableStringBuilder,
			listContext: ListContext
		) {
			val isBlock = blockLevelElements.contains(tag)
            when(tag){
				TAG_TEXT->{
					if (options.context != null && options.decodeEmoji) {
						sb.append(options.decodeEmoji(decodeEntity(text)))
					} else {
						sb.append(decodeEntity(text))
					}
					return
				}
				"img"->{
					val attrs = parseAttributes(text)

					if (options.unwrapEmojiImageTag) {
						val cssClass = attrs["class"]
						val title = attrs["title"]
						val url = attrs["src"]
						val alt = attrs["alt"]
						if (cssClass != null
							&& title != null
							&& cssClass.contains("emojione")
							&& reShortcode.matcher(title).find()
						) {
							sb.append(options.decodeEmoji(title))
							return
						} else if (cssClass == "emoji" && url != null && alt != null && reNotestockEmojiAlt.matches(alt)) {
							// notestock custom emoji
							sb.run {
								val start = length
								append(alt)
								val end = length
								setSpan(
									NetworkEmojiSpan(url, scale = options.enlargeCustomEmoji),
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
							val span = MyClickableSpan(LinkInfo(url = url, ac = null, tag = null, caption = caption, mention = null))
							sb.setSpan(span, start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
						}
						sb.append(" ")
					}
					sb.append("/>")
					return
				}

				"script","style" -> return

				"th", "td" -> sb.append("|")

				else -> if( isBlock && tag !="script" && tag != "style" ){
					val lastLine = reLastLine.find(sb)?.groupValues?.firstOrNull() ?: ""
					if(CharacterGroup.reNotWhitespace.matcher(lastLine).find()){
						sb.append("\n")
					}
				}
			}

			var spanStart = 0

			val tmpFlusherOriginal: (SpannableStringBuilder) -> Unit = {

				when (tag) {
					"s", "strike", "del" -> {
						sb.setSpan(StrikethroughSpan(), spanStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
					}
					"em" -> {
						sb.setSpan(fontSpan(Typeface.defaultFromStyle(Typeface.ITALIC)), spanStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
					}
					"strong" -> {
						sb.setSpan(StyleSpan(Typeface.BOLD), spanStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
					}
					"tr" -> {
						sb.append("|")
					}

					"style", "script" -> {
						// sb_tmpにレンダリングした分は読み捨てる
					}
					"h1" -> {
						sb.setSpan(StyleSpan(Typeface.BOLD), spanStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
						sb.setSpan(RelativeSizeSpan(1.8f), spanStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
					}
					"h2" -> {
						sb.setSpan(StyleSpan(Typeface.BOLD), spanStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
						sb.setSpan(RelativeSizeSpan(1.6f), spanStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
					}
					"h3" -> {
						sb.setSpan(StyleSpan(Typeface.BOLD), spanStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
						sb.setSpan(RelativeSizeSpan(1.4f), spanStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
					}
					"h4" -> {
						sb.setSpan(StyleSpan(Typeface.BOLD), spanStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
						sb.setSpan(RelativeSizeSpan(1.2f), spanStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
					}
					"h5" -> {
						sb.setSpan(StyleSpan(Typeface.BOLD), spanStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
						sb.setSpan(RelativeSizeSpan(1.0f), spanStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
					}
					"h6" -> {
						sb.setSpan(StyleSpan(Typeface.BOLD), spanStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
						sb.setSpan(RelativeSizeSpan(0.8f), spanStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
					}
					"pre" -> {
						sb.setSpan(BackgroundColorSpan(0x40808080), spanStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
						sb.setSpan(RelativeSizeSpan(0.7f), spanStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
						sb.setSpan(fontSpan(Typeface.MONOSPACE), spanStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
					}
					"code" ->{
						sb.setSpan(BackgroundColorSpan(0x40808080), spanStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
						sb.setSpan(fontSpan(Typeface.MONOSPACE), spanStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
					}
					"hr" -> sb.append("----------")
				}
			}

			val tmpFlusher = when (tag) {
				"a" -> {
					{ sb_tmp ->
						val linkInfo = formatLinkCaption(options, sb_tmp, href ?: "")
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
									val word = HighlightWord.load(range.word) ?: continue
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
				}

				"style", "script" -> {
					{
						// 読み捨てる
						// 最適化によりtmpFlusherOriginalとこのラムダが同一オブジェクトにならないようにする
					}
				}

				"blockquote" -> {
					{ sb_tmp ->
						val bg_color = listContext.quoteColor()

						// TextView の文字装飾では「ブロック要素の入れ子」を表現できない
						// 内容の各行の始端に何か追加するというのがまずキツい
						// しかし各行の頭に引用マークをつけないと引用のネストで意味が通じなくなってしまう


						val startItalic = sb.length
						sb_tmp.splitLines().forEach { line ->
							val lineStart = sb.length
							sb.append("> ")
							sb.setSpan(BackgroundColorSpan(bg_color), lineStart, lineStart + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
							sb.append(line)
						}
						sb.setSpan(fontSpan(Typeface.defaultFromStyle(Typeface.ITALIC)), startItalic, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
					}
				}

				"li" -> {
					{ sb_tmp ->
						val lineHeader1 = listContext.increment()
						val lineHeader2 = " ".repeat(lineHeader1.length)
						sb_tmp.splitLines().forEachIndexed { i, line ->
							sb.append(if (i == 0) lineHeader1 else lineHeader2)
							sb.append(line)
						}
					}
				}

				"dt" -> {
					{ sb_tmp ->
						val prefix = listContext.increment()
						val startBold = sb.length
						sb_tmp.splitLines().forEach { line ->
							sb.append(prefix)
							sb.append(line)
						}
						sb.setSpan(fontSpan(Typeface.defaultFromStyle(Typeface.BOLD)), startBold, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
					}
				}

				"dd" -> {
					{ sb_tmp ->
						val prefix = listContext.increment() + "　　"
						sb_tmp.splitLines().forEach { line ->
							sb.append(prefix)
							sb.append(line)
						}
					}
				}

				else -> tmpFlusherOriginal
			}

			val sb_tmp = if(tmpFlusher == tmpFlusherOriginal) {
				sb
			}else {
				SpannableStringBuilder()
			}

			spanStart = sb_tmp.length

			val childListContext = when (tag) {
				"ol" -> listContext.subOrdered()
				"ul" -> listContext.subUnordered()
				"dl" -> listContext.subDefinition()
				"blockquote" ->  listContext.subQuote()
				else -> listContext
			}
			fun String.tagsCanRemoveNearSpaces() = when(this){
				"li","ol","ul","dl","dt","dd","blockquote","h1","h2","h3","h4","h5","h6",
				"table","tbody","thead","tfoot","tr","td","th" ->true
				else->false
			}
			val childLast = child_nodes.size-1
			child_nodes.forEachIndexed{ i,child->
				if(child.tag == TAG_TEXT && child.text.isBlank() && isBlock){
					val preNode = child_nodes.elementAtOrNull(i-1)
					val nextNode = child_nodes.elementAtOrNull(i+1)
					if(preNode?.tag?.tagsCanRemoveNearSpaces()== true ||
						nextNode?.tag?.tagsCanRemoveNearSpaces()==true ||
						((i==0 || i==childLast) && tag.tagsCanRemoveNearSpaces())
					){
						return@forEachIndexed
					}
				}
				child.encodeSpan(options, sb_tmp, childListContext)
			}

			tmpFlusher(sb_tmp)

			if (isBlock) {
				// ブロック要素
				// 末尾の改行が２文字未満なら改行を追加する
				var appendCount = 2 - sb.lastBrCount()
				if( listContext.inList()) appendCount = min(1,appendCount)
				when(tag){
					"tr" -> appendCount = min(1,appendCount)
					"thead","tfoot","tbody" -> appendCount = 0
				}
				repeat(appendCount){ sb.append( "\n" )}
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
            val name = m.groupEx(1)!!.toLowerCase(Locale.JAPAN)
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
                rootNode.encodeSpan(options, sb,  ListContext(type = ListType.None, 0, 0, 0,0))

                // 末尾の空白を取り除く
                sb.removeEndWhitespaces()
            }
        } catch (ex: Throwable) {
            log.trace(ex)
        }

        return sb
    }

    fun decodeMentions(
		linkHelper: LinkHelper,
		status: TootStatus
	): Spannable? {
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

            val linkInfo = if (fullAcct != null) {
                LinkInfo(
					url = item.url,
					caption = "@${(if (Pref.bpMentionFullAcct(App1.pref)) fullAcct else item.acct).pretty}",
					ac = AcctColor.load(fullAcct),
					mention = item,
					tag = link_tag
				)
            } else {
                LinkInfo(
					url = item.url,
					caption = "@${item.acct.pretty}",
					ac = null,
					mention = item,
					tag = link_tag
				)
            }

            val start = sb.length
            sb.append(linkInfo.caption)
            val end = sb.length
            if (end > start)
                sb.setSpan(
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
            log.trace(ex)
        }

        return originalUrl
    }

    private val reNicodic = """\Ahttps?://dic.nicovideo.jp/a/([^?#/]+)""".asciiPattern()

    private fun formatLinkCaption(
		options: DecodeOptions,
		originalCaption: CharSequence,
		href: String
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
					linkInfo.ac = AcctColor.load(fullAcct)
					if (options.mentionFullAcct || Pref.bpMentionFullAcct(App1.pref)) {
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
						options.linkHelper,
						options.mentionDefaultHostDomain
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
						options.linkHelper,
						options.mentionDefaultHostDomain
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
								EmojiImageSpan(context, R.drawable.emj_1f5bc_fe0f),
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
        _addEntity("amp", '&') // ampersand
        _addEntity("gt", '>') // greater than
        _addEntity("lt", '<') // less than
        _addEntity("quot", '"') // double quote
        _addEntity("apos", '\'') // single quote
        _addEntity("AElig", chr(198)) // capital AE diphthong (ligature)
        _addEntity("Aacute", chr(193)) // capital A, acute accent
        _addEntity("Acirc", chr(194)) // capital A, circumflex accent
        _addEntity("Agrave", chr(192)) // capital A, grave accent
        _addEntity("Aring", chr(197)) // capital A, ring
        _addEntity("Atilde", chr(195)) // capital A, tilde
        _addEntity("Auml", chr(196)) // capital A, dieresis or umlaut mark
        _addEntity("Ccedil", chr(199)) // capital C, cedilla
        _addEntity("ETH", chr(208)) // capital Eth, Icelandic
        _addEntity("Eacute", chr(201)) // capital E, acute accent
        _addEntity("Ecirc", chr(202)) // capital E, circumflex accent
        _addEntity("Egrave", chr(200)) // capital E, grave accent
        _addEntity("Euml", chr(203)) // capital E, dieresis or umlaut mark
        _addEntity("Iacute", chr(205)) // capital I, acute accent
        _addEntity("Icirc", chr(206)) // capital I, circumflex accent
        _addEntity("Igrave", chr(204)) // capital I, grave accent
        _addEntity("Iuml", chr(207)) // capital I, dieresis or umlaut mark
        _addEntity("Ntilde", chr(209)) // capital N, tilde
        _addEntity("Oacute", chr(211)) // capital O, acute accent
        _addEntity("Ocirc", chr(212)) // capital O, circumflex accent
        _addEntity("Ograve", chr(210)) // capital O, grave accent
        _addEntity("Oslash", chr(216)) // capital O, slash
        _addEntity("Otilde", chr(213)) // capital O, tilde
        _addEntity("Ouml", chr(214)) // capital O, dieresis or umlaut mark
        _addEntity("THORN", chr(222)) // capital THORN, Icelandic
        _addEntity("Uacute", chr(218)) // capital U, acute accent
        _addEntity("Ucirc", chr(219)) // capital U, circumflex accent
        _addEntity("Ugrave", chr(217)) // capital U, grave accent
        _addEntity("Uuml", chr(220)) // capital U, dieresis or umlaut mark
        _addEntity("Yacute", chr(221)) // capital Y, acute accent
        _addEntity("aacute", chr(225)) // small a, acute accent
        _addEntity("acirc", chr(226)) // small a, circumflex accent
        _addEntity("aelig", chr(230)) // small ae diphthong (ligature)
        _addEntity("agrave", chr(224)) // small a, grave accent
        _addEntity("aring", chr(229)) // small a, ring
        _addEntity("atilde", chr(227)) // small a, tilde
        _addEntity("auml", chr(228)) // small a, dieresis or umlaut mark
        _addEntity("ccedil", chr(231)) // small c, cedilla
        _addEntity("eacute", chr(233)) // small e, acute accent
        _addEntity("ecirc", chr(234)) // small e, circumflex accent
        _addEntity("egrave", chr(232)) // small e, grave accent
        _addEntity("eth", chr(240)) // small eth, Icelandic
        _addEntity("euml", chr(235)) // small e, dieresis or umlaut mark
        _addEntity("iacute", chr(237)) // small i, acute accent
        _addEntity("icirc", chr(238)) // small i, circumflex accent
        _addEntity("igrave", chr(236)) // small i, grave accent
        _addEntity("iuml", chr(239)) // small i, dieresis or umlaut mark
        _addEntity("ntilde", chr(241)) // small n, tilde
        _addEntity("oacute", chr(243)) // small o, acute accent
        _addEntity("ocirc", chr(244)) // small o, circumflex accent
        _addEntity("ograve", chr(242)) // small o, grave accent
        _addEntity("oslash", chr(248)) // small o, slash
        _addEntity("otilde", chr(245)) // small o, tilde
        _addEntity("ouml", chr(246)) // small o, dieresis or umlaut mark
        _addEntity("szlig", chr(223)) // small sharp s, German (sz ligature)
        _addEntity("thorn", chr(254)) // small thorn, Icelandic
        _addEntity("uacute", chr(250)) // small u, acute accent
        _addEntity("ucirc", chr(251)) // small u, circumflex accent
        _addEntity("ugrave", chr(249)) // small u, grave accent
        _addEntity("uuml", chr(252)) // small u, dieresis or umlaut mark
        _addEntity("yacute", chr(253)) // small y, acute accent
        _addEntity("yuml", chr(255)) // small y, dieresis or umlaut mark
        _addEntity("copy", chr(169)) // copyright sign
        _addEntity("reg", chr(174)) // registered sign
        _addEntity("nbsp", chr(160)) // non breaking space
        _addEntity("iexcl", chr(161))
        _addEntity("cent", chr(162))
        _addEntity("pound", chr(163))
        _addEntity("curren", chr(164))
        _addEntity("yen", chr(165))
        _addEntity("brvbar", chr(166))
        _addEntity("sect", chr(167))
        _addEntity("uml", chr(168))
        _addEntity("ordf", chr(170))
        _addEntity("laquo", chr(171))
        _addEntity("not", chr(172))
        _addEntity("shy", chr(173))
        _addEntity("macr", chr(175))
        _addEntity("deg", chr(176))
        _addEntity("plusmn", chr(177))
        _addEntity("sup1", chr(185))
        _addEntity("sup2", chr(178))
        _addEntity("sup3", chr(179))
        _addEntity("acute", chr(180))
        _addEntity("micro", chr(181))
        _addEntity("para", chr(182))
        _addEntity("middot", chr(183))
        _addEntity("cedil", chr(184))
        _addEntity("ordm", chr(186))
        _addEntity("raquo", chr(187))
        _addEntity("frac14", chr(188))
        _addEntity("frac12", chr(189))
        _addEntity("frac34", chr(190))
        _addEntity("iquest", chr(191))
        _addEntity("times", chr(215))

    }

    private fun init2() {
        _addEntity("divide", chr(247))
        _addEntity("OElig", chr(338))
        _addEntity("oelig", chr(339))
        _addEntity("Scaron", chr(352))
        _addEntity("scaron", chr(353))
        _addEntity("Yuml", chr(376))
        _addEntity("fnof", chr(402))
        _addEntity("circ", chr(710))
        _addEntity("tilde", chr(732))
        _addEntity("Alpha", chr(913))
        _addEntity("Beta", chr(914))
        _addEntity("Gamma", chr(915))
        _addEntity("Delta", chr(916))
        _addEntity("Epsilon", chr(917))
        _addEntity("Zeta", chr(918))
        _addEntity("Eta", chr(919))
        _addEntity("Theta", chr(920))
        _addEntity("Iota", chr(921))
        _addEntity("Kappa", chr(922))
        _addEntity("Lambda", chr(923))
        _addEntity("Mu", chr(924))
        _addEntity("Nu", chr(925))
        _addEntity("Xi", chr(926))
        _addEntity("Omicron", chr(927))
        _addEntity("Pi", chr(928))
        _addEntity("Rho", chr(929))
        _addEntity("Sigma", chr(931))
        _addEntity("Tau", chr(932))
        _addEntity("Upsilon", chr(933))
        _addEntity("Phi", chr(934))
        _addEntity("Chi", chr(935))
        _addEntity("Psi", chr(936))
        _addEntity("Omega", chr(937))
        _addEntity("alpha", chr(945))
        _addEntity("beta", chr(946))
        _addEntity("gamma", chr(947))
        _addEntity("delta", chr(948))
        _addEntity("epsilon", chr(949))
        _addEntity("zeta", chr(950))
        _addEntity("eta", chr(951))
        _addEntity("theta", chr(952))
        _addEntity("iota", chr(953))
        _addEntity("kappa", chr(954))
        _addEntity("lambda", chr(955))
        _addEntity("mu", chr(956))
        _addEntity("nu", chr(957))
        _addEntity("xi", chr(958))
        _addEntity("omicron", chr(959))
        _addEntity("pi", chr(960))
        _addEntity("rho", chr(961))
        _addEntity("sigmaf", chr(962))
        _addEntity("sigma", chr(963))
        _addEntity("tau", chr(964))
        _addEntity("upsilon", chr(965))
        _addEntity("phi", chr(966))
        _addEntity("chi", chr(967))
        _addEntity("psi", chr(968))
        _addEntity("omega", chr(969))
        _addEntity("thetasym", chr(977))
        _addEntity("upsih", chr(978))
        _addEntity("piv", chr(982))
        _addEntity("ensp", chr(8194))
        _addEntity("emsp", chr(8195))
        _addEntity("thinsp", chr(8201))
        _addEntity("zwnj", chr(8204))
        _addEntity("zwj", chr(8205))
        _addEntity("lrm", chr(8206))
        _addEntity("rlm", chr(8207))
        _addEntity("ndash", chr(8211))
        _addEntity("mdash", chr(8212))
        _addEntity("lsquo", chr(8216))
        _addEntity("rsquo", chr(8217))
        _addEntity("sbquo", chr(8218))
        _addEntity("ldquo", chr(8220))
        _addEntity("rdquo", chr(8221))
        _addEntity("bdquo", chr(8222))
        _addEntity("dagger", chr(8224))
        _addEntity("Dagger", chr(8225))
        _addEntity("bull", chr(8226))
        _addEntity("hellip", chr(8230))
        _addEntity("permil", chr(8240))
        _addEntity("prime", chr(8242))
        _addEntity("Prime", chr(8243))
        _addEntity("lsaquo", chr(8249))
        _addEntity("rsaquo", chr(8250))
        _addEntity("oline", chr(8254))
        _addEntity("frasl", chr(8260))
        _addEntity("euro", chr(8364))
        _addEntity("image", chr(8465))
        _addEntity("weierp", chr(8472))
        _addEntity("real", chr(8476))
        _addEntity("trade", chr(8482))
        _addEntity("alefsym", chr(8501))
        _addEntity("larr", chr(8592))
        _addEntity("uarr", chr(8593))
        _addEntity("rarr", chr(8594))
        _addEntity("darr", chr(8595))
        _addEntity("harr", chr(8596))
        _addEntity("crarr", chr(8629))
        _addEntity("lArr", chr(8656))

    }

    private fun init3() {
        _addEntity("uArr", chr(8657))
        _addEntity("rArr", chr(8658))
        _addEntity("dArr", chr(8659))
        _addEntity("hArr", chr(8660))
        _addEntity("forall", chr(8704))
        _addEntity("part", chr(8706))
        _addEntity("exist", chr(8707))
        _addEntity("empty", chr(8709))
        _addEntity("nabla", chr(8711))
        _addEntity("isin", chr(8712))
        _addEntity("notin", chr(8713))
        _addEntity("ni", chr(8715))
        _addEntity("prod", chr(8719))
        _addEntity("sum", chr(8721))
        _addEntity("minus", chr(8722))
        _addEntity("lowast", chr(8727))
        _addEntity("radic", chr(8730))
        _addEntity("prop", chr(8733))
        _addEntity("infin", chr(8734))
        _addEntity("ang", chr(8736))
        _addEntity("and", chr(8743))
        _addEntity("or", chr(8744))
        _addEntity("cap", chr(8745))
        _addEntity("cup", chr(8746))
        _addEntity("int", chr(8747))
        _addEntity("there4", chr(8756))
        _addEntity("sim", chr(8764))
        _addEntity("cong", chr(8773))
        _addEntity("asymp", chr(8776))
        _addEntity("ne", chr(8800))
        _addEntity("equiv", chr(8801))
        _addEntity("le", chr(8804))
        _addEntity("ge", chr(8805))
        _addEntity("sub", chr(8834))
        _addEntity("sup", chr(8835))
        _addEntity("nsub", chr(8836))
        _addEntity("sube", chr(8838))
        _addEntity("supe", chr(8839))
        _addEntity("oplus", chr(8853))
        _addEntity("otimes", chr(8855))
        _addEntity("perp", chr(8869))
        _addEntity("sdot", chr(8901))
        _addEntity("lceil", chr(8968))
        _addEntity("rceil", chr(8969))
        _addEntity("lfloor", chr(8970))
        _addEntity("rfloor", chr(8971))
        _addEntity("lang", chr(9001))
        _addEntity("rang", chr(9002))
        _addEntity("loz", chr(9674))
        _addEntity("spades", chr(9824))
        _addEntity("clubs", chr(9827))
        _addEntity("hearts", chr(9829))
        _addEntity("diams", chr(9830))

    }

    init {
        init1()
        init2()
        init3()
    }
}
