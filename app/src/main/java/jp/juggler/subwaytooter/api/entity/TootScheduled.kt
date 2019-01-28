package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.util.LogCategory
import jp.juggler.util.parseLong
import jp.juggler.util.parseString
import org.json.JSONObject

class TootScheduled(parser:TootParser,val src:JSONObject): TimelineItem(){
	
	companion object {
		val log = LogCategory("TootScheduled")
	}
	
	val id :EntityId
	private val scheduled_at: String?
	val timeScheduledAt: Long
	val media_attachments : ArrayList<TootAttachmentLike>?
	val text :String?
	val visibility: TootVisibility
	val spoiler_text: String?
	val in_reply_to_id: Long?
	val sensitive : Boolean
	val uri : String
	
	init{
		id = EntityId.mayDefault( src.parseString("id"))
		uri = "scheduled://${parser.linkHelper.host}/$id"
		
		scheduled_at = src.parseString("scheduled_at")
		timeScheduledAt = TootStatus.parseTime(scheduled_at)
		media_attachments =
			parseListOrNull(
				::TootAttachment,
				parser,
				src.optJSONArray("media_attachments"),
				log
			)
		val params = src.optJSONObject("params")
		text = params?.parseString("text")
		visibility = TootVisibility.parseMastodon(params?.parseString("visibility"))
			?: TootVisibility.Public
		spoiler_text = params?.parseString("spoiler_text")
		in_reply_to_id = params?.parseLong("in_reply_to_id")
		sensitive = params?.optBoolean("sensitive") ?: false
	}
	
	fun hasMedia() = media_attachments?.isNotEmpty() == true
}