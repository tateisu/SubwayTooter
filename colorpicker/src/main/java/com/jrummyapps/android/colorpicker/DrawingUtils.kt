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
import android.util.TypedValue

internal object DrawingUtils {
    fun dpToPx(c: Context, dipValue: Float): Int {
        val metrics = c.resources.displayMetrics
        val v = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics)
        val res = (v + 0.5).toInt() // Round
        // Ensure at least 1 pixel if val was > 0
        return if (res == 0 && v > 0) 1 else res
    }
}
