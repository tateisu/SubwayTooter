package jp.juggler.apng.sample

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import jp.juggler.apng.ApngFrames

class ApngView : View{
	
	private var wView : Float = 1f
	
	private var hView : Float = 1f
	
	private var aspectView: Float = 1f
	
	private var wImage : Float = 1f
	
	private var hImage : Float = 1f
	
	private var aspectImage : Float = 1f
	
	var apngFrames : ApngFrames? = null
		set(value) {
			timeShowStart = SystemClock.elapsedRealtime()
			field = value
			initializeScale()
		}
	
	private var currentScale : Float = 1f
	
	private var currentTransX :Float = 0f
	private var currentTransY :Float = 0f
	
	private var timeShowStart = SystemClock.elapsedRealtime()
	
	private val drawMatrix = Matrix()
	
	private val paint = Paint()
	
	private val findFrameResult = ApngFrames.FindFrameResult()
	
	constructor(context : Context) : super(context, null) {
		init(context)
	}
	constructor(context : Context, attrs : AttributeSet?) : super(context, attrs, 0) {
		init(context)
	}
	constructor(context : Context, attrs : AttributeSet?, defStyle : Int) : super(context, attrs, defStyle) {
		init(context)
	}

	private fun init(@Suppress("UNUSED_PARAMETER") context:Context){
		//
	}
	
	override fun onSizeChanged(w : Int, h : Int, oldw : Int, oldh : Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		
		wView = Math.max(1, w).toFloat()
		hView = Math.max(1, h).toFloat()
		aspectView = wView / hView
		
		initializeScale()
	}
	
	private fun initializeScale(){
		val apngFrames = this.apngFrames
		if( apngFrames != null) {
			wImage =Math.max(1, apngFrames.width).toFloat()
			hImage =Math.max(1, apngFrames.height).toFloat()
			aspectImage = wImage / hImage
			
			currentScale = if(aspectView > aspectImage) {
				hView / hImage
			} else {
				wView / wImage
			}
			
			val wDraw = wImage * currentScale
			val hDraw = hImage * currentScale
			
			currentTransX = (wView - wDraw) / 2f
			currentTransY = (hView - hDraw) / 2f
			
		} else {
			currentScale = 1f
			currentTransX = 0f
			currentTransY = 0f
		}
		invalidate()
	}
	

	override fun onDraw(canvas : Canvas) {
		super.onDraw(canvas)
		
		val apngFrames = this.apngFrames
		if(apngFrames != null ){
			
			val t = SystemClock.elapsedRealtime() - timeShowStart

			apngFrames.findFrame(findFrameResult,t)
			val delay = findFrameResult.delay
			val bitmap = findFrameResult.bitmap
			if( bitmap != null) {
				drawMatrix.reset()
				drawMatrix.postScale(currentScale, currentScale)
				drawMatrix.postTranslate(currentTransX, currentTransY)
				paint.isFilterBitmap = currentScale < 4f
				canvas.drawBitmap(bitmap, drawMatrix, paint)
				
				if( delay != Long.MAX_VALUE){
					postInvalidateDelayed(Math.max(1L,delay))
				}
			}
		}
	}
	
}