package jp.juggler.subwaytooter.api.entity

import org.json.JSONObject

import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils
import jp.juggler.subwaytooter.util.VersionString

class TootInstance(src : JSONObject) {
	
	// いつ取得したか(内部利用)
	var time_parse : Long = System.currentTimeMillis()
	
	//	URI of the current instance
	val uri : String?
	
	//	The instance's title
	val title : String?
	
	//	A description for the instance
	val description : String?
	
	//	An email address which can be used to contact the instance administrator
	val email : String?
	
	val version : String?
	
	// バージョンの内部表現
	val decoded_version : VersionString
	
	// インスタンスのサムネイル。推奨サイズ1200x630px。マストドン1.6.1以降。
	val thumbnail : String?
	
	// ユーザ数等の数字。マストドン1.6以降。
	val stats : Stats?
	
	// FIXME: urls をパースしてない。使ってないから…
	
	class Stats(src : JSONObject) {
		val user_count : Long
		val status_count : Long
		val domain_count : Long
		
		init {
			this.user_count = Utils.optLongX(src, "user_count", - 1L)
			this.status_count = Utils.optLongX(src, "status_count", - 1L)
			this.domain_count = Utils.optLongX(src, "domain_count", - 1L)
		}
	}
	
	init {
		this.uri = Utils.optStringX(src, "uri")
		this.title = Utils.optStringX(src, "title")
		this.description = Utils.optStringX(src, "description")
		this.email = Utils.optStringX(src, "email")
		this.version = Utils.optStringX(src, "version")
		this.decoded_version = VersionString(version)
		this.stats = parseStats(src.optJSONObject("stats"))
		this.thumbnail = Utils.optStringX(src, "thumbnail")
		
	}
	
	fun isEnoughVersion(check : VersionString) : Boolean {
		
		if(decoded_version.isEmpty || check.isEmpty) return false
		val i = VersionString.compare(decoded_version, check)
		return i >= 0
	}
	
	companion object {
		private val log = LogCategory("TootInstance")
		
		fun parse(src : JSONObject?) : TootInstance? {
			if(src == null) return null
			return try {
				TootInstance(src)
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "parse failed.")
				null
			}
		}
		
		private fun parseStats(src : JSONObject?) : Stats? {
			if(src == null) return null
			return try {
				Stats(src)
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "parseStats failed.")
				null
			}
		}
	}
}
