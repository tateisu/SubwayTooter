package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns

import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.util.LogCategory

object SubscriptionServerKey : TableCompanion {
	
	private val log = LogCategory("ServerKey")
	
	private const val table = "subscription_server_key2"
	private const val COL_ID = BaseColumns._ID
	private const val COL_CLIENT_IDENTIFIER = "ci"
	private const val COL_SERVER_KEY = "sk"
	
	private val findColumns = arrayOf(COL_SERVER_KEY)
	
	private val findWhereArgs = object : ThreadLocal<Array<String?>>() {
		override fun initialValue() : Array<String?> {
			return arrayOfNulls(1)
		}
	}
	
	override fun onDBCreate(db : SQLiteDatabase) {
		log.d("onDBCreate!")
		db.execSQL(
			"create table if not exists $table"
				+ "($COL_ID INTEGER PRIMARY KEY"
				+ ",$COL_CLIENT_IDENTIFIER text not null"
				+ ",$COL_SERVER_KEY text not null"
				+ ")"
		)
		db.execSQL(
			"create unique index if not exists ${table}_ti on $table($COL_CLIENT_IDENTIFIER)"
		)
	}
	
	override fun onDBUpgrade(db : SQLiteDatabase, oldVersion : Int, newVersion : Int) {
		if(oldVersion < 26 && newVersion >= 26) {
			onDBCreate(db)
		}
	}
	
	fun find(clientIdentifier : String) : String? {
		try {
			val whereArgs = findWhereArgs.get()
			whereArgs[0] = clientIdentifier
			App1.database.query(
				table,
				findColumns,
				"$COL_CLIENT_IDENTIFIER=?",
				whereArgs,
				null,
				null,
				null
			)?.use { cursor ->
				if(cursor.moveToNext()) {
					val idx = cursor.getColumnIndex(COL_SERVER_KEY)
					return cursor.getString(idx)
				}
			}
		} catch(ex : Throwable) {
			log.e(ex, "query failed.")
			log.trace(ex)
		}
		return null
	}
	
	fun save(clientIdentifier : String, serverKey : String) {
		try {
			val cv = ContentValues()
			cv.put(COL_CLIENT_IDENTIFIER, clientIdentifier)
			cv.put(COL_SERVER_KEY, serverKey)
			App1.database.replace(table, null, cv)
		} catch(ex : Throwable) {
			log.e(ex, "save failed.")
			log.trace(ex)
		}
	}
	
}
