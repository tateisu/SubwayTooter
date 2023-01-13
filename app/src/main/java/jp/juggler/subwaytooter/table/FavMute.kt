package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.global.appDatabase
import jp.juggler.util.data.TableCompanion
import jp.juggler.util.log.LogCategory

object FavMute : TableCompanion {

    private val log = LogCategory("FavMute")

    override val table = "fav_mute"
    const val COL_ID = "_id"
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

    fun save(acct: Acct?) {
        acct ?: return
        try {
            val cv = ContentValues()
            cv.put(COL_ACCT, acct.ascii)
            appDatabase.replace(table, null, cv)
        } catch (ex: Throwable) {
            log.e(ex, "save failed.")
        }
    }

    fun delete(acct: Acct) {
        try {
            appDatabase.delete(table, "$COL_ACCT=?", arrayOf(acct.ascii))
        } catch (ex: Throwable) {
            log.e(ex, "delete failed.")
        }
    }

    fun createCursor(): Cursor {
        return appDatabase.query(table, null, null, null, null, null, "$COL_ACCT asc")
    }

    val acctSet: HashSet<Acct>
        get() = HashSet<Acct>().also { dst ->
            try {
                appDatabase.query(table, null, null, null, null, null, null)
                    .use { cursor ->
                        val idx_name = cursor.getColumnIndex(COL_ACCT)
                        while (cursor.moveToNext()) {
                            val s = cursor.getString(idx_name)
                            dst.add(Acct.parse(s))
                        }
                    }
            } catch (ex: Throwable) {
                log.e(ex, "acctSet load failed.")
            }
        }

    fun contains(acct: Acct): Boolean =
        try {
            appDatabase.query(table, null, "$COL_ACCT=?", arrayOf(acct.ascii), null, null, null)
                ?.use { it.moveToNext() }
        } catch (ex: Throwable) {
            log.e(ex, "contains failed.")
            null
        } ?: false
}
