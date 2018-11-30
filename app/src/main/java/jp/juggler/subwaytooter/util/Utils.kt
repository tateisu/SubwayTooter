package jp.juggler.subwaytooter.util

import android.util.SparseBooleanArray
import jp.juggler.util.LogCategory
import kotlin.collections.set

object Utils {
	
	val log = LogCategory("Utils")
	
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
}

