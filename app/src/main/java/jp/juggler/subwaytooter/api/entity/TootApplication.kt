package jp.juggler.subwaytooter.api.entity

import org.json.JSONObject

import jp.juggler.subwaytooter.util.parseString

class TootApplication(
	val name : String?,
	@Suppress("unused") private val website : String?
) {
	
	constructor(src : JSONObject) : this(
		name = src.parseString("name"),
		website = src.parseString("website")
	)
}
