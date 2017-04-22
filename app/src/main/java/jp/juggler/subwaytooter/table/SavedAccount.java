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
import jp.juggler.subwaytooter.util.LogCategory;

public class SavedAccount extends TootAccount{
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
	
	public static final long INVALID_ID = -1L;
	
	// login information
	public long db_id = INVALID_ID;
	public String host;
	public String user;
	public JSONObject token_info;
	public String visibility;
	public boolean confirm_boost;
	public boolean dont_hide_nsfw;
	
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
				+ ")"
		);
		db.execSQL("create index if not exists " + table + "_user on " + table + "(u)" );
		db.execSQL("create index if not exists " + table + "_host on " + table + "(h,u)" );
	}
	
	public static void onDBUpgrade( SQLiteDatabase db, int oldVersion, int newVersion ){
		
	}
	
	private static SavedAccount parse(  Cursor cursor ) throws JSONException{
		JSONObject src = new JSONObject( cursor.getString( cursor.getColumnIndex( COL_ACCOUNT ) ) );
		SavedAccount dst = (SavedAccount)parse(log,src,new SavedAccount());
		if( dst != null){
			dst.db_id = cursor.getLong( cursor.getColumnIndex( COL_ID ) );
			dst.host = cursor.getString( cursor.getColumnIndex( COL_HOST ) );
			dst.user = cursor.getString( cursor.getColumnIndex( COL_USER ) );
			
			int colIdx_visibility = cursor.getColumnIndex( COL_VISIBILITY );
			dst.visibility = cursor.isNull( colIdx_visibility )? null : cursor.getString( colIdx_visibility );

			dst.confirm_boost = ( 0 != cursor.getInt( cursor.getColumnIndex( COL_CONFIRM_BOOST ) ) );
			dst.dont_hide_nsfw = ( 0 != cursor.getInt( cursor.getColumnIndex( COL_DONT_HIDE_NSFW ) ) );
			
			dst.token_info = new JSONObject( cursor.getString( cursor.getColumnIndex( COL_TOKEN ) ) );
		}
		return dst;
	}
	
	
	public static long insert( String host, String user, JSONObject account,JSONObject token ){
		try{
			ContentValues cv = new ContentValues();
			cv.put( COL_HOST, host );
			cv.put( COL_USER, user );
			cv.put( COL_ACCOUNT, account.toString() );
			cv.put( COL_TOKEN, token.toString() );
			return App1.getDB().insert(  table, null, cv );
		}catch( Throwable ex ){
			log.e( ex, "saveAccount failed." );
		}
		return INVALID_ID;
	}
	
	public void delete(){
		try{
			App1.getDB().delete(  table,  COL_ID + "=?", new String[]{ Long.toString(db_id) } );
		}catch( Throwable ex ){
			log.e( ex, "saveAccount failed." );
		}
	}
	
	public void updateTokenInfo( JSONObject token_info ){
		if( db_id != INVALID_ID ){
			ContentValues cv = new ContentValues();
			cv.put( COL_TOKEN, token_info.toString() );
			App1.getDB().update( table, cv, COL_ID + "=?", new String[]{ Long.toString(db_id) } );
		}
	}
	public void saveSetting(){
		if( db_id != INVALID_ID ){
			ContentValues cv = new ContentValues();
			cv.put( COL_VISIBILITY, visibility );
			cv.put( COL_CONFIRM_BOOST, confirm_boost? 1:0  );
			cv.put( COL_DONT_HIDE_NSFW, dont_hide_nsfw ? 1: 0  );
			App1.getDB().update( table, cv, COL_ID + "=?", new String[]{ Long.toString(db_id) } );
		}
	}
	
	public static SavedAccount loadAccount( LogCategory log, long id ){
		try{
			Cursor cursor = App1.getDB().query( table, null, COL_ID+"=?", new String[]{ Long.toString(id) }, null, null, null );
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

	public static ArrayList< SavedAccount > loadAccountList(LogCategory log){
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
	
	public String getFullAcct(TootAccount who ){
		if( who== null || who.acct ==null ) return "@?";
		if( -1 != who.acct.indexOf( '@' ) ){
			return "@" + who.acct;
		}else{
			return "@"+ who.acct +"@"+ this.host;
		}
	}
	
}
