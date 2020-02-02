package jp.juggler.subwaytooter.api

import android.content.Context
import android.content.SharedPreferences
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.table.ClientInfo
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.*
import jp.juggler.util.*
import okhttp3.*
import java.util.*
import java.util.regex.Pattern

class TootApiClient(
	internal val context : Context,
	internal val httpClient : SimpleHttpClient = SimpleHttpClientImpl(
		context,
		App1.ok_http_client
	),
	internal val callback : TootApiCallback
) {
	
	// 認証に関する設定を保存する
	internal val pref : SharedPreferences
	
	// インスタンスのホスト名
	var instance : Host? = null
	
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
		
		private const val DEFAULT_CLIENT_NAME = "SubwayTooter"
		private const val REDIRECT_URL = "subwaytooter://oauth/"
		
		// 20181225 3=>4 client credentialの取得時にもscopeの取得が必要になった
		// 20190147 4=>5 client id とユーザIDが同じだと同じアクセストークンが返ってくるので複数端末の利用で困る。
		// AUTH_VERSIONが古いclient情報は使わない。また、インポートの対象にしない。
		private const val AUTH_VERSION = 5
		
		internal const val KEY_CLIENT_CREDENTIAL = "SubwayTooterClientCredential"
		internal const val KEY_CLIENT_SCOPE = "SubwayTooterClientScope"
		private const val KEY_AUTH_VERSION = "SubwayTooterAuthVersion"
		const val KEY_IS_MISSKEY = "isMisskey" // for ClientInfo
		const val KEY_MISSKEY_VERSION = "isMisskey" // for tokenInfo,TootInstance
		const val KEY_MISSKEY_APP_SECRET = "secret"
		const val KEY_API_KEY_MISSKEY = "apiKeyMisskey"
		const val KEY_USER_ID = "userId"
		
		private const val NO_INFORMATION = "(no information)"
		
		private val reStartJsonArray = Pattern.compile("""\A\s*\[""")
		private val reStartJsonObject = Pattern.compile("""\A\s*\{""")
		private val reWhiteSpace = Pattern.compile("""\s+""")
		
		private const val mspTokenUrl = "http://mastodonsearch.jp/api/v1.0.1/utoken"
		private const val mspSearchUrl = "http://mastodonsearch.jp/api/v1.0.1/cross"
		private const val mspApiKey = "e53de7f66130208f62d1808672bf6320523dcd0873dc69bc"
		
		fun getMspMaxId(array : JsonArray, old : String?) : String? {
			// max_id の更新
			val size = array.size
			if(size > 0) {
				val sv = array[size - 1].cast<JsonObject>()?.string("msp_id")?.notEmpty()
				if(sv != null) return sv
			}
			// MSPでは終端は分からず、何度もリトライする
			return old
		}
		
		fun getTootsearchHits(root : JsonObject) : JsonArray? {
			return root["hits"].cast<JsonObject>()?.get("hits")?.cast()
		}
		
		// returns the number for "from" parameter of next page.
		// returns null if no more next page.
		fun getTootsearchMaxId(root : JsonObject, old : Long?) : Long? {
			val size = getTootsearchHits(root)?.size ?: 0
			return when {
				size <= 0 -> null
				else -> (old ?: 0L) + size.toLong()
			}
		}
		
		val DEFAULT_JSON_ERROR_PARSER = { json : JsonObject ->
			json["error"]?.toString()
		}
		
		internal fun simplifyErrorHtml(
			response : Response,
			sv : String,
			jsonErrorParser : (json : JsonObject) -> String? = DEFAULT_JSON_ERROR_PARSER
		) : String {
			
			// JsonObjectとして解釈できるならエラーメッセージを検出する
			try {
				val json = sv.decodeJsonObject()
				val error_message =
					jsonErrorParser(json)?.notEmpty()
				if(error_message != null) {
					return error_message
				}
			}catch(_:Throwable) {
			}
			
			// HTMLならタグの除去を試みる
			val ct = response.body?.contentType()
			if(ct?.subtype == "html") {
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
			jsonErrorParser : (json : JsonObject) -> String? = DEFAULT_JSON_ERROR_PARSER
		) : String {
			val sb = StringBuilder()
			try {
				// body は既に読み終わっているか、そうでなければこれから読む
				if(bodyString != null) {
					sb.append(simplifyErrorHtml(response, bodyString, jsonErrorParser))
				} else {
					try {
						val string = response.body?.string()
						if(string != null) {
							sb.append(simplifyErrorHtml(response, string, jsonErrorParser))
						}
					} catch(ex : Throwable) {
						log.e(ex, "missing response body.")
						sb.append("(missing response body)")
					}
				}
				
				if(sb.isNotEmpty()) sb.append(' ')
				sb.append("(HTTP ").append(response.code.toString())
				
				val message = response.message
				if(message.isNotEmpty()) sb.append(' ').append(message)
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
			// ti.versionGE(TootInstance.VERSION_2_7_0_rc1) -> "read+write+follow+push+create"
			ti.versionGE(TootInstance.VERSION_2_4_0_rc1) -> "read+write+follow+push"
			else -> "read+write+follow"
		}
		
		fun getScopeArrayMisskey(@Suppress("UNUSED_PARAMETER") ti : TootInstance) =
			JsonArray().apply {
				if(ti.versionGE(TootInstance.MISSKEY_VERSION_11)) {
					// https://github.com/syuilo/misskey/blob/master/src/server/api/kinds.ts
					arrayOf(
						"read:account",
						"write:account",
						"read:blocks",
						"write:blocks",
						"read:drive",
						"write:drive",
						"read:favorites",
						"write:favorites",
						"read:following",
						"write:following",
						"read:messaging",
						"write:messaging",
						"read:mutes",
						"write:mutes",
						"write:notes",
						"read:notifications",
						"write:notifications",
						"read:reactions",
						"write:reactions",
						"write:votes"
					)
				} else {
					// https://github.com/syuilo/misskey/issues/2341
					arrayOf(
						"account-read",
						"account-write",
						"account/read",
						"account/write",
						"drive-read",
						"drive-write",
						"favorite-read",
						"favorite-write",
						"favorites-read",
						"following-read",
						"following-write",
						"messaging-read",
						"messaging-write",
						"note-read",
						"note-write",
						"notification-read",
						"notification-write",
						"reaction-read",
						"reaction-write",
						"vote-read",
						"vote-write"
					
					)
					
				}
					// APIのエラーを回避するため、重複を排除する
					.toMutableSet()
					.forEach { add(it) }
			}
		
		private fun encodeScopeArray(scope_array : JsonArray?) : String? {
			scope_array ?: return null
			val list = scope_array.stringArrayList()
			list.sort()
			return list.joinToString(",")
		}
		
		private fun compareScopeArray(a : JsonArray, b : JsonArray?) : Boolean {
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
		tmpOkhttpClient : OkHttpClient? = null,
		block : () -> Request
	) : Boolean {
		return try {
			result.response = null
			result.bodyString = null
			result.data = null
			
			val request = block()
			
			result.requestInfo = "${request.method} ${progressPath ?: request.url.encodedPath}"
			
			callback.publishApiProgress(
				context.getString(
					R.string.request_api
					, request.method
					, progressPath ?: request.url.encodedPath
				)
			)
			
			val response = httpClient.getResponse(request, tmpOkhttpClient = tmpOkhttpClient)
			result.response = response
			
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
		jsonErrorParser : (json : JsonObject) -> String? = DEFAULT_JSON_ERROR_PARSER
	) : String? {
		
		if(isApiCancelled) return null
		
		val response = result.response !!
		
		val request = response.request
		publishApiProgress(
			context.getString(
				R.string.reading_api,
				request.method,
				progressPath ?: result.caption
			)
		)
		
		val bodyString = response.body?.string()
		if(isApiCancelled) return null
		
		// Misskey の /api/notes/favorites/create は 204(no content)を返す。ボディはカラになる。
		if(bodyString?.isEmpty() != false && response.code in 200 until 300) {
			result.bodyString = ""
			return ""
		}
		
		if(! response.isSuccessful || bodyString?.isEmpty() != false) {
			
			result.error = formatResponse(
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
		jsonErrorParser : (json : JsonObject) -> String? = DEFAULT_JSON_ERROR_PARSER
	) : ByteArray? {
		
		if(isApiCancelled) return null
		
		val response = result.response !!
		
		val request = response.request
		publishApiProgress(
			context.getString(
				R.string.reading_api,
				request.method,
				progressPath ?: result.caption
			)
		)
		
		val bodyBytes = response.body?.bytes()
		if(isApiCancelled) return null
		
		if(! response.isSuccessful || bodyBytes?.isEmpty() != false) {
			
			result.error = formatResponse(
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
		jsonErrorParser : (json : JsonObject) -> String? = DEFAULT_JSON_ERROR_PARSER
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
		jsonErrorParser : (json : JsonObject) -> String? = DEFAULT_JSON_ERROR_PARSER
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
		jsonErrorParser : (json : JsonObject) -> String? = DEFAULT_JSON_ERROR_PARSER
	) : TootApiResult? // 引数に指定したresultそのものか、キャンセルされたらnull
	{
		val response = result.response !! // nullにならないはず
		
		try {
			var bodyString = readBodyString(result, progressPath, jsonErrorParser)
				?: return if(isApiCancelled) null else result
			
			if(bodyString.isEmpty()) {
				
				// 204 no content は 空オブジェクトと解釈する
				result.data = JsonObject()
				
			} else if(reStartJsonArray.matcher(bodyString).find()) {
				result.data = bodyString.decodeJsonArray()
				
			} else if(reStartJsonObject.matcher(bodyString).find()) {
				val json = bodyString.decodeJsonObject()
				val error_message = jsonErrorParser(json)
				if(error_message != null) {
					result.error = error_message
				} else {
					result.data = json
				}
			} else {
				// HTMLならタグを除去する
				val ct = response.body?.contentType()
				if(ct?.subtype == "html") {
					val decoded = DecodeOptions().decodeHTML(bodyString).toString()
						.replace("""[\s　]+""".toRegex(), " ")
					bodyString = decoded
				}
				
				val sb = StringBuilder()
					.append(context.getString(R.string.response_not_json))
					.append(' ')
					.append(bodyString)
				
				if(sb.isNotEmpty()) sb.append(' ')
				sb.append("(HTTP ").append(response.code.toString())
				
				val message = response.message
				if(message.isNotEmpty()) sb.append(' ').append(message)
				
				sb.append(")")
				
				val url = response.request.url.toString()
				if(url.isNotEmpty()) sb.append(' ').append(url)
				
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
		val result = TootApiResult.makeWithCaption(instance?.pretty)
		if(result.error != null) return result
		
		val account = this.account // may null
		
		try {
			if(! sendRequest(result) {
					
					log.d("request: $path")
					
					request_builder.url("https://${instance?.ascii}$path")
					
					val access_token = account?.getAccessToken()
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
	
	private fun getAppInfoMisskey(appId : String?) : TootApiResult? {
		appId ?: return TootApiResult("missing app id")
		val result = TootApiResult.makeWithCaption(instance?.pretty)
		if(result.error != null) return result
		if(sendRequest(result) {
				JsonObject().apply {
					put("appId", appId)
				}
					.toPostRequestBuilder()
					.url("https://${instance?.ascii}/api/app/show")
					.build()
			}) {
			parseJson(result) ?: return null
			result.jsonObject?.put(KEY_IS_MISSKEY, true)
		}
		return result
	}
	
	private fun prepareBrowserUrlMisskey(appSecret : String) : String? {
		
		val result = TootApiResult.makeWithCaption(instance?.pretty)
		
		if(result.error != null) {
			showToast(context, false, result.error)
			return null
		}
		
		
		
		if(! sendRequest(result) {
				JsonObject().apply {
					put("appSecret", appSecret)
				}
					.toPostRequestBuilder()
					.url("https://${instance?.ascii}/api/auth/session/generate")
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
		val url = jsonObject.string("url")
		if(url?.isEmpty() != false) {
			showToast(context, false, "missing 'url' in auth session response.")
			return null
		}
		
		val e = PrefDevice.prefDevice(context)
			.edit()
			.putString(PrefDevice.LAST_AUTH_INSTANCE, instance?.ascii)
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
		scope_array : JsonArray,
		client_name : String
	) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(instance?.pretty)
		if(result.error != null) return result
		if(sendRequest(result) {
				JsonObject().apply {
					put("nameId", "SubwayTooter")
					put("name", client_name)
					put("description", "Android app for federated SNS")
					put("callbackUrl", "subwaytooter://misskey/auth_callback")
					put("permission", scope_array)
				}
					.toPostRequestBuilder()
					.url("https://${instance?.ascii}/api/app/create")
					.build()
			}) {
			parseJson(result) ?: return null
		}
		return result
	}
	
	private fun authentication1Misskey(clientNameArg : String, ti : TootInstance) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(this.instance?.pretty)
		if(result.error != null) return result
		val instance = result.caption // same to instance
		
		// クライアントIDがアプリ上に保存されているか？
		val client_name = clientNameArg.notEmpty() ?: DEFAULT_CLIENT_NAME
		val client_info = ClientInfo.load(instance, client_name)
		
		// スコープ一覧を取得する
		val scope_array = getScopeArrayMisskey(ti)
		
		if(client_info != null
			&& AUTH_VERSION == client_info.int(KEY_AUTH_VERSION)
			&& client_info.boolean(KEY_IS_MISSKEY) == true
		) {
			val appSecret = client_info.string(KEY_MISSKEY_APP_SECRET)
			
			val r2 = getAppInfoMisskey(client_info.string("id"))
			val tmpClientInfo = r2?.jsonObject
			// tmpClientInfo はsecretを含まないので保存してはいけない
			when {
				// アプリが登録済みで
				// クライアント名が一致してて
				// パーミッションが同じ
				tmpClientInfo != null
					&& client_name == tmpClientInfo.string("name")
					&& compareScopeArray(scope_array, tmpClientInfo["permission"].cast())
					&& appSecret?.isNotEmpty() == true -> {
					// クライアント情報を再利用する
					result.data = prepareBrowserUrlMisskey(appSecret)
					return result
				}
				
				else -> {
					// XXX appSecretを使ってクライアント情報を削除できるようにするべきだが、該当するAPIが存在しない
				}
			}
		}
		
		val r2 = registerClientMisskey(scope_array, client_name)
		val jsonObject = r2?.jsonObject ?: return r2
		
		val appSecret = jsonObject.string(KEY_MISSKEY_APP_SECRET)
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
		
		jsonObject[KEY_IS_MISSKEY] = true
		jsonObject[KEY_AUTH_VERSION] = AUTH_VERSION
		ClientInfo.save(instance, client_name, jsonObject.toString())
		result.data = prepareBrowserUrlMisskey(appSecret)
		
		return result
	}
	
	// oAuth2認証の続きを行う
	fun authentication2Misskey(
		clientNameArg : String,
		token : String,
		misskeyVersion : Int
	) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(instance?.pretty)
		if(result.error != null) return result
		val instance = result.caption // same to instance
		val client_name = clientNameArg.notEmpty() ?: DEFAULT_CLIENT_NAME
		
		@Suppress("UNUSED_VARIABLE")
		val client_info = ClientInfo.load(instance, client_name)
			?: return result.setError("missing client id")
		
		val appSecret = client_info.string(KEY_MISSKEY_APP_SECRET)
		if(appSecret?.isEmpty() != false) {
			return result.setError(context.getString(R.string.cant_get_misskey_app_secret))
		}
		
		if(! sendRequest(result) {
				JsonObject().apply {
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
		
		val access_token = token_info.string("accessToken")
		if(access_token?.isEmpty() != false) {
			return result.setError("missing accessToken in the response.")
		}
		
		val user = token_info["user"].cast<JsonObject>()
			?: return result.setError("missing user in the response.")
		
		token_info.remove("user")
		
		val apiKey = "$access_token$appSecret".encodeUTF8().digestSHA256().encodeHexLower()
		
		// ユーザ情報を読めたならtokenInfoを保存する
		EntityId.mayNull(user.string("id"))?.putTo(token_info, KEY_USER_ID)
		token_info[KEY_MISSKEY_VERSION] = misskeyVersion
		token_info[KEY_AUTH_VERSION] = AUTH_VERSION
		token_info[KEY_API_KEY_MISSKEY] = apiKey
		
		// tokenInfoとユーザ情報の入ったresultを返す
		result.tokenInfo = token_info
		result.data = user
		return result
	}
	
	//////////////////////////////////////////////////////////////////////
	
	// クライアントをタンスに登録
	fun registerClient(scope_string : String, clientName : String) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(instance?.pretty)
		if(result.error != null) return result
		val instance = result.caption // same to instance
		// OAuth2 クライアント登録
		if(! sendRequest(result) {
				("client_name=" + clientName.encodePercent()
					+ "&redirect_uris=" + REDIRECT_URL.encodePercent()
					+ "&scopes=$scope_string"
					).toFormRequestBody().toPost()
					.url("https://$instance/api/v1/apps")
					.build()
			}) return result
		
		return parseJson(result)
	}
	
	// クライアントアプリの登録を確認するためのトークンを生成する
	// oAuth2 Client Credentials の取得
	// https://github.com/doorkeeper-gem/doorkeeper/wiki/Client-Credentials-flow
	// このトークンはAPIを呼び出すたびに新しく生成される…
	internal fun getClientCredential(client_info : JsonObject) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(instance?.pretty)
		if(result.error != null) return result
		
		if(! sendRequest(result) {
				
				val client_id = client_info.string("client_id")
					?: return result.setError("missing client_id")
				
				val client_secret = client_info.string("client_secret")
					?: return result.setError("missing client_secret")
				
				"grant_type=client_credentials&scope=read+write&client_id=${client_id.encodePercent()}&client_secret=${client_secret.encodePercent()}"
					.toFormRequestBody().toPost()
					.url("https://${instance?.ascii}/oauth/token")
					.build()
			}) return result
		
		val r2 = parseJson(result)
		val jsonObject = r2?.jsonObject ?: return r2
		
		log.d("getClientCredential: ${jsonObject}")
		
		val sv = jsonObject.string("access_token")?.notEmpty()
		if(sv != null ) {
			result.data = sv
		} else {
			result.data = null
			result.error = "missing client credential."
		}
		return result
	}
	
	// client_credentialがまだ有効か調べる
	internal fun verifyClientCredential(client_credential : String) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(instance?.pretty)
		if(result.error != null) return result
		
		if(! sendRequest(result) {
				Request.Builder()
					.url("https://${instance?.ascii}/api/v1/apps/verify_credentials")
					.header("Authorization", "Bearer $client_credential")
					.build()
			}) return result
		
		return parseJson(result)
	}
	
	// client_credentialを無効にする
	private fun revokeClientCredential(
		client_info : JsonObject,
		client_credential : String
	) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(instance?.pretty)
		if(result.error != null) return result
		
		val client_id = client_info.string("client_id")
			?: return result.setError("missing client_id")
		
		val client_secret = client_info.string("client_secret")
			?: return result.setError("missing client_secret")
		
		if(! sendRequest(result) {
				("token=" + client_credential.encodePercent()
					+ "&client_id=" + client_id.encodePercent()
					+ "&client_secret=" + client_secret.encodePercent()
					).toFormRequestBody().toPost()
					.url("https://${instance?.ascii}/oauth/revoke")
					.build()
				
			}) return result
		
		return parseJson(result)
	}
	
	// 認証ページURLを作る
	internal fun prepareBrowserUrl(scope_string : String, client_info : JsonObject) : String? {
		val account = this.account
		val client_id = client_info.string("client_id") ?: return null
		
		val state = StringBuilder()
			.append((if(account != null) "db:${account.db_id}" else "host:${instance?.ascii}"))
			.append(',')
			.append("random:${System.currentTimeMillis()}")
			.toString()
		
		return ("https://${instance?.ascii}/oauth/authorize"
			+ "?client_id=" + client_id.encodePercent()
			+ "&response_type=code"
			+ "&redirect_uri=" + REDIRECT_URL.encodePercent()
			+ "&scope=$scope_string"
			+ "&scopes=$scope_string"
			+ "&state=" + state.encodePercent()
			+ "&grant_type=authorization_code"
			+ "&approval_prompt=force"
			+ "&force_login=true"
			//		+"&access_type=offline"
			)
	}
	
	private fun prepareClientMastodon(
		clientNameArg : String,
		ti : TootInstance,
		forceUpdateClient : Boolean = false
	) : TootApiResult? {
		// 前準備
		val result = TootApiResult.makeWithCaption(instance?.pretty)
		if(result.error != null) return result
		val instance = result.caption // same to instance
		
		// クライアントIDがアプリ上に保存されているか？
		val client_name = clientNameArg.notEmpty() ?: DEFAULT_CLIENT_NAME
		var client_info = ClientInfo.load(instance, client_name)
		
		// スコープ一覧を取得する
		val scope_string = getScopeString(ti)
		
		when {
			AUTH_VERSION != client_info?.int(KEY_AUTH_VERSION) -> {
				// 古いクライアント情報は使わない。削除もしない。
			}
			
			client_info.boolean(KEY_IS_MISSKEY) == true -> {
				// Misskeyにはclient情報をまだ利用できるかどうか調べる手段がないので、再利用しない
			}
			
			else -> {
				val old_scope = client_info.string(KEY_CLIENT_SCOPE)
				
				// client_credential をまだ取得していないなら取得する
				var client_credential = client_info.string(KEY_CLIENT_CREDENTIAL)
				if(client_credential?.isEmpty() != false) {
					val resultSub = getClientCredential(client_info)
					client_credential = resultSub?.string
					if(client_credential?.isNotEmpty() == true) {
						try {
							client_info[KEY_CLIENT_CREDENTIAL] = client_credential
							ClientInfo.save(instance, client_name, client_info.toString())
						} catch(ignored : JsonException) {
						}
					}
				}
				
				// client_credential があるならcredentialがまだ使えるか確認する
				if(client_credential?.isNotEmpty() == true) {
					val resultSub = verifyClientCredential(client_credential)
					val currentCC = resultSub?.jsonObject
					if(currentCC != null) {
						if(old_scope != scope_string || forceUpdateClient) {
							// マストドン2.4でスコープが追加された
							// 取得時のスコープ指定がマッチしない(もしくは記録されていない)ならクライアント情報を再利用してはいけない
							ClientInfo.delete(instance, client_name)
							
							// client credential をタンスから消去する
							revokeClientCredential(client_info, client_credential)
							
							// XXX クライアントアプリ情報そのものはまだサーバに残っているが、明示的に消す方法は現状存在しない
						} else {
							// クライアント情報を再利用する
							result.data = client_info
							return result
						}
					}
				}
			}
		}
		
		val r2 = registerClient(scope_string, client_name)
		client_info = r2?.jsonObject ?: return r2
		
		// {"id":999,"redirect_uri":"urn:ietf:wg:oauth:2.0:oob","client_id":"******","client_secret":"******"}
		client_info[KEY_AUTH_VERSION] = AUTH_VERSION
		client_info[KEY_CLIENT_SCOPE] = scope_string
		
		// client_credential をまだ取得していないなら取得する
		var client_credential = client_info.string(KEY_CLIENT_CREDENTIAL)
		if(client_credential?.isEmpty() != false) {
			val resultSub = getClientCredential(client_info)
			client_credential = resultSub?.string
			if(client_credential?.isEmpty() != false) return resultSub
			
			client_info[KEY_CLIENT_CREDENTIAL] = client_credential
		}
		
		try {
			ClientInfo.save(instance, client_name, client_info.toString())
		} catch(ignored : JsonException) {
		}
		result.data = client_info
		return result
	}
	
	private fun authentication1Mastodon(
		clientNameArg : String,
		ti : TootInstance,
		forceUpdateClient : Boolean = false
	) : TootApiResult? {
		
		if(ti.instanceType == TootInstance.InstanceType.Pixelfed) {
			return TootApiResult("currently Pixelfed instance is not supported.")
		}
		
		return prepareClientMastodon(clientNameArg, ti, forceUpdateClient)?.also { result ->
			val client_info = result.jsonObject
			if(client_info != null) {
				result.data = prepareBrowserUrl(getScopeString(ti), client_info)
			}
		}
	}
	
	// クライアントを登録してブラウザで開くURLを生成する
	fun authentication1(
		clientNameArg : String,
		forceUpdateClient : Boolean = false
	) : TootApiResult? {
		
		val (ti, ri) = TootInstance.get(this)
		ti ?: return ri
		return when {
			ti.misskeyVersion > 0 -> authentication1Misskey(clientNameArg, ti)
			else -> authentication1Mastodon(clientNameArg, ti, forceUpdateClient)
		}
	}
	
	// oAuth2認証の続きを行う
	fun authentication2(clientNameArg : String, code : String) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(instance?.pretty)
		if(result.error != null) return result
		
		val instance = result.caption // same to instance
		val client_name = if(clientNameArg.isNotEmpty()) clientNameArg else DEFAULT_CLIENT_NAME
		val client_info =
			ClientInfo.load(instance, client_name) ?: return result.setError("missing client id")
		
		if(! sendRequest(result) {
				
				val scope_string = client_info.string(KEY_CLIENT_SCOPE)
				val client_id = client_info.string("client_id")
				val client_secret = client_info.string("client_secret")
				if(client_id == null) return result.setError("missing client_id ")
				if(client_secret == null) return result.setError("missing client_secret")
				
				val post_content = ("grant_type=authorization_code"
					+ "&code=" + code.encodePercent()
					+ "&client_id=" + client_id.encodePercent()
					+ "&redirect_uri=" + REDIRECT_URL.encodePercent()
					+ "&client_secret=" + client_secret.encodePercent()
					+ "&scope=$scope_string"
					+ "&scopes=$scope_string")
				
				post_content.toFormRequestBody().toPost()
					.url("https://$instance/oauth/token")
					.build()
				
			}) return result
		
		val r2 = parseJson(result)
		val token_info = r2?.jsonObject ?: return r2
		
		// {"access_token":"******","token_type":"bearer","scope":"read","created_at":1492334641}
		val access_token = token_info.string("access_token")
		if(access_token?.isEmpty() != false) {
			return result.setError("missing access_token in the response.")
		}
		return getUserCredential(access_token, token_info)
		
	}
	
	// アクセストークン手動入力でアカウントを更新する場合、アカウントの情報を取得する
	fun getUserCredential(
		access_token : String
		, tokenInfo : JsonObject = JsonObject()
		, misskeyVersion : Int = 0
	) : TootApiResult? {
		if(misskeyVersion > 0) {
			val result = TootApiResult.makeWithCaption(instance?.pretty)
			if(result.error != null) return result
			
			// 認証されたアカウントのユーザ情報を取得する
			if(! sendRequest(result) {
					JsonObject().apply{
						put("i", access_token)
					}
						.toPostRequestBuilder()
						.url("https://${instance?.ascii}/api/i")
						.build()
				}) return result
			
			val r2 = parseJson(result)
			if(r2?.jsonObject != null) {
				// ユーザ情報を読めたならtokenInfoを保存する
				tokenInfo[KEY_AUTH_VERSION] = AUTH_VERSION
				tokenInfo[KEY_API_KEY_MISSKEY] = access_token
				tokenInfo[KEY_MISSKEY_VERSION] = misskeyVersion
				result.tokenInfo = tokenInfo
			}
			return r2
			
		} else {
			val result = TootApiResult.makeWithCaption(instance?.pretty)
			if(result.error != null) return result
			
			// 認証されたアカウントのユーザ情報を取得する
			if(! sendRequest(result) {
					Request.Builder()
						.url("https://${instance?.ascii}/api/v1/accounts/verify_credentials")
						.header("Authorization", "Bearer $access_token")
						.build()
				}) return result
			
			val r2 = parseJson(result)
			if(r2?.jsonObject != null) {
				// ユーザ情報を読めたならtokenInfoを保存する
				tokenInfo[KEY_AUTH_VERSION] = AUTH_VERSION
				tokenInfo["access_token"] = access_token
				result.tokenInfo = tokenInfo
			}
			return r2
			
		}
		
	}
	
	fun createUser1(clientNameArg : String) : TootApiResult? {
		
		val (ti, ri) = TootInstance.get(this)
		ti ?: return ri
		
		return when(ti.instanceType) {
			TootInstance.InstanceType.Misskey ->
				TootApiResult("Misskey has no API to create new account")
			TootInstance.InstanceType.Pleroma ->
				TootApiResult("Pleroma has no API to create new account")
			TootInstance.InstanceType.Pixelfed ->
				TootApiResult("Pixelfed has no API to create new account")
			else ->
				prepareClientMastodon(clientNameArg, ti)
			// result.JsonObject に credentialつきのclient_info を格納して返す
		}
	}
	
	// ユーザ名入力の後に呼ばれる
	fun createUser2Mastodon(
		client_info : JsonObject,
		username : String,
		email : String,
		password : String,
		agreement : Boolean,
		reason : String?
	) : TootApiResult? {
		
		val result = TootApiResult.makeWithCaption(instance?.pretty)
		if(result.error != null) return result
		
		log.d("createUser2Mastodon: client is : ${client_info}")
		
		val client_credential = client_info.string(KEY_CLIENT_CREDENTIAL)
			?: return result.setError("createUser2Mastodon(): missing client credential")
		
		if(! sendRequest(result) {
				
				val params = ArrayList<String>().apply {
					add("username=${username.encodePercent()}")
					add("email=${email.encodePercent()}")
					add("password=${password.encodePercent()}")
					add("agreement=${agreement}")
					if(reason?.isNotEmpty() == true) add("reason=${reason.encodePercent()}")
				}
				
				params
					.joinToString("&").toFormRequestBody().toPost()
					.url("https://${instance?.ascii}/api/v1/accounts")
					.header("Authorization", "Bearer ${client_credential}")
					.build()
				
			}) return result
		
		return parseJson(result)
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
					val error = json.string("error")
					if(error == null) {
						null
					} else {
						val type = json.string("type")
						"error: $type $error"
					}
				}
				val jsonObject = r2?.jsonObject ?: return r2
				user_token = jsonObject.jsonObject("result")?.string("token")
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
				val error = json.string("error")
				if(error == null) {
					null
				} else {
					// ユーザトークンがダメなら生成しなおす
					val detail = json.string("detail")
					if("utoken" == detail) {
						isUserTokenError = true
					}

					val type = json.string("type")
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
		val result = TootApiResult.makeWithCaption(req.url.host)
		if(result.error != null) return result
		
		sendRequest(result, progressPath = null) { req }
		return result
	}
	
	//	fun requestJson(req : Request) : TootApiResult? {
	//		val result = TootApiResult.makeWithCaption(req.url().host())
	//		if(result.error != null) return result
	//		if(sendRequest(result, progressPath = null) { req }) {
	//			decodeJsonValue(result)
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
	
	fun getHttpBytes(url : String) : Pair<TootApiResult?, ByteArray?> {
		val result = TootApiResult.makeWithCaption(url)
		if(result.error != null) return Pair(result, null)
		
		if(! sendRequest(result, progressPath = url) {
				Request.Builder().url(url).build()
			}) {
			return Pair(result, null)
		}
		val r2 = parseBytes(result)
		return Pair(r2, r2?.data as? ByteArray)
	}
	
	fun webSocket(
		path : String,
		ws_listener : WebSocketListener
	) : Pair<TootApiResult?, WebSocket?> {
		var ws : WebSocket? = null
		val result = TootApiResult.makeWithCaption(instance?.pretty)
		if(result.error != null) return Pair(result, null)
		val account = this.account ?: return Pair(TootApiResult("account is null"), null)
		try {
			var url = "wss://${instance?.ascii}$path"
			
			val request_builder = Request.Builder()
			
			val access_token = account.getAccessToken()
			if(access_token?.isNotEmpty() == true) {
				val delm = if(- 1 != url.indexOf('?')) '&' else '?'
				url = url + delm + "access_token=" + access_token.encodePercent()
			}
			
			val request = request_builder.url(url).build()
			publishApiProgress(context.getString(R.string.request_api, request.method, path))
			ws = httpClient.getWebSocket(request, ws_listener)
			if(isApiCancelled) {
				ws.cancel()
				return Pair(null, null)
			}
		} catch(ex : Throwable) {
			log.trace(ex)
			result.error =
				"${result.caption}: ${ex.withCaption(context.resources, R.string.network_error)}"
		}
		return Pair(result, ws)
		
	}
	
}

// query: query_string after ? ( ? itself is excluded )
fun TootApiClient.requestMastodonSearch(
	parser : TootParser,
	query : String
) : Pair<TootApiResult?, TootResults?> {
	
	var searchApiVersion = 2
	var apiResult = request("/api/v2/search?$query")
		?: return Pair(null, null)
	
	if((apiResult.response?.code ?: 0) in 400 until 500) {
		searchApiVersion = 1
		apiResult = request("/api/v1/search?$query")
			?: return Pair(null, null)
		
	}
	
	val searchResult = parser.results(apiResult.jsonObject)
	searchResult?.searchApiVersion = searchApiVersion
	
	return Pair(apiResult, searchResult)
}

// result.data に TootAccountRefを格納して返す。もしくはエラーかキャンセル
fun TootApiClient.syncAccountByUrl(
	accessInfo : SavedAccount,
	who_url : String
) : Pair<TootApiResult?, TootAccountRef?> {
	
	// misskey由来のアカウントURLは https://host/@user@instance などがある
	val m = TootAccount.reAccountUrl.matcher(who_url)
	if(m.find()) {
		// val host = m.group(1)
		val user = m.groupEx(2) !!.decodePercent()
		val instance = m.groupEx(3)?.decodePercent()
		if(instance?.isNotEmpty() == true) {
			return this.syncAccountByUrl(accessInfo, "https://$instance/@$user")
		}
	}
	
	val parser = TootParser(context, accessInfo)
	
	return if(accessInfo.isMisskey) {
		
		val acct = TootAccount.getAcctFromUrl(who_url)
			?: return Pair(
				TootApiResult(context.getString(R.string.user_id_conversion_failed)),
				null
			)
		
		var ar : TootAccountRef? = null
		val result = request(
			"/api/users/show",
			accessInfo.putMisskeyApiToken().apply {
				put("username", acct.username)
				acct.host?.let{ put("host", it.ascii )}
			}.toPostRequestBuilder()
		)
			?.apply {
				ar = TootAccountRef.mayNull(parser, parser.account(jsonObject))
				if(ar == null && error == null) {
					setError(context.getString(R.string.user_id_conversion_failed))
				}
			}
		Pair(result, ar)
	} else {
		val (apiResult, searchResult) = requestMastodonSearch(
			parser,
			"q=${who_url.encodePercent()}&resolve=true"
		)
		val ar = searchResult?.accounts?.firstOrNull()
		if(apiResult != null && apiResult.error == null && ar == null) {
			apiResult.setError(context.getString(R.string.user_id_conversion_failed))
		}
		Pair(apiResult, ar)
	}
}

fun TootApiClient.syncAccountByAcct(
	accessInfo : SavedAccount,
	acctArg : String
) : Pair<TootApiResult?, TootAccountRef?> = syncAccountByAcct(accessInfo, Acct.parse(acctArg))

fun TootApiClient.syncAccountByAcct(
	accessInfo : SavedAccount,
	acct:Acct
) : Pair<TootApiResult?, TootAccountRef?> {
	
	val parser = TootParser(context, accessInfo)
	return if(accessInfo.isMisskey) {
		var ar : TootAccountRef? = null
		val result = request(
			"/api/users/show",
			accessInfo.putMisskeyApiToken()
				.apply {
					if( acct.isValid) put("username", acct.username)
					if( acct.host!=null ) put("host", acct.host.ascii )
				}
				.toPostRequestBuilder()
		)
			?.apply {
				ar = TootAccountRef.mayNull(parser, parser.account(jsonObject))
				if(ar == null && error == null) {
					setError(context.getString(R.string.user_id_conversion_failed))
				}
			}
		Pair(result, ar)
	} else {
		val (apiResult, searchResult) = requestMastodonSearch(
			parser,
			"q=${acct.ascii.encodePercent()}&resolve=true"
		)
		val ar = searchResult?.accounts?.firstOrNull()
		if(apiResult != null && apiResult.error == null && ar == null) {
			apiResult.setError(context.getString(R.string.user_id_conversion_failed))
		}
		Pair(apiResult, ar)
	}
	
}

fun TootApiClient.syncStatus(
	accessInfo : SavedAccount,
	urlArg : String
) : Pair<TootApiResult?, TootStatus?> {
	
	var url = urlArg
	
	// misskey の投稿URLは外部タンスの投稿を複製したものの可能性がある
	// これを投稿元タンスのURLに変換しないと、投稿の同期には使えない
	val m = TootStatus.reStatusPageMisskey.matcher(urlArg)
	if(m.find()) {
		val host = Host.parse(m.groupEx(1)!!)
		val noteId = m.groupEx(2)
		
		TootApiClient(context, callback = callback)
			.apply { instance = host }
			.request(
				"/api/notes/show",
				JsonObject().apply{
					put("noteId", noteId)
				}
					.toPostRequestBuilder()
			)
			?.also { result ->
				TootParser(
					context,
					LinkHelper.newLinkHelper(host, misskeyVersion = 10),
					serviceType = ServiceType.MISSKEY
				)
					.status(result.jsonObject)
					?.apply {
						if( accessInfo.host == host) {
							return Pair(result, this)
						}
						uri.letNotEmpty { url = it }
					}
				
			}
			?: return Pair(null, null) // cancelled.
	}
	
	// 使いたいタンス上の投稿IDを取得する
	val parser = TootParser(context, accessInfo)
	return if(accessInfo.isMisskey) {
		var targetStatus : TootStatus? = null
		val result = request(
			"/api/ap/show",
			accessInfo.putMisskeyApiToken().apply{
				put("uri", url)
			}
				.toPostRequestBuilder()
		)
			?.apply {
				targetStatus = parser.parseMisskeyApShow(jsonObject) as? TootStatus
				if(targetStatus == null && error == null) {
					setError(context.getString(R.string.cant_sync_toot))
				}
			}
		Pair(result, targetStatus)
	} else {
		val (apiResult, searchResult) = requestMastodonSearch(
			parser,
			"q=${url.encodePercent()}&resolve=true"
		)
		val targetStatus = searchResult?.statuses?.firstOrNull()
		if(apiResult != null && apiResult.error == null && targetStatus == null) {
			apiResult.setError(context.getString(R.string.cant_sync_toot))
		}
		Pair(apiResult, targetStatus)
	}
	
}

fun TootApiClient.syncStatus(
	accessInfo : SavedAccount,
	statusRemote : TootStatus
) : Pair<TootApiResult?, TootStatus?> {
	
	// URL->URIの順に試す
	
	val uriList = ArrayList<String>(2)
	
	statusRemote.url.letNotEmpty {
		when {
			it.contains("/notes/") -> {
				// Misskeyタンスから読んだマストドンの投稿はurlがmisskeyタンス上のものになる
				// ActivityPub object id としては不適切なので使わない
			}
			
			else -> uriList.add(it)
		}
	}
	
	statusRemote.uri.letNotEmpty {
		// uri の方は↑の問題はない
		uriList.add(it)
	}
	
	if(accessInfo.isMisskey && uriList.firstOrNull()?.contains("@") == true) {
		// https://github.com/syuilo/misskey/pull/2832
		// @user を含むuri はMisskeyだと少し効率が悪いそうなので順序を入れ替える
		uriList.reverse()
	}
	
	for(uri in uriList) {
		val pair = syncStatus(accessInfo, uri)
		if(pair.second != null || pair.first == null) {
			return pair
		}
	}
	
	return Pair(TootApiResult("can't resolve status URL/URI."), null)
}
