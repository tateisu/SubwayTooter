package jp.juggler.subwaytooter.api.entity

import org.json.JSONObject
import jp.juggler.subwaytooter.util.Utils

class CustomEmoji(
	val shortcode : String, // shortcode (コロンを含まない)
	val url : String, // 画像URL
	val static_url : String? // アニメーションなしの画像URL
) : Mappable<String> {
	
	constructor(src : JSONObject) : this(
		shortcode = src.notEmptyOrThrow("shortcode"),
		url = src.notEmptyOrThrow("url"),
		static_url = Utils.optStringX(src, "static_url")
	)
	
	override val mapKey : String
		get() = shortcode
}
