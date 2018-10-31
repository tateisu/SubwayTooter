package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.util.*
import org.json.JSONObject
import java.util.regex.Pattern

class TootInstance(parser : TootParser, src : JSONObject) {
	
	companion object {
		private val rePleroma = Pattern.compile("\\bpleroma\\b", Pattern.CASE_INSENSITIVE)
		
		val VERSION_1_6 = VersionString("1.6")
		val VERSION_2_4_0_rc1 = VersionString("2.4.0rc1")
		val VERSION_2_4_0_rc2 = VersionString("2.4.0rc2")
		val VERSION_2_4_0 = VersionString("2.4.0")
		val VERSION_2_4_1_rc1 = VersionString("2.4.1rc1")
		val VERSION_2_4_1 = VersionString("2.4.1")
		val VERSION_2_6_0 = VersionString("2.6.0")
		
	}
	
	// いつ取得したか(内部利用)
	var time_parse : Long = System.currentTimeMillis()
	
	//	URI of the current instance
	val uri : String?
	
	//	The instance's title
	val title : String?
	
	//	A description for the instance
	val description : String?
	
	// An email address which can be used to contact the instance administrator
	// misskeyの場合はURLらしい
	val email : String?
	
	val version : String?
	
	// バージョンの内部表現
	private val decoded_version : VersionString
	
	// インスタンスのサムネイル。推奨サイズ1200x630px。マストドン1.6.1以降。
	val thumbnail : String?
	
	// ユーザ数等の数字。マストドン1.6以降。
	val stats : Stats?
	
	// 言語のリスト。マストドン2.3.0以降
	val languages : ArrayList<String>?
	
	val contact_account : TootAccount?
	
	// (Pleroma only) トゥートの最大文字数
	val max_toot_chars : Int?
	
	// インスタンスの種別
	enum class InstanceType {
		
		Mastodon,
		Pleroma,
		Misskey
	}
	
	val instanceType : InstanceType
	
	// XXX: urls をパースしてない。使ってないから…
	
	init {
		if(parser.serviceType == ServiceType.MISSKEY){
			
			this.uri = parser.linkHelper.host
			this.title = parser.linkHelper.host
			this.description = "(Misskey instance)"
			val sv = src.optJSONObject("maintainer")?.parseString("url")
			this.email = when{
				sv?.startsWith("mailto:") ==true-> sv.substring(7)
				else-> sv
			}
			
			this.version = src.parseString("version")
			this.decoded_version = VersionString(version)
			this.stats = null
			this.thumbnail = null
			this.max_toot_chars = 1000
			this.instanceType = InstanceType.Misskey
			this.languages = ArrayList<String>().also{ it.add("?")}
			this.contact_account = null
			
		}else {
			this.uri = src.parseString("uri")
			this.title = src.parseString("title")
			this.description = src.parseString("description")
			
			val sv = src.parseString("email")
			this.email = when{
				sv?.startsWith("mailto:") ==true-> sv.substring(7)
				else-> sv
			}
			
			this.version = src.parseString("version")
			this.decoded_version = VersionString(version)
			this.stats = parseItem(::Stats, src.optJSONObject("stats"))
			this.thumbnail = src.parseString("thumbnail")
			
			this.max_toot_chars = src.parseInt("max_toot_chars")
			
			this.instanceType = when {
				rePleroma.matcher(version ?: "").find() -> InstanceType.Pleroma
				else -> InstanceType.Mastodon
			}
			
			languages = src.optJSONArray("languages")?.toStringArrayList()
			
			val parser2 = TootParser(
				parser.context,
				LinkHelper.newLinkHelper(uri ?: "?")
			)
			contact_account =
				parseItem(::TootAccount, parser2, src.optJSONObject("contact_account"))
		}
	}
	
	class Stats(src : JSONObject) {
		val user_count : Long
		val status_count : Long
		val domain_count : Long
		
		init {
			this.user_count = src.parseLong("user_count") ?: - 1L
			this.status_count = src.parseLong("status_count") ?: - 1L
			this.domain_count = src.parseLong("domain_count") ?: - 1L
		}
	}
	
	fun versionGE(check : VersionString) : Boolean {
		if(decoded_version.isEmpty || check.isEmpty) return false
		val i = VersionString.compare(decoded_version, check)
		return i >= 0
	}

}
