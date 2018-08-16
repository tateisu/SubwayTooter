package jp.juggler.subwaytooter.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v7.widget.RecyclerView
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.util.Base64
import android.util.SparseBooleanArray
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.MimeTypeMap
import android.widget.Toast
import me.drakeet.support.toast.ToastCompat
import org.apache.commons.io.IOUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.security.MessageDigest
import java.util.LinkedList
import java.util.Locale
import java.util.regex.Pattern
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.isEmpty
import kotlin.collections.isNotEmpty
import kotlin.collections.set
import kotlin.collections.toString

object Utils {
	
	val log = LogCategory("Utils")
	
	val hex =
		charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
	
	/////////////////////////////////////////////
	
	private val taisaku_map : HashMap<Char, String>
	private val taisaku_map2 : SparseBooleanArray
	
	//	public static int getEnumStringId( String residPrefix, String name,Context context ) {
	//		name = residPrefix + name;
	//		try{
	//			int iv = context.getResources().getIdentifier(name,"string",context.getPackageName() );
	//			if( iv != 0 ) return iv;
	//		}catch(Throwable ex){
	//		}
	//		warning.e("missing resid for %s",name);
	//		return R.string.Dialog_Cancel;
	//	}
	
	//	public static String getConnectionResultErrorMessage( ConnectionResult connectionResult ){
	//		int code = connectionResult.getErrorCode();
	//		String msg = connectionResult.getErrorMessage();
	//		if( msg == null || msg.isEmpty(  ) ){
	//			switch( code ){
	//			case ConnectionResult.SUCCESS:
	//				msg = "SUCCESS";
	//				break;
	//			case ConnectionResult.SERVICE_MISSING:
	//				msg = "SERVICE_MISSING";
	//				break;
	//			case ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED:
	//				msg = "SERVICE_VERSION_UPDATE_REQUIRED";
	//				break;
	//			case ConnectionResult.SERVICE_DISABLED:
	//				msg = "SERVICE_DISABLED";
	//				break;
	//			case ConnectionResult.SIGN_IN_REQUIRED:
	//				msg = "SIGN_IN_REQUIRED";
	//				break;
	//			case ConnectionResult.INVALID_ACCOUNT:
	//				msg = "INVALID_ACCOUNT";
	//				break;
	//			case ConnectionResult.RESOLUTION_REQUIRED:
	//				msg = "RESOLUTION_REQUIRED";
	//				break;
	//			case ConnectionResult.NETWORK_ERROR:
	//				msg = "NETWORK_ERROR";
	//				break;
	//			case ConnectionResult.INTERNAL_ERROR:
	//				msg = "INTERNAL_ERROR";
	//				break;
	//			case ConnectionResult.SERVICE_INVALID:
	//				msg = "SERVICE_INVALID";
	//				break;
	//			case ConnectionResult.DEVELOPER_ERROR:
	//				msg = "DEVELOPER_ERROR";
	//				break;
	//			case ConnectionResult.LICENSE_CHECK_FAILED:
	//				msg = "LICENSE_CHECK_FAILED";
	//				break;
	//			case ConnectionResult.CANCELED:
	//				msg = "CANCELED";
	//				break;
	//			case ConnectionResult.TIMEOUT:
	//				msg = "TIMEOUT";
	//				break;
	//			case ConnectionResult.INTERRUPTED:
	//				msg = "INTERRUPTED";
	//				break;
	//			case ConnectionResult.API_UNAVAILABLE:
	//				msg = "API_UNAVAILABLE";
	//				break;
	//			case ConnectionResult.SIGN_IN_FAILED:
	//				msg = "SIGN_IN_FAILED";
	//				break;
	//			case ConnectionResult.SERVICE_UPDATING:
	//				msg = "SERVICE_UPDATING";
	//				break;
	//			case ConnectionResult.SERVICE_MISSING_PERMISSION:
	//				msg = "SERVICE_MISSING_PERMISSION";
	//				break;
	//			case ConnectionResult.RESTRICTED_PROFILE:
	//				msg = "RESTRICTED_PROFILE";
	//				break;
	//
	//			}
	//		}
	//		return msg;
	//	}
	
	//	public static String getConnectionSuspendedMessage( int i ){
	//		switch( i ){
	//		default:
	//			return "?";
	//		case GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST:
	//			return "NETWORK_LOST";
	//		case GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED:
	//			return "SERVICE_DISCONNECTED";
	//		}
	//	}
	
	private const val MIME_TYPE_APPLICATION_OCTET_STREAM = "application/octet-stream"
	
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
	
	private var refToast : WeakReference<Toast>? = null
	
	internal fun showToastImpl(context : Context, bLong : Boolean, message : String) {
		runOnMainLooper {
			
			// 前回のトーストの表示を終了する
			try {
				refToast?.get()?.cancel()
			} catch(ex : Throwable) {
				log.trace(ex)
			} finally {
				refToast = null
			}
			
			// 新しいトーストを作る
			try {
				val duration = if(bLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
				val t = ToastCompat.makeText(context, message, duration)
				t.setBadTokenListener {}
				t.show()
				refToast = WeakReference(t)
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			
			// コールスタックの外側でエラーになる…
			// android.view.WindowManager$BadTokenException:
			// at android.view.ViewRootImpl.setView (ViewRootImpl.java:679)
			// at android.view.WindowManagerGlobal.addView (WindowManagerGlobal.java:342)
			// at android.view.WindowManagerImpl.addView (WindowManagerImpl.java:94)
			// at android.widget.Toast$TN.handleShow (Toast.java:435)
			// at android.widget.Toast$TN$2.handleMessage (Toast.java:345)
		}
	}
	
	//	fun url2name(url : String?) : String? {
	//		return if(url == null) null else encodeBase64Url(encodeSHA256(encodeUTF8(url)))
	//	}
	
	//	public static String name2url(String entry) {
	//		if(entry==null) return null;
	//		byte[] b = new byte[entry.length()/2];
	//		for(int i=0,ie=b.length;i<ie;++i){
	//			b[i]= (byte)((hex2int(entry.charAt(i*2))<<4)| hex2int(entry.charAt(i*2+1)));
	//		}
	//		return decodeUTF8(b);
	//	}
	
	///////////////////////////////////////////////////
	
	private fun _taisaku_add_string(z : String, h : String) {
		var i = 0
		val e = z.length
		while(i < e) {
			val zc = z[i]
			taisaku_map[zc] = h[i].toString()
			taisaku_map2.put(zc.toInt(), true)
			++ i
		}
	}
	
	init {
		taisaku_map = HashMap()
		taisaku_map2 = SparseBooleanArray()
		
		// tilde,wave dash,horizontal ellipsis,minus sign
		_taisaku_add_string(
			"\u2073\u301C\u22EF\uFF0D", "\u007e\uFF5E\u2026\u2212"
		)
		// zenkaku to hankaku
		_taisaku_add_string(
			"　！”＃＄％＆’（）＊＋，－．／０１２３４５６７８９：；＜＝＞？＠ＡＢＣＤＥＦＧＨＩＪＫＬＭＮＯＰＱＲＳＴＵＶＷＸＹＺ［］＾＿｀ａｂｃｄｅｆｇｈｉｊｋｌｍｎｏｐｑｒｓｔｕｖｗｘｙｚ｛｜｝",
			" !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_`abcdefghijklmnopqrstuvwxyz{|}"
		)
		
	}
	
	private fun isBadChar2(c : Char) : Boolean {
		return c.toInt() == 0xa || taisaku_map2.get(c.toInt())
	}
	
	//! フォントによって全角文字が化けるので、その対策
	@Suppress("unused")
	fun font_taisaku(text : String?, lf2br : Boolean) : String? {
		if(text == null) return null
		val l = text.length
		val sb = StringBuilder(l)
		if(! lf2br) {
			var i = 0
			while(i < l) {
				val start = i
				while(i < l && ! taisaku_map2.get(text[i].toInt())) ++ i
				if(i > start) {
					sb.append(text.substring(start, i))
					if(i >= l) break
				}
				sb.append(taisaku_map[text[i]])
				++ i
			}
		} else {
			var i = 0
			while(i < l) {
				val start = i
				while(i < l && ! isBadChar2(text[i])) ++ i
				if(i > start) {
					sb.append(text.substring(start, i))
					if(i >= l) break
				}
				val c = text[i]
				if(c.toInt() == 0xa) {
					sb.append("<br/>")
				} else {
					sb.append(taisaku_map[c])
				}
				++ i
			}
		}
		return sb.toString()
	}
	
	////////////////////////////
	
	private val mimeTypeExMap : HashMap<String, String> by lazy {
		val map = HashMap<String, String>()
		map["BDM"] = "application/vnd.syncml.dm+wbxml"
		map["DAT"] = ""
		map["TID"] = ""
		map["js"] = "text/javascript"
		map["sh"] = "application/x-sh"
		map["lua"] = "text/x-lua"
		map
	}
	
	@Suppress("unused")
	fun getMimeType(log : LogCategory?, src : String) : String {
		var ext = MimeTypeMap.getFileExtensionFromUrl(src)
		if(ext != null && ext.isNotEmpty()) {
			ext = ext.toLowerCase(Locale.US)
			
			//
			var mime_type : String? = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
			if(mime_type?.isNotEmpty() == true) return mime_type
			
			//
			mime_type = mimeTypeExMap[ext]
			if(mime_type?.isNotEmpty() == true) return mime_type
			
			// 戻り値が空文字列の場合とnullの場合があり、空文字列の場合は既知なのでログ出力しない
			
			if(mime_type == null && log != null) {
				log.w("getMimeType(): unknown file extension '%s'", ext)
			}
		}
		return MIME_TYPE_APPLICATION_OCTET_STREAM
	}
}

////////////////////////////////////////////////////////////////////
// Comparable

fun <T : Comparable<T>> clipRange(min : T, max : T, src : T) =
	if(src < min) min else if(src > max) max else src


////////////////////////////////////////////////////////////////////
// ByteArray

// 16進ダンプ
private fun ByteArray.encodeHex() : String {
	val sb = StringBuilder()
	for(b in this) {
		sb.appendHex2(b.toInt())
	}
	return sb.toString()
}
fun ByteArray.encodeBase64Url() : String {
	return Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP )
}

fun ByteArray.digestSHA256() : ByteArray {
	val digest = MessageDigest.getInstance("SHA-256")
	digest.reset()
	return digest.digest(this)
}

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

////////////////////////////////////////////////////////////////////
// CharSequence

fun CharSequence.replaceFirst(pattern : Pattern, replacement : String) : String {
	return pattern.matcher(this).replaceFirst(replacement)
	// replaceFirstの戻り値がplatform type なので expression body 形式にすると警告がでる
}

fun CharSequence.replaceAll(pattern : Pattern, replacement : String) : String {
	return pattern.matcher(this).replaceAll(replacement)
	// replaceAllの戻り値がplatform type なので expression body 形式にすると警告がでる
}

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

fun CharSequence.codePointBefore(index:Int) :Int{
	if( index >0 ) {
		val c2 = this[index - 1]
		if(Character.isLowSurrogate(c2) && index > 1) {
			val c1 = this[index - 2]
			if(Character.isHighSurrogate(c1)) return Character.toCodePoint(c1, c2)
		}
		return c2.toInt()
	}else {
		return - 1
	}
}

////////////////////////////////////////////////////////////////////
// string

val charsetUTF8 = Charsets.UTF_8

// 文字列とバイト列の変換
fun String.encodeUTF8() = this.toByteArray(charsetUTF8)

fun ByteArray.decodeUTF8() = this.toString(charsetUTF8)

fun StringBuilder.appendHex2(value : Int) : StringBuilder {
	this.append(Utils.hex[(value shr 4) and 15])
	this.append(Utils.hex[value and 15])
	return this
}

fun String?.optInt() : Int? {
	return try {
		this?.toInt(10)
	} catch(ignored : Throwable) {
		null
	}
}

//fun String.ellipsize(max : Int) = if(this.length > max) this.substring(0, max - 1) + "…" else this
//
//fun String.toCamelCase() : String {
//	val sb = StringBuilder()
//	for(s in this.split("_".toRegex())) {
//		if(s.isEmpty()) continue
//		sb.append(Character.toUpperCase(s[0]))
//		sb.append(s.substring(1, s.length).toLowerCase())
//	}
//	return sb.toString()
//}

fun String.sanitizeBDI() : String {
	
	// 文字列をスキャンしてBDI制御文字をスタックに入れていく
	var stack : LinkedList<Char>? = null
	for(c in this) {
		val closer = Utils.sanitizeBdiMap[c]
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

// Uri.encode(s:Nullable) だと nullチェックができないので、簡単なラッパーを用意する
fun String.encodePercent(allow : String? = null) : String = Uri.encode(this, allow)

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


////////////////////////////////////////////////////////////////////
// long

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

////////////////////////////////////////////////////////////////////
// JSON

fun String.toJsonObject() = JSONObject(this)
fun String.toJsonArray() = JSONArray(this)

fun JSONObject.parseString(key : String) : String? {
	val o = this.opt(key)
	return if(o == null || o == JSONObject.NULL) null else o.toString()
}

fun JSONArray.parseString(key : Int) : String? {
	val o = this.opt(key)
	return if(o == null || o == JSONObject.NULL) null else o.toString()
}

fun notEmptyOrThrow(name : String, value : String?) =
	if(value?.isNotEmpty() == true) value else throw RuntimeException("$name is empty")

fun JSONObject.notEmptyOrThrow(name : String) = notEmptyOrThrow(name, this.parseString(name))

fun JSONArray.toStringArrayList() : ArrayList<String> {
	val size = this.length()
	val dst_list = ArrayList<String>(size)
	for(i in 0 until size) {
		val sv = this.parseString(i) ?: continue
		dst_list.add(sv)
	}
	return dst_list
}

// 文字列データをLong精度で取得できる代替品
// (JsonObject.optLong はLong精度が出ない)
fun JSONObject.parseLong(key : String) : Long? {
	val o = this.opt(key)
	return when(o) {
		is Long -> return o
		is Number -> return o.toLong()
		
		is String -> {
			if(o.indexOf('.') == - 1 && o.indexOf(',') == - 1) {
				try {
					return o.toLong(10)
				} catch(ignored : NumberFormatException) {
				}
			}
			try {
				o.toDouble().toLong()
			} catch(ignored : NumberFormatException) {
				null
			}
		}
		
		else -> null // may null or JSONObject.NULL or object,array,boolean
	}
}

fun JSONObject.parseInt(key : String) : Int? {
	val o = this.opt(key)
	return when(o) {
		is Int -> return o

		is Number -> return try{
			o.toInt()
		}catch(ignored:NumberFormatException){
			null
		}

		is String -> {
			if(o.indexOf('.') == - 1 && o.indexOf(',') == - 1) {
				try {
					return o.toInt(10)
				} catch(ignored : NumberFormatException) {
				}
			}
			try {
				o.toDouble().toInt()
			} catch(ignored : NumberFormatException) {
				null
			}
		}
		
		else -> null // may null or JSONObject.NULL or object,array,boolean
	}
}

////////////////////////////////////////////////////////////////////
// Bundle

fun Bundle.parseString(key : String) : String? {
	return try {
		this.getString(key)
	} catch(ignored : Throwable) {
		null
	}
}

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
// threading

val isMainThread : Boolean get() = Looper.getMainLooper().thread === Thread.currentThread()

fun runOnMainLooper(proc : () -> Unit) {
	val looper = Looper.getMainLooper()
	if(looper.thread === Thread.currentThread()) {
		proc()
	} else {
		Handler(looper).post { proc() }
	}
}
fun runOnMainLooperDelayed(delayMs:Long, proc : () -> Unit) {
	val looper = Looper.getMainLooper()
	Handler(looper).postDelayed({ proc() },delayMs)
}

////////////////////////////////////////////////////////////////////
// View

fun View?.scan(callback : (view : View) -> Unit) {
	this ?: return
	callback(this)
	if(this is ViewGroup) {
		for(i in 0 until this.childCount) {
			this.getChildAt(i)?.scan(callback)
		}
	}
}

val View?.activity : Activity?
	get() {
		var context = this?.context
		while(context is ContextWrapper) {
			if(context is Activity) return context
			context = context.baseContext
		}
		return null
	}

fun View.hideKeyboard() {
	try {
		val imm = this.context?.getSystemService(Context.INPUT_METHOD_SERVICE)
		if(imm is InputMethodManager) {
			imm.hideSoftInputFromWindow(this.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
		} else {
			Utils.log.e("hideKeyboard: can't get InputMethodManager")
		}
	} catch(ex : Throwable) {
		Utils.log.trace(ex)
	}
}

fun View.showKeyboard() {
	try {
		val imm = this.context?.getSystemService(Context.INPUT_METHOD_SERVICE)
		if(imm is InputMethodManager) {
			imm.showSoftInput(this, InputMethodManager.HIDE_NOT_ALWAYS)
		} else {
			Utils.log.e("showKeyboard: can't get InputMethodManager")
		}
	} catch(ex : Throwable) {
		Utils.log.trace(ex)
	}
}

////////////////////////////////////////////////////////////////////
// context

fun Context.loadRawResource(res_id : Int) : ByteArray? {
	try {
		this.resources.openRawResource(res_id).use { inStream ->
			val bao = ByteArrayOutputStream()
			IOUtils.copy(inStream, bao)
			return bao.toByteArray()
		}
	} catch(ex : Throwable) {
		Utils.log.trace(ex)
	}
	return null
}

////////////////////////////////////////////////////////////////////
// file

@Suppress("unused")
@Throws(IOException::class)
fun File.loadByteArray() : ByteArray {
	val size = this.length().toInt()
	val data = ByteArray(size)
	FileInputStream(this).use { inStream ->
		val nRead = 0
		while(nRead < size) {
			val delta = inStream.read(data, nRead, size - nRead)
			if(delta <= 0) break
		}
		return data
	}
}

////////////////////////////////////////////////////////////////////
// toast

fun showToast(context : Context, bLong : Boolean, fmt : String?, vararg args : Any) {
	Utils.showToastImpl(
		context,
		bLong,
		if(fmt == null) "(null)" else if(args.isEmpty()) fmt else String.format(fmt, *args)
	)
}

fun showToast(context : Context, ex : Throwable, fmt : String?, vararg args : Any) {
	Utils.showToastImpl(context, true, ex.withCaption(fmt, *args))
}

fun showToast(context : Context, bLong : Boolean, string_id : Int, vararg args : Any) {
	Utils.showToastImpl(context, bLong, context.getString(string_id, *args))
}

fun showToast(context : Context, ex : Throwable, string_id : Int, vararg args : Any) {
	Utils.showToastImpl(context, true, ex.withCaption(context.resources, string_id, *args))
}

