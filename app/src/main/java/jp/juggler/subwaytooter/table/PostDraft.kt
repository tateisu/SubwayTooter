package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import jp.juggler.subwaytooter.App1
import jp.juggler.util.*

class PostDraft {

    var id: Long = 0
    var time_save: Long = 0
    var json: JsonObject? = null
    var hash: String? = null

    class ColIdx(cursor: Cursor) {
        internal val idx_id: Int
        internal val idx_time_save: Int
        internal val idx_json: Int
        internal val idx_hash: Int

        init {
            idx_id = cursor.getColumnIndex(COL_ID)
            idx_time_save = cursor.getColumnIndex(COL_TIME_SAVE)
            idx_json = cursor.getColumnIndex(COL_JSON)
            idx_hash = cursor.getColumnIndex(COL_HASH)
        }
    }

    fun delete() {
        try {
            App1.database.delete(table, "$COL_ID=?", arrayOf(id.toString()))
        } catch (ex: Throwable) {
            log.e(ex, "delete failed.")
        }
    }

    companion object : TableCompanion {

        private val log = LogCategory("PostDraft")

        private const val table = "post_draft"
        private const val COL_ID = BaseColumns._ID
        private const val COL_TIME_SAVE = "time_save"
        private const val COL_JSON = "json"
        private const val COL_HASH = "hash"

        override fun onDBCreate(db: SQLiteDatabase) {
            log.d("onDBCreate!")
            db.execSQL(
                """create table if not exists $table
                ($COL_ID INTEGER PRIMARY KEY
                ,$COL_TIME_SAVE integer not null
                ,$COL_JSON text not null
                ,$COL_HASH text not null
                )""".trimIndent()
            )
            db.execSQL("create unique index if not exists ${table}_hash on $table($COL_HASH)")
            db.execSQL("create index if not exists ${table}_time on $table($COL_TIME_SAVE)")
        }

        override fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 12 && newVersion >= 12) {
                onDBCreate(db)
            }
        }

        private fun deleteOld(now: Long) {
            try {
                // 古いデータを掃除する
                val expire = now - 86400000L * 30
                App1.database.delete(table, "$COL_TIME_SAVE<?", arrayOf(expire.toString()))
            } catch (ex: Throwable) {
                log.e(ex, "deleteOld failed.")
            }
        }

        fun save(now: Long, json: JsonObject) {

            deleteOld(now)

            try {
                // make hash
                val hash = StringBuilder().also { sb ->
                    json.keys.sorted().forEach { k ->
                        val v = json[k]?.toString() ?: "(null)"
                        sb.append("&")
                        sb.append(k)
                        sb.append("=")
                        sb.append(v)
                    }
                }.toString().digestSHA256Hex()

                // save to db
                App1.database.replace(table, null, ContentValues().apply {
                    put(COL_TIME_SAVE, now)
                    put(COL_JSON, json.toString())
                    put(COL_HASH, hash)
                })
            } catch (ex: Throwable) {
                log.e(ex, "save failed.")
            }
        }

        fun hasDraft(): Boolean {
            try {
                App1.database.query(table, arrayOf("count(*)"), null, null, null, null, null)
                    .use { cursor ->
                        if (cursor.moveToNext()) {
                            val count = cursor.getInt(0)
                            return count > 0
                        }
                    }
            } catch (ex: Throwable) {
                log.trace(ex, "hasDraft failed.")
            }
            return false
        }

        fun createCursor(): Cursor? {
            return try {
                App1.database.query(
                    table,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "$COL_TIME_SAVE desc"
                )
            } catch (ex: Throwable) {
                log.trace(ex, "createCursor failed.")
                null
            }
        }

        fun loadFromCursor(cursor: Cursor, colIdxArg: ColIdx?, position: Int): PostDraft? {
            return if (!cursor.moveToPosition(position)) {
                log.d("loadFromCursor: move failed. position=$position")
                null
            } else {
                PostDraft().also { dst ->
                    val colIdx = colIdxArg ?: ColIdx(cursor)
                    dst.id = cursor.getLong(colIdx.idx_id)
                    dst.time_save = cursor.getLong(colIdx.idx_time_save)
                    dst.hash = cursor.getString(colIdx.idx_hash)
                    dst.json = try {
                        cursor.getString(colIdx.idx_json).decodeJsonObject()
                    } catch (ex: Throwable) {
                        log.trace(ex)
                        JsonObject()
                    }
                }
            }
        }
    }
}
