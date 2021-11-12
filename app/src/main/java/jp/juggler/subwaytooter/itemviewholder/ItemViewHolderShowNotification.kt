package jp.juggler.subwaytooter.itemviewholder

import android.view.Gravity
import android.view.View
import jp.juggler.subwaytooter.pref.PrefI
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.Styler
import jp.juggler.subwaytooter.actmain.addColumn
import jp.juggler.subwaytooter.actmain.nextPosition
import jp.juggler.subwaytooter.api.entity.TootAccountRef
import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.api.entity.TootReaction
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.util.notZero
import org.jetbrains.anko.backgroundColor

fun ItemViewHolder.showNotification(n: TootNotification) {
    val nStatus = n.status
    val nAccountRef = n.accountRef
    when (n.type) {
        TootNotification.TYPE_FAVOURITE ->
            showNotificationFavourite(n, nAccountRef, nStatus)

        TootNotification.TYPE_REBLOG ->
            showNotificationReblog(n, nAccountRef, nStatus)

        TootNotification.TYPE_RENOTE ->
            showNotificationRenote(n, nAccountRef, nStatus)

        TootNotification.TYPE_FOLLOW ->
            showNotificationFollow(n, nAccountRef)

        TootNotification.TYPE_UNFOLLOW ->
            showNotificationUnfollow(n, nAccountRef)

        TootNotification.TYPE_MENTION,
        TootNotification.TYPE_REPLY,
        -> showNotificationMention(n, nAccountRef, nStatus)

        TootNotification.TYPE_EMOJI_REACTION_PLEROMA,
        TootNotification.TYPE_EMOJI_REACTION,
        TootNotification.TYPE_REACTION,
        -> showNotificationReaction(n, nAccountRef, nStatus)

        TootNotification.TYPE_QUOTE ->
            showNotificationQuote(n, nAccountRef, nStatus)

        TootNotification.TYPE_STATUS ->
            showNotificationPost(n, nAccountRef, nStatus)

        TootNotification.TYPE_FOLLOW_REQUEST,
        TootNotification.TYPE_FOLLOW_REQUEST_MISSKEY,
        -> showNotificationFollowRequest(n, nAccountRef)

        TootNotification.TYPE_FOLLOW_REQUEST_ACCEPTED_MISSKEY ->
            showNotificationFollowRequestAccepted(n, nAccountRef)

        TootNotification.TYPE_VOTE,
        TootNotification.TYPE_POLL_VOTE_MISSKEY,
        -> showNotificationVote(n, nAccountRef, nStatus)

        TootNotification.TYPE_POLL ->
            showNotificationPoll(n, nAccountRef, nStatus)

        else ->
            showNotificationUnknown(n, nAccountRef, nStatus)
    }
}

private fun ItemViewHolder.showNotificationFollow(n: TootNotification, nAccountRef: TootAccountRef?) {
    val colorBg = PrefI.ipEventBgColorFollow(activity.pref)
    colorBg.notZero()?.let { viewRoot.backgroundColor = it }
    nAccountRef?.let {
        showBoost(it, n.time_created_at, R.drawable.ic_follow_plus, R.string.display_name_followed_by)
        showAccount(it)
    }
}

private fun ItemViewHolder.showNotificationUnfollow(n: TootNotification, nAccountRef: TootAccountRef?) {
    val colorBg = PrefI.ipEventBgColorUnfollow(activity.pref)
    colorBg.notZero()?.let { viewRoot.backgroundColor = it }
    nAccountRef?.let {
        showBoost(it, n.time_created_at, R.drawable.ic_follow_cross, R.string.display_name_unfollowed_by)
        showAccount(it)
    }
}

private fun ItemViewHolder.showNotificationFollowRequest(n: TootNotification, nAccountRef: TootAccountRef?) {
    val colorBg = PrefI.ipEventBgColorFollowRequest(activity.pref)
    colorBg.notZero()?.let { viewRoot.backgroundColor = it }
    nAccountRef?.let {
        showBoost(it, n.time_created_at, R.drawable.ic_follow_wait, R.string.display_name_follow_request_by)
        showAccount(it)
    }
    boostedAction = {
        activity.addColumn(activity.nextPosition(column), accessInfo, ColumnType.FOLLOW_REQUESTS)
    }
}

private fun ItemViewHolder.showNotificationFollowRequestAccepted(n: TootNotification, nAccountRef: TootAccountRef?) {
    val colorBg = PrefI.ipEventBgColorFollow(activity.pref)
    colorBg.notZero()?.let { viewRoot.backgroundColor = it }
    nAccountRef?.let {
        showBoost(it, n.time_created_at, R.drawable.ic_follow_plus, R.string.display_name_follow_request_accepted_by)
        showAccount(it)
    }
}

private fun ItemViewHolder.showNotificationPost(n: TootNotification, nAccountRef: TootAccountRef?, nStatus: TootStatus?) {
    val colorBg = PrefI.ipEventBgColorStatus(activity.pref)
    val iconId = when (nStatus) {
        null -> R.drawable.ic_question
        else -> Styler.getVisibilityIconId(accessInfo.isMisskey, nStatus.visibility)
    }
    nAccountRef?.let { showBoost(it, n.time_created_at, iconId, R.string.display_name_posted_by) }
    nStatus?.let { showNotificationStatus(it, colorBg) }
}

private fun ItemViewHolder.showNotificationReaction(n: TootNotification, nAccountRef: TootAccountRef?, nStatus: TootStatus?) {
    val colorBg = PrefI.ipEventBgColorReaction(activity.pref)
    nAccountRef?.let {
        showBoost(
            it, n.time_created_at,
            R.drawable.ic_face,
            R.string.display_name_reaction_by,
            reaction = n.reaction ?: TootReaction.UNKNOWN,
            boostStatus = nStatus
        )
    }
    nStatus?.let { showNotificationStatus(it, colorBg) }
}

private fun ItemViewHolder.showNotificationFavourite(n: TootNotification, nAccountRef: TootAccountRef?, nStatus: TootStatus?) {
    nAccountRef?.let {
        val iconId = if (accessInfo.isNicoru(it.get())) R.drawable.ic_nicoru else R.drawable.ic_star
        showBoost(it, n.time_created_at, iconId, R.string.display_name_favourited_by)
    }
    val colorBg = PrefI.ipEventBgColorFavourite(activity.pref)
    nStatus?.let { showNotificationStatus(it, colorBg) }
}

private fun ItemViewHolder.showNotificationReblog(n: TootNotification, nAccountRef: TootAccountRef?, nStatus: TootStatus?) {
    nAccountRef?.let {
        showBoost(
            it,
            n.time_created_at,
            R.drawable.ic_repeat,
            R.string.display_name_boosted_by,
            boostStatus = null,
            reblogVisibility = n.reblog_visibility
        )
    }
    val colorBg = PrefI.ipEventBgColorBoost(activity.pref)
    nStatus?.let { showNotificationStatus(it, colorBg) }
}

private fun ItemViewHolder.showNotificationRenote(n: TootNotification, nAccountRef: TootAccountRef?, nStatus: TootStatus?) {
    // 引用のないreblog
    nAccountRef?.let {
        showBoost(it, n.time_created_at, R.drawable.ic_repeat, R.string.display_name_boosted_by, boostStatus = nStatus)
    }
    val colorBg = PrefI.ipEventBgColorBoost(activity.pref)
    nStatus?.let { showNotificationStatus(it, colorBg) }
}

private fun ItemViewHolder.showNotificationMention(n: TootNotification, nAccountRef: TootAccountRef?, nStatus: TootStatus?) {
    // メンション通知に「～～からの返信」を表示するカラムなのかどうか
    fun willShowReplyInfo(status: TootStatus?): Boolean = when {
        // メンションではなく返信の場合、トゥート内部に「～への返信」を表示するので
        // 通知イベントの「～からの返信」を表示しない
        status.let { it?.in_reply_to_id != null && it.reply != null } -> false

        // XXX: 簡略表示だったりMisskeyだったりが影響してた時期もあったが、今後どうしようか…
        else -> true
    }

    if (willShowReplyInfo(nStatus)) {
        nAccountRef?.let { showBoost(it, n.time_created_at, R.drawable.ic_reply, R.string.display_name_mentioned_by) }
    }

    val colorBg = PrefI.ipEventBgColorMention(activity.pref)
    nStatus?.let { showNotificationStatus(it, colorBg) }
}

private fun ItemViewHolder.showNotificationQuote(n: TootNotification, nAccountRef: TootAccountRef?, nStatus: TootStatus?) {
    nAccountRef?.let { showBoost(it, n.time_created_at, R.drawable.ic_repeat, R.string.display_name_quoted_by) }

    val colorBg = PrefI.ipEventBgColorQuote(activity.pref)
    nStatus?.let { showNotificationStatus(it, colorBg) }
}

private fun ItemViewHolder.showNotificationVote(n: TootNotification, nAccountRef: TootAccountRef?, nStatus: TootStatus?) {
    nAccountRef?.let { showBoost(it, n.time_created_at, R.drawable.ic_vote, R.string.display_name_voted_by) }
    val colorBg = PrefI.ipEventBgColorVote(activity.pref)
    nStatus?.let { showNotificationStatus(it, colorBg) }
}

private fun ItemViewHolder.showNotificationPoll(n: TootNotification, nAccountRef: TootAccountRef?, nStatus: TootStatus?) {
    nAccountRef?.let { showBoost(it, n.time_created_at, R.drawable.ic_vote, R.string.end_of_polling_from) }
    val colorBg = 0
    nStatus?.let { showNotificationStatus(it, colorBg) }
}

private fun ItemViewHolder.showNotificationUnknown(n: TootNotification, nAccountRef: TootAccountRef?, nStatus: TootStatus?) {
    nAccountRef?.let { showBoost(it, n.time_created_at, R.drawable.ic_question, R.string.unknown_notification_from) }
    val colorBg = 0
    nStatus?.let { showNotificationStatus(it, colorBg) }

    tvMessageHolder.visibility = View.VISIBLE
    tvMessageHolder.text = "notification type is ${n.type}"
    tvMessageHolder.gravity = Gravity.CENTER
}

private fun ItemViewHolder.showNotificationStatus(item: TootStatus, colorBgDefault: Int) {
    val reblog = item.reblog
    when {
        reblog == null -> showStatusOrReply(item, colorBgDefault)

        item.isQuoteToot -> {
            // 引用Renote
            showReply(reblog, R.drawable.ic_repeat, R.string.quote_to)
            showStatus(item, PrefI.ipEventBgColorQuote(activity.pref))
        }

        else -> {
            // 通常のブースト。引用なしブースト。
            // ブースト表示は通知イベントと被るのでしない
            showStatusOrReply(reblog, PrefI.ipEventBgColorBoost(activity.pref))
        }
    }
}
