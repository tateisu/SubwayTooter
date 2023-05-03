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
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.action.bookmarkFromAnotherAccount
import jp.juggler.subwaytooter.action.boostFromAnotherAccount
import jp.juggler.subwaytooter.action.clickBookmark
import jp.juggler.subwaytooter.action.clickBoost
import jp.juggler.subwaytooter.action.clickConversation
import jp.juggler.subwaytooter.action.clickFavourite
import jp.juggler.subwaytooter.action.clickFollow
import jp.juggler.subwaytooter.action.clickQuote
import jp.juggler.subwaytooter.action.clickReaction
import jp.juggler.subwaytooter.action.clickReply
import jp.juggler.subwaytooter.action.conversationOtherInstance
import jp.juggler.subwaytooter.action.favouriteFromAnotherAccount
import jp.juggler.subwaytooter.action.followFromAnotherAccount
import jp.juggler.subwaytooter.action.quoteFromAnotherAccount
import jp.juggler.subwaytooter.action.reactionFromAnotherAccount
import jp.juggler.subwaytooter.action.replyFromAnotherAccount
import jp.juggler.subwaytooter.actmain.nextPosition
import jp.juggler.subwaytooter.api.entity.InstanceCapability
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.api.entity.TootReaction
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.api.entity.TootVisibility
import jp.juggler.subwaytooter.column.Column
import jp.juggler.subwaytooter.column.getContentColor
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.pref.PrefI
import jp.juggler.subwaytooter.setFollowIcon
import jp.juggler.subwaytooter.stylerBoostAlpha
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.table.daoUserRelation
import jp.juggler.subwaytooter.util.CustomShare
import jp.juggler.subwaytooter.util.CustomShareTarget
import jp.juggler.subwaytooter.util.startMargin
import jp.juggler.util.data.notZero
import jp.juggler.util.log.LogCategory
import jp.juggler.util.ui.applyAlphaMultiplier
import jp.juggler.util.ui.attrColor
import jp.juggler.util.ui.createColoredDrawable
import jp.juggler.util.ui.setIconDrawableId
import jp.juggler.util.ui.vg
import org.jetbrains.anko.UI
import org.jetbrains.anko.custom.customView
import org.jetbrains.anko.dip
import org.jetbrains.anko.frameLayout
import org.jetbrains.anko.imageButton
import org.jetbrains.anko.imageResource
import org.jetbrains.anko.imageView
import org.jetbrains.anko.matchParent
import org.jetbrains.anko.wrapContent

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
    private val btnCustomShares = holder.btnCustomShares
    private val btnMore = holder.btnMore

    private val colorTextContent = column.getContentColor()

    private var optionalButtonFirst: View? = null
    private var optionalButtonCount = 0
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
        ).forEach {
            it.setOnClickListener(this)
            it.setOnLongClickListener(this)
        }
        btnCustomShares.forEach {
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
            color = colorTextContent,
            alphaMultiplier = stylerBoostAlpha
        )
    }

    private fun bindMoreButton() {
        setIconDrawableId(
            activity,
            btnMore,
            R.drawable.ic_more,
            color = colorTextContent,
            alphaMultiplier = stylerBoostAlpha
        )
    }

    private fun bindReplyButton(status: TootStatus) {
        setButton(
            btnReply,
            true,
            colorTextContent,
            R.drawable.ic_reply,
            when (val repliesCount = status.replies_count) {
                null -> ""
                else -> when (PrefI.ipRepliesCount.value) {
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
                    PrefI.ipButtonBoostedColor.value.notZero()
                        ?: activity.attrColor(R.attr.colorButtonAccentBoost),
                    R.drawable.ic_mail,
                    "",
                    activity.getString(R.string.boost)
                )

            activity.appState.isBusyBoost(accessInfo, status) ->
                setButton(
                    btnBoost,
                    false,
                    colorTextContent,
                    R.drawable.ic_refresh,
                    "?",
                    activity.getString(R.string.boost)
                )

            else -> setButton(
                btnBoost,
                true,
                when {
                    status.reblogged ->
                        PrefI.ipButtonBoostedColor.value.notZero()
                            ?: activity.attrColor(R.attr.colorButtonAccentBoost)

                    else ->
                        colorTextContent
                },
                R.drawable.ic_repeat,
                when (val boostsCount = status.reblogs_count) {
                    null -> ""
                    else -> when (PrefI.ipBoostsCount.value) {
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
                colorTextContent,
                R.drawable.ic_quote,
                activity.getString(R.string.quote)
            )
        }
    }

    private fun bindReactionButton(status: TootStatus) {
        btnReaction.vg(TootReaction.canReaction(accessInfo, ti))?.let {
            val myReactionCount: Int = status.reactionSet?.myReactionCount ?: 0
            val maxReactionPerAccount: Int =
                InstanceCapability.maxReactionPerAccount(accessInfo, ti)
            setButton(
                it,
                true,
                when (myReactionCount) {
                    0 -> colorTextContent
                    else -> PrefI.ipButtonReactionedColor.value.notZero()
                        ?: activity.attrColor(R.attr.colorButtonAccentReaction)
                },
                when (myReactionCount >= maxReactionPerAccount) {
                    true -> R.drawable.outline_face_retouching_off
                    else -> R.drawable.outline_face
                },
                activity.getString(
                    when (myReactionCount >= maxReactionPerAccount) {
                        true -> R.string.reaction_remove
                        else -> R.string.reaction_add
                    },
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
                    colorTextContent,
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
                            PrefI.ipButtonFavoritedColor.value.notZero()
                                ?: activity.attrColor(R.attr.colorButtonAccentFavourite)

                        else -> colorTextContent
                    },
                    when {
                        status.favourited -> R.drawable.ic_star
                        else -> R.drawable.ic_star_outline
                    },
                    when (val favouritesCount = status.favourites_count) {
                        null -> ""
                        else -> when (PrefI.ipFavouritesCount.value) {
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
        btnBookmark.vg(PrefB.bpShowBookmarkButton.value)
            ?.let { btn ->
                when {
                    activity.appState.isBusyBookmark(accessInfo, status) ->
                        setButton(
                            btn,
                            false,
                            colorTextContent,
                            R.drawable.ic_refresh,
                            activity.getString(R.string.bookmark)
                        )

                    else ->
                        setButton(
                            btn,
                            true,
                            when {
                                status.bookmarked ->
                                    PrefI.ipButtonBookmarkedColor.value.notZero()
                                        ?: activity.attrColor(R.attr.colorButtonAccentBookmark)

                                else ->
                                    colorTextContent
                            },
                            when {
                                status.bookmarked -> R.drawable.ic_bookmark_added
                                else -> R.drawable.ic_bookmark
                            },
                            activity.getString(R.string.bookmark)
                        )
                }
            }
    }

    private fun bindFollowButton(status: TootStatus) {
        val account = status.account
        this.relation = if (!PrefB.bpShowFollowButtonInButtonBar.value) {
            llFollow2.visibility = View.GONE
            null
        } else {
            llFollow2.visibility = View.VISIBLE
            val relation = daoUserRelation.load(accessInfo.db_id, account.id)
            setFollowIcon(
                activity,
                btnFollow2,
                ivFollowedBy2,
                relation,
                account,
                colorTextContent,
                alphaMultiplier = stylerBoostAlpha
            )
            relation
        }
    }

    private fun bindAdditionalButtons() {
        optionalButtonFirst = null
        optionalButtonCount = 0
        btnCustomShares.forEach { btn ->
            val target = btn.getTag(R.id.custom_share_target) as CustomShareTarget
            val (label, icon) = CustomShare.getCache(target)
                ?: error("showCustomShare: invalid target")

            val isShown = when {
                target == CustomShareTarget.Translate && !PrefB.bpShowTranslateButton.value -> false
                else -> label != null || icon != null
            }
            btn.vg(isShown)?.apply {
                isEnabled = true
                contentDescription = label ?: "?"
                setImageDrawable(
                    icon ?: createColoredDrawable(
                        this@StatusButtons.activity,
                        R.drawable.ic_question,
                        colorTextContent,
                        stylerBoostAlpha
                    )
                )
                ++optionalButtonCount
                if (optionalButtonFirst == null) {
                    optionalButtonFirst = this
                }
            }
        }

        val updateAdditionalButton: (btn: ImageButton) -> Unit =
            getUpdateAdditionalButton(optionalButtonCount, optionalButtonFirst)

        btnCustomShares.forEach { btn ->
            updateAdditionalButton(btn)
        }
    }

    private fun getUpdateAdditionalButton(
        optionalButtonCount: Int,
        optionalButtonFirst: View?,
    ): (btn: ImageButton) -> Unit {
        val lpConversation = btnConversation.layoutParams as? FlexboxLayout.LayoutParams
        return when (AdditionalButtonsPosition.fromIndex(PrefI.ipAdditionalButtonsPosition.value)) {
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
        val alpha = stylerBoostAlpha
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
        val alpha = stylerBoostAlpha
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
                btnConversation -> clickConversation(
                    pos,
                    accessInfo,
                    itemViewHolder.listAdapter,
                    status = status
                )

                btnReply -> clickReply(accessInfo, status)
                btnQuote -> clickQuote(accessInfo, status)
                btnBoost -> clickBoost(accessInfo, status, willToast = bSimpleList)
                btnFavourite -> clickFavourite(accessInfo, status, willToast = bSimpleList)
                btnBookmark -> clickBookmark(accessInfo, status, willToast = bSimpleList)
                btnReaction -> clickReaction(accessInfo, column, status)
                btnFollow2 -> clickFollow(pos, accessInfo, status.accountRef, relation)

                else -> {
                    btnCustomShares.find { it == v }?.let {
                        val target = it.getTag(R.id.custom_share_target) as CustomShareTarget
                        shareStatusText(status, target)
                        return
                    }
                }
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
                btnFollow2 -> followFromAnotherAccount(
                    nextPosition(column),
                    accessInfo,
                    status.account
                )

                else -> {
                    btnCustomShares.find { it == v }?.let {
                        val target = it.getTag(R.id.custom_share_target) as CustomShareTarget
                        shareStatusUrl(status, target)
                        return true
                    }
                }
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
    lateinit var btnCustomShares: List<ImageButton>
    lateinit var btnCustomShare1: ImageButton
    lateinit var btnMore: ImageButton

    private fun AnkoFlexboxLayout.normalButtons() {
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
        btnCustomShares = CustomShareTarget.values().map { target ->
            imageButton {
                background = ContextCompat.getDrawable(
                    context,
                    R.drawable.btn_bg_transparent_round6dp
                )
                setPadding(paddingH, paddingV, paddingH, paddingV)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setTag(R.id.custom_share_target, target)
            }.lparams(buttonHeight, buttonHeight) {
                startMargin = marginBetween
            }
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
                when (AdditionalButtonsPosition.fromIndex(
                    PrefI.ipAdditionalButtonsPosition.value
                )) {
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
