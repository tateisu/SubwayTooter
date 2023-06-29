package jp.juggler.util.ui

import android.graphics.Typeface
import io.github.inflationx.calligraphy3.CalligraphyConfig
import io.github.inflationx.calligraphy3.CalligraphyInterceptor
import io.github.inflationx.calligraphy3.CalligraphyTypefaceSpan
import io.github.inflationx.viewpump.ViewPump

val viewPumpFonts by lazy {
    ViewPump.builder()
        .addInterceptor(
            CalligraphyInterceptor(
                CalligraphyConfig.Builder()
                    // AGP8で参照するRクラスが分割されてエラーになる。
                    // 指定する必要もないと思う…
                    // .setFontAttrId(R.attr.fontPath)
                    .build()
            )
        )
        .build()
}

fun fontSpan(tf: Typeface): Any = CalligraphyTypefaceSpan(tf)
