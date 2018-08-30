package jp.juggler.subwaytooter.api

import android.content.Context
import android.content.SharedPreferences
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.api.entity.TootAccount

import org.json.JSONException
import org.json.JSONObject

import jp.juggler.subwaytooter.table.ClientInfo
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.util.*
import okhttp3.*
import org.json.JSONArray
import java.util.*
import java.util.regex.Pattern

class TootApiClient(
	internal val context : Context,
	internal val httpClient : SimpleHttpClient = SimpleHttpClientImpl(
		context,
		App1.ok_http_client,
		App1.ok_http_client2
	),
	internal val callback : TootApiCallback
) {
	
	// 認証に関する設定を保存する
	internal val pref : SharedPreferences
	
	// インスタンスのホスト名
	var instance : String? = null
	
	// アカウントがある場合に使用する
	var account : SavedAccount? = null
		set(value) {
			instance = value?.host
			field = value
		}
	
	var currentCallCallback : CurrentCallCallback?
		get() = httpClient.currentCallCallback
		set(value) {
			httpClient.currentCallCallback = value
		}
	
	init {
		pref = Pref.pref(context)
	}
	
	companion object {
		private val log = LogCategory("TootApiClient")
		
		val MEDIA_TYPE_FORM_URL_ENCODED = MediaType.parse("application/x-www-form-urlencoded")
		val MEDIA_TYPE_JSON = MediaType.parse("application/json;charset=UTF-8")
		
		private const val DEFAULT_CLIENT_NAME = "SubwayTooter"
		internal const val KEY_CLIENT_CREDENTIAL = "SubwayTooterClientCredential"
		internal const val KEY_CLIENT_SCOPE = "SubwayTooterClientScope"
		
		private const val KEY_AUTH_VERSION = "SubwayTooterAuthVersion"
		private const val AUTH_VERSION = 3
		private const val REDIRECT_URL = "subwaytooter://oauth/"
		
		const val KEY_IS_MISSKEY = "isMisskey"
		const val KEY_MISSKEY_APP_SECRET = "secret"
		const val KEY_API_KEY_MISSKEY = "apiKeyMisskey"
		
		//		// APIからsecretを得られないバグがあるので定数を渡す
		//		const val appSecretError =
		//			"This Misskey instance Currently Misskey does not allow client registration from API, please tell me notify instance name that you want login via Subway Tooter."
		//		val testAppSecretMap = mapOf(
		//			Pair("misskey.xyz", "NGiWNZFP37WiAee3SGcVe8eSiDyLbbWf")
		//			, Pair("misskey.jp", "GO45N7JgeEWtlNUS4xRcOFY56JMjUTZk")
		//			, Pair("msky.cafe", "lvU12i7CXAB5xiqkABwzyJRzdAqhf0k3")
		//			, Pair("misskey.m544.net", "SLcaqff0Puymh4Fl30JCc09i6uumwJ4t")
		//		)
		
		private const val NO_INFORMATION = "(no information)"
		
		private val reStartJsonArray = Pattern.compile("\\A\\s*\\[")
		private val reStartJsonObject = Pattern.compile("\\A\\s*\\{")
		private val reWhiteSpace = Pattern.compile("\\s+")
		
		private const val mspTokenUrl = "http://mastodonsearch.jp/api/v1.0.1/utoken"
		private const val mspSearchUrl = "http://mastodonsearch.jp/api/v1.0.1/cross"
		private const val mspApiKey = "e53de7f66130208f62d1808672bf6320523dcd0873dc69bc"
		
		fun getMspMaxId(array : JSONArray, old : String?) : String? {
			// max_id の更新
			val size = array.length()
			if(size > 0) {
				val item = array.optJSONObject(size - 1)
				if(item != null) {
					val sv = item.optString("msp_id")
					if(sv?.isNotEmpty() == true) return sv
				}
			}
			// MSPでは終端は分からず、何度もリトライする
			return old
		}
		
		fun getTootsearchHits(root : JSONObject) : JSONArray? {
			val hits = root.optJSONObject("hits")
			return hits?.optJSONArray("hits")
		}
		
		// returns the number for "from" parameter of next page.
		// returns null if no more next page.
		fun getTootsearchMaxId(root : JSONObject, old : Long?) : Long? {
			val size = getTootsearchHits(root)?.length() ?: 0
			return when {
				size <= 0 -> null
				else -> (old ?: 0L) + size.toLong()
			}
		}
		
		val DEFAULT_JSON_ERROR_PARSER = { json : JSONObject -> json.parseString("error") }
		
		internal fun simplifyErrorHtml(
			response : Response,
			sv : String,
			jsonErrorParser : (json : JSONObject) -> String? = DEFAULT_JSON_ERROR_PARSER
		) : String {
			
			// JSONObjectとして解釈できるならエラーメッセージを検出する
			try {
				val error_message = jsonErrorParser(sv.toJsonObject())
				if(error_message?.isNotEmpty() == true) {
					return error_message
				}
			} catch(ex : Throwable) {
				log.e(ex, "response body is not JSON or missing 'error' attribute.")
			}
			
			// HTMLならタグの除去を試みる
			val ct = response.body()?.contentType()
			if(ct?.subtype() == "html") {
				val decoded = DecodeOptions().decodeHTML(sv).toString()
				
				return reWhiteSpace.matcher(decoded).replaceAll(" ").trim()
			}
			
			// XXX: Amazon S3 が403を返した場合にcontent-typeが?/xmlでserverがAmazonならXMLをパースしてエラーを整形することもできるが、多分必要ない
			
			return reWhiteSpace.matcher(sv).replaceAll(" ").trim()
		}
		
		fun formatResponse(
			response : Response,
			caption : String,
			bodyString : String? = null,
			jsonErrorParser : (json : JSONObject) -> String? = DEFAULT_JSON_ERROR_PARSER
		) : String {
			val url = response.request()?.url()
			if(url?.toString()?.contains("misskey") == true) {
				log.d("Misskey response error: url=$url")
			}
			val sb = StringBuilder()
			try {
				// body は既に読み終わっているか、そうでなければこれから読む
				if(bodyString != null) {
					sb.append(simplifyErrorHtml(response, bodyString, jsonErrorParser))
				} else {
					try {
						val string = response.body()?.string()
						if(string != null) {
							sb.append(simplifyErrorHtml(response, string, jsonErrorParser))
						}
					} catch(ex : Throwable) {
						log.e(ex, "missing response body.")
						sb.append("(missing response body)")
					}
				}
				
				if(sb.isNotEmpty()) sb.append(' ')
				sb.append("(HTTP ").append(Integer.toString(response.code()))
				
				val message = response.message()
				if(message != null && message.isNotEmpty()) {
					sb.append(' ').append(message)
				}
				sb.append(")")
				
				if(caption.isNotEmpty()) {
					sb.append(' ').append(caption)
				}
				
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			
			return sb.toString().replace("\n+".toRegex(), "\n")
		}
		
		fun getScopeString(ti : TootInstance) = when {
			ti.versionGE(TootInstance.VERSION_2_4_0_rc1) -> "read+write+follow+push"
			else -> "read+write+follow"
		}
		
		fun getScopeArrayMisskey(@Suppress("UNUSED_PARAMETER") ti : TootInstance) =
			JSONArray().apply {
				
				put("account-read")
				put("account-write")
				
				put("note-read")
				put("note-write")
				
				put("reaction-read")
				put("reaction-write")
				
				put("following-read") // フォロリク申請一覧で使われていた
				put("following-write")
				
				put("drive-read")
				put("drive-write")
				
				put("notification-read")
				put("notification-write")
				
				put("favorite-read")
				put("favorites-read")
				put("favorite-write")
				
				put("account/read")
				put("account/write")
				
				put("messaging-read")
				put("messaging-write")
				
				put("vote-read")
				put("vote-write")
				
				// https://github.com/syuilo/misskey/issues/2341
			}
		
		private fun encodeScopeArray(scope_array : JSONArray?) : String? {
			scope_array ?: return null
			val list = scope_array.toStringArrayList()
			list.sort()
			return list.joinToString(",")
		}
		
		private fun compareScopeArray(a : JSONArray, b : JSONArray?) : Boolean {
			return encodeScopeArray(a) == encodeScopeArray(b)
		}
	}
	
	@Suppress("unused")
	internal val isApiCancelled : Boolean
		get() = callback.isApiCancelled
	
	fun publishApiProgress(s : String) {
		callback.publishApiProgress(s)
	}
	
	fun publishApiProgressRatio(value : Int, max : Int) {
		callback.publishApiProgressRatio(value, max)
	}
	
	//////////////////////////////////////////////////////////////////////
	// ユーティリティ
	
	// リクエストをokHttpに渡してレスポンスを取得する
	internal inline fun sendRequest(
		result : TootApiResult,
		progressPath : String? = null,
		cached : Boolean = false,
		block : () -> Request
	) : Boolean {
		return try {
			result.response = null
			result.bodyString = null
			result.data = null
			
			val request = block()
			
			callback.publishApiProgress(
				context.getString(
					R.string.request_api
					, request.method()
					, progressPath ?: request.url().encodedPath()
				)
			)
			
			result.response = httpClient.getResponse(request, cached = cached)
			
			null == result.error
			
		} catch(ex : Throwable) {
			result.setError(
				"${result.caption}: ${ex.withCaption(
					context.resources,
					R.string.network_error
				)}"
			)
			false
		}
	}
	
	// レスポンスがエラーかボディがカラならエラー状態を設定する
	// 例外を出すかも
	internal fun readBodyString(
		result : TootApiResult,
		progressPath : String? = null,
		jsonErrorParser : (json : JSONObject) -> String? = DEFAULT_JSON_ERROR_PARSER
	) : String? {
		
		if(isApiCancelled) return null
		
		val response = result.response !!
		
		val request = response.request()
		if(request != null) {
			publishApiProgress(
				context.getString(
					R.string.reading_api,
					request.method(),
					progressPath ?: result.caption
				)
			)
		}
		
		val bodyString = response.body()?.string()
		if(isApiCancelled) return null
		
		// Misskey の /api/notes/favorites/create は 204(no content)を返す。ボディはカラになる。
		if(bodyString?.isEmpty() != false && response.code() in 200 until 300) {
			result.bodyString = ""
			return ""
		}
		
		if(! response.isSuccessful || bodyString?.isEmpty() != false) {
			
			result.error = TootApiClient.formatResponse(
				response,
				result.caption,
				if(bodyString?.isNotEmpty() == true) bodyString else NO_INFORMATION,
				jsonErrorParser
			)
		}
		
		return if(result.error != null) {
			null
		} else {
			publishApiProgress(context.getString(R.string.parsing_response))
			result.bodyString = bodyString
			bodyString
		}
	}
	
	// レスポンスがエラーかボディがカラならエラー状態を設定する
	// 例外を出すかも
	private fun readBodyBytes(
		result : TootApiResult,
		progressPath : String? = null,
		jsonErrorParser : (json : JSONObject) -> String? = DEFAULT_JSON_ERROR_PARSER
	) : ByteArray? {
		
		if(isApiCancelled) return null
		
		val response = result.response !!
		
		val request = response.request()
		if(request != null) {
			publishApiProgress(
				context.getString(
					R.string.reading_api,
					request.method(),
					progressPath ?: result.caption
				)
			)
		}
		
		val bodyBytes = response.body()?.bytes()
		if(isApiCancelled) return null
		
		if(! response.isSuccessful || bodyBytes?.isEmpty() != false) {
			
			result.error = TootApiClient.formatResponse(
				response,
				result.caption,
				if(bodyBytes?.isNotEmpty() == true) bodyBytes.decodeUTF8() else NO_INFORMATION,
				jsonErrorParser
			)
		}
		
		return if(result.error != null) {
			null
		} else {
			result.bodyString = "(binary data)"
			result.data = bodyBytes
			bodyBytes
		}
	}
	
	private fun parseBytes(
		result : TootApiResult,
		progressPath : String? = null,
		jsonErrorParser : (json : JSONObject) -> String? = DEFAULT_JSON_ERROR_PARSER
	) : TootApiResult? {
		
		val response = result.response !! // nullにならないはず
		
		try {
			readBodyBytes(result, progressPath, jsonErrorParser)
				?: return if(isApiCancelled) null else result
			
		} catch(ex : Throwable) {
			log.trace(ex)
			result.error =
				formatResponse(response, result.caption, result.bodyString ?: NO_INFORMATION)
		}
		return result
	}
	
	internal fun parseString(
		result : TootApiResult,
		progressPath : String? = null,
		jsonErrorParser : (json : JSONObject) -> String? = DEFAULT_JSON_ERROR_PARSER
	) : TootApiResult? {
		
		val response = result.response !! // nullにならないはず
		
		try {
			val bodyString = readBodyString(result, progressPath, jsonErrorParser)
				?: return if(isApiCancelled) null else result
			
			result.data = bodyString
			
		} catch(ex : Throwable) {
			log.trace(ex)
			result.error =
				formatResponse(response, result.caption, result.bodyString ?: NO_INFORMATION)
		}
		return result
	}
	
	// レスポンスからJSONデータを読む
	internal fun parseJson(
		result : TootApiResult,
		progressPath : String? = null,
		jsonErrorParser : (json : JSONObject) -> String? = DEFAULT_JSON_ERROR_PARSER
	) : TootApiResult? // 引数に指定したresultそのものか、キャンセルされたらnull
	{
		val response = result.response !! // nullにならないはず
		
		try {
			var bodyString = readBodyString(result, progressPath, jsonErrorParser)
				?: return if(isApiCancelled) null else result
			
			if(bodyString.isEmpty()) {
				
				// 204 no content は 空オブジェクトと解釈する
				result.data = JSONObject()
				
			} else if(reStartJsonArray.matcher(bodyString).find()) {
				result.data = bodyString.toJsonArray()
				
			} else if(reStartJsonObject.matcher(bodyString).find()) {
				val json = bodyString.toJsonObject()
				val error_message = jsonErrorParser(json)
				if(error_message != null) {
					result.error = error_message
				} else {
					result.data = json
				}
			} else {
				// HTMLならタグを除去する
				val ct = response.body()?.contentType()
				if(ct?.subtype() == "html") {
					val decoded = DecodeOptions().decodeHTML(bodyString).toString()
					bodyString = decoded
				}
				
				val sb = StringBuilder()
					.append(context.getString(R.string.response_not_json))
					.append(' ')
					.append(bodyString)
				
				if(sb.isNotEmpty()) sb.append(' ')
				sb.append("(HTTP ").append(Integer.toString(response.code()))
				
				val message = response.message()
				if(message != null && message.isNotEmpty()) {
					sb.append(' ').append(message)
				}
				sb.append(")")
				
				val url = response.request()?.url()?.toString()
				if(url?.isNotEmpty() == true) {
					sb.append(' ').append(url)
				}
				
				result.error = sb.toString()
			}
			
		} catch(ex : Throwable) {
			log.trace(ex)
			result.error =
				formatResponse(response, result.caption, result.bodyString ?: NO_INFORMATION)
		}
		return result
		
	}
	
	//////////////////////////////////////////////////////////////////////
	
	fun request(
		path : String,
		request_builder : Request.Builder = Request.Builder()
	) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(instance)
		if(result.error != null) return result
		
		val account = this.account ?: return result.setError("account is null")
		
		try {
			if(! sendRequest(result) {
					
					log.d("request: $path")
					
					request_builder.url("https://$instance$path")
					
					val access_token = account.getAccessToken()
					if(access_token?.isNotEmpty() == true) {
						request_builder.header("Authorization", "Bearer $access_token")
					}
					
					request_builder.build()
					
				}) return result
			
			return parseJson(result)
		} finally {
			val error = result.error
			if(error != null) log.d("error: $error")
		}
	}
	
	//////////////////////////////////////////////////////////////////////
	// misskey authentication
	
	// 疑似アカウントの追加時に、インスタンスの検証を行う
	private fun getInstanceInformationMisskey() : TootApiResult? {
		val result = TootApiResult.makeWithCaption(instance)
		if(result.error != null) return result
		if(sendRequest(result) {
				JSONObject().apply {
					put("dummy", 1)
				}
					.toPostRequestBuilder()
					.url("https://$instance/api/meta")
					.build()
			}) {
			parseJson(result) ?: return null
			result.jsonObject?.put(KEY_IS_MISSKEY, true)
		}
		return result
	}
	
	// インスタンス情報を取得する
	internal fun parseInstanceInformation(result : TootApiResult?) : TootApiResult? {
		if(result != null) {
			val json = result.jsonObject
			if(json != null) {
				val parser = TootParser(
					context,
					LinkHelper.newLinkHelper(instance, isMisskey = json.optBoolean(KEY_IS_MISSKEY))
				)
				val ti = parser.instance(json)
				if(ti != null) {
					result.data = ti
				} else {
					result.setError("can't parse data in instance information.")
				}
			}
		}
		return result
	}
	
	private fun getAppInfoMisskey(appId : String?) : TootApiResult? {
		appId ?: return TootApiResult("missing app id")
		val result = TootApiResult.makeWithCaption(instance)
		if(result.error != null) return result
		if(sendRequest(result) {
				JSONObject().apply {
					put("appId", appId)
				}
					.toPostRequestBuilder()
					.url("https://$instance/api/app/show")
					.build()
			}) {
			parseJson(result) ?: return null
			result.jsonObject?.put(KEY_IS_MISSKEY, true)
		}
		return result
	}
	
	private fun prepareBrowserUrlMisskey(appSecret : String) : String? {
		
		val result = TootApiResult.makeWithCaption(instance)
		
		if(result.error != null) {
			showToast(context, false, result.error)
			return null
		}
		
		
		
		if(! sendRequest(result) {
				JSONObject().apply {
					put("appSecret", appSecret)
				}
					.toPostRequestBuilder()
					.url("https://$instance/api/auth/session/generate")
					.build()
			}
		) {
			val error = result.error
			if(error != null) {
				showToast(context, false, error)
				return null
			}
			return null
		}
		
		parseJson(result) ?: return null
		
		val jsonObject = result.jsonObject
		if(jsonObject == null) {
			showToast(context, false, result.error)
			return null
		}
		// {"token":"0ba88e2d-4b7d-4599-8d90-dc341a005637","url":"https://misskey.xyz/auth/0ba88e2d-4b7d-4599-8d90-dc341a005637"}
		
		// ブラウザで開くURL
		val url = jsonObject.parseString("url")
		if(url?.isEmpty() != false) {
			showToast(context, false, "missing 'url' in auth session response.")
			return null
		}
		
		val e = PrefDevice.prefDevice(context)
			.edit()
			.putString(PrefDevice.LAST_AUTH_INSTANCE, instance)
			.putString(PrefDevice.LAST_AUTH_SECRET, appSecret)
		
		val account = this.account
		if(account != null) {
			e.putLong(PrefDevice.LAST_AUTH_DB_ID, account.db_id)
		} else {
			e.remove(PrefDevice.LAST_AUTH_DB_ID)
		}
		
		e.apply()
		
		return url
	}
	
	private fun registerClientMisskey(
		scope_array : JSONArray,
		client_name : String
	) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(instance)
		if(result.error != null) return result
		if(sendRequest(result) {
				JSONObject().apply {
					put("nameId", "SubwayTooter")
					put("name", client_name)
					put("description", "Android app for federated SNS")
					put("callbackUrl", "subwaytooter://misskey/auth_callback")
					put("permission", scope_array)
				}
					.toPostRequestBuilder()
					.url("https://$instance/api/app/create")
					.build()
			}) {
			parseJson(result) ?: return null
		}
		return result
	}
	
	private fun authentication1Misskey(clientNameArg : String, ti : TootInstance) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(this.instance)
		if(result.error != null) return result
		val instance = result.caption // same to instance
		
		// クライアントIDがアプリ上に保存されているか？
		val client_name = if(clientNameArg.isNotEmpty()) clientNameArg else DEFAULT_CLIENT_NAME
		val client_info = ClientInfo.load(instance, client_name)
		
		// スコープ一覧を取得する
		val scope_array = getScopeArrayMisskey(ti)
		
		if(client_info != null && client_info.optBoolean(KEY_IS_MISSKEY)) {
			val appSecret = client_info.parseString(KEY_MISSKEY_APP_SECRET)
			
			val r2 = getAppInfoMisskey(client_info.parseString("id"))
			val tmpClientInfo = r2?.jsonObject
			// tmpClientInfo はsecretを含まないので保存してはいけない
			if(tmpClientInfo != null // アプリが登録済みで
				&& client_name == tmpClientInfo.parseString("name") // クライアント名が一致してて
				&& compareScopeArray(
					scope_array,
					tmpClientInfo.optJSONArray("permission")
				) // パーミッションが同じ
				&& appSecret?.isNotEmpty() == true
			) {
				// クライアント情報を再利用する
				result.data = prepareBrowserUrlMisskey(appSecret)
				return result
			} else {
				// XXX appSecretを使ってクライアント情報を削除できるようにするべきだが、該当するAPIが存在しない
			}
		}
		
		val r2 = registerClientMisskey(scope_array, client_name)
		val jsonObject = r2?.jsonObject ?: return r2
		
		val appSecret = jsonObject.parseString(KEY_MISSKEY_APP_SECRET)
		if(appSecret?.isEmpty() != false) {
			showToast(context, true, context.getString(R.string.cant_get_misskey_app_secret))
			return null
		}
		//		{
		//			"createdAt": "2018-08-19T00:43:10.105Z",
		//			"userId": null,
		//			"name": "Via芸",
		//			"nameId": "test1",
		//			"description": "test1",
		//			"permission": [
		//			"account-read",
		//			"account-write",
		//			"note-write",
		//			"reaction-write",
		//			"following-write",
		//			"drive-read",
		//			"drive-write",
		//			"notification-read",
		//			"notification-write"
		//			],
		//			"callbackUrl": "test1://test1/auth_callback",
		//			"id": "5b78bd1ea0db0527f25815c3",
		//			"iconUrl": "https://misskey.xyz/files/app-default.jpg"
		//		}
		
		// 2018/8/19現在、/api/app/create のレスポンスにsecretが含まれないので認証に使えない
		// https://github.com/syuilo/misskey/issues/2343
		
		jsonObject.put(KEY_IS_MISSKEY, true)
		jsonObject.put(KEY_AUTH_VERSION, AUTH_VERSION)
		ClientInfo.save(instance, client_name, jsonObject.toString())
		result.data = prepareBrowserUrlMisskey(appSecret)
		
		return result
	}
	
	// oAuth2認証の続きを行う
	fun authentication2Misskey(clientNameArg : String, token : String) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(instance)
		if(result.error != null) return result
		val instance = result.caption // same to instance
		val client_name = if(clientNameArg.isNotEmpty()) clientNameArg else DEFAULT_CLIENT_NAME
		
		@Suppress("UNUSED_VARIABLE")
		val client_info = ClientInfo.load(instance, client_name)
			?: return result.setError("missing client id")
		
		val appSecret = client_info.parseString(KEY_MISSKEY_APP_SECRET)
		if(appSecret?.isEmpty() != false) {
			return result.setError(context.getString(R.string.cant_get_misskey_app_secret))
		}
		
		if(! sendRequest(result) {
				JSONObject().apply {
					put("appSecret", appSecret)
					put("token", token)
				}
					.toPostRequestBuilder()
					.url("https://$instance/api/auth/session/userkey")
					.build()
			}
		) {
			return result
		}
		
		parseJson(result) ?: return null
		
		val token_info = result.jsonObject ?: return result
		
		// {"accessToken":"...","user":{…}}
		
		val access_token = token_info.parseString("accessToken")
		if(access_token?.isEmpty() != false) {
			return result.setError("missing accessToken in the response.")
		}
		
		val user = token_info.optJSONObject("user")
			?: result.setError("missing user in the response.")
		token_info.remove("user")
		
		val apiKey = "$access_token$appSecret".encodeUTF8().digestSHA256().encodeHexLower()
		
		// ユーザ情報を読めたならtokenInfoを保存する
		token_info.put(KEY_IS_MISSKEY, true)
		token_info.put(KEY_AUTH_VERSION, AUTH_VERSION)
		token_info.put(KEY_API_KEY_MISSKEY, apiKey)
		
		// tokenInfoとユーザ情報の入ったresultを返す
		result.tokenInfo = token_info
		result.data = user
		return result
	}
	
	//////////////////////////////////////////////////////////////////////
	
	// 疑似アカウントの追加時に、インスタンスの検証を行う
	private fun getInstanceInformationMastodon() : TootApiResult? {
		val result = TootApiResult.makeWithCaption(instance)
		if(result.error != null) return result
		
		if(sendRequest(result) {
				Request.Builder().url("https://$instance/api/v1/instance").build()
			}
		) {
			parseJson(result) ?: return null
		}
		
		// misskeyの事は忘れて本来のエラー情報を返す
		return result
	}
	
	// クライアントをタンスに登録
	fun registerClient(scope_string : String, clientName : String) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(this.instance)
		if(result.error != null) return result
		val instance = result.caption // same to instance
		// OAuth2 クライアント登録
		if(! sendRequest(result) {
				Request.Builder()
					.url("https://$instance/api/v1/apps")
					.post(
						RequestBody.create(
							MEDIA_TYPE_FORM_URL_ENCODED,
							"client_name=" + clientName.encodePercent()
								+ "&redirect_uris=" + REDIRECT_URL.encodePercent()
								+ "&scopes=$scope_string"
						)
					)
					.build()
			}) return result
		
		return parseJson(result)
	}
	
	// クライアントアプリの登録を確認するためのトークンを生成する
	// oAuth2 Client Credentials の取得
	// https://github.com/doorkeeper-gem/doorkeeper/wiki/Client-Credentials-flow
	// このトークンはAPIを呼び出すたびに新しく生成される…
	internal fun getClientCredential(client_info : JSONObject) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(this.instance)
		if(result.error != null) return result
		
		if(! sendRequest(result) {
				
				val client_id = client_info.parseString("client_id")
					?: return result.setError("missing client_id")
				
				val client_secret = client_info.parseString("client_secret")
					?: return result.setError("missing client_secret")
				
				Request.Builder()
					.url("https://$instance/oauth/token")
					.post(
						RequestBody.create(
							MEDIA_TYPE_FORM_URL_ENCODED,
							"grant_type=client_credentials"
								+ "&client_id=" + client_id.encodePercent()
								+ "&client_secret=" + client_secret.encodePercent()
						)
					)
					.build()
			}) return result
		
		val r2 = parseJson(result)
		val jsonObject = r2?.jsonObject ?: return r2
		
		val sv = jsonObject.parseString("access_token")
		if(sv?.isNotEmpty() == true) {
			result.data = sv
		} else {
			result.data = null
			result.error = "missing client credential."
		}
		return result
	}
	
	// client_credentialがまだ有効か調べる
	internal fun verifyClientCredential(client_credential : String) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(this.instance)
		if(result.error != null) return result
		
		if(! sendRequest(result) {
				Request.Builder()
					.url("https://$instance/api/v1/apps/verify_credentials")
					.header("Authorization", "Bearer $client_credential")
					.build()
			}) return result
		
		return parseJson(result)
	}
	
	// client_credentialを無効にする
	private fun revokeClientCredential(
		client_info : JSONObject,
		client_credential : String
	) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(this.instance)
		if(result.error != null) return result
		
		val client_id = client_info.parseString("client_id")
			?: return result.setError("missing client_id")
		
		val client_secret = client_info.parseString("client_secret")
			?: return result.setError("missing client_secret")
		
		if(! sendRequest(result) {
				Request.Builder()
					.url("https://$instance/oauth/revoke")
					.post(
						RequestBody.create(
							TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED,
							"token=" + client_credential.encodePercent()
								+ "&client_id=" + client_id.encodePercent()
								+ "&client_secret=" + client_secret.encodePercent()
						)
					)
					.build()
			}) return result
		
		return parseJson(result)
	}
	
	// 認証ページURLを作る
	internal fun prepareBrowserUrl(scope_string : String, client_info : JSONObject) : String? {
		val account = this.account
		val client_id = client_info.parseString("client_id") ?: return null
		
		return ("https://" + instance + "/oauth/authorize"
			+ "?client_id=" + client_id.encodePercent()
			+ "&response_type=code"
			+ "&redirect_uri=" + REDIRECT_URL.encodePercent()
			+ "&scope=$scope_string"
			+ "&scopes=$scope_string"
			+ "&state=" + (if(account != null) "db:${account.db_id}" else "host:$instance")
			+ "&grant_type=authorization_code"
			+ "&approval_prompt=force"
			//		+"&access_type=offline"
			)
	}
	
	private fun authentication1Mastodon(
		clientNameArg : String,
		ti : TootInstance
	) : TootApiResult? {
		
		// 前準備
		val result = TootApiResult.makeWithCaption(this.instance)
		if(result.error != null) return result
		val instance = result.caption // same to instance
		
		// クライアントIDがアプリ上に保存されているか？
		val client_name = if(clientNameArg.isNotEmpty()) clientNameArg else DEFAULT_CLIENT_NAME
		val client_info = ClientInfo.load(instance, client_name)
		
		// スコープ一覧を取得する
		
		val scope_string = getScopeString(ti)
		
		if(client_info != null && ! client_info.optBoolean(KEY_IS_MISSKEY)) {
			
			var client_credential = client_info.parseString(KEY_CLIENT_CREDENTIAL)
			val old_scope = client_info.parseString(KEY_CLIENT_SCOPE)
			
			// client_credential をまだ取得していないなら取得する
			if(client_credential?.isEmpty() != false) {
				val resultSub = getClientCredential(client_info)
				client_credential = resultSub?.string
				if(client_credential?.isNotEmpty() == true) {
					try {
						client_info.put(KEY_CLIENT_CREDENTIAL, client_credential)
						ClientInfo.save(instance, client_name, client_info.toString())
					} catch(ignored : JSONException) {
					}
				}
			}
			
			// client_credential があるならcredentialがまだ使えるか確認する
			if(client_credential?.isNotEmpty() == true) {
				val resultSub = verifyClientCredential(client_credential)
				if(resultSub?.jsonObject != null) {
					
					if(old_scope != scope_string) {
						// マストドン2.4でスコープが追加された
						// 取得時のスコープ指定がマッチしない(もしくは記録されていない)ならクライアント情報を再利用してはいけない
						ClientInfo.delete(instance, client_name)
						
						// client credential をタンスから消去する
						revokeClientCredential(client_info, client_credential)
						
						// XXX クライアントアプリ情報そのものはまだサーバに残っているが、明示的に消す方法は現状存在しない
					} else {
						// クライアント情報を再利用する
						result.data = prepareBrowserUrl(scope_string, client_info)
						return result
					}
				}
			}
		}
		
		val r2 = registerClient(scope_string, client_name)
		val jsonObject = r2?.jsonObject ?: return r2
		
		// {"id":999,"redirect_uri":"urn:ietf:wg:oauth:2.0:oob","client_id":"******","client_secret":"******"}
		jsonObject.put(KEY_AUTH_VERSION, AUTH_VERSION)
		jsonObject.put(KEY_CLIENT_SCOPE, scope_string)
		ClientInfo.save(instance, client_name, jsonObject.toString())
		result.data = prepareBrowserUrl(scope_string, jsonObject)
		
		return result
	}
	
	// 疑似アカウントの追加時に、インスタンスの検証を行う
	fun getInstanceInformation() : TootApiResult? {
		// マストドンのインスタンス情報を読めたら、それはマストドンのインスタンス
		val r1 = getInstanceInformationMastodon() ?: return null
		if(r1.jsonObject != null) return r1
		
		// misskeyのインスタンス情報を読めたら、それはmisskeyのインスタンス
		val r2 = getInstanceInformationMisskey() ?: return null
		if(r2.jsonObject != null) return r2
		
		return r1 // 通信エラーの表示ならr1でもr2でも構わないはず
	}
	
	// クライアントを登録してブラウザで開くURLを生成する
	fun authentication1(clientNameArg : String) : TootApiResult? {
		
		// マストドンのインスタンス情報
		var ri = parseInstanceInformation(getInstanceInformationMastodon())
		var ti = ri?.data as? TootInstance
		if(ti != null && (ri?.response?.code() ?: 0) in 200 until 300) {
			return authentication1Mastodon(clientNameArg, ti)
		}
		
		// misskeyのインスタンス情報
		ri = parseInstanceInformation(getInstanceInformationMisskey())
		ti = ri?.data as? TootInstance
		if(ti != null && (ri?.response?.code() ?: 0) in 200 until 300) {
			return authentication1Misskey(clientNameArg, ti)
		}
		
		return ri
	}
	
	// oAuth2認証の続きを行う
	fun authentication2(clientNameArg : String, code : String) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(instance)
		if(result.error != null) return result
		
		val instance = result.caption // same to instance
		val client_name = if(clientNameArg.isNotEmpty()) clientNameArg else DEFAULT_CLIENT_NAME
		val client_info =
			ClientInfo.load(instance, client_name) ?: return result.setError("missing client id")
		
		if(! sendRequest(result) {
				
				val scope_string = client_info.optString(KEY_CLIENT_SCOPE)
				
				val client_id = client_info.parseString("client_id")
				val client_secret = client_info.parseString("client_secret")
				if(client_id == null) return result.setError("missing client_id ")
				if(client_secret == null) return result.setError("missing client_secret")
				
				val post_content = ("grant_type=authorization_code"
					+ "&code=" + code.encodePercent()
					+ "&client_id=" + client_id.encodePercent()
					+ "&redirect_uri=" + REDIRECT_URL.encodePercent()
					+ "&client_secret=" + client_secret.encodePercent()
					+ "&scope=$scope_string"
					+ "&scopes=$scope_string")
				
				Request.Builder()
					.url("https://$instance/oauth/token")
					.post(RequestBody.create(MEDIA_TYPE_FORM_URL_ENCODED, post_content))
					.build()
				
			}) return result
		
		val r2 = parseJson(result)
		val token_info = r2?.jsonObject ?: return r2
		
		// {"access_token":"******","token_type":"bearer","scope":"read","created_at":1492334641}
		val access_token = token_info.parseString("access_token")
		if(access_token?.isEmpty() != false) {
			return result.setError("missing access_token in the response.")
		}
		return getUserCredential(access_token, token_info)
		
	}
	
	// アクセストークン手動入力でアカウントを更新する場合
	// verify_credentialsを呼び出す
	fun getUserCredential(
		access_token : String
		, tokenInfo : JSONObject = JSONObject()
		, isMisskey : Boolean = false
	) : TootApiResult? {
		if(isMisskey) {
			val result = TootApiResult.makeWithCaption(instance)
			if(result.error != null) return result
			
			// 認証されたアカウントのユーザ情報を取得する
			if(! sendRequest(result) {
					JSONObject()
						.put("i", access_token)
						.toPostRequestBuilder()
						.url("https://$instance/api/i")
						.build()
				}) return result
			
			val r2 = parseJson(result)
			if(r2?.jsonObject != null) {
				// ユーザ情報を読めたならtokenInfoを保存する
				tokenInfo.put(KEY_AUTH_VERSION, AUTH_VERSION)
				tokenInfo.put(KEY_API_KEY_MISSKEY, access_token)
				tokenInfo.put(KEY_IS_MISSKEY, true)
				result.tokenInfo = tokenInfo
			}
			return r2
			
		} else {
			val result = TootApiResult.makeWithCaption(instance)
			if(result.error != null) return result
			
			// 認証されたアカウントのユーザ情報を取得する
			if(! sendRequest(result) {
					Request.Builder()
						.url("https://$instance/api/v1/accounts/verify_credentials")
						.header("Authorization", "Bearer $access_token")
						.build()
				}) return result
			
			val r2 = parseJson(result)
			if(r2?.jsonObject != null) {
				// ユーザ情報を読めたならtokenInfoを保存する
				tokenInfo.put(KEY_AUTH_VERSION, AUTH_VERSION)
				tokenInfo.put("access_token", access_token)
				result.tokenInfo = tokenInfo
			}
			return r2
			
		}
		
	}
	
	fun searchMsp(query : String, max_id : String?) : TootApiResult? {
		
		// ユーザトークンを読む
		var user_token : String? = Pref.spMspUserToken(pref)
		
		for(nTry in 0 until 3) {
			if(callback.isApiCancelled) return null
			
			// ユーザトークンがなければ取得する
			if(user_token == null || user_token.isEmpty()) {
				
				callback.publishApiProgress("get MSP user token...")
				
				val result : TootApiResult = TootApiResult.makeWithCaption("Mastodon Search Portal")
				if(result.error != null) return result
				
				if(! sendRequest(result) {
						Request.Builder()
							.url(mspTokenUrl + "?apikey=" + mspApiKey.encodePercent())
							.build()
					}) return result
				
				val r2 = parseJson(result) { json ->
					val error = json.parseString("error")
					if(error == null) {
						null
					} else {
						val type = json.parseString("type")
						"error: $type $error"
					}
				}
				val jsonObject = r2?.jsonObject ?: return r2
				user_token = jsonObject.optJSONObject("result")?.optString("token")
				if(user_token?.isEmpty() != false) {
					return result.setError("Can't get MSP user token. response=${result.bodyString}")
				} else {
					pref.edit().put(Pref.spMspUserToken, user_token).apply()
				}
				
			}
			
			// ユーザトークンを使って検索APIを呼び出す
			val result : TootApiResult = TootApiResult.makeWithCaption("Mastodon Search Portal")
			if(result.error != null) return result
			
			if(! sendRequest(result) {
					val url = StringBuilder()
						.append(mspSearchUrl)
						.append("?apikey=").append(mspApiKey.encodePercent())
						.append("&utoken=").append(user_token.encodePercent())
						.append("&q=").append(query.encodePercent())
						.append("&max=").append(max_id?.encodePercent() ?: "")
					
					Request.Builder().url(url.toString()).build()
				}) return result
			
			var isUserTokenError = false
			val r2 = parseJson(result) { json ->
				val error = json.parseString("error")
				if(error == null) {
					null
				} else {
					// ユーザトークンがダメなら生成しなおす
					val detail = json.optString("detail")
					if("utoken" == detail) {
						isUserTokenError = true
					}
					
					val type = json.parseString("type")
					"API returns error: $type $error"
				}
			}
			if(r2 == null || ! isUserTokenError) return r2
		}
		return TootApiResult("MSP user token retry exceeded.")
	}
	
	fun searchTootsearch(
		query : String,
		from : Long?
	) : TootApiResult? {
		
		val result = TootApiResult.makeWithCaption("Tootsearch")
		if(result.error != null) return result
		
		if(! sendRequest(result) {
				val sb = StringBuilder()
					.append("https://tootsearch.chotto.moe/api/v1/search?sort=")
					.append("created_at:desc".encodePercent())
					.append("&q=").append(query.encodePercent())
				if(from != null) {
					sb.append("&from=").append(from.toString().encodePercent())
				}
				
				Request.Builder()
					.url(sb.toString())
					.build()
				
			}) return result
		
		return parseJson(result)
	}
	
	////////////////////////////////////////////////////////////////////////
	// JSONデータ以外を扱うリクエスト
	
	fun http(req : Request) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(req.url().host())
		if(result.error != null) return result
		
		sendRequest(result, progressPath = null) { req }
		return result
	}
	
	//	fun requestJson(req : Request) : TootApiResult? {
	//		val result = TootApiResult.makeWithCaption(req.url().host())
	//		if(result.error != null) return result
	//		if(sendRequest(result, progressPath = null) { req }) {
	//			parseJson(result)
	//		}
	//		return result
	//	}
	
	// 疑似アカウントでステータスURLからステータスIDを取得するためにHTMLを取得する
	fun getHttp(url : String) : TootApiResult? {
		val result = http(Request.Builder().url(url).build())
		if(result != null && result.error == null) {
			parseString(result)
		}
		return result
	}
	
	fun getHttpBytes(url : String) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(url)
		if(result.error != null) return result
		
		if(! sendRequest(result, progressPath = url) {
				Request.Builder().url(url).build()
			}) return result
		return parseBytes(result)
		
	}
	
	fun webSocket(path : String, ws_listener : WebSocketListener) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(instance)
		if(result.error != null) return result
		val account = this.account ?: return TootApiResult("account is null")
		try {
			var url = "wss://$instance$path"
			
			val request_builder = Request.Builder()
			
			val access_token = account.getAccessToken()
			if(access_token?.isNotEmpty() == true) {
				val delm = if(- 1 != url.indexOf('?')) '&' else '?'
				url = url + delm + "access_token=" + access_token.encodePercent()
			}
			
			val request = request_builder.url(url).build()
			publishApiProgress(context.getString(R.string.request_api, request.method(), path))
			val ws = httpClient.getWebSocket(request, ws_listener)
			if(isApiCancelled) {
				ws.cancel()
				return null
			}
			result.data = ws
		} catch(ex : Throwable) {
			log.trace(ex)
			result.error =
				"${result.caption}: ${ex.withCaption(context.resources, R.string.network_error)}"
		}
		return result
		
	}
	
}

fun TootApiClient.syncAccountByUrl(accessInfo : SavedAccount, who_url : String) : TootApiResult? {
	if(accessInfo.isMisskey) {
		val acct = TootAccount.getAcctFromUrl(who_url)
			?: return TootApiResult(context.getString(R.string.user_id_conversion_failed))
		
		val delm = acct.indexOf('@')
		val params = accessInfo.putMisskeyApiToken(JSONObject())
		if(delm != - 1) {
			params.put("username", acct.substring(0, delm))
			params.put("host", acct.substring(delm + 1))
		} else {
			params.put("username", acct)
		}
		
		val result = this.request("/api/users/show", params.toPostRequestBuilder())
		val jsonObject = result?.jsonObject
		
		if(jsonObject != null) {
			val tmp = TootParser(this.context, accessInfo).account(jsonObject)
			if(tmp != null) {
				result.data = tmp
			} else {
				result.setError(context.getString(R.string.user_id_conversion_failed))
			}
		}
		return result
	} else {
		val path = String.format(
			Locale.JAPAN,
			Column.PATH_SEARCH,
			who_url.encodePercent()
		) + "&resolve=1"
		val result = this.request(path)
		val jsonObject = result?.jsonObject
		if(jsonObject != null) {
			val tmp = TootParser(this.context, accessInfo).results(jsonObject)
			if(tmp != null && tmp.accounts.isNotEmpty()) {
				result.data = tmp.accounts[0].get()
			} else {
				result.setError(context.getString(R.string.user_id_conversion_failed))
			}
		}
		return result
	}
}

fun TootApiClient.syncAccountByAcct(accessInfo : SavedAccount, acct : String) : TootApiResult? {
	if(accessInfo.isMisskey) {
		val delm = acct.indexOf('@')
		val params = accessInfo.putMisskeyApiToken(JSONObject())
		if(delm != - 1) {
			params.put("username", acct.substring(0, delm))
			params.put("host", acct.substring(delm + 1))
		} else {
			params.put("username", acct)
		}
		
		val result = this.request("/api/users/show", params.toPostRequestBuilder())
		val jsonObject = result?.jsonObject
		
		if(jsonObject != null) {
			val tmp = TootParser(this.context, accessInfo).account(jsonObject)
			if(tmp != null) {
				result.data = tmp
			} else {
				result.setError(context.getString(R.string.user_id_conversion_failed))
			}
		}
		return result
	} else {
		val path = String.format(
			Locale.JAPAN,
			Column.PATH_SEARCH,
			acct.encodePercent()
		) + "&resolve=1"
		val result = this.request(path)
		val jsonObject = result?.jsonObject
		if(jsonObject != null) {
			val tmp = TootParser(this.context, accessInfo).results(jsonObject)
			if(tmp != null && tmp.accounts.isNotEmpty()) {
				result.data = tmp.accounts[0].get()
			} else {
				result.setError(context.getString(R.string.user_id_conversion_failed))
			}
		}
		return result
	}
}

fun TootApiClient.syncStatus(accessInfo : SavedAccount, url : String) : TootApiResult? {

	// FIXME Misskeyアカウントでも別アカのトゥートをローカルに同期したい！
	if(accessInfo.isMisskey) {
		return TootApiResult("Misskey has no API to sync note from remote to local.")
	}
	
	val path = String.format(
		Locale.JAPAN,
		Column.PATH_SEARCH,
		url.encodePercent()
	) + "&resolve=1"
	
	val result = request(path)
	if(result != null) {
		val tmp = TootParser(context, accessInfo).results(result.jsonObject)
		if(tmp != null && tmp.statuses.isNotEmpty()) {
			result.data = tmp.statuses[0]
		}
	}
	return result
}

fun TootApiClient.syncStatus(
	accessInfo : SavedAccount,
	statusRemote : TootStatus
) : TootApiResult? {
	
	// FIXME Misskeyアカウントでも別アカのトゥートをローカルに同期したい！
	if(accessInfo.isMisskey) {
		return TootApiResult("Misskey has no API to sync note from remote to local.")
	}
	
	var result : TootApiResult? = TootApiResult("missing url or uri")
	var sv = statusRemote.url
	when {
		sv?.isEmpty() != false -> {
		}
		
		sv.contains("/notes/") -> {
			// Misskeyタンスから読んだマストドンの投稿はurlがmisskeyタンス上のものになる
			// uri で試す
		}
		
		else -> {
			result = syncStatus(accessInfo, sv)
			if(result == null || result.data is TootStatus) {
				return result
			}
		}
	}
	
	sv = statusRemote.uri
	if(sv?.isNotEmpty() == true) {
		result = syncStatus(accessInfo, sv)
	}
	return result
}