package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import jp.juggler.util.data.TableCompanion
import jp.juggler.util.data.getString
import jp.juggler.util.log.LogCategory

class SubscriptionServerKey private constructor() {
    companion object : TableCompanion {

        private val log = LogCategory("ServerKey")

        override val table = "subscription_server_key2"
        private const val COL_ID = BaseColumns._ID
        private const val COL_CLIENT_IDENTIFIER = "ci"
        private const val COL_SERVER_KEY = "sk"

        override fun onDBCreate(db: SQLiteDatabase) {
            log.d("onDBCreate!")
            db.execSQL(
                """create table if not exists $table
            ($COL_ID INTEGER PRIMARY KEY
            ,$COL_CLIENT_IDENTIFIER text not null
            ,$COL_SERVER_KEY text not null
            )""".trimIndent()
            )
            db.execSQL("create unique index if not exists ${table}_ti on $table($COL_CLIENT_IDENTIFIER)")
        }

        override fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 26 && newVersion >= 26) {
                onDBCreate(db)
            }
        }

        private val findColumns = arrayOf(COL_SERVER_KEY)

        private val findWhereArgs = object : ThreadLocal<Array<String?>>() {
            override fun initialValue(): Array<String?> {
                return arrayOfNulls(1)
            }
        }
    }

    class Access(val db: SQLiteDatabase) {

        fun find(clientIdentifier: String): String? {
            try {
                val whereArgs = findWhereArgs.get() ?: arrayOfNulls<String?>(1)
                whereArgs[0] = clientIdentifier
                db.query(
                    table,
                    findColumns,
                    "$COL_CLIENT_IDENTIFIER=?",
                    whereArgs,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToNext()) {
                        return cursor.getString(COL_SERVER_KEY)
                    }
                }
            } catch (ex: Throwable) {
                log.e(ex, "query failed.")
            }
            return null
        }

        fun save(clientIdentifier: String, serverKey: String) {
            try {
                val cv = ContentValues()
                cv.put(COL_CLIENT_IDENTIFIER, clientIdentifier)
                cv.put(COL_SERVER_KEY, serverKey)
                db.replace(table, null, cv)
            } catch (ex: Throwable) {
                log.e(ex, "save failed.")
            }
        }
    }
}
