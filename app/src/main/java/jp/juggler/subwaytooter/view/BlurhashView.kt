package jp.juggler.subwaytooter.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.SparseIntArray
import androidx.appcompat.widget.AppCompatTextView
import jp.juggler.util.LogCategory
import java.lang.Math.pow
import kotlin.math.sign

class Blurhash(blurhash : String, punch : Float = 1f) {
	
	companion object {
		
		// map from base83 character to index
		private val base83Map = SparseIntArray().apply {
			"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"
				.forEachIndexed { index, c ->
					put(c.toInt(), index)
				}
		}
		
		// convert from base83 chars(1..4 length) to integer
		private fun String.decodeBase83(start : Int, length : Int) : Int {
			var v = 0
			for(i in start until start + length) {
				val ci = this[i].toInt()
				val idx = base83Map.get(ci, - 1)
				if(idx == - 1) {
					error("decodeBase83: incorrect char code $ci")
				}
				v = v * 83 + idx
			}
			return v
		}
		
		// array to convert gamma curve from sRGB(0..255) to linear(0..1f)
		private val arraySRGB2Linear = FloatArray(256) { i ->
			val v = i.toDouble() / 255.0
			if(v <= 0.04045) {
				v / 12.92
			} else {
				pow(((v + 0.055) / 1.055), 2.4)
			}
				.toFloat()
		}
		
		private fun clip(min:Int,max:Int,value:Int) = if(value < min) min else if(value > max) max else value
		
		private fun sRGBToLinear(value : Int) = arraySRGB2Linear[clip(0,255,value)]
		
		private fun linearTosRGB(value : Float) : Int {
			// binary search in arraySRGB2Linear to avoid using pow()
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
			return clip(0,255,start)
		}
		
		private fun signPow2(v : Float) : Float = sign(v) * (v * v)
		
		private fun decodeACSub(maximumValue : Float, v : Int) : Float =
			signPow2((v - 9).toFloat() / 9f) * maximumValue
		
		private fun decodeAC(value : Int, maximumValue : Float) = floatArrayOf(
			decodeACSub(maximumValue, value / 361), // 19*19
			decodeACSub(maximumValue, ((value / 19) % 19)),
			decodeACSub(maximumValue, value % 19)
		)
		
		private fun decodeDC(value : Int) = floatArrayOf(
			sRGBToLinear((value ushr 16) and 255),
			sRGBToLinear((value ushr 8) and 255),
			sRGBToLinear((value) and 255)
		)
	}
	
	private val height : Int
	private val width : Int
	private val colors : Array<FloatArray>
	
	init {
		if(blurhash.length < 6) {
			error("blurhash: too short $blurhash")
		}
		
		val sizeFlag = blurhash.decodeBase83(0, 1)
		this.height = (sizeFlag / 9) + 1
		this.width = (sizeFlag % 9) + 1
		
		val lengthExpect = 4 + 2 * width * height
		if(blurhash.length != lengthExpect) {
			error("'blurhash length mismatch. expect=$lengthExpect,actual=${blurhash.length}")
		}
		
		val quantisedMaximumValue = blurhash.decodeBase83(1, 1)
		val maximumValue = ((quantisedMaximumValue + 1).toFloat() / 166f) * punch
		
		this.colors = Array(width * height) { i ->
			when(i) {
				0 -> decodeDC(blurhash.decodeBase83(2, 4))
				else -> decodeAC(blurhash.decodeBase83(4 + i * 2, 2), maximumValue)
			}
		}
	}
	
	// render to IntArray that can be used to Bitmap.setPixels()
	fun render(pixels : IntArray, pixelWidth : Int, pixelHeight : Int) {
		var pos = 0
		for(y in 0 until pixelHeight) {
			val ky = Math.PI * y.toDouble() / pixelHeight.toDouble()
			for(x in 0 until pixelWidth) {
				val kx = Math.PI * x.toDouble() / pixelWidth.toDouble()
				var r = 0f
				var g = 0f
				var b = 0f
				for(j in 0 until height) {
					for(i in 0 until width) {
						val basis = (Math.cos(kx * i) * Math.cos(ky * j)).toFloat()
						val color = colors[i + j * width]
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
}

class BlurhashView : AppCompatTextView {
	
	companion object {
		val log = LogCategory("BlurhashView")
		
		const val bitmapWidth = 16
		const val bitmapHeight = 16
	}
	
	constructor(context : Context) :
		super(context)
	
	constructor(context : Context, attrs : AttributeSet) :
		super(context, attrs)
	
	constructor(context : Context, attrs : AttributeSet, defStyleAttr : Int) :
		super(context, attrs, defStyleAttr)
	
	// keep bitmap and IntArray to reuse it.
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
			
			blurhashDecodeOk = if(v?.isEmpty() != false) {
				false
			} else try {
				Blurhash(v).render(pixels, bitmapWidth, bitmapHeight)
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
			
			field = v
			invalidate()
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
