package jp.juggler.subwaytooter.push

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.table.PushMessage
import jp.juggler.util.log.LogCategory

private val log = LogCategory("NotificationIconAndColor")

enum class PushMessageIconColor(
    @ColorRes val colorRes: Int,
    @DrawableRes val iconId: Int,
    val keys: Array<String>,
) {
    Favourite(
        R.color.colorNotificationAccentFavourite,
        R.drawable.ic_star_outline,
        arrayOf("favourite"),
    ),
    Mention(
        R.color.colorNotificationAccentMention,
        R.drawable.outline_alternate_email_24,
        arrayOf("mention"),
    ),
    Reply(
        R.color.colorNotificationAccentReply,
        R.drawable.ic_reply,
        arrayOf("reply")
    ),
    Reblog(
        R.color.colorNotificationAccentReblog,
        R.drawable.ic_repeat,
        arrayOf("reblog", "renote"),
    ),
    Quote(
        R.color.colorNotificationAccentQuote,
        R.drawable.ic_quote,
        arrayOf("quote"),
    ),
    Follow(
        R.color.colorNotificationAccentFollow,
        R.drawable.ic_person_add,
        arrayOf("follow", "followRequestAccepted")
    ),
    Unfollow(
        R.color.colorNotificationAccentUnfollow,
        R.drawable.ic_follow_cross,
        arrayOf("unfollow")
    ),
    Reaction(
        R.color.colorNotificationAccentReaction,
        R.drawable.outline_add_reaction_24,
        arrayOf("reaction", "emoji_reaction", "pleroma:emoji_reaction")
    ),
    FollowRequest(
        R.color.colorNotificationAccentFollowRequest,
        R.drawable.ic_follow_wait,
        arrayOf("follow_request", "receiveFollowRequest"),
    ),
    Poll(
        R.color.colorNotificationAccentPoll,
        R.drawable.outline_poll_24,
        arrayOf("pollVote", "poll_vote", "poll"),
    ),
    Status(
        R.color.colorNotificationAccentStatus,
        R.drawable.ic_edit,
        arrayOf("status", "update", "status_reference")
    ),
    SignUp(
        R.color.colorNotificationAccentSignUp,
        R.drawable.outline_group_add_24,
        arrayOf("admin.sign_up"),
    ),

    Unknown(
        R.color.colorNotificationAccentUnknown,
        R.drawable.ic_question,
        arrayOf("unknown", "admin.sign_up"),
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
