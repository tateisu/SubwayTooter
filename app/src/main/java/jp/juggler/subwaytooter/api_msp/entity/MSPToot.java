package jp.juggler.subwaytooter.api_msp.entity;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootStatusLike;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.HTMLDecoder;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class MSPToot extends TootStatusLike {
	
	public static class List extends ArrayList<MSPToot> {
		
	}
	
	private static final Pattern reAccountUrl = Pattern.compile("\\Ahttps://([^/#?]+)/@([^/#?]+)\\z");
	
	
	private static TootAccount parseAccount( LogCategory log, SavedAccount access_info, JSONObject src ){
		
		if( src == null ) return null;
		
		TootAccount dst = new TootAccount();
		dst.url = Utils.optStringX( src, "url" );
		dst.username = Utils.optStringX( src, "username" );
		dst.avatar = dst.avatar_static = Utils.optStringX( src, "avatar" );
		
		String sv = Utils.optStringX( src, "display_name" );
		if( TextUtils.isEmpty( sv ) ){
			dst.display_name = dst.username;
		}else{
			dst.display_name = TootAccount.filterDisplayName( sv );
		}

		dst.id = src.optLong( "id" );
		dst.note = HTMLDecoder.decodeHTML( access_info, Utils.optStringX( src, "note" ), true, null );
		
		if( TextUtils.isEmpty( dst.url ) ){
			log.e( "parseAccount: missing url" );
			return null;
		}
		Matcher m = reAccountUrl.matcher( dst.url );
		if( ! m.find() ){
			log.e( "parseAccount: not account url: %s", dst.url );
			return null;
		}else{
			dst.acct = dst.username + "@" + m.group( 1 );
		}
		
		return dst;
	}
	
//	private static final Pattern reTime = Pattern.compile( "\\A(\\d+)\\D+(\\d+)\\D+(\\d+)\\D+(\\d+)\\D+(\\d+)\\D+(\\d+)" );
//
//	private static final TimeZone tz_tokyo = TimeZone.getTimeZone( "Asia/Tokyo" );
//
//	private static long parseMSPTime( LogCategory log, String strTime ){
//		if( ! TextUtils.isEmpty( strTime ) ){
//			try{
//				Matcher m = reTime.matcher( strTime );
//				if( ! m.find() ){
//					log.d( "!!invalid time format: %s", strTime );
//				}else{
//					GregorianCalendar g = new GregorianCalendar( tz_tokyo );
//					g.set(
//						Utils.parse_int( m.group( 1 ), 1 ),
//						Utils.parse_int( m.group( 2 ), 1 ) - 1,
//						Utils.parse_int( m.group( 3 ), 1 ),
//						Utils.parse_int( m.group( 4 ), 0 ),
//						Utils.parse_int( m.group( 5 ), 0 ),
//						Utils.parse_int( m.group( 6 ), 0 )
//					);
//					g.set( Calendar.MILLISECOND,  0 );
//					return g.getTimeInMillis();
//				}
//			}catch( Throwable ex ){// ParseException,  ArrayIndexOutOfBoundsException
//				ex.printStackTrace();
//				log.e( ex, "parseMSPTime failed. src=%s", strTime );
//			}
//		}
//		return 0L;
//	}
	
	public String created_at;
	public ArrayList<String> media_attachments;
	public long msp_id;
	
	private static MSPToot parse( LogCategory log, SavedAccount access_info,JSONObject src ){
		if( src == null ) return null;
		MSPToot dst = new MSPToot();

		dst.account =parseAccount( log, access_info, src.optJSONObject( "account" ));
		if( dst.account == null ){
			log.e("missing status account");
			return null;
		}
		
		dst.url = Utils.optStringX( src, "url" );
		dst.status_host = dst.account.getAcctHost();
		dst.id =  src.optLong( "id" ,-1L );
		
		if( TextUtils.isEmpty( dst.url ) || TextUtils.isEmpty( dst.status_host ) || dst.id == -1L ){
			log.e("missing status url or host or id");
			return null;
		}

		dst.created_at = Utils.optStringX( src, "created_at" );

		JSONArray a = src.optJSONArray( "media_attachments" );
		if( a != null && a.length() > 0 ){
			dst.media_attachments = new ArrayList<>();
			for(int i=0,ie=a.length();i<ie;++i){
				String sv = Utils.optStringX( a,i );
				if(!TextUtils.isEmpty( sv )){
					dst.media_attachments.add( sv );
				}
			}
		}

		dst.msp_id = src.optLong("msp_id");
		dst.sensitive = (src.optInt( "sensitive" ,0) != 0);
		
		dst.spoiler_text = Utils.optStringX( src, "spoiler_text" );
		if( ! TextUtils.isEmpty( dst.spoiler_text ) ){
			dst.decoded_spoiler_text = HTMLDecoder.decodeHTML( access_info, dst.spoiler_text ,true,null);
		}
		
		dst.content = Utils.optStringX( src, "content" );
		dst.decoded_content = HTMLDecoder.decodeHTML( access_info, dst.content ,true,null );
		
		return dst;
	}
	
	
	public static List parseList(  LogCategory log, SavedAccount access_info,JSONArray array ){
		List list = new List();
		for(int i=0,ie=array.length();i<ie;++i){
			JSONObject src = array.optJSONObject( i );
			if( src == null ) continue;
			MSPToot item = parse( log, access_info,src );
			if( item == null ) continue;
			list.add( item );
		}
		return list;
	}
	


	
}
