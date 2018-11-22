package jp.juggler.subwaytooter.util

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.SparseArray
import android.util.SparseBooleanArray
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.EntityIdLong
import jp.juggler.subwaytooter.api.entity.TootMention
import jp.juggler.subwaytooter.span.EmojiImageSpan
import jp.juggler.subwaytooter.span.HighlightSpan
import jp.juggler.subwaytooter.span.MisskeyBigSpan
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.table.HighlightWord
import java.util.regex.Pattern
import uk.co.chrisjenx.calligraphy.CalligraphyTypefaceSpan
import java.util.*

// 指定した文字数までの部分文字列を返す
private fun String.safeSubstring(count : Int, offset : Int = 0) = when {
	offset + count <= length -> this.substring(offset, count)
	else -> this.substring(offset, length)
}

// 配列中の要素をラムダ式で変換して、戻り値が非nullならそこで処理を打ち切る
private inline fun <T, V> Array<out T>.firstNonNull(predicate : (T) -> V?) : V? {
	for(element in this) return predicate(element) ?: continue
	return null
}

class SpanPos(
	var start : Int,
	var end : Int,
	val span : Any
)

// 文字装飾の指定を溜めておいてノードの親子関係に応じて順序を調整して、最後にまとめて適用する
class SpanList {
	val list = LinkedList<SpanPos>()
	
	fun addFirst(start : Int, end : Int, span : Any) = when {
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
	
	fun addLast(start : Int, end : Int, span : Any) = when {
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
	
	fun addWithOffset(src : Iterable<SpanPos>, offset : Int) {
		for(sp in src) {
			addLast(sp.start + offset, sp.end + offset, sp.span)
		}
	}
	
	fun insert(offset : Int, length : Int) {
		for(sp in list) {
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
		addAll(_keywords.map { k -> k.toUpperCase() })
		
		// Snake
		addAll(_keywords.map { k -> k[0].toUpperCase() + k.substring(1) })
		
		add("NaN")
		
		// 識別子に対して既存の名前と一致するか調べるようになったので、もはやソートの必要はない
	}
	
	private val symbolMap = SparseBooleanArray().apply {
		for(c in "=+-*/%~^&|><!?") {
			this.put(c.toInt(), true)
		}
	}
	
	// 文字列リテラルの開始文字のマップ
	private val stringStart = SparseBooleanArray().apply {
		for(c in "\"'`") {
			this.put(c.toInt(), true)
		}
	}
	
	private class Token(
		val length : Int,
		val color : Int = 0,
		val italic : Boolean = false,
		val comment : Boolean = false
	)
	
	private class Env(val source : String) {
		
		// 出力先2
		val spanList = SpanList()
		
		// 残り部分
		var remain : String = source
			private set
		
		// スキャン位置
		var pos : Int = 0
			set(value) {
				field = value
				remain = source.substring(value)
			}
		
		fun push(start : Int, token : Token) {
			val end = start + token.length
			
			if(token.comment) {
				spanList.addLast(start, end, ForegroundColorSpan(Color.BLACK or 0x808000))
			} else {
				var c = token.color
				if(c != 0) {
					if(c < 0x1000000) {
						c = c or Color.BLACK
					}
					spanList.addLast(start, end, ForegroundColorSpan(c))
				}
				if(token.italic) {
					spanList.addLast(
						start,
						end,
						CalligraphyTypefaceSpan(Typeface.defaultFromStyle(Typeface.ITALIC))
					)
				}
			}
		}
		
		fun parse() : SpanList {
			
			var lastEnd = 0
			fun closeTextToken(textEnd : Int) {
				val length = textEnd - lastEnd
				if(length > 0) {
					push(lastEnd, Token(length = length))
					lastEnd = textEnd
				}
			}
			
			while(remain.isNotEmpty()) {
				val token = elements.firstNonNull { this.it() }
				if(token == null) {
					++ pos
				} else {
					closeTextToken(pos)
					push(pos, token)
					this.pos += token.length
					lastEnd = pos
				}
			}
			closeTextToken(pos)
			
			return spanList
		}
	}
	
	private val reLineComment = Pattern.compile("""\A//.*""")
	private val reBlockComment = Pattern.compile("""\A/\*.*?\*/""", Pattern.DOTALL)
	private val reNumber = Pattern.compile("""\A[+-]?[\d.]+""")
	private val reLabel = Pattern.compile("""\A@([A-Z_-][A-Z0-9_-]*)""", Pattern.CASE_INSENSITIVE)
	private val reKeyword =
		Pattern.compile("""\A([A-Z_-][A-Z0-9_-]*)([ \t]*\()?""", Pattern.CASE_INSENSITIVE)
	
	private val elements = arrayOf<Env.() -> Token?>(
		
		// comment
		{
			val match = reLineComment.matcher(remain)
			when {
				! match.find() -> null
				else -> Token(length = match.end(), comment = true)
			}
		},
		
		// block comment
		{
			val match = reBlockComment.matcher(remain)
			when {
				! match.find() -> null
				else -> Token(length = match.end(), comment = true)
			}
		},
		
		// string
		{
			val beginChar = remain[0]
			if(! stringStart[beginChar.toInt()]) return@arrayOf null
			var len = 1
			while(len < remain.length) {
				val char = remain[len ++]
				if(char == beginChar) {
					break // end
				} else if(char == '\n' || len >= remain.length) {
					len = 0 // not string literal
					break
				} else if(char == '\\' && len < remain.length) {
					++ len // \" では閉じないようにする
				}
			}
			when(len) {
				0 -> null
				else -> Token(length = len, color = 0xe96900)
			}
		},
		
		// regexp
		{
			if(remain[0] != '/') return@arrayOf null
			val regexp = StringBuilder()
			var notClosed = false
			var i = 1
			while(i < remain.length) {
				val char = remain[i ++]
				if(char == '/') {
					break
				} else if(char == '\n' || i >= remain.length) {
					notClosed = true
					break
				} else {
					regexp.append(char)
					if(char == '\\' && i < remain.length) {
						regexp.append(remain[i ++])
					}
				}
			}
			when {
				notClosed -> null
				regexp.isEmpty() -> null
				regexp[0] == ' ' && regexp[regexp.length - 1] == ' ' -> null
				else -> Token(length = regexp.length + 2, color = 0xe9003f)
			}
		},
		
		// label
		{
			// 直前に識別子があればNG
			val prev = if(pos <= 0) null else source[pos - 1]
			if(prev?.isLetterOrDigit() == true) return@arrayOf null
			
			val match = reLabel.matcher(remain)
			if(! match.find()) return@arrayOf null
			
			val end = match.end()
			when {
				// @user@host のように直後に@が続くのはNG
				remain.length > end && remain[end] == '@' -> null
				else -> Token(length = match.end(), color = 0xe9003f)
			}
		},
		
		// number
		{
			val prev = if(pos <= 0) null else source[pos - 1]
			if(prev?.isLetterOrDigit() == true) return@arrayOf null
			val match = reNumber.matcher(remain)
			when {
				! match.find() -> null
				else -> Token(length = match.end(), color = 0xae81ff)
			}
		},
		
		// method, property, keyword
		{
			// 直前の文字が識別子に使えるなら識別子の開始とはみなさない
			val prev = if(pos <= 0) null else source[pos - 1]
			if(prev?.isLetterOrDigit() == true || prev == '_') return@arrayOf null
			
			val match = reKeyword.matcher(remain)
			if(! match.find()) return@arrayOf null
			val kw = match.group(1)
			val bracket = match.group(2)
			
			when {
				// メソッド呼び出しは対象が変数かプロパティかに関わらずメソッドの色になる
				bracket?.isNotEmpty() == true ->
					Token(length = kw.length, color = 0x8964c1, italic = true)
				
				// 変数や定数ではなくプロパティならプロパティの色になる
				prev == '.' -> Token(length = kw.length, color = 0xa71d5d)
				
				// 予約語ではない
				// 強調表示しないが、識別子単位で読み飛ばす
				! keywords.contains(kw) -> Token(length = kw.length)
				
				else -> when(kw) {
					
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
			when {
				symbolMap.get(remain[0].toInt(), false) ->
					Token(length = 1, color = 0x42b983)
				else -> null
			}
		}
	)
	
	fun parse(source : String) = Env(source = source).parse()
	
}

object MisskeyMarkdownDecoder {
	
	internal val log = LogCategory("MisskeyMarkdownDecoder")
	
	internal const val DEBUG = false
	
	// デコード結果にはメンションの配列を含む。TootStatusのパーサがこれを回収する。
	class SpannableStringBuilderEx(
		var mentions : ArrayList<TootMention>? = null
	) : SpannableStringBuilder()
	
	// ブロック要素は始端と終端の空行を除去したい
	private val reStartEmptyLines = """\A(?:[ 　]*?[\x0d\x0a]+)+""".toRegex()
	private val reEndEmptyLines = """[\s\x0d\x0a]+\z""".toRegex()
	private fun trimBlock(s : String) =
		s.replace(reStartEmptyLines, "")
			.replace(reEndEmptyLines, "")
	
	private fun shortenUrl(display_url : String) : String {
		return try {
			val uri = Uri.parse(display_url)
			
			val sbTmp = StringBuilder()
			if(! display_url.startsWith("http")) {
				sbTmp.append(uri.scheme)
				sbTmp.append("://")
			}
			sbTmp.append(uri.authority)
			val a = uri.encodedPath ?: ""
			val q = uri.encodedQuery
			val f = uri.encodedFragment
			val remain = a + (if(q == null) "" else "?$q") + if(f == null) "" else "#$f"
			if(remain.length > 10) {
				sbTmp.append(remain.safeSubstring(10))
				sbTmp.append("…")
			} else {
				sbTmp.append(remain)
			}
			sbTmp.toString()
		} catch(ex : Throwable) {
			log.trace(ex)
			display_url
		}
	}
	
	// マークダウン要素のデコード時に使う作業変数をまとめたクラス
	internal class SpanOutputEnv(
		val options : DecodeOptions,
		val sb : SpannableStringBuilderEx
	) {
		
		val context : Context = options.context ?: error("missing context")
		val font_bold = ActMain.timeline_font_bold
		val linkHelper : LinkHelper? = options.linkHelper
		var spanList = SpanList()
		
		var start = 0
		
		fun prepareMentions() : ArrayList<TootMention> {
			var mentions = sb.mentions
			if(mentions != null) return mentions
			mentions = ArrayList()
			sb.mentions = mentions
			return mentions
		}
		
		fun fireRender(node : Node) : SpanList {
			val spanList = SpanList()
			this.spanList = spanList
			this.start = sb.length
			val render = node.type.render
			this.render(node)
			return spanList
		}
		
		internal fun fireRenderChildNodes(parent : Node) : SpanList {
			val parent_result = this.spanList
			parent.childNodes.forEach {
				val child_result = fireRender(it)
				parent_result.list.addAll(child_result.list)
			}
			this.spanList = parent_result
			return parent_result
		}
		
		// 直前の文字が改行文字でなければ改行する
		fun closePreviousBlock() {
			if(start > 0 && sb[start - 1] != '\n') {
				sb.append('\n')
				start = sb.length
			}
		}
		
		fun closeBlock() {
			if(sb.length > 0 && sb[sb.length - 1] != '\n') {
				sb.append('\n')
			}
		}
		
		private fun applyHighlight(start : Int, end : Int) {
			val list = options.highlightTrie?.matchList(sb, start, end)
			if(list != null) {
				for(range in list) {
					val word = HighlightWord.load(range.word)
					if(word != null) {
						options.hasHighlight = true
						spanList.addLast(
							range.start,
							range.end,
							HighlightSpan(word.color_fg, word.color_bg)
						)
						if(word.sound_type != HighlightWord.SOUND_TYPE_NONE) {
							options.highlight_sound = word
						}
					}
				}
			}
		}
		
		// テキストを追加する
		fun appendText(text : String, decodeEmoji : Boolean = false) {
			val start = sb.length
			if(decodeEmoji) {
				sb.append(options.decodeEmoji(text))
			} else {
				sb.append(text)
			}
			applyHighlight(start, sb.length)
		}
		
		// URL中のテキストを追加する
		private fun appendLinkText(display_url : String, href : String) {
			when {
				// 添付メディアのURLなら絵文字に変えてしまう
				options.isMediaAttachment(href) -> {
					// リンクの一部に絵文字がある場合、絵文字スパンをセットしてからリンクをセットする
					val start = sb.length
					sb.append(href)
					spanList.addFirst(
						start,
						sb.length,
						EmojiImageSpan(context, R.drawable.emj_1f5bc_fe0f)
					)
				}
				
				else -> appendText(shortenUrl(display_url))
			}
		}
		
		// リンクを追加する
		fun appendLink(text : String, url : String, allowShort : Boolean = false) {
			when {
				allowShort -> appendLinkText(text, url)
				else -> appendText(text)
			}
			
			val linkHelper = options.linkHelper
			if(linkHelper != null) {
				// リンクの一部にハイライトがある場合、リンクをセットしてからハイライトをセットしないとクリック判定がおかしくなる。
				spanList.addFirst(
					start, sb.length, MyClickableSpan(
						text
						, url
						, linkHelper.findAcctColor(url)
						, options.linkTag
					)
				)
			}
		}
	}
	
	////////////////////////////////////////////////////////////////////////////
	
	private fun mixColor(col1 : Int, col2 : Int) : Int = Color.rgb(
			(Color.red(col1) + Color.red(col2)) ushr 1,
			(Color.green(col1) + Color.green(col2)) ushr 1,
			(Color.blue(col1) + Color.blue(col2)) ushr 1
		)

	val quoteNestColors = intArrayOf(
		mixColor(Color.GRAY,0x0000ff ),
		mixColor(Color.GRAY,0x0080ff ),
		mixColor(Color.GRAY,0x00ff80 ),
		mixColor(Color.GRAY, 0x00ff00 ),
		mixColor(Color.GRAY,0x80ff00 ),
		mixColor(Color.GRAY,0xff8000 ),
		mixColor(Color.GRAY,0xff0000 ),
		mixColor(Color.GRAY,0xff0080 ),
		mixColor(Color.GRAY,0x8000ff )
	)
	
	fun <T> hashSetOf(vararg values : T) = HashSet<T>().apply { addAll(values) }
	
	enum class Node2Type(
		val allowInside : Set<Node2Type> = emptySet(),
		val allowInsideAll : Boolean = false,
		val render : SpanOutputEnv.(Node) -> Unit
	) {
		/////////////////////////////////////////////
		// 入れ子なし
		
		TEXT(
			render = { appendText(it.args[0],decodeEmoji=true) }
		),
		
		EMOJI(
			render = {
				val code = it.args[0]
				if(code.isNotEmpty()) {
					appendText(":$code:",decodeEmoji=true)
				}
			}
		),
		
		MENTION(
			render = {
				val username = it.args[0]
				val host = it.args[1]
				val linkHelper = linkHelper
				if(linkHelper == null) {
					appendText(
						when {
							host.isEmpty() -> "@$username"
							else -> "@$username@$host"
						}
						
					)
				} else {
					
					val shortAcct = when {
						host.isEmpty()
							|| host.equals(linkHelper.host, ignoreCase = true) ->
							username
						else ->
							"$username@$host"
					}
					
					val userHost = when {
						host.isEmpty() -> linkHelper.host
						else -> host
					}
					val userUrl = "https://$userHost/@$username"
					
					val mentions = prepareMentions()
					
					if(mentions.find { m -> m.acct == shortAcct } == null) {
						mentions.add(
							TootMention(
								EntityIdLong(- 1L)
								, userUrl
								, shortAcct
								, username
							)
						)
					}
					
					appendLink(
						when {
							Pref.bpMentionFullAcct(App1.pref) -> "@$username@$userHost"
							else -> "@$shortAcct"
						}
						, userUrl
					)
				}
			}
		),
		
		HASHTAG(
			render = {
				val linkHelper = linkHelper
				val tag = it.args[0]
				if(tag.isNotEmpty() && linkHelper != null) {
					appendLink(
						"#$tag",
						"https://${linkHelper.host}/tags/" + tag.encodePercent()
					)
				}
			}
		),
		
		CODE_INLINE(
			render = {
				val text = it.args[0]
				val sp = MisskeySyntaxHighlighter.parse(text)
				appendText(text)
				spanList.addWithOffset(sp.list, start)
				spanList.addLast(start, sb.length, BackgroundColorSpan(0x40808080))
				spanList.addLast(start, sb.length, CalligraphyTypefaceSpan(Typeface.MONOSPACE))
			}
		),
		
		URL(
			render = {
				val url = it.args[0]
				if(url.isNotEmpty()) {
					appendLink(url, url, allowShort = true)
				}
			}
		),
		
		CODE_BLOCK(
			
			render = {
				closePreviousBlock()
				
				val text = trimBlock(it.args[0])
				val sp = MisskeySyntaxHighlighter.parse(text)
				appendText(text)
				spanList.addWithOffset(sp.list, start)
				spanList.addLast(start, sb.length, BackgroundColorSpan(0x40808080))
				spanList.addLast(start, sb.length, android.text.style.RelativeSizeSpan(0.7f))
				spanList.addLast(start, sb.length, CalligraphyTypefaceSpan(Typeface.MONOSPACE))
				closeBlock()
			}
		),
		
		QUOTE_INLINE(
			render = {
				val text = trimBlock(it.args[0])
				appendText(text)
				spanList.addLast(
					start,
					sb.length,
					android.text.style.BackgroundColorSpan(0x20808080)
				)
				spanList.addLast(
					start,
					sb.length,
					CalligraphyTypefaceSpan(android.graphics.Typeface.defaultFromStyle(android.graphics.Typeface.ITALIC))
				)
			}
		),
		
		SEARCH(
			render = {
				closePreviousBlock()
				
				val text = it.args[0]
				val kw_start = sb.length // キーワードの開始位置
				appendText(text)
				appendText(" ")
				start = sb.length // 検索リンクの開始位置
				
				appendLink(
					context.getString(jp.juggler.subwaytooter.R.string.search),
					"https://www.google.co.jp/search?q=${text.encodePercent()}"
				)
				spanList.addLast(kw_start, sb.length, android.text.style.RelativeSizeSpan(1.2f))
				
				closeBlock()
			}
		),
		
		/////////////////////////////////////////////
		// 入れ子あり
		
		// インライン要素、装飾のみ
		
		BIG(
			allowInside = hashSetOf(MENTION, HASHTAG, EMOJI),
			render = {
				val start = this.start
				fireRenderChildNodes(it)
				spanList.addLast(start, sb.length, MisskeyBigSpan(font_bold))
			}
		),
		
		BOLD(
			allowInside = hashSetOf(MENTION, HASHTAG, EMOJI),
			render = {
				val start = this.start
				fireRenderChildNodes(it)
				spanList.addLast(start, sb.length, CalligraphyTypefaceSpan(font_bold))
			}
		),
		
		MOTION(
			allowInside = hashSetOf(BOLD, MENTION, HASHTAG, EMOJI),
			render = {
				val start = this.start
				fireRenderChildNodes(it)
				spanList.addFirst(
					start,
					sb.length,
					jp.juggler.subwaytooter.span.MisskeyMotionSpan(jp.juggler.subwaytooter.ActMain.timeline_font)
				)
			}
		),
		
		// リンクなどのデータを扱う要素
		
		LINK(
			allowInside = hashSetOf(
				BIG,
				BOLD,
				MOTION,
				EMOJI
			),
			render = {
				val url = it.args[1]
				// val silent = data?.get(2)
				// silentはプレビュー表示を抑制するが、Subwayにはもともとないので関係なかった
				
				if(url.isNotEmpty()) {
					val start = this.start
					fireRenderChildNodes(it)
					val linkHelper = options.linkHelper
					if(linkHelper != null) {
						spanList.addFirst(
							start, sb.length,
							MyClickableSpan(
								sb.substring(start, sb.length)
								, url
								, linkHelper.findAcctColor(url)
								, options.linkTag
							)
						)
					}
				}
			}
		),
		
		TITLE(
			allowInside = hashSetOf(
				BIG,
				BOLD,
				MOTION,
				URL,
				LINK,
				MENTION,
				HASHTAG,
				EMOJI,
				CODE_INLINE
			),
			render = {
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
					android.text.style.BackgroundColorSpan(0x20808080)
				)
				spanList.addLast(start, sb.length, android.text.style.RelativeSizeSpan(1.5f))
				
				closeBlock()
			}
		),
		
		QUOTE_BLOCK(
			allowInsideAll = true,
			render = {
				closePreviousBlock()
				
				val start = this.start
				
				// 末尾にある空白のテキストノードを除去する
				while(it.childNodes.isNotEmpty()) {
					val last = it.childNodes.last()
					if(last.type == Node2Type.TEXT && last.args[0].isBlank()) {
						it.childNodes.removeLast()
					} else {
						break
					}
				}
				
				fireRenderChildNodes(it)
				
				val bg_color = quoteNestColors[ it.quoteNest % quoteNestColors.size ]
				// TextView の文字装飾では「ブロック要素の入れ子」を表現できない
				// 内容の各行の始端に何か追加するというのがまずキツい
				// しかし各行の頭に引用マークをつけないと引用のネストで意味が通じなくなってしまう
				val tmp = sb.toString()
				//log.d("QUOTE_BLOCK tmp=${tmp} start=$start end=${tmp.length}")
				for(i in tmp.length - 1 downTo start) {
					val prevChar = when(i) {
						start -> '\n'
						else -> tmp[i - 1]
					}
					//log.d("QUOTE_BLOCK prevChar=${ String.format("%x",prevChar.toInt())}")
					if(prevChar == '\n') {
						//log.d("QUOTE_BLOCK insert! i=$i")
						sb.insert(i, "> ")
						spanList.insert(i, 2)
						spanList.addLast(
							i, i + 1,
							android.text.style.BackgroundColorSpan(bg_color)
						)
					}
				}
				
				spanList.addLast(
					start,
					sb.length,
					CalligraphyTypefaceSpan(android.graphics.Typeface.defaultFromStyle(android.graphics.Typeface.ITALIC))
				)
				
				closeBlock()
			}
		),
		
		ROOT(
			allowInsideAll = true,
			render = { fireRenderChildNodes(it) }
		),
		
	}
	
	val nodeTypeAllSet = HashSet<Node2Type>().apply {
		for(v in Node2Type.values()) {
			this.add(v)
		}
	}
	
	class Node(
		val type : Node2Type, // ノード種別
		val args : Array<String> = emptyArray(), // 引数
		parentNode : Node?
	) {
		
		val childNodes = LinkedList<Node>()
		
		internal val quoteNest :Int = (parentNode?.quoteNest ?: 0) + when(type){
			Node2Type.QUOTE_BLOCK,Node2Type.QUOTE_INLINE -> 1
			else->0
		}

	}
	
	class NodeDetected(
		val node : Node,
		val start : Int, // テキスト中の開始位置
		val length : Int, // テキスト中の長さ
		val textInside : String, // 内部範囲。親から継承する場合もあるし独自に作る場合もある
		val startInside : Int, // 内部範囲の開始位置
		private val lengthInside : Int // 内部範囲の終了位置
	) {
		
		val end : Int
			get() = start + length
		
		val endInside : Int
			get() = startInside + lengthInside
		
	}
	
	class NodeParseEnv(
		private val parentNode : Node,
		val text : String,
		start : Int,
		val end : Int
	) {
		
		private val childNodes = parentNode.childNodes
		private val allowInside = if(parentNode.type.allowInsideAll) {
			nodeTypeAllSet
		} else {
			parentNode.type.allowInside
		}
		
		
		var pos : Int = 0
		var remain : String = ""
		var previous : String = ""
		
		private var lastEnd = start // 直前のノードの終了位置
		
		// 直前のノードの終了位置から次のノードの開始位置の手前までをresultに追加する
		private fun closeText(endText : Int) {
			val length = endText - lastEnd
			if(length <= 0) return
			val textInside = text.substring(lastEnd, endText)
			childNodes.add(Node(Node2Type.TEXT, arrayOf(textInside),null))
		}
		
		fun parseInside() {
			if(allowInside.isEmpty()) return
			
			var i = lastEnd //スキャン中の位置
			while(i < end) {
				// 注目位置の文字に関連するパーサー
				val lastParsers = nodeParserMap[text[i].toInt()]
				if(lastParsers == null) {
					++ i
					continue
				}

				// パーサー用のパラメータを用意する
				// 部分文字列のコストは高くないと信じたい
				pos = i
				remain = text.substring(i, end)
				previous = text.substring(lastEnd, i)

				val detected = lastParsers.firstNonNull {
					val d = this.it()
					if(d == null) {
						null
					} else {
						val n = d.node
						if(! allowInside.contains(d.node.type)) {
							log.w(
								"not allowed : ${parentNode.type} => ${n.type} ${text.substring(
									d.start,
									d.end
								)}"
							)
							null
						} else {
							d
						}
					}
				}

				if(detected == null) {
					++ i
					continue
				}

				closeText(detected.start)
				childNodes.add(detected.node)
				i = detected.end
				lastEnd = i
				
				NodeParseEnv(
					detected.node,
					detected.textInside,
					detected.startInside,
					detected.endInside
				).parseInside()
			}
			closeText(end)
		}
		
		fun makeDetected(
			type: Node2Type,
			args: Array<String>,
			start : Int,
			length : Int,
			textInside : String,
			startInside : Int,
			lengthInside : Int
		) : NodeDetected {
			
			val node = Node(type,args,parentNode)
			
			if(DEBUG) log.d(
				"NodeDetected: ${node.type} inside=${
				textInside.substring(startInside, startInside + lengthInside)
				}"
			)
			
			return NodeDetected(
				node,
				pos + start,
				length,
				textInside,
				startInside,
				lengthInside
			)
		}
	}
	
	// ノードのパースを行う関数をキャプチャパラメータつきで生成する
	private fun simpleParser(
		pattern : Pattern
		, type : Node2Type
	) : NodeParseEnv.() -> NodeDetected? = {
		val matcher = pattern.matcher(remain)
		when {
			! matcher.find() -> null
			
			else -> {
				
				val textInside = matcher.group(1)
				makeDetected(
					type,
					arrayOf(textInside),
					0, matcher.end(),
					this.text, pos + matcher.start(1), textInside.length
				)
				
			}
		}
	}
	
	// (マークダウン要素の特徴的な文字)と(パーサ関数の配列)のマップ
	private val nodeParserMap = SparseArray<Array<out NodeParseEnv.() -> NodeDetected?>>().apply {
		
		fun addParser(firstChars : String, vararg nodeParsers : NodeParseEnv.() -> NodeDetected?) {
			for(s in firstChars) {
				put(s.toInt(), nodeParsers)
			}
		}
		
		// Quote "..."
		addParser(
			"\""
			, simpleParser(
				Pattern.compile("""^"([^\x0d\x0a]+?)\n"[\x0d\x0a]*""")
				, Node2Type.QUOTE_INLINE
			)
		)
		
		// Quote (行頭)>...(改行)
		val reQuoteBlock = Pattern.compile(
			"^>(?:[ 　]?)([^\\x0d\\x0a]*)(\\x0a|\\x0d\\x0a?)?",
			Pattern.MULTILINE
		)
		addParser(">", {
			if(pos > 0) {
				val c = text[pos - 1]
				if(c != '\r' && c != '\n') {
					//直前が改行文字ではない
					if(DEBUG) log.d("QUOTE: previous char is not line end. ${c} pos=$pos text=$text")
					return@addParser null
				}
			}
			
			var p = 0
			val content = StringBuilder()
			val matcher = reQuoteBlock.matcher(remain)
			while(true) {
				if(! matcher.find(p)) break
				p = matcher.end()
				if(content.isNotEmpty()) content.append('\n')
				content.append(matcher.group(1))
			}
			if(content.isNotEmpty()) content.append('\n')
			
			if(p == 0) {
				// > のあとに全く何もない
				if(DEBUG) log.d("QUOTE: not a quote")
				return@addParser null
			}
			val textInside = content.toString()
			
			makeDetected(
				Node2Type.QUOTE_BLOCK,
				emptyArray(),
				0, p,
				textInside, 0, textInside.length
			)
		})
		
		// 絵文字 :emoji:
		addParser(
			":"
			, simpleParser(
				Pattern.compile("""^:([a-zA-Z0-9+-_]+):""")
				, Node2Type.EMOJI
			)
		)
		
		addParser(
			"("
			, simpleParser(
				Pattern.compile("""^\Q(((\E(.+?)\Q)))\E""")
				, Node2Type.MOTION
			)
		)
		
		addParser(
			"<"
			, simpleParser(
				Pattern.compile("""^<motion>(.+?)</motion>""")
				, Node2Type.MOTION
			)
		)
		
		// ***big*** **bold**
		addParser(
			"*"
			// 処理順序に意味があるので入れ替えないこと
			// 記号列が長い順にパースを試す
			, simpleParser(
				Pattern.compile("""^\Q***\E(.+?)\Q***\E""")
				, Node2Type.BIG
			)
			, simpleParser(
				Pattern.compile("""^\Q**\E(.+?)\Q**\E""")
				, Node2Type.BOLD
			)
		)
		
		// http(s)://....
		addParser(
			"h"
			, simpleParser(
				Pattern.compile("""^(https?://[\w/:%#@${'$'}&?!()\[\]~.=+\-]+)""")
				, Node2Type.URL
			)
		)
		
		// 検索
		
		val reSearchButton = Pattern.compile(
			"""^(検索|\[検索]|Search|\[Search])(\n|${'$'})"""
			, Pattern.CASE_INSENSITIVE
		)
		
		fun parseSearchPrev(prev : String) : String? {
			val delm = prev.lastIndexOf('\n')
			val end = prev.length
			return when {
				end <= 1 -> null // キーワードを含まないくらい短い
				delm + 1 >= end - 1 -> null // 改行より後の部分が短すぎる
				! " 　".contains(prev.last()) -> null // 末尾が空白ではない
				else -> prev.substring(delm + 1, end - 1) // キーワード部分を返す
			}
		}
		
		val searchParser : NodeParseEnv.() -> NodeDetected? = {
			val matcher = reSearchButton.matcher(remain)
			when {
				! matcher.find() -> null
				
				else -> {
					val buttonLength = matcher.end()
					val keyword = parseSearchPrev(previous)
					when {
						keyword?.isEmpty() != false -> null
						
						else -> makeDetected(
							Node2Type.SEARCH,
							arrayOf(keyword),
							- (keyword.length + 1),
							buttonLength + (keyword.length + 1),
							this.text, pos - (keyword.length + 1), keyword.length
						)
					}
				}
			}
		}
		
		// [title] 【title】
		// 直後に改行が必要だったが文末でも良いことになった https://github.com/syuilo/misskey/commit/79ffbf95db9d0cc019d06ab93b1bfa6ba0d4f9ae
		val titleParser = simpleParser(
			Pattern.compile("""^[【\[](.+?)[】\]](\n|\z)""")
			, Node2Type.TITLE
		)
		
		// Link
		val reLink = Pattern.compile(
			"""^\??\[([^\[\]]+?)]\((https?://[\w/:%#@${'$'}&?!()\[\]~.=+\-]+?)\)"""
		)
		
		val linkParser : NodeParseEnv.() -> NodeDetected? = {
			val matcher = reLink.matcher(remain)
			when {
				! matcher.find() -> null
				
				else -> {
					val title = matcher.group(1)
					makeDetected(
						Node2Type.LINK,
						arrayOf(
							title
							, matcher.group(2) // url
							, remain[0].toString()   // silent なら "?" になる
						),
						0, matcher.end(),
						this.text, pos + matcher.start(1), title.length
					)
				}
			}
		}
		
		// [ はいろんな要素で使われる
		// searchの判定をtitleより前に行うこと。 「abc [検索] 」でtitleが優先されるとマズい
		addParser("[", searchParser, titleParser, linkParser)
		// その他の文字でも判定する
		addParser("【", titleParser)
		addParser("検Ss", searchParser)
		addParser("?", linkParser)
		
		// メンション @username @username@host
		val reMention = Pattern.compile(
			"""^@([a-z0-9_]+)(?:@([a-z0-9.\-]+[a-z0-9]))?"""
			, Pattern.CASE_INSENSITIVE
		)
		
		addParser("@", {
			val matcher = reMention.matcher(remain)
			when {
				! matcher.find() -> null
				else -> makeDetected(
					Node2Type.MENTION,
					arrayOf(
						matcher.group(1),
						matcher.group(2) ?: "" // username, host
					),
					0, matcher.end(),
					"", 0, 0
				)
			}
		})
		
		// Hashtag
		val reHashtag = Pattern.compile("""^#([^\s]+)""")
		addParser("#"
			, {
				val matcher = reHashtag.matcher(remain)
				when {
					! matcher.find() -> null
					else -> when {
						// 先頭以外では直前に空白が必要らしい
						pos > 0 &&
							! CharacterGroup.isWhitespace(text[pos - 1].toInt()) ->
							null
						
						else -> makeDetected(
							Node2Type.HASHTAG,
							arrayOf(matcher.group(1)), // 先頭の#を含まない
							0, matcher.end(),
							"", 0, 0
						)
					}
				}
			}
		)
		
		// code (ブロック、インライン)
		addParser(
			"`"
			, simpleParser(
				Pattern.compile("""^```(.+?)```[\x0d\x0a]*""", Pattern.DOTALL)
				, Node2Type.CODE_BLOCK
			)
			, simpleParser(
				// インラインコードは内部にとある文字を含むと認識されない。理由は顔文字と衝突するからだとか
				Pattern.compile("""^`([^`´\x0d\x0a]+)`""")
				, Node2Type.CODE_INLINE
			)
		)
	}
	
	// このファイルのエントリーポイント
	fun decodeMarkdown(options : DecodeOptions, src : String?) =
		SpannableStringBuilderEx().apply {
			val save = options.enlargeCustomEmoji
			options.enlargeCustomEmoji = 2.5f
			try {
				val env = SpanOutputEnv(options, this)
				
				if(src != null) {
					val root = Node(Node2Type.ROOT, emptyArray(),null)
					NodeParseEnv(root, src, 0, src.length).parseInside()
					for(sp in env.fireRender(root).list) {
						env.sb.setSpan(sp.span, sp.start, sp.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
					}
				}
				
				// 末尾の空白を取り除く
				val end = length
				var pos = end
				while(pos > 0 && HTMLDecoder.isWhitespaceOrLineFeed(get(pos - 1).toInt())) -- pos
				if(pos < end) delete(pos, end)
				
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "decodeMarkdown failed")
			} finally {
				options.enlargeCustomEmoji = save
			}
		}
}
