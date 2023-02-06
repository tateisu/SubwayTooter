package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory

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
        const val TYPE_QUOTE = "quote" // Misskeyの引用Renote, fedibirdのquote

        // フォロー
        const val TYPE_FOLLOW = "follow" // Mastodon,Misskey
        const val TYPE_UNFOLLOW = "unfollow" // Mastodon,Misskey

        const val TYPE_FAVOURITE = "favourite"
        const val TYPE_REACTION = "reaction" // misskey
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

        // (Mastodon 3.5.0rc1)
        const val TYPE_UPDATE = "update"
        const val TYPE_ADMIN_SIGNUP = "admin.sign_up"

        // (Mastodon 4.0)
        const val TYPE_ADMIN_REPORT = "admin.report"

        // (Fedibird)
        // https://github.com/fedibird/mastodon/blob/fedibird/app/controllers/api/v1/push/subscriptions_controller.rb#L55
        // https://github.com/fedibird/mastodon/blob/fedibird/app/models/notification.rb
        const val TYPE_EMOJI_REACTION = "emoji_reaction"
        const val TYPE_STATUS_REFERENCE = "status_reference"
        const val TYPE_SCHEDULED_STATUS = "scheduled_status"
    }

    val json: JsonObject
    val id: EntityId
    val type: String    //	One of: "mention", "reblog", "favourite", "follow"
    val accountRef: TootAccountRef?    //	The Account sending the notification to the user

    //	The Status associated with the notification, if applicable
    // 投稿の更新により変更可能になる
    var status: TootStatus?

    var reaction: TootReaction? = null

    val reblog_visibility: TootVisibility

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

            reblog_visibility = TootVisibility.Unknown

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

            // fedibird
            // https://github.com/fedibird/mastodon/blob/7974fd3c7ec11ea9f7bef4ad7f4009fff53f62af/app/serializers/rest/notification_serializer.rb#L9
            val visibilityString = when {
                src.boolean("limited") == true -> "limited"
                else -> src.string("reblog_visibility")
            }
            this.reblog_visibility = TootVisibility.parseMastodon(visibilityString)
                ?: TootVisibility.Unknown
        }
    }
}
