package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.util.LogCategory

object FavMute {
	
	private val log = LogCategory("FavMute")
	
	const val table = "fav_mute"
	const val COL_ID = "_id"
	const val COL_ACCT = "acct"
	
	fun onDBCreate(db : SQLiteDatabase) {
		log.d("onDBCreate!")
		db.execSQL(
			"create table if not exists " + table
				+ "($COL_ID INTEGER PRIMARY KEY"
				+ ",$COL_ACCT text not null "
				+ ")"
		)
		db.execSQL(
			"create unique index if not exists ${table}_acct on $table($COL_ACCT)"
		)
	}
	
	fun onDBUpgrade(db : SQLiteDatabase, oldVersion : Int, newVersion : Int) {
		if(oldVersion < 22 && newVersion >= 22) {
			onDBCreate(db)
		}
	}
	
	fun save(acct : String?) {
		acct ?: return
		try {
			val cv = ContentValues()
			cv.put(COL_ACCT, acct)
			App1.database.replace(table, null, cv)
		} catch(ex : Throwable) {
			log.e(ex, "save failed.")
		}
		
	}
	
	fun delete(acct : String) {
		try {
			App1.database.delete(table, "$COL_ACCT=?", arrayOf(acct))
		} catch(ex : Throwable) {
			log.e(ex, "delete failed.")
		}
	}

	fun createCursor() : Cursor {
		return App1.database.query(table, null, null, null, null, null, COL_ACCT + " asc")
	}
	
	val acctSet: HashSet<String>
		get() {
			val dst = HashSet<String>()
			try {
				App1.database.query(table, null, null, null, null, null, null)
					.use{ cursor->
						val idx_name = cursor.getColumnIndex(COL_ACCT)
						while(cursor.moveToNext()) {
							val s = cursor.getString(idx_name)
							dst.add(s)
						}
						
					}
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			
			return dst
		}
	
	fun contains( acct : String) : Boolean {
		var found = false
		try {
			App1.database.query(table, null, "$COL_ACCT=?", arrayOf(acct), null, null, null)
				.use{ cursor->
					while(cursor.moveToNext()) {
						found= true
					}
				}
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
		return found
	}
	
}
