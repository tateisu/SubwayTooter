package jp.juggler.subwaytooter.api

import android.content.Context
import android.net.Uri
import android.text.TextUtils

import org.json.JSONArray
import org.json.JSONObject

import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils
import okhttp3.Request
import okhttp3.Response

object MSPClient {
	private val log = LogCategory("MSPClient")
	
	private val url_token = "http://mastodonsearch.jp/api/v1.0.1/utoken"
	private val url_search = "http://mastodonsearch.jp/api/v1.0.1/cross"
	private val api_key = "e53de7f66130208f62d1808672bf6320523dcd0873dc69bc"
	
	private val ok_http_client = App1.ok_http_client
	
	fun search(
		context : Context,
		query : String,
		max_id : String,
		callback : TootApiCallback
	) : TootApiResult? {

		// ユーザトークンを読む
		val pref = Pref.pref(context)
		var user_token = pref.getString(Pref.KEY_MASTODON_SEARCH_PORTAL_USER_TOKEN, null)

		var response : Response
		
		for(nTry in 0 .. 9) {
			// ユーザトークンがなければ取得する
			if(user_token == null || user_token.isEmpty() ) {
				
				callback.publishApiProgress("get MSP user token...")
				
				val url = url_token + "?apikey=" + Uri.encode(api_key)
				
				try {
					val request = Request.Builder()
						.url(url)
						.build()
					val call = ok_http_client.newCall(request)
					response = call.execute()
				} catch(ex : Throwable) {
					log.trace(ex)
					return TootApiResult(Utils.formatError(ex, context.resources, R.string.network_error))
				}
				
				if(callback.isApiCancelled) return null
				val bodyString = response.body()?.string()
				if(callback.isApiCancelled) return null

				if(! response.isSuccessful) {
					val result = TootApiResult( 0,response = response)
					val code = response.code()

					if(response.code() < 400 || bodyString == null ) {
						result.error = Utils.formatResponse(response, "マストドン検索ポータル", bodyString ?: "(no information)")
						return result
					}

					result.bodyString = bodyString

					try {
						val obj = JSONObject(bodyString)
						val type = Utils.optStringX(obj,"type")
						val error = Utils.optStringX(obj,"error")
						if( error != null && error.isNotEmpty() ){
							result.error = "API returns error. $code, $type, $error"
							return result
						}
					} catch(ex : Throwable) {
						log.trace(ex)
					}
					result.error = Utils.formatResponse(response,"マストドン検索ポータル",bodyString)
					return result
				}
				
				try {
					user_token = JSONObject(bodyString).getJSONObject("result").getString("token")
					if( user_token == null || user_token.isEmpty() ) {
						return TootApiResult("Can't get MSP user token. response=$bodyString")
					}
					pref.edit().putString(Pref.KEY_MASTODON_SEARCH_PORTAL_USER_TOKEN, user_token).apply()
				} catch(ex : Throwable) {
					log.trace(ex)
					return TootApiResult(Utils.formatError(ex, "API data error"))
				}
			}

			// ユーザトークンを使って検索APIを呼び出す
			callback.publishApiProgress("waiting search result...")
			val url = (url_search
				+ "?apikey=" + Uri.encode(api_key)
				+ "&utoken=" + Uri.encode(user_token)
				+ "&q=" + Uri.encode(query)
				+ "&max=" + Uri.encode(max_id))
			
			try {
				val request = Request.Builder()
					.url(url)
					.build()
				val call = ok_http_client.newCall(request)
				response = call.execute()
			} catch(ex : Throwable) {
				log.trace(ex)
				return TootApiResult(Utils.formatError(ex, context.resources, R.string.network_error))
			}
			
			if(callback.isApiCancelled) return null
			val bodyString = response.body()?.string()
			if(callback.isApiCancelled) return null
			
			if(! response.isSuccessful) {
				val result = TootApiResult( 0,response = response)
				val code = response.code()
				if(response.code() < 400 || bodyString == null ) {
					result.error = Utils.formatResponse(response, "マストドン検索ポータル", bodyString ?: "(no information)")
					return result
				}
				try {
					val error = JSONObject(bodyString).getJSONObject("error")
					val detail = error.optString("detail")
					val type = error.optString("type")
					// ユーザトークンがダメなら生成しなおす
					if("utoken" == detail ) {
						user_token = null
						continue
					}
					result.error = "API returns error. $code, $type, $detail"
					return result
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				result.error = Utils.formatResponse(response,"マストドン検索ポータル",bodyString)
				return result
			}
			
			try{
				if( bodyString != null ) {
					val array = JSONArray(bodyString)
					return TootApiResult(0,response = response, bodyString = bodyString, data = array)
				}
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			return TootApiResult( response, Utils.formatResponse(response,"マストドン検索ポータル",bodyString))
		}
		return TootApiResult("MSP user token retry exceeded.")
	}
	
	fun getMaxId(array : JSONArray, max_id : String) : String {
		// max_id の更新
		val size = array.length()
		if(size > 0) {
			val item = array.optJSONObject(size - 1)
			if(item != null) {
				val sv = item.optString("msp_id")
				if(! TextUtils.isEmpty(sv)) {
					return sv
				}
			}
		}
		return max_id
	}
	
}
