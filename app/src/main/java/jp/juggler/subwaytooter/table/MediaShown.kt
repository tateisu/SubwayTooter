package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.util.*

object MediaShown : TableCompanion {
    private val log = LogCategory("MediaShown")

    private const val table = "media_shown_misskey"

    val columnList: ColumnMeta.List = ColumnMeta.List(table, 30).apply {
        ColumnMeta(this, 0, BaseColumns._ID, "INTEGER PRIMARY KEY", primary = true)
        deleteBeforeCreate = true
        createExtra = {
            arrayOf(
                "create unique index if not exists ${table}_status_id on $table($COL_HOST,$COL_STATUS_ID)",
                "create index if not exists ${table}_time_save on $table($COL_TIME_SAVE)",
            )
        }
    }
    private val COL_HOST = ColumnMeta(columnList, 0, "h", "")
    private val COL_STATUS_ID = ColumnMeta(columnList, 0, "si", "")
    private val COL_SHOWN = ColumnMeta(columnList, 0, "sh", "")
    private val COL_TIME_SAVE = ColumnMeta(columnList, 0, "time_save", "")

    private val projection_shown = arrayOf(COL_SHOWN.name)

    override fun onDBCreate(db: SQLiteDatabase) =
        columnList.onDBCreate(db)

    override fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        columnList.onDBUpgrade(db, oldVersion, newVersion)

        // 特定バージョンを交差したらテーブルを作り直す
        if (oldVersion < 30 && newVersion >= 30) {
            columnList.onDBCreate(db)
        }
    }

    fun deleteOld(now: Long) {
        try {
            val expire = now - 86400000L * 365
            App1.database.delete(table, "$COL_TIME_SAVE<?", arrayOf(expire.toString()))
        } catch (ex: Throwable) {
            log.e(ex, "deleteOld failed.")
        }

        // 旧型式のテーブルも古い項目の削除だけ行う
        try {
            val table = "media_shown"
            val COL_TIME_SAVE = "time_save"
            val expire = now - 86400000L * 365
            App1.database.delete(table, "$COL_TIME_SAVE<?", arrayOf(expire.toString()))
        } catch (ignored: Throwable) {
        }
    }

    private fun saveImpl(host: String, id: String, isShown: Boolean) {
        try {
            ContentValues().apply {
                put(COL_HOST, host)
                put(COL_STATUS_ID, id)
                put(COL_SHOWN, isShown)
                put(COL_TIME_SAVE, System.currentTimeMillis())
            }.let { App1.database.replace(table, null, it) }
        } catch (ex: Throwable) {
            log.e(ex, "saveImpl failed.")
        }
    }

    private fun isShownImpl(host: String, id: String, defaultValue: Boolean): Boolean {
        try {
            App1.database.query(
                table,
                projection_shown,
                "h=? and si=?",
                arrayOf(host, id),
                null,
                null,
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) return cursor.getBoolean(COL_SHOWN)
            }
        } catch (ex: Throwable) {
            log.e(ex, "isShownImpl failed.")
        }
        return defaultValue
    }

    fun save(uri: String, isShown: Boolean) =
        saveImpl(uri, uri, isShown)

    fun isShown(uri: String, defaultValue: Boolean) =
        isShownImpl(uri, uri, defaultValue)

    fun save(status: TootStatus, isShown: Boolean) =
        saveImpl(status.hostAccessOrOriginal.ascii, status.id.toString(), isShown)

    fun isShown(status: TootStatus, defaultValue: Boolean) =
        isShownImpl(status.hostAccessOrOriginal.ascii, status.id.toString(), defaultValue)
}
