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

import android.graphics.*
import android.graphics.drawable.Drawable
import kotlin.math.ceil

/**
 * This drawable will draw a simple white and gray chessboard pattern.
 * It's the pattern you will often see as a background behind a partly transparent image in many applications.
 */
internal class AlphaPatternDrawable(private val rectangleSize: Int) : Drawable() {
    private val paint = Paint()
    private val paintWhite = Paint().apply { color = Color.WHITE }
    private val paintGray = Paint().apply { color = -0x343434 }
    private var numRectanglesHorizontal = 0
    private var numRectanglesVertical = 0

    /**
     * Bitmap in which the pattern will be cached.
     * This is so the pattern will not have to be recreated each time draw() gets called.
     * Because recreating the pattern i rather expensive. I will only be recreated if the size changes.
     */
    private var bitmap: Bitmap? = null


    /**
     * This will generate a bitmap with the pattern as big as the rectangle we were allow to draw on.
     * We do this to chache the bitmap so we don't need to recreate it each time draw() is called since it takes a few milliseconds
     */
    private fun generatePatternBitmap() {
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return
        }
        val bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
            .also { this.bitmap = it }
        val canvas = Canvas(bitmap)
        val r = Rect()
        var verticalStartWhite = true
        for (i in 0..numRectanglesVertical) {
            var isWhite = verticalStartWhite
            for (j in 0..numRectanglesHorizontal) {
                r.top = i * rectangleSize
                r.left = j * rectangleSize
                r.bottom = r.top + rectangleSize
                r.right = r.left + rectangleSize
                canvas.drawRect(r, if (isWhite) paintWhite else paintGray)
                isWhite = !isWhite
            }
            verticalStartWhite = !verticalStartWhite
        }
    }

    override fun draw(canvas: Canvas) {
        val bitmap = this.bitmap
        if (bitmap != null && !bitmap.isRecycled) {
            canvas.drawBitmap(bitmap, null, bounds, paint)
        }
    }

    override fun getOpacity(): Int {
        return PixelFormat.UNKNOWN
    }

    override fun setAlpha(alpha: Int) {
        throw UnsupportedOperationException("Alpha is not supported by this drawable.")
    }

    override fun setColorFilter(cf: ColorFilter?) {
        throw UnsupportedOperationException("ColorFilter is not supported by this drawable.")
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val height = bounds.height()
        val width = bounds.width()
        numRectanglesHorizontal = ceil((width / rectangleSize.toFloat()).toDouble()).toInt()
        numRectanglesVertical = ceil((height / rectangleSize.toFloat()).toDouble()).toInt()
        generatePatternBitmap()
    }
}
