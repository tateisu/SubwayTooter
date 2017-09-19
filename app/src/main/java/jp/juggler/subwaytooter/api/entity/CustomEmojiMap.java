package jp.juggler.subwaytooter.api.entity;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;

import jp.juggler.subwaytooter.util.Utils;

public class CustomEmojiMap extends HashMap<String,String> {
	
	// キー： shortcode (コロンを含まない)
	// 値： url
	
	public static CustomEmojiMap parse( JSONArray src  ){
		if( src==null ) return null;
		CustomEmojiMap dst = new CustomEmojiMap();
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
