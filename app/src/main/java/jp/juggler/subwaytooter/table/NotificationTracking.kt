package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.putMayNull
import jp.juggler.util.data.TableCompanion
import jp.juggler.util.data.getLong
import jp.juggler.util.data.minComparable
import jp.juggler.util.data.replaceTo
import jp.juggler.util.log.LogCategory
import java.util.concurrent.ConcurrentHashMap

class NotificationTracking {

    private var dirty = false

    private var id = -1L
        set(value) {
            dirty = true; field = value
        }

    private var accountDbId: Long = 0
        set(value) {
            dirty = true; field = value
        }

    private var notificationType: String = ""
        set(value) {
            dirty = true; field = value
        }

    var nid_read: EntityId? = null
        set(value) {
            dirty = true; field = value
        }

    var nid_show: EntityId? = null
        set(value) {
            dirty = true; field = value
        }

    var post_id: EntityId? = null
        set(value) {
            dirty = true; field = value
        }

    var post_time: Long = 0
        set(value) {
            dirty = true; field = value
        }

    companion object : TableCompanion {

        private val log = LogCategory("NotificationTracking")

        override val table = "noti_trac"

        private const val COL_ID = BaseColumns._ID

        // アカウントDBの行ID。 サーバ側のIDではない
        private const val COL_ACCOUNT_DB_ID = "a"

        // 通知ID。ここまで既読
        private const val COL_NID_READ = "nr"

        // 通知ID。もっとも最近取得したもの
        private const val COL_NID_SHOW = "ns"

        // 最後に表示した通知のID
        private const val COL_POST_ID = "pi"

        // 最後に表示した通知の作成時刻
        private const val COL_POST_TIME = "pt"

        // 返信だけ通知グループを分ける
        private const val COL_NOTIFICATION_TYPE = "nt"

        override fun onDBCreate(db: SQLiteDatabase) {

            db.execSQL(
                """
				create table if not exists $table
				($COL_ID INTEGER PRIMARY KEY
				,$COL_ACCOUNT_DB_ID integer not null
				,$COL_NID_READ text
				,$COL_NID_SHOW text
				,$COL_POST_ID text
				,$COL_POST_TIME integer default 0
				,$COL_NOTIFICATION_TYPE text default ''
				)
				"""
            )
            db.execSQL(
                "create unique index if not exists ${table}_b on $table ($COL_ACCOUNT_DB_ID,$COL_NOTIFICATION_TYPE)"
            )
        }

        override fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

            if (newVersion < oldVersion) {
                try {
                    db.execSQL("drop table if exists $table")
                } catch (ex: Throwable) {
                    log.e(ex, "delete DB failed.")
                }
                onDBCreate(db)
                return
            }

            if (oldVersion < 2 && newVersion >= 2) {
                onDBCreate(db)
                return
            }

            if (oldVersion < 40 && newVersion >= 40) {
                try {
                    db.execSQL("drop index if exists ${table}_a")
                } catch (ex: Throwable) {
                    log.e(ex, "drop index failed.")
                }
                try {
                    db.execSQL("alter table $table add column $COL_NOTIFICATION_TYPE text default ''")
                } catch (ex: Throwable) {
                    log.e(ex, "can't add $COL_NOTIFICATION_TYPE")
                }
                try {
                    db.execSQL(
                        "create unique index if not exists ${table}_b on $table ($COL_ACCOUNT_DB_ID,$COL_NOTIFICATION_TYPE)"
                    )
                } catch (ex: Throwable) {
                    log.e(ex, "can't add index.")
                }
            }
        }

        private val cache =
            ConcurrentHashMap<Long, ConcurrentHashMap<String, NotificationTracking>>()

        private fun <K : Any, V : Any> ConcurrentHashMap<K, V>.getOrCreate(
            key: K,
            creator: () -> V,
        ): V {
            var v = this[key]
            if (v == null) v = creator().also { this[key] = it }
            return v
        }

        private fun loadCache(accountDbId: Long, notificationType: String): NotificationTracking? =
            cache[accountDbId]?.get(notificationType)

        private fun clearCache(accountDbId: Long, notificationType: String): NotificationTracking? =
            cache[accountDbId]?.remove(notificationType)

        private fun saveCache(
            accountDbId: Long,
            notificationType: String,
            nt: NotificationTracking,
        ) {
            cache.getOrCreate(accountDbId) {
                ConcurrentHashMap<String, NotificationTracking>()
            }[notificationType] = nt
        }

        /////////////////////////////////////////////////////////////////////////////////

        private const val WHERE_AID = "$COL_ACCOUNT_DB_ID=? and $COL_NOTIFICATION_TYPE=?"
    }

    fun toContentValues() = ContentValues().apply {
        put(COL_ACCOUNT_DB_ID, accountDbId)
        put(COL_NOTIFICATION_TYPE, notificationType)
        nid_read.putMayNull(this, COL_NID_READ)
        nid_show.putMayNull(this, COL_NID_SHOW)
        post_id.putMayNull(this, COL_POST_ID)
        put(COL_POST_TIME, post_time)
    }

    class Access(val db: SQLiteDatabase) {

        fun load(
            // ログ出力だけに使う
            acct: Acct,
            accountDbId: Long,
            notificationType: String,
        ): NotificationTracking {
            loadCache(accountDbId, notificationType)
                ?.takeIf { !it.dirty }
                ?.let {
                    log.d(
                        "${acct.pretty}/$notificationType load-cached. post=(${it.post_id},${it.post_time}), read=${it.nid_read}, show=${it.nid_show}"
                    )
                    return it
                }

            val whereArgs = arrayOf(accountDbId.toString(), notificationType)
            val dst = NotificationTracking()
            dst.accountDbId = accountDbId
            dst.notificationType = notificationType
            try {
                db.rawQuery(
                    "select * from $table where $COL_ACCOUNT_DB_ID=? and $COL_NOTIFICATION_TYPE=?",
                    whereArgs,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        dst.id = cursor.getLong(COL_ID)

                        dst.post_id = EntityId.from(cursor, COL_POST_ID)
                        dst.post_time = cursor.getLong(COL_POST_TIME)

                        val show = EntityId.from(cursor, COL_NID_SHOW)
                        if (show == null) {
                            dst.nid_show = null
                            dst.nid_read = null
                        } else {
                            dst.nid_show = show
                            val read = EntityId.from(cursor, COL_NID_READ)
                            if (read == null) {
                                dst.nid_read = null
                            } else {
                                val r2 = minComparable(show, read)
                                dst.nid_read = r2
                                if (r2 != read) {
                                    log.i("$acct/$notificationType read>show! clip to $show")
                                    val cv = ContentValues()
                                    show.putTo(cv, COL_NID_READ) //変数名とキー名が異なるのに注意
                                    db.update(table, cv, WHERE_AID, whereArgs)
                                }
                            }
                        }

                        log.d(
                            "${acct.pretty}/$notificationType load. post=(${dst.post_id},${dst.post_time}), read=${dst.nid_read}, show=${dst.nid_show}"
                        )
                        saveCache(accountDbId, notificationType, dst)
                    }
                }
            } catch (ex: Throwable) {
                log.e(ex, "${acct.pretty} load failed.")
            } finally {
                dst.dirty = false
            }
            return dst
        }

        fun updateRead(accountDbId: Long, notificationType: String) {
            try {
                val whereArgs = arrayOf(accountDbId.toString(), notificationType)
                db.rawQuery(
                    "select $COL_NID_SHOW, $COL_NID_READ from $table where $COL_ACCOUNT_DB_ID=? and $COL_NOTIFICATION_TYPE=?",
                    whereArgs,
                )?.use { cursor ->
                    when {
                        !cursor.moveToFirst() -> log.e("updateRead[$accountDbId,$notificationType]: can't find the data row.")

                        else -> {
                            val nid_show = EntityId.from(cursor, COL_NID_SHOW)
                            val nid_read = EntityId.from(cursor, COL_NID_READ)
                            when {
                                nid_show == null ->
                                    log.e("updateRead[$accountDbId,$notificationType]: nid_show is null.")

                                nid_read != null && nid_read >= nid_show ->
                                    log.e("updateRead[$accountDbId,$notificationType]: nid_read already updated.")

                                else -> {
                                    log.i("updateRead[$accountDbId,$notificationType]: update nid_read as $nid_show...")
                                    val cv = ContentValues()
                                    nid_show.putTo(cv, COL_NID_READ) //変数名とキー名が異なるのに注意
                                    db.update(table, cv, WHERE_AID, whereArgs)
                                    clearCache(accountDbId, notificationType)
                                }
                            }
                        }
                    }
                }
            } catch (ex: Throwable) {
                log.e(ex, "updateRead[$accountDbId] failed.")
            }
        }

        fun resetPostAll() {
            try {
                val cv = ContentValues()
                cv.putNull(COL_POST_ID)
                cv.put(COL_POST_TIME, 0)
                db.update(table, cv, null, null)
                cache.clear()
            } catch (ex: Throwable) {
                log.e(ex, "resetPostAll failed.")
            }
        }

        // アカウント設定から手動で呼ばれる
        fun resetTrackingState(accountDbId: Long?) {
            accountDbId ?: return
            try {
                db.delete(table, "$COL_ACCOUNT_DB_ID=?", arrayOf(accountDbId.toString()))
                cache.remove(accountDbId)
            } catch (ex: Throwable) {
                log.e(ex, "resetTrackingState failed.")
            }
        }

        fun save(
            // ログ出力だけに使う
            acct: Acct,
            item: NotificationTracking,
        ) {
            try {
                if (!item.dirty) return
                val rowId = item.toContentValues()
                    .replaceTo(db, table)
                if (item.id == -1L) item.id = rowId

                log.d("${acct.pretty}/${item.notificationType} save. post=(${item.post_id},${item.post_time})")
                item.dirty = false
                clearCache(item.accountDbId, item.notificationType)
            } catch (ex: Throwable) {
                log.e(ex, "save failed.")
            }
        }

        fun updatePost(postId: EntityId, postTime: Long, item: NotificationTracking) {
            try {
                item.post_id = postId
                item.post_time = postTime
                if (!item.dirty) return
                val cv = item.toContentValues().apply {
                    postId.putTo(this, COL_POST_ID)
                    put(COL_POST_TIME, postTime)
                }
                val rows = db.update(
                    table,
                    cv,
                    WHERE_AID,
                    arrayOf(item.accountDbId.toString(), item.notificationType)
                )
                log.d("updatePost account_db_id=${item.accountDbId}, nt=${item.notificationType}, post=${postId},${postTime} update_rows=${rows}")
                item.dirty = false
                clearCache(item.accountDbId, item.notificationType)
            } catch (ex: Throwable) {
                log.e(ex, "updatePost failed.")
            }
        }
    }
}
