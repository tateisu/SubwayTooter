package jp.juggler.subwaytooter.table;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.util.LogCategory;

public class MediaShown {
	private static final LogCategory log = new LogCategory( "MediaShown" );
	
	private static final String table = "media_shown";
	private static final String COL_HOST = "h";
	private static final String COL_STATUS_ID = "si";
	private static final String COL_SHOWN = "sh";
	
	public static void onDBCreate( SQLiteDatabase db ){
		db.execSQL(
			"create table if not exists " + table
				+ "(_id INTEGER PRIMARY KEY"
				+ ",h text not null"
				+ ",si integer not null"
				+ ",sh integer not null"
				+ ")"
		);
		db.execSQL(
			"create unique index if not exists " + table + "_status_id on " + table + "(h,si,sh)"
		);
	}
	
	public static void onDBUpgrade( SQLiteDatabase db, int oldVersion, int newVersion ){
		
	}
	
	private static final String[] projection_shown = new String[]{COL_SHOWN};
	
	public static boolean isShown( String host,long status_id ,boolean default_value ){
		try{
			Cursor cursor = App1.getDB().query( table, projection_shown, "h=? and si=?", new String[]{host, Long.toString(status_id) }, null, null, null );
			try{
				if( cursor.moveToFirst() ){
					return ( 0 != cursor.getInt( cursor.getColumnIndex( COL_SHOWN ) ) );
				}
			}finally{
				cursor.close();
			}
		}catch( Throwable ex ){
			log.e( ex, "load failed." );
		}
		return default_value ;
	}
	
	public static void save( String host,long status_id ,boolean is_shown ){
		try{
			ContentValues cv = new ContentValues();
			cv.put( COL_HOST, host );
			cv.put( COL_STATUS_ID, status_id );
			cv.put( COL_SHOWN, is_shown ? 1:0 );
			App1.getDB().replace( table, null, cv );
		}catch( Throwable ex ){
			log.e( ex, "save failed." );
		}
	}
}
