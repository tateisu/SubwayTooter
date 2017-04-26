package jp.juggler.subwaytooter.table;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

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
	private static final String COL_NOTIFICATION_MENTION = "notification_mention";
	private static final String COL_NOTIFICATION_BOOST = "notification_boost";
	private static final String COL_NOTIFICATION_FAVOURITE = "notification_favourite";
	private static final String COL_NOTIFICATION_FOLLOW = "notification_follow";
	
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
			
			dst.token_info = new JSONObject( cursor.getString( cursor.getColumnIndex( COL_TOKEN ) ) );
		}
		return dst;
	}
	
	public static long insert( String host, String acct, JSONObject account, JSONObject token ){
		try{
			ContentValues cv = new ContentValues();
			cv.put( COL_HOST, host );
			cv.put( COL_USER, acct );
			cv.put( COL_ACCOUNT, account.toString() );
			cv.put( COL_TOKEN, token.toString() );
			return App1.getDB().insert( table, null, cv );
		}catch( Throwable ex ){
			log.e( ex, "saveAccount failed." );
		}
		return INVALID_ID;
	}
	
	public void delete(){
		try{
			App1.getDB().delete( table, COL_ID + "=?", new String[]{ Long.toString( db_id ) } );
		}catch( Throwable ex ){
			log.e( ex, "saveAccount failed." );
		}
	}
	
	public void updateTokenInfo( JSONObject token_info ){
		if( db_id != INVALID_ID ){
			this.token_info = token_info;
			
			ContentValues cv = new ContentValues();
			cv.put( COL_TOKEN, token_info.toString() );
			App1.getDB().update( table, cv, COL_ID + "=?", new String[]{ Long.toString( db_id ) } );
		}
	}
	
	public void saveSetting(){
		if( db_id != INVALID_ID ){
			ContentValues cv = new ContentValues();
			cv.put( COL_VISIBILITY, visibility );
			cv.put( COL_CONFIRM_BOOST, confirm_boost ? 1 : 0 );
			cv.put( COL_DONT_HIDE_NSFW, dont_hide_nsfw ? 1 : 0 );
			
			cv.put( COL_NOTIFICATION_MENTION, notification_mention ? 1 : 0 );
			cv.put( COL_NOTIFICATION_BOOST, notification_boost ? 1 : 0 );
			cv.put( COL_NOTIFICATION_FAVOURITE, notification_favourite ? 1 : 0 );
			cv.put( COL_NOTIFICATION_FOLLOW, notification_follow ? 1 : 0 );
			
			App1.getDB().update( table, cv, COL_ID + "=?", new String[]{ Long.toString( db_id ) } );
		}
	}
	
	// onResumeの時に設定を読み直す
	public void reloadSetting(){
		if( db_id != INVALID_ID ){
			SavedAccount b = loadAccount( log, db_id );
			if( b != null ){
				this.visibility = b.visibility;
				this.confirm_boost = b.confirm_boost;
				this.dont_hide_nsfw = b.dont_hide_nsfw;
				this.token_info = b.token_info;
				this.notification_mention = b.notification_follow;
				this.notification_boost = b.notification_boost;
				this.notification_favourite = b.notification_favourite;
				this.notification_follow = b.notification_follow;
			}
		}
	}
	
	public static SavedAccount loadAccount( LogCategory log, long db_id ){
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
			log.e( ex, "loadToken failed." );
		}
		return null;
	}
	
	public static ArrayList< SavedAccount > loadAccountList( LogCategory log ){
		ArrayList< SavedAccount > result = new ArrayList<>();
		
		try{
			Cursor cursor = App1.getDB().query( table, null, null, null, null, null, null );
			try{
				while( cursor.moveToNext() ){
					result.add( parse( cursor ) );
				}
				return result;
			}finally{
				cursor.close();
			}
		}catch( Throwable ex ){
			log.e( ex, "loadAccountList failed." );
		}
		return null;
	}
	
	public String getFullAcct( TootAccount who ){
		return getFullAcct( who == null ? null : who.acct );
	}
	
	public String getFullAcct( String acct ){
		if( acct == null ) return "?";
		if( - 1 != acct.indexOf( '@' ) ){
			return acct;
		}else{
			return acct + "@" + this.host;
		}
	}
	
	public boolean isMe( TootAccount who ){
		int pos = this.acct.indexOf( '@' );
		String this_user = this.acct.substring( 0, pos );
		//
		if( ! this_user.equals( who.username ) ) return false;
		//
		pos = who.acct.indexOf( '@' );
		return pos == - 1 || this.host.equalsIgnoreCase( who.acct.substring( pos + 1 ) );
	}
	
	public boolean isMe( String who_acct ){
		int pos = this.acct.indexOf( '@' );
		String this_user = this.acct.substring( 0, pos );
		//
		pos = who_acct.indexOf( '@' );
		if( pos == - 1 ) return this_user.equals( who_acct );
		//
		return this_user.equals( who_acct.substring( 0, pos ) )
			&& this.host.equalsIgnoreCase( who_acct.substring( pos + 1 ) );
	}
}
