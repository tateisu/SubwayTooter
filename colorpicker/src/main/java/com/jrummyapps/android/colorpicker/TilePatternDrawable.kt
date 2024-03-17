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

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import kotlin.math.min

/**
 * This drawable will draw a simple white and gray chessboard pattern.
 * It's the pattern you will often see as a background behind a partly transparent image in many applications.
 */
internal class TilePatternDrawable(
    private val rectangleSize: Int,
) : Drawable() {
    private val rect = Rect()
    private val paintWhite = Paint().apply { color = Color.WHITE }
    private val paintGray = Paint().apply { color = -0x343434 }

    override fun draw(canvas: Canvas) {
        val xEnd = bounds.right
        val yEnd = bounds.bottom
        var verticalStartWhite = true
        for (y in bounds.top until yEnd step rectangleSize) {
            rect.top = y
            rect.bottom = min(yEnd, y + rectangleSize)
            var isWhite = verticalStartWhite
            for (x in bounds.left until xEnd step rectangleSize) {
                rect.left = x
                rect.right = min(xEnd, x + rectangleSize)
                canvas.drawRect(rect, if (isWhite) paintWhite else paintGray)
                isWhite = !isWhite
            }
            verticalStartWhite = !verticalStartWhite
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity() = PixelFormat.OPAQUE

    override fun setAlpha(alpha: Int) {
        throw UnsupportedOperationException("Alpha is not supported by this drawable.")
    }

    override fun setColorFilter(cf: ColorFilter?) {
        throw UnsupportedOperationException("ColorFilter is not supported by this drawable.")
    }
}
