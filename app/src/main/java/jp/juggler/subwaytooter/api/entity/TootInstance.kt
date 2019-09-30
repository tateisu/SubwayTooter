package jp.juggler.subwaytooter.api.entity

import android.os.SystemClock
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.*
import jp.juggler.util.*
import org.json.JSONObject
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class TootInstance(parser : TootParser, src : JSONObject) {
	
	companion object {
		private val rePleroma = Pattern.compile("\\bpleroma\\b", Pattern.CASE_INSENSITIVE)
		private val rePixelfed = Pattern.compile("\\bpixelfed\\b", Pattern.CASE_INSENSITIVE)
		
		val VERSION_1_6 = VersionString("1.6")
		val VERSION_2_4_0_rc1 = VersionString("2.4.0rc1")
		val VERSION_2_4_0_rc2 = VersionString("2.4.0rc2")
		//		val VERSION_2_4_0 = VersionString("2.4.0")
		//		val VERSION_2_4_1_rc1 = VersionString("2.4.1rc1")
		val VERSION_2_4_1 = VersionString("2.4.1")
		val VERSION_2_6_0 = VersionString("2.6.0")
		val VERSION_2_7_0_rc1 = VersionString("2.7.0rc1")
		val VERSION_3_0_0_rc1 = VersionString("3.0.0rc1")
		
		val MISSKEY_VERSION_11 = VersionString("11.0")
		
		private const val EXPIRE = 600000L
		
		private val cache = HashMap<String, TootInstance>()
		
		// get from cache
		// no request, no expiration check
		fun getCached(host : String) : TootInstance? {
			synchronized(cache) {
				return cache[host.toLowerCase(Locale.JAPAN)]
			}
		}
		
		fun get(
			client : TootApiClient,
			account : SavedAccount,
			host : String? = null
		) : Pair<TootApiResult?, TootInstance?> {
			val tmpInstance = client.instance
			val tmpAccount = client.account
			try {
				synchronized(cache) {
					// re-use cached item.
					val now = SystemClock.elapsedRealtime()
					val item = cache[account.host.toLowerCase(Locale.JAPAN)]
					if(item != null && now - item.time_parse <= EXPIRE)
						return Pair(TootApiResult(), item)
					
					// get new information
					client.account = account
					if(host != null) client.instance = host
					val result = if(account.isMisskey) {
						val params = JSONObject().apply {
							put("dummy", 1)
						}
						client.request("/api/meta", params.toPostRequestBuilder())
					} else {
						client.request("/api/v1/instance")
					}
					
					val data = parseItem(
						::TootInstance,
						TootParser(client.context, account),
						result?.jsonObject
					)
					if(data != null) {
						cache[account.host.toLowerCase(Locale.JAPAN)] = data
					}
					return Pair(result, data)
				}
			} finally {
				client.account = tmpAccount
				client.instance = tmpInstance // must be last.
			}
		}
	}
	
	// いつ取得したか(内部利用)
	private var time_parse : Long = SystemClock.elapsedRealtime()
	
	val isExpire : Boolean
		get() = SystemClock.elapsedRealtime() - time_parse >= EXPIRE
	
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
		Misskey,
		Pixelfed,
		Pleroma
	}
	
	val instanceType : InstanceType
	
	// XXX: urls をパースしてない。使ってないから…
	
	init {
		if(parser.serviceType == ServiceType.MISSKEY) {
			
			this.uri = parser.accessHost
			this.title = parser.accessHost
			this.description = "(Misskey instance)"
			val sv = src.optJSONObject("maintainer")?.parseString("url")
			this.email = when {
				sv?.startsWith("mailto:") == true -> sv.substring(7)
				else -> sv
			}
			
			this.version = src.parseString("version")
			this.decoded_version = VersionString(version)
			this.stats = null
			this.thumbnail = null
			this.max_toot_chars = src.parseInt("maxNoteTextLength")
			this.instanceType = InstanceType.Misskey
			this.languages = src.optJSONArray("langs")?.toStringArrayList() ?: ArrayList()
			this.contact_account = null
			
		} else {
			this.uri = src.parseString("uri")
			this.title = src.parseString("title")
			this.description = src.parseString("description")
			
			val sv = src.parseString("email")
			this.email = when {
				sv?.startsWith("mailto:") == true -> sv.substring(7)
				else -> sv
			}
			
			this.version = src.parseString("version")
			this.decoded_version = VersionString(version)
			this.stats = parseItem(::Stats, src.optJSONObject("stats"))
			this.thumbnail = src.parseString("thumbnail")
			
			this.max_toot_chars = src.parseInt("max_toot_chars")
			
			this.instanceType = when {
				rePleroma.matcher(version ?: "").find() -> InstanceType.Pleroma
				rePixelfed.matcher(version ?: "").find() -> InstanceType.Pixelfed
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
	
	val misskeyVersion : Int
		get() = when {
			instanceType != InstanceType.Misskey -> 0
			versionGE(MISSKEY_VERSION_11) -> 11
			else -> 10
		}
}

//
//import android.os.SystemClock
//import jp.juggler.subwaytooter.api.TootApiClient
//import jp.juggler.subwaytooter.api.TootApiResult
//import jp.juggler.subwaytooter.api.TootParser
//import jp.juggler.subwaytooter.api.entity.TootInstance
//import jp.juggler.subwaytooter.api.entity.parseItem
//import jp.juggler.subwaytooter.table.SavedAccount
//import jp.juggler.util.toPostRequestBuilder
//import org.json.JSONObject
//import java.util.*
//import kotlin.collections.HashMap
//
//object InstanceInformationCache {
//
//
//	//		var instance =
//	//		if(instance == null) {
//	//			val r2 = getInstanceInformation(client)
//	//			instance = instance_tmp ?: return r2
//	//			account.instance = instance
//	//		}
//	//		var instance_tmp : TootInstance? = null
//	//		fun getInstanceInformation(client : TootApiClient) : TootApiResult? {
//	//
//	//			instance_tmp =
//	//			return result
//	//		}
//	//
//	//		client.instance = host
//	//		val result = if(isMisskey) {
//	//			client.getInstanceInformation()
//	//			client.request(
//	//				"/api/meta",
//	//				account.putMisskeyApiToken().toPostRequestBuilder()
//	//			)
//	//		} else {
//	//			client.request("/api/v1/instance")
//	//		}
//	//		newInfo =
//	//			TootParser(this@ActPost, account).instance(result?.jsonObject)
//	//		return Pair(null,null)
//	//	}
//
//	//private val refInstance = AtomicReference<TootInstance>(null)
//	//
//	//// DBには保存しない
//	//var instance : TootInstance?
//	//	get() {
//	//		val instance = refInstance.get()
//	//		return when {
//	//			instance == null -> null
//	//			System.currentTimeMillis() - instance.time_parse > INSTANCE_INFORMATION_EXPIRE -> null
//	//			else -> instance
//	//		}
//	//	}
//	//	set(instance) = refInstance.set(instance)
//
//}