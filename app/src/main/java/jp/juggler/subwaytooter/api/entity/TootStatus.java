package jp.juggler.subwaytooter.api.entity;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.Pref;
import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.DecodeOptions;
import jp.juggler.subwaytooter.util.HTMLDecoder;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;
import jp.juggler.subwaytooter.util.WordTrieTree;

public class TootStatus extends TootStatusLike {
	
	private static final LogCategory log = new LogCategory( "TootStatus" );
	
	public static class List extends ArrayList< TootStatus > {
		
		public List(){
			super();
		}
		
		public List( int capacity ){
			super( capacity );
		}
	}
	
	//	The ID of the status
	// TootId public long id;
	
	// A Fediverse-unique resource ID
	public String uri;
	
	//	null or the ID of the status it replies to
	public String in_reply_to_id;
	
	//	null or the ID of the account it replies to
	public String in_reply_to_account_id;
	
	//	null or the reblogged Status
	public TootStatus reblog;
	
	//	The time the status was created
	private String created_at;
	
	//One of: public, unlisted, private, direct
	public String visibility;
	public static final String VISIBILITY_PUBLIC = "public";
	public static final String VISIBILITY_UNLISTED = "unlisted";
	public static final String VISIBILITY_PRIVATE = "private";
	public static final String VISIBILITY_DIRECT = "direct";
	public static final String VISIBILITY_WEB_SETTING = "web_setting";
	
	//	An array of Attachments
	@Nullable public TootAttachment.List media_attachments;
	
	//	An array of Mentions
	public TootMention.List mentions;
	
	//An array of Tags
	public TootTag.List tags;
	
	// public Spannable decoded_tags;
	public Spannable decoded_mentions;
	
	public boolean conversation_main;
	
	public NicoEnquete enquete;
	
	@Nullable
	public static TootStatus parse( @NonNull Context context, @NonNull SavedAccount access_info, JSONObject src ){
		return parse( context,access_info,src,false);
	}

	@Nullable
	public static TootStatus parse( @NonNull Context context, @NonNull SavedAccount access_info, JSONObject src ,boolean bPinned){
		/*
			bPinned 引数がtrueになるのはプロフィールカラムからpinned TL を読んだ時だけである
		*/
		
		if( src == null ) return null;
		//	log.d( "parse: %s", src.toString() );
		
		try{
			TootStatus status = new TootStatus();
			status.json = src;
			
			// 絵文字マップは割と最初の方で読み込んでおきたい
			status.custom_emojis = CustomEmoji.parseMap( src.optJSONArray( "emojis" ),access_info.host);
			status.profile_emojis = NicoProfileEmoji.parseMap( src.optJSONArray( "profile_emojis" ) );

			status.account = TootAccount.parse( context, access_info, src.optJSONObject( "account" ) );
			
			if( status.account == null ) return null;
			
			status.id = Utils.optLongX( src, "id" ); // host_remote の上のID
			status.uri = Utils.optStringX( src, "uri" );
			status.url = Utils.optStringX( src, "url" );
			
			status.host_access = access_info.host;
			status.host_original = status.account.getAcctHost();
			if( status.host_original == null ){
				status.host_original = access_info.host;
			}
			
			status.in_reply_to_id = Utils.optStringX( src, "in_reply_to_id" ); // null
			status.in_reply_to_account_id = Utils.optStringX( src, "in_reply_to_account_id" ); // null
			status.reblog = TootStatus.parse( context, access_info, src.optJSONObject( "reblog" ) ,false  );
			/* Pinned TL を取得した時にreblogが登場することはないので、reblogをパースするときのbPinnedはfalseでよい */
			status.content = Utils.optStringX( src, "content" );
			status.created_at = Utils.optStringX( src, "created_at" ); // "2017-04-16T09:37:14.000Z"
			status.reblogs_count = Utils.optLongX(src,  "reblogs_count" );
			status.favourites_count = Utils.optLongX(src, "favourites_count" );
			status.reblogged = src.optBoolean( "reblogged" );
			status.favourited = src.optBoolean( "favourited" );
			status.sensitive = src.optBoolean( "sensitive" ); // false
			status.visibility = Utils.optStringX( src, "visibility" );
			status.media_attachments = TootAttachment.parseList( src.optJSONArray( "media_attachments" ) );
			status.mentions = TootMention.parseList( src.optJSONArray( "mentions" ) );
			status.tags = TootTag.parseList( src.optJSONArray( "tags" ) );
			status.application = TootApplication.parse( src.optJSONObject( "application" ) ); // null
			
			status.pinned = bPinned || src.optBoolean( "pinned" );
			
			status.setSpoilerText( context, Utils.optStringX( src, "spoiler_text" ) );
			
			status.muted = src.optBoolean( "muted" );
			status.language = Utils.optStringX( src, "language" );
			
			
			status.time_created_at = parseTime( status.created_at );
			status.decoded_content = new DecodeOptions()
				.setShort( true )
				.setDecodeEmoji( true)
				.setAttachment( status.media_attachments )
				.setCustomEmojiMap( status.custom_emojis )
				.setProfileEmojis( status.profile_emojis )
				.setLinkTag( status )
				.decodeHTML( context, access_info, status.content );

			// status.decoded_tags = HTMLDecoder.decodeTags( account,status.tags );
			status.decoded_mentions = HTMLDecoder.decodeMentions( access_info, status.mentions ,status);
			
			status.enquete = NicoEnquete.parse( context,access_info , status.media_attachments , Utils.optStringX( src, "enquete"),status.id,status.time_created_at,status );
			
			
			return status;
		}catch( Throwable ex ){
			log.trace( ex );
			log.e( ex, "TootStatus.parse failed." );
			return null;
		}
	}
	
	@NonNull
	public static List parseList( @NonNull Context context, @NonNull SavedAccount access_info, JSONArray array ){
		return parseList( context,access_info,array,false );
	}

	@NonNull
	public static List parseList( @NonNull Context context, @NonNull SavedAccount access_info, JSONArray array ,boolean bPinned){
		List result = new List();
		if( array != null ){
			int array_size = array.length();
			result.ensureCapacity( array_size );
			for( int i = 0 ; i < array_size ; ++ i ){
				JSONObject src = array.optJSONObject( i );
				if( src == null ) continue;
				TootStatus item = parse( context, access_info, src ,bPinned );
				if( item != null ) result.add( item );
			}
		}
		return result;
	}
	
	private static final Pattern reTime = Pattern.compile( "\\A(\\d+)\\D+(\\d+)\\D+(\\d+)\\D+(\\d+)\\D+(\\d+)\\D+(\\d+)\\D+(\\d+)" );
	
	private static final TimeZone tz_utc = TimeZone.getTimeZone( "UTC" );
	
	public static long parseTime( String strTime ){
		if( ! TextUtils.isEmpty( strTime ) ){
			try{
				Matcher m = reTime.matcher( strTime );
				if( ! m.find() ){
					log.d( "!!invalid time format: %s", strTime );
				}else{
					GregorianCalendar g = new GregorianCalendar( tz_utc );
					g.set(
						Utils.parse_int( m.group( 1 ), 1 ),
						Utils.parse_int( m.group( 2 ), 1 ) - 1,
						Utils.parse_int( m.group( 3 ), 1 ),
						Utils.parse_int( m.group( 4 ), 0 ),
						Utils.parse_int( m.group( 5 ), 0 ),
						Utils.parse_int( m.group( 6 ), 0 )
					);
					g.set( Calendar.MILLISECOND, Utils.parse_int( m.group( 7 ), 0 ) );
					return g.getTimeInMillis();
				}
			}catch( Throwable ex ){// ParseException,  ArrayIndexOutOfBoundsException
				log.trace( ex );
				log.e( ex, "TootStatus.parseTime failed. src=%s", strTime );
			}
		}
		return 0L;
	}
	
	private static final SimpleDateFormat date_format = new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss", Locale.getDefault() );
	
	public static String formatTime( Context context, long t, boolean bAllowRelative ){
		if( bAllowRelative && App1.pref.getBoolean( Pref.KEY_RELATIVE_TIMESTAMP, false ) ){
			long now = System.currentTimeMillis();
			long delta = now - t;
			String sign = context.getString( delta > 0 ? R.string.ago : R.string.later );
			delta = delta >= 0 ? delta : - delta;
			if( delta < 1000L ){
				return context.getString( R.string.time_within_second );
			}else if( delta < 60000L ){
				int v = (int) ( delta / 1000L );
				return context.getString( v > 1 ? R.string.relative_time_second_2 : R.string.relative_time_second_1, v, sign );
			}else if( delta < 3600000L ){
				int v = (int) ( delta / 60000L );
				return context.getString( v > 1 ? R.string.relative_time_minute_2 : R.string.relative_time_minute_1, v, sign );
			}else if( delta < 86400000L ){
				int v = (int) ( delta / 3600000L );
				return context.getString( v > 1 ? R.string.relative_time_hour_2 : R.string.relative_time_hour_1, v, sign );
			}else if( delta < 40 * 86400000L ){
				int v = (int) ( delta / 86400000L );
				return context.getString( v > 1 ? R.string.relative_time_day_2 : R.string.relative_time_day_1, v, sign );
			}
		}
		date_format.setTimeZone( TimeZone.getDefault() );
		return date_format.format( new Date( t ) );
	}
	
	// 公開範囲を比較する
	// 公開範囲が広い => 大きい
	// aの方が小さい（狭い)ならマイナス
	// aの方が大きい（狭い)ならプラス
	// IndexOutOfBoundsException 公開範囲が想定外
	public static int compareVisibility( String a, String b ){
		int ia = compareVisibility_tmp( a );
		int ib = compareVisibility_tmp( b );
		if( ia < ib ) return - 1;
		if( ia > ib ) return 1;
		return 0;
	}
	
	private static int compareVisibility_tmp( String a ){
		if( TootStatus.VISIBILITY_DIRECT.equals( a ) ) return 0;
		if( TootStatus.VISIBILITY_PRIVATE.equals( a ) ) return 1;
		if( TootStatus.VISIBILITY_UNLISTED.equals( a ) ) return 2;
		if( TootStatus.VISIBILITY_PUBLIC.equals( a ) ) return 3;
		if( TootStatus.VISIBILITY_WEB_SETTING.equals( a ) ) return 4;
		throw new IndexOutOfBoundsException( "visibility not in range" );
	}
	
	//	public void updateNickname( SavedAccount access_info ){
	//		decoded_content = HTMLDecoder.decodeHTML( access_info, content );
	//		decoded_mentions = HTMLDecoder.decodeMentions( access_info, mentions );
	//
	//		if( ! TextUtils.isEmpty( spoiler_text ) ){
	//			decoded_spoiler_text = HTMLDecoder.decodeHTML( access_info, spoiler_text );
	//		}
	//	}
	
	public boolean checkMuted( @NonNull HashSet< String > muted_app, @NonNull WordTrieTree muted_word ){
		
		// app mute
		if( application != null ){
			String name = application.name;
			if( name != null ){
				if( muted_app.contains( name ) ){
					return true;
				}
			}
		}
		
		// word mute
		if( decoded_content != null && muted_word.containsWord( decoded_content.toString() ) ){
			return true;
		}
		
		if( decoded_spoiler_text != null && muted_word.containsWord( decoded_spoiler_text.toString() ) ){
			return true;
		}
		
		// reblog
		return reblog != null && reblog.checkMuted( muted_app, muted_word );
		
	}
	
	public boolean hasMedia(){
		return media_attachments != null && media_attachments.size() > 0;
	}
	
	@Override public boolean canPin( SavedAccount access_info ){
		return reblog == null
			&& access_info.isMe( account )
			&& ( VISIBILITY_PUBLIC.equals( visibility ) || VISIBILITY_UNLISTED.equals( visibility ) );
	}
	
}
