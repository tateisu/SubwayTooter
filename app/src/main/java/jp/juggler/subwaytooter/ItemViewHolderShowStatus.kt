package jp.juggler.subwaytooter

import android.text.SpannableStringBuilder
import android.view.View
import android.widget.ImageView
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.table.ContentWarning
import jp.juggler.subwaytooter.table.MediaShown
import jp.juggler.subwaytooter.util.OpenSticker
import jp.juggler.subwaytooter.view.MyNetworkImageView
import jp.juggler.util.*
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.textColor

fun ItemViewHolder.showStatusOrReply(item: TootStatus, colorBgArg: Int = 0) {
    var colorBg = colorBgArg
    val reply = item.reply
    val inReplyToId = item.in_reply_to_id
    val inReplyToAccountId = item.in_reply_to_account_id
    when {
        reply != null -> {
            showReply(reply, R.drawable.ic_reply, R.string.reply_to)
            if (colorBgArg == 0) colorBg = PrefI.ipEventBgColorMention(activity.pref)
        }

        inReplyToId != null && inReplyToAccountId != null -> {
            showReply(item, inReplyToAccountId)
            if (colorBgArg == 0) colorBg = PrefI.ipEventBgColorMention(activity.pref)
        }
    }
    showStatus(item, colorBg)
}

fun ItemViewHolder.showStatus(status: TootStatus, colorBg: Int = 0) {

    val filteredWord = status.filteredWord
    if (filteredWord != null) {
        showMessageHolder(
            TootMessageHolder(
                if (PrefB.bpShowFilteredWord(activity.pref)) {
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
            PrefI.ipConversationMainTootBgColor(activity.pref).notZero()
                ?: (activity.attrColor(R.attr.colorImageButtonAccent) and 0xffffff) or 0x20000000

        this.viewRoot.setBackgroundColor(conversationMainBgColor)
    } else {
        val c = colorBg.notZero()

            ?: when (status.bookmarked) {
                true -> PrefI.ipEventBgColorBookmark(App1.pref)
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
    ivAvatar.setImageUrl(
        activity.pref,
        Styler.calcIconRound(ivAvatar.layoutParams),
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
        (column.type == ColumnType.CONVERSATION || PrefB.bpShowAppName(activity.pref))
    ) {
        prepareSb().append(activity.getString(R.string.application_is, application.name ?: ""))
    }

    val language = status.language
    if (language != null &&
        (column.type == ColumnType.CONVERSATION || PrefB.bpShowLanguage(activity.pref))
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

    if (PrefB.bpDontCropMediaThumb(App1.pref)) {
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
