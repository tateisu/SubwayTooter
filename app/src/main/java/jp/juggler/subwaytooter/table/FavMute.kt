package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.util.data.TableCompanion
import jp.juggler.util.log.LogCategory

class FavMute(
    var id: Long = 0L,
    val acct: String = "",
) {
    companion object : TableCompanion {
        private val log = LogCategory("FavMute")
        override val table = "fav_mute"
        const val COL_ID = BaseColumns._ID
        const val COL_ACCT = "acct"

        override fun onDBCreate(db: SQLiteDatabase) {
            log.d("onDBCreate!")
            db.execSQL(
                """create table if not exists $table
            ($COL_ID INTEGER PRIMARY KEY
            ,$COL_ACCT text not null
            )""".trimIndent()
            )
            db.execSQL("create unique index if not exists ${table}_acct on $table($COL_ACCT)")
        }

        override fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 22 && newVersion >= 22) {
                onDBCreate(db)
            }
        }
    }

    class Access(val db: SQLiteDatabase) {

        fun save(acct: Acct?) {
            acct ?: return
            try {
                val cv = ContentValues()
                cv.put(COL_ACCT, acct.ascii)
                db.replace(table, null, cv)
            } catch (ex: Throwable) {
                log.e(ex, "save failed.")
            }
        }

        fun delete(acct: Acct) {
            try {
                db.delete(table, "$COL_ACCT=?", arrayOf(acct.ascii))
            } catch (ex: Throwable) {
                log.e(ex, "delete failed.")
            }
        }

        fun listAll() =
            db.rawQuery(
                "select * from $table order by $COL_ACCT asc",
                emptyArray()
            )?.use { cursor ->
                buildList {
                    val idxId = cursor.getColumnIndex(COL_ID)
                    val idxAcct = cursor.getColumnIndex(COL_ACCT)
                    while (cursor.moveToNext()) {
                        add(
                            FavMute(
                                id = cursor.getLong(idxId),
                                acct = cursor.getString(idxAcct)
                            )
                        )
                    }
                }
            } ?: emptyList()


        fun acctSet()= buildSet {
            try {
                db.query(table, null, null, null, null, null, null)
                    .use { cursor ->
                        val idx_name = cursor.getColumnIndex(COL_ACCT)
                        while (cursor.moveToNext()) {
                            val s = cursor.getString(idx_name)
                            add(Acct.parse(s))
                        }
                    }
            } catch (ex: Throwable) {
                log.e(ex, "acctSet load failed.")
            }
        }

        fun contains(acct: Acct): Boolean =
            try {
                db.query(table, null, "$COL_ACCT=?", arrayOf(acct.ascii), null, null, null)
                    ?.use { it.moveToNext() }
            } catch (ex: Throwable) {
                log.e(ex, "contains failed.")
                null
            } ?: false
    }
}
