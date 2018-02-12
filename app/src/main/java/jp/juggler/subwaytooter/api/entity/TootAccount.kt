package jp.juggler.subwaytooter.api.entity

import android.content.Context
import android.net.Uri
import android.text.Spannable
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.util.*

import org.json.JSONArray
import org.json.JSONObject

import java.util.ArrayList
import java.util.regex.Pattern

open class TootAccount(
	parser : TootParser,
	src : JSONObject
) : TimelineItem() {
	
	//URL of the user's profile page (can be remote)
	// https://mastodon.juggler.jp/@tateisu
	// 疑似アカウントではnullになります
	val url : String?
	
	//	The ID of the account
	val id : Long
	
	//	Equals username for local users, includes @domain for remote ones
	val acct : String
	
	// 	The username of the account  /[A-Za-z0-9_]{1,30}/
	val username : String
	
	val host : String
	
	//	The account's display name
	val display_name : String
	
	//	The account's display name
	val decoded_display_name : Spannable
	
	//Boolean for when the account cannot be followed without waiting for approval first
	val locked : Boolean
	
	//	The time the account was created
	// ex: "2017-04-13T11:06:08.289Z"
	val created_at : String?
	val time_created_at : Long
	
	//	The number of followers for the account
	var followers_count : Long? = null
	
	//The number of accounts the given account is following
	var following_count : Long? = null
	
	//	The number of statuses the account has made
	var statuses_count : Long? = null
	
	// Biography of user
	// 説明文。改行は\r\n。リンクなどはHTMLタグで書かれている
	val note : String?
	
	val decoded_note : Spannable
	
	//	URL to the avatar image
	val avatar : String?
	
	//	URL to the avatar static image (gif)
	val avatar_static : String?
	
	//URL to the header image
	val header : String?
	
	//	URL to the header static image (gif)
	val header_static : String?
	
	val source : Source?
	
	val profile_emojis : HashMap<String, NicoProfileEmoji>?
	
	val moved : TootAccount?
	
	init {
		var sv : String?
		
		// 絵文字データは先に読んでおく
		this.profile_emojis = parseMapOrNull(::NicoProfileEmoji, src.optJSONArray("profile_emojis"))
		
		// 疑似アカウントにacctとusernameだけ
		this.url = src.parseString("url")
		this.username = src.notEmptyOrThrow("username")
		
		//
		sv = src.parseString("display_name")
		this.display_name = if(sv?.isNotEmpty() == true) sv.sanitizeBDI() else username
		this.decoded_display_name = decodeDisplayName(parser.context)
		
		//
		this.note = src.parseString("note")
		this.decoded_note = DecodeOptions(
			parser.context,
			parser.linkHelper,
			short = true,
			decodeEmoji = true,
			emojiMapProfile = this.profile_emojis
		).decodeHTML(this.note)
		
		this.source = parseSource(src.optJSONObject("source"))
		this.moved =
			src.optJSONObject("moved")?.let { TootAccount(parser, it) }
		this.locked = src.optBoolean("locked")
		
		when(parser.serviceType) {
			ServiceType.MASTODON -> {
				
				val hostAccess = parser.linkHelper.host
				
				this.id = src.parseLong("id") ?: INVALID_ID
				
				this.acct = src.notEmptyOrThrow("acct")
				this.host = findHostFromUrl(acct, hostAccess, url)
					?: throw RuntimeException("can't find host from acct or url")
				
				this.followers_count = src.parseLong("followers_count")
				this.following_count = src.parseLong("following_count")
				this.statuses_count = src.parseLong("statuses_count")
				
				this.created_at = src.parseString("created_at")
				this.time_created_at = TootStatus.parseTime(this.created_at)
				
				this.avatar = src.parseString("avatar")
				this.avatar_static = src.parseString("avatar_static")
				this.header = src.parseString("header")
				this.header_static = src.parseString("header_static")
				
			}
			
			ServiceType.TOOTSEARCH -> {
				// tootsearch のアカウントのIDはどのタンス上のものか分からないので役に立たない
				this.id = INVALID_ID // src.parseLong( "id", INVALID_ID)
				
				sv = src.notEmptyOrThrow("acct")
				this.host = findHostFromUrl(sv, null, url)
					?: throw RuntimeException("can't find host from acct or url")
				this.acct = this.username + "@" + this.host
				
				this.followers_count = src.parseLong("followers_count")
				this.following_count = src.parseLong("following_count")
				this.statuses_count = src.parseLong("statuses_count")
				
				this.created_at = src.parseString("created_at")
				this.time_created_at = TootStatus.parseTime(this.created_at)
				
				this.avatar = src.parseString("avatar")
				this.avatar_static = src.parseString("avatar_static")
				this.header = src.parseString("header")
				this.header_static = src.parseString("header_static")
			}
			
			ServiceType.MSP -> {
				this.id = src.parseLong("id") ?: INVALID_ID
				
				// MSPはLTLの情報しか持ってないのでacctは常にホスト名部分を持たない
				this.host = findHostFromUrl(null, null, url)
					?: throw RuntimeException("can't find host from url")
				this.acct = this.username + "@" + host
				
				this.followers_count = null
				this.following_count = null
				this.statuses_count = null
				
				this.created_at = null
				this.time_created_at = 0L
				
				val avatar = src.parseString("avatar")
				this.avatar = avatar
				this.avatar_static = avatar
				this.header = null
				this.header_static = null
				
			}
			
		}
	}
	
	class Source(src : JSONObject) {
		// デフォルト公開範囲
		val privacy : String?
		
		// 添付画像をデフォルトでNSFWにする設定
		private val sensitive : Boolean
		
		// HTMLエンコードされていない、生のnote
		val note : String?
		
		init {
			this.privacy = src.parseString("privacy")
			this.note = src.parseString("note")
			// nullになることがあるが、falseと同じ扱いでよい
			this.sensitive = src.optBoolean("sensitive", false)
		}
	}
	
	// リストメンバーダイアログや引っ越し先ユーザなど、TL以外の部分に名前を表示する場合は
	// Invalidator の都合でSpannableを別途生成する必要がある
	fun decodeDisplayName(context : Context) : Spannable {
		
		// remove white spaces
		val sv = reWhitespace.matcher(display_name).replaceAll(" ")
		
		// decode emoji code
		return DecodeOptions(context, emojiMapProfile = profile_emojis).decodeEmoji(sv)
	}
	
	companion object {
		private val log = LogCategory("TootAccount")
		
		const val INVALID_ID = - 1L
		
		@Suppress("HasPlatformType")
		private val reWhitespace = Pattern.compile("[\\s\\t\\x0d\\x0a]+")
		
		@Suppress("HasPlatformType")
		val reAccountUrl =
			Pattern.compile("\\Ahttps://([A-Za-z0-9.-]+)/@([A-Za-z0-9_]+)(?:\\z|[?#])")
		
		private fun parseSource(src : JSONObject?) : Source? {
			src ?: return null
			return try {
				Source(src)
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e("parseSource failed.")
				null
			}
		}
		
		// Tootsearch用。URLやUriを使ってアカウントのインスタンス名を調べる
		fun findHostFromUrl(acct : String?, accessHost : String?, url : String?) : String? {
			
			// acctから調べる
			if(acct != null) {
				val pos = acct.indexOf('@')
				if(pos != - 1) {
					val host = acct.substring(pos + 1)
					if(host.isNotEmpty()) return host.toLowerCase()
				}
			}
			
			// accessHostから調べる
			if(accessHost != null) {
				return accessHost
			}
			
			// URLから調べる
			if(url != null) {
				try {
					// たぶんどんなURLでもauthorityの部分にホスト名が来るだろう(慢心)
					val uri = Uri.parse(url)
					return uri.authority.toLowerCase()
				} catch(ex : Throwable) {
					log.e(ex, "findHostFromUrl: can't parse host from URL $url")
				}
			}
			
			return null
		}
	}
}
