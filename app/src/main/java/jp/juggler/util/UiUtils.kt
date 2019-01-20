package jp.juggler.util

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.*
import android.graphics.drawable.shapes.RectShape
import android.os.Build
import android.os.SystemClock
import android.support.v4.content.ContextCompat
import android.util.SparseArray
import android.view.View
import android.widget.ImageView
import jp.juggler.subwaytooter.R
import org.jetbrains.anko.backgroundDrawable
import java.util.*

object UiUtils{
	val log = LogCategory("UiUtils")
}

// colorARGB.applyAlphaMultiplier(0.5f) でalpha値が半分になったARGB値を得る
fun Int.applyAlphaMultiplier(alphaMultiplier : Float? = null) : Int {
	return if(alphaMultiplier == null) {
		this
	} else {
		val rgb = (this and 0xffffff)
		val alpha = clipRange(0, 255, ((this ushr 24).toFloat() * alphaMultiplier + 0.5f).toInt())
		return rgb or (alpha shl 24)
	}
}


fun getAttributeColor(context : Context, attrId : Int) : Int {
	val theme = context.theme
	val a = theme.obtainStyledAttributes(intArrayOf(attrId))
	val color = a.getColor(0, Color.BLACK)
	a.recycle()
	return color
}


fun getAttributeDrawable(context : Context, attrId : Int) : Drawable {
	
	fun getAttributeResourceId(context : Context, attrId : Int) : Int {
		val theme = context.theme
		val a = theme.obtainStyledAttributes(intArrayOf(attrId))
		val resourceId = a.getResourceId(0, 0)
		a.recycle()
		if(resourceId == 0)
			throw RuntimeException(
				String.format(
					Locale.JAPAN,
					"attr not defined.attr_id=0x%x",
					attrId
				)
			)
		return resourceId
	}
	
	val drawableId = getAttributeResourceId(context, attrId)
	val d = ContextCompat.getDrawable(context, drawableId)
	return d ?: throw RuntimeException(
		String.format(
			Locale.JAPAN,
			"getDrawable failed. drawableId=0x%x",
			drawableId
		)
	)
}

/////////////////////////////////////////////////////////

// 後方互換用にボタン背景Drawableを生成する
private fun getStateListDrawable(normalColor : Int, pressedColor : Int) : StateListDrawable {
	val states = StateListDrawable()
	states.addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(pressedColor))
	states.addState(intArrayOf(android.R.attr.state_focused), ColorDrawable(pressedColor))
	states.addState(intArrayOf(android.R.attr.state_activated), ColorDrawable(pressedColor))
	states.addState(intArrayOf(), ColorDrawable(normalColor))
	return states
}

// 色を指定してRectShapeを生成する
private fun getRectShape(color : Int) : Drawable {
	val r = RectShape()
	val shapeDrawable = ShapeDrawable(r)
	shapeDrawable.paint.color = color
	return shapeDrawable
}

// 色を指定してRippleDrawableを生成する
fun getAdaptiveRippleDrawable(normalColor : Int, pressedColor : Int) : Drawable {
	return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
		RippleDrawable(
			ColorStateList.valueOf(pressedColor), getRectShape(normalColor), null
		)
	} else {
		getStateListDrawable(normalColor, pressedColor)
	}
}

/////////////////////////////////////////////////////////

private class ColorFilterCacheValue(
	val filter : ColorFilter,
	var lastUsed : Long
)

private val colorFilterCache = SparseArray<ColorFilterCacheValue>()
private var colorFilterCacheLastSweep = 0L

private fun createColorFilter(rgb : Int) : ColorFilter? {
	synchronized(colorFilterCache) {
		val now = SystemClock.elapsedRealtime()
		val cacheValue = colorFilterCache[rgb]
		if(cacheValue != null) {
			cacheValue.lastUsed = now
			return cacheValue.filter
		}
		
		val size = colorFilterCache.size()
		if(now - colorFilterCacheLastSweep >= 10000L && size >= 128) {
			colorFilterCacheLastSweep = now
			for(i in size - 1 downTo 0) {
				val v = colorFilterCache.valueAt(i)
				if(now - v.lastUsed >= 10000L) {
					colorFilterCache.removeAt(i)
				}
			}
		}
		
		val f = PorterDuffColorFilter(rgb, PorterDuff.Mode.SRC_ATOP)
		colorFilterCache.put(rgb, ColorFilterCacheValue(f, now))
		return f
	}
}

/////////////////////////////////////////////////////////

private class ColoredDrawableCacheKey(
	val drawableId : Int,
	val rgb : Int,
	val alpha : Int
) {
	
	override fun equals(other : Any?) : Boolean {
		return this === other || (
			other is ColoredDrawableCacheKey
				&& drawableId == other.drawableId
				&& rgb == other.rgb
				&& alpha == other.alpha
			)
	}
	
	override fun hashCode() : Int {
		return drawableId xor (rgb or (alpha shl 24))
	}
}

private class ColoredDrawableCacheValue(
	val drawable : Drawable,
	var lastUsed : Long
)

private val coloredDrawableCache = HashMap<ColoredDrawableCacheKey, ColoredDrawableCacheValue>()
private var coloredDrawableCacheLastSweep = 0L

fun createColoredDrawable(
	context : Context,
	drawableId : Int,
	color : Int,
	alphaMultiplier : Float
) : Drawable {
	val rgb = (color and 0xffffff) or Color.BLACK
	val alpha = if(alphaMultiplier >= 1f ) {
		(color ushr 24)
	} else {
		clipRange(0, 255, ((color ushr 24).toFloat() * alphaMultiplier + 0.5f).toInt())
	}
	
	val cacheKey = ColoredDrawableCacheKey(drawableId, rgb, alpha)
	synchronized(coloredDrawableCache) {
		val now = SystemClock.elapsedRealtime()
		val cacheValue = coloredDrawableCache[cacheKey]
		if(cacheValue != null) {
			cacheValue.lastUsed = now
			return cacheValue.drawable
		}
		
		if(now - coloredDrawableCacheLastSweep >= 10000L && coloredDrawableCache.size >= 128) {
			coloredDrawableCacheLastSweep = now
			val list = coloredDrawableCache.entries.sortedBy { it.value.lastUsed }
			for(i in 0 until list.size - 64) {
				val (k, v) = list[i]
				if(now - v.lastUsed <= 10000L) break
				coloredDrawableCache.remove(k)
			}
		}
		
		// 色指定が他のアイコンに影響しないようにする
		// カラーフィルターとアルファ値を設定する
		val d = ContextCompat.getDrawable(context, drawableId) !!.mutate()
		d.colorFilter = createColorFilter(rgb)
		d.alpha = alpha
		coloredDrawableCache[cacheKey] = ColoredDrawableCacheValue(d, now)
		return d
	}
}

//////////////////////////////////////////////////////////////////

fun setIconDrawableId(
	context : Context,
	imageView : ImageView,
	drawableId : Int,
	color : Int? = null,
	alphaMultiplier : Float
) {
	if(color == null) {
		// ImageViewにアイコンを設定する。デフォルトの色
		imageView.setImageDrawable(ContextCompat.getDrawable(context, drawableId))
	} else {
		imageView.setImageDrawable(
			createColoredDrawable(
				context,
				drawableId,
				color,
				alphaMultiplier
			)
		)
	}
}

//fun setIconAttr(
//	context : Context,
//	imageView : ImageView,
//	iconAttrId : Int,
//	color : Int? = null,
//	alphaMultiplier : Float? = null
//) {
//	setIconDrawableId(
//		context,
//		imageView,
//		getAttributeResourceId(context, iconAttrId),
//		color,
//		alphaMultiplier
//	)
//}

fun CharSequence.copyToClipboard(context:Context) {
	try {
		// Gets a handle to the clipboard service.
		val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
			?: throw NotImplementedError("missing ClipboardManager system service")
		
		// Creates a new text clip to put on the clipboard
		val clip = ClipData.newPlainText("text", this)
		
		// Set the clipboard's primary clip.
		
		clipboard.primaryClip = clip
		
		showToast(context, false, R.string.copy_complete)
	} catch(ex : Throwable) {
		UiUtils.log.trace(ex)
		showToast(context, ex, "copy failed.")
	}
	
}


fun DialogInterface.dismissSafe(){
	try {
		dismiss()
	} catch(ignored : Throwable) {
		// 非同期処理の後などではDialogがWindowTokenを失っている場合があり、IllegalArgumentException がたまに出る
	}
}

