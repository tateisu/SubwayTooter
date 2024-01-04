/*
 * Copyright 2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.anko

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import androidx.annotation.DimenRes
import androidx.fragment.app.Fragment

const val LDPI: Int = DisplayMetrics.DENSITY_LOW
const val MDPI: Int = DisplayMetrics.DENSITY_MEDIUM
const val HDPI: Int = DisplayMetrics.DENSITY_HIGH

const val TVDPI: Int = DisplayMetrics.DENSITY_TV
const val XHDPI: Int = DisplayMetrics.DENSITY_XHIGH
const val XXHDPI: Int = DisplayMetrics.DENSITY_XXHIGH
const val XXXHDPI: Int = DisplayMetrics.DENSITY_XXXHIGH

const val MAXDPI: Int = 0xfffe

// sp to px
fun DisplayMetrics.sp(sp: Float) =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, this)

fun Context.sp(sp: Float) = resources.displayMetrics.sp(sp)
fun Context.sp(sp: Int) = resources.displayMetrics.sp(sp.toFloat())

// px to sp
fun DisplayMetrics.px2sp(px: Float): Float = when {
    Build.VERSION.SDK_INT >= 34 ->
        TypedValue.deriveDimension(TypedValue.COMPLEX_UNIT_SP, px, this)

    else -> try {
        @Suppress("DEPRECATION")
        px / scaledDensity
    } catch (ex: Throwable) {
        0f
    }
}

fun Context.px2sp(px: Float): Float = resources.displayMetrics.px2sp(px)
fun Context.px2sp(px: Int): Float = resources.displayMetrics.px2sp(px.toFloat())

// dip to px
fun Context.dip(value: Int): Int = (value * resources.displayMetrics.density).toInt()
fun Context.dip(value: Float): Int = (value * resources.displayMetrics.density).toInt()

//converts px value into dip or sp
fun Context.px2dip(px: Int): Float = px.toFloat() / resources.displayMetrics.density

fun Context.dimen(@DimenRes resource: Int): Int = resources.getDimensionPixelSize(resource)

//the same for nested DSL components
fun AnkoContext<*>.dip(value: Int): Int = ctx.dip(value)
fun AnkoContext<*>.dip(value: Float): Int = ctx.dip(value)
fun AnkoContext<*>.sp(value: Int) = ctx.sp(value)
fun AnkoContext<*>.sp(value: Float) = ctx.sp(value)
fun AnkoContext<*>.px2dip(px: Int): Float = ctx.px2dip(px)
fun AnkoContext<*>.px2sp(px: Int): Float = ctx.px2sp(px)
fun AnkoContext<*>.dimen(@DimenRes resource: Int): Int = ctx.dimen(resource)

//the same for the views
fun View.dip(value: Int): Int = context.dip(value)
fun View.dip(value: Float): Int = context.dip(value)
fun View.sp(value: Int) = context.sp(value)
fun View.sp(value: Float) = context.sp(value)
fun View.px2dip(px: Int): Float = context.px2dip(px)
fun View.px2sp(px: Int): Float = context.px2sp(px)
fun View.dimen(@DimenRes resource: Int): Int = context.dimen(resource)

//the same for Fragments
fun Fragment.dip(value: Int): Int = requireContext().dip(value)
fun Fragment.dip(value: Float): Int = requireContext().dip(value)
fun Fragment.sp(value: Int) = requireContext().sp(value)
fun Fragment.sp(value: Float) = requireContext().sp(value)
fun Fragment.px2dip(px: Int): Float = requireContext().px2dip(px)
fun Fragment.px2sp(px: Int): Float = requireContext().px2sp(px)
fun Fragment.dimen(@DimenRes resource: Int): Int = requireContext().dimen(resource)
