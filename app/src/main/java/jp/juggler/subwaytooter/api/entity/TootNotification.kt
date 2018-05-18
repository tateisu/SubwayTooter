package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.util.notEmptyOrThrow
import jp.juggler.subwaytooter.util.parseLong
import jp.juggler.subwaytooter.util.parseString

import org.json.JSONObject

class TootNotification(
	val json : JSONObject,
	val id : Long,
	val type : String,    //	One of: "mention", "reblog", "favourite", "follow"
	private val created_at : String?,    //	The time the notification was created
	val accountRef : TootAccountRef?,    //	The Account sending the notification to the user
	val status : TootStatus?    //	The Status associated with the notification, if applicable
) : TimelineItem() {
	
	val time_created_at : Long

	val account : TootAccount?
		get() = accountRef?.get()
	
	init {
		time_created_at = TootStatus.parseTime(created_at)
	}
	
	constructor(parser : TootParser, src : JSONObject) : this(
		json = src,
		id = src.parseLong("id") ?: - 1L,
		type = src.notEmptyOrThrow("type"),
		created_at = src.parseString("created_at"),
		accountRef = TootAccountRef.mayNull( parser, parser.account(src.optJSONObject("account"))),
		status = parser.status(src.optJSONObject("status"))
	)
	
	companion object {
		const val TYPE_MENTION = "mention"
		const val TYPE_REBLOG = "reblog"
		const val TYPE_FAVOURITE = "favourite"
		const val TYPE_FOLLOW = "follow"
	}
}
