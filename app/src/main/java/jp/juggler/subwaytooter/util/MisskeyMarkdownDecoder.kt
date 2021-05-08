package jp.juggler.subwaytooter.util

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.util.SparseArray
import android.util.SparseBooleanArray
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.span.*
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.HighlightWord
import jp.juggler.subwaytooter.util.HTMLDecoder.shortenUrl
import jp.juggler.util.*
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.text.codePointBefore

private val brackets = arrayOf(
	"()",
	"()",
	"[]",
	"{}",
	"“”",
	"‘’",
	"‹›",
	"«»",
	"（）",
	"［］",
	"｛｝",
	"｟｠",
	"⦅⦆",
	"〚〛",
	"⦃⦄",
	"「」",
	"〈〉",
	"《》",
	"【】",
	"〔〕",
	"⦗⦘",
	"『』",
	"〖〗",
	"〘〙",
	"[]",
	"｢｣",
	"⟦⟧",
	"⟨⟩",
	"⟪⟫",
	"⟮⟯",
	"⟬⟭",
	"⌈⌉",
	"⌊⌋",
	"⦇⦈",
	"⦉⦊",
	"❛❜",
	"❝❞",
	"❨❩",
	"❪❫",
	"❴❵",
	"❬❭",
	"❮❯",
	"❰❱",
	"❲❳",
	"()",
	"﴾﴿",
	"〈〉",
	"⦑⦒",
	"⧼⧽",
	"﹙﹚",
	"﹛﹜",
	"﹝﹞",
	"⁽⁾",
	"₍₎",
	"⦋⦌",
	"⦍⦎",
	"⦏⦐",
	"⁅⁆",
	"⸢⸣",
	"⸤⸥",
	"⟅⟆",
	"⦓⦔",
	"⦕⦖",
	"⸦⸧",
	"⸨⸩",
	"⧘⧙",
	"⧚⧛",
	"⸜⸝",
	"⸌⸍",
	"⸂⸃",
	"⸄⸅",
	"⸉⸊",
	"᚛᚜",
	"༺༻",
	"༼༽",
	"⏜⏝",
	"⎴⎵",
	"⏞⏟",
	"⏠⏡",
	"﹁﹂",
	"﹃﹄",
	"︹︺",
	"︻︼",
	"︗︘",
	"︿﹀",
	"︽︾",
	"﹇﹈",
	"︷︸"
)

private val bracketsMap = HashMap<Char, Int>().apply {
    brackets.forEach {
        put(it[0], 1)
        put(it[1], -1)
    }
}
private val bracketsMapUrlSafe = HashMap<Char, Int>().apply {
    brackets.forEach {
        if ("([".contains(it[0])) return@forEach
        put(it[0], 1)
        put(it[1], -1)
    }
}

// 末尾の余計な」や（を取り除く。
// 例えば「#タグ」 とか （#タグ）
fun String.removeOrphanedBrackets(urlSafe: Boolean = false): String {
    var last = 0
    val nests = when (urlSafe) {
		true -> this.map {
			last += bracketsMapUrlSafe[it] ?: 0
			last
		}
        else -> this.map {

            last += bracketsMap[it] ?: 0
            last
        }
    }

    // first position of unmatched close
    var pos = nests.indexOfFirst { it < 0 }
    if (pos != -1) return substring(0, pos)

    // last position of unmatched open
    pos = nests.indexOfLast { it == 0 }
    return substring(0, pos + 1)
}

// 配列中の要素をラムダ式で変換して、戻り値が非nullならそこで処理を打ち切る
private inline fun <T, V> Array<out T>.firstNonNull(predicate: (T) -> V?): V? {
    for (element in this) return predicate(element) ?: continue
    return null
}

// 文字装飾の指定を溜めておいてノードの親子関係に応じて順序を調整して、最後にまとめて適用する
class SpanList {

    private class SpanPos(var start: Int, var end: Int, val span: Any)

    private val list = LinkedList<SpanPos>()

    fun setSpan(sb: SpannableStringBuilder) =
        list.forEach { sb.setSpan(it.span, it.start, it.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }

    fun addAll(other: SpanList) = list.addAll(other.list)

    fun addWithOffset(src: SpanList, offset: Int) {
        src.list.forEach { addLast(it.start + offset, it.end + offset, it.span) }
    }

    fun addFirst(start: Int, end: Int, span: Any) = when {
        start == end -> {
            // empty span allowed
        }

        start > end -> {
            MisskeyMarkdownDecoder.log.e("SpanList.add: range error! start=$start,end=$end,span=$span")
        }

        else -> {
            list.addFirst(SpanPos(start, end, span))
        }
    }

    fun addLast(start: Int, end: Int, span: Any) = when {
        start == end -> {
            // empty span allowed
        }

        start > end -> {
            MisskeyMarkdownDecoder.log.e("SpanList.add: range error! start=$start,end=$end,span=$span")
        }

        else -> {
            list.addLast(SpanPos(start, end, span))
        }
    }

    fun insert(offset: Int, length: Int) {
        for (sp in list) {
            when {
                sp.end <= offset -> {

                }

                sp.start <= offset -> {
                    sp.end += length
                }

                else -> {
                    sp.start += length
                    sp.end += length
                }
            }

        }
    }

}

// 正規表現パターンごとにMatcherをキャッシュする
// 対象テキストが変わったらキャッシュを捨てて更新する
// Matcher#region(start,text.length) を設定してから返す
// (同一テキストに対してMatcher.usePatternで正規表現パターンを切り替えるのも検討したが、usePatternの方が多分遅くなる)
internal object MatcherCache {

    private class MatcherCacheItem(
		var matcher: Matcher,
		var text: String,
		var textHashCode: Int
	)

    // スレッドごとにキャッシュ用のマップを持つ
    private val matcherCache =
        object : ThreadLocal<HashMap<Pattern, MatcherCacheItem>>() {
            override fun initialValue(): HashMap<Pattern, MatcherCacheItem> = HashMap()
        }

    internal fun matcher(
		pattern: Pattern,
		text: String,
		start: Int = 0,
		end: Int = text.length
	): Matcher {
        val m: Matcher
        val textHashCode = text.hashCode()
        val map = matcherCache.get()!!
        val item = map[pattern]
        if (item != null) {
            if (item.textHashCode != textHashCode || item.text != text) {
                item.matcher = pattern.matcher(text).apply {
                    useAnchoringBounds(true)
                }
                item.text = text
                item.textHashCode = textHashCode
            }
            m = item.matcher
        } else {
            m = pattern.matcher(text).apply {
                useAnchoringBounds(true)
            }
            map[pattern] = MatcherCacheItem(m, text, textHashCode)
        }
        m.region(start, end)
        return m
    }
}

// ```code``` マークダウン内部ではプログラムっぽい何かの文法強調表示が行われる
object MisskeySyntaxHighlighter {

    private val keywords = HashSet<String>().apply {

        val _keywords = arrayOf(
			"true",
			"false",
			"null",
			"nil",
			"undefined",
			"void",
			"var",
			"const",
			"let",
			"mut",
			"dim",
			"if",
			"then",
			"else",
			"switch",
			"match",
			"case",
			"default",
			"for",
			"each",
			"in",
			"while",
			"loop",
			"continue",
			"break",
			"do",
			"goto",
			"next",
			"end",
			"sub",
			"throw",
			"try",
			"catch",
			"finally",
			"enum",
			"delegate",
			"function",
			"func",
			"fun",
			"fn",
			"return",
			"yield",
			"async",
			"await",
			"require",
			"include",
			"import",
			"imports",
			"export",
			"exports",
			"from",
			"as",
			"using",
			"use",
			"internal",
			"module",
			"namespace",
			"where",
			"select",
			"struct",
			"union",
			"new",
			"delete",
			"this",
			"super",
			"base",
			"class",
			"interface",
			"abstract",
			"static",
			"public",
			"private",
			"protected",
			"virtual",
			"partial",
			"override",
			"extends",
			"implements",
			"constructor"
		)

        // lower
        addAll(_keywords)

        // UPPER
        addAll(_keywords.map { it.uppercase() })

        // Snake
        addAll(_keywords.map { k -> k[0].uppercase() + k.substring(1) })

        add("NaN")

        // 識別子に対して既存の名前と一致するか調べるようになったので、もはやソートの必要はない
    }

    private val symbolMap = SparseBooleanArray().apply {
        "=+-*/%~^&|><!?".forEach { put(it.code, true) }
    }

    // 文字列リテラルの開始文字のマップ
    private val stringStart = SparseBooleanArray().apply {
        "\"'`".forEach { put(it.code, true) }
    }

    private class Token(
		val length: Int,
		val color: Int = 0,
		val italic: Boolean = false,
		val comment: Boolean = false
	)

    private class Env(
		val source: String,
		val start: Int,
		val end: Int
	) {

        // 出力先2
        val spanList = SpanList()

        fun push(start: Int, token: Token) {
            val end = start + token.length

            if (token.comment) {
                spanList.addLast(start, end, ForegroundColorSpan(Color.BLACK or 0x808000))
            } else {
                var c = token.color
                if (c != 0) {
                    if (c < 0x1000000) {
                        c = c or Color.BLACK
                    }
                    spanList.addLast(start, end, ForegroundColorSpan(c))
                }
                if (token.italic) {
                    spanList.addLast(
						start,
						end,
						fontSpan(Typeface.defaultFromStyle(Typeface.ITALIC))
					)
                }
            }
        }

        // スキャン位置
        var pos: Int = start

        fun remainMatcher(pattern: Pattern): Matcher =
            MatcherCache.matcher(pattern, source, pos, end)

        fun parse(): SpanList {

            var i = start

            var lastEnd = start
            fun closeTextToken(textEnd: Int) {
                val length = textEnd - lastEnd
                if (length > 0) {
                    push(lastEnd, Token(length = length))
                    lastEnd = textEnd
                }
            }

            while (i < end) {
                pos = i
                val token = elements.firstNonNull {
                    val t = this.it()
                    when {
                        t == null -> null // not match
                        i + t.length > end -> null // overrun detected
                        else -> t
                    }
                }
                if (token == null) {
                    ++i
                    continue
                }
                closeTextToken(i)
                push(i, token)
                i += token.length
                lastEnd = i
            }
            closeTextToken(end)

            return spanList
        }
    }

    private val reLineComment = """\A//.*"""
        .asciiPattern()

    private val reBlockComment = """\A/\*.*?\*/"""
        .asciiPattern(Pattern.DOTALL)

    private val reNumber = """\A[\-+]?[\d.]+"""
        .asciiPattern()

    private val reLabel = """\A@([A-Z_-][A-Z0-9_-]*)"""
        .asciiPattern(Pattern.CASE_INSENSITIVE)

    private val reKeyword = """\A([A-Z_-][A-Z0-9_-]*)([ \t]*\()?"""
        .asciiPattern(Pattern.CASE_INSENSITIVE)

    private val reContainsAlpha = """[A-Za-z_]"""
        .asciiPattern()

    private const val charH80 = 0x80.toChar()

    private val elements = arrayOf<Env.() -> Token?>(

		// マルチバイト文字をまとめて読み飛ばす
		{
			var s = pos
			while (s < end && source[s] >= charH80) {
				++s
			}
			when {
				s > pos -> Token(length = s - pos)
				else -> null
			}
		},

		// 空白と改行をまとめて読み飛ばす
		{
			var s = pos
			while (s < end && source[s] <= ' ') {
				++s
			}
			when {
				s > pos -> Token(length = s - pos)
				else -> null
			}
		},

		// comment
		{
			val match = remainMatcher(reLineComment)
			when {
				!match.find() -> null
				else -> Token(length = match.end() - match.start(), comment = true)
			}
		},

		// block comment
		{
			val match = remainMatcher(reBlockComment)
			when {
				!match.find() -> null
				else -> Token(length = match.end() - match.start(), comment = true)
			}
		},

		// string
		{
			val beginChar = source[pos]
			if (!stringStart[beginChar.code]) return@arrayOf null
			var i = pos + 1
			while (i < end) {
				val char = source[i++]
				if (char == beginChar) {
					break // end
				} else if (char == '\n' || i >= end) {
					i = 0 // not string literal
					break
				} else if (char == '\\' && i < end) {
					++i // \" では閉じないようにする
				}
			}
			when {
				i <= pos -> null
				else -> Token(length = i - pos, color = 0xe96900)
			}
		},

		// regexp
		{
			if (source[pos] != '/') return@arrayOf null
			val regexp = StringBuilder()
			var i = pos + 1
			while (i < end) {
				val char = source[i++]
				if (char == '/') {
					break
				} else if (char == '\n' || i >= end) {
					i = 0 // not closed
					break
				} else {
					regexp.append(char)
					if (char == '\\' && i < end) {
						regexp.append(source[i++])
					}
				}
			}
			when {
				i == 0 -> null
				regexp.isEmpty() -> null
				regexp.first() == ' ' && regexp.last() == ' ' -> null
				else -> Token(length = regexp.length + 2, color = 0xe9003f)
			}
		},

		// label
		{
			// 直前に識別子があればNG
			val prev = if (pos <= 0) null else source[pos - 1]
			if (prev?.isLetterOrDigit() == true) return@arrayOf null

			val match = remainMatcher(reLabel)
			if (!match.find()) return@arrayOf null

			val matchEnd = match.end()
			when {
				// @user@host のように直後に@が続くのはNG
				matchEnd < end && source[matchEnd] == '@' -> null
				else -> Token(length = match.end() - pos, color = 0xe9003f)
			}
		},

		// number
		{
			val prev = if (pos <= 0) null else source[pos - 1]
			if (prev?.isLetterOrDigit() == true) return@arrayOf null
			val match = remainMatcher(reNumber)
			when {
				!match.find() -> null
				else -> Token(length = match.end() - pos, color = 0xae81ff)
			}
		},

		// method, property, keyword
		{
			// 直前の文字が識別子に使えるなら識別子の開始とはみなさない
			val prev = if (pos <= 0) null else source[pos - 1]
			if (prev?.isLetterOrDigit() == true || prev == '_') return@arrayOf null

			val match = remainMatcher(reKeyword)
			if (!match.find()) return@arrayOf null
			val kw = match.groupEx(1)!!
			val bracket = match.groupEx(2) // may null

			when {
				// 英数字や_を含まないキーワードは無視する
				// -moz-foo- や __ はキーワードだが、 - や -- はキーワードではない
				!reContainsAlpha.matcher(kw).find() -> null

				// メソッド呼び出しは対象が変数かプロパティかに関わらずメソッドの色になる
				bracket?.isNotEmpty() == true ->
					Token(length = kw.length, color = 0x8964c1, italic = true)

				// 変数や定数ではなくプロパティならプロパティの色になる
				prev == '.' -> Token(length = kw.length, color = 0xa71d5d)

				// 予約語ではない
				// 強調表示しないが、識別子単位で読み飛ばす
				!keywords.contains(kw) -> Token(length = kw.length)

				else -> when (kw) {

					// 定数
					"true", "false", "null", "nil", "undefined", "NaN" ->
						Token(length = kw.length, color = 0xae81ff)

					// その他の予約語
					else -> Token(length = kw.length, color = 0x2973b7)
				}
			}
		},

		// symbol
		{
			val c = source[pos]
			when {
				symbolMap.get(c.code, false) ->
					Token(length = 1, color = 0x42b983)
				c == '-' ->
					Token(length = 1, color = 0x42b983)
				else -> null
			}
		}
	)

    fun parse(source: String) = Env(source, 0, source.length).parse()
}

object MisskeyMarkdownDecoder {

    internal val log = LogCategory("MisskeyMarkdownDecoder")

    internal const val DEBUG = false

    // デコード結果にはメンションの配列を含む。TootStatusのパーサがこれを回収する。
    class SpannableStringBuilderEx(
		var mentions: ArrayList<TootMention>? = null
	) : SpannableStringBuilder()

    // ブロック要素は始端と終端の空行を除去したい
    private val reStartEmptyLines = """\A(?:[ 　]*?[\x0d\x0a]+)+""".toRegex()
    private val reEndEmptyLines = """[\s\x0d\x0a]+\z""".toRegex()
    private fun trimBlock(s: String) =
        s.replace(reStartEmptyLines, "")
            .replace(reEndEmptyLines, "")

    // 装飾つきテキストの出力時に使うデータの集まり
    internal class SpanOutputEnv(
		val options: DecodeOptions,
		val sb: SpannableStringBuilderEx
	) {

        val context: Context = options.context ?: error("missing context")
        val font_bold = ActMain.timeline_font_bold
        val linkHelper: LinkHelper? = options.linkHelper
        var spanList = SpanList()

        var start = 0

        fun fireRender(node: Node): SpanList {
            val spanList = SpanList()
            this.spanList = spanList
            this.start = sb.length
            val render = node.type.render
            this.render(node)
            return spanList
        }

        internal fun fireRenderChildNodes(parent: Node): SpanList {
            val parent_result = this.spanList
            parent.childNodes.forEach {
                val child_result = fireRender(it)
                parent_result.addAll(child_result)
            }
            this.spanList = parent_result
            return parent_result
        }

        // 直前の文字が改行文字でなければ改行する
        fun closePreviousBlock() {
            if (start > 0 && sb[start - 1] != '\n') {
                sb.append('\n')
                start = sb.length
            }
        }

        fun closeBlock() {
            if (sb.length > 0 && sb[sb.length - 1] != '\n') {
                val start = sb.length
                sb.append('\n')
                val end = sb.length
                sb.setSpan(RelativeSizeSpan(0.1f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        private fun applyHighlight(start: Int, end: Int) {
            val list = options.highlightTrie?.matchList(sb, start, end)
            if (list != null) {
                for (range in list) {
                    val word = HighlightWord.load(range.word) ?: continue
                    spanList.addLast(
						range.start,
						range.end,
						HighlightSpan(word.color_fg, word.color_bg)
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

        // テキストを追加する
        fun appendText(text: CharSequence, decodeEmoji: Boolean = false) {
            val start = sb.length
            if (decodeEmoji) {
                sb.append(options.decodeEmoji(text.toString()))
            } else {
                sb.append(text)
            }
            applyHighlight(start, sb.length)
        }

        // URL中のテキストを追加する
        private fun appendLinkText(display_url: String, href: String) {
            when {
                // 添付メディアのURLなら絵文字に変えてしまう
                options.isMediaAttachment(href) -> {
                    // リンクの一部に絵文字がある場合、絵文字スパンをセットしてからリンクをセットする
                    val start = sb.length
                    sb.append(href)
                    spanList.addFirst(
						start,
						sb.length,
						SvgEmojiSpan(context, "emj_1f5bc.svg",scale=1f),
					)
                }

                else -> appendText(shortenUrl(display_url))
            }
        }

        // リンクを追加する
        fun appendLink(
			text: String,
			url: String,
			allowShort: Boolean = false,
			mention: TootMention? = null
		) {
            when {
                allowShort -> appendLinkText(text, url)
                else -> appendText(text)
            }

            val fullAcct = if (!text.startsWith('@')) {
                null
                //リンクキャプションがメンション風でないならメンションとは扱わない
            } else {
                // 通称と色を調べる
                getFullAcctOrNull(
					rawAcct = Acct.parse(text.substring(1)),
					url = url,
					options.linkHelper,
					options.mentionDefaultHostDomain,
				)
            }

            val linkInfo = LinkInfo(
				caption = text,
				url = url,
				ac = fullAcct?.let { AcctColor.load(fullAcct) },
				tag = options.linkTag,
				mention = mention
			)
            // リンクの一部にハイライトがある場合、リンクをセットしてからハイライトをセットしないとクリック判定がおかしくなる。
            spanList.addFirst(start, sb.length, MyClickableSpan(linkInfo))
        }

        private fun prepareMentions(): ArrayList<TootMention> {
            var mentions = sb.mentions
            if (mentions == null) {
                mentions = ArrayList()
                sb.mentions = mentions
            }
            return mentions
        }

        fun appendMention(
			username: String,
			strHost: String?
		) {
            // ユーザが記述したacct
            val rawAcct = Acct.parse(username, strHost)

            val linkHelper = linkHelper
            if (linkHelper == null) {
                appendText("@${rawAcct.pretty}")
                return
            }

            // 長いacct
            // MFMでは投稿者のドメインを補うのはサーバ側の仕事の筈なので、options.mentionDefault…は見ない
            val fullAcct = rawAcct.followHost(linkHelper.apDomain)

            // mentionsメタデータに含まれるacct
            // ユーザの記述に因らず、サーバのホスト名同じなら短い、そうでなければ長いメンション
            val shortAcct = if (linkHelper.matchHost(fullAcct.host))
                Acct.parse(username)
            else
                fullAcct

            // リンク表記はユーザの記述やアプリ設定の影響を受ける
            val caption = "@${
                when {
                    Pref.bpMentionFullAcct(App1.pref) -> fullAcct
                    else -> rawAcct
                }.pretty
            }"

            var mention: TootMention? = null
            val url = when (strHost) {

                // https://github.com/syuilo/misskey/pull/3603

				"github.com", "twitter.com" ->
					"https://$strHost/$username" // no @

				"gmail.com" ->
					"mailto:$username@$strHost"

                else ->
                    // MFMはメンションからユーザのURIを調べる正式な方法がない
                    // たとえば @group_dev_jp@gup.pe の正式なURLは https://gup.pe/u/group_dev_jp
                    // だが、 misskey.io ではメンションのリンク先は https://misskey.io/@group_dev_jp@gup.pe になる
                    "https://${fullAcct.host?.ascii}/@$username"
                        .also { url ->
                            val mentions = prepareMentions()
                            mention = mentions.find { m -> m.acct == shortAcct }
                            if (mention == null) {
                                val newMention = TootMention(
									EntityId.DEFAULT, url, shortAcct.ascii, username
								)
                                mentions.add(newMention)
                                mention = newMention
                            }
                        }
            }
            appendLink(caption, url, mention = mention)
        }
    }

    ////////////////////////////////////////////////////////////////////////////

    private fun mixColor(
		@Suppress("SameParameterValue") col1: Int,
		col2: Int
	): Int = Color.rgb(
		(Color.red(col1) + Color.red(col2)) ushr 1,
		(Color.green(col1) + Color.green(col2)) ushr 1,
		(Color.blue(col1) + Color.blue(col2)) ushr 1
	)

    val quoteNestColors = intArrayOf(
		mixColor(Color.GRAY, 0x0000ff),
		mixColor(Color.GRAY, 0x0080ff),
		mixColor(Color.GRAY, 0x00ff80),
		mixColor(Color.GRAY, 0x00ff00),
		mixColor(Color.GRAY, 0x80ff00),
		mixColor(Color.GRAY, 0xff8000),
		mixColor(Color.GRAY, 0xff0000),
		mixColor(Color.GRAY, 0xff0080),
		mixColor(Color.GRAY, 0x8000ff)
	)

    // ノード種別とレンダリング関数
    internal enum class NodeType(val render: SpanOutputEnv.(Node) -> Unit) {

        TEXT({
			appendText(it.args[0], decodeEmoji = true)
		}),

        EMOJI({
			val code = it.args[0]
			if (code.isNotEmpty()) {
				appendText(":$code:", decodeEmoji = true)
			}
		}),

        MENTION({
			appendMention(it.args[0], it.args[1].notEmpty())
		}),

        LATEX({
			fireRenderChildNodes(it)
		}),

        HASHTAG({
			val linkHelper = linkHelper
			val tag = it.args[0]
			if (tag.isNotEmpty() && linkHelper != null) {
				appendLink(
					"#$tag",
					"https://${linkHelper.apiHost.ascii}/tags/" + tag.encodePercent()
				)
			}
		}),

        CODE_INLINE({
			val text = it.args[0]
			val sp = MisskeySyntaxHighlighter.parse(text)
			appendText(text)
			spanList.addWithOffset(sp, start)
			spanList.addLast(start, sb.length, BackgroundColorSpan(0x40808080))
			spanList.addLast(start, sb.length, fontSpan(Typeface.MONOSPACE))
		}),

        URL({
			val url = it.args[0]
			if (url.isNotEmpty()) {
				appendLink(url, url, allowShort = true)
			}
		}),

        CODE_BLOCK({
			closePreviousBlock()

			val text = trimBlock(it.args[0])
			val sp = MisskeySyntaxHighlighter.parse(text)
			appendText(text)
			spanList.addWithOffset(sp, start)
			spanList.addLast(start, sb.length, BackgroundColorSpan(0x40808080))
			spanList.addLast(start, sb.length, RelativeSizeSpan(0.7f))
			spanList.addLast(start, sb.length, fontSpan(Typeface.MONOSPACE))
			closeBlock()
		}),

        QUOTE_INLINE({
			val text = trimBlock(it.args[0])
			appendText(text)
			spanList.addLast(
				start,
				sb.length,
				BackgroundColorSpan(0x20808080)
			)
			spanList.addLast(
				start,
				sb.length,
				fontSpan(Typeface.defaultFromStyle(Typeface.ITALIC))
			)
		}),

        SEARCH({
			closePreviousBlock()

			val text = it.args[0]
			val kw_start = sb.length // キーワードの開始位置
			appendText(text)
			appendText(" ")
			start = sb.length // 検索リンクの開始位置

			appendLink(
				context.getString(R.string.search),
				"https://www.google.co.jp/search?q=${text.encodePercent()}"
			)
			spanList.addLast(kw_start, sb.length, RelativeSizeSpan(1.2f))

			closeBlock()
		}),

        BIG({
			val start = this.start
			fireRenderChildNodes(it)
			spanList.addLast(start, sb.length, MisskeyBigSpan(font_bold))
		}),

        BOLD({
			val start = this.start
			fireRenderChildNodes(it)
			spanList.addLast(start, sb.length, fontSpan(font_bold))
		}),

        STRIKE({
			val start = this.start
			fireRenderChildNodes(it)
			spanList.addLast(start, sb.length, StrikethroughSpan())
		}),

        SMALL({
			val start = this.start
			fireRenderChildNodes(it)
			spanList.addLast(start, sb.length, RelativeSizeSpan(0.7f))
		}),

        FUNCTION({
			val name = it.args.elementAtOrNull(0)
			appendText("[")
			appendText(name ?: "")
			appendText(" ")
			fireRenderChildNodes(it)
			appendText("]")
		}),

        ITALIC({
			val start = this.start
			fireRenderChildNodes(it)
			spanList.addLast(start, sb.length, fontSpan(Typeface.defaultFromStyle(Typeface.ITALIC)))
		}),

        MOTION({
			val start = this.start
			fireRenderChildNodes(it)
			spanList.addFirst(
				start,
				sb.length,
				MisskeyMotionSpan(ActMain.timeline_font)
			)
		}),

        LINK({
			val url = it.args[1]
			// val silent = data?.get(2)
			// silentはプレビュー表示を抑制するが、Subwayにはもともとないので関係なかった

			if (url.isNotEmpty()) {
				val start = this.start
				fireRenderChildNodes(it)
				val linkHelper = options.linkHelper
				if (linkHelper != null) {
					val linkInfo = LinkInfo(
						url = url,
						tag = options.linkTag,
						ac = TootAccount.getAcctFromUrl(url)?.let { acct -> AcctColor.load(acct) },
						caption = sb.substring(start, sb.length)
					)
					spanList.addFirst(start, sb.length, MyClickableSpan(linkInfo))
				}
			}
		}),

        TITLE({
			closePreviousBlock()

			val start = this.start
			fireRenderChildNodes(it) // 改行を含まないことが分かっている
			spanList.addLast(
				start,
				sb.length,
				android.text.style.AlignmentSpan.Standard(android.text.Layout.Alignment.ALIGN_CENTER)
			)
			spanList.addLast(
				start,
				sb.length,
				BackgroundColorSpan(0x20808080)
			)
			spanList.addLast(start, sb.length, RelativeSizeSpan(1.5f))

			closeBlock()
		}),

        CENTER({
			closePreviousBlock()

			val start = this.start
			fireRenderChildNodes(it)
			when {
				it.quoteNest > 0 -> {
					// 引用ネストの内部ではセンタリングさせると引用マーカーまで動いてしまうので
					// センタリングが機能しないようにする
				}

				else -> spanList.addLast(
					start,
					sb.length,
					android.text.style.AlignmentSpan.Standard(
						android.text.Layout.Alignment.ALIGN_CENTER
					)
				)
			}

			closeBlock()
		}),

        QUOTE_BLOCK({
			closePreviousBlock()

			val start = this.start

			// 末尾にある空白のテキストノードを除去する
			while (it.childNodes.isNotEmpty()) {
				val last = it.childNodes.last()
				if (last.type == TEXT && last.args[0].isBlank()) {
					it.childNodes.removeLast()
				} else {
					break
				}
			}

			fireRenderChildNodes(it)

			val bg_color = quoteNestColors[it.quoteNest % quoteNestColors.size]
			// TextView の文字装飾では「ブロック要素の入れ子」を表現できない
			// 内容の各行の始端に何か追加するというのがまずキツい
			// しかし各行の頭に引用マークをつけないと引用のネストで意味が通じなくなってしまう
			val tmp = sb.toString()
			//log.d("QUOTE_BLOCK tmp=${tmp} start=$start end=${tmp.length}")
			for (i in tmp.length - 1 downTo start) {
				val prevChar = when (i) {
					start -> '\n'
					else -> tmp[i - 1]
				}
				//log.d("QUOTE_BLOCK prevChar=${ String.format("%x",prevChar.toInt())}")
				if (prevChar == '\n') {
					//log.d("QUOTE_BLOCK insert! i=$i")
					sb.insert(i, "> ")
					spanList.insert(i, 2)
					spanList.addLast(
						i, i + 1,
						BackgroundColorSpan(bg_color)
					)
				}
			}

			spanList.addLast(
				start,
				sb.length,
				fontSpan(Typeface.defaultFromStyle(Typeface.ITALIC))
			)

			closeBlock()
		}),

        ROOT({
			fireRenderChildNodes(it)
		}),

        ;

        companion object {

            // あるノードが内部に持てるノード種別のマップ
            val mapAllowInside = HashMap<NodeType, HashSet<NodeType>>().apply {

                fun <T> hashSetOf(vararg values: T) = HashSet<T>().apply { addAll(values) }

                infix fun NodeType.wraps(inner: HashSet<NodeType>) = put(this, inner)

                // EMOJI, HASHTAG, MENTION, CODE_BLOCK, QUOTE_INLINE, SEARCH 等はマークダウン要素のネストを許可しない

                BIG wraps
                    hashSetOf(
						EMOJI, HASHTAG, MENTION, FUNCTION, LATEX,
						STRIKE, SMALL, ITALIC
					)

                BOLD wraps
                    hashSetOf(
						EMOJI, HASHTAG, MENTION, FUNCTION, LATEX, URL, LINK,
						STRIKE, SMALL, ITALIC
					)

                STRIKE wraps
                    hashSetOf(
						EMOJI, HASHTAG, MENTION, FUNCTION, LATEX, URL, LINK,
						BIG, BOLD, SMALL, ITALIC
					)

                SMALL wraps
                    hashSetOf(
						EMOJI, HASHTAG, MENTION, FUNCTION, LATEX, URL, LINK,
						BOLD, STRIKE, ITALIC
					)

                ITALIC wraps
                    hashSetOf(
						EMOJI, HASHTAG, MENTION, FUNCTION, LATEX, URL, LINK,
						BIG, BOLD, STRIKE, SMALL
					)

                MOTION wraps
                    hashSetOf(
						EMOJI, HASHTAG, MENTION, FUNCTION, LATEX, URL, LINK,
						BOLD, STRIKE, SMALL, ITALIC
					)

                LINK wraps
                    hashSetOf(
						EMOJI, MOTION, FUNCTION, LATEX,
						BIG, BOLD, STRIKE, SMALL, ITALIC
					)

                TITLE wraps
                    hashSetOf(
						EMOJI, HASHTAG, MENTION, FUNCTION, LATEX, URL, LINK,
						BIG, BOLD, STRIKE, SMALL, ITALIC,
						MOTION, CODE_INLINE
					)

                CENTER wraps
                    hashSetOf(
						EMOJI, HASHTAG, MENTION, FUNCTION, LATEX, URL, LINK,
						BIG, BOLD, STRIKE, SMALL, ITALIC,
						MOTION, CODE_INLINE
					)

                FUNCTION wraps hashSetOf(
					CODE_BLOCK, QUOTE_INLINE, SEARCH,
					EMOJI, HASHTAG, MENTION, LATEX, URL, LINK,
					BIG, BOLD, STRIKE, SMALL, ITALIC,
					MOTION, CODE_INLINE,
					TITLE, CENTER, QUOTE_BLOCK
				)

                LATEX wraps hashSetOf(
					CODE_BLOCK, QUOTE_INLINE, SEARCH,
					EMOJI, HASHTAG, MENTION, FUNCTION, URL, LINK,
					BIG, BOLD, STRIKE, SMALL, ITALIC,
					MOTION, CODE_INLINE,
					TITLE, CENTER, QUOTE_BLOCK
				)

                // all except ROOT,TEXT
                val allSet = hashSetOf(
					CODE_BLOCK, QUOTE_INLINE, SEARCH,
					EMOJI, HASHTAG, MENTION, FUNCTION, LATEX, URL, LINK,
					BIG, BOLD, STRIKE, SMALL, ITALIC,
					MOTION, CODE_INLINE,
					TITLE, CENTER, QUOTE_BLOCK
				)

                QUOTE_BLOCK wraps allSet

                ROOT wraps allSet

            }

        }
    }


    // マークダウン要素
    internal class Node(
		val type: NodeType, // ノード種別
		val args: Array<String> = emptyArray(), // 引数
		parentNode: Node?
	) {

        val childNodes = LinkedList<Node>()

        internal val quoteNest: Int = (parentNode?.quoteNest ?: 0) + when (type) {
			NodeType.QUOTE_BLOCK, NodeType.QUOTE_INLINE -> 1
            else -> 0
        }

    }

    // マークダウン要素の出現位置
    internal class NodeDetected(
		val node: Node,
		val start: Int, // テキスト中の開始位置
		val end: Int, // テキスト中の終了位置
		val textInside: String, // 内部範囲。親から継承する場合もあるし独自に作る場合もある
		val startInside: Int, // 内部範囲の開始位置
		private val lengthInside: Int // 内部範囲の終了位置
	) {

        val endInside: Int
            get() = startInside + lengthInside
    }

    internal class NodeParseEnv(
		val useFunction: Boolean,
		private val parentNode: Node,
		val text: String,
		start: Int,
		val end: Int
	) {

        private val childNodes = parentNode.childNodes
        private val allowInside: HashSet<NodeType> =
            NodeType.mapAllowInside[parentNode.type] ?: hashSetOf()

        // 直前のノードの終了位置
        internal var lastEnd = start

        // 注目中の位置
        internal var pos: Int = 0

        // 直前のノードの終了位置から次のノードの開始位置の手前までをresultに追加する
        private fun closeText(endText: Int) {
            val length = endText - lastEnd
            if (length <= 0) return
            val textInside = text.substring(lastEnd, endText)
            childNodes.add(Node(NodeType.TEXT, arrayOf(textInside), null))
        }

        fun remainMatcher(pattern: Pattern) =
            MatcherCache.matcher(pattern, text, pos, end)

        fun parseInside() {
            if (allowInside.isEmpty()) return

            var i = lastEnd //スキャン中の位置
            while (i < end) {
                // 注目位置の文字に関連するパーサー
				val lastParsers = nodeParserMap[text[i].code]
                if (lastParsers == null) {
                    ++i
                    continue
                }

                // パーサー用のパラメータを用意する
                // 部分文字列のコストは高くないと信じたい
                pos = i

                val detected = lastParsers.firstNonNull {
                    val d = this.it()
                    if (d == null) {
                        null
                    } else {
                        val n = d.node
                        if (!allowInside.contains(d.node.type)) {
                            log.w(
								"not allowed : ${parentNode.type} => ${n.type} ${
									text.substring(
										d.start,
										d.end
									)
								}"
							)
                            null
                        } else {
                            d
                        }
                    }
                }

                if (detected == null) {
                    ++i
                    continue
                }

                closeText(detected.start)
                childNodes.add(detected.node)
                i = detected.end
                lastEnd = i

                NodeParseEnv(
					useFunction,
					detected.node,
					detected.textInside,
					detected.startInside,
					detected.endInside
				).parseInside()
            }
            closeText(end)
        }

        internal fun makeDetected(
			type: NodeType,
			args: Array<String>,
			start: Int,
			end: Int,
			textInside: String,
			startInside: Int,
			lengthInside: Int
		): NodeDetected {

            val node = Node(type, args, parentNode)

            if (DEBUG) log.d(
				"NodeDetected: ${node.type} inside=${
					textInside.substring(startInside, startInside + lengthInside)
				}"
			)

            return NodeDetected(
				node,
				start,
				end,
				textInside,
				startInside,
				lengthInside
			)
        }
    }

    // ノードのパースを行う関数をキャプチャパラメータつきで生成する
    private fun simpleParser(
		pattern: Pattern, type: NodeType
	): NodeParseEnv.() -> NodeDetected? = {
        val matcher = remainMatcher(pattern)
        when {
            !matcher.find() -> null

            else -> {
                val textInside = matcher.groupEx(1)!!
                makeDetected(
					type,
					arrayOf(textInside),
					matcher.start(), matcher.end(),
					this.text, matcher.start(1), textInside.length
				)

            }
        }
    }


    // [title] 【title】
    // 直後に改行が必要だったが文末でも良いことになった https://github.com/syuilo/misskey/commit/79ffbf95db9d0cc019d06ab93b1bfa6ba0d4f9ae
//		val titleParser = simpleParser(
//			"""\A[【\[](.+?)[】\]](\n|\z)""".asciiPattern()
//			, NodeType.TITLE
//		)
    private val reTitle = """\A[【\[](.+?)[】\]](\n|\z)""".asciiPattern()
    private val reFunction = """\A\[([^\s\n\[\]]+) \s*([^\n\[\]]+)\]""".asciiPattern()
    private fun NodeParseEnv.titleParserImpl(): NodeDetected? {
        if (useFunction) {
            val type = NodeType.FUNCTION
            val matcher = remainMatcher(reFunction)
            if (matcher.find()) {
                val name = matcher.groupEx(1)?.ellipsizeDot3(3) ?: "???"
                val textInside = matcher.groupEx(2)!!
                return makeDetected(
					type,
					arrayOf(name),
					matcher.start(), matcher.end(),
					this.text, matcher.start(2), textInside.length
				)
            }
        }
        val type = NodeType.TITLE
        val matcher = remainMatcher(reTitle)
        if (matcher.find()) {
            val textInside = matcher.groupEx(1)!!
            return makeDetected(
				type,
				arrayOf(textInside),
				matcher.start(), matcher.end(),
				this.text, matcher.start(1), textInside.length
			)
        }

        return null
    }

	private val latexEscape = listOf(
		"\\#" to "#",
		"\\$" to "$",
		"\\%" to "%",
		"\\&" to "&",
		"\\_" to "_",
		"\\{" to "{",
		"\\}" to "}",
		"\\;" to "",
		"\\!" to "",

		"\\textbackslash" to "\\",
		"\\backslash" to "\\",
		"\\textasciitilde" to "~",
		"\\textasciicircum" to "^",
		"\\textbar" to "|",
		"\\textless" to "<",
		"\\textgreater" to ">",
	).sortedByDescending{ it.first.length}

	private fun partialEquals(src:String,start:Int,needle:String):Boolean{
		for( i in needle.indices){
			if( src[start+i] != needle[i]) return false
		}
		return true
	}

	private fun String.unescapeLatex():String{
		val sb = StringBuilder(length)
		val end = length
		var i = 0
		while(i<end) {
			val c = this[i]
			if (c == '\\') {
				val pair = latexEscape.find { partialEquals(this, i, it.first) }
				if (pair != null) {
					sb.append(pair.second)
					i += pair.first.length
					continue
				}
			}
			sb.append(c)
			++i
		}
		return sb.toString()
	}

	// \} \]はムダなエスケープに見えるが、androidでは必要なので削ってはいけない
	@Suppress("RegExpRedundantEscape")
	private val reLatexRemove = """\\(?:quad|Huge|atop|sf|scriptsize|bf|small|tiny|underline|large|(?:color)\{[^}]*\})""".toRegex()
	@Suppress("RegExpRedundantEscape")
	private val reLatex1 = """\\(?:(?:url)|(?:textcolor|colorbox)\{[^}]*\}|(?:fcolorbox|raisebox)\{[^}]*\}\{[^}]*\}|includegraphics\[[^]]*\])\{([^}]*)\}""".toRegex()
	@Suppress("RegExpRedundantEscape")
	private val reLatex2reversed = """\\(?:overset|href)\{([^}]+)\}\{([^}]+)\}""".toRegex()

	private fun String.removeLatex(): String {
        return this
            .replace(reLatexRemove,"")
			.replace(reLatex1,"$1")
			.replace(reLatex2reversed,"$2 $1")
			.unescapeLatex()
    }

    private val reLatexBlock = """^\\\[(.+?)\\\]""".asciiPattern(Pattern.MULTILINE or Pattern.DOTALL)
    private val reLatexInline = """\A\\\((.+?)\\\)""".asciiPattern()
    private fun NodeParseEnv.latexParserImpl(): NodeDetected? {
        val type = NodeType.LATEX
        var matcher = remainMatcher(reLatexBlock)
        if (matcher.find()) {
            val textInside = matcher.groupEx(1)!!.removeLatex().trim()
            return makeDetected(
				type,
				arrayOf(textInside),
				matcher.start(), matcher.end(),
				textInside, 0, textInside.length
			)
        }
        matcher = remainMatcher(reLatexInline)
        if (matcher.find()) {
            val textInside = matcher.groupEx(1)!!.removeLatex()
            return makeDetected(
				type,
				arrayOf(textInside),
				matcher.start(), matcher.end(),
				textInside, 0, textInside.length
			)
        }
        return null
    }


    // (マークダウン要素の特徴的な文字)と(パーサ関数の配列)のマップ
    private val nodeParserMap = SparseArray<Array<out NodeParseEnv.() -> NodeDetected?>>().apply {

        fun addParser(
			firstChars: String,
			vararg nodeParsers: NodeParseEnv.() -> NodeDetected?
		) {
            for (s in firstChars) {
				put(s.code, nodeParsers)
            }
        }

        // Strike ~~...~~
        addParser(
			"~", simpleParser(
			"""\A~~(.+?)~~""".asciiPattern(), NodeType.STRIKE
		)
		)

        // Quote "..."
        addParser(
			"\"", simpleParser(
			"""\A"([^\x0d\x0a]+?)\n"[\x0d\x0a]*""".asciiPattern(), NodeType.QUOTE_INLINE
		)
		)

        // Quote (行頭)>...(改行)
        // この正規表現の場合は \A ではなく ^ で各行の始端にマッチさせる
        val reQuoteBlock = """^>(?:[ 　]?)([^\x0d\x0a]*)(\x0a|\x0d\x0a?)?"""
            .asciiPattern(Pattern.MULTILINE)

        addParser(">", {
			if (pos > 0) {
				val c = text[pos - 1]
				if (c != '\r' && c != '\n') {
					//直前が改行文字ではない
					if (DEBUG) log.d("QUOTE: previous char is not line end. ${c} pos=$pos text=$text")
					return@addParser null
				}
			}

			var p = pos
			val content = StringBuilder()
			val matcher = remainMatcher(reQuoteBlock)
			while (true) {
				if (!matcher.find(p)) break
				p = matcher.end()
				if (content.isNotEmpty()) content.append('\n')
				content.append(matcher.groupEx(1))
				// 改行の直後なので次回マッチの ^ は大丈夫なはず…
			}
			if (content.isNotEmpty()) content.append('\n')

			if (p <= pos) {
				// > のあとに全く何もない
				if (DEBUG) log.d("QUOTE: not a quote")
				return@addParser null
			}
			val textInside = content.toString()

			makeDetected(
				NodeType.QUOTE_BLOCK,
				emptyArray(),
				pos, p,
				textInside, 0, textInside.length
			)
		})

        // 絵文字 :emoji:
        addParser(
			":",
			simpleParser(
				"""\A:([a-zA-Z0-9+-_@]+):""".asciiPattern(), NodeType.EMOJI
			)
		)

        // モーション
        addParser(
			"(", simpleParser(
			"""\A\Q(((\E(.+?)\Q)))\E""".asciiPattern(Pattern.DOTALL), NodeType.MOTION
		)
		)

        val reHtmlTag = """\A<([a-z]+)>(.+?)</\1>""".asciiPattern(Pattern.DOTALL)

        addParser("<", {
			val matcher = remainMatcher(reHtmlTag)
			when {
				!matcher.find() -> null

				else -> {
					val tagName = matcher.groupEx(1)!!
					val textInside = matcher.groupEx(2)!!

					fun a(type: NodeType) = makeDetected(
						type,
						arrayOf(textInside),
						matcher.start(), matcher.end(),
						this.text, matcher.start(2), textInside.length
					)

					when (tagName) {
						"motion" -> a(NodeType.MOTION)
						"center" -> a(NodeType.CENTER)
						"small" -> a(NodeType.SMALL)
						"i" -> a(NodeType.ITALIC)
						else -> null
					}
				}
			}
		})

        // ***big*** **bold**
        addParser(
			"*"
			// 処理順序に意味があるので入れ替えないこと
			// 記号列が長い順にパースを試す
			, simpleParser(
			"""^\Q***\E(.+?)\Q***\E""".asciiPattern(), NodeType.BIG
		), simpleParser(
			"""^\Q**\E(.+?)\Q**\E""".asciiPattern(), NodeType.BOLD
		)
		)

        val reAlnum = """[A-Za-z0-9]""".asciiPattern()

        // http(s)://....
        val reUrl = """\A(https?://[\w/:%#@${'$'}&?!()\[\]~.=+\-]+)"""
            .asciiPattern()

        addParser("h", {

			// 直前の文字が英数字ならURLの開始とはみなさない
			if (pos > 0 && MatcherCache.matcher(reAlnum, text, pos - 1, pos).find()) {
				return@addParser null
			}

			val matcher = remainMatcher(reUrl)
			if (!matcher.find()) {
				return@addParser null
			}

			val url = matcher.groupEx(1)!!.removeOrphanedBrackets(urlSafe = true)
			makeDetected(
				NodeType.URL,
				arrayOf(url),
				matcher.start(), matcher.start() + url.length,
				"", 0, 0
			)
		})

        // 検索
        val reSearchButton = """\A(検索|\[検索]|Search|\[Search])(\n|\z)"""
            .asciiPattern(Pattern.CASE_INSENSITIVE)

        fun NodeParseEnv.parseSearchPrev(): String? {
            val prev = text.substring(lastEnd, pos)
            val delm = prev.lastIndexOf('\n')
            val end = prev.length
            return when {
                end <= 1 -> null // キーワードを含まないくらい短い
                delm + 1 >= end - 1 -> null // 改行より後の部分が短すぎる
                !" 　".contains(prev.last()) -> null // 末尾が空白ではない
                else -> prev.substring(delm + 1, end - 1) // キーワード部分を返す
            }
        }

        val searchParser: NodeParseEnv.() -> NodeDetected? = {
            val matcher = remainMatcher(reSearchButton)
            when {
                !matcher.find() -> null

                else -> {
                    val keyword = parseSearchPrev()
                    when {
                        keyword?.isEmpty() != false -> null

                        else -> makeDetected(
							NodeType.SEARCH,
							arrayOf(keyword),
							pos - (keyword.length + 1), matcher.end(),
							this.text, pos - (keyword.length + 1), keyword.length
						)
                    }
                }
            }
        }


        val titleParser: NodeParseEnv.() -> NodeDetected? = { titleParserImpl() }

        // Link
        val reLink = """\A\??\[([^\n\[\]]+?)]\((https?://[\w/:%#@${'$'}&?!()\[\]~.=+\-]+?)\)"""
            .asciiPattern()

        val linkParser: NodeParseEnv.() -> NodeDetected? = {
            val matcher = remainMatcher(reLink)
            when {
                !matcher.find() -> null

                else -> {
                    val title = matcher.groupEx(1)!!
                    makeDetected(
						NodeType.LINK,
						arrayOf(
							title, matcher.groupEx(2)!! // url
							, text[pos].toString()   // silent なら "?" になる
						),
						matcher.start(), matcher.end(),
						this.text, matcher.start(1), title.length
					)
                }
            }
        }

        // [ はいろんな要素で使われる
        // searchの判定をtitleより前に行うこと。 「abc [検索] 」でtitleが優先されるとマズい
        // v10でもv12でもlinkの優先度はtitleやfunctionより高い
        addParser("[", searchParser, linkParser, titleParser)
        // その他の文字でも判定する
        addParser("【", titleParser)
        addParser("検Ss", searchParser)
        addParser("?", linkParser)

        // \(…\) \{…\}
        addParser("\\", { latexParserImpl() })

        // メールアドレスの@の手前に使える文字なら真
        val mailChars = SparseBooleanArray().apply {
            for (it in '0'..'9') {
				put(it.code, true)
            }
            for (it in 'A'..'Z') {
				put(it.code, true)
            }
            for (it in 'a'..'z') {
				put(it.code, true)
            }
            """${'$'}!#%&'`"*+-/=?^_{|}~""".forEach { put(it.code, true) }
        }

        addParser("@", {

			val matcher = remainMatcher(TootAccount.reMisskeyMentionMFM)

			when {
				!matcher.find() -> null

				else -> when {
					// 直前の文字がメールアドレスの@の手前に使える文字ならメンションではない
					pos > 0 && mailChars.get(text.codePointBefore(pos)) -> null

					else -> {
//						log.d(
//							"mention detected: ${matcher.group(1)},${matcher.group(2)},${
//								matcher.group(
//									0
//								)
//							}"
//						)
						makeDetected(
							NodeType.MENTION,
							arrayOf(
								matcher.groupEx(1)!!,
								matcher.groupEx(2) ?: "" // username, host
							),
							matcher.start(), matcher.end(),
							"", 0, 0
						)
					}
				}
			}
		})

        // Hashtag
        val reHashtag = """\A#([^\s.,!?#:]+)""".asciiPattern()
        val reDigitsOnly = """\A\d*\z""".asciiPattern()

        addParser("#", {

			if (pos > 0 && MatcherCache.matcher(reAlnum, text, pos - 1, pos).find()) {
				// 直前に英数字があるならタグにしない
				return@addParser null
			}

			val matcher = remainMatcher(reHashtag)
			if (!matcher.find()) {
				// タグにマッチしない
				return@addParser null
			}

			// 先頭の#を含まないタグテキスト
			val tag = matcher.groupEx(1)!!.removeOrphanedBrackets()

			if (tag.isEmpty() || tag.length > 50 || reDigitsOnly.matcher(tag).find()) {
				// 空文字列、50文字超過、数字だけのタグは不許可
				return@addParser null
			}

			makeDetected(
				NodeType.HASHTAG,
				arrayOf(tag),
				matcher.start(), matcher.start() + 1 + tag.length,
				"", 0, 0
			)
		})

        // code (ブロック、インライン)
        addParser(
			"`", simpleParser(
			"""\A```(?:.*)\n([\s\S]+?)\n```(?:\n|$)""".asciiPattern(), NodeType.CODE_BLOCK
			/*
            (A)
                ```code``` は 閉じる部分の前後に改行がないのでダメ
            (B)
                ```lang
                code
                code
                code
                ```
                はlang部分は表示されない
            (C)
                STの表示上の都合で閉じる部分の後の改行が複数あっても全て除去する
             */
		), simpleParser(
			// インラインコードは内部にとある文字を含むと認識されない。理由は顔文字と衝突するからだとか
			"""\A`([^`´\x0d\x0a]+)`""".asciiPattern(), NodeType.CODE_INLINE
		)
		)
    }

    // 入力テキストからタグを抽出するために使う
    // #を含まないタグ文字列のリスト、またはnullを返す
    fun findHashtags(src: String?): ArrayList<String>? {
        try {
            if (src != null) {
                val root = Node(NodeType.ROOT, emptyArray(), null)
                NodeParseEnv(useFunction = true, root, src, 0, src.length).parseInside()
                val result = ArrayList<String>()
                fun track(n: Node) {
                    if (n.type == NodeType.HASHTAG) result.add(n.args[0])
                    n.childNodes.forEach { track(it) }
                }
                track(root)
                if (result.isNotEmpty()) return result
            }
        } catch (ex: Throwable) {
            log.e(ex, "findHashtags failed.")
        }
        return null
    }

    // このファイルのエントリーポイント
    fun decodeMarkdown(options: DecodeOptions, src: String?) =
        SpannableStringBuilderEx().apply {
            val save = options.enlargeCustomEmoji
            options.enlargeCustomEmoji = 2.5f
            try {
                val env = SpanOutputEnv(options, this)

                if (src != null) {
                    val root = Node(NodeType.ROOT, emptyArray(), null)
                    NodeParseEnv(useFunction = (options.linkHelper?.misskeyVersion
						?: 12) >= 11, root, src, 0, src.length).parseInside()
                    env.fireRender(root).setSpan(env.sb)
                }

                // 末尾の空白を取り除く
                this.removeEndWhitespaces()

            } catch (ex: Throwable) {
                log.trace(ex)
                log.e(ex, "decodeMarkdown failed")
            } finally {
                options.enlargeCustomEmoji = save
            }
        }
}
