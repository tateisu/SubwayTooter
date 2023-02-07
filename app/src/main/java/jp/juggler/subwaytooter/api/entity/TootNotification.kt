package jp.juggler.subwaytooter.api.entity

import android.content.Context
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory

class TootNotification(
    val json: JsonObject,
    val id: EntityId,
    //	One of: "mention", "reblog", "favourite", "follow"
    val type: String,
    //	The Account sending the notification to the user
    val accountRef: TootAccountRef?,

    // The Status associated with the notification, if applicable
    // 投稿の更新により変更可能になる
    var status: TootStatus?,

    var reaction: TootReaction? = null,

    val reblog_visibility: TootVisibility,

    //	The time the notification was created
    private val created_at: String?,
    val time_created_at: Long,
) : TimelineItem() {

    val account: TootAccount?
        get() = accountRef?.get()

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

        fun tootNotification(parser: TootParser, src: JsonObject): TootNotification {
            val id: EntityId
            //	One of: "mention", "reblog", "favourite", "follow"
            val type: String
            //	The Account sending the notification to the user
            val accountRef: TootAccountRef?

            // The Status associated with the notification, if applicable
            // 投稿の更新により変更可能になる
            val status: TootStatus?

            val reaction: TootReaction?

            val reblog_visibility: TootVisibility

            //	The time the notification was created
            val created_at: String?
            val time_created_at: Long


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
                reblog_visibility = TootVisibility.parseMastodon(visibilityString)
                    ?: TootVisibility.Unknown
            }
            return TootNotification(
                json = src,
                id = id,
                type = type,
                accountRef = accountRef,
                status = status,
                reaction = reaction,
                reblog_visibility = reblog_visibility,
                created_at = created_at,
                time_created_at = time_created_at,
            )
        }
    }

    override fun getOrderId() = id

    fun getNotificationLine(context: Context): String {

        val name = when (PrefB.bpShowAcctInSystemNotification.value) {
            false -> accountRef?.decoded_display_name

            true -> {
                val acctPretty = accountRef?.get()?.acct?.pretty
                if (acctPretty?.isNotEmpty() == true) {
                    "@$acctPretty"
                } else {
                    null
                }
            }
        } ?: "?"

        return when (type) {
            TYPE_MENTION,
            TYPE_REPLY,
            -> context.getString(R.string.display_name_replied_by, name)

            TYPE_RENOTE,
            TYPE_REBLOG,
            -> context.getString(R.string.display_name_boosted_by, name)

            TYPE_QUOTE,
            -> context.getString(R.string.display_name_quoted_by, name)

            TYPE_STATUS,
            -> context.getString(R.string.display_name_posted_by, name)

            TYPE_UPDATE,
            -> context.getString(R.string.display_name_updates_post, name)

            TYPE_STATUS_REFERENCE,
            -> context.getString(R.string.display_name_references_post, name)

            TYPE_FOLLOW,
            -> context.getString(R.string.display_name_followed_by, name)

            TYPE_UNFOLLOW,
            -> context.getString(R.string.display_name_unfollowed_by, name)

            TYPE_ADMIN_SIGNUP,
            -> context.getString(R.string.display_name_signed_up, name)

            TYPE_ADMIN_REPORT,
            -> context.getString(R.string.display_name_report, name)

            TYPE_FAVOURITE,
            -> context.getString(R.string.display_name_favourited_by, name)

            TYPE_EMOJI_REACTION_PLEROMA,
            TYPE_EMOJI_REACTION,
            TYPE_REACTION,
            -> context.getString(R.string.display_name_reaction_by, name)

            TYPE_VOTE,
            TYPE_POLL_VOTE_MISSKEY,
            -> context.getString(R.string.display_name_voted_by, name)

            TYPE_FOLLOW_REQUEST,
            TYPE_FOLLOW_REQUEST_MISSKEY,
            -> context.getString(R.string.display_name_follow_request_by, name)

            TYPE_FOLLOW_REQUEST_ACCEPTED_MISSKEY,
            -> context.getString(R.string.display_name_follow_request_accepted_by, name)

            TYPE_POLL,
            -> context.getString(R.string.end_of_polling_from, name)

            else -> "?"
        }
    }
}
