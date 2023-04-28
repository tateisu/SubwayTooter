package jp.juggler.subwaytooter.itemviewholder

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.StringRes
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.actmain.closePopup
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.column.Column
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.column.getAcctColor
import jp.juggler.subwaytooter.column.getContentColor
import jp.juggler.subwaytooter.columnviewholder.ItemListAdapter
import jp.juggler.subwaytooter.drawable.PreviewCardBorder
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.pref.PrefI
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.table.*
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.emojiSizeMode
import jp.juggler.subwaytooter.view.MyNetworkImageView
import jp.juggler.util.*
import jp.juggler.util.data.*
import jp.juggler.util.log.*
import jp.juggler.util.ui.*
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.textColor
import kotlin.math.max

private val log = LogCategory("ItemViewHolderShow")

@SuppressLint("ClickableViewAccessibility")
fun ItemViewHolder.bind(
    listAdapter: ItemListAdapter,
    column: Column,
    bSimpleList: Boolean,
    item: TimelineItem,
) {
    bindBenchmark.start()

    this.listAdapter = listAdapter
    this.column = column
    this.bSimpleList = bSimpleList

    this.accessInfo = column.accessInfo

    val fontBold = ActMain.timelineFontBold
    val fontNormal = ActMain.timelineFont
    viewRoot.scan { v ->
        try {
            when (v) {
                // ボタンは太字なので触らない
                is Button, is CountImageButton ->
                    if(v is Button && tvMediaDescriptions.contains(v)){
                        v.typeface = fontNormal
                    }

                is TextView -> v.typeface = when {
                    v === tvName ||
                            v === tvFollowerName ||
                            v === tvBoosted ||
                            v === tvReply ||
                            v === tvTrendTagCount ||
                            v === tvTrendTagName ||
                            v === tvConversationIconsMore ||
                            v === tvConversationParticipants ||
                            v === tvFilterPhrase -> fontBold

                    else -> fontNormal
                }
            }
        } catch (ex: Throwable) {
            log.e(ex, "can't change font.")
        }
    }

    if (bSimpleList) {

        viewRoot.setOnTouchListener { _, ev ->
            // ポップアップを閉じた時にクリックでリストを触ったことになってしまう不具合の回避
            val now = SystemClock.elapsedRealtime()
            // ポップアップを閉じた直後はタッチダウンを無視する
            if (now - StatusButtonsPopup.lastPopupClose >= 30L) {
                false
            } else {
                val action = ev.action
                ItemViewHolder.log.d("onTouchEvent action=$action")
                true
            }
        }

        viewRoot.setOnClickListener { viewClicked ->
            activity.closePopup()
            statusShowing?.let { status ->
                val popup =
                    StatusButtonsPopup(activity, column, true, this@bind)
                activity.popupStatusButtons = popup
                popup.show(
                    listAdapter.columnVh.listView,
                    viewClicked,
                    status,
                    item as? TootNotification
                )
            }
        }
        llButtonBar.visibility = View.GONE
        this.buttonsForStatus = null
    } else {
        viewRoot.isClickable = false
        llButtonBar.visibility = View.VISIBLE
        this.buttonsForStatus = StatusButtons(
            activity,
            column,
            false,
            statusButtonsViewHolder,
            this
        )
    }

    this.statusShowing = null
    this.statusReply = null
    this.statusAccount = null
    this.boostAccount = null
    this.followAccount = null
    this.boostTime = 0L
    this.viewRoot.setBackgroundColor(0)
    this.boostedAction = defaultBoostedAction

    btnGapHead.visibility = View.GONE
    btnGapTail.visibility = View.GONE
    flCardImage.visibility = View.GONE
    llBoosted.visibility = View.GONE
    llCardOuter.visibility = View.GONE
    llConversationIcons.visibility = View.GONE
    llFilter.visibility = View.GONE
    llFollow.visibility = View.GONE
    llFollowRequest.visibility = View.GONE
    llList.visibility = View.GONE
    llOpenSticker.visibility = View.GONE
    llReply.visibility = View.GONE
    llSearchTag.visibility = View.GONE
    llStatus.visibility = View.GONE
    llTrendTag.visibility = View.GONE
    tvCardText.visibility = View.GONE
    tvMediaCount.visibility = View.GONE
    tvMessageHolder.visibility = View.GONE

    tvMediaDescriptions.forEach { it.visibility = View.GONE }

    removeExtraView()

    val colorTextContent = column.getContentColor()
    this.colorTextContent = colorTextContent
    this.contentColorCsl = ColorStateList.valueOf(colorTextContent)

    tvApplication.setTextColor(colorTextContent)
    tvBoosted.setTextColor(colorTextContent)
    tvCardText.setTextColor(colorTextContent)
    tvContent.setTextColor(colorTextContent)
    tvContentWarning.setTextColor(colorTextContent)
    tvConversationIconsMore.setTextColor(colorTextContent)
    tvConversationParticipants.setTextColor(colorTextContent)
    tvFilterPhrase.setTextColor(colorTextContent)
    tvFollowerName.setTextColor(colorTextContent)
    tvMediaCount.setTextColor(colorTextContent)
    tvMentions.setTextColor(colorTextContent)
    tvMessageHolder.setTextColor(colorTextContent)
    tvName.setTextColor(colorTextContent)
    tvReply.setTextColor(colorTextContent)
    tvTrendTagCount.setTextColor(colorTextContent)
    tvTrendTagName.setTextColor(colorTextContent)

    tvMediaDescriptions.forEach { it.setTextColor(colorTextContent) }
    cvTagHistory.setColor(colorTextContent)

    //NSFWは文字色固定 btnShowMedia.setTextColor( colorTextContent );

    (llCardOuter.background as? PreviewCardBorder)?.color =
        colorPreviewCardBorder(colorTextContent)

    val colorTextAcct = column.getAcctColor()
    this.acctColor = colorTextAcct

    tvBoostedTime.setTextColor(colorTextAcct)
    tvFilterDetail.setTextColor(colorTextAcct)
    tvFilterPhrase.setTextColor(colorTextAcct)
    tvTime.setTextColor(colorTextAcct)
    tvTrendTagDesc.setTextColor(colorTextAcct)

    // 以下のビューの文字色はsetAcct() で設定される
    //		tvBoostedAcct.setTextColor(c)
    //		tvFollowerAcct.setTextColor(c)
    //		tvAcct.setTextColor(c)

    this.item = item
    when (item) {
        is TootStatus -> {
            val reblog = item.reblog
            when {
                reblog == null -> showStatusOrReply(item)

                item.isQuoteToot -> {
                    // 引用Renote
                    val colorBg = PrefI.ipEventBgColorBoost.value
                    showReply(item.account, reblog, R.drawable.ic_quote, R.string.quote_to)
                    showStatus(item, colorBg)
                }

                else -> {
                    // 引用なしブースト
                    val colorBg = PrefI.ipEventBgColorBoost.value
                    showBoost(
                        item.accountRef,
                        item.time_created_at,
                        R.drawable.ic_repeat,
                        R.string.display_name_boosted_by,
                        boostStatus = item
                    )
                    showStatusOrReply(reblog, colorBg)
                }
            }
        }

        is TootAccountRef -> showAccount(item)
        is TootNotification -> showNotification(item)
        is TootGap -> showGap()
        is TootSearchGap -> showSearchGap(item)
        is TootDomainBlock -> showDomainBlock(item)
        is TootList -> showList(item)
        is MisskeyAntenna -> showAntenna(item)
        is TootMessageHolder -> showMessageHolder(item)
        is TootTag -> showSearchTag(item)
        is TootFilter -> showFilter(item)
        is TootConversationSummary -> {
            showStatusOrReply(item.last_status)
            showConversationIcons(item)
        }

        is TootScheduled -> showScheduled(item)
    }
    bindBenchmark.report()
}

// プレビューカードの枠の色はテキストより薄め
private fun colorPreviewCardBorder(src: Int): Int {
    val rgb = src and 0xffffff
    val alpha = max(1, src ushr (24 + 1)) // 本来の値の半分にする
    return rgb or (alpha shl 24)
}

fun ItemViewHolder.removeExtraView() {
    llExtra.scan { v ->
        if (v is MyNetworkImageView) {
            v.cancelLoading()
        }
    }
    llExtra.removeAllViews()

    for (invalidator in extraInvalidatorList) {
        invalidator.register(null)
    }
    extraInvalidatorList.clear()
}

fun ItemViewHolder.showAccount(whoRef: TootAccountRef) {

    followAccount = whoRef
    val who = whoRef.get()
    llFollow.visibility = View.VISIBLE
    ivFollow.setImageUrl(
        calcIconRound(ivFollow.layoutParams),
        accessInfo.supplyBaseUrl(who.avatar_static),
        accessInfo.supplyBaseUrl(who.avatar)
    )

    followInvalidator.text = whoRef.decoded_display_name

    setAcct(tvFollowerAcct, accessInfo, who)

    who.setAccountExtra(
        accessInfo,
        lastActiveInvalidator,
        suggestionSource = if (column.type == ColumnType.FOLLOW_SUGGESTION) {
            SuggestionSource.get(accessInfo.db_id, who.acct)
        } else {
            null
        }
    )

    val relation = daoUserRelation.load(accessInfo.db_id, who.id)
    setFollowIcon(
        activity,
        btnFollow,
        ivFollowedBy,
        relation,
        who,
        colorTextContent,
        alphaMultiplier = stylerBoostAlpha
    )

    if (column.type == ColumnType.FOLLOW_REQUESTS) {
        llFollowRequest.visibility = View.VISIBLE
        btnFollowRequestAccept.imageTintList = contentColorCsl
        btnFollowRequestDeny.imageTintList = contentColorCsl
    }
}

fun ItemViewHolder.showAntenna(a: MisskeyAntenna) {
    llList.visibility = View.VISIBLE
    btnListTL.text = a.name
    btnListTL.textColor = colorTextContent
    btnListMore.imageTintList = contentColorCsl
}

fun ItemViewHolder.showBoost(
    whoRef: TootAccountRef,
    time: Long,
    iconId: Int,
    @StringRes stringId: Int,
    reaction: TootReaction? = null,
    boostStatus: TootStatus? = null,
    reblogVisibility: TootVisibility? = null,
) {
    boostAccount = whoRef

    val who = whoRef.get()

    setIconDrawableId(
        activity,
        ivBoosted,
        iconId,
        color = colorTextContent,
        alphaMultiplier = stylerBoostAlpha
    )

    ivBoostAvatar.let { v ->
        v.setImageUrl(
            calcIconRound(v.layoutParams),
            accessInfo.supplyBaseUrl(who.avatar_static),
            accessInfo.supplyBaseUrl(who.avatar)
        )
    }
    boostTime = time
    llBoosted.visibility = View.VISIBLE
    showStatusTime(
        activity,
        tvBoostedTime,
        who,
        time = time,
        status = boostStatus,
        reblogVisibility = reblogVisibility
    )
    setAcct(tvBoostedAcct, accessInfo, who)

    // フォローの場合 decoded_display_name が2箇所で表示に使われるのを避ける必要がある
    boostInvalidator.text = if (reaction != null) {
        val options = DecodeOptions(
            activity,
            accessInfo,
            decodeEmoji = true,
            enlargeEmoji = DecodeOptions.emojiScaleReaction,
            enlargeCustomEmoji = DecodeOptions.emojiScaleReaction,
            emojiSizeMode = accessInfo.emojiSizeMode(),
        )
        reaction.toSpannableStringBuilder(options, boostStatus).apply {
            append(" ")
            append(
                who.decodeDisplayNameCached(activity)
                    .intoStringResource(activity, stringId)
            )
        }
    } else {
        who.decodeDisplayNameCached(activity)
            .intoStringResource(activity, stringId)
    }
}

fun ItemViewHolder.showMessageHolder(item: TootMessageHolder) {
    tvMessageHolder.visibility = View.VISIBLE
    tvMessageHolder.text = item.text
    tvMessageHolder.gravity = item.gravity
}

fun ItemViewHolder.showList(list: TootList) {
    llList.visibility = View.VISIBLE
    btnListTL.text = list.title
    btnListTL.textColor = colorTextContent
    btnListMore.imageTintList = contentColorCsl
}

fun ItemViewHolder.showDomainBlock(domainBlock: TootDomainBlock) {
    llSearchTag.visibility = View.VISIBLE
    btnSearchTag.text = domainBlock.domain.pretty
}

fun ItemViewHolder.showFilter(filter: TootFilter) {
    llFilter.visibility = View.VISIBLE
    tvFilterPhrase.text = filter.displayString

    tvFilterDetail.text = StringBuffer().apply {
        val contextNames = filter.contextNames.joinToString("/") { activity.getString(it) }
        append(activity.getString(R.string.filter_context))
        append(": ")
        append(contextNames)

        val action = when (filter.hide) {
            true -> activity.getString(R.string.filter_action_hide)
            else -> activity.getString(R.string.filter_action_warn)
        }
        append('\n')
        append(activity.getString(R.string.filter_action))
        append(": ")
        append(action)

        if (filter.time_expires_at > 0L) {
            append('\n')
            append(activity.getString(R.string.filter_expires_at))
            append(": ")
            append(TootStatus.formatTime(activity, filter.time_expires_at, false))
        }
    }.toString()
}

fun ItemViewHolder.showSearchTag(tag: TootTag) {
    if (tag.history?.isNotEmpty() == true) {
        llTrendTag.visibility = View.VISIBLE

        tvTrendTagCount.text = "${tag.countDaily}(${tag.countWeekly})"
        cvTagHistory.setHistory(tag.history)
        when (tag.type) {
            TootTag.TagType.Link -> {
                tvTrendTagName.text = tag.url?.ellipsizeDot3(256)
                tvTrendTagDesc.text = tag.name + "\n" + tag.description
            }

            TootTag.TagType.Tag -> {
                tvTrendTagName.text = "#${tag.name.ellipsizeDot3(256)}"
                tvTrendTagDesc.text = listOf(
                    when (tag.following) {
                        true -> activity.getString(R.string.following)
                        else -> ""
                    },
                    activity.getString(R.string.people_talking, tag.accountDaily, tag.accountWeekly)
                ).filter { it.isNotEmpty() }.joinToString(" ")
            }
        }
    } else {
        llSearchTag.visibility = View.VISIBLE
        btnSearchTag.text = "#" + tag.name
    }
}

fun ItemViewHolder.showGap() {
    llSearchTag.visibility = View.VISIBLE
    btnSearchTag.text = activity.getString(R.string.read_gap)

    btnGapHead.vg(column.type.gapDirection(column, true))
        ?.imageTintList = contentColorCsl

    btnGapTail.vg(column.type.gapDirection(column, false))
        ?.imageTintList = contentColorCsl

    val c = PrefI.ipEventBgColorGap.value
    if (c != 0) this.viewRoot.backgroundColor = c
}

fun ItemViewHolder.showSearchGap(item: TootSearchGap) {
    llSearchTag.visibility = View.VISIBLE
    btnSearchTag.text = activity.getString(
        when (item.type) {
            TootSearchGap.SearchType.Hashtag -> R.string.read_more_hashtag
            TootSearchGap.SearchType.Account -> R.string.read_more_account
            TootSearchGap.SearchType.Status -> R.string.read_more_status
        }
    )
}

fun ItemViewHolder.showReply(
    // 返信した人
    replyer: TootAccount?,
    // 返信された人
    target: TootAccount?,
    iconId: Int,
    text: Spannable,
) {
    llReply.visibility = View.VISIBLE

    setIconDrawableId(
        activity,
        ivReply,
        iconId,
        color = colorTextContent,
        alphaMultiplier = stylerBoostAlpha
    )

    ivReplyAvatar.vg(target != null && target.avatar != replyer?.avatar)?.let { v ->
        v.setImageUrl(
            calcIconRound(v.layoutParams),
            accessInfo.supplyBaseUrl(target!!.avatar_static),
            accessInfo.supplyBaseUrl(target.avatar)
        )
    }

    replyInvalidator.text = text
}

fun ItemViewHolder.showReply(replyer: TootAccount?, reply: TootStatus, iconId: Int, stringId: Int) {
    statusReply = reply
    showReply(
        replyer = replyer,
        target = reply.accountRef.get(),
        iconId,
        reply.accountRef.decoded_display_name.intoStringResource(activity, stringId)
    )
}

fun ItemViewHolder.showReply(replyer: TootAccount?, reply: TootStatus, accountId: EntityId) {
    val name = if (accountId == reply.account.id) {
        // 自己レスなら
        daoAcctColor.getNicknameWithColor(accessInfo, reply.account)
    } else {
        val m = reply.mentions?.find { it.id == accountId }
        if (m != null) {
            daoAcctColor.getNicknameWithColor(accessInfo.getFullAcct(m.acct))
        } else {
            SpannableString("ID($accountId)")
        }
    }

    val text = name.intoStringResource(activity, R.string.reply_to)
    showReply(replyer = replyer, target = null, R.drawable.ic_reply, text)

    // tootsearchはreplyオブジェクトがなくin_reply_toだけが提供される場合があるが
    // tootsearchではどのタンスから読んだか分からないのでin_reply_toのIDも信用できない
}

fun ItemViewHolder.showStatusTime(
    activity: ActMain,
    tv: TextView,
    @Suppress("UNUSED_PARAMETER") who: TootAccount,
    status: TootStatus? = null,
    time: Long? = null,
    reblogVisibility: TootVisibility? = null,
) {
    val sb = SpannableStringBuilder()

    if (status != null) {

        if (status.account.isAdmin) {
            if (sb.isNotEmpty()) sb.append('\u200B')
            sb.appendColorShadeIcon(activity, R.drawable.ic_shield, "admin")
        }

        if (status.account.isPro) {
            if (sb.isNotEmpty()) sb.append('\u200B')
            sb.appendColorShadeIcon(activity, R.drawable.ic_authorized, "pro")
        }

        if (status.account.isCat) {
            if (sb.isNotEmpty()) sb.append('\u200B')
            sb.appendColorShadeIcon(activity, R.drawable.ic_cat, "cat")
        }

        // botマーク
        if (status.account.bot) {
            if (sb.isNotEmpty()) sb.append('\u200B')
            sb.appendColorShadeIcon(activity, R.drawable.ic_bot, "bot")
        }

        if (status.account.suspended) {
            if (sb.isNotEmpty()) sb.append('\u200B')
            sb.appendColorShadeIcon(activity, R.drawable.ic_delete, "suspended")
        }

        // mobileマーク
        if (status.viaMobile) {
            if (sb.isNotEmpty()) sb.append('\u200B')
            sb.appendColorShadeIcon(activity, R.drawable.ic_mobile, "mobile")
        }

        // ブックマーク済み
        if (status.bookmarked) {
            if (sb.isNotEmpty()) sb.append('\u200B')
            sb.appendColorShadeIcon(activity, R.drawable.ic_bookmark_added, "bookmarked")
        }

        // NSFWマーク
        if (status.hasMedia() && status.sensitive) {
            if (sb.isNotEmpty()) sb.append('\u200B')
            sb.appendColorShadeIcon(activity, R.drawable.ic_eye_off, "NSFW")
        }

        // visibility
        val visIconId = status.visibility.getVisibilityIconId(accessInfo.isMisskey)
        if (R.drawable.ic_public != visIconId) {
            if (sb.isNotEmpty()) sb.append('\u200B')
            sb.appendColorShadeIcon(
                activity,
                visIconId,
                status.visibility.getVisibilityString(accessInfo.isMisskey)
            )
        }

        // pinned
        if (status.pinned) {
            if (sb.isNotEmpty()) sb.append('\u200B')
            sb.appendColorShadeIcon(activity, R.drawable.ic_pin, "pinned")

            //				val start = sb.length
            //				sb.append("pinned")
            //				val end = sb.length
            //				val icon_id = Styler.getAttributeResourceId(activity, R.attr.ic_pin)
            //				sb.setSpan(
            //					EmojiImageSpan(activity, icon_id),
            //					start,
            //					end,
            //					Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            //				)
        }

        // unread
        if (status.conversationSummary?.unread == true) {
            if (sb.isNotEmpty()) sb.append('\u200B')

            sb.appendColorShadeIcon(
                activity,
                R.drawable.ic_unread,
                "unread",
                color = MyClickableSpan.defaultLinkColor
            )
        }

        if (status.isPromoted) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(activity.getString(R.string.promoted))
        }

        if (status.isFeatured) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(activity.getString(R.string.featured))
        }

        if (status.time_edited_at > 0L) {
            if (sb.isNotEmpty()) sb.append(' ')
            val start = sb.length
            sb.append(activity.getString(R.string.edited))
            val end = sb.length
            sb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    } else {
        reblogVisibility?.takeIf { it != TootVisibility.Unknown }?.let { visibility ->
            val visIconId = visibility.getVisibilityIconId(accessInfo.isMisskey)
            if (R.drawable.ic_public != visIconId) {
                if (sb.isNotEmpty()) sb.append('\u200B')
                sb.appendColorShadeIcon(
                    activity,
                    visIconId,
                    visibility.getVisibilityString(accessInfo.isMisskey)
                )
            }
        }
    }

    if (sb.isNotEmpty()) sb.append(' ')

    sb.append(
        (time ?: status?.time_created_at)?.let {
            TootStatus.formatTime(activity, it, column.canRelativeTime)
        } ?: "?"
    )

    tv.text = sb
}

fun ItemViewHolder.showStatusTimeScheduled(
    activity: ActMain,
    tv: TextView,
    item: TootScheduled,
) {
    val sb = SpannableStringBuilder()

    // NSFWマーク
    if (item.hasMedia() && item.sensitive) {
        if (sb.isNotEmpty()) sb.append('\u200B')
        sb.appendColorShadeIcon(activity, R.drawable.ic_eye_off, "NSFW")
    }

    // visibility
    val visIconId = item.visibility.getVisibilityIconId(accessInfo.isMisskey)
    if (R.drawable.ic_public != visIconId) {
        if (sb.isNotEmpty()) sb.append('\u200B')
        sb.appendColorShadeIcon(
            activity,
            visIconId,
            item.visibility.getVisibilityString(accessInfo.isMisskey)
        )
    }

    if (sb.isNotEmpty()) sb.append(' ')
    sb.append(TootStatus.formatTime(activity, item.timeScheduledAt, column.canRelativeTime))

    tv.text = sb
}

val Column.canRelativeTime
    get() = when (type) {
        ColumnType.CONVERSATION,
        ColumnType.CONVERSATION_WITH_REFERENCE,
        ColumnType.STATUS_HISTORY,
        -> false

        else -> true
    }

//	fun updateRelativeTime() {
//		val boost_time = this.boost_time
//		if(boost_time != 0L) {
//			tvBoostedTime.text = TootStatus.formatTime(tvBoostedTime.context, boost_time, true)
//		}
//		val status_showing = this.status_showing
//		if(status_showing != null) {
//			showStatusTime(activity, status_showing)
//		}
//	}

fun ItemViewHolder.showScheduled(item: TootScheduled) {
    try {

        llStatus.visibility = View.VISIBLE

        this.viewRoot.setBackgroundColor(0)

        showStatusTimeScheduled(activity, tvTime, item)

        val who = column.whoAccount!!.get()
        val whoRef = TootAccountRef.tootAccountRef(TootParser(activity, accessInfo), who)
        this.statusAccount = whoRef

        setAcct(tvAcct, accessInfo, who)

        nameInvalidator.text = whoRef.decoded_display_name

        ivAvatar.setImageUrl(
            calcIconRound(ivAvatar.layoutParams),
            accessInfo.supplyBaseUrl(who.avatar_static),
            accessInfo.supplyBaseUrl(who.avatar)
        )

        val content = SpannableString(item.text ?: "")

        tvMentions.visibility = View.GONE

        contentInvalidator.text = content

        tvContent.minLines = -1

        val decodedSpoilerText = SpannableString(item.spoilerText ?: "")
        when {
            decodedSpoilerText.isNotEmpty() -> {
                // 元データに含まれるContent Warning を使う
                llContentWarning.visibility = View.VISIBLE
                spoilerInvalidator.text = decodedSpoilerText
                val cwShown = daoContentWarning.isShown(item.uri, accessInfo.expandCw)
                setContentVisibility(cwShown)
            }

            else -> {
                // CWしない
                llContentWarning.visibility = View.GONE
                llContents.visibility = View.VISIBLE
            }
        }

        val mediaAttachments = item.mediaAttachments
        if (mediaAttachments?.isEmpty() != false) {
            flMedia.visibility = View.GONE
            llMedia.visibility = View.GONE
            btnShowMedia.visibility = View.GONE
        } else {
            flMedia.visibility = View.VISIBLE

            // hide sensitive media
            val defaultShown = when {
                column.hideMediaDefault -> false
                accessInfo.dontHideNsfw -> true
                else -> !item.sensitive
            }
            val isShown = daoMediaShown.isShown(item.uri, defaultShown)

            btnShowMedia.visibility = if (!isShown) View.VISIBLE else View.GONE
            llMedia.visibility = if (!isShown) View.GONE else View.VISIBLE
            repeat(ItemViewHolder.MEDIA_VIEW_COUNT) { idx ->
                setMedia(mediaAttachments, idx)
            }

            setIconDrawableId(
                activity,
                btnHideMedia,
                R.drawable.ic_close,
                color = colorTextContent,
                alphaMultiplier = stylerBoostAlpha
            )
        }

        buttonsForStatus?.hide()

        tvApplication.visibility = View.GONE
    } catch (ex: Throwable) {
        ItemViewHolder.log.w(ex, "showScheduled failed")
    }
    llSearchTag.visibility = View.VISIBLE
    btnSearchTag.text = activity.getString(R.string.scheduled_status) + " " +
            TootStatus.formatTime(activity, item.timeScheduledAt, true)
}

fun ItemViewHolder.showConversationIcons(cs: TootConversationSummary) {

    val lastAccountId = cs.last_status.account.id

    val accountsOther = cs.accounts.filter { it.get().id != lastAccountId }
    if (accountsOther.isNotEmpty()) {
        llConversationIcons.visibility = View.VISIBLE

        val size = accountsOther.size

        tvConversationParticipants.text = if (size <= 1) {
            activity.getString(R.string.conversation_to)
        } else {
            activity.getString(R.string.participants)
        }

        fun showIcon(iv: MyNetworkImageView, idx: Int) {
            val bShown = idx < size
            iv.visibility = if (bShown) View.VISIBLE else View.GONE
            if (!bShown) return

            val who = accountsOther[idx].get()
            iv.setImageUrl(
                calcIconRound(iv.layoutParams),
                accessInfo.supplyBaseUrl(who.avatar_static),
                accessInfo.supplyBaseUrl(who.avatar)
            )
        }
        showIcon(ivConversationIcon1, 0)
        showIcon(ivConversationIcon2, 1)
        showIcon(ivConversationIcon3, 2)
        showIcon(ivConversationIcon4, 3)

        tvConversationIconsMore.text = when {
            size <= 4 -> ""
            else -> activity.getString(R.string.participants_and_more)
        }
    }

    if (cs.last_status.in_reply_to_id != null) {
        llSearchTag.visibility = View.VISIBLE
        btnSearchTag.text = activity.getString(R.string.show_conversation)
    }
}

fun ItemViewHolder.setAcct(tv: TextView, accessInfo: SavedAccount, who: TootAccount) {
    val ac = daoAcctColor.load(accessInfo, who)
    tv.text = when {
        daoAcctColor.hasNickname(ac) -> ac.nickname
        PrefB.bpShortAcctLocalUser.value -> "@${who.acct.pretty}"
        else -> "@${ac.nickname}"
    }
    tv.textColor = ac.colorFg.notZero() ?: this.acctColor

    tv.setBackgroundColor(ac.colorBg) // may 0
    tv.setPaddingRelative(activity.acctPadLr, 0, activity.acctPadLr, 0)
}
