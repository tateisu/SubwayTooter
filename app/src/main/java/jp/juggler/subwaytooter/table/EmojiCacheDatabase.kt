package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import jp.juggler.util.data.TableCompanion
import jp.juggler.util.data.getBlobOrNull
import jp.juggler.util.data.getLong
import jp.juggler.util.log.LogCategory
import java.util.concurrent.TimeUnit

private val log = LogCategory("EmojiCacheDatabase")

// カスタム絵文字のキャッシュ専用のデータベースファイルを作る
// (DB破損などの際に削除してしまえるようにする)
private const val CACHE_DB_NAME = "emoji_cache_db"
private const val CACHE_DB_VERSION = 1

class EmojiCache(
    val id: Long,
    val timeUsed: Long,
    val data: ByteArray,
) {
    companion object : TableCompanion {

        override val table = "custom_emoji_cache"

        const val COL_ID = BaseColumns._ID
        const val COL_TIME_SAVE = "time_save"
        const val COL_TIME_USED = "time_used"
        const val COL_URL = "url"
        const val COL_DATA = "data"

        override fun onDBCreate(db: SQLiteDatabase) {
            db.execSQL(
                """create table if not exists $table
						($COL_ID INTEGER PRIMARY KEY
						,$COL_TIME_SAVE integer not null
						,$COL_TIME_USED integer not null
						,$COL_URL text not null
						,$COL_DATA blob not null
						)""".trimIndent()
            )
            db.execSQL("create unique index if not exists ${table}_url on $table($COL_URL)")
            db.execSQL("create index if not exists ${table}_old on $table($COL_TIME_USED)")
        }

        override fun onDBUpgrade(
            db: SQLiteDatabase,
            oldVersion: Int,
            newVersion: Int,
        ) {
        }
    }

    class Access(val db: SQLiteDatabase) {
        fun load(url: String, now: Long) =
            db.rawQuery(
                "select $COL_ID,$COL_TIME_USED,$COL_DATA from $table where $COL_URL=?",
                arrayOf(url)
            )?.use { cursor ->
                if (cursor.moveToNext()) {
                    EmojiCache(
                        id = cursor.getLong(COL_ID),
                        timeUsed = cursor.getLong(COL_TIME_USED),
                        data = cursor.getBlobOrNull(COL_DATA)!!
                    ).apply {
                        if (now - timeUsed >= 5 * 3600000L) {
                            db.update(
                                table,
                                ContentValues().apply {
                                    put(COL_TIME_USED, now)
                                },
                                "$COL_ID=?",
                                arrayOf(id.toString())
                            )
                        }
                    }
                } else {
                    null
                }
            }

        fun sweep(now: Long) {
            val expire = now - TimeUnit.DAYS.toMillis(30)
            db.delete(
                table,
                "$COL_TIME_USED < ?",
                arrayOf(expire.toString())
            )
        }

        fun update(url: String, data: ByteArray) {
            val now = System.currentTimeMillis()
            db.replace(table,
                null,
                ContentValues().apply {
                    put(COL_URL, url)
                    put(COL_DATA, data)
                    put(COL_TIME_USED, now)
                    put(COL_TIME_SAVE, now)
                }
            )
        }
    }
}

class EmojiCacheDbOpenHelper(val context: Context) :
    SQLiteOpenHelper(context, CACHE_DB_NAME, null, CACHE_DB_VERSION) {

    private val tables = arrayOf(EmojiCache)
    override fun onCreate(db: SQLiteDatabase) =
        tables.forEach { it.onDBCreate(db) }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
        tables.forEach { it.onDBUpgrade(db, oldVersion, newVersion) }

    fun deleteDatabase() {
        try {
            close()
        } catch (ex: Throwable) {
            log.e(ex, "deleteDatabase: close() failed.")
        }
        try {
            SQLiteDatabase.deleteDatabase(context.getDatabasePath(databaseName))
        } catch (ex: Throwable) {
            log.e(ex, "deleteDatabase failed.")
        }
    }

    // DB処理を行い、SQLiteDatabaseCorruptExceptionを検出したらDBを削除してリトライする
    fun <T : Any> access(block: EmojiCache.Access.() -> T?): T? {
        for (nTry in 0 until 3) {
            try {
                val db = writableDatabase
                if (db == null) {
                    log.e("access[$nTry]: writableDatabase returns null.")
                    break
                }
                return EmojiCache.Access(db).block()
            } catch (ex: SQLiteDatabaseCorruptException) {
                log.e(ex, "access[$nTry]: db corrupt!")
                deleteDatabase()
            } catch (ex: Throwable) {
                log.e(ex, "access[$nTry]: failed.")
                break
            }
        }
        return null
    }
}
