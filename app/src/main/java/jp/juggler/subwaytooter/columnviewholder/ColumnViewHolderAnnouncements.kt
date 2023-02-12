package jp.juggler.subwaytooter.columnviewholder

import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.JustifyContent
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootAnnouncement
import jp.juggler.subwaytooter.api.entity.TootReaction
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.column.Column
import jp.juggler.subwaytooter.column.getContentColor
import jp.juggler.subwaytooter.dialog.launchEmojiPicker
import jp.juggler.subwaytooter.emoji.CustomEmoji
import jp.juggler.subwaytooter.emoji.UnicodeEmoji
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.span.NetworkEmojiSpan
import jp.juggler.subwaytooter.util.*
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.data.*
import jp.juggler.util.log.showToast
import jp.juggler.util.network.toDeleteRequestBuilder
import jp.juggler.util.network.toPutRequestBuilder
import jp.juggler.util.ui.*
import org.jetbrains.anko.allCaps
import org.jetbrains.anko.padding
import org.jetbrains.anko.textColor

fun ColumnViewHolder.hideAnnouncements() {
    val column = column ?: return

    if (column.announcementHideTime <= 0L) {
        column.announcementHideTime = System.currentTimeMillis()
    }

    activity.appState.saveColumnList()

    showAnnouncements()
}

fun ColumnViewHolder.toggleAnnouncements() {
    val column = column ?: return

    if (llAnnouncementsBox.visibility == View.VISIBLE) {
        if (column.announcementHideTime <= 0L) {
            column.announcementHideTime = System.currentTimeMillis()
        }
    } else {
        showColumnSetting(false)
        column.announcementHideTime = 0L
    }
    activity.appState.saveColumnList()
    showAnnouncements()
}

fun ColumnViewHolder.showAnnouncements(force: Boolean = true) {
    val column = column ?: return

    if (!force && lastAnnouncementShown >= column.announcementUpdated) {
        return
    }
    lastAnnouncementShown = SystemClock.elapsedRealtime()
    llAnnouncementExtra.removeAllViews()
    clearExtras()

    val listShown = TootAnnouncement.filterShown(column.announcements)
    if (listShown?.isEmpty() != false) {
        showAnnouncementsEmpty()
        return
    }

    btnAnnouncements.vg(true)

    val expand = column.announcementHideTime <= 0L

    llAnnouncementsBox.vg(expand)
    llColumnHeader.invalidate()

    btnAnnouncementsBadge.vg(false)
    if (!expand) {
        val newer = listShown.find { it.updated_at > column.announcementHideTime }
        if (newer != null) {
            column.announcementId = newer.id
            btnAnnouncementsBadge.vg(true)
        }
        return
    }

    val item = listShown.find { it.id == column.announcementId }
        ?: listShown[0]

    val itemIndex = listShown.indexOf(item)

    val enablePaging = listShown.size > 1
    val contentColor = column.getContentColor()

    showAnnouncementColors(true, enablePaging, contentColor)
    showAnnouncementFonts()

    llAnnouncements.vg(true)
    tvAnnouncementsIndex.vg(true)?.text =
        activity.getString(R.string.announcements_index, itemIndex + 1, listShown.size)

    showAnnouncementContent(item, contentColor)
    showReactionBox(column, item, contentColor)
}

private fun ColumnViewHolder.clearExtras() {
    for (invalidator in extraInvalidatorList) {
        invalidator.register(null)
    }
    extraInvalidatorList.clear()
}

private fun ColumnViewHolder.showAnnouncementsEmpty() {
    btnAnnouncements.vg(false)
    llAnnouncementsBox.vg(false)
    btnAnnouncementsBadge.vg(false)
    llColumnHeader.invalidate()
}

private fun ColumnViewHolder.showAnnouncementColors(
    expand: Boolean,
    enablePaging: Boolean,
    contentColor: Int,
) {
    val alphaPrevNext = if (enablePaging) 1f else 0.3f

    setIconDrawableId(
        activity,
        btnAnnouncementsPrev,
        R.drawable.ic_arrow_start,
        color = contentColor,
        alphaMultiplier = alphaPrevNext
    )

    setIconDrawableId(
        activity,
        btnAnnouncementsNext,
        R.drawable.ic_arrow_end,
        color = contentColor,
        alphaMultiplier = alphaPrevNext
    )

    btnAnnouncementsPrev.vg(expand)?.apply {
        isEnabledAlpha = enablePaging
    }
    btnAnnouncementsNext.vg(expand)?.apply {
        isEnabledAlpha = enablePaging
    }

    tvAnnouncementsCaption.textColor = contentColor
    tvAnnouncementsIndex.textColor = contentColor
    tvAnnouncementPeriod.textColor = contentColor
}

private fun ColumnViewHolder.showAnnouncementFonts() {
    val f = activity.timelineFontSizeSp
    if (!f.isNaN()) {
        tvAnnouncementsCaption.textSize = f
        tvAnnouncementsIndex.textSize = f
        tvAnnouncementPeriod.textSize = f
        tvAnnouncementContent.textSize = f
    }
    val spacing = activity.timelineSpacing
    if (spacing != null) {
        tvAnnouncementPeriod.setLineSpacing(0f, spacing)
        tvAnnouncementContent.setLineSpacing(0f, spacing)
    }
    tvAnnouncementsCaption.typeface = ActMain.timelineFontBold
    val fontNormal = ActMain.timelineFont
    tvAnnouncementsIndex.typeface = fontNormal
    tvAnnouncementPeriod.typeface = fontNormal
    tvAnnouncementContent.typeface = fontNormal
}

private fun ColumnViewHolder.showAnnouncementContent(item: TootAnnouncement, contentColor: Int) {
    var periods: StringBuilder? = null
    fun String.appendPeriod() {
        val sb = periods
        if (sb == null) {
            periods = StringBuilder(this)
        } else {
            sb.append("\n")
            sb.append(this)
        }
    }

    val (strStart, strEnd) = TootStatus.formatTimeRange(item.starts_at, item.ends_at, item.all_day)

    when {
        // no periods.
        strStart == "" && strEnd == "" -> {
        }

        // single date
        strStart == strEnd -> {
            activity.getString(R.string.announcements_period1, strStart)
                .appendPeriod()
        }

        else -> {
            activity.getString(R.string.announcements_period2, strStart, strEnd)
                .appendPeriod()
        }
    }

    if (item.updated_at > item.published_at) {
        val strUpdateAt = TootStatus.formatTime(activity, item.updated_at, false)
        activity.getString(R.string.edited_at, strUpdateAt).appendPeriod()
    }

    val sb = periods
    tvAnnouncementPeriod.vg(sb != null)?.text = sb
    tvAnnouncementContent.textColor = contentColor
    tvAnnouncementContent.text = item.decoded_content
    tvAnnouncementContent.tag = this
    announcementContentInvalidator.register(item.decoded_content)
}

private fun ColumnViewHolder.showReactionBox(
    column: Column,
    item: TootAnnouncement,
    contentColor: Int,
) {
    // リアクションの表示

    val density = activity.density

    val buttonHeight = ActMain.boostButtonSize
    val marginBetween = (buttonHeight.toFloat() * 0.2f + 0.5f).toInt()

    val paddingH = (buttonHeight.toFloat() * 0.1f + 0.5f).toInt()
    val paddingV = (buttonHeight.toFloat() * 0.1f + 0.5f).toInt()

    val box = FlexboxLayout(activity).apply {
        flexWrap = FlexWrap.WRAP
        justifyContent = JustifyContent.FLEX_START
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = (0.5f + density * 3f).toInt()
        }
    }
    // +ボタン
    showReactionPlus(box, item, buttonHeight, marginBetween, contentColor, paddingV)
    item.reactions?.filter { it.count > 0L }
        ?.notEmpty()
        ?.let {
            showReactions(
                box,
                item,
                it,
                column,
                buttonHeight,
                marginBetween,
                contentColor,
                paddingH,
                paddingV
            )
        }
    llAnnouncementExtra.addView(box)
}

private fun ColumnViewHolder.showReactionPlus(
    box: FlexboxLayout,
    item: TootAnnouncement,
    buttonHeight: Int,
    marginBetween: Int,
    contentColor: Int,
    paddingV: Int,
) {
    val b = ImageButton(activity)
    val blp = FlexboxLayout.LayoutParams(
        buttonHeight,
        buttonHeight
    ).apply {
        bottomMargin = marginBottom
        endMargin = marginBetween
    }
    b.layoutParams = blp
    b.background = ContextCompat.getDrawable(
        activity,
        R.drawable.btn_bg_transparent_round6dp
    )

    b.contentDescription = activity.getString(R.string.reaction_add)
    b.scaleType = ImageView.ScaleType.FIT_CENTER
    b.padding = paddingV
    b.setOnClickListener {
        reactionAdd(item, null)
    }

    setIconDrawableId(
        activity,
        b,
        R.drawable.ic_add,
        color = contentColor,
    )

    box.addView(b)
}

private fun ColumnViewHolder.showReactions(
    box: FlexboxLayout,
    item: TootAnnouncement,
    reactions: List<TootReaction>,
    column: Column,
    buttonHeight: Int,
    marginBetween: Int,
    contentColor: Int,
    paddingH: Int,
    paddingV: Int,
) {

    var lastButton: View? = null

    val options = DecodeOptions(
        activity,
        column.accessInfo,
        decodeEmoji = true,
        enlargeEmoji = 1.5f,
        authorDomain = column.accessInfo
    )

    val actMain = activity
    val disableEmojiAnimation = PrefB.bpDisableEmojiAnimation.value

    for (reaction in reactions) {

        val url = if (disableEmojiAnimation) {
            reaction.staticUrl.notEmpty() ?: reaction.url.notEmpty()
        } else {
            reaction.url.notEmpty() ?: reaction.staticUrl.notEmpty()
        }

        val b = AppCompatButton(activity).also { btn ->
            btn.layoutParams = FlexboxLayout.LayoutParams(
                FlexboxLayout.LayoutParams.WRAP_CONTENT,
                buttonHeight
            ).apply {
                endMargin = marginBetween
                bottomMargin = marginBottom
            }
            btn.minWidthCompat = buttonHeight

            btn.allCaps = false
            btn.tag = reaction

            btn.background = if (reaction.me) {
                getAdaptiveRippleDrawableRound(
                    actMain,
                    actMain.attrColor(R.attr.colorButtonBgCw),
                    actMain.attrColor(R.attr.colorRippleEffect)
                )
            } else {
                ContextCompat.getDrawable(actMain, R.drawable.btn_bg_transparent_round6dp)
            }

            btn.setTextColor(contentColor)

            btn.setPadding(paddingH, paddingV, paddingH, paddingV)

            if (url == null) {
                btn.text = EmojiDecoder.decodeEmoji(options, "${reaction.name} ${reaction.count}")
            } else {
                btn.text = SpannableStringBuilder("${reaction.name} ${reaction.count}").also { sb ->
                    sb.setSpan(
                        NetworkEmojiSpan(url, scale = 1.5f),
                        0,
                        reaction.name.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    val invalidator =
                        NetworkEmojiInvalidator(actMain.handler, btn)
                    invalidator.register(sb)
                    extraInvalidatorList.add(invalidator)
                }
            }

            btn.setOnClickListener {
                if (reaction.me) {
                    reactionRemove(item, reaction.name)
                } else {
                    reactionAdd(item, TootReaction.parseFedibird(buildJsonObject {
                        put("name", reaction.name)
                        put("count", 1)
                        put("me", true)
                        putNotNull("url", reaction.url)
                        putNotNull("static_url", reaction.staticUrl)
                    }))
                }
            }
        }
        box.addView(b)
        lastButton = b
    }

    lastButton
        ?.layoutParams
        ?.cast<ViewGroup.MarginLayoutParams>()
        ?.endMargin = 0
}

fun ColumnViewHolder.reactionAdd(item: TootAnnouncement, sample: TootReaction?) {
    val column = column ?: return

    if (sample == null) {
        launchEmojiPicker(activity, column.accessInfo, closeOnSelected = true) { emoji, _ ->
            val code = when (emoji) {
                is UnicodeEmoji -> emoji.unifiedCode
                is CustomEmoji -> emoji.shortcode
            }
            ColumnViewHolder.log.d("addReaction: $code ${emoji.javaClass.simpleName}")
            reactionAdd(item, TootReaction.parseFedibird(buildJsonObject {
                put("name", code)
                put("count", 1)
                put("me", true)
                // 以下はカスタム絵文字のみ
                if (emoji is CustomEmoji) {
                    putNotNull("url", emoji.url)
                    putNotNull("static_url", emoji.staticUrl)
                }
            }))
        }
        return
    }
    activity.launchAndShowError {
        activity.runApiTask(column.accessInfo) { client ->
            client.request(
                "/api/v1/announcements/${item.id}/reactions/${sample.name.encodePercent()}",
                JsonObject().toPutRequestBuilder()
            )
            // 200 {}
        }?.let { result ->
            when (result.jsonObject) {
                null -> activity.showToast(true, result.error)
                else -> {
                    sample.count = 0
                    val list = item.reactions
                    if (list == null) {
                        item.reactions = mutableListOf(sample)
                    } else {
                        val reaction = list.find { it.name == sample.name }
                        if (reaction == null) {
                            list.add(sample)
                        } else {
                            reaction.me = true
                            ++reaction.count
                        }
                    }
                    column.announcementUpdated = SystemClock.elapsedRealtime()
                    showAnnouncements()
                }
            }
        }
    }
}

fun ColumnViewHolder.reactionRemove(item: TootAnnouncement, name: String) {
    val column = column ?: return
    launchMain {
        activity.runApiTask(column.accessInfo) { client ->
            client.request(
                "/api/v1/announcements/${item.id}/reactions/${name.encodePercent()}",
                JsonObject().toDeleteRequestBuilder()
            )
            // 200 {}
        }?.let { result ->
            when (result.jsonObject) {
                null -> activity.showToast(true, result.error)
                else -> item.reactions?.iterator()?.let {
                    while (it.hasNext()) {
                        val reaction = it.next()
                        if (reaction.name == name) {
                            reaction.me = false
                            if (--reaction.count <= 0) it.remove()
                            break
                        }
                    }
                    column.announcementUpdated = SystemClock.elapsedRealtime()
                    showAnnouncements()
                }
            }
        }
    }
}
