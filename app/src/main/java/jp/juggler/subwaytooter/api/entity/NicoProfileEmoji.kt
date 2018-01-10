package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.util.LogCategory

import org.json.JSONArray
import org.json.JSONObject

import java.util.HashMap

import jp.juggler.subwaytooter.util.Utils

class NicoProfileEmoji(
	val url : String,
	val shortcode : String,
	private val account_url : String?,
	private val account_id : Long

) {
	
	constructor(src : JSONObject) : this(
		url = src.notEmptyOrThrow("url"),
		shortcode = src.notEmptyOrThrow("shortcode"),
		account_url = Utils.optStringX(src, "account_url"),
		account_id = Utils.optLongX(src, "account_id", TootAccount.INVALID_ID)
	)
	
	class Map : HashMap<String, NicoProfileEmoji>()
	
	companion object {
		
		fun parseMap(src : JSONArray?, log : LogCategory = EntityUtil.log) : Map? {
			if(src == null) return null
			val count = src.length()
			if(count == 0) return null
			val dst = Map()
			for(i in 0 until count) {
				val item = parseItem(::NicoProfileEmoji, src.optJSONObject(i), log)
				if(item != null) dst.put(item.shortcode, item)
			}
			return if(dst.isEmpty()) null else dst
		}
	}
}


