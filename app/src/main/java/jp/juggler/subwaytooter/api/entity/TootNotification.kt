package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.util.notEmptyOrThrow
import jp.juggler.util.parseString
import org.json.JSONObject

class TootNotification(parser : TootParser, src : JSONObject) : TimelineItem() {
	
	companion object {
		// 言及と返信
		const val TYPE_MENTION = "mention" // Mastodon,Misskey
		const val TYPE_REPLY = "reply" // Misskey (メンションとReplyは別の物らしい

		// ブーストとリノート
		const val TYPE_REBLOG = "reblog" // Mastodon
		const val TYPE_RENOTE = "renote" // Misskey
		const val TYPE_QUOTE = "quote" // Misskey 引用Renote
		
		// フォロー
		const val TYPE_FOLLOW = "follow" // Mastodon,Misskey
		const val TYPE_UNFOLLOW = "unfollow" // Mastodon,Misskey
		
		const val TYPE_FAVOURITE = "favourite"
		const val TYPE_REACTION = "reaction"

		// 投票
		const val TYPE_VOTE = "poll_vote"
		const val TYPE_FOLLOW_REQUEST = "receiveFollowRequest"
	
		// (Mastodon 2.8)投票完了
		const val TYPE_POLL = "poll"
	}
	
	val json : JSONObject
	val id : EntityId
	val type : String    //	One of: "mention", "reblog", "favourite", "follow"
	val accountRef : TootAccountRef?    //	The Account sending the notification to the user
	val status : TootStatus?    //	The Status associated with the notification, if applicable
	var reaction : String? = null
	
	private val created_at : String?    //	The time the notification was created
	val time_created_at : Long
	
	val account : TootAccount?
		get() = accountRef?.get()
	
	override fun getOrderId() = id
	
	init {
		json = src
		
		if( parser.serviceType == ServiceType.MISSKEY){
			id = EntityId.mayDefault(src.parseString("id"))
			
			type = src.notEmptyOrThrow("type")
			
			created_at = src.parseString("createdAt")
			time_created_at = TootStatus.parseTime(created_at)
			
			accountRef = TootAccountRef.mayNull(parser, parser.account(src.optJSONObject("user")))
			status = parser.status(src.optJSONObject("note"))
			
			reaction = src.parseString("reaction")
			
			
			// Misskeyの通知APIはページネーションをIDでしか行えない
			// これは改善される予定 https://github.com/syuilo/misskey/issues/2275
			
		}else{
			id = EntityId.mayDefault(src.parseString("id"))
			
			type = src.notEmptyOrThrow("type")
			created_at = src.parseString("created_at")
			time_created_at = TootStatus.parseTime(created_at)
			accountRef = TootAccountRef.mayNull(parser, parser.account(src.optJSONObject("account")))
			status = parser.status(src.optJSONObject("status"))
			
		}
	}
	
}
