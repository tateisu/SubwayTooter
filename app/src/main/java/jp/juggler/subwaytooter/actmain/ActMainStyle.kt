package jp.juggler.subwaytooter.actmain

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.itemviewholder.ItemViewHolder
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.pref.PrefF
import jp.juggler.subwaytooter.pref.PrefI
import jp.juggler.subwaytooter.pref.PrefS
import jp.juggler.subwaytooter.pref.impl.StringPref
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.stylerBoostAlpha
import jp.juggler.subwaytooter.stylerRoundRatio
import jp.juggler.subwaytooter.util.CustomShare
import jp.juggler.subwaytooter.view.ListDivider
import jp.juggler.util.data.clip
import jp.juggler.util.data.notEmpty
import jp.juggler.util.data.notZero
import jp.juggler.util.log.LogCategory
import jp.juggler.util.ui.attrColor
import jp.juggler.util.ui.getAdaptiveRippleDrawableRound
import jp.juggler.util.ui.resDrawable
import jp.juggler.util.ui.wrapAndTint
import org.jetbrains.anko.backgroundDrawable
import java.util.*
import kotlin.math.max

private val log = LogCategory("ActMainStyle")

private fun Float.dpToPx(context: Context) =
    (this * context.resources.displayMetrics.density + 0.5f).toInt()

// initUIから呼ばれる
fun reloadFonts() {
    ActMain.timelineFont = PrefS.spTimelineFont.value.notEmpty()?.let {
        try {
            Typeface.createFromFile(it)
        } catch (ex: Throwable) {
            log.e(ex, "timelineFont load failed")
            null
        }
    } ?: Typeface.DEFAULT

    ActMain.timelineFontBold = PrefS.spTimelineFontBold.value.notEmpty()?.let {
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
    (try {
        stringPref.value
            .toFloatOrNull()
            ?.takeIf { it.isFinite() && it >= minDp }
    } catch (ex: Throwable) {
        log.e(ex, "parseIconSize failed.")
        null
    } ?: stringPref.defVal.toFloat()).dpToPx(this)

// initUIから呼ばれる
fun ActMain.reloadIconSize() {
    avatarIconSize = parseIconSize(PrefS.spAvatarIconSize)
    notificationTlIconSize = parseIconSize(PrefS.spNotificationTlIconSize)
    ActMain.boostButtonSize = parseIconSize(PrefS.spBoostButtonSize)
    ActMain.replyIconSize = parseIconSize(PrefS.spReplyIconSize)
    ActMain.headerIconSize = parseIconSize(PrefS.spHeaderIconSize)
    ActMain.stripIconSize = parseIconSize(PrefS.spStripIconSize)
    ActMain.screenBottomPadding = parseIconSize(PrefS.spScreenBottomPadding, minDp = 0f)

    ActMain.eventFadeAlpha = PrefS.spEventTextAlpha.value
        .toFloatOrNull()
        ?.takeIf { it.isFinite() }
        ?.clip(0f, 1f)
        ?: 1f
}

// initUIから呼ばれる
fun reloadRoundRatio() {
    val sizeDp = when {
        PrefB.bpDontRound.value -> 0f
        else -> PrefS.spRoundRatio.value
            .toFloatOrNull()
            ?.takeIf { it.isFinite() }
            ?: 33f
    }
    stylerRoundRatio = (sizeDp / 100f).clip(0f, 1f) * 0.5f
}

// initUI から呼ばれる
fun reloadBoostAlpha() {
    stylerBoostAlpha = PrefS.spBoostAlpha.value
        .toIntOrNull()
        ?.toFloat()
        ?.let { (it + 0.5f) / 100f }
        ?.takeIf { it > 0f && it < 1f }
        ?: 1f
}

fun ActMain.reloadMediaHeight() {
    appState.mediaThumbHeight = (
            PrefS.spMediaThumbHeight.value
                .toFloatOrNull()
                ?.takeIf { it >= 32f }
                ?: 64f
            ).dpToPx(this)
}

private fun Float.clipFontSize(): Float =
    if (isNaN()) this else max(1f, this)

fun ActMain.reloadTextSize() {
    ActMain.timelineFontSizeSp = PrefF.fpTimelineFontSize.value.clipFontSize()
    acctFontSizeSp = PrefF.fpAcctFontSize.value.clipFontSize()
    notificationTlFontSizeSp = PrefF.fpNotificationTlFontSize.value.clipFontSize()
    headerTextSizeSp = PrefF.fpHeaderTextSize.value.clipFontSize()
    val fv = PrefS.spTimelineSpacing.value.toFloatOrNull()
    ActMain.timelineSpacing = if (fv != null && fv.isFinite() && fv != 0f) fv else null
}

fun ActMain.loadColumnMin() =
    (PrefS.spColumnWidth.value
        .toFloatOrNull()
        ?.takeIf { it.isFinite() && it >= 100f }
        ?: ActMain.COLUMN_WIDTH_MIN_DP.toFloat()
            ).dpToPx(this)

fun ActMain.justifyWindowContentPortrait() {
    when (PrefI.ipJustifyWindowContentPortrait.value) {
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
            val borderWidth = 1f.dpToPx(this)
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
fun reloadTimeZone() {
    try {
        var tz = TimeZone.getDefault()
        val tzId = PrefS.spTimeZone.value
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
    ListDivider.color = PrefI.ipListDividerColor.value
    TabletColumnDivider.color = PrefI.ipListDividerColor.value
    ItemViewHolder.toot_color_unlisted = PrefI.ipTootColorUnlisted.value
    ItemViewHolder.toot_color_follower = PrefI.ipTootColorFollower.value
    ItemViewHolder.toot_color_direct_user = PrefI.ipTootColorDirectUser.value
    ItemViewHolder.toot_color_direct_me = PrefI.ipTootColorDirectMe.value
    MyClickableSpan.showLinkUnderline = PrefB.bpShowLinkUnderline.value
    MyClickableSpan.defaultLinkColor = PrefI.ipLinkColor.value.notZero()
        ?: attrColor(R.attr.colorLink)

    CustomShare.reloadCache(this)
}

fun ActMain.showFooterColor() {
    val footerButtonBgColor = PrefI.ipFooterButtonBgColor.value
    val footerButtonFgColor = PrefI.ipFooterButtonFgColor.value
    val footerTabBgColor = PrefI.ipFooterTabBgColor.value
    val footerTabDividerColor = PrefI.ipFooterTabDividerColor.value
    val footerTabIndicatorColor = PrefI.ipFooterTabIndicatorColor.value

    val colorColumnStripBackground = footerTabBgColor.notZero()
        ?: attrColor(R.attr.colorColumnStripBackground)

    svColumnStrip.setBackgroundColor(colorColumnStripBackground)
    llQuickPostBar.setBackgroundColor(colorColumnStripBackground)

    vBottomPadding.setBackgroundColor(colorColumnStripBackground)

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
    ivQuickTootAccount.backgroundDrawable =
        getAdaptiveRippleDrawableRound(this, colorButtonBg, colorButtonFg)
    btnQuickPostMenu.backgroundDrawable =
        getAdaptiveRippleDrawableRound(this, colorButtonBg, colorButtonFg)

    var c = footerButtonFgColor.notZero() ?: attrColor(R.attr.colorTextContent)
    val d = resDrawable(R.drawable.ic_question).wrapAndTint(color = c)
    ivQuickTootAccount.setDefaultImage(d)

    val csl = ColorStateList.valueOf(
        footerButtonFgColor.notZero() ?: attrColor(R.attr.colorTextContent)
    )
    btnToot.imageTintList = csl
    btnMenu.imageTintList = csl
    btnQuickToot.imageTintList = csl
    btnQuickPostMenu.imageTintList = csl

    c = footerTabDividerColor.notZero() ?: colorColumnStripBackground
    vFooterDivider1.setBackgroundColor(c)
    vFooterDivider2.setBackgroundColor(c)

    llColumnStrip.indicatorColor =
        footerTabIndicatorColor.notZero() ?: attrColor(R.attr.colorTextHelp)
}

fun ActMain.closePopup() {
    try {
        popupStatusButtons?.dismiss()
    } catch (ignored: Throwable) {
    }
    popupStatusButtons = null
}
