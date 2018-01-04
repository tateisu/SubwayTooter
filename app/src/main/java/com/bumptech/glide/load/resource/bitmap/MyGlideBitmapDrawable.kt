//package com.bumptech.glide.load.resource.bitmap
//
//import android.content.res.Resources
//import android.graphics.*
//import android.graphics.drawable.Drawable
//import android.util.DisplayMetrics
//import android.view.Gravity
//
//import com.bumptech.glide.load.resource.drawable.GlideDrawable
//
//import java.lang.reflect.Field
//
//import jp.juggler.subwaytooter.util.LogCategory
//
//class MyGlideBitmapDrawable private constructor(res : Resources?, private var state : GlideBitmapDrawable.BitmapState?) : GlideDrawable() {
//
//	companion object {
//
//		@Suppress("unused")
//		private val log = LogCategory("MyGlideBitmapDrawable")
//
//		private var field_state : Field? = null
//
//		private fun cloneState(other : GlideBitmapDrawable) : GlideBitmapDrawable.BitmapState {
//			try {
//				if(field_state == null) {
//					field_state = GlideBitmapDrawable::class.java.getDeclaredField("state")
//					field_state !!.isAccessible = true
//				}
//				val other_state = field_state !!.get(other) as GlideBitmapDrawable.BitmapState
//
//				return GlideBitmapDrawable.BitmapState(other_state)
//			} catch(ex : Throwable) {
//				throw RuntimeException("cloning GlideBitmapDrawable.BitmapState failed.", ex)
//			}
//
//		}
//	}
//
//	private val destRect = Rect()
//	private val width : Int
//	private val height : Int
//	private var applyGravity : Boolean = false
//	private var mutated : Boolean = false
//
//	private var mCornerRadius : Float = 0f
//
//	private val mShaderMatrix = Matrix()
//	private val mDstRectF = RectF()
//	private var mBitmapShader : BitmapShader? = null
//
//	val bitmap : Bitmap
//		get() = state !!.bitmap
//
//	constructor(res : Resources, other : GlideBitmapDrawable, radius : Float) : this(res, cloneState(other)) {
//		this.mCornerRadius = radius
//	}
//
//	init {
//		if(state == null) {
//			throw NullPointerException("BitmapState must not be null")
//		}
//		val targetDensity : Int
//		if(res != null) {
//			val density = res.displayMetrics.densityDpi
//			targetDensity = if(density == 0) DisplayMetrics.DENSITY_DEFAULT else density
//			state !!.targetDensity = targetDensity
//		} else {
//			targetDensity = state !!.targetDensity
//		}
//		width = state !!.bitmap.getScaledWidth(targetDensity)
//		height = state !!.bitmap.getScaledHeight(targetDensity)
//	}
//
//	override fun getIntrinsicWidth() : Int {
//		return width
//	}
//
//	override fun getIntrinsicHeight() : Int {
//		return height
//	}
//
//	override fun isAnimated() : Boolean {
//		return false
//	}
//
//	override fun setLoopCount(loopCount : Int) {
//		// Do nothing.
//	}
//
//	override fun start() {
//		// Do nothing.
//	}
//
//	override fun stop() {
//		// Do nothing.
//	}
//
//	override fun isRunning() : Boolean {
//		return false
//	}
//
//	override fun onBoundsChange(bounds : Rect) {
//		super.onBoundsChange(bounds)
//		applyGravity = true
//	}
//
//	override fun getConstantState() : Drawable.ConstantState? {
//		return state
//	}
//
//	override fun draw(canvas : Canvas) {
//		if(applyGravity) {
//			Gravity.apply(Gravity.FILL, width, height, bounds, destRect)
//			applyGravity = false
//			mBitmapShader = null
//		}
//
//		val toDraw = state !!.bitmap
//
//		if(mCornerRadius <= 0f) {
//			state !!.paint.shader = null
//			canvas.drawBitmap(toDraw, null, destRect, state !!.paint)
//			mBitmapShader = null
//		} else {
//			drawRoundImage(canvas, toDraw)
//		}
//	}
//
//	private fun drawRoundImage(canvas : Canvas, src : Bitmap?) {
//		if(src == null) return
//		val src_w = src.width
//		val src_h = src.height
//		if(src_w < 1 || src_h < 1) return
//
//		if(mBitmapShader == null) {
//			//			int outWidth = destRect.width();
//			//			int outHeight = destRect.height();
//			mDstRectF.set(destRect)
//			mShaderMatrix.preScale(mDstRectF.width() / src_w, mDstRectF.height() / src_h)
//
//			mBitmapShader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
//			mBitmapShader !!.setLocalMatrix(mShaderMatrix)
//		}
//
//		state !!.paint.shader = mBitmapShader
//		canvas.drawRoundRect(mDstRectF, mCornerRadius, mCornerRadius, state !!.paint)
//
//	}
//
//	override fun setAlpha(alpha : Int) {
//		val currentAlpha = state !!.paint.alpha
//		if(currentAlpha != alpha) {
//			state !!.setAlpha(alpha)
//			invalidateSelf()
//		}
//	}
//
//	override fun setColorFilter(colorFilter : ColorFilter?) {
//		state !!.setColorFilter(colorFilter)
//		invalidateSelf()
//	}
//
//	override fun getOpacity() : Int {
//		val bm = state !!.bitmap
//		return if(bm == null || bm.hasAlpha() || state !!.paint.alpha < 255)
//			PixelFormat.TRANSLUCENT
//		else
//			PixelFormat.OPAQUE
//	}
//
//	override fun mutate() : Drawable {
//		if(! mutated && super.mutate() === this) {
//			state = GlideBitmapDrawable.BitmapState(state !!)
//			mutated = true
//		}
//		return this
//	}
//
//
//
//}
