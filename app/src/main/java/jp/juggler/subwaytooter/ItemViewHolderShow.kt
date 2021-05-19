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
    list_adapter: ItemListAdapter,
    column: Column,
    bSimpleList: Boolean,
    item: TimelineItem
) {
    val b = Benchmark(ItemViewHolder.log, "Item-bind", 40L)

    this.list_adapter = list_adapter
    this.column = column
    this.bSimpleList = bSimpleList

    this.access_info = column.access_info

    val font_bold = ActMain.timeline_font_bold
    val font_normal = ActMain.timeline_font
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
                        v === tvFilterPhrase -> font_bold
                    else -> font_normal
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
            if (now - StatusButtonsPopup.last_popup_close >= 30L) {
                false
            } else {
                val action = ev.action
                ItemViewHolder.log.d("onTouchEvent action=$action")
                true
            }
        }

        viewRoot.setOnClickListener { viewClicked ->
            activity.closeListItemPopup()
            status_showing?.let { status ->
                val popup =
                    StatusButtonsPopup(activity, column, bSimpleList, this@bind)
                activity.listItemPopup = popup
                popup.show(
                    list_adapter.columnVh.listView,
                    viewClicked,
                    status,
                    item as? TootNotification
                )
            }
        }
        llButtonBar.visibility = View.GONE
        this.buttons_for_status = null
    } else {
        viewRoot.isClickable = false
        llButtonBar.visibility = View.VISIBLE
        this.buttons_for_status = StatusButtons(
            activity,
            column,
            false,
            statusButtonsViewHolder,
            this
        )
    }

    this.status_showing = null
    this.status_reply = null
    this.status_account = null
    this.boost_account = null
    this.follow_account = null
    this.boost_time = 0L
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
    this.content_color = c
    this.content_color_csl = ColorStateList.valueOf(c)

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
    this.acct_color = c
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
                        boost_status = item
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

    for (invalidator in extra_invalidator_list) {
        invalidator.register(null)
    }
    extra_invalidator_list.clear()
}

fun ItemViewHolder.showAccount(whoRef: TootAccountRef) {

    follow_account = whoRef
    val who = whoRef.get()
    llFollow.visibility = View.VISIBLE
    ivFollow.setImageUrl(
        activity.pref,
        Styler.calcIconRound(ivFollow.layoutParams),
        access_info.supplyBaseUrl(who.avatar_static),
        access_info.supplyBaseUrl(who.avatar)
    )

    tvFollowerName.text = whoRef.decoded_display_name
    follow_invalidator.register(whoRef.decoded_display_name)

    setAcct(tvFollowerAcct, access_info, who)

    who.setAccountExtra(access_info, tvLastStatusAt, lastActive_invalidator)

    val relation = UserRelation.load(access_info.db_id, who.id)
    Styler.setFollowIcon(
        activity,
        btnFollow,
        ivFollowedBy,
        relation,
        who,
        content_color,
        alphaMultiplier = Styler.boost_alpha
    )

    if (column.type == ColumnType.FOLLOW_REQUESTS) {
        llFollowRequest.visibility = View.VISIBLE
        btnFollowRequestAccept.imageTintList = content_color_csl
        btnFollowRequestDeny.imageTintList = content_color_csl
    }
}

fun ItemViewHolder.showAntenna(a: MisskeyAntenna) {
    llList.visibility = View.VISIBLE
    btnListTL.text = a.name
    btnListTL.textColor = content_color
    btnListMore.imageTintList = content_color_csl
}

fun ItemViewHolder.showBoost(
    whoRef: TootAccountRef,
    time: Long,
    iconId: Int,
    string_id: Int,
    reaction: TootReaction? = null,
    boost_status: TootStatus? = null
) {
    boost_account = whoRef

    setIconDrawableId(
        activity,
        ivBoosted,
        iconId,
        color = content_color,
        alphaMultiplier = Styler.boost_alpha
    )

    val who = whoRef.get()

    // フォローの場合 decoded_display_name が2箇所で表示に使われるのを避ける必要がある
    val text: Spannable = if (reaction != null) {
        val options = DecodeOptions(
            activity,
            access_info,
            decodeEmoji = true,
            enlargeEmoji = 1.5f,
            enlargeCustomEmoji = 1.5f
        )
        val ssb = reaction.toSpannableStringBuilder(options, boost_status)
        ssb.append(" ")
        ssb.append(
            who.decodeDisplayName(activity)
                .intoStringResource(activity, string_id)
        )
    } else {
        who.decodeDisplayName(activity)
            .intoStringResource(activity, string_id)
    }

    boost_time = time
    llBoosted.visibility = View.VISIBLE
    showStatusTime(activity, tvBoostedTime, who, time = time, status = boost_status)
    tvBoosted.text = text
    boost_invalidator.register(text)
    setAcct(tvBoostedAcct, access_info, who)
}

fun ItemViewHolder.showStatusOrReply(item: TootStatus, colorBgArg: Int = 0) {
    var colorBg = colorBgArg
    val reply = item.reply
    val in_reply_to_id = item.in_reply_to_id
    val in_reply_to_account_id = item.in_reply_to_account_id
    when {
        reply != null -> {
            showReply(reply, R.drawable.ic_reply, R.string.reply_to)
            if (colorBgArg == 0) colorBg = Pref.ipEventBgColorMention(activity.pref)
        }

        in_reply_to_id != null && in_reply_to_account_id != null -> {
            showReply(item, in_reply_to_account_id)
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
    val n_status = n.status
    val n_accountRef = n.accountRef
    val n_account = n_accountRef?.get()

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
            if (n_account != null) showBoost(
                n_accountRef,
                n.time_created_at,
                if (access_info.isNicoru(n_account)) R.drawable.ic_nicoru else R.drawable.ic_star,
                R.string.display_name_favourited_by
            )
            if (n_status != null) {
                showNotificationStatus(n_status, colorBg)
            }
        }

        TootNotification.TYPE_REBLOG -> {
            val colorBg = Pref.ipEventBgColorBoost(activity.pref)
            if (n_account != null) showBoost(
                n_accountRef,
                n.time_created_at,
                R.drawable.ic_repeat,
                R.string.display_name_boosted_by,
                boost_status = n_status
            )
            if (n_status != null) {
                showNotificationStatus(n_status, colorBg)
            }

        }

        TootNotification.TYPE_RENOTE -> {
            // 引用のないreblog
            val colorBg = Pref.ipEventBgColorBoost(activity.pref)
            if (n_account != null) showBoost(
                n_accountRef,
                n.time_created_at,
                R.drawable.ic_repeat,
                R.string.display_name_boosted_by,
                boost_status = n_status
            )
            if (n_status != null) {
                showNotificationStatus(n_status, colorBg)
            }
        }

        TootNotification.TYPE_FOLLOW -> {
            val colorBg = Pref.ipEventBgColorFollow(activity.pref)
            if (n_account != null) {
                showBoost(
                    n_accountRef,
                    n.time_created_at,
                    R.drawable.ic_follow_plus,
                    R.string.display_name_followed_by
                )
                showAccount(n_accountRef)
                if (colorBg != 0) this.viewRoot.backgroundColor = colorBg
            }
        }

        TootNotification.TYPE_UNFOLLOW -> {
            val colorBg = Pref.ipEventBgColorUnfollow(activity.pref)
            if (n_account != null) {
                showBoost(
                    n_accountRef,
                    n.time_created_at,
                    R.drawable.ic_follow_cross,
                    R.string.display_name_unfollowed_by
                )
                showAccount(n_accountRef)
                if (colorBg != 0) this.viewRoot.backgroundColor = colorBg
            }
        }

        TootNotification.TYPE_MENTION,
        TootNotification.TYPE_REPLY -> {
            val colorBg = Pref.ipEventBgColorMention(activity.pref)
            if (!bSimpleList && !access_info.isMisskey) {
                when {
                    n_account == null -> {

                    }

                    n_status?.in_reply_to_id != null || n_status?.reply != null -> {
                        // トゥート内部に「～への返信」を表示するので、
                        // 通知イベントの「～からの返信」は表示しない
                    }

                    else -> // 返信ではなくメンションの場合は「～からの返信」を表示する
                        showBoost(
                            n_accountRef,
                            n.time_created_at,
                            R.drawable.ic_reply,
                            R.string.display_name_mentioned_by
                        )
                }
            }
            if (n_status != null) {
                showNotificationStatus(n_status, colorBg)
            }
        }

        TootNotification.TYPE_EMOJI_REACTION,
        TootNotification.TYPE_REACTION -> {
            val colorBg = Pref.ipEventBgColorReaction(activity.pref)
            if (n_account != null) showBoost(
                n_accountRef,
                n.time_created_at,
                R.drawable.ic_face,
                R.string.display_name_reaction_by,
                reaction = n.reaction ?: TootReaction.UNKNOWN,
                boost_status = n_status
            )
            if (n_status != null) {
                showNotificationStatus(n_status, colorBg)
            }
        }

        TootNotification.TYPE_QUOTE -> {
            val colorBg = Pref.ipEventBgColorQuote(activity.pref)
            if (n_account != null) showBoost(
                n_accountRef,
                n.time_created_at,
                R.drawable.ic_repeat,
                R.string.display_name_quoted_by
            )
            if (n_status != null) {
                showNotificationStatus(n_status, colorBg)
            }
        }

        TootNotification.TYPE_STATUS -> {
            val colorBg = Pref.ipEventBgColorStatus(activity.pref)
            if (n_account != null) showBoost(
                n_accountRef,
                n.time_created_at,
                if (n_status == null) {
                    R.drawable.ic_question
                } else {
                    Styler.getVisibilityIconId(access_info.isMisskey, n_status.visibility)
                },
                R.string.display_name_posted_by
            )
            if (n_status != null) {
                showNotificationStatus(n_status, colorBg)
            }
        }

        TootNotification.TYPE_FOLLOW_REQUEST,
        TootNotification.TYPE_FOLLOW_REQUEST_MISSKEY -> {
            val colorBg = Pref.ipEventBgColorFollowRequest(activity.pref)
            if (n_account != null) {
                showBoost(
                    n_accountRef,
                    n.time_created_at,
                    R.drawable.ic_follow_wait,
                    R.string.display_name_follow_request_by
                )
                if (colorBg != 0) this.viewRoot.backgroundColor = colorBg
                boostedAction = {
                    activity.addColumn(
                        activity.nextPosition(column), access_info, ColumnType.FOLLOW_REQUESTS
                    )
                }
            }
        }

        TootNotification.TYPE_FOLLOW_REQUEST_ACCEPTED_MISSKEY -> {
            val colorBg = Pref.ipEventBgColorFollow(activity.pref)
            if (n_account != null) {
                showBoost(
                    n_accountRef,
                    n.time_created_at,
                    R.drawable.ic_follow_plus,
                    R.string.display_name_follow_request_accepted_by
                )
                showAccount(n_accountRef)
                if (colorBg != 0) this.viewRoot.backgroundColor = colorBg
            }
        }

        TootNotification.TYPE_VOTE,
        TootNotification.TYPE_POLL_VOTE_MISSKEY -> {
            val colorBg = Pref.ipEventBgColorVote(activity.pref)
            if (n_account != null) showBoost(
                n_accountRef,
                n.time_created_at,
                R.drawable.ic_vote,
                R.string.display_name_voted_by
            )
            if (n_status != null) {
                showNotificationStatus(n_status, colorBg)
            }
        }

        TootNotification.TYPE_POLL -> {
            val colorBg = 0
            if (n_account != null) showBoost(
                n_accountRef,
                n.time_created_at,
                R.drawable.ic_vote,
                R.string.end_of_polling_from
            )
            if (n_status != null) {
                showNotificationStatus(n_status, colorBg)
            }
        }

        else -> {
            val colorBg = 0
            if (n_account != null) showBoost(
                n_accountRef,
                n.time_created_at,
                R.drawable.ic_question,
                R.string.unknown_notification_from
            )
            if (n_status != null) {
                showNotificationStatus(n_status, colorBg)
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
    btnListTL.textColor = content_color
    btnListMore.imageTintList = content_color_csl
}


fun ItemViewHolder.showDomainBlock(domain_block: TootDomainBlock) {
    llSearchTag.visibility = View.VISIBLE
    btnSearchTag.text = domain_block.domain.pretty
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
        ?.imageTintList = content_color_csl

    btnGapTail.vg(column.type.gapDirection(column, false))
        ?.imageTintList = content_color_csl

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
        color = content_color,
        alphaMultiplier = Styler.boost_alpha
    )

    tvReply.text = text
    reply_invalidator.register(text)
}

fun ItemViewHolder.showReply(reply: TootStatus, iconId: Int, stringId: Int) {
    status_reply = reply
    showReply(
        iconId,
        reply.accountRef.decoded_display_name.intoStringResource(activity, stringId)
    )
}

fun ItemViewHolder.showReply(reply: TootStatus, accountId: EntityId) {
    val name = if (accountId == reply.account.id) {
        // 自己レスなら
        AcctColor.getNicknameWithColor(access_info, reply.account)
    } else {
        val m = reply.mentions?.find { it.id == accountId }
        if (m != null) {
            AcctColor.getNicknameWithColor(access_info.getFullAcct(m.acct))
        } else {
            SpannableString("ID(${accountId})")
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

    this.status_showing = status
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

            ?: when (status.getBackgroundColorType(access_info)) {
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
    this.status_account = whoRef

    setAcct(tvAcct, access_info, who)

    //		if(who == null) {
    //			tvName.text = "?"
    //			name_invalidator.register(null)
    //			ivThumbnail.setImageUrl(activity.pref, 16f, null, null)
    //		} else {
    tvName.text = whoRef.decoded_display_name
    name_invalidator.register(whoRef.decoded_display_name)
    ivThumbnail.setImageUrl(
        activity.pref,
        Styler.calcIconRound(ivThumbnail.layoutParams),
        access_info.supplyBaseUrl(who.avatar_static),
        access_info.supplyBaseUrl(who.avatar)
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
    content_invalidator.register(content)

    activity.checkAutoCW(status, content)
    val r = status.auto_cw

    tvContent.minLines = r?.originalLineCount ?: -1

    val decoded_spoiler_text = status.decoded_spoiler_text
    when {
        decoded_spoiler_text.isNotEmpty() -> {
            // 元データに含まれるContent Warning を使う
            llContentWarning.visibility = View.VISIBLE
            tvContentWarning.text = status.decoded_spoiler_text
            spoiler_invalidator.register(status.decoded_spoiler_text)
            val cw_shown = ContentWarning.isShown(status, access_info.expand_cw)
            showContent(cw_shown)
        }

        r?.decoded_spoiler_text != null -> {
            // 自動CW
            llContentWarning.visibility = View.VISIBLE
            tvContentWarning.text = r.decoded_spoiler_text
            spoiler_invalidator.register(r.decoded_spoiler_text)
            val cw_shown = ContentWarning.isShown(status, access_info.expand_cw)
            showContent(cw_shown)
        }

        else -> {
            // CWしない
            llContentWarning.visibility = View.GONE
            llContents.visibility = View.VISIBLE
        }
    }

    val media_attachments = status.media_attachments
    if (media_attachments == null || media_attachments.isEmpty()) {
        flMedia.visibility = View.GONE
        llMedia.visibility = View.GONE
        btnShowMedia.visibility = View.GONE
    } else {
        flMedia.visibility = View.VISIBLE

        // hide sensitive media
        val default_shown = when {
            column.hide_media_default -> false
            access_info.dont_hide_nsfw -> true
            else -> !status.sensitive
        }
        val is_shown = MediaShown.isShown(status, default_shown)

        btnShowMedia.visibility = if (!is_shown) View.VISIBLE else View.GONE
        llMedia.visibility = if (!is_shown) View.GONE else View.VISIBLE
        val sb = StringBuilder()
        setMedia(media_attachments, sb, ivMedia1, 0)
        setMedia(media_attachments, sb, ivMedia2, 1)
        setMedia(media_attachments, sb, ivMedia3, 2)
        setMedia(media_attachments, sb, ivMedia4, 3)

        val m0 =
            if (media_attachments.isEmpty()) null else media_attachments[0] as? TootAttachment
        btnShowMedia.blurhash = m0?.blurhash

        if (sb.isNotEmpty()) {
            tvMediaDescription.visibility = View.VISIBLE
            tvMediaDescription.text = sb
        }

        setIconDrawableId(
            activity,
            btnHideMedia,
            R.drawable.ic_close,
            color = content_color,
            alphaMultiplier = Styler.boost_alpha
        )
    }

    makeReactionsView(status)

    buttons_for_status?.bind(status, (item as? TootNotification))

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
        when (column.type) {
            ColumnType.LOCAL, ColumnType.LOCAL_AROUND -> {
                if (host == access_info.apDomain) return
            }

            else -> {

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
    time: Long? = null
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
            Styler.getVisibilityIconId(access_info.isMisskey, status.visibility)
        if (R.drawable.ic_public != visIconId) {
            if (sb.isNotEmpty()) sb.append('\u200B')
            sb.appendColorShadeIcon(
                activity,
                visIconId,
                Styler.getVisibilityString(
                    activity,
                    access_info.isMisskey,
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
    item: TootScheduled
) {
    val sb = SpannableStringBuilder()

    // NSFWマーク
    if (item.hasMedia() && item.sensitive) {
        if (sb.isNotEmpty()) sb.append('\u200B')
        sb.appendColorShadeIcon(activity, R.drawable.ic_eye_off, "NSFW")
    }

    // visibility
    val visIconId =
        Styler.getVisibilityIconId(access_info.isMisskey, item.visibility)
    if (R.drawable.ic_public != visIconId) {
        if (sb.isNotEmpty()) sb.append('\u200B')
        sb.appendColorShadeIcon(
            activity,
            visIconId,
            Styler.getVisibilityString(
                activity,
                access_info.isMisskey,
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

        val who = column.who_account!!.get()
        val whoRef = TootAccountRef(TootParser(activity, access_info), who)
        this.status_account = whoRef

        setAcct(tvAcct, access_info, who)

        tvName.text = whoRef.decoded_display_name
        name_invalidator.register(whoRef.decoded_display_name)
        ivThumbnail.setImageUrl(
            activity.pref,
            Styler.calcIconRound(ivThumbnail.layoutParams),
            access_info.supplyBaseUrl(who.avatar_static),
            access_info.supplyBaseUrl(who.avatar)
        )

        val content = SpannableString(item.text ?: "")

        tvMentions.visibility = View.GONE

        tvContent.text = content
        content_invalidator.register(content)

        tvContent.minLines = -1

        val decoded_spoiler_text = SpannableString(item.spoiler_text ?: "")
        when {
            decoded_spoiler_text.isNotEmpty() -> {
                // 元データに含まれるContent Warning を使う
                llContentWarning.visibility = View.VISIBLE
                tvContentWarning.text = decoded_spoiler_text
                spoiler_invalidator.register(decoded_spoiler_text)
                val cw_shown = ContentWarning.isShown(item.uri, access_info.expand_cw)
                showContent(cw_shown)
            }

            else -> {
                // CWしない
                llContentWarning.visibility = View.GONE
                llContents.visibility = View.VISIBLE
            }
        }

        val media_attachments = item.media_attachments
        if (media_attachments?.isEmpty() != false) {
            flMedia.visibility = View.GONE
            llMedia.visibility = View.GONE
            btnShowMedia.visibility = View.GONE
        } else {
            flMedia.visibility = View.VISIBLE

            // hide sensitive media
            val default_shown = when {
                column.hide_media_default -> false
                access_info.dont_hide_nsfw -> true
                else -> !item.sensitive
            }
            val is_shown = MediaShown.isShown(item.uri, default_shown)

            btnShowMedia.visibility = if (!is_shown) View.VISIBLE else View.GONE
            llMedia.visibility = if (!is_shown) View.GONE else View.VISIBLE
            val sb = StringBuilder()
            setMedia(media_attachments, sb, ivMedia1, 0)
            setMedia(media_attachments, sb, ivMedia2, 1)
            setMedia(media_attachments, sb, ivMedia3, 2)
            setMedia(media_attachments, sb, ivMedia4, 3)
            if (sb.isNotEmpty()) {
                tvMediaDescription.visibility = View.VISIBLE
                tvMediaDescription.text = sb
            }

            setIconDrawableId(
                activity,
                btnHideMedia,
                R.drawable.ic_close,
                color = content_color,
                alphaMultiplier = Styler.boost_alpha
            )
        }

        buttons_for_status?.hide()

        tvApplication.visibility = View.GONE

    } catch (ex: Throwable) {

    }
    llSearchTag.visibility = View.VISIBLE
    btnSearchTag.text = activity.getString(R.string.scheduled_status) + " " +
        TootStatus.formatTime(
            activity,
            item.timeScheduledAt,
            true
        )
}

fun ItemViewHolder.showContent(shown: Boolean) {
    llContents.visibility = if (shown) View.VISIBLE else View.GONE
    btnContentWarning.setText(if (shown) R.string.hide else R.string.show)
    status_showing?.let { status ->
        val r = status.auto_cw
        tvContent.minLines = r?.originalLineCount ?: -1
        if (r?.decoded_spoiler_text != null) {
            // 自動CWの場合はContentWarningのテキストを切り替える
            tvContentWarning.text =
                if (shown) activity.getString(R.string.auto_cw_prefix) else r.decoded_spoiler_text
        }
    }
}

fun ItemViewHolder.showConversationIcons(cs: TootConversationSummary) {

    val last_account_id = cs.last_status.account.id

    val accountsOther = cs.accounts.filter { it.get().id != last_account_id }
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
                access_info.supplyBaseUrl(who.avatar_static),
                access_info.supplyBaseUrl(who.avatar)
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
    tv.textColor = ac.color_fg.notZero() ?: this.acct_color

    tv.setBackgroundColor(ac.color_bg) // may 0
    tv.setPaddingRelative(activity.acct_pad_lr, 0, activity.acct_pad_lr, 0)

}


fun ItemViewHolder.setMedia(
    media_attachments: ArrayList<TootAttachmentLike>,
    sbDesc: StringBuilder,
    iv: MyNetworkImageView,
    idx: Int
) {
    val ta = if (idx < media_attachments.size) media_attachments[idx] else null
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
                    access_info.supplyBaseUrl(urlThumbnail),
                    access_info.supplyBaseUrl(urlThumbnail)
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
