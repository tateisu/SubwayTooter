package jp.juggler.util.data

import java.lang.ref.WeakReference

// 型推論できる文脈だと型名を書かずにすむ
@Suppress("unused")
inline fun <reified T : Any> Any?.cast(): T? = this as? T

@Suppress("unused")
inline fun <reified T : Any> Any.castNotNull(): T = this as T

val <T : Any> T.wrapWeakReference: WeakReference<T>
    get() = WeakReference(this)

////////////////////////////////////////////////////////////////////
// Comparable

fun <T : Comparable<T>> minComparable(a: T, b: T): T = if (a <= b) a else b
fun <T : Comparable<T>> maxComparable(a: T, b: T): T = if (a >= b) a else b

fun <T : Comparable<T>> T.clip(min: T, max: T) =
    if (this < min) min else if (this > max) max else this

////////////////////////////////////////////////////////////////////

// usage: number.notZero() ?: fallback
// equivalent: if(this != 0 ) this else null
fun Int.notZero(): Int? = if (this != 0) this else null
fun Long.notZero(): Long? = if (this != 0L) this else null
fun Float.notZero(): Float? = if (this != 0f) this else null
fun Double.notZero(): Double? = if (this != .0) this else null

////////////////////////////////////////////////////////////////////
// boolean
inline fun <T : Any?> Boolean.ifTrue(block: () -> T?) = if (this) block() else null
inline fun <T : Any?> Boolean.ifFalse(block: () -> T?) = if (this) null else block()

// usage: boolean.truth() ?: fallback()
// equivalent: if(this != 0 ) this else null
// fun Boolean.truth() : Boolean? = if(this) this else null

////////////////////////////////////////////////////////////////////
// long

////////////////////////////////////////////////////////////////////
// float

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
