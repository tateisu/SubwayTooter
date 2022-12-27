package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import jp.juggler.subwaytooter.global.appDatabase
import jp.juggler.util.LogCategory
import jp.juggler.util.TableCompanion

object TagSet : TableCompanion {

    private val log = LogCategory("TagSet")

    override val table = "tag_set"
    private const val COL_TIME_SAVE = "time_save"
    private const val COL_TAG = "tag" // タグ。先頭の#を含まない

    private const val prefix_search_where = "$COL_TAG like ? escape '$'"

    private val prefix_search_where_arg = object : ThreadLocal<Array<String?>>() {
        override fun initialValue(): Array<String?> {
            return Array(1) { null }
        }
    }

    override fun onDBCreate(db: SQLiteDatabase) {
        log.d("onDBCreate!")
        db.execSQL(
            """create table if not exists $table
            (_id INTEGER PRIMARY KEY
            ,$COL_TIME_SAVE integer not null
            ,$COL_TAG text not null
            )""".trimIndent()
        )
        db.execSQL("create unique index if not exists ${table}_tag on $table($COL_TAG)")
        db.execSQL("create index if not exists ${table}_time on $table($COL_TIME_SAVE)")
    }

    override fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 15 && newVersion >= 15) {
            onDBCreate(db)
        }
    }

    // XXX: タグ履歴って掃除する必要あるかな？
    //	fun deleteOld(now : Long) {
    //		try {
    //			// 古いデータを掃除する
    //			val expire = now - 86400000L * 365
    //			appDatabase.delete(table, COL_TIME_SAVE + "<?", arrayOf(expire.toString()))
    //
    //		} catch(ex : Throwable) {
    //			warning.e(ex, "deleteOld failed.")
    //		}
    //	}

    //	public static void save1( long now, String acct ){
    //		try{
    //
    //			ContentValues cv = new ContentValues();
    //			cv.put( COL_TIME_SAVE, now );
    //			cv.put( COL_ACCT, acct );
    //			App1.getDB().replace( table, null, cv );
    //		}catch( Throwable ex ){
    //			warning.e( ex, "save failed." );
    //		}
    //	}

    fun saveList(now: Long, srcList: ArrayList<String?>, offset: Int, length: Int) {

        try {
            val cv = ContentValues()
            cv.put(COL_TIME_SAVE, now)

            var bOK = false
            val db = appDatabase
            db.execSQL("BEGIN TRANSACTION")
            try {
                for (i in 0 until length) {
                    val acct = srcList.elementAtOrNull(i + offset) ?: continue
                    cv.put(COL_TAG, acct)
                    db.replace(table, null, cv)
                }
                bOK = true
            } catch (ex: Throwable) {
                log.e(ex, "saveList failed.")
            }

            if (bOK) {
                db.execSQL("COMMIT TRANSACTION")
            } else {
                db.execSQL("ROLLBACK TRANSACTION")
            }
        } catch (ex: Throwable) {
            log.e(ex, "saveList failed.")
        }
    }

    private fun makePattern(src: String): String {
        val sb = StringBuilder()

        // エスケープしながらコピー
        for (element in src) {
            when (element) {
                '%', '_', '$' -> sb.append('$')
            }
            sb.append(element)
        }

        // 前方一致検索にするため、末尾に%をつける
        sb.append('%')

        return sb.toString()
    }

    fun searchPrefix(prefix: String, limit: Int): ArrayList<CharSequence> {
        val dst = ArrayList<CharSequence>()

        try {
            val where_arg = prefix_search_where_arg.get() ?: arrayOfNulls<String?>(1)
            where_arg[0] = makePattern(prefix)
            appDatabase.query(
                table,
                null,
                prefix_search_where,
                where_arg,
                null,
                null,
                "$COL_TAG asc limit $limit"
            ).use { cursor ->
                dst.ensureCapacity(cursor.count)
                val idx_acct = cursor.getColumnIndex(COL_TAG)
                while (cursor.moveToNext()) {
                    dst.add("#" + cursor.getString(idx_acct))
                }
            }
        } catch (ex: Throwable) {
            log.e(ex, "searchPrefix failed.")
        }

        return dst
    }
}
