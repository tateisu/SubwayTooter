package jp.juggler.subwaytooter.api.entity

import jp.juggler.util.parseString
import org.json.JSONObject

class TootReport(src : JSONObject) : TimelineItem() {
	
	val id : EntityId
	private val action_taken : String? // The action taken in response to the report
	
	init {
		id = EntityId.mayDefault(src.parseString("id"))
		action_taken = src.parseString("action_taken")
	}
	
	override fun getOrderId() = id
}
