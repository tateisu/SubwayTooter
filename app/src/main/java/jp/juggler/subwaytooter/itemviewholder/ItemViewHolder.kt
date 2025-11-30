package jp.juggler.subwaytooter.itemviewholder

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import com.google.android.flexbox.JustifyContent
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TimelineItem
import jp.juggler.subwaytooter.api.entity.TootAccountRef
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.column.Column
import jp.juggler.subwaytooter.columnviewholder.ItemListAdapter
import jp.juggler.subwaytooter.drawable.PreviewCardBorder
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.pref.PrefI
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.NetworkEmojiInvalidator
import jp.juggler.subwaytooter.util.blurhashView
import jp.juggler.subwaytooter.util.compatButton
import jp.juggler.subwaytooter.util.endMargin
import jp.juggler.subwaytooter.util.minHeightCompat
import jp.juggler.subwaytooter.util.myNetworkImageView
import jp.juggler.subwaytooter.util.myTextView
import jp.juggler.subwaytooter.util.setPaddingStartEnd
import jp.juggler.subwaytooter.util.startMargin
import jp.juggler.subwaytooter.util.startPadding
import jp.juggler.subwaytooter.util.trendTagHistoryView
import jp.juggler.subwaytooter.view.BlurhashView
import jp.juggler.subwaytooter.view.MyLinkMovementMethod
import jp.juggler.subwaytooter.view.MyNetworkImageView
import jp.juggler.subwaytooter.view.MyTextView
import jp.juggler.subwaytooter.view.TagHistoryView
import jp.juggler.util.log.Benchmark
import jp.juggler.util.log.LogCategory
import jp.juggler.util.ui.applyAlphaMultiplier
import jp.juggler.util.ui.attrColor
import jp.juggler.util.ui.resDrawable
import org.jetbrains.anko.UI
import org.jetbrains.anko._LinearLayout
import org.jetbrains.anko.allCaps
import org.jetbrains.anko.backgroundDrawable
import org.jetbrains.anko.backgroundResource
import org.jetbrains.anko.bottomPadding
import org.jetbrains.anko.button
import org.jetbrains.anko.dip
import org.jetbrains.anko.frameLayout
import org.jetbrains.anko.imageButton
import org.jetbrains.anko.imageResource
import org.jetbrains.anko.imageView
import org.jetbrains.anko.linearLayout
import org.jetbrains.anko.matchParent
import org.jetbrains.anko.padding
import org.jetbrains.anko.textColor
import org.jetbrains.anko.verticalLayout
import org.jetbrains.anko.verticalMargin
import org.jetbrains.anko.wrapContent

class ItemViewHolder(
    val activity: ActMain,
) : View.OnClickListener, View.OnLongClickListener {

    companion object {
        const val MEDIA_VIEW_COUNT = 4

        val log = LogCategory("ItemViewHolder")
        var toot_color_unlisted: Int = 0
        var toot_color_follower: Int = 0
        var toot_color_direct_user: Int = 0
        var toot_color_direct_me: Int = 0
    }

    val viewRoot: View

    var bSimpleList: Boolean = false

    lateinit var column: Column

    internal lateinit var listAdapter: ItemListAdapter

    private val inflateBench = Benchmark(log, "Item-Inflate", 40L)
    val bindBenchmark = Benchmark(log, "Item-bind", 40L)

    lateinit var llBoosted: View
    lateinit var ivBoostAvatar: MyNetworkImageView
    lateinit var ivBoosted: ImageView
    lateinit var tvBoosted: MyTextView
    lateinit var tvBoostedAcct: MyTextView
    lateinit var tvBoostedTime: MyTextView

    lateinit var llReply: View
    lateinit var ivReplyAvatar: MyNetworkImageView
    lateinit var ivReply: ImageView
    lateinit var tvReply: MyTextView

    lateinit var llFollow: View
    lateinit var ivFollow: MyNetworkImageView
    lateinit var tvFollowerName: MyTextView
    lateinit var tvFollowerAcct: MyTextView
    lateinit var btnFollow: ImageButton
    lateinit var ivFollowedBy: ImageView

    lateinit var llStatus: View
    lateinit var ivAvatar: MyNetworkImageView
    lateinit var tvName: MyTextView
    lateinit var tvTime: MyTextView
    lateinit var tvAcct: MyTextView

    lateinit var llContentWarning: View
    lateinit var tvContentWarning: MyTextView
    lateinit var btnContentWarning: AppCompatImageButton

    lateinit var llContents: View
    lateinit var tvMentions: MyTextView
    internal lateinit var tvContent: MyTextView

    lateinit var flMedia: View
    lateinit var llMedia: View
    lateinit var btnShowMedia: BlurhashView
    lateinit var btnHideMedia: ImageButton
    lateinit var tvMediaCount: MyTextView
    val tvMediaDescriptions = ArrayList<AppCompatButton>()
    val ivMediaThumbnails = ArrayList<MyNetworkImageView>()

    lateinit var statusButtonsViewHolder: StatusButtonsViewHolder
    lateinit var llButtonBar: View

    lateinit var llSearchTag: View
    lateinit var btnSearchTag: AppCompatButton
    lateinit var btnGapHead: ImageButton
    lateinit var btnGapTail: ImageButton
    lateinit var llTrendTag: View
    lateinit var tvTrendTagName: MyTextView
    lateinit var tvTrendTagDesc: MyTextView
    lateinit var tvTrendTagCount: MyTextView
    lateinit var cvTagHistory: TagHistoryView

    lateinit var llList: View
    lateinit var btnListTL: AppCompatButton
    lateinit var btnListMore: ImageButton

    lateinit var llFollowRequest: View
    lateinit var btnFollowRequestAccept: ImageButton
    lateinit var btnFollowRequestDeny: ImageButton

    lateinit var llFilter: View
    lateinit var tvFilterPhrase: MyTextView
    lateinit var tvFilterDetail: MyTextView

    lateinit var llCardOuter: View
    lateinit var tvCardText: MyTextView
    lateinit var flCardImage: View
    lateinit var llCardImage: View
    lateinit var ivCardImage: MyNetworkImageView
    lateinit var btnCardImageHide: ImageButton
    lateinit var btnCardImageShow: BlurhashView

    lateinit var llExtra: LinearLayout

    lateinit var llConversationIcons: View
    lateinit var ivConversationIcon1: MyNetworkImageView
    lateinit var ivConversationIcon2: MyNetworkImageView
    lateinit var ivConversationIcon3: MyNetworkImageView
    lateinit var ivConversationIcon4: MyNetworkImageView
    lateinit var tvConversationIconsMore: MyTextView
    lateinit var tvConversationParticipants: MyTextView

    lateinit var tvApplication: MyTextView

    lateinit var tvMessageHolder: MyTextView

    lateinit var llOpenSticker: View
    lateinit var ivOpenSticker: MyNetworkImageView
    lateinit var tvOpenSticker: MyTextView

    @Suppress("MemberVisibilityCanBePrivate")
    lateinit var tvLastStatusAt: MyTextView

    lateinit var accessInfo: SavedAccount

    var buttonsForStatus: StatusButtons? = null

    var item: TimelineItem? = null

    var statusShowing: TootStatus? = null
    var statusReply: TootStatus? = null
    var statusAccount: TootAccountRef? = null
    var boostAccount: TootAccountRef? = null
    var followAccount: TootAccountRef? = null

    var boostTime = 0L

    var colorTextContent: Int = 0
    var acctColor = 0
    var contentColorCsl = ColorStateList.valueOf(0)

    val boostInvalidator: NetworkEmojiInvalidator
    val replyInvalidator: NetworkEmojiInvalidator
    val followInvalidator: NetworkEmojiInvalidator
    val nameInvalidator: NetworkEmojiInvalidator
    val contentInvalidator: NetworkEmojiInvalidator
    val spoilerInvalidator: NetworkEmojiInvalidator
    val lastActiveInvalidator: NetworkEmojiInvalidator
    val extraInvalidatorList = ArrayList<NetworkEmojiInvalidator>()

    var boostedAction: ItemViewHolder.() -> Unit = defaultBoostedAction

    init {
        this.viewRoot = inflate(activity)

        for (v in arrayOf(
            btnCardImageHide,
            btnCardImageShow,
            btnContentWarning,
            btnFollow,
            btnFollow,
            btnFollowRequestAccept,
            btnFollowRequestDeny,
            btnGapHead,
            btnGapTail,
            btnHideMedia,
            btnListMore,
            btnListTL,
            btnSearchTag,
            btnShowMedia,
            ivAvatar,
            ivCardImage,
            llBoosted,
            llFilter,
            llFollow,
            llReply,
            llTrendTag,
        )) {
            v.setOnClickListener(this)
        }
        ivMediaThumbnails.forEach { it.setOnClickListener(this) }
        tvMediaDescriptions.forEach {
            it.isClickable = true
            it.setOnClickListener(this)
        }

        for (v in arrayOf(
            btnSearchTag,
            btnFollow,
            ivCardImage,
            llBoosted,
            llReply,
            llFollow,
            llConversationIcons,
            ivAvatar,
            llTrendTag
        )) {
            v.setOnLongClickListener(this)
        }

        // リンク処理用のMyLinkMovementMethod
        for (v in arrayOf(
            tvContent,
            tvMentions,
            tvContentWarning,
            tvCardText,
            tvMessageHolder,
        )) {
            v.movementMethod = MyLinkMovementMethod
        }

        ActMain.timelineFontSizeSp.takeIf { it.isFinite() }?.let { f ->
            tvFollowerName.textSize = f
            tvName.textSize = f
            tvMentions.textSize = f
            tvContentWarning.textSize = f
            tvContent.textSize = f
            btnShowMedia.textSize = f
            btnCardImageShow.textSize = f
            tvApplication.textSize = f
            tvMessageHolder.textSize = f
            btnListTL.textSize = f
            tvTrendTagName.textSize = f
            tvTrendTagCount.textSize = f
            tvFilterPhrase.textSize = f
            tvCardText.textSize = f
            tvConversationIconsMore.textSize = f
            tvConversationParticipants.textSize = f

            tvMediaDescriptions.forEach { it.textSize = f }
        }

        var f: Float

        f = activity.notificationTlFontSizeSp
        if (!f.isNaN()) {
            tvBoosted.textSize = f
            tvReply.textSize = f
        }

        f = activity.acctFontSizeSp
        if (!f.isNaN()) {
            tvBoostedAcct.textSize = f
            tvBoostedTime.textSize = f
            tvFollowerAcct.textSize = f
            tvLastStatusAt.textSize = f
            tvAcct.textSize = f
            tvTime.textSize = f
            tvTrendTagDesc.textSize = f
            tvFilterDetail.textSize = f
        }

        ActMain.timelineSpacing?.let { spacing ->
            tvFollowerName.setLineSpacing(0f, spacing)
            tvName.setLineSpacing(0f, spacing)
            tvMentions.setLineSpacing(0f, spacing)
            tvContentWarning.setLineSpacing(0f, spacing)
            tvContent.setLineSpacing(0f, spacing)
            btnShowMedia.setLineSpacing(0f, spacing)
            btnCardImageShow.setLineSpacing(0f, spacing)
            tvApplication.setLineSpacing(0f, spacing)
            tvMessageHolder.setLineSpacing(0f, spacing)
            btnListTL.setLineSpacing(0f, spacing)
            tvTrendTagName.setLineSpacing(0f, spacing)
            tvTrendTagCount.setLineSpacing(0f, spacing)
            tvFilterPhrase.setLineSpacing(0f, spacing)
            tvMediaDescriptions.forEach { it.setLineSpacing(0f, spacing) }
            tvCardText.setLineSpacing(0f, spacing)
            tvConversationIconsMore.setLineSpacing(0f, spacing)
            tvConversationParticipants.setLineSpacing(0f, spacing)
            tvBoosted.setLineSpacing(0f, spacing)
            tvReply.setLineSpacing(0f, spacing)
            tvLastStatusAt.setLineSpacing(0f, spacing)
        }

        var s = activity.avatarIconSize
        ivAvatar.layoutParams.height = s
        ivAvatar.layoutParams.width = s
        ivFollow.layoutParams.width = s

        s = ActMain.replyIconSize
        ivReply.layoutParams.width = s
        ivReply.layoutParams.height = s
        ivReplyAvatar.layoutParams.width = s
        ivReplyAvatar.layoutParams.height = s

        s = activity.notificationTlIconSize
        ivBoosted.layoutParams.width = s
        ivBoosted.layoutParams.height = s
        ivBoostAvatar.layoutParams.width = s
        ivBoostAvatar.layoutParams.height = s

        this.contentInvalidator = NetworkEmojiInvalidator(activity.handler, tvContent)
        this.spoilerInvalidator = NetworkEmojiInvalidator(activity.handler, tvContentWarning)
        this.boostInvalidator = NetworkEmojiInvalidator(activity.handler, tvBoosted)
        this.replyInvalidator = NetworkEmojiInvalidator(activity.handler, tvReply)
        this.followInvalidator = NetworkEmojiInvalidator(activity.handler, tvFollowerName)
        this.nameInvalidator = NetworkEmojiInvalidator(activity.handler, tvName)
        this.lastActiveInvalidator = NetworkEmojiInvalidator(activity.handler, tvLastStatusAt)

        val cardBackground = llCardOuter.background
        if (cardBackground is PreviewCardBorder) {
            val density = activity.density
            cardBackground.round = (density * 8f)
            cardBackground.width = (density * 1f)
        }

        val textShowMedia = SpannableString(activity.getString(R.string.tap_to_show))
            .apply {
                val colorBg = activity.attrColor(R.attr.colorShowMediaBackground)
                    .applyAlphaMultiplier(0.5f)
                setSpan(
                    BackgroundColorSpan(colorBg),
                    0,
                    this.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

        btnShowMedia.text = textShowMedia
        btnCardImageShow.text = textShowMedia
    }

    override fun onClick(v: View?) = onClickImpl(v)
    override fun onLongClick(v: View?): Boolean = onLongClickImpl(v)

    fun onViewRecycled() {
    }

    fun getAccount() = statusAccount ?: boostAccount ?: followAccount

    /////////////////////////////////////////////////////////////////////

    private fun _LinearLayout.inflateBoosted() {
        llBoosted = linearLayout {
            lparams(matchParent, wrapContent) {
                bottomMargin = dip(6)
            }
            backgroundResource = R.drawable.btn_bg_transparent_round6dp
            gravity = Gravity.CENTER_VERTICAL

            ivBoosted = imageView {
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }.lparams(dip(32), dip(32)) {}

            ivBoostAvatar = myNetworkImageView {
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }.lparams(dip(32), dip(32)) {}

            verticalLayout {
                lparams(dip(0), wrapContent) {
                    weight = 1f
                    startMargin = dip(4)
                }

                linearLayout {
                    lparams(matchParent, wrapContent)

                    tvBoostedAcct = myTextView {
                        ellipsize = TextUtils.TruncateAt.END
                        gravity = Gravity.END
                        maxLines = 1
                        textSize = 12f // textSize の単位はSP
                        // tools:text ="who@hoge"
                    }.lparams(dip(0), wrapContent) {
                        weight = 1f
                    }

                    tvBoostedTime = myTextView {

                        startPadding = dip(2)

                        gravity = Gravity.END
                        textSize = 12f // textSize の単位はSP
                        // tools:ignore="RtlSymmetry"
                        // tools:text="2017-04-16 09:37:14"
                    }.lparams(wrapContent, wrapContent)
                }

                tvBoosted = myTextView {
                    // tools:text = "～にブーストされました"
                }.lparams(matchParent, wrapContent) {
                    gravity = Gravity.CENTER_VERTICAL
                }
            }
        }
    }

    private fun _LinearLayout.inflateFollowed() {
        llFollow = linearLayout {
            lparams(matchParent, wrapContent)

            background = resDrawable(R.drawable.btn_bg_transparent_round6dp)
            gravity = Gravity.CENTER_VERTICAL

            ivFollow = myNetworkImageView {
                contentDescription = context.getString(R.string.thumbnail)
                scaleType = ImageView.ScaleType.FIT_END
            }.lparams(dip(48), dip(40)) {
                endMargin = dip(4)
            }

            verticalLayout {

                lparams(dip(0), wrapContent) {
                    weight = 1f
                }

                tvFollowerName = myTextView {
                    // tools:text="Follower Name"
                }.lparams(matchParent, wrapContent)

                tvFollowerAcct = myTextView {
                    setPaddingStartEnd(dip(4), dip(4))
                    textSize = 12f // SP
                }.lparams(matchParent, wrapContent)

                tvLastStatusAt = myTextView {
                    setPaddingStartEnd(dip(4), dip(4))
                    textSize = 12f // SP
                }.lparams(matchParent, wrapContent)
            }

            frameLayout {
                lparams(dip(40), dip(40)) {
                    startMargin = dip(4)
                }

                btnFollow = imageButton {
                    background =
                        ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                    contentDescription = context.getString(R.string.follow)
                    scaleType = ImageView.ScaleType.CENTER
                    // tools:src="?attr/ic_follow_plus"
                }.lparams(matchParent, matchParent)

                ivFollowedBy = imageView {
                    scaleType = ImageView.ScaleType.CENTER
                    // tools:src="?attr/ic_followed_by"
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }.lparams(matchParent, matchParent)
            }
        }
    }

    private fun _LinearLayout.inflateVerticalMedia(thumbnailHeight: Int) =
        frameLayout {
            lparams(matchParent, wrapContent) {
                topMargin = dip(3)
            }
            llMedia = verticalLayout {
                lparams(matchParent, matchParent)

                btnHideMedia = imageButton {
                    background = resDrawable(R.drawable.btn_bg_transparent_round6dp)
                    contentDescription = context.getString(R.string.hide)
                    imageResource = R.drawable.ic_close
                }.lparams(dip(32), dip(32)) {
                    gravity = Gravity.END
                }
                ivMediaThumbnails.clear()
                repeat(MEDIA_VIEW_COUNT) {
                    myNetworkImageView {
                        background = resDrawable(R.drawable.bg_thumbnail)
                        contentDescription = context.getString(R.string.thumbnail)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }.lparams(matchParent, thumbnailHeight) {
                        topMargin = dip(3)
                    }.let { ivMediaThumbnails.add(it) }
                }
            }

            btnShowMedia = blurhashView {
                errorColor = context.attrColor(R.attr.colorShowMediaBackground)
                gravity = Gravity.CENTER
                textColor = context.attrColor(R.attr.colorShowMediaText)
                minHeightCompat = dip(48)
            }.lparams(matchParent, thumbnailHeight)
        }

    private fun _LinearLayout.inflateHorizontalMedia(thumbnailHeight: Int) =
        frameLayout {
            lparams(matchParent, thumbnailHeight) { topMargin = dip(3) }
            llMedia = linearLayout {
                lparams(matchParent, matchParent)
                ivMediaThumbnails.clear()

                repeat(MEDIA_VIEW_COUNT) { idx ->
                    myNetworkImageView {
                        background = resDrawable(R.drawable.bg_thumbnail)
                        contentDescription = context.getString(R.string.thumbnail)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }.lparams(0, matchParent) {
                        weight = 1f
                        if (idx > 0) startMargin = dip(8)
                    }.let { ivMediaThumbnails.add(it) }
                }

                btnHideMedia = imageButton {
                    background = ContextCompat.getDrawable(
                        context,
                        R.drawable.btn_bg_transparent_round6dp
                    )
                    contentDescription = context.getString(R.string.hide)
                    imageResource = R.drawable.ic_close
                }.lparams(dip(32), matchParent) {
                    startMargin = dip(8)
                }
            }

            btnShowMedia = blurhashView {
                errorColor = context.attrColor(R.attr.colorShowMediaBackground)
                textColor = context.attrColor(R.attr.colorShowMediaText)
                gravity = Gravity.CENTER
            }.lparams(matchParent, matchParent)
        }

    private fun _LinearLayout.inflateCard(actMain: ActMain) {
        llCardOuter = verticalLayout {
            lparams(matchParent, wrapContent) {
                topMargin = dip(3)
                startMargin = dip(12)
                endMargin = dip(6)
            }
            padding = dip(3)
            bottomPadding = dip(6)

            background = PreviewCardBorder()

            tvCardText = myTextView {
            }.lparams(matchParent, wrapContent) {
            }

            flCardImage = frameLayout {
                lparams(matchParent, actMain.appState.mediaThumbHeight) {
                    topMargin = dip(3)
                }

                llCardImage = linearLayout {
                    lparams(matchParent, matchParent)

                    ivCardImage = myNetworkImageView {
                        contentDescription = context.getString(R.string.thumbnail)
                        scaleType = when {
                            PrefB.bpDontCropMediaThumb.value -> ImageView.ScaleType.FIT_CENTER
                            else -> ImageView.ScaleType.CENTER_CROP
                        }
                    }.lparams(0, matchParent) {
                        weight = 1f
                    }
                    btnCardImageHide = imageButton {
                        background =
                            ContextCompat.getDrawable(
                                context,
                                R.drawable.btn_bg_transparent_round6dp
                            )
                        contentDescription = context.getString(R.string.hide)
                        imageResource = R.drawable.ic_close
                    }.lparams(dip(32), matchParent) {
                        startMargin = dip(4)
                    }
                }

                btnCardImageShow = blurhashView {
                    errorColor = context.attrColor(R.attr.colorShowMediaBackground)
                    textColor = context.attrColor(R.attr.colorShowMediaText)
                    gravity = Gravity.CENTER
                }.lparams(matchParent, matchParent)
            }
        }
    }

    private fun _LinearLayout.inflateStatusReplyInfo() {
        llReply = linearLayout {
            lparams(matchParent, wrapContent) {}
            minimumHeight = dip(40)
            padding = dip(4)
            background = resDrawable(R.drawable.btn_bg_transparent_round6dp)
            gravity = Gravity.CENTER_VERTICAL
            ivReply = imageView {
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }.lparams(dip(32), dip(32)) {
            }

            ivReplyAvatar = myNetworkImageView {
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }.lparams(dip(32), dip(32)) {
                startMargin = dip(2)
            }

            tvReply = myTextView {
            }.lparams(dip(0), wrapContent) {
                startMargin = dip(4)
                weight = 1f
            }
        }
    }

    private fun _LinearLayout.inflateStatusContentWarning() {
        llContentWarning = linearLayout {
            lparams(matchParent, wrapContent) {
                topMargin = dip(3)
                isBaselineAligned = false
            }
            gravity = Gravity.CENTER_VERTICAL

            btnContentWarning = imageButton {
                backgroundDrawable = resDrawable(R.drawable.bg_button_cw)
                contentDescription = context.getString(R.string.show)
                imageResource = R.drawable.ic_eye
                imageTintList = ColorStateList.valueOf(context.attrColor(R.attr.colorTextContent))
            }.lparams(dip(40), dip(40)) {
                endMargin = dip(8)
            }

            verticalLayout {
                lparams(dip(0), wrapContent) {
                    weight = 1f
                }

                tvMentions = myTextView {}.lparams(matchParent, wrapContent)

                tvContentWarning = myTextView {
                }.lparams(matchParent, wrapContent) {
                    topMargin = dip(3)
                }
            }
        }
    }

    private fun _LinearLayout.inflateStatusContents(actMain: ActMain) {
        llContents = verticalLayout {
            lparams(matchParent, wrapContent)

            tvContent = myTextView {
                setLineSpacing(lineSpacingExtra, 1.1f)
            }.lparams(matchParent, wrapContent) {
                topMargin = dip(3)
            }

            val thumbnailHeight = actMain.appState.mediaThumbHeight
            flMedia = when (PrefB.bpVerticalArrangeThumbnails.value) {
                true -> inflateVerticalMedia(thumbnailHeight)
                else -> inflateHorizontalMedia(thumbnailHeight)
            }

            tvMediaCount = myTextView {
                gravity = Gravity.END
                includeFontPadding = false
            }.lparams(matchParent, wrapContent) {
                verticalMargin = dip(3)
            }

            tvMediaDescriptions.clear()
            repeat(MEDIA_VIEW_COUNT) {
                tvMediaDescriptions.add(
                    button {
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        allCaps = false
                        background =
                            ContextCompat.getDrawable(
                                context,
                                R.drawable.btn_bg_transparent_round6dp
                            )
                        minHeightCompat = dip(48)
                        padding = dip(4)
                    }.lparams(matchParent, wrapContent)
                )
            }

            inflateCard(actMain)

            llExtra = verticalLayout {
                lparams(matchParent, wrapContent) {
                    topMargin = dip(0)
                }
            }
        }
    }

    private fun _LinearLayout.inflateStatusButtons(actMain: ActMain) {
        // compatButton bar
        statusButtonsViewHolder = StatusButtonsViewHolder(
            actMain,
            matchParent,
            3f,
            justifyContent = when (PrefI.ipBoostButtonJustify.value) {
                0 -> JustifyContent.FLEX_START
                1 -> JustifyContent.CENTER
                else -> JustifyContent.FLEX_END
            }
        )
        llButtonBar = statusButtonsViewHolder.viewRoot
        addView(llButtonBar)
    }

    private fun _LinearLayout.inflateOpenSticker() {

        llOpenSticker = linearLayout {
            lparams(matchParent, wrapContent)

            ivOpenSticker = myNetworkImageView {
            }.lparams(dip(16), dip(16)) {
                isBaselineAligned = false
            }

            tvOpenSticker = myTextView {
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10f)
                gravity = Gravity.CENTER_VERTICAL
                setPaddingStartEnd(dip(4f), dip(4f))
            }.lparams(0, dip(16)) {
                isBaselineAligned = false
                weight = 1f
            }
        }
    }

    private fun _LinearLayout.inflateStatusAcctTime() {
        linearLayout {
            lparams(matchParent, wrapContent)
            tvAcct = myTextView {
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.END
                maxLines = 1
                textSize = 12f // SP
                // tools:text="who@hoge"
            }.lparams(dip(0), wrapContent) {
                weight = 1f
            }

            tvTime = myTextView {
                gravity = Gravity.END
                startPadding = dip(2)
                textSize = 12f // SP
                // tools:ignore="RtlSymmetry"
                // tools:text="2017-04-16 09:37:14"
            }.lparams(wrapContent, wrapContent)
        }
    }

    private fun _LinearLayout.inflateStatusAvatar() {
        ivAvatar = myNetworkImageView {
            background = resDrawable(R.drawable.btn_bg_transparent_round6dp)
            contentDescription = context.getString(R.string.thumbnail)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }.lparams(dip(48), dip(48)) {
            topMargin = dip(4)
            endMargin = dip(4)
        }
    }

    private fun _LinearLayout.inflateStatus(actMain: ActMain) {
        llStatus = verticalLayout {
            lparams(matchParent, wrapContent)

            inflateStatusAcctTime()

            // horizontal split : avatar and other
            linearLayout {
                lparams(matchParent, wrapContent)
                inflateStatusAvatar()
                verticalLayout {
                    lparams(0, wrapContent) { weight = 1f }

                    tvName = myTextView {}
                        .lparams(matchParent, wrapContent)

                    inflateOpenSticker()
                    inflateStatusReplyInfo()
                    inflateStatusContentWarning()
                    inflateStatusContents(actMain)
                    inflateStatusButtons(actMain)

                    tvApplication = myTextView {
                        gravity = Gravity.END
                    }.lparams(matchParent, wrapContent)
                }
            }
        }
    }

    private fun _LinearLayout.inflateConversationIconOne() =
        myNetworkImageView {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }.lparams(dip(24), dip(24)) {
            endMargin = dip(3)
        }

    private fun _LinearLayout.inflateConversationIcons() {
        llConversationIcons = linearLayout {
            lparams(matchParent, dip(40))

            isBaselineAligned = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL

            tvConversationParticipants = myTextView {
                text = context.getString(R.string.participants)
            }.lparams(wrapContent, wrapContent) {
                endMargin = dip(3)
            }

            ivConversationIcon1 = inflateConversationIconOne()
            ivConversationIcon2 = inflateConversationIconOne()
            ivConversationIcon3 = inflateConversationIconOne()
            ivConversationIcon4 = inflateConversationIconOne()

            tvConversationIconsMore = myTextView {}.lparams(wrapContent, wrapContent)
        }
    }

    private fun _LinearLayout.inflateSearchTag() {
        llSearchTag = linearLayout {
            lparams(matchParent, wrapContent)
            isBaselineAligned = false
            gravity = Gravity.CENTER_VERTICAL or Gravity.START

            btnSearchTag = compatButton {
                background =
                    resDrawable(R.drawable.btn_bg_transparent_round6dp)
                allCaps = false
            }.lparams(0, wrapContent) {
                weight = 1f
            }

            verticalLayout {
                btnGapHead = imageButton {
                    background = ContextCompat.getDrawable(
                        context,
                        R.drawable.btn_bg_transparent_round6dp
                    )
                    contentDescription = context.getString(R.string.read_gap_head)
                    imageResource = R.drawable.ic_arrow_drop_down
                }.lparams(dip(36), dip(36))
                btnGapTail = imageButton {
                    background = ContextCompat.getDrawable(
                        context,
                        R.drawable.btn_bg_transparent_round6dp
                    )
                    contentDescription = context.getString(R.string.read_gap_tail)
                    imageResource = R.drawable.ic_arrow_drop_up
                }.lparams(dip(36), dip(36))
            }.lparams(wrapContent, wrapContent) {
                startMargin = dip(8)
                topMargin = dip(3)
            }
        }
    }

    private fun _LinearLayout.inflateTrendTag() {
        llTrendTag = linearLayout {
            lparams(matchParent, wrapContent)

            gravity = Gravity.CENTER_VERTICAL
            background = resDrawable(R.drawable.btn_bg_transparent_round6dp)

            verticalLayout {
                lparams(0, wrapContent) {
                    weight = 1f
                }

                tvTrendTagName = myTextView {}.lparams(matchParent, wrapContent)

                tvTrendTagDesc = myTextView {
                    textSize = 12f // SP
                }.lparams(matchParent, wrapContent)
            }
            tvTrendTagCount = myTextView {}.lparams(wrapContent, wrapContent) {
                startMargin = dip(6)
                endMargin = dip(6)
            }

            cvTagHistory = trendTagHistoryView {}.lparams(dip(64), dip(32))
        }
    }

    private fun _LinearLayout.inflateList() {
        llList = linearLayout {
            lparams(matchParent, wrapContent)

            gravity = Gravity.CENTER_VERTICAL
            isBaselineAligned = false
            minimumHeight = dip(40)

            btnListTL = compatButton {
                background = resDrawable(R.drawable.btn_bg_transparent_round6dp)
                allCaps = false
            }.lparams(0, wrapContent) {
                weight = 1f
            }

            btnListMore = imageButton {
                background = resDrawable(R.drawable.btn_bg_transparent_round6dp)
                imageResource = R.drawable.ic_more
                contentDescription = context.getString(R.string.more)
            }.lparams(dip(40), matchParent) {
                startMargin = dip(4)
            }
        }
    }

    private fun _LinearLayout.inflateMessageHolder() {
        tvMessageHolder = myTextView {
            padding = dip(4)
            compoundDrawablePadding = dip(4)
        }.lparams(matchParent, wrapContent)
    }

    private fun _LinearLayout.inflateFollowRequest() {
        llFollowRequest = linearLayout {
            lparams(matchParent, wrapContent) {
                topMargin = dip(6)
            }
            gravity = Gravity.END

            btnFollowRequestAccept = imageButton {
                background = resDrawable(R.drawable.btn_bg_transparent_round6dp)
                contentDescription = context.getString(R.string.follow_accept)
                imageResource = R.drawable.ic_check
                setPadding(0, 0, 0, 0)
            }.lparams(dip(48f), dip(32f))

            btnFollowRequestDeny = imageButton {
                background = resDrawable(R.drawable.btn_bg_transparent_round6dp)
                contentDescription = context.getString(R.string.follow_deny)
                imageResource = R.drawable.ic_close
                setPadding(0, 0, 0, 0)
            }.lparams(dip(48f), dip(32f)) {
                startMargin = dip(4)
            }
        }
    }

    private fun _LinearLayout.inflateFilter() {
        llFilter = verticalLayout {
            lparams(matchParent, wrapContent) {
            }
            minimumHeight = dip(40)

            tvFilterPhrase = myTextView {
                typeface = Typeface.DEFAULT_BOLD
            }.lparams(matchParent, wrapContent)

            tvFilterDetail = myTextView {
                textSize = 12f // SP
            }.lparams(matchParent, wrapContent)
        }
    }

    fun inflate(actMain: ActMain) = with(actMain.UI {}) {
        inflateBench.bench {
            verticalLayout {
                // トップレベルのViewGroupのlparamsはイニシャライザ内部に置く
                layoutParams = androidx.recyclerview.widget.RecyclerView.LayoutParams(
                    matchParent,
                    wrapContent,
                ).apply {
                    marginStart = dip(8)
                    marginEnd = dip(8)
                    topMargin = dip(2f)
                    bottomMargin = dip(1f)
                }

                setPaddingRelative(dip(4), dip(1f), dip(4), dip(2f))

                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

                inflateBoosted()
                inflateFollowed()
                inflateStatus(actMain)
                inflateConversationIcons()
                inflateSearchTag()
                inflateTrendTag()
                inflateList()
                inflateMessageHolder()
                inflateFollowRequest()
                inflateFilter()
            }
        }
    }
}
