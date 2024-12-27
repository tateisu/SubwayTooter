package jp.juggler.subwaytooter.api.entity

import android.content.Context
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.NotificationType.Companion.toNotificationType
import jp.juggler.subwaytooter.api.entity.TootAccountRef.Companion.tootAccountRefOrNull
import jp.juggler.subwaytooter.api.entity.TootNotificationEvent.Companion.parseTootNotififcationEvent
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory

class TootNotification(
    val json: JsonObject,
    val id: EntityId,
    val type: NotificationType,
    //	The Account sending the notification to the user
    val accountRef: TootAccountRef?,

    // The Status associated with the notification, if applicable
    // 投稿の更新により変更可能になる
    var status: TootStatus?,

    // Mastodon 4.3, severed_relationships で供給される
    val event: TootNotificationEvent?,

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

        private fun tootNotificationMisskey(parser: TootParser, src: JsonObject): TootNotification {
            // Misskeyの通知APIはページネーションをIDでしか行えない
            // これは改善される予定 https://github.com/syuilo/misskey/issues/2275

            val created_at: String? = src.string("createdAt")

            val accountRef = tootAccountRefOrNull(
                parser,
                parser.account(src.jsonObject("user"))
            )

            val reaction: TootReaction? = src.string("reaction")
                ?.notEmpty()
                ?.let { TootReaction.parseMisskey(it) }

            return TootNotification(
                json = src,
                id = EntityId.mayDefault(src.string("id")),
                type = src.stringOrThrow("type").toNotificationType(),
                accountRef = accountRef,
                status = parser.status(src.jsonObject("note")),
                reaction = reaction,
                reblog_visibility = TootVisibility.Unknown,
                created_at = created_at,
                time_created_at = TootStatus.parseTime(created_at),
                event = null,
            )
        }

        private fun tootNotificationMastodon(
            parser: TootParser,
            src: JsonObject,
        ): TootNotification {
            val type = src.stringOrThrow("type").toNotificationType()
            if( type == NotificationType.SeveredRelationships){
                log.i("src=$src")
            }

            val created_at: String? = src.string("created_at")

            val accountRef: TootAccountRef? =
                tootAccountRefOrNull(parser, parser.account(src.jsonObject("account")))

            val status: TootStatus? = parser.status(src.jsonObject("status"))

            val reaction: TootReaction? = src.jsonObject("emoji_reaction")
                ?.notEmpty()
                ?.let { TootReaction.parseFedibird(it) }
                ?: src.string("emoji")?.let { TootReaction(name = it) } // pleroma unicode emoji

            // fedibird
            // https://github.com/fedibird/mastodon/blob/7974fd3c7ec11ea9f7bef4ad7f4009fff53f62af/app/serializers/rest/notification_serializer.rb#L9
            val visibilityString = when {
                src.boolean("limited") == true -> "limited"
                else -> src.string("reblog_visibility")
            }

            val reblog_visibility = TootVisibility.parseMastodon(visibilityString)
                ?: TootVisibility.Unknown

            return TootNotification(
                json = src,
                id = EntityId.mayDefault(src.string("id")),
                type = type,
                accountRef = accountRef,
                status = status,
                reaction = reaction,
                reblog_visibility = reblog_visibility,
                created_at = created_at,
                time_created_at = TootStatus.parseTime(created_at),
                event = src.jsonObject("event")?.parseTootNotififcationEvent(),
            )
        }

        fun tootNotification(parser: TootParser, src: JsonObject): TootNotification =
            when (parser.serviceType) {
                ServiceType.MISSKEY -> tootNotificationMisskey(parser, src)
                else -> tootNotificationMastodon(parser, src)
            }
    }

    override fun getOrderId() = id

    fun getNotificationLine(context: Context): String {

        val name = when (PrefB.bpShowAcctInSystemNotification.value) {
            true -> accountRef?.get()?.acct?.pretty?.notEmpty()?.let { "@$it" }
            else -> accountRef?.decoded_display_name
        } ?: "?"

        return when (type) {
            NotificationType.Mention,
            NotificationType.Reply,
                -> context.getString(R.string.display_name_replied_by, name)

            NotificationType.Renote,
            NotificationType.Reblog,
                -> context.getString(R.string.display_name_boosted_by, name)

            NotificationType.Quote,
                -> context.getString(R.string.display_name_quoted_by, name)

            NotificationType.Status,
                -> context.getString(R.string.display_name_posted_by, name)

            NotificationType.Update,
                -> context.getString(R.string.display_name_updates_post, name)

            NotificationType.StatusReference,
                -> context.getString(R.string.display_name_references_post, name)

            NotificationType.Follow,
                -> context.getString(R.string.display_name_followed_by, name)

            NotificationType.Unfollow,
                -> context.getString(R.string.display_name_unfollowed_by, name)

            NotificationType.AdminSignup,
                -> context.getString(R.string.display_name_signed_up, name)

            NotificationType.AdminReport,
                -> context.getString(R.string.display_name_report, name)

            NotificationType.Favourite,
                -> context.getString(R.string.display_name_favourited_by, name)

            NotificationType.EmojiReactionPleroma,
            NotificationType.EmojiReactionFedibird,
            NotificationType.Reaction,
                -> arrayOf(
                context.getString(R.string.display_name_reaction_by, name),
                reaction?.name
            ).mapNotNull { it.notEmpty() }.joinToString(" ")

            NotificationType.Vote,
            NotificationType.PollVoteMisskey,
                -> context.getString(R.string.display_name_voted_by, name)

            NotificationType.FollowRequest,
            NotificationType.FollowRequestMisskey,
                -> context.getString(R.string.display_name_follow_request_by, name)

            NotificationType.FollowRequestAcceptedMisskey,
                -> context.getString(R.string.display_name_follow_request_accepted_by, name)

            NotificationType.Poll,
                -> context.getString(R.string.end_of_polling_from, name)

            NotificationType.ScheduledStatus,
                -> context.getString(R.string.scheduled_status)

            NotificationType.SeveredRelationships,
                -> context.getString(R.string.notification_type_severed_relationships)

            is NotificationType.Unknown ->
                context.getString(R.string.unknown_notification_from, name) + " :" + type
        }
    }
}
