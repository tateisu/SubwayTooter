package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.util.JsonObject
import jp.juggler.util.LogCategory
import jp.juggler.util.jsonObject

class TootScheduled(parser: TootParser, val src: JsonObject) : TimelineItem() {

    companion object {
        val log = LogCategory("TootScheduled")
    }

    val id = EntityId.mayDefault(src.string("id"))
    val uri = "scheduled://${parser.apiHost}/$id"
    private val scheduledAt = src.string("scheduled_at")
    val timeScheduledAt: Long

    val mediaAttachments: ArrayList<TootAttachmentLike>?
    val text: String?
    val visibility: TootVisibility
    val spoilerText: String?
    val inReplyToId: Long?
    val sensitive: Boolean

    init {
        timeScheduledAt = TootStatus.parseTime(scheduledAt)

        mediaAttachments =
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
        spoilerText = params?.string("spoiler_text")
        inReplyToId = params?.long("in_reply_to_id")
        sensitive = params?.optBoolean("sensitive") ?: false
    }

    fun hasMedia() = mediaAttachments?.isNotEmpty() == true

    // 投稿画面の復元時に、IDだけでもないと困る
    fun encodeSimple() = jsonObject {
        put("id", id.toString())
        put("scheduled_at", scheduledAt)
        // SKIP: put("media_attachments",mediaAttachments?.map{ it.})
        put("params", jsonObject {
            put("text", text)
            put("visibility", visibility.strMastodon)
            put("spoiler_text", spoilerText)
            put("in_reply_to_id", inReplyToId)
            put("sensitive", sensitive)
        })
    }
}
