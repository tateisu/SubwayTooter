package jp.juggler.subwaytooter.api.entity;

import android.text.Spannable;
import android.text.TextUtils;

import jp.juggler.subwaytooter.util.Emojione;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

import jp.juggler.subwaytooter.util.HTMLDecoder;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class TootAccount {
	
	public static class List extends ArrayList< TootAccount > {
		
	}
	
	//	The ID of the account
	public long id;
	
	// 	The username of the account
	public String username;
	
	//	Equals username for local users, includes @domain for remote ones
	public String acct;
	
	//	The account's display name
	public CharSequence display_name;
	
	//Boolean for when the account cannot be followed without waiting for approval first
	public boolean locked;
	
	//	The time the account was created
	// ex: "2017-04-13T11:06:08.289Z"
	public String created_at;
	
	//	The number of followers for the account
	public long followers_count;
	
	//The number of accounts the given account is following
	public long following_count;
	
	//	The number of statuses the account has made
	public long statuses_count;
	
	// Biography of user
	// 説明文。改行は\r\n。リンクなどはHTMLタグで書かれている
	public Spannable note;
	
	//URL of the user's profile page (can be remote)
	// https://mastodon.juggler.jp/@tateisu
	public String url;
	
	//	URL to the avatar image
	public String avatar;
	
	//	URL to the avatar static image (gif)
	public String avatar_static;
	
	//URL to the header image
	public String header;
	
	//	URL to the header static image (gif)
	public String header_static;
	
	public long time_created_at;
	
	public static TootAccount parse( LogCategory log, JSONObject src, TootAccount dst ){
		if( src == null ) return null;
		try{
			dst.id = src.optLong( "id" );
			dst.username = Utils.optStringX( src, "username" );
			dst.acct = Utils.optStringX( src, "acct" );
			
			String sv = Utils.optStringX( src, "display_name" );
			if( TextUtils.isEmpty( sv ) ){
				dst.display_name = dst.username;
			}else{
				dst.display_name =  Emojione.decodeEmoji( HTMLDecoder.decodeEntity(sv ) );
			}
			
			dst.locked = src.optBoolean( "locked" );
			dst.created_at = Utils.optStringX( src, "created_at" );
			dst.followers_count = src.optLong( "followers_count" );
			dst.following_count = src.optLong( "following_count" );
			dst.statuses_count = src.optLong( "statuses_count" );
			dst.note = HTMLDecoder.decodeHTML( Utils.optStringX( src, "note" ) );
			dst.url = Utils.optStringX( src, "url" );
			dst.avatar = Utils.optStringX( src, "avatar" ); // "https:\/\/mastodon.juggler.jp\/system\/accounts\/avatars\/000\/000\/148\/original\/0a468974fac5a448.PNG?1492081886",
			dst.avatar_static = Utils.optStringX( src, "avatar_static" ); // "https:\/\/mastodon.juggler.jp\/system\/accounts\/avatars\/000\/000\/148\/original\/0a468974fac5a448.PNG?1492081886",
			dst.header = Utils.optStringX( src, "header" ); // "https:\/\/mastodon.juggler.jp\/headers\/original\/missing.png"
			dst.header_static = Utils.optStringX( src, "header_static" ); // "https:\/\/mastodon.juggler.jp\/headers\/original\/missing.png"}
			
			dst.time_created_at = TootStatus.parseTime( log, dst.created_at );
			
			return dst;
			
		}catch( Throwable ex ){
			ex.printStackTrace();
			log.e( ex, "TootAccount.parse failed." );
			return null;
		}
	}
	
	public static TootAccount parse( LogCategory log, JSONObject src ){
		return parse( log, src, new TootAccount() );
	}
	
	public static List parseList( LogCategory log, JSONArray array ){
		List result = new List();
		if( array != null ){
			for( int i = array.length() - 1 ; i >= 0 ; -- i ){
				JSONObject src = array.optJSONObject( i );
				if( src == null ) continue;
				TootAccount item = parse( log, src );
				if( item != null ) result.add( 0, item );
			}
		}
		return result;
	}
	
}
