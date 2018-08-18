package jp.juggler.subwaytooter.api.entity

import org.json.JSONObject

import jp.juggler.subwaytooter.util.parseLong
import jp.juggler.subwaytooter.util.parseString

class TootReport(src:JSONObject): TimelineItem() {
	
	val id : EntityId
	private val action_taken : String? // The action taken in response to the report

	init{
		id = EntityIdLong( src.parseLong("id") ?: - 1L)
		action_taken = src.parseString("action_taken")
	}
	
	override fun getOrderId() =id
}
