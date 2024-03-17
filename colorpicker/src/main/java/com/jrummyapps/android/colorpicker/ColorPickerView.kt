/*
 * Copyright (C) 2017 JRummy Apps Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jrummyapps.android.colorpicker

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Paint.Align
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Shader.TileMode
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.annotation.ColorInt
import kotlin.math.max
import kotlin.math.min

/**
 * Displays a color picker to the user and allow them to select a color. A slider for the alpha channel is also available.
 * Enable it by setting setAlphaSliderVisible(boolean) to true.
 */
class ColorPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    companion object {
        private const val DEFAULT_BORDER_COLOR = -0x919192
        private const val DEFAULT_SLIDER_COLOR = -0x424243
        private const val HUE_PANEL_WDITH_DP = 30
        private const val ALPHA_PANEL_HEIGH_DP = 20
        private const val PANEL_SPACING_DP = 10
        private const val CIRCLE_TRACKER_RADIUS_DP = 5
        private const val SLIDER_TRACKER_SIZE_DP = 4
        private const val SLIDER_TRACKER_OFFSET_DP = 2

        /**
         * The width in pixels of the border
         * surrounding all color panels.
         */
        private const val BORDER_WIDTH_PX = 1
    }

    fun interface OnColorChangedListener {
        fun onColorChanged(newColor: Int)
    }

    private class BitmapCache(
        var canvas: Canvas? = null,
        var bitmap: Bitmap? = null,
        var value: Float = 0f,
    )

    /**
     * The width in px of the hue panel.
     */
    private val huePanelWidthPx = context.dpToPx(HUE_PANEL_WDITH_DP)

    /**
     * The height in px of the alpha panel
     */
    private val alphaPanelHeightPx = context.dpToPx(ALPHA_PANEL_HEIGH_DP)

    /**
     * The distance in px between the different
     * color panels.
     */
    private val panelSpacingPx = context.dpToPx(PANEL_SPACING_DP)

    /**
     * The radius in px of the color palette tracker circle.
     */
    private val circleTrackerRadiusPx = context.dpToPx(CIRCLE_TRACKER_RADIUS_DP)

    /**
     * The px which the tracker of the hue or alpha panel
     * will extend outside of its bounds.
     */
    private val sliderTrackerOffsetPx = context.dpToPx(SLIDER_TRACKER_OFFSET_DP)

    /**
     * Height of slider tracker on hue panel,
     * width of slider on alpha panel.
     */
    private val sliderTrackerSizePx = context.dpToPx(SLIDER_TRACKER_SIZE_DP)

    /**
     * the current value of the text that will be shown in the alpha slider.
     * null to disable text.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    var alphaSliderText: String? = null
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    // the color the view should show.
    var color: Int
        get() = Color.HSVToColor(alpha, floatArrayOf(hue, sat, bri))
        set(color) {
            setColor(color, false)
        }

    @ColorInt
    var sliderTrackerColor = DEFAULT_SLIDER_COLOR
        set(value) {
            field = value
            hueAlphaTrackerPaint.color = value
            invalidate()
        }

    @ColorInt
    var borderColor = DEFAULT_BORDER_COLOR
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val satValPaint = Paint()

    private val satValTrackerPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpToPx(2f).toFloat()
        isAntiAlias = true
    }

    private val alphaPaint = Paint()

    private val alphaTextPaint = Paint().apply {
        color = -0xe3e3e4
        textSize = context.dpToPx(14f).toFloat()
        isAntiAlias = true
        textAlign = Align.CENTER
        isFakeBoldText = true
    }

    private val hueAlphaTrackerPaint = Paint().apply {
        color = sliderTrackerColor
        style = Paint.Style.STROKE
        strokeWidth = context.dpToPx(2f).toFloat()
        isAntiAlias = true
    }

    private val borderPaint = Paint()

    private var valShader: Shader? = null
    private var satShader: Shader? = null
    private var alphaShader: Shader? = null

    /*
     * We cache a bitmap of the sat/val panel which is expensive to draw each time.
     * We can reuse it when the user is sliding the circle picker as long as the hue isn't changed.
     */
    private var satValBackgroundCache: BitmapCache? = null

    /* We cache the hue background to since its also very expensive now. */
    private var hueBackgroundCache: BitmapCache? = null

    /* Current values */
    private var alpha = 0xff
    private var hue = 360f
    private var sat = 0f
    private var bri = 0f
    private var showAlphaPanel = false

    /**
     * Minimum required padding. The offset from the
     * edge we must have or else the finger tracker will
     * get clipped when it's drawn outside of the view.
     */
    private val mRequiredPadding =
        context.resources.getDimensionPixelSize(R.dimen.cpv_required_padding)

    /**
     * The Rect in which we are allowed to draw.
     * Trackers can extend outside slightly,
     * due to the required padding we have set.
     */
    private var drawingRect: Rect? = null
    private var satValRect: Rect? = null
    private var hueRect: Rect? = null
    private var alphaRect: Rect? = null
    private var startTouchPoint: Point? = null
    private var tilePatternDrawable: TilePatternDrawable? = null

    /**
     * OnColorChangedListener to get notified when the color selected by the user has changed.
     */
    var onColorChangedListener: OnColorChangedListener? = null

    public override fun onSaveInstanceState(): Parcelable {
        val state = Bundle()
        state.putParcelable("instanceState", super.onSaveInstanceState())
        state.putInt("alpha", alpha)
        state.putFloat("hue", hue)
        state.putFloat("sat", sat)
        state.putFloat("val", bri)
        state.putBoolean("show_alpha", showAlphaPanel)
        state.putString("alpha_text", alphaSliderText)
        return state
    }

    public override fun onRestoreInstanceState(state: Parcelable) {
        if (state is Bundle) {
            alpha = state.getInt("alpha")
            hue = state.getFloat("hue")
            sat = state.getFloat("sat")
            bri = state.getFloat("val")
            showAlphaPanel = state.getBoolean("show_alpha")
            alphaSliderText = state.getString("alpha_text")
            super.onRestoreInstanceState(state.getParcelableCompat("instanceState"))
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    init {
        //Load those if set in xml resource file.
        var a = context.obtainStyledAttributes(attrs, R.styleable.ColorPickerView)
        showAlphaPanel = a.getBoolean(R.styleable.ColorPickerView_cpv_alphaChannelVisible, false)
        alphaSliderText = a.getString(R.styleable.ColorPickerView_cpv_alphaChannelText)
        sliderTrackerColor = a.getColor(R.styleable.ColorPickerView_cpv_sliderColor, -0x424243)
        borderColor = a.getColor(R.styleable.ColorPickerView_cpv_borderColor, -0x919192)
        a.recycle()

        // If no specific border/slider color has been
        // set we take the default secondary text color
        // as border/slider color. Thus it will adopt
        // to theme changes automatically.
        val value = TypedValue()
        a = context.obtainStyledAttributes(
            value.data,
            intArrayOf(android.R.attr.textColorSecondary)
        )
        if (borderColor == DEFAULT_BORDER_COLOR) {
            borderColor = a.getColor(0, DEFAULT_BORDER_COLOR)
        }
        if (sliderTrackerColor == DEFAULT_SLIDER_COLOR) {
            sliderTrackerColor = a.getColor(0, DEFAULT_SLIDER_COLOR)
        }
        a.recycle()

        //Needed for receiving trackball motion events.
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onDraw(canvas: Canvas) {
        val drawingRect = this.drawingRect
        if (drawingRect == null || drawingRect.width() <= 0 || drawingRect.height() <= 0) {
            return
        }
        drawSatValPanel(canvas)
        drawHuePanel(canvas)
        drawAlphaPanel(canvas)
    }

    private fun drawSatValPanel(canvas: Canvas) {
        val rect = this.satValRect ?: return
        val drawingRect = this.drawingRect ?: return

        @Suppress("KotlinConstantConditions")
        if (BORDER_WIDTH_PX > 0) {
            borderPaint.color = borderColor
            canvas.drawRect(
                drawingRect.left.toFloat(),
                drawingRect.top.toFloat(),
                (rect.right + BORDER_WIDTH_PX).toFloat(),
                (rect.bottom + BORDER_WIDTH_PX).toFloat(), borderPaint
            )
        }
        if (valShader == null) {
            //Black gradient has either not been created or the view has been resized.
            valShader = LinearGradient(
                rect.left.toFloat(),
                rect.top.toFloat(),
                rect.left.toFloat(),
                rect.bottom.toFloat(),
                -0x1,
                -0x1000000,
                TileMode.CLAMP
            )
        }

        //If the hue has changed we need to recreate the cache.
        if (satValBackgroundCache == null || satValBackgroundCache!!.value != hue) {
            if (satValBackgroundCache == null) {
                satValBackgroundCache = BitmapCache()
            }

            //We create our bitmap in the cache if it doesn't exist.
            if (satValBackgroundCache!!.bitmap == null) {
                satValBackgroundCache!!.bitmap = Bitmap
                    .createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
            }

            //We create the canvas once so we can draw on our bitmap and the hold on to it.
            if (satValBackgroundCache!!.canvas == null) {
                satValBackgroundCache!!.canvas = Canvas(satValBackgroundCache!!.bitmap!!)
            }
            val rgb = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
            satShader = LinearGradient(
                rect.left.toFloat(),
                rect.top.toFloat(),
                rect.right.toFloat(),
                rect.top.toFloat(),
                -0x1,
                rgb,
                TileMode.CLAMP
            )
            satValPaint.shader = ComposeShader(
                valShader!!,
                satShader!!,
                PorterDuff.Mode.MULTIPLY
            )

            // Finally we draw on our canvas, the result will be
            // stored in our bitmap which is already in the cache.
            // Since this is drawn on a canvas not rendered on
            // screen it will automatically not be using the
            // hardware acceleration. And this was the code that
            // wasn't supported by hardware acceleration which mean
            // there is no need to turn it of anymore. The rest of
            // the view will still be hw accelerated.
            satValBackgroundCache!!.canvas!!.drawRect(
                0f, 0f,
                satValBackgroundCache!!.bitmap!!.width.toFloat(),
                satValBackgroundCache!!.bitmap!!.height.toFloat(),
                satValPaint
            )

            //We set the hue value in our cache to which hue it was drawn with,
            //then we know that if it hasn't changed we can reuse our cached bitmap.
            satValBackgroundCache!!.value = hue
        }

        // We draw our bitmap from the cached, if the hue has changed
        // then it was just recreated otherwise the old one will be used.
        canvas.drawBitmap(satValBackgroundCache!!.bitmap!!, null, rect, null)
        val p = satValToPoint(sat, bri)
        satValTrackerPaint.color = -0x1000000
        canvas.drawCircle(
            p.x.toFloat(),
            p.y.toFloat(),
            (circleTrackerRadiusPx - context.dpToPx(1f)).toFloat(),
            satValTrackerPaint
        )
        satValTrackerPaint.color = -0x222223
        canvas.drawCircle(
            p.x.toFloat(),
            p.y.toFloat(),
            circleTrackerRadiusPx.toFloat(),
            satValTrackerPaint
        )
    }

    private fun drawHuePanel(canvas: Canvas) {
        val rect = hueRect
        @Suppress("KotlinConstantConditions")
        if (BORDER_WIDTH_PX > 0) {
            borderPaint.color = borderColor
            canvas.drawRect(
                (rect!!.left - BORDER_WIDTH_PX).toFloat(), (
                        rect.top - BORDER_WIDTH_PX).toFloat(), (
                        rect.right + BORDER_WIDTH_PX).toFloat(), (
                        rect.bottom + BORDER_WIDTH_PX).toFloat(),
                borderPaint
            )
        }
        if (hueBackgroundCache == null) {
            val hueBackgroundCache = BitmapCache()
                .also { this.hueBackgroundCache = it }

            Bitmap.createBitmap(rect!!.width(), rect.height(), Bitmap.Config.ARGB_8888)
                .also {
                    hueBackgroundCache.bitmap = it
                    hueBackgroundCache.canvas = Canvas(it)
                }

            val hueColors = IntArray((rect.height() + 0.5f).toInt())

            // Generate array of all colors, will be drawn as individual lines.
            var h = 360f
            for (i in hueColors.indices) {
                hueColors[i] = Color.HSVToColor(floatArrayOf(h, 1f, 1f))
                h -= 360f / hueColors.size
            }

            // Time to draw the hue color gradient,
            // its drawn as individual lines which
            // will be quite many when the resolution is high
            // and/or the panel is large.
            val linePaint = Paint()
            linePaint.strokeWidth = 0f
            for (i in hueColors.indices) {
                linePaint.color = hueColors[i]
                hueBackgroundCache.canvas?.drawLine(
                    0f,
                    i.toFloat(),
                    hueBackgroundCache.bitmap?.width?.toFloat() ?: 0f,
                    i.toFloat(),
                    linePaint
                )
            }
        }
        canvas.drawBitmap(hueBackgroundCache!!.bitmap!!, null, rect!!, null)
        val p = hueToPoint(hue)
        val r = RectF()
        r.left = (rect.left - sliderTrackerOffsetPx).toFloat()
        r.right = (rect.right + sliderTrackerOffsetPx).toFloat()
        r.top = p.y - sliderTrackerSizePx / 2f
        r.bottom = p.y + sliderTrackerSizePx / 2f
        canvas.drawRoundRect(r, 2f, 2f, hueAlphaTrackerPaint)
    }

    private fun drawAlphaPanel(canvas: Canvas) {
        if (!showAlphaPanel) return
        val rect = this.alphaRect ?: return
        val alphaPatternDrawable = this.tilePatternDrawable ?: return

        /*
         * Will be drawn with hw acceleration, very fast.
         * Also the AlphaPatternDrawable is backed by a bitmap
         * generated only once if the size does not change.
         */
        @Suppress("KotlinConstantConditions")
        if (BORDER_WIDTH_PX > 0) {
            borderPaint.color = borderColor
            canvas.drawRect(
                (rect.left - BORDER_WIDTH_PX).toFloat(), (
                        rect.top - BORDER_WIDTH_PX).toFloat(), (
                        rect.right + BORDER_WIDTH_PX).toFloat(), (
                        rect.bottom + BORDER_WIDTH_PX).toFloat(),
                borderPaint
            )
        }
        alphaPatternDrawable.draw(canvas)
        val hsv = floatArrayOf(hue, sat, bri)
        val color = Color.HSVToColor(hsv)
        val acolor = Color.HSVToColor(0, hsv)
        alphaShader = LinearGradient(
            rect.left.toFloat(),
            rect.top.toFloat(),
            rect.right.toFloat(),
            rect.top.toFloat(),
            color, acolor, TileMode.CLAMP
        )
        alphaPaint.shader = alphaShader
        canvas.drawRect(rect, alphaPaint)

        alphaSliderText
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                canvas.drawText(
                    it,
                    rect.centerX().toFloat(),
                    (rect.centerY() + context.dpToPx(4f)).toFloat(),
                    alphaTextPaint
                )
            }

        val p = alphaToPoint(alpha)
        val r = RectF()
        r.left = p.x - sliderTrackerSizePx / 2f
        r.right = p.x + sliderTrackerSizePx / 2f
        r.top = (rect.top - sliderTrackerOffsetPx).toFloat()
        r.bottom = (rect.bottom + sliderTrackerOffsetPx).toFloat()
        canvas.drawRoundRect(r, 2f, 2f, hueAlphaTrackerPaint)
    }

    private fun hueToPoint(hue: Float): Point {
        val rect = hueRect
        val height = rect!!.height().toFloat()
        val p = Point()
        p.y = (height - hue * height / 360f + rect.top).toInt()
        p.x = rect.left
        return p
    }

    private fun satValToPoint(sat: Float, inValue: Float): Point {
        val rect = satValRect!!
        val height = rect.height().toFloat()
        val width = rect.width().toFloat()
        val p = Point()
        p.x = (sat * width + rect.left).toInt()
        p.y = ((1f - inValue) * height + rect.top).toInt()
        return p
    }

    private fun alphaToPoint(alpha: Int): Point {
        val rect = alphaRect
        val width = rect!!.width().toFloat()
        val p = Point()
        p.x = (width - alpha * width / 0xff + rect.left).toInt()
        p.y = rect.top
        return p
    }

    private fun pointToSatVal(xArg: Float, yArg: Float): FloatArray {
        var x = xArg
        var y = yArg
        val rect = satValRect
        val result = FloatArray(2)
        val width = rect!!.width().toFloat()
        val height = rect.height().toFloat()
        x = when {
            x < rect.left -> 0f
            x > rect.right -> width
            else -> x - rect.left
        }
        y = when {
            y < rect.top -> 0f
            y > rect.bottom -> height
            else -> y - rect.top
        }
        result[0] = 1f / width * x
        result[1] = 1f - 1f / height * y
        return result
    }

    private fun pointToHue(yArg: Float): Float {
        var y = yArg
        val rect = hueRect
        val height = rect!!.height().toFloat()
        y = when {
            y < rect.top -> 0f
            y > rect.bottom -> height
            else -> y - rect.top
        }
        return 360f - y * 360f / height
    }

    private fun pointToAlpha(xArg: Int): Int {
        var x = xArg
        val rect = alphaRect
        val width = rect!!.width()
        x = when {
            x < rect.left -> 0
            x > rect.right -> width
            else -> x - rect.left
        }
        return 0xff - x * 0xff / width
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        try {
            this.parent.requestDisallowInterceptTouchEvent(true)
        } catch (ignored: Throwable) {
        }
        var update = false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startTouchPoint = Point(event.x.toInt(), event.y.toInt())
                update = moveTrackersIfNeeded(event)
            }

            MotionEvent.ACTION_MOVE -> update = moveTrackersIfNeeded(event)
            MotionEvent.ACTION_UP -> {
                startTouchPoint = null
                update = moveTrackersIfNeeded(event)
            }
        }
        if (update) {
            onColorChangedListener?.onColorChanged(
                Color.HSVToColor(
                    alpha,
                    floatArrayOf(hue, sat, bri)
                )
            )
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun moveTrackersIfNeeded(event: MotionEvent): Boolean {
        val startTouchPoint = this.startTouchPoint ?: return false
        val startX = startTouchPoint.x
        val startY = startTouchPoint.y
        return when {
            hueRect?.contains(startX, startY) == true -> {
                hue = pointToHue(event.y)
                true
            }

            satValRect?.contains(startX, startY) == true -> {
                val result = pointToSatVal(event.x, event.y)
                sat = result[0]
                bri = result[1]
                true
            }

            alphaRect?.contains(startX, startY) == true -> {
                alpha = pointToAlpha(event.x.toInt())
                true
            }

            else -> false
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val finalWidth: Int
        val finalHeight: Int
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val widthAllowed = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val heightAllowed = MeasureSpec.getSize(heightMeasureSpec) - paddingBottom - paddingTop
        if (widthMode == MeasureSpec.EXACTLY || heightMode == MeasureSpec.EXACTLY) {
            //A exact value has been set in either direction, we need to stay within this size.
            if (widthMode == MeasureSpec.EXACTLY && heightMode != MeasureSpec.EXACTLY) {
                //The with has been specified exactly, we need to adopt the height to fit.
                var h = widthAllowed - panelSpacingPx - huePanelWidthPx
                if (showAlphaPanel) {
                    h += panelSpacingPx + alphaPanelHeightPx
                }

                //We can't fit the view in this container, set the size to whatever was allowed.
                finalHeight = min(h, heightAllowed)
                finalWidth = widthAllowed
            } else if (widthMode != MeasureSpec.EXACTLY) {
                //The height has been specified exactly, we need to stay within this height and adopt the width.
                var w = heightAllowed + panelSpacingPx + huePanelWidthPx
                if (showAlphaPanel) {
                    w -= panelSpacingPx + alphaPanelHeightPx
                }

                //we can't fit within this container, set the size to whatever was allowed.
                finalWidth = min(w, widthAllowed)
                finalHeight = heightAllowed
            } else {
                //If we get here the dev has set the width and height to exact sizes. For example match_parent or 300dp.
                //This will mean that the sat/val panel will not be square but it doesn't matter. It will work anyway.
                //In all other senarios our goal is to make that panel square.

                //We set the sizes to exactly what we were told.
                finalWidth = widthAllowed
                finalHeight = heightAllowed
            }
        } else {
            //If no exact size has been set we try to make our view as big as possible
            //within the allowed space.

            //Calculate the needed width to layout using max allowed height.
            var widthNeeded = heightAllowed + panelSpacingPx + huePanelWidthPx

            //Calculate the needed height to layout using max allowed width.
            var heightNeeded = widthAllowed - panelSpacingPx - huePanelWidthPx
            if (showAlphaPanel) {
                widthNeeded -= panelSpacingPx + alphaPanelHeightPx
                heightNeeded += panelSpacingPx + alphaPanelHeightPx
            }
            val widthOk = widthNeeded <= widthAllowed
            val heightOk = heightNeeded <= heightAllowed
            when {
                widthOk && heightOk -> {
                    finalWidth = widthAllowed
                    finalHeight = heightNeeded
                }

                widthOk -> {
                    finalHeight = heightAllowed
                    finalWidth = widthNeeded
                }

                heightOk -> {
                    finalHeight = heightNeeded
                    finalWidth = widthAllowed
                }

                else -> {
                    finalHeight = heightAllowed
                    finalWidth = widthAllowed
                }
            }
        }
        setMeasuredDimension(
            finalWidth + paddingLeft + paddingRight,
            finalHeight + paddingTop + paddingBottom
        )
    }

    override fun getPaddingTop(): Int =
        max(super.getPaddingTop(), mRequiredPadding)

    override fun getPaddingBottom(): Int =
        max(super.getPaddingBottom(), mRequiredPadding)

    override fun getPaddingLeft(): Int =
        max(super.getPaddingLeft(), mRequiredPadding)

    override fun getPaddingRight(): Int =
        max(super.getPaddingRight(), mRequiredPadding)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val drawingRect = Rect().also { this.drawingRect = it }
        drawingRect.left = paddingLeft
        drawingRect.right = w - paddingRight
        drawingRect.top = paddingTop
        drawingRect.bottom = h - paddingBottom

        //The need to be recreated because they depend on the size of the view.
        valShader = null
        satShader = null
        alphaShader = null

        // Clear those bitmap caches since the size may have changed.
        satValBackgroundCache = null
        hueBackgroundCache = null
        setUpSatValRect()
        setUpHueRect()
        setUpAlphaRect()
    }

    private fun setUpSatValRect() {
        //Calculate the size for the big color rectangle.
        val dRect = drawingRect!!
        val left = dRect.left + BORDER_WIDTH_PX
        val top = dRect.top + BORDER_WIDTH_PX
        var bottom = dRect.bottom - BORDER_WIDTH_PX
        val right = dRect.right - BORDER_WIDTH_PX - panelSpacingPx - huePanelWidthPx
        if (showAlphaPanel) {
            bottom -= alphaPanelHeightPx + panelSpacingPx
        }
        satValRect = Rect(left, top, right, bottom)
    }

    private fun setUpHueRect() {
        //Calculate the size for the hue slider on the left.
        val dRect = drawingRect!!
        val left = dRect.right - huePanelWidthPx + BORDER_WIDTH_PX
        val top = dRect.top + BORDER_WIDTH_PX
        val bottom = dRect.bottom - BORDER_WIDTH_PX -
                if (showAlphaPanel) panelSpacingPx + alphaPanelHeightPx else 0
        val right = dRect.right - BORDER_WIDTH_PX
        hueRect = Rect(left, top, right, bottom)
    }

    private fun setUpAlphaRect() {
        if (!showAlphaPanel) return

        val dRect = drawingRect ?: return

        val left = dRect.left + BORDER_WIDTH_PX
        val top = dRect.bottom - alphaPanelHeightPx + BORDER_WIDTH_PX
        val bottom = dRect.bottom - BORDER_WIDTH_PX
        val right = dRect.right - BORDER_WIDTH_PX

        val alphaRect = Rect(left, top, right, bottom)
            .also { this.alphaRect = it }

        tilePatternDrawable = TilePatternDrawable(context.dpToPx(4f))
            .apply {
                setBounds(
                    alphaRect.left,
                    alphaRect.top,
                    alphaRect.right,
                    alphaRect.bottom
                )
            }
    }

    /**
     * Set the color this view should show.
     *
     * @param color    The color that should be selected. #argb
     * @param callback If you want to get a callback to your OnColorChangedListener.
     */
    fun setColor(color: Int, callback: Boolean) {
        val alpha = Color.alpha(color)
        val red = Color.red(color)
        val blue = Color.blue(color)
        val green = Color.green(color)
        val hsv = FloatArray(3)
        Color.RGBToHSV(red, green, blue, hsv)
        this.alpha = alpha
        hue = hsv[0]
        sat = hsv[1]
        bri = hsv[2]
        if (callback) {
            onColorChangedListener
                ?.onColorChanged(Color.HSVToColor(this.alpha, floatArrayOf(hue, sat, bri)))
        }
        invalidate()
    }

    /**
     * Set if the user is allowed to adjust the alpha panel. Default is false.
     * If it is set to false no alpha will be set.
     *
     * @param visible `true` to show the alpha slider
     */
    fun setAlphaSliderVisible(visible: Boolean) {
        if (showAlphaPanel != visible) {
            showAlphaPanel = visible

            /*
             * Force recreation.
             */
            valShader = null
            satShader = null
            alphaShader = null
            hueBackgroundCache = null
            satValBackgroundCache = null
            requestLayout()
        }
    }

    /**
     * Set the text that should be shown in the
     * alpha slider. Set to null to disable text.
     *
     * @param res string resource id.
     */
    fun setAlphaSliderText(res: Int) {
        alphaSliderText = context.getString(res)
    }
}