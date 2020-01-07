package jp.juggler.subwaytooter.api.entity

import jp.juggler.util.JsonObject

class NicoProfileEmoji(
	val url : String,
	private val shortcode : String,
	@Suppress("unused") private val account_url : String?,
	@Suppress("unused") private val account_id : EntityId
) : Mappable<String> {
	
	constructor(src : JsonObject, shortcode : String? = null) : this(
		url = src.notEmptyOrThrow("url"),
		shortcode = shortcode ?: src.notEmptyOrThrow("shortcode"),
		account_url = src.parseString("account_url"),
		account_id = EntityId.mayDefault(src.parseString("account_id"))
	)
	
	constructor(src : JsonObject) : this(src, null)
	
	override val mapKey : String
		get() = shortcode
	
}
