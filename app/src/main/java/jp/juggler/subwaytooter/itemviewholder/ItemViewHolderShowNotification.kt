package jp.juggler.subwaytooter.itemviewholder

import android.content.Context
import android.text.Spannable
import android.view.Gravity
import androidx.annotation.StringRes
import androidx.core.text.buildSpannedString
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.actmain.addColumn
import jp.juggler.subwaytooter.actmain.nextPosition
import jp.juggler.subwaytooter.api.entity.NotificationType
import jp.juggler.subwaytooter.api.entity.RelationshipSeveranceEvent
import jp.juggler.subwaytooter.api.entity.RelationshipSeveranceEventType
import jp.juggler.subwaytooter.api.entity.TootAccountRef
import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.api.entity.TootReaction
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.getVisibilityIconId
import jp.juggler.subwaytooter.pref.PrefI
import jp.juggler.subwaytooter.span.LinkInfo
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.data.notEmpty
import jp.juggler.util.data.notZero
import jp.juggler.util.log.LogCategory
import jp.juggler.util.ui.bold
import jp.juggler.util.ui.getSpannedString
import jp.juggler.util.ui.joinToSpannedString
import org.jetbrains.anko.backgroundColor

private val log = LogCategory("ItemViewHolderShowNotification")

fun ItemViewHolder.showNotification(n: TootNotification) {
    val nStatus = n.status
    val nAccountRef = n.accountRef
    when (n.type) {
        is NotificationType.Unknown ->
            showNotificationUnknown(n, nAccountRef, nStatus)

        NotificationType.Favourite ->
            showNotificationFavourite(n, nAccountRef, nStatus)

        NotificationType.Reblog ->
            showNotificationReblog(n, nAccountRef, nStatus)

        NotificationType.Renote ->
            showNotificationRenote(n, nAccountRef, nStatus)

        NotificationType.Follow ->
            showNotificationFollow(n, nAccountRef)

        NotificationType.Unfollow ->
            showNotificationUnfollow(n, nAccountRef)

        NotificationType.AdminSignup ->
            showNotificationSignup(n, nAccountRef)

        NotificationType.AdminReport ->
            showNotificationReport(n, nAccountRef)

        NotificationType.Mention,
        NotificationType.Reply,
            -> showNotificationMention(n, nAccountRef, nStatus)

        NotificationType.EmojiReactionPleroma,
        NotificationType.EmojiReactionFedibird,
        NotificationType.Reaction,
            -> showNotificationReaction(n, nAccountRef, nStatus)

        NotificationType.Quote ->
            showNotificationQuote(n, nAccountRef, nStatus)

        NotificationType.Status ->
            showNotificationPost(n, nAccountRef, nStatus)

        NotificationType.Update ->
            showNotificationUpdate(n, nAccountRef, nStatus)

        NotificationType.StatusReference ->
            showNotificationStatusReference(n, nAccountRef, nStatus)

        NotificationType.FollowRequest,
        NotificationType.FollowRequestMisskey,
            -> showNotificationFollowRequest(n, nAccountRef)

        NotificationType.FollowRequestAcceptedMisskey ->
            showNotificationFollowRequestAccepted(n, nAccountRef)

        NotificationType.Vote,
        NotificationType.PollVoteMisskey,
            -> showNotificationVote(n, nAccountRef, nStatus)

        NotificationType.Poll ->
            showNotificationPoll(n, nAccountRef, nStatus)

        NotificationType.ScheduledStatus ->
            showNotificationUnknown(n, nAccountRef, nStatus)

        NotificationType.SeveredRelationships ->
            showNotificationSeveredRelationship(n, nAccountRef, nStatus)
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

/**
 *
 */
private fun RelationshipSeveranceEvent.format(
    context: Context,
    account: SavedAccount,
    user: TootAccountRef?,
): CharSequence = with(context) {
    buildSpannedString {
        fun appendResString(@StringRes id: Int, vararg args: Any?) =
            append(getSpannedString(id, *args))

        // 管理者やモデレータのドメイン
        val adminDomain = account.apDomain.pretty

        // typeによりドメインかユーザ名か異なる
        val targetBold = (targetName?.notEmpty() ?: "(???)").bold()

        when (val type = type) {
            is RelationshipSeveranceEventType.Unknown ->
                append("unknown event type. ${type.code}")

            RelationshipSeveranceEventType.DomainBlock ->
                appendResString(
                    R.string.servered_relationships_domain_block,
                    adminDomain.bold(),
                    targetBold, // ドメイン名
                )

            RelationshipSeveranceEventType.AccountSuspension ->
                appendResString(
                    R.string.servered_relationships_account_suspension,
                    adminDomain.bold(),
                    targetBold, // ユーザ名
                )

            RelationshipSeveranceEventType.UserDomainBlock ->
                appendResString(
                    R.string.servered_relationships_account_suspension,
                    user?.decoded_display_name ?: "?", // 誰の名前が出るんだろう?
                    targetBold, // ドメイン名
                )
        }

        val losts = listOfNotNull(
            when (val n = followingCount) {
                null, 0 -> null
                1 -> getString(R.string.count_following_1, n)
                else -> getString(R.string.count_followings, n)
            }?.bold(),
            when (val n = followersCount) {
                null, 0 -> null
                1 -> getString(R.string.count_follower_1, n)
                else -> getString(R.string.count_followers, n)
            }?.bold(),
        )
        if (losts.isNotEmpty()) {
            append(" ")
            appendResString(
                R.string.lost_relationships,
                losts.joinToSpannedString(getString(R.string.losts_join))
            )
        }
        // リンク追加
        append(" ")
        val start = length
        appendResString(R.string.check_details_web_ui)
        val end = length
        setSpan(
            MyClickableSpan(LinkInfo(url = "https://${account.apiHost.ascii}/severed_relationships")),
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
}

private fun ItemViewHolder.showNotificationSeveredRelationship(
    n: TootNotification,
    nAccountRef: TootAccountRef?,
    nStatus: TootStatus?,
) {
    // この通知のための色設定はない
    val colorBg = 0
    // statusがなくても背景色は変えておく
    viewRoot.backgroundColor = colorBg
    // statusはないはずだが、あれば一応表示する
    nStatus?.let { showNotificationStatus(it, colorBg) }

    showMessageHolder(
        gravity = Gravity.START,
        text = n.event?.format(
            context = activity,
            account = column.accessInfo,
            user = nAccountRef,
        ) ?: "missing event details…",
        iconId = R.drawable.baseline_heart_broken_24,
        iconColor = colorTextContent,
    )
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
    showMessageHolder(
        text = "notification type is ${n.type}",
        gravity = Gravity.CENTER,
    )
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
