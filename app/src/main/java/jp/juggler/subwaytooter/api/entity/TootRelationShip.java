package jp.juggler.subwaytooter.api.entity;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

import jp.juggler.subwaytooter.table.UserRelation;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class TootRelationShip {
	
	private static final LogCategory log = new LogCategory( "TootRelationShip" );
	
	// Target account id
	public long id;
	
	// Whether the authorized user is currently following the target account.
	// maybe faked in response of follow-request.
	public boolean following;
	
	// Whether the authorized user is currently being followed by the target account.
	public boolean followed_by;
	
	// Whether the authorized user is currently blocking the target account.
	public boolean blocking;
	
	// Whether the authorized user is currently muting the target account.
	public boolean muting;
	
	// Whether the authorized user has requested to follow the target account.
	// maybe true while follow-request is progress on server, even if the user is not locked.
	public boolean requested;
	
	// (mastodon 2.1 or later) per-following-user setting.
	// Whether the boosts from target account will be shown on authorized user's home TL.
	public int showing_reblogs = UserRelation.REBLOG_UNKNOWN;
	
	
	
	@Nullable
	public static TootRelationShip parse( JSONObject src ){
		if( src == null ) return null;
		try{
			TootRelationShip dst = new TootRelationShip();
			dst.id = Utils.optLongX( src, "id" );
			
			Object ov = src.opt( "following" );
			if( ov instanceof JSONObject ){
				// https://github.com/tootsuite/mastodon/issues/5856
				// 一部の開発版ではこうなっていた
				
				dst.following = true;
				
				ov = ( (JSONObject) ov ).opt( "reblogs" );
				if( ov instanceof Boolean ){
					dst.showing_reblogs = (Boolean) ov ? UserRelation.REBLOG_SHOW : UserRelation.REBLOG_HIDE;
				}else{
					dst.showing_reblogs = UserRelation.REBLOG_UNKNOWN;
				}
				
			}else{
				// 2.0 までの挙動
				dst.following = ( ov instanceof Boolean ? (Boolean) ov : false );
				
				// 2.1 の挙動
				ov = src.opt( "showing_reblogs" );
				if( dst.following && ov instanceof Boolean ){
					dst.showing_reblogs = (Boolean) ov ? UserRelation.REBLOG_SHOW : UserRelation.REBLOG_HIDE;
				}else{
					dst.showing_reblogs = UserRelation.REBLOG_UNKNOWN;
				}
			}
			
			dst.followed_by = src.optBoolean( "followed_by" );
			dst.blocking = src.optBoolean( "blocking" );
			dst.muting = src.optBoolean( "muting" );
			dst.requested = src.optBoolean( "requested" );
			return dst;
		}catch( Throwable ex ){
			log.trace( ex );
			log.e( ex, "TootRelationShip.parse failed." );
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
	
	@NonNull public static List parseList( JSONArray array ){
		List result = new List();
		if( array != null ){
			int array_size = array.length();
			result.ensureCapacity( array_size );
			for( int i = 0 ; i < array_size ; ++ i ){
				JSONObject src = array.optJSONObject( i );
				if( src == null ) continue;
				TootRelationShip item = parse( src );
				if( item != null ) result.add( item );
			}
		}
		return result;
	}
}
