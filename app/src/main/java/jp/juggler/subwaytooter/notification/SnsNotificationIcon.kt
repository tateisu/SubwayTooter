package jp.juggler.subwaytooter.notification

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.table.PushMessage
import jp.juggler.util.log.LogCategory

private val log = LogCategory("NotificationIconAndColor")

enum class NotificationIconAndColor(
    @ColorInt colorArg: Int,
    @DrawableRes val iconId: Int,
    val keys: Array<String>,
) {
    Favourite(
        0xe5e825,
        R.drawable.ic_star_outline,
        arrayOf("favourite"),
    ),
    Mention(
        0x60f516,
        R.drawable.outline_alternate_email_24,
        arrayOf("mention"),
    ),
    Reply(
        0xff3dbb,
        R.drawable.ic_reply,
        arrayOf("reply")
    ),
    Reblog(
        0x39e3d5,
        R.drawable.ic_repeat,
        arrayOf("reblog", "renote"),
    ),
    Quote(
        0x40a9ff,
        R.drawable.ic_quote,
        arrayOf("quote"),
    ),
    Follow(
        0xf57a33,
        R.drawable.ic_person_add,
        arrayOf("follow", "followRequestAccepted")
    ),
    Unfollow(
        0x9433f5,
        R.drawable.ic_follow_cross,
        arrayOf("unfollow")
    ),
    Reaction(
        0xf5f233,
        R.drawable.outline_add_reaction_24,
        arrayOf("reaction", "emoji_reaction", "pleroma:emoji_reaction")
    ),
    FollowRequest(
        0xf53333,
        R.drawable.ic_follow_wait,
        arrayOf("follow_request", "receiveFollowRequest"),
    ),
    Poll(
        0x33f59b,
        R.drawable.outline_poll_24,
        arrayOf("pollVote", "poll_vote", "poll"),
    ),
    Status(
        0x33f597,
        R.drawable.ic_edit,
        arrayOf("status", "update", "status_reference")
    ),
    SignUp(
        0xf56a33,
        R.drawable.outline_group_add_24,
        arrayOf("admin.sign_up"),
    ),

    Unknown(
        0xae1aed,
        R.drawable.ic_question,
        arrayOf("unknown"),
    )
    ;

    val color = Color.BLACK or colorArg

    companion object {
        val map = buildMap {
            values().forEach {
                for (k in it.keys) {
                    val old: NotificationIconAndColor? = get(k)
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

fun String.findNotificationIconAndColor() =
    NotificationIconAndColor.map[this]

fun PushMessage.notificationIconAndColor(): NotificationIconAndColor {
    // mastodon
    messageJson?.string("notification_type")
        ?.findNotificationIconAndColor()?.let { return it }

    // misskey
    when (messageJson?.string("type")) {
        "notification" ->
            messageJson?.jsonObject("body")?.string("type")
                ?.findNotificationIconAndColor()?.let { return it }
    }

    return NotificationIconAndColor.Unknown
}
