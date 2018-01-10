package com.bumptech.glide.load.resource.gif

import com.bumptech.glide.gifdecoder.GifDecoder.TOTAL_ITERATION_COUNT_FOREVER

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.support.annotation.VisibleForTesting
import android.view.Gravity
import com.bumptech.glide.Glide
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.util.Preconditions
import jp.juggler.subwaytooter.util.LogCategory
import java.lang.reflect.Field
import java.nio.ByteBuffer

/**
 * An animated [android.graphics.drawable.Drawable] that plays the frames of an animated GIF.
 */
@Suppress("unused", "UNUSED_PARAMETER", "MemberVisibilityCanPrivate")
class MyGifDrawable internal constructor(state : GifDrawable.GifState) : Drawable(), GifFrameLoader.FrameCallback, Animatable {
	
	private val state : GifDrawable.GifState
	/**
	 * True if the drawable is currently animating.
	 */
	private var isRunning : Boolean = false
	/**
	 * True if the drawable should animate while visible.
	 */
	private var isStarted : Boolean = false
	/**
	 * True if the drawable's resources have been recycled.
	 */
	// For testing.
	internal var isRecycled : Boolean = false
		private set
	/**
	 * True if the drawable is currently visible. Default to true because on certain platforms (at
	 * least 4.1.1), setVisible is not called on [Drawables][android.graphics.drawable.Drawable]
	 * during [android.widget.ImageView.setImageDrawable].
	 * See issue #130.
	 */
	private var isVisibleX = true
	/**
	 * The number of times we've looped over all the frames in the GIF.
	 */
	private var loopCount : Int = 0
	/**
	 * The number of times to loop through the GIF animation.
	 */
	private var maxLoopCount = LOOP_FOREVER
	
	private var applyGravity : Boolean = false
	
	private var destRect : Rect? = null
	
	val size : Int
		get() = state.frameLoader.size
	
	private var mCornerRadius : Float = 0f
	
	val firstFrame : Bitmap
		get() = state.frameLoader.firstFrame
	
	val frameTransformation : Transformation<Bitmap>
		get() = state.frameLoader.frameTransformation
	
	val buffer : ByteBuffer
		get() = state.frameLoader.buffer
	
	val frameCount : Int
		get() = state.frameLoader.frameCount
	
	/**
	 * Returns the current frame index in the range 0..[.getFrameCount] - 1, or -1 if no frame
	 * is displayed.
	 */
	// Public API.
	val frameIndex : Int
		get() = state.frameLoader.currentIndex
	
	/**
	 * Constructor for GifDrawable.
	 *
	 * @param context             A context.
	 * @param bitmapPool          Ignored, see deprecation note.
	 * @param frameTransformation An [com.bumptech.glide.load.Transformation] that can be
	 * applied to each frame.
	 * @param targetFrameWidth    The desired width of the frames displayed by this drawable (the
	 * width of the view or
	 * [com.bumptech.glide.request.target.Target]
	 * this drawable is being loaded into).
	 * @param targetFrameHeight   The desired height of the frames displayed by this drawable (the
	 * height of the view or
	 * [com.bumptech.glide.request.target.Target]
	 * this drawable is being loaded into).
	 * @param gifDecoder          The decoder to use to decode GIF data.
	 * @param firstFrame          The decoded and transformed first frame of this GIF.
	 * @see .setFrameTransformation
	 */
	@Deprecated("Use {@link #GifDrawable(Context, GifDecoder, Transformation, int, int, Bitmap)}")
	constructor(
		context : Context,
		gifDecoder : GifDecoder,
		bitmapPool : BitmapPool,
		frameTransformation : Transformation<Bitmap>,
		targetFrameWidth : Int,
		targetFrameHeight : Int,
		firstFrame : Bitmap) : this(context, gifDecoder, frameTransformation, targetFrameWidth, targetFrameHeight, firstFrame)
	
	/**
	 * Constructor for GifDrawable.
	 *
	 * @param context             A context.
	 * @param frameTransformation An [com.bumptech.glide.load.Transformation] that can be
	 * applied to each frame.
	 * @param targetFrameWidth    The desired width of the frames displayed by this drawable (the
	 * width of the view or
	 * [com.bumptech.glide.request.target.Target]
	 * this drawable is being loaded into).
	 * @param targetFrameHeight   The desired height of the frames displayed by this drawable (the
	 * height of the view or
	 * [com.bumptech.glide.request.target.Target]
	 * this drawable is being loaded into).
	 * @param gifDecoder          The decoder to use to decode GIF data.
	 * @param firstFrame          The decoded and transformed first frame of this GIF.
	 * @see .setFrameTransformation
	 */
	constructor(
		context : Context,
		gifDecoder : GifDecoder,
		frameTransformation : Transformation<Bitmap>,
		targetFrameWidth : Int,
		targetFrameHeight : Int,
		firstFrame : Bitmap
	) : this(
		GifDrawable.GifState(
			GifFrameLoader(
				// TODO(b/27524013): Factor out this call to Glide.get()
				Glide.get(context),
				gifDecoder,
				targetFrameWidth,
				targetFrameHeight,
				frameTransformation,
				firstFrame)
		)
	)
	
	constructor(other : GifDrawable, radius : Float) : this(cloneState(other)) {
		this.mCornerRadius = radius
	}
	
	init {
		this.state = Preconditions.checkNotNull(state)
	}
	
	@VisibleForTesting
	internal constructor(frameLoader : GifFrameLoader, paint : Paint) : this(GifDrawable.GifState(frameLoader)) {
		this.paintX = paint
	}
	
	// Public API.
	fun setFrameTransformation(frameTransformation : Transformation<Bitmap>,
	                           firstFrame : Bitmap) {
		state.frameLoader.setFrameTransformation(frameTransformation, firstFrame)
	}
	
	private fun resetLoopCount() {
		loopCount = 0
	}
	
	/**
	 * Starts the animation from the first frame. Can only be called while animation is not running.
	 */
	// Public API.
	fun startFromFirstFrame() {
		Preconditions.checkArgument(! isRunning, "You cannot restart a currently running animation.")
		state.frameLoader.setNextStartFromFirstFrame()
		start()
	}
	
	override fun start() {
		isStarted = true
		resetLoopCount()
		if(isVisibleX) {
			startRunning()
		}
	}
	
	override fun stop() {
		isStarted = false
		stopRunning()
	}
	
	private fun startRunning() {
		Preconditions.checkArgument(! isRecycled, "You cannot start a recycled Drawable. Ensure that" + "you clear any references to the Drawable when clearing the corresponding request.")
		// If we have only a single frame, we don't want to decode it endlessly.
		if(state.frameLoader.frameCount == 1) {
			invalidateSelf()
		} else if(! isRunning) {
			isRunning = true
			state.frameLoader.subscribe(this)
			invalidateSelf()
		}
	}
	
	private fun stopRunning() {
		isRunning = false
		state.frameLoader.unsubscribe(this)
	}
	
	override fun setVisible(visible : Boolean, restart : Boolean) : Boolean {
		Preconditions.checkArgument(! isRecycled, "Cannot change the visibility of a recycled resource."
			+ " Ensure that you unset the Drawable from your View before changing the View's"
			+ " visibility.")
		isVisibleX = visible
		if(! visible) {
			stopRunning()
		} else if(isStarted) {
			startRunning()
		}
		return super.setVisible(visible, restart)
	}
	
	override fun getIntrinsicWidth() : Int {
		return state.frameLoader.width
	}
	
	override fun getIntrinsicHeight() : Int {
		return state.frameLoader.height
	}
	
	override fun isRunning() : Boolean {
		return isRunning
	}
	
	// For testing.
	internal fun setIsRunning(isRunning : Boolean) {
		this.isRunning = isRunning
	}
	
	override fun onBoundsChange(bounds : Rect) {
		super.onBoundsChange(bounds)
		applyGravity = true
	}
	
	override fun draw(canvas : Canvas) {
		if(isRecycled) {
			return
		}
		
		if(applyGravity) {
			Gravity.apply(GRAVITY, intrinsicWidth, intrinsicHeight, bounds, getDestRect())
			applyGravity = false
		}
		
		val currentFrame = state.frameLoader.currentFrame
		
		if(mCornerRadius <= 0f) {
			val paint = getPaint()
			paint.shader = null
			canvas.drawBitmap(currentFrame, null, getDestRect(), paint)
		} else {
			drawRoundImage(canvas, currentFrame)
		}
		
	}
	
	private val mShaderMatrix = Matrix()
	private val mDstRectF = RectF()
	
	private fun drawRoundImage(canvas : Canvas, src : Bitmap?) {
		if(src == null) return
		val src_w = src.width
		val src_h = src.height
		if(src_w < 1 || src_h < 1) return
		//		int outWidth = destRect.width();
		//		int outHeight = destRect.height();
		
		mDstRectF.set(destRect)
		mShaderMatrix.reset()
		mShaderMatrix.preScale(mDstRectF.width() / src_w, mDstRectF.height() / src_h)
		
		val mBitmapShader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
		mBitmapShader.setLocalMatrix(mShaderMatrix)
		
		val paint = getPaint()
		paint.shader = mBitmapShader
		canvas.drawRoundRect(mDstRectF, mCornerRadius, mCornerRadius, paint)
		
	}
	
	override fun setAlpha(i : Int) {
		getPaint().alpha = i
	}
	
	override fun setColorFilter(colorFilter : ColorFilter?) {
		getPaint().colorFilter = colorFilter
	}
	
	private fun getDestRect() : Rect {
		var r = destRect
		if(r == null) {
			r = Rect()
			destRect = r
		}
		return r
	}
	
	private var paintX : Paint? = null
	private fun getPaint() : Paint {
		var p = paintX
		if(p == null) {
			p = Paint(Paint.FILTER_BITMAP_FLAG)
			paintX = p
		}
		return p
	}
	
	override fun getOpacity() : Int {
		// We can't tell, so default to transparent to be safe.
		return PixelFormat.TRANSPARENT
	}
	
	// See #1087.
	private fun findCallback() : Drawable.Callback? {
		var callback : Drawable.Callback? = callback
		while(callback is Drawable) {
			callback = (callback as Drawable).callback
		}
		return callback
	}
	
	override fun onFrameReady() {
		if(findCallback() == null) {
			stop()
			invalidateSelf()
			return
		}
		
		invalidateSelf()
		
		if(frameIndex == frameCount - 1) {
			loopCount ++
		}
		
		if(maxLoopCount != LOOP_FOREVER && loopCount >= maxLoopCount) {
			stop()
		}
	}
	
	override fun getConstantState() : Drawable.ConstantState? {
		return state
	}
	
	/**
	 * Clears any resources for loading frames that are currently held on to by this object.
	 */
	fun recycle() {
		isRecycled = true
		state.frameLoader.clear()
	}
	
	// Public API.
	fun setLoopCount(loopCount : Int) {
		if(loopCount <= 0 && loopCount != LOOP_FOREVER && loopCount != LOOP_INTRINSIC) {
			throw IllegalArgumentException("Loop count must be greater than 0, or equal to " + "GlideDrawable.LOOP_FOREVER, or equal to GlideDrawable.LOOP_INTRINSIC")
		}
		
		maxLoopCount = if(loopCount == LOOP_INTRINSIC) {
			val intrinsicCount = state.frameLoader.loopCount
			if(intrinsicCount == TOTAL_ITERATION_COUNT_FOREVER) LOOP_FOREVER else intrinsicCount
		} else {
			loopCount
		}
	}
	
	//	internal class GifState(@field:VisibleForTesting
	//	                        val frameLoader : GifFrameLoader) : Drawable.ConstantState() {
	//
	//		override fun newDrawable(res : Resources?) : Drawable {
	//			return newDrawable()
	//		}
	//
	//		override fun newDrawable() : Drawable {
	//			return MyGifDrawable(this)
	//		}
	//
	//		override fun getChangingConfigurations() : Int {
	//			return 0
	//		}
	//	}
	
	companion object {
		/**
		 * A constant indicating that an animated drawable should loop continuously.
		 */
		// Public API.
		val LOOP_FOREVER = - 1
		/**
		 * A constant indicating that an animated drawable should loop for its default number of times.
		 * For animated GIFs, this constant indicates the GIF should use the netscape loop count if
		 * present.
		 */
		// Public API.
		val LOOP_INTRINSIC = 0
		private val GRAVITY = Gravity.FILL
		
		//////////////////////////////////////////////////////////////////
		
		internal val log = LogCategory("MyGifDrawable")
		
		private val field_state : Field by lazy {
			val rv = GifDrawable::class.java.getDeclaredField("state")
			rv.isAccessible = true
			rv
		}
		
		internal fun cloneState(other : GifDrawable) : GifDrawable.GifState {
			try {
				val other_state = field_state.get(other) as GifDrawable.GifState
				val frameLoader : GifFrameLoader = other_state.frameLoader
				
				return GifDrawable.GifState(frameLoader)
				
				//					other_state.gifHeader,
				//					other_state.data,
				//					other_state.context,
				//					other.frameTransformation,
				//					other_state.targetWidth,
				//					other_state.targetHeight,
				//					other_state.bitmapProvider,
				//					other_state.bitmapPool,
				//					other.firstFrame
			} catch(ex : Throwable) {
				throw RuntimeException("cloning GifDrawable.GifState failed.", ex)
			}
			
		}
	}
}

