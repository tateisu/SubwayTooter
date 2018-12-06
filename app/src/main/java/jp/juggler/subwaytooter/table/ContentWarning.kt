package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.util.LogCategory
import jp.juggler.util.getInt

object ContentWarning : TableCompanion {
	private val log = LogCategory("ContentWarning")
	
	private const val table = "content_warning"
	private const val COL_STATUS_URI = "su"
	private const val COL_SHOWN = "sh"
	private const val COL_TIME_SAVE = "time_save"
	
	private val projection_shown = arrayOf(COL_SHOWN)
	
	override fun onDBCreate(db : SQLiteDatabase) {
		log.d("onDBCreate!")
		db.execSQL(
			"""
			create table if not exists $table
			(_id INTEGER PRIMARY KEY
			,$COL_STATUS_URI text not null
			,$COL_SHOWN integer not null
			,$COL_TIME_SAVE integer default 0
			)
			""".trimIndent()
		)
		db.execSQL(
			"create unique index if not exists ${table}_status_uri on $table($COL_STATUS_URI)"
		)
		db.execSQL(
			"create index if not exists ${table}_time_save on $table($COL_TIME_SAVE)"
		)
	}
	
	override fun onDBUpgrade(db : SQLiteDatabase, oldVersion : Int, newVersion : Int) {
		fun intersect(x:Int) = (oldVersion < x && newVersion >= x)
		
		if( intersect(36) || intersect(31) || intersect(5) ){
			db.execSQL("drop table if exists $table")
			onDBCreate(db)
		}
	}
	
	fun isShown(status : TootStatus, default_value : Boolean) : Boolean {
		
		try {
			App1.database.query(
				table,
				projection_shown,
				"$COL_STATUS_URI=?",
				arrayOf(status.uri),
				null,
				null,
				null
			).use { cursor ->
				if(cursor.moveToFirst()) {
					return 0 != cursor.getInt(COL_SHOWN)
				}
				
			}
		} catch(ex : Throwable) {
			log.e(ex, "load failed.")
		}
		
		return default_value
	}
	
	fun save(status : TootStatus, is_shown : Boolean) {

		try {
			val now = System.currentTimeMillis()
			
			val cv = ContentValues()
			cv.put(COL_STATUS_URI, status.uri)
			cv.put(COL_SHOWN, is_shown.b2i())
			cv.put(COL_TIME_SAVE, now)
			App1.database.replace(table, null, cv)
		} catch(ex : Throwable) {
			log.e(ex, "save failed.")
		}

	}

	fun deleteOld(now : Long) {
		try {
			// 古いデータを掃除する
			val expire = now - 86400000L * 365
			App1.database.delete(table, "$COL_TIME_SAVE<?", arrayOf(expire.toString()))
		} catch(ex : Throwable) {
			log.e(ex, "deleteOld failed.")
		}
	}
}
