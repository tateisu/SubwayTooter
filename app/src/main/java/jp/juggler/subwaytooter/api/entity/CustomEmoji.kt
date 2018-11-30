package jp.juggler.subwaytooter.api.entity

import jp.juggler.util.notEmptyOrThrow
import jp.juggler.util.parseString
import org.json.JSONArray

import org.json.JSONObject

class CustomEmoji(
	val shortcode : String, // shortcode (コロンを含まない)
	val url : String, // 画像URL
	val static_url : String?, // アニメーションなしの画像URL
	val aliases : ArrayList<String>? = null,
	val alias:String? =null
) : Mappable<String> {
	
	fun makeAlias(alias : String) = CustomEmoji (
		shortcode= this.shortcode,
		url = this.url,
		static_url = this.static_url,
		alias = alias
	)
	
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
			
			CustomEmoji(
				shortcode = src.parseString("name") ?: error("missing name"),
				url = url,
				static_url = url,
				aliases = parseAliases(src.optJSONArray("aliases"))
			)
		}
		
		private fun parseAliases(src : JSONArray?) : ArrayList<String>? {
			var dst = null as ArrayList<String>?
			if(src != null) {
				val size = src.length()
				if( size > 0){
					dst = ArrayList(size)
					for(i in 0 until size) {
						val str = src.parseString(i) ?: continue
						if(str.isNotEmpty()) {
							dst.add(str)
						}
					}
				}
			}
			return if(dst?.isNotEmpty() == true ) dst else null
		}
	}
	
}
