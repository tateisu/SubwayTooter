package jp.juggler.subwaytooter

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
import android.widget.*
import androidx.core.content.ContextCompat
import com.google.android.flexbox.JustifyContent
import jp.juggler.subwaytooter.api.entity.TimelineItem
import jp.juggler.subwaytooter.api.entity.TootAccountRef
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.drawable.PreviewCardBorder
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.*
import jp.juggler.subwaytooter.view.*
import jp.juggler.util.LogCategory
import jp.juggler.util.applyAlphaMultiplier
import jp.juggler.util.attrColor
import org.jetbrains.anko.*

class ItemViewHolder(
    val activity: ActMain,
) : View.OnClickListener, View.OnLongClickListener {

    companion object {

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

    lateinit var llBoosted: View
    lateinit var ivBoosted: ImageView
    lateinit var tvBoosted: TextView
    lateinit var tvBoostedAcct: TextView
    lateinit var tvBoostedTime: TextView

    lateinit var llReply: View
    lateinit var ivReply: ImageView
    lateinit var tvReply: TextView

    lateinit var llFollow: View
    lateinit var ivFollow: MyNetworkImageView
    lateinit var tvFollowerName: TextView
    lateinit var tvFollowerAcct: TextView
    lateinit var btnFollow: ImageButton
    lateinit var ivFollowedBy: ImageView

    lateinit var llStatus: View
    lateinit var ivThumbnail: MyNetworkImageView
    lateinit var tvName: TextView
    lateinit var tvTime: TextView
    lateinit var tvAcct: TextView

    lateinit var llContentWarning: View
    lateinit var tvContentWarning: MyTextView
    lateinit var btnContentWarning: Button

    lateinit var llContents: View
    lateinit var tvMentions: MyTextView
    internal lateinit var tvContent: MyTextView

    lateinit var flMedia: View
    lateinit var llMedia: View
    lateinit var btnShowMedia: BlurhashView
    lateinit var ivMedia1: MyNetworkImageView
    lateinit var ivMedia2: MyNetworkImageView
    lateinit var ivMedia3: MyNetworkImageView
    lateinit var ivMedia4: MyNetworkImageView
    lateinit var btnHideMedia: ImageButton

    lateinit var statusButtonsViewHolder: StatusButtonsViewHolder
    lateinit var llButtonBar: View

    lateinit var llSearchTag: View
    lateinit var btnSearchTag: Button
    lateinit var btnGapHead: ImageButton
    lateinit var btnGapTail: ImageButton
    lateinit var llTrendTag: View
    lateinit var tvTrendTagName: TextView
    lateinit var tvTrendTagDesc: TextView
    lateinit var tvTrendTagCount: TextView
    lateinit var cvTagHistory: TagHistoryView

    lateinit var llList: View
    lateinit var btnListTL: Button
    lateinit var btnListMore: ImageButton

    lateinit var llFollowRequest: View
    lateinit var btnFollowRequestAccept: ImageButton
    lateinit var btnFollowRequestDeny: ImageButton

    lateinit var llFilter: View
    lateinit var tvFilterPhrase: TextView
    lateinit var tvFilterDetail: TextView

    lateinit var tvMediaDescription: TextView

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
    lateinit var tvConversationIconsMore: TextView
    lateinit var tvConversationParticipants: TextView

    lateinit var tvApplication: TextView

    lateinit var tvMessageHolder: TextView

    lateinit var llOpenSticker: View
    lateinit var ivOpenSticker: MyNetworkImageView
    lateinit var tvOpenSticker: TextView

    lateinit var tvLastStatusAt: TextView

    lateinit var accessInfo: SavedAccount

    var buttonsForStatus: StatusButtons? = null

    var item: TimelineItem? = null

    var statusShowing: TootStatus? = null
    var statusReply: TootStatus? = null
    var statusAccount: TootAccountRef? = null
    var boostAccount: TootAccountRef? = null
    var followAccount: TootAccountRef? = null

    var boostTime: Long = 0L

    var contentColor: Int = 0
    var acctColor: Int = 0
    var contentColorCsl: ColorStateList = ColorStateList.valueOf(0)

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
            btnListTL,
            btnListMore,
            btnSearchTag,
            btnGapHead,
            btnGapTail,
            btnContentWarning,
            btnShowMedia,
            ivMedia1,
            ivMedia2,
            ivMedia3,
            ivMedia4,
            btnFollow,
            ivCardImage,
            btnCardImageHide,
            btnCardImageShow,
            ivThumbnail,
            llBoosted,
            llReply,
            llFollow,
            btnFollow,
            btnFollowRequestAccept,
            btnFollowRequestDeny,
            btnHideMedia,
            llTrendTag,
            llFilter
        )) {
            v.setOnClickListener(this)
        }

        for (v in arrayOf(
            btnSearchTag,
            btnFollow,
            ivCardImage,
            llBoosted,
            llReply,
            llFollow,
            llConversationIcons,
            ivThumbnail,
            llTrendTag
        )) {
            v.setOnLongClickListener(this)
        }

        //
        tvContent.movementMethod = MyLinkMovementMethod
        tvMentions.movementMethod = MyLinkMovementMethod
        tvContentWarning.movementMethod = MyLinkMovementMethod
        tvMediaDescription.movementMethod = MyLinkMovementMethod
        tvCardText.movementMethod = MyLinkMovementMethod

        var f: Float

        f = activity.timelineFontSizeSp
        if (!f.isNaN()) {
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
            tvMediaDescription.textSize = f
            tvCardText.textSize = f
            tvConversationIconsMore.textSize = f
            tvConversationParticipants.textSize = f
        }

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

        val spacing = activity.timelineSpacing
        if (spacing != null) {
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
            tvMediaDescription.setLineSpacing(0f, spacing)
            tvCardText.setLineSpacing(0f, spacing)
            tvConversationIconsMore.setLineSpacing(0f, spacing)
            tvConversationParticipants.setLineSpacing(0f, spacing)
            tvBoosted.setLineSpacing(0f, spacing)
            tvReply.setLineSpacing(0f, spacing)
            tvLastStatusAt.setLineSpacing(0f, spacing)
        }

        var s = activity.avatarIconSize
        ivThumbnail.layoutParams.height = s
        ivThumbnail.layoutParams.width = s
        ivFollow.layoutParams.width = s
        ivBoosted.layoutParams.width = s

        s = ActMain.replyIconSize + (activity.density * 8).toInt()
        ivReply.layoutParams.width = s
        ivReply.layoutParams.height = s

        s = activity.notificationTlIconSize
        ivBoosted.layoutParams.height = s

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

    fun inflate(activity: ActMain) = with(activity.UI {}) {
        val b = Benchmark(log, "Item-Inflate", 40L)
        val rv = verticalLayout {
            // トップレベルのViewGroupのlparamsはイニシャライザ内部に置くしかないみたい
            layoutParams =
                androidx.recyclerview.widget.RecyclerView.LayoutParams(matchParent, wrapContent)
                    .apply {
                        marginStart = dip(8)
                        marginEnd = dip(8)
                        topMargin = dip(2f)
                        bottomMargin = dip(1f)
                    }

            setPaddingRelative(dip(4), dip(1f), dip(4), dip(2f))

            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

            llBoosted = linearLayout {
                lparams(matchParent, wrapContent) {
                    bottomMargin = dip(6)
                }
                backgroundResource = R.drawable.btn_bg_transparent_round6dp
                gravity = Gravity.CENTER_VERTICAL

                ivBoosted = imageView {
                    scaleType = ImageView.ScaleType.FIT_END
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }.lparams(dip(48), dip(32)) {
                    endMargin = dip(4)
                }

                verticalLayout {
                    lparams(dip(0), wrapContent) {
                        weight = 1f
                    }

                    linearLayout {
                        lparams(matchParent, wrapContent)

                        tvBoostedAcct = textView {
                            ellipsize = TextUtils.TruncateAt.END
                            gravity = Gravity.END
                            maxLines = 1
                            textSize = 12f // textSize の単位はSP
                            // tools:text ="who@hoge"
                        }.lparams(dip(0), wrapContent) {
                            weight = 1f
                        }

                        tvBoostedTime = textView {

                            startPadding = dip(2)

                            gravity = Gravity.END
                            textSize = 12f // textSize の単位はSP
                            // tools:ignore="RtlSymmetry"
                            // tools:text="2017-04-16 09:37:14"
                        }.lparams(wrapContent, wrapContent)
                    }

                    tvBoosted = textView {
                        // tools:text = "～にブーストされました"
                    }.lparams(matchParent, wrapContent)
                }
            }

            llFollow = linearLayout {
                lparams(matchParent, wrapContent)

                background =
                    ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent_round6dp)
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

                    tvFollowerName = textView {
                        // tools:text="Follower Name"
                    }.lparams(matchParent, wrapContent)

                    tvFollowerAcct = textView {
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

            llStatus = verticalLayout {
                lparams(matchParent, wrapContent)

                linearLayout {
                    lparams(matchParent, wrapContent)

                    tvAcct = textView {
                        ellipsize = TextUtils.TruncateAt.END
                        gravity = Gravity.END
                        maxLines = 1
                        textSize = 12f // SP
                        // tools:text="who@hoge"
                    }.lparams(dip(0), wrapContent) {
                        weight = 1f
                    }

                    tvTime = textView {
                        gravity = Gravity.END
                        startPadding = dip(2)
                        textSize = 12f // SP
                        // tools:ignore="RtlSymmetry"
                        // tools:text="2017-04-16 09:37:14"
                    }.lparams(wrapContent, wrapContent)
                }

                linearLayout {
                    lparams(matchParent, wrapContent)

                    ivThumbnail = myNetworkImageView {
                        background =
                            ContextCompat.getDrawable(
                                context,
                                R.drawable.btn_bg_transparent_round6dp
                            )
                        contentDescription = context.getString(R.string.thumbnail)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }.lparams(dip(48), dip(48)) {
                        topMargin = dip(4)
                        endMargin = dip(4)
                    }

                    verticalLayout {
                        lparams(dip(0), wrapContent) {
                            weight = 1f
                        }

                        tvName = textView {
                        }.lparams(matchParent, wrapContent)

                        llOpenSticker = linearLayout {
                            lparams(matchParent, wrapContent)

                            ivOpenSticker = myNetworkImageView {
                            }.lparams(dip(16), dip(16)) {
                                isBaselineAligned = false
                            }

                            tvOpenSticker = textView {
                                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10f)
                                gravity = Gravity.CENTER_VERTICAL
                                setPaddingStartEnd(dip(4f), dip(4f))
                            }.lparams(0, dip(16)) {
                                isBaselineAligned = false
                                weight = 1f
                            }
                        }

                        llReply = linearLayout {
                            lparams(matchParent, wrapContent) {
                                bottomMargin = dip(3)
                            }

                            background =
                                ContextCompat.getDrawable(
                                    context,
                                    R.drawable.btn_bg_transparent_round6dp
                                )
                            gravity = Gravity.CENTER_VERTICAL

                            ivReply = imageView {
                                scaleType = ImageView.ScaleType.FIT_END
                                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                                padding = dip(4)
                            }.lparams(dip(32), dip(32)) {
                                endMargin = dip(4)
                            }

                            tvReply = textView {
                            }.lparams(dip(0), wrapContent) {
                                weight = 1f
                            }
                        }

                        llContentWarning = linearLayout {
                            lparams(matchParent, wrapContent) {
                                topMargin = dip(3)
                                isBaselineAligned = false
                            }
                            gravity = Gravity.CENTER_VERTICAL

                            btnContentWarning = button {
                                backgroundDrawable =
                                    ContextCompat.getDrawable(context, R.drawable.bg_button_cw)
                                minWidthCompat = dip(40)
                                padding = dip(4)
                                //tools:text="見る"
                            }.lparams(wrapContent, dip(40)) {
                                endMargin = dip(8)
                            }

                            verticalLayout {
                                lparams(dip(0), wrapContent) {
                                    weight = 1f
                                }

                                tvMentions = myTextView {
                                }.lparams(matchParent, wrapContent)

                                tvContentWarning = myTextView {
                                }.lparams(matchParent, wrapContent) {
                                    topMargin = dip(3)
                                }
                            }
                        }

                        llContents = verticalLayout {
                            lparams(matchParent, wrapContent)

                            tvContent = myTextView {
                                setLineSpacing(lineSpacingExtra, 1.1f)
                                // tools:text="Contents\nContents"
                            }.lparams(matchParent, wrapContent) {
                                topMargin = dip(3)
                            }

                            val thumbnailHeight = activity.appState.mediaThumbHeight
                            val verticalArrangeThumbnails =
                                Pref.bpVerticalArrangeThumbnails(activity.pref)

                            flMedia = if (verticalArrangeThumbnails) {
                                frameLayout {
                                    lparams(matchParent, wrapContent) {
                                        topMargin = dip(3)
                                    }
                                    llMedia = verticalLayout {
                                        lparams(matchParent, matchParent)

                                        btnHideMedia = imageButton {
                                            background = ContextCompat.getDrawable(
                                                context,
                                                R.drawable.btn_bg_transparent_round6dp
                                            )
                                            contentDescription = context.getString(R.string.hide)
                                            imageResource = R.drawable.ic_close
                                        }.lparams(dip(32), dip(32)) {
                                            gravity = Gravity.END
                                        }

                                        ivMedia1 = myNetworkImageView {
                                            background = ContextCompat.getDrawable(context, R.drawable.bg_thumbnail)
                                            contentDescription = context.getString(R.string.thumbnail)
                                            scaleType = ImageView.ScaleType.CENTER_CROP
                                        }.lparams(matchParent, thumbnailHeight) {
                                            topMargin = dip(3)
                                        }

                                        ivMedia2 = myNetworkImageView {
                                            background = ContextCompat.getDrawable(context, R.drawable.bg_thumbnail)
                                            contentDescription = context.getString(R.string.thumbnail)
                                            scaleType = ImageView.ScaleType.CENTER_CROP
                                        }.lparams(matchParent, thumbnailHeight) {
                                            topMargin = dip(3)
                                        }

                                        ivMedia3 = myNetworkImageView {
                                            background = ContextCompat.getDrawable(context, R.drawable.bg_thumbnail)
                                            contentDescription = context.getString(R.string.thumbnail)
                                            scaleType = ImageView.ScaleType.CENTER_CROP
                                        }.lparams(matchParent, thumbnailHeight) {
                                            topMargin = dip(3)
                                        }

                                        ivMedia4 = myNetworkImageView {
                                            background = ContextCompat.getDrawable(context, R.drawable.bg_thumbnail)
                                            contentDescription = context.getString(R.string.thumbnail)
                                            scaleType = ImageView.ScaleType.CENTER_CROP
                                        }.lparams(matchParent, thumbnailHeight) {
                                            topMargin = dip(3)
                                        }
                                    }

                                    btnShowMedia = blurhashView {
                                        errorColor = context.attrColor(R.attr.colorShowMediaBackground)
                                        gravity = Gravity.CENTER
                                        textColor = context.attrColor(R.attr.colorShowMediaText)
                                        minHeightCompat = dip(48)
                                    }.lparams(matchParent, thumbnailHeight)
                                }
                            } else {
                                frameLayout {
                                    lparams(matchParent, thumbnailHeight) {
                                        topMargin = dip(3)
                                    }
                                    llMedia = linearLayout {
                                        lparams(matchParent, matchParent)

                                        ivMedia1 = myNetworkImageView {
                                            background = ContextCompat.getDrawable(
                                                context,
                                                R.drawable.bg_thumbnail
                                            )
                                            contentDescription =
                                                context.getString(R.string.thumbnail)
                                            scaleType = ImageView.ScaleType.CENTER_CROP
                                        }.lparams(0, matchParent) {
                                            weight = 1f
                                        }

                                        ivMedia2 = myNetworkImageView {
                                            background = ContextCompat.getDrawable(context, R.drawable.bg_thumbnail)
                                            contentDescription = context.getString(R.string.thumbnail)
                                            scaleType = ImageView.ScaleType.CENTER_CROP
                                        }.lparams(0, matchParent) {
                                            startMargin = dip(8)
                                            weight = 1f
                                        }

                                        ivMedia3 = myNetworkImageView {
                                            background = ContextCompat.getDrawable(context, R.drawable.bg_thumbnail)
                                            contentDescription = context.getString(R.string.thumbnail)
                                            scaleType = ImageView.ScaleType.CENTER_CROP
                                        }.lparams(0, matchParent) {
                                            startMargin = dip(8)
                                            weight = 1f
                                        }

                                        ivMedia4 = myNetworkImageView {
                                            background = ContextCompat.getDrawable(context, R.drawable.bg_thumbnail)
                                            contentDescription = context.getString(R.string.thumbnail)
                                            scaleType = ImageView.ScaleType.CENTER_CROP
                                        }.lparams(0, matchParent) {
                                            startMargin = dip(8)
                                            weight = 1f
                                        }

                                        btnHideMedia = imageButton {
                                            background =
                                                ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent_round6dp)
                                            contentDescription = context.getString(R.string.hide)
                                            imageResource = R.drawable.ic_close
                                        }.lparams(dip(32), matchParent) {
                                            startMargin = dip(8)
                                        }
                                    }

                                    btnShowMedia = blurhashView {
                                        errorColor = context.attrColor(
                                            R.attr.colorShowMediaBackground
                                        )
                                        gravity = Gravity.CENTER

                                        textColor = context.attrColor(
                                            R.attr.colorShowMediaText
                                        )
                                    }.lparams(matchParent, matchParent)
                                }
                            }

                            tvMediaDescription = textView {}.lparams(matchParent, wrapContent)

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
                                    lparams(matchParent, activity.appState.mediaThumbHeight) {
                                        topMargin = dip(3)
                                    }

                                    llCardImage = linearLayout {
                                        lparams(matchParent, matchParent)

                                        ivCardImage = myNetworkImageView {
                                            contentDescription =
                                                context.getString(R.string.thumbnail)

                                            scaleType = when {
                                                Pref.bpDontCropMediaThumb(App1.pref) -> ImageView.ScaleType.FIT_CENTER
                                                else -> ImageView.ScaleType.CENTER_CROP
                                            }
                                        }.lparams(0, matchParent) {
                                            weight = 1f
                                        }
                                        btnCardImageHide = imageButton {
                                            background = ContextCompat.getDrawable(
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
                                        errorColor = context.attrColor(
                                            R.attr.colorShowMediaBackground
                                        )
                                        gravity = Gravity.CENTER

                                        textColor = context.attrColor(
                                            R.attr.colorShowMediaText
                                        )
                                    }.lparams(matchParent, matchParent)
                                }
                            }

                            llExtra = verticalLayout {
                                lparams(matchParent, wrapContent) {
                                    topMargin = dip(0)
                                }
                            }
                        }

                        // button bar
                        statusButtonsViewHolder = StatusButtonsViewHolder(
                            activity,
                            matchParent,
                            3f,
                            justifyContent = when (Pref.ipBoostButtonJustify(App1.pref)) {
                                0 -> JustifyContent.FLEX_START
                                1 -> JustifyContent.CENTER
                                else -> JustifyContent.FLEX_END
                            }
                        )
                        llButtonBar = statusButtonsViewHolder.viewRoot
                        addView(llButtonBar)

                        tvApplication = textView {
                            gravity = Gravity.END
                        }.lparams(matchParent, wrapContent)
                    }
                }
            }

            llConversationIcons = linearLayout {
                lparams(matchParent, dip(40))

                isBaselineAligned = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL

                tvConversationParticipants = textView {
                    text = context.getString(R.string.participants)
                }.lparams(wrapContent, wrapContent) {
                    endMargin = dip(3)
                }

                ivConversationIcon1 = myNetworkImageView {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }.lparams(dip(24), dip(24)) {
                    endMargin = dip(3)
                }
                ivConversationIcon2 = myNetworkImageView {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }.lparams(dip(24), dip(24)) {
                    endMargin = dip(3)
                }
                ivConversationIcon3 = myNetworkImageView {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }.lparams(dip(24), dip(24)) {
                    endMargin = dip(3)
                }
                ivConversationIcon4 = myNetworkImageView {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }.lparams(dip(24), dip(24)) {
                    endMargin = dip(3)
                }

                tvConversationIconsMore = textView {}.lparams(wrapContent, wrapContent)
            }

            llSearchTag = linearLayout {
                lparams(matchParent, wrapContent)

                btnSearchTag = button {
                    background =
                        ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent_round6dp)
                    allCaps = false
                }.lparams(0, wrapContent) {
                    weight = 1f
                }

                btnGapHead = imageButton {
                    background = ContextCompat.getDrawable(
                        context,
                        R.drawable.btn_bg_transparent_round6dp
                    )
                    contentDescription = context.getString(R.string.read_gap_head)
                    imageResource = R.drawable.ic_arrow_drop_down
                }.lparams(dip(32), matchParent) {
                    startMargin = dip(8)
                }

                btnGapTail = imageButton {
                    background = ContextCompat.getDrawable(
                        context,
                        R.drawable.btn_bg_transparent_round6dp
                    )
                    contentDescription = context.getString(R.string.read_gap_tail)
                    imageResource = R.drawable.ic_arrow_drop_up
                }.lparams(dip(32), matchParent) {
                    startMargin = dip(8)
                }
            }

            llTrendTag = linearLayout {
                lparams(matchParent, wrapContent)

                gravity = Gravity.CENTER_VERTICAL
                background =
                    ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent_round6dp)

                verticalLayout {
                    lparams(0, wrapContent) {
                        weight = 1f
                    }

                    tvTrendTagName = textView {}.lparams(matchParent, wrapContent)

                    tvTrendTagDesc = textView {
                        textSize = 12f // SP
                    }.lparams(matchParent, wrapContent)
                }
                tvTrendTagCount = textView {}.lparams(wrapContent, wrapContent) {
                    startMargin = dip(6)
                    endMargin = dip(6)
                }

                cvTagHistory = trendTagHistoryView {}.lparams(dip(64), dip(32))
            }

            llList = linearLayout {
                lparams(matchParent, wrapContent)

                gravity = Gravity.CENTER_VERTICAL
                isBaselineAligned = false
                minimumHeight = dip(40)

                btnListTL = button {
                    background =
                        ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent_round6dp)
                    allCaps = false
                }.lparams(0, wrapContent) {
                    weight = 1f
                }

                btnListMore = imageButton {
                    background =
                        ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent_round6dp)
                    imageResource = R.drawable.ic_more
                    contentDescription = context.getString(R.string.more)
                }.lparams(dip(40), matchParent) {
                    startMargin = dip(4)
                }
            }

            tvMessageHolder = textView {
                padding = dip(4)
            }.lparams(matchParent, wrapContent)

            llFollowRequest = linearLayout {
                lparams(matchParent, wrapContent) {
                    topMargin = dip(6)
                }
                gravity = Gravity.END

                btnFollowRequestAccept = imageButton {
                    background =
                        ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent_round6dp)
                    contentDescription = context.getString(R.string.follow_accept)
                    imageResource = R.drawable.ic_check
                    setPadding(0, 0, 0, 0)
                }.lparams(dip(48f), dip(32f))

                btnFollowRequestDeny = imageButton {
                    background =
                        ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent_round6dp)
                    contentDescription = context.getString(R.string.follow_deny)
                    imageResource = R.drawable.ic_close
                    setPadding(0, 0, 0, 0)
                }.lparams(dip(48f), dip(32f)) {
                    startMargin = dip(4)
                }
            }

            llFilter = verticalLayout {
                lparams(matchParent, wrapContent) {
                }
                minimumHeight = dip(40)

                tvFilterPhrase = textView {
                    typeface = Typeface.DEFAULT_BOLD
                }.lparams(matchParent, wrapContent)

                tvFilterDetail = textView {
                    textSize = 12f // SP
                }.lparams(matchParent, wrapContent)
            }
        }
        b.report()
        rv
    }
}
