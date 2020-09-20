package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootRelationShip
import jp.juggler.util.JsonObject
import jp.juggler.util.LogCategory
import jp.juggler.util.getInt
import jp.juggler.util.getStringOrNull

class UserRelation {
	
	var following  = false   // 認証ユーザからのフォロー状態にある
	var followed_by  = false // 認証ユーザは被フォロー状態にある
	var blocking  = false // 認証ユーザからブロックした
	var blocked_by  = false // 認証ユーザからブロックされた(Misskeyのみ。Mastodonでは常にfalse)
	var muting  = false
	var requested = false  // 認証ユーザからのフォローは申請中である
	var requested_by  = false  // 相手から認証ユーザへのフォローリクエスト申請中(Misskeyのみ。Mastodonでは常にfalse)
	var following_reblogs  = 0 // このユーザからのブーストをTLに表示する
	var endorsed  = false // ユーザをプロフィールで紹介する
	var notifying = false // ユーザの投稿を通知する
	var note : String? = null
	
	// 認証ユーザからのフォロー状態
	fun getFollowing(who : TootAccount?) : Boolean {
		return if(requested && ! following && who != null && ! who.locked) true else following
	}
	
	// 認証ユーザからのフォローリクエスト申請中状態
	fun getRequested(who : TootAccount?) : Boolean {
		return if(requested && ! following && who != null && ! who.locked) false else requested
	}
	
	companion object : TableCompanion {
		
		const val REBLOG_HIDE =
			0 // don't show the boosts from target account will be shown on authorized user's home TL.
		const val REBLOG_SHOW =
			1 // show the boosts from target account will be shown on authorized user's home TL.
		const val REBLOG_UNKNOWN = 2 // not following, or instance don't support hide reblog.
		
		private val mMemoryCache = androidx.collection.LruCache<String, UserRelation>(2048)
		
		private val log = LogCategory("UserRelationMisskey")
		
		private const val table = "user_relation_misskey"
		const val COL_ID = BaseColumns._ID
		private const val COL_TIME_SAVE = "time_save"
		private const val COL_DB_ID = "db_id" // SavedAccount のDB_ID。 疑似アカウント用のエントリは -2L
		const val COL_WHO_ID = "who_id" // ターゲットアカウントのID
		private const val COL_FOLLOWING = "following"
		private const val COL_FOLLOWED_BY = "followed_by"
		private const val COL_BLOCKING = "blocking"
		private const val COL_MUTING = "muting"
		private const val COL_REQUESTED = "requested"
		private const val COL_FOLLOWING_REBLOGS = "following_reblogs"
		private const val COL_ENDORSED = "endorsed"
		private const val COL_BLOCKED_BY = "blocked_by"
		private const val COL_REQUESTED_BY = "requested_by"
		private const val COL_NOTE = "note"
		private const val COL_NOTIFYING = "notifying"
		
		private const val DB_ID_PSEUDO = - 2L
		
		override fun onDBCreate(db : SQLiteDatabase) {
			log.d("onDBCreate!")
			db.execSQL(
				"""
				create table if not exists $table
				($COL_ID INTEGER PRIMARY KEY
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
				,$COL_BLOCKED_BY integer default 0
				,$COL_REQUESTED_BY integer default 0
				,$COL_NOTE text default null
				,$COL_NOTIFYING integer default 0
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
			if(oldVersion < 34 && newVersion >= 34) {
				try {
					db.execSQL("alter table $table add column $COL_BLOCKED_BY integer default 0")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
			}
			if(oldVersion < 35 && newVersion >= 35) {
				try {
					db.execSQL("alter table $table add column $COL_REQUESTED_BY integer default 0")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
			}
			if(oldVersion < 55 && newVersion >= 55) {
				try {
					db.execSQL("alter table $table add column $COL_NOTE text default null")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
			}
			if(oldVersion < 58 && newVersion >= 58) {
				try {
					db.execSQL("alter table $table add column $COL_NOTIFYING integer default 0")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
			}
		}
		
		fun deleteOld(now : Long) {
			try {
				val expire = now - 86400000L * 365
				App1.database.delete(table, "$COL_TIME_SAVE<?", arrayOf(expire.toString()))
			} catch(ex : Throwable) {
				log.e(ex, "deleteOld failed.")
			}
			
			try {
				// 旧型式のテーブルの古いデータの削除だけは時々回す
				val table = "user_relation"
				val COL_TIME_SAVE = "time_save"
				val expire = now - 86400000L * 365
				App1.database.delete(table, "$COL_TIME_SAVE<?", arrayOf(expire.toString()))
			} catch(_ : Throwable) {
			}
		}
		
		fun save1Misskey(now : Long, db_id : Long, whoId : String, src : UserRelation?) {
			src ?: return
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
				cv.put(COL_BLOCKED_BY, src.blocked_by.b2i())
				cv.put(COL_REQUESTED_BY, src.requested_by.b2i())
				cv.put(COL_NOTIFYING, src.notifying.b2i())
				
				cv.putOrNull(COL_NOTE, src.note)
				App1.database.replaceOrThrow(table, null, cv)
				
				val key = String.format("%s:%s", db_id, whoId)
				mMemoryCache.remove(key)
				
			} catch(ex : Throwable) {
				log.e(ex, "save failed.")
			}
		}
		
		// マストドン用
		fun save1Mastodon(now : Long, db_id : Long, src : TootRelationShip) : UserRelation {
			
			val id : String = src.id.toString()
			
			try {
				val cv = ContentValues()
				cv.put(COL_TIME_SAVE, now)
				cv.put(COL_DB_ID, db_id)
				cv.put(COL_WHO_ID, id)
				cv.put(COL_FOLLOWING, src.following.b2i())
				cv.put(COL_FOLLOWED_BY, src.followed_by.b2i())
				cv.put(COL_BLOCKING, src.blocking.b2i())
				cv.put(COL_MUTING, src.muting.b2i())
				cv.put(COL_REQUESTED, src.requested.b2i())
				cv.put(COL_FOLLOWING_REBLOGS, src.showing_reblogs)
				cv.put(COL_ENDORSED, src.endorsed.b2i())
				cv.put(COL_BLOCKED_BY, src.blocked_by.b2i())
				cv.put(COL_REQUESTED_BY, src.requested_by.b2i())
				cv.put(COL_NOTIFYING, src.notifying.b2i())
				
				cv.putOrNull(COL_NOTE, src.note)
				App1.database.replaceOrThrow(table, null, cv)
				val key = String.format("%s:%s", db_id, id)
				mMemoryCache.remove(key)
			} catch(ex : Throwable) {
				log.e(ex, "save failed.")
			}
			
			return load(db_id, id)
		}
		
		// マストドン用
		fun saveListMastodon(now : Long, db_id : Long, src_list : ArrayList<TootRelationShip>) {
			
			val cv = ContentValues()
			cv.put(COL_TIME_SAVE, now)
			cv.put(COL_DB_ID, db_id)
			
			var bOK = false
			val db = App1.database
			db.execSQL("BEGIN TRANSACTION")
			try {
				for(src in src_list) {
					val id = src.id.toString()
					cv.put(COL_WHO_ID, id)
					cv.put(COL_FOLLOWING, src.following.b2i())
					cv.put(COL_FOLLOWED_BY, src.followed_by.b2i())
					cv.put(COL_BLOCKING, src.blocking.b2i())
					cv.put(COL_BLOCKED_BY, src.blocked_by.b2i())
					cv.put(COL_MUTING, src.muting.b2i())
					cv.put(COL_REQUESTED, src.requested.b2i())
					cv.put(COL_FOLLOWING_REBLOGS, src.showing_reblogs)
					cv.put(COL_ENDORSED, src.endorsed.b2i())
					cv.putOrNull(COL_NOTE, src.note)
					db.replaceOrThrow(table, null, cv)
					
				}
				bOK = true
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "saveList failed.")
			}
			
			if(bOK) {
				db.execSQL("COMMIT TRANSACTION")
				for(src in src_list) {
					val id = src.id.toString()
					val key = String.format("%s:%s", db_id, id)
					mMemoryCache.remove(key)
				}
			} else {
				db.execSQL("ROLLBACK TRANSACTION")
			}
		}
		
		fun saveListMisskey(
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
					cv.put(COL_BLOCKED_BY, src.blocked_by.b2i())
					cv.put(COL_REQUESTED_BY, src.requested_by.b2i())
					cv.put(COL_NOTIFYING, src.notifying.b2i())
					
					cv.putOrNull(COL_NOTE, src.note)
					db.replaceOrThrow(table, null, cv)
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
					mMemoryCache.remove(key)
				}
			} else {
				db.execSQL("ROLLBACK TRANSACTION")
			}
		}
		
		fun saveList2(now : Long, db_id : Long, list : ArrayList<TootRelationShip>) {
			val cv = ContentValues()
			cv.put(COL_TIME_SAVE, now)
			cv.put(COL_DB_ID, db_id)
			
			var bOK = false
			val db = App1.database
			db.execSQL("BEGIN TRANSACTION")
			try {
				for(src in list) {
					cv.put(COL_WHO_ID, src.id.toString())
					cv.put(COL_FOLLOWING, src.following.b2i())
					cv.put(COL_FOLLOWED_BY, src.followed_by.b2i())
					cv.put(COL_BLOCKING, src.blocking.b2i())
					cv.put(COL_MUTING, src.muting.b2i())
					cv.put(COL_REQUESTED, src.requested.b2i())
					cv.put(COL_FOLLOWING_REBLOGS, src.showing_reblogs)
					cv.put(COL_ENDORSED, src.endorsed.b2i())
					cv.put(COL_BLOCKED_BY, src.blocked_by.b2i())
					cv.put(COL_REQUESTED_BY, src.requested_by.b2i())
					cv.put(COL_NOTIFYING, src.notifying.b2i())
					
					cv.putOrNull(COL_NOTE, src.note)
					db.replace(table, null, cv)
				}
				bOK = true
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "saveList2 failed.")
			}
			
			if(bOK) {
				db.execSQL("COMMIT TRANSACTION")
				for(src in list) {
					val key = String.format("%s:%s", db_id, src.id)
					mMemoryCache.remove(key)
				}
			} else {
				db.execSQL("ROLLBACK TRANSACTION")
			}
		}
		
		private const val load_where = "$COL_DB_ID=? and $COL_WHO_ID=?"
		
		private val load_where_arg = object : ThreadLocal<Array<String?>>() {
			override fun initialValue() : Array<String?> {
				return Array(2) { null }
			}
		}
		
		fun load(db_id : Long, whoId : EntityId) : UserRelation {
			//
			val key = String.format("%s:%s", db_id, whoId)
			val cached : UserRelation? = mMemoryCache.get(key)
			if(cached != null) return cached
			
			val dst = load(db_id, whoId.toString())
			
			mMemoryCache.put(key, dst)
			return dst
		}
		
		fun load(db_id : Long, who_id : String) : UserRelation {
			
			try {
				val where_arg = load_where_arg.get() ?: arrayOfNulls<String?>(2)
				where_arg[0] = db_id.toString()
				where_arg[1] = who_id
				App1.database.query(table, null, load_where, where_arg, null, null, null)
					.use { cursor ->
						if(cursor.moveToNext()) {
							val dst = UserRelation()
							dst.following = cursor.getBoolean(COL_FOLLOWING)
							dst.followed_by = cursor.getBoolean(COL_FOLLOWED_BY)
							dst.blocking = cursor.getBoolean(COL_BLOCKING)
							dst.muting = cursor.getBoolean(COL_MUTING)
							dst.requested = cursor.getBoolean(COL_REQUESTED)
							dst.following_reblogs = cursor.getInt(COL_FOLLOWING_REBLOGS)
							dst.endorsed = cursor.getBoolean(COL_ENDORSED)
							dst.blocked_by = cursor.getBoolean(COL_BLOCKED_BY)
							dst.requested_by = cursor.getBoolean(COL_REQUESTED_BY)
							dst.notifying = cursor.getBoolean(COL_NOTIFYING)
							
							dst.note = cursor.getStringOrNull(COL_NOTE)
							return dst
						}
					}
				
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "load failed.")
			}
			return UserRelation()
		}
		
		// MisskeyはUserエンティティにユーザリレーションが含まれたり含まれなかったりする
		fun fromAccount(parser : TootParser, src : JsonObject, id : EntityId) {
			
			// アカウントのjsonがユーザリレーションを含まないなら何もしない
			src["isFollowing"] ?: return
			
			// プロフカラムで ユーザのプロフ(A)とアカウントTL(B)を順に取得すると
			// (A)ではisBlockingに情報が入っているが、(B)では情報が入っていない
			// 対策として(A)でリレーションを取得済みのユーザは(B)のタイミングではリレーションを読み捨てる
			
			val map = parser.misskeyUserRelationMap
			if(map.containsKey(id)) return
			
			map[id] = UserRelation().apply {
				following = src.optBoolean("isFollowing")
				followed_by = src.optBoolean("isFollowed")
				muting = src.optBoolean("isMuted")
				blocking = src.optBoolean("isBlocking")
				blocked_by = src.optBoolean("isBlocked")
				endorsed = false
				requested = src.optBoolean("hasPendingFollowRequestFromYou")
				requested_by = src.optBoolean("hasPendingFollowRequestToYou")
			}
		}
		
		fun loadPseudo(acct : Acct) = load(DB_ID_PSEUDO, acct.ascii)
		
		fun createCursorPseudo() : Cursor =
			App1.database.query(
				table,
				arrayOf(COL_ID, COL_WHO_ID),
				"$COL_DB_ID=$DB_ID_PSEUDO and ( $COL_MUTING=1 or $COL_BLOCKING=1 )",
				null,
				null,
				null,
				"$COL_WHO_ID asc"
			)
		
		fun deletePseudo(rowId : Long) {
			try {
				App1.database.delete(table, "$COL_ID=$rowId", null)
			} catch(ex : Throwable) {
				log.trace(ex)
			}
		}
	}
	
	fun savePseudo(acct : String) =
		save1Misskey(System.currentTimeMillis(), DB_ID_PSEUDO, acct, this)
}
