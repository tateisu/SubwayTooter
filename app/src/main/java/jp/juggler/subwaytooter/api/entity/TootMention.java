package jp.juggler.subwaytooter.api.entity;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class TootMention {
	private static final LogCategory log = new LogCategory( "TootMention" );
	
	public static class List extends ArrayList< TootMention > {
		
	}
	
	//	URL of user's profile (can be remote)
	public String url;
	
	//	The username of the account
	public String username;
	
	//	Equals username for local users, includes @domain for remote ones
	public String acct;
	
	//	Account ID
	public long id;
	
	@Nullable
	public static TootMention parse( JSONObject src ){
		if( src == null ) return null;
		try{
			TootMention dst = new TootMention();
			dst.url = Utils.optStringX( src, "url" );
			dst.username = Utils.optStringX( src, "username" );
			dst.acct = Utils.optStringX( src, "acct" );
			dst.id = src.optLong( "id" );
			return dst;
		}catch( Throwable ex ){
			log.trace( ex );
			log.e( ex, "TootMention.parse failed." );
			return null;
		}
	}
	
	@NonNull public static List parseList( JSONArray array ){
		List result = new List();
		if( array != null ){
			int array_size = array.length();
			result.ensureCapacity( array_size );
			for( int i = 0 ; i < array_size ; ++ i ){
				JSONObject src = array.optJSONObject( i );
				if( src == null ) continue;
				TootMention item = parse( src );
				if( item != null ) result.add( item );
			}
		}
		return result;
	}
}
