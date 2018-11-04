package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.util.notEmptyOrThrow
import jp.juggler.subwaytooter.util.parseString

import org.json.JSONObject

class CustomEmoji(
	val shortcode : String, // shortcode (コロンを含まない)
	val url : String, // 画像URL
	val static_url : String? // アニメーションなしの画像URL
) : Mappable<String> {
	
	override val mapKey : String
		get() = shortcode
	
	companion object {
		val decode : (JSONObject) -> CustomEmoji = { src ->
			CustomEmoji(
				shortcode = src.notEmptyOrThrow("shortcode"),
				url = src.notEmptyOrThrow("url"),
				static_url = src.parseString("static_url")
			)
		}
		val decodeMisskey : (JSONObject) -> CustomEmoji = { src ->
			val url = src.parseString("url") ?: error("missing url")
			val name = src.parseString("name") ?: error("missing name")
			
			// 使い方が分からない val aliases = parseAliases(src.optJSONArray("aliases"))
			
			CustomEmoji(
				shortcode = name,
				url = url,
				static_url = url
			)
		}
		
//		private fun parseAliases(src : JSONArray?) : ArrayList<String>? {
//			var dst = null as ArrayList<String>?
//			if(src != null) {
//				val size = src.length()
//				for(i in 0 until size) {
//					val str = src.parseString(i) ?: continue
//					if(str.isNotEmpty()) {
//						if(dst == null) dst = ArrayList(size)
//						dst.add(str)
//					}
//				}
//			}
//			return dst
//		}
	}
	
}
