package it.sephiroth.android.library.exif2

import java.text.DecimalFormat

/**
 * Created by alessandro on 20/04/14.
 */
object ExifUtil {
	
	private val formatter = DecimalFormat.getInstance()
	
	fun processLensSpecifications(values : Array<Rational>) : String {
		val min_focal = values[0]
		val max_focal = values[1]
		val min_f = values[2]
		val max_f = values[3]
		
		formatter.maximumFractionDigits = 1
		
		val sb = StringBuilder()
		sb.append(formatter.format(min_focal.toDouble()))
		sb.append("-")
		sb.append(formatter.format(max_focal.toDouble()))
		sb.append("mm f/")
		sb.append(formatter.format(min_f.toDouble()))
		sb.append("-")
		sb.append(formatter.format(max_f.toDouble()))
		
		return sb.toString()
	}
	
}
