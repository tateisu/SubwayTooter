package jp.juggler.subwaytooter.table;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONObject;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.util.LogCategory;

public class ClientInfo {
	static final LogCategory log = new LogCategory( "ClientInfo" );
	
	static final String table = "client_info";
	static final String COL_HOST = "h";
	static final String COL_RESULT = "r";
	
	public static void onDBCreate( SQLiteDatabase db ){
		db.execSQL(
			"create table if not exists " + table
				+ "(_id INTEGER PRIMARY KEY"
				+ ",h text not null"
				+ ",r text not null"
				+ ")"
		);
		db.execSQL(
			"create unique index if not exists " + table + "_host on " + table
				+ "(h"
				+ ")"
		);
	}
	
	public static void onDBUpgrade( SQLiteDatabase db, int oldVersion, int newVersion ){
		
	}
	
	public static JSONObject load( String instance ){
		try{
			Cursor cursor = App1.getDB().query( table, null, "h=?", new String[]{ instance }, null, null, null );
			try{
				if( cursor.moveToFirst() ){
					return new JSONObject( cursor.getString( cursor.getColumnIndex( COL_RESULT ) ) );
				}
			}finally{
				cursor.close();
			}
		}catch( Throwable ex ){
			log.e( ex, "load failed." );
		}
		return null;
	}
	
	public static void save( String host, String json ){
		try{
			ContentValues cv = new ContentValues();
			cv.put( COL_HOST, host );
			cv.put( COL_RESULT, json );
			App1.getDB().replace( table, null, cv );
		}catch( Throwable ex ){
			log.e( ex, "save failed." );
		}
	}
}
