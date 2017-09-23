package jp.juggler.subwaytooter.api.entity;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import jp.juggler.subwaytooter.util.Utils;

public class CustomEmoji {
	
	// shortcode (コロンを含まない)
	public String shortcode;
	// url
	public String url;
	
	public static class List extends ArrayList< CustomEmoji > {
	}
	
	public static class Map extends HashMap<String,String> {
		// キー： shortcode (コロンを含まない)
		// 値： url
	}
	
	public static CustomEmoji parse( JSONObject src ){
		if( src == null ) return null;
		CustomEmoji dst = new CustomEmoji();
		String k = dst.shortcode = Utils.optStringX( src, "shortcode" );
		String v = dst.url = Utils.optStringX( src, "url" );
		if( ! TextUtils.isEmpty( k ) && ! TextUtils.isEmpty( v ) ){
			return dst;
		}
		
		return null;
	}
	
	public static List parseList( JSONArray src ){
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
		return dst;
	}

	
	public static Map parseMap( JSONArray src  ){
		if( src==null ) return null;
		Map dst = new Map();
		for(int i=0,ie=src.length();i<ie;++i){
			JSONObject it = src.optJSONObject( i );
			String k = Utils.optStringX(it,"shortcode");
			String v = Utils.optStringX(it,"url");
			if( ! TextUtils.isEmpty( k ) && ! TextUtils.isEmpty( v )  ){
				dst.put( k,v);
			}
		}
		return dst;
	}
	
}
