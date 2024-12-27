package jp.juggler.subwaytooter.itemviewholder

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.ActText
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.action.*
import jp.juggler.subwaytooter.actmain.nextPosition
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.column.Column
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.databinding.DlgContextMenuBinding
import jp.juggler.subwaytooter.dialog.DlgListMember
import jp.juggler.subwaytooter.dialog.dialogQrCode
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.pref.PrefI
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.table.*
import jp.juggler.subwaytooter.util.*
import jp.juggler.util.*
import jp.juggler.util.data.*
import jp.juggler.util.log.*
import jp.juggler.util.ui.*
import org.jetbrains.anko.allCaps
import org.jetbrains.anko.backgroundDrawable
import java.util.*

@SuppressLint("InflateParams")
internal class DlgContextMenu(
    val activity: ActMain,
    private val column: Column,
    private val whoRef: TootAccountRef?,
    private val status: TootStatus?,
    private val notification: TootNotification? = null,
    private val contentTextView: TextView? = null,
) : View.OnClickListener, View.OnLongClickListener {

//    companion object {
//        private val log = LogCategory("DlgContextMenu")
//    }

    private val accessInfo = column.accessInfo
    private val relation: UserRelation

    private val dialog: Dialog

    private val views = DlgContextMenuBinding.inflate(activity.layoutInflater)

//    private fun <T : View> fv(@IdRes id: Int): T = viewRoot.findViewById(id)
//
//    private val btnGroupStatusCrossAccount: Button = fv(R.id.btnGroupStatusCrossAccount)
//    private val llGroupStatusCrossAccount: View = fv(R.id.llGroupStatusCrossAccount)
//    private val btnGroupStatusAround: Button = fv(R.id.btnGroupStatusAround)
//    private val llGroupStatusAround: View = fv(R.id.llGroupStatusAround)
//    private val btnGroupStatusByMe: Button = fv(R.id.btnGroupStatusByMe)
//    private val llGroupStatusByMe: View = fv(R.id.llGroupStatusByMe)
//    private val btnGroupStatusExtra: Button = fv(R.id.btnGroupStatusExtra)
//    private val llGroupStatusExtra: View = fv(R.id.llGroupStatusExtra)
//    private val btnGroupUserCrossAccount: Button = fv(R.id.btnGroupUserCrossAccount)
//    private val llGroupUserCrossAccount: View = fv(R.id.llGroupUserCrossAccount)
//    private val btnGroupUserExtra: Button = fv(R.id.btnGroupUserExtra)
//    private val llGroupUserExtra: View = fv(R.id.llGroupUserExtra)

    init {
        val columnType = column.type

        val who = whoRef?.get()
        val status = this.status

        this.relation = when {
            who == null -> UserRelation()
            accessInfo.isPseudo -> daoUserRelation.loadPseudo(accessInfo.getFullAcct(who))
            else -> daoUserRelation.load(accessInfo.db_id, who.id)
        }

        this.dialog = Dialog(activity)
        dialog.setContentView(views.root)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        views.root.scan { v ->
            when (v) {
                is Button -> v.setOnClickListener(this)
                is ImageButton -> v.setOnClickListener(this)
            }
        }

        arrayOf(
            views.btnBlock,
            views.btnFollow,
            views.btnMute,
            views.btnProfile,
            views.btnQuoteAnotherAccount,
            views.btnQuoteTootBT,
            views.btnSendMessage,
        ).forEach { it.setOnLongClickListener(this) }

        val accountList = daoSavedAccount.loadAccountList()

        val accountListNonPseudo = ArrayList<SavedAccount>()
        for (a in accountList) {
            if (!a.isPseudo) {
                accountListNonPseudo.add(a)
                //				if( a.host.equalsIgnoreCase( access_info.host ) ){
                //					account_list_non_pseudo_same_instance.add( a );
                //				}
            }
        }

        if (status == null) {
            views.llStatus.visibility = View.GONE
            views.llLinks.visibility = View.GONE
        } else {
            val statusByMe = accessInfo.isMe(status.account)

            if (PrefB.bpLinksInContextMenu.value && contentTextView != null) {

                var insPos = 0

                fun addLinkButton(span: MyClickableSpan, caption: String) {
                    val b = AppCompatButton(activity)
                    val lp = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    b.layoutParams = lp
                    b.backgroundDrawable =
                        ContextCompat.getDrawable(activity, R.drawable.btn_bg_transparent_round6dp)
                    b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    b.minHeight = (activity.density * 32f + 0.5f).toInt()
                    b.minimumHeight = (activity.density * 32f + 0.5f).toInt()
                    val padLr = (activity.density * 8f + 0.5f).toInt()
                    val padTb = (activity.density * 4f + 0.5f).toInt()
                    b.setPaddingRelative(padLr, padTb, padLr, padTb)
                    b.text = caption
                    b.allCaps = false
                    b.setOnClickListener {
                        dialog.dismissSafe()
                        span.onClick(contentTextView)
                    }
                    views.llLinks.addView(b, insPos++)
                }

                val dc = status.decoded_content
                for (span in dc.getSpans(0, dc.length, MyClickableSpan::class.java)) {
                    val caption = span.linkInfo.text
                    when (caption.firstOrNull()) {
                        '@', '#' -> addLinkButton(span, caption)
                        else -> addLinkButton(span, span.linkInfo.url)
                    }
                }
            }

            val hasEditHistory =
                status.time_edited_at > 0L && columnType != ColumnType.STATUS_HISTORY

            views.btnStatusHistory2.vg(hasEditHistory)
            views.btnStatusHistory.vg(hasEditHistory)
                ?.text = activity.getString(R.string.edit_history) + "\n" +
                    TootStatus.formatTime(activity, status.time_edited_at, bAllowRelative = false)

            views.llLinks.vg(views.llLinks.childCount > 1)

            val hasTranslateApp = CustomShare.hasTranslateApp(
                CustomShareTarget.Translate,
                activity,
            )

            views.btnStatusTranslate2.vg(hasTranslateApp)
            views.btnTranslate.vg(hasTranslateApp)

            val canEdit = statusByMe && (TootInstance.getCached(column.accessInfo)
                ?.let {
                    when {
                        it.isMastodon && it.versionGE(TootInstance.VERSION_3_5_0_rc1) -> true
                        it.pleromaFeatures?.contains("editing") == true -> true
                        else -> false
                    }
                } ?: false)

            views.btnStatusEdit2.vg(canEdit)
            views.btnStatusEdit.vg(canEdit)

            views.btnStatusDelete2.vg(statusByMe)
            views.btnGroupStatusByMe.vg(statusByMe)

            views.btnQuoteTootBT.vg(status.reblogParent != null)

            views.btnBoostWithVisibility.vg(!accessInfo.isPseudo && !accessInfo.isMisskey)

            views.btnReportStatus.vg(!(statusByMe || accessInfo.isPseudo))

            val applicationName = status.application?.name
            if (statusByMe || applicationName == null || applicationName.isEmpty()) {
                views.btnMuteApp.visibility = View.GONE
            } else {
                views.btnMuteApp.text = activity.getString(R.string.mute_app_of, applicationName)
            }

            val canPin = status.canPin(accessInfo)
            views.btnProfileUnpin.vg(canPin && status.pinned)
            views.btnProfilePin.vg(canPin && !status.pinned)
        }

        val bShowConversationMute = when {
            status == null -> false
            accessInfo.isMe(status.account) -> true
            notification != null && NotificationType.Mention == notification.type -> true
            else -> false
        }

        val muted = status?.muted == true
        views.btnConversationMute.vg(bShowConversationMute)
            ?.setText(
                when {
                    muted -> R.string.unmute_this_conversation
                    else -> R.string.mute_this_conversation
                }
            )

        views.llNotification.vg(notification != null)

        val colorButtonAccent =
            PrefI.ipButtonFollowingColor.value.notZero()
                ?: activity.attrColor(R.attr.colorButtonAccentFollow)

        val colorButtonFollowRequest =
            PrefI.ipButtonFollowRequestColor.value.notZero()
                ?: activity.attrColor(R.attr.colorButtonAccentFollowRequest)

        val colorButtonNormal =
            activity.attrColor(R.attr.colorTextContent)

        fun showRelation(relation: UserRelation) {

            // 被フォロー状態
            // Styler.setFollowIconとは異なり細かい状態を表示しない
            views.ivFollowedBy.vg(relation.followed_by)

            // フォロー状態
            // Styler.setFollowIconとは異なりミュートやブロックを表示しない
            views.btnFollow.setImageResource(
                when {
                    relation.getRequested(who) -> R.drawable.ic_follow_wait
                    relation.getFollowing(who) -> R.drawable.ic_follow_cross
                    else -> R.drawable.ic_follow_plus
                }
            )

            arrayOf(
                views.btnStatusEdit2,
                views.btnStatusHistory2,
                views.btnStatusTranslate2,
                views.btnStatusDelete2,
            ).forEach {
                it.imageTintList = ColorStateList.valueOf(colorButtonNormal)
            }

            views.btnFollow.imageTintList = ColorStateList.valueOf(
                when {
                    relation.getRequested(who) -> colorButtonFollowRequest
                    relation.getFollowing(who) -> colorButtonAccent
                    else -> colorButtonNormal
                }
            )

            // ミュート状態
            views.btnMute.imageTintList = ColorStateList.valueOf(
                when (relation.muting) {
                    true -> colorButtonAccent
                    else -> colorButtonNormal
                }
            )

            // ブロック状態
            views.btnBlock.imageTintList = ColorStateList.valueOf(
                when (relation.blocking) {
                    true -> colorButtonAccent
                    else -> colorButtonNormal
                }
            )
        }

        if (accessInfo.isPseudo) {
            // 疑似アカミュートができたのでアカウントアクションを表示する
            showRelation(relation)
            views.llAccountActionBar.visibility = View.VISIBLE
            views.ivFollowedBy.vg(false)
            views.btnFollow.setImageResource(R.drawable.ic_follow_plus)
            views.btnFollow.imageTintList =
                ColorStateList.valueOf(colorButtonNormal)

            views.btnNotificationFrom.visibility = View.GONE
        } else {
            showRelation(relation)
        }

        val whoApiHost = getUserApiHost()
        val whoApDomain = getUserApDomain()

        views.llInstance
            .vg(whoApiHost.isValid)
            ?.let {
                val tvInstanceActions: TextView = views.tvInstanceActions
                tvInstanceActions.text =
                    activity.getString(R.string.instance_actions_for, whoApDomain.pretty)

                // 疑似アカウントではドメインブロックできない
                // 自ドメインはブロックできない
                views.btnDomainBlock.vg(
                    !(accessInfo.isPseudo || accessInfo.matchHost(whoApiHost))
                )

                views.btnDomainTimeline.vg(
                    PrefB.bpEnableDomainTimeline.value &&
                            !accessInfo.isPseudo &&
                            !accessInfo.isMisskey
                )
            }

        if (who == null) {
            views.btnCopyAccountId.visibility = View.GONE
            views.btnOpenAccountInAdminWebUi.visibility = View.GONE
            views.btnOpenInstanceInAdminWebUi.visibility = View.GONE

            views.btnReportUser.visibility = View.GONE
        } else {

            views.btnCopyAccountId.visibility = View.VISIBLE
            views.btnCopyAccountId.text =
                activity.getString(R.string.copy_account_id, who.id.toString())

            views.btnOpenAccountInAdminWebUi.vg(!accessInfo.isPseudo)
            views.btnOpenInstanceInAdminWebUi.vg(!accessInfo.isPseudo)

            views.btnReportUser.vg(!(accessInfo.isPseudo || accessInfo.isMe(who)))

            views.btnStatusNotification.vg(!accessInfo.isPseudo && accessInfo.isMastodon && relation.following)
                ?.text = when (relation.notifying) {
                true -> activity.getString(R.string.stop_notify_posts_from_this_user)
                else -> activity.getString(R.string.notify_posts_from_this_user)
            }
        }

        if (accessInfo.isPseudo) {
            views.btnProfile.visibility = View.GONE
            views.btnSendMessage.visibility = View.GONE
            views.btnEndorse.visibility = View.GONE
        }

        views.btnEndorse.text = when (relation.endorsed) {
            false -> activity.getString(R.string.endorse_set)
            else -> activity.getString(R.string.endorse_unset)
        }

        if (columnType != ColumnType.FOLLOW_REQUESTS) {
            views.btnFollowRequestOK.visibility = View.GONE
            views.btnFollowRequestNG.visibility = View.GONE
        }

        if (columnType != ColumnType.FOLLOW_SUGGESTION) {
            views.btnDeleteSuggestion.visibility = View.GONE
        }

        if (accountListNonPseudo.isEmpty()) {
            views.btnFollowFromAnotherAccount.visibility = View.GONE
            views.btnSendMessageFromAnotherAccount.visibility = View.GONE
        }

        if (accessInfo.isPseudo ||
            who == null ||
            !relation.getFollowing(who) ||
            relation.following_reblogs == UserRelation.REBLOG_UNKNOWN
        ) {
            views.btnHideBoost.visibility = View.GONE
            views.btnShowBoost.visibility = View.GONE
        } else if (relation.following_reblogs == UserRelation.REBLOG_SHOW) {
            views.btnHideBoost.visibility = View.VISIBLE
            views.btnShowBoost.visibility = View.GONE
        } else {
            views.btnHideBoost.visibility = View.GONE
            views.btnShowBoost.visibility = View.VISIBLE
        }

        when {
            who == null -> {
                views.btnHideFavourite.visibility = View.GONE
                views.btnShowFavourite.visibility = View.GONE
            }

            daoFavMute.contains(accessInfo.getFullAcct(who)) -> {
                views.btnHideFavourite.visibility = View.GONE
                views.btnShowFavourite.visibility = View.VISIBLE
            }

            else -> {
                views.btnHideFavourite.visibility = View.VISIBLE
                views.btnShowFavourite.visibility = View.GONE
            }
        }

        views.btnListMemberAddRemove.visibility = View.VISIBLE

        updateGroup(views.btnGroupStatusCrossAccount, views.llGroupStatusCrossAccount)
        updateGroup(views.btnGroupUserCrossAccount, views.llGroupUserCrossAccount)
        updateGroup(views.btnGroupStatusAround, views.llGroupStatusAround)
        updateGroup(views.btnGroupStatusByMe, views.llGroupStatusByMe)
        updateGroup(views.btnGroupStatusExtra, views.llGroupStatusExtra)
        updateGroup(views.btnGroupUserExtra, views.llGroupUserExtra)
    }

    fun show() {
        val window = dialog.window
        if (window != null) {
            val lp = window.attributes
            lp.width = (0.5f + 280f * activity.density).toInt()
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT
            window.attributes = lp
        }
        dialog.show()
    }

    private fun getUserApiHost(): Host =
        when (val whoHost = whoRef?.get()?.apiHost) {
            Host.UNKNOWN -> Host.parse(column.instanceUri)
            Host.EMPTY, null -> accessInfo.apiHost
            else -> whoHost
        }

    private fun getUserApDomain(): Host =
        when (val whoHost = whoRef?.get()?.apDomain) {
            Host.UNKNOWN -> Host.parse(column.instanceUri)
            Host.EMPTY, null -> accessInfo.apDomain
            else -> whoHost
        }

    private fun updateGroup(btn: Button, group: View, toggle: Boolean = false) {

        if (btn.visibility != View.VISIBLE) {
            group.vg(false)
            return
        }

        when {
            PrefB.bpAlwaysExpandContextMenuItems.value -> {
                group.vg(true)
                btn.background = null
            }

            toggle -> group.vg(group.visibility != View.VISIBLE)
            else -> btn.setOnClickListener(this)
        }

        val iconId = when (group.visibility) {
            View.VISIBLE -> R.drawable.ic_arrow_drop_up
            else -> R.drawable.ic_arrow_drop_down
        }

        val iconColor = activity.attrColor(R.attr.colorTimeSmall)
        val drawable = createColoredDrawable(activity, iconId, iconColor, 1f)
        btn.setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null)
    }

    private fun onClickUpdateGroup(v: View): Boolean {
        when (v.id) {
            R.id.btnGroupStatusCrossAccount -> updateGroup(
                views.btnGroupStatusCrossAccount,
                views.llGroupStatusCrossAccount,
                toggle = true
            )

            R.id.btnGroupUserCrossAccount -> updateGroup(
                views.btnGroupUserCrossAccount,
                views.llGroupUserCrossAccount,
                toggle = true
            )

            R.id.btnGroupStatusAround -> updateGroup(
                views.btnGroupStatusAround,
                views.llGroupStatusAround,
                toggle = true
            )

            R.id.btnGroupStatusByMe -> updateGroup(
                views.btnGroupStatusByMe,
                views.llGroupStatusByMe,
                toggle = true
            )

            R.id.btnGroupStatusExtra -> updateGroup(
                views.btnGroupStatusExtra,
                views.llGroupStatusExtra,
                toggle = true
            )

            R.id.btnGroupUserExtra -> updateGroup(
                views.btnGroupUserExtra,
                views.llGroupUserExtra,
                toggle = true
            )

            else -> return false
        }
        return true
    }

    private fun ActMain.onClickUserAndStatus(
        v: View,
        pos: Int,
        who: TootAccount,
        status: TootStatus,
    ): Boolean {
        when (v.id) {
            R.id.btnAroundAccountTL -> clickAroundAccountTL(accessInfo, pos, who, status)
            R.id.btnAroundLTL -> clickAroundLTL(accessInfo, pos, who, status)
            R.id.btnAroundFTL -> clickAroundFTL(accessInfo, pos, who, status)
            R.id.btnReportStatus -> userReportForm(accessInfo, who, status)
            else -> return false
        }
        return true
    }

    @Suppress("ComplexMethod")
    private fun ActMain.onClickUser(
        v: View,
        pos: Int,
        who: TootAccount,
        whoRef: TootAccountRef,
    ): Boolean {
        when (v.id) {
            R.id.btnReportUser -> userReportForm(accessInfo, who)
            R.id.btnFollow -> clickFollow(pos, accessInfo, whoRef, relation)
            R.id.btnMute -> clickMute(accessInfo, who, relation)
            R.id.btnBlock -> clickBlock(accessInfo, who, relation)
            R.id.btnAccountText -> launchActText(ActText.createIntent(activity, accessInfo, who))
            R.id.btnProfile -> userProfileLocal(pos, accessInfo, who)
            R.id.btnSendMessage -> mention(accessInfo, who)
            R.id.btnAccountWebPage -> openCustomTab(who.url)
            R.id.btnFollowRequestOK -> followRequestAuthorize(accessInfo, whoRef, true)
            R.id.btnDeleteSuggestion -> userSuggestionDelete(accessInfo, who)
            R.id.btnFollowRequestNG -> followRequestAuthorize(accessInfo, whoRef, false)
            R.id.btnFollowFromAnotherAccount -> followFromAnotherAccount(pos, accessInfo, who)
            R.id.btnSendMessageFromAnotherAccount -> mentionFromAnotherAccount(accessInfo, who)
            R.id.btnOpenProfileFromAnotherAccount -> userProfileFromAnotherAccount(
                pos,
                accessInfo,
                who
            )

            R.id.btnNickname -> clickNicknameCustomize(accessInfo, who)
            R.id.btnAccountQrCode -> activity.dialogQrCode(
                message = whoRef.decoded_display_name,
                url = who.getUserUrl()
            )

            R.id.btnDomainBlock -> clickDomainBlock(accessInfo, who)
            R.id.btnOpenTimeline -> who.apiHost.valid()?.let { timelineLocal(pos, it) }
            R.id.btnDomainTimeline -> who.apiHost.valid()
                ?.let { timelineDomain(pos, accessInfo, it) }

            R.id.btnAvatarImage -> openAvatarImage(who)
            R.id.btnQuoteName -> quoteName(who)
            R.id.btnHideBoost -> userSetShowBoosts(accessInfo, who, false)
            R.id.btnShowBoost -> userSetShowBoosts(accessInfo, who, true)
            R.id.btnHideFavourite -> clickHideFavourite(accessInfo, who)
            R.id.btnShowFavourite -> clickShowFavourite(accessInfo, who)
            R.id.btnListMemberAddRemove -> DlgListMember(activity, who, accessInfo).show()
            R.id.btnInstanceInformation -> serverInformation(pos, getUserApiHost())
            R.id.btnProfileDirectory -> serverProfileDirectoryFromInstanceInformation(
                column,
                getUserApiHost()
            )

            R.id.btnEndorse -> userEndorsement(accessInfo, who, !relation.endorsed)
            R.id.btnCopyAccountId -> who.id.toString().copyToClipboard(activity)
            R.id.btnOpenAccountInAdminWebUi -> openBrowser("https://${accessInfo.apiHost.ascii}/admin/accounts/${who.id}")
            R.id.btnOpenInstanceInAdminWebUi -> openBrowser("https://${accessInfo.apiHost.ascii}/admin/instances/${who.apDomain.ascii}")
            R.id.btnNotificationFrom -> clickNotificationFrom(pos, accessInfo, who)
            R.id.btnStatusNotification -> clickStatusNotification(accessInfo, who, relation)
            R.id.btnQuoteUrlAccount -> openPost(who.url?.notEmpty())
            R.id.btnShareUrlAccount -> shareText(who.url?.notEmpty())
            else -> return false
        }
        return true
    }

    private fun ActMain.onClickStatus(v: View, pos: Int, status: TootStatus): Boolean {
        when (v.id) {
            R.id.btnBoostWithVisibility -> clickBoostWithVisibility(accessInfo, status)
            R.id.btnStatusWebPage -> openCustomTab(status.url)
            R.id.btnText -> launchActText(ActText.createIntent(this, accessInfo, status))
            R.id.btnFavouriteAnotherAccount -> favouriteFromAnotherAccount(accessInfo, status)
            R.id.btnBookmarkAnotherAccount -> bookmarkFromAnotherAccount(accessInfo, status)
            R.id.btnBoostAnotherAccount -> boostFromAnotherAccount(accessInfo, status)
            R.id.btnReactionAnotherAccount -> reactionFromAnotherAccount(accessInfo, status)
            R.id.btnReplyAnotherAccount -> replyFromAnotherAccount(accessInfo, status)
            R.id.btnQuoteAnotherAccount -> quoteFromAnotherAccount(accessInfo, status)
            R.id.btnQuoteTootBT -> quoteFromAnotherAccount(accessInfo, status.reblogParent)
            R.id.btnConversationAnotherAccount -> conversationOtherInstance(pos, status)
            R.id.btnDelete, R.id.btnStatusDelete2 -> clickStatusDelete(accessInfo, status)
            R.id.btnRedraft -> statusRedraft(accessInfo, status)
            R.id.btnStatusEdit, R.id.btnStatusEdit2 -> statusEdit(accessInfo, status)
            R.id.btnMuteApp -> appMute(status.application)
            R.id.btnBoostedBy -> clickBoostBy(pos, accessInfo, status, ColumnType.BOOSTED_BY)
            R.id.btnFavouritedBy -> clickBoostBy(pos, accessInfo, status, ColumnType.FAVOURITED_BY)
            R.id.btnTranslate, R.id.btnStatusTranslate2 -> CustomShare.invokeStatusText(
                CustomShareTarget.Translate,
                activity,
                accessInfo,
                status
            )

            R.id.btnQuoteUrlStatus -> openPost(status.url?.notEmpty())
            R.id.btnShareUrlStatus -> shareText(status.url?.notEmpty())
            R.id.btnConversationMute -> conversationMute(accessInfo, status)
            R.id.btnProfilePin -> statusPin(accessInfo, status, true)
            R.id.btnProfileUnpin -> statusPin(accessInfo, status, false)
            R.id.btnStatusHistory, R.id.btnStatusHistory2 -> openStatusHistory(
                pos,
                accessInfo,
                status
            )

            else -> return false
        }
        return true
    }

    private fun ActMain.onClickOther(v: View) {
        when (v.id) {
            R.id.btnNotificationDelete -> notificationDeleteOne(accessInfo, notification)
            R.id.btnCancel -> dialog.cancel()
        }
    }

    override fun onClick(v: View) {
        if (onClickUpdateGroup(v)) return // ダイアログを閉じない操作

        dialog.dismissSafe()

        val pos = activity.nextPosition(column)
        val status = this.status
        val whoRef = this.whoRef
        val who = whoRef?.get()

        if (status != null && activity.onClickStatus(v, pos, status)) return

        if (whoRef != null && who != null) {
            when {
                activity.onClickUser(v, pos, who, whoRef) -> return
                status != null && activity.onClickUserAndStatus(v, pos, who, status) -> return
            }
        }

        activity.onClickOther(v)
    }

    override fun onLongClick(v: View): Boolean {
        val whoRef = this.whoRef
        val who = whoRef?.get()

        with(activity) {
            val pos = nextPosition(column)

            when (v.id) {
                // events don't close dialog
                R.id.btnMute -> userMuteFromAnotherAccount(who, accessInfo)
                R.id.btnBlock -> userBlockFromAnotherAccount(who, accessInfo)
                R.id.btnQuoteAnotherAccount -> quoteFromAnotherAccount(accessInfo, status)
                R.id.btnQuoteTootBT -> quoteFromAnotherAccount(accessInfo, status?.reblogParent)

                // events close dialog before action
                R.id.btnFollow -> {
                    dialog.dismissSafe()
                    followFromAnotherAccount(pos, accessInfo, who)
                }

                R.id.btnProfile -> {
                    dialog.dismissSafe()
                    userProfileFromAnotherAccount(pos, accessInfo, who)
                }

                R.id.btnSendMessage -> {
                    dialog.dismissSafe()
                    mentionFromAnotherAccount(accessInfo, who)
                }

                else -> return false
            }
        }
        return true
    }
}
