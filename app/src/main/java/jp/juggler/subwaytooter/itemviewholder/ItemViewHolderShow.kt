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
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.Styler
import jp.juggler.subwaytooter.actmain.closePopup
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.appendColorShadeIcon
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
import jp.juggler.subwaytooter.util.Benchmark
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.view.MyNetworkImageView
import jp.juggler.util.*
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.textColor
import kotlin.math.max

@SuppressLint("ClickableViewAccessibility")
fun ItemViewHolder.bind(
    listAdapter: ItemListAdapter,
    column: Column,
    bSimpleList: Boolean,
    item: TimelineItem,
) {
    val b = Benchmark(ItemViewHolder.log, "Item-bind", 40L)

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
                is CountImageButton -> {
                }
                // ボタンは太字なので触らない
                is Button -> {
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
            ItemViewHolder.log.trace(ex)
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
                    StatusButtonsPopup(activity, column, bSimpleList, this@bind)
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

    llOpenSticker.visibility = View.GONE
    llBoosted.visibility = View.GONE
    llReply.visibility = View.GONE
    llFollow.visibility = View.GONE
    llStatus.visibility = View.GONE
    llSearchTag.visibility = View.GONE
    btnGapHead.visibility = View.GONE
    btnGapTail.visibility = View.GONE
    llList.visibility = View.GONE
    llFollowRequest.visibility = View.GONE
    tvMessageHolder.visibility = View.GONE
    llTrendTag.visibility = View.GONE
    llFilter.visibility = View.GONE
    tvMediaDescription.visibility = View.GONE
    llCardOuter.visibility = View.GONE
    tvCardText.visibility = View.GONE
    flCardImage.visibility = View.GONE
    llConversationIcons.visibility = View.GONE

    removeExtraView()

    var c: Int
    c = column.getContentColor()
    this.contentColor = c
    this.contentColorCsl = ColorStateList.valueOf(c)

    tvBoosted.setTextColor(c)
    tvReply.setTextColor(c)
    tvFollowerName.setTextColor(c)
    tvName.setTextColor(c)
    tvMentions.setTextColor(c)
    tvContentWarning.setTextColor(c)
    tvContent.setTextColor(c)
    //NSFWは文字色固定 btnShowMedia.setTextColor( c );
    tvApplication.setTextColor(c)
    tvMessageHolder.setTextColor(c)
    tvTrendTagName.setTextColor(c)
    tvTrendTagCount.setTextColor(c)
    cvTagHistory.setColor(c)
    tvFilterPhrase.setTextColor(c)
    tvMediaDescription.setTextColor(c)
    tvCardText.setTextColor(c)
    tvConversationIconsMore.setTextColor(c)
    tvConversationParticipants.setTextColor(c)

    (llCardOuter.background as? PreviewCardBorder)?.let {
        val rgb = c and 0xffffff
        val alpha = max(1, c ushr (24 + 1)) // 本来の値の半分にする
        it.color = rgb or (alpha shl 24)
    }

    c = column.getAcctColor()
    this.acctColor = c
    tvBoostedTime.setTextColor(c)
    tvTime.setTextColor(c)
    tvTrendTagDesc.setTextColor(c)
    tvFilterDetail.setTextColor(c)
    tvFilterPhrase.setTextColor(c)

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
                    val colorBg = PrefI.ipEventBgColorBoost(activity.pref)
                    showReply(reblog, R.drawable.ic_repeat, R.string.quote_to)
                    showStatus(item, colorBg)
                }

                else -> {
                    // 引用なしブースト
                    val colorBg = PrefI.ipEventBgColorBoost(activity.pref)
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
    b.report()
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
        Styler.calcIconRound(ivFollow.layoutParams),
        accessInfo.supplyBaseUrl(who.avatar_static),
        accessInfo.supplyBaseUrl(who.avatar)
    )

    tvFollowerName.text = whoRef.decoded_display_name
    followInvalidator.register(whoRef.decoded_display_name)

    setAcct(tvFollowerAcct, accessInfo, who)

    who.setAccountExtra(
        accessInfo,
        tvLastStatusAt,
        lastActiveInvalidator,
        suggestionSource = if (column.type == ColumnType.FOLLOW_SUGGESTION) {
            SuggestionSource.get(accessInfo.db_id, who.acct)
        } else {
            null
        }
    )

    val relation = UserRelation.load(accessInfo.db_id, who.id)
    Styler.setFollowIcon(
        activity,
        btnFollow,
        ivFollowedBy,
        relation,
        who,
        contentColor,
        alphaMultiplier = Styler.boostAlpha
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
    btnListTL.textColor = contentColor
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

    setIconDrawableId(
        activity,
        ivBoosted,
        iconId,
        color = contentColor,
        alphaMultiplier = Styler.boostAlpha
    )

    val who = whoRef.get()

    // フォローの場合 decoded_display_name が2箇所で表示に使われるのを避ける必要がある
    val text: Spannable = if (reaction != null) {
        val options = DecodeOptions(
            activity,
            accessInfo,
            decodeEmoji = true,
            enlargeEmoji = 1.5f,
            enlargeCustomEmoji = 1.5f
        )
        val ssb = reaction.toSpannableStringBuilder(options, boostStatus)
        ssb.append(" ")
        ssb.append(
            who.decodeDisplayName(activity)
                .intoStringResource(activity, stringId)
        )
    } else {
        who.decodeDisplayName(activity)
            .intoStringResource(activity, stringId)
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
    tvBoosted.text = text
    boostInvalidator.register(text)
    setAcct(tvBoostedAcct, accessInfo, who)
}

fun ItemViewHolder.showMessageHolder(item: TootMessageHolder) {
    tvMessageHolder.visibility = View.VISIBLE
    tvMessageHolder.text = item.text
    tvMessageHolder.gravity = item.gravity
}

fun ItemViewHolder.showList(list: TootList) {
    llList.visibility = View.VISIBLE
    btnListTL.text = list.title
    btnListTL.textColor = contentColor
    btnListMore.imageTintList = contentColorCsl
}

fun ItemViewHolder.showDomainBlock(domainBlock: TootDomainBlock) {
    llSearchTag.visibility = View.VISIBLE
    btnSearchTag.text = domainBlock.domain.pretty
}

fun ItemViewHolder.showFilter(filter: TootFilter) {
    llFilter.visibility = View.VISIBLE
    tvFilterPhrase.text = filter.phrase

    val sb = StringBuffer()
    //
    sb.append(activity.getString(R.string.filter_context))
        .append(": ")
        .append(filter.getContextNames(activity).joinToString("/"))
    //
    val flags = ArrayList<String>()
    if (filter.irreversible) flags.add(activity.getString(R.string.filter_irreversible))
    if (filter.whole_word) flags.add(activity.getString(R.string.filter_word_match))
    if (flags.isNotEmpty()) {
        sb.append('\n')
            .append(flags.joinToString(", "))
    }
    //
    if (filter.time_expires_at != 0L) {
        sb.append('\n')
            .append(activity.getString(R.string.filter_expires_at))
            .append(": ")
            .append(TootStatus.formatTime(activity, filter.time_expires_at, false))
    }

    tvFilterDetail.text = sb.toString()
}

fun ItemViewHolder.showSearchTag(tag: TootTag) {
    if (tag.history?.isNotEmpty() == true) {
        llTrendTag.visibility = View.VISIBLE

        tvTrendTagCount.text = "${tag.countDaily}(${tag.countWeekly})"
        cvTagHistory.setHistory(tag.history)
        when (tag.type) {
            TootTag.TagType.TrendLink -> {
                tvTrendTagName.text = tag.url?.ellipsizeDot3(256)
                tvTrendTagDesc.text = tag.name + "\n" + tag.description
            }
            else -> {
                tvTrendTagName.text = "#${tag.name.ellipsizeDot3(256)}"
                tvTrendTagDesc.text =
                    activity.getString(R.string.people_talking, tag.accountDaily, tag.accountWeekly)
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

    val c = PrefI.ipEventBgColorGap()
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

fun ItemViewHolder.showReply(iconId: Int, text: Spannable) {

    llReply.visibility = View.VISIBLE

    setIconDrawableId(
        activity,
        ivReply,
        iconId,
        color = contentColor,
        alphaMultiplier = Styler.boostAlpha
    )

    tvReply.text = text
    replyInvalidator.register(text)
}

fun ItemViewHolder.showReply(reply: TootStatus, iconId: Int, stringId: Int) {
    statusReply = reply
    showReply(
        iconId,
        reply.accountRef.decoded_display_name.intoStringResource(activity, stringId)
    )
}

fun ItemViewHolder.showReply(reply: TootStatus, accountId: EntityId) {
    val name = if (accountId == reply.account.id) {
        // 自己レスなら
        AcctColor.getNicknameWithColor(accessInfo, reply.account)
    } else {
        val m = reply.mentions?.find { it.id == accountId }
        if (m != null) {
            AcctColor.getNicknameWithColor(accessInfo.getFullAcct(m.acct))
        } else {
            SpannableString("ID($accountId)")
        }
    }

    val text = name.intoStringResource(activity, R.string.reply_to)
    showReply(R.drawable.ic_reply, text)

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

        // mobileマーク
        if (status.bookmarked) {
            if (sb.isNotEmpty()) sb.append('\u200B')
            sb.appendColorShadeIcon(activity, R.drawable.ic_bookmark, "bookmarked")
        }

        // NSFWマーク
        if (status.hasMedia() && status.sensitive) {
            if (sb.isNotEmpty()) sb.append('\u200B')
            sb.appendColorShadeIcon(activity, R.drawable.ic_eye_off, "NSFW")
        }

        // visibility
        val visIconId =
            Styler.getVisibilityIconId(accessInfo.isMisskey, status.visibility)
        if (R.drawable.ic_public != visIconId) {
            if (sb.isNotEmpty()) sb.append('\u200B')
            sb.appendColorShadeIcon(
                activity,
                visIconId,
                Styler.getVisibilityString(
                    activity,
                    accessInfo.isMisskey,
                    status.visibility
                )
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
            val visIconId = Styler.getVisibilityIconId(accessInfo.isMisskey, visibility)
            if (R.drawable.ic_public != visIconId) {
                if (sb.isNotEmpty()) sb.append('\u200B')
                sb.appendColorShadeIcon(
                    activity,
                    visIconId,
                    Styler.getVisibilityString(
                        activity,
                        accessInfo.isMisskey,
                        visibility
                    )
                )
            }
        }
    }

    if (sb.isNotEmpty()) sb.append(' ')
    sb.append(
        when {
            time != null -> TootStatus.formatTime(
                activity,
                time,
                when (column.type) {
                    ColumnType.CONVERSATION, ColumnType.STATUS_HISTORY -> false
                    else -> true
                }
            )
            status != null -> TootStatus.formatTime(
                activity,
                status.time_created_at,
                when (column.type) {
                    ColumnType.CONVERSATION, ColumnType.STATUS_HISTORY -> false
                    else -> true
                }
            )
            else -> "?"
        }
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
    val visIconId =
        Styler.getVisibilityIconId(accessInfo.isMisskey, item.visibility)
    if (R.drawable.ic_public != visIconId) {
        if (sb.isNotEmpty()) sb.append('\u200B')
        sb.appendColorShadeIcon(
            activity,
            visIconId,
            Styler.getVisibilityString(
                activity,
                accessInfo.isMisskey,
                item.visibility
            )
        )
    }

    if (sb.isNotEmpty()) sb.append(' ')
    sb.append(
        TootStatus.formatTime(
            activity,
            item.timeScheduledAt,
            column.type != ColumnType.CONVERSATION
        )
    )

    tv.text = sb
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
        val whoRef = TootAccountRef(TootParser(activity, accessInfo), who)
        this.statusAccount = whoRef

        setAcct(tvAcct, accessInfo, who)

        tvName.text = whoRef.decoded_display_name
        nameInvalidator.register(whoRef.decoded_display_name)
        ivAvatar.setImageUrl(
            Styler.calcIconRound(ivAvatar.layoutParams),
            accessInfo.supplyBaseUrl(who.avatar_static),
            accessInfo.supplyBaseUrl(who.avatar)
        )

        val content = SpannableString(item.text ?: "")

        tvMentions.visibility = View.GONE

        tvContent.text = content
        contentInvalidator.register(content)

        tvContent.minLines = -1

        val decodedSpoilerText = SpannableString(item.spoilerText ?: "")
        when {
            decodedSpoilerText.isNotEmpty() -> {
                // 元データに含まれるContent Warning を使う
                llContentWarning.visibility = View.VISIBLE
                tvContentWarning.text = decodedSpoilerText
                spoilerInvalidator.register(decodedSpoilerText)
                val cwShown = ContentWarning.isShown(item.uri, accessInfo.expand_cw)
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
                accessInfo.dont_hide_nsfw -> true
                else -> !item.sensitive
            }
            val isShown = MediaShown.isShown(item.uri, defaultShown)

            btnShowMedia.visibility = if (!isShown) View.VISIBLE else View.GONE
            llMedia.visibility = if (!isShown) View.GONE else View.VISIBLE
            val sb = StringBuilder()
            setMedia(mediaAttachments, sb, ivMedia1, 0)
            setMedia(mediaAttachments, sb, ivMedia2, 1)
            setMedia(mediaAttachments, sb, ivMedia3, 2)
            setMedia(mediaAttachments, sb, ivMedia4, 3)
            if (sb.isNotEmpty()) {
                tvMediaDescription.visibility = View.VISIBLE
                tvMediaDescription.text = sb
            }

            setIconDrawableId(
                activity,
                btnHideMedia,
                R.drawable.ic_close,
                color = contentColor,
                alphaMultiplier = Styler.boostAlpha
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
                Styler.calcIconRound(iv.layoutParams),
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
    val ac = AcctColor.load(accessInfo, who)
    tv.text = when {
        AcctColor.hasNickname(ac) -> ac.nickname
        PrefB.bpShortAcctLocalUser() -> "@${who.acct.pretty}"
        else -> "@${ac.nickname}"
    }
    tv.textColor = ac.color_fg.notZero() ?: this.acctColor

    tv.setBackgroundColor(ac.color_bg) // may 0
    tv.setPaddingRelative(activity.acctPadLr, 0, activity.acctPadLr, 0)
}
