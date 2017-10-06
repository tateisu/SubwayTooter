package jp.juggler.subwaytooter.api.entity;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class CustomEmoji {
	static final LogCategory log = new LogCategory( "CustomEmoji" );
	
	// shortcode (コロンを含まない)
	@NonNull final public String shortcode;
	
	// 画像URL
	@NonNull final public String url;
	
	// アニメーションなしの画像URL
	@Nullable final public String static_url;
	
	private CustomEmoji( @NonNull String shortcode, @NonNull String url, @Nullable String static_url ){
		this.shortcode = shortcode;
		this.url = url;
		this.static_url = static_url;
	}
	
	public static CustomEmoji parse( JSONObject src ){
		if( src == null ) return null;
		String shortcode = Utils.optStringX( src, "shortcode" );
		String url = Utils.optStringX( src, "url" );
		String static_url = Utils.optStringX( src, "static_url" ); // may null
		if( TextUtils.isEmpty( shortcode ) || TextUtils.isEmpty( url ) ){
			return null;
		}
		return new CustomEmoji( shortcode, url, static_url );
	}
	
	public static class List extends ArrayList< CustomEmoji > {
	}
	
	public static List parseList( JSONArray src, @NonNull String instance ){
		if( src == null ) return null;
		List dst = new List();
		for( int i = 0, ie = src.length() ; i < ie ; ++ i ){
			CustomEmoji item = parse( src.optJSONObject( i ) );
			if( item != null ) dst.add( item );
		}
		Collections.sort( dst, new Comparator< CustomEmoji >() {
			@Override public int compare( CustomEmoji a, CustomEmoji b ){
				return a.shortcode.compareToIgnoreCase( b.shortcode );
			}
		} );
		log.d( "parseList: parse %d emojis for %s.", dst.size(), instance );
		return dst;
	}
	
	public static class Map extends HashMap< String, CustomEmoji > {
		// キー： shortcode (コロンを含まない)
	}
	
	public static Map parseMap( JSONArray src, @NonNull String instance ){
		if( src == null ) return null;
		Map dst = new Map();
		for( int i = 0, ie = src.length() ; i < ie ; ++ i ){
			CustomEmoji item = parse( src.optJSONObject( i ) );
			if( item != null ) dst.put( item.shortcode, item );
		}
		log.d( "parseMap: parse %d emojis for %s.", dst.size(), instance );
		return dst;
	}
	
}
