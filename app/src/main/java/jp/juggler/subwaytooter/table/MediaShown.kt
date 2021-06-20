package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.util.LogCategory
import jp.juggler.util.getInt

object MediaShown : TableCompanion {
    private val log = LogCategory("MediaShown")

    private const val table = "media_shown_misskey"
    private const val COL_HOST = "h"
    private const val COL_STATUS_ID = "si"
    private const val COL_SHOWN = "sh"
    private const val COL_TIME_SAVE = "time_save"

    private val projection_shown = arrayOf(COL_SHOWN)

    override fun onDBCreate(db: SQLiteDatabase) {
        log.d("onDBCreate!")
        db.execSQL(
            """
			create table if not exists $table
			(_id INTEGER PRIMARY KEY
			,$COL_HOST text not null
			,$COL_STATUS_ID text not null
			,$COL_SHOWN integer not null
			,$COL_TIME_SAVE integer default 0
			)
			""".trimIndent()
        )
        db.execSQL(
            "create unique index if not exists ${table}_status_id on $table($COL_HOST,$COL_STATUS_ID)"
        )
    }

    override fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 30 && newVersion >= 30) {
            db.execSQL("drop table if exists $table")
            onDBCreate(db)
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

    fun isShown(uri: String, defaultValue: Boolean): Boolean {
        try {
            App1.database.query(
                table,
                projection_shown,
                "h=? and si=?",
                arrayOf(uri, uri),
                null,
                null,
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    return 0 != cursor.getInt(COL_SHOWN)
                }
            }
        } catch (ex: Throwable) {
            log.e(ex, "load failed.")
        }

        return defaultValue
    }

    fun isShown(status: TootStatus, defaultValue: Boolean): Boolean {
        try {
            App1.database.query(
                table,
                projection_shown,
                "h=? and si=?",
                arrayOf(status.hostAccessOrOriginal.ascii, status.id.toString()),
                null,
                null,
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    return 0 != cursor.getInt(COL_SHOWN)
                }
            }
        } catch (ex: Throwable) {
            log.e(ex, "load failed.")
        }

        return defaultValue
    }

    fun save(uri: String, isShown: Boolean) {
        try {
            val now = System.currentTimeMillis()
            val cv = ContentValues()
            cv.put(COL_HOST, uri)
            cv.put(COL_STATUS_ID, uri)
            cv.put(COL_SHOWN, isShown.b2i())
            cv.put(COL_TIME_SAVE, now)
            App1.database.replace(table, null, cv)
        } catch (ex: Throwable) {
            log.e(ex, "save failed.")
        }
    }

    fun save(status: TootStatus, isShown: Boolean) {
        try {
            val now = System.currentTimeMillis()
            val cv = ContentValues()
            cv.put(COL_HOST, status.hostAccessOrOriginal.ascii)
            cv.put(COL_STATUS_ID, status.id.toString())
            cv.put(COL_SHOWN, isShown.b2i())
            cv.put(COL_TIME_SAVE, now)
            App1.database.replace(table, null, cv)
        } catch (ex: Throwable) {
            log.e(ex, "save failed.")
        }
    }
}
