package jp.juggler.subwaytooter.api.entity

import org.json.JSONObject

import jp.juggler.subwaytooter.util.Utils

class TootReport(
	val id : Long,
	private val action_taken : String? // The action taken in response to the report
) :TimelineItem() {
	
	constructor(src : JSONObject) : this(
		id = Utils.optLongX(src, "id"),
		action_taken = Utils.optStringX(src, "action_taken")
	)
}
