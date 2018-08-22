package jp.juggler.subwaytooter.util

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

object MisskeySyntaxHighlighter {
	
	private val symbolMap = SparseBooleanArray().apply {
		for(c in "=+-*/%~^&|><!?") {
			this.put(c.toInt(), true)
		}
	}
	
	// 識別子に対して既存の名前と一致するか調べるようになったので、もはやソートの必要はない
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
	}
	
	private class Token(
		var length : Int,
		var color : Int = 0,
		val italic : Boolean = false,
		val comment : Boolean = false
	)
	
	private class Env(
		var source : String,
		var pos : Int,
		var remain : String
	)
	
	private val reLineComment = Pattern.compile("""\A//.*""")
	private val reBlockComment = Pattern.compile("""\A/\*.*?\*/""", Pattern.DOTALL)
	private val reNumber = Pattern.compile("""\A[+-]?[\d.]+""")
	private val reLabel = Pattern.compile("""\A@([A-Z_-][A-Z0-9_-]*)""", Pattern.CASE_INSENSITIVE)
	private val reKeyword =
		Pattern.compile("""\A([A-Z_-][A-Z0-9_-]*)([ \t]*\()?""", Pattern.CASE_INSENSITIVE)
	
	private val elements = arrayOf(
		
		// comment
		{ env : Env ->
			val match = reLineComment.matcher(env.remain)
			when {
				match.find() -> Token(length = match.end(), comment = true)
				else -> null
			}
		},
		
		// block comment
		{ env : Env ->
			val match = reBlockComment.matcher(env.remain)
			when {
				match.find() -> Token(length = match.end(), comment = true)
				else -> null
			}
		},
		
		// string
		{ env : Env ->
			val beginChar = env.remain[0]
			if(beginChar != '"' && beginChar != '`') return@arrayOf null
			var len = 1
			while(len < env.remain.length) {
				val char = env.remain[len ++]
				if(char == beginChar) {
					break // end
				} else if(char == '\n' || len >= env.remain.length) {
					len = 0 // not string literal
					break
				} else if(char == '\\' && len < env.remain.length) {
					++ len // \" では閉じないようにする
				}
			}
			when(len) {
				0 -> null
				else -> Token(length = len, color = 0xe96900)
			}
		},
		
		// regexp
		{ env : Env ->
			if(env.remain[0] != '/') return@arrayOf null
			val regexp = StringBuilder()
			var thisIsNotARegexp = false
			var i = 1
			while(i < env.remain.length) {
				val char = env.remain[i ++]
				if(char == '/') {
					break
				} else if(char == '\n' || i >= env.remain.length) {
					thisIsNotARegexp = true
					break
				} else {
					regexp.append(char)
					if(char == '\\' && i < env.remain.length) {
						regexp.append(env.remain[i ++])
					}
				}
			}
			when {
				thisIsNotARegexp -> null
				regexp.isEmpty() -> null
				regexp[0] == ' ' && regexp[regexp.length - 1] == ' ' -> null
				else -> Token(length = regexp.length + 2, color = 0xe9003f)
			}
		},
		
		// label
		{ env : Env ->
			// 直前に識別子があればNG
			val prev = if(env.pos <= 0) null else env.source[env.pos - 1]
			if(prev?.isLetterOrDigit() == true) return@arrayOf null

			val match = reLabel.matcher(env.remain)
			if(! match.find()) return@arrayOf null

			val end = match.end()
			when{
				// @user@host のように直後に@が続くのはNG
				env.remain.length > end && env.remain[end] =='@' -> null
				else->Token(length = match.end(), color = 0xe9003f)
			}
		},
		
		// number
		{ env : Env ->
			val prev = if(env.pos <= 0) null else env.source[env.pos - 1]
			if(prev?.isLetterOrDigit() == true) return@arrayOf null
			val match = reNumber.matcher(env.remain)
			when {
				match.find() -> Token(length = match.end(), color = 0xae81ff)
				else -> null
			}
		},
		
		// method, property, keyword
		{ env : Env ->
			// 直前の文字が識別子に使えるなら識別子の開始とはみなさない
			val prev = if(env.pos <= 0) null else env.source[env.pos - 1]
			if(prev?.isLetterOrDigit() == true || prev == '_') return@arrayOf null
			
			val match = reKeyword.matcher(env.remain)
			if(! match.find()) return@arrayOf null
			val kw = match.group(1)
			val bracket = match.group(2)
			
			when {
				// メソッド呼び出しは対照が変数かプロパティかに関わらずメソッドの色になる
				bracket?.isNotEmpty() == true ->
					Token(length = kw.length, color = 0x8964c1, italic = true)
				
				// 変数や定数ではなくプロパティならプロパティの色になる
				prev == '.' ->Token(length = kw.length, color = 0xa71d5d)
				
				keywords.contains(kw) -> when(kw) {

					// 定数
					"true", "false", "null", "nil", "undefined" ,"NaN" ->
						Token(length = kw.length, color = 0xae81ff)

					// その他の予約語
					else ->Token(length = kw.length, color = 0x2973b7)
				}
				
				// 強調表示しないが、識別子単位で読み飛ばす
				else -> Token(length = kw.length)
			}
		},
		
		// symbol
		{ env : Env ->
			when {
				symbolMap.get(env.remain[0].toInt(), false) ->
					Token(length = 1, color = 0x42b983)
				else ->
					null
			}
		}
	)
	
	fun parse(source : String) : SpannableStringBuilder {
		val sb = SpannableStringBuilder()
		
		val env = Env(source = source, pos = 0, remain = source)
		
		fun push(pos : Int, token : Token) {
			val end = pos + token.length
			sb.append(source.substring(pos, end))
			env.pos = end
			
			if(token.comment) {
				sb.setSpan(
					ForegroundColorSpan(Color.BLACK or 0x808000)
					, pos, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
				)
			} else {
				var c = token.color
				if(c != 0) {
					if(c < 0x1000000) {
						c = c or Color.BLACK
					}
					sb.setSpan(
						ForegroundColorSpan(c)
						, pos, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
					)
				}
				if(token.italic) {
					sb.setSpan(
						CalligraphyTypefaceSpan(Typeface.defaultFromStyle(Typeface.ITALIC))
						, pos, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
					)
				}
			}
		}
		
		var textToken : Token? = null
		var textTokenStart = 0
		fun closeTextToken() {
			val token = textToken
			if(token != null) {
				token.length = env.pos - textTokenStart
				push(textTokenStart, token)
				textToken = null
			}
		}
		loop1@ while(env.remain.isNotEmpty()) {
			for(el in elements) {
				val token = el(env) ?: continue
				closeTextToken()
				push(env.pos, token)
				env.remain = source.substring(env.pos)
				continue@loop1
			}
			if(textToken == null) {
				textToken = Token(length = 0)
				textTokenStart = env.pos
			}
			env.remain = source.substring(++ env.pos)
		}
		closeTextToken()
		
		return sb
	}
}

object MisskeyMarkdownDecoder {
	
	private val log = LogCategory("MisskeyMarkdownDecoder")
	
	enum class NodeType {
		Text,
		Big,
		Bold,
		Title,
		Url,
		Link,
		Mention,
		Hashtag,
		CodeBlock,
		CodeInline,
		Quote,
		Emoji,
		Search,
		Motion
	}
	
	private class Node(
		var type : NodeType,
		var sourceStart : Int,
		var sourceLength : Int,
		var data : ArrayList<String?>?
	)
	
	private class ParserEnv(
		val text : String
		, var pos : Int
		, var remain : String
	) {
		
		internal fun genNode1(
			type : NodeType,
			sourceLength : Int,
			data : ArrayList<String?>?
		) = Node(
			type = type,
			sourceStart = pos,
			sourceLength = sourceLength,
			data = data
		)
	}
	
	private fun simpleParser(
		type : NodeType,
		pattern : Pattern
	) = { env : ParserEnv ->
		val matcher = pattern.matcher(env.remain)
		when {
			matcher.find() -> env.genNode1(
				type
				, matcher.end()
				, arrayListOf(matcher.group(1))
			)
			else -> null
		}
	}
	
	private val reLink =
		Pattern.compile("""^\??\[([^\[\]]+?)]\((https?://[\w/:%#@${'$'}&?!()\[\]~.=+\-]+?)\)""")
	
	private val reCodeInline = Pattern.compile("""^`(.+?)`""")
	
	private val reMention = Pattern.compile(
		"""^@([a-z0-9_]+)(?:@([a-z0-9.\-]+[a-z0-9]))?"""
		, Pattern.CASE_INSENSITIVE
	)
	
	private val reHashtag = Pattern.compile("""^#([^\s]+)""")
	
	private val reMotion1 = Pattern.compile("""^\Q(((\E(.+?)\Q)))\E""")
	private val reMotion2 = Pattern.compile("""^<motion>(.+?)</motion>""")
	
	private val nodeParserList = arrayOf(
		
		// 処理順序に意味があるので入れ替えないこと
		// 記号列が長い順
		simpleParser(
			NodeType.Big,
			Pattern.compile("""^\Q***\E(.+?)\Q***\E""")
		),
		
		simpleParser(
			NodeType.Bold,
			Pattern.compile("""^\Q**\E(.+?)\Q**\E""")
		),
		
		simpleParser(
			NodeType.Title,
			Pattern.compile("""^[【\[](.+?)[】\]]\n""")
		),
		
		simpleParser(
			NodeType.Url,
			Pattern.compile("""^(https?://[\w/:%#@${'$'}&?!()\[\]~.=+\-]+)""")
		),
		
		{ env : ParserEnv ->
			val matcher = reLink.matcher(env.remain)
			when {
				matcher.find() -> env.genNode1(
					NodeType.Link
					, matcher.end()
					, arrayListOf(
						matcher.group(1) // title
						, matcher.group(2) // url
						, env.remain[0].toString()   // silent なら "?" になる
					)
				)
				else -> null
			}
		},
		
		{ env : ParserEnv ->
			val matcher = reMention.matcher(env.remain)
			when {
				matcher.find() -> env.genNode1(
					NodeType.Mention
					, matcher.end()
					, arrayListOf(
						matcher.group(1) // username
						, matcher.group(2) // host
					)
				)
				else -> null
			}
		},
		
		{ env : ParserEnv ->
			val matcher = reHashtag.matcher(env.remain)
			when {
				matcher.find() -> when {
					
					// 先頭以外では直前に空白が必要らしい
					env.pos > 0 && ! CharacterGroup.isWhitespace(
						env.text[env.pos - 1].toInt()
					) -> null
					
					else -> env.genNode1(
						NodeType.Hashtag
						, matcher.end()
						, arrayListOf(
							matcher.group(1) // 先頭の#を含まないハッシュタグ
						)
					)
				}
				else -> null
			}
		},
		
		simpleParser(
			NodeType.CodeBlock,
			Pattern.compile("""^```(.+?)```""", Pattern.DOTALL)
		),
		
		{ env : ParserEnv ->
			val matcher = reCodeInline.matcher(env.remain)
			when {
				matcher.find() -> when {
					
					// インラインコードは内部にある文字を含むと認識されない。理由は謎
					matcher.group(1).contains('´') -> null
					
					else -> env.genNode1(
						NodeType.CodeInline
						, matcher.end()
						, arrayListOf(
							matcher.group(1)
						)
					)
				}
				else -> null
			}
		},
		
		simpleParser(
			NodeType.Quote,
			Pattern.compile("""^"([\s\S]+?)\n"""")
		),
		
		simpleParser(
			NodeType.Emoji,
			Pattern.compile("""^:([a-zA-Z0-9+-_]+):""")
		),
		
		simpleParser(
			NodeType.Search,
			Pattern.compile(
				"""^(.+?)[ 　](検索|\[検索]|Search|\[Search])(\n|${'$'})"""
				, Pattern.CASE_INSENSITIVE
			)
		),
		
		{ env : ParserEnv ->
			var found = false
			var matcher = reMotion1.matcher(env.remain)
			if(matcher.find()) {
				found = true
			} else {
				matcher = reMotion2.matcher(env.remain)
				if(matcher.find()) {
					found = true
				}
			}
			when(found) {
				true -> env.genNode1(
					NodeType.Motion
					, matcher.end()
					, arrayListOf(
						matcher.group(1) // 先頭の#を含まないハッシュタグ
					)
				)
				else -> null
			}
		}
	)
	
	private val reStartEmptyLines = """\A(?:[ 　]*?[\x0d\x0a]+)+""".toRegex()
	private val reEndEmptyLines = """[\s\x0d\x0a]+\z""".toRegex()
	private fun trimBlock(s : String?) : String? {
		s ?: return null
		return s
			.replace(reStartEmptyLines, "")
			.replace(reEndEmptyLines, "")
	}
	
	private fun parse(source : String?) : ArrayList<Node> {
		val result = ArrayList<Node>()
		
		if(source != null) {
			
			val env = ParserEnv(
				text = source,
				pos = 0,
				remain = source
			)
			
			//
			var textNode : Node? = null
			fun closeTextNode() {
				val node = textNode ?: return
				val length = env.pos - node.sourceStart
				if(length > 0) {
					node.sourceLength = length
					result.add(node)
				}
				textNode = null
			}
			//
			loop1@ while(env.remain.isNotEmpty()) {
				
				for(el in nodeParserList) {
					val node = el(env) ?: continue
					closeTextNode()
					result.add(node)
					env.pos += node.sourceLength
					env.remain = env.text.substring(env.pos)
					continue@loop1
				}
				
				// テキストノードの開始
				if(textNode == null) {
					textNode = Node(
						NodeType.Text,
						env.pos,
						0,
						null
					)
				}
				
				env.remain = env.text.substring(++ env.pos)
			}
			closeTextNode()
		}
		
		return result
	}
	
	class SpannableStringBuilderEx : SpannableStringBuilder() {
		
		var mentions : ArrayList<TootMention>? = null
	}
	
	fun decodeMarkdown(options : DecodeOptions, src : String?) : SpannableStringBuilderEx {
		val sb = SpannableStringBuilderEx()
		val context = options.context ?: return sb
		
		fun urlShorter(display_url : String, href : String) : CharSequence {
			
			if(options.isMediaAttachment(href)) {
				@Suppress("NAME_SHADOWING")
				val sb = SpannableStringBuilder()
				sb.append(href)
				val start = 0
				val end = sb.length
				sb.setSpan(
					EmojiImageSpan(context, R.drawable.emj_1f5bc),
					start,
					end,
					Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
				)
				return sb
			}
			
			try {
				val uri = Uri.parse(display_url)
				
				@Suppress("NAME_SHADOWING")
				val sb = StringBuilder()
				if(! display_url.startsWith("http")) {
					sb.append(uri.scheme)
					sb.append("://")
				}
				sb.append(uri.authority)
				val a = uri.encodedPath
				val q = uri.encodedQuery
				val f = uri.encodedFragment
				val remain = a + (if(q == null) "" else "?$q") + if(f == null) "" else "#$f"
				if(remain.length > 10) {
					sb.append(remain.safeSubstring(10))
					sb.append("…")
				} else {
					sb.append(remain)
				}
				return sb
			} catch(ex : Throwable) {
				log.trace(ex)
				return display_url
			}
		}
		
		try {
			if(src != null) {
				
				val font_bold = ActMain.timeline_font_bold
				
				for(node in parse(src)) {
					val nodeSource =
						src.substring(node.sourceStart, node.sourceStart + node.sourceLength)
					var start = sb.length
					val data = node.data
					
					fun setSpan(span : Any) {
						val end = sb.length
						sb.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
					}
					
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
					
					fun appendText(text : CharSequence?, preventHighlight : Boolean = false) {
						text ?: return
						
						sb.append(text)
						
						if(! preventHighlight) {
							setHighlight()
						}
					}
					
					fun appendTextCode(text : String?, preventHighlight : Boolean = false) {
						text ?: return
						
						sb.append(MisskeySyntaxHighlighter.parse(text))
						
						if(! preventHighlight) {
							setHighlight()
						}
					}
					
					fun appendLink(text : String, url : String, allowShort : Boolean = false) {
						when {
							text.isEmpty() -> return
							! allowShort -> appendText(text, preventHighlight = true)
							
							else -> {
								val short = urlShorter(text, url)
								appendText(short, preventHighlight = true)
							}
						}
						val linkHelper = options.linkHelper
						if(linkHelper != null) {
							setSpan(
								MyClickableSpan(
									text,
									url,
									linkHelper.findAcctColor(url),
									options.linkTag
								)
							)
						}
						setHighlight()
					}
					
					when(node.type) {
						
						NodeType.Url -> {
							val url = data?.get(0)
							if(url?.isNotEmpty() == true) {
								appendLink(url, url, allowShort = true)
							}
						}
						
						NodeType.Link -> {
							val title = data?.get(0) ?: "?"
							val url = data?.get(1)
							// val silent = data?.get(2)
							// silentはプレビュー表示を抑制するが、Subwayにはもともとないので関係なかった
							if(url?.isNotEmpty() == true) {
								appendLink(title, url)
							}
						}
						
						NodeType.Mention -> {
							
							val username = data?.get(0) ?: ""
							val host = data?.get(1) ?: ""
							
							val linkHelper = options.linkHelper
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
						
						NodeType.Hashtag -> {
							val linkHelper = options.linkHelper
							val tag = data?.get(0)
							if(tag?.isNotEmpty() == true && linkHelper != null) {
								appendLink(
									"#$tag",
									"https://${linkHelper.host}/tags/" + tag.encodePercent()
								)
							}
						}
						
						NodeType.Emoji -> {
							val code = data?.get(0)
							if(code?.isNotEmpty() == true) {
								appendText(options.decodeEmoji(":$code:"))
							}
						}
						
						////////////////////////////////////////////
						// 装飾インライン要素
						
						NodeType.Text -> {
							appendText(nodeSource)
						}
						
						NodeType.Big -> {
							appendText(data?.get(0))
							setSpan(MisskeyBigSpan(font_bold))
						}
						
						NodeType.Motion -> {
							val code = data?.get(0)
							appendText(code)
							setSpan(MisskeyMotionSpan(ActMain.timeline_font))
						}
						
						NodeType.Bold -> {
							appendText(data?.get(0))
							setSpan(CalligraphyTypefaceSpan(font_bold))
						}
						
						NodeType.CodeInline -> {
							appendTextCode(data?.get(0))
							setSpan(BackgroundColorSpan(0x40808080))
							setSpan(CalligraphyTypefaceSpan(Typeface.MONOSPACE))
						}
						
						////////////////////////////////////////////
						// ブロック要素
						
						NodeType.Title -> {
							
							appendText(trimBlock(data?.get(0)))
							setSpan(AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER))
							setSpan(BackgroundColorSpan(0x20808080))
							setSpan(RelativeSizeSpan(1.5f))
							appendText("\n")
						}
						
						NodeType.CodeBlock -> {
							appendTextCode(trimBlock(data?.get(0)))
							setSpan(BackgroundColorSpan(0x40808080))
							setSpan(RelativeSizeSpan(0.7f))
							setSpan(CalligraphyTypefaceSpan(Typeface.MONOSPACE))
							appendText("\n")
						}
						
						NodeType.Quote -> {
							appendText(trimBlock(data?.get(0)))
							setSpan(BackgroundColorSpan(0x20808080))
							setSpan(CalligraphyTypefaceSpan(Typeface.defaultFromStyle(Typeface.ITALIC)))
							appendText("\n")
						}
						
						NodeType.Search -> {
							val text = data?.get(0)
							val kw_start = sb.length // キーワードの開始位置
							appendText(text)
							appendText(" ")
							start = sb.length // 検索リンクの開始位置
							appendLink(
								context.getString(R.string.search),
								"https://www.google.co.jp/search?q=" + (text
									?: "Subway Tooter").encodePercent()
							)
							sb.setSpan(
								RelativeSizeSpan(1.2f),
								kw_start,
								sb.length,
								Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
							)
							appendText("\n")
						}
					}
				}
				
				// 末尾の空白を取り除く
				var end = sb.length
				while(end > 0 && HTMLDecoder.isWhitespaceOrLineFeed(sb[end - 1].toInt())) -- end
				if(end < sb.length) sb.delete(end, sb.length)
			}
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
		return sb
	}
}

