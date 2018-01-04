package jp.juggler.subwaytooter.api

import android.content.Context
import android.net.Uri

import org.json.JSONArray
import org.json.JSONObject

import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils
import okhttp3.Request
import okhttp3.Response

object TootsearchClient {
	private val log = LogCategory("TootsearchClient")
	
	private val ok_http_client = App1.ok_http_client
	
	fun search(
		context : Context,
		query : String,
		max_id : String, // 空文字列、もしくはfromに指定するパラメータ
		callback : TootApiCallback
	) : TootApiResult? {
		val url = ("https://tootsearch.chotto.moe/api/v1/search"
			+ "?sort=" + Uri.encode("created_at:desc")
			+ "&from=" + max_id
			+ "&q=" + Uri.encode(query))
		
		val response : Response
		try {
			val request = Request.Builder()
				.url(url)
				.build()
			
			callback.publishApiProgress("waiting search result...")
			val call = ok_http_client.newCall(request)
			response = call.execute()
		} catch(ex : Throwable) {
			log.trace(ex)
			return TootApiResult(Utils.formatError(ex, context.resources, R.string.network_error))
		}
		
		if(callback.isApiCancelled) return null
		val bodyString = response.body()?.string()
		if(! response.isSuccessful || bodyString == null ) {
			log.d("response failed.")
			return TootApiResult(Utils.formatResponse(response, "Tootsearch",bodyString ?: "(no information)"))
		}
		
		return try {
			TootApiResult(response,bodyString,JSONObject(bodyString))
		} catch(ex : Throwable) {
			log.trace(ex)
			TootApiResult(Utils.formatError(ex, "API data error"))
		}
	}
	
	fun getHits(root : JSONObject) : JSONArray? {
		val hits = root.optJSONObject("hits")
		return hits?.optJSONArray("hits")
	}
	
	// returns the number for "from" parameter of next page.
	// returns "" if no more next page.
	fun getMaxId(root : JSONObject, old : String) : String {
		val old_from = Utils.parse_int(old, 0)
		val hits2 = getHits(root)
		if(hits2 != null) {
			val size = hits2.length()
			return if(size == 0) "" else Integer.toString(old_from + hits2.length())
		}
		return ""
	}
	
}
