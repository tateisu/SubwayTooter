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
    private val debug:Boolean =false
) : ApngDecoderCallback {
	
	companion object {
		
		private const val TAG = "ApngFrames"
		
		// ループしない画像の場合は3秒でまたループさせる
		private const val DELAY_AFTER_END = 3000L
		
		// アニメーションフレームの描画に使う
		private val sSrcModePaint : Paint by lazy {
			val paint = Paint()
			paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
			paint.isFilterBitmap = true
			paint
		}
		
		private fun createBlankBitmap(w : Int, h : Int) : Bitmap {
			return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
		}
		
		// WARNING: ownership of "src" will be moved or recycled.
		private fun scaleBitmap(src : Bitmap?, size_max : Int) : Bitmap? {
			if(src == null) return null
			
			val wSrc = src.width
			val hSrc = src.height
			if(size_max <= 0 || wSrc <= size_max && hSrc <= size_max) return src
			
			val wDst : Int
			val hDst : Int
			if(wSrc >= hSrc) {
				wDst = size_max
				hDst = Math.max(
					1,
					(size_max.toFloat() * hSrc.toFloat() / wSrc.toFloat() + 0.5f).toInt()
				)
			} else {
				hDst = size_max
				wDst = Math.max(
					1,
					(size_max.toFloat() * wSrc.toFloat() / hSrc.toFloat() + 0.5f).toInt()
				)
			}
			Log.v(TAG,"scaleBitmap: $wSrc,$hSrc => $wDst,$hDst")
			
			val b2 = createBlankBitmap(wDst, hDst)
			val canvas = Canvas(b2)
			val rectSrc = Rect(0, 0, wSrc, hSrc)
			val rectDst = Rect(0, 0, wDst, hDst)
			canvas.drawBitmap(src, rectSrc, rectDst,
				sSrcModePaint
			)
			src.recycle()
			return b2
		}
		
		private fun toBitmap(src : ApngBitmap) : Bitmap {
			return Bitmap.createBitmap(
				src.colors, // int[] 配列
				0, // offset
				src.width, //stride
				src.width, // width
				src.height, //height
				Bitmap.Config.ARGB_8888
			)
		}
		
		private fun toBitmap(src : ApngBitmap, size_max : Int) : Bitmap? {
			return scaleBitmap(
				toBitmap(
					src
				), size_max
			)
		}
		
		@Suppress("unused")
		fun parseApng(inStream : InputStream, pixelSizeMax : Int,debug:Boolean=false) : ApngFrames {
			val result = ApngFrames(pixelSizeMax,debug)
			try {
				ApngDecoder.parseStream(inStream, result)
				result.onParseComplete()
				return if( result.defaultImage != null || result.frames?.isNotEmpty() == true ){
					result
				}else{
					throw RuntimeException("APNG has no image")
				}
			} catch(ex : Throwable) {
				result.dispose()
				throw ex
			}
			
		}
	}
	
	private var header : ApngImageHeader? = null
	private var animationControl : ApngAnimationControl? = null
	
	val width : Int
		get() = Math.min( pixelSizeMax, header?.width ?: 1)
	
	val height : Int
		get() = Math.min( pixelSizeMax, header?.height ?: 1)
	
	@Suppress("MemberVisibilityCanBePrivate")
	val numFrames : Int
		get() = animationControl?.numFrames ?: 1
	
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
	
	private class Frame(
		internal val bitmap : Bitmap,
		internal val time_start : Long,
		internal val time_width : Long
	)
	
	private var frames : ArrayList<Frame>? = null
	
	@Suppress("unused")
	constructor(bitmap : Bitmap) : this() {
		this.defaultImage = bitmap
	}
	
	private fun onParseComplete() {
		canvasBitmap?.recycle()
		canvasBitmap = null
		
		val frames = this.frames
		if( frames != null ){
			if( frames.size > 1){
				defaultImage?.recycle()
				defaultImage = null
			}else if( frames.size == 1){
				defaultImage?.recycle()
				defaultImage = frames.first().bitmap
				frames.clear()
			}
		}
	}
	
	fun dispose() {
		defaultImage?.recycle()
		canvasBitmap?.recycle()
		
		val frames = this.frames
		if(frames != null) {
			for(f in frames) {
				f.bitmap.recycle()
			}
		}
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
		if(animationControl == null || frames == null) {
			// この場合は既に mBitmapNonAnimation が用意されてるはずだ
			result.bitmap = null
			result.delay = Long.MAX_VALUE
			return
		}
		
		val frameCount = frames.size
		
		val isFinite = ! animationControl.isPlayIndefinitely
		val repeatSequenceCount = if(isFinite) animationControl.numPlays else 1
		val endWait = if(isFinite) DELAY_AFTER_END else 0L
		val timeTotalLoop = Math.max(1,timeTotal * repeatSequenceCount + endWait)
		
		val tf = (if(0.5f + t < 0f) 0f else t / durationScale).toLong()
		
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
			// log.d("s=%d,m=%d,e=%d tt=%d,fs=%s,fe=%d",s,mid,e,tt,frame.time_start,frame.time_start+frame.time_width );
			if(tt < frame.time_start) {
				e = mid
			} else if(tt >= frame.time_start + frame.time_width) {
				s = mid + 1
			} else {
				s = mid
				break
			}
		}
		s = if(s < 0) 0 else if(s >= frameCount - 1) frameCount - 1 else s
		val frame = frames[s]
		val delay = frame.time_start + frame.time_width - tt
		result.bitmap = frames[s].bitmap
		result.delay = (0.5f + durationScale * Math.max(0f, delay.toFloat())).toLong()
		
		// log.d("findFrame tf=%d,tl=%d/%d,tt=%d/%d,s=%d,w=%d,delay=%d",tf,tl,loop_total,tt,timeTotal,s,frame.time_width,result.delay);
	}
	
	/////////////////////////////////////////////////////
	// implements ApngDecoderCallback
	
	override fun onApngWarning(message : String) {
		Log.w(TAG, message)
	}
	
	override fun onApngDebug(message : String) {
		Log.d(TAG, message)
	}
	
	override fun canApngDebug():Boolean = debug
	
	override fun onHeader(apng : Apng, header : ApngImageHeader) {
		this.header = header
	}
	
	override fun onAnimationInfo(
		apng : Apng,
		header: ApngImageHeader,
		animationControl : ApngAnimationControl
	) {
		this.animationControl = animationControl
		
		val canvasBitmap =
			createBlankBitmap(header.width, header.height)
		this.canvasBitmap = canvasBitmap
		this.canvas = Canvas(canvasBitmap)
		this.frames = ArrayList(animationControl.numFrames)
	}
	
	override fun onDefaultImage(apng : Apng, bitmap : ApngBitmap) {
		defaultImage?.recycle()
		defaultImage = toBitmap(bitmap, pixelSizeMax)
	}
	
	override fun onAnimationFrame(
		apng : Apng,
		frameControl : ApngFrameControl,
		bitmap : ApngBitmap
	) {
		val frames = this.frames ?: return
		val canvasBitmap = this.canvasBitmap ?: return
		
		// APNGのフレーム画像をAndroidの形式に変換する。この段階ではリサイズしない
		val bitmapNative = toBitmap(bitmap)
		
		val previous : Bitmap? = if(frameControl.disposeOp == DisposeOp.Previous) {
			// Capture the current bitmap region IF it needs to be reverted after rendering
			Bitmap.createBitmap(
				canvasBitmap,
				frameControl.xOffset,
				frameControl.yOffset,
				frameControl.width,
				frameControl.height
			)
		} else {
			null
		}
		
		val paint = if(frameControl.blendOp == BlendOp.Source) {
			sSrcModePaint  // SRC_OVER, not blend
		} else {
			null // (for blend, leave paint null)
		}
		
		// Draw the new frame into place
		canvas.drawBitmap(
			bitmapNative,
			frameControl.xOffset.toFloat(),
			frameControl.yOffset.toFloat(),
			paint
		)
		
		// Extract a drawable from the canvas. Have to copy the current bitmap.
		// Store the drawable in the sequence of frames
		val timeStart = timeTotal
		val timeWidth = Math.max(1L, frameControl.delayMilliseconds)
		timeTotal += timeWidth
		
		val scaledBitmap =
			scaleBitmap(
				canvasBitmap.copy(
					Bitmap.Config.ARGB_8888,
					false
				), pixelSizeMax
			)
		if(scaledBitmap != null) {
			frames.add(Frame(scaledBitmap, timeStart, timeWidth))
		}
		
		// Now "dispose" of the frame in preparation for the next.
		// https://wiki.mozilla.org/APNG_Specification#.60fcTL.60:_The_Frame_Control_Chunk
		
		when(frameControl.disposeOp) {
			DisposeOp.None -> {
			}
			
			DisposeOp.Background ->
				// APNG_DISPOSE_OP_BACKGROUND: the frame's region of the output buffer is to be cleared to fully transparent black before rendering the next frame.
				//System.out.println(String.format("Frame %d clear background (full=%s, x=%d y=%d w=%d h=%d) previous=%s", currentFrame.sequenceNumber,
				//        isFull, currentFrame.xOffset, currentFrame.yOffset, currentFrame.width, currentFrame.height, previous));
				//if (true || isFull) {
				canvas.drawColor(0, PorterDuff.Mode.CLEAR) // Clear to fully transparent black
			
			DisposeOp.Previous ->
				// APNG_DISPOSE_OP_PREVIOUS: the frame's region of the output buffer is to be reverted to the previous contents before rendering the next frame.
				//System.out.println(String.format("Frame %d restore previous (full=%s, x=%d y=%d w=%d h=%d) previous=%s", currentFrame.sequenceNumber,
				//        isFull, currentFrame.xOffset, currentFrame.yOffset, currentFrame.width, currentFrame.height, previous));
				// Put the original section back
				if(previous != null) {
					canvas.drawBitmap(
						previous, frameControl.xOffset.toFloat(), frameControl.yOffset.toFloat(),
						sSrcModePaint
					)
					previous.recycle()
				}
			
			else -> {
				// 0: Default should never happen
				
				// APNG_DISPOSE_OP_NONE: no disposal is done on this frame before rendering the next; the contents of the output buffer are left as is.
				//System.out.println("Frame "+currentFrame.sequenceNumber+" do nothing dispose");
				// do nothing
				//                } else {
				//                    Rect rt = new Rect(currentFrame.xOffset, currentFrame.yOffset, currentFrame.width+currentFrame.xOffset, currentFrame.height+currentFrame.yOffset);
				//                    paint = new Paint();
				//                    paint.setColor(0);
				//                    paint.setStyle(Paint.Style.FILL);
				//                    canvas.drawRect(rt, paint);
				//                }
				
			}
		}
	}
	
}
