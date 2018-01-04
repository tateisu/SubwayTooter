package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns

import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.util.LogCategory

class NotificationTracking {
	
	private var id = - 1L
	private var account_db_id : Long = 0
	var last_load : Long = 0
	var nid_read : Long = 0
	var nid_show : Long = 0
	
	var post_id : Long = 0
	var post_time : Long = 0
	
	var last_data : String? = null
	
	fun save() {
		try {
			val cv = ContentValues()
			cv.put(COL_ACCOUNT_DB_ID, account_db_id)
			cv.put(COL_LAST_LOAD, last_load)
			cv.put(COL_NID_READ, nid_read)
			cv.put(COL_NID_SHOW, nid_show)
			cv.put(COL_LAST_DATA, last_data)
			cv.put(COL_POST_ID, post_id)
			cv.put(COL_POST_TIME, post_time)
			if(id == - 1L) {
				id = App1.database.insert(table, null, cv)
				log.d("save.insert account_db_id=%s,post=%s,%s last_data=%s", account_db_id, post_id, post_time, if(last_data == null) "null" else "" + last_data !!.length
				)
				
			} else {
				App1.database.update(table, cv, WHERE_AID, arrayOf(account_db_id.toString()))
				log.d("save.update account_db_id=%s,post=%s,%s last_data=%s", account_db_id, post_id, post_time, if(last_data == null) "null" else "" + last_data !!.length
				)
			}
		} catch(ex : Throwable) {
			log.e(ex, "save failed.")
		}
		
	}
	
	fun updatePost(post_id : Long, post_time : Long) {
		this.post_id = post_id
		this.post_time = post_time
		try {
			val cv = ContentValues()
			cv.put(COL_POST_ID, post_id)
			cv.put(COL_POST_TIME, post_time)
			val rows = App1.database.update(table, cv, WHERE_AID, arrayOf(account_db_id.toString()))
			log.d("updatePost account_db_id=%s,post=%s,%s last_data=%s,update_rows=%s", account_db_id, post_id, post_time, if(last_data == null) "null" else "" + last_data !!.length, rows
			)
		} catch(ex : Throwable) {
			log.e(ex, "updatePost failed.")
		}
		
	}
	
	companion object {
		
		private val log = LogCategory("NotificationTracking")
		
		private const val table = "noti_trac"
		
		private const val COL_ID = BaseColumns._ID
		
		// アカウントDBの行ID。 サーバ側のIDではない
		private const val COL_ACCOUNT_DB_ID = "a"
		
		// サーバから通知を取得した時刻
		private const val COL_LAST_LOAD = "ll"
		
		// サーバから最後に読んだデータ。既読は排除されてるかも
		private const val COL_LAST_DATA = "ld"
		
		// 通知ID。ここまで既読
		private const val COL_NID_READ = "nr"
		
		// 通知ID。もっとも最近取得したもの
		private const val COL_NID_SHOW = "ns"
		
		// 最後に表示した通知のID
		private const val COL_POST_ID = "pi"
		
		// 最後に表示した通知の作成時刻
		private const val COL_POST_TIME = "pt"
		
		fun onDBCreate(db : SQLiteDatabase) {
			
			db.execSQL(
				"create table if not exists " + table
					+ "(_id INTEGER PRIMARY KEY"
					+ ",a integer not null"
					+ ",ll integer default 0"
					+ ",ld text"
					+ ",nr integer default 0"
					+ ",ns integer default 0"
					+ ",pi integer default 0"
					+ ",pt integer default 0"
					+ ")"
			)
			db.execSQL(
				"create unique index if not exists " + table + "_a on " + table + "(a)"
			)
		}
		
		fun onDBUpgrade(db : SQLiteDatabase, oldVersion : Int, newVersion : Int) {
			if(oldVersion < 2 && newVersion >= 2) {
				onDBCreate(db)
			}
		}
		
		private const val WHERE_AID = COL_ACCOUNT_DB_ID + "=?"
		
		fun load(account_db_id : Long) : NotificationTracking {
			val dst = NotificationTracking()
			dst.account_db_id = account_db_id
			try {
				App1.database.query(table, null, WHERE_AID, arrayOf(account_db_id.toString() ), null, null, null)
					.use{ cursor->
						if(cursor.moveToFirst()) {
							dst.id = cursor.getLong(cursor.getColumnIndex(COL_ID))
							dst.last_load = cursor.getLong(cursor.getColumnIndex(COL_LAST_LOAD))
							dst.nid_read = cursor.getLong(cursor.getColumnIndex(COL_NID_READ))
							dst.nid_show = cursor.getLong(cursor.getColumnIndex(COL_NID_SHOW))
							
							dst.post_id = cursor.getLong(cursor.getColumnIndex(COL_POST_ID))
							dst.post_time = cursor.getLong(cursor.getColumnIndex(COL_POST_TIME))
							
							val idx_last_data = cursor.getColumnIndex(COL_LAST_DATA)
							dst.last_data = if(cursor.isNull(idx_last_data)) null else cursor.getString(idx_last_data)
							
							log.d("load account_db_id=%s,post=%s,%s last_data=%s", account_db_id, dst.post_id, dst.post_time, if(dst.last_data == null) "null" else "" + dst.last_data !!.length
							)
						}
						
					}
			} catch(ex : Throwable) {
				log.e(ex, "load failed.")
			}
			
			return dst
		}
		
		fun updateRead(account_db_id : Long) {
			try {
				val where_args = arrayOf( account_db_id.toString() )
				App1.database.query(table, arrayOf(COL_NID_SHOW, COL_NID_READ), WHERE_AID, where_args, null, null, null)
					.use{ cursor ->
						if(cursor.moveToFirst()) {
							val nid_show = cursor.getLong(cursor.getColumnIndex(COL_NID_SHOW))
							val nid_read = cursor.getLong(cursor.getColumnIndex(COL_NID_READ))
							log.d("updateRead account_db_id=%s, nid_show=%s, nid_read=%s", account_db_id, nid_show, nid_read)
							val cv = ContentValues()
							cv.put(COL_NID_READ, nid_show)
							App1.database.update(table, cv, WHERE_AID, where_args)
						} else {
							log.e("updateRead no row in query.")
						}
					}
			} catch(ex : Throwable) {
				log.e(ex, "updateRead failed.")
			}
			
		}
		
		fun resetPostAll() {
			try {
				val cv = ContentValues()
				cv.put(COL_POST_ID, 0)
				cv.put(COL_POST_TIME, 0)
				App1.database.update(table, cv, null, null)
				
			} catch(ex : Throwable) {
				log.e(ex, "resetPostAll failed.")
			}
			
		}
		
		fun resetLastLoad(db_id : Long) {
			try {
				val cv = ContentValues()
				cv.put(COL_LAST_LOAD, 0)
				App1.database.update(table, cv, COL_ACCOUNT_DB_ID + "=?", arrayOf(db_id.toString()))
			} catch(ex : Throwable) {
				log.e(ex, "resetLastLoad(db_id) failed.")
			}
			
		}
		
		fun resetLastLoad() {
			try {
				val cv = ContentValues()
				cv.put(COL_LAST_LOAD, 0)
				App1.database.update(table, cv, null, null)
			} catch(ex : Throwable) {
				log.e(ex, "resetLastLoad() failed.")
			}
			
		}
	}
}
