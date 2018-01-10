package jp.juggler.subwaytooter.api.entity

import org.json.JSONArray
import org.json.JSONObject

import java.util.HashMap

import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils

class CustomEmoji(
	// shortcode (コロンを含まない)
	val shortcode : String,
	
	// 画像URL
	val url : String,
	
	// アニメーションなしの画像URL
	val static_url : String?
	
){
	constructor(src : JSONObject) :this(
		shortcode = src.notEmptyOrThrow("shortcode"),
		url = src.notEmptyOrThrow("url"),
		static_url = Utils.optStringX(src, "static_url")
	)
	class Map : HashMap<String, CustomEmoji>() // キー： shortcode (コロンを含まない)
	
	companion object {
		internal val log = LogCategory("CustomEmoji")
		
		fun parseMap(instance : String, src : JSONArray?, log : LogCategory = EntityUtil.log) : Map? {
			if(src == null) return null
			val dst = Map()
			for(i in 0 until src.length()) {
				val item = parseItem(::CustomEmoji, src.optJSONObject(i), log)
				if(item != null) dst.put(item.shortcode, item)
			}
			if(dst.isNotEmpty()) log.d("parseMap: parse %d emojis for %s.", dst.size, instance)
			return dst
		}
	}
	
}
