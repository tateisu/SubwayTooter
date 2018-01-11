package jp.juggler.subwaytooter.api

import android.content.Context
import android.net.Uri

import org.json.JSONException
import org.json.JSONObject

import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.table.ClientInfo
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.util.Utils
import okhttp3.Call
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.WebSocketListener
import org.json.JSONArray

class TootApiClient(
	private val context : Context,
	private val callback : TootApiCallback
) {
	
	companion object {
		private val log = LogCategory("TootApiClient")
		
		private val ok_http_client = App1.ok_http_client
		
		val MEDIA_TYPE_FORM_URL_ENCODED = MediaType.parse("application/x-www-form-urlencoded")
		val MEDIA_TYPE_JSON = MediaType.parse("application/json;charset=UTF-8")
		
		private const val DEFAULT_CLIENT_NAME = "SubwayTooter"
		private const val KEY_CLIENT_CREDENTIAL = "SubwayTooterClientCredential"
		
		private const val KEY_AUTH_VERSION = "SubwayTooterAuthVersion"
		private const val AUTH_VERSION = 1
		private const val REDIRECT_URL = "subwaytooter://oauth/"
		
	}
	
	interface CurrentCallCallback {
		fun onCallCreated(call : Call)
	}
	
	private var call_callback : CurrentCallCallback? = null
	
	// インスタンスのホスト名
	var instance : String? = null
	
	// アカウントがある場合に使用する
	var account : SavedAccount? = null
	
	@Suppress("unused")
	val isApiCancelled : Boolean
		get() = callback.isApiCancelled
	
	val isCancelled : Boolean
		get() = callback.isApiCancelled
	
	fun publishApiProgress(s : String) {
		callback.publishApiProgress(s)
	}
	
	fun publishApiProgressRatio(value : Int, max : Int) {
		callback.publishApiProgressRatio(value, max)
	}
	
	fun setCurrentCallCallback(call_callback : CurrentCallCallback) {
		this.call_callback = call_callback
	}
	
	// アカウント追加時に使用する
	fun setInstance(instance : String?) : TootApiClient {
		this.instance = instance
		return this
	}
	
	fun setAccount(account : SavedAccount) : TootApiClient {
		this.account = account
		this.instance = account.host
		return this
	}
	
	@JvmOverloads
	fun request(path : String, request_builder : Request.Builder = Request.Builder()) : TootApiResult? {
		log.d("request: $path")
		val result = request_sub(path, request_builder)
		val error = result?.error
		if(error != null) log.d("error: $error")
		return result
	}
	
	private fun request_sub(path : String, request_builder : Request.Builder) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(instance)
		if(result.error != null) return result
		val instance = result.caption // same to instance
		val account = this.account ?: return result.setError("account is null")
		val access_token = account.getAccessToken()
		
		val response = try {
			request_builder.url("https://" + instance + path)
			
			if(access_token != null && access_token.isNotEmpty()) {
				request_builder.header("Authorization", "Bearer " + access_token)
			}
			
			sendRequest(request_builder.build())
		} catch(ex : Throwable) {
			log.trace(ex)
			return result.setError(instance + ": " + Utils.formatError(ex, context.resources, R.string.network_error))
		}
		return readJson(result, response)
	}
	
	fun webSocket(path : String, request_builder : Request.Builder, ws_listener : WebSocketListener) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(instance)
		if(result.error != null) return result
		val instance = result.caption // same to instance
		
		try {
			val account = this.account ?: return TootApiResult("account is null")
			val access_token = account.getAccessToken()
			
			var url = "wss://" + instance + path
			
			if(access_token != null && access_token.isNotEmpty()) {
				val delm = if(- 1 != url.indexOf('?')) '&' else '?'
				url = url + delm + "access_token=" + Uri.encode(access_token)
			}
			
			request_builder.url(url)
			val request = request_builder.build()
			callback.publishApiProgress(context.getString(R.string.request_api, request.method(), path))
			val ws = ok_http_client.newWebSocket(request, ws_listener)
			if(callback.isApiCancelled) {
				ws.cancel()
				return null
			}
			result.data = ws
		} catch(ex : Throwable) {
			log.trace(ex)
			result.error = instance + ": " + Utils.formatError(ex, context.resources, R.string.network_error)
		}
		return result
		
	}
	
	// 疑似アカウントの追加時に、インスタンスの検証を行う
	fun checkInstance() : TootApiResult? {
		val result = TootApiResult.makeWithCaption(instance)
		if(result.error != null) return result
		val instance = result.caption // same to instance
		
		val response = try {
			val request = Request.Builder().url("https://$instance/api/v1/instance").build()
			sendRequest(request)
		} catch(ex : Throwable) {
			log.trace(ex)
			return result.setError(instance + ": " + Utils.formatError(ex, context.resources, R.string.network_error))
		}
		return readJson(result, response)
	}
	
	// クライアントアプリの登録を確認するためのトークンを生成する
	// oAuth2 Client Credentials の取得
	// https://github.com/doorkeeper-gem/doorkeeper/wiki/Client-Credentials-flow
	// このトークンはAPIを呼び出すたびに新しく生成される…
	private fun getClientCredential(client_info : JSONObject) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(this.instance)
		if(result.error != null) return result
		val instance = result.caption
		
		val response = try {
			
			val request = Request.Builder()
				.url("https://$instance/oauth/token")
				.post(RequestBody.create(MEDIA_TYPE_FORM_URL_ENCODED, "grant_type=client_credentials"
					+ "&client_id=" + Uri.encode(client_info.optString("client_id"))
					+ "&client_secret=" + Uri.encode(client_info.optString("client_secret"))
				))
				.build()
			
			sendRequest(request)
		} catch(ex : Throwable) {
			log.trace(ex)
			return result.setError("getClientCredential: " + instance + ": " + Utils.formatError(ex, context.resources, R.string.network_error))
		}
		
		val r2 = readJson(result, response)
		val jsonObject = r2?.jsonObject ?: return r2
		val sv = Utils.optStringX(jsonObject, "access_token")
		if(sv?.isNotEmpty() == true) {
			result.data = sv
		} else {
			result.data = null
			result.error = "getClientCredential: API returns empty client_credential."
		}
		return result
	}
	
	// client_credentialがまだ有効か調べる
	private fun verifyClientCredential(client_credential : String) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(this.instance)
		if(result.error != null) return result
		val instance = result.caption // same to instance
		
		val response = try {
			val request = Request.Builder()
				.url("https://$instance/api/v1/apps/verify_credentials")
				.header("Authorization", "Bearer $client_credential")
				.build()
			
			sendRequest(request)
		} catch(ex : Throwable) {
			log.trace(ex)
			return result.setError("$instance: " + Utils.formatError(ex, context.resources, R.string.network_error))
		}
		
		return readJson(result, response)
	}
	
	private fun prepareBrowserUrl(client_info : JSONObject) : String {
		val account = this.account
		
		// 認証ページURLを作る
		val browser_url = ("https://" + instance + "/oauth/authorize"
			+ "?client_id=" + Uri.encode(Utils.optStringX(client_info, "client_id"))
			+ "&response_type=code"
			+ "&redirect_uri=" + Uri.encode(REDIRECT_URL)
			+ "&scope=read write follow"
			+ "&scopes=read write follow"
			+ "&state=" + (if(account != null) "db:" + account.db_id else "host:" + instance)
			+ "&grant_type=authorization_code"
			+ "&approval_prompt=force"
			//		+"&access_type=offline"
			)
		
		return browser_url
	}
	
	// クライアントを登録してブラウザで開くURLを生成する
	fun authorize1(clientNameArg : String) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(this.instance)
		if(result.error != null) return result
		val instance = result.caption // same to instance
		
		// クライアントIDがアプリ上に保存されているか？
		val client_name = if(clientNameArg.isNotEmpty()) clientNameArg else DEFAULT_CLIENT_NAME
		val client_info = ClientInfo.load(instance, client_name)
		if(client_info != null) {
			
			var client_credential = Utils.optStringX(client_info, KEY_CLIENT_CREDENTIAL)
			
			// client_credential をまだ取得していないなら取得する
			if(client_credential == null || client_credential.isEmpty()) {
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
		
		// OAuth2 クライアント登録
		val response = try {
			val request = Request.Builder()
				.url("https://$instance/api/v1/apps")
				.post(RequestBody.create(MEDIA_TYPE_FORM_URL_ENCODED, "client_name=" + Uri.encode(client_name)
					+ "&redirect_uris=" + Uri.encode(REDIRECT_URL)
					+ "&scopes=read write follow"
				))
				.build()
			
			sendRequest(request)
		} catch(ex : Throwable) {
			log.trace(ex)
			return result.setError(instance + ": " + Utils.formatError(ex, context.resources, R.string.network_error))
		}
		
		val r2 = readJson(result, response)
		val jsonObject = r2?.jsonObject ?: return r2
		
		// {"id":999,"redirect_uri":"urn:ietf:wg:oauth:2.0:oob","client_id":"******","client_secret":"******"}
		jsonObject.put(KEY_AUTH_VERSION, AUTH_VERSION)
		ClientInfo.save(instance, client_name, jsonObject.toString())
		result.data = prepareBrowserUrl(jsonObject)
		return result
	}
	
	// oAuth2認証の続きを行う
	fun authorize2(clientNameArg : String, code : String) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(instance)
		if(result.error != null) return result
		
		val instance = result.caption // same to instance
		val client_name = if(clientNameArg.isNotEmpty()) clientNameArg else DEFAULT_CLIENT_NAME
		val client_info = ClientInfo.load(instance, client_name) ?: return result.setError("missing client id")
		
		var response = try {
			
			val post_content = ("grant_type=authorization_code"
				+ "&code=" + Uri.encode(code)
				+ "&client_id=" + Uri.encode(Utils.optStringX(client_info, "client_id"))
				+ "&redirect_uri=" + Uri.encode(REDIRECT_URL)
				+ "&client_secret=" + Uri.encode(Utils.optStringX(client_info, "client_secret"))
				+ "&scope=read write follow"
				+ "&scopes=read write follow")
			
			val request = Request.Builder()
				.url("https://$instance/oauth/token")
				.post(RequestBody.create(MEDIA_TYPE_FORM_URL_ENCODED, post_content))
				.build()
			
			sendRequest(request)
		} catch(ex : Throwable) {
			log.trace(ex)
			return result.setError(instance + ": " + Utils.formatError(ex, context.resources, R.string.network_error))
		}
		
		val token_info : JSONObject
		
		val r2 = readJson(result, response)
		val jsonObject = r2?.jsonObject ?: return r2
		
		// {"access_token":"******","token_type":"bearer","scope":"read","created_at":1492334641}
		jsonObject.put(KEY_AUTH_VERSION, AUTH_VERSION)
		token_info = jsonObject
		result.token_info = jsonObject
		
		val access_token = Utils.optStringX(token_info, "access_token")
		if(access_token == null || access_token.isEmpty()) {
			return result.setError("missing access_token in the response.")
		}
		
		response = try {
			
			// 認証されたアカウントのユーザ名を取得する
			val request = Request.Builder()
				.url("https://$instance/api/v1/accounts/verify_credentials")
				.header("Authorization", "Bearer $access_token")
				.build()
			
			sendRequest(request)
		} catch(ex : Throwable) {
			log.trace(ex)
			return result.setError(instance + ": " + Utils.formatError(ex, context.resources, R.string.network_error))
		}
		
		return readJson(result, response)
	}
	
	// アクセストークン手動入力でアカウントを更新する場合
	// verify_credentialsを呼び出す
	fun checkAccessToken(access_token : String) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(instance)
		if(result.error != null) return result
		
		val token_info = JSONObject()
		val response = try {
			
			// 指定されたアクセストークンを使って token_info を捏造する
			token_info.put("access_token", access_token)
			
			// 認証されたアカウントのユーザ名を取得する
			val request = Request.Builder()
				.url("https://$instance/api/v1/accounts/verify_credentials")
				.header("Authorization", "Bearer $access_token")
				.build()
			
			sendRequest(request)
		} catch(ex : Throwable) {
			log.trace(ex)
			return result.setError(instance + ": " + Utils.formatError(ex, context.resources, R.string.network_error))
		}
		
		val r2 = readJson(result, response)
		r2?.jsonObject ?: return r2
		
		// credentialを読めたならtoken_infoを保存したい
		result.token_info = token_info
		return result
	}
	
	fun getHttp(url : String) : TootApiResult? {
		val result = TootApiResult.makeWithCaption(url)
		if(result.error != null) return result
		
		val response = try {
			sendRequest(Request.Builder().url(url).build(), url)
		} catch(ex : Throwable) {
			log.trace(ex)
			return result.setError(url + ": " + Utils.formatError(ex, context.resources, R.string.network_error))
		}
		try {
			if(callback.isApiCancelled) return null
			val request = response.request()
			if( request != null ){
				callback.publishApiProgress(context.getString(R.string.reading_api, request.method(), url))
			}
			result.readBodyString(response)
			
			if(callback.isApiCancelled) return null
			callback.publishApiProgress(context.getString(R.string.parsing_response))
			if(result.isErrorOrEmptyBody()) return result
			
			result.data = result.bodyString
			
		} catch(ex : Throwable) {
			log.trace(ex)
			result.error = Utils.formatResponse(response, result.caption, result.bodyString ?: "no information")
		}
		return result
	}
	
	private fun sendRequest(request : Request, showPath : String? = null) : Response {
		callback.publishApiProgress(context.getString(R.string.request_api, request.method(), showPath ?: request.url().encodedPath()))
		val call = ok_http_client.newCall(request)
		call_callback?.onCallCreated(call)
		return call.execute()
	}
	
	private fun readJson(result : TootApiResult, response : Response) : TootApiResult? {
		try {
			if(callback.isApiCancelled) return null
			val request = response.request()
			if( request != null ){
				callback.publishApiProgress(context.getString(R.string.reading_api, request.method(), request.url().encodedPath()))
			}
			result.readBodyString(response)
	
			if(callback.isApiCancelled) return null
			callback.publishApiProgress(context.getString(R.string.parsing_response))
			if(result.isErrorOrEmptyBody()) return result
			
			val bodyString = result.bodyString
			if(bodyString?.startsWith("[") == true) {
				result.data = JSONArray(bodyString)
			} else if(bodyString?.startsWith("{") == true) {
				val json = JSONObject(bodyString)
				val error = Utils.optStringX(json, "error")
				if(error != null) {
					result.error = "API returns error: $error"
				} else {
					result.data = json
				}
			} else {
				result.error = context.getString(R.string.response_not_json) + "\n" + bodyString
			}
			
		} catch(ex : Throwable) {
			log.trace(ex)
			result.error = Utils.formatResponse(response, result.caption, result.bodyString ?: "no information")
		}
		return result
		
	}
	
	//	private fun parseResponse(tokenInfo : JSONObject?, response : Response) : TootApiResult? {
	//		try {
	//			if(callback.isApiCancelled) return null
	//
	//			if(! response.isSuccessful) {
	//				return TootApiResult(response, Utils.formatResponse(response, instance ?: "(no instance)"))
	//			}
	//
	//			val bodyString = response.body()?.string() ?: throw RuntimeException("missing response body.")
	//			if(callback.isApiCancelled) return null
	//
	//			callback.publishApiProgress(context.getString(R.string.parsing_response))
	//			return if(bodyString.startsWith("{")) {
	//
	//				val obj = JSONObject(bodyString)
	//
	//				val error = Utils.optStringX(obj, "error")
	//
	//				if(error != null)
	//					TootApiResult(context.getString(R.string.api_error, error))
	//				else
	//					TootApiResult(response, tokenInfo, bodyString, obj)
	//
	//			} else if(bodyString.startsWith("[")) {
	//				val array = JSONArray(bodyString)
	//				TootApiResult(response, tokenInfo, bodyString, array)
	//			} else {
	//				TootApiResult(response, Utils.formatResponse(response, instance ?: "(no instance)", bodyString))
	//			}
	//		} catch(ex : Throwable) {
	//			TootApiClient.log.trace(ex)
	//			return TootApiResult(Utils.formatError(ex, "API data error"))
	//		}
	//
	//	}
	
}