package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.util.JsonObject
import jp.juggler.util.LogCategory
import jp.juggler.util.notEmpty

class TootNotification(parser: TootParser, src: JsonObject) : TimelineItem() {

    companion object {
        @Suppress("unused")
        private val log = LogCategory("TootNotification")

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
        const val TYPE_REACTION = "reaction" // misskey
        const val TYPE_EMOJI_REACTION = "emoji_reaction" // fedibird
        const val TYPE_EMOJI_REACTION_PLEROMA = "pleroma:emoji_reaction" // pleroma

        const val TYPE_FOLLOW_REQUEST = "follow_request"
        const val TYPE_FOLLOW_REQUEST_MISSKEY = "receiveFollowRequest"

        const val TYPE_FOLLOW_REQUEST_ACCEPTED_MISSKEY = "followRequestAccepted"
        const val TYPE_POLL_VOTE_MISSKEY = "pollVote"

        // 投票
        const val TYPE_VOTE = "poll_vote"

        // (Mastodon 2.8)投票完了
        const val TYPE_POLL = "poll"

        const val TYPE_STATUS = "status"
    }

    val json: JsonObject
    val id: EntityId
    val type: String    //	One of: "mention", "reblog", "favourite", "follow"
    val accountRef: TootAccountRef?    //	The Account sending the notification to the user
    val status: TootStatus?    //	The Status associated with the notification, if applicable
    var reaction: TootReaction? = null

    private val created_at: String?    //	The time the notification was created
    val time_created_at: Long

    val account: TootAccount?
        get() = accountRef?.get()

    override fun getOrderId() = id

    init {
        json = src

        if (parser.serviceType == ServiceType.MISSKEY) {
            id = EntityId.mayDefault(src.string("id"))

            type = src.stringOrThrow("type")

            created_at = src.string("createdAt")
            time_created_at = TootStatus.parseTime(created_at)

            accountRef = TootAccountRef.mayNull(
                parser,
                parser.account(
                    src.jsonObject("user")
                )
            )
            status = parser.status(
                src.jsonObject("note")
            )

            reaction = src.string("reaction")
                ?.notEmpty()
                ?.let { TootReaction.parseMisskey(it) }

            // Misskeyの通知APIはページネーションをIDでしか行えない
            // これは改善される予定 https://github.com/syuilo/misskey/issues/2275
        } else {
            id = EntityId.mayDefault(src.string("id"))

            type = src.stringOrThrow("type")

            created_at = src.string("created_at")
            time_created_at = TootStatus.parseTime(created_at)
            accountRef =
                TootAccountRef.mayNull(parser, parser.account(src.jsonObject("account")))
            status = parser.status(src.jsonObject("status"))

            reaction = src.jsonObject("emoji_reaction")
                ?.notEmpty()
                ?.let { TootReaction.parseFedibird(it) }
                // pleroma unicode emoji
                ?: src.string("emoji")?.let { TootReaction(name = it) }
        }
    }
}
