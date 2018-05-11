package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.util.*
import org.json.JSONObject
import java.util.regex.Pattern

class TootInstance(parser:TootParser,src : JSONObject) {
	
	companion object {
		val rePleroma = Pattern.compile("\\bpleroma\\b",Pattern.CASE_INSENSITIVE)
		
		val VERSION_2_4 = VersionString("2.4")
		
	}
	
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
	enum class InstanceType{
		Mastodon,
		Pleroma
	}
	val instanceType : InstanceType
	
	// XXX: urls をパースしてない。使ってないから…
	
	init {
		this.uri = src.parseString("uri")
		this.title = src.parseString("title")
		this.description = src.parseString("description")
		this.email = src.parseString("email")
		this.version = src.parseString("version")
		this.decoded_version = VersionString(version)
		this.stats = parseItem(::Stats, src.optJSONObject("stats"))
		this.thumbnail = src.parseString("thumbnail")
		
		this.max_toot_chars = src.parseInt("max_toot_chars")

		this.instanceType = when{
			rePleroma.matcher(version ?:"").find() -> InstanceType.Pleroma
			else->InstanceType.Mastodon
		}
		
		languages = src.optJSONArray("languages")?.toStringArrayList()
		
		val parser2 = TootParser(
			parser.context,
			object:LinkHelper {
				override val host :String
					get()= uri ?: "?"
			}
		)
		contact_account = parseItem(::TootAccount,parser2,src.optJSONObject("contact_account"))
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
	
	fun isEnoughVersion(check : VersionString) : Boolean {
		if(decoded_version.isEmpty || check.isEmpty) return false
		val i = VersionString.compare(decoded_version, check)
		return i >= 0
	}
	
}
