package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

import org.json.JSONObject

import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.util.LogCategory

object ClientInfo {
	private val log = LogCategory("ClientInfo")
	
	const val table = "client_info2"
	private const val COL_HOST = "h"
	private const val COL_CLIENT_NAME = "cn"
	private const val COL_RESULT = "r"
	
	fun onDBCreate(db : SQLiteDatabase) {
		db.execSQL(
			"create table if not exists " + table
				+ "(_id INTEGER PRIMARY KEY"
				+ ",h text not null"
				+ ",cn text not null"
				+ ",r text not null"
				+ ")"
		)
		db.execSQL(
			"create unique index if not exists " + table + "_host_client_name on " + table + "(h,cn)"
		)
	}
	
	fun onDBUpgrade(db : SQLiteDatabase, oldVersion : Int, newVersion : Int) {
		if(oldVersion <= 18 && newVersion >= 19) {
			onDBCreate(db)
		}
	}
	
	fun load(instance : String, client_name : String) : JSONObject? {
		try {
			App1.database.query(table, null, "h=? and cn=?", arrayOf(instance, client_name), null, null, null)
				.use { cursor ->
					if(cursor.moveToFirst()) {
						return JSONObject(cursor.getString(cursor.getColumnIndex(COL_RESULT)))
					}
					
				}
		} catch(ex : Throwable) {
			log.e(ex, "load failed.")
		}
		
		return null
	}
	
	fun save(instance : String, client_name : String, json : String) {
		try {
			val cv = ContentValues()
			cv.put(COL_HOST, instance)
			cv.put(COL_CLIENT_NAME, client_name)
			cv.put(COL_RESULT, json)
			App1.database.replace(table, null, cv)
		} catch(ex : Throwable) {
			log.e(ex, "save failed.")
		}
		
	}
	
	// 単体テスト用。インスタンス名を指定して削除する
	internal fun delete(instance : String) {
		try {
			App1.database.delete(table, "$COL_HOST=?", arrayOf(instance))
		} catch(ex : Throwable) {
			log.e(ex, "delete failed.")
		}
	}
}
