package jp.juggler.subwaytooter.api.entity;

import android.content.Context;
import android.support.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

import jp.juggler.subwaytooter.util.LinkClickContext;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class TootNotification extends TootId {
	
	//	The notification ID
	//TootId public long id;
	
	//	One of: "mention", "reblog", "favourite", "follow"
	public String type;
	
	public static final String TYPE_MENTION = "mention";
	public static final String TYPE_REBLOG = "reblog";
	public static final String TYPE_FAVOURITE = "favourite";
	public static final String TYPE_FOLLOW = "follow";
	
	//	The time the notification was created
	public String created_at;
	
	//	The Account sending the notification to the user
	public TootAccount account;
	
	//	The Status associated with the notification, if applicable
	public TootStatus status;
	
	public long time_created_at;
	
	public JSONObject json;
	
	public static TootNotification parse( @NonNull Context context, LogCategory log, LinkClickContext lcc, String status_host, JSONObject src ){
		if( src == null ) return null;
		try{
			TootNotification dst = new TootNotification();
			dst.json = src;
			dst.id = src.optLong( "id" );
			dst.type = Utils.optStringX( src, "type" );
			dst.created_at = Utils.optStringX( src, "created_at" );
			dst.account = TootAccount.parse( context, log, lcc, src.optJSONObject( "account" ) );
			dst.status = TootStatus.parse( context, log, lcc, status_host, src.optJSONObject( "status" ) );
			
			dst.time_created_at = TootStatus.parseTime( log, dst.created_at );
			
			return dst;
		}catch( Throwable ex ){
			ex.printStackTrace();
			log.e( ex, "TootNotification.parse failed." );
			return null;
		}
	}
	
	public static class List extends ArrayList< TootNotification > {
		public List(){
			super();
		}
		
		public List( int capacity ){
			super( capacity );
		}
	}
	
	@NonNull
	public static List parseList( @NonNull Context context, LogCategory log, LinkClickContext lcc, String status_host, JSONArray array ){
		List result = new List();
		if( array != null ){
			int array_size = array.length();
			result.ensureCapacity( array_size );
			for( int i = 0 ; i < array_size ; ++ i ){
				JSONObject src = array.optJSONObject( i );
				if( src == null ) continue;
				TootNotification item = parse( context, log, lcc, status_host, src );
				if( item != null ) result.add( item );
			}
		}
		return result;
	}
}
