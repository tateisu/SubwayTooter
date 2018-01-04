package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.util.LogCategory

import org.json.JSONArray
import org.json.JSONObject

import java.util.HashMap

import jp.juggler.subwaytooter.util.Utils

class NicoProfileEmoji(src : JSONObject) {
	
	val url : String
	private val account_url : String
	private val account_id : Long
	val shortcode : String
	
	init {
		this.url = Utils.optStringX(src, "url") ?: ""
		this.account_url = Utils.optStringX(src, "account_url") ?: ""
		this.account_id = Utils.optLongX(src, "account_id", - 1L)
		this.shortcode = Utils.optStringX(src, "shortcode") ?: ""
		
		when {
			url.isEmpty() -> throw RuntimeException("missing url")
			account_url.isEmpty() -> throw RuntimeException("missing account_url")
			account_id == - 1L -> throw RuntimeException("missing account_id")
			shortcode.isEmpty() -> throw RuntimeException("missing shortcode")
		}
		
	}
	
	class Map : HashMap<String, NicoProfileEmoji>()
	
	companion object {
		private val log = LogCategory("NicoProfileEmoji")
		
		fun parse(src : JSONObject?) : NicoProfileEmoji? {
			src ?: return null
			return try {
				NicoProfileEmoji(src)
			} catch(ex : Throwable) {
				log.e(ex, "parse failed.")
				null
			}
		}
		
		fun parseMap(src : JSONArray?) : Map? {
			if(src == null) return null
			val count = src.length()
			if(count == 0) return null
			val dst = Map()
			for(i in 0 until count) {
				val item = parse(src.optJSONObject(i))
				if(item != null) dst.put(item.shortcode, item)
			}
			return if(dst.isEmpty()) null else dst
		}
	}
}


