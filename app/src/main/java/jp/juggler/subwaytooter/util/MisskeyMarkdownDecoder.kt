package jp.juggler.subwaytooter.util

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.SparseArray
import android.util.SparseBooleanArray
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.EntityIdLong
import jp.juggler.subwaytooter.api.entity.TootMention
import jp.juggler.subwaytooter.span.*
import jp.juggler.subwaytooter.table.HighlightWord
import uk.co.chrisjenx.calligraphy.CalligraphyTypefaceSpan
import java.util.regex.Pattern

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
		
		// 出力先
		val sb = SpannableStringBuilder(source)
		
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
				sb.setSpan(
					ForegroundColorSpan(Color.BLACK or 0x808000)
					, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
				)
			} else {
				var c = token.color
				if(c != 0) {
					if(c < 0x1000000) {
						c = c or Color.BLACK
					}
					sb.setSpan(
						ForegroundColorSpan(c)
						, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
					)
				}
				if(token.italic) {
					sb.setSpan(
						CalligraphyTypefaceSpan(Typeface.defaultFromStyle(Typeface.ITALIC))
						, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
					)
				}
			}
		}
		
		fun parse() : SpannableStringBuilder {
			
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
			
			return sb
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
	
	private val log = LogCategory("MisskeyMarkdownDecoder")
	
	// デコード結果にはメンションの配列を含む。TootStatusのパーサがこれを回収する。
	class SpannableStringBuilderEx : SpannableStringBuilder() {
		
		var mentions : ArrayList<TootMention>? = null
	}
	
	// マークダウン要素のデコード時に使う作業変数をまとめたクラス
	private class SpanOutputEnv(val options : DecodeOptions, val sb : SpannableStringBuilderEx) {
		
		val context : Context = options.context ?: error("missing context")
		val font_bold = ActMain.timeline_font_bold
		var start = 0
		var nodeSource : String = ""
		var data : Array<String> = emptyArray()
		val linkHelper : LinkHelper? = options.linkHelper
		
		// URLの短縮表記。出力は絵文字スパンを含むかもしれない
		fun urlShorter(display_url : String, href : String) : CharSequence = when {
			options.isMediaAttachment(href) -> {
				// 添付メディアのURLなら絵文字に変えてしまう
				val sbTmp = SpannableStringBuilder()
				sbTmp.append(href)
				val start = 0
				val end = sbTmp.length
				sbTmp.setSpan(
					EmojiImageSpan(context, R.drawable.emj_1f5bc_fe0f),
					start,
					end,
					Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
				)
				sbTmp
			}
			
			else -> try {
				val uri = Uri.parse(display_url)
				
				val sbTmp = SpannableStringBuilder()
				if(! display_url.startsWith("http")) {
					sbTmp.append(uri.scheme)
					sbTmp.append("://")
				}
				sbTmp.append(uri.authority)
				val a = uri.encodedPath
				val q = uri.encodedQuery
				val f = uri.encodedFragment
				val remain = a + (if(q == null) "" else "?$q") + if(f == null) "" else "#$f"
				if(remain.length > 10) {
					sbTmp.append(remain.safeSubstring(10))
					sbTmp.append("…")
				} else {
					sbTmp.append(remain)
				}
				sbTmp
			} catch(ex : Throwable) {
				log.trace(ex)
				display_url
			}
		}
		
		// 直前の文字が改行文字でなければ改行する
		fun closePreviousBlock() {
			if(start > 0 && sb[start - 1] != '\n') {
				sb.append('\n')
				start = sb.length
			}
		}
		
		// startから現在の終端までにスパンを設定する
		fun setSpan(span : Any) =
			sb.setSpan(span, start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
		
		// startから現在の終端までに強調表示を設定する
		fun setHighlight() {
			val list = options.highlightTrie?.matchList(sb, start, sb.length)
			if(list != null) {
				for(range in list) {
					val word = HighlightWord.load(range.word)
					if(word != null) {
						options.hasHighlight = true
						sb.setSpan(
							HighlightSpan(word.color_fg, word.color_bg),
							range.start,
							range.end,
							Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
						)
						if(word.sound_type != HighlightWord.SOUND_TYPE_NONE) {
							options.highlight_sound = word
						}
					}
				}
			}
		}
		
		// テキストを追加する
		fun appendText(text : CharSequence, preventHighlight : Boolean = false) {
			sb.append(text)
			if(! preventHighlight) setHighlight()
		}
		
		// リンクを追加する
		fun appendLink(text : String, url : String, allowShort : Boolean = false) {
			appendText(
				when {
					allowShort -> urlShorter(text, url)
					else -> text
				}
				, preventHighlight = true
			)
			val linkHelper = options.linkHelper
			if(linkHelper != null) {
				setSpan(
					MyClickableSpan(
						text
						, url
						, linkHelper.findAcctColor(url)
						, options.linkTag
					)
				)
			}
			setHighlight()
		}
	}
	
	// インライン要素、装飾のみ
	
	private val outputBig : SpanOutputEnv.() -> Unit = {
		appendText(data[0])
		setSpan(MisskeyBigSpan(font_bold))
	}
	
	private val outputBold : SpanOutputEnv.() -> Unit = {
		appendText(data[0])
		setSpan(CalligraphyTypefaceSpan(font_bold))
	}
	
	private val outputMotion : SpanOutputEnv.() -> Unit = {
		val code = data[0]
		appendText(code)
		setSpan(MisskeyMotionSpan(ActMain.timeline_font))
	}
	
	private val outputCodeInline : SpanOutputEnv.() -> Unit = {
		appendText(MisskeySyntaxHighlighter.parse(data[0]))
		setSpan(BackgroundColorSpan(0x40808080))
		setSpan(CalligraphyTypefaceSpan(Typeface.MONOSPACE))
	}
	
	// ブロック要素 装飾のみ
	
	private val outputCodeBlock : SpanOutputEnv.() -> Unit = {
		closePreviousBlock()
		appendText(MisskeySyntaxHighlighter.parse(trimBlock(data[0])))
		setSpan(BackgroundColorSpan(0x40808080))
		setSpan(RelativeSizeSpan(0.7f))
		setSpan(CalligraphyTypefaceSpan(Typeface.MONOSPACE))
		appendText("\n")
	}
	
	private val outputQuote : SpanOutputEnv.() -> Unit = {
		closePreviousBlock()
		appendText(trimBlock(data[0]))
		setSpan(BackgroundColorSpan(0x20808080))
		setSpan(CalligraphyTypefaceSpan(Typeface.defaultFromStyle(Typeface.ITALIC)))
		appendText("\n")
	}
	
	private val outputTitle : SpanOutputEnv.() -> Unit = {
		closePreviousBlock()
		appendText(trimBlock(data[0]))
		setSpan(AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER))
		setSpan(BackgroundColorSpan(0x20808080))
		setSpan(RelativeSizeSpan(1.5f))
		appendText("\n")
	}
	
	// リンクなどのデータを扱う要素
	
	private val outputEmoji : SpanOutputEnv.() -> Unit = {
		val code = data[0]
		if(code.isNotEmpty()) {
			appendText(options.decodeEmoji(":$code:"))
		}
	}
	
	private val outputUrl : SpanOutputEnv.() -> Unit = {
		val url = data[0]
		if(url.isNotEmpty()) {
			appendLink(url, url, allowShort = true)
		}
	}
	
	private val outputLink : SpanOutputEnv.() -> Unit = {
		val title = data[0]
		val url = data[1]
		// val silent = data?.get(2)
		// silentはプレビュー表示を抑制するが、Subwayにはもともとないので関係なかった
		if(url.isNotEmpty()) {
			appendLink(title, url)
		}
	}
	
	private val outputHashTag : SpanOutputEnv.() -> Unit = {
		val linkHelper = linkHelper
		val tag = data[0]
		if(tag.isNotEmpty() && linkHelper != null) {
			appendLink(
				"#$tag",
				"https://${linkHelper.host}/tags/" + tag.encodePercent()
			)
		}
	}
	
	private val outputSearch : SpanOutputEnv.() -> Unit = {
		val text = data[0]
		closePreviousBlock()
		val kw_start = sb.length // キーワードの開始位置
		appendText(text)
		appendText(" ")
		start = sb.length // 検索リンクの開始位置
		appendLink(
			context.getString(R.string.search),
			"https://www.google.co.jp/search?q=${text.encodePercent()}"
		)
		sb.setSpan(
			RelativeSizeSpan(1.2f),
			kw_start,
			sb.length,
			Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
		)
		appendText("\n")
	}
	
	private val outputMention : SpanOutputEnv.() -> Unit = {
		val username = data[0]
		val host = data[1]
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
			
			var mentions = sb.mentions
			if(mentions == null) {
				mentions = ArrayList()
				sb.mentions = mentions
			}
			
			if(mentions.find { it.acct == shortAcct } == null) {
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
	
	// マークダウン要素のパース時に使う作業変数をまとめたクラス
	private class ParseEnv(val text : String) {
		
		var remain : String = ""
		var previous : String = ""
		
		var lastEnd = 0 // 直前のノードの終了位置
		
		var pos : Int = 0
			set(value) {
				field = value
				remain = text.substring(pos)
				previous = text.substring(lastEnd, pos)
			}
		
		var callback : (MisskeyMarkdownDecoder.Node) -> Unit = {}
		
		// 直前のノードの終了位置から次のノードの開始位置の手前までをresultに追加する
		fun closeText(endText : Int) {
			val length = endText - lastEnd
			if(length > 0) callback(
				Node(lastEnd, length, emptyArray()) {
					appendText(nodeSource)
				}
			)
		}
		
		fun parse(callback : (Node) -> Unit) {
			this.callback = callback
			val end = text.length
			var i = 0 //スキャン中の位置
			while(i < end) {
				val lastParsers = nodeParserMap[text[i].toInt()]
				if(lastParsers == null) {
					++ i
					continue
				}
				pos = i
				val node = lastParsers.firstNonNull { this.it() }
				if(node == null) {
					++ i
					continue
				}
				closeText(node.start)
				callback(node)
				i = node.start + node.length
				lastEnd = i
			}
			closeText(i)
		}
	}
	
	// マークダウン要素の出現位置
	private class Node(
		
		// ソース文字列中の開始位置
		var start : Int
		
		// ソース文字列中の長さ
		, var length : Int
		
		// 出力時に使うパラメータ
		, var data : Array<String>
		
		// 出力処理を行う関数
		, var decoder : SpanOutputEnv.() -> Unit
	
	)
	
	// ノードのパースを行う関数をキャプチャパラメータつきで生成する
	private fun simpleParser(
		pattern : Pattern
		, decoder : SpanOutputEnv.() -> Unit
	) : ParseEnv.() -> Node? = {
		val matcher = pattern.matcher(remain)
		when {
			! matcher.find() -> null
			else -> Node(
				pos
				, matcher.end()
				, arrayOf(matcher.group(1))
				, decoder
			)
		}
	}
	
	// (マークダウン要素の特徴的な文字)と(パーサ関数の配列)のマップ
	private val nodeParserMap = SparseArray<Array<out ParseEnv.() -> Node?>>().apply {
		
		fun addParser(firstChars : String, vararg nodeParsers : ParseEnv.() -> Node?) {
			for(s in firstChars) {
				put(s.toInt(), nodeParsers)
			}
		}
		
		// Quote "...(改行)"
		addParser(
			"\""
			, simpleParser(
				Pattern.compile("""^"([\s\S]+?)\n"""")
				, outputQuote
			)
		)
		
		// 絵文字 :emoji:
		addParser(
			":"
			, simpleParser(
				Pattern.compile("""^:([a-zA-Z0-9+-_]+):""")
				, outputEmoji
			)
		)
		
		addParser(
			"("
			, simpleParser(
				Pattern.compile("""^\Q(((\E(.+?)\Q)))\E""")
				, outputMotion
			)
		)
		
		addParser(
			"<"
			, simpleParser(
				Pattern.compile("""^<motion>(.+?)</motion>""")
				, outputMotion
			)
		)
		
		// ***big*** **bold**
		addParser(
			"*"
			// 処理順序に意味があるので入れ替えないこと
			// 記号列が長い順にパースを試す
			, simpleParser(
				Pattern.compile("""^\Q***\E(.+?)\Q***\E""")
				, outputBig
			)
			, simpleParser(
				Pattern.compile("""^\Q**\E(.+?)\Q**\E""")
				, outputBold
			)
		)
		
		// http(s)://....
		addParser(
			"h"
			, simpleParser(
				Pattern.compile("""^(https?://[\w/:%#@${'$'}&?!()\[\]~.=+\-]+)""")
				, outputUrl
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
		
		val searchParser : ParseEnv.() -> Node? = {
			val matcher = reSearchButton.matcher(remain)
			when {
				! matcher.find() -> null
				
				else -> {
					val buttonLength = matcher.end()
					val keyword = parseSearchPrev(previous)
					when {
						keyword?.isEmpty() != false -> null
						else -> Node(
							pos - (keyword.length + 1)
							, buttonLength + (keyword.length + 1)
							, arrayOf(keyword)
							, outputSearch
						)
					}
				}
			}
		}
		
		// [title] 【title】 直後に改行が必要
		val titleParser = simpleParser(
			Pattern.compile("""^[【\[](.+?)[】\]]\n""")
			, outputTitle
		)
		
		// Link
		val reLink = Pattern.compile(
			"""^\??\[([^\[\]]+?)]\((https?://[\w/:%#@${'$'}&?!()\[\]~.=+\-]+?)\)"""
		)
		
		val linkParser : ParseEnv.() -> Node? = {
			val matcher = reLink.matcher(remain)
			when {
				! matcher.find() -> null
				else -> Node(
					pos
					, matcher.end()
					, arrayOf(
						matcher.group(1) // title
						, matcher.group(2) // url
						, remain[0].toString()   // silent なら "?" になる
					)
					, outputLink
				)
			}
		}
		
		// [ はいろんな要素で使われる
		addParser("[", titleParser, searchParser, linkParser)
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
				else -> Node(
					pos
					, matcher.end()
					, arrayOf(matcher.group(1), matcher.group(2) ?: "") // username, host
					, outputMention
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
						pos > 0
							&& ! CharacterGroup.isWhitespace(text[pos - 1].toInt()) ->
							null
						
						else -> Node(
							pos
							, matcher.end()
							, arrayOf(matcher.group(1)) // 先頭の#を含まない
							, outputHashTag
						)
					}
				}
			}
		)
		
		// code (ブロック、インライン)
		addParser(
			"`"
			, simpleParser(
				Pattern.compile("""^```(.+?)```""", Pattern.DOTALL)
				, outputCodeBlock
			)
			, simpleParser(
				// インラインコードは内部にとある文字を含むと認識されない。理由は顔文字と衝突するからだとか
				Pattern.compile("""^`([^`´\x0d\x0a]+)`""")
				, outputCodeInline
			)
		)
	}
	
	// ブロック要素は始端と終端の空行を除去したい
	private val reStartEmptyLines = """\A(?:[ 　]*?[\x0d\x0a]+)+""".toRegex()
	private val reEndEmptyLines = """[\s\x0d\x0a]+\z""".toRegex()
	private fun trimBlock(s : String) =
		s.replace(reStartEmptyLines, "")
			.replace(reEndEmptyLines, "")
	
	// このファイルのエントリーポイント
	fun decodeMarkdown(options : DecodeOptions, src : String?) =
		SpannableStringBuilderEx().apply {
			try {
				val env = SpanOutputEnv(options, this)
				
				if(src != null) ParseEnv(src).parse { node ->
					env.nodeSource = src.substring(node.start, node.start + node.length)
					env.start = length
					env.data = node.data
					val decoder = node.decoder
					env.decoder()
				}
				
				// 末尾の空白を取り除く
				val end = length
				var pos = end
				while(pos > 0 && HTMLDecoder.isWhitespaceOrLineFeed(get(pos - 1).toInt())) -- pos
				if(pos < end) delete(pos, end)
				
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "decodeMarkdown failed")
			}
		}
}
