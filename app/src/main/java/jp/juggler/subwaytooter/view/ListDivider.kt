package jp.juggler.subwaytooter.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.support.v7.widget.RecyclerView
import android.graphics.drawable.Drawable
import android.view.View
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.Styler

class ListDivider(context : Context) : RecyclerView.ItemDecoration() {

	companion object {
		var height : Int =0
	}

	private val drawable : Drawable
	
	init {
		drawable = Styler.getAttributeDrawable(context, R.attr.colorSettingDivider)
		height = (context.resources.displayMetrics.density * 1f +0.5f).toInt()
	}
	
	override fun getItemOffsets(outRect : Rect, view : View, parent : RecyclerView, state : RecyclerView.State) {
		outRect.set(0, 0, 0, height)
	}
	
	override fun onDraw(canvas : Canvas, parent : RecyclerView, state : RecyclerView.State) {
		val left = parent.paddingLeft
		val right = parent.width - parent.paddingRight
		
		for(i in 0 until parent.childCount) {
			val child = parent.getChildAt(i)
			val params = child.layoutParams as RecyclerView.LayoutParams
			val top = child.bottom + params.bottomMargin
			val bottom = top + height
			drawable.setBounds(left, top, right, bottom)
			drawable.draw(canvas)
		}
	}

}