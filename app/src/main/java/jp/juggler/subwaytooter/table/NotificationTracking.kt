package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns

import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.putMayNull
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.getStringOrNull

class NotificationTracking {
	
	private var id = - 1L
	private var account_db_id : Long = 0
	var last_load : Long = 0
	var nid_read : EntityId? = null
	var nid_show : EntityId? = null
	var post_id : EntityId? = null
	var post_time : Long = 0
	
	var last_data : String? = null
	
	fun save() {
		try {
			val cv = ContentValues()
			cv.put(COL_ACCOUNT_DB_ID, account_db_id)
			cv.put(COL_LAST_LOAD, last_load)
			nid_read.putMayNull(cv, COL_NID_READ)
			nid_show.putMayNull(cv, COL_NID_SHOW)
			post_id.putMayNull(cv, COL_POST_ID)
			cv.put(COL_POST_TIME, post_time)
			cv.put(COL_LAST_DATA, last_data)
			if(id == - 1L) {
				id = App1.database.insert(table, null, cv)
				log.d(
					"save.insert account_db_id=%s,post=%s,%s last_data=%s"
					, account_db_id
					, post_id
					, post_time
					, last_data?.length
				)
				
			} else {
				App1.database.update(table, cv, WHERE_AID, arrayOf(account_db_id.toString()))
				log.d(
					"save.update account_db_id=%s,post=%s,%s last_data=%s"
					, account_db_id
					, post_id
					, post_time
					, last_data?.length
				)
			}
		} catch(ex : Throwable) {
			log.e(ex, "save failed.")
		}
		
	}
	
	fun updatePost(post_id : EntityId, post_time : Long) {
		this.post_id = post_id
		this.post_time = post_time
		try {
			val cv = ContentValues()
			post_id.putTo(cv, COL_POST_ID)
			cv.put(COL_POST_TIME, post_time)
			val rows = App1.database.update(table, cv, WHERE_AID, arrayOf(account_db_id.toString()))
			log.d(
				"updatePost account_db_id=%s,post=%s,%s last_data=%s,update_rows=%s"
				, account_db_id
				, post_id
				, post_time
				, last_data?.length
				, rows
			)
			
		} catch(ex : Throwable) {
			log.e(ex, "updatePost failed.")
		}
		
	}
	
	companion object : TableCompanion {
		
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
		
		override fun onDBCreate(db : SQLiteDatabase) {
			
			db.execSQL(
				"""
				create table if not exists $table
				($COL_ID INTEGER PRIMARY KEY
				,$COL_ACCOUNT_DB_ID integer not null
				,$COL_LAST_LOAD integer default 0
				,$COL_LAST_DATA text
				,$COL_NID_READ text
				,$COL_NID_SHOW text
				,$COL_POST_ID text
				,$COL_POST_TIME integer default 0
				)
				"""
			)
			db.execSQL(
				"create unique index if not exists ${table}_a on $table ($COL_ACCOUNT_DB_ID)"
			)
		}
		
		override fun onDBUpgrade(db : SQLiteDatabase, oldVersion : Int, newVersion : Int) {
			if(oldVersion < 2 && newVersion >= 2) {
				onDBCreate(db)
			} else if(oldVersion < 33 && newVersion >= 33) {
				try {
					db.execSQL("drop table if exists $table")
				} catch(ex : Throwable) {
					log.trace(ex, "delete DB failed.")
				}
				onDBCreate(db)
			}
		}
		
		private const val WHERE_AID = "$COL_ACCOUNT_DB_ID=?"
		
		fun load(account_db_id : Long) : NotificationTracking {
			val dst = NotificationTracking()
			dst.account_db_id = account_db_id
			try {
				App1.database.query(
					table,
					null,
					WHERE_AID,
					arrayOf(account_db_id.toString()),
					null,
					null,
					null
				)?.use { cursor ->
					if(cursor.moveToFirst()) {
						dst.id = cursor.getLong(cursor.getColumnIndex(COL_ID))
						dst.last_load = cursor.getLong(cursor.getColumnIndex(COL_LAST_LOAD))
						
						dst.nid_show = EntityId.from(cursor, COL_NID_SHOW)
						dst.nid_read = EntityId.from(cursor, COL_NID_READ)
						dst.post_id = EntityId.from(cursor, COL_POST_ID)
						dst.post_time = cursor.getLong(cursor.getColumnIndex(COL_POST_TIME))
						
						dst.last_data = cursor.getStringOrNull(COL_LAST_DATA)
						
						log.d(
							"load account_db_id=%s,post=%s,%s last_data=%s"
							, account_db_id
							, dst.post_id
							, dst.post_time
							, dst.last_data?.length
						)
					}
					
				}
			} catch(ex : Throwable) {
				log.trace(ex, "load failed.")
			}
			
			return dst
		}
		
		fun updateRead(account_db_id : Long) {
			try {
				val where_args = arrayOf(account_db_id.toString())
				App1.database.query(
					table,
					arrayOf(COL_NID_SHOW, COL_NID_READ),
					WHERE_AID,
					where_args,
					null,
					null,
					null
				)?.use { cursor ->
					when {
						! cursor.moveToFirst() -> log.e("updateRead[${account_db_id}]: can't find the data row.")
						
						else -> {
							val nid_show = EntityId.from(cursor, COL_NID_SHOW)
							val nid_read = EntityId.from(cursor, COL_NID_READ)
							when {
								nid_show == null ->
									log.w("updateRead[${account_db_id}]: nid_show is null.")
								nid_read != null && nid_read >= nid_show ->
									log.d("updateRead[${account_db_id}]: nid_read already updated.")
								
								else -> {
									log.w("updateRead[${account_db_id}]: update nid_read as ${nid_show}...")
									val cv = ContentValues()
									nid_show.putTo(cv, COL_NID_READ) //変数名とキー名が異なるのに注意
									App1.database.update(table, cv, WHERE_AID, where_args)
								}
							}
						}
					}
				}
			} catch(ex : Throwable) {
				log.e(ex, "updateRead[${account_db_id}] failed.")
			}
		}
		
		fun resetPostAll() {
			try {
				val cv = ContentValues()
				cv.putNull(COL_POST_ID)
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
				App1.database.update(table, cv, WHERE_AID, arrayOf(db_id.toString()))
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
