package jp.juggler.subwaytooter.push

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.table.PushMessage
import jp.juggler.util.log.LogCategory

private val log = LogCategory("PushMessageIconColor")

enum class PushMessageIconColor(
    @ColorRes val colorRes: Int,
    @DrawableRes val iconId: Int,
    val keys: Set<String>,
) {
    Favourite(
        0,
        R.drawable.ic_star_outline,
        setOf("favourite"),
    ),
    Mention(
        0,
        R.drawable.outline_alternate_email_24,
        setOf("mention"),
    ),
    Reply(
        0,
        R.drawable.ic_reply,
        setOf("reply")
    ),
    Reblog(
        0,
        R.drawable.ic_repeat,
        setOf("reblog", "renote"),
    ),
    Quote(
        0,
        R.drawable.ic_quote,
        setOf("quote"),
    ),
    Follow(
        0,
        R.drawable.ic_person_add,
        setOf("follow", "followRequestAccepted")
    ),
    Unfollow(
        0,
        R.drawable.ic_follow_cross,
        setOf("unfollow")
    ),
    Reaction(
        0,
        R.drawable.outline_add_reaction_24,
        setOf("reaction", "emoji_reaction", "pleroma:emoji_reaction")
    ),
    FollowRequest(
        R.color.colorNotificationAccentFollowRequest,
        R.drawable.ic_follow_wait,
        setOf("follow_request", "receiveFollowRequest"),
    ),
    Poll(
        0,
        R.drawable.outline_poll_24,
        setOf("pollVote", "poll_vote", "poll"),
    ),
    Status(
        0,
        R.drawable.ic_edit,
        setOf("status", "update", "status_reference")
    ),
    AdminSignUp(
        0,
        R.drawable.outline_group_add_24,
        setOf(TootNotification.TYPE_ADMIN_SIGNUP),
    ),
    AdminReport(
        R.color.colorNotificationAccentAdminReport,
        R.drawable.ic_error,
        setOf(TootNotification.TYPE_ADMIN_REPORT),
    ),

    Unknown(
        R.color.colorNotificationAccentUnknown,
        R.drawable.ic_question,
        setOf("unknown"),
    )
    ;

    companion object {
        val map = PushMessageIconColor.entries.map { it.keys }.flatten().toSet()
            .associateWith { key ->
                val colors = PushMessageIconColor.entries
                    .filter { it.keys.contains(key) }
                when {
                    colors.isEmpty() -> error("missing color fot key=$key")
                    colors.size > 1 -> error(
                        "NotificationIconAndColor: duplicate key $key to ${
                            colors.joinToString(", ") { it.name }
                        }"
                    )

                    else -> colors.first()
                }
            }
    }
}

fun PushMessage.iconColor() =
    notificationType?.let { PushMessageIconColor.map[it] }
        ?: PushMessageIconColor.Unknown
