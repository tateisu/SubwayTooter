package jp.juggler.subwaytooter

import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.JustifyContent
import jp.juggler.subwaytooter.emoji.UnicodeEmoji
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootTask
import jp.juggler.subwaytooter.api.TootTaskRunner
import jp.juggler.subwaytooter.api.entity.TootAnnouncement
import jp.juggler.subwaytooter.api.entity.TootReaction
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.dialog.EmojiPicker
import jp.juggler.subwaytooter.emoji.CustomEmoji
import jp.juggler.subwaytooter.span.NetworkEmojiSpan
import jp.juggler.subwaytooter.util.*
import jp.juggler.util.*
import org.jetbrains.anko.allCaps
import org.jetbrains.anko.padding
import org.jetbrains.anko.textColor


fun ColumnViewHolder.hideAnnouncements() {
    val column = column ?: return

    if (column.announcementHideTime <= 0L)
        column.announcementHideTime = System.currentTimeMillis()
    activity.app_state.saveColumnList()
    showAnnouncements()
}

fun ColumnViewHolder.toggleAnnouncements() {
    val column = column ?: return

    if (llAnnouncementsBox.visibility == View.VISIBLE) {
        if (column.announcementHideTime <= 0L)
            column.announcementHideTime = System.currentTimeMillis()
    } else {
        showColumnSetting(false)
        column.announcementHideTime = 0L
    }
    activity.app_state.saveColumnList()
    showAnnouncements()
}

fun ColumnViewHolder.showAnnouncements(force: Boolean = true) {
    val column = column ?: return

    if (!force && lastAnnouncementShown >= column.announcementUpdated) {
        return
    }
    lastAnnouncementShown = SystemClock.elapsedRealtime()

    fun clearExtras() {
        for (invalidator in extra_invalidator_list) {
            invalidator.register(null)
        }
        extra_invalidator_list.clear()
    }
    llAnnouncementExtra.removeAllViews()
    clearExtras()

    val listShown = TootAnnouncement.filterShown(column.announcements)
    if (listShown?.isEmpty() != false) {
        btnAnnouncements.vg(false)
        llAnnouncementsBox.vg(false)
        btnAnnouncementsBadge.vg(false)
        llColumnHeader.invalidate()
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

    val content_color = column.getContentColor()

    val item = listShown.find { it.id == column.announcementId }
        ?: listShown[0]

    val itemIndex = listShown.indexOf(item)

    val enablePaging = listShown.size > 1

    val alphaPrevNext = if (enablePaging) 1f else 0.3f

    setIconDrawableId(
        activity,
        btnAnnouncementsPrev,
        R.drawable.ic_arrow_start,
        color = content_color,
        alphaMultiplier = alphaPrevNext
    )

    setIconDrawableId(
        activity,
        btnAnnouncementsNext,
        R.drawable.ic_arrow_end,
        color = content_color,
        alphaMultiplier = alphaPrevNext
    )


    btnAnnouncementsPrev.vg(expand)?.run {
        isEnabled = enablePaging
    }
    btnAnnouncementsNext.vg(expand)?.run {
        isEnabled = enablePaging
    }

    tvAnnouncementsCaption.textColor = content_color
    tvAnnouncementsIndex.textColor = content_color
    tvAnnouncementPeriod.textColor = content_color

    val f = activity.timeline_font_size_sp
    if (!f.isNaN()) {
        tvAnnouncementsCaption.textSize = f
        tvAnnouncementsIndex.textSize = f
        tvAnnouncementPeriod.textSize = f
        tvAnnouncementContent.textSize = f
    }
    val spacing = activity.timeline_spacing
    if (spacing != null) {
        tvAnnouncementPeriod.setLineSpacing(0f, spacing)
        tvAnnouncementContent.setLineSpacing(0f, spacing)
    }
    tvAnnouncementsCaption.typeface = ActMain.timeline_font_bold
    val font_normal = ActMain.timeline_font
    tvAnnouncementsIndex.typeface = font_normal
    tvAnnouncementPeriod.typeface = font_normal
    tvAnnouncementContent.typeface = font_normal

    tvAnnouncementsIndex.vg(expand)?.text =
        activity.getString(R.string.announcements_index, itemIndex + 1, listShown.size)
    llAnnouncements.vg(expand)

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

    val (strStart, strEnd) = TootStatus.formatTimeRange(
        item.starts_at,
        item.ends_at,
        item.all_day
    )

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

    tvAnnouncementContent.textColor = content_color
    tvAnnouncementContent.text = item.decoded_content
    tvAnnouncementContent.tag = this@showAnnouncements
    announcementContentInvalidator.register(item.decoded_content)

    // リアクションの表示

    val density = activity.density

    val buttonHeight = ActMain.boostButtonSize
    val marginBetween = (buttonHeight.toFloat() * 0.2f + 0.5f).toInt()
    val marginBottom = (buttonHeight.toFloat() * 0.2f + 0.5f).toInt()

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
    run {
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
            addReaction(item, null)
        }

        setIconDrawableId(
            activity,
            b,
            R.drawable.ic_add,
            color = content_color,
            alphaMultiplier = 1f
        )

        box.addView(b)
    }
    val reactions = item.reactions?.filter { it.count > 0L }?.notEmpty()
    if (reactions != null) {

        var lastButton: View? = null

        val options = DecodeOptions(
            activity,
            column.access_info,
            decodeEmoji = true,
            enlargeEmoji = 1.5f,
            mentionDefaultHostDomain = column.access_info
        )

        val actMain = activity
        val disableEmojiAnimation = Pref.bpDisableEmojiAnimation(actMain.pref)

        for (reaction in reactions) {

            val url = if (disableEmojiAnimation) {
                reaction.static_url.notEmpty() ?: reaction.url.notEmpty()
            } else {
                reaction.url.notEmpty() ?: reaction.static_url.notEmpty()
            }

            val b = Button(activity).also { btn ->
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

                btn.setTextColor(content_color)

                btn.setPadding(paddingH, paddingV, paddingH, paddingV)


                btn.text = if (url == null) {
                    EmojiDecoder.decodeEmoji(options, "${reaction.name} ${reaction.count}")
                } else {
                    SpannableStringBuilder("${reaction.name} ${reaction.count}").also { sb ->
                        sb.setSpan(
                            NetworkEmojiSpan(url, scale = 1.5f),
                            0,
                            reaction.name.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        val invalidator =
                            NetworkEmojiInvalidator(actMain.handler, btn)
                        invalidator.register(sb)
                        extra_invalidator_list.add(invalidator)
                    }
                }

                btn.setOnClickListener {
                    if (reaction.me) {
                        removeReaction(item, reaction.name)
                    } else {
                        addReaction(item, TootReaction.parseFedibird(jsonObject {
                            put("name", reaction.name)
                            put("count", 1)
                            put("me", true)
                            putNotNull("url", reaction.url)
                            putNotNull("static_url", reaction.static_url)
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

    llAnnouncementExtra.addView(box)
}


fun ColumnViewHolder.addReaction(item: TootAnnouncement, sample: TootReaction?) {
    val column = column ?: return
    if (sample == null) {
        EmojiPicker(activity, column.access_info, closeOnSelected = true) { result ->
            val emoji = result.emoji
            val code = when (emoji) {
                is UnicodeEmoji -> emoji.unifiedCode
                is CustomEmoji -> emoji.shortcode
            }
            ColumnViewHolder.log.d("addReaction: $code ${result.emoji.javaClass.simpleName}")
            addReaction(item, TootReaction.parseFedibird(jsonObject {
                put("name", code)
                put("count", 1)
                put("me", true)
                // 以下はカスタム絵文字のみ
                if (emoji is CustomEmoji) {
                    putNotNull("url", emoji.url)
                    putNotNull("static_url", emoji.static_url)
                }
            }))
        }.show()
        return
    }

    TootTaskRunner(activity).run(column.access_info, object : TootTask {
        override suspend fun background(client: TootApiClient): TootApiResult? {
            return client.request(
                "/api/v1/announcements/${item.id}/reactions/${sample.name.encodePercent()}",
                JsonObject().toPutRequestBuilder()
            )
            // 200 {}
        }

        override suspend fun handleResult(result: TootApiResult?) {
            result ?: return
            if (result.jsonObject == null) {
                activity.showToast(true, result.error)
            } else {
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
    })
}

fun ColumnViewHolder.removeReaction(item: TootAnnouncement, name: String) {
    val column = column ?: return
    TootTaskRunner(activity).run(column.access_info, object : TootTask {
        override suspend fun background(client: TootApiClient): TootApiResult? {
            return client.request(
                "/api/v1/announcements/${item.id}/reactions/${name.encodePercent()}",
                JsonObject().toDeleteRequestBuilder()
            )
            // 200 {}
        }

        override suspend fun handleResult(result: TootApiResult?) {
            result ?: return
            if (result.jsonObject == null) {
                activity.showToast(true, result.error)
            } else {
                val it = item.reactions?.iterator() ?: return
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
    })
}