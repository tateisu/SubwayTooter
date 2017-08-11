package jp.juggler.subwaytooter.api_msp.entity;

import android.content.Context;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.HTMLDecoder;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class MSPAccount extends TootAccount {
	
	private static final Pattern reAccountUrl = Pattern.compile( "\\Ahttps://([^/#?]+)/@([^/#?]+)\\z" );
	
	
	static TootAccount parseAccount( @NonNull Context context, LogCategory log, SavedAccount access_info, JSONObject src ){
		
		if( src == null ) return null;
		
		MSPAccount dst = new MSPAccount();
		dst.url = Utils.optStringX( src, "url" );
		dst.username = Utils.optStringX( src, "username" );
		dst.avatar = dst.avatar_static = Utils.optStringX( src, "avatar" );
		
		String sv = Utils.optStringX( src, "display_name" );
		dst.setDisplayName( context, dst.username , sv );
		
		dst.id = src.optLong( "id" );
		
		dst.note = Utils.optStringX( src, "note" );
		dst.decoded_note = HTMLDecoder.decodeHTML( context,access_info, ( dst.note != null ? dst.note : null ), true, true,null );
		
		if( TextUtils.isEmpty( dst.url ) ){
			log.e( "parseAccount: missing url" );
			return null;
		}
		Matcher m = reAccountUrl.matcher( dst.url );
		if( ! m.find() ){
			log.e( "parseAccount: not account url: %s", dst.url );
			return null;
		}else{
			dst.acct = dst.username + "@" + m.group( 1 );
		}
		
		return dst;
	}
}
