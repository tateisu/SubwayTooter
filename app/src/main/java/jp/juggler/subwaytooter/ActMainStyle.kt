package jp.juggler.subwaytooter

import android.content.res.ColorStateList
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.util.CustomShare
import jp.juggler.subwaytooter.view.ListDivider
import jp.juggler.subwaytooter.view.TabletColumnDivider
import jp.juggler.util.attrColor
import jp.juggler.util.getAdaptiveRippleDrawableRound
import jp.juggler.util.notZero
import org.jetbrains.anko.backgroundDrawable
import java.util.*

// onStart時に呼ばれる
fun ActMain.reloadTimeZone(){
    try {
        var tz = TimeZone.getDefault()
        val tzId = PrefS.spTimeZone(pref)
        if (tzId.isNotEmpty()) {
            tz = TimeZone.getTimeZone(tzId)
        }
        TootStatus.date_format.timeZone = tz
    } catch (ex: Throwable) {
        ActMain.log.e(ex, "getTimeZone failed.")
    }
}

// onStart時に呼ばれる
// カラーカスタマイズを読み直す
fun ActMain.reloadColors(){
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
