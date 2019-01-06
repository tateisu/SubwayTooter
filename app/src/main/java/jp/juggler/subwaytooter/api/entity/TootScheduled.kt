package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.util.LogCategory
import jp.juggler.util.parseLong
import jp.juggler.util.parseString
import org.json.JSONObject

class TootScheduled(parser:TootParser,src:JSONObject): TimelineItem(){
	
	companion object {
		val log = LogCategory("TootScheduled")
	}
	
	val id :EntityId
	val scheduled_at: String?
	val timeScheduledAt: Long
	val media_attachments : ArrayList<TootAttachment>?
	
	init{
		id = EntityId.mayDefault( src.parseLong("id"))
		scheduled_at = src.parseString("scheduled_at")
		timeScheduledAt = TootStatus.parseTime(scheduled_at)
		media_attachments =
			parseListOrNull(
				::TootAttachment,
				parser,
				src.optJSONArray("media_attachments"),
				log
			)
	}
}