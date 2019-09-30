package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.util.MisskeyMarkdownDecoder
import jp.juggler.util.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

open class TootTag constructor(
	
	// The hashtag, not including the preceding #
	val name : String,
	
	// The URL of the hashtag. may null if generated from TootContext
	val url : String? = null,
	
	// Mastodon /api/v2/search provides history.
	val history : ArrayList<History>? = null

) : TimelineItem() {
	
	val countDaily : Int
	val countWeekly : Int
	val accountDaily : Int
	val accountWeekly : Int
	
	init {
		countDaily = history?.first()?.uses ?: 0
		countWeekly = history?.sumBy { it.uses } ?: 0
		
		accountDaily = history?.first()?.accounts ?: 0
		accountWeekly = history?.map { it.accounts }?.max() ?: accountDaily
	}
	
	class History(src : JSONObject) {
		val day : Long
		val uses : Int
		val accounts : Int
		
		init {
			day = src.parseLong("day")
				?: throw RuntimeException("TootTrendTag.History: missing day")
			uses = src.parseInt("uses")
				?: throw RuntimeException("TootTrendTag.History: missing uses")
			accounts = src.parseInt("accounts")
				?: throw RuntimeException("TootTrendTag.History: missing accounts")
		}
		
	}
	
	// for TREND_TAG column
	constructor(src : JSONObject) : this(
		name = src.notEmptyOrThrow("name"),
		url = src.parseString("url"),
		history = parseHistories(src.optJSONArray("history"))
	)
	
	companion object {
		
		val log = LogCategory("TootTag")
		
		private fun parseHistories(src : JSONArray?) : ArrayList<History>? {
			src ?: return null
			
			val dst = ArrayList<History>()
			for(i in 0 until src.length()) {
				try {
					dst.add(History(src.optJSONObject(i)))
				} catch(ex : Throwable) {
					log.e(ex, "parseHistories failed.")
				}
			}
			return dst
		}
		
		fun parseList(parser : TootParser, array : JSONArray?) : ArrayList<TootTag> {
			val result = ArrayList<TootTag>()
			if(array != null) {
				if(parser.serviceType == ServiceType.MISSKEY) {
					for(i in 0 until array.length()) {
						val sv = array.parseString(i)
						if(sv?.isNotEmpty() == true) {
							result.add(TootTag(name = sv))
						}
					}
				} else {
					for(i in 0 until array.length()) {
						val tag = try {
							when(val item = array.opt(i)) {
								is String -> if(item.isNotEmpty()) {
									TootTag(name = item)
								} else {
									null
								}
								is JSONObject -> TootTag(item)
								else -> null
							}
						} catch(ex : Throwable) {
							log.w(ex, "parseList: parse error")
							null
						}
						if(tag != null) result.add(tag)
					}
				}
			}
			return result
		}
		
		// \p{L} : アルファベット (Letter)。
		// 　　Ll(小文字)、Lm(擬似文字)、Lo(その他の文字)、Lt(タイトル文字)、Lu(大文字アルファベット)を含む
		// \p{M} : 記号 (Mark)
		// \p{Nd} : 10 進数字 (Decimal number)
		// \p{Pc} : 連結用句読記号 (Connector punctuation)
		
		// rubyの [:word:] ： 単語構成文字 (Letter | Mark | Decimal_Number | Connector_Punctuation)
		private const val w = """\p{L}\p{M}\p{Nd}\p{Pc}"""
		
		// rubyの [:alpha:] : 英字 (Letter | Mark)
		private const val a = """\p{L}\p{M}"""
		
		// 2019/7/20 https://github.com/tootsuite/mastodon/pull/11363/files
		private val reTagMastodon : Pattern =
			Pattern.compile("""(?:^|[^\w)])#([_$w][·_$w]*[·_$a][·_$w]*[_$w]|[_$w]*[$a][_$w]*)""")
		
		// https://medium.com/@alice/some-article#.abcdef123 => タグにならない
		// https://en.wikipedia.org/wiki/Ghostbusters_(song)#Lawsuit => タグにならない
		// #ａｅｓｔｈｅｔｉｃ => #ａｅｓｔｈｅｔｉｃ
		// #3d => #3d
		// #l33ts35k =>  #l33ts35k
		// #world2016 => #world2016
		// #_test => #_test
		// #test_ => #test_
		// #one·two·three· => 末尾の・はタグに含まれない。#one·two·three までがハッシュタグになる。
		// #0123456' => 数字だけのハッシュタグはタグとして認識されない。
		// #000_000 => 認識される。orの前半分が機能してるらしい
		//
		
		// タグに使えない文字
		// 入力補完用なのでやや緩め
		private val reCharsNotTagMastodon = Pattern.compile("""[^·_$w$a]""")
		private val reCharsNotTagMisskey = Pattern.compile("""[\s.,!?'${'"'}:/\[\]【】]""")
		
		// find hashtags in content text(raw)
		// returns null if hashtags not found, or ArrayList of String (tag without #)
		fun findHashtags(src : String, isMisskey : Boolean) : ArrayList<String>? =
			if(isMisskey) {
				MisskeyMarkdownDecoder.findHashtags(src)
			} else {
				var result : ArrayList<String>? = null
				val m = reTagMastodon.matcher(src)
				while(m.find()) {
					if(result == null) result = ArrayList()
					result.add(m.groupEx(1) !!)
				}
				result
			}
		
		fun isValid(src : String, isMisskey : Boolean) =
			if(isMisskey) {
				! reCharsNotTagMisskey.matcher(src).find()
			} else {
				! reCharsNotTagMastodon.matcher(src).find()
			}
		
		// https://mastodon.juggler.jp/tags/%E3%83%8F%E3%83%83%E3%82%B7%E3%83%A5%E3%82%BF%E3%82%B0
		private val reUrlHashTag =
			Pattern.compile("""\Ahttps://([^/]+)/tags/([^?#・\s\-+.,:;/]+)(?:\z|[?#])""")
		
		// https://pixelfed.tokyo/discover/tags/SubwayTooter?src=hash
		private val reUrlHashTagPixelfed =
			Pattern.compile("""\Ahttps://([^/]+)/discover/tags/([^?#・\s\-+.,:;/]+)(?:\z|[?#])""")
		
		// returns null or pair of ( decoded tag without sharp, host)
		fun String.findHashtagFromUrl() : Pair<String, String>? {
			var m = reUrlHashTag.matcher(this)
			if(m.find()) {
				val host = m.groupEx(1) !!
				val tag = m.groupEx(2) !!.decodePercent()
				return Pair(tag, host)
			}
			
			m = reUrlHashTagPixelfed.matcher(this)
			if(m.find()) {
				val host = m.groupEx(1) !!
				val tag = m.groupEx(2) !!.decodePercent()
				return Pair(tag, host)
			}
			
			return null
		}
		
	}
}