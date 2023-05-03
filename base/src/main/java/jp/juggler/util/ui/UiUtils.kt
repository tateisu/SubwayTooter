package jp.juggler.util.ui

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.media.RingtoneManager
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.util.SparseArray
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import jp.juggler.util.data.clip
import jp.juggler.util.data.notZero
import jp.juggler.util.getUriExtra
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast

private val log = LogCategory("UiUtils")

// colorARGB.applyAlphaMultiplier(0.5f) でalpha値が半分になったARGB値を得る
fun Int.applyAlphaMultiplier(alphaMultiplier: Float? = null): Int {
    return if (alphaMultiplier == null) {
        this
    } else {
        val rgb = (this and 0xffffff)
        val alpha = ((this ushr 24).toFloat() * alphaMultiplier + 0.5f).toInt().clip(0, 255)
        return rgb or (alpha shl 24)
    }
}

fun Context.attrColor(attrId: Int): Int {
    val a = theme.obtainStyledAttributes(intArrayOf(attrId))
    val color = a.getColor(0, Color.BLACK)
    a.recycle()
    return color
}

fun <T> TypedArray.use(block: (TypedArray) -> T): T =
    try {
        block(this)
    } finally {
        recycle()
    }

fun Context.getAttributeResourceId(attrId: Int) =
    theme.obtainStyledAttributes(intArrayOf(attrId))
        .use { it.getResourceId(0, 0) }
        .notZero() ?: error("missing resource id. attr_id=0x${attrId.toString(16)}")

fun Context.attrDrawable(attrId: Int): Drawable {
    val drawableId = getAttributeResourceId(attrId)
    return ContextCompat.getDrawable(this, drawableId)
        ?: error("getDrawable failed. drawableId=0x${drawableId.toString(16)}")
}

/////////////////////////////////////////////////////////

// 後方互換用にボタン背景Drawableを生成する
//private fun getStateListDrawable(normalColor : Int, pressedColor : Int) : StateListDrawable {
//	val states = StateListDrawable()
//	states.addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(pressedColor))
//	states.addState(intArrayOf(android.R.attr.state_focused), ColorDrawable(pressedColor))
//	states.addState(intArrayOf(android.R.attr.state_activated), ColorDrawable(pressedColor))
//	states.addState(intArrayOf(), ColorDrawable(normalColor))
//	return states
//}

// 色を指定してRectShapeを生成する
private fun getRectShape(color: Int): Drawable {
    val r = RectShape()
    val shapeDrawable = ShapeDrawable(r)
    shapeDrawable.paint.color = color
    return shapeDrawable
}

// 色を指定して角丸Drawableを作成する
fun createRoundDrawable(
    radius: Float,
    fillColor: Int? = null,
    strokeColor: Int? = null,
    strokeWidth: Int = 4,
) = GradientDrawable().apply {
    cornerRadius = radius
    if (fillColor != null) setColor(fillColor)
    if (strokeColor != null) setStroke(strokeWidth, strokeColor)
}

// 色を指定してRippleDrawableを生成する
fun getAdaptiveRippleDrawable(normalColor: Int, pressedColor: Int): Drawable {
    return RippleDrawable(ColorStateList.valueOf(pressedColor), getRectShape(normalColor), null)
}

// 色を指定してRippleDrawableを生成する
fun getAdaptiveRippleDrawableRound(
    context: Context,
    normalColor: Int,
    pressedColor: Int,
    roundNormal: Boolean = false,
): Drawable {
    val dp6 = context.resources.displayMetrics.density * 6f
    return if (roundNormal) {
        // 押してない時に通常色を塗る範囲も角丸にする
        RippleDrawable(
            ColorStateList.valueOf(pressedColor),
            createRoundDrawable(dp6, fillColor = normalColor),
            null
        )
    } else {
        // 押してない時に通常色を塗る範囲は四角だが、リップルエフェクトは角丸
        return RippleDrawable(
            ColorStateList.valueOf(pressedColor),
            getRectShape(normalColor),
            createRoundDrawable(dp6, Color.WHITE)
        )
    }
}

/////////////////////////////////////////////////////////

private class ColorFilterCacheValue(
    val filter: ColorFilter,
    var lastUsed: Long,
)

private val colorFilterCache = SparseArray<ColorFilterCacheValue>()
private var colorFilterCacheLastSweep = 0L

private fun createColorFilter(rgb: Int): ColorFilter {
    synchronized(colorFilterCache) {
        val now = SystemClock.elapsedRealtime()
        val cacheValue = colorFilterCache[rgb]
        if (cacheValue != null) {
            cacheValue.lastUsed = now
            return cacheValue.filter
        }

        val size = colorFilterCache.size()
        if (now - colorFilterCacheLastSweep >= 10000L && size >= 128) {
            colorFilterCacheLastSweep = now
            for (i in size - 1 downTo 0) {
                val v = colorFilterCache.valueAt(i)
                if (now - v.lastUsed >= 10000L) {
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
    val drawableId: Int,
    val rgb: Int,
    val alpha: Int,
) {

    override fun equals(other: Any?): Boolean {
        return this === other || (
                other is ColoredDrawableCacheKey &&
                        drawableId == other.drawableId &&
                        rgb == other.rgb &&
                        alpha == other.alpha
                )
    }

    override fun hashCode(): Int {
        return drawableId xor (rgb or (alpha shl 24))
    }
}

private class ColoredDrawableCacheValue(
    val drawable: Drawable,
    var lastUsed: Long,
)

private val coloredDrawableCache = HashMap<ColoredDrawableCacheKey, ColoredDrawableCacheValue>()
private var coloredDrawableCacheLastSweep = 0L

fun createColoredDrawable(
    context: Context,
    drawableId: Int,
    color: Int,
    alphaMultiplier: Float,
): Drawable {
    val rgb = (color and 0xffffff) or Color.BLACK
    val alpha = if (alphaMultiplier >= 1f) {
        (color ushr 24)
    } else {
        ((color ushr 24).toFloat() * alphaMultiplier + 0.5f).toInt().clip(0, 255)
    }

    val cacheKey = ColoredDrawableCacheKey(drawableId, rgb, alpha)
    synchronized(coloredDrawableCache) {
        val now = SystemClock.elapsedRealtime()
        val cacheValue = coloredDrawableCache[cacheKey]
        if (cacheValue != null) {
            cacheValue.lastUsed = now
            return cacheValue.drawable
        }

        if (now - coloredDrawableCacheLastSweep >= 10000L && coloredDrawableCache.size >= 128) {
            coloredDrawableCacheLastSweep = now
            val list = coloredDrawableCache.entries.sortedBy { it.value.lastUsed }
            for (i in 0 until list.size - 64) {
                val (k, v) = list[i]
                if (now - v.lastUsed <= 10000L) break
                coloredDrawableCache.remove(k)
            }
        }

        // 色指定が他のアイコンに影響しないようにする
        // カラーフィルターとアルファ値を設定する
        val d = ContextCompat.getDrawable(context, drawableId)!!.mutate()
        d.colorFilter = createColorFilter(rgb)
        d.alpha = alpha
        coloredDrawableCache[cacheKey] = ColoredDrawableCacheValue(d, now)
        return d
    }
}

//////////////////////////////////////////////////////////////////

fun setIconDrawableId(
    context: Context,
    imageView: ImageView,
    drawableId: Int,
    color: Int? = null,
    alphaMultiplier: Float = 1f,
) {
    if (color == null) {
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

fun DialogInterface.dismissSafe() {
    try {
        dismiss()
    } catch (ignored: Throwable) {
        // 非同期処理の後などではDialogがWindowTokenを失っている場合があり、IllegalArgumentException がたまに出る
    }
}

class CustomTextWatcher(
    val callback: () -> Unit,
) : TextWatcher {

    override fun beforeTextChanged(
        s: CharSequence,
        start: Int,
        count: Int,
        after: Int,
    ) {
    }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable) {
        callback()
    }
}

// ImageButton のForeground colorで有効/無効を表現する
fun ImageButton.setEnabledColor(context: Context, iconId: Int, color: Int, enabled: Boolean) {
    isEnabled = enabled
    setImageDrawable(
        createColoredDrawable(
            context = context,
            drawableId = iconId,
            color = color,
            alphaMultiplier = when (enabled) {
                true -> 1f
                else -> 0.5f
            }
        )
    )
}

var View.isEnabledAlpha: Boolean
    get() = isEnabled
    set(enabled) {
        this.isEnabled = enabled
        this.alpha = when (enabled) {
            true -> 1f
            else -> 0.3f
        }
    }

/////////////////////////////////////////////////

class ActivityResultHandler(
    private val log: LogCategory,
    private val callback: (ActivityResult) -> Unit,
) {
    private var launcher: ActivityResultLauncher<Intent>? = null
    private var getContext: (() -> Context?)? = null

    private val context
        get() = getContext?.invoke()

    // startForActivityResultの代わりに呼び出す
    fun launch(intent: Intent, options: ActivityOptionsCompat? = null) = try {
        (launcher ?: error("ActivityResultHandler not registered."))
            .launch(intent, options)
    } catch (ex: Throwable) {
        log.e(ex, "launch failed")
        context?.showToast(ex, "activity launch failed.")
    }

    // onCreate時に呼び出す
    fun register(a: FragmentActivity) {
        getContext = { a.applicationContext }
        this.launcher = a.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { callback(it) }
    }
}

fun Intent.launch(ar: ActivityResultHandler) = ar.launch(this)

val AppCompatActivity.isLiveActivity: Boolean
    get() = !(isFinishing || isDestroyed)

val ActivityResult.isNotOk
    get() = Activity.RESULT_OK != resultCode

/**
 * Ringtone pickerの処理結果のUriまたはnull
 */
val ActivityResult.decodeRingtonePickerResult
    get() = when {
        isNotOk -> null
        else -> data?.getUriExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
    }

fun AppCompatActivity.setNavigationBack(toolbar: Toolbar) =
    toolbar.setNavigationOnClickListener {
        onBackPressedDispatcher.onBackPressed()
    }
