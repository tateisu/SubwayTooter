package jp.juggler.subwaytooter.util

import android.net.Uri
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned

import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.regex.Pattern

import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootMention
import jp.juggler.subwaytooter.span.EmojiImageSpan
import jp.juggler.subwaytooter.span.HighlightSpan
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.table.HighlightWord

object HTMLDecoder {
	private val log = LogCategory("HTMLDecoder")
	
	private const val DEBUG_HTML_PARSER = false
	
	private const val OPEN_TYPE_OPEN_CLOSE = 1
	private const val OPEN_TYPE_OPEN = 2
	private const val OPEN_TYPE_CLOSE = 3
	
	private const val TAG_TEXT = "<>text"
	private const val TAG_END = "<>end"
	
	private val reTag = Pattern.compile("<(/?)(\\w+)")
	private val reTagEnd = Pattern.compile("(/?)>$")
	private val reHref = Pattern.compile("\\bhref=\"([^\"]*)\"")
	private val reAttribute = Pattern.compile("\\s+([A-Za-z0-9:_-]+)\\s*=([\"'])([^>]*?)\\2")
	private val reShortcode = Pattern.compile(":[A-Za-z0-9_-]+:")
	
	private val block_tag : HashSet<String> by lazy {
		val set = HashSet<String>()
		set.add("div")
		set.add("p")
		set.add("li")
		set.add("h1")
		set.add("h2")
		set.add("h3")
		set.add("h4")
		set.add("h5")
		set.add("body")
		set
	}
	
	fun isWhitespaceOrLineFeed(codepoint : Int) : Boolean {
		return CharacterGroup.isWhitespace(codepoint) || when(codepoint) {
			0x0a, 0x0d -> true
			else -> false
		}
	}
	
	private val reEntity = Pattern.compile("&(#?)(\\w+);")
	private val entity_map = HashMap<String, Char>()
	private fun _addEntity(s : String, c : Char) {
		entity_map[s] = c
	}
	
	private fun chr(num : Int) : Char {
		return num.toChar()
	}
	
	fun decodeEntity(src : String) : String {
		var sb : StringBuilder? = null
		val m = reEntity.matcher(src)
		var last_end = 0
		while(m.find()) {
			if(sb == null) sb = StringBuilder()
			val start = m.start()
			val end = m.end()
			try {
				if(start > last_end) {
					sb.append(src.substring(last_end, start))
				}
				val is_numeric = m.group(1).isNotEmpty()
				val part = m.group(2)
				if(! is_numeric) {
					val c = entity_map[part]
					if(c != null) {
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
					} catch(ex : Throwable) {
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
		if(end > last_end) {
			sb.append(src.substring(last_end, end))
		}
		return sb.toString()
	}
	
	fun encodeEntity(src : String) : String {
		val size = src.length
		val sb = StringBuilder()
		sb.ensureCapacity(size)
		for(i in 0 until size) {
			val c = src[i]
			when(c) {
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
	
	private val reDoctype = Pattern.compile("\\A\\s*<!doctype[^>]*>", Pattern.CASE_INSENSITIVE)
	private val reComment = Pattern.compile("<!--.*?-->", Pattern.DOTALL)
	
	private class TokenParser(srcArg : String) {
		
		internal val src : String
		internal var next : Int = 0
		
		internal var tag : String = ""
		internal var open_type : Int = 0
		internal var text : String = ""
		
		init {
			this.src = srcArg
				.replaceFirst(reDoctype, "")
				.replaceAll(reComment, " ")
			
			eat()
		}
		
		internal fun eat() {
			
			// end?
			if(next >= src.length) {
				tag = TAG_END
				open_type = OPEN_TYPE_OPEN_CLOSE
				return
			}
			
			// text ?
			var end = src.indexOf('<', next)
			if(end == - 1) end = src.length
			if(end > next) {
				this.text = src.substring(next, end)
				this.tag = TAG_TEXT
				this.open_type = OPEN_TYPE_OPEN_CLOSE
				next = end
				return
			}
			
			// tag ?
			end = src.indexOf('>', next)
			if(end == - 1) {
				end = src.length
			} else {
				++ end
			}
			text = src.substring(next, end)
			
			next = end
			
			val m = reTag.matcher(text)
			if(m.find()) {
				val is_close = m.group(1).isNotEmpty()
				tag = m.group(2).toLowerCase()
				val m2 = reTagEnd.matcher(text)
				var is_openclose = false
				if(m2.find()) {
					is_openclose = m2.group(1).isNotEmpty()
				}
				open_type =
					if(is_close) OPEN_TYPE_CLOSE else if(is_openclose) OPEN_TYPE_OPEN_CLOSE else OPEN_TYPE_OPEN
				if(tag == "br") open_type = OPEN_TYPE_OPEN_CLOSE
			} else {
				tag = TAG_TEXT
				this.open_type = OPEN_TYPE_OPEN_CLOSE
			}
		}
		
	}
	
	private class Node {
		internal val child_nodes = ArrayList<Node>()
		
		internal val tag : String
		internal val text : String
		
		private val href : String?
			get() {
				val m = reHref.matcher(text)
				if(m.find()) {
					val href = decodeEntity(m.group(1))
					if(href.isNotEmpty()) {
						return href
					}
				}
				return null
			}
		
		internal constructor() {
			tag = "<>root"
			text = ""
		}
		
		internal constructor(t : TokenParser) {
			this.tag = t.tag
			this.text = t.text
		}
		
		internal fun addChild(t : TokenParser, indent : String) {
			if(DEBUG_HTML_PARSER) log.d("parseChild: %s(%s", indent, tag)
			while(true) {
				if(TAG_END == t.tag) break
				if(OPEN_TYPE_CLOSE == t.open_type) {
					t.eat()
					break
				}
				val open_type = t.open_type
				val child = Node(t)
				child_nodes.add(child)
				t.eat()
				
				if(DEBUG_HTML_PARSER)
					log.d("parseChild: %s|%s %s [%s]", indent, child.tag, open_type, child.text)
				
				if(OPEN_TYPE_OPEN == open_type) {
					child.addChild(t, indent + "--")
				}
			}
			if(DEBUG_HTML_PARSER) log.d("parseChild: %s)%s", indent, tag)
		}
		
		internal fun encodeSpan(
			options : DecodeOptions,
			sb : SpannableStringBuilder
		) {
			if(TAG_TEXT == tag) {
				if(options.context != null && options.decodeEmoji) {
					sb.append(options.decodeEmoji(decodeEntity(text)))
				} else {
					sb.append(decodeEntity(text))
				}
				return
			}
			if(DEBUG_HTML_PARSER) sb.append("(start ").append(tag).append(")")
			
			val sb_tmp = when(tag) {
				"a", "style", "script" -> SpannableStringBuilder()
				else -> sb
			}
			
			if("img" == tag) {
				var replaced = false
				if(options.unwrapEmojiImageTag) {
					val attrs = parseAttributes(text)
					val cssClass = attrs["class"]
					val title = attrs["title"]
					if(cssClass != null
						&& title != null
						&& cssClass.contains("emojione")
						&& reShortcode.matcher(title).find()
					) {
						replaced = true
						sb_tmp.append(options.decodeEmoji(title))
					}
				}
				
				if(! replaced) {
					sb_tmp.append("<img/>")
				}
			} else {
				for(child in child_nodes) {
					child.encodeSpan(options, sb_tmp)
				}
				// sb_tmpを作成したa 以外のタグ(style,script)は読み捨てる
			}
			
			if("a" == tag) {
				
				val start = sb.length
				
				sb.append(
					encodeUrl(options, sb_tmp.toString(), href)
				)
				
				val end = sb.length
				
				if(end > start) {
					val linkHelper = options.linkHelper
					if(linkHelper != null) {
						val href = href
						if(href != null) {
							val link_text = sb.subSequence(start, end).toString()
							val span = MyClickableSpan(
								link_text,
								href,
								linkHelper.findAcctColor(href),
								options.linkTag
							)
							sb.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
							
						}
					}
					
					// リンクスパンを設定した後に色をつける
					val list = options.highlightTrie?.matchList(sb, start, end)
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
				
			}
			
			if(DEBUG_HTML_PARSER) sb.append("(end ").append(tag).append(")")
			
			if("br" == tag) sb.append('\n')
			
			if(block_tag.contains(tag)) {
				if(sb.isNotEmpty()) {
					// 末尾の改行を数える
					var last_br_count = 0
					var last = sb.length - 1
					while(last >= 0) {
						val c = sb[last --]
						if(c == '\n') {
							++ last_br_count
							continue
						}
						if(Character.isWhitespace(c)) continue
						break
					}
					// 末尾の改行が２文字未満なら改行を追加する
					while(last_br_count ++ < 2) sb.append('\n')
				}
			}
		}
		
		private val reNormalLink = Pattern.compile("\\A\\w+://")
		
		private fun encodeUrl(
			options : DecodeOptions,
			display_url : String,
			href : String?
		) : CharSequence {
			
			val context = options.context
			if(context == null || ! options.short) {
				return display_url
			}
			
			// 通常リンクはhttp,httpsだけでなく幾つかのスキーマ名が含まれる
			// スキーマ名の直後には必ず :// が出現する
			// https://github.com/tootsuite/mastodon/pull/7810
			if(! reNormalLink.matcher(display_url).find() ) {
				if(display_url.startsWith("@") && href != null && Pref.bpMentionFullAcct(App1.pref)) {
					// メンションをfull acct にする
					val m = TootAccount.reAccountUrl.matcher(href)
					if(m.find()) {
						return "@" + m.group(2) + "@" + m.group(1)
					}
				}
				// ハッシュタグやメンションはURLの短縮表示の対象外
				return display_url
			}
			
			if(options.isMediaAttachment(href)) {
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
				val sb = StringBuilder()
				if(! display_url.startsWith("http")){
					sb.append(uri.scheme)
					sb.append("://")
				}
				sb.append(uri.authority)
				val a = uri.encodedPath
				val q = uri.encodedQuery
				val f = uri.encodedFragment
				val remain = a + (if(q == null) "" else "?" + q) + if(f == null) "" else "#" + f
				if(remain.length > 10) {
					sb.append(remain.substring(0, 10))
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
	}
	
	// split attributes
	private fun parseAttributes(text : String) : HashMap<String, String> {
		val dst = HashMap<String, String>()
		val m = reAttribute.matcher(text)
		while(m.find()) {
			val name = m.group(1).toLowerCase()
			val value = decodeEntity(m.group(3))
			dst[name] = value
		}
		return dst
	}
	
	fun decodeHTML(options : DecodeOptions, src : String?) : SpannableStringBuilder {
		
		if( options.linkHelper?.isMisskey == true){
			return MisskeyMarkdownDecoder.decodeMarkdown(options,src)
		}
		
		val sb = SpannableStringBuilder()
		
		try {
			if(src != null) {
				val tracker = TokenParser(src)
				val rootNode = Node()
				while(TAG_END != tracker.tag) {
					rootNode.addChild(tracker, "")
				}
				
				rootNode.encodeSpan(options, sb)
				
				// 末尾の空白を取り除く
				var end = sb.length
				while(end > 0 && isWhitespaceOrLineFeed(sb[end - 1].toInt())) -- end
				if(end < sb.length) sb.delete(end, sb.length)
			}
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
		return sb
	}
	
	fun decodeMentions(
		linkHelper : LinkHelper,
		src_list : ArrayList<TootMention>?,
		link_tag : Any?
	) : Spannable? {
		if(src_list == null || src_list.isEmpty()) return null
		val sb = SpannableStringBuilder()
		for(item in src_list) {
			if(sb.isNotEmpty()) sb.append(" ")
			val start = sb.length
			sb.append('@')
			if(Pref.bpMentionFullAcct(App1.pref)) {
				sb.append(linkHelper.getFullAcct(item.acct))
			} else {
				sb.append(item.acct)
			}
			val end = sb.length
			val url = item.url
			if(end > start) {
				val link_text = sb.subSequence(start, end).toString()
				val span = MyClickableSpan(
					link_text,
					url,
					linkHelper.findAcctColor(item.url),
					link_tag
				)
				sb.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
			}
		}
		return sb
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
