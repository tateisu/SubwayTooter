package jp.juggler.subwaytooter.api.entity

import jp.juggler.util.JsonArray
import jp.juggler.util.JsonObject
import jp.juggler.util.notEmpty

class CustomEmoji(
	val shortcode : String, // shortcode (コロンを含まない)
	val url : String, // 画像URL
	val static_url : String?, // アニメーションなしの画像URL
	val aliases : ArrayList<String>? = null,
	val alias : String? = null,
	val visible_in_picker : Boolean = true,
	val category: String? = null
) : Mappable<String> {
	
	fun makeAlias(alias : String) = CustomEmoji(
		shortcode = this.shortcode,
		url = this.url,
		static_url = this.static_url,
		alias = alias
	)
	
	override val mapKey : String
		get() = shortcode
	
	companion object {
		
		val decode : (JsonObject) -> CustomEmoji = { src ->
			CustomEmoji(
				shortcode = src.notEmptyOrThrow("shortcode"),
				url = src.notEmptyOrThrow("url"),
				static_url = src.string("static_url"),
				visible_in_picker = src.optBoolean("visible_in_picker", true),
				category =src.string("category")
			)
		}
		
		val decodeMisskey : (JsonObject) -> CustomEmoji = { src ->
			val url = src.string("url") ?: error("missing url")
			
			CustomEmoji(
				shortcode = src.string("name") ?: error("missing name"),
				url = url,
				static_url = url,
				aliases = parseAliases(src.jsonArray("aliases"))
			)
		}
		
		private fun parseAliases(src : JsonArray?) : ArrayList<String>? {
			var dst = null as ArrayList<String>?
			if(src != null) {
				val size = src.size
				if(size > 0) {
					dst = ArrayList(size)
					src.forEach {
						val str = it?.toString()?.notEmpty()
						if(str != null ) dst.add(str)
					}
				}
			}
			return if(dst?.isNotEmpty() == true) dst else null
		}
	}
	
}
