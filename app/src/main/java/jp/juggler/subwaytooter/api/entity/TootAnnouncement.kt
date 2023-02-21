package jp.juggler.subwaytooter.api.entity

import android.text.Spannable
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.emoji.CustomEmoji
import jp.juggler.subwaytooter.span.emojiSizeMode
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory

//	{"id":"1",
//	"content":"\u003cp\u003e日本語\u003cbr /\u003eURL \u003ca href=\"https://www.youtube.com/watch?v=2n1fM2ItdL8\" rel=\"nofollow noopener noreferrer\" target=\"_blank\"\u003e\u003cspan class=\"invisible\"\u003ehttps://www.\u003c/span\u003e\u003cspan class=\"ellipsis\"\u003eyoutube.com/watch?v=2n1fM2ItdL\u003c/span\u003e\u003cspan class=\"invisible\"\u003e8\u003c/span\u003e\u003c/a\u003e\u003cbr /\u003eカスタム絵文字 :ct013: \u003cbr /\u003e普通の絵文字 🤹 \u003c/p\u003e\u003cp\u003e改行2つ\u003c/p\u003e",
//	"starts_at":"2020-01-23T00:00:00.000Z",
//	"ends_at":"2020-01-28T23:59:00.000Z",
//	"all_day":true,
//	"mentions":[],
//	"tags":[],
//	"emojis":[{"shortcode":"ct013","url":"https://m2j.zzz.ac/custom_emojis/images/000/004/116/original/ct013.png","static_url":"https://m2j.zzz.ac/custom_emojis/images/000/004/116/static/ct013.png","visible_in_picker":true}],
//	"reactions":[]}]
class TootAnnouncement(
    val id: EntityId,
    val starts_at: Long,
    val ends_at: Long,
    val all_day: Boolean,
    val published_at: Long,
    val updated_at: Long,
    private val custom_emojis: HashMap<String, CustomEmoji>?,
    //	Body of the status; this will contain HTML (remote HTML already sanitized)
    val content: String,
    val decoded_content: Spannable,
    //An array of Tags
    val tags: List<TootTag>?,
    //	An array of Mentions
    val mentions: ArrayList<TootMention>?,
    var reactions: MutableList<TootReaction>? = null,
) {
    companion object {
        private val log = LogCategory("TootAnnouncement")

        fun tootAnnouncement(parser: TootParser, src: JsonObject): TootAnnouncement {
            val custom_emojis = parseMapOrNull(src.jsonArray("emojis"), CustomEmoji::decodeMastodon)
            val reactions = parseListOrNull(src.jsonArray("reactions")) {
                TootReaction.parseFedibird(it)
            }
            val mentions = parseListOrNull(src.jsonArray("mentions")) {
                TootMention(it)
            }
            val options = DecodeOptions(
                parser.context,
                parser.linkHelper,
                short = true,
                decodeEmoji = true,
                emojiMapCustom = custom_emojis,
                // emojiMapProfile = profile_emojis,
                // attachmentList = media_attachments,
                highlightTrie = parser.highlightTrie,
                mentions = mentions,
                emojiSizeMode =  parser.emojiSizeMode,
            )
            val content = src.string("content") ?: ""
            return TootAnnouncement(
                id = EntityId.mayDefault(src.string("id")),
                starts_at = TootStatus.parseTime(src.string("starts_at")),
                ends_at = TootStatus.parseTime(src.string("ends_at")),
                all_day = src.boolean("all_day") ?: false,
                published_at = TootStatus.parseTime(src.string("published_at")),
                updated_at = TootStatus.parseTime(src.string("updated_at")),
                custom_emojis = custom_emojis,
                tags = TootTag.parseListOrNull(parser, src.jsonArray("tags")),
                mentions = mentions,
                content = content,
                decoded_content = options.decodeHTML(content),
                reactions = reactions,
            )
        }

        // return null if list is empty
        fun filterShown(src: List<TootAnnouncement>?): List<TootAnnouncement>? {
            val now = System.currentTimeMillis()
            return src
                ?.filter {

                    when {
                        // 期間の大小が入れ替わってる場合はフィルタしない
                        it.starts_at > it.ends_at -> true

                        // まだ開始していない
                        it.starts_at > 0L && now < it.starts_at -> false

                        // 終了した後
                        it.ends_at > 0L && now > it.ends_at -> false

                        // フィルタしない
                        else -> true
                    }
                }
                ?.notEmpty()
        }

        // return previous/next item in announcement list.
        fun move(src: List<TootAnnouncement>?, currentId: EntityId?, delta: Int): EntityId? {

            val listShown = filterShown(src)
                ?: return null

            val size = listShown.size
            if (size <= 0) return null

            val idx = delta + when (val v = listShown.indexOfFirst { it.id == currentId }) {
                -1 -> 0
                else -> v
            }
            return listShown[(idx + size) % size].id
        }

        // https://github.com/tootsuite/mastodon/blob/b9d74d407673a6dbdc87c3310618b22c85358c85/app/javascript/mastodon/reducers/announcements.js#L64
        // reactionsのmeを残したまま他の項目を更新したい
        fun merge(old: TootAnnouncement, dst: TootAnnouncement): TootAnnouncement {
            val oldReactions = old.reactions
            val dstReactions = dst.reactions
            if (dstReactions == null) {
                dst.reactions = oldReactions
            } else if (oldReactions != null) {
                val reactions = mutableListOf<TootReaction>()
                reactions.addAll(oldReactions)
                for (newItem in dstReactions) {
                    val oldItem = reactions.find { it.name == newItem.name }
                    if (oldItem == null) {
                        reactions.add(newItem)
                    } else {
                        oldItem.count = newItem.count
                    }
                }
                dst.reactions = reactions
            }
            return dst
        }
    }
}
