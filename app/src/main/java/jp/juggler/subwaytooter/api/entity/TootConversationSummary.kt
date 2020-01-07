package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.util.JsonObject

class TootConversationSummary(parser : TootParser, src : JsonObject) : TimelineItem() {
	
	val id : EntityId
	val accounts : ArrayList<TootAccountRef>
	val last_status : TootStatus
	var unread : Boolean // タップ時にクリアする
	
	init {
		this.id = EntityId.mayDefault(src.parseString("id"))
		this.accounts = parser.accountList(src.parseJsonArray("accounts"))
		this.last_status = parser.status(src.parseJsonObject("last_status")) !!
		this.unread = src.optBoolean("unread")
		
		this.last_status.conversationSummary = this
	}
	
	override fun getOrderId() = id
}
