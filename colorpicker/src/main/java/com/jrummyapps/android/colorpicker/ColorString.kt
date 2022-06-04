package com.jrummyapps.android.colorpicker

import android.graphics.Color

fun parseColorString(src: String): Int {
    val start = if (src.startsWith("#")) 1 else 0

    fun c1(offset: Int) =
        src.substring(start + offset, start + offset + 1).toInt(16) * 0x11

    fun c2(offset: Int) =
        src.substring(start + offset, start + offset + 2).toInt(16)

    return when (src.length - start) {
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
