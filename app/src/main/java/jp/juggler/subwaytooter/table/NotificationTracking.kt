package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.putMayNull
import jp.juggler.util.LogCategory
import jp.juggler.util.getLong

class NotificationTracking {
	
	private var id = - 1L
	private var account_db_id : Long = 0
	private var notificationType: String =""

	var nid_read : EntityId? = null
	var nid_show : EntityId? = null
	var post_id : EntityId? = null
	var post_time : Long = 0
	
	fun save() {
		try {
			val cv = ContentValues()
			cv.put(COL_ACCOUNT_DB_ID, account_db_id)
			cv.put(COL_NOTIFICATION_TYPE,notificationType)
			nid_read.putMayNull(cv, COL_NID_READ)
			nid_show.putMayNull(cv, COL_NID_SHOW)
			post_id.putMayNull(cv, COL_POST_ID)
			cv.put(COL_POST_TIME, post_time)

			val rv = App1.database.replaceOrThrow(table,null,cv)
			if( rv != -1L && id == -1L) id = rv
			log.d(
				"save account_db_id=%s,nt=%s,post=%s,%s"
				, account_db_id
				, notificationType
				, post_id
				, post_time
			)
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
			val rows = App1.database.update(table, cv, WHERE_AID, arrayOf(account_db_id.toString(),notificationType))
			log.d(
				"updatePost account_db_id=%s, nt=%s, post=%s,%s update_rows=%s"
				, account_db_id
				, notificationType
				, post_id
				, post_time
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
		
		// 通知ID。ここまで既読
		private const val COL_NID_READ = "nr"
		
		// 通知ID。もっとも最近取得したもの
		private const val COL_NID_SHOW = "ns"
		
		// 最後に表示した通知のID
		private const val COL_POST_ID = "pi"
		
		// 最後に表示した通知の作成時刻
		private const val COL_POST_TIME = "pt"
		
		// 返信だけ通知グループを分ける
		private const val COL_NOTIFICATION_TYPE ="nt"
		
		override fun onDBCreate(db : SQLiteDatabase) {
			
			db.execSQL(
				"""
				create table if not exists $table
				($COL_ID INTEGER PRIMARY KEY
				,$COL_ACCOUNT_DB_ID integer not null
				,$COL_NID_READ text
				,$COL_NID_SHOW text
				,$COL_POST_ID text
				,$COL_POST_TIME integer default 0
				,$COL_NOTIFICATION_TYPE text default ''
				)
				"""
			)
			db.execSQL(
				"create unique index if not exists ${table}_b on $table ($COL_ACCOUNT_DB_ID,$COL_NOTIFICATION_TYPE)"
			)
		}
		
		override fun onDBUpgrade(db : SQLiteDatabase, oldVersion : Int, newVersion : Int) {
			
			if( newVersion < oldVersion ) {
				try {
					db.execSQL("drop table if exists $table")
				} catch(ex : Throwable) {
					log.trace(ex, "delete DB failed.")
				}
				onDBCreate(db)
				return
			}

			if(oldVersion < 2 && newVersion >= 2) {
				onDBCreate(db)
				return
			}
			
			if(oldVersion < 40 && newVersion >= 40) {
				try {
					db.execSQL("drop index if exists ${table}_a")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				try {
					db.execSQL("alter table $table add column $COL_NOTIFICATION_TYPE text default ''")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				try {
					db.execSQL(
						"create unique index if not exists ${table}_b on $table ($COL_ACCOUNT_DB_ID,$COL_NOTIFICATION_TYPE)"
					)
				} catch(ex : Throwable) {
					log.trace(ex)
				}
			}
		}
		
		private const val WHERE_AID = "$COL_ACCOUNT_DB_ID=? and $COL_NOTIFICATION_TYPE=?"
		
		fun load(account_db_id : Long,notificationType:String) : NotificationTracking {
			val dst = NotificationTracking()
			dst.account_db_id = account_db_id
			dst.notificationType = notificationType
			try {
				App1.database.query(
					table,
					null,
					WHERE_AID,
					arrayOf(account_db_id.toString(),notificationType),
					null,
					null,
					null
				)?.use { cursor ->
					if(cursor.moveToFirst()) {
						dst.id = cursor.getLong(COL_ID)
						
						dst.nid_show = EntityId.from(cursor, COL_NID_SHOW)
						dst.nid_read = EntityId.from(cursor, COL_NID_READ)
						
						dst.post_id = EntityId.from(cursor, COL_POST_ID)
						dst.post_time = cursor.getLong(COL_POST_TIME)
						
						log.d(
							"load account_db_id=%s,post=%s,%s,read=%s,show=%s"
							, account_db_id
							, dst.post_id
							, dst.post_time
							, dst.nid_read
							, dst.nid_show
						)
					}
					
				}
			} catch(ex : Throwable) {
				log.trace(ex, "load failed.")
			}
			
			return dst
		}
		
		fun updateRead(account_db_id : Long,notificationType:String) {
			try {
				val where_args = arrayOf(account_db_id.toString(),notificationType)
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
						! cursor.moveToFirst() -> log.e("updateRead[$account_db_id,$notificationType]: can't find the data row.")
						
						else -> {
							val nid_show = EntityId.from(cursor, COL_NID_SHOW)
							val nid_read = EntityId.from(cursor, COL_NID_READ)
							when {
								nid_show == null ->
									log.w("updateRead[$account_db_id,$notificationType]: nid_show is null.")

								nid_read != null && nid_read >= nid_show ->
									log.d("updateRead[$account_db_id,$notificationType]: nid_read already updated.")
								
								else -> {
									log.w("updateRead[$account_db_id,$notificationType]: update nid_read as $nid_show...")
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
	}
}
