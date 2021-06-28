package jp.juggler.subwaytooter.itemviewholder

import android.content.Context
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.JustifyContent
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.action.*
import jp.juggler.subwaytooter.actmain.nextPosition
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.column.Column
import jp.juggler.subwaytooter.column.getContentColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.util.CustomShare
import jp.juggler.subwaytooter.util.CustomShareTarget
import jp.juggler.subwaytooter.util.startMargin
import jp.juggler.util.*
import org.jetbrains.anko.*
import org.jetbrains.anko.custom.customView

enum class AdditionalButtonsPosition(
    val idx: Int, // spinner index start from 0
    @StringRes val captionId: Int,
) {
    Top(0, R.string.top),
    Bottom(1, R.string.bottom),
    Start(2, R.string.start),
    End(3, R.string.end),
    ;

    companion object {
        fun fromIndex(i: Int) = values().find { it.idx == i } ?: Top
    }
}

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

    var optionalButtonFirst: View? = null
    var optionalButtonCount = 0
    var ti: TootInstance? = null

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
        this.ti = TootInstance.getCached(accessInfo)

        bindMoreButton()
        bindConversationButton()
        bindQuoteButton()
        bindReplyButton(status)
        bindBoostButton(status)
        bindReactionButton(status)
        bindFavouriteButton(status)
        bindBookmarkButton(status)
        bindFollowButton(status)

        // 最後に呼び出す
        bindAdditionalButtons()
    }

    private fun bindConversationButton() {
        setIconDrawableId(
            activity,
            btnConversation,
            R.drawable.ic_forum,
            color = colorNormal,
            alphaMultiplier = Styler.boostAlpha
        )
    }

    private fun bindMoreButton() {
        setIconDrawableId(
            activity,
            btnMore,
            R.drawable.ic_more,
            color = colorNormal,
            alphaMultiplier = Styler.boostAlpha
        )
    }

    private fun bindReplyButton(status: TootStatus) {
        setButton(
            btnReply,
            true,
            colorNormal,
            R.drawable.ic_reply,
            when (val repliesCount = status.replies_count) {
                null -> ""
                else -> when (PrefI.ipRepliesCount(activity.pref)) {
                    PrefI.RC_SIMPLE -> when {
                        repliesCount >= 2L -> "1+"
                        repliesCount == 1L -> "1"
                        else -> ""
                    }
                    PrefI.RC_ACTUAL -> repliesCount.toString()
                    else -> ""
                }
            },
            activity.getString(R.string.reply)
        )
    }

    private fun bindBoostButton(status: TootStatus) {
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
                    status.reblogged ->
                        PrefI.ipButtonBoostedColor(activity.pref).notZero() ?: colorAccent
                    else ->
                        colorNormal
                },
                R.drawable.ic_repeat,
                when (val boostsCount = status.reblogs_count) {
                    null -> ""
                    else -> when (PrefI.ipBoostsCount(activity.pref)) {
                        PrefI.RC_SIMPLE -> when {
                            boostsCount >= 2L -> "1+"
                            boostsCount == 1L -> "1"
                            else -> ""
                        }
                        PrefI.RC_ACTUAL -> boostsCount.toString()
                        else -> ""
                    }
                },
                activity.getString(R.string.boost)
            )
        }
    }

    private fun bindQuoteButton() {
        btnQuote.vg(ti?.feature_quote == true)?.let {
            setButton(
                it,
                true,
                colorNormal,
                R.drawable.ic_quote,
                activity.getString(R.string.quote)
            )
        }
    }

    private fun bindReactionButton(status: TootStatus) {
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
    }

    private fun bindFavouriteButton(status: TootStatus) {
        when {
            activity.appState.isBusyFav(accessInfo, status) ->
                setButton(
                    btnFavourite,
                    false,
                    colorNormal,
                    R.drawable.ic_refresh,
                    "?",
                    activity.getString(R.string.favourite)
                )

            else ->
                setButton(
                    btnFavourite,
                    true,
                    when {
                        status.favourited ->
                            PrefI.ipButtonFavoritedColor(activity.pref).notZero() ?: colorAccent
                        else -> colorNormal
                    },
                    when {
                        accessInfo.isNicoru(status.account) -> R.drawable.ic_nicoru
                        else -> R.drawable.ic_star
                    },
                    when (val favouritesCount = status.favourites_count) {
                        null -> ""
                        else -> when (PrefI.ipFavouritesCount(activity.pref)) {
                            PrefI.RC_SIMPLE -> when {
                                favouritesCount >= 2L -> "1+"
                                favouritesCount == 1L -> "1"
                                else -> ""
                            }
                            PrefI.RC_ACTUAL -> favouritesCount.toString()
                            else -> ""
                        }
                    },
                    activity.getString(R.string.favourite)
                )
        }
    }

    private fun bindBookmarkButton(status: TootStatus) {
        btnBookmark.vg(PrefB.bpShowBookmarkButton(activity.pref))
            ?.let { btn ->
                when {
                    activity.appState.isBusyBookmark(accessInfo, status) ->
                        setButton(
                            btn,
                            false,
                            colorNormal,
                            R.drawable.ic_refresh,
                            activity.getString(R.string.bookmark)
                        )

                    else ->
                        setButton(
                            btn,
                            true,
                            when {
                                status.bookmarked ->
                                    PrefI.ipButtonBookmarkedColor(activity.pref).notZero() ?: colorAccent
                                else ->
                                    colorNormal
                            },
                            R.drawable.ic_bookmark,
                            activity.getString(R.string.bookmark)
                        )
                }
            }
    }

    private fun bindFollowButton(status: TootStatus) {
        val account = status.account
        this.relation = if (!PrefB.bpShowFollowButtonInButtonBar(activity.pref)) {
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
    }

    private fun bindAdditionalButtons() {
        optionalButtonFirst = null
        optionalButtonCount = 0

        btnTranslate.vg(PrefB.bpShowTranslateButton(activity.pref))
            ?.showCustomShare(CustomShareTarget.Translate)
        btnCustomShare1.showCustomShare(CustomShareTarget.CustomShare1)
        btnCustomShare2.showCustomShare(CustomShareTarget.CustomShare2)
        btnCustomShare3.showCustomShare(CustomShareTarget.CustomShare3)

        val updateAdditionalButton: (btn: ImageButton) -> Unit =
            getUpdateAdditionalButton(optionalButtonCount, optionalButtonFirst)

        updateAdditionalButton(btnTranslate)
        updateAdditionalButton(btnCustomShare1)
        updateAdditionalButton(btnCustomShare2)
        updateAdditionalButton(btnCustomShare3)
    }

    private fun ImageButton.showCustomShare(target: CustomShareTarget) {
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

    private fun getUpdateAdditionalButton(
        optionalButtonCount: Int,
        optionalButtonFirst: View?,
    ): (btn: ImageButton) -> Unit {
        val lpConversation = btnConversation.layoutParams as? FlexboxLayout.LayoutParams
        return when (AdditionalButtonsPosition.fromIndex(PrefI.ipAdditionalButtonsPosition(activity.pref))) {
            AdditionalButtonsPosition.Top -> {
                // 1行目に追加ボタンが並ぶ
                // 2行目は通常ボタンが並ぶ
                // 2行目最初のボタンのstartMarginは追加ボタンの有無で変化する
                lpConversation?.startMargin = 0
                lpConversation?.isWrapBefore = (optionalButtonCount != 0)
                // ラムダを返したいが、上の文との区切りでセミコロンか()が必要らしい
                ({ btn ->
                    (btn.layoutParams as? FlexboxLayout.LayoutParams)?.let { lp ->
                        lp.isWrapBefore = false
                        lp.startMargin = when (btn) {
                            optionalButtonFirst -> 0
                            else -> holder.marginBetween
                        }
                    }
                })
            }

            AdditionalButtonsPosition.Start -> {
                // 始端に追加ボタンが並ぶ
                // 続いて通常ボタンが並ぶ
                lpConversation?.startMargin = holder.marginBetween
                lpConversation?.isWrapBefore = false
                // ラムダを返したいが、上の文との区切りでセミコロンか()が必要らしい
                ({ btn ->
                    (btn.layoutParams as? FlexboxLayout.LayoutParams)?.let { lp ->
                        lp.isWrapBefore = false
                        lp.startMargin = when (btn) {
                            optionalButtonFirst -> 0
                            else -> holder.marginBetween
                        }
                    }
                })
            }

            AdditionalButtonsPosition.End -> {
                // 始端に通常ボタンが並ぶ
                // 続いて追加ボタンが並ぶ
                lpConversation?.startMargin = 0
                lpConversation?.isWrapBefore = false
                // ラムダを返したいが、上の文との区切りでセミコロンか()が必要らしい
                ({ btn ->
                    (btn.layoutParams as? FlexboxLayout.LayoutParams)?.let { lp ->
                        lp.isWrapBefore = false
                        lp.startMargin = holder.marginBetween
                    }
                })
            }

            AdditionalButtonsPosition.Bottom -> {
                // 1行目は通常ボタンが並ぶ
                // 2行目は追加ボタンが並ぶ
                lpConversation?.startMargin = 0
                lpConversation?.isWrapBefore = false
                // ラムダを返したいが、上の文との区切りでセミコロンか()が必要らしい
                ({ btn ->
                    (btn.layoutParams as? FlexboxLayout.LayoutParams)?.let { lp ->
                        lp.isWrapBefore = btn == optionalButtonFirst
                        lp.startMargin = when (btn) {
                            optionalButtonFirst -> 0
                            else -> holder.marginBetween
                        }
                    }
                })
            }
        }
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

        with(activity) {
            val pos = nextPosition(column)
            when (v) {
                btnMore -> clickMore(status)
                btnConversation -> clickConversation(pos, accessInfo, itemViewHolder.listAdapter, status = status)
                btnReply -> clickReply(accessInfo, status)
                btnQuote -> clickQuote(accessInfo, status)
                btnBoost -> clickBoost(accessInfo, status, willToast = bSimpleList)
                btnFavourite -> clickFavourite(accessInfo, status, willToast = bSimpleList)
                btnBookmark -> clickBookmark(accessInfo, status, willToast = bSimpleList)
                btnReaction -> clickReaction(accessInfo, column, status)
                btnFollow2 -> clickFollow(pos, accessInfo, status.accountRef, relation)
                btnTranslate -> shareStatusText(status, CustomShareTarget.Translate)
                btnCustomShare1 -> shareStatusText(status, CustomShareTarget.CustomShare1)
                btnCustomShare2 -> shareStatusText(status, CustomShareTarget.CustomShare2)
                btnCustomShare3 -> shareStatusText(status, CustomShareTarget.CustomShare3)
            }
        }
    }

    override fun onLongClick(v: View): Boolean {
        closeWindow?.dismiss()
        closeWindow = null

        val status = this.status ?: return true

        with(activity) {
            when (v) {
                btnBoost -> boostFromAnotherAccount(accessInfo, status)
                btnFavourite -> favouriteFromAnotherAccount(accessInfo, status)
                btnBookmark -> bookmarkFromAnotherAccount(accessInfo, status)
                btnReply -> replyFromAnotherAccount(accessInfo, status)
                btnQuote -> quoteFromAnotherAccount(accessInfo, status)
                btnReaction -> reactionFromAnotherAccount(accessInfo, status)
                btnConversation -> conversationOtherInstance(nextPosition(column), status)
                btnFollow2 -> followFromAnotherAccount(nextPosition(column), accessInfo, status.account)

                // 以下、長押し
                btnTranslate -> shareStatusUrl(status, CustomShareTarget.Translate)
                btnCustomShare1 -> shareStatusUrl(status, CustomShareTarget.CustomShare1)
                btnCustomShare2 -> shareStatusUrl(status, CustomShareTarget.CustomShare2)
                btnCustomShare3 -> shareStatusUrl(status, CustomShareTarget.CustomShare3)
            }
        }
        return true
    }

    private fun shareStatusText(status: TootStatus, target: CustomShareTarget) {
        CustomShare.invokeStatusText(target, activity, accessInfo, status)
    }

    private fun shareStatusUrl(status: TootStatus, target: CustomShareTarget) {
        CustomShare.invokeText(target, activity, status.url ?: status.uri)
    }

    private fun clickMore(status: TootStatus) {
        DlgContextMenu(
            activity,
            column,
            status.accountRef,
            status,
            notification,
            itemViewHolder.tvContent
        ).show()
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

    fun AnkoFlexboxLayout.normalButtons() {
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

    private fun AnkoFlexboxLayout.additionalButtons() {
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

    init {
        viewRoot = with(activity.UI {}) {
            customView<AnkoFlexboxLayout> {
                // トップレベルのViewGroupのlparamsはイニシャライザ内部に置くしかないみたい
                layoutParams = LinearLayout.LayoutParams(lpWidth, wrapContent).apply {
                    topMargin = dip(topMarginDp)
                }
                flexWrap = FlexWrap.WRAP
                this.justifyContent = justifyContent
                when (AdditionalButtonsPosition.fromIndex(PrefI.ipAdditionalButtonsPosition(activity.pref))) {
                    AdditionalButtonsPosition.Top, AdditionalButtonsPosition.Start -> {
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
