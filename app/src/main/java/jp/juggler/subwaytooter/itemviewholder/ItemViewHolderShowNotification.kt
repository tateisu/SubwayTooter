package jp.juggler.subwaytooter.itemviewholder

import android.view.Gravity
import android.view.View
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.actmain.addColumn
import jp.juggler.subwaytooter.actmain.nextPosition
import jp.juggler.subwaytooter.api.entity.TootAccountRef
import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.api.entity.TootReaction
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.getVisibilityIconId
import jp.juggler.subwaytooter.pref.PrefI
import jp.juggler.util.data.notZero
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

        TootNotification.TYPE_ADMIN_SIGNUP ->
            showNotificationSignup(n, nAccountRef)

        TootNotification.TYPE_ADMIN_REPORT ->
            showNotificationReport(n, nAccountRef)

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

        TootNotification.TYPE_UPDATE ->
            showNotificationUpdate(n, nAccountRef, nStatus)

        TootNotification.TYPE_STATUS_REFERENCE ->
            showNotificationStatusReference(n, nAccountRef, nStatus)

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

private fun ItemViewHolder.showNotificationFollow(
    n: TootNotification,
    nAccountRef: TootAccountRef?,
) {
    val colorBg = PrefI.ipEventBgColorFollow.value
    colorBg.notZero()?.let { viewRoot.backgroundColor = it }
    nAccountRef?.let {
        showBoost(
            it,
            n.time_created_at,
            R.drawable.ic_person_add,
            R.string.display_name_followed_by
        )
        showAccount(it)
    }
}

private fun ItemViewHolder.showNotificationUnfollow(
    n: TootNotification,
    nAccountRef: TootAccountRef?,
) {
    val colorBg = PrefI.ipEventBgColorUnfollow.value
    colorBg.notZero()?.let { viewRoot.backgroundColor = it }
    nAccountRef?.let {
        showBoost(
            it,
            n.time_created_at,
            R.drawable.ic_follow_cross,
            R.string.display_name_unfollowed_by
        )
        showAccount(it)
    }
}

private fun ItemViewHolder.showNotificationSignup(
    n: TootNotification,
    nAccountRef: TootAccountRef?,
) {
    val colorBg = PrefI.ipEventBgColorSignUp.value
    colorBg.notZero()?.let { viewRoot.backgroundColor = it }
    nAccountRef?.let {
        showBoost(
            it,
            n.time_created_at,
            R.drawable.ic_add,
            R.string.display_name_signed_up
        )
        showAccount(it)
    }
}
private fun ItemViewHolder.showNotificationReport(
    n: TootNotification,
    nAccountRef: TootAccountRef?,
) {
    val colorBg = PrefI.ipEventBgColorReport.value
    colorBg.notZero()?.let { viewRoot.backgroundColor = it }
    nAccountRef?.let {
        showBoost(
            it,
            n.time_created_at,
            R.drawable.ic_follow_wait,
            R.string.display_name_report
        )
        showAccount(it)
    }
}

private fun ItemViewHolder.showNotificationFollowRequest(
    n: TootNotification,
    nAccountRef: TootAccountRef?,
) {
    val colorBg = PrefI.ipEventBgColorFollowRequest.value
    colorBg.notZero()?.let { viewRoot.backgroundColor = it }
    nAccountRef?.let {
        showBoost(
            it,
            n.time_created_at,
            R.drawable.ic_follow_wait,
            R.string.display_name_follow_request_by
        )
        showAccount(it)
    }
    boostedAction = {
        activity.addColumn(activity.nextPosition(column), accessInfo, ColumnType.FOLLOW_REQUESTS)
    }
}

private fun ItemViewHolder.showNotificationFollowRequestAccepted(
    n: TootNotification,
    nAccountRef: TootAccountRef?,
) {
    val colorBg = PrefI.ipEventBgColorFollow.value
    colorBg.notZero()?.let { viewRoot.backgroundColor = it }
    nAccountRef?.let {
        showBoost(
            it,
            n.time_created_at,
            R.drawable.ic_person_add,
            R.string.display_name_follow_request_accepted_by
        )
        showAccount(it)
    }
}

private fun ItemViewHolder.showNotificationPost(
    n: TootNotification,
    nAccountRef: TootAccountRef?,
    nStatus: TootStatus?,
) {
    val colorBg = PrefI.ipEventBgColorStatus.value
    val iconId = when (nStatus) {
        null -> R.drawable.ic_question
        else -> nStatus.visibility.getVisibilityIconId(accessInfo.isMisskey)
    }
    nAccountRef?.let { showBoost(it, n.time_created_at, iconId, R.string.display_name_posted_by) }
    nStatus?.let { showNotificationStatus(it, colorBg) }
}

private fun ItemViewHolder.showNotificationUpdate(
    n: TootNotification,
    nAccountRef: TootAccountRef?,
    nStatus: TootStatus?,
) {
    val colorBg = PrefI.ipEventBgColorUpdate.value
    val iconId = R.drawable.ic_history
    nAccountRef?.let {
        showBoost(
            it,
            n.time_created_at,
            iconId,
            R.string.display_name_updates_post
        )
    }
    nStatus?.let { showNotificationStatus(it, colorBg) }
}

private fun ItemViewHolder.showNotificationStatusReference(
    n: TootNotification,
    nAccountRef: TootAccountRef?,
    nStatus: TootStatus?,
) {
    val colorBg = PrefI.ipEventBgColorStatusReference.value
    val iconId = R.drawable.ic_link_variant
    nAccountRef?.let {
        showBoost(
            it,
            n.time_created_at,
            iconId,
            R.string.display_name_references_post
        )
    }
    nStatus?.let { showNotificationStatus(it, colorBg) }
}

private fun ItemViewHolder.showNotificationReaction(
    n: TootNotification,
    nAccountRef: TootAccountRef?,
    nStatus: TootStatus?,
) {
    val colorBg = PrefI.ipEventBgColorReaction.value
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

private fun ItemViewHolder.showNotificationFavourite(
    n: TootNotification,
    nAccountRef: TootAccountRef?,
    nStatus: TootStatus?,
) {
    nAccountRef?.let {
        val iconId = R.drawable.ic_star_outline
        showBoost(it, n.time_created_at, iconId, R.string.display_name_favourited_by)
    }
    val colorBg = PrefI.ipEventBgColorFavourite.value
    nStatus?.let { showNotificationStatus(it, colorBg, fadeText = true) }
}

private fun ItemViewHolder.showNotificationReblog(
    n: TootNotification,
    nAccountRef: TootAccountRef?,
    nStatus: TootStatus?,
) {
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
    val colorBg = PrefI.ipEventBgColorBoost.value
    nStatus?.let { showNotificationStatus(it, colorBg, fadeText = true) }
}

private fun ItemViewHolder.showNotificationRenote(
    n: TootNotification,
    nAccountRef: TootAccountRef?,
    nStatus: TootStatus?,
) {
    // 引用のないreblog
    nAccountRef?.let {
        showBoost(
            it,
            n.time_created_at,
            R.drawable.ic_repeat,
            R.string.display_name_boosted_by,
            boostStatus = nStatus
        )
    }
    val colorBg = PrefI.ipEventBgColorBoost.value
    nStatus?.let { showNotificationStatus(it, colorBg) }
}

private fun ItemViewHolder.showNotificationMention(
    n: TootNotification,
    nAccountRef: TootAccountRef?,
    nStatus: TootStatus?,
) {
    // メンション通知に「～～からの返信」を表示するカラムなのかどうか
    fun willShowReplyInfo(status: TootStatus?): Boolean = when {
        // メンションではなく返信の場合、トゥート内部に「～への返信」を表示するので
        // 通知イベントの「～からの返信」を表示しない
        status.let { it?.in_reply_to_id != null && it.reply != null } -> false

        // XXX: 簡略表示だったりMisskeyだったりが影響してた時期もあったが、今後どうしようか…
        else -> true
    }

    if (willShowReplyInfo(nStatus)) {
        nAccountRef?.let {
            showBoost(
                it,
                n.time_created_at,
                R.drawable.ic_reply,
                R.string.display_name_mentioned_by
            )
        }
    }

    val colorBg = PrefI.ipEventBgColorMention.value
    nStatus?.let { showNotificationStatus(it, colorBg) }
}

private fun ItemViewHolder.showNotificationQuote(
    n: TootNotification,
    nAccountRef: TootAccountRef?,
    nStatus: TootStatus?,
) {
    nAccountRef?.let {
        showBoost(
            it,
            n.time_created_at,
            R.drawable.ic_quote,
            R.string.display_name_quoted_by
        )
    }

    val colorBg = PrefI.ipEventBgColorQuote.value
    nStatus?.let { showNotificationStatus(it, colorBg) }
}

private fun ItemViewHolder.showNotificationVote(
    n: TootNotification,
    nAccountRef: TootAccountRef?,
    nStatus: TootStatus?,
) {
    nAccountRef?.let {
        showBoost(
            it,
            n.time_created_at,
            R.drawable.ic_vote,
            R.string.display_name_voted_by
        )
    }
    val colorBg = PrefI.ipEventBgColorVote.value
    nStatus?.let { showNotificationStatus(it, colorBg) }
}

private fun ItemViewHolder.showNotificationPoll(
    n: TootNotification,
    nAccountRef: TootAccountRef?,
    nStatus: TootStatus?,
) {
    nAccountRef?.let {
        showBoost(
            it,
            n.time_created_at,
            R.drawable.ic_vote,
            R.string.end_of_polling_from
        )
    }
    val colorBg = 0
    nStatus?.let { showNotificationStatus(it, colorBg) }
}

private fun ItemViewHolder.showNotificationUnknown(
    n: TootNotification,
    nAccountRef: TootAccountRef?,
    nStatus: TootStatus?,
) {
    nAccountRef?.let {
        showBoost(
            it,
            n.time_created_at,
            R.drawable.ic_question,
            R.string.unknown_notification_from
        )
    }
    val colorBg = 0
    nStatus?.let { showNotificationStatus(it, colorBg) }

    tvMessageHolder.visibility = View.VISIBLE
    tvMessageHolder.text = "notification type is ${n.type}"
    tvMessageHolder.gravity = Gravity.CENTER
}

private fun ItemViewHolder.showNotificationStatus(
    item: TootStatus,
    colorBgDefault: Int,
    fadeText: Boolean = false,
) {
    val reblog = item.reblog
    when {
        reblog == null -> showStatusOrReply(item, colorBgDefault, fadeText = fadeText)

        item.isQuoteToot -> {
            // 引用Renote
            showReply(item.account, reblog, R.drawable.ic_quote, R.string.quote_to)
            showStatus(item, PrefI.ipEventBgColorQuote.value, fadeText = fadeText)
        }

        else -> {
            // 通常のブースト。引用なしブースト。
            // ブースト表示は通知イベントと被るのでしない
            showStatusOrReply(reblog, PrefI.ipEventBgColorBoost.value, fadeText = fadeText)
        }
    }
}
