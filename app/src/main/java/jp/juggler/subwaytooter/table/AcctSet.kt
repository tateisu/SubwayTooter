package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

import java.util.ArrayList

import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.util.LogCategory

object AcctSet :TableCompanion{
	
	private val log = LogCategory("AcctSet")
	
	private const val table = "acct_set"
	private const val COL_TIME_SAVE = "time_save"
	private const val COL_ACCT = "acct" //@who@host ascii文字の大文字小文字は(sqliteにより)同一視される
	
	private const val prefix_search_where = "$COL_ACCT like ? escape '$'"
	
	private val prefix_search_where_arg = object : ThreadLocal<Array<String?>>() {
		override fun initialValue() : Array<String?> {
			return arrayOfNulls(1)
		}
	}
	
	override fun onDBCreate(db : SQLiteDatabase) {
		log.d("onDBCreate!")
		db.execSQL(
			"create table if not exists " + table
				+ "(_id INTEGER PRIMARY KEY"
				+ "," + COL_TIME_SAVE + " integer not null"
				+ "," + COL_ACCT + " text not null"
				+ ")"
		)
		db.execSQL(
			"create unique index if not exists " + table + "_acct on " + table + "(" + COL_ACCT + ")"
		)
		db.execSQL(
			"create index if not exists " + table + "_time on " + table + "(" + COL_TIME_SAVE + ")"
		)
	}
	
	override fun onDBUpgrade(db : SQLiteDatabase, oldVersion : Int, newVersion : Int) {
		if(oldVersion < 7 && newVersion >= 7) {
			onDBCreate(db)
		}
	}
	
	fun deleteOld(now : Long) {
		try {
			// 古いデータを掃除する
			val expire = now - 86400000L * 365
			App1.database.delete(table, COL_TIME_SAVE + "<?", arrayOf(expire.toString()))
			
		} catch(ex : Throwable) {
			log.e(ex, "deleteOld failed.")
		}
		
	}
	
	fun saveList(now : Long, src_list : ArrayList<String?>, offset : Int, length : Int) {
		
		try {
			val cv = ContentValues()
			cv.put(COL_TIME_SAVE, now)
			
			var bOK = false
			val db = App1.database
			db.execSQL("BEGIN TRANSACTION")
			try {
				for(i in 0 until length) {
					val acct = src_list[i + offset] ?: continue
					cv.put(COL_ACCT, acct)
					db.replace(table, null, cv)
				}
				bOK = true
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "saveList failed.")
			}
			
			if(bOK) {
				db.execSQL("COMMIT TRANSACTION")
			} else {
				db.execSQL("ROLLBACK TRANSACTION")
			}
		} catch(ex : Throwable) {
			log.trace(ex)
			log.e(ex, "saveList failed.")
		}
		
	}
	
	private fun makePattern(src : String) : String {
		val sb = StringBuilder()
		var i = 0
		val ie = src.length
		while(i < ie) {
			val c = src[i]
			if(c == '%' || c == '_' || c == '$') {
				sb.append('$')
			}
			sb.append(c)
			++ i
		}
		// 前方一致検索にするため、末尾に%をつける
		sb.append('%')
		return sb.toString()
	}
	
	fun searchPrefix(prefix : String, limit : Int) : ArrayList<CharSequence> {
		try {
			val where_arg = prefix_search_where_arg.get() ?: arrayOfNulls<String?>(1)
			where_arg[0] = makePattern(prefix)
			App1.database.query(table, null, prefix_search_where, where_arg, null, null, COL_ACCT + " asc limit " + limit)
				.use { cursor ->
					val dst = ArrayList<CharSequence>(cursor.count)
					val idx_acct = cursor.getColumnIndex(COL_ACCT)
					while(cursor.moveToNext()) {
						dst.add(cursor.getString(idx_acct))
					}
					return dst
				}
		} catch(ex : Throwable) {
			log.trace(ex)
			log.e(ex, "searchPrefix failed.")
		}
		
		return ArrayList()
	}
	
}
