package jp.juggler.util

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.util.Base64
import java.security.MessageDigest
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

object StringUtils {
	val log = LogCategory("StringUtils")
	
	val hexLower =
		charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
	
	// BDI制御文字からその制御文字を閉じる文字を得るためのマップ
	val sanitizeBdiMap = HashMap<Char, Char>().apply {
		
		val PDF = 0x202C.toChar() // Pop directional formatting (PDF)
		this[0x202A.toChar()] = PDF // Left-to-right embedding (LRE)
		this[0x202B.toChar()] = PDF // Right-to-left embedding (RLE)
		this[0x202D.toChar()] = PDF // Left-to-right override (LRO)
		this[0x202E.toChar()] = PDF // Right-to-left override (RLO)
		
		val PDI = 0x2069.toChar() // Pop directional isolate (PDI)
		this[0x2066.toChar()] = PDI // Left-to-right isolate (LRI)
		this[0x2067.toChar()] = PDI // Right-to-left isolate (RLI)
		this[0x2068.toChar()] = PDI // First strong isolate (FSI)
		
		//	private const val ALM = 0x061c.toChar() // Arabic letter mark (ALM)
		//	private const val LRM = 0x200E.toChar() //	Left-to-right mark (LRM)
		//	private const val RLM = 0x200F.toChar() //	Right-to-left mark (RLM)
	}
	
}

////////////////////////////////////////////////////////////////////
// ByteArray

fun ByteArray.encodeBase64Url() : String =
	Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

fun ByteArray.digestSHA256() : ByteArray {
	val digest = MessageDigest.getInstance("SHA-256")
	digest.reset()
	return digest.digest(this)
}

fun ByteArray.startWith(
	key : ByteArray,
	thisOffset : Int = 0,
	keyOffset : Int = 0,
	length : Int = key.size - keyOffset
) : Boolean {
	if(this.size - thisOffset >= length && key.size - keyOffset >= length) {
		for(i in 0 until length) {
			if(this[i + thisOffset] != key[i + keyOffset]) return false
		}
		return true
	}
	return false
}

// 各要素の下位8ビットを使ってバイト配列を作る
fun IntArray.toByteArray() : ByteArray {
	val dst = ByteArray(this.size)
	for(i in 0 until this.size) {
		dst[i] = this[i].toByte()
	}
	return dst
}

// 各要素の下位8ビットを使ってバイト配列を作る
fun CharArray.toLowerByteArray() : ByteArray {
	val dst = ByteArray(this.size)
	for(i in 0 until this.size) {
		dst[i] = this[i].toByte()
	}
	return dst
}

////////////////////////////////////////////////////////////////////
// CharSequence

fun CharSequence.replaceFirst(pattern : Pattern, replacement : String) : String =
	pattern.matcher(this).replaceFirst(replacement)

fun CharSequence.replaceAll(pattern : Pattern, replacement : String) : String =
	pattern.matcher(this).replaceAll(replacement)

// %1$s を含む文字列リソースを利用して装飾テキストの前後に文字列を追加する
fun CharSequence?.intoStringResource(context : Context, string_id : Int) : Spannable {
	
	val s = context.getString(string_id)
	val end = s.length
	val pos = s.indexOf("%1\$s")
	if(pos == - 1) return SpannableString(s)
	
	val sb = SpannableStringBuilder()
	if(pos > 0) sb.append(s.substring(0, pos))
	if(this != null) sb.append(this)
	if(pos + 4 < end) sb.append(s.substring(pos + 4, end))
	return sb
}

//fun Char.hex2int() : Int {
//	if( '0' <= this && this <= '9') return ((this-'0'))
//	if( 'A' <= this && this <= 'F') return ((this-'A')+0xa)
//	if( 'a' <= this && this <= 'f') return ((this-'a')+0xa)
//	return 0
//}

fun CharSequence.codePointBefore(index : Int) : Int {
	if(index > 0) {
		val c2 = this[index - 1]
		if(Character.isLowSurrogate(c2) && index > 1) {
			val c1 = this[index - 2]
			if(Character.isHighSurrogate(c1)) return Character.toCodePoint(c1, c2)
		}
		return c2.toInt()
	} else {
		return - 1
	}
}

inline fun <S : CharSequence, Z : Any?> S?.letNotEmpty(block : (S) -> Z?) : Z? =
	if(this?.isNotEmpty() == true) {
		block(this)
	} else {
		null
	}

// usage: str.notEmpty() ?: fallback
// equivalent: if(this.isNotEmpty() ) this else null
fun <S : CharSequence> S?.notEmpty() : S? = if(this?.isNotEmpty() == true) this else null

fun CharSequence.toUri() :Uri = Uri.parse(toString())

fun CharSequence?.mayUri() : Uri? = try {
	if(this?.isNotEmpty() == true)
		toUri()
	else
		null
} catch(ignored : Throwable) {
	null
}

////////////////////////////////////////////////////////////////////
// string

val charsetUTF8 = Charsets.UTF_8

// 文字列とバイト列の変換
fun String.encodeUTF8() = this.toByteArray(charsetUTF8)

fun ByteArray.decodeUTF8() = this.toString(charsetUTF8)

fun String.codePointCount(beginIndex : Int = 0) : Int = this.codePointCount(beginIndex, this.length)

// 16進ダンプ
fun ByteArray.encodeHex() : String {
	val sb = StringBuilder()
	for(b in this) {
		sb.appendHex2(b.toInt())
	}
	return sb.toString()
}

fun StringBuilder.appendHex2(value : Int) : StringBuilder {
	this.append(StringUtils.hexLower[(value shr 4) and 15])
	this.append(StringUtils.hexLower[value and 15])
	return this
}

fun ByteArray.encodeHexLower() : String {
	val size = this.size
	val sb = StringBuilder(size * 2)
	for(i in 0 until size) {
		val value = this[i].toInt()
		sb.append(StringUtils.hexLower[(value shr 4) and 15])
		sb.append(StringUtils.hexLower[value and 15])
	}
	return sb.toString()
}

fun String?.optInt() : Int? {
	return try {
		this?.toInt(10)
	} catch(ignored : Throwable) {
		null
	}
}

fun String?.filterNotEmpty() : String? = when {
	this == null -> null
	this.isEmpty() -> null
	else -> this
}

fun String?.ellipsizeDot3(max : Int) = when {
	this == null -> null
	this.length > max -> this.substring(0, max - 1) + "…"
	else -> this
}

//fun String.toCamelCase() : String {
//	val sb = StringBuilder()
//	for(s in this.split("_".toRegex())) {
//		if(s.isEmpty()) continue
//		sb.append(Character.toUpperCase(s[0]))
//		sb.append(s.substring(1, s.length).toLowerCase())
//	}
//	return sb.toString()
//}

fun ellipsize(src : String, limit : Int) : String =
	if(src.codePointCount(0, src.length) <= limit) {
		src
	} else {
		"${src.substring(0, src.offsetByCodePoints(0, limit))}…"
	}

fun String.sanitizeBDI() : String {
	
	// 文字列をスキャンしてBDI制御文字をスタックに入れていく
	var stack : LinkedList<Char>? = null
	for(c in this) {
		val closer = StringUtils.sanitizeBdiMap[c]
		if(closer != null) {
			if(stack == null) stack = LinkedList()
			stack.add(closer)
		} else if(stack?.isNotEmpty() == true && stack.last == c) {
			stack.removeLast()
		}
	}
	
	if(stack?.isNotEmpty() == true) {
		val sb = StringBuilder(this.length + stack.size)
		sb.append(this)
		while(! stack.isEmpty()) {
			sb.append(stack.removeLast())
		}
		return sb.toString()
	}
	
	return this
}

//fun String.dumpCodePoints() : CharSequence {
//	val sb = StringBuilder()
//	val length = this.length
//	var i=0
//	while(i<length) {
//		val cp = codePointAt(i)
//		sb.append(String.format("0x%x,", cp))
//		i += Character.charCount(cp)
//	}
//	return sb
//}

// 指定した文字数までの部分文字列を返す
// 文字列の長さが足りない場合は指定オフセットから終端までの長さを返す
//fun String.safeSubstring(count : Int, offset : Int = 0) = when {
//	offset + count <= length -> this.substring(offset, count)
//	else -> this.substring(offset, length)
//}

//// MD5ハッシュの作成
//@Suppress("unused")
//fun String.digestMD5() : String {
//	val md = MessageDigest.getInstance("MD5")
//	md.reset()
//	return md.digest(this.encodeUTF8()).encodeHex()
//}

fun String.digestSHA256Hex() : String {
	return this.encodeUTF8().digestSHA256().encodeHex()
}

fun String.digestSHA256Base64Url() : String {
	return this.encodeUTF8().digestSHA256().encodeBase64Url()
}



// Uri.encode(s:Nullable) だと nullチェックができないので、簡単なラッパーを用意する
fun String.encodePercent(allow : String? = null) : String = Uri.encode(this, allow)

// replace + to %20, then decode it.
fun String.decodePercent() : String =
	Uri.decode(replace("+", "%20"))

////////////////////////////////////////////////////////////////////
// Throwable

fun Throwable.withCaption(fmt : String?, vararg args : Any) =
	"${
	if(fmt == null || args.isEmpty())
		fmt
	else
		String.format(fmt, *args)
	}: ${this.javaClass.simpleName} ${this.message}"

fun Throwable.withCaption(resources : Resources, string_id : Int, vararg args : Any) =
	"${
	resources.getString(string_id, *args)
	}: ${this.javaClass.simpleName} ${this.message}"

////////////////////////////////////////////////////////////////////
// Bundle

fun Bundle.parseString(key : String) : String? {
	return try {
		this.getString(key)
	} catch(ignored : Throwable) {
		null
	}
}

////////////////////////////////////////////////////////////////
// Pattern

fun Matcher.groupOrNull(g : Int) : String? =
	if(groupCount() >= g) {
		group(g)
	} else {
		null
	}
