package jp.juggler.subwaytooter.table;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.HashSet;

import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.util.LogCategory;

public class MutedApp {
	
	private static final LogCategory log = new LogCategory( "MutedApp" );
	
	public static final String table = "app_mute";
	public static final String COL_NAME = "name";
	private static final String COL_TIME_SAVE = "time_save";
	
	public static void onDBCreate( SQLiteDatabase db ){
		log.d( "onDBCreate!" );
		db.execSQL(
			"create table if not exists " + table
				+ "(_id INTEGER PRIMARY KEY"
				+ ",name text not null"
				+ ",time_save integer not null"
				+ ")"
		);
		db.execSQL(
			"create unique index if not exists " + table + "_name on " + table + "(name)"
		);
	}
	
	public static void onDBUpgrade( SQLiteDatabase db, int oldVersion, int newVersion ){
		if( oldVersion < 6 && newVersion >= 6 ){
			onDBCreate( db );
		}
	}
	
	public static void save( String app_name ){
		if( app_name == null ) return;
		try{
			long now = System.currentTimeMillis();
			
			ContentValues cv = new ContentValues();
			cv.put( COL_NAME, app_name );
			cv.put( COL_TIME_SAVE, now );
			App1.getDB().replace( table, null, cv );
			
		}catch( Throwable ex ){
			log.e( ex, "save failed." );
		}
	}
	
	public static Cursor createCursor(){
		return App1.getDB().query( table, null, null, null, null, null, COL_NAME + " asc" );
	}
	
	public static void delete( String name ){
		try{
			App1.getDB().delete( table, COL_NAME + "=?", new String[]{ name } );
		}catch( Throwable ex ){
			log.e( ex, "delete failed." );
		}
	}
	
	public static HashSet< String > getNameSet(){
		HashSet< String > dst = new HashSet<>();
		try{
			Cursor cursor = App1.getDB().query( table, null, null, null, null, null, null );
			if( cursor != null ){
				try{
					int idx_name = cursor.getColumnIndex( COL_NAME );
					while( cursor.moveToNext() ){
						String s = cursor.getString( idx_name );
						dst.add( s );
					}
				}finally{
					cursor.close();
				}
			}
		}catch( Throwable ex ){
			log.trace( ex );
		}
		return dst;
	}
	
	//	private static final String[] isMuted_projection = new String[]{COL_NAME};
	//	private static final String   isMuted_where = COL_NAME+"=?";
	//	private static final ThreadLocal<String[]> isMuted_where_arg = new ThreadLocal<String[]>() {
	//		@Override protected String[] initialValue() {
	//			return new String[1];
	//		}
	//	};
	//	public static boolean isMuted( String app_name ){
	//		if( app_name == null ) return false;
	//		try{
	//			String[] where_arg = isMuted_where_arg.get();
	//			where_arg[0] = app_name;
	//			Cursor cursor = App1.getDB().query( table, isMuted_projection,isMuted_where , where_arg, null, null, null );
	//			try{
	//				if( cursor.moveToFirst() ){
	//					return true;
	//				}
	//			}finally{
	//				cursor.close();
	//			}
	//		}catch( Throwable ex ){
	//			log.e( ex, "load failed." );
	//		}
	//		return false;
	//	}
	
}
