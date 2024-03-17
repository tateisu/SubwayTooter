package com.jrummyapps.android.colorpicker

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.TypedValue

internal fun Context.dpToPx(dipValue: Float): Int {
    val metrics = resources.displayMetrics
    val v = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics)
    val res = (v + 0.5).toInt() // Round
    // Ensure at least 1 pixel if val was > 0
    return if (res == 0 && v > 0) 1 else res
}

internal fun Context.dpToPx(dipValue: Int): Int =
    dpToPx(dipValue.toFloat())

internal inline fun <reified T : Parcelable> Bundle.getParcelableCompat(key: String) =
    if (Build.VERSION.SDK_INT >= 33) {
        getParcelable(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelable(key)
    }

// 単体テストするのでpublic
fun String.parseColor(): Int {
    val start = if (startsWith("#")) 1 else 0
    fun c1(offset: Int) = substring(start + offset, start + offset + 1).toInt(16) * 0x11
    fun c2(offset: Int) = substring(start + offset, start + offset + 2).toInt(16)

    return when (length - start) {
        0 -> Color.BLACK
        1 -> Color.argb(255, c1(0), c1(0), c1(0))
        2 -> Color.argb(255, c1(0), c1(1), 0x80)
        3 -> Color.argb(255, c1(0), c1(1), c1(2))
        4 -> Color.argb(c1(0), c1(1), c1(2), c1(3))
        5 -> Color.argb(255, c2(0), c2(2), c1(4))
        6 -> Color.argb(255, c2(0), c2(2), c2(4))
        7 -> Color.argb(c2(0), c2(2), c2(4), c1(6))
        8 -> Color.argb(c2(0), c2(2), c2(4), c2(6))
        else -> Color.WHITE
    }
}
