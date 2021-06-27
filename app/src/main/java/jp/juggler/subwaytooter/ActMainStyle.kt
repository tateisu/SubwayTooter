package jp.juggler.subwaytooter

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.util.CustomShare
import jp.juggler.subwaytooter.view.ListDivider
import jp.juggler.subwaytooter.view.TabletColumnDivider
import jp.juggler.util.*
import org.jetbrains.anko.backgroundDrawable
import java.util.*

private val log = LogCategory("ActMainStyle")

// initUIから呼ばれる
fun ActMain.reloadFonts() {
    var sv = PrefS.spTimelineFont(pref)
    if (sv.isNotEmpty()) {
        try {
            ActMain.timelineFont = Typeface.createFromFile(sv)
        } catch (ex: Throwable) {
            log.trace(ex)
        }
    }

    sv = PrefS.spTimelineFontBold(pref)
    if (sv.isNotEmpty()) {
        try {
            ActMain.timeline_font_bold = Typeface.createFromFile(sv)
        } catch (ex: Throwable) {
            log.trace(ex)
        }
    } else {
        try {
            ActMain.timeline_font_bold = Typeface.create(ActMain.timelineFont, Typeface.BOLD)
        } catch (ex: Throwable) {
            log.trace(ex)
        }
    }
}

fun ActMain.parseIconSize(stringPref: StringPref, minDp: Float = 1f): Int {
    var iconSizeDp = stringPref.defVal.toFloat()
    try {
        val sv = stringPref(pref)
        val fv = if (sv.isEmpty()) Float.NaN else sv.toFloat()
        if (fv.isFinite() && fv >= minDp) {
            iconSizeDp = fv
        }
    } catch (ex: Throwable) {
        log.trace(ex)
    }
    return (0.5f + iconSizeDp * density).toInt()
}

// initUIから呼ばれる
fun ActMain.reloadIconSize() {
    avatarIconSize = parseIconSize(PrefS.spAvatarIconSize)
    notificationTlIconSize = parseIconSize(PrefS.spNotificationTlIconSize)
    ActMain.boostButtonSize = parseIconSize(PrefS.spBoostButtonSize)
    ActMain.replyIconSize = parseIconSize(PrefS.spReplyIconSize)
    ActMain.headerIconSize = parseIconSize(PrefS.spHeaderIconSize)
    ActMain.stripIconSize = parseIconSize(PrefS.spStripIconSize)
    ActMain.screenBottomPadding = parseIconSize(PrefS.spScreenBottomPadding, minDp = 0f)
}

// initUIから呼ばれる
fun ActMain.reloadRoundRatio() {
    var roundRatio = 33f
    try {
        if (PrefB.bpDontRound(pref)) {
            roundRatio = 0f
        } else {
            val sv = PrefS.spRoundRatio(pref)
            if (sv.isNotEmpty()) {
                val fv = sv.toFloat()
                if (fv.isFinite()) {
                    roundRatio = fv
                }
            }
        }
    } catch (ex: Throwable) {
        log.trace(ex)
    }
    Styler.round_ratio = clipRange(0f, 1f, roundRatio / 100f) * 0.5f
}

// initUI から呼ばれる
fun ActMain.reloadBoostAlpha() {
    var boostAlpha = 0.8f
    try {
        val f = (PrefS.spBoostAlpha.toInt(pref).toFloat() + 0.5f) / 100f
        boostAlpha = when {
            f >= 1f -> 1f
            f < 0f -> 0.66f
            else -> f
        }
    } catch (ex: Throwable) {
        log.trace(ex)
    }
    Styler.boostAlpha = boostAlpha
}

fun ActMain.reloadMediaHeight() {
    var mediaThumbHeightDp = 64
    val sv = PrefS.spMediaThumbHeight(pref)
    if (sv.isNotEmpty()) {
        try {
            val iv = Integer.parseInt(sv)
            if (iv >= 32) mediaThumbHeightDp = iv
        } catch (ex: Throwable) {
            log.trace(ex)
        }
    }
    appState.mediaThumbHeight = (0.5f + mediaThumbHeightDp * density).toInt()
}

fun ActMain.loadColumnMin(density: Float): Int {
    var x = ActMain.COLUMN_WIDTH_MIN_DP.toFloat()
    val sv = PrefS.spColumnWidth(pref)
    if (sv.isNotEmpty()) {
        try {
            val fv = sv.toFloat()
            if (fv.isFinite() && fv >= 100f) {
                x = fv
            }
        } catch (ex: Throwable) {
            log.trace(ex)
        }
    }
    return (0.5f + x * density).toInt()
}

fun ActMain.justifyWindowContentPortrait() {
    when (PrefI.ipJustifyWindowContentPortrait(pref)) {
        PrefI.JWCP_START -> {
            val iconW = (ActMain.stripIconSize * 1.5f + 0.5f).toInt()
            val padding = resources.displayMetrics.widthPixels / 2 - iconW

            fun ViewGroup.addViewBeforeLast(v: View) = addView(v, childCount - 1)
            (svColumnStrip.parent as LinearLayout).addViewBeforeLast(
                View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(padding, 0)
                }
            )
            llQuickTootBar.addViewBeforeLast(
                View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(padding, 0)
                }
            )
        }

        PrefI.JWCP_END -> {
            val iconW = (ActMain.stripIconSize * 1.5f + 0.5f).toInt()
            val borderWidth = (1f * density + 0.5f).toInt()
            val padding = resources.displayMetrics.widthPixels / 2 - iconW - borderWidth

            fun ViewGroup.addViewAfterFirst(v: View) = addView(v, 1)
            (svColumnStrip.parent as LinearLayout).addViewAfterFirst(
                View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(padding, 0)
                }
            )
            llQuickTootBar.addViewAfterFirst(
                View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(padding, 0)
                }
            )
        }
    }
}

//////////////////////////////////////////////////////

// onStart時に呼ばれる
fun ActMain.reloadTimeZone() {
    try {
        var tz = TimeZone.getDefault()
        val tzId = PrefS.spTimeZone(pref)
        if (tzId.isNotEmpty()) {
            tz = TimeZone.getTimeZone(tzId)
        }
        TootStatus.date_format.timeZone = tz
    } catch (ex: Throwable) {
        log.e(ex, "getTimeZone failed.")
    }
}

// onStart時に呼ばれる
// カラーカスタマイズを読み直す
fun ActMain.reloadColors() {
    ListDivider.color = PrefI.ipListDividerColor(pref)
    TabletColumnDivider.color = PrefI.ipListDividerColor(pref)
    ItemViewHolder.toot_color_unlisted = PrefI.ipTootColorUnlisted(pref)
    ItemViewHolder.toot_color_follower = PrefI.ipTootColorFollower(pref)
    ItemViewHolder.toot_color_direct_user = PrefI.ipTootColorDirectUser(pref)
    ItemViewHolder.toot_color_direct_me = PrefI.ipTootColorDirectMe(pref)
    MyClickableSpan.showLinkUnderline = PrefB.bpShowLinkUnderline(pref)
    MyClickableSpan.defaultLinkColor = PrefI.ipLinkColor(pref).notZero()
        ?: attrColor(R.attr.colorLink)

    CustomShare.reloadCache(this, pref)
}

fun ActMain.showFooterColor() {
    val footerButtonBgColor = PrefI.ipFooterButtonBgColor(pref)
    val footerButtonFgColor = PrefI.ipFooterButtonFgColor(pref)
    val footerTabBgColor = PrefI.ipFooterTabBgColor(pref)
    val footerTabDividerColor = PrefI.ipFooterTabDividerColor(pref)
    val footerTabIndicatorColor = PrefI.ipFooterTabIndicatorColor(pref)

    val colorColumnStripBackground = footerTabBgColor.notZero()
        ?: attrColor(R.attr.colorColumnStripBackground)

    svColumnStrip.setBackgroundColor(colorColumnStripBackground)
    llQuickTootBar.setBackgroundColor(colorColumnStripBackground)

    val colorButtonBg = footerButtonBgColor.notZero()
        ?: colorColumnStripBackground

    val colorButtonFg = footerButtonFgColor.notZero()
        ?: attrColor(R.attr.colorRippleEffect)

    btnMenu.backgroundDrawable =
        getAdaptiveRippleDrawableRound(this, colorButtonBg, colorButtonFg)
    btnToot.backgroundDrawable =
        getAdaptiveRippleDrawableRound(this, colorButtonBg, colorButtonFg)
    btnQuickToot.backgroundDrawable =
        getAdaptiveRippleDrawableRound(this, colorButtonBg, colorButtonFg)
    btnQuickTootMenu.backgroundDrawable =
        getAdaptiveRippleDrawableRound(this, colorButtonBg, colorButtonFg)

    val csl = ColorStateList.valueOf(
        footerButtonFgColor.notZero()
            ?: attrColor(R.attr.colorVectorDrawable)
    )
    btnToot.imageTintList = csl
    btnMenu.imageTintList = csl
    btnQuickToot.imageTintList = csl
    btnQuickTootMenu.imageTintList = csl

    val c = footerTabDividerColor.notZero()
        ?: colorColumnStripBackground
    vFooterDivider1.setBackgroundColor(c)
    vFooterDivider2.setBackgroundColor(c)

    llColumnStrip.indicatorColor = footerTabIndicatorColor.notZero()
        ?: attrColor(R.attr.colorAccent)
}