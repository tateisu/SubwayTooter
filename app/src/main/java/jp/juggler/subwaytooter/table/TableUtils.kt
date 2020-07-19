package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

// SQLite にBooleanをそのまま保存することはできないのでInt型との変換が必要になる

// boolean to integer
fun Boolean.b2i() = if(this) 1 else 0

// integer to boolean
fun Int.i2b() = this != 0

fun Cursor.getBoolean(keyIdx : Int) =
	getInt(keyIdx).i2b()

fun Cursor.getBoolean(key : String) =
	getBoolean(getColumnIndex(key))

interface TableCompanion {
	fun onDBCreate(db : SQLiteDatabase)
	fun onDBUpgrade(db : SQLiteDatabase, oldVersion : Int, newVersion : Int)
}

fun ContentValues.putOrNull(key : String, value : String?) =
	if(value == null) putNull(key) else put(key, value)
