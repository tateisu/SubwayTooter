package jp.juggler.subwaytooter.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageButton

class CountImageButton : AppCompatImageButton {

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr) {
        init(context)
    }

    private val paint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
        typeface = Typeface.DEFAULT_BOLD
    }

    var density: Float = 1f
    var text: String = ""
    var textSize: Float = 12f

    private fun init(context: Context) {
        this.density = context.resources.displayMetrics.density
    }

    fun setTextColor(color: Int) {
        paint.color = color
        invalidate()
    }

    private var compoundPadding = 0
    private var paddingRightOriginal = 0
    private var paddingRightWithText = 0
    private val textBounds = Rect()
    private var textWidth = 0

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        paddingRightOriginal = right
        paddingRightWithText = right + when (textWidth) {
            0 -> 0
            else -> textWidth + compoundPadding
        }
        super.setPadding(left, top, paddingRightWithText, bottom)
    }

    fun setPaddingAndText(
        paddingH: Int,
        paddingV: Int,
        text: String,
        textSizeDp: Float,
        compoundPaddingDp: Float
    ) {
        this.text = text
        if (text.isEmpty()) {
            this.textSize = density * textSizeDp
            paint.textSize = textSize
            paint.getTextBounds(text, 0, text.length, textBounds)
            this.compoundPadding = 0
            textWidth = 0
        } else {
            this.textSize = density * textSizeDp
            paint.textSize = textSize
            paint.getTextBounds(text, 0, text.length, textBounds)
            this.compoundPadding = (density * compoundPaddingDp + 0.5f).toInt()
            textWidth = textBounds.width()
        }
        setPadding(paddingH, paddingV, paddingH, paddingV)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (textWidth > 0) {
            val cx = this.width - paddingRightOriginal - textWidth / 2
            val cy = this.height / 2
            canvas.drawText(
                text, cx - textBounds.exactCenterX(), cy - textBounds.exactCenterY(), paint
            )
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val lp = layoutParams
        val btnHeight = lp.height
        when (btnHeight) {
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT -> error("please set height.")
        }

        val modeH = MeasureSpec.getMode(heightMeasureSpec)
        val sizeH = MeasureSpec.getSize(heightMeasureSpec)
        val measuredH = when (modeH) {
            MeasureSpec.UNSPECIFIED -> btnHeight
            MeasureSpec.EXACTLY -> sizeH
            MeasureSpec.AT_MOST -> if (btnHeight <= sizeH) {
                btnHeight
            } else {
                sizeH
            }
            else -> btnHeight
        }
        val btnWidth = btnHeight + when (textWidth) {
            0 -> 0
            else -> textWidth + compoundPadding
        }
        val modeW = MeasureSpec.getMode(widthMeasureSpec)
        val sizeW = MeasureSpec.getSize(widthMeasureSpec)
        val measuredW = when (modeW) {
            MeasureSpec.UNSPECIFIED -> btnWidth
            MeasureSpec.EXACTLY -> sizeW
            MeasureSpec.AT_MOST -> if (btnWidth <= sizeW) {
                btnWidth
            } else {
                btnHeight
            }
            else -> btnWidth
        }

        setMeasuredDimension(measuredW, measuredH)
    }
}
