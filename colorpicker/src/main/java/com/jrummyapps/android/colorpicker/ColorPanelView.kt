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

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat

/**
 * This class draws a panel which which will be filled with a color which can be set. It can be used to show the
 * currently selected color which you will get from the [ColorPickerView].
 */
class ColorPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    companion object {
        private const val DEFAULT_BORDER_COLOR = -0x919192
    }

    /* The width in pixels of the border surrounding the color panel. */
    private val borderWidthPx = DrawingUtils.dpToPx(context, 1f)

    private val borderPaint = Paint().apply {
        isAntiAlias = true
    }

    private val colorPaint = Paint().apply {
        isAntiAlias = true
    }

    private val alphaPaint = Paint().apply {
        val bitmap = (ContextCompat.getDrawable(context, R.drawable.cpv_alpha) as BitmapDrawable)
            .bitmap
        shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        isAntiAlias = true
    }

    private val originalPaint = Paint()

    private val centerRect = RectF()
    private var drawingRect = Rect()
    private var colorRect = Rect()

    private var alphaPattern = AlphaPatternDrawable(DrawingUtils.dpToPx(context, 4f))

    private var showOldColor = false

    var borderColor = DEFAULT_BORDER_COLOR
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var color = Color.BLACK
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    @ColorShape
    private var shape = 0
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    init {
        val a = getContext().obtainStyledAttributes(attrs, R.styleable.ColorPanelView)
        shape = a.getInt(R.styleable.ColorPanelView_cpv_colorShape, ColorShape.CIRCLE)
        showOldColor = a.getBoolean(R.styleable.ColorPanelView_cpv_showOldColor, false)
        check(!(showOldColor && shape != ColorShape.CIRCLE)) { "Color preview is only available in circle mode" }
        borderColor = a.getColor(R.styleable.ColorPanelView_cpv_borderColor, DEFAULT_BORDER_COLOR)
        a.recycle()

        if (borderColor == DEFAULT_BORDER_COLOR) {
            // If no specific border color has been set we take the default secondary text color as border/slider color.
            // Thus it will adopt to theme changes automatically.
            val value = TypedValue()
            val typedArray = context.obtainStyledAttributes(
                value.data,
                intArrayOf(android.R.attr.textColorSecondary)
            )
            borderColor = typedArray.getColor(0, borderColor)
            typedArray.recycle()
        }
    }

    public override fun onSaveInstanceState(): Parcelable {
        val state = Bundle()
        state.putParcelable("instanceState", super.onSaveInstanceState())
        state.putInt("color", color)
        return state
    }

    public override fun onRestoreInstanceState(state: Parcelable) {
        if (state is Bundle) {
            color = state.getInt("color")
            super.onRestoreInstanceState(state.getParcelableCompat("instanceState"))
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    override fun onDraw(canvas: Canvas) {
        borderPaint.color = borderColor
        colorPaint.color = color
        if (shape == ColorShape.SQUARE) {
            if (borderWidthPx > 0) {
                canvas.drawRect(drawingRect, borderPaint)
            }
            alphaPattern.draw(canvas)
            canvas.drawRect(colorRect, colorPaint)
        } else if (shape == ColorShape.CIRCLE) {
            val outerRadius = measuredWidth / 2
            if (borderWidthPx > 0) {
                canvas.drawCircle(
                    measuredWidth / 2f,
                    measuredHeight / 2f,
                    outerRadius.toFloat(),
                    borderPaint
                )
            }
            if (Color.alpha(color) < 255) {
                canvas.drawCircle(
                    measuredWidth / 2f,
                    measuredHeight / 2f, (
                            outerRadius - borderWidthPx).toFloat(), alphaPaint
                )
            }
            if (showOldColor) {
                canvas.drawArc(centerRect, 90f, 180f, true, originalPaint)
                canvas.drawArc(centerRect, 270f, 180f, true, colorPaint)
            } else {
                canvas.drawCircle(
                    measuredWidth / 2f,
                    measuredHeight / 2f, (
                            outerRadius - borderWidthPx).toFloat(),
                    colorPaint
                )
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        when (shape) {
            ColorShape.SQUARE -> {
                val width = MeasureSpec.getSize(widthMeasureSpec)
                val height = MeasureSpec.getSize(heightMeasureSpec)
                setMeasuredDimension(width, height)
            }
            ColorShape.CIRCLE -> {
                super.onMeasure(widthMeasureSpec, widthMeasureSpec)
                setMeasuredDimension(measuredWidth, measuredWidth)
            }
            else -> {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (shape == ColorShape.SQUARE || showOldColor) {
            drawingRect.set(
                paddingLeft,
                paddingTop,
                w - paddingRight,
                h - paddingBottom
            )
            if (showOldColor) {
                setUpCenterRect()
            } else {
                setUpColorRect()
            }
        }
    }

    private fun setUpCenterRect() {
        val dRect = drawingRect
        val left = dRect.left + borderWidthPx
        val top = dRect.top + borderWidthPx
        val bottom = dRect.bottom - borderWidthPx
        val right = dRect.right - borderWidthPx
        centerRect.set(
            left.toFloat(),
            top.toFloat(),
            right.toFloat(),
            bottom.toFloat()
        )
    }

    private fun setUpColorRect() {
        val left = drawingRect.left + borderWidthPx
        val top = drawingRect.top + borderWidthPx
        val bottom = drawingRect.bottom - borderWidthPx
        val right = drawingRect.right - borderWidthPx
        colorRect.set(left, top, right, bottom)
        alphaPattern.setBounds(left, top, right, bottom)
    }

    /**
     * Set the original color. This is only used for previewing colors.
     *
     * @param color
     * The original color
     */
    fun setOriginalColor(@ColorInt color: Int) {
        originalPaint.color = color
    }

    /**
     * Show a toast message with the hex color code below the view.
     */
    fun showHint() {
        val screenPos = IntArray(2)
        val displayFrame = Rect()
        getLocationOnScreen(screenPos)
        getWindowVisibleDisplayFrame(displayFrame)
        val context = context
        val width = width
        val height = height
        val midy = screenPos[1] + height / 2
        var referenceX = screenPos[0] + width / 2
        if (ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_LTR) {
            val screenWidth = context.resources.displayMetrics.widthPixels
            referenceX = screenWidth - referenceX // mirror
        }
        val hexText = when {
            Color.alpha(color) == 255 -> "%06X".format(color and 0xFFFFFF)
            else -> Integer.toHexString(color)
        }
        val hint = "#${hexText.uppercase()}"
        val cheatSheet = Toast.makeText(context, hint, Toast.LENGTH_SHORT)
        if (midy < displayFrame.height()) {
            // Show along the top; follow action buttons
            cheatSheet.setGravity(
                Gravity.TOP or GravityCompat.END, referenceX,
                screenPos[1] + height - displayFrame.top
            )
        } else {
            // Show along the bottom center
            cheatSheet.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, height)
        }
        cheatSheet.show()
    }
}