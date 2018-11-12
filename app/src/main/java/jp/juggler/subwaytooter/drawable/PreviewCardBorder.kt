package jp.juggler.subwaytooter.drawable

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

class PreviewCardBorder : Drawable() {
	
	var color = 0
	var round = 0f
	var width = 1f
	
	private val paint = Paint()
	
	override fun draw(canvas : Canvas) {
		paint.isAntiAlias = true
		paint.color = color
		paint.style = Paint.Style.STROKE
		paint.strokeWidth = width
		
		val bounds = this.bounds
		val left = bounds.left + width/2
		val right = bounds.right -width/2
		val top = bounds.top + width/2
		val bottom = bounds.bottom -width/2
		
		canvas.drawRoundRect( left,top,right,bottom,round,round,paint )
	}
	
	override fun getOpacity() : Int = PixelFormat.TRANSLUCENT

	override fun setAlpha(alpha : Int) =Unit
	
	override fun setColorFilter(colorFilter : ColorFilter?) =Unit
	
	
}