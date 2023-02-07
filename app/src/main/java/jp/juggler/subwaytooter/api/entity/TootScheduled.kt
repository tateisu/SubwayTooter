package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.TootAttachment.Companion.tootAttachment
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.buildJsonObject
import jp.juggler.util.log.LogCategory

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
            parseList(src.jsonArray("media_attachments")) {
                tootAttachment(parser, it)
            }
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
    fun encodeSimple() = buildJsonObject {
        put("id", id.toString())
        put("scheduled_at", scheduledAt)
        // SKIP: put("media_attachments",mediaAttachments?.map{ it.})
        put("params", buildJsonObject {
            put("text", text)
            put("visibility", visibility.strMastodon)
            put("spoiler_text", spoilerText)
            put("in_reply_to_id", inReplyToId)
            put("sensitive", sensitive)
        })
    }
}
