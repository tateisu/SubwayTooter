package jp.juggler.subwaytooter

import android.content.Context
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.JustifyContent
import jp.juggler.subwaytooter.action.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.util.CustomShare
import jp.juggler.subwaytooter.util.CustomShareTarget
import jp.juggler.subwaytooter.util.emptyCallback
import jp.juggler.subwaytooter.util.startMargin
import jp.juggler.subwaytooter.view.CountImageButton
import jp.juggler.util.*
import org.jetbrains.anko.*
import org.jetbrains.anko.custom.customView

class StatusButtons(
    private val activity: ActMain,
    private val column: Column,
    private val bSimpleList: Boolean,

    private val holder: StatusButtonsViewHolder,
    private val itemViewHolder: ItemViewHolder,

    ) : View.OnClickListener, View.OnLongClickListener {

    companion object {

        val log = LogCategory("StatusButtons")
    }

    private val accessInfo: SavedAccount
    private var relation: UserRelation? = null
    private var status: TootStatus? = null
    private var notification: TootNotification? = null

    var closeWindow: PopupWindow? = null

    private val btnConversation = holder.btnConversation
    private val btnReply = holder.btnReply
    private val btnBoost = holder.btnBoost
    private val btnFavourite = holder.btnFavourite
    private val btnBookmark = holder.btnBookmark
    private val btnQuote = holder.btnQuote
    private val btnReaction = holder.btnReaction
    private val llFollow2 = holder.llFollow2
    private val btnFollow2 = holder.btnFollow2
    private val ivFollowedBy2 = holder.ivFollowedBy2
    private val btnTranslate = holder.btnTranslate
    private val btnCustomShare1 = holder.btnCustomShare1
    private val btnCustomShare2 = holder.btnCustomShare2
    private val btnCustomShare3 = holder.btnCustomShare3
    private val btnMore = holder.btnMore

    private val colorNormal = column.getContentColor()

    private val colorAccent: Int
        get() = activity.attrColor(R.attr.colorImageButtonAccent)

    init {
        this.accessInfo = column.accessInfo

        arrayOf(
            btnBoost,
            btnFavourite,
            btnBookmark,
            btnQuote,
            btnReaction,
            btnFollow2,
            btnConversation,
            btnReply,
            btnTranslate,
            btnCustomShare1,
            btnCustomShare2,
            btnCustomShare3,
        ).forEach {
            it.setOnClickListener(this)
            it.setOnLongClickListener(this)
        }

        // moreボタンだけ長押しがない
        btnMore.setOnClickListener(this)
    }

    fun hide() {
        holder.viewRoot.visibility = View.GONE
    }

    fun bind(status: TootStatus, notification: TootNotification?) {
        holder.viewRoot.visibility = View.VISIBLE
        this.status = status
        this.notification = notification

        val pref = activity.pref

        setIconDrawableId(
            activity,
            btnConversation,
            R.drawable.ic_forum,
            color = colorNormal,
            alphaMultiplier = Styler.boostAlpha
        )

        setIconDrawableId(
            activity,
            btnMore,
            R.drawable.ic_more,
            color = colorNormal,
            alphaMultiplier = Styler.boostAlpha
        )

        setButton(
            btnReply,
            true,
            colorNormal,
            R.drawable.ic_reply,
            when (val repliesCount = status.replies_count) {
                null -> ""
                else -> when (Pref.ipRepliesCount(activity.pref)) {
                    Pref.RC_SIMPLE -> when {
                        repliesCount >= 2L -> "1+"
                        repliesCount == 1L -> "1"
                        else -> ""
                    }
                    Pref.RC_ACTUAL -> repliesCount.toString()
                    else -> ""
                }
            },
            activity.getString(R.string.reply)
        )

        // ブーストボタン
        when {
            // マストドンではDirectはブーストできない (Misskeyはできる)
            (!accessInfo.isMisskey && status.visibility.order <= TootVisibility.DirectSpecified.order) ->
                setButton(
                    btnBoost,
                    false,
                    colorAccent,
                    R.drawable.ic_mail,
                    "",
                    activity.getString(R.string.boost)
                )

            activity.appState.isBusyBoost(accessInfo, status) ->
                setButton(
                    btnBoost,
                    false,
                    colorNormal,
                    R.drawable.ic_refresh,
                    "?",
                    activity.getString(R.string.boost)
                )

            else -> setButton(
                btnBoost,
                true,
                when {
                    status.reblogged -> Pref.ipButtonBoostedColor(pref).notZero() ?: colorAccent
                    else -> colorNormal
                },
                R.drawable.ic_repeat,
                when (val boostsCount = status.reblogs_count) {
                    null -> ""
                    else -> when (Pref.ipBoostsCount(activity.pref)) {
                        Pref.RC_SIMPLE -> when {
                            boostsCount >= 2L -> "1+"
                            boostsCount == 1L -> "1"
                            else -> ""
                        }
                        Pref.RC_ACTUAL -> boostsCount.toString()
                        else -> ""
                    }
                },
                activity.getString(R.string.boost)
            )
        }

        val ti = TootInstance.getCached(accessInfo)
        btnQuote.vg(ti?.feature_quote == true)?.let {
            setButton(
                it,
                true,
                colorNormal,
                R.drawable.ic_quote,
                activity.getString(R.string.quote)
            )
        }

        btnReaction.vg(TootReaction.canReaction(accessInfo, ti))?.let {
            val canMultipleReaction = InstanceCapability.canMultipleReaction(accessInfo, ti)
            val hasMyReaction = status.reactionSet?.hasMyReaction() == true
            val bRemoveButton = hasMyReaction && !canMultipleReaction
            setButton(
                it,
                true,
                colorNormal,
                if (bRemoveButton) R.drawable.ic_remove else R.drawable.ic_add,
                activity.getString(
                    if (bRemoveButton) R.string.reaction_remove else R.string.reaction_add
                )
            )
        }

        // お気に入りボタン
        val favIconDrawable = when {
            accessInfo.isNicoru(status.account) -> R.drawable.ic_nicoru
            else -> R.drawable.ic_star
        }
        when {
            activity.appState.isBusyFav(accessInfo, status) -> setButton(
                btnFavourite,
                false,
                colorNormal,
                R.drawable.ic_refresh,
                "?",
                activity.getString(R.string.favourite)
            )

            else -> setButton(
                btnFavourite,
                true,
                when {
                    status.favourited -> Pref.ipButtonFavoritedColor(pref).notZero() ?: colorAccent
                    else -> colorNormal
                },
                favIconDrawable,
                when (val favouritesCount = status.favourites_count) {
                    null -> ""
                    else -> when (Pref.ipFavouritesCount(activity.pref)) {
                        Pref.RC_SIMPLE -> when {
                            favouritesCount >= 2L -> "1+"
                            favouritesCount == 1L -> "1"
                            else -> ""
                        }
                        Pref.RC_ACTUAL -> favouritesCount.toString()
                        else -> ""
                    }
                },
                activity.getString(R.string.favourite)
            )
        }

        // ブックマークボタン
        when {
            !Pref.bpShowBookmarkButton(activity.pref) -> btnBookmark.vg(false)

            activity.appState.isBusyBookmark(accessInfo, status) -> setButton(
                btnBookmark,
                false,
                colorNormal,
                R.drawable.ic_refresh,
                activity.getString(R.string.bookmark)
            )

            else -> setButton(
                btnBookmark,
                true,
                when {
                    status.bookmarked -> Pref.ipButtonBookmarkedColor(pref).notZero() ?: colorAccent
                    else -> colorNormal
                },
                R.drawable.ic_bookmark,
                activity.getString(R.string.bookmark)
            )
        }

        val account = status.account

        this.relation = if (!Pref.bpShowFollowButtonInButtonBar(activity.pref)) {
            llFollow2.visibility = View.GONE
            null
        } else {
            llFollow2.visibility = View.VISIBLE
            val relation = UserRelation.load(accessInfo.db_id, account.id)
            Styler.setFollowIcon(
                activity,
                btnFollow2,
                ivFollowedBy2,
                relation,
                account,
                colorNormal,
                alphaMultiplier = Styler.boostAlpha
            )
            relation
        }

        var optionalButtonFirst: View? = null
        var optionalButtonCount = 0

        fun ImageButton.showCustomShare(target: CustomShareTarget) {
            val (label, icon) = CustomShare.getCache(target)
                ?: error("showCustomShare: invalid target")

            vg(label != null || icon != null)?.apply {
                isEnabled = true
                contentDescription = label ?: "?"
                setImageDrawable(
                    icon ?: createColoredDrawable(
                        this@StatusButtons.activity,
                        R.drawable.ic_question,
                        colorNormal,
                        Styler.boostAlpha
                    )
                )
                ++optionalButtonCount
                if (optionalButtonFirst == null) {
                    optionalButtonFirst = this
                }
            }
        }

        btnTranslate.vg(Pref.bpShowTranslateButton(activity.pref))
            ?.showCustomShare(CustomShareTarget.Translate)

        btnCustomShare1.showCustomShare(CustomShareTarget.CustomShare1)
        btnCustomShare2.showCustomShare(CustomShareTarget.CustomShare2)
        btnCustomShare3.showCustomShare(CustomShareTarget.CustomShare3)

        val lpConversation = btnConversation.layoutParams as? FlexboxLayout.LayoutParams
        val updateAdditionalButton: (btn: ImageButton) -> Unit
        when (Pref.ipAdditionalButtonsPosition(activity.pref)) {
            Pref.ABP_TOP -> {
                // 1行目に追加ボタンが並ぶ
                updateAdditionalButton = { btn ->
                    (btn.layoutParams as? FlexboxLayout.LayoutParams)?.let { lp ->
                        lp.isWrapBefore = false
                        lp.startMargin = when (btn) {
                            optionalButtonFirst -> 0
                            else -> holder.marginBetween
                        }
                    }
                }
                // 2行目は通常ボタンが並ぶ
                // 2行目最初のボタンのstartMarginは追加ボタンの有無で変化する
                lpConversation?.startMargin = 0
                lpConversation?.isWrapBefore = (optionalButtonCount != 0)
            }

            Pref.ABP_START -> {
                // 始端に追加ボタンが並ぶ
                updateAdditionalButton = { btn ->
                    (btn.layoutParams as? FlexboxLayout.LayoutParams)?.let { lp ->
                        lp.isWrapBefore = false
                        lp.startMargin = when (btn) {
                            optionalButtonFirst -> 0
                            else -> holder.marginBetween
                        }
                    }
                }
                // 続いて通常ボタンが並ぶ
                lpConversation?.startMargin = holder.marginBetween
                lpConversation?.isWrapBefore = false
            }

            Pref.ABP_END -> {
                // 始端に通常ボタンが並ぶ
                lpConversation?.startMargin = 0
                lpConversation?.isWrapBefore = false
                // 続いて追加ボタンが並ぶ
                updateAdditionalButton = { btn ->
                    (btn.layoutParams as? FlexboxLayout.LayoutParams)?.let { lp ->
                        lp.isWrapBefore = false
                        lp.startMargin = holder.marginBetween
                    }
                }
            }

            else /* Pref.ABP_BOTTOM */ -> {
                // 1行目は通常ボタンが並ぶ
                lpConversation?.startMargin = 0
                lpConversation?.isWrapBefore = false
                // 2行目は追加ボタンが並ぶ
                updateAdditionalButton = { btn ->
                    (btn.layoutParams as? FlexboxLayout.LayoutParams)?.let { lp ->
                        lp.isWrapBefore = btn == optionalButtonFirst
                        lp.startMargin = when (btn) {
                            optionalButtonFirst -> 0
                            else -> holder.marginBetween
                        }
                    }
                }
            }
        }

        updateAdditionalButton(btnTranslate)
        updateAdditionalButton(btnCustomShare1)
        updateAdditionalButton(btnCustomShare2)
        updateAdditionalButton(btnCustomShare3)
    }

    private fun setButton(
        b: CountImageButton,
        enabled: Boolean,
        color: Int,
        drawableId: Int,
        count: String,
        contentDescription: String,
    ) {
        val alpha = Styler.boostAlpha
        val d = createColoredDrawable(
            activity,
            drawableId,
            color,
            alpha
        )
        b.setImageDrawable(d)
        b.setPaddingAndText(holder.paddingH, holder.paddingV, count, 14f, holder.compoundPaddingDp)
        b.setTextColor(color.applyAlphaMultiplier(alpha))
        b.contentDescription = contentDescription + count
        b.isEnabled = enabled
    }

    private fun setButton(
        b: ImageButton,
        enabled: Boolean,
        color: Int,
        drawableId: Int,
        contentDescription: String,
    ) {
        val alpha = Styler.boostAlpha
        val d = createColoredDrawable(
            activity,
            drawableId,
            color,
            alpha
        )
        b.setImageDrawable(d)
        b.contentDescription = contentDescription
        b.isEnabled = enabled
    }

    override fun onClick(v: View) {

        closeWindow?.dismiss()
        closeWindow = null

        val status = this.status ?: return

        when (v) {

            btnConversation -> {

                val cs = status.conversationSummary
                if (activity.conversationUnreadClear(accessInfo, cs)) {
                    // 表示の更新
                    itemViewHolder.listAdapter.notifyChange(
                        reason = "ConversationSummary reset unread",
                        reset = true
                    )
                }

                activity.conversation(
                    activity.nextPosition(column),
                    accessInfo,
                    status
                )
            }

            btnReply -> if (!accessInfo.isPseudo) {
                activity.reply(accessInfo, status)
            } else {
                activity.replyFromAnotherAccount(accessInfo, status)
            }

            btnQuote -> if (!accessInfo.isPseudo) {
                activity.reply(accessInfo, status, quote = true)
            } else {
                activity.quoteFromAnotherAccount(accessInfo, status)
            }

            btnBoost -> {
                if (accessInfo.isPseudo) {
                    activity.boostFromAnotherAccount(accessInfo, status)
                } else {

                    // トグル動作
                    val bSet = !status.reblogged

                    activity.boost(

                        accessInfo,
                        status,
                        accessInfo.getFullAcct(status.account),
                        CrossAccountMode.SameAccount,
                        bSet = bSet,
                        callback = when {
                            !bSimpleList -> emptyCallback
                            // 簡略表示なら結果をトースト表示
                            bSet -> activity.boostCompleteCallback
                            else -> activity.unboostCompleteCallback
                        },
                    )
                }
            }

            btnFavourite -> {
                if (accessInfo.isPseudo) {
                    activity.favouriteFromAnotherAccount(accessInfo, status)
                } else {

                    // トグル動作
                    val bSet = !status.favourited

                    activity.favourite(
                        accessInfo,
                        status,
                        CrossAccountMode.SameAccount,
                        bSet = bSet,
                        callback = when {
                            !bSimpleList -> emptyCallback
                            // 簡略表示なら結果をトースト表示
                            bSet -> activity.favouriteCompleteCallback
                            else -> activity.unfavouriteCompleteCallback
                        },
                    )
                }
            }

            btnBookmark -> {
                if (accessInfo.isPseudo) {
                    activity.bookmarkFromAnotherAccount(accessInfo, status)
                } else {

                    // トグル動作
                    val bSet = !status.bookmarked

                    activity.bookmark(
                        accessInfo,
                        status,
                        CrossAccountMode.SameAccount,
                        bSet = bSet,
                        callback = when {
                            !bSimpleList -> emptyCallback
                            // 簡略表示なら結果をトースト表示
                            bSet -> activity.bookmarkCompleteCallback
                            else -> activity.unbookmarkCompleteCallback
                        },
                    )
                }
            }

            btnReaction -> {
                val canMultipleReaction = InstanceCapability.canMultipleReaction(accessInfo)
                val hasMyReaction = status.reactionSet?.hasMyReaction() == true
                val bRemoveButton = hasMyReaction && !canMultipleReaction
                when {
                    !TootReaction.canReaction(accessInfo) ->
                        activity.reactionFromAnotherAccount(
                            accessInfo,
                            status
                        )
                    bRemoveButton ->
                        activity.reactionRemove(column, status)
                    else ->
                        activity.reactionAdd(column, status)
                }
            }

            btnFollow2 -> {
                val accountRef = status.accountRef
                val account = accountRef.get()
                val relation = this.relation ?: return

                when {
                    accessInfo.isPseudo -> {
                        // 別アカでフォロー
                        activity.followFromAnotherAccount(

                            activity.nextPosition(column),
                            accessInfo,
                            account
                        )
                    }

                    relation.blocking || relation.muting -> {
                        // 何もしない
                    }

                    accessInfo.isMisskey && relation.getRequested(account) && !relation.getFollowing(
                        account
                    ) ->
                        activity.followRequestDelete(

                            activity.nextPosition(column),
                            accessInfo,
                            accountRef,
                            callback = activity.cancelFollowRequestCompleteCallback
                        )

                    relation.getFollowing(account) || relation.getRequested(account) -> {
                        // フォロー解除
                        activity.follow(

                            activity.nextPosition(column),
                            accessInfo,
                            accountRef,
                            bFollow = false,
                            callback = activity.unfollowCompleteCallback
                        )
                    }

                    else -> {
                        // フォロー
                        activity.follow(
                            activity.nextPosition(column),
                            accessInfo,
                            accountRef,
                            bFollow = true,
                            callback = activity.followCompleteCallback
                        )
                    }
                }
            }

            btnTranslate -> CustomShare.invoke(
                activity,
                accessInfo,
                status,
                CustomShareTarget.Translate
            )

            btnCustomShare1 -> CustomShare.invoke(
                activity,
                accessInfo,
                status,
                CustomShareTarget.CustomShare1
            )

            btnCustomShare2 -> CustomShare.invoke(
                activity,
                accessInfo,
                status,
                CustomShareTarget.CustomShare2
            )

            btnCustomShare3 -> CustomShare.invoke(
                activity,
                accessInfo,
                status,
                CustomShareTarget.CustomShare3
            )

            btnMore -> DlgContextMenu(
                activity,
                column,
                status.accountRef,
                status,
                notification,
                itemViewHolder.tvContent
            ).show()
        }
    }

    override fun onLongClick(v: View): Boolean {

        closeWindow?.dismiss()
        closeWindow = null

        val status = this.status ?: return true

        when (v) {
            btnBoost -> activity.boostFromAnotherAccount(accessInfo, status)
            btnFavourite -> activity.favouriteFromAnotherAccount(accessInfo, status)
            btnBookmark -> activity.bookmarkFromAnotherAccount(accessInfo, status)

            btnReply -> activity.replyFromAnotherAccount(accessInfo, status)
            btnQuote -> activity.quoteFromAnotherAccount(accessInfo, status)

            btnReaction -> activity.reactionFromAnotherAccount(accessInfo, status)

            btnConversation -> activity.conversationOtherInstance(activity.nextPosition(column), status)

            btnFollow2 ->
                activity.followFromAnotherAccount(activity.nextPosition(column), accessInfo, status.account)

            btnTranslate -> shareUrl(status, CustomShareTarget.Translate)
            btnCustomShare1 -> shareUrl(status, CustomShareTarget.CustomShare1)
            btnCustomShare2 -> shareUrl(status, CustomShareTarget.CustomShare2)
            btnCustomShare3 -> shareUrl(status, CustomShareTarget.CustomShare3)
        }
        return true
    }

    private fun shareUrl(
        status: TootStatus,
        target: CustomShareTarget,
    ) {
        val url = status.url ?: status.uri

        CustomShare.invoke(activity, url, target)
    }
}

open class AnkoFlexboxLayout(ctx: Context) : FlexboxLayout(ctx) {

    inline fun <T : View> T.lparams(
        width: Int = android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        init: LayoutParams.() -> Unit = {},
    ): T {
        val layoutParams = LayoutParams(width, height)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }
}

class StatusButtonsViewHolder(
    activity: ActMain,
    lpWidth: Int,
    topMarginDp: Float,
    @JustifyContent justifyContent: Int = JustifyContent.CENTER,
) {

    private val buttonHeight = ActMain.boostButtonSize
    internal val marginBetween = (ActMain.boostButtonSize.toFloat() * 0.05f + 0.5f).toInt()

    val paddingH = (buttonHeight.toFloat() * 0.1f + 0.5f).toInt()
    val paddingV = (buttonHeight.toFloat() * 0.1f + 0.5f).toInt()
    val compoundPaddingDp =
        0f //  ActMain.boostButtonSize.toFloat() * -0f / activity.resources.displayMetrics.density

    val viewRoot: FlexboxLayout

    lateinit var btnConversation: ImageButton
    lateinit var btnReply: CountImageButton
    lateinit var btnBoost: CountImageButton
    lateinit var btnFavourite: CountImageButton
    lateinit var btnBookmark: ImageButton
    lateinit var btnQuote: ImageButton
    lateinit var btnReaction: ImageButton
    lateinit var llFollow2: View
    lateinit var btnFollow2: ImageButton
    lateinit var ivFollowedBy2: ImageView
    lateinit var btnTranslate: ImageButton
    lateinit var btnCustomShare1: ImageButton
    lateinit var btnCustomShare2: ImageButton
    lateinit var btnCustomShare3: ImageButton
    lateinit var btnMore: ImageButton

    init {
        viewRoot = with(activity.UI {}) {

            customView<AnkoFlexboxLayout> {
                // トップレベルのViewGroupのlparamsはイニシャライザ内部に置くしかないみたい
                layoutParams = LinearLayout.LayoutParams(lpWidth, wrapContent).apply {
                    topMargin = dip(topMarginDp)
                }
                flexWrap = FlexWrap.WRAP
                this.justifyContent = justifyContent

                fun normalButtons() {

                    btnConversation = imageButton {
                        background = ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                        contentDescription = context.getString(R.string.conversation_view)

                        setPadding(paddingH, paddingV, paddingH, paddingV)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        imageResource = R.drawable.ic_forum
                    }.lparams(buttonHeight, buttonHeight)

                    btnReply = customView<CountImageButton> {
                        background = ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                        setPadding(paddingH, paddingV, paddingH, paddingV)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        minimumWidth = buttonHeight
                    }.lparams(wrapContent, buttonHeight) {
                        startMargin = marginBetween
                    }

                    btnBoost = customView<CountImageButton> {
                        background = ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                        setPadding(paddingH, paddingV, paddingH, paddingV)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        minimumWidth = buttonHeight
                    }.lparams(wrapContent, buttonHeight) {
                        startMargin = marginBetween
                    }

                    btnFavourite = customView<CountImageButton> {
                        background = ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                        setPadding(paddingH, paddingV, paddingH, paddingV)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        minimumWidth = buttonHeight
                    }.lparams(wrapContent, buttonHeight) {
                        startMargin = marginBetween
                    }

                    btnBookmark = imageButton {
                        background = ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                        setPadding(paddingH, paddingV, paddingH, paddingV)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        minimumWidth = buttonHeight
                    }.lparams(wrapContent, buttonHeight) {
                        startMargin = marginBetween
                    }

                    btnQuote = imageButton {
                        background = ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                        setPadding(paddingH, paddingV, paddingH, paddingV)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        minimumWidth = buttonHeight
                    }.lparams(wrapContent, buttonHeight) {
                        startMargin = marginBetween
                    }

                    btnReaction = imageButton {
                        background = ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                        setPadding(paddingH, paddingV, paddingH, paddingV)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        minimumWidth = buttonHeight
                    }.lparams(wrapContent, buttonHeight) {
                        startMargin = marginBetween
                    }

                    llFollow2 = frameLayout {
                        lparams(buttonHeight, buttonHeight) {
                            startMargin = marginBetween
                        }

                        btnFollow2 = imageButton {
                            background = ContextCompat.getDrawable(
                                context,
                                R.drawable.btn_bg_transparent_round6dp
                            )
                            setPadding(paddingH, paddingV, paddingH, paddingV)
                            scaleType = ImageView.ScaleType.FIT_CENTER

                            contentDescription = context.getString(R.string.follow)
                        }.lparams(matchParent, matchParent)

                        ivFollowedBy2 = imageView {

                            setPadding(paddingH, paddingV, paddingH, paddingV)
                            scaleType = ImageView.ScaleType.FIT_CENTER

                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        }.lparams(matchParent, matchParent)
                    }

                    btnMore = imageButton {
                        background = ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                        setPadding(paddingH, paddingV, paddingH, paddingV)
                        scaleType = ImageView.ScaleType.FIT_CENTER

                        contentDescription = context.getString(R.string.more)
                        imageResource = R.drawable.ic_more
                    }.lparams(buttonHeight, buttonHeight) {
                        startMargin = marginBetween
                    }
                }

                fun additionalButtons() {
                    btnTranslate = imageButton {
                        background = ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                        setPadding(paddingH, paddingV, paddingH, paddingV)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }.lparams(buttonHeight, buttonHeight) {
                        startMargin = marginBetween
                    }

                    btnCustomShare1 = imageButton {
                        background = ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                        setPadding(paddingH, paddingV, paddingH, paddingV)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }.lparams(buttonHeight, buttonHeight) {
                        startMargin = marginBetween
                    }

                    btnCustomShare2 = imageButton {
                        background = ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                        setPadding(paddingH, paddingV, paddingH, paddingV)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }.lparams(buttonHeight, buttonHeight) {
                        startMargin = marginBetween
                    }

                    btnCustomShare3 = imageButton {
                        background = ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                        setPadding(paddingH, paddingV, paddingH, paddingV)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }.lparams(buttonHeight, buttonHeight) {
                        startMargin = marginBetween
                    }
                }
                when (Pref.ipAdditionalButtonsPosition(activity.pref)) {
                    Pref.ABP_TOP, Pref.ABP_START -> {
                        additionalButtons()
                        normalButtons()
                    }

                    else -> {
                        normalButtons()
                        additionalButtons()
                    }
                }
            }
        }
    }
}
