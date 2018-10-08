package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.util.parseLong
import org.json.JSONObject

class TootConversationSummary(parser:TootParser,src:JSONObject) : TimelineItem(){

	val id : EntityId
	val accounts: ArrayList<TootAccountRef>
	val last_status : TootStatus
	
	init{
		this.id = EntityId.mayDefault(src.parseLong("id"))
		this.accounts = parser.accountList( src.optJSONArray("accounts"))
		this.last_status = parser.status(src.optJSONObject("last_status")) !!
	}
	
	override fun getOrderId() =id
}
