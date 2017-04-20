package jp.juggler.subwaytooter.table;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.util.LogCategory;

public class SavedAccount extends TootAccount{
	
	static final String table = "access_info";
	
	static final String COL_HOST = "h";
	static final String COL_USER_MAIL = "um";
	static final String COL_ACCOUNT = "a";
	static final String COL_LOGIN_REQUIRED = "lr";
	
	// login information
	public String host;
	public String user_mail;
	public boolean login_required;
	
	
	public static void onDBCreate( SQLiteDatabase db ){
		db.execSQL(
			"create table if not exists " + table
				+ "(_id INTEGER PRIMARY KEY"
				+ ",h text not null"
				+ ",um text not null"
				+ ",a text not null"
				+ ",lr integer default 0"
				+ ")"
		);
		db.execSQL(
			"create unique index if not exists " + table + "_host on " + table
				+ "(h"
				+ ",um"
				+ ")"
		);
	}
	
	public static void onDBUpgrade( SQLiteDatabase db, int oldVersion, int newVersion ){
		
	}
	
	private static SavedAccount parse( LogCategory log, Cursor cursor ) throws JSONException{
		JSONObject src = new JSONObject( cursor.getString( cursor.getColumnIndex( COL_ACCOUNT ) ) );
		SavedAccount dst = (SavedAccount)parse(log,src,new SavedAccount());
		if( dst != null){
			dst.host = cursor.getString( cursor.getColumnIndex( COL_HOST ) );
			dst.user_mail = cursor.getString( cursor.getColumnIndex( COL_USER_MAIL ) );
			dst.login_required = ( 0 != cursor.getInt( cursor.getColumnIndex( COL_LOGIN_REQUIRED ) ) );
		}
		return dst;
	}
	
	
	public static void save( LogCategory log,String instance, String user_mail, JSONObject data ){
		try{
			ContentValues cv = new ContentValues();
			cv.put( COL_HOST, instance );
			cv.put( COL_USER_MAIL, user_mail );
			cv.put( COL_ACCOUNT, data.toString() );
			App1.getDB().replace( table, null, cv );
		}catch( Throwable ex ){
			log.e( ex, "saveAccount failed." );
		}
	}
	
	public static SavedAccount loadAccount( LogCategory log, String instance, String user_mail ){
		try{
			Cursor cursor = App1.getDB().query( table, null, "h=? and um=?", new String[]{ instance, user_mail }, null, null, null );
			try{
				if( cursor.moveToFirst() ){
					return parse( log,cursor );
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
					result.add( parse( log,cursor ) );
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
	
	public static boolean hasAccount( LogCategory log,String instance, String user_mail ){
		return null != loadAccount( log,instance,user_mail );
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
