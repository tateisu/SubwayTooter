package jp.juggler.subwaytooter.table

import android.annotation.SuppressLint
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import jp.juggler.util.*
import jp.juggler.util.data.*
import jp.juggler.util.log.LogCategory

class PostDraft(
    var id: Long = 0,
    var time_save: Long = 0,
    var json: JsonObject? = null,
    var hash: String? = null,
) {

    companion object : TableCompanion {

        private val log = LogCategory("PostDraft")

        override val table = "post_draft"
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
    }

    class ColIdx(cursor: Cursor) {
        private val idxId = cursor.getColumnIndex(COL_ID)
        private val idxTimeSave = cursor.getColumnIndex(COL_TIME_SAVE)
        private val idxJson = cursor.getColumnIndex(COL_JSON)
        private val idxHash = cursor.getColumnIndex(COL_HASH)
        fun readRow(cursor: Cursor) = PostDraft(
            id = cursor.getLong(idxId),
            time_save = cursor.getLong(idxTimeSave),
            hash = cursor.getString(idxHash),
            json = try {
                cursor.getString(idxJson).decodeJsonObject()
            } catch (ex: Throwable) {
                log.e(ex, "loadFromCursor failed.")
                JsonObject()
            }
        )
    }

    class Access(val db: SQLiteDatabase) {

        private fun deleteOld(now: Long) {
            try {
                // 古いデータを掃除する
                val expire = now - 86400000L * 30
                db.delete(table, "$COL_TIME_SAVE<?", arrayOf(expire.toString()))
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
                db.replace(table, null, ContentValues().apply {
                    put(COL_TIME_SAVE, now)
                    put(COL_JSON, json.toString())
                    put(COL_HASH, hash)
                })
            } catch (ex: Throwable) {
                log.e(ex, "save failed.")
            }
        }

        fun delete(item: PostDraft) {
            try {
                db.deleteById(table, item.id.toString(), COL_ID)
            } catch (ex: Throwable) {
                log.e(ex, "delete failed.")
            }
        }

        fun hasDraft(): Boolean {
            try {
                db.query(table, arrayOf("count(*)"), null, null, null, null, null)
                    .use { cursor ->
                        if (cursor.moveToNext()) {
                            val count = cursor.getInt(0)
                            return count > 0
                        }
                    }
            } catch (ex: Throwable) {
                log.e(ex, "hasDraft failed.")
            }
            return false
        }

        // caller must close the cursor
        @SuppressLint("Recycle")
        fun createCursor(): Cursor =
            db.queryAll(table, "$COL_TIME_SAVE desc")!!

        fun loadFromCursor(
            cursor: Cursor,
            colIdxArg: ColIdx?,
            position: Int,
        ): PostDraft? = when {
            cursor.moveToPosition(position) ->
                (colIdxArg ?: ColIdx(cursor)).readRow(cursor)
            else -> {
                log.d("loadFromCursor: move failed. position=$position")
                null
            }
        }
    }
}
