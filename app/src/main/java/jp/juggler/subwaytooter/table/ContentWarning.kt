package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.global.appDatabase
import jp.juggler.util.*

object ContentWarning : TableCompanion {
    private val log = LogCategory("ContentWarning")

    override val table = "content_warning"

    val columnList: ColumnMeta.List = ColumnMeta.List(table, 0).apply {
        ColumnMeta(this, 0, BaseColumns._ID, "INTEGER PRIMARY KEY", primary = true)
        deleteBeforeCreate = true
        createExtra = {
            arrayOf(
                "create unique index if not exists ${table}_status_uri on $table($COL_STATUS_URI)",
                "create index if not exists ${table}_time_save on $table($COL_TIME_SAVE)",
            )
        }
    }
    private val COL_STATUS_URI = ColumnMeta(columnList, 0, "su", "text not null")
    private val COL_SHOWN = ColumnMeta(columnList, 0, "sh", "integer not null")
    private val COL_TIME_SAVE = ColumnMeta(columnList, 0, "time_save", "integer default 0")

    private val projection_shown = arrayOf(COL_SHOWN.name)

    override fun onDBCreate(db: SQLiteDatabase) =
        columnList.onDBCreate(db)

    override fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 特定バージョンと交差したらテーブルを削除して作り直す
        fun intersect(x: Int) = (oldVersion < x && newVersion >= x)
        if (intersect(36) || intersect(31) || intersect(5)) {
            columnList.onDBCreate(db)
        }
    }

    fun deleteOld(now: Long) {
        try {
            // 古いデータを掃除する
            val expire = now - 86400000L * 365
            appDatabase.delete(table, "$COL_TIME_SAVE<?", arrayOf(expire.toString()))
        } catch (ex: Throwable) {
            log.e(ex, "deleteOld failed.")
        }
    }

    private fun saveImpl(uri: String, isShown: Boolean) {
        try {
            ContentValues().apply {
                put(COL_STATUS_URI, uri)
                put(COL_SHOWN, isShown)
                put(COL_TIME_SAVE, System.currentTimeMillis())
            }.let { appDatabase.replace(table, null, it) }
        } catch (ex: Throwable) {
            log.e(ex, "save failed.")
        }
    }

    private fun isShownImpl(uri: String, defaultValue: Boolean): Boolean {
        try {
            appDatabase.query(
                table,
                projection_shown,
                "$COL_STATUS_URI=?",
                arrayOf(uri),
                null,
                null,
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getBoolean(COL_SHOWN)
                }
            }
        } catch (ex: Throwable) {
            log.e(ex, "load failed.")
        }

        return defaultValue
    }

    fun save(uri: String, isShown: Boolean) =
        saveImpl(uri, isShown)

    fun isShown(uri: String, defaultValue: Boolean) =
        isShownImpl(uri, defaultValue)

    fun save(status: TootStatus, isShown: Boolean) =
        saveImpl(status.uri, isShown)

    fun isShown(status: TootStatus, defaultValue: Boolean) =
        isShownImpl(status.uri, defaultValue)
}
