package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import jp.juggler.subwaytooter.App1
import jp.juggler.util.JsonObject
import jp.juggler.util.LogCategory
import jp.juggler.util.getString
import jp.juggler.util.decodeJsonObject

object ClientInfo : TableCompanion {
    private val log = LogCategory("ClientInfo")

    const val table = "client_info2"
    private const val COL_HOST = "h"
    private const val COL_CLIENT_NAME = "cn"
    private const val COL_RESULT = "r"

    override fun onDBCreate(db: SQLiteDatabase) {
        db.execSQL(
            """create table if not exists $table
            (_id INTEGER PRIMARY KEY
            ,$COL_HOST text not null
            ,$COL_CLIENT_NAME text not null
            ,$COL_RESULT text not null
            )""".trimIndent()
        )
        db.execSQL("create unique index if not exists ${table}_host_client_name on $table($COL_HOST,$COL_CLIENT_NAME)")
    }

    override fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion <= 18 && newVersion >= 19) {
            onDBCreate(db)
        }
    }

    fun load(instance: String, clientName: String): JsonObject? {
        try {
            App1.database.query(table, null, "h=? and cn=?", arrayOf(instance, clientName), null, null, null)
                .use { cursor ->
                    if (cursor.moveToFirst()) {
                        return cursor.getString(COL_RESULT).decodeJsonObject()
                    }
                }
        } catch (ex: Throwable) {
            log.e(ex, "load failed.")
        }

        return null
    }

    fun save(instance: String, clientName: String, json: String) {
        try {
            val cv = ContentValues()
            cv.put(COL_HOST, instance)
            cv.put(COL_CLIENT_NAME, clientName)
            cv.put(COL_RESULT, json)
            App1.database.replace(table, null, cv)
        } catch (ex: Throwable) {
            log.e(ex, "save failed.")
        }
    }

    // 単体テスト用。インスタンス名を指定して削除する
    fun delete(instance: String, clientName: String) {
        try {
            App1.database.delete(table, "$COL_HOST=? and $COL_CLIENT_NAME=?", arrayOf(instance, clientName))
        } catch (ex: Throwable) {
            log.e(ex, "delete failed.")
        }
    }
}
