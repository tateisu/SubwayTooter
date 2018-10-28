package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.util.parseLong
import org.json.JSONObject

class TootConversationSummary(parser:TootParser,src:JSONObject) : TimelineItem(){

	val id : EntityId
	val accounts: ArrayList<TootAccountRef>
	val last_status : TootStatus
	var unread : Boolean // タップ時にクリアする
	
	init{
		this.id = EntityId.mayDefault(src.parseLong("id"))
		this.accounts = parser.accountList( src.optJSONArray("accounts"))
		this.last_status = parser.status(src.optJSONObject("last_status")) !!
		this.unread = src.optBoolean("unread")

		this.last_status.conversationSummary = this

		// TODO 未読状態の表示
		// TODO 未読の会話を開いたら POST /api/v1/conversations/${id}/read で未読クリア
		// TODO 未読の会話を開いたら未読状態の表示をクリア
	}
	
	override fun getOrderId() =id
}
