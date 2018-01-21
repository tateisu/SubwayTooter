package jp.juggler.subwaytooter.api.entity

import org.json.JSONObject

import jp.juggler.subwaytooter.util.parseLong
import jp.juggler.subwaytooter.util.parseString

class NicoProfileEmoji(
	val url : String,
	private val shortcode : String,
	@Suppress("unused") private val account_url : String?,
	@Suppress("unused") private val account_id : Long
) : Mappable<String> {
	
	constructor(src : JSONObject) : this(
		url = src.notEmptyOrThrow("url"),
		shortcode = src.notEmptyOrThrow("shortcode"),
		account_url = src.parseString("account_url"),
		account_id = src.parseLong("account_id") ?: TootAccount.INVALID_ID
	)
	
	override val mapKey : String
		get() = shortcode
	
}


