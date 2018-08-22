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
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.span.*
import jp.juggler.subwaytooter.table.HighlightWord
import uk.co.chrisjenx.calligraphy.CalligraphyTypefaceSpan
import java.util.regex.Pattern

private fun String.safeSubstring(end:Int):String{
	val l = this.length
	if( end > l ) return this
	return this.substring(0,end)
}


object MisskeySyntaxHighlighter {
	
	private val symbols = setOf(
		"=",
		"+",
		"-",
		"*",
		"/",
		"%",
		"~",
		"^",
		"&",
		"|",
		">",
		"<",
		"!",
		"?"
	)
	
	// 文字数が多い順にソートします
	// そうしないと、「function」という文字列が与えられたときに「func」が先にマッチしてしまう可能性があるためです
	private val _keywords = arrayOf(
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
	
	private val keywords = ArrayList<String>().apply {
		
		// lower
		addAll(_keywords)
		
		// UPPER
		addAll(_keywords.map { k -> k.toUpperCase() })
		
		// Snake
		addAll(_keywords.map { k -> k[0].toUpperCase() + k.substring(1) })
		
		// 長い順にソート
		sortWith(Comparator { a, b -> b.length - a.length })
	}
	

	
	private class Token(
		var length : Int,
		var color:Int =0,
		val italic :Boolean = false
	)
	
	private class Env(
		var source:String,
		var pos:Int,
		var remain:String
	)
	
	private val reLineComment = Pattern.compile("^//(.+?)(\n|$)")
	private val reBlockComment = Pattern.compile("""^/\*([\s\S]*?)\*/""")
	private val reStringStart = Pattern.compile("""^(["`])""")
	private val reLabel = Pattern.compile("""^@([a-zA-Z_-]+?)\n""")
	private val reAlphabet = Pattern.compile("""[a-zA-Z]""")
	private val reNumber = Pattern.compile("""^[+-]?[\d.]+""")
	
	private val reMethod = Pattern.compile("""^([a-zA-Z_-]+?)\(""")
	private val reProperty = Pattern.compile("""^[a-zA-Z0-9_-]+""")
	private val reStartAlphabet = Pattern.compile("""^[a-zA-Z]""")
	

	private val elements = arrayOf(
		// comment
		{ env:Env ->
			if( env.remain.safeSubstring( 2) != "//") return@arrayOf null
			val match = reLineComment.matcher(env.remain)
			if(! match.find()) return@arrayOf null
			val comment = match.group()
			Token(
				color=0x7f00000,
				length = comment.length
			)
		},
		
		// block comment
		{ env:Env ->
			val match = reBlockComment.matcher(env.remain)
			if(! match.find()) return@arrayOf null
			val g0 = match.group()
			Token(length = g0.length,color=0x7f00000)
		},
		
		// string
		{ env:Env ->
			val match = reStringStart.matcher(env.remain)
			if(! match.find()) return@arrayOf null
			val begin = env.remain[0]
			val str = StringBuilder().append(begin)
			var thisIsNotAString = false
			var i = 1
			loop@ while(i < env.remain.length) {
				val char = env.remain[i ++]
				when {
					char == '\\' -> {
						str.append(char)
						if(i < env.remain.length) str.append(env.remain[i ++])
						continue@loop
					}
					char == begin -> {
						str.append(char)
						break@loop
					}
					char == '\n' || i >= env.remain.length -> {
						thisIsNotAString = true
						break@loop
					}
					else -> str.append(char)
				}
			}
			if(thisIsNotAString) {
				null
			} else {
				Token(length = str.length,color = 0xe96900)
			}
		},
		
		// regexp
		{ env:Env ->
			if(env.remain[0] != '/') return@arrayOf null
			val regexp = StringBuilder()
			var thisIsNotARegexp = false
			var i = 1
			while(i < env.remain.length) {
				val char = env.remain[i ++]
				if(char == '\\') {
					regexp.append(char)
					if(i < env.remain.length) regexp.append(env.remain[i ++])
					continue
				} else if(char == '/') {
					break
				} else if(char == '\n' || i >= env.remain.length) {
					thisIsNotARegexp = true
					break
				} else {
					regexp.append(char)
				}
			}
			if(thisIsNotARegexp) {
				null
			} else if(regexp.isEmpty()) {
				null
			} else if(regexp[0] == ' ' && regexp[regexp.length - 1] == ' ') {
				null
			} else {
				Token(length = regexp.length + 2,color=0xe9003f)
			}
		},
		
		// label
		{ env:Env ->
			if(env.remain[0] != '@') return@arrayOf null
			val match = reLabel.matcher(env.remain)
			if(! match.find()) return@arrayOf null
			val label = match.group(0)
			Token(length = label.length,color=0xe9003f)
		},
		
		// number
		{ env:Env ->
			val prev = if(env.pos <= 0) null else env.source[env.pos - 1].toString()
			if(prev != null && reAlphabet.matcher(prev).find()) return@arrayOf null
			val match = reNumber.matcher(env.remain)
			if(match.find()) {
				val g0 = match.group(0)
				Token(length = g0.length,color=0xae81ff)
			} else {
				null
			}
		},
		
		// nan
		{ env:Env ->
			val prev = if(env.pos <= 0) null else env.source[env.pos - 1].toString()
			if(prev != null && reAlphabet.matcher(prev).find()) return@arrayOf null
			
			if(env.remain.safeSubstring( 3) == "NaN") {
				Token(length = 3,color=0xae81ff)
			} else {
				null
			}
		},
		
		// method
		{ env:Env ->
			val match = reMethod.matcher(env.remain)
			if(match.find()) {
				val g1 = match.group(1)
				if(g1 != "-") {
					return@arrayOf Token(length = g1.length,color=0x8964c1,italic = true)
				}
			}
			null
		},
		
		// property
		{ env:Env ->
			val prev = if(env.pos <= 0) null else env.source[env.pos - 1]
			if(prev != '.') return@arrayOf null
			
			val match = reProperty.matcher(env.remain)
			if(match.find()) {
				val g0 = match.group()
				return@arrayOf Token(length = g0.length,color=0xa71d5d)
			}
			null
		},
		
		// keyword
		{ env:Env ->
			val prev = if(env.pos <= 0) "" else env.source[env.pos - 1].toString()
			if(reAlphabet.matcher(prev).find()) return@arrayOf null
			
			val match = keywords.find {
				env.remain.safeSubstring(it.length) == it
			}
				?: return@arrayOf null

			val kw = env.remain.safeSubstring( match.length)

			// 先頭は英字
			if( reStartAlphabet.matcher(kw).find() ){

				return@arrayOf when( kw ){
					"true","false","null","nil","undefined" ->
						Token( length = kw.length, color  = 0xae81ff)
					else->
						Token( length = kw.length, color  = 0x2973b7)
				}

			}
			null
		},
		
		// symbol
		{ env:Env ->
			val s = env.remain[0].toString()
			if(symbols.contains(s)) {
				return@arrayOf Token(length=1,color=0x42b983)
			}
			null
		}
	)
	
	fun parse(source : String) : SpannableStringBuilder {
		val sb =SpannableStringBuilder()
		
		val env = Env(source=source,pos=0,remain=source)
		
		fun push(pos:Int, token : Token) {
			val start = pos
			val end = pos+ token.length
			sb.append( source.substring(start,end))
			env.pos = end
			
			var c = token.color
			if( c !=0){
				if( c <0x1000000) {
					c = c or Color.BLACK
				}
				sb.setSpan(ForegroundColorSpan( c)
					,start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
			}

			if( token.italic){
				sb.setSpan(CalligraphyTypefaceSpan(Typeface.defaultFromStyle(Typeface.ITALIC))
					,start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
			}
		}
		
		var textToken : Token? = null
		var textTokenStart = 0
		fun closeTextToken(){
			val token = textToken
			if(token != null){
				token.length = env.pos -textTokenStart
				push(textTokenStart,token)
				textToken = null
			}
		}
		loop1@ while(env.remain.isNotEmpty()) {
			for(el in elements) {
				val token = el(env) ?: continue
				closeTextToken()
				push(env.pos,token)
				env.remain = source.substring(env.pos)
				continue@loop1
			}
			if( textToken == null ){
				textToken = Token(length = 0)
				textTokenStart = env.pos
			}
			env.remain = source.substring(++env.pos)
		}
		closeTextToken()
		
		return sb
	}
}

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
)

// 指定された位置から始まるノードがあれば処理してノードのリストを返す。
// なければ偽を返す
private typealias NodeParser = (env : ParserEnv) -> List<Node>?

object MisskeyMarkdownDecoder {

	private val log = LogCategory("MisskeyMarkdownDecoder")
	
	//////////////////////////////////////
	// parser
	
	private fun ParserEnv.genNode1(type : NodeType, sourceLength : Int, data : ArrayList<String?>?) : List<Node> {
		return listOf(
			Node(
				type = type,
				sourceStart = pos,
				sourceLength = sourceLength,
				data = data
			)
		)
	}
	
	private fun generateSimpleNodeParser(
		type : NodeType,
		pattern : Pattern
	) : NodeParser = { env : ParserEnv ->
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
	
	private fun generateLinkParser(
		type : NodeType,
		pattern : Pattern
	) : NodeParser = { env : ParserEnv ->
		val matcher = pattern.matcher(env.remain)
		when {
			matcher.find() -> env.genNode1(
				type
				, matcher.end()
				, arrayListOf(
					matcher.group(1) // title
					, matcher.group(2) // url
					, env.remain[0].toString()   // silent なら "?" になる
				)
			)
			
			else -> null
		}
	}
	
	private fun generateMentionParser(
		type : NodeType,
		pattern : Pattern
	) : NodeParser = { env : ParserEnv ->
		val matcher = pattern.matcher(env.remain)
		when {
			matcher.find() -> env.genNode1(
				type
				, matcher.end()
				, arrayListOf(
					matcher.group(1) // username
					, matcher.group(2) // host
				)
			)
			else -> null
		}
	}
	
	private fun generateHashtagParser(
		type : NodeType,
		pattern : Pattern
	) : NodeParser = { env : ParserEnv ->
		val matcher = pattern.matcher(env.remain)
		when {
			matcher.find() -> when {
				
				// 先頭以外では直前に空白が必要らしい
				env.pos > 0 && ! CharacterGroup.isWhitespace(
					env.text[env.pos - 1].toInt()
				) -> null
				
				else -> env.genNode1(
					type
					, matcher.end()
					, arrayListOf(
						matcher.group(1) // 先頭の#を含まないハッシュタグ
					)
				)
			}
			else -> null
		}
	}
	private fun generateCodeInlineParser(
		type : NodeType,
		pattern : Pattern
	) : NodeParser = { env : ParserEnv ->
		val matcher = pattern.matcher(env.remain)
		when {
			matcher.find() ->when{

				// インラインコードは内部にある文字を含むと認識されない。理由は謎
				matcher.group(1).contains('´') -> null

				else->env.genNode1(
					type
					, matcher.end()
					, arrayListOf(
						matcher.group(1)
					)
				)
			}
			else -> null
		}
	}
	private val reMotion1 = Pattern.compile("""^\Q(((\E(.+?)\Q)))\E""")
	private val reMotion2 = Pattern.compile("""^<motion>(.+?)</motion>""")
	
	private fun generateMotionNodeParser(type : NodeType) : NodeParser {
		return { env : ParserEnv ->
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
					type
					, matcher.end()
					, arrayListOf(
						matcher.group(1) // 先頭の#を含まないハッシュタグ
					)
				)
				else -> null
			}
		}
	}
	
	private val nodeParserList = arrayOf(
		// 処理順序に意味があるので入れ替えないこと
		// 記号列が長い順
		generateSimpleNodeParser(
			NodeType.Big,
			Pattern.compile("""^\Q***\E(.+?)\Q***\E""")
		),
		generateSimpleNodeParser(
			NodeType.Bold,
			Pattern.compile("""^\Q**\E(.+?)\Q**\E""")
		),
		generateSimpleNodeParser(
			NodeType.Title,
			Pattern.compile("""^[【\[](.+?)[】\]]\n""")
		),
		generateSimpleNodeParser(
			NodeType.Url,
			Pattern.compile("""^(https?://[\w/:%#@${'$'}&?!()\[\]~.=+\-]+)""")
		),
		generateLinkParser(
			NodeType.Link,
			Pattern.compile("""^\??\[([^\[\]]+?)]\((https?://[\w/:%#@${'$'}&?!()\[\]~.=+\-]+?)\)""")
		),
		generateMentionParser(
			NodeType.Mention,
			Pattern.compile(
				"""^@([a-z0-9_]+)(?:@([a-z0-9.\-]+[a-z0-9]))?""",
				Pattern.CASE_INSENSITIVE
			)
		),
		generateHashtagParser(
			NodeType.Hashtag,
			Pattern.compile("""^#([^\s]+)""")
		),
		
		generateSimpleNodeParser(
			NodeType.CodeBlock,
			Pattern.compile("""^```([\s\S]+?)```""")
		),
		
		generateCodeInlineParser(
			NodeType.CodeInline,
			Pattern.compile("""^`(.+?)`""")
		),
		
		generateSimpleNodeParser(
			NodeType.Quote,
			Pattern.compile("""^"([\s\S]+?)\n"""")
		),
		
		generateSimpleNodeParser(
			NodeType.Emoji,
			Pattern.compile("""^:([a-zA-Z0-9+-_]+):""")
		),
		
		generateSimpleNodeParser(
			NodeType.Search,
			Pattern.compile(
				"""^(.+?)[ 　](検索|\[検索]|Search|\[Search])(\n|${'$'})""",
				Pattern.CASE_INSENSITIVE
			)
		),
		
		generateMotionNodeParser(NodeType.Motion)
	)
	
	
	private val reStartEmptyLines = """\A(?:[ 　]*?[\x0d\x0a]+)+""".toRegex()
	private val reEndEmptyLines = """[\s\x0d\x0a]+\z""".toRegex()
	private fun trimBlock(s:String?):String?{
		s?:return null
		return s
			.replace(reStartEmptyLines,"")
			.replace(reEndEmptyLines,"")
	}
	
	private fun parse(source : String?) : ArrayList<Node> {
		val result = ArrayList<Node>()

		if(source!=null) {
			
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
					val list = el(env) ?: continue
					closeTextNode()
					for(node in list) {
						result.add(node)
						env.pos += node.sourceLength
					}
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
				
				env.remain = env.text.substring( ++ env.pos)
			}
			closeTextNode()
		}
		
		return result
	}
	
	fun decodeMarkdown(options : DecodeOptions, src : String?) : SpannableStringBuilder {
		val sb = SpannableStringBuilder()
		val context= options.context ?: return sb
		
		
		fun urlShorter(display_url:String,href:String):CharSequence{
			
			if( options.isMediaAttachment(href)) {
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
				if(! display_url.startsWith("http")){
					sb.append(uri.scheme)
					sb.append("://")
				}
				sb.append(uri.authority)
				val a = uri.encodedPath
				val q = uri.encodedQuery
				val f = uri.encodedFragment
				val remain = a + (if(q == null) "" else "?$q") + if(f == null) "" else "#$f"
				if(remain.length > 10) {
					sb.append(remain.safeSubstring( 10))
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

				for( node in parse(src) ){
					val nodeSource = src.substring(node.sourceStart,node.sourceStart+node.sourceLength)
					var start = sb.length
					val data = node.data
					
					fun setSpan(span:Any){
						val end = sb.length
						sb.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
					}
					fun setHighlight(){
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

					fun appendText(text:CharSequence? ,preventHighlight:Boolean=false){
						text?:return
						
						sb.append(text)
						
						if(!preventHighlight){
							setHighlight()
						}
					}
					fun appendTextCode(text:String? ,preventHighlight:Boolean=false){
						text?:return
						
						sb.append(MisskeySyntaxHighlighter.parse(text))
						
						if(!preventHighlight){
							setHighlight()
						}
					}
					fun appendLink(text:String, url:String,allowShort:Boolean = false){
						when {
							text.isEmpty() -> return
							!allowShort -> appendText(text,preventHighlight = true)
							else -> {
								val short = urlShorter(text,url)
								appendText(short,preventHighlight = true)
							}
						}
						val linkHelper = options.linkHelper
						if(linkHelper != null) {
							setSpan(MyClickableSpan(
								text,
								url,
								linkHelper.findAcctColor(url), // TODO 通称と色 が働くか確認する
								options.linkTag
							))
						}
						
						// リンクスパンを設定した後に色をつける
						setHighlight()
					}
					
					when(node.type){

						NodeType.Url->{
							val url = data?.get(0)
							if(url?.isNotEmpty()==true){
								appendLink(url,url,allowShort = true)
							}
						}

						NodeType.Link ->{
							val title = data?.get(0)?:"?"
							val url = data?.get(1)
							// val silent = data?.get(2)
							// silentはプレビュー表示を抑制するが、Subwayにはもともとないので関係なかった
							if(url?.isNotEmpty()==true){
								appendLink(title,url)
							}
						}
						
						NodeType.Mention->{

							val username = data?.get(0)?:""
							val host = data?.get(1)?:""
						
							val linkHelper = options.linkHelper
							if(linkHelper == null) {
								appendText(
									if(host.isEmpty()) {
										"@$username"
									} else {
										"@$username@$host"
									}
								)
							}else{
								val acct = if( Pref.bpMentionFullAcct(App1.pref)) {
									when {
										host.isEmpty() -> "@$username@${linkHelper.host}"
										else -> "@$username@$host"
									}
								}else {
									when {
										host.isEmpty() -> "@$username"
										host.equals(linkHelper.host,ignoreCase = true) -> "@$username"
										else -> "@$username@$host"
									}
								}
								appendLink(acct,"https://${linkHelper}/$acct")
							}
						}
						
						NodeType.Hashtag ->{
							val tag = data?.get(0)
							if(tag?.isNotEmpty()==true){
								appendLink("#$tag","https://misskey.m544.net/tags/"+tag.encodePercent())
							}
						}
						
						NodeType.Emoji->{
							val code = data?.get(0)
							if( code?.isNotEmpty()==true){
								appendText(options.decodeEmoji(":$code:"))
							}
						}
						
						////////////////////////////////////////////
						// 装飾インライン要素
						
						NodeType.Text->{
							appendText(nodeSource)
						}
						
						NodeType.Big ->{
							appendText(data?.get(0))
							setSpan(MisskeyBigSpan(font_bold))
						}
						
						NodeType.Motion->{
							val code = data?.get(0)
							appendText(code)
							setSpan(MisskeyMotionSpan(ActMain.timeline_font))
						}
						
						NodeType.Bold->{
							appendText(data?.get(0))
							setSpan(CalligraphyTypefaceSpan(font_bold))
						}
						
						NodeType.CodeInline->{
							appendTextCode(data?.get(0))
							setSpan(BackgroundColorSpan(0x40808080))
							setSpan(CalligraphyTypefaceSpan(Typeface.MONOSPACE))
						}
						
						////////////////////////////////////////////
						// ブロック要素

						NodeType.Title->{
							
							appendText(trimBlock(data?.get(0)))
							setSpan(AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER))
							setSpan(BackgroundColorSpan(0x20808080))
							setSpan(RelativeSizeSpan(1.5f))
							appendText("\n")
						}

						NodeType.CodeBlock->{
							appendTextCode(trimBlock(data?.get(0)))
							setSpan(BackgroundColorSpan(0x40808080))
							setSpan(RelativeSizeSpan(0.7f))
							setSpan(CalligraphyTypefaceSpan(Typeface.MONOSPACE))
							appendText("\n")
						}

						NodeType.Quote->{
							appendText(trimBlock(data?.get(0)))
							setSpan(BackgroundColorSpan(0x20808080))
							setSpan(CalligraphyTypefaceSpan(Typeface.defaultFromStyle(Typeface.ITALIC)))
							appendText("\n")
						}
						
						NodeType.Search->{
							val text = data?.get(0)
							val kw_start = sb.length // キーワードの開始位置
							appendText(text)
							appendText(" ")
							start = sb.length // 検索リンクの開始位置
							appendLink(
								context.getString(R.string.search),
								"https://www.google.co.jp/search?q="+(text?:"Subway Tooter").encodePercent()
							)
							sb.setSpan(RelativeSizeSpan(1.5f), kw_start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
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

