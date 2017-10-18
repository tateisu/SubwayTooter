package jp.juggler.subwaytooter.table;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

import org.json.JSONObject;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.util.LogCategory;

public class ClientInfo {
	private static final LogCategory log = new LogCategory( "ClientInfo" );
	
	public static final String table = "client_info2";
	private static final String COL_HOST = "h";
	private static final String COL_CLIENT_NAME = "cn";
	private static final String COL_RESULT = "r";
	
	public static void onDBCreate( SQLiteDatabase db ){
		db.execSQL(
			"create table if not exists " + table
				+ "(_id INTEGER PRIMARY KEY"
				+ ",h text not null"
				+ ",cn text not null"
				+ ",r text not null"
				+ ")"
		);
		db.execSQL(
			"create unique index if not exists " + table + "_host_client_name on " + table + "(h,cn)"
		);
	}
	
	public static void onDBUpgrade( SQLiteDatabase db, int oldVersion, int newVersion ){
		if( oldVersion <= 18 && newVersion >= 19 ){
			onDBCreate( db );
		}
	}
	
	public static JSONObject load( @NonNull String instance, @NonNull String client_name ){
		try{
			Cursor cursor = App1.getDB().query( table, null, "h=? and cn=?", new String[]{ instance, client_name }, null, null, null );
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
	
	public static void save( @NonNull String instance, @NonNull String client_name, @NonNull String json ){
		try{
			ContentValues cv = new ContentValues();
			cv.put( COL_HOST, instance );
			cv.put( COL_CLIENT_NAME, client_name );
			cv.put( COL_RESULT, json );
			App1.getDB().replace( table, null, cv );
		}catch( Throwable ex ){
			log.e( ex, "save failed." );
		}
	}
}
