package jp.juggler.subwaytooter.itemviewholder

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.action.*
import jp.juggler.subwaytooter.actmain.nextPosition
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.column.Column
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.dialog.DlgListMember
import jp.juggler.subwaytooter.dialog.DlgQRCode
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.pref.PrefI
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
    private fun <T : View> fv(@IdRes id: Int): T = viewRoot.findViewById(id)

    private val btnGroupStatusCrossAccount: Button = fv(R.id.btnGroupStatusCrossAccount)
    private val llGroupStatusCrossAccount: View = fv(R.id.llGroupStatusCrossAccount)
    private val btnGroupStatusAround: Button = fv(R.id.btnGroupStatusAround)
    private val llGroupStatusAround: View = fv(R.id.llGroupStatusAround)
    private val btnGroupStatusByMe: Button = fv(R.id.btnGroupStatusByMe)
    private val llGroupStatusByMe: View = fv(R.id.llGroupStatusByMe)
    private val btnGroupStatusExtra: Button = fv(R.id.btnGroupStatusExtra)
    private val llGroupStatusExtra: View = fv(R.id.llGroupStatusExtra)
    private val btnGroupUserCrossAccount: Button = fv(R.id.btnGroupUserCrossAccount)
    private val llGroupUserCrossAccount: View = fv(R.id.llGroupUserCrossAccount)
    private val btnGroupUserExtra: Button = fv(R.id.btnGroupUserExtra)
    private val llGroupUserExtra: View = fv(R.id.llGroupUserExtra)

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

        val btnAccountWebPage: View = fv(R.id.btnAccountWebPage)
        val btnAroundAccountTL: View = fv(R.id.btnAroundAccountTL)
        val btnAroundFTL: View = fv(R.id.btnAroundFTL)
        val btnAroundLTL: View = fv(R.id.btnAroundLTL)
        val btnAvatarImage: View = fv(R.id.btnAvatarImage)
        val btnBlock: ImageView = fv(R.id.btnBlock)
        val btnBookmarkAnotherAccount: View = fv(R.id.btnBookmarkAnotherAccount)
        val btnBoostAnotherAccount: View = fv(R.id.btnBoostAnotherAccount)
        val btnBoostedBy: View = fv(R.id.btnBoostedBy)
        val btnBoostWithVisibility: Button = fv(R.id.btnBoostWithVisibility)
        val btnConversationAnotherAccount: View = fv(R.id.btnConversationAnotherAccount)
        val btnConversationMute: Button = fv(R.id.btnConversationMute)
        val btnCopyAccountId: Button = fv(R.id.btnCopyAccountId)
        val btnDelete: View = fv(R.id.btnDelete)
        val btnDeleteSuggestion: View = fv(R.id.btnDeleteSuggestion)
        val btnDomainBlock: Button = fv(R.id.btnDomainBlock)
        val btnDomainTimeline: View = fv(R.id.btnDomainTimeline)
        val btnEndorse: Button = fv(R.id.btnEndorse)
        val btnFavouriteAnotherAccount: View = fv(R.id.btnFavouriteAnotherAccount)
        val btnFavouritedBy: View = fv(R.id.btnFavouritedBy)
        val btnFollow: ImageButton = fv(R.id.btnFollow)
        val btnFollowFromAnotherAccount: View = fv(R.id.btnFollowFromAnotherAccount)
        val btnFollowRequestNG: View = fv(R.id.btnFollowRequestNG)
        val btnFollowRequestOK: View = fv(R.id.btnFollowRequestOK)
        val btnHideBoost: View = fv(R.id.btnHideBoost)
        val btnHideFavourite: View = fv(R.id.btnHideFavourite)
        val btnInstanceInformation: Button = fv(R.id.btnInstanceInformation)
        val btnListMemberAddRemove: View = fv(R.id.btnListMemberAddRemove)
        val btnMute: ImageView = fv(R.id.btnMute)
        val btnMuteApp: Button = fv(R.id.btnMuteApp)
        val btnNotificationDelete: View = fv(R.id.btnNotificationDelete)
        val btnNotificationFrom: Button = fv(R.id.btnNotificationFrom)
        val btnOpenAccountInAdminWebUi: Button = fv(R.id.btnOpenAccountInAdminWebUi)
        val btnOpenInstanceInAdminWebUi: Button = fv(R.id.btnOpenInstanceInAdminWebUi)
        val btnOpenProfileFromAnotherAccount: View = fv(R.id.btnOpenProfileFromAnotherAccount)
        val btnOpenTimeline: Button = fv(R.id.btnOpenTimeline)
        val btnProfile: View = fv(R.id.btnProfile)
        val btnProfileDirectory: Button = fv(R.id.btnProfileDirectory)
        val btnProfilePin: View = fv(R.id.btnProfilePin)
        val btnProfileUnpin: View = fv(R.id.btnProfileUnpin)
        val btnQuoteAnotherAccount: View = fv(R.id.btnQuoteAnotherAccount)
        val btnQuoteTootBT: View = fv(R.id.btnQuoteTootBT)
        val btnReactionAnotherAccount: View = fv(R.id.btnReactionAnotherAccount)
        val btnRedraft: View = fv(R.id.btnRedraft)
        val btnReplyAnotherAccount: View = fv(R.id.btnReplyAnotherAccount)
        val btnReportStatus: View = fv(R.id.btnReportStatus)
        val btnReportUser: View = fv(R.id.btnReportUser)
        val btnSendMessage: View = fv(R.id.btnSendMessage)
        val btnSendMessageFromAnotherAccount: View = fv(R.id.btnSendMessageFromAnotherAccount)
        val btnShowBoost: View = fv(R.id.btnShowBoost)
        val btnShowFavourite: View = fv(R.id.btnShowFavourite)
        val btnStatusWebPage: View = fv(R.id.btnStatusWebPage)
        val btnText: View = fv(R.id.btnText)
        val ivFollowedBy: ImageView = fv(R.id.ivFollowedBy)
        val llAccountActionBar: View = fv(R.id.llAccountActionBar)
        val llLinks: LinearLayout = fv(R.id.llLinks)
        val llNotification: View = fv(R.id.llNotification)
        val llStatus: View = fv(R.id.llStatus)
        val btnStatusNotification: Button = fv(R.id.btnStatusNotification)

        arrayOf(
            btnAccountWebPage,
            btnAroundAccountTL,
            btnAroundFTL,
            btnAroundLTL,
            btnAvatarImage,
            btnBlock,
            btnBookmarkAnotherAccount,
            btnBoostAnotherAccount,
            btnBoostedBy,
            btnBoostWithVisibility,
            btnConversationAnotherAccount,
            btnConversationMute,
            btnCopyAccountId,
            btnDelete,
            btnDeleteSuggestion,
            btnDomainBlock,
            btnDomainTimeline,
            btnEndorse,
            btnFavouriteAnotherAccount,
            btnFavouritedBy,
            btnFollow,
            btnFollowFromAnotherAccount,
            btnFollowRequestNG,
            btnFollowRequestOK,
            btnHideBoost,
            btnHideFavourite,
            btnInstanceInformation,
            btnListMemberAddRemove,
            btnMute,
            btnMuteApp,
            btnNotificationDelete,
            btnNotificationFrom,
            btnOpenAccountInAdminWebUi,
            btnOpenInstanceInAdminWebUi,
            btnOpenProfileFromAnotherAccount,
            btnOpenTimeline,
            btnProfile,
            btnProfileDirectory,
            btnProfilePin,
            btnProfileUnpin,
            btnQuoteAnotherAccount,
            btnQuoteTootBT,
            btnReactionAnotherAccount,
            btnRedraft,
            btnReplyAnotherAccount,
            btnReportStatus,
            btnReportUser,
            btnSendMessage,
            btnSendMessageFromAnotherAccount,
            btnShowBoost,
            btnShowFavourite,
            btnStatusNotification,
            btnStatusWebPage,
            btnText,

            viewRoot.findViewById(R.id.btnQuoteUrlStatus),
            viewRoot.findViewById(R.id.btnTranslate),
            viewRoot.findViewById(R.id.btnQuoteUrlAccount),
            viewRoot.findViewById(R.id.btnShareUrlStatus),
            viewRoot.findViewById(R.id.btnShareUrlAccount),
            viewRoot.findViewById(R.id.btnQuoteName)

        ).forEach { it.setOnClickListener(this) }

        arrayOf(
            btnBlock,
            btnFollow,
            btnMute,
            btnProfile,
            btnQuoteAnotherAccount,
            btnQuoteTootBT,
            btnSendMessage,
        ).forEach { it.setOnLongClickListener(this) }

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

            btnGroupStatusByMe.vg(statusByMe)

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

            btnStatusNotification.vg(!accessInfo.isPseudo && accessInfo.isMastodon && relation.following)
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

        updateGroup(btnGroupStatusCrossAccount, llGroupStatusCrossAccount)
        updateGroup(btnGroupUserCrossAccount, llGroupUserCrossAccount)
        updateGroup(btnGroupStatusAround, llGroupStatusAround)
        updateGroup(btnGroupStatusByMe, llGroupStatusByMe)
        updateGroup(btnGroupStatusExtra, llGroupStatusExtra)
        updateGroup(btnGroupUserExtra, llGroupUserExtra)
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

    private fun onClickUpdateGroup(v: View): Boolean = when (v.id) {
        R.id.btnGroupStatusCrossAccount -> updateGroup(
            btnGroupStatusCrossAccount,
            llGroupStatusCrossAccount,
            toggle = true
        )

        R.id.btnGroupUserCrossAccount -> updateGroup(
            btnGroupUserCrossAccount,
            llGroupUserCrossAccount,
            toggle = true
        )
        R.id.btnGroupStatusAround -> updateGroup(
            btnGroupStatusAround,
            llGroupStatusAround,
            toggle = true
        )
        R.id.btnGroupStatusByMe -> updateGroup(
            btnGroupStatusByMe,
            llGroupStatusByMe,
            toggle = true
        )
        R.id.btnGroupStatusExtra -> updateGroup(
            btnGroupStatusExtra,
            llGroupStatusExtra,
            toggle = true
        )
        R.id.btnGroupUserExtra -> updateGroup(
            btnGroupUserExtra,
            llGroupUserExtra,
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

    @Suppress("ComplexMethod")
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
            R.id.btnDelete -> clickStatusDelete(accessInfo, status)
            R.id.btnRedraft -> statusRedraft(accessInfo, status)
            R.id.btnMuteApp -> appMute(status.application)
            R.id.btnBoostedBy -> clickBoostBy(pos, accessInfo, status, ColumnType.BOOSTED_BY)
            R.id.btnFavouritedBy -> clickBoostBy(pos, accessInfo, status, ColumnType.FAVOURITED_BY)
            R.id.btnTranslate -> CustomShare.invokeStatusText(CustomShareTarget.Translate, activity, accessInfo, status)
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
