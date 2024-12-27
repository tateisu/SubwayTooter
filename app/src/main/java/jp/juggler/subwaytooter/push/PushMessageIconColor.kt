package jp.juggler.subwaytooter.push

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.NotificationType
import jp.juggler.subwaytooter.push.PushMessageIconColor.AdminReport
import jp.juggler.subwaytooter.push.PushMessageIconColor.AdminSignUp
import jp.juggler.subwaytooter.push.PushMessageIconColor.Favourite
import jp.juggler.subwaytooter.push.PushMessageIconColor.Follow
import jp.juggler.subwaytooter.push.PushMessageIconColor.FollowRequest
import jp.juggler.subwaytooter.push.PushMessageIconColor.Mention
import jp.juggler.subwaytooter.push.PushMessageIconColor.Poll
import jp.juggler.subwaytooter.push.PushMessageIconColor.Quote
import jp.juggler.subwaytooter.push.PushMessageIconColor.Reaction
import jp.juggler.subwaytooter.push.PushMessageIconColor.Reblog
import jp.juggler.subwaytooter.push.PushMessageIconColor.Reply
import jp.juggler.subwaytooter.push.PushMessageIconColor.SeveredRelationships
import jp.juggler.subwaytooter.push.PushMessageIconColor.Status
import jp.juggler.subwaytooter.push.PushMessageIconColor.Unfollow
import jp.juggler.subwaytooter.push.PushMessageIconColor.Unknown
import jp.juggler.util.log.LogCategory

private val log = LogCategory("PushMessageIconColor")

enum class PushMessageIconColor(
    @ColorRes val colorRes: Int,
    @DrawableRes val iconId: Int,
) {
    Favourite(
        0,
        R.drawable.ic_star_outline,
    ),
    Mention(
        0,
        R.drawable.outline_alternate_email_24,
    ),
    Reply(
        0,
        R.drawable.ic_reply,
    ),
    Reblog(
        0,
        R.drawable.ic_repeat,
    ),
    Quote(
        0,
        R.drawable.ic_quote,
    ),
    Follow(
        0,
        R.drawable.ic_person_add,
    ),
    Unfollow(
        0,
        R.drawable.ic_follow_cross,
    ),
    Reaction(
        0,
        R.drawable.outline_add_reaction_24,
    ),
    FollowRequest(
        R.color.colorNotificationAccentFollowRequest,
        R.drawable.ic_follow_wait,
    ),
    Poll(
        0,
        R.drawable.outline_poll_24,
    ),
    Status(
        0,
        R.drawable.ic_edit,
    ),
    AdminSignUp(
        0,
        R.drawable.outline_group_add_24,
    ),
    AdminReport(
        R.color.colorNotificationAccentAdminReport,
        R.drawable.ic_error,
    ),
    SeveredRelationships(
        R.color.colorNotificationAccentAdminReport,
        R.drawable.baseline_heart_broken_24,
    ),
    Unknown(
        R.color.colorNotificationAccentUnknown,
        R.drawable.ic_question,
    )
}

fun NotificationType?.pushMessageIconAndColor(): PushMessageIconColor = when (this) {
    NotificationType.Favourite -> Favourite
    NotificationType.Mention -> Mention
    NotificationType.Reply -> Reply

    NotificationType.Reblog,
    NotificationType.Renote,
        -> Reblog

    NotificationType.Quote -> Quote

    NotificationType.Follow,
    NotificationType.FollowRequestAcceptedMisskey,
        -> Follow

    NotificationType.FollowRequest,
    NotificationType.FollowRequestMisskey,
        -> FollowRequest

    NotificationType.Unfollow -> Unfollow

    NotificationType.Reaction,
    NotificationType.EmojiReactionFedibird,
    NotificationType.EmojiReactionPleroma,
        -> Reaction

    NotificationType.Poll,
    NotificationType.PollVoteMisskey,
    NotificationType.Vote,
        -> Poll

    NotificationType.Status,
    NotificationType.Update,
    NotificationType.StatusReference,
        -> Status

    NotificationType.AdminSignup -> AdminSignUp
    NotificationType.AdminReport -> AdminReport
    NotificationType.SeveredRelationships -> SeveredRelationships

    NotificationType.ScheduledStatus -> Unknown
    null, is NotificationType.Unknown -> Unknown
}
