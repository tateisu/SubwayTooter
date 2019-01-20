package jp.juggler.util

////////////////////////////////////////////////////////////////////
// Comparable

fun <T : Comparable<T>> clipRange(min : T, max : T, src : T) =
	if(src < min) min else if(src > max) max else src

////////////////////////////////////////////////////////////////////

// usage: number.notZero() ?: fallback
// equivalent: if(this != 0 ) this else null
fun Int.notZero() : Int? = if(this != 0) this else null
fun Long.notZero() : Long? = if(this != 0L) this else null
fun Float.notZero() : Float? = if(this != 0f) this else null
fun Double.notZero() : Double? = if(this != .0) this else null


////////////////////////////////////////////////////////////////////
// long

////////////////////////////////////////////////////////////////////
// float

fun Float.abs() : Float = Math.abs(this)

//@SuppressLint("DefaultLocale")
//fun Long.formatTimeDuration() : String {
//	var t = this
//	val sb = StringBuilder()
//	var n : Long
//	// day
//	n = t / 86400000L
//	if(n > 0) {
//		sb.append(String.format(Locale.JAPAN, "%dd", n))
//		t -= n * 86400000L
//	}
//	// h
//	n = t / 3600000L
//	if(n > 0 || sb.isNotEmpty()) {
//		sb.append(String.format(Locale.JAPAN, "%dh", n))
//		t -= n * 3600000L
//	}
//	// m
//	n = t / 60000L
//	if(n > 0 || sb.isNotEmpty()) {
//		sb.append(String.format(Locale.JAPAN, "%dm", n))
//		t -= n * 60000L
//	}
//	// s
//	val f = t / 1000f
//	sb.append(String.format(Locale.JAPAN, "%.03fs", f))
//
//	return sb.toString()
//}

//private val bytesSizeFormat = DecimalFormat("#,###")
//fun Long.formatBytesSize() = Utils.bytesSizeFormat.format(this)

//		StringBuilder sb = new StringBuilder();
//		long n;
//		// giga
//		n = t / 1000000000L;
//		if( n > 0 ){
//			sb.append( String.format( Locale.JAPAN, "%dg", n ) );
//			t -= n * 1000000000L;
//		}
//		// Mega
//		n = t / 1000000L;
//		if( sb.length() > 0 ){
//			sb.append( String.format( Locale.JAPAN, "%03dm", n ) );
//			t -= n * 1000000L;
//		}else if( n > 0 ){
//			sb.append( String.format( Locale.JAPAN, "%dm", n ) );
//			t -= n * 1000000L;
//		}
//		// kilo
//		n = t / 1000L;
//		if( sb.length() > 0 ){
//			sb.append( String.format( Locale.JAPAN, "%03dk", n ) );
//			t -= n * 1000L;
//		}else if( n > 0 ){
//			sb.append( String.format( Locale.JAPAN, "%dk", n ) );
//			t -= n * 1000L;
//		}
//		// length
//		if( sb.length() > 0 ){
//			sb.append( String.format( Locale.JAPAN, "%03d", t ) );
//		}else if( n > 0 ){
//			sb.append( String.format( Locale.JAPAN, "%d", t ) );
//		}
//
//		return sb.toString();

