package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.util.JsonObject
import jp.juggler.util.LogCategory

class TootScheduled(parser : TootParser, val src : JsonObject) : TimelineItem() {
	
	companion object {
		val log = LogCategory("TootScheduled")
	}
	
	val id : EntityId
	private val scheduled_at : String?
	val timeScheduledAt : Long
	val media_attachments : ArrayList<TootAttachmentLike>?
	val text : String?
	val visibility : TootVisibility
	val spoiler_text : String?
	val in_reply_to_id : Long?
	val sensitive : Boolean
	val uri : String
	
	init {
		id = EntityId.mayDefault(src.string("id"))
		uri = "scheduled://${parser.apiHost}/$id"
		
		scheduled_at = src.string("scheduled_at")
		timeScheduledAt = TootStatus.parseTime(scheduled_at)
		media_attachments =
			parseListOrNull(
				::TootAttachment,
				parser,
				src.jsonArray("media_attachments"),
				log
			)
		val params = src.jsonObject("params")
		text = params?.string("text")
		visibility = TootVisibility.parseMastodon(params?.string("visibility"))
			?: TootVisibility.Public
		spoiler_text = params?.string("spoiler_text")
		in_reply_to_id = params?.long("in_reply_to_id")
		sensitive = params?.optBoolean("sensitive") ?: false
	}
	
	fun hasMedia() = media_attachments?.isNotEmpty() == true
}