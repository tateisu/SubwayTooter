package jp.juggler.subwaytooter.itemviewholder

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.PopupWindow
import androidx.recyclerview.widget.RecyclerView
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.column.Column
import jp.juggler.subwaytooter.databinding.ListItemPopupBinding
import jp.juggler.subwaytooter.pref.PrefI
import jp.juggler.util.data.*
import jp.juggler.util.log.*
import jp.juggler.util.ui.*
import org.jetbrains.anko.matchParent
import kotlin.math.max

internal class StatusButtonsPopup(
    private val activity: ActMain,
    column: Column,
    bSimpleList: Boolean,
    itemViewHolder: ItemViewHolder,
) {

    companion object {

        @Suppress("unused")
        private var log = LogCategory("StatusButtonsPopup")

        var lastPopupClose = 0L
    }

    private val views = ListItemPopupBinding.inflate(activity.layoutInflater)
    private val buttonsForStatus: StatusButtons
    private var window: PopupWindow? = null

    init {
        @SuppressLint("InflateParams")
        val statusButtonsViewHolder = StatusButtonsViewHolder(activity, matchParent, 0f)
        views.llBarPlaceHolder.addView(statusButtonsViewHolder.viewRoot)
        this.buttonsForStatus = StatusButtons(
            activity,
            column,
            bSimpleList,
            statusButtonsViewHolder,
            itemViewHolder
        )
    }

    fun dismiss() {
        val window = this.window
        if (window != null && window.isShowing) {
            window.dismiss()
        }
    }

    @SuppressLint("RtlHardcoded", "ClickableViewAccessibility")
    fun show(
        listView: RecyclerView,
        anchor: View,
        status: TootStatus,
        notification: TootNotification?,
    ) {
        val window = PopupWindow(activity)
        this.window = window

        window.width = WindowManager.LayoutParams.WRAP_CONTENT
        window.height = WindowManager.LayoutParams.WRAP_CONTENT
        window.contentView = views.root
        window.setBackgroundDrawable(ColorDrawable(0x00000000))
        window.isTouchable = true
        window.isOutsideTouchable = true
        window.setTouchInterceptor { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                // ポップアップの外側をタッチしたらポップアップを閉じる
                // また、そのタッチイベントがlistViewに影響しないようにする
                window.dismiss()
                lastPopupClose = SystemClock.elapsedRealtime()
                true
            } else {
                false
            }
        }

        buttonsForStatus.bind(status, notification)
        buttonsForStatus.closeWindow = window

        val bgColor = PrefI.ipPopupBgColor.value
            .notZero() ?: activity.attrColor(R.attr.colorStatusButtonsPopupBg)
        val bgColorState = ColorStateList.valueOf(bgColor)
        views.ivTriangleTop.backgroundTintList = bgColorState
        views.ivTriangleBottom.backgroundTintList = bgColorState
        views.llBarPlaceHolder.backgroundTintList = bgColorState

        val density = activity.density
        fun Int.dp() = (this * density + 0.5f).toInt()

        // popupの大きさ
        views.root.measure(
            View.MeasureSpec.makeMeasureSpec(listView.width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(listView.height, View.MeasureSpec.AT_MOST)
        )
        val popupWidth = views.root.measuredWidth
        val popupHeight = views.root.measuredHeight

        val location = IntArray(2)

        listView.getLocationInWindow(location)
        val listviewTop = location[1]
        val clipTop = listviewTop + 8.dp()
        val clipBottom = listviewTop + listView.height - 8.dp()

        anchor.getLocationInWindow(location)
        val anchorLeft = location[0]
        val anchorTop = location[1]
        val anchorWidth = anchor.width
        val anchorHeight = anchor.height

        // ポップアップウィンドウの左上（基準は親ウィンドウの左上)
        val popupX = anchorLeft + max(0, (anchorWidth - popupWidth) / 2)
        var popupY = anchorTop + anchorHeight / 2
        if (popupY < clipTop) {
            // 画面外のは画面内にする
            popupY = clipTop
        } else if (popupY + popupHeight > clipBottom) {
            // 画面外のは画面内にする
            if (popupY > clipBottom) popupY = clipBottom

            // 画面の下側にあるならポップアップの吹き出しが下から出ているように見せる
            views.ivTriangleTop.visibility = View.GONE
            views.ivTriangleBottom.visibility = View.VISIBLE
            popupY -= popupHeight
        }

        window.showAtLocation(listView, Gravity.LEFT or Gravity.TOP, popupX, popupY)
    }
}
