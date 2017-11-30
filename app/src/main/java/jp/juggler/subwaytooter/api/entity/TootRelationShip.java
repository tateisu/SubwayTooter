package jp.juggler.subwaytooter.api.entity;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class TootRelationShip {
	
	private static final LogCategory log = new LogCategory( "TootRelationShip" );
	
	//	Target account id
	public long id;
	
	//	Whether the user is currently following the account
	private  boolean following;
	
	//	Whether the user is currently being followed by the account
	public boolean followed_by;
	
	//	Whether the user is currently blocking the account
	public boolean blocking;
	
	//	Whether the user is currently muting the account
	public boolean muting;
	
	//	Whether the user has requested to follow the account
	private boolean requested;
	
	// 認証ユーザからのフォロー状態
	public boolean getFollowing(@NonNull TootAccount who){
		//noinspection SimplifiableIfStatement
		if( requested && ! following && ! who.locked ){
			return true;
		}
		return following;
	}
	
	public boolean _getRealFollowing(){
		return following;
	}
	
	// 認証ユーザからのフォローリクエスト申請中状態
	public boolean getRequested(@NonNull TootAccount who){
		//noinspection SimplifiableIfStatement
		if( requested && ! following && ! who.locked ){
			return false;
		}
		return requested;
	}
	
	public boolean _getRealRequested(){
		return requested;
	}
	
	@Nullable
	public static TootRelationShip parse( JSONObject src ){
		if( src == null ) return null;
		try{
			TootRelationShip dst = new TootRelationShip();
			dst.id = Utils.optLongX(src, "id" );
			
			Object ov = src.opt("following");
			if( ov instanceof Boolean ){
				// 2.0 まで : following はboolean
				dst.following = (Boolean) ov;
			}else if( ov instanceof JSONObject ){
				// https://github.com/tootsuite/mastodon/issues/5856
				// issueに上げられたコミット： object ?
				dst.following = true;

				// TODO: 2.1リリース後に reblogs: true , false に対応する
			}else{
				dst.following = false;
				log.d("'following' is not boolean ,not object.");
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
