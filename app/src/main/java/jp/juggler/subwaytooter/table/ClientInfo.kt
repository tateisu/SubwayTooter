package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import jp.juggler.subwaytooter.App1
import jp.juggler.util.*

object ClientInfo : TableCompanion {
    private val log = LogCategory("ClientInfo")

    override val table = "client_info2"
    val columnList: ColumnMeta.List = ColumnMeta.List(table, 19).apply {
        ColumnMeta(this, 0, BaseColumns._ID, "INTEGER PRIMARY KEY", primary = true)
        createExtra = {
            arrayOf(
                "create unique index if not exists ${table}_host_client_name on $table($COL_HOST,$COL_CLIENT_NAME)"
            )
        }
    }
    private val COL_HOST = ColumnMeta(columnList, 0, "h", "text not null")
    private val COL_CLIENT_NAME = ColumnMeta(columnList, 0, "cn", "text not null")
    private val COL_RESULT = ColumnMeta(columnList, 0, "r", "text not null")

    override fun onDBCreate(db: SQLiteDatabase) =
        columnList.onDBCreate(db)

    override fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
        columnList.onDBUpgrade(db, oldVersion, newVersion)

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
