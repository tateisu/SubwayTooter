package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

import org.json.JSONObject

import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.api.entity.notEmptyOrThrow
import jp.juggler.subwaytooter.util.*

class HighlightWord {
	
	companion object {
		
		private val log = LogCategory("HighlightWord")
		
		const val SOUND_TYPE_NONE = 0
		const val SOUND_TYPE_DEFAULT = 1
		const val SOUND_TYPE_CUSTOM = 2
		
		const val table = "highlight_word"
		const val COL_ID = "_id"
		const val COL_NAME = "name"
		private const val COL_TIME_SAVE = "time_save"
		private const val COL_COLOR_BG = "color_bg"
		private const val COL_COLOR_FG = "color_fg"
		private const val COL_SOUND_TYPE = "sound_type"
		private const val COL_SOUND_URI = "sound_uri"
		
		private const val selection_name = COL_NAME + "=?"
		private const val selection_id = COL_ID + "=?"
		
		private val columns_name = arrayOf(COL_NAME)
		
		fun onDBCreate(db : SQLiteDatabase) {
			log.d("onDBCreate!")
			db.execSQL(
				"create table if not exists " + table
					+ "(_id INTEGER PRIMARY KEY"
					+ ",name text not null"
					+ ",time_save integer not null"
					+ ",color_bg integer not null default 0"
					+ ",color_fg integer not null default 0"
					+ ",sound_type integer not null default 1"
					+ ",sound_uri text default null"
					+ ")"
			)
			db.execSQL(
				"create unique index if not exists " + table + "_name on " + table + "(name)"
			)
		}
		
		fun onDBUpgrade(db : SQLiteDatabase, oldVersion : Int, newVersion : Int) {
			if(oldVersion < 21 && newVersion >= 21) {
				onDBCreate(db)
			}
		}
		
		fun load(name : String) : HighlightWord? {
			try {
				App1.database.query(table, null, selection_name, arrayOf(name), null, null, null)
					.use { cursor ->
						if(cursor.moveToNext()) {
							return HighlightWord(cursor)
						}
					}
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			
			return null
		}
		
		fun createCursor() : Cursor {
			return App1.database.query(table, null, null, null, null, null, COL_NAME + " asc")
		}
		
		val nameSet : WordTrieTree?
			get() {
				val dst = WordTrieTree()
				try {
					App1.database.query(table, columns_name, null, null, null, null, null)
						.use { cursor ->
							val idx_name = cursor.getColumnIndex(COL_NAME)
							while(cursor.moveToNext()) {
								val s = cursor.getString(idx_name)
								dst.add(s)
							}
							
						}
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
				return if(dst.isEmpty) null else dst
			}
	}
	
	var id = - 1L
	var name : String
	var color_bg : Int = 0
	var color_fg : Int = 0
	var sound_type : Int = 0
	var sound_uri : String? = null
	
	fun encodeJson() : JSONObject {
		val dst = JSONObject()
		dst.put(COL_ID, id)
		dst.put(COL_NAME, name)
		dst.put(COL_COLOR_BG, color_bg)
		dst.put(COL_COLOR_FG, color_fg)
		dst.put(COL_SOUND_TYPE, sound_type)
		if(sound_uri != null) dst.put(COL_SOUND_URI, sound_uri)
		return dst
	}
	
	constructor(src : JSONObject) {
		this.id = src.parseLong( COL_ID) ?: -1L
		this.name = src.notEmptyOrThrow(COL_NAME)
		this.color_bg = src.optInt(COL_COLOR_BG)
		this.color_fg = src.optInt(COL_COLOR_FG)
		this.sound_type = src.optInt(COL_SOUND_TYPE)
		this.sound_uri = src.parseString( COL_SOUND_URI)
	}
	
	constructor(name : String) {
		this.name = name
		this.sound_type = SOUND_TYPE_DEFAULT
		this.color_fg = - 0x10000
	}
	
	constructor(cursor : Cursor) {
		this.id = cursor.getLong(cursor.getColumnIndex(COL_ID))
		this.name = cursor.getString(cursor.getColumnIndex(COL_NAME))
		this.color_bg = cursor.getInt(cursor.getColumnIndex(COL_COLOR_BG))
		this.color_fg = cursor.getInt(cursor.getColumnIndex(COL_COLOR_FG))
		this.sound_type = cursor.getInt(cursor.getColumnIndex(COL_SOUND_TYPE))
		val colIdx_sound_uri = cursor.getColumnIndex(COL_SOUND_URI)
		this.sound_uri =
			if(cursor.isNull(colIdx_sound_uri)) null else cursor.getString(colIdx_sound_uri)
	}
	
	fun save() {
		if(name.isEmpty()) throw RuntimeException("HighlightWord.save(): name is empty")
		
		try {
			val cv = ContentValues()
			cv.put(COL_NAME, name)
			cv.put(COL_TIME_SAVE, System.currentTimeMillis())
			cv.put(COL_COLOR_BG, color_bg)
			cv.put(COL_COLOR_FG, color_fg)
			cv.put(COL_SOUND_TYPE, sound_type)
			val sound_uri = this.sound_uri
			if(sound_uri?.isEmpty() != false) {
				cv.putNull(COL_SOUND_URI)
			} else {
				cv.put(COL_SOUND_URI, sound_uri)
			}
			if(id == - 1L) {
				App1.database.replace(table, null, cv)
			} else {
				App1.database.update(table, cv, selection_id, arrayOf(id.toString()))
			}
		} catch(ex : Throwable) {
			log.e(ex, "save failed.")
		}
		
	}
	
	fun delete() {
		try {
			App1.database.delete(table, selection_id, arrayOf(id.toString()))
		} catch(ex : Throwable) {
			log.e(ex, "delete failed.")
		}
	}
	
}
