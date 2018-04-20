package jp.juggler.apng

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.util.Log

import java.io.InputStream
import java.util.ArrayList

class ApngFrames private constructor(
	private val pixelSizeMax : Int = 0,
	private val debug : Boolean = false
) : ApngDecoderCallback {
	
	companion object {
		
		private const val TAG = "ApngFrames"
		
		// ループしない画像の場合は3秒でまたループさせる
		private const val DELAY_AFTER_END = 3000L
		
		// アニメーションフレームの描画に使う
		private val sPaintDontBlend = Paint().apply {
			xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
			isFilterBitmap = true
		}
		private val sPaintClear = Paint().apply {
			xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
			color = 0
		}
		
		private fun createBlankBitmap(w : Int, h : Int) =
			Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
		
		private fun scale(max : Int, num : Int, den : Int) =
			(max.toFloat() * num.toFloat() / den.toFloat() + 0.5f).toInt()
		
		private fun scaleBitmap(
			size_max : Int,
			src : Bitmap,
			recycleSrc : Boolean = true // true: ownership of "src" will be moved or recycled.
		) : Bitmap {
			
			val wSrc = src.width
			val hSrc = src.height
			if(size_max <= 0 || (wSrc <= size_max && hSrc <= size_max)) {
				return if(recycleSrc) {
					src
				} else {
					src.copy(Bitmap.Config.ARGB_8888, false)
				}
			}
			
			val wDst : Int
			val hDst : Int
			if(wSrc >= hSrc) {
				wDst = size_max
				hDst = Math.max(1, scale(size_max, hSrc, wSrc))
			} else {
				hDst = size_max
				wDst = Math.max(1, scale(size_max, wSrc, hSrc))
			}
			//Log.v(TAG,"scaleBitmap: $wSrc,$hSrc => $wDst,$hDst")
			
			val b2 = createBlankBitmap(wDst, hDst)
			val canvas = Canvas(b2)
			val rectSrc = Rect(0, 0, wSrc, hSrc)
			val rectDst = Rect(0, 0, wDst, hDst)
			canvas.drawBitmap(src, rectSrc, rectDst, sPaintDontBlend)
			
			if(recycleSrc) src.recycle()
			
			return b2
		}
		
		private fun toAndroidBitmap(src : ApngBitmap) =
			Bitmap.createBitmap(
				src.colors, // int[] 配列
				0, // offset
				src.width, //stride
				src.width, // width
				src.height, //height
				Bitmap.Config.ARGB_8888
			)
		
		private fun toAndroidBitmap(src : ApngBitmap, size_max : Int) =
			scaleBitmap(size_max, toAndroidBitmap(src))
		
		@Suppress("unused")
		fun parseApng(
			inStream : InputStream,
			pixelSizeMax : Int,
			debug : Boolean = false
		) : ApngFrames {
			val result = ApngFrames(pixelSizeMax, debug)
			try {
				ApngDecoder.parseStream(inStream, result)
				result.onParseComplete()
				return result.takeIf { result.defaultImage != null || result.frames?.isNotEmpty() == true }
					?: throw RuntimeException("APNG has no image")
			} catch(ex : Throwable) {
				result.dispose()
				throw ex
			}
			
		}
	}
	
	private var header : ApngImageHeader? = null
	private var animationControl : ApngAnimationControl? = null
	
	// width,height (after resized)
	var width : Int = 1
		private set
	
	var height : Int = 1
		private set
	
	
	class Frame(
		val bitmap : Bitmap,
		val timeStart : Long,
		val timeWidth : Long
	)
	
	var frames : ArrayList<Frame>? = null
	
	@Suppress("MemberVisibilityCanBePrivate")
	val numFrames : Int
		get() = frames?.size ?: 1
	
	@Suppress("unused")
	val hasMultipleFrame : Boolean
		get() = numFrames > 1
	
	private var timeTotal = 0L
	
	private lateinit var canvas : Canvas
	
	private var canvasBitmap : Bitmap? = null
	
	// 再生速度の調整
	private var durationScale = 1f
	
	// APNGじゃなかった場合に使われる
	private var defaultImage : Bitmap? = null
		set(value) {
			field = value
			if(value != null) {
				width = value.width
				height = value.height
			}
		}
	

	constructor(bitmap : Bitmap) : this() {
		defaultImage = bitmap
	}
	
	private fun onParseComplete() {
		canvasBitmap?.recycle()
		canvasBitmap = null
		
		val frames = this.frames
		if(frames != null) {
			if(frames.size > 1) {
				defaultImage?.recycle()
				defaultImage = null
			} else if(frames.size == 1) {
				defaultImage?.recycle()
				defaultImage = frames.first().bitmap
				frames.clear()
			}
		}
	}
	
	fun dispose() {
		canvasBitmap?.recycle()
		defaultImage?.recycle()
		frames?.forEach { it.bitmap.recycle() }
	}
	
	class FindFrameResult {
		var bitmap : Bitmap? = null // may null
		var delay : Long = 0 // 再描画が必要ない場合は Long.MAX_VALUE
	}
	
	// シーク位置に応じたコマ画像と次のコマまでの残り時間をresultに格納する
	@Suppress("unused")
	fun findFrame(result : FindFrameResult, t : Long) {
		
		if(defaultImage != null) {
			result.bitmap = defaultImage
			result.delay = Long.MAX_VALUE
			return
		}
		
		val animationControl = this.animationControl
		val frames = this.frames
		
		if(animationControl == null || frames == null || frames.isEmpty()) {
			// ここは通らないはず…
			result.bitmap = null
			result.delay = Long.MAX_VALUE
			return
		}
		
		val frameCount = frames.size
		
		val isFinite = animationControl.isFinite
		val repeatSequenceCount = if(isFinite) animationControl.numPlays else 1
		val endWait = if(isFinite) DELAY_AFTER_END else 0L
		val timeTotalLoop = Math.max(1, timeTotal * repeatSequenceCount + endWait)
		
		val tf = (Math.max(0, t) / durationScale).toLong()
		
		// 全体の繰り返し時刻で余りを計算
		val tl = tf % timeTotalLoop
		
		if(tl >= timeTotalLoop - endWait) {
			// 終端で待機状態
			result.bitmap = frames[frameCount - 1].bitmap
			result.delay = (0.5f + (timeTotalLoop - tl) * durationScale).toLong()
			return
		}
		
		// １ループの繰り返し時刻で余りを計算
		val tt = tl % timeTotal
		
		// フレームリストを時刻で二分探索
		var s = 0
		var e = frameCount
		while(e - s > 1) {
			val mid = s + e shr 1
			val frame = frames[mid]
			// log.d("s=%d,m=%d,e=%d tt=%d,fs=%s,fe=%d",s,mid,e,tt,frame.timeStart,frame.timeStart+frame.timeWidth );
			if(tt < frame.timeStart) {
				e = mid
			} else if(tt >= frame.timeStart + frame.timeWidth) {
				s = mid + 1
			} else {
				s = mid
				break
			}
		}
		s = if(s < 0) 0 else if(s >= frameCount - 1) frameCount - 1 else s
		val frame = frames[s]
		val delay = frame.timeStart + frame.timeWidth - tt
		result.bitmap = frames[s].bitmap
		result.delay = (0.5f + durationScale * Math.max(0f, delay.toFloat())).toLong()
		
		// log.d("findFrame tf=%d,tl=%d/%d,tt=%d/%d,s=%d,w=%d,delay=%d",tf,tl,loop_total,tt,timeTotal,s,frame.timeWidth,result.delay);
	}
	
	/////////////////////////////////////////////////////
	// implements ApngDecoderCallback
	
	override fun onApngWarning(message : String) {
		Log.w(TAG, message)
	}
	
	override fun onApngDebug(message : String) {
		Log.d(TAG, message)
	}
	
	override fun canApngDebug() : Boolean = debug
	
	override fun onHeader(apng : Apng, header : ApngImageHeader) {
		this.header = header
	}
	
	override fun onAnimationInfo(
		apng : Apng,
		header : ApngImageHeader,
		animationControl : ApngAnimationControl
	) {
		if(debug){
			Log.d(TAG,"onAnimationInfo")
		}
		this.animationControl = animationControl
		this.frames = ArrayList(animationControl.numFrames)
		
		val canvasBitmap = createBlankBitmap(header.width, header.height)
		this.canvasBitmap = canvasBitmap
		this.canvas = Canvas(canvasBitmap)
	}
	
	override fun onDefaultImage(apng : Apng, bitmap : ApngBitmap) {
		if(debug){
			Log.d(TAG,"onDefaultImage")
		}
		defaultImage?.recycle()
		defaultImage = toAndroidBitmap(bitmap, pixelSizeMax)
	}
	
	
	
	override fun onAnimationFrame(
		apng : Apng,
		frameControl : ApngFrameControl,
		frameBitmap : ApngBitmap
	) {
		if(debug){
			Log.d(TAG,"onAnimationFrame seq=${frameControl.sequenceNumber }, xywh=${frameControl.xOffset},${frameControl.yOffset},${frameControl.width},${frameControl.height} blendOp=${frameControl.blendOp}, disposeOp=${frameControl.disposeOp},delay=${frameControl.delayMilliseconds}")
		}
		val frames = this.frames ?: return
		val canvasBitmap = this.canvasBitmap ?: return
		
		val disposeOp = when{

			// If the first `fcTL` chunk uses a `dispose_op` of APNG_DISPOSE_OP_PREVIOUS it should be treated as APNG_DISPOSE_OP_BACKGROUND.
			frameControl.disposeOp == DisposeOp.Previous && frames.isEmpty() -> DisposeOp.Background

			else-> frameControl.disposeOp
		}
		
		val previous : Bitmap? = when(disposeOp) {
			DisposeOp.Previous -> Bitmap.createBitmap(
				canvasBitmap,
				frameControl.xOffset,
				frameControl.yOffset,
				frameControl.width,
				frameControl.height
			)
			else -> null
		}
		
		try {
			
			val frameBitmapAndroid = toAndroidBitmap(frameBitmap)
			
			try {
				
				canvas.drawBitmap(
					frameBitmapAndroid,
					frameControl.xOffset.toFloat(),
					frameControl.yOffset.toFloat(),
					when(frameControl.blendOp) {
					// all color components of the frame, including alpha,
					// overwrite the current contents of the frame's output buffer region.
						BlendOp.Source -> sPaintDontBlend
					// the frame should be composited onto the output buffer based on its alpha,
					// using a simple OVER operation as described in the "Alpha Channel Processing" section of the PNG specification [PNG-1.2].
						BlendOp.Over -> null
					}
				)
				
				val frame = Frame(
					bitmap = scaleBitmap(pixelSizeMax, canvasBitmap, recycleSrc = false),
					timeStart = timeTotal,
					timeWidth = Math.max(1L, frameControl.delayMilliseconds)
				)
				frames.add(frame)
				timeTotal += frame.timeWidth
				
				when(disposeOp) {
				
				// no disposal is done on this frame before rendering the next;
				// the contents of the output buffer are left as is.
					DisposeOp.None -> {
					}
				
				// the frame's region of the output buffer is
				// to be cleared to fully transparent black
				// before rendering the next frame.
					DisposeOp.Background -> {
						val rect = Rect()
						rect.left = frameControl.xOffset
						rect.top = frameControl.yOffset
						rect.right = frameControl.xOffset + frameControl.width
						rect.bottom = frameControl.yOffset + frameControl.height
						canvas.drawRect(rect,sPaintClear)
						//	canvas.drawColor(0, PorterDuff.Mode.CLEAR)
					}
				
				// the frame's region of the output buffer is
				// to be reverted to the previous contents
				// before rendering the next frame.
					DisposeOp.Previous -> if(previous != null) {
						canvas.drawBitmap(
							previous,
							frameControl.xOffset.toFloat(),
							frameControl.yOffset.toFloat(),
							sPaintDontBlend
						)
					}
					
				}
				
			} finally {
				frameBitmapAndroid.recycle()
			}
		} finally {
			previous?.recycle()
		}
	}
}
