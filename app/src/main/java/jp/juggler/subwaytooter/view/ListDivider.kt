package jp.juggler.subwaytooter.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.support.v7.widget.RecyclerView
import android.view.View
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.Styler

class ListDivider(context : Context) : RecyclerView.ItemDecoration() {
	
	companion object {

		var color :Int =0
		
		var height : Int = 0

	}
	
	private val drawable = Styler.getAttributeDrawable(context, R.attr.colorSettingDivider)
	private val paint = Paint()
	private val rect = Rect()
	
	init {
		height = (context.resources.displayMetrics.density * 1f + 0.5f).toInt()
		paint.style = Paint.Style.FILL
		paint.isAntiAlias = true
	}
	
	override fun getItemOffsets(
		outRect : Rect,
		view : View,
		parent : RecyclerView,
		state : RecyclerView.State
	) {
		outRect.set(0, 0, 0, height)
	}
	
	override fun onDraw(canvas : Canvas, parent : RecyclerView, state : RecyclerView.State) {
		val left = parent.paddingLeft
		val right = parent.width - parent.paddingRight
		
		if( color != 0){
			paint.color = color
		}
		
		for(i in 0 until parent.childCount) {
			val child = parent.getChildAt(i)
			val params = child.layoutParams as RecyclerView.LayoutParams
			val top = child.bottom + params.bottomMargin
			val bottom = top + height
			if( color != 0){
				rect.set(left, top, right, bottom)
				canvas.drawRect(rect,paint)
			}else {
				drawable.setBounds(left, top, right, bottom)
				drawable.draw(canvas)
			}
		}
	}
	
}