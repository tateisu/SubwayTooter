package jp.juggler.subwaytooter.itemviewholder

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.view.View
import android.widget.ImageView
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.actmain.checkAutoCW
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootAttachment
import jp.juggler.subwaytooter.api.entity.TootAttachmentLike
import jp.juggler.subwaytooter.api.entity.TootAttachmentType
import jp.juggler.subwaytooter.api.entity.TootMessageHolder
import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.api.entity.TootPolls
import jp.juggler.subwaytooter.api.entity.TootPollsType
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.api.entity.TootVisibility
import jp.juggler.subwaytooter.calcIconRound
import jp.juggler.subwaytooter.column.Column
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.column.isConversation
import jp.juggler.subwaytooter.defaultColorIcon
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.pref.PrefI
import jp.juggler.subwaytooter.stylerBoostAlpha
import jp.juggler.subwaytooter.table.daoContentWarning
import jp.juggler.subwaytooter.table.daoMediaShown
import jp.juggler.subwaytooter.util.OpenSticker
import jp.juggler.util.data.cast
import jp.juggler.util.data.ellipsizeDot3
import jp.juggler.util.data.notBlank
import jp.juggler.util.data.notEmpty
import jp.juggler.util.data.notZero
import jp.juggler.util.log.LogCategory
import jp.juggler.util.ui.attrColor
import jp.juggler.util.ui.setIconDrawableId
import jp.juggler.util.ui.textOrGone
import jp.juggler.util.ui.vg
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.imageResource
import org.jetbrains.anko.textColor

private val log = LogCategory("ItemViewHolderShowStatus")

fun ItemViewHolder.showStatusOrReply(
    item: TootStatus,
    colorBgArg: Int = 0,
    fadeText: Boolean = false,
) {
    var colorBg = colorBgArg
    val reply = item.reply
    val inReplyToId = item.in_reply_to_id
    val inReplyToAccountId = item.in_reply_to_account_id
    when {
        reply != null -> {
            showReply(item.account, reply, R.drawable.ic_reply, R.string.reply_to)
            if (colorBgArg == 0) colorBg = PrefI.ipEventBgColorMention.value
        }

        inReplyToId != null && inReplyToAccountId != null -> {
            showReply(null, item, inReplyToAccountId)
            if (colorBgArg == 0) colorBg = PrefI.ipEventBgColorMention.value
        }
    }
    showStatus(item, colorBg, fadeText = fadeText)
}

fun ItemViewHolder.showStatus(
    status: TootStatus,
    colorBg: Int = 0,
    fadeText: Boolean = false,
) {

    val filteredWord = status.filteredWord
    if (filteredWord != null) {
        PrefI.ipEventBgColorFiltered.value.notZero()?.let {
            viewRoot.backgroundColor = it
        }
        val text = StringBuilder().apply {
            append(activity.getString(R.string.filtered))
            if (PrefB.bpShowFilteredWord.value) {
                append(" / $filteredWord")
            }
            if (PrefB.bpShowUsernameFilteredPost.value) {
                val s = status.reblog ?: status
                append(" / ${s.account.display_name} @${s.account.acct}")
            }
        }.toString()
        showMessageHolder(TootMessageHolder(text))
        return
    }

    this.statusShowing = status
    llStatus.visibility = View.VISIBLE

    if (status.conversation_main) {
        PrefI.ipConversationMainTootBgColor.value.notZero()
            ?: activity.attrColor(R.attr.colorConversationMainTootBg)
    } else {
        colorBg.notZero() ?: when (status.bookmarked) {
            true -> PrefI.ipEventBgColorBookmark.value
            false -> 0
        }.notZero() ?: when (status.getBackgroundColorType(accessInfo)) {
            TootVisibility.UnlistedHome -> ItemViewHolder.toot_color_unlisted
            TootVisibility.PrivateFollowers -> ItemViewHolder.toot_color_follower
            TootVisibility.DirectSpecified -> ItemViewHolder.toot_color_direct_user
            TootVisibility.DirectPrivate -> ItemViewHolder.toot_color_direct_me
            // TODO add color setting for limited?
            TootVisibility.Limited -> ItemViewHolder.toot_color_follower
            else -> 0
        }.notZero()
    }?.let { viewRoot.backgroundColor = it }

    showStatusTime(activity, tvTime, who = status.account, status = status)

    val whoRef = status.accountRef
    val who = whoRef.get()
    this.statusAccount = whoRef

    setAcct(tvAcct, accessInfo, who)

    nameInvalidator.text = whoRef.decoded_display_name

    ivAvatar.setImageUrl(
        calcIconRound(ivAvatar.layoutParams),
        accessInfo.supplyBaseUrl(who.avatar_static),
        accessInfo.supplyBaseUrl(who.avatar)
    )

    showOpenSticker(who)

    val modifiedContent = if (status.time_deleted_at > 0L) {
        SpannableStringBuilder()
            .append('(')
            .append(
                activity.getString(
                    R.string.deleted_at,
                    TootStatus.formatTime(activity, status.time_deleted_at, true)
                )
            )
            .append(')')
    } else {
        showPoll(status) ?: status.decoded_content
    }

    //			if( status.decoded_tags == null ){
    //				tvTags.setVisibility( View.GONE );
    //			}else{
    //				tvTags.setVisibility( View.VISIBLE );
    //				tvTags.setText( status.decoded_tags );
    //			}

    val fadeAlpha = ActMain.eventFadeAlpha
    if (fadeAlpha < 1f) {
        val a = if (fadeText) fadeAlpha else 1f
        tvMentions.alpha = a
        tvContentWarning.alpha = a
        tvContent.alpha = a
        tvApplication.alpha = a
        tvCardText.alpha = a
    }

    tvMentions.textOrGone = status.decoded_mentions

    contentInvalidator.text = modifiedContent

    activity.checkAutoCW(status, modifiedContent)
    val r = status.auto_cw
    tvContent.minLines = r?.originalLineCount ?: -1

    showPreviewCard(status)
    showSpoilerTextAndContent(status)
    showAttachments(status)
    makeReactionsView(status)
    buttonsForStatus?.bind(status, (item as? TootNotification))
    showApplicationAndLanguage(status)
}

// 投票の表示
// returns modified decoded_content or null
private fun ItemViewHolder.showPoll(status: TootStatus): Spannable? {
    val enquete = status.enquete
    return when {
        enquete == null -> null

        // フレニコの投票の結果表示は普通にテキストを表示するだけでよい
        enquete.pollType == TootPollsType.FriendsNico && enquete.type != TootPolls.TYPE_ENQUETE -> null

        else -> {
            showEnqueteItems(status, enquete)
            // アンケートの本文を使ってcontentを上書きする
            enquete.decoded_question.notBlank()
        }
    }
}

private fun ItemViewHolder.showSpoilerTextAndContent(status: TootStatus) {
    val r = status.auto_cw
    val decodedSpoilerText = status.decoded_spoiler_text
    val autoCwText = r?.decodedSpoilerText
    when {
        decodedSpoilerText.isNotEmpty() -> {
            // 元データに含まれるContent Warning を使う
            llContentWarning.visibility = View.VISIBLE
            spoilerInvalidator.text = status.decoded_spoiler_text
            val cwShown = daoContentWarning.isShown(status, accessInfo.expandCw)
            setContentVisibility(cwShown)
        }

        autoCwText != null -> {
            // 自動CW
            llContentWarning.visibility = View.VISIBLE
            spoilerInvalidator.text = autoCwText
            val cwShown = daoContentWarning.isShown(status, accessInfo.expandCw)
            setContentVisibility(cwShown)
        }

        else -> {
            // CWしない
            llContentWarning.visibility = View.GONE
            llContents.visibility = View.VISIBLE
        }
    }
}

// 予約投稿でも使う
fun ItemViewHolder.setContentVisibility(shown: Boolean) {
    llContents.visibility = if (shown) View.VISIBLE else View.GONE
    btnContentWarning.contentDescription =
        activity.getString(if (shown) R.string.hide else R.string.show)
    btnContentWarning.imageResource =
        if (shown) R.drawable.outline_compress_24 else R.drawable.outline_expand_24

    statusShowing?.let { status ->
        val r = status.auto_cw
        tvContent.minLines = r?.originalLineCount ?: -1
        val autoCwText = r?.decodedSpoilerText
        if (autoCwText != null) {
            // 自動CWの場合はContentWarningのテキストを切り替える
            spoilerInvalidator.text =
                if (shown) activity.getString(R.string.auto_cw_prefix) else autoCwText
        }
    }
}

private fun ItemViewHolder.showApplicationAndLanguage(status: TootStatus) {

    var sb: StringBuilder? = null
    fun prepareSb(): StringBuilder =
        sb?.append(", ") ?: StringBuilder().also { sb = it }

    val application = status.application
    if (application != null &&
        (column.isConversation || PrefB.bpShowAppName.value)
    ) {
        prepareSb().append(activity.getString(R.string.application_is, application.name ?: ""))
    }

    val language = status.language
    if (language != null &&
        (column.isConversation || PrefB.bpShowLanguage.value)
    ) {
        prepareSb().append(activity.getString(R.string.language_is, language))
    }

    tvApplication.vg(sb != null)?.text = sb
}

private fun ItemViewHolder.showOpenSticker(who: TootAccount) {
    try {
        if (!Column.showOpenSticker) return

        val host = who.apDomain

        // LTLでホスト名が同じならTickerを表示しない
        when (column.type) {
            ColumnType.LOCAL, ColumnType.LOCAL_AROUND -> {
                if (host == accessInfo.apDomain) return
            }

            else -> Unit
        }

        val item = OpenSticker.lastList[host.ascii] ?: return

        tvOpenSticker.text = item.name
        tvOpenSticker.textColor = item.fontColor

        val density = activity.density

        val lp = ivOpenSticker.layoutParams
        lp.height = (density * 16f + 0.5f).toInt()
        lp.width = (density * item.imageWidth + 0.5f).toInt()

        ivOpenSticker.layoutParams = lp
        ivOpenSticker.setImageUrl(0f, item.favicon)
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
        log.e(ex, "showOpenSticker failed.")
    }
}

private fun ItemViewHolder.showAttachments(status: TootStatus) {
    val mediaAttachments = status.media_attachments
    if (mediaAttachments.isNullOrEmpty()) {
        flMedia.visibility = View.GONE
        llMedia.visibility = View.GONE
        btnShowMedia.visibility = View.GONE
    } else {
        flMedia.visibility = View.VISIBLE
        tvMediaCount.vg(mediaAttachments.size > 4)?.let {
            it.text = activity.getString(R.string.media_count, mediaAttachments.size)
        }

        // hide sensitive media
        val defaultShown = when {
            column.hideMediaDefault -> false
            accessInfo.dontHideNsfw -> true
            else -> !status.sensitive
        }
        val isShown = daoMediaShown.isShown(status, defaultShown)

        btnShowMedia.visibility = if (!isShown) View.VISIBLE else View.GONE
        llMedia.visibility = if (!isShown) View.GONE else View.VISIBLE
        repeat(ItemViewHolder.MEDIA_VIEW_COUNT) { idx ->
            setMedia(mediaAttachments, idx)
        }

        btnShowMedia.blurhash = mediaAttachments.firstOrNull()
            ?.cast<TootAttachment>()
            ?.blurhash

        setIconDrawableId(
            activity,
            btnHideMedia,
            R.drawable.ic_close,
            color = colorTextContent,
            alphaMultiplier = stylerBoostAlpha
        )
    }
}

val reUrlGif by lazy {
    """\.gif(?:\z|\?|#)"""
        .toRegex(RegexOption.IGNORE_CASE)
}

fun ItemViewHolder.setMedia(
    mediaAttachments: ArrayList<TootAttachmentLike>,
    idx: Int,
) {
    val iv = ivMediaThumbnails[idx]
    val ta = if (idx < mediaAttachments.size) mediaAttachments[idx] else null
    if (ta == null) {
        iv.visibility = View.GONE
        return
    }

    iv.visibility = View.VISIBLE

    iv.setFocusPoint(ta.focusX, ta.focusY)

    if (PrefB.bpDontCropMediaThumb.value) {
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
    } else {
        iv.setScaleTypeForMedia()
    }

    val showUrl: Boolean

    when (ta.type) {
        TootAttachmentType.Audio -> {
            iv.setMediaType(0)
            iv.setDefaultImage(defaultColorIcon(activity, R.drawable.wide_music))
            iv.setImageUrl(0f, ta.urlForThumbnail())
            showUrl = true
        }

        TootAttachmentType.Unknown -> {
            iv.setMediaType(0)
            iv.setDefaultImage(defaultColorIcon(activity, R.drawable.wide_question))
            if (ta is TootAttachment &&
                reUrlGif.containsMatchIn(ta.remote_url ?: "") &&
                PrefB.bpImageAnimationEnable.value
            ) {
                val url = ta.remote_url!!
                iv.setImageUrl(0f, url, url)
            } else {
                iv.setImageUrl(0f, null)
            }
            showUrl = true
        }

        else -> if (ta is TootAttachment &&
            reUrlGif.containsMatchIn(ta.remote_url ?: "") &&
            PrefB.bpImageAnimationEnable.value
        ) {
            iv.setMediaType(0)
            iv.setDefaultImage(null)
            val url = ta.remote_url!!
            iv.setImageUrl(0f, url, url)
            showUrl = false
        } else when (val urlThumbnail = ta.urlForThumbnail()) {
            null, "" -> {
                iv.setMediaType(0)
                iv.setDefaultImage(defaultColorIcon(activity, R.drawable.wide_question))
                iv.setImageUrl(0f, null)
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
                    0f,
                    accessInfo.supplyBaseUrl(urlThumbnail),
                    accessInfo.supplyBaseUrl(urlThumbnail)
                )
                showUrl = false
            }
        }
    }

    val desc = ta.description.notEmpty()
        ?: if (!showUrl) null else ta.urlForDescription.notEmpty()

    tvMediaDescriptions[idx].vg(
        !desc.isNullOrBlank() && column.showMediaDescription
    )?.let {
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
        it.setTag(R.id.text, desc)
        it.text = activity.getString(
            R.string.media_description,
            idx + 1,
            desc?.ellipsizeDot3(140)
        )
    }
}
