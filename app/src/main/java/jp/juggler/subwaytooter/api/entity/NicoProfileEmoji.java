package jp.juggler.subwaytooter.api.entity;

import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;

import jp.juggler.subwaytooter.util.Utils;

public class NicoProfileEmoji {
	public String url;
	public String account_url;
	public long account_id;
	public String shortcode;
	
	public static class Map extends HashMap< String, NicoProfileEmoji > {
		
	}
	
	public static NicoProfileEmoji parse( @Nullable JSONObject src ){
		if( src == null ) return null;
		NicoProfileEmoji dst = new NicoProfileEmoji();
		dst.url = Utils.optStringX( src, "url" );
		dst.account_url = Utils.optStringX( src, "account_url" );
		dst.account_id = Utils.optLongX( src, "account_id", - 1L );
		dst.shortcode = Utils.optStringX( src, "shortcode" );
		return ( ( TextUtils.isEmpty( dst.url )
			|| TextUtils.isEmpty( dst.account_url )
			|| dst.account_id == - 1L
			|| TextUtils.isEmpty( dst.shortcode )
		) ) ? null : dst;
	}
	
	public static Map parseMap( @Nullable JSONArray src ){
		if( src == null ) return null;
		int count = src.length();
		if( count == 0 ) return null;
		Map dst = new Map();
		for( int i = 0 ; i < count ; ++ i ){
			NicoProfileEmoji item = parse( src.optJSONObject( i ) );
			if( item != null ) dst.put( item.shortcode,item );
		}
		return dst.isEmpty() ? null : dst;
	}
}


