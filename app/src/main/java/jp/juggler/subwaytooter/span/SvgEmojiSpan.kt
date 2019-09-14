package jp.juggler.subwaytooter.span

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.style.ReplacementSpan
import androidx.annotation.IntRange
import com.caverock.androidsvg.SVG
import jp.juggler.emoji.EmojiMap
import jp.juggler.util.LogCategory

// 絵文字リソースの種類によって異なるスパンを作る
fun EmojiMap.EmojiResource.createSpan(context : Context) = if(isSvg) {
	SvgEmojiSpan(context, assetsName!!)
} else {
	EmojiImageSpan(context, drawableId)
}

// SVG絵文字スパン
class SvgEmojiSpan internal constructor(
	context : Context,
	assetsName : String,
	private val scale : Float = 1f
) : ReplacementSpan() {
	
	companion object {
		
		internal val log = LogCategory("SvgEmojiSpan")

		private var assetsManager : AssetManager? = null
		
		private const val scale_ratio = 1.14f
		private const val descent_ratio = 0.211f

		// SVGの描画はBitmapを消費しないので、上限なしキャッシュ
		private class CacheResult(val svg : SVG?)
		private val cacheMap = HashMap<String, CacheResult>()
		
		private fun loadFromCache(assetsName : String) : SVG? {
			assetsManager ?: return null
			synchronized(cacheMap) {
				val item = cacheMap[assetsName]
				if(item != null) return item.svg
				val svg = try {
					SVG.getFromAsset(assetsManager, assetsName)
				} catch(ex : Throwable) {
					log.trace(ex)
					log.e(ex, "getFromAsset failed.")
					null
				}
				cacheMap[assetsName] = CacheResult(svg)
				return svg
			}
		}
	}
	
	init {
		if(assetsManager == null)
			assetsManager = context.applicationContext.assets
	}

	private val rect_dst = RectF()
	
	private val svg = loadFromCache(assetsName)

	override fun getSize(
		paint : Paint,
		text : CharSequence,
		@IntRange(from = 0) start : Int,
		@IntRange(from = 0) end : Int,
		fm : Paint.FontMetricsInt?
	) : Int {
		val size = (paint.textSize * scale_ratio * scale + 0.5f).toInt()
		if(fm != null) {
			val c_descent = (0.5f + size * descent_ratio).toInt()
			val c_ascent = c_descent - size
			if(fm.ascent > c_ascent) fm.ascent = c_ascent
			if(fm.top > c_ascent) fm.top = c_ascent
			if(fm.descent < c_descent) fm.descent = c_descent
			if(fm.bottom < c_descent) fm.bottom = c_descent
		}
		return size
	}

	override fun draw(
		canvas : Canvas,
		text : CharSequence,
		start : Int,
		end : Int,
		x : Float,
		top : Int,
		baseline : Int,
		bottom : Int,
		textPaint : Paint
	) {
		svg?:return
		
		val src_w = svg.documentWidth // the width in pixels, or -1 if there is no width available.
		val src_h = svg.documentHeight // the height in pixels, or -1 if there is no height available.
		val srcAspect = if( src_w <= 0f || src_h <=0f){
			// widthやheightの情報がない
			1f
		}else{
			src_w / src_h
		}
		
		// 絵文字の正方形のサイズ
		val dstSize = textPaint.textSize * scale_ratio * scale
		
		// ベースラインから上下方向にずらすオフセット
		val c_descent = dstSize * descent_ratio
		val transY = baseline - dstSize + c_descent
		
		// 絵文字のアスペクト比から描画範囲の幅と高さを決める
		val dstWidth : Float
		val dstHeight : Float
		if(srcAspect >= 1f) {
			dstWidth = dstSize
			dstHeight = dstSize / srcAspect
		} else {
			dstHeight = dstSize
			dstWidth = dstSize * srcAspect
		}
		val dstX = (dstSize - dstWidth) / 2f
		val dstY = (dstSize - dstHeight) / 2f
		
		rect_dst.set(x+dstX, transY+dstY , x+dstX+ dstWidth, transY+dstY+ dstHeight )
		svg.renderToCanvas(canvas,rect_dst)
	}
	
}
