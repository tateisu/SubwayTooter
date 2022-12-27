package jp.juggler.subwaytooter.actmain

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.Styler
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.itemviewholder.ItemViewHolder
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.pref.PrefF
import jp.juggler.subwaytooter.pref.PrefI
import jp.juggler.subwaytooter.pref.PrefS
import jp.juggler.subwaytooter.pref.impl.StringPref
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.util.CustomShare
import jp.juggler.subwaytooter.view.ListDivider
import jp.juggler.util.*
import org.jetbrains.anko.backgroundDrawable
import java.util.*
import kotlin.math.max

private val log = LogCategory("ActMainStyle")

private fun ActMain.dpToPx(dp: Float) =
    (dp * density + 0.5f).toInt()

// initUIから呼ばれる
fun ActMain.reloadFonts() {
    ActMain.timelineFont = PrefS.spTimelineFont(pref).notEmpty()?.let {
        try {
            Typeface.createFromFile(it)
        } catch (ex: Throwable) {
            log.e(ex, "timelineFont load failed")
            null
        }
    } ?: Typeface.DEFAULT

    ActMain.timelineFontBold = PrefS.spTimelineFontBold(pref).notEmpty()?.let {
        try {
            Typeface.createFromFile(it)
        } catch (ex: Throwable) {
            log.e(ex, "timelineFontBold load failed")
            null
        }
    } ?: try {
        Typeface.create(ActMain.timelineFont, Typeface.BOLD)
    } catch (ex: Throwable) {
        log.e(ex, "timelineFontBold create from timelineFont failed")
        null
    } ?: Typeface.DEFAULT_BOLD
}

private fun ActMain.parseIconSize(stringPref: StringPref, minDp: Float = 1f) =
    dpToPx(
        try {
            stringPref(pref)
                .toFloatOrNull()
                ?.takeIf { it.isFinite() && it >= minDp }
        } catch (ex: Throwable) {
            log.e(ex, "parseIconSize failed.")
            null
        } ?: stringPref.defVal.toFloat()
    )

// initUIから呼ばれる
fun ActMain.reloadIconSize() {
    avatarIconSize = parseIconSize(PrefS.spAvatarIconSize)
    notificationTlIconSize = parseIconSize(PrefS.spNotificationTlIconSize)
    ActMain.boostButtonSize = parseIconSize(PrefS.spBoostButtonSize)
    ActMain.replyIconSize = parseIconSize(PrefS.spReplyIconSize)
    ActMain.headerIconSize = parseIconSize(PrefS.spHeaderIconSize)
    ActMain.stripIconSize = parseIconSize(PrefS.spStripIconSize)
    ActMain.screenBottomPadding = parseIconSize(PrefS.spScreenBottomPadding, minDp = 0f)

    ActMain.eventFadeAlpha = PrefS.spEventTextAlpha()
        .toFloatOrNull()
        ?.takeIf { it.isFinite() }
        ?.clip(0f, 1f)
        ?: 1f
}

// initUIから呼ばれる
fun ActMain.reloadRoundRatio() {
    val sizeDp = when {
        PrefB.bpDontRound(pref) -> 0f
        else -> PrefS.spRoundRatio(pref)
            .toFloatOrNull()
            ?.takeIf { it.isFinite() }
            ?: 33f
    }
    Styler.round_ratio = clipRange(0f, 1f, sizeDp / 100f) * 0.5f
}

// initUI から呼ばれる
fun ActMain.reloadBoostAlpha() {
    Styler.boostAlpha = PrefS.spBoostAlpha(pref)
        .toIntOrNull()
        ?.toFloat()
        ?.let { (it + 0.5f) / 100f }
        ?.let {
            when {
                it >= 1f -> 1f
                it < 0f -> 0.66f
                else -> it
            }
        } ?: 0.8f
}

fun ActMain.reloadMediaHeight() {
    appState.mediaThumbHeight = dpToPx(
        PrefS.spMediaThumbHeight(pref)
            .toFloatOrNull()
            ?.takeIf { it >= 32f }
            ?: 64f
    )
}

private fun Float.clipFontSize(): Float =
    if (isNaN()) this else max(1f, this)

fun ActMain.reloadTextSize() {
    timelineFontSizeSp = PrefF.fpTimelineFontSize(pref).clipFontSize()
    acctFontSizeSp = PrefF.fpAcctFontSize(pref).clipFontSize()
    notificationTlFontSizeSp = PrefF.fpNotificationTlFontSize(pref).clipFontSize()
    headerTextSizeSp = PrefF.fpHeaderTextSize(pref).clipFontSize()
    val fv = PrefS.spTimelineSpacing(pref).toFloatOrNull()
    timelineSpacing = if (fv != null && fv.isFinite() && fv != 0f) fv else null
}

fun ActMain.loadColumnMin() =
    dpToPx(
        PrefS.spColumnWidth(pref)
            .toFloatOrNull()
            ?.takeIf { it.isFinite() && it >= 100f }
            ?: ActMain.COLUMN_WIDTH_MIN_DP.toFloat()
    )

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
            llQuickPostBar.addViewBeforeLast(
                View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(padding, 0)
                }
            )
        }

        PrefI.JWCP_END -> {
            val iconW = (ActMain.stripIconSize * 1.5f + 0.5f).toInt()
            val borderWidth = dpToPx(1f)
            val padding = resources.displayMetrics.widthPixels / 2 - iconW - borderWidth

            fun ViewGroup.addViewAfterFirst(v: View) = addView(v, 1)
            (svColumnStrip.parent as LinearLayout).addViewAfterFirst(
                View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(padding, 0)
                }
            )
            llQuickPostBar.addViewAfterFirst(
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
        log.w("reloadTimeZone: tz=${tz.displayName}")
        TootStatus.dateFormatFull.timeZone = tz
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

    CustomShare.reloadCache(this)
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
    llQuickPostBar.setBackgroundColor(colorColumnStripBackground)

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
    btnQuickPostMenu.backgroundDrawable =
        getAdaptiveRippleDrawableRound(this, colorButtonBg, colorButtonFg)

    val csl = ColorStateList.valueOf(
        footerButtonFgColor.notZero() ?: attrColor(R.attr.colorVectorDrawable)
    )
    btnToot.imageTintList = csl
    btnMenu.imageTintList = csl
    btnQuickToot.imageTintList = csl
    btnQuickPostMenu.imageTintList = csl

    val c = footerTabDividerColor.notZero() ?: colorColumnStripBackground
    vFooterDivider1.setBackgroundColor(c)
    vFooterDivider2.setBackgroundColor(c)

    llColumnStrip.indicatorColor =
        footerTabIndicatorColor.notZero() ?: attrColor(R.attr.colorAccent)
}

fun ActMain.closePopup() {
    try {
        popupStatusButtons?.dismiss()
    } catch (ignored: Throwable) {
    }
    popupStatusButtons = null
}
