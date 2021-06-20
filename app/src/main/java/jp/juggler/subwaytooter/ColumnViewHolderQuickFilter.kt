package jp.juggler.subwaytooter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import jp.juggler.util.applyAlphaMultiplier
import jp.juggler.util.attrColor
import jp.juggler.util.getAdaptiveRippleDrawableRound
import jp.juggler.util.vg
import org.jetbrains.anko.backgroundDrawable
import org.jetbrains.anko.textColor

fun ColumnViewHolder.clickQuickFilter(filter: Int) {
    column?.quickFilter = filter
    showQuickFilter()
    activity.appState.saveColumnList()
    column?.startLoading()
}

fun ColumnViewHolder.showQuickFilter() {
    val column = this.column ?: return

    svQuickFilter.vg(column.isNotificationColumn) ?: return

    btnQuickFilterReaction.vg(column.isMisskey)
    btnQuickFilterFavourite.vg(!column.isMisskey)

    val insideColumnSetting = Pref.bpMoveNotificationsQuickFilter(activity.pref)

    val showQuickFilterButton: (btn: View, iconId: Int, selected: Boolean) -> Unit

    if (insideColumnSetting) {
        svQuickFilter.setBackgroundColor(0)

        val colorFg = activity.attrColor(R.attr.colorContentText)
        val colorBgSelected = colorFg.applyAlphaMultiplier(0.25f)
        val colorFgList = ColorStateList.valueOf(colorFg)
        val colorBg = activity.attrColor(R.attr.colorColumnSettingBackground)
        showQuickFilterButton = { btn, iconId, selected ->
            btn.backgroundDrawable =
                getAdaptiveRippleDrawableRound(
                    activity,
                    if (selected) colorBgSelected else colorBg,
                    colorFg,
                    roundNormal = true
                )

            when (btn) {
                is TextView -> btn.textColor = colorFg

                is ImageButton -> {
                    btn.setImageResource(iconId)
                    btn.imageTintList = colorFgList
                }
            }
        }
    } else {
        val colorBg = column.getHeaderBackgroundColor()
        val colorFg = column.getHeaderNameColor()
        val colorFgList = ColorStateList.valueOf(colorFg)
        val colorBgSelected = Color.rgb(
            (Color.red(colorBg) * 3 + Color.red(colorFg)) / 4,
            (Color.green(colorBg) * 3 + Color.green(colorFg)) / 4,
            (Color.blue(colorBg) * 3 + Color.blue(colorFg)) / 4
        )
        svQuickFilter.setBackgroundColor(colorBg)

        showQuickFilterButton = { btn, iconId, selected ->

            btn.backgroundDrawable = getAdaptiveRippleDrawableRound(
                activity,
                if (selected) colorBgSelected else colorBg,
                colorFg
            )

            when (btn) {
                is TextView -> btn.textColor = colorFg

                is ImageButton -> {
                    btn.setImageResource(iconId)
                    btn.imageTintList = colorFgList
                }
            }
        }
    }

    showQuickFilterButton(
        btnQuickFilterAll,
        0,
        column.quickFilter == Column.QUICK_FILTER_ALL
    )

    showQuickFilterButton(
        btnQuickFilterMention,
        R.drawable.ic_reply,
        column.quickFilter == Column.QUICK_FILTER_MENTION
    )

    showQuickFilterButton(
        btnQuickFilterFavourite,
        R.drawable.ic_star,
        column.quickFilter == Column.QUICK_FILTER_FAVOURITE
    )

    showQuickFilterButton(
        btnQuickFilterBoost,
        R.drawable.ic_repeat,
        column.quickFilter == Column.QUICK_FILTER_BOOST
    )

    showQuickFilterButton(
        btnQuickFilterFollow,
        R.drawable.ic_follow_plus,
        column.quickFilter == Column.QUICK_FILTER_FOLLOW
    )

    showQuickFilterButton(
        btnQuickFilterPost,
        R.drawable.ic_send,
        column.quickFilter == Column.QUICK_FILTER_POST
    )

    showQuickFilterButton(
        btnQuickFilterReaction,
        R.drawable.ic_add,
        column.quickFilter == Column.QUICK_FILTER_REACTION
    )

    showQuickFilterButton(
        btnQuickFilterVote,
        R.drawable.ic_vote,
        column.quickFilter == Column.QUICK_FILTER_VOTE
    )
}
