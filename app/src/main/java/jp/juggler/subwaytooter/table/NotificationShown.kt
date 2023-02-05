package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import jp.juggler.util.data.MetaColumns
import jp.juggler.util.data.TableCompanion
import jp.juggler.util.data.replaceTo
import jp.juggler.util.log.LogCategory
import kotlin.math.min

class NotificationShown(
    var id: Long = 0L,
    var acct: String = "",
    var notificationId: String = "",
    var timeCreate: Long = System.currentTimeMillis(),
    var timeDismiss: Long = 0L,
) {
    companion object : TableCompanion {
        private val log = LogCategory("NotificationShown")
        override val table = "notification_shown"
        private const val COL_ID = BaseColumns._ID
        private const val COL_ACCT = "a"
        private const val COL_NOTIFICATION_ID = "ni"
        private const val COL_TIME_CREATE = "tc"
        private const val COL_TIME_DISMISS = "td"
        private val columnList = MetaColumns(table, initialVersion = 65).apply {
            column(0, COL_ID, MetaColumns.TS_INT_PRIMARY_KEY_NOT_NULL)
            column(0, COL_ACCT, MetaColumns.TS_EMPTY_NOT_NULL)
            column(0, COL_NOTIFICATION_ID, MetaColumns.TS_EMPTY_NOT_NULL)
            column(0, COL_TIME_CREATE, MetaColumns.TS_ZERO_NOT_NULL)
            column(0, COL_TIME_DISMISS, MetaColumns.TS_ZERO_NOT_NULL)
            createExtra = {
                arrayOf(
                    "create unique index if not exists ${table}_a on $table($COL_ACCT,$COL_NOTIFICATION_ID)",
                    "create index if not exists ${table}_at on $table($COL_ACCT,$COL_TIME_CREATE)",
                )
            }
        }

        override fun onDBCreate(db: SQLiteDatabase) {
            columnList.onDBCreate(db)
        }

        override fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 65 && newVersion >= 65) {
                onDBCreate(db)
            }
        }
    }

    class Access(val db: SQLiteDatabase) {
        fun deleteOld() {
            try {
                val list = db.rawQuery(
                    "select $COL_ACCT,count(*) from $table GROUP BY $COL_ACCT",
                    emptyArray(),
                )?.use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(
                                Pair(
                                    cursor.getString(0),
                                    cursor.getInt(1),
                                )
                            )
                        }
                    }
                }
                list ?: error("can't get usage count group by $COL_ACCT")
                // ある程度の量は残したい
                val keep = 200
                for (pair in list) {
                    val acct = pair.first
                    var size = pair.second
                    log.i("$acct size=$size")
                    // 掃除する頻度を下げるため、ここのしきい値は倍にする
                    if (size <= keep * 2) continue
                    while (true) {
                        // 一度に削除したい数
                        val step = min(100, size - keep)
                        if (step <= 0) continue
                        // 古いものからstep件目の時刻を読む
                        val time = db.rawQuery(
                            "select $COL_TIME_CREATE from $table where $COL_ACCT=? order by $COL_TIME_CREATE asc limit ?",
                            arrayOf(acct, step.toString())
                        )?.use { cursor ->
                            when (cursor.moveToLast()) {
                                true -> cursor.getLong(0)
                                else -> null
                            }
                        }
                        if (time == null || time <= 0L) {
                            log.e("can't get time of position $step")
                            break
                        }
                        // 時刻がそれ以下のデータを削除する
                        db.execSQL(
                            "delete from $table where $COL_ACCT=? and $COL_TIME_CREATE<=?",
                            arrayOf(acct, time.toString())
                        )
                        // 件数を読み直す
                        size = db.rawQuery(
                            "select count(*) from $table where $COL_ACCT=?",
                            arrayOf(acct),
                        )?.use { cursor ->
                            when (cursor.moveToNext()) {
                                true -> cursor.getInt(0)
                                else -> null
                            }
                        } ?: -1
                        log.i("$acct size=$size")
                        if (size < 0) {
                            log.e("can't get size for $acct")
                            break
                        }
                    }
                }
            } catch (ex: Throwable) {
                log.e(ex, "deleteOld failed.")
            }
        }

        fun duplicateOrPut(acct: String, notificationId: String): Boolean {
            try {
                db.rawQuery(
                    "select $COL_ID from $table where $COL_ACCT=? and $COL_NOTIFICATION_ID=? limit 1",
                    arrayOf(acct, notificationId)
                )?.use {
                    if (it.count > 0) return true
                }
                ContentValues().apply {
                    put(COL_TIME_CREATE, System.currentTimeMillis())
                    put(COL_ACCT, acct)
                    put(COL_NOTIFICATION_ID, notificationId)
                }.replaceTo(db, table)
            } catch (ex: Throwable) {
                log.e(ex, "duplicateOrPut failed.")
            }
            return false
        }
    }
}
