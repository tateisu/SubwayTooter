package jp.juggler.subwaytooter.api.entity;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class TootTag {
	
	private static final LogCategory log = new LogCategory( "TootTag" );
	
	// The hashtag, not including the preceding #
	public String name;
	
	// The URL of the hashtag
	public String url;
	
	@Nullable
	public static TootTag parse( JSONObject src ){
		if( src == null ) return null;
		try{
			TootTag dst = new TootTag();
			dst.name = Utils.optStringX( src, "name" );
			dst.url = Utils.optStringX( src, "url" );
			return dst;
		}catch( Throwable ex ){
			log.trace( ex );
			log.e( ex, "parse failed." );
			return null;
		}
	}
	
	public static class List extends ArrayList< TootTag > {
		
	}
	
	@NonNull
	public static List parseList( JSONArray array ){
		List result = new List();
		if( array != null ){
			int array_size = array.length();
			result.ensureCapacity( array_size );
			for( int i = 0 ; i < array_size ; ++ i ){
				JSONObject src = array.optJSONObject( i );
				if( src == null ) continue;
				TootTag item = parse( src );
				if( item != null ) result.add( item );
			}
		}
		return result;
	}
}
