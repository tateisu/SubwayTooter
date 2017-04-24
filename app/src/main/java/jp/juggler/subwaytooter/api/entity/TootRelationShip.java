package jp.juggler.subwaytooter.api.entity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

import jp.juggler.subwaytooter.util.LogCategory;

public class TootRelationShip {
	
	//	Target account id
	public long id;
	
	//	Whether the user is currently following the account
	public boolean following;
	
	//	Whether the user is currently being followed by the account
	public boolean followed_by;
	
	//	Whether the user is currently blocking the account
	public boolean blocking;
	
	//	Whether the user is currently muting the account
	public boolean muting;
	
	//	Whether the user has requested to follow the account
	public boolean requested;
	
	public static TootRelationShip parse( LogCategory log, JSONObject src ){
		if( src == null ) return null;
		try{
			TootRelationShip dst = new TootRelationShip();
			dst.id = src.optLong( "id" );
			dst.following = src.optBoolean( "following" );
			dst.followed_by = src.optBoolean( "followed_by" );
			dst.blocking = src.optBoolean( "blocking" );
			dst.muting = src.optBoolean( "muting" );
			dst.requested = src.optBoolean( "requested" );
			return dst;
		}catch( Throwable ex ){
			ex.printStackTrace();
			log.e(ex,"TootRelationShip.parse failed.");
			return null;
		}
	}
	
	public static class List extends ArrayList< TootRelationShip > {
		public List(){
			super();
		}
		
		public List( int capacity ){
			super( capacity );
		}
	}
	
	public static List parseList( LogCategory log, JSONArray array ){
		List result = new List();
		if( array != null ){
			for( int i = array.length() - 1 ; i >= 0 ; -- i ){
				JSONObject src = array.optJSONObject( i );
				if( src == null ) continue;
				TootRelationShip item = parse( log, src );
				if( item != null ) result.add( 0, item );
			}
		}
		return result;
	}
	
}
