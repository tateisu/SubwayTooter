package jp.juggler.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PackageInfoFlags
import android.content.pm.PackageManager.ResolveInfoFlags
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.annotation.AnimRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellableContinuation
import kotlin.coroutines.CoroutineContext

/**
 * API 33 で Bundle.get() が deprecatedになる。
 * type safeにするべきだが、過去の使い方にもよるかな…
 */
private fun Bundle.getRaw(key: String) =
    @Suppress("DEPRECATION")
    get(key)

fun Intent.getUriExtra(key: String) =
    extras?.getRaw(key) as? Uri

fun Intent.getStreamUriExtra() =
    if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(Intent.EXTRA_STREAM) as? Uri?
    }

fun Intent.getStreamUriListExtra() =
    if (Build.VERSION.SDK_INT >= 33) {
        getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayListExtra(Intent.EXTRA_STREAM)
    }

fun Intent.getIntentExtra(key: String) =
    if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(key, Intent::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
    }

/**
 * Bundleからキーを指定してint値またはnullを得る
 */
fun Bundle.boolean(key: String) =
    when (val v = getRaw(key)) {
        is Boolean -> v
        else -> null
    }

fun Bundle.string(key: String) =
    when (val v = getRaw(key)) {
        is String -> v
        else -> null
    }

/**
 * Bundleからキーを指定してint値またはnullを得る
 */
fun Bundle.int(key: String) =
    when (val v = getRaw(key)) {
        null -> null
        is Number -> v.toInt()
        is String -> v.toIntOrNull()
        else -> null
    }

/**
 * Bundleからキーを指定してlong値またはnullを得る
 */
fun Bundle.long(key: String) =
    when (val v = getRaw(key)) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull()
        else -> null
    }

/**
 * IntentのExtrasからキーを指定してboolean値またはnullを得る
 */
fun Intent.boolean(key: String) = extras?.boolean(key)

/**
 * IntentのExtrasからキーを指定してint値またはnullを得る
 */
fun Intent.int(key: String) = extras?.int(key)

/**
 * IntentのExtrasからキーを指定してlong値またはnullを得る
 */
fun Intent.long(key: String) = extras?.long(key)

fun Intent.string(key: String) = extras?.string(key)

fun PackageManager.getPackageInfoCompat(
    pakageName: String,
    flags: Int = 0,
): PackageInfo? = if (Build.VERSION.SDK_INT >= 33) {
    getPackageInfo(pakageName, PackageInfoFlags.of(flags.toLong()))
} else {
    getPackageInfo(pakageName, flags)
}

@SuppressLint("QueryPermissionsNeeded")
fun PackageManager.queryIntentActivitiesCompat(
    intent: Intent,
    queryFlag: Int = 0,
): List<ResolveInfo> = if (Build.VERSION.SDK_INT >= 33) {
    queryIntentActivities(intent, ResolveInfoFlags.of(queryFlag.toLong()))
} else {
    queryIntentActivities(intent, queryFlag)
}

fun PackageManager.resolveActivityCompat(
    intent: Intent,
    queryFlag: Int = 0,
): ResolveInfo? = if (Build.VERSION.SDK_INT >= 33) {
    resolveActivity(intent, ResolveInfoFlags.of(queryFlag.toLong()))
} else {
    resolveActivity(intent, queryFlag)
}

fun AppCompatActivity.backPressed(block: () -> Unit) {
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = block()
    })
}

fun ComponentActivity.backPressed(block: () -> Unit) {
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = block()
    })
}

// 型推論できる文脈だと型名を書かずにすむ
inline fun <reified T> systemService(context: Context): T? =
    /* ContextCompat. */ ContextCompat.getSystemService(context, T::class.java)

enum class TransitionOverrideType { Open, Close, }

/**
 *
 * @param overrideType one of OVERRIDE_TRANSITION_OPEN, OVERRIDE_TRANSITION_CLOSE .
 */
fun AppCompatActivity.overrideActivityTransitionCompat(
    overrideType: TransitionOverrideType,
    @AnimRes animEnter: Int,
    @AnimRes animExit: Int,
) {
    when {
        Build.VERSION.SDK_INT >= 34 -> {
            overrideActivityTransition(
                when (overrideType) {
                    TransitionOverrideType.Open ->
                        AppCompatActivity.OVERRIDE_TRANSITION_OPEN

                    TransitionOverrideType.Close ->
                        AppCompatActivity.OVERRIDE_TRANSITION_CLOSE
                },
                animEnter,
                animExit
            )
        }

        else -> {
            @Suppress("DEPRECATION")
            overridePendingTransition(
                animEnter,
                animExit,
            )
        }
    }
}

//
//object Utils {
//
//	val log = LogCategory("Utils")
//
//	/////////////////////////////////////////////
//
//	private val taisaku_map : HashMap<Char, String>
//	private val taisaku_map2 : SparseBooleanArray
//
//	//	public static int getEnumStringId( String residPrefix, String name,Context context ) {
//	//		name = residPrefix + name;
//	//		try{
//	//			int iv = context.getResources().getIdentifier(name,"string",context.getPackageName() );
//	//			if( iv != 0 ) return iv;
//	//		}catch(Throwable ex){
//	//		}
//	//		warning.e("missing resid for %s",name);
//	//		return R.string.Dialog_Cancel;
//	//	}
//
//	//	public static String getConnectionResultErrorMessage( ConnectionResult connectionResult ){
//	//		int code = connectionResult.getErrorCode();
//	//		String msg = connectionResult.getErrorMessage();
//	//		if( msg == null || msg.isEmpty(  ) ){
//	//			switch( code ){
//	//			case ConnectionResult.SUCCESS:
//	//				msg = "SUCCESS";
//	//				break;
//	//			case ConnectionResult.SERVICE_MISSING:
//	//				msg = "SERVICE_MISSING";
//	//				break;
//	//			case ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED:
//	//				msg = "SERVICE_VERSION_UPDATE_REQUIRED";
//	//				break;
//	//			case ConnectionResult.SERVICE_DISABLED:
//	//				msg = "SERVICE_DISABLED";
//	//				break;
//	//			case ConnectionResult.SIGN_IN_REQUIRED:
//	//				msg = "SIGN_IN_REQUIRED";
//	//				break;
//	//			case ConnectionResult.INVALID_ACCOUNT:
//	//				msg = "INVALID_ACCOUNT";
//	//				break;
//	//			case ConnectionResult.RESOLUTION_REQUIRED:
//	//				msg = "RESOLUTION_REQUIRED";
//	//				break;
//	//			case ConnectionResult.NETWORK_ERROR:
//	//				msg = "NETWORK_ERROR";
//	//				break;
//	//			case ConnectionResult.INTERNAL_ERROR:
//	//				msg = "INTERNAL_ERROR";
//	//				break;
//	//			case ConnectionResult.SERVICE_INVALID:
//	//				msg = "SERVICE_INVALID";
//	//				break;
//	//			case ConnectionResult.DEVELOPER_ERROR:
//	//				msg = "DEVELOPER_ERROR";
//	//				break;
//	//			case ConnectionResult.LICENSE_CHECK_FAILED:
//	//				msg = "LICENSE_CHECK_FAILED";
//	//				break;
//	//			case ConnectionResult.CANCELED:
//	//				msg = "CANCELED";
//	//				break;
//	//			case ConnectionResult.TIMEOUT:
//	//				msg = "TIMEOUT";
//	//				break;
//	//			case ConnectionResult.INTERRUPTED:
//	//				msg = "INTERRUPTED";
//	//				break;
//	//			case ConnectionResult.API_UNAVAILABLE:
//	//				msg = "API_UNAVAILABLE";
//	//				break;
//	//			case ConnectionResult.SIGN_IN_FAILED:
//	//				msg = "SIGN_IN_FAILED";
//	//				break;
//	//			case ConnectionResult.SERVICE_UPDATING:
//	//				msg = "SERVICE_UPDATING";
//	//				break;
//	//			case ConnectionResult.SERVICE_MISSING_PERMISSION:
//	//				msg = "SERVICE_MISSING_PERMISSION";
//	//				break;
//	//			case ConnectionResult.RESTRICTED_PROFILE:
//	//				msg = "RESTRICTED_PROFILE";
//	//				break;
//	//
//	//			}
//	//		}
//	//		return msg;
//	//	}
//
//	//	public static String getConnectionSuspendedMessage( int i ){
//	//		switch( i ){
//	//		default:
//	//			return "?";
//	//		case GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST:
//	//			return "NETWORK_LOST";
//	//		case GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED:
//	//			return "SERVICE_DISCONNECTED";
//	//		}
//	//	}
//
//	//	fun url2name(url : String?) : String? {
//	//		return if(url == null) null else encodeBase64Url(encodeSHA256(encodeUTF8(url)))
//	//	}
//
//	//	public static String name2url(String entry) {
//	//		if(entry==null) return null;
//	//		byte[] b = new byte[entry.length()/2];
//	//		for(int i=0,ie=b.length;i<ie;++i){
//	//			b[i]= (byte)((hex2int(entry.charAt(i*2))<<4)| hex2int(entry.charAt(i*2+1)));
//	//		}
//	//		return decodeUTF8(b);
//	//	}
//
//	///////////////////////////////////////////////////
//
//	private fun _taisaku_add_string(z : String, h : String) {
//		var i = 0
//		val e = z.length
//		while(i < e) {
//			val zc = z[i]
//			taisaku_map[zc] = h[i].toString()
//			taisaku_map2.put(zc.toInt(), true)
//			++ i
//		}
//	}
//
//	init {
//		taisaku_map = HashMap()
//		taisaku_map2 = SparseBooleanArray()
//
//		// tilde,wave dash,horizontal ellipsis,minus sign
//		_taisaku_add_string(
//			"\u2073\u301C\u22EF\uFF0D", "\u007e\uFF5E\u2026\u2212"
//		)
//		// zenkaku to hankaku
//		_taisaku_add_string(
//			"　！”＃＄％＆’（）＊＋，－．／０１２３４５６７８９：；＜＝＞？＠ＡＢＣＤＥＦＧＨＩＪＫＬＭＮＯＰＱＲＳＴＵＶＷＸＹＺ［］＾＿｀ａｂｃｄｅｆｇｈｉｊｋｌｍｎｏｐｑｒｓｔｕｖｗｘｙｚ｛｜｝",
//			" !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_`abcdefghijklmnopqrstuvwxyz{|}"
//		)
//
//	}
//
//	private fun isBadChar2(c : Char) : Boolean {
//		return c.toInt() == 0xa || taisaku_map2.get(c.toInt())
//	}
//
//	//! フォントによって全角文字が化けるので、その対策
//	@Suppress("unused")
//	fun font_taisaku(text : String?, lf2br : Boolean) : String? {
//		if(text == null) return null
//		val l = text.length
//		val sb = StringBuilder(l)
//		if(! lf2br) {
//			var i = 0
//			while(i < l) {
//				val start = i
//				while(i < l && ! taisaku_map2.get(text[i].toInt())) ++ i
//				if(i > start) {
//					sb.append(text.substring(start, i))
//					if(i >= l) break
//				}
//				sb.append(taisaku_map[text[i]])
//				++ i
//			}
//		} else {
//			var i = 0
//			while(i < l) {
//				val start = i
//				while(i < l && ! isBadChar2(text[i])) ++ i
//				if(i > start) {
//					sb.append(text.substring(start, i))
//					if(i >= l) break
//				}
//				val c = text[i]
//				if(c.toInt() == 0xa) {
//					sb.append("<br/>")
//				} else {
//					sb.append(taisaku_map[c])
//				}
//				++ i
//			}
//		}
//		return sb.toString()
//	}
//}
//

/**
 * suspendCancellableCoroutine 内部で使う cont.resume() の互換関数。
 */
fun <T> CancellableContinuation<T>.resumeCompat(
    value: T,
    onCancellation: ((cause: Throwable, value: T, context: CoroutineContext) -> Unit)? = null,
) = resume(value, onCancellation)

/**
 * Java 19 で deprecated になった Thread.getId() の五感関数。
 * Java 19 の threadId() が推奨されているが、
 * API 26 のエミュで使うと例外が出ていた
 * > NoSuchMethodError: No virtual method threadId()J in class Ljava/lang/Thread;
 * https://developer.android.com/build/jdks
 * によると Android 14 (API 34)で Java 17 なので、当面は Thread.getId() を使うことになる。
 */
@Suppress("DEPRECATION")
val Thread.idCompat: Long
    get() = id
