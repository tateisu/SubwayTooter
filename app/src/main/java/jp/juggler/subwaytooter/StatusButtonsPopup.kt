package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.PopupWindow

import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.view.MyListView

class StatusButtonsPopup(
	private val activity : ActMain,
	column : Column,
	bSimpleList : Boolean
) {
	
	companion object {
		@Suppress("unused")
		private var log = LogCategory("StatusButtonsPopup")
		
		private fun getViewWidth(v : View) : Int {
			val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
			v.measure(spec, spec)
			return v.measuredWidth
		}
	}
	
	private val viewRoot : View
	private val buttons_for_status : StatusButtons
	private var window : PopupWindow? = null
	
	init {
		@SuppressLint("InflateParams")
		this.viewRoot = activity.layoutInflater.inflate(R.layout.list_item_popup, null, false)
		this.buttons_for_status = StatusButtons(activity, column, viewRoot, bSimpleList)
	}
	
	fun dismiss() {
		val window = this.window
		if(window != null && window.isShowing) {
			window.dismiss()
		}
	}
	
	@SuppressLint("RtlHardcoded")
	fun show(
		listView : MyListView
		, anchor : View
		, status : TootStatus
		, notification : TootNotification?
	) {
		
		val window = PopupWindow(activity)
		this.window = window
		
		window.width = WindowManager.LayoutParams.WRAP_CONTENT
		window.height = WindowManager.LayoutParams.WRAP_CONTENT
		window.contentView = viewRoot
		window.setBackgroundDrawable(ColorDrawable(0x00000000))
		window.isTouchable = true
		window.isOutsideTouchable = true
		window.setTouchInterceptor(View.OnTouchListener { _, event ->
			if(event.action == MotionEvent.ACTION_OUTSIDE) {
				// ポップアップの外側をタッチしたらポップアップを閉じる
				// また、そのタッチイベントがlistViewに影響しないようにする
				window.dismiss()
				listView.last_popup_close = SystemClock.elapsedRealtime()
				return@OnTouchListener true
			}
			false
		})
		
		buttons_for_status.bind(status, notification)
		buttons_for_status.close_window = window
		
		val location = IntArray(2)
		
		anchor.getLocationOnScreen(location)
		val anchor_left = location[0]
		val anchor_top = location[1]
		
		listView.getLocationOnScreen(location)
		val listView_top = location[1]
		
		val density = activity.density
		
		val clip_top = listView_top + (0.5f + 8f * density).toInt()
		val clip_bottom = listView_top + listView.height - (0.5f + 8f * density).toInt()
		
		val popup_height = (0.5f + (56f + 24f) * density).toInt()
		var popup_y = anchor_top + anchor.height / 2
		
		if(popup_y < clip_top) {
			// 画面外のは画面内にする
			popup_y = clip_top
		} else if(clip_bottom - popup_y < popup_height) {
			// 画面外のは画面内にする
			if(popup_y > clip_bottom) popup_y = clip_bottom
			
			// 画面の下側にあるならポップアップの吹き出しが下から出ているように見せる
			viewRoot.findViewById<View>(R.id.ivTriangleTop).visibility = View.GONE
			viewRoot.findViewById<View>(R.id.ivTriangleBottom).visibility = View.VISIBLE
			popup_y -= popup_height
		}
		
		val anchor_width = anchor.width
		val popup_width = getViewWidth(viewRoot)
		var popup_x = anchor_left + anchor_width / 2 - popup_width / 2
		if(popup_x < 0) popup_x = 0
		val popup_x_max = activity.resources.displayMetrics.widthPixels - popup_width
		if(popup_x > popup_x_max) popup_x = popup_x_max
		
		window.showAtLocation(
			listView, Gravity.LEFT or Gravity.TOP, popup_x, popup_y
		)
	}
}