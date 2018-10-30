package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.getInt

object ContentWarningMisskey : TableCompanion {
	private val log = LogCategory("ContentWarningMisskey")
	
	private const val table = "content_warning_misskey"
	private const val COL_HOST = "h"
	private const val COL_STATUS_ID = "si"
	private const val COL_SHOWN = "sh"
	private const val COL_TIME_SAVE = "time_save"
	
	private val projection_shown = arrayOf(COL_SHOWN)
	
	override fun onDBCreate(db : SQLiteDatabase) {
		log.d("onDBCreate!")
		db.execSQL(
			"""
			create table if not exists $table
			(_id INTEGER PRIMARY KEY
			,$COL_HOST text not null
			,$COL_STATUS_ID text not null
			,$COL_SHOWN integer not null
			,$COL_TIME_SAVE integer default 0
			)
			""".trimIndent()
		)
		db.execSQL(
			"create unique index if not exists ${table}_status_id on $table($COL_HOST,$COL_STATUS_ID)"
		)
	}
	
	override fun onDBUpgrade(db : SQLiteDatabase, oldVersion : Int, newVersion : Int) {
		if(oldVersion < 30 && newVersion >= 30) {
			db.execSQL("drop table if exists $table")
			onDBCreate(db)
		}
	}
	
	fun isShown(status : TootStatus, default_value : Boolean) : Boolean {
		try {
			App1.database.query(
				table,
				projection_shown,
				"h=? and si=?",
				arrayOf(
					status.hostAccessOrOriginal,
					status.id.toString()
				),
				null,
				null,
				null
			).use { cursor ->
				if(cursor.moveToFirst()) {
					val iv = cursor.getInt(COL_SHOWN)
					return 0 != iv
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
			cv.put(COL_HOST, status.hostAccessOrOriginal)
			cv.put(COL_STATUS_ID, status.id.toString())
			cv.put(COL_SHOWN, is_shown.b2i())
			cv.put(COL_TIME_SAVE, now)
			App1.database.replace(table, null, cv)
			
		} catch(ex : Throwable) {
			log.e(ex, "save failed.")
		}
	}
	
	fun deleteOld(now : Long) {
		try {
			val expire = now - 86400000L * 365
			App1.database.delete(table, "$COL_TIME_SAVE<?", arrayOf(expire.toString()))
		} catch(ex : Throwable) {
			log.e(ex, "deleteOld failed.")
		}
	}
}
