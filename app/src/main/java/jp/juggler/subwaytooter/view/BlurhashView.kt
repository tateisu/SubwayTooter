package jp.juggler.subwaytooter.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.SparseIntArray
import androidx.appcompat.widget.AppCompatTextView
import jp.juggler.util.LogCategory
import java.lang.Math.pow
import kotlin.math.abs
import kotlin.math.sign

class BlurhashView : AppCompatTextView {
	
	companion object {
		
		val log = LogCategory("BlurhashView")
		
		private val base83Map = SparseIntArray().apply {
			val base83Chars =
				"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"
			for(i in 0 until base83Chars.length) {
				val c = base83Chars[i]
				put(c.toInt(), i)
			}
		}
		
		// base83から整数へのデコード
		private fun String.decodeBase83() : Int {
			var v = 0
			for(c in this) {
				val ci = c.toInt()
				val i = base83Map.get(ci, - 1)
				if(i == - 1) error("decodeBase83: incorrect char code $ci")
				v = v * 83 + i
			}
			return v
		}
		
		private val arraySRGB2Linear = FloatArray(256).apply {
			for(i in 0 until 256) {
				val v = i.toDouble() / 255.0
				this[i] =
					if(v <= 0.04045) {
						v / 12.92
					} else {
						pow(((v + 0.055) / 1.055), 2.4)
					}
						.toFloat()
				
			}
		}
		
		private fun sRGBToLinear(value : Int) =
			arraySRGB2Linear[if(value < 0) 0 else if(value > 255) 255 else value]
		
		private fun linearTosRGB(value : Float) : Int {
			var start = 0
			var end = 256
			while(end - start > 1) {
				val mid = (start + end) shr 1
				val midValue = arraySRGB2Linear[mid]
				when {
					value < midValue -> end = mid
					value > midValue -> start = mid + 1
					else -> return mid
				}
			}
			return when {
				start < 0 -> 0
				start >= 256 -> 255
				else -> start
			}
		}
		
		private fun signPow(v : Float, exp : Double) : Float =
			sign(v) * pow(abs(v).toDouble(), exp).toFloat()
		
		private fun decodeDC(value : Int) = floatArrayOf(
			sRGBToLinear((value ushr 16) and 255),
			sRGBToLinear((value ushr 8) and 255),
			sRGBToLinear((value) and 255)
		)
		
		private fun decodeACSub(maximumValue : Float, v : Int) : Float =
			signPow((v - 9).toFloat() / 9f, 2.0) * maximumValue
		
		private fun decodeAC(value : Int, maximumValue : Float) = floatArrayOf(
			decodeACSub(maximumValue, value / 361), // 19*19
			decodeACSub(maximumValue, ((value / 19) % 19)),
			decodeACSub(maximumValue, value % 19)
		)
		
		private fun String.sub1(idx : Int) = substring(idx, idx + 1)
		private fun String.sub2(idx : Int) = substring(idx, idx + 2)
		private fun String.sub4(idx : Int) = substring(idx, idx + 4)
		
		private fun decode(
			pixels : IntArray,
			pixelWidth : Int,
			pixelHeight : Int,
			blurhash : String,
			punch : Float = 1f
		) {
			
			if(blurhash.length < 6) error("blurhash: too short $blurhash")
			
			val sizeFlag = blurhash.sub1(0).decodeBase83()
			val numY : Int = (sizeFlag / 9) + 1
			val numX : Int = (sizeFlag % 9) + 1
			val quantisedMaximumValue : Int = blurhash.sub1(1).decodeBase83()
			val maximumValue : Float = ((quantisedMaximumValue + 1).toFloat() / 166f) * punch
			
			if(blurhash.length != 4 + 2 * numX * numY) {
				error("'blurhash length mismatch. actual=${blurhash.length},expect=${4 + 2 * numX * numY}")
			}
			
			val colors = ArrayList<FloatArray>()
			for(i in 0 until numX * numY) {
				colors.add(
					if(i == 0) {
						decodeDC(blurhash.sub4(2).decodeBase83()) // 2..5
					} else {
						// 6..8,...
						decodeAC(blurhash.sub2(4 + i * 2).decodeBase83(), maximumValue)
					}
				)
			}
			
			var pos = 0
			for(y in 0 until pixelHeight) {
				val ky = Math.PI * y.toDouble() / pixelHeight.toDouble()
				for(x in 0 until pixelWidth) {
					val kx = Math.PI * x.toDouble() / pixelWidth.toDouble()
					var r = 0f
					var g = 0f
					var b = 0f
					for(j in 0 until numY) {
						for(i in 0 until numX) {
							val basis = (Math.cos(kx * i) * Math.cos(ky * j)).toFloat()
							val color = colors[i + j * numX]
							r += color[0] * basis
							g += color[1] * basis
							b += color[2] * basis
						}
					}
					pixels[pos ++] = Color.argb(
						255,
						linearTosRGB(r),
						linearTosRGB(g),
						linearTosRGB(b)
					)
				}
			}
		}
		
		// デコード後のビットマップのサイズ
		const val bitmapWidth = 32
		const val bitmapHeight = 32
	}
	
	constructor(context : Context) : super(context)
	constructor(context : Context, attrs : AttributeSet) : super(context, attrs)
	constructor(context : Context, attrs : AttributeSet, defStyleAttr : Int) : super(
		context,
		attrs,
		defStyleAttr
	)
	
	// bitmapとIntArrayはViewに保持して再利用する
	private val pixels = IntArray(bitmapWidth * bitmapHeight)
	private val blurhashBitmap = Bitmap.createBitmap(
		bitmapWidth,
		bitmapHeight,
		Bitmap.Config.ARGB_8888
	)
	
	private var blurhashDecodeOk = false
	
	private val rectSrc = Rect()
	private val rectDst = Rect()
	private val paint = Paint().apply {
		isFilterBitmap = true
	}
	
	var errorColor : Int = 0
		set(v) {
			field = v
			invalidate()
		}
	
	var blurhash : String? = null
		set(v) {
			if(v == field) return
			field = v
			
			blurhashDecodeOk = if(v?.isEmpty() != false) {
				false
			} else try {
				decode(pixels, bitmapWidth, bitmapHeight, v)
				blurhashBitmap.setPixels(
					pixels,
					0,
					bitmapWidth,
					0,
					0,
					bitmapWidth,
					bitmapHeight
				)
				true
			} catch(ex : Throwable) {
				log.e(ex, "blurhash decode failed.")
				false
			}
		}
	
	override fun onDraw(canvas : Canvas) {
		
		val view_w = width
		val view_h = height
		
		val b = blurhashBitmap
		if(b != null && ! b.isRecycled && blurhashDecodeOk) {
			rectSrc.set(0, 0, b.width, b.height)
			rectDst.set(0, 0, view_w, view_h)
			canvas.drawBitmap(b, rectSrc, rectDst, paint)
		} else {
			paint.color = errorColor
			rectDst.set(0, 0, view_w, view_h)
			canvas.drawRect(rectDst, paint)
		}
		
		super.onDraw(canvas)
	}
}