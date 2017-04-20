package jp.juggler.subwaytooter.table;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.util.LogCategory;

public class AccessToken {

	static final LogCategory log = new LogCategory( "AccessToken" );
	
	static final String table = "access_token";
	
	static final String COL_HOST = "h";
	static final String COL_USER_MAIL = "um";
	static final String COL_TOKEN = "t";
	
	public String host;
	public String user_mail;
	
	public static void onDBCreate( SQLiteDatabase db ){
		db.execSQL(
			"create table if not exists " + table
				+ "(_id INTEGER PRIMARY KEY"
				+ ",h text not null"
				+ ",um text not null"
				+ ",t text not null"
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
	
	public static JSONObject load( String instance, String user_mail ){
		try{
			Cursor cursor = App1.getDB().query( table, null, "h=? and um=?", new String[]{ instance, user_mail }, null, null, null );
			try{
				if( cursor.moveToFirst() ){
					return new JSONObject( cursor.getString( cursor.getColumnIndex( COL_TOKEN ) ) );
				}
			}finally{
				cursor.close();
			}
		}catch( Throwable ex ){
			log.e( ex, "load failed." );
		}
		return null;
	}
	
	public static void save( String host, String user_mail, String json ){
		try{
			ContentValues cv = new ContentValues();
			cv.put( COL_HOST, host );
			cv.put( COL_USER_MAIL, user_mail );
			cv.put( COL_TOKEN, json );
			App1.getDB().replace( table, null, cv );
		}catch( Throwable ex ){
			log.e( ex, "save failed." );
		}
	}
	
	
}
