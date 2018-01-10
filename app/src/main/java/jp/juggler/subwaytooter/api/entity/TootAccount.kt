package jp.juggler.subwaytooter.api.entity

import android.content.Context
import android.net.Uri
import android.text.Spannable
import jp.juggler.subwaytooter.table.SavedAccount

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
	
	val profile_emojis : NicoProfileEmoji.Map?
	
	val moved : TootAccount?
	
	init {
		var sv : String?

		// 絵文字データは先に読んでおく
		this.profile_emojis = NicoProfileEmoji.parseMap(src.optJSONArray("profile_emojis"))
		
		// 疑似アカウントにacctとusernameだけ
		this.url = Utils.optStringX(src,"url")
		this.username = src.notEmptyOrThrow( "username")
		
		//
		sv = Utils.optStringX(src, "display_name")
		this.display_name = if(sv?.isNotEmpty() == true) Utils.sanitizeBDI(sv) else username
		this.decoded_display_name = decodeDisplayName(context)
		
		//
		this.note = Utils.optStringX(src, "note")
		this.decoded_note = DecodeOptions()
			.setShort(true)
			.setDecodeEmoji(true)
			.setProfileEmojis(this.profile_emojis)
			.decodeHTML(context, accessInfo, this.note)
		
		this.source = parseSource(src.optJSONObject("source"))
		this.moved = src.optJSONObject("moved")?.let { TootAccount(context, accessInfo, it, serviceType) }
		this.locked = src.optBoolean("locked")
		
		when(serviceType) {
			ServiceType.MASTODON -> {
				
				val hostAccess = accessInfo.host
				
				this.id = Utils.optLongX(src, "id", INVALID_ID)
				
				this.acct = src.notEmptyOrThrow("acct")
				this.host = findHostFromUrl(acct,hostAccess,url)
					?: throw RuntimeException("can't find host from acct or url")
				
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
				// tootsearch のアカウントのIDはどのタンス上のものか分からないので役に立たない
				this.id = INVALID_ID // Utils.optLongX(src, "id", INVALID_ID)
				
				sv = src.notEmptyOrThrow("acct")
				this.host = findHostFromUrl(sv,null,url)
					?: throw RuntimeException("can't find host from acct or url")
				this.acct = this.username + "@" + this.host
				
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
				
				// MSPはLTLの情報しか持ってないのでacctは常にホスト名部分を持たない
				this.host = findHostFromUrl(null,null,url)
					?:throw RuntimeException("can't find host from url")
				this.acct = this.username + "@" + host
				
				this.followers_count = null
				this.following_count = null
				this.statuses_count = null
				
				this.created_at = null
				this.time_created_at = 0L
				
				val avatar = Utils.optStringX(src, "avatar")
				this.avatar = avatar
				this.avatar_static = avatar
				this.header = null
				this.header_static = null
				
			}
			
		}
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
			serviceType : ServiceType = ServiceType.MASTODON
		) : TootAccount? {
			src ?: return null
			return try {
				TootAccount(context, account, src, serviceType)
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
		
		// Tootsearch用。URLやUriを使ってアカウントのインスタンス名を調べる
		private fun findHostFromUrl(acct:String? ,accessHost:String?, url : String?) : String? {

			// acctから調べる
			if( acct != null ) {
				val pos = acct.indexOf('@')
				if(pos != - 1) {
					val host = acct.substring(pos + 1)
					if(host.isNotEmpty()) return host.toLowerCase()
				}
			}
			
			// accessHostから調べる
			if( accessHost != null ){
				return accessHost
			}

			// URLから調べる
			if( url != null ) {
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
