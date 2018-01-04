//package com.bumptech.glide.load.resource.gif
//
//import android.annotation.SuppressLint
//import android.annotation.TargetApi
//import android.graphics.*
//import android.graphics.drawable.Animatable
//import android.graphics.drawable.Drawable
//import android.os.Build
//import android.view.Gravity
//
//import com.bumptech.glide.gifdecoder.GifDecoder
//import com.bumptech.glide.load.Transformation
//import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
//
//import java.lang.reflect.Field
//
//import jp.juggler.subwaytooter.util.LogCategory
//
//import com.bumptech.glide.gifdecoder.GifDecoder.TOTAL_ITERATION_COUNT_FOREVER
//
//@SuppressLint("ObsoleteSdkInt")
//@Suppress("unused")
//class MyGifDrawable : Drawable, GifFrameLoader.FrameCallback, Animatable {
//
//
//	companion object {
//
//		internal val log = LogCategory("MyGifDrawable")
//
//		private val field_state : Field by lazy{
//			val rv = GifDrawable::class.java.getDeclaredField("state")
//			rv.isAccessible = true
//			rv
//		}
//
//		internal fun cloneState(other : GifDrawable) : GifDrawable.GifState {
//			try {
//				val other_state = field_state.get(other) as GifDrawable.GifState
//				val frameLoader : GifFrameLoader = other_state.frameLoader
//
//				return GifDrawable.GifState(
//					other_state.gifHeader,
//					other_state.data,
//					other_state.context,
//					other.frameTransformation,
//					other_state.targetWidth,
//					other_state.targetHeight,
//					other_state.bitmapProvider,
//					other_state.bitmapPool,
//					other.firstFrame
//				)
//			} catch(ex : Throwable) {
//				throw RuntimeException("cloning GifDrawable.GifState failed.", ex)
//			}
//
//		}
//	}
//
//	private val paint : Paint
//	private val destRect = Rect()
//	private val state : GifDrawable.GifState
//	private val decoder : GifDecoder
//	private val frameLoader : GifFrameLoader
//
//	/**
//	 * True if the drawable is currently animating.
//	 */
//	private var isRunning : Boolean = false
//	/**
//	 * True if the drawable should animate while visible.
//	 */
//	private var isStarted : Boolean = false
//	/**
//	 * True if the drawable's resources have been recycled.
//	 */
//	// For testing.
//	private var isRecycled : Boolean = false
//
//	override fun onFrameReady() {
//		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//	}
//
//
//	/**
//	 * True if the drawable is currently visible. Default to true because on certain platforms (at least 4.1.1),
//	 * setVisible is not called on [Drawables][android.graphics.drawable.Drawable] during
//	 * [android.widget.ImageView.setImageDrawable]. See issue #130.
//	 */
//	private var isVisibleX = true
//	/**
//	 * The number of times we've looped over all the frames in the gif.
//	 */
//	private var loopCount : Int = 0
//	/**
//	 * The number of times to loop through the gif animation.
//	 */
//	private var maxLoopCount = Animatable.LOOP_FOREVER
//
//	private var applyGravity : Boolean = false
//
//	private var mCornerRadius : Float = 0.toFloat()
//
//	val firstFrame : Bitmap
//		get() = state.firstFrame
//
//	val frameTransformation : Transformation<Bitmap>
//		get() = state.frameTransformation
//
//	val data : ByteArray
//		get() = state.data
//
//	val frameCount : Int
//		get() = decoder.frameCount
//
//	private val mShaderMatrix = Matrix()
//	private val mDstRectF = RectF()
//
//	constructor(other : GifDrawable, radius : Float) : this(cloneState(other)) {
//		this.mCornerRadius = radius
//	}
//
//	private constructor(state : GifDrawable.GifState) {
//
//		this.state = state
//		this.decoder = GifDecoder(state.bitmapProvider)
//		this.paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
//
//		decoder.setData(state.gifHeader, state.data)
//		frameLoader = GifFrameLoader(
//			state.context,
//			this,
//			decoder,
//			state.targetWidth,
//			state.targetHeight
//		)
//		frameLoader.setFrameTransformation(
//			state.frameTransformation
//		)
//	}
//
//	// Visible for testing.
//	internal constructor(
//		decoder : GifDecoder,
//		frameLoader : GifFrameLoader,
//		firstFrame : Bitmap,
//		bitmapPool : BitmapPool,
//		paint : Paint
//	) {
//		this.decoder = decoder
//		this.frameLoader = frameLoader
//		this.state = GifDrawable.GifState(null)
//		this.paint = paint
//		state.bitmapPool = bitmapPool
//		state.firstFrame = firstFrame
//	}
//
//	fun setFrameTransformation(frameTransformation : Transformation<Bitmap>?, firstFrame : Bitmap?) {
//		if(firstFrame == null) {
//			throw NullPointerException("The first frame of the GIF must not be null")
//		}
//		if(frameTransformation == null) {
//			throw NullPointerException("The frame transformation must not be null")
//		}
//		state.frameTransformation = frameTransformation
//		state.firstFrame = firstFrame
//		frameLoader.setFrameTransformation(frameTransformation)
//	}
//
//	private fun resetLoopCount() {
//		loopCount = 0
//	}
//
//	override fun start() {
//		log.d("start")
//		isStarted = true
//		resetLoopCount()
//		if(isVisibleX) {
//			startRunning()
//		}
//	}
//
//	override fun stop() {
//		isStarted = false
//		stopRunning()
//
//		// On APIs > honeycomb we know our drawable is not being displayed anymore when it's callback is cleared and so
//		// we can use the absence of a callback as an indication that it's ok to clear our temporary data. Prior to
//		// honeycomb we can't tell if our callback is null and instead eagerly reset to avoid holding on to resources we
//		// no longer need.
//		if(Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
//			reset()
//		}
//	}
//
//	/**
//	 * Clears temporary data and resets the drawable back to the first frame.
//	 */
//	private fun reset() {
//		frameLoader.clear()
//		invalidateSelf()
//	}
//
//	private fun startRunning() {
//		// If we have only a single frame, we don't want to decode it endlessly.
//		if(decoder.frameCount == 1) {
//			invalidateSelf()
//		} else if(! isRunning) {
//			isRunning = true
//			frameLoader.start()
//			invalidateSelf()
//		}
//	}
//
//	private fun stopRunning() {
//		isRunning = false
//		frameLoader.stop()
//	}
//
//	override fun setVisible(visible : Boolean, restart : Boolean) : Boolean {
//		isVisibleX = visible
//		if(! visible) {
//			stopRunning()
//		} else if(isStarted) {
//			startRunning()
//		}
//		return super.setVisible(visible, restart)
//	}
//
//	override fun getIntrinsicWidth() : Int {
//		return state.firstFrame.width
//	}
//
//	override fun getIntrinsicHeight() : Int {
//		return state.firstFrame.height
//	}
//
//	override fun isRunning() : Boolean {
//		return isRunning
//	}
//
//	// For testing.
//	internal fun setIsRunning(isRunning : Boolean) {
//		this.isRunning = isRunning
//	}
//
//	override fun onBoundsChange(bounds : Rect) {
//		super.onBoundsChange(bounds)
//		applyGravity = true
//	}
//
//	override fun draw(canvas : Canvas) {
//		if(isRecycled) {
//			return
//		}
//
//		if(applyGravity) {
//			Gravity.apply(Gravity.FILL, intrinsicWidth, intrinsicHeight, bounds, destRect)
//			applyGravity = false
//		}
//
//		val currentFrame = frameLoader.currentFrame
//		val toDraw = currentFrame ?: state.firstFrame
//
//		if(mCornerRadius <= 0f) {
//			paint.shader = null
//			canvas.drawBitmap(toDraw, null, destRect, paint)
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
//		//		int outWidth = destRect.width();
//		//		int outHeight = destRect.height();
//
//		mDstRectF.set(destRect)
//		mShaderMatrix.preScale(mDstRectF.width() / src_w, mDstRectF.height() / src_h)
//
//		val mBitmapShader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
//		mBitmapShader.setLocalMatrix(mShaderMatrix)
//
//		paint.shader = mBitmapShader
//		canvas.drawRoundRect(mDstRectF, mCornerRadius, mCornerRadius, paint)
//
//	}
//
//	override fun setAlpha(i : Int) {
//		paint.alpha = i
//	}
//
//	override fun setColorFilter(colorFilter : ColorFilter?) {
//		paint.colorFilter = colorFilter
//	}
//
//	override fun getOpacity() : Int {
//		// We can't tell, so default to transparent to be safe.
//		return PixelFormat.TRANSPARENT
//	}
//
//	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
//	override fun onFrameReady(frameIndex : Int) {
//		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && callback == null) {
//			stop()
//			reset()
//			return
//		}
//
//		invalidateSelf()
//
//		if(frameIndex == decoder.frameCount - 1) {
//			loopCount ++
//		}
//
//		if(maxLoopCount != GlideDrawable.LOOP_FOREVER && loopCount >= maxLoopCount) {
//			stop()
//		}
//	}
//
//	override fun getConstantState() : Drawable.ConstantState? {
//		return state
//	}
//
//	/**
//	 * Clears any resources for loading frames that are currently held on to by this object.
//	 */
//	fun recycle() {
//		isRecycled = true
//		state.bitmapPool.put(state.firstFrame)
//		frameLoader.clear()
//		frameLoader.stop()
//	}
//
//	override fun isAnimated() : Boolean {
//		return true
//	}
//
//	override fun setLoopCount(loopCount : Int) {
//		if(loopCount <= 0 && loopCount != GlideDrawable.LOOP_FOREVER && loopCount != GlideDrawable.LOOP_INTRINSIC) {
//			throw IllegalArgumentException("Loop count must be greater than 0, or equal to " + "GlideDrawable.LOOP_FOREVER, or equal to GlideDrawable.LOOP_INTRINSIC")
//		}
//
//		maxLoopCount = if(loopCount == GlideDrawable.LOOP_INTRINSIC) {
//			val intrinsicCount = decoder.totalIterationCount
//			if(intrinsicCount == TOTAL_ITERATION_COUNT_FOREVER) GlideDrawable.LOOP_FOREVER else intrinsicCount
//		} else {
//			loopCount
//		}
//	}
//
//
//}
