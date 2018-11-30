package jp.juggler.subwaytooter.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.support.v7.widget.RecyclerView
import android.view.View
import jp.juggler.subwaytooter.R
import jp.juggler.util.LogCategory
import jp.juggler.util.getAttributeDrawable

class TabletColumnDivider(context : Context) : RecyclerView.ItemDecoration() {
	
	companion object {
		private val log = LogCategory("TabletColumnDivider")

		var color :Int =0
		
		var barWidth : Int = 0
	}
	
	private val drawable = getAttributeDrawable(context, R.attr.colorSettingDivider)
	private val paint = Paint()
	private val rect = Rect()
	
	init {
		val density = context.resources.displayMetrics.density
		barWidth = ( density * 1f + 0.5f).toInt()
		paint.style = Paint.Style.FILL
		paint.isAntiAlias = true
	}
	
	override fun getItemOffsets(
		outRect : Rect,
		view : View,
		parent : RecyclerView,
		state : RecyclerView.State
	) {
		outRect.set(0, 0, barWidth, 0)
	}
	
	override fun onDraw(canvas : Canvas, parent : RecyclerView, state : RecyclerView.State) {
		val clip = canvas.clipBounds
		
		val top = clip.top
		val bottom = clip.bottom
		
		if( color != 0){
			paint.color = color
		}
		
		for(i in 0 until parent.childCount) {
			val child = parent.getChildAt(i)
			val params = child.layoutParams as RecyclerView.LayoutParams
			
			if( child.left >= clip.right ) break
			
			if( i == 0 ){
				// 左端
				val left = child.left - params.leftMargin
				val right = left + barWidth
				if( color != 0){
					rect.set(left, top, right, bottom)
					canvas.drawRect(rect,paint)
				}else {
					drawable.setBounds(left, top, right, bottom)
					drawable.draw(canvas)
				}
			}
			
			val left = child.right + params.rightMargin
			val right = left + barWidth
			
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