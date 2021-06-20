package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.drawable.PreviewCardBorder
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.table.*
import jp.juggler.subwaytooter.util.Benchmark
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.OpenSticker
import jp.juggler.subwaytooter.view.CountImageButton
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

    val fontBold = ActMain.timeline_font_bold
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
            activity.closeListItemPopup()
            statusShowing?.let { status ->
                val popup =
                    StatusButtonsPopup(activity, column, bSimpleList, this@bind)
                activity.listItemPopup = popup
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
                    val colorBg = Pref.ipEventBgColorBoost(activity.pref)
                    showReply(reblog, R.drawable.ic_repeat, R.string.quote_to)
                    showStatus(item, colorBg)
                }

                else -> {
                    // 引用なしブースト
                    val colorBg = Pref.ipEventBgColorBoost(activity.pref)
                    showBoost(
                        item.accountRef,
                        item.time_created_at,
                        R.drawable.ic_repeat,
                        R.string.display_name_boosted_by,
                        boostStatus = item
                    )
                    showStatusOrReply(item.reblog, colorBg)
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

        is TootScheduled -> {
            showScheduled(item)
        }

        else -> {
        }
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
        activity.pref,
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
    showStatusTime(activity, tvBoostedTime, who, time = time, status = boostStatus)
    tvBoosted.text = text
    boostInvalidator.register(text)
    setAcct(tvBoostedAcct, accessInfo, who)
}

fun ItemViewHolder.showStatusOrReply(item: TootStatus, colorBgArg: Int = 0) {
    var colorBg = colorBgArg
    val reply = item.reply
    val inReplyToId = item.in_reply_to_id
    val inReplyToAccountId = item.in_reply_to_account_id
    when {
        reply != null -> {
            showReply(reply, R.drawable.ic_reply, R.string.reply_to)
            if (colorBgArg == 0) colorBg = Pref.ipEventBgColorMention(activity.pref)
        }

        inReplyToId != null && inReplyToAccountId != null -> {
            showReply(item, inReplyToAccountId)
            if (colorBgArg == 0) colorBg = Pref.ipEventBgColorMention(activity.pref)
        }
    }
    showStatus(item, colorBg)
}

fun ItemViewHolder.showMessageHolder(item: TootMessageHolder) {
    tvMessageHolder.visibility = View.VISIBLE
    tvMessageHolder.text = item.text
    tvMessageHolder.gravity = item.gravity
}

fun ItemViewHolder.showNotification(n: TootNotification) {
    val nStatus = n.status
    val nAccountRef = n.accountRef
    val nAccount = nAccountRef?.get()

    fun showNotificationStatus(item: TootStatus, colorBgDefault: Int) {
        val reblog = item.reblog
        when {
            reblog == null -> showStatusOrReply(item, colorBgDefault)

            item.isQuoteToot -> {
                // 引用Renote
                showReply(reblog, R.drawable.ic_repeat, R.string.quote_to)
                showStatus(item, Pref.ipEventBgColorQuote(activity.pref))
            }

            else -> {
                // 通常のブースト。引用なしブースト。
                // ブースト表示は通知イベントと被るのでしない
                showStatusOrReply(reblog, Pref.ipEventBgColorBoost(activity.pref))
            }
        }
    }

    when (n.type) {

        TootNotification.TYPE_FAVOURITE -> {
            val colorBg = Pref.ipEventBgColorFavourite(activity.pref)
            if (nAccount != null) showBoost(
                nAccountRef,
                n.time_created_at,
                if (accessInfo.isNicoru(nAccount)) R.drawable.ic_nicoru else R.drawable.ic_star,
                R.string.display_name_favourited_by
            )
            if (nStatus != null) {
                showNotificationStatus(nStatus, colorBg)
            }
        }

        TootNotification.TYPE_REBLOG -> {
            val colorBg = Pref.ipEventBgColorBoost(activity.pref)
            if (nAccount != null) showBoost(
                nAccountRef,
                n.time_created_at,
                R.drawable.ic_repeat,
                R.string.display_name_boosted_by,
                boostStatus = nStatus
            )
            if (nStatus != null) {
                showNotificationStatus(nStatus, colorBg)
            }
        }

        TootNotification.TYPE_RENOTE -> {
            // 引用のないreblog
            val colorBg = Pref.ipEventBgColorBoost(activity.pref)
            if (nAccount != null) showBoost(
                nAccountRef,
                n.time_created_at,
                R.drawable.ic_repeat,
                R.string.display_name_boosted_by,
                boostStatus = nStatus
            )
            if (nStatus != null) {
                showNotificationStatus(nStatus, colorBg)
            }
        }

        TootNotification.TYPE_FOLLOW -> {
            val colorBg = Pref.ipEventBgColorFollow(activity.pref)
            if (nAccount != null) {
                showBoost(
                    nAccountRef,
                    n.time_created_at,
                    R.drawable.ic_follow_plus,
                    R.string.display_name_followed_by
                )
                showAccount(nAccountRef)
                if (colorBg != 0) this.viewRoot.backgroundColor = colorBg
            }
        }

        TootNotification.TYPE_UNFOLLOW -> {
            val colorBg = Pref.ipEventBgColorUnfollow(activity.pref)
            if (nAccount != null) {
                showBoost(
                    nAccountRef,
                    n.time_created_at,
                    R.drawable.ic_follow_cross,
                    R.string.display_name_unfollowed_by
                )
                showAccount(nAccountRef)
                if (colorBg != 0) this.viewRoot.backgroundColor = colorBg
            }
        }

        TootNotification.TYPE_MENTION,
        TootNotification.TYPE_REPLY,
        -> {
            val colorBg = Pref.ipEventBgColorMention(activity.pref)
            if (!bSimpleList && !accessInfo.isMisskey) {
                when {
                    nAccount == null -> {
                        //
                    }

                    nStatus?.in_reply_to_id != null || nStatus?.reply != null -> {
                        // トゥート内部に「～への返信」を表示するので、
                        // 通知イベントの「～からの返信」は表示しない
                    }

                    else -> // 返信ではなくメンションの場合は「～からの返信」を表示する
                        showBoost(
                            nAccountRef,
                            n.time_created_at,
                            R.drawable.ic_reply,
                            R.string.display_name_mentioned_by
                        )
                }
            }
            if (nStatus != null) {
                showNotificationStatus(nStatus, colorBg)
            }
        }

        TootNotification.TYPE_EMOJI_REACTION_PLEROMA,
        TootNotification.TYPE_EMOJI_REACTION,
        TootNotification.TYPE_REACTION,
        -> {
            val colorBg = Pref.ipEventBgColorReaction(activity.pref)
            if (nAccount != null) showBoost(
                nAccountRef,
                n.time_created_at,
                R.drawable.ic_face,
                R.string.display_name_reaction_by,
                reaction = n.reaction ?: TootReaction.UNKNOWN,
                boostStatus = nStatus
            )
            if (nStatus != null) {
                showNotificationStatus(nStatus, colorBg)
            }
        }

        TootNotification.TYPE_QUOTE -> {
            val colorBg = Pref.ipEventBgColorQuote(activity.pref)
            if (nAccount != null) showBoost(
                nAccountRef,
                n.time_created_at,
                R.drawable.ic_repeat,
                R.string.display_name_quoted_by
            )
            if (nStatus != null) {
                showNotificationStatus(nStatus, colorBg)
            }
        }

        TootNotification.TYPE_STATUS -> {
            val colorBg = Pref.ipEventBgColorStatus(activity.pref)
            if (nAccount != null) showBoost(
                nAccountRef,
                n.time_created_at,
                if (nStatus == null) {
                    R.drawable.ic_question
                } else {
                    Styler.getVisibilityIconId(accessInfo.isMisskey, nStatus.visibility)
                },
                R.string.display_name_posted_by
            )
            if (nStatus != null) {
                showNotificationStatus(nStatus, colorBg)
            }
        }

        TootNotification.TYPE_FOLLOW_REQUEST,
        TootNotification.TYPE_FOLLOW_REQUEST_MISSKEY,
        -> {
            val colorBg = Pref.ipEventBgColorFollowRequest(activity.pref)
            if (nAccount != null) {
                showBoost(
                    nAccountRef,
                    n.time_created_at,
                    R.drawable.ic_follow_wait,
                    R.string.display_name_follow_request_by
                )
                if (colorBg != 0) this.viewRoot.backgroundColor = colorBg
                boostedAction = {
                    activity.addColumn(
                        activity.nextPosition(column), accessInfo, ColumnType.FOLLOW_REQUESTS
                    )
                }
            }
        }

        TootNotification.TYPE_FOLLOW_REQUEST_ACCEPTED_MISSKEY -> {
            val colorBg = Pref.ipEventBgColorFollow(activity.pref)
            if (nAccount != null) {
                showBoost(
                    nAccountRef,
                    n.time_created_at,
                    R.drawable.ic_follow_plus,
                    R.string.display_name_follow_request_accepted_by
                )
                showAccount(nAccountRef)
                if (colorBg != 0) this.viewRoot.backgroundColor = colorBg
            }
        }

        TootNotification.TYPE_VOTE,
        TootNotification.TYPE_POLL_VOTE_MISSKEY,
        -> {
            val colorBg = Pref.ipEventBgColorVote(activity.pref)
            if (nAccount != null) showBoost(
                nAccountRef,
                n.time_created_at,
                R.drawable.ic_vote,
                R.string.display_name_voted_by
            )
            if (nStatus != null) {
                showNotificationStatus(nStatus, colorBg)
            }
        }

        TootNotification.TYPE_POLL -> {
            val colorBg = 0
            if (nAccount != null) showBoost(
                nAccountRef,
                n.time_created_at,
                R.drawable.ic_vote,
                R.string.end_of_polling_from
            )
            if (nStatus != null) {
                showNotificationStatus(nStatus, colorBg)
            }
        }

        else -> {
            val colorBg = 0
            if (nAccount != null) showBoost(
                nAccountRef,
                n.time_created_at,
                R.drawable.ic_question,
                R.string.unknown_notification_from
            )
            if (nStatus != null) {
                showNotificationStatus(nStatus, colorBg)
            }
            tvMessageHolder.visibility = View.VISIBLE
            tvMessageHolder.text = "notification type is ${n.type}"
            tvMessageHolder.gravity = Gravity.CENTER
        }
    }
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
        tvTrendTagName.text = "#${tag.name}"
        tvTrendTagDesc.text =
            activity.getString(R.string.people_talking, tag.accountDaily, tag.accountWeekly)
        tvTrendTagCount.text = "${tag.countDaily}(${tag.countWeekly})"
        cvTagHistory.setHistory(tag.history)
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

    val c = Pref.ipEventBgColorGap(App1.pref)
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

fun ItemViewHolder.showStatus(status: TootStatus, colorBg: Int = 0) {

    val filteredWord = status.filteredWord
    if (filteredWord != null) {
        showMessageHolder(
            TootMessageHolder(
                if (Pref.bpShowFilteredWord(activity.pref)) {
                    "${activity.getString(R.string.filtered)} / $filteredWord"
                } else {
                    activity.getString(R.string.filtered)
                }
            )
        )
        return
    }

    this.statusShowing = status
    llStatus.visibility = View.VISIBLE

    if (status.conversation_main) {

        val conversationMainBgColor =
            Pref.ipConversationMainTootBgColor(activity.pref).notZero()
                ?: (activity.attrColor(R.attr.colorImageButtonAccent) and 0xffffff) or 0x20000000

        this.viewRoot.setBackgroundColor(conversationMainBgColor)
    } else {
        val c = colorBg.notZero()

            ?: when (status.bookmarked) {
                true -> Pref.ipEventBgColorBookmark(App1.pref)
                false -> 0
            }.notZero()

            ?: when (status.getBackgroundColorType(accessInfo)) {
                TootVisibility.UnlistedHome -> ItemViewHolder.toot_color_unlisted
                TootVisibility.PrivateFollowers -> ItemViewHolder.toot_color_follower
                TootVisibility.DirectSpecified -> ItemViewHolder.toot_color_direct_user
                TootVisibility.DirectPrivate -> ItemViewHolder.toot_color_direct_me
                // TODO add color setting for limited?
                TootVisibility.Limited -> ItemViewHolder.toot_color_follower
                else -> 0
            }

        if (c != 0) {
            this.viewRoot.backgroundColor = c
        }
    }

    showStatusTime(activity, tvTime, who = status.account, status = status)

    val whoRef = status.accountRef
    val who = whoRef.get()
    this.statusAccount = whoRef

    setAcct(tvAcct, accessInfo, who)

    //		if(who == null) {
    //			tvName.text = "?"
    //			name_invalidator.register(null)
    //			ivThumbnail.setImageUrl(activity.pref, 16f, null, null)
    //		} else {
    tvName.text = whoRef.decoded_display_name
    nameInvalidator.register(whoRef.decoded_display_name)
    ivThumbnail.setImageUrl(
        activity.pref,
        Styler.calcIconRound(ivThumbnail.layoutParams),
        accessInfo.supplyBaseUrl(who.avatar_static),
        accessInfo.supplyBaseUrl(who.avatar)
    )
    //		}

    showOpenSticker(who)

    var content = status.decoded_content

    // ニコフレのアンケートの表示
    val enquete = status.enquete
    when {
        enquete == null -> {
        }

        enquete.pollType == TootPollsType.FriendsNico && enquete.type != TootPolls.TYPE_ENQUETE -> {
            // フレニコの投票の結果表示は普通にテキストを表示するだけでよい
        }

        else -> {

            // アンケートの本文を上書きする
            val question = enquete.decoded_question
            if (question.isNotBlank()) content = question

            showEnqueteItems(status, enquete)
        }
    }

    showPreviewCard(status)

    //			if( status.decoded_tags == null ){
    //				tvTags.setVisibility( View.GONE );
    //			}else{
    //				tvTags.setVisibility( View.VISIBLE );
    //				tvTags.setText( status.decoded_tags );
    //			}

    if (status.decoded_mentions.isEmpty()) {
        tvMentions.visibility = View.GONE
    } else {
        tvMentions.visibility = View.VISIBLE
        tvMentions.text = status.decoded_mentions
    }

    if (status.time_deleted_at > 0L) {
        val s = SpannableStringBuilder()
            .append('(')
            .append(
                activity.getString(
                    R.string.deleted_at,
                    TootStatus.formatTime(activity, status.time_deleted_at, true)
                )
            )
            .append(')')
        content = s
    }

    tvContent.text = content
    contentInvalidator.register(content)

    activity.checkAutoCW(status, content)
    val r = status.auto_cw

    tvContent.minLines = r?.originalLineCount ?: -1

    val decodedSpoilerText = status.decoded_spoiler_text
    when {
        decodedSpoilerText.isNotEmpty() -> {
            // 元データに含まれるContent Warning を使う
            llContentWarning.visibility = View.VISIBLE
            tvContentWarning.text = status.decoded_spoiler_text
            spoilerInvalidator.register(status.decoded_spoiler_text)
            val cwShown = ContentWarning.isShown(status, accessInfo.expand_cw)
            showContent(cwShown)
        }

        r?.decodedSpoilerText != null -> {
            // 自動CW
            llContentWarning.visibility = View.VISIBLE
            tvContentWarning.text = r.decodedSpoilerText
            spoilerInvalidator.register(r.decodedSpoilerText)
            val cwShown = ContentWarning.isShown(status, accessInfo.expand_cw)
            showContent(cwShown)
        }

        else -> {
            // CWしない
            llContentWarning.visibility = View.GONE
            llContents.visibility = View.VISIBLE
        }
    }

    val mediaAttachments = status.media_attachments
    if (mediaAttachments == null || mediaAttachments.isEmpty()) {
        flMedia.visibility = View.GONE
        llMedia.visibility = View.GONE
        btnShowMedia.visibility = View.GONE
    } else {
        flMedia.visibility = View.VISIBLE

        // hide sensitive media
        val defaultShown = when {
            column.hideMediaDefault -> false
            accessInfo.dont_hide_nsfw -> true
            else -> !status.sensitive
        }
        val isShown = MediaShown.isShown(status, defaultShown)

        btnShowMedia.visibility = if (!isShown) View.VISIBLE else View.GONE
        llMedia.visibility = if (!isShown) View.GONE else View.VISIBLE
        val sb = StringBuilder()
        setMedia(mediaAttachments, sb, ivMedia1, 0)
        setMedia(mediaAttachments, sb, ivMedia2, 1)
        setMedia(mediaAttachments, sb, ivMedia3, 2)
        setMedia(mediaAttachments, sb, ivMedia4, 3)

        val m0 =
            if (mediaAttachments.isEmpty()) null else mediaAttachments[0] as? TootAttachment
        btnShowMedia.blurhash = m0?.blurhash

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

    makeReactionsView(status)

    buttonsForStatus?.bind(status, (item as? TootNotification))

    var sb: StringBuilder? = null

    fun prepareSb(): StringBuilder =
        sb?.append(", ") ?: StringBuilder().also { sb = it }

    val application = status.application
    if (application != null &&
        (column.type == ColumnType.CONVERSATION || Pref.bpShowAppName(activity.pref))
    ) {
        prepareSb().append(activity.getString(R.string.application_is, application.name ?: ""))
    }

    val language = status.language
    if (language != null &&
        (column.type == ColumnType.CONVERSATION || Pref.bpShowLanguage(activity.pref))
    ) {
        prepareSb().append(activity.getString(R.string.language_is, language))
    }

    tvApplication.vg(sb != null)?.text = sb
}

fun ItemViewHolder.showOpenSticker(who: TootAccount) {
    try {
        if (!Column.showOpenSticker) return

        val host = who.apDomain

        // LTLでホスト名が同じならTickerを表示しない
        @Suppress("NON_EXHAUSTIVE_WHEN")
        when (column.type) {
            ColumnType.LOCAL, ColumnType.LOCAL_AROUND -> {
                if (host == accessInfo.apDomain) return
            }
        }

        val item = OpenSticker.lastList[host.ascii] ?: return

        tvOpenSticker.text = item.name
        tvOpenSticker.textColor = item.fontColor

        val density = activity.density

        val lp = ivOpenSticker.layoutParams
        lp.height = (density * 16f + 0.5f).toInt()
        lp.width = (density * item.imageWidth + 0.5f).toInt()

        ivOpenSticker.layoutParams = lp
        ivOpenSticker.setImageUrl(activity.pref, 0f, item.favicon)
        val colorBg = item.bgColor
        when (colorBg.size) {
            1 -> {
                val c = colorBg.first()
                tvOpenSticker.setBackgroundColor(c)
                ivOpenSticker.setBackgroundColor(c)
            }

            else -> {
                ivOpenSticker.setBackgroundColor(colorBg.last())
                tvOpenSticker.background = colorBg.getGradation()
            }
        }
        llOpenSticker.visibility = View.VISIBLE
        llOpenSticker.requestLayout()
    } catch (ex: Throwable) {
        ItemViewHolder.log.trace(ex)
    }
}

fun ItemViewHolder.showStatusTime(
    activity: ActMain,
    tv: TextView,
    @Suppress("UNUSED_PARAMETER") who: TootAccount,
    status: TootStatus? = null,
    time: Long? = null,
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
    }

    if (sb.isNotEmpty()) sb.append(' ')
    sb.append(
        when {
            time != null -> TootStatus.formatTime(
                activity,
                time,
                column.type != ColumnType.CONVERSATION
            )
            status != null -> TootStatus.formatTime(
                activity,
                status.time_created_at,
                column.type != ColumnType.CONVERSATION
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
        ivThumbnail.setImageUrl(
            activity.pref,
            Styler.calcIconRound(ivThumbnail.layoutParams),
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
                showContent(cwShown)
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

fun ItemViewHolder.showContent(shown: Boolean) {
    llContents.visibility = if (shown) View.VISIBLE else View.GONE
    btnContentWarning.setText(if (shown) R.string.hide else R.string.show)
    statusShowing?.let { status ->
        val r = status.auto_cw
        tvContent.minLines = r?.originalLineCount ?: -1
        if (r?.decodedSpoilerText != null) {
            // 自動CWの場合はContentWarningのテキストを切り替える
            tvContentWarning.text =
                if (shown) activity.getString(R.string.auto_cw_prefix) else r.decodedSpoilerText
        }
    }
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
                activity.pref,
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
        Pref.bpShortAcctLocalUser(App1.pref) -> "@${who.acct.pretty}"
        else -> "@${ac.nickname}"
    }
    tv.textColor = ac.color_fg.notZero() ?: this.acctColor

    tv.setBackgroundColor(ac.color_bg) // may 0
    tv.setPaddingRelative(activity.acctPadLr, 0, activity.acctPadLr, 0)
}

fun ItemViewHolder.setMedia(
    mediaAttachments: ArrayList<TootAttachmentLike>,
    sbDesc: StringBuilder,
    iv: MyNetworkImageView,
    idx: Int,
) {
    val ta = if (idx < mediaAttachments.size) mediaAttachments[idx] else null
    if (ta == null) {
        iv.visibility = View.GONE
        return
    }

    iv.visibility = View.VISIBLE

    iv.setFocusPoint(ta.focusX, ta.focusY)

    if (Pref.bpDontCropMediaThumb(App1.pref)) {
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
    } else {
        iv.setScaleTypeForMedia()
    }

    val showUrl: Boolean

    when (ta.type) {
        TootAttachmentType.Audio -> {
            iv.setMediaType(0)
            iv.setDefaultImage(Styler.defaultColorIcon(activity, R.drawable.wide_music))
            iv.setImageUrl(activity.pref, 0f, ta.urlForThumbnail(activity.pref))
            showUrl = true
        }

        TootAttachmentType.Unknown -> {
            iv.setMediaType(0)
            iv.setDefaultImage(Styler.defaultColorIcon(activity, R.drawable.wide_question))
            iv.setImageUrl(activity.pref, 0f, null)
            showUrl = true
        }

        else -> when (val urlThumbnail = ta.urlForThumbnail(activity.pref)) {
            null, "" -> {
                iv.setMediaType(0)
                iv.setDefaultImage(Styler.defaultColorIcon(activity, R.drawable.wide_question))
                iv.setImageUrl(activity.pref, 0f, null)
                showUrl = true
            }

            else -> {
                iv.setMediaType(
                    when (ta.type) {
                        TootAttachmentType.Video -> R.drawable.media_type_video
                        TootAttachmentType.GIFV -> R.drawable.media_type_gifv
                        else -> 0
                    }
                )
                iv.setDefaultImage(null)
                iv.setImageUrl(
                    activity.pref,
                    0f,
                    accessInfo.supplyBaseUrl(urlThumbnail),
                    accessInfo.supplyBaseUrl(urlThumbnail)
                )
                showUrl = false
            }
        }
    }

    fun appendDescription(s: String) {
        //			val lp = LinearLayout.LayoutParams(
        //				LinearLayout.LayoutParams.MATCH_PARENT,
        //				LinearLayout.LayoutParams.WRAP_CONTENT
        //			)
        //			lp.topMargin = (0.5f + activity.density * 3f).toInt()
        //
        //			val tv = MyTextView(activity)
        //			tv.layoutParams = lp
        //			//
        //			tv.movementMethod = MyLinkMovementMethod
        //			if(! activity.timeline_font_size_sp.isNaN()) {
        //				tv.textSize = activity.timeline_font_size_sp
        //			}
        //			tv.setTextColor(content_color)

        if (sbDesc.isNotEmpty()) sbDesc.append("\n")
        val desc = activity.getString(R.string.media_description, idx + 1, s)
        sbDesc.append(desc)
    }

    when (val description = ta.description.notEmpty()) {
        null -> if (showUrl) ta.urlForDescription.notEmpty()?.let { appendDescription(it) }
        else -> appendDescription(description)
    }
}
