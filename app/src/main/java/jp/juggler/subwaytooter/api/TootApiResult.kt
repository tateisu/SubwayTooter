package jp.juggler.subwaytooter.api

import jp.juggler.subwaytooter.R
import org.json.JSONArray
import org.json.JSONObject

import java.util.regex.Pattern

import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils
import okhttp3.Response
import okhttp3.WebSocket

open class TootApiResult(
	val dummy : Int,
	var error : String? = null,
	var response : Response? = null,
	var data : Any? = null,
	var bodyString : String? = null,
	var token_info : JSONObject? = null
) {
	
	companion object {
		private val log = LogCategory("TootApiResult")
		
		private val reLinkURL = Pattern.compile("<([^>]+)>;\\s*rel=\"([^\"]+)\"")
		
		private const val MIMUMEDON = "mimumedon.com"
		private const val MIMUMEDON_ERROR = "mimumedon.comには対応しません"
		
		const val NO_INSTANCE = "missing instance name"
		const val NO_INFORMATION = "(no information)"
		
		fun makeWithCaption(caption : String?) : TootApiResult {
			val result = TootApiResult()
			if(caption == null || caption.isEmpty()) {
				result.error = NO_INSTANCE
			} else {
				result.caption = caption
				if(MIMUMEDON.equals(caption, ignoreCase = true)) {
					result.error = MIMUMEDON_ERROR
				}
			}
			return result
		}
	}
	
	var link_older : String? = null // より古いデータへのリンク
	var link_newer : String? = null // より新しいデータへの
	var caption : String = "?"
	
	
	constructor() : this(0)
	
	constructor(
		error : String?
	) : this(0, error = error)
	
	constructor(
		response : Response,
		error : String
	) : this(0,
		response = response,
		error = error
	)
	
	constructor(
		response : Response,
		bodyString : String,
		data : Any?
	) : this(0,
		response = response,
		bodyString = bodyString,
		data = data
	) {
		if(data is JSONArray) {
			parseLinkHeader(response, data)
		}
	}
	
	//		this.token_info = token_info
	
	constructor(socket : WebSocket) : this(0, data = socket)
	
	
	// return result.setError(...) と書きたい
	fun setError(error:String) :TootApiResult{
		this.error = error
		return this
	}
	
	// レスポンスボディを読む
	fun readBodyString(response : Response) {
		this.response = response
		this.bodyString = response.body()?.string()
	}

	// レスポンスがエラーかボディがカラならエラー状態を設定する
	// エラーがあれば真を返す
	fun isErrorOrEmptyBody() :Boolean{
		val response = this.response ?: throw NotImplementedError("not calling readBodyString")
		if(! response.isSuccessful || bodyString == null ) {
			error = Utils.formatResponse(response, caption, bodyString ?: NO_INFORMATION)
		}
		return error != null
	}
	
	
	
	
	private fun parseLinkHeader(
		response : Response,
		array : JSONArray
	) {
		log.d("array size=%s", array.length() )
		
		val sv = response.header("Link")
		if(sv == null) {
			log.d("missing Link header")
		} else {
			// Link:  <https://mastodon.juggler.jp/api/v1/timelines/home?limit=XX&max_id=405228>; rel="next",
			//        <https://mastodon.juggler.jp/api/v1/timelines/home?limit=XX&since_id=436946>; rel="prev"
			val m = reLinkURL.matcher(sv)
			while(m.find()) {
				val url = m.group(1)
				val rel = m.group(2)
				//	log.d("Link %s,%s",rel,url);
				if("next" == rel) link_older = url
				if("prev" == rel) link_newer = url
			}
		}
	}
	

	val jsonObject :JSONObject?
		get() = data as? JSONObject

	val jsonArray :JSONArray?
		get() = data as? JSONArray
	
	val string: String?
		get() = data as? String
	
	
}
