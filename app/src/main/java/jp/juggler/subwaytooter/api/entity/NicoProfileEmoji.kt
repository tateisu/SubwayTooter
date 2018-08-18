package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.util.notEmptyOrThrow
import jp.juggler.subwaytooter.util.parseLong
import jp.juggler.subwaytooter.util.parseString

import org.json.JSONObject

class NicoProfileEmoji(
	val url : String,
	private val shortcode : String,
	@Suppress("unused") private val account_url : String?,
	@Suppress("unused") private val account_id : EntityId?
) : Mappable<String> {
	
	constructor(src : JSONObject) : this(
		url = src.notEmptyOrThrow("url"),
		shortcode = src.notEmptyOrThrow("shortcode"),
		account_url = src.parseString("account_url"),
		account_id = EntityId.mayNull( src.parseLong("account_id") )
	)
	
	override val mapKey : String
		get() = shortcode
	
}


