package jp.juggler.subwaytooter.api.entity

import android.os.SystemClock
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.subwaytooter.util.VersionString
import jp.juggler.util.JsonObject
import jp.juggler.util.asciiPattern
import jp.juggler.util.groupEx
import jp.juggler.util.toPostRequestBuilder
import okhttp3.Request
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.ArrayList
import kotlin.math.max

class TootInstance(parser : TootParser, src : JsonObject) {
	
	// いつ取得したか(内部利用)
	private var time_parse : Long = SystemClock.elapsedRealtime()
	
	val isExpire : Boolean
		get() = SystemClock.elapsedRealtime() - time_parse >= EXPIRE
	
	//	URI of the current instance
	val uri : String?
	
	//	The instance's title
	val title : String?
	
	//	A description for the instance
	// (HTML)
	// (Mastodon: 3.0.0より後のWebUIでは全く使われなくなる見込み。 https://github.com/tootsuite/mastodon/pull/12119)
	val description : String?
	
	// (Mastodon 3.0.0以降)
	// (HTML)
	val short_description : String?
	
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
	
	// (Mastodon 3.0.0)
	val approval_required : Boolean
	
	// (Mastodon 3.1.4)
	val invites_enabled : Boolean?
	
	// インスタンスの種別
	enum class InstanceType {
		
		Mastodon,
		Misskey,
		Pixelfed,
		Pleroma
	}
	
	val instanceType : InstanceType
	
	var feature_quote = false
	
	
	// XXX: urls をパースしてない。使ってないから…
	
	init {
		if(parser.serviceType == ServiceType.MISSKEY) {
			
			this.uri = parser.accessHost?.ascii
			this.title = parser.accessHost?.pretty
			val sv = src.jsonObject("maintainer")?.string("url")
			this.email = when {
				sv?.startsWith("mailto:") == true -> sv.substring(7)
				else -> sv
			}
			
			this.version = src.string("version")
			this.decoded_version = VersionString(version)
			this.stats = null
			this.thumbnail = null
			this.max_toot_chars = src.int("maxNoteTextLength")
			this.instanceType = InstanceType.Misskey
			this.languages = src.jsonArray("langs")?.stringArrayList() ?: ArrayList()
			this.contact_account = null
			
			this.description = src.string("description")
			this.short_description = null
			this.approval_required = false
			
			this.feature_quote = true
			
			this.invites_enabled = null
		} else {
			this.uri = src.string("uri")
			this.title = src.string("title")
			
			val sv = src.string("email")
			this.email = when {
				sv?.startsWith("mailto:") == true -> sv.substring(7)
				else -> sv
			}
			
			this.version = src.string("version")
			this.decoded_version = VersionString(version)
			this.stats = parseItem(::Stats, src.jsonObject("stats"))
			this.thumbnail = src.string("thumbnail")
			
			this.max_toot_chars = src.int("max_toot_chars")
			
			this.instanceType = when {
				rePleroma.matcher(version ?: "").find() -> InstanceType.Pleroma
				rePixelfed.matcher(version ?: "").find() -> InstanceType.Pixelfed
				else -> InstanceType.Mastodon
			}
			
			languages = src.jsonArray("languages")?.stringArrayList()
			
			val parser2 = TootParser(
				parser.context,
				LinkHelper.newLinkHelper(Host.parse(uri ?: "?"))
			)
			contact_account =
				parseItem(::TootAccount, parser2, src.jsonObject("contact_account"))
			
			this.description = src.string("description")
			this.short_description = src.string("short_description")
			this.approval_required = src.boolean("approval_required") ?: false
			
			this.feature_quote = src.boolean("feature_quote") ?: false
			
			this.invites_enabled = src.boolean("invites_enabled")
			
		}
	}
	
	class Stats(src : JsonObject) {
		val user_count : Long
		val status_count : Long
		val domain_count : Long
		
		init {
			this.user_count = src.long("user_count") ?: - 1L
			this.status_count = src.long("status_count") ?: - 1L
			this.domain_count = src.long("domain_count") ?: - 1L
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
	
	companion object {
		private val rePleroma = """\bpleroma\b""".asciiPattern(Pattern.CASE_INSENSITIVE)
		private val rePixelfed = """\bpixelfed\b""".asciiPattern( Pattern.CASE_INSENSITIVE)
		
		val VERSION_1_6 = VersionString("1.6")
		val VERSION_2_4_0_rc1 = VersionString("2.4.0rc1")
		val VERSION_2_4_0_rc2 = VersionString("2.4.0rc2")
		//		val VERSION_2_4_0 = VersionString("2.4.0")
		//		val VERSION_2_4_1_rc1 = VersionString("2.4.1rc1")
		val VERSION_2_4_1 = VersionString("2.4.1")
		val VERSION_2_6_0 = VersionString("2.6.0")
		val VERSION_2_7_0_rc1 = VersionString("2.7.0rc1")
		val VERSION_3_0_0_rc1 = VersionString("3.0.0rc1")
		val VERSION_3_1_0_rc1 = VersionString("3.1.0rc1")
		val VERSION_3_1_3 = VersionString("3.1.3")
		val VERSION_3_2 = VersionString("3.1.4") // TODO まだ出てないがE2EEの権限に必要
		
		val MISSKEY_VERSION_11 = VersionString("11.0")
		val MISSKEY_VERSION_12 = VersionString("12.0")
		
		private val reDigits = """(\d+)""".asciiPattern()
		
		private const val EXPIRE = (1000 * 3600).toLong()
		
		const val DESCRIPTION_DEFAULT = "(no description)"
		
		// 引数はtoken_infoかTootInstanceのパース前のいずれか
		fun parseMisskeyVersion(token_info : JsonObject) : Int {
			return when(val o = token_info[TootApiClient.KEY_MISSKEY_VERSION]) {
				is Int -> o
				is Boolean -> if(o) 10 else 0
				else -> 0
			}
		}
		
		// 疑似アカウントの追加時に、インスタンスの検証を行う
		private fun TootApiClient.getInstanceInformationMastodon() : TootApiResult? {
			val result = TootApiResult.makeWithCaption(instance?.pretty)
			if(result.error != null) return result
			
			if(sendRequest(result) {
					Request.Builder().url("https://${instance?.ascii}/api/v1/instance").build()
				}
			) {
				parseJson(result) ?: return null
			}
			
			// misskeyの事は忘れて本来のエラー情報を返す
			return result
		}
		
		// 疑似アカウントの追加時に、インスタンスの検証を行う
		private fun TootApiClient.getInstanceInformationMisskey() : TootApiResult? {
			val result = TootApiResult.makeWithCaption(instance?.pretty)
			if(result.error != null) return result
			if(sendRequest(result) {
					JsonObject().apply {
						put("dummy", 1)
					}
						.toPostRequestBuilder()
						.url("https://${instance?.ascii}/api/meta")
						.build()
				}) {
				parseJson(result) ?: return null
				
				result.jsonObject?.apply {
					val m = reDigits.matcher(string("version") ?: "")
					if(m.find()) {
						put(TootApiClient.KEY_MISSKEY_VERSION, max(1, m.groupEx(1) !!.toInt()))
					}
				}
			}
			return result
		}
		
		// 疑似アカウントの追加時に、インスタンスの検証を行う
		private fun TootApiClient.getInstanceInformation() : TootApiResult? {
			// misskeyのインスタンス情報を読めたら、それはmisskeyのインスタンス
			val r2 = getInstanceInformationMisskey() ?: return null
			if(r2.jsonObject != null) return r2
			
			// マストドンのインスタンス情報を読めたら、それはマストドンのインスタンス
			val r1 = getInstanceInformationMastodon() ?: return null
			if(r1.jsonObject != null) return r1
			
			return r1 // 通信エラーの表示ならr1でもr2でも構わないはず
		}
		
		// インスタンス情報のキャッシュ。同期オブジェクトを兼ねる
		class CacheEntry(var data : TootInstance? = null)
		
		private val cache = HashMap<String, CacheEntry>()
		
		private fun getCacheEntry(hostLower : String) : CacheEntry =
			synchronized(cache) {
				var item = cache[hostLower]
				if(item == null) {
					item = CacheEntry()
					cache[hostLower] = item
				}
				item
			}
		
		// get from cache
		// no request, no expiration check
		fun getCached(host : String) = getCacheEntry(host.toLowerCase(Locale.JAPAN)).data
		
		fun get(
			client : TootApiClient,
			host : String,
			account : SavedAccount? = client.account?.takeIf { it.matchHost(host)} ,
			allowPixelfed : Boolean = false,
			forceUpdate : Boolean = false
		) : Pair<TootInstance?, TootApiResult?> = get(client,Host.parse(host),account,allowPixelfed,forceUpdate)
		
		fun get(
			client : TootApiClient,
			host : Host? = client.instance,
			account : SavedAccount? = if(host == client.instance) client.account else null,
			allowPixelfed : Boolean = false,
			forceUpdate : Boolean = false
		) : Pair<TootInstance?, TootApiResult?> {
			
			val tmpInstance = client.instance
			val tmpAccount = client.account
			try {
				client.account = account
				if(host != null) client.instance = host
				val instanceName = client.instance!!.pretty
				
				// ホスト名ごとに用意したオブジェクトで同期する
				val cacheEntry = getCacheEntry(instanceName)
				synchronized(cacheEntry) {
					
					var item : TootInstance?
					
					if(! forceUpdate) {
						// re-use cached item.
						val now = SystemClock.elapsedRealtime()
						item = cacheEntry.data
						if(item != null && now - item.time_parse <= EXPIRE) {
							
							if(item.instanceType == InstanceType.Pixelfed &&
								! Pref.bpEnablePixelfed(App1.pref) &&
								! allowPixelfed
							) {
								return Pair(
									null,
									TootApiResult("currently Pixelfed instance is not supported.")
								)
							}
							
							return Pair(item, TootApiResult())
						}
					}
					
					// get new information
					val result = if(account != null) {
						if(account.isMisskey) {
							val params = JsonObject().apply {
								put("dummy", 1)
							}
							client.request("/api/meta", params.toPostRequestBuilder())
						} else {
							client.request("/api/v1/instance")
						}
					} else {
						client.getInstanceInformation()
					}
					
					val json = result?.jsonObject ?: return Pair(null, result)
					
					item = parseItem(
						::TootInstance,
						if(account != null) {
							TootParser(client.context, account)
						} else {
							TootParser(
								client.context,
								LinkHelper.newLinkHelper(
									Host.parse(instanceName),
									misskeyVersion = parseMisskeyVersion(json)
								)
							)
						},
						json
					)
					
					return when {
						item == null ->
							Pair(
								null,
								result.setError("instance information parse error.")
							)
						
						item.instanceType == InstanceType.Pixelfed &&
							! Pref.bpEnablePixelfed(App1.pref) &&
							! allowPixelfed ->
							Pair(
								null,
								result.setError("currently Pixelfed instance is not supported.")
							)
						
						else -> {
							cacheEntry.data = item
							Pair(item, result)
						}
					}
				}
			} finally {
				client.account = tmpAccount
				client.instance = tmpInstance // must be last.
			}
		}
		
	}
}
