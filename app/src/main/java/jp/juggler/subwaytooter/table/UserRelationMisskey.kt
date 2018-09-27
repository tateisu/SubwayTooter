package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.util.LogCategory

object UserRelationMisskey : TableCompanion {
	
	private val log = LogCategory("UserRelationMisskey")
	
	private const val table = "user_relation_misskey"
	private const val COL_TIME_SAVE = "time_save"
	private const val COL_DB_ID = "db_id" // SavedAccount のDB_ID
	private const val COL_WHO_ID = "who_id" // ターゲットアカウントのID
	private const val COL_FOLLOWING = "following"
	private const val COL_FOLLOWED_BY = "followed_by"
	private const val COL_BLOCKING = "blocking"
	private const val COL_MUTING = "muting"
	private const val COL_REQUESTED = "requested"
	private const val COL_FOLLOWING_REBLOGS = "following_reblogs"
	private const val COL_ENDORSED = "endorsed"
	
	override fun onDBCreate(db : SQLiteDatabase) {
		log.d("onDBCreate!")
		db.execSQL(
			"""
				create table if not exists $table
				(_id INTEGER PRIMARY KEY
				,$COL_TIME_SAVE integer not null
				,$COL_DB_ID integer not null
				,$COL_WHO_ID text not null
				,$COL_FOLLOWING integer not null
				,$COL_FOLLOWED_BY integer not null
				,$COL_BLOCKING integer not null
				,$COL_MUTING integer not null
				,$COL_REQUESTED integer not null
				,$COL_FOLLOWING_REBLOGS integer not null
				,$COL_ENDORSED integer default 0
				)"""
		)
		db.execSQL(
			"create unique index if not exists ${table}_id on $table ($COL_DB_ID,$COL_WHO_ID)"
		)
		db.execSQL(
			"create index if not exists ${table}_time on $table ($COL_TIME_SAVE)"
		)
	}
	
	override fun onDBUpgrade(db : SQLiteDatabase, oldVersion : Int, newVersion : Int) {
		if(oldVersion < 30 && newVersion >= 30) {
			db.execSQL("drop table if exists $table")
			onDBCreate(db)
		}
		if(oldVersion < 32 && newVersion >= 32) {
			try {
				db.execSQL("alter table $table add column $COL_ENDORSED integer default 0")
			} catch(ex : Throwable) {
				log.trace(ex)
			}
		}
	}
	
	fun save1(now : Long, db_id : Long, whoId : String, src : UserRelation) {
		try {
			val cv = ContentValues()
			cv.put(COL_TIME_SAVE, now)
			cv.put(COL_DB_ID, db_id)
			cv.put(COL_WHO_ID, whoId)
			cv.put(COL_FOLLOWING, src.following.b2i())
			cv.put(COL_FOLLOWED_BY, src.followed_by.b2i())
			cv.put(COL_BLOCKING, src.blocking.b2i())
			cv.put(COL_MUTING, src.muting.b2i())
			cv.put(COL_REQUESTED, src.requested.b2i())
			cv.put(COL_FOLLOWING_REBLOGS, src.following_reblogs)
			cv.put(COL_ENDORSED, src.endorsed.b2i())
			App1.database.replace(table, null, cv)
			
			val key = String.format("%s:%s", db_id, whoId)
			UserRelation.mMemoryCache.remove(key)
			
		} catch(ex : Throwable) {
			log.e(ex, "save failed.")
		}
	}
	
	fun saveList(
		now : Long,
		db_id : Long,
		src_list : List<Map.Entry<EntityId, UserRelation>>,
		start : Int,
		end : Int
	) {
		
		val cv = ContentValues()
		cv.put(COL_TIME_SAVE, now)
		cv.put(COL_DB_ID, db_id)
		
		var bOK = false
		val db = App1.database
		db.execSQL("BEGIN TRANSACTION")
		try {
			for(i in start until end) {
				val entry = src_list[i]
				val id = entry.key
				val src = entry.value
				cv.put(COL_WHO_ID, id.toString())
				cv.put(COL_FOLLOWING, src.following.b2i())
				cv.put(COL_FOLLOWED_BY, src.followed_by.b2i())
				cv.put(COL_BLOCKING, src.blocking.b2i())
				cv.put(COL_MUTING, src.muting.b2i())
				cv.put(COL_REQUESTED, src.requested.b2i())
				cv.put(COL_FOLLOWING_REBLOGS, src.following_reblogs)
				cv.put(COL_ENDORSED, src.endorsed.b2i())
				db.replace(table, null, cv)
			}
			bOK = true
		} catch(ex : Throwable) {
			log.trace(ex)
			log.e(ex, "saveList failed.")
		}
		
		if(bOK) {
			db.execSQL("COMMIT TRANSACTION")
			for(i in start until end) {
				val entry = src_list[i]
				val key = String.format("%s:%s", db_id, entry.key)
				UserRelation.mMemoryCache.remove(key)
			}
		} else {
			db.execSQL("ROLLBACK TRANSACTION")
		}
	}
	
	private const val load_where = "$COL_DB_ID=? and $COL_WHO_ID=?"
	
	private val load_where_arg = object : ThreadLocal<Array<String?>>() {
		override fun initialValue() : Array<String?> {
			return Array(2) { _ -> null }
		}
	}
	
	fun load(db_id : Long, who_id : String) : UserRelation? {
		
		try {
			val where_arg = load_where_arg.get() ?: arrayOfNulls<String?>(2)
			where_arg[0] = db_id.toString()
			where_arg[1] = who_id
			App1.database.query(table, null, load_where, where_arg, null, null, null)
				.use { cursor ->
					if(cursor.moveToNext()) {
						val dst = UserRelation()
						dst.following = cursor.getInt(cursor.getColumnIndex(COL_FOLLOWING)).i2b()
						dst.followed_by =
							cursor.getInt(cursor.getColumnIndex(COL_FOLLOWED_BY)).i2b()
						dst.blocking = cursor.getInt(cursor.getColumnIndex(COL_BLOCKING)).i2b()
						dst.muting = cursor.getInt(cursor.getColumnIndex(COL_MUTING)).i2b()
						dst.requested = cursor.getInt(cursor.getColumnIndex(COL_REQUESTED)).i2b()
						dst.following_reblogs =
							cursor.getInt(cursor.getColumnIndex(COL_FOLLOWING_REBLOGS))
						dst.endorsed = cursor.getInt(cursor.getColumnIndex(COL_ENDORSED)).i2b()
						return dst
					}
				}
		} catch(ex : Throwable) {
			log.trace(ex)
			log.e(ex, "load failed.")
		}
		return null
		
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
