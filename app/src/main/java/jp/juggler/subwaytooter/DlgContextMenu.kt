package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.core.content.ContextCompat
import jp.juggler.subwaytooter.action.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.DlgListMember
import jp.juggler.subwaytooter.dialog.DlgQRCode
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.table.FavMute
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.util.*
import jp.juggler.util.*
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

    private val viewRoot = activity.layoutInflater.inflate(R.layout.dlg_context_menu, null, false)

    private val btnCrossAccountActionsForStatus: Button =
        viewRoot.findViewById(R.id.btnCrossAccountActionsForStatus)
    private val llCrossAccountActionsForStatus: View =
        viewRoot.findViewById(R.id.llCrossAccountActionsForStatus)

    private val btnCrossAccountActionsForAccount: Button =
        viewRoot.findViewById(R.id.btnCrossAccountActionsForAccount)
    private val llCrossAccountActionsForAccount: View =
        viewRoot.findViewById(R.id.llCrossAccountActionsForAccount)

    private val btnAroundThisToot: Button =
        viewRoot.findViewById(R.id.btnAroundThisToot)
    private val llAroundThisToot: View =
        viewRoot.findViewById(R.id.llAroundThisToot)

    private val btnYourToot: Button =
        viewRoot.findViewById(R.id.btnYourToot)
    private val llYourToot: View =
        viewRoot.findViewById(R.id.llYourToot)

    private val btnStatusExtraAction: Button =
        viewRoot.findViewById(R.id.btnStatusExtraAction)
    private val llStatusExtraAction: View =
        viewRoot.findViewById(R.id.llStatusExtraAction)

    private val btnAccountExtraAction: Button =
        viewRoot.findViewById(R.id.btnAccountExtraAction)
    private val llAccountExtraAction: View =
        viewRoot.findViewById(R.id.llAccountExtraAction)

    private val btnPostNotification: Button = viewRoot.findViewById(R.id.btnPostNotification)

    init {
        val columnType = column.type

        val who = whoRef?.get()
        val status = this.status

        this.relation = when {
            who == null -> UserRelation()
            accessInfo.isPseudo -> UserRelation.loadPseudo(accessInfo.getFullAcct(who))
            else -> UserRelation.load(accessInfo.db_id, who.id)
        }

        this.dialog = Dialog(activity)
        dialog.setContentView(viewRoot)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        val llStatus: View = viewRoot.findViewById(R.id.llStatus)
        val btnStatusWebPage: View = viewRoot.findViewById(R.id.btnStatusWebPage)
        val btnText: View = viewRoot.findViewById(R.id.btnText)
        val btnFavouriteAnotherAccount: View =
            viewRoot.findViewById(R.id.btnFavouriteAnotherAccount)
        val btnBookmarkAnotherAccount: View =
            viewRoot.findViewById(R.id.btnBookmarkAnotherAccount)
        val btnBoostAnotherAccount: View = viewRoot.findViewById(R.id.btnBoostAnotherAccount)
        val btnReactionAnotherAccount: View = viewRoot.findViewById(R.id.btnReactionAnotherAccount)
        val btnReplyAnotherAccount: View = viewRoot.findViewById(R.id.btnReplyAnotherAccount)
        val btnQuoteAnotherAccount: View = viewRoot.findViewById(R.id.btnQuoteAnotherAccount)
        val btnQuoteTootBT: View = viewRoot.findViewById(R.id.btnQuoteTootBT)
        val btnDelete: View = viewRoot.findViewById(R.id.btnDelete)
        val btnRedraft: View = viewRoot.findViewById(R.id.btnRedraft)

        val btnReportStatus: View = viewRoot.findViewById(R.id.btnReportStatus)
        val btnReportUser: View = viewRoot.findViewById(R.id.btnReportUser)
        val btnMuteApp: Button = viewRoot.findViewById(R.id.btnMuteApp)
        val llAccountActionBar: View = viewRoot.findViewById(R.id.llAccountActionBar)
        val btnFollow: ImageButton = viewRoot.findViewById(R.id.btnFollow)

        val btnMute: ImageView = viewRoot.findViewById(R.id.btnMute)
        val btnBlock: ImageView = viewRoot.findViewById(R.id.btnBlock)
        val btnProfile: View = viewRoot.findViewById(R.id.btnProfile)
        val btnSendMessage: View = viewRoot.findViewById(R.id.btnSendMessage)
        val btnAccountWebPage: View = viewRoot.findViewById(R.id.btnAccountWebPage)
        val btnFollowRequestOK: View = viewRoot.findViewById(R.id.btnFollowRequestOK)
        val btnFollowRequestNG: View = viewRoot.findViewById(R.id.btnFollowRequestNG)
        val btnDeleteSuggestion: View = viewRoot.findViewById(R.id.btnDeleteSuggestion)
        val btnFollowFromAnotherAccount: View =
            viewRoot.findViewById(R.id.btnFollowFromAnotherAccount)
        val btnSendMessageFromAnotherAccount: View =
            viewRoot.findViewById(R.id.btnSendMessageFromAnotherAccount)
        val btnOpenProfileFromAnotherAccount: View =
            viewRoot.findViewById(R.id.btnOpenProfileFromAnotherAccount)
        val btnDomainBlock: Button = viewRoot.findViewById(R.id.btnDomainBlock)
        val btnInstanceInformation: Button = viewRoot.findViewById(R.id.btnInstanceInformation)
        val btnProfileDirectory: Button = viewRoot.findViewById(R.id.btnProfileDirectory)
        val ivFollowedBy: ImageView = viewRoot.findViewById(R.id.ivFollowedBy)
        val btnOpenTimeline: Button = viewRoot.findViewById(R.id.btnOpenTimeline)
        val btnConversationAnotherAccount: View =
            viewRoot.findViewById(R.id.btnConversationAnotherAccount)
        val btnAvatarImage: View = viewRoot.findViewById(R.id.btnAvatarImage)

        val llNotification: View = viewRoot.findViewById(R.id.llNotification)
        val btnNotificationDelete: View = viewRoot.findViewById(R.id.btnNotificationDelete)
        val btnConversationMute: Button = viewRoot.findViewById(R.id.btnConversationMute)

        val btnHideBoost: View = viewRoot.findViewById(R.id.btnHideBoost)
        val btnShowBoost: View = viewRoot.findViewById(R.id.btnShowBoost)
        val btnHideFavourite: View = viewRoot.findViewById(R.id.btnHideFavourite)
        val btnShowFavourite: View = viewRoot.findViewById(R.id.btnShowFavourite)

        val btnListMemberAddRemove: View = viewRoot.findViewById(R.id.btnListMemberAddRemove)
        val btnEndorse: Button = viewRoot.findViewById(R.id.btnEndorse)

        val btnAroundAccountTL: View = viewRoot.findViewById(R.id.btnAroundAccountTL)
        val btnAroundLTL: View = viewRoot.findViewById(R.id.btnAroundLTL)
        val btnAroundFTL: View = viewRoot.findViewById(R.id.btnAroundFTL)
        val btnCopyAccountId: Button = viewRoot.findViewById(R.id.btnCopyAccountId)
        val btnOpenAccountInAdminWebUi: Button =
            viewRoot.findViewById(R.id.btnOpenAccountInAdminWebUi)
        val btnOpenInstanceInAdminWebUi: Button =
            viewRoot.findViewById(R.id.btnOpenInstanceInAdminWebUi)
        val btnBoostWithVisibility: Button = viewRoot.findViewById(R.id.btnBoostWithVisibility)
        val llLinks: LinearLayout = viewRoot.findViewById(R.id.llLinks)

        val btnNotificationFrom: Button = viewRoot.findViewById(R.id.btnNotificationFrom)
        val btnProfilePin = viewRoot.findViewById<View>(R.id.btnProfilePin)
        val btnProfileUnpin = viewRoot.findViewById<View>(R.id.btnProfileUnpin)
        val btnBoostedBy = viewRoot.findViewById<View>(R.id.btnBoostedBy)
        val btnFavouritedBy = viewRoot.findViewById<View>(R.id.btnFavouritedBy)

        val btnDomainTimeline = viewRoot.findViewById<View>(R.id.btnDomainTimeline)

        arrayOf(
            btnNotificationFrom,
            btnAroundAccountTL,
            btnAroundLTL,
            btnAroundFTL,
            btnStatusWebPage,
            btnText,
            btnFavouriteAnotherAccount,
            btnBookmarkAnotherAccount,
            btnBoostAnotherAccount,
            btnReactionAnotherAccount,
            btnReplyAnotherAccount,
            btnQuoteAnotherAccount,
            btnQuoteTootBT,
            btnReportStatus,
            btnReportUser,
            btnMuteApp,
            btnDelete,
            btnRedraft,
            btnFollow,
            btnMute,
            btnBlock,
            btnProfile,
            btnSendMessage,
            btnAccountWebPage,
            btnFollowRequestOK,
            btnFollowRequestNG,
            btnDeleteSuggestion,
            btnFollowFromAnotherAccount,
            btnSendMessageFromAnotherAccount,
            btnOpenProfileFromAnotherAccount,
            btnOpenTimeline,
            btnConversationAnotherAccount,
            btnAvatarImage,
            btnNotificationDelete,
            btnConversationMute,
            btnHideBoost,
            btnShowBoost,
            btnHideFavourite,
            btnShowFavourite,
            btnListMemberAddRemove,
            btnInstanceInformation,
            btnProfileDirectory,
            btnDomainBlock,
            btnEndorse,
            btnCopyAccountId,
            btnOpenAccountInAdminWebUi,
            btnOpenInstanceInAdminWebUi,
            btnBoostWithVisibility,
            btnProfilePin,
            btnProfileUnpin,
            btnBoostedBy,
            btnFavouritedBy,
            btnDomainTimeline,
            btnPostNotification,

            viewRoot.findViewById(R.id.btnQuoteUrlStatus),
            viewRoot.findViewById(R.id.btnTranslate),
            viewRoot.findViewById(R.id.btnQuoteUrlAccount),
            viewRoot.findViewById(R.id.btnShareUrlStatus),
            viewRoot.findViewById(R.id.btnShareUrlAccount),
            viewRoot.findViewById(R.id.btnQuoteName)

        ).forEach {
            it.setOnClickListener(this@DlgContextMenu)
        }

        arrayOf(
            btnFollow,
            btnProfile,
            btnMute,
            btnBlock,
            btnSendMessage,
            btnQuoteAnotherAccount,
            btnQuoteTootBT,
        ).forEach {
            it.setOnLongClickListener(this)
        }

        val accountList = SavedAccount.loadAccountList(activity)
        //	final ArrayList< SavedAccount > account_list_non_pseudo_same_instance = new ArrayList<>();

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
            llStatus.visibility = View.GONE
            llLinks.visibility = View.GONE
        } else {
            val statusByMe = accessInfo.isMe(status.account)

            if (PrefB.bpLinksInContextMenu(activity.pref) && contentTextView != null) {

                var insPos = 0

                fun addLinkButton(span: MyClickableSpan, caption: String) {
                    val b = Button(activity)
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
                    llLinks.addView(b, insPos++)
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
            llLinks.vg(llLinks.childCount > 1)

            btnYourToot.vg(statusByMe)

            btnQuoteTootBT.vg(status.reblogParent != null)

            btnBoostWithVisibility.vg(!accessInfo.isPseudo && !accessInfo.isMisskey)

            btnReportStatus.vg(!(statusByMe || accessInfo.isPseudo))

            val applicationName = status.application?.name
            if (statusByMe || applicationName == null || applicationName.isEmpty()) {
                btnMuteApp.visibility = View.GONE
            } else {
                btnMuteApp.text = activity.getString(R.string.mute_app_of, applicationName)
            }

            val canPin = status.canPin(accessInfo)
            btnProfileUnpin.vg(canPin && status.pinned)
            btnProfilePin.vg(canPin && !status.pinned)
        }

        val bShowConversationMute = when {
            status == null -> false
            accessInfo.isMe(status.account) -> true
            notification != null && TootNotification.TYPE_MENTION == notification.type -> true
            else -> false
        }

        val muted = status?.muted ?: false
        btnConversationMute.vg(bShowConversationMute)
            ?.setText(
                when {
                    muted -> R.string.unmute_this_conversation
                    else -> R.string.mute_this_conversation
                }
            )

        llNotification.vg(notification != null)

        val colorButtonAccent =
            PrefI.ipButtonFollowingColor(activity.pref).notZero()
                ?: activity.attrColor(R.attr.colorImageButtonAccent)

        val colorButtonError =
            PrefI.ipButtonFollowRequestColor(activity.pref).notZero()
                ?: activity.attrColor(R.attr.colorRegexFilterError)

        val colorButtonNormal =
            activity.attrColor(R.attr.colorImageButton)

        fun showRelation(relation: UserRelation) {

            // 被フォロー状態
            // Styler.setFollowIconとは異なり細かい状態を表示しない
            ivFollowedBy.vg(relation.followed_by)

            // フォロー状態
            // Styler.setFollowIconとは異なりミュートやブロックを表示しない
            btnFollow.setImageResource(
                when {
                    relation.getRequested(who) -> R.drawable.ic_follow_wait
                    relation.getFollowing(who) -> R.drawable.ic_follow_cross
                    else -> R.drawable.ic_follow_plus
                }
            )

            btnFollow.imageTintList = ColorStateList.valueOf(
                when {
                    relation.getRequested(who) -> colorButtonError
                    relation.getFollowing(who) -> colorButtonAccent
                    else -> colorButtonNormal
                }
            )

            // ミュート状態
            btnMute.imageTintList = ColorStateList.valueOf(
                when (relation.muting) {
                    true -> colorButtonAccent
                    else -> colorButtonNormal
                }
            )

            // ブロック状態
            btnBlock.imageTintList = ColorStateList.valueOf(
                when (relation.blocking) {
                    true -> colorButtonAccent
                    else -> colorButtonNormal
                }
            )
        }

        if (accessInfo.isPseudo) {
            // 疑似アカミュートができたのでアカウントアクションを表示する
            showRelation(relation)
            llAccountActionBar.visibility = View.VISIBLE
            ivFollowedBy.vg(false)
            btnFollow.setImageResource(R.drawable.ic_follow_plus)
            btnFollow.imageTintList =
                ColorStateList.valueOf(activity.attrColor(R.attr.colorImageButton))

            btnNotificationFrom.visibility = View.GONE
        } else {
            showRelation(relation)
        }

        val whoApiHost = getUserApiHost()
        val whoApDomain = getUserApDomain()

        viewRoot.findViewById<View>(R.id.llInstance)
            .vg(whoApiHost.isValid)
            ?.let {
                val tvInstanceActions: TextView = viewRoot.findViewById(R.id.tvInstanceActions)
                tvInstanceActions.text =
                    activity.getString(R.string.instance_actions_for, whoApDomain.pretty)

                // 疑似アカウントではドメインブロックできない
                // 自ドメインはブロックできない
                btnDomainBlock.vg(
                    !(accessInfo.isPseudo || accessInfo.matchHost(whoApiHost))
                )

                btnDomainTimeline.vg(
                    PrefB.bpEnableDomainTimeline(activity.pref) &&
                        !accessInfo.isPseudo &&
                        !accessInfo.isMisskey
                )
            }

        if (who == null) {
            btnCopyAccountId.visibility = View.GONE
            btnOpenAccountInAdminWebUi.visibility = View.GONE
            btnOpenInstanceInAdminWebUi.visibility = View.GONE

            btnReportUser.visibility = View.GONE
        } else {

            btnCopyAccountId.visibility = View.VISIBLE
            btnCopyAccountId.text = activity.getString(R.string.copy_account_id, who.id.toString())

            btnOpenAccountInAdminWebUi.vg(!accessInfo.isPseudo)
            btnOpenInstanceInAdminWebUi.vg(!accessInfo.isPseudo)

            btnReportUser.vg(!(accessInfo.isPseudo || accessInfo.isMe(who)))

            btnPostNotification.vg(!accessInfo.isPseudo && accessInfo.isMastodon && relation.following)
                ?.let {
                    it.text = when (relation.notifying) {
                        true -> activity.getString(R.string.stop_notify_posts_from_this_user)
                        else -> activity.getString(R.string.notify_posts_from_this_user)
                    }
                }
        }

        viewRoot.findViewById<View>(R.id.btnAccountText).setOnClickListener(this)

        if (accessInfo.isPseudo) {
            btnProfile.visibility = View.GONE
            btnSendMessage.visibility = View.GONE
            btnEndorse.visibility = View.GONE
        }

        btnEndorse.text = when (relation.endorsed) {
            false -> activity.getString(R.string.endorse_set)
            else -> activity.getString(R.string.endorse_unset)
        }

        if (columnType != ColumnType.FOLLOW_REQUESTS) {
            btnFollowRequestOK.visibility = View.GONE
            btnFollowRequestNG.visibility = View.GONE
        }

        if (columnType != ColumnType.FOLLOW_SUGGESTION) {
            btnDeleteSuggestion.visibility = View.GONE
        }

        if (accountListNonPseudo.isEmpty()) {
            btnFollowFromAnotherAccount.visibility = View.GONE
            btnSendMessageFromAnotherAccount.visibility = View.GONE
        }

        viewRoot.findViewById<View>(R.id.btnNickname).setOnClickListener(this)
        viewRoot.findViewById<View>(R.id.btnCancel).setOnClickListener(this)
        viewRoot.findViewById<View>(R.id.btnAccountQrCode).setOnClickListener(this)

        if (accessInfo.isPseudo ||
            who == null ||
            !relation.getFollowing(who) ||
            relation.following_reblogs == UserRelation.REBLOG_UNKNOWN
        ) {
            btnHideBoost.visibility = View.GONE
            btnShowBoost.visibility = View.GONE
        } else if (relation.following_reblogs == UserRelation.REBLOG_SHOW) {
            btnHideBoost.visibility = View.VISIBLE
            btnShowBoost.visibility = View.GONE
        } else {
            btnHideBoost.visibility = View.GONE
            btnShowBoost.visibility = View.VISIBLE
        }

        when {
            who == null -> {
                btnHideFavourite.visibility = View.GONE
                btnShowFavourite.visibility = View.GONE
            }

            FavMute.contains(accessInfo.getFullAcct(who)) -> {
                btnHideFavourite.visibility = View.GONE
                btnShowFavourite.visibility = View.VISIBLE
            }

            else -> {
                btnHideFavourite.visibility = View.VISIBLE
                btnShowFavourite.visibility = View.GONE
            }
        }

        btnListMemberAddRemove.visibility = View.VISIBLE

        updateGroup(btnCrossAccountActionsForStatus, llCrossAccountActionsForStatus)
        updateGroup(btnCrossAccountActionsForAccount, llCrossAccountActionsForAccount)
        updateGroup(btnAroundThisToot, llAroundThisToot)
        updateGroup(btnYourToot, llYourToot)
        updateGroup(btnStatusExtraAction, llStatusExtraAction)
        updateGroup(btnAccountExtraAction, llAccountExtraAction)
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

    private fun updateGroup(btn: Button, group: View, toggle: Boolean = false): Boolean {

        if (btn.visibility != View.VISIBLE) {
            group.vg(false)
            return true
        }

        when {
            PrefB.bpAlwaysExpandContextMenuItems(activity.pref) -> {
                group.vg(true)
                btn.background = null
            }

            toggle -> group.vg(group.visibility != View.VISIBLE)
            else -> btn.setOnClickListener(this)
        }

        val iconId = if (group.visibility == View.VISIBLE) {
            R.drawable.ic_arrow_drop_up
        } else {
            R.drawable.ic_arrow_drop_down
        }

        val iconColor = activity.attrColor(R.attr.colorTimeSmall)
        val drawable = createColoredDrawable(activity, iconId, iconColor, 1f)
        btn.setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null)
        return true
    }

    fun onClickUpdateGroup(v: View): Boolean = when (v.id) {
        R.id.btnCrossAccountActionsForStatus -> updateGroup(
            btnCrossAccountActionsForStatus,
            llCrossAccountActionsForStatus,
            toggle = true
        )

        R.id.btnCrossAccountActionsForAccount -> updateGroup(
            btnCrossAccountActionsForAccount,
            llCrossAccountActionsForAccount,
            toggle = true
        )
        R.id.btnAroundThisToot -> updateGroup(
            btnAroundThisToot,
            llAroundThisToot,
            toggle = true
        )
        R.id.btnYourToot -> updateGroup(
            btnYourToot,
            llYourToot,
            toggle = true
        )
        R.id.btnStatusExtraAction -> updateGroup(
            btnStatusExtraAction,
            llStatusExtraAction,
            toggle = true
        )
        R.id.btnAccountExtraAction -> updateGroup(
            btnAccountExtraAction,
            llAccountExtraAction,
            toggle = true
        )
        else -> false
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

    private fun ActMain.onClickUser(v: View, pos: Int, who: TootAccount, whoRef: TootAccountRef): Boolean {
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
            R.id.btnOpenProfileFromAnotherAccount -> userProfileFromAnotherAccount(pos, accessInfo, who)
            R.id.btnNickname -> clickNicknameCustomize(accessInfo, who)
            R.id.btnAccountQrCode -> DlgQRCode.open(activity, whoRef.decoded_display_name, who.getUserUrl())
            R.id.btnDomainBlock -> clickDomainBlock(accessInfo, who)
            R.id.btnOpenTimeline -> who.apiHost.valid()?.let { timelineLocal(pos, it) }
            R.id.btnDomainTimeline -> who.apiHost.valid()?.let { timelineDomain(pos, accessInfo, it) }
            R.id.btnAvatarImage -> openAvatarImage(who)
            R.id.btnQuoteName -> quoteName(who)
            R.id.btnHideBoost -> userSetShowBoosts(accessInfo, who, false)
            R.id.btnShowBoost -> userSetShowBoosts(accessInfo, who, true)
            R.id.btnHideFavourite -> clickHideFavourite(accessInfo, who)
            R.id.btnShowFavourite -> clickShowFavourite(accessInfo, who)
            R.id.btnListMemberAddRemove -> DlgListMember(activity, who, accessInfo).show()
            R.id.btnInstanceInformation -> serverInformation(pos, getUserApiHost())
            R.id.btnProfileDirectory -> serverProfileDirectoryFromInstanceInformation(column, getUserApiHost())
            R.id.btnEndorse -> userEndorsement(accessInfo, who, !relation.endorsed)
            R.id.btnCopyAccountId -> who.id.toString().copyToClipboard(activity)
            R.id.btnOpenAccountInAdminWebUi -> openBrowser("https://${accessInfo.apiHost.ascii}/admin/accounts/${who.id}")
            R.id.btnOpenInstanceInAdminWebUi -> openBrowser("https://${accessInfo.apiHost.ascii}/admin/instances/${who.apDomain.ascii}")
            R.id.btnNotificationFrom -> clickNotificationFrom(pos, accessInfo, who)
            R.id.btnPostNotification -> clickStatusNotification(accessInfo, who, relation)
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
            R.id.btnDelete -> clickStatusDelete(accessInfo, status)
            R.id.btnRedraft -> statusRedraft(accessInfo, status)
            R.id.btnMuteApp -> appMute(status.application)
            R.id.btnBoostedBy -> clickBoostBy(pos, accessInfo, status, ColumnType.BOOSTED_BY)
            R.id.btnFavouritedBy -> clickBoostBy(pos, accessInfo, status, ColumnType.FAVOURITED_BY)
            R.id.btnTranslate -> CustomShare.invoke(activity, accessInfo, status, CustomShareTarget.Translate)
            R.id.btnQuoteUrlStatus -> openPost(status.url?.notEmpty())
            R.id.btnShareUrlStatus -> shareText(status.url?.notEmpty())
            R.id.btnConversationMute -> conversationMute(accessInfo, status)
            R.id.btnProfilePin -> statusPin(accessInfo, status, true)
            R.id.btnProfileUnpin -> statusPin(accessInfo, status, false)
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
        when (v.id) {
            R.id.btnFollow -> {
                dialog.dismissSafe()
                activity.followFromAnotherAccount(
                    activity.nextPosition(column),
                    accessInfo,
                    who
                )
            }

            R.id.btnProfile -> {
                dialog.dismissSafe()
                activity.userProfileFromAnotherAccount(
                    activity.nextPosition(column),
                    accessInfo,
                    who
                )
            }

            R.id.btnSendMessage -> {
                dialog.dismissSafe()
                activity.mentionFromAnotherAccount(accessInfo, who)
            }

            R.id.btnMute -> activity.userMuteFromAnotherAccount(who, accessInfo)
            R.id.btnBlock -> activity.userBlockFromAnotherAccount(who, accessInfo)
            R.id.btnQuoteAnotherAccount -> activity.quoteFromAnotherAccount(accessInfo, status)
            R.id.btnQuoteTootBT -> activity.quoteFromAnotherAccount(accessInfo, status?.reblogParent)

            else -> return false
        }
        return true
    }
}
