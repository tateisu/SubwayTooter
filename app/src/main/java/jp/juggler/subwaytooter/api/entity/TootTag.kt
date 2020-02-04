package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.util.MisskeyMarkdownDecoder
import jp.juggler.util.*
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
	
	class History(src : JsonObject) {
		val day : Long
		val uses : Int
		val accounts : Int
		
		init {
			day = src.long("day")
				?: throw RuntimeException("TootTrendTag.History: missing day")
			uses = src.int("uses")
				?: throw RuntimeException("TootTrendTag.History: missing uses")
			accounts = src.int("accounts")
				?: throw RuntimeException("TootTrendTag.History: missing accounts")
		}
		
	}
	
	// for TREND_TAG column
	constructor(src : JsonObject) : this(
		name = src.notEmptyOrThrow("name"),
		url = src.string("url"),
		history = parseHistories(src.jsonArray("history"))
	)
	
	companion object {
		
		val log = LogCategory("TootTag")
		
		private fun parseHistories(src : JsonArray?) : ArrayList<History>? {
			src ?: return null
			
			val dst = ArrayList<History>()
			src.objectList().forEach {
				try {
					dst.add(History(it))
				} catch(ex : Throwable) {
					log.e(ex, "parseHistories failed.")
				}
			}
			return dst
		}
		
		fun parseList(parser : TootParser, array : JsonArray?) : ArrayList<TootTag> {
			val result = ArrayList<TootTag>()
			if(array != null) {
				if(parser.serviceType == ServiceType.MISSKEY) {
					array.stringArrayList().forEach { sv ->
						if(sv.isNotEmpty()) {
							result.add(TootTag(name = sv))
						}
					}
				} else {
					array.forEach { item ->
						val tag = try {
							when(item) {
								is String -> if(item.isNotEmpty()) {
									TootTag(name = item)
								} else {
									null
								}
								is JsonObject -> TootTag(item)
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
		
		private const val w = TootAccount.reRubyWord
		private const val a = TootAccount.reRubyAlpha
		private const val s = "_\\u00B7\\u200c" // separators
	
		private fun generateMastodonTagPattern():Pattern{
			val reMastodonTagName = """([_$w][$s$w]*[$s$a][$s$w]*[_$w])|([_$w]*[$a][_$w]*)"""
			return  """(?:^|[^\w/)])#($reMastodonTagName)""".asciiPattern()
		}
		
		private val reMastodonTag = generateMastodonTagPattern()
		
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
		private val reCharsNotTagMastodon = """[^$s$w$a]""".asciiPattern()
		private val reCharsNotTagMisskey = """[\s.,!?'${'"'}:/\[\]【】]""".asciiPattern()
		
		// find hashtags in content text(raw)
		// returns null if hashtags not found, or ArrayList of String (tag without #)
		fun findHashtags(src : String, isMisskey : Boolean) : ArrayList<String>? =
			if(isMisskey) {
				MisskeyMarkdownDecoder.findHashtags(src)
			} else {
				var result : ArrayList<String>? = null
				val m = reMastodonTag.matcher(src)
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
		private val reUrlHashTag ="""\Ahttps://([^/]+)/tags/([^?#・\s\-+.,:;/]+)(?:\z|[?#])"""
			.asciiPattern()
		
		// https://pixelfed.tokyo/discover/tags/SubwayTooter?src=hash
		private val reUrlHashTagPixelfed ="""\Ahttps://([^/]+)/discover/tags/([^?#・\s\-+.,:;/]+)(?:\z|[?#])"""
			.asciiPattern()
		
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