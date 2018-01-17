package jp.juggler.subwaytooter.api

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

import org.json.JSONException
import org.json.JSONObject

import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.table.ClientInfo
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.put
import jp.juggler.subwaytooter.util.*
import okhttp3.*
import org.json.JSONArray
import java.util.regex.Pattern

class TootApiClient(
	internal val context : Context,
	internal val httpClient : SimpleHttpClient = SimpleHttpClientImpl(App1.ok_http_client),
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
		
		private const val KEY_AUTH_VERSION = "SubwayTooterAuthVersion"
		private const val AUTH_VERSION = 1
		private const val REDIRECT_URL = "subwaytooter://oauth/"
		
		private const val NO_INFORMATION = "(no information)"
		
		private val reStartJsonArray = Pattern.compile("\\A\\s*\\[")
		private val reStartJsonObject = Pattern.compile("\\A\\s*\\{")
		
		private val mspTokenUrl = "http://mastodonsearch.jp/api/v1.0.1/utoken"
		private val mspSearchUrl = "http://mastodonsearch.jp/api/v1.0.1/cross"
		private val mspApiKey = "e53de7f66130208f62d1808672bf6320523dcd0873dc69bc"
		
		fun getMspMaxId(array : JSONArray, max_id : String) : String {
			// max_id の更新
			val size = array.length()
			if(size > 0) {
				val item = array.optJSONObject(size - 1)
				if(item != null) {
					val sv = item.optString("msp_id")
					if(sv?.isNotEmpty() == true) return sv
				}
			}
			return max_id
		}
		
		fun getTootsearchHits(root : JSONObject) : JSONArray? {
			val hits = root.optJSONObject("hits")
			return hits?.optJSONArray("hits")
		}
		
		// returns the number for "from" parameter of next page.
		// returns "" if no more next page.
		fun getTootsearchMaxId(root : JSONObject, old : String) : String {
			val old_from = Utils.parse_int(old, 0)
			val hits2 = getTootsearchHits(root)
			if(hits2 != null) {
				val size = hits2.length()
				return if(size == 0) "" else Integer.toString(old_from + hits2.length())
			}
			return ""
		}
		
		val DEFAULT_JSON_ERROR_PARSER = { json : JSONObject ->
			Utils.optStringX(json, "error")
		}
		
		internal fun simplifyErrorHtml(
			response : Response,
			sv : String,
			jsonErrorParser : (json : JSONObject) -> String? = DEFAULT_JSON_ERROR_PARSER
		) : String {
			
			// JSONObjectとして解釈できるならエラーメッセージを検出する
			try {
				val data = JSONObject(sv)
				val error_message = jsonErrorParser(data)
				if(error_message?.isNotEmpty() == true) {
					return error_message
				}
			} catch(ex : Throwable) {
				log.e(ex, "response body is not JSON or missing 'error' attribute.")
			}
			
			// HTMLならタグの除去を試みる
			val ct = response.body()?.contentType()
			if(ct?.subtype() == "html") {
				return DecodeOptions().decodeHTML(null, null, sv).toString()
			}
			
			// XXX: Amazon S3 が403を返した場合にcontent-typeが?/xmlでserverがAmazonならXMLをパースしてエラーを整形することもできるが、多分必要ない
			
			return sv
		}
		
		fun formatResponse(
			response : Response,
			caption : String,
			bodyString : String? = null,
			jsonErrorParser : (json : JSONObject) -> String? = DEFAULT_JSON_ERROR_PARSER
		) : String {
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
			
			result.response = httpClient.getResponse(request)
			
			null == result.error
			
		} catch(ex : Throwable) {
			result.setError(result.caption + ": " + Utils.formatError(ex, context.resources, R.string.network_error))
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
			publishApiProgress(context.getString(R.string.reading_api, request.method(), progressPath ?: result.caption))
		}
		
		val bodyString = response.body()?.string()
		if(isApiCancelled) return null
		
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
			result.error = formatResponse(response, result.caption, result.bodyString ?: NO_INFORMATION)
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
			val bodyString = readBodyString(result, progressPath, jsonErrorParser)
				?: return if(isApiCancelled) null else result
			
			if(reStartJsonArray.matcher(bodyString).find()) {
				result.data = JSONArray(bodyString)
				
			} else if(reStartJsonObject.matcher(bodyString).find()) {
				val json = JSONObject(bodyString)
				val error_message = jsonErrorParser(json)
				if(error_message != null) {
					result.error = error_message
				} else {
					result.data = json
				}
			} else {
				result.error = context.getString(R.string.response_not_json) + "\n" + bodyString
			}
			
		} catch(ex : Throwable) {
			log.trace(ex)
			result.error = formatResponse(response, result.caption, result.bodyString ?: NO_INFORMATION)
		}
		return result
		
	}
	
	//////////////////////////////////////////////////////////////////////
	
	fun request(path : String, request_builder : Request.Builder = Request.Builder()) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(instance)
		if(result.error != null) return result
		
		val account = this.account ?: return result.setError("account is null")
		
		try {
			if(! sendRequest(result) {
				
				log.d("request: $path")
				
				request_builder.url("https://" + instance + path)
				
				val access_token = account.getAccessToken()
				if(access_token?.isNotEmpty() == true) {
					request_builder.header("Authorization", "Bearer " + access_token)
				}
				
				request_builder.build()
				
			}) return result
			
			return parseJson(result)
		} finally {
			val error = result.error
			if(error != null) log.d("error: $error")
		}
	}
	
	// 疑似アカウントの追加時に、インスタンスの検証を行う
	fun getInstanceInformation() : TootApiResult? {
		val result = TootApiResult.makeWithCaption(instance)
		if(result.error != null) return result
		if(! sendRequest(result) {
			Request.Builder().url("https://$instance/api/v1/instance").build()
		}) return result
		return parseJson(result)
	}
	
	// クライアントをタンスに登録
	internal fun registerClient(clientName : String) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(this.instance)
		if(result.error != null) return result
		val instance = result.caption // same to instance
		
		// OAuth2 クライアント登録
		if(! sendRequest(result) {
			Request.Builder()
				.url("https://$instance/api/v1/apps")
				.post(RequestBody.create(MEDIA_TYPE_FORM_URL_ENCODED, "client_name=" + Uri.encode(clientName)
					+ "&redirect_uris=" + Uri.encode(REDIRECT_URL)
					+ "&scopes=read write follow"
				))
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
			Request.Builder()
				.url("https://$instance/oauth/token")
				.post(RequestBody.create(MEDIA_TYPE_FORM_URL_ENCODED, "grant_type=client_credentials"
					+ "&client_id=" + Uri.encode(client_info.optString("client_id"))
					+ "&client_secret=" + Uri.encode(client_info.optString("client_secret"))
				))
				.build()
		}) return result
		
		val r2 = parseJson(result)
		val jsonObject = r2?.jsonObject ?: return r2
		
		val sv = Utils.optStringX(jsonObject, "access_token")
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
	
	internal fun prepareBrowserUrl(client_info : JSONObject) : String {
		val account = this.account
		
		// 認証ページURLを作る
		
		return ("https://" + instance + "/oauth/authorize"
			+ "?client_id=" + Uri.encode(Utils.optStringX(client_info, "client_id"))
			+ "&response_type=code"
			+ "&redirect_uri=" + Uri.encode(REDIRECT_URL)
			+ "&scope=read+write+follow"
			+ "&scopes=read+write+follow"
			+ "&state=" + (if(account != null) "db:" + account.db_id else "host:" + instance)
			+ "&grant_type=authorization_code"
			+ "&approval_prompt=force"
			//		+"&access_type=offline"
			)
	}
	
	// クライアントを登録してブラウザで開くURLを生成する
	fun authentication1(clientNameArg : String) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(this.instance)
		if(result.error != null) return result
		val instance = result.caption // same to instance
		
		// クライアントIDがアプリ上に保存されているか？
		val client_name = if(clientNameArg.isNotEmpty()) clientNameArg else DEFAULT_CLIENT_NAME
		val client_info = ClientInfo.load(instance, client_name)
		if(client_info != null) {
			
			var client_credential = Utils.optStringX(client_info, KEY_CLIENT_CREDENTIAL)
			
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
					result.data = prepareBrowserUrl(client_info)
					return result
				}
			}
		}
		
		val r2 = registerClient(client_name)
		val jsonObject = r2?.jsonObject ?: return r2
		
		// {"id":999,"redirect_uri":"urn:ietf:wg:oauth:2.0:oob","client_id":"******","client_secret":"******"}
		jsonObject.put(KEY_AUTH_VERSION, AUTH_VERSION)
		ClientInfo.save(instance, client_name, jsonObject.toString())
		result.data = prepareBrowserUrl(jsonObject)
		
		return result
	}
	
	// oAuth2認証の続きを行う
	fun authentication2(clientNameArg : String, code : String) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(instance)
		if(result.error != null) return result
		
		val instance = result.caption // same to instance
		val client_name = if(clientNameArg.isNotEmpty()) clientNameArg else DEFAULT_CLIENT_NAME
		val client_info = ClientInfo.load(instance, client_name) ?: return result.setError("missing client id")
		
		if(! sendRequest(result) {
			
			val post_content = ("grant_type=authorization_code"
				+ "&code=" + Uri.encode(code)
				+ "&client_id=" + Uri.encode(Utils.optStringX(client_info, "client_id"))
				+ "&redirect_uri=" + Uri.encode(REDIRECT_URL)
				+ "&client_secret=" + Uri.encode(Utils.optStringX(client_info, "client_secret"))
				+ "&scope=read+write+follow"
				+ "&scopes=read+write+follow")
			
			Request.Builder()
				.url("https://$instance/oauth/token")
				.post(RequestBody.create(MEDIA_TYPE_FORM_URL_ENCODED, post_content))
				.build()
			
		}) return result
		
		val r2 = parseJson(result)
		val token_info = r2?.jsonObject ?: return r2
		
		// {"access_token":"******","token_type":"bearer","scope":"read","created_at":1492334641}
		val access_token = Utils.optStringX(token_info, "access_token")
		if(access_token?.isEmpty() != false) {
			return result.setError("missing access_token in the response.")
		}
		return getUserCredential(access_token, token_info)
		
	}
	
	// アクセストークン手動入力でアカウントを更新する場合
	// verify_credentialsを呼び出す
	fun getUserCredential(
		access_token : String,
		tokenInfo : JSONObject = JSONObject()
	) : TootApiResult? {
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
	
	fun searchMsp(query : String, max_id : String) : TootApiResult? {
		
		// ユーザトークンを読む
		var user_token :String? = Pref.spMspUserToken(pref)
		
		for(nTry in 0 until 3) {
			if(callback.isApiCancelled) return null
			
			// ユーザトークンがなければ取得する
			if( user_token == null || user_token.isEmpty() ){
				
				callback.publishApiProgress("get MSP user token...")
				
				val result : TootApiResult = TootApiResult.makeWithCaption("Mastodon Search Portal")
				if(result.error != null) return result
				
				if(! sendRequest(result) {
					Request.Builder()
						.url(mspTokenUrl + "?apikey=" + Uri.encode(mspApiKey))
						.build()
				}) return result
				
				val r2 = parseJson(result) { json ->
					val error = Utils.optStringX(json, "error")
					if(error == null) {
						null
					} else {
						val type = Utils.optStringX(json, "type")
						"error: $type $error"
					}
				}
				val jsonObject = r2?.jsonObject ?: return r2
				user_token = jsonObject.optJSONObject("result")?.optString("token")
				if(user_token?.isEmpty() != false) {
					return result.setError("Can't get MSP user token. response=${result.bodyString}")
				}else{
					pref.edit().put( Pref.spMspUserToken,user_token).apply()
				}
				
			}
			
			// ユーザトークンを使って検索APIを呼び出す
			val result : TootApiResult = TootApiResult.makeWithCaption("Mastodon Search Portal")
			if(result.error != null) return result
			
			if(! sendRequest(result) {
				val url = (mspSearchUrl
					+ "?apikey=" + Uri.encode(mspApiKey)
					+ "&utoken=" + Uri.encode(user_token)
					+ "&q=" + Uri.encode(query)
					+ "&max=" + Uri.encode(max_id))
				
				Request.Builder().url(url).build()
			}) return result
			
			var isUserTokenError = false
			val r2 = parseJson(result) { json ->
				val error = Utils.optStringX(json, "error")
				if(error == null) {
					null
				} else {
					// ユーザトークンがダメなら生成しなおす
					val detail = json.optString("detail")
					if("utoken" == detail) {
						isUserTokenError = true
					}
					
					val type = Utils.optStringX(json, "type")
					"API returns error: $type $error"
				}
			}
			if(r2 == null || ! isUserTokenError) return r2
		}
		return TootApiResult("MSP user token retry exceeded.")
	}
	
	fun searchTootsearch(
		query : String,
		max_id : String // 空文字列、もしくはfromに指定するパラメータ
	) : TootApiResult? {
		
		val result = TootApiResult.makeWithCaption("Tootsearch")
		if(result.error != null) return result
		
		if(! sendRequest(result) {
			val url = ("https://tootsearch.chotto.moe/api/v1/search"
				+ "?sort=" + Uri.encode("created_at:desc")
				+ "&from=" + max_id
				+ "&q=" + Uri.encode(query))
			
			Request.Builder()
				.url(url)
				.build()
			
		}) return result
		
		return parseJson(result)
	}
	
	////////////////////////////////////////////////////////////////////////
	// JSONデータ以外を扱うリクエスト
	
	// 疑似アカウントでステータスURLからステータスIDを取得するためにHTMLを取得する
	fun getHttp(url : String) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(url)
		if(result.error != null) return result
		
		if(! sendRequest(result, progressPath = url) {
			Request.Builder().url(url).build()
		}) return result
		return parseString(result)
		
	}
	
	fun webSocket(path : String,  ws_listener : WebSocketListener) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(instance)
		if(result.error != null) return result
		val account = this.account ?: return TootApiResult("account is null")
		try {
			var url = "wss://$instance$path"
			
			val request_builder = Request.Builder()

			val access_token = account.getAccessToken()
			if(access_token?.isNotEmpty() == true) {
				val delm = if(- 1 != url.indexOf('?')) '&' else '?'
				url = url + delm + "access_token=" + Uri.encode(access_token)
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
			result.error = result.caption + ": " + Utils.formatError(ex, context.resources, R.string.network_error)
		}
		return result
		
	}
	
}
