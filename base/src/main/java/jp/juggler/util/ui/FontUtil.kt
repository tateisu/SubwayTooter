package jp.juggler.util.ui

import android.graphics.Typeface
import io.github.inflationx.calligraphy3.CalligraphyConfig
import io.github.inflationx.calligraphy3.CalligraphyInterceptor
import io.github.inflationx.calligraphy3.CalligraphyTypefaceSpan
import io.github.inflationx.viewpump.ViewPump
import jp.juggler.base.R

fun initializeFont() {
    ViewPump.init(
        ViewPump.builder()
            .addInterceptor(
                CalligraphyInterceptor(
                    CalligraphyConfig.Builder()
                        .setFontAttrId(R.attr.fontPath)
                        .build()
                )
            )
            .build()
    )
}

fun fontSpan(tf: Typeface): Any = CalligraphyTypefaceSpan(tf)
