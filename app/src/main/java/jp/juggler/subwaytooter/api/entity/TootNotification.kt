package jp.juggler.subwaytooter.api.entity

import org.json.JSONObject

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.util.Utils

class TootNotification(
	val json : JSONObject,
	val id : Long,
	val type : String,    //	One of: "mention", "reblog", "favourite", "follow"
	private val created_at : String?,    //	The time the notification was created
	val account : TootAccount?,    //	The Account sending the notification to the user
	val status : TootStatus?    //	The Status associated with the notification, if applicable
) :TimelineItem(){
	
	val time_created_at : Long
	
	init {
		time_created_at = TootStatus.parseTime(created_at)
	}
	
	constructor(parser : TootParser, src : JSONObject) : this(
		json = src,
		id = Utils.optLongX(src, "id"),
		type = src.notEmptyOrThrow("type"),
		created_at = Utils.optStringX(src,"created_at"),
		account = TootAccount.parse(parser.context, parser.accessInfo, src.optJSONObject("account"), ServiceType.MASTODON),
		status = TootStatus.parse(parser, src.optJSONObject("status"), ServiceType.MASTODON)
	)
	
	companion object {
		const val TYPE_MENTION = "mention"
		const val TYPE_REBLOG = "reblog"
		const val TYPE_FAVOURITE = "favourite"
		const val TYPE_FOLLOW = "follow"
	}
}
