package jp.juggler.subwaytooter.table;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.util.LinkClickContext;
import jp.juggler.subwaytooter.util.LogCategory;

public class SavedAccount extends TootAccount implements LinkClickContext {
	private static final LogCategory log = new LogCategory( "SavedAccount" );
	
	private static final String table = "access_info";
	
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
	
	public boolean confirm_follow;
	public boolean confirm_follow_locked;
	public boolean confirm_unfollow;
	public boolean confirm_post;
	
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
				ex.printStackTrace();
			}
			try{
				db.execSQL( "alter table " + table + " add column notification_boost integer default 1" );
			}catch( Throwable ex ){
				ex.printStackTrace();
			}
			try{
				db.execSQL( "alter table " + table + " add column notification_favourite integer default 1" );
			}catch( Throwable ex ){
				ex.printStackTrace();
			}
			try{
				db.execSQL( "alter table " + table + " add column notification_follow integer default 1" );
			}catch( Throwable ex ){
				ex.printStackTrace();
			}
		}
		if( oldVersion < 10 && newVersion >= 10 ){
			try{
				db.execSQL( "alter table " + table + " add column " + COL_CONFIRM_FOLLOW + " integer default 1" );
			}catch( Throwable ex ){
				ex.printStackTrace();
			}
			try{
				db.execSQL( "alter table " + table + " add column " + COL_CONFIRM_FOLLOW_LOCKED + " integer default 1" );
			}catch( Throwable ex ){
				ex.printStackTrace();
			}
			try{
				db.execSQL( "alter table " + table + " add column " + COL_CONFIRM_UNFOLLOW + " integer default 1" );
			}catch( Throwable ex ){
				ex.printStackTrace();
			}
			try{
				db.execSQL( "alter table " + table + " add column " + COL_CONFIRM_POST + " integer default 1" );
			}catch( Throwable ex ){
				ex.printStackTrace();
			}
		}
	}
	
	private SavedAccount(){
	}
	
	private static SavedAccount parse( Cursor cursor ) throws JSONException{
		JSONObject src = new JSONObject( cursor.getString( cursor.getColumnIndex( COL_ACCOUNT ) ) );
		SavedAccount dst = new SavedAccount();
		dst = (SavedAccount) parse( log, dst, src, dst );
		if( dst != null ){
			dst.db_id = cursor.getLong( cursor.getColumnIndex( COL_ID ) );
			dst.host = cursor.getString( cursor.getColumnIndex( COL_HOST ) );
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
			
			dst.token_info = new JSONObject( cursor.getString( cursor.getColumnIndex( COL_TOKEN ) ) );
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
			ex.printStackTrace();
			throw new RuntimeException( "SavedAccount.insert failed.", ex );
		}
	}
	
	public void delete(){
		try{
			App1.getDB().delete( table, COL_ID + "=?", new String[]{ Long.toString( db_id ) } );
		}catch( Throwable ex ){
			ex.printStackTrace();
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
		
		App1.getDB().update( table, cv, COL_ID + "=?", new String[]{ Long.toString( db_id ) } );
	}
	
	// onResumeの時に設定を読み直す
	public void reloadSetting(){
		if( db_id == INVALID_ID )
			throw new RuntimeException( "SavedAccount.reloadSetting missing db_id" );
		SavedAccount b = loadAccount( log, db_id );
		if( b == null ) return; // DBから削除されてる？
		this.visibility = b.visibility;
		this.confirm_boost = b.confirm_boost;
		this.dont_hide_nsfw = b.dont_hide_nsfw;
		this.token_info = b.token_info;
		this.notification_mention = b.notification_follow;
		this.notification_boost = b.notification_boost;
		this.notification_favourite = b.notification_favourite;
		this.notification_follow = b.notification_follow;
	}
	
	public static @Nullable SavedAccount loadAccount( @NonNull LogCategory log, long db_id ){
		try{
			Cursor cursor = App1.getDB().query( table, null, COL_ID + "=?", new String[]{ Long.toString( db_id ) }, null, null, null );
			try{
				if( cursor.moveToFirst() ){
					return parse( cursor );
				}
			}finally{
				cursor.close();
			}
		}catch( Throwable ex ){
			ex.printStackTrace();
			log.e( ex, "loadAccount failed." );
		}
		return null;
	}
	
	public static @NonNull ArrayList< SavedAccount > loadAccountList( @NonNull LogCategory log ){
		ArrayList< SavedAccount > result = new ArrayList<>();
		try{
			Cursor cursor = App1.getDB().query( table, null, null, null, null, null, null );
			try{
				while( cursor.moveToNext() ){
					result.add( parse( cursor ) );
				}
			}finally{
				cursor.close();
			}
		}catch( Throwable ex ){
			ex.printStackTrace();
			log.e( ex, "loadAccountList failed." );
			throw new RuntimeException( "SavedAccount.loadAccountList failed.", ex );
		}
		return result;
	}
	
	public String getFullAcct( @NonNull TootAccount who ){
		return getFullAcct( who.acct );
	}
	
	public String getFullAcct( @NonNull String acct ){
		if( - 1 != acct.indexOf( '@' ) ){
			return acct;
		}else{
			return acct + "@" + this.host;
		}
	}
	
	public String getUserUrl( @NonNull String who_acct ){
		int p = who_acct.indexOf( '@' );
		if( - 1 != p ){
			return "https://" +who_acct.substring( p + 1 ) + "/@" + who_acct.substring( 0,p);
		}else{
			return "https://" + host + "/@" + who_acct;
		}
	}
	
	public boolean isMe( @NonNull TootAccount who ){
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
	
}
