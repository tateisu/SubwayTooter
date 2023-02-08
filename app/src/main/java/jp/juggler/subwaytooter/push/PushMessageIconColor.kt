package jp.juggler.subwaytooter.push

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.table.PushMessage
import jp.juggler.util.log.LogCategory

private val log = LogCategory("NotificationIconAndColor")

enum class PushMessageIconColor(
    @ColorRes val colorRes: Int,
    @DrawableRes val iconId: Int,
    val keys: Array<String>,
) {
    Favourite(
        0,
        R.drawable.ic_star_outline,
        arrayOf("favourite"),
    ),
    Mention(
        0,
        R.drawable.outline_alternate_email_24,
        arrayOf("mention"),
    ),
    Reply(
        0,
        R.drawable.ic_reply,
        arrayOf("reply")
    ),
    Reblog(
        0,
        R.drawable.ic_repeat,
        arrayOf("reblog", "renote"),
    ),
    Quote(
        0,
        R.drawable.ic_quote,
        arrayOf("quote"),
    ),
    Follow(
        0,
        R.drawable.ic_person_add,
        arrayOf("follow", "followRequestAccepted")
    ),
    Unfollow(
        0,
        R.drawable.ic_follow_cross,
        arrayOf("unfollow")
    ),
    Reaction(
        0,
        R.drawable.outline_add_reaction_24,
        arrayOf("reaction", "emoji_reaction", "pleroma:emoji_reaction")
    ),
    FollowRequest(
        R.color.colorNotificationAccentFollowRequest,
        R.drawable.ic_follow_wait,
        arrayOf("follow_request", "receiveFollowRequest"),
    ),
    Poll(
        0,
        R.drawable.outline_poll_24,
        arrayOf("pollVote", "poll_vote", "poll"),
    ),
    Status(
        0,
        R.drawable.ic_edit,
        arrayOf("status", "update", "status_reference")
    ),
    AdminSignUp(
        0,
        R.drawable.outline_group_add_24,
        arrayOf(TootNotification.TYPE_ADMIN_SIGNUP),
    ),
    AdminReport(
        R.color.colorNotificationAccentAdminReport,
        R.drawable.ic_error,
        arrayOf(TootNotification.TYPE_ADMIN_REPORT),
    ),

    Unknown(
        R.color.colorNotificationAccentUnknown,
        R.drawable.ic_question,
        arrayOf("unknown"),
    )
    ;

    companion object {
        val map = buildMap {
            values().forEach {
                for (k in it.keys) {
                    val old: PushMessageIconColor? = get(k)
                    if (old != null) {
                        error("NotificationIconAndColor: $k is duplicate: ${it.name} and ${old.name}")
                    } else {
                        put(k, it)
                    }
                }
            }
        }
    }
}

fun PushMessage.iconColor() =
    notificationType?.let { PushMessageIconColor.map[it] }
        ?: PushMessageIconColor.Unknown
