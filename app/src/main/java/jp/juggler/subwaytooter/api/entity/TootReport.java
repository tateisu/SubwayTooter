package jp.juggler.subwaytooter.api.entity;

import android.support.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class TootReport extends TootId {
	
	//	The ID of the report
	//TootId public long id;
	
	//	The action taken in response to the report
	public String action_taken;
	
	public static TootReport parse( LogCategory log, JSONObject src ){
		if( src == null ) return null;
		try{
			TootReport dst = new TootReport();
			dst.id = src.optLong( "id" );
			dst.action_taken = Utils.optStringX( src, "action_taken" );
			return dst;
		}catch( Throwable ex ){
			ex.printStackTrace();
			log.e( ex, "TootReport.parse failed." );
			return null;
		}
	}
	
	public static class List extends ArrayList< TootReport > {
		
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
				TootReport item = parse( log, src );
				if( item != null ) result.add( item );
			}
		}
		return result;
	}
}
