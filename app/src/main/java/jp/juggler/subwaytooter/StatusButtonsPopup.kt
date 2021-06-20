package jp.juggler.subwaytooter

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
import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.util.LogCategory
import org.jetbrains.anko.matchParent

internal class StatusButtonsPopup(
    private val activity: ActMain,
    column: Column,
    bSimpleList: Boolean,
    itemViewHolder: ItemViewHolder,
) {

    companion object {

        @Suppress("unused")
        private var log = LogCategory("StatusButtonsPopup")

        private fun getViewWidth(v: View): Int {
            val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            v.measure(spec, spec)
            return v.measuredWidth
        }

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

        val location = IntArray(2)

        anchor.getLocationOnScreen(location)
        val anchorLeft = location[0]
        val anchorTop = location[1]

        listView.getLocationOnScreen(location)
        val listviewTop = location[1]

        val density = activity.density

        val clipTop = listviewTop + (0.5f + 8f * density).toInt()
        val clipBottom = listviewTop + listView.height - (0.5f + 8f * density).toInt()

        val popupHeight = (0.5f + (56f + 24f) * density).toInt()
        var popupY = anchorTop + anchor.height / 2

        if (popupY < clipTop) {
            // 画面外のは画面内にする
            popupY = clipTop
        } else if (clipBottom - popupY < popupHeight) {
            // 画面外のは画面内にする
            if (popupY > clipBottom) popupY = clipBottom

            // 画面の下側にあるならポップアップの吹き出しが下から出ているように見せる
            viewRoot.findViewById<View>(R.id.ivTriangleTop).visibility = View.GONE
            viewRoot.findViewById<View>(R.id.ivTriangleBottom).visibility = View.VISIBLE
            popupY -= popupHeight
        }

        val anchorWidth = anchor.width
        val popupWidth = getViewWidth(viewRoot)
        var popupX = anchorLeft + anchorWidth / 2 - popupWidth / 2
        if (popupX < 0) popupX = 0
        val popupXMax = activity.resources.displayMetrics.widthPixels - popupWidth
        if (popupX > popupXMax) popupX = popupXMax

        window.showAtLocation(listView, Gravity.LEFT or Gravity.TOP, popupX, popupY)
    }
}
