package jp.juggler.subwaytooter.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect

import net.ellerton.japng.PngScanlineBuffer
import net.ellerton.japng.argb8888.Argb8888Bitmap
import net.ellerton.japng.argb8888.Argb8888Processor
import net.ellerton.japng.argb8888.Argb8888Processors
import net.ellerton.japng.argb8888.Argb8888ScanlineProcessor
import net.ellerton.japng.argb8888.BasicArgb8888Director
import net.ellerton.japng.chunks.PngAnimationControl
import net.ellerton.japng.chunks.PngFrameControl
import net.ellerton.japng.chunks.PngHeader
import net.ellerton.japng.error.PngException
import net.ellerton.japng.reader.DefaultPngChunkReader
import net.ellerton.japng.reader.PngReadHelper

import java.io.InputStream
import java.util.ArrayList

// APNGを解釈した結果を保持する
// (フレーム数分のbitmapと時間情報)

class APNGFrames {
	
	companion object {
		
		internal val log = LogCategory("APNGFrames")
		
		// ループしない画像の場合は3秒でまたループさせる
		private const val DELAY_AFTER_END = 3000L
		
		/**
		 * Keep a 1x1 transparent image around as reference for creating a scaled starting bitmap.
		 * Considering this because of some reported OutOfMemory errors, and this post:
		 *
		 *
		 * http://stackoverflow.com/a/8527745/963195
		 *
		 *
		 * Specifically: "NEVER use Bitmap.createBitmap(width, height, Config.ARGB_8888). I mean NEVER!"
		 *
		 *
		 * Instead the 1x1 image (68 bytes of resources) is scaled up to the needed size.
		 * Whether or not this fixes the OOM problems is TBD...
		 */
		//static Bitmap sOnePxTransparent;
		internal val sSrcModePaint : Paint by lazy{
			val paint = Paint()
			paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
			paint.isFilterBitmap = true
			paint
		}
		
		internal fun createBlankBitmap(w : Int, h : Int) : Bitmap {
			return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
		}
		
		// WARNING: ownership of "src" will be moved or recycled.
		internal fun scaleBitmap(src : Bitmap?, size_max : Int) : Bitmap? {
			if(src == null) return null
			
			val src_w = src.width
			val src_h = src.height
			if(src_w <= size_max && src_h <= size_max) return src
			
			var dst_w : Int
			var dst_h : Int
			if(src_w >= src_h) {
				dst_w = size_max
				dst_h = (0.5f + src_h * size_max / src_w.toFloat()).toInt()
				if(dst_h < 1) dst_h = 1
			} else {
				dst_h = size_max
				dst_w = (0.5f + src_w * size_max / src_h.toFloat()).toInt()
				if(dst_w < 1) dst_w = 1
			}
			
			// この方法だとリークがあるらしい？？？
			// http://stackoverflow.com/a/8527745/963195
			// return Bitmap.createScaledBitmap( src, dst_w , dst_h , true );
			
			val b2 = createBlankBitmap(dst_w, dst_h)
			val canvas = Canvas(b2)
			val rect_src = Rect(0, 0, src_w, src_h)
			val rect_dst = Rect(0, 0, dst_w, dst_h)
			canvas.drawBitmap(src, rect_src, rect_dst, sSrcModePaint)
			src.recycle()
			return b2
		}
		
		internal fun toBitmap(src : Argb8888Bitmap) : Bitmap {
			val offset = 0
			val stride = src.width
			return Bitmap.createBitmap(src.pixelArray, offset, stride, src.width, src.height, Bitmap.Config.ARGB_8888)
		}
		
		internal fun toBitmap(src : Argb8888Bitmap, size_max : Int) : Bitmap? {
			return scaleBitmap(toBitmap(src), size_max)
		}
		
		/////////////////////////////////////////////////////////////////////
		
		// entry point is here
		@Throws(PngException::class)
		internal fun parseAPNG(`is` : InputStream, size_max : Int) : APNGFrames? {
			val handler = APNGParseEventHandler(size_max)
			try {
				val processor = Argb8888Processor(handler)
				val reader = DefaultPngChunkReader(processor)
				val result = PngReadHelper.read(`is`, reader)
				result?.onParseComplete()
				return result
			} catch(ex : Throwable) {
				handler.dispose()
				throw ex
			}
			
		}
	}
	
	// ピクセルサイズ制限
	private var mPixelSizeMax : Int = 0
	
	// APNGじゃなかった場合に使われる
	private var mBitmapNonAnimation : Bitmap? = null
	
	private lateinit var header : PngHeader
	private lateinit var scanlineProcessor : Argb8888ScanlineProcessor
	private lateinit var canvas : Canvas
	
	private var canvasBitmap : Bitmap? = null
	private var currentFrame : PngFrameControl? = null
	private var animationControl : PngAnimationControl? = null
	
	private var time_total = 0L
	
	private var frames : ArrayList<Frame>? = null
	
	///////////////////////////////////////////////////////////////
	
	// 再生速度の調整
	private var durationScale = 1f
	
	private val numFrames : Int
		get() = animationControl?.numFrames ?: 1
	
	val isSingleFrame : Boolean
		get() = numFrames == 1
	
	private class Frame(
		internal val bitmap : Bitmap,
		internal val time_start : Long,
		internal val time_width : Long
	)
	
	///////////////////////////////////////////////////////////////
	
	internal constructor(bitmap : Bitmap) {
		this.mBitmapNonAnimation = bitmap
	}
	
	internal constructor(
		header : PngHeader, scanlineProcessor : Argb8888ScanlineProcessor, animationControl : PngAnimationControl, size_max : Int
	) {
		this.header = header
		this.scanlineProcessor = scanlineProcessor
		this.animationControl = animationControl
		this.mPixelSizeMax = size_max
		
		this.canvasBitmap = createBlankBitmap(header.width, header.height)
		this.canvas = Canvas(this.canvasBitmap)
		this.frames = ArrayList(animationControl.numFrames)
		
	}
	
	internal fun onParseComplete() {
		val frames = this.frames
		
		if(frames != null && frames.size <= 1) {
			mBitmapNonAnimation = toBitmap(scanlineProcessor.bitmap, mPixelSizeMax)
		}
		
		canvasBitmap?.recycle()
		canvasBitmap = null
	}
	
	internal fun dispose() {
		mBitmapNonAnimation?.recycle()
		canvasBitmap?.recycle()
		
		val frames = this.frames
		if(frames != null) {
			for(f in frames) {
				f.bitmap.recycle()
			}
		}
	}
	
	// フレームが追加される
	internal fun beginFrame(frameControl : PngFrameControl) : Argb8888ScanlineProcessor {
		currentFrame = frameControl
		return scanlineProcessor.cloneWithSharedBitmap(header.adjustFor(currentFrame))
	}
	
	// フレームが追加される
	internal fun completeFrame(frameImage : Argb8888Bitmap) {
		
		val frames = this.frames ?: return
		
		val canvasBitmap = this.canvasBitmap ?: return
		
		val currentFrame = this.currentFrame ?: return
		this.currentFrame = null
		
		// APNGのフレーム画像をAndroidの形式に変換する
		val frame = toBitmap(frameImage)
		
		var previous : Bitmap? = null
		// Capture the current bitmap region IF it needs to be reverted after rendering
		if(2 == currentFrame.disposeOp.toInt()) {
			previous = Bitmap.createBitmap(canvasBitmap, currentFrame.xOffset, currentFrame.yOffset, currentFrame.width, currentFrame.height) // or could use from frames?
			//System.out.println(String.format("Captured previous %d x %d", previous.getWidth(), previous.getHeight()));
		}
		
		var paint : Paint? = null // (for blend, leave paint null)
		if(0 == currentFrame.blendOp.toInt()) { // SRC_OVER, not blend
			paint = sSrcModePaint
		}
		
		// boolean isFull = currentFrame.height == header.height && currentFrame.width == header.width;
		
		// Draw the new frame into place
		canvas.drawBitmap(frame, currentFrame.xOffset.toFloat(), currentFrame.yOffset.toFloat(), paint)
		
		// Extract a drawable from the canvas. Have to copy the current bitmap.
		// Store the drawable in the sequence of frames
		val time_start = time_total
		var time_width = currentFrame.delayMilliseconds.toLong()
		if(time_width <= 0L) time_width = 1L
		time_total = time_start + time_width
		
		val scaledBitmap = scaleBitmap(canvasBitmap.copy(Bitmap.Config.ARGB_8888, false), mPixelSizeMax)
		if(scaledBitmap != null) {
			frames.add(Frame(scaledBitmap, time_start, time_width))
		}
		
		// Now "dispose" of the frame in preparation for the next.
		// https://wiki.mozilla.org/APNG_Specification#.60fcTL.60:_The_Frame_Control_Chunk
		
		when(currentFrame.disposeOp.toInt()) {
			
			1 ->
				// APNG_DISPOSE_OP_BACKGROUND: the frame's region of the output buffer is to be cleared to fully transparent black before rendering the next frame.
				//System.out.println(String.format("Frame %d clear background (full=%s, x=%d y=%d w=%d h=%d) previous=%s", currentFrame.sequenceNumber,
				//        isFull, currentFrame.xOffset, currentFrame.yOffset, currentFrame.width, currentFrame.height, previous));
				//if (true || isFull) {
				canvas.drawColor(0, PorterDuff.Mode.CLEAR) // Clear to fully transparent black
			
			2 ->
				// APNG_DISPOSE_OP_PREVIOUS: the frame's region of the output buffer is to be reverted to the previous contents before rendering the next frame.
				//System.out.println(String.format("Frame %d restore previous (full=%s, x=%d y=%d w=%d h=%d) previous=%s", currentFrame.sequenceNumber,
				//        isFull, currentFrame.xOffset, currentFrame.yOffset, currentFrame.width, currentFrame.height, previous));
				// Put the original section back
				if(previous != null) {
					canvas.drawBitmap(previous, currentFrame.xOffset.toFloat(), currentFrame.yOffset.toFloat(), sSrcModePaint)
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
	
	class FindFrameResult {
		var bitmap : Bitmap? = null // may null
		var delay : Long = 0 // 再描画が必要ない場合は Long.MAX_VALUE
	}
	
	// シーク位置に応じたコマ画像と次のコマまでの残り時間をresultに格納する
	fun findFrame(result : FindFrameResult, t : Long) {
		
		if(mBitmapNonAnimation != null) {
			result.bitmap = mBitmapNonAnimation
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
		
		val frame_count = frames.size
		
		val isFinite = ! animationControl.loopForever()
		val repeatSequenceCount = if(isFinite) animationControl.numPlays else 1
		val end_wait = if(isFinite) DELAY_AFTER_END else 0L
		var loop_total = time_total * repeatSequenceCount + end_wait
		if(loop_total <= 0) loop_total = 1
		
		val tf = (if(0.5f + t < 0f) 0f else t / durationScale).toLong()
		
		// 全体の繰り返し時刻で余りを計算
		val tl = tf % loop_total
		if(tl >= loop_total - end_wait) {
			// 終端で待機状態
			result.bitmap = frames[frame_count - 1].bitmap
			result.delay = (0.5f + (loop_total - tl) * durationScale).toLong()
			return
		}
		// １ループの繰り返し時刻で余りを計算
		val tt = tl % time_total
		
		// フレームリストを時刻で二分探索
		var s = 0
		var e = frame_count
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
		s = if(s < 0) 0 else if(s >= frame_count - 1) frame_count - 1 else s
		val frame = frames[s]
		val delay = frame.time_start + frame.time_width - tt
		result.bitmap = frames[s].bitmap
		result.delay = (0.5f + durationScale * Math.max(0f, delay.toFloat())).toLong()
		
		// log.d("findFrame tf=%d,tl=%d/%d,tt=%d/%d,s=%d,w=%d,delay=%d",tf,tl,loop_total,tt,time_total,s,frame.time_width,result.delay);
	}
	
	/////////////////////////////////////////////////////////////////////
	
	// APNGのパース中に随時呼び出される
	internal class APNGParseEventHandler // 作成
	(private val size_max : Int) : BasicArgb8888Director<APNGFrames>() {
		
		private var header : PngHeader? = null
		
		private var mFrames : APNGFrames? = null
		
		private val isAnimated : Boolean
			get() = mFrames != null
		
		// ヘッダが分かった
		@Throws(PngException::class)
		override fun receiveHeader(header : PngHeader, buffer : PngScanlineBuffer) {
			this.header = header
			
			// 親クラスのprotectedフィールドを更新する
			val pngBitmap = Argb8888Bitmap(header.width, header.height)
			this.scanlineProcessor = Argb8888Processors.from(header, buffer, pngBitmap)
		}
		
		// デフォルト画像の手前で呼ばれる
		override fun beforeDefaultImage() : Argb8888ScanlineProcessor {
			return scanlineProcessor
		}
		
		// デフォルト画像が分かった
		// おそらく receiveAnimationControl より先に呼ばれる
		override fun receiveDefaultImage(defaultImage : Argb8888Bitmap) {
			// japng ライブラリの返すデフォルトイメージはあまり信用できないので使わない
		}
		
		// アニメーション制御情報が分かった
		override fun receiveAnimationControl(animationControl : PngAnimationControl) {
			this.mFrames = APNGFrames(header !!, scanlineProcessor, animationControl, size_max)
		}
		
		override fun wantDefaultImage() : Boolean {
			return ! isAnimated
		}
		
		override fun wantAnimationFrames() : Boolean {
			return true // isAnimated;
		}
		
		// フレーム制御情報が分かった
		override fun receiveFrameControl(frameControl : PngFrameControl) : Argb8888ScanlineProcessor {
			if(! isAnimated) throw RuntimeException("not animation image")
			return mFrames !!.beginFrame(frameControl)
		}
		
		// フレーム画像が分かった
		override fun receiveFrameImage(frameImage : Argb8888Bitmap) {
			if(! isAnimated) throw RuntimeException("not animation image")
			mFrames !!.completeFrame(frameImage)
		}
		
		// 結果を取得する
		override fun getResult() : APNGFrames? {
			val frames = mFrames
			return if(frames?.isSingleFrame != false ){
				frames
			}else {
				dispose()
				return null
			}
		}
		
		// 処理中に例外が起きた場合、Bitmapリソースを解放する
		fun dispose() {
			mFrames?.dispose()
			mFrames = null
		}
	}
	
}
