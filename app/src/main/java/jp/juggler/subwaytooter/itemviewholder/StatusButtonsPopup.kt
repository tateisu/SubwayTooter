package jp.juggler.subwaytooter.itemviewholder

import android.annotation.SuppressLint
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.recyclerview.widget.RecyclerView
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.column.Column
import jp.juggler.util.LogCategory
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

    private val viewRoot: View
    private val buttonsForStatus: StatusButtons
    private var window: PopupWindow? = null

    init {
        @SuppressLint("InflateParams")
        this.viewRoot = activity.layoutInflater.inflate(R.layout.list_item_popup, null, false)
        val statusButtonsViewHolder = StatusButtonsViewHolder(activity, matchParent, 0f)
        viewRoot.findViewById<LinearLayout>(R.id.llBarPlaceHolder)
            .addView(statusButtonsViewHolder.viewRoot)
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
        window.contentView = viewRoot
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

        val density = activity.density
        fun dip(src: Float) = (src * density + 0.5f).toInt()

        val location = IntArray(2)

        listView.getLocationInWindow(location)
        val listviewTop = location[1]

        anchor.getLocationInWindow(location)
        val anchorLeft = location[0]
        val anchorTop = location[1]
        val anchorWidth = anchor.width
        val anchorHeight = anchor.height

        // popupの大きさ
        viewRoot.measure(
            View.MeasureSpec.makeMeasureSpec(listView.width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(listView.height, View.MeasureSpec.AT_MOST)
        )
        val popupWidth = viewRoot.measuredWidth
        val popupHeight = viewRoot.measuredHeight

        val clipTop = listviewTop + dip(8f)
        val clipBottom = listviewTop + listView.height - dip(8f)

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
            viewRoot.findViewById<View>(R.id.ivTriangleTop).visibility = View.GONE
            viewRoot.findViewById<View>(R.id.ivTriangleBottom).visibility = View.VISIBLE
            popupY -= popupHeight
        }

        window.showAtLocation(listView, Gravity.LEFT or Gravity.TOP, popupX, popupY)
    }
}
