package jp.juggler.subwaytooter.table

import android.database.sqlite.SQLiteDatabase
import jp.juggler.util.TableCompanion

object LogData : TableCompanion {
    // private const val TAG = "SubwayTooter"

    override val table = "warning"

    private const val COL_TIME = "t"
    private const val COL_LEVEL = "l"
    private const val COL_CATEGORY = "c"
    private const val COL_MESSAGE = "m"

    @Suppress("unused")
    const val LEVEL_ERROR = 100
    private const val LEVEL_WARNING = 200
    private const val LEVEL_INFO = 300
    private const val LEVEL_VERBOSE = 400
    private const val LEVEL_DEBUG = 500
    private const val LEVEL_HEARTBEAT = 600
    private const val LEVEL_FLOOD = 700

    override fun onDBCreate(db: SQLiteDatabase) {
        db.execSQL(
            """create table if not exists $table
			(_id INTEGER PRIMARY KEY
			,$COL_TIME integer not null
			,$COL_LEVEL integer not null
			,$COL_CATEGORY text not null
			,$COL_MESSAGE text not null
			)""".trimIndent()
        )
        db.execSQL("create index if not exists ${table}_time on $table($COL_TIME,$COL_LEVEL)")
    }

    override fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    }

//	fun insert(cv : ContentValues, time : Long, level : Int, category : String, message : String) : Long {
//		try {
//			Log.d(TAG, "$category: $message")
//			//		try{
//			//			cv.clear();
//			//			cv.put( COL_TIME, time );
//			//			cv.put( COL_LEVEL, level );
//			//			cv.put( COL_MESSAGE, message );
//			//			cv.put( COL_CATEGORY, category );
//			//			return App1.getDB().insert( table, null, cv );
//			//		}catch( Throwable ignored ){
//			//		}
//		}catch(ex:Throwable){
//			// PC上で行う単体テストにはLog クラスがない
//			println("$category: $message")
//		}
//		return - 1L
//	}

    @Suppress("unused")
    private fun getLogLevelString(level: Int): String {
        return when {
            level >= LEVEL_FLOOD -> "Flood"
            level >= LEVEL_HEARTBEAT -> "HeartBeat"
            level >= LEVEL_DEBUG -> "Debug"
            level >= LEVEL_VERBOSE -> "Verbose"
            level >= LEVEL_INFO -> "Info"
            level >= LEVEL_WARNING -> "Warning"
            else -> "Error"
        }
    }
}
