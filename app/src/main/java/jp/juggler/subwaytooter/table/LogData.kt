package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log

@Suppress("UNUSED_PARAMETER")
object LogData {
	private const val TAG = "SubwayTooter"
	
	internal const val table = "log"
	
	private const val  COL_TIME = "t"
	private const val  COL_LEVEL = "l"
	private const val  COL_CATEGORY = "c"
	private const val  COL_MESSAGE = "m"
	
	@Suppress("unused")
	const val LEVEL_ERROR = 100
	const val LEVEL_WARNING = 200
	const val LEVEL_INFO = 300
	const val LEVEL_VERBOSE = 400
	const val LEVEL_DEBUG = 500
	const val LEVEL_HEARTBEAT = 600
	const val LEVEL_FLOOD = 700
	
	fun onDBCreate(db : SQLiteDatabase) {
		db.execSQL(
			"create table if not exists " + table
				+ "(_id INTEGER PRIMARY KEY"
				+ ","+COL_TIME+" integer not null"
				+ ","+COL_LEVEL+" integer not null"
				+ ","+COL_CATEGORY+" text not null"
				+ ","+COL_MESSAGE+" text not null"
				+ ")"
		)
		db.execSQL(
			"create index if not exists " + table + "_time on " + table
				+ "(t"
				+ ",l"
				+ ")"
		)
	}
	
	fun onDBUpgrade(db : SQLiteDatabase, v_old : Int, v_new : Int) {
	
	}
	
	fun insert(cv : ContentValues, time : Long, level : Int, category : String, message : String) : Long {
		try {
			Log.d(TAG, category + ": " + message)
			//		try{
			//			cv.clear();
			//			cv.put( COL_TIME, time );
			//			cv.put( COL_LEVEL, level );
			//			cv.put( COL_MESSAGE, message );
			//			cv.put( COL_CATEGORY, category );
			//			return App1.getDB().insert( table, null, cv );
			//		}catch( Throwable ignored ){
			//		}
		}catch(ex:Throwable){
			// PC上で行う単体テストにはLog クラスがない
			println(category + ": " + message)
		}
		return - 1L
	}
	
	@Suppress("unused")
	private fun getLogLevelString(level : Int) :String {
		return when {
			level >= LogData.LEVEL_FLOOD -> "Flood"
			level >= LogData.LEVEL_HEARTBEAT -> "HeartBeat"
			level >= LogData.LEVEL_DEBUG -> "Debug"
			level >= LogData.LEVEL_VERBOSE -> "Verbose"
			level >= LogData.LEVEL_INFO -> "Info"
			level >= LogData.LEVEL_WARNING -> "Warning"
			else -> "Error"
		}
	}
	
}