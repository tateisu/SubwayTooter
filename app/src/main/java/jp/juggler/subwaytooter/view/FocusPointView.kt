package jp.juggler.subwaytooter.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import jp.juggler.subwaytooter.api.entity.TootAttachment
import jp.juggler.util.clipRange

class FocusPointView : View {

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context)
    }

    private val paint = Paint()
    private val rect = Rect()
    private val rectF = RectF()
    private var strokeWidth: Float = 0f
    private var circleRadius: Float = 0f
    private var crossRadius: Float = 0f
    private var attachment: TootAttachment? = null
    private var bitmap: Bitmap? = null
    var callback: (x: Float, y: Float) -> Unit = { _, _ -> }

    private var focusX: Float = 0f
    private var focusY: Float = 0f

    private fun init(context: Context) {

        paint.isFilterBitmap = true
        paint.isAntiAlias = true

        val density = context.resources.displayMetrics.density
        this.strokeWidth = density * 2f
        this.circleRadius = density * 10f
        this.crossRadius = density * 13f
    }

    fun setAttachment(
        attachment: TootAttachment,
        bitmap: Bitmap
    ) {
        this.attachment = attachment
        this.bitmap = bitmap
        this.focusX = attachment.focusX
        this.focusY = attachment.focusY
        invalidate()
    }

    private var scale: Float = 0f
    private var drawW: Float = 0f
    private var drawH: Float = 0f
    private var drawX: Float = 0f
    private var drawY: Float = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val bitmap = this.bitmap
        val attachment = this.attachment
        if (bitmap == null || attachment == null || bitmap.isRecycled) return

        // draw bitmap

        val viewW = this.width.toFloat()
        val viewH = this.height.toFloat()
        if (viewW <= 0f || viewH <= 0f) return

        val bitmapW = bitmap.width.toFloat()
        val bitmapH = bitmap.height.toFloat()
        if (bitmapW <= 0f || bitmapH <= 0f) return

        val viewAspect = viewW / viewH
        val bitmapAspect = bitmapW / bitmapH

        if (bitmapAspect >= viewAspect) {
            scale = viewW / bitmapW
            drawW = viewW
            drawH = scale * bitmapH
        } else {
            scale = viewH / bitmapH
            drawW = scale * bitmapW
            drawH = viewH
        }
        drawX = (viewW - drawW) * 0.5f
        drawY = (viewH - drawH) * 0.5f

        rect.left = 0
        rect.top = 0
        rect.right = bitmapW.toInt()
        rect.bottom = bitmapH.toInt()

        rectF.left = drawX
        rectF.top = drawY
        rectF.right = drawX + drawW
        rectF.bottom = drawY + drawH

        paint.style = Paint.Style.FILL
        canvas.drawBitmap(bitmap, rect, rectF, paint)

        // draw focus point

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        paint.color = when ((SystemClock.elapsedRealtime() / 500L) % 3) {
            2L -> Color.RED
            1L -> Color.BLUE
            else -> Color.GREEN
        }

        val pointX = (focusX + 1f) * 0.5f * drawW + drawX
        val pointY = (-focusY + 1f) * 0.5f * drawH + drawY
        canvas.drawCircle(pointX, pointY, circleRadius, paint)
        canvas.drawLine(pointX, pointY - crossRadius, pointX, pointY + crossRadius, paint)
        canvas.drawLine(pointX - crossRadius, pointY, pointX + crossRadius, pointY, paint)

        postInvalidateDelayed(500L)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                updateFocusPoint(event)
            }

            MotionEvent.ACTION_MOVE -> {
                updateFocusPoint(event)
            }

            MotionEvent.ACTION_UP -> {
                updateFocusPoint(event)
                callback(focusX, focusY)
            }
        }
        return true
    }

    private fun updateFocusPoint(event: MotionEvent) {
        focusX = clipRange(-1f, 1f, ((event.x - drawX) / drawW) * 2f - 1f)
        focusY = -clipRange(-1f, 1f, ((event.y - drawY) / drawH) * 2f - 1f)
        invalidate()
    }
}
