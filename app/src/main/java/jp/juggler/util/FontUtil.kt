package jp.juggler.util

import android.graphics.Typeface
import jp.juggler.subwaytooter.R
import uk.co.chrisjenx.calligraphy.CalligraphyConfig
import uk.co.chrisjenx.calligraphy.CalligraphyTypefaceSpan

//import uk.co.chrisjenx.calligraphy.CalligraphyConfig
//import uk.co.chrisjenx.calligraphy.CalligraphyTypefaceSpan

fun initializeFont(){
	CalligraphyConfig.initDefault(
		CalligraphyConfig.Builder()
			.setFontAttrId(R.attr.fontPath)
			.build()
	)
}

fun fontSpan(tf : Typeface) : Any = CalligraphyTypefaceSpan(tf)

