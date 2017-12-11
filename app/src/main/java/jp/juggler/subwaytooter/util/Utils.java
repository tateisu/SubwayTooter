package jp.juggler.subwaytooter.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import android.content.ContextWrapper;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.storage.StorageManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.Base64;
import android.util.SparseBooleanArray;

import android.database.Cursor;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.text.DecimalFormat;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import it.sephiroth.android.library.exif2.ExifInterface;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class Utils {
	private static final LogCategory log = new LogCategory( "Utils" );
	
	@SuppressLint("DefaultLocale")
	public static String formatTimeDuration( long t ){
		StringBuilder sb = new StringBuilder();
		long n;
		// day
		n = t / 86400000L;
		if( n > 0 ){
			sb.append( String.format( Locale.JAPAN, "%dd", n ) );
			t -= n * 86400000L;
		}
		// h
		n = t / 3600000L;
		if( n > 0 || sb.length() > 0 ){
			sb.append( String.format( Locale.JAPAN, "%dh", n ) );
			t -= n * 3600000L;
		}
		// m
		n = t / 60000L;
		if( n > 0 || sb.length() > 0 ){
			sb.append( String.format( Locale.JAPAN, "%dm", n ) );
			t -= n * 60000L;
		}
		// s
		float f = t / 1000f;
		sb.append( String.format( Locale.JAPAN, "%.03fs", f ) );
		
		return sb.toString();
	}
	
	static DecimalFormat bytes_format = new DecimalFormat( "#,###" );
	
	public static String formatBytes( long t ){
		return bytes_format.format( t );
		
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
	// 文字列とバイト列の変換
	@NonNull public static byte[] encodeUTF8( @NonNull String str ){
		try{
			return str.getBytes( "UTF-8" );
		}catch( Throwable ex ){
			return new byte[ 0 ]; // 入力がnullの場合のみ発生
		}
	}
	
	// 文字列とバイト列の変換
	@NonNull public static String decodeUTF8( @NonNull byte[] data ){
		try{
			return new String( data, "UTF-8" );
		}catch( Throwable ex ){
			return ""; // 入力がnullの場合のみ発生
		}
	}
	
	// 文字列と整数の変換
	public static int parse_int( String v, int defval ){
		try{
			return Integer.parseInt( v, 10 );
		}catch( Throwable ex ){
			return defval;
		}
	}
	
	public static String optStringX( JSONObject src, String key ){
		return src.isNull( key ) ? null : src.optString( key );
	}
	
	public static String optStringX( JSONArray src, int key ){
		return src.isNull( key ) ? null : src.optString( key );
	}
	
	public static long optLongX( JSONObject src, String key ){
		return optLongX( src, key, 0L );
	}
	
	// 文字列データをLong精度で取得できる
	public static long optLongX( JSONObject src, String key, long defaultValue ){
		Object o = src.opt( key );
		if( o instanceof String ){
			String sv = (String) o;
			if( sv.indexOf( '.' ) == - 1 ){
				try{
					return Long.parseLong( sv, 10 );
				}catch( NumberFormatException ignored ){
				}
			}
		}
		return src.optLong( key, defaultValue );
	}
	
	public static ArrayList< String > parseStringArray( JSONArray array ){
		ArrayList< String > dst_list = new ArrayList<>();
		if( array != null ){
			for( int i = 0, ie = array.length() ; i < ie ; ++ i ){
				String sv = Utils.optStringX( array, i );
				dst_list.add( sv );
			}
		}
		return dst_list;
	}
	
	public static < T > boolean equalsNullable( T a, T b ){
		return a == null ? b == null : a.equals( b );
	}
	
	public static CharSequence dumpCodePoints( String str ){
		StringBuilder sb = new StringBuilder();
		for( int i = 0, ie = str.length(), cp ; i < ie ; i += Character.charCount( cp ) ){
			cp = str.codePointAt( i );
			sb.append( String.format( "0x%x,", cp ) );
		}
		return sb;
	}
	
	static final char[] hex = new char[]{ '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
	
	public static void addHex( StringBuilder sb, byte b ){
		sb.append( hex[ ( b >> 4 ) & 15 ] );
		sb.append( hex[ ( b ) & 15 ] );
	}
	
	public static int hex2int( int c ){
		switch( c ){
		default:
			return 0;
		case '0':
			return 0;
		case '1':
			return 1;
		case '2':
			return 2;
		case '3':
			return 3;
		case '4':
			return 4;
		case '5':
			return 5;
		case '6':
			return 6;
		case '7':
			return 7;
		case '8':
			return 8;
		case '9':
			return 9;
		case 'a':
			return 0xa;
		case 'b':
			return 0xb;
		case 'c':
			return 0xc;
		case 'd':
			return 0xd;
		case 'e':
			return 0xe;
		case 'f':
			return 0xf;
		case 'A':
			return 0xa;
		case 'B':
			return 0xb;
		case 'C':
			return 0xc;
		case 'D':
			return 0xd;
		case 'E':
			return 0xe;
		case 'F':
			return 0xf;
		}
	}
	
	// 16進ダンプ
	public static String encodeHex( byte[] data ){
		if( data == null ) return null;
		StringBuilder sb = new StringBuilder();
		for( byte b : data ){
			addHex( sb, b );
		}
		return sb.toString();
	}
	
	public static byte[] encodeSHA256( byte[] src ){
		try{
			MessageDigest digest = MessageDigest.getInstance( "SHA-256" );
			digest.reset();
			return digest.digest( src );
		}catch( NoSuchAlgorithmException e1 ){
			return null;
		}
	}
	
	public static String encodeBase64Safe( byte[] src ){
		try{
			return Base64.encodeToString( src, Base64.URL_SAFE );
		}catch( Throwable ex ){
			return null;
		}
	}
	
	public static String url2name( String url ){
		if( url == null ) return null;
		return encodeBase64Safe( encodeSHA256( encodeUTF8( url ) ) );
	}
	
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
	public static String digestMD5( String s ){
		if( s == null ) return null;
		try{
			MessageDigest md = MessageDigest.getInstance( "MD5" );
			md.reset();
			return encodeHex( md.digest( s.getBytes( "UTF-8" ) ) );
		}catch( Throwable ex ){
			log.trace( ex );
		}
		return null;
	}
	
	public static String digestSHA256( String src ){
		if( src == null ) return null;
		try{
			MessageDigest md = MessageDigest.getInstance( "SHA-256" );
			md.reset();
			return encodeHex( md.digest( src.getBytes( "UTF-8" ) ) );
		}catch( Throwable ex ){
			log.trace( ex );
		}
		return null;
	}
	
	/////////////////////////////////////////////
	
	static HashMap< Character, String > taisaku_map = new HashMap<>();
	static SparseBooleanArray taisaku_map2 = new SparseBooleanArray();
	
	static void _taisaku_add_string( String z, String h ){
		for( int i = 0, e = z.length() ; i < e ; ++ i ){
			char zc = z.charAt( i );
			taisaku_map.put( zc, "" + Character.toString( h.charAt( i ) ) );
			taisaku_map2.put( (int) zc, true );
		}
	}
	
	static{
		taisaku_map = new HashMap<>();
		taisaku_map2 = new SparseBooleanArray();
		
		// tilde,wave dash,horizontal ellipsis,minus sign
		_taisaku_add_string(
			"\u2073\u301C\u22EF\uFF0D"
			, "\u007e\uFF5E\u2026\u2212"
		);
		// zenkaku to hankaku
		_taisaku_add_string(
			"　！”＃＄％＆’（）＊＋，－．／０１２３４５６７８９：；＜＝＞？＠ＡＢＣＤＥＦＧＨＩＪＫＬＭＮＯＰＱＲＳＴＵＶＷＸＹＺ［］＾＿｀ａｂｃｄｅｆｇｈｉｊｋｌｍｎｏｐｑｒｓｔｕｖｗｘｙｚ｛｜｝"
			, " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_`abcdefghijklmnopqrstuvwxyz{|}"
		);
		
	}
	
	static boolean isBadChar2( char c ){
		return c == 0xa || taisaku_map2.get( (int) c );
	}
	
	//! フォントによって全角文字が化けるので、その対策
	public static String font_taisaku( String text, boolean lf2br ){
		if( text == null ) return null;
		int l = text.length();
		StringBuilder sb = new StringBuilder( l );
		if( ! lf2br ){
			for( int i = 0 ; i < l ; ++ i ){
				int start = i;
				while( i < l && ! taisaku_map2.get( (int) text.charAt( i ) ) ) ++ i;
				if( i > start ){
					sb.append( text.substring( start, i ) );
					if( i >= l ) break;
				}
				sb.append( taisaku_map.get( text.charAt( i ) ) );
			}
		}else{
			for( int i = 0 ; i < l ; ++ i ){
				int start = i;
				while( i < l && ! isBadChar2( text.charAt( i ) ) ) ++ i;
				if( i > start ){
					sb.append( text.substring( start, i ) );
					if( i >= l ) break;
				}
				char c = text.charAt( i );
				if( c == 0xa ){
					sb.append( "<br/>" );
				}else{
					sb.append( taisaku_map.get( c ) );
				}
			}
		}
		return sb.toString();
	}
	
	////////////////////////////
	
	public static String toLower( String from ){
		if( from == null ) return null;
		return from.toLowerCase( Locale.US );
	}
	
	public static String toUpper( String from ){
		if( from == null ) return null;
		return from.toUpperCase( Locale.US );
	}
	
	public static String getString( Bundle b, String key, String defval ){
		try{
			String v = b.getString( key );
			if( v != null ) return v;
		}catch( Throwable ignored ){
		}
		return defval;
	}
	
	public static byte[] loadFile( File file ) throws IOException{
		int size = (int) file.length();
		byte[] data = new byte[ size ];
		FileInputStream in = new FileInputStream( file );
		try{
			int nRead = 0;
			while( nRead < size ){
				int delta = in.read( data, nRead, size - nRead );
				if( delta <= 0 ) break;
			}
			return data;
		}finally{
			try{
				in.close();
			}catch( Throwable ignored ){
			}
		}
	}
	
	public static void hideKeyboard( Context context, View v ){
		InputMethodManager imm = (InputMethodManager) context.getSystemService( Context.INPUT_METHOD_SERVICE );
		imm.hideSoftInputFromWindow( v.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS );
	}
	
	public static void showKeyboard( Context context, View v ){
		try{
			InputMethodManager imm = (InputMethodManager) context.getSystemService( Context.INPUT_METHOD_SERVICE );
			imm.showSoftInput( v, InputMethodManager.HIDE_NOT_ALWAYS );
		}catch( Throwable ignored ){
			
		}
	}
	
	public static String ellipsize( String t, int max ){
		return ( t.length() > max ? t.substring( 0, max - 1 ) + "…" : t );
	}
	
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
	//		if( TextUtils.isEmpty( msg ) ){
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
	
	static HashMap< String, String > mime_type_ex = null;
	static final Object mime_type_ex_lock = new Object();
	
	static String findMimeTypeEx( String ext ){
		synchronized( mime_type_ex_lock ){
			if( mime_type_ex == null ){
				HashMap< String, String > tmp = new HashMap<>();
				tmp.put( "BDM", "application/vnd.syncml.dm+wbxml" );
				tmp.put( "DAT", "" );
				tmp.put( "TID", "" );
				tmp.put( "js", "text/javascript" );
				tmp.put( "sh", "application/x-sh" );
				tmp.put( "lua", "text/x-lua" );
				mime_type_ex = tmp;
			}
			return mime_type_ex.get( ext );
		}
	}
	
	public static final String MIME_TYPE_APPLICATION_OCTET_STREAM = "application/octet-stream";
	
	public static String getMimeType( LogCategory log, String src ){
		String ext = MimeTypeMap.getFileExtensionFromUrl( src );
		if( ! TextUtils.isEmpty( ext ) ){
			ext = ext.toLowerCase( Locale.US );
			
			//
			String mime_type = MimeTypeMap.getSingleton().getMimeTypeFromExtension( ext );
			if( ! TextUtils.isEmpty( mime_type ) ) return mime_type;
			
			//
			mime_type = findMimeTypeEx( ext );
			if( ! TextUtils.isEmpty( mime_type ) ) return mime_type;
			
			// 戻り値が空文字列の場合とnullの場合があり、空文字列の場合は既知でありログ出力しない
			
			if( mime_type == null && log != null )
				log.w( "getMimeType(): unknown file extension '%s'", ext );
		}
		return MIME_TYPE_APPLICATION_OCTET_STREAM;
	}
	
	public static Spannable formatSpannable1( Context context, int string_id, CharSequence display_name ){
		String s = context.getString( string_id );
		int end = s.length();
		int pos = s.indexOf( "%1$s" );
		if( pos == - 1 ) return new SpannableString( s );
		
		SpannableStringBuilder sb = new SpannableStringBuilder();
		if( pos > 0 ) sb.append( s.substring( 0, pos ) );
		sb.append( display_name );
		if( pos + 4 < end ) sb.append( s.substring( pos + 4, end ) );
		return sb;
	}
	
	public static Bitmap createResizedBitmap( LogCategory log, Context context, Uri uri, boolean skipIfNoNeedToResizeAndRotate, int resize_to ){
		try{
			// EXIF回転情報の取得
			Integer orientation;
			
			InputStream is = context.getContentResolver().openInputStream( uri );
			if( is == null ){
				Utils.showToast( context, false, "could not open image." );
				return null;
			}
			
			try{
				ExifInterface exif = new ExifInterface();
				exif.readExif( is, ExifInterface.Options.OPTION_IFD_0 | ExifInterface.Options.OPTION_IFD_1 | ExifInterface.Options.OPTION_IFD_EXIF );
				orientation = exif.getTagIntValue( ExifInterface.TAG_ORIENTATION );
			}finally{
				IOUtils.closeQuietly( is );
			}
			
			// 画像のサイズを調べる
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;
			options.inScaled = false;
			is = context.getContentResolver().openInputStream( uri );
			if( is == null ){
				Utils.showToast( context, false, "could not open image." );
				return null;
			}
			try{
				BitmapFactory.decodeStream( is, null, options );
			}finally{
				IOUtils.closeQuietly( is );
			}
			int src_width = options.outWidth;
			int src_height = options.outHeight;
			if( src_width <= 0 || src_height <= 0 ){
				Utils.showToast( context, false, "could not get image bounds." );
				return null;
			}
			
			// 長辺
			int size = ( src_width > src_height ? src_width : src_height );
			
			// リサイズも回転も必要がない場合
			if( skipIfNoNeedToResizeAndRotate
				&& ( orientation == null || orientation == 1 )
				&& ( resize_to <= 0 || size <= resize_to )
				
				){
				log.d( "createOpener: no need to resize & rotate" );
				return null;
			}
			
			//noinspection StatementWithEmptyBody
			if( size > resize_to ){
				// 縮小が必要
			}else{
				// 縮小は不要
				resize_to = size;
			}
			
			// inSampleSizeを計算
			int bits = 0;
			int x = size;
			while( x > resize_to * 2 ){
				++ bits;
				x >>= 1;
			}
			options.inJustDecodeBounds = false;
			options.inSampleSize = 1 << bits;
			is = context.getContentResolver().openInputStream( uri );
			if( is == null ){
				Utils.showToast( context, false, "could not open image." );
				return null;
			}
			Bitmap src;
			try{
				src = BitmapFactory.decodeStream( is, null, options );
			}finally{
				IOUtils.closeQuietly( is );
			}
			if( src == null ){
				Utils.showToast( context, false, "could not decode image." );
				return null;
			}
			try{
				src_width = options.outWidth;
				src_height = options.outHeight;
				float scale;
				int dst_width;
				int dst_height;
				if( src_width >= src_height ){
					scale = resize_to / (float) src_width;
					dst_width = resize_to;
					dst_height = (int) ( 0.5f + src_height / (float) src_width * resize_to );
					if( dst_height < 1 ) dst_height = 1;
				}else{
					scale = resize_to / (float) src_height;
					dst_height = resize_to;
					dst_width = (int) ( 0.5f + src_width / (float) src_height * resize_to );
					if( dst_width < 1 ) dst_width = 1;
				}
				
				Matrix matrix = new Matrix();
				matrix.reset();
				
				// 画像の中心が原点に来るようにして
				matrix.postTranslate( src_width * - 0.5f, src_height * - 0.5f );
				// スケーリング
				matrix.postScale( scale, scale );
				// 回転情報があれば回転
				if( orientation != null ){
					int tmp;
					switch( orientation.shortValue() ){
					default:
						break;
					case 2:
						matrix.postScale( 1f, - 1f );
						break; // 上下反転
					case 3:
						matrix.postRotate( 180f );
						break; // 180度回転
					case 4:
						matrix.postScale( - 1f, 1f );
						break; // 左右反転
					case 5:
						tmp = dst_width;
						//noinspection SuspiciousNameCombination
						dst_width = dst_height;
						dst_height = tmp;
						matrix.postScale( 1f, - 1f );
						matrix.postRotate( - 90f );
						break;
					case 6:
						tmp = dst_width;
						//noinspection SuspiciousNameCombination
						dst_width = dst_height;
						dst_height = tmp;
						matrix.postRotate( 90f );
						break;
					case 7:
						tmp = dst_width;
						//noinspection SuspiciousNameCombination
						dst_width = dst_height;
						dst_height = tmp;
						matrix.postScale( 1f, - 1f );
						matrix.postRotate( 90f );
						break;
					case 8:
						tmp = dst_width;
						//noinspection SuspiciousNameCombination
						dst_width = dst_height;
						dst_height = tmp;
						matrix.postRotate( - 90f );
						break;
					}
				}
				// 表示領域に埋まるように平行移動
				matrix.postTranslate( dst_width * 0.5f, dst_height * 0.5f );
				
				// 出力用Bitmap作成
				Bitmap dst = Bitmap.createBitmap( dst_width, dst_height, Bitmap.Config.ARGB_8888 );
				if( dst == null ){
					Utils.showToast( context, false, "bitmap creation failed." );
					return null;
				}
				try{
					Canvas canvas = new Canvas( dst );
					Paint paint = new Paint();
					paint.setFilterBitmap( true );
					canvas.drawBitmap( src, matrix, paint );
					log.d( "createResizedBitmap: resized to %sx%s", dst_width, dst_height );
					Bitmap tmp = dst;
					dst = null;
					return tmp;
				}finally{
					if( dst != null ) dst.recycle();
				}
			}finally{
				src.recycle();
			}
		}catch( SecurityException ex ){
			log.e( ex, "maybe we need pick up image again." );
		}catch( Throwable ex ){
			log.trace( ex );
		}
		return null;
	}
	
	public static String formatResponse( @NonNull Response response, String caption ){
		StringBuilder sb = new StringBuilder();
		try{
			int empty_length = sb.length();
			
			ResponseBody body = response.body();
			if( body != null ){
				try{
					String sv = body.string();
					if( ! TextUtils.isEmpty( sv ) ){
						try{
							JSONObject data = new JSONObject( sv );
							String error = data.getString( "error" );
							sb.append( error );
						}catch( Throwable ex ){
							log.e( ex, "response body is not JSON or missing 'error' attribute." );
						}
						if( sb.length() == empty_length ){
							// JSONではなかった
							
							// HTMLならタグの除去を試みる
							String ct = response.header( "content-type" );
							if( ct != null && ct.contains( "/html" ) ){
								sv = new DecodeOptions().decodeHTML( null, null, sv ).toString();
							}
							
							sb.append( sv );
						}
					}
				}catch( Throwable ex ){
					log.e( ex, "response body is not String." );
				}
			}
			
			if( sb.length() == empty_length ) sb.append( ' ' );
			sb.append( "(HTTP " ).append( Integer.toString( response.code() ) );
			
			String message = response.message();
			if( ! TextUtils.isEmpty( message ) ){
				sb.append( ' ' ).append( message );
			}
			sb.append( ")" );
			
			if( ! TextUtils.isEmpty( caption ) ){
				sb.append( ' ' ).append( caption );
			}
			
		}catch( Throwable ex ){
			log.trace( ex );
		}
		return sb.toString().replaceAll( "\n+", "\n" );
	}
	
	public interface ScanViewCallback {
		void onScanView( View v );
	}
	
	public static void scanView( View v, ScanViewCallback callback ){
		if( v == null ) return;
		callback.onScanView( v );
		if( v instanceof ViewGroup ){
			ViewGroup vg = (ViewGroup) v;
			for( int i = 0, ie = vg.getChildCount() ; i < ie ; ++ i ){
				scanView( vg.getChildAt( i ), callback );
			}
		}
	}
	
	static class FileInfo {
		
		Uri uri;
		String mime_type;
		
		FileInfo( String any_uri ){
			if( any_uri == null ) return;
			
			if( any_uri.startsWith( "/" ) ){
				uri = Uri.fromFile( new File( any_uri ) );
			}else{
				uri = Uri.parse( any_uri );
			}
			
			String ext = MimeTypeMap.getFileExtensionFromUrl( any_uri );
			if( ext != null ){
				mime_type = MimeTypeMap.getSingleton().getMimeTypeFromExtension( ext.toLowerCase() );
			}
		}
	}
	
	static
	@NonNull
	Map< String, String > getSecondaryStorageVolumesMap( Context context ){
		Map< String, String > result = new HashMap<>();
		try{
			
			StorageManager sm = (StorageManager) context.getApplicationContext().getSystemService( Context.STORAGE_SERVICE );
			
			// SDカードスロットのある7.0端末が手元にないから検証できない
			//			if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ){
			//				for(StorageVolume volume : sm.getStorageVolumes() ){
			//					// String path = volume.getPath();
			//					String state = volume.getState();
			//
			//				}
			//			}
			
			Method getVolumeList = sm.getClass().getMethod( "getVolumeList" );
			Object[] volumes = (Object[]) getVolumeList.invoke( sm );
			//
			for( Object volume : volumes ){
				Class< ? > volume_clazz = volume.getClass();
				
				String path = (String) volume_clazz.getMethod( "getPath" ).invoke( volume );
				String state = (String) volume_clazz.getMethod( "getState" ).invoke( volume );
				if( ! TextUtils.isEmpty( path ) && "mounted".equals( state ) ){
					//
					boolean isPrimary = (Boolean) volume_clazz.getMethod( "isPrimary" ).invoke( volume );
					if( isPrimary ) result.put( "primary", path );
					//
					String uuid = (String) volume_clazz.getMethod( "getUuid" ).invoke( volume );
					if( ! TextUtils.isEmpty( uuid ) ) result.put( uuid, path );
				}
			}
		}catch( Throwable ex ){
			log.trace( ex );
		}
		return result;
	}
	
	public static String toCamelCase( String src ){
		StringBuilder sb = new StringBuilder();
		for( String s : src.split( "_" ) ){
			if( TextUtils.isEmpty( s ) ) continue;
			sb.append( Character.toUpperCase( s.charAt( 0 ) ) );
			sb.append( s.substring( 1, s.length() ).toLowerCase() );
		}
		return sb.toString();
	}
	
	private static DocumentBuilder xml_builder;
	
	public static Element parseXml( byte[] src ){
		if( xml_builder == null ){
			try{
				xml_builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			}catch( Throwable ex ){
				log.trace( ex );
				return null;
			}
		}
		try{
			return xml_builder.parse( new ByteArrayInputStream( src ) ).getDocumentElement();
		}catch( Throwable ex ){
			log.trace( ex );
			return null;
		}
	}
	
	public static String getAttribute( @NonNull NamedNodeMap attr_map, @NonNull String name, @Nullable String defval ){
		Node node = attr_map.getNamedItem( name );
		if( node != null ) return node.getNodeValue();
		return defval;
	}
	
	@SuppressWarnings("unused")
	public static String formatError( @NonNull Throwable ex, @NonNull String fmt, Object... args ){
		if( args.length > 0 ) fmt = String.format( fmt, args );
		return fmt + String.format( " :%s %s", ex.getClass().getSimpleName(), ex.getMessage() );
	}
	
	@SuppressWarnings("unused")
	public static String formatError( @NonNull Throwable ex, @NonNull Resources resources, int string_id, Object... args ){
		return resources.getString( string_id, args ) + String.format( " :%s %s", ex.getClass().getSimpleName(), ex.getMessage() );
	}
	
	public static boolean isMainThread(){
		return Looper.getMainLooper().getThread() == Thread.currentThread();
	}
	
	public static void runOnMainThread( @NonNull Runnable proc ){
		if( Looper.getMainLooper().getThread() == Thread.currentThread() ){
			proc.run();
		}else{
			new Handler( Looper.getMainLooper() ).post( proc );
		}
	}
	
	private static WeakReference< Toast > refToast;
	
	private static void showToastImpl( final Context context, final boolean bLong, final String message ){
		runOnMainThread( new Runnable() {
			@Override public void run(){
				// 前回のトーストの表示を終了する
				Toast t = refToast == null ? null : refToast.get();
				if( t != null ) t.cancel();
				// 新しいトーストを作る
				t = Toast.makeText(
					context
					, message
					, bLong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT
				);
				t.show();
				refToast = new WeakReference<>( t );
			}
		} );
	}
	
	public static void showToast( @NonNull Context context, boolean bLong, @NonNull String fmt, Object... args ){
		showToastImpl( context, bLong, args.length == 0 ? fmt : String.format( fmt, args ) );
	}
	
	public static void showToast( @NonNull Context context, @NonNull Throwable ex, String fmt, Object... args ){
		showToastImpl( context, true, formatError( ex, fmt, args ) );
	}
	
	public static void showToast( @NonNull Context context, boolean bLong, int string_id, Object... args ){
		showToastImpl( context, bLong, context.getString( string_id, args ) );
	}
	
	public static void showToast( @NonNull Context context, @NonNull Throwable ex, int string_id, Object... args ){
		showToastImpl( context, true, formatError( ex, context.getResources(), string_id, args ) );
	}
	
	public static boolean isExternalStorageDocument( Uri uri ){
		return "com.android.externalstorage.documents".equals( uri.getAuthority() );
	}
	
	private static final String PATH_TREE = "tree";
	private static final String PATH_DOCUMENT = "document";
	
	public static String getDocumentId( Uri documentUri ){
		final List< String > paths = documentUri.getPathSegments();
		if( paths.size() >= 2 && PATH_DOCUMENT.equals( paths.get( 0 ) ) ){
			// document
			return paths.get( 1 );
		}
		if( paths.size() >= 4 && PATH_TREE.equals( paths.get( 0 ) )
			&& PATH_DOCUMENT.equals( paths.get( 2 ) ) ){
			// document in tree
			return paths.get( 3 );
		}
		if( paths.size() >= 2 && PATH_TREE.equals( paths.get( 0 ) ) ){
			// tree
			return paths.get( 1 );
		}
		throw new IllegalArgumentException( "Invalid URI: " + documentUri );
	}
	
	public static
	@Nullable
	File getFile( Context context, @NonNull String path ){
		try{
			if( path.startsWith( "/" ) ) return new File( path );
			Uri uri = Uri.parse( path );
			if( "file".equals( uri.getScheme() ) ) return new File( uri.getPath() );
			
			// if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT )
			{
				if( isExternalStorageDocument( uri ) ){
					try{
						final String docId = getDocumentId( uri );
						final String[] split = docId.split( ":" );
						if( split.length >= 2 ){
							final String uuid = split[ 0 ];
							if( "primary".equalsIgnoreCase( uuid ) ){
								return new File( Environment.getExternalStorageDirectory() + "/" + split[ 1 ] );
							}else{
								Map< String, String > volume_map = Utils.getSecondaryStorageVolumesMap( context );
								String volume_path = volume_map.get( uuid );
								if( volume_path != null ){
									return new File( volume_path + "/" + split[ 1 ] );
								}
							}
						}
					}catch( Throwable ex ){
						log.trace( ex );
					}
				}
			}
			// MediaStore Uri
			Cursor cursor = context.getContentResolver().query( uri, null, null, null, null );
			if( cursor != null ){
				try{
					if( cursor.moveToFirst() ){
						int col_count = cursor.getColumnCount();
						for( int i = 0 ; i < col_count ; ++ i ){
							int type = cursor.getType( i );
							if( type != Cursor.FIELD_TYPE_STRING ) continue;
							String name = cursor.getColumnName( i );
							String value = cursor.isNull( i ) ? null : cursor.getString( i );
							if( ! TextUtils.isEmpty( value ) ){
								if( "filePath".equals( name ) ){
									return new File( value );
								}
							}
						}
					}
				}finally{
					cursor.close();
				}
			}
		}catch( Throwable ex ){
			log.trace( ex );
		}
		return null;
	}
	
	public static Activity getActivityFromView( View view ){
		Context context = view.getContext();
		while( context instanceof ContextWrapper ){
			if( context instanceof Activity ){
				return (Activity) context;
			}
			context = ( (ContextWrapper) context ).getBaseContext();
		}
		return null;
	}
	
	private static final char ALM = (char) 0x061c; // Arabic letter mark (ALM)
	private static final char LRM = (char) 0x200E; //	Left-to-right mark (LRM)
	private static final char RLM = (char) 0x200F; //	Right-to-left mark (RLM)
	private static final char LRE = (char) 0x202A; // Left-to-right embedding (LRE)
	private static final char RLE = (char) 0x202B; // Right-to-left embedding (RLE)
	private static final char PDF = (char) 0x202C; // Pop directional formatting (PDF)
	private static final char LRO = (char) 0x202D; // Left-to-right override (LRO)
	private static final char RLO = (char) 0x202E; // Right-to-left override (RLO)
	
	private static final String CHARS_MUST_PDF = String.valueOf( LRE ) + RLE + LRO + RLO;
	
	private static final char LRI = (char) 0x2066; // Left-to-right isolate (LRI)
	private static final char RLI = (char) 0x2067; // Right-to-left isolate (RLI)
	private static final char FSI = (char) 0x2068; // First strong isolate (FSI)
	private static final char PDI = (char) 0x2069; // Pop directional isolate (PDI)
	
	private static final String CHARS_MUST_PDI = String.valueOf( LRI ) + RLI + FSI;
	
	public static String sanitizeBDI( String src ){
		
		LinkedList< Character > stack = null;
		
		for( int i = 0, ie = src.length() ; i < ie ; ++ i ){
			char c = src.charAt( i );
			
			if( - 1 != CHARS_MUST_PDF.indexOf( c ) ){
				if( stack == null ) stack = new LinkedList<>();
				stack.add( PDF );
				
			}else if( - 1 != CHARS_MUST_PDI.indexOf( c ) ){
				if( stack == null ) stack = new LinkedList<>();
				stack.add( PDI );
			}else if( stack != null && ! stack.isEmpty() && stack.getLast() == c ){
				stack.removeLast();
			}
		}
		
		if( stack == null || stack.isEmpty() ) return src;
		
		StringBuilder sb = new StringBuilder();
		sb.append( src );
		while( ! stack.isEmpty() ){
			sb.append( (char) stack.removeLast() );
		}
		return sb.toString();
	}
	
	public static byte[] loadRawResource( Context context, int res_id ){
		try{
			InputStream is = context.getResources().openRawResource( res_id );
			try{
				ByteArrayOutputStream bao = new ByteArrayOutputStream();
				IOUtils.copy( is, bao );
				
				return bao.toByteArray();
				
			}finally{
				IOUtils.closeQuietly( is );
			}
			
		}catch( Throwable ex ){
			log.trace( ex );
		}
		return null;
	}
	
	// defined or
	public static < T > T dor( T... args ){
		if( args != null ){
			for( T a : args ){
				if( a != null ) return a;
			}
		}
		return null;
	}
}
