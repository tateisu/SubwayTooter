package jp.juggler.subwaytooter.api.entity

import org.json.JSONArray
import org.json.JSONObject

import java.util.ArrayList
import java.util.HashMap

import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils

class CustomEmoji private constructor(src : JSONObject) {
	
	// shortcode (コロンを含まない)
	val shortcode : String
	
	// 画像URL
	val url : String
	
	// アニメーションなしの画像URL
	val static_url : String?
	
	class List : ArrayList<CustomEmoji>()
	
	class Map : HashMap<String, CustomEmoji>() // キー： shortcode (コロンを含まない)
	
	init {
		var sv : String?
		
		sv = Utils.optStringX(src, "shortcode")
		if(sv == null || sv.isEmpty()) throw RuntimeException("missing shortcode")
		this.shortcode = sv
		//
		sv = Utils.optStringX(src, "url")
		if(sv == null || sv.isEmpty()) throw RuntimeException("missing url")
		this.url = sv
		//
		this.static_url = Utils.optStringX(src, "static_url") // may null
		
	}
	
	companion object {
		internal val log = LogCategory("CustomEmoji")
		
		fun parse(src : JSONObject?) : CustomEmoji? {
			if(src == null) return null
			return try {
				CustomEmoji(src)
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "parse failed.")
				null
			}
		}
		
		fun parseList(src : JSONArray?) : List? {
			
			if(src == null) return null
			
			val src_length = src.length()
			if(src_length == 0) return null
			
			val dst = List()
			var i = 0
			val ie = src.length()
			while(i < ie) {
				val item = parse(src.optJSONObject(i))
				if(item != null) dst.add(item)
				++ i
			}
			
			if(dst.isEmpty()) return null
			
			dst.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER, { it.shortcode }))
			
			return dst
		}
		
		fun parseMap(src : JSONArray?, instance : String) : Map? {
			if(src == null) return null
			val dst = Map()
			var i = 0
			val ie = src.length()
			while(i < ie) {
				val item = parse(src.optJSONObject(i))
				if(item != null) dst.put(item.shortcode, item)
				++ i
			}
			log.d("parseMap: parse %d emojis for %s.", dst.size, instance)
			return dst
		}
	}
	
}
