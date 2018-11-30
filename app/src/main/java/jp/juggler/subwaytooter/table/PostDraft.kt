package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import jp.juggler.subwaytooter.App1
import jp.juggler.util.LogCategory
import jp.juggler.util.digestSHA256Hex
import jp.juggler.util.toJsonObject
import org.json.JSONObject
import java.util.*

class PostDraft {
	
	var id : Long = 0
	var time_save : Long = 0
	var json : JSONObject? = null
	var hash : String? = null
	
	class ColIdx(cursor : Cursor) {
		internal val idx_id : Int
		internal val idx_time_save : Int
		internal val idx_json : Int
		internal val idx_hash : Int
		
		init {
			idx_id = cursor.getColumnIndex(COL_ID)
			idx_time_save = cursor.getColumnIndex(COL_TIME_SAVE)
			idx_json = cursor.getColumnIndex(COL_JSON)
			idx_hash = cursor.getColumnIndex(COL_HASH)
		}
		
	}
	
	fun delete() {
		try {
			App1.database.delete(table, "$COL_ID=?", arrayOf(id.toString()))
		} catch(ex : Throwable) {
			log.e(ex, "delete failed.")
		}
		
	}
	
	companion object :TableCompanion{
		
		private val log = LogCategory("PostDraft")
		
		private const val table = "post_draft"
		private const val COL_ID = BaseColumns._ID
		private const val COL_TIME_SAVE = "time_save"
		private const val COL_JSON = "json"
		private const val COL_HASH = "hash"
		
		override fun onDBCreate(db : SQLiteDatabase) {
			log.d("onDBCreate!")
			db.execSQL(
				"create table if not exists " + table
					+ "(" + COL_ID + " INTEGER PRIMARY KEY"
					+ "," + COL_TIME_SAVE + " integer not null"
					+ "," + COL_JSON + " text not null"
					+ "," + COL_HASH + " text not null"
					+ ")"
			)
			db.execSQL(
				"create unique index if not exists " + table + "_hash on " + table + "(" + COL_HASH + ")"
			)
			db.execSQL(
				"create index if not exists " + table + "_time on " + table + "(" + COL_TIME_SAVE + ")"
			)
		}
		
		override fun onDBUpgrade(db : SQLiteDatabase, oldVersion : Int, newVersion : Int) {
			if(oldVersion < 12 && newVersion >= 12) {
				onDBCreate(db)
			}
		}
		
		private fun deleteOld(now : Long) {
			try {
				// 古いデータを掃除する
				val expire = now - 86400000L * 30
				App1.database.delete(table, "$COL_TIME_SAVE<?", arrayOf(expire.toString()))
			} catch(ex : Throwable) {
				log.e(ex, "deleteOld failed.")
			}
			
		}
		
		fun save(now : Long, json : JSONObject) {
			
			deleteOld(now)
			
			try {
				// make hash
				val sb = StringBuilder()
				val keys = ArrayList<String>()
				val it = json.keys()
				while(it.hasNext()) {
					keys.add(it.next())
				}
				keys.sort()
				for(k in keys) {
					val v = if(json.isNull(k)) "(null)" else json.opt(k).toString()
					sb.append("&")
					sb.append(k)
					sb.append("=")
					sb.append(v)
				}
				val hash = sb.toString().digestSHA256Hex()
				
				// save to db
				val cv = ContentValues()
				cv.put(COL_TIME_SAVE, now)
				cv.put(COL_JSON, json.toString())
				cv.put(COL_HASH, hash)
				App1.database.replace(table, null, cv)
			} catch(ex : Throwable) {
				log.e(ex, "save failed.")
			}
			
		}
		
		fun hasDraft() : Boolean {
			try {
				App1.database.query(table, arrayOf("count(*)"), null, null, null, null, null)
					.use { cursor ->
						if(cursor.moveToNext()) {
							val count = cursor.getInt(0)
							return count > 0
						}
					}
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "hasDraft failed.")
			}
			
			return false
		}
		
		fun createCursor() : Cursor? {
			try {
				return App1.database.query(
					table,
					null,
					null,
					null,
					null,
					null,
					"$COL_TIME_SAVE desc"
				)
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "createCursor failed.")
			}
			
			return null
		}
		
		fun loadFromCursor(cursor : Cursor, colIdxArg : ColIdx?, position : Int) : PostDraft? {
			val colIdx = colIdxArg ?: ColIdx(cursor)
			
			if(! cursor.moveToPosition(position)) {
				log.d("loadFromCursor: move failed. position=%s", position)
				return null
			}
			
			val dst = PostDraft()
			dst.id = cursor.getLong(colIdx.idx_id)
			dst.time_save = cursor.getLong(colIdx.idx_time_save)
			try {
				dst.json = cursor.getString(colIdx.idx_json).toJsonObject()
			} catch(ex : Throwable) {
				log.trace(ex)
				dst.json = JSONObject()
			}
			
			dst.hash = cursor.getString(colIdx.idx_hash)
			return dst
		}
	}
	
}
