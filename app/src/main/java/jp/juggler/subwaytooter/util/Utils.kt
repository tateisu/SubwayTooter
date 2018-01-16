package jp.juggler.subwaytooter.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedList
import java.util.Locale

import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.storage.StorageManager
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.util.Base64
import android.util.SparseBooleanArray

import android.database.Cursor
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.MimeTypeMap
import android.widget.Toast

import org.apache.commons.io.IOUtils
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import org.w3c.dom.NamedNodeMap

import java.text.DecimalFormat

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

import it.sephiroth.android.library.exif2.ExifInterface

@Suppress("unused")
object Utils {
	
	private val log = LogCategory("Utils")
	
	private val bytes_format = DecimalFormat("#,###")
	
	private val hex = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
	
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
	//		log.e("missing resid for %s",name);
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
	
	private val MIME_TYPE_APPLICATION_OCTET_STREAM = "application/octet-stream"
	
	val isMainThread : Boolean
		get() = Looper.getMainLooper().thread === Thread.currentThread()
	
	private var refToast : WeakReference<Toast>? = null
	
	private val PATH_TREE = "tree"
	private val PATH_DOCUMENT = "document"
	
	private val ALM = 0x061c.toChar() // Arabic letter mark (ALM)
	private val LRM = 0x200E.toChar() //	Left-to-right mark (LRM)
	private val RLM = 0x200F.toChar() //	Right-to-left mark (RLM)
	private val LRE = 0x202A.toChar() // Left-to-right embedding (LRE)
	private val RLE = 0x202B.toChar() // Right-to-left embedding (RLE)
	private val PDF = 0x202C.toChar() // Pop directional formatting (PDF)
	private val LRO = 0x202D.toChar() // Left-to-right override (LRO)
	private val RLO = 0x202E.toChar() // Right-to-left override (RLO)
	
	private val CHARS_MUST_PDF = LRE.toString() + RLE + LRO + RLO
	
	private val LRI = 0x2066.toChar() // Left-to-right isolate (LRI)
	private val RLI = 0x2067.toChar() // Right-to-left isolate (RLI)
	private val FSI = 0x2068.toChar() // First strong isolate (FSI)
	private val PDI = 0x2069.toChar() // Pop directional isolate (PDI)
	
	private val CHARS_MUST_PDI = LRI.toString() + RLI + FSI
	
	private fun showToastImpl(context : Context, bLong : Boolean, message : String) {
		runOnMainThread {
			
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
				val t = Toast.makeText(
					context, message, if(bLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
				)
				t.show()
				refToast = WeakReference(t)
			} catch(ex : Throwable) {
				log.trace(ex)
				// android.view.WindowManager$BadTokenException:
				// at android.view.ViewRootImpl.setView (ViewRootImpl.java:679)
				// at android.view.WindowManagerGlobal.addView (WindowManagerGlobal.java:342)
				// at android.view.WindowManagerImpl.addView (WindowManagerImpl.java:94)
				// at android.widget.Toast$TN.handleShow (Toast.java:435)
				// at android.widget.Toast$TN$2.handleMessage (Toast.java:345)
			}
		}
	}
	
	fun showToast(context : Context, bLong : Boolean, fmt : String?, vararg args : Any) {
		showToastImpl(context, bLong, if(fmt == null) "(null)" else if(args.isEmpty()) fmt else String.format(fmt, *args))
	}
	
	fun showToast(context : Context, ex : Throwable, fmt : String?, vararg args : Any) {
		showToastImpl(context, true, formatError(ex, fmt, *args))
	}
	
	fun showToast(context : Context, bLong : Boolean, string_id : Int, vararg args : Any) {
		showToastImpl(context, bLong, context.getString(string_id, *args))
	}
	
	fun showToast(context : Context, ex : Throwable, string_id : Int, vararg args : Any) {
		showToastImpl(context, true, formatError(ex, context.resources, string_id, *args))
	}
	
	@SuppressLint("DefaultLocale")
	fun formatTimeDuration(timeArg : Long) : String {
		var t = timeArg
		val sb = StringBuilder()
		var n : Long
		// day
		n = t / 86400000L
		if(n > 0) {
			sb.append(String.format(Locale.JAPAN, "%dd", n))
			t -= n * 86400000L
		}
		// h
		n = t / 3600000L
		if(n > 0 || sb.isNotEmpty()) {
			sb.append(String.format(Locale.JAPAN, "%dh", n))
			t -= n * 3600000L
		}
		// m
		n = t / 60000L
		if(n > 0 || sb.isNotEmpty()) {
			sb.append(String.format(Locale.JAPAN, "%dm", n))
			t -= n * 60000L
		}
		// s
		val f = t / 1000f
		sb.append(String.format(Locale.JAPAN, "%.03fs", f))
		
		return sb.toString()
	}
	
	fun formatBytes(t : Long) : String {
		return bytes_format.format(t)
		
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
		//		// remain
		//		if( sb.length() > 0 ){
		//			sb.append( String.format( Locale.JAPAN, "%03d", t ) );
		//		}else if( n > 0 ){
		//			sb.append( String.format( Locale.JAPAN, "%d", t ) );
		//		}
		//
		//		return sb.toString();
	}
	
	//	public static PendingIntent createAlarmPendingIntent( Context context ){
	//		Intent i = new Intent( context.getApplicationContext(), Receiver1.class );
	//		i.setAction( Receiver1.ACTION_ALARM );
	//		return PendingIntent.getBroadcast( context.getApplicationContext(), 0, i, 0 );
	//	}
	//
	
	private val charsetUtf8 = Charsets.UTF_8
	
	// 文字列とバイト列の変換
	fun encodeUTF8(str : String) : ByteArray {
		return try {
			str.toByteArray(charsetUtf8)
		} catch(ex : Throwable) {
			ByteArray(0) // 入力がnullの場合のみ発生
		}
		
	}
	
	// 文字列とバイト列の変換
	fun decodeUTF8(data : ByteArray) : String {
		return try {
			String(data, charsetUtf8)
		} catch(ex : Throwable) {
			"" // 入力がnullの場合のみ発生
		}
		
	}
	
	// 文字列と整数の変換
	fun parse_int(v : String, defval : Int) : Int {
		return try {
			Integer.parseInt(v, 10)
		} catch(ex : Throwable) {
			defval
		}
		
	}
	
	fun optStringX(src : JSONObject?, key : String) : String? {
		src ?: return null
		return if(src.isNull(key)) null else src.optString(key)
	}
	
	fun optStringX(src : JSONArray?, key : Int) : String? {
		src ?: return null
		return if(src.isNull(key)) null else src.optString(key)
	}
	
	// 文字列データをLong精度で取得できる
	fun optLongX(src : JSONObject?, key : String, defaultValue : Long = 0L) : Long {
		src ?: return defaultValue
		val o = src.opt(key)
		if(o is String) {
			if(o.indexOf('.') == - 1 && o.indexOf(',') == - 1) {
				try {
					return o.toLong(10)
				} catch(ignored : NumberFormatException) {
				}
				
			}
		}
		return src.optLong(key, defaultValue)
	}
	
	fun parseStringArray(array : JSONArray?) : ArrayList<String> {
		val dst_list = ArrayList<String>()
		if(array != null) {
			var i = 0
			val ie = array.length()
			while(i < ie) {
				val sv = Utils.optStringX(array, i) ?: continue
				dst_list.add(sv)
				++ i
			}
		}
		return dst_list
	}
	
	fun dumpCodePoints(str : String) : CharSequence {
		val sb = StringBuilder()
		var i = 0
		val ie = str.length
		var cp : Int
		while(i < ie) {
			cp = str.codePointAt(i)
			sb.append(String.format("0x%x,", cp))
			i += Character.charCount(cp)
		}
		return sb
	}
	
	private fun addHex(sb : StringBuilder, value : Int) {
		sb.append(hex[(value shr 4) and 15])
		sb.append(hex[value and 15])
	}
	
	fun hex2int(c : Char) : Int {
		when(c) {
			'0' -> return 0
			'1' -> return 1
			'2' -> return 2
			'3' -> return 3
			'4' -> return 4
			'5' -> return 5
			'6' -> return 6
			'7' -> return 7
			'8' -> return 8
			'9' -> return 9
			'a' -> return 0xa
			'b' -> return 0xb
			'c' -> return 0xc
			'd' -> return 0xd
			'e' -> return 0xe
			'f' -> return 0xf
			'A' -> return 0xa
			'B' -> return 0xb
			'C' -> return 0xc
			'D' -> return 0xd
			'E' -> return 0xe
			'F' -> return 0xf
			else -> return 0
		}
	}
	
	// 16進ダンプ
	private fun encodeHex(data : ByteArray?) : String? {
		if(data == null) return null
		val sb = StringBuilder()
		for(b in data) {
			addHex(sb, b.toInt())
		}
		return sb.toString()
	}
	
	private fun encodeSHA256(src : ByteArray) : ByteArray? {
		return try {
			val digest = MessageDigest.getInstance("SHA-256")
			digest.reset()
			digest.digest(src)
		} catch(e1 : NoSuchAlgorithmException) {
			null
		}
		
	}
	
	private fun encodeBase64Safe(src : ByteArray?) : String? {
		return try {
			Base64.encodeToString(src, Base64.URL_SAFE)
		} catch(ex : Throwable) {
			null
		}
	}
	
	//	fun url2name(url : String?) : String? {
	//		return if(url == null) null else encodeBase64Safe(encodeSHA256(encodeUTF8(url)))
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
	
	// MD5ハッシュの作成
	//	fun digestMD5(s : String?) : String? {
	//		if(s == null) return null
	//		try {
	//			val md = MessageDigest.getInstance("MD5")
	//			md.reset()
	//			return encodeHex(md.digest(s.toByteArray(charset("UTF-8"))))
	//		} catch(ex : Throwable) {
	//			log.trace(ex)
	//		}
	//
	//		return null
	//	}
	
	fun digestSHA256(src : String?) : String? {
		if(src == null) return null
		try {
			val md = MessageDigest.getInstance("SHA-256")
			md.reset()
			return encodeHex(md.digest(src.toByteArray(charset("UTF-8"))))
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
		return null
	}
	
	private fun _taisaku_add_string(z : String, h : String) {
		var i = 0
		val e = z.length
		while(i < e) {
			val zc = z[i]
			taisaku_map.put(zc, "" + Character.toString(h[i]))
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
			"　！”＃＄％＆’（）＊＋，－．／０１２３４５６７８９：；＜＝＞？＠ＡＢＣＤＥＦＧＨＩＪＫＬＭＮＯＰＱＲＳＴＵＶＷＸＹＺ［］＾＿｀ａｂｃｄｅｆｇｈｉｊｋｌｍｎｏｐｑｒｓｔｕｖｗｘｙｚ｛｜｝", " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_`abcdefghijklmnopqrstuvwxyz{|}"
		)
		
	}
	
	private fun isBadChar2(c : Char) : Boolean {
		return c.toInt() == 0xa || taisaku_map2.get(c.toInt())
	}
	
	//! フォントによって全角文字が化けるので、その対策
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
	
	fun toLower(from : String?) : String? {
		return from?.toLowerCase(Locale.US)
	}
	
	fun toUpper(from : String?) : String? {
		return from?.toUpperCase(Locale.US)
	}
	
	fun getString(b : Bundle, key : String, defval : String) : String {
		try {
			val v = b.getString(key)
			if(v != null) return v
		} catch(ignored : Throwable) {
		}
		
		return defval
	}
	
	@Throws(IOException::class)
	fun loadFile(file : File) : ByteArray {
		val size = file.length().toInt()
		val data = ByteArray(size)
		val `in` = FileInputStream(file)
		try {
			val nRead = 0
			while(nRead < size) {
				val delta = `in`.read(data, nRead, size - nRead)
				if(delta <= 0) break
			}
			return data
		} finally {
			try {
				`in`.close()
			} catch(ignored : Throwable) {
			}
			
		}
	}
	
	fun hideKeyboard(context : Context, v : View) {
		try {
			val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
			if(imm is InputMethodManager) {
				imm.hideSoftInputFromWindow(v.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
			} else {
				log.e("can't get InputMethodManager")
			}
		} catch(ex : Throwable) {
			log.trace(ex)
		}
	}
	
	fun showKeyboard(context : Context, v : View) {
		try {
			val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
			if(imm is InputMethodManager) {
				imm.showSoftInput(v, InputMethodManager.HIDE_NOT_ALWAYS)
			} else {
				log.e("can't get InputMethodManager")
			}
		} catch(ex : Throwable) {
			log.trace(ex)
		}
	}
	
	fun ellipsize(t : String, max : Int) : String {
		return if(t.length > max) t.substring(0, max - 1) + "…" else t
	}
	
	private val mimeTypeExMap = HashMap<String, String>()
	
	private fun findMimeTypeEx(ext : String) : String? {
		synchronized(mimeTypeExMap) {
			if(mimeTypeExMap.isEmpty()) {
				mimeTypeExMap.put("BDM", "application/vnd.syncml.dm+wbxml")
				mimeTypeExMap.put("DAT", "")
				mimeTypeExMap.put("TID", "")
				mimeTypeExMap.put("js", "text/javascript")
				mimeTypeExMap.put("sh", "application/x-sh")
				mimeTypeExMap.put("lua", "text/x-lua")
			}
		}
		return mimeTypeExMap[ext]
	}
	
	fun getMimeType(log : LogCategory?, src : String) : String {
		var ext = MimeTypeMap.getFileExtensionFromUrl(src)
		if(ext != null && ext.isNotEmpty()) {
			ext = ext.toLowerCase(Locale.US)
			
			//
			var mime_type : String? = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
			if(mime_type?.isNotEmpty() == true) return mime_type
			
			//
			mime_type = findMimeTypeEx(ext)
			if(mime_type?.isNotEmpty() == true) return mime_type
			
			// 戻り値が空文字列の場合とnullの場合があり、空文字列の場合は既知なのでログ出力しない
			
			if(mime_type == null && log != null) {
				log.w("getMimeType(): unknown file extension '%s'", ext)
			}
		}
		return MIME_TYPE_APPLICATION_OCTET_STREAM
	}
	
	fun formatSpannable1(context : Context, string_id : Int, display_name : CharSequence?) : Spannable {
		
		val s = context.getString(string_id)
		val end = s.length
		val pos = s.indexOf("%1\$s")
		if(pos == - 1) return SpannableString(s)
		
		val sb = SpannableStringBuilder()
		if(pos > 0) sb.append(s.substring(0, pos))
		if(display_name != null) sb.append(display_name)
		if(pos + 4 < end) sb.append(s.substring(pos + 4, end))
		return sb
	}
	
	fun createResizedBitmap(log : LogCategory, context : Context, uri : Uri, skipIfNoNeedToResizeAndRotate : Boolean, resizeToArg : Int) : Bitmap? {
		var resize_to = resizeToArg
		try {
			
			// EXIF回転情報の取得
			val orientation : Int? = context.contentResolver.openInputStream(uri)?.use { inStream ->
				val exif = ExifInterface()
				exif.readExif(inStream, ExifInterface.Options.OPTION_IFD_0 or ExifInterface.Options.OPTION_IFD_1 or ExifInterface.Options.OPTION_IFD_EXIF)
				exif.getTagIntValue(ExifInterface.TAG_ORIENTATION)
			}
			
			// 画像のサイズを調べる
			val options = BitmapFactory.Options()
			options.inJustDecodeBounds = true
			options.inScaled = false
			options.outWidth = 0
			options.outHeight = 0
			context.contentResolver.openInputStream(uri)?.use { inStream ->
				BitmapFactory.decodeStream(inStream, null, options)
			}
			var src_width = options.outWidth
			var src_height = options.outHeight
			if(src_width <= 0 || src_height <= 0) {
				Utils.showToast(context, false, "could not get image bounds.")
				return null
			}
			
			// 長辺
			val size = if(src_width > src_height) src_width else src_height
			
			// リサイズも回転も必要がない場合
			if(skipIfNoNeedToResizeAndRotate
				&& (orientation == null || orientation == 1)
				&& (resize_to <= 0 || size <= resize_to)) {
				log.d("createOpener: no need to resize & rotate")
				return null
			}
			
			
			if(size > resize_to) {
				// 縮小が必要
			} else {
				// 縮小は不要
				resize_to = size
			}
			
			// inSampleSizeを計算
			var bits = 0
			var x = size
			while(x > resize_to * 2) {
				++ bits
				x = x shr 1
			}
			options.inJustDecodeBounds = false
			options.inSampleSize = 1 shl bits
			
			val sourceBitmap : Bitmap? = context.contentResolver.openInputStream(uri)?.use { inStream ->
				BitmapFactory.decodeStream(inStream, null, options)
			}
			
			if(sourceBitmap == null) {
				Utils.showToast(context, false, "could not decode image.")
				return null
			}
			try {
				src_width = options.outWidth
				src_height = options.outHeight
				val scale : Float
				var dst_width : Int
				var dst_height : Int
				if(src_width >= src_height) {
					scale = resize_to / src_width.toFloat()
					dst_width = resize_to
					dst_height = (0.5f + src_height / src_width.toFloat() * resize_to).toInt()
					if(dst_height < 1) dst_height = 1
				} else {
					scale = resize_to / src_height.toFloat()
					dst_height = resize_to
					dst_width = (0.5f + src_width / src_height.toFloat() * resize_to).toInt()
					if(dst_width < 1) dst_width = 1
				}
				
				val matrix = Matrix()
				matrix.reset()
				
				// 画像の中心が原点に来るようにして
				matrix.postTranslate(src_width * - 0.5f, src_height * - 0.5f)
				// スケーリング
				matrix.postScale(scale, scale)
				// 回転情報があれば回転
				if(orientation != null) {
					val tmp : Int
					when(orientation) {
						
						2 -> matrix.postScale(1f, - 1f)  // 上下反転
						3 -> matrix.postRotate(180f) // 180度回転
						4 -> matrix.postScale(- 1f, 1f) // 左右反転
						
						5 -> {
							tmp = dst_width
							
							dst_width = dst_height
							dst_height = tmp
							matrix.postScale(1f, - 1f)
							matrix.postRotate(- 90f)
						}
						
						6 -> {
							tmp = dst_width
							
							dst_width = dst_height
							dst_height = tmp
							matrix.postRotate(90f)
						}
						
						7 -> {
							tmp = dst_width
							
							dst_width = dst_height
							dst_height = tmp
							matrix.postScale(1f, - 1f)
							matrix.postRotate(90f)
						}
						
						8 -> {
							tmp = dst_width
							
							dst_width = dst_height
							dst_height = tmp
							matrix.postRotate(- 90f)
						}
						
						else -> {
						}
					}
				}
				// 表示領域に埋まるように平行移動
				matrix.postTranslate(dst_width * 0.5f, dst_height * 0.5f)
				
				// 出力用Bitmap作成
				var dst : Bitmap? = Bitmap.createBitmap(dst_width, dst_height, Bitmap.Config.ARGB_8888)
				try {
					return if(dst == null) {
						Utils.showToast(context, false, "bitmap creation failed.")
						null
					} else {
						val canvas = Canvas(dst)
						val paint = Paint()
						paint.isFilterBitmap = true
						canvas.drawBitmap(sourceBitmap, matrix, paint)
						log.d("createResizedBitmap: resized to %sx%s", dst_width, dst_height)
						val tmp = dst
						dst = null
						tmp
					}
				} finally {
					dst?.recycle()
				}
			} finally {
				sourceBitmap.recycle()
			}
		} catch(ex : SecurityException) {
			log.e(ex, "maybe we need pick up image again.")
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		return null
	}
	
	fun scanView(view : View?, callback : (view : View) -> Unit) {
		view ?: return
		callback(view)
		if(view is ViewGroup) {
			for(i in 0 until view.childCount) {
				scanView(view.getChildAt(i), callback)
			}
		}
	}
	
	internal class FileInfo(any_uri : String?) {
		
		var uri : Uri? = null
		private var mime_type : String? = null
		
		init {
			if(any_uri != null) {
				uri = if(any_uri.startsWith("/")) {
					Uri.fromFile(File(any_uri))
				} else {
					Uri.parse(any_uri)
				}
				val ext = MimeTypeMap.getFileExtensionFromUrl(any_uri)
				if(ext != null) {
					mime_type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase())
				}
			}
		}
	}
	
	private fun getSecondaryStorageVolumesMap(context : Context) : Map<String, String> {
		val result = HashMap<String, String>()
		try {
			val sm = context.applicationContext.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
			if(sm == null) {
				log.e("can't get StorageManager")
			} else {
				
				// SDカードスロットのある7.0端末が手元にないから検証できない
				//			if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ){
				//				for(StorageVolume volume : sm.getStorageVolumes() ){
				//					// String path = volume.getPath();
				//					String state = volume.getState();
				//
				//				}
				//			}
				
				val getVolumeList = sm.javaClass.getMethod("getVolumeList")
				val volumes = getVolumeList.invoke(sm)
				log.d("volumes type=%s", volumes.javaClass)
				
				if(volumes is ArrayList<*>) {
					//
					for(volume in volumes) {
						val volume_clazz = volume.javaClass
						
						val path = volume_clazz.getMethod("getPath").invoke(volume) as? String
						val state = volume_clazz.getMethod("getState").invoke(volume) as? String
						if(path != null && state == "mounted") {
							//
							val isPrimary = volume_clazz.getMethod("isPrimary").invoke(volume) as? Boolean
							if(isPrimary == true) result.put("primary", path)
							//
							val uuid = volume_clazz.getMethod("getUuid").invoke(volume) as? String
							if(uuid != null) result.put(uuid, path)
						}
					}
				}
			}
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
		return result
	}
	
	fun toCamelCase(src : String) : String {
		val sb = StringBuilder()
		for(s in src.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
			if(s.isEmpty()) continue
			sb.append(Character.toUpperCase(s[0]))
			sb.append(s.substring(1, s.length).toLowerCase())
		}
		return sb.toString()
	}
	
	private val xml_builder : DocumentBuilder by lazy {
		DocumentBuilderFactory.newInstance().newDocumentBuilder()
	}
	
	fun parseXml(src : ByteArray) : Element? {
		return try {
			xml_builder.parse(ByteArrayInputStream(src)).documentElement
		} catch(ex : Throwable) {
			log.trace(ex)
			null
		}
	}
	
	fun getAttribute(attr_map : NamedNodeMap, name : String, defval : String?) : String? {
		val node = attr_map.getNamedItem(name)
		return if(node != null) node.nodeValue else defval
	}
	
	fun formatError(ex : Throwable, fmt : String?, vararg args : Any) : String {
		return when {
			fmt == null -> "(null)"
			args.isEmpty() -> fmt
			else -> String.format(fmt, *args)
		} + " : ${ex.javaClass.simpleName} ${ex.message}"
	}
	
	fun formatError(ex : Throwable, resources : Resources, string_id : Int, vararg args : Any) : String {
		return resources.getString(string_id, *args) + String.format(" :%s %s", ex.javaClass.simpleName, ex.message)
	}
	
	fun runOnMainThread(proc : () -> Unit) {
		if(Looper.getMainLooper().thread === Thread.currentThread()) {
			proc()
		} else {
			Handler(Looper.getMainLooper()).post { proc() }
		}
	}
	
	private fun isExternalStorageDocument(uri : Uri) : Boolean {
		return "com.android.externalstorage.documents" == uri.authority
	}
	
	private fun getDocumentId(documentUri : Uri) : String {
		val paths = documentUri.pathSegments
		if(paths.size >= 2 && PATH_DOCUMENT == paths[0]) {
			// document
			return paths[1]
		}
		if(paths.size >= 4 && PATH_TREE == paths[0]
			&& PATH_DOCUMENT == paths[2]) {
			// document in tree
			return paths[3]
		}
		if(paths.size >= 2 && PATH_TREE == paths[0]) {
			// tree
			return paths[1]
		}
		throw IllegalArgumentException("Invalid URI: " + documentUri)
	}
	
	fun getFile(context : Context, path : String) : File? {
		try {
			if(path.startsWith("/")) return File(path)
			val uri = Uri.parse(path)
			if("file" == uri.scheme) return File(uri.path)
			
			// if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT )
			run {
				if(isExternalStorageDocument(uri)) {
					try {
						val docId = getDocumentId(uri)
						val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
						if(split.size >= 2) {
							val uuid = split[0]
							if("primary".equals(uuid, ignoreCase = true)) {
								return File(Environment.getExternalStorageDirectory().toString() + "/" + split[1])
							} else {
								val volume_map = Utils.getSecondaryStorageVolumesMap(context)
								val volume_path = volume_map[uuid]
								if(volume_path != null) {
									return File(volume_path + "/" + split[1])
								}
							}
						}
					} catch(ex : Throwable) {
						log.trace(ex)
					}
					
				}
			}
			// MediaStore Uri
			context.contentResolver.query(uri, null, null, null, null).use { cursor ->
				if(cursor.moveToFirst()) {
					val col_count = cursor.columnCount
					for(i in 0 until col_count) {
						val type = cursor.getType(i)
						if(type != Cursor.FIELD_TYPE_STRING) continue
						val name = cursor.getColumnName(i)
						val value = if(cursor.isNull(i)) null else cursor.getString(i)
						if(value != null && value.isNotEmpty() && "filePath" == name) return File(value)
					}
				}
			}
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
		return null
	}
	
	fun getActivityFromView(view : View?) : Activity? {
		if(view != null) {
			var context = view.context
			while(context is ContextWrapper) {
				if(context is Activity) return context
				context = context.baseContext
			}
		}
		return null
	}
	
	fun sanitizeBDI(src : String) : String {
		
		var stack : LinkedList<Char>? = null
		
		var i = 0
		val ie = src.length
		while(i < ie) {
			val c = src[i]
			
			if(- 1 != CHARS_MUST_PDF.indexOf(c)) {
				if(stack == null) stack = LinkedList()
				stack.add(PDF)
				
			} else if(- 1 != CHARS_MUST_PDI.indexOf(c)) {
				if(stack == null) stack = LinkedList()
				stack.add(PDI)
			} else if(stack != null && ! stack.isEmpty() && stack.last == c) {
				stack.removeLast()
			}
			++ i
		}
		
		if(stack == null || stack.isEmpty()) return src
		
		val sb = StringBuilder()
		sb.append(src)
		while(! stack.isEmpty()) {
			sb.append(stack.removeLast() as Char)
		}
		return sb.toString()
	}
	
	fun loadRawResource(context : Context, res_id : Int) : ByteArray? {
		try {
			context.resources.openRawResource(res_id).use { inStream ->
				val bao = ByteArrayOutputStream()
				IOUtils.copy(inStream, bao)
				return bao.toByteArray()
			}
			
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
		return null
	}
	
	fun getExtraObject(data : Intent?, key : String) : Any? {
		return data?.extras?.get(key)
	}
	
}
