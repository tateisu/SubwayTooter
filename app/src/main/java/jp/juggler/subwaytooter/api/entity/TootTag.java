package jp.juggler.subwaytooter.api.entity;

import android.support.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class TootTag {
	
	// The hashtag, not including the preceding #
	public String name;
	
	// The URL of the hashtag
	public String url;
	
	public static TootTag parse( LogCategory log, JSONObject src ){
		if( src == null ) return null;
		try{
			TootTag dst = new TootTag();
			dst.name = Utils.optStringX( src, "name" );
			dst.url = Utils.optStringX( src, "url" );
			return dst;
		}catch( Throwable ex ){
			ex.printStackTrace();
			log.e( ex, "TootTag.parse failed." );
			return null;
		}
	}
	
	public static class List extends ArrayList< TootTag > {
		
	}
	
	@NonNull
	public static List parseList( LogCategory log, JSONArray array ){
		List result = new List();
		if( array != null ){
			int array_size = array.length();
			result.ensureCapacity( array_size );
			for( int i = 0 ; i < array_size ; ++ i ){
				JSONObject src = array.optJSONObject( i );
				if( src == null ) continue;
				TootTag item = parse( log, src );
				if( item != null ) result.add( item );
			}
		}
		return result;
	}
}
