package jp.juggler.subwaytooter.api.entity

import android.content.Context
import android.text.Spannable

import jp.juggler.subwaytooter.util.DecodeOptions

import org.json.JSONArray
import org.json.JSONObject

import java.util.ArrayList
import java.util.regex.Pattern

import jp.juggler.subwaytooter.util.LinkClickContext
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils


open class TootAccount(
	context : Context,
	accessInfo : LinkClickContext,
	src : JSONObject,
	serviceType : ServiceType
) {
	
	//URL of the user's profile page (can be remote)
	// https://mastodon.juggler.jp/@tateisu
	val url : String
	
	//	The ID of the account
	val id : Long
	
	// 	The username of the account  /[A-Za-z0-9_]{1,30}/
	val username : String
	
	//	Equals username for local users, includes @domain for remote ones
	val acct : String
	
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
	val avatar : String ?
	
	//	URL to the avatar static image (gif)
	val avatar_static : String ?
	
	//URL to the header image
	val header : String ?
	
	//	URL to the header static image (gif)
	val header_static : String ?
	
	
	val source : Source?
	
	val profile_emojis : NicoProfileEmoji.Map?
	
	val moved : TootAccount?
	
	init {
		// 絵文字データは先に読んでおく
		this.profile_emojis = NicoProfileEmoji.parseMap(src.optJSONArray("profile_emojis"))

		var sv :String?
		
		//
		sv = Utils.optStringX(src, "url")
		this.url = if( sv != null && sv.isNotEmpty() ) sv else throw RuntimeException("missing url")

		//
		sv = Utils.optStringX(src, "username")
		this.username = if( sv != null && sv.isNotEmpty() ) sv else "?"
		
		sv = Utils.optStringX(src, "display_name")
		this.display_name = if( sv != null && sv.isNotEmpty() ) Utils.sanitizeBDI(sv) else username
		this.decoded_display_name = decodeDisplayName(context)

		//
		this.note = Utils.optStringX(src, "note")
		this.decoded_note = DecodeOptions()
			.setShort(true)
			.setDecodeEmoji(true)
			.setProfileEmojis(this.profile_emojis)
			.decodeHTML(context, accessInfo, this.note)
		
		this.source = parseSource(src.optJSONObject("source"))
		this.moved = src.optJSONObject("moved")?.let{ TootAccount(context, accessInfo, it,serviceType) }
		this.locked = src.optBoolean("locked")
		
		when(serviceType) {
			ServiceType.MASTODON -> {
				this.id = Utils.optLongX(src, "id", - 1L)
				
				sv = Utils.optStringX(src, "acct")
				this.acct = if(sv != null && sv.isNotEmpty() ) sv else throw RuntimeException("missing acct")

				this.followers_count = Utils.optLongX(src, "followers_count")
				this.following_count = Utils.optLongX(src, "following_count")
				this.statuses_count = Utils.optLongX(src, "statuses_count")
				
				this.created_at = Utils.optStringX(src, "created_at")
				this.time_created_at = TootStatus.parseTime(this.created_at)
				
				this.avatar = Utils.optStringX(src, "avatar")
				this.avatar_static = Utils.optStringX(src, "avatar_static")
				this.header = Utils.optStringX(src, "header")
				this.header_static = Utils.optStringX(src, "header_static")
			}
			
			ServiceType.TOOTSEARCH -> {
				// tootsearch のアカウントのIDはどのタンス上のものか分からないので、IDには意味がない
				this.id = INVALID_ID // Utils.optLongX(src, "id", INVALID_ID)
				
				sv = Utils.optStringX(src, "acct")
				this.acct = if( sv != null && sv.contains('@') ){
					sv
				}else {
					val m = TootAccount.reAccountUrl.matcher(this.url)
					if(! m.find()) throw RuntimeException("parseAccount: not account url: $url")
					this.username + "@" + m.group(1).toLowerCase()
				}
				
				this.followers_count = Utils.optLongX(src, "followers_count")
				this.following_count = Utils.optLongX(src, "following_count")
				this.statuses_count = Utils.optLongX(src, "statuses_count")
				
				this.created_at = Utils.optStringX(src, "created_at")
				this.time_created_at = TootStatus.parseTime(this.created_at)
				
				this.avatar = Utils.optStringX(src, "avatar")
				this.avatar_static = Utils.optStringX(src, "avatar_static")
				this.header = Utils.optStringX(src, "header")
				this.header_static = Utils.optStringX(src, "header_static")
			}

			ServiceType.MSP -> {
				this.id = Utils.optLongX(src, "id", INVALID_ID)

				val m = TootAccount.reAccountUrl.matcher(this.url)
				if(! m.find()) throw RuntimeException("parseAccount: not account url: $url")
				this.acct = this.username + "@" + m.group(1).toLowerCase()
				
				this.followers_count = null
				this.following_count = null
				this.statuses_count = null
				
				this.created_at = null
				this.time_created_at = 0L

				val avatar = Utils.optStringX(src, "avatar")
				this.avatar =avatar
				this.avatar_static = avatar
				this.header = null
				this.header_static = null
				
				
			}
			
		}
	}
	
	// acctのホスト名部分またはnull
	val acctHost : String?
		get() {
			val pos = acct.indexOf('@')
			if(pos == - 1) return null
			val host = acct.substring(pos + 1)
			return when {
				host.isEmpty() || host.contains("?") -> null
				else -> host
			}
		}
	
	// Tootsearch用。URLやUriを使ってアカウントのインスタンス名を調べる
	fun findHost() : String? {
		val acctHost = this.acctHost
		if( acctHost != null ) return acctHost
		// TODO URLやURIから調べる
		return null
	}
	
	
	class List : ArrayList<TootAccount>()
	
	class Source(src : JSONObject) {
		// デフォルト公開範囲
		val privacy : String?
		
		// 添付画像をデフォルトでNSFWにする設定
		private val sensitive : Boolean
		
		// HTMLエンコードされていない、生のnote
		val note : String?
		
		init {
			this.privacy = Utils.optStringX(src, "privacy")
			this.note = Utils.optStringX(src, "note")
			// nullになることがあるが、falseと同じ扱いでよい
			this.sensitive = src.optBoolean("sensitive", false)
		}
	}
	
	fun decodeDisplayName(context : Context) : Spannable {
		
		// remove white spaces
		val sv = reWhitespace.matcher(display_name).replaceAll(" ")
		
		// decode emoji code
		return DecodeOptions().setProfileEmojis(profile_emojis).decodeEmoji(context, sv)
	}
	
	
	companion object {
		private val log = LogCategory("TootAccount")
		
		const val INVALID_ID = - 1L
		
		@Suppress("HasPlatformType")
		private val reWhitespace = Pattern.compile("[\\s\\t\\x0d\\x0a]+")
		
		@Suppress("HasPlatformType")
		val reAccountUrl = Pattern.compile("\\Ahttps://([A-Za-z0-9.-]+)/@([A-Za-z0-9_]+)(?:\\z|[?#])")
		
		fun parse(
			context : Context,
			account : LinkClickContext,
			src : JSONObject?,
		    serviceType :ServiceType = ServiceType.MASTODON
		) : TootAccount? {
			src ?: return null
			return try {
				TootAccount(context, account, src,serviceType)
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "parse failed.")
				null
				
			}
		}
		
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
		
		fun parseList(context : Context, account : LinkClickContext, array : JSONArray?) : List {
			val result = List()
			if(array != null) {
				val array_size = array.length()
				result.ensureCapacity(array_size)
				for(i in 0 until array_size) {
					val src = array.optJSONObject(i) ?: continue
					val item = parse(context, account, src)
					if(item != null) result.add(item)
				}
			}
			return result
		}
		
	}
	

}
