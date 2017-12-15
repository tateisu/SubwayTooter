package jp.juggler.subwaytooter.table;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class LogData {
	private static final String TAG = "SubwayTooter";
	
	static final String table = "log";
	
//	public static final String COL_TIME = "t";
//	public static final String COL_LEVEL = "l";
//	public static final String COL_CATEGORY = "c";
//	public static final String COL_MESSAGE = "m";
	
	public static final int LEVEL_ERROR = 100;
	public static final int LEVEL_WARNING = 200;
	public static final int LEVEL_INFO = 300;
	public static final int LEVEL_VERBOSE = 400;
	public static final int LEVEL_DEBUG = 500;
	public static final int LEVEL_HEARTBEAT = 600;
	public static final int LEVEL_FLOOD = 700;
	
	
	
	public static void onDBCreate( SQLiteDatabase db ){
		db.execSQL(
			"create table if not exists " + table
				+ "(_id INTEGER PRIMARY KEY"
				+ ",t integer not null"
				+ ",l integer not null"
				+ ",c text not null"
				+ ",m text not null"
				+ ")"
		);
		db.execSQL(
			"create index if not exists " + table + "_time on " + table
				+ "(t"
				+ ",l"
				+ ")"
		);
	}
	
	public static void onDBUpgrade( SQLiteDatabase db, int v_old, int v_new ){
		
	}
	
	public static long insert( ContentValues cv, long time, int level,String category, String message ){
		Log.d( TAG,category+": "+message);
//		try{
//			cv.clear();
//			cv.put( COL_TIME, time );
//			cv.put( COL_LEVEL, level );
//			cv.put( COL_MESSAGE, message );
//			cv.put( COL_CATEGORY, category );
//			return App1.getDB().insert( table, null, cv );
//		}catch( Throwable ignored ){
//		}
		return -1L;
	}
	
//	public static String getLogLevelString( int level ){
//		if( level >= LogData.LEVEL_FLOOD ){
//			return "Flood";
//		}else if( level >= LogData.LEVEL_HEARTBEAT ){
//			return "HeartBeat";
//		}else if( level >= LogData.LEVEL_DEBUG ){
//			return "Debug";
//		}else if( level >= LogData.LEVEL_VERBOSE ){
//			return "Verbose";
//		}else if( level >= LogData.LEVEL_INFO ){
//			return "Info";
//		}else if( level >= LogData.LEVEL_WARNING ){
//			return "Warning";
//		}else{
//			return "Error";
//		}
//	}
	
}