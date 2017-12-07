package jp.juggler.subwaytooter.api.entity;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;

import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class TootList {
	private static final LogCategory log = new LogCategory( "TootList" );
	
	public long id;
	
	@Nullable public String title;
	
	@Nullable
	public static TootList parse( JSONObject src ){
		if( src == null ) return null;
		try{
			TootList dst = new TootList();
			dst.id = Utils.optLongX( src, "id" );
			dst.title = Utils.optStringX( src, "title" );
			
			return dst;
		}catch( Throwable ex ){
			log.trace( ex );
			log.e( ex, "parse failed." );
			return null;
		}
	}
	
	public static class List extends ArrayList< TootList > {
	}
	
	@NonNull public static List parseList( JSONArray array ){
		TootList.List result = new TootList.List();
		if( array != null ){
			int array_size = array.length();
			result.ensureCapacity( array_size );
			for( int i = 0 ; i < array_size ; ++ i ){
				JSONObject obj = array.optJSONObject( i );
				if( obj != null ) {
					TootList dst = TootList.parse( obj );
					if( dst != null ) result.add( dst );
				}
			}
		}
		return result;
	}
}
