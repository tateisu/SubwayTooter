package jp.juggler.subwaytooter.api.entity

import org.json.JSONObject

import jp.juggler.subwaytooter.util.Utils

class TootApplication (
	val name : String?,
	private val website : String?
){
	constructor(src:JSONObject):this(
		name = Utils.optStringX(src, "name"),
		website = Utils.optStringX(src, "website")
	)
}
