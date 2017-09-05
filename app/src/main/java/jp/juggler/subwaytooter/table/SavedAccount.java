package jp.juggler.subwaytooter.table;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootInstance;
import jp.juggler.subwaytooter.util.LinkClickContext;
import jp.juggler.subwaytooter.util.LogCategory;

public class SavedAccount extends TootAccount implements LinkClickContext {
	private static final LogCategory log = new LogCategory( "SavedAccount" );
	
	public static final String table = "access_info";
	
	private static final String COL_ID = BaseColumns._ID;
	private static final String COL_HOST = "h";
	private static final String COL_USER = "u";
	private static final String COL_ACCOUNT = "a";
	private static final String COL_TOKEN = "t";
	
	private static final String COL_VISIBILITY = "visibility";
	private static final String COL_CONFIRM_BOOST = "confirm_boost";
	private static final String COL_DONT_HIDE_NSFW = "dont_hide_nsfw";
	// スキーマ2から
	private static final String COL_NOTIFICATION_MENTION = "notification_mention";
	private static final String COL_NOTIFICATION_BOOST = "notification_boost";
	private static final String COL_NOTIFICATION_FAVOURITE = "notification_favourite";
	private static final String COL_NOTIFICATION_FOLLOW = "notification_follow";
	// スキーマ10から
	private static final String COL_CONFIRM_FOLLOW = "confirm_follow";
	private static final String COL_CONFIRM_FOLLOW_LOCKED = "confirm_follow_locked";
	private static final String COL_CONFIRM_UNFOLLOW = "confirm_unfollow";
	private static final String COL_CONFIRM_POST = "confirm_post";
	
	// スキーマ13から
	public static final String COL_NOTIFICATION_TAG = "notification_server";
	
	// スキーマ14から
	public static final String COL_REGISTER_KEY = "register_key";
	public static final String COL_REGISTER_TIME = "register_time";
	
	// スキーマ16から
	private static final String COL_SOUND_URI = "sound_uri";
	
	public static final long INVALID_ID = - 1L;
	
	// login information
	public long db_id = INVALID_ID;
	public String host;
	public String acct; // user@host
	public JSONObject token_info;
	public String visibility;
	public boolean confirm_boost;
	public boolean dont_hide_nsfw;
	public boolean notification_mention;
	public boolean notification_boost;
	public boolean notification_favourite;
	public boolean notification_follow;
	@NonNull public String sound_uri = "";
	
	public boolean confirm_follow;
	public boolean confirm_follow_locked;
	public boolean confirm_unfollow;
	public boolean confirm_post;
	
	public String notification_tag;
	public String register_key;
	public long register_time;
	
	
	private final AtomicReference<TootInstance> refInstance = new AtomicReference<>( null );
	private static final long INSTANCE_INFORMATION_EXPIRE = 60000L * 5;

	// DBには保存しない
	public @Nullable TootInstance getInstance(){
		TootInstance instance = refInstance.get();
		if( instance != null && System.currentTimeMillis() - instance.time_parse > INSTANCE_INFORMATION_EXPIRE ){
			return null;
		}
		return instance;
	}
	public void setInstance(@NonNull TootInstance instance){
		if( instance != null ) refInstance.set(instance);
	}
	
	
	// アプリデータのインポート時に呼ばれる
	public static void onDBDelete( SQLiteDatabase db ){
		try{
			db.execSQL( "drop table if exists " + table );
		}catch( Throwable ex ){
			log.trace( ex );
		}
	}
	
	public static void onDBCreate( SQLiteDatabase db ){
		db.execSQL(
			"create table if not exists " + table
				+ "(_id INTEGER PRIMARY KEY"
				+ ",u text not null"
				+ ",h text not null"
				+ ",a text not null"
				+ ",t text not null"
				+ ",visibility text"
				+ ",confirm_boost integer default 1"
				+ ",dont_hide_nsfw integer default 0"
				
				// 以下はDBスキーマ2で追加
				+ ",notification_mention integer default 1"
				+ ",notification_boost integer default 1"
				+ ",notification_favourite integer default 1"
				+ ",notification_follow integer default 1"
				
				// 以下はDBスキーマ10で更新
				+ "," + COL_CONFIRM_FOLLOW + " integer default 1"
				+ "," + COL_CONFIRM_FOLLOW_LOCKED + " integer default 1"
				+ "," + COL_CONFIRM_UNFOLLOW + " integer default 1"
				+ "," + COL_CONFIRM_POST + " integer default 1"
				
				// 以下はDBスキーマ13で更新
				+ "," + COL_NOTIFICATION_TAG + " text default ''"
				
				// 以下はDBスキーマ14で更新
				+ "," + COL_REGISTER_KEY + " text default ''"
				+ "," + COL_REGISTER_TIME + " integer default 0"
				
				// 以下はDBスキーマ16で更新
				+ "," + COL_SOUND_URI + " text default ''"
				
				+ ")"
		);
		db.execSQL( "create index if not exists " + table + "_user on " + table + "(u)" );
		db.execSQL( "create index if not exists " + table + "_host on " + table + "(h,u)" );
	}
	
	public static void onDBUpgrade( SQLiteDatabase db, int oldVersion, int newVersion ){
		if( oldVersion < 2 && newVersion >= 2 ){
			try{
				db.execSQL( "alter table " + table + " add column notification_mention integer default 1" );
			}catch( Throwable ex ){
				log.trace( ex );
			}
			try{
				db.execSQL( "alter table " + table + " add column notification_boost integer default 1" );
			}catch( Throwable ex ){
				log.trace( ex );
			}
			try{
				db.execSQL( "alter table " + table + " add column notification_favourite integer default 1" );
			}catch( Throwable ex ){
				log.trace( ex );
			}
			try{
				db.execSQL( "alter table " + table + " add column notification_follow integer default 1" );
			}catch( Throwable ex ){
				log.trace( ex );
			}
		}
		if( oldVersion < 10 && newVersion >= 10 ){
			try{
				db.execSQL( "alter table " + table + " add column " + COL_CONFIRM_FOLLOW + " integer default 1" );
			}catch( Throwable ex ){
				log.trace( ex );
			}
			try{
				db.execSQL( "alter table " + table + " add column " + COL_CONFIRM_FOLLOW_LOCKED + " integer default 1" );
			}catch( Throwable ex ){
				log.trace( ex );
			}
			try{
				db.execSQL( "alter table " + table + " add column " + COL_CONFIRM_UNFOLLOW + " integer default 1" );
			}catch( Throwable ex ){
				log.trace( ex );
			}
			try{
				db.execSQL( "alter table " + table + " add column " + COL_CONFIRM_POST + " integer default 1" );
			}catch( Throwable ex ){
				log.trace( ex );
			}
		}
		if( oldVersion < 13 && newVersion >= 13 ){
			try{
				db.execSQL( "alter table " + table + " add column " + COL_NOTIFICATION_TAG + " text default ''" );
			}catch( Throwable ex ){
				log.trace( ex );
			}
		}
		if( oldVersion < 14 && newVersion >= 14 ){
			try{
				db.execSQL( "alter table " + table + " add column " + COL_REGISTER_KEY + " text default ''" );
			}catch( Throwable ex ){
				log.trace( ex );
			}
			try{
				db.execSQL( "alter table " + table + " add column " + COL_REGISTER_TIME + " integer default 0" );
			}catch( Throwable ex ){
				log.trace( ex );
			}
		}
		if( oldVersion < 16 && newVersion >= 16 ){
			try{
				db.execSQL( "alter table " + table + " add column " + COL_SOUND_URI + " text default ''" );
			}catch( Throwable ex ){
				log.trace( ex );
			}
		}
	}
	
	private SavedAccount(){
	}
	
	// 横断検索用の、何とも紐ついていないアカウント
	// 保存しない。
	private static SavedAccount na_account;
	
	public static SavedAccount getNA(){
		if( na_account == null ){
			SavedAccount dst = new SavedAccount();
			dst.db_id = - 1L;
			dst.username = "?";
			dst.acct = "?@?";
			dst.host = "?";
			dst.notification_follow = false;
			dst.notification_favourite = false;
			dst.notification_boost = false;
			dst.notification_mention = false;
			na_account = dst;
		}
		return na_account;
	}
	
	public boolean isNA(){
		return acct.equals( "?@?" );
	}
	
	private static SavedAccount parse( Context context, Cursor cursor ) throws JSONException{
		JSONObject src = new JSONObject( cursor.getString( cursor.getColumnIndex( COL_ACCOUNT ) ) );
		SavedAccount dst = new SavedAccount();
		dst = (SavedAccount) parse( context, dst, src, dst );
		if( dst != null ){
			dst.db_id = cursor.getLong( cursor.getColumnIndex( COL_ID ) );
			dst.host = cursor.getString( cursor.getColumnIndex( COL_HOST ) ).toLowerCase();
			dst.acct = cursor.getString( cursor.getColumnIndex( COL_USER ) );
			
			int colIdx_visibility = cursor.getColumnIndex( COL_VISIBILITY );
			dst.visibility = cursor.isNull( colIdx_visibility ) ? null : cursor.getString( colIdx_visibility );
			
			dst.confirm_boost = ( 0 != cursor.getInt( cursor.getColumnIndex( COL_CONFIRM_BOOST ) ) );
			dst.dont_hide_nsfw = ( 0 != cursor.getInt( cursor.getColumnIndex( COL_DONT_HIDE_NSFW ) ) );
			
			dst.notification_mention = ( 0 != cursor.getInt( cursor.getColumnIndex( COL_NOTIFICATION_MENTION ) ) );
			dst.notification_boost = ( 0 != cursor.getInt( cursor.getColumnIndex( COL_NOTIFICATION_BOOST ) ) );
			dst.notification_favourite = ( 0 != cursor.getInt( cursor.getColumnIndex( COL_NOTIFICATION_FAVOURITE ) ) );
			dst.notification_follow = ( 0 != cursor.getInt( cursor.getColumnIndex( COL_NOTIFICATION_FOLLOW ) ) );
			
			dst.confirm_follow = ( 0 != cursor.getInt( cursor.getColumnIndex( COL_CONFIRM_FOLLOW ) ) );
			dst.confirm_follow_locked = ( 0 != cursor.getInt( cursor.getColumnIndex( COL_CONFIRM_FOLLOW_LOCKED ) ) );
			dst.confirm_unfollow = ( 0 != cursor.getInt( cursor.getColumnIndex( COL_CONFIRM_UNFOLLOW ) ) );
			dst.confirm_post = ( 0 != cursor.getInt( cursor.getColumnIndex( COL_CONFIRM_POST ) ) );
			
			int idx_notification_tag = cursor.getColumnIndex( COL_NOTIFICATION_TAG );
			dst.notification_tag = cursor.isNull( idx_notification_tag ) ? null : cursor.getString( idx_notification_tag );
			
			int idx_register_key = cursor.getColumnIndex( COL_REGISTER_KEY );
			dst.register_key = cursor.isNull( idx_register_key ) ? null : cursor.getString( idx_register_key );
			
			dst.register_time = cursor.getLong( cursor.getColumnIndex( COL_REGISTER_TIME ) );
			
			dst.token_info = new JSONObject( cursor.getString( cursor.getColumnIndex( COL_TOKEN ) ) );
			
			dst.sound_uri = cursor.getString( cursor.getColumnIndex( COL_SOUND_URI ) );
		}
		return dst;
	}
	
	public static long insert(
		@NonNull String host,
		@NonNull String acct,
		@NonNull JSONObject account,
		@NonNull JSONObject token
	){
		try{
			ContentValues cv = new ContentValues();
			cv.put( COL_HOST, host );
			cv.put( COL_USER, acct );
			cv.put( COL_ACCOUNT, account.toString() );
			cv.put( COL_TOKEN, token.toString() );
			return App1.getDB().insert( table, null, cv );
		}catch( Throwable ex ){
			log.trace( ex );
			throw new RuntimeException( "SavedAccount.insert failed.", ex );
		}
	}
	
	public void delete(){
		try{
			App1.getDB().delete( table, COL_ID + "=?", new String[]{ Long.toString( db_id ) } );
		}catch( Throwable ex ){
			log.trace( ex );
			throw new RuntimeException( "SavedAccount.delete failed.", ex );
		}
	}
	
	public void updateTokenInfo( @NonNull JSONObject token_info ){
		if( db_id == INVALID_ID )
			throw new RuntimeException( "SavedAccount.updateTokenInfo missing db_id" );
		this.token_info = token_info;
		ContentValues cv = new ContentValues();
		cv.put( COL_TOKEN, token_info.toString() );
		App1.getDB().update( table, cv, COL_ID + "=?", new String[]{ Long.toString( db_id ) } );
	}
	
	public void saveSetting(){
		if( db_id == INVALID_ID )
			throw new RuntimeException( "SavedAccount.saveSetting missing db_id" );
		ContentValues cv = new ContentValues();
		cv.put( COL_VISIBILITY, visibility );
		cv.put( COL_CONFIRM_BOOST, confirm_boost ? 1 : 0 );
		cv.put( COL_DONT_HIDE_NSFW, dont_hide_nsfw ? 1 : 0 );
		cv.put( COL_NOTIFICATION_MENTION, notification_mention ? 1 : 0 );
		cv.put( COL_NOTIFICATION_BOOST, notification_boost ? 1 : 0 );
		cv.put( COL_NOTIFICATION_FAVOURITE, notification_favourite ? 1 : 0 );
		cv.put( COL_NOTIFICATION_FOLLOW, notification_follow ? 1 : 0 );
		
		cv.put( COL_CONFIRM_FOLLOW, confirm_follow ? 1 : 0 );
		cv.put( COL_CONFIRM_FOLLOW_LOCKED, confirm_follow_locked ? 1 : 0 );
		cv.put( COL_CONFIRM_UNFOLLOW, confirm_unfollow ? 1 : 0 );
		cv.put( COL_CONFIRM_POST, confirm_post ? 1 : 0 );
		
		cv.put( COL_SOUND_URI, sound_uri );
		
		// UIからは更新しない
		// notification_tag
		// register_key
		
		App1.getDB().update( table, cv, COL_ID + "=?", new String[]{ Long.toString( db_id ) } );
	}
	
	public void saveNotificationTag(){
		if( db_id == INVALID_ID )
			throw new RuntimeException( "SavedAccount.saveNotificationTag missing db_id" );
		
		ContentValues cv = new ContentValues();
		cv.put( COL_NOTIFICATION_TAG, notification_tag );
		
		App1.getDB().update( table, cv, COL_ID + "=?", new String[]{ Long.toString( db_id ) } );
	}
	
	public void saveRegisterKey(){
		if( db_id == INVALID_ID )
			throw new RuntimeException( "SavedAccount.saveRegisterKey missing db_id" );
		
		ContentValues cv = new ContentValues();
		cv.put( COL_REGISTER_KEY, register_key );
		cv.put( COL_REGISTER_TIME, register_time );
		
		App1.getDB().update( table, cv, COL_ID + "=?", new String[]{ Long.toString( db_id ) } );
	}
	
	public static final String REGISTER_KEY_UNREGISTERED = "unregistered";
	
	public static void clearRegistrationCache(){
		ContentValues cv = new ContentValues();
		cv.put( COL_REGISTER_KEY, REGISTER_KEY_UNREGISTERED );
		cv.put( COL_REGISTER_TIME, 0L );
		App1.getDB().update( table, cv, null, null );
	}
	
	// onResumeの時に設定を読み直す
	public void reloadSetting( Context context ){
		if( db_id == INVALID_ID )
			throw new RuntimeException( "SavedAccount.reloadSetting missing db_id" );
		SavedAccount b = loadAccount( context, log, db_id );
		if( b == null ) return; // DBから削除されてる？
		this.visibility = b.visibility;
		this.confirm_boost = b.confirm_boost;
		this.dont_hide_nsfw = b.dont_hide_nsfw;
		this.token_info = b.token_info;
		this.notification_mention = b.notification_follow;
		this.notification_boost = b.notification_boost;
		this.notification_favourite = b.notification_favourite;
		this.notification_follow = b.notification_follow;
		this.notification_tag = b.notification_tag;
		
		this.sound_uri = b.sound_uri;
	}
	
	public static @Nullable
	SavedAccount loadAccount( @NonNull Context context, @NonNull LogCategory log, long db_id ){
		try{
			Cursor cursor = App1.getDB().query( table, null, COL_ID + "=?", new String[]{ Long.toString( db_id ) }, null, null, null );
			try{
				if( cursor.moveToFirst() ){
					return parse( context, cursor );
				}
			}finally{
				cursor.close();
			}
		}catch( Throwable ex ){
			log.trace( ex );
			log.e( ex, "loadAccount failed." );
		}
		return null;
	}
	
	public static @NonNull
	ArrayList< SavedAccount > loadAccountList( Context context, @NonNull LogCategory log ){
		ArrayList< SavedAccount > result = new ArrayList<>();
		try{
			Cursor cursor = App1.getDB().query( table, null, null, null, null, null, null );
			try{
				while( cursor.moveToNext() ){
					result.add( parse( context, cursor ) );
				}
			}finally{
				cursor.close();
			}
		}catch( Throwable ex ){
			log.trace( ex );
			log.e( ex, "loadAccountList failed." );
			throw new RuntimeException( "SavedAccount.loadAccountList failed.", ex );
		}
		return result;
	}
	
	public static @NonNull
	ArrayList< SavedAccount > loadByTag( Context context, @NonNull LogCategory log, String tag ){
		ArrayList< SavedAccount > result = new ArrayList<>();
		try{
			Cursor cursor = App1.getDB().query( table, null, COL_NOTIFICATION_TAG + "=?", new String[]{ tag }, null, null, null );
			try{
				while( cursor.moveToNext() ){
					result.add( parse( context, cursor ) );
				}
			}finally{
				cursor.close();
			}
		}catch( Throwable ex ){
			log.trace( ex );
			log.e( ex, "loadByTag failed." );
			throw new RuntimeException( "SavedAccount.loadByTag failed.", ex );
		}
		return result;
	}
	
	@SuppressWarnings("WeakerAccess")
	public @NonNull String getAccountHost( @Nullable String acct ){
		if( acct != null ){
			int pos = acct.indexOf( '@' );
			if( pos != - 1 ) return acct.substring( pos + 1 );
		}
		return this.host;
	}
	
	public @NonNull String getAccountHost( @Nullable TootAccount who ){
		if( who != null ) return getAccountHost( who.acct );
		return this.host;
	}
	
	public @NonNull String getFullAcct( @NonNull String acct ){
		return acct.indexOf( '@' ) != - 1 ? acct : acct + "@" + this.host;
	}
	
	public String getFullAcct( @Nullable TootAccount who ){
		if( who != null && who.acct != null ){
			return getFullAcct( who.acct );
		}
		return "?@?";
	}
	
	@SuppressWarnings("WeakerAccess")
	public boolean isLocalUser( @NonNull TootAccount who ){
		return isLocalUser( who.acct );
	}
	
	@SuppressWarnings("WeakerAccess")
	public boolean isLocalUser( @NonNull String acct ){
		int pos = acct.indexOf( '@' );
		return pos == - 1 || host.equalsIgnoreCase( acct.substring( pos + 1 ) );
	}
	
	public boolean isRemoteUser( @NonNull TootAccount who ){
		return ! isLocalUser( who );
	}
	
	@SuppressWarnings("unused")
	public boolean isRemoteUser( @NonNull String acct ){
		return ! isLocalUser( acct );
	}
	
	public String getUserUrl( @NonNull String who_acct ){
		int p = who_acct.indexOf( '@' );
		if( - 1 != p ){
			return "https://" + who_acct.substring( p + 1 ) + "/@" + who_acct.substring( 0, p );
		}else{
			return "https://" + host + "/@" + who_acct;
		}
	}
	
	public boolean isMe( @Nullable TootAccount who ){
		if( who == null ) return false;
		
		int pos = this.acct.indexOf( '@' );
		String this_user = this.acct.substring( 0, pos );
		//
		if( ! this_user.equals( who.username ) ) return false;
		//
		pos = who.acct.indexOf( '@' );
		return pos == - 1 || this.host.equalsIgnoreCase( who.acct.substring( pos + 1 ) );
	}
	
	public boolean isMe( @NonNull String who_acct ){
		int pos = this.acct.indexOf( '@' );
		String this_user = this.acct.substring( 0, pos );
		//
		pos = who_acct.indexOf( '@' );
		if( pos == - 1 ) return this_user.equals( who_acct );
		//
		return this_user.equals( who_acct.substring( 0, pos ) )
			&& this.host.equalsIgnoreCase( who_acct.substring( pos + 1 ) );
	}
	
	public String supplyBaseUrl( String url ){
		if( TextUtils.isEmpty( url ) ) return url;
		if( url.charAt( 0 ) == '/' ) return "https://" + host + url;
		return url;
	}
	
	public boolean isPseudo(){
		return "?".equals( username );
	}
	
	private static final Pattern reAcctUrl = Pattern.compile( "\\Ahttps://([A-Za-z0-9.-]+)/@([A-Za-z0-9_]+)\\z" );
	
	@Override public AcctColor findAcctColor( String url ){
		Matcher m = reAcctUrl.matcher( url );
		if( m.find() ) return AcctColor.load( m.group( 2 ) + "@" + m.group( 1 ) );
		return null;
	}
	
	public static long getCount(){
		try{
			Cursor cursor = App1.getDB().query( table, new String[]{ "count(*)" }, null, null, null, null, null );
			try{
				if( cursor.moveToNext() ){
					return cursor.getLong( 0 );
				}
			}finally{
				cursor.close();
			}
		}catch( Throwable ex ){
			log.trace( ex );
			log.e( ex, "getCount failed." );
			throw new RuntimeException( "SavedAccount.getCount failed.", ex );
		}
		return 0L;
	}
	
	private static final String strNicoruHost = "friends.nico";
	private static final Pattern reAtNicoruHost = Pattern.compile( "@friends\\.nico\\z", Pattern.CASE_INSENSITIVE );
	
	public static boolean isNicoru( String acct ){
		return acct != null && reAtNicoruHost.matcher( acct ).find();
	}
	
	public boolean isNicoru( TootAccount account ){
		String host = this.host;
		int host_start = 0;
		if( account != null && account.acct != null ){
			int pos = account.acct.indexOf( '@' );
			if( pos != - 1 ){
				host = account.acct;
				host_start = pos + 1;
			}
		}
		return host_match( strNicoruHost, 0, host, host_start );
	}
	
	private static int charAtLower( @NonNull final CharSequence src, final int pos ){
		final int c = src.charAt( pos );
		return ( c >= 'a' && c <= 'z' ? c - ( 'a' - 'A' ) : c );
	}
	
	private static boolean host_match( @NonNull final CharSequence a, int a_start, @NonNull final CharSequence b, int b_start ){
		
		final int a_end = a.length();
		final int b_end = b.length();
		
		int a_remain = a_end - a_start;
		final int b_remain = b_end - b_start;
		
		// 文字数が違う
		if( a_remain != b_remain ) return false;
		
		// 文字数がゼロ
		if( a_remain <= 0 ) return true;
		
		// 末尾の文字が違う
		if( charAtLower( a, a_end - 1 ) != charAtLower( b, b_end - 1 ) ) return false;
		
		// 先頭からチェック
		while( a_remain-- > 0 ){
			if( charAtLower( a, a_start++ ) != charAtLower( b, b_start++ ) ) return false;
		}
		
		return true;
	}
	
}
