package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import jp.juggler.util.data.TableCompanion
import jp.juggler.util.data.getStringOrNull
import jp.juggler.util.data.queryAll
import jp.juggler.util.log.LogCategory

// リスト要素のデータ
class MutedApp(
    var id: Long = 0L,
    var name: String = "",
    var timeSave: Long = 0L,
) {
    companion object : TableCompanion {
        private val log = LogCategory("MutedApp")
        override val table = "app_mute"
        private const val COL_ID = "_id"
        private const val COL_NAME = "name"
        private const val COL_TIME_SAVE = "time_save"

        override fun onDBCreate(db: SQLiteDatabase) {
            log.d("onDBCreate!")
            db.execSQL(
                """create table if not exists $table
			($COL_ID INTEGER PRIMARY KEY
			,$COL_NAME text not null
			,$COL_TIME_SAVE integer not null
			)"""
            )
            db.execSQL(
                "create unique index if not exists ${table}_name on $table($COL_NAME)"
            )
        }

        override fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 6 && newVersion >= 6) {
                onDBCreate(db)
            }
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    class ColIdx(cursor: Cursor) {
        val idxId = cursor.getColumnIndex(COL_ID)
        val idxName = cursor.getColumnIndex(COL_NAME)
        val idxTimeSave = cursor.getColumnIndex(COL_TIME_SAVE)

        fun readRow(cursor: Cursor) = MutedApp(
            id = cursor.getLong(idxId),
            name = cursor.getStringOrNull(idxName) ?: "",
            timeSave = cursor.getLong(idxTimeSave),
        )

        fun readOne(cursor: Cursor) = when {
            cursor.moveToNext() -> readRow(cursor)
            else -> null
        }

        fun readAll(cursor: Cursor) = buildList {
            while (cursor.moveToNext()) {
                add(readRow(cursor))
            }
        }
    }

    class Access(val db: SQLiteDatabase) {
        fun save(appName: String?) {
            if (appName == null) return
            try {
                val now = System.currentTimeMillis()
                val cv = ContentValues()
                cv.put(COL_NAME, appName)
                cv.put(COL_TIME_SAVE, now)
                db.replace(table, null, cv)
            } catch (ex: Throwable) {
                log.e(ex, "save failed.")
            }
        }

        fun delete(name: String) {
            try {
                db.delete(table, "$COL_NAME=?", arrayOf(name))
            } catch (ex: Throwable) {
                log.e(ex, "delete failed.")
            }
        }

        fun listAll() = db.queryAll(table, "$COL_NAME asc")
            ?.use { ColIdx(it).readAll(it) }
            ?: emptyList()

        fun nameSet() = buildSet {
            try {
                db.rawQuery("select $COL_NAME from $table", emptyArray())
                    ?.use { cursor ->
                        val idx_name = cursor.getColumnIndex(COL_NAME)
                        while (cursor.moveToNext()) {
                            add(cursor.getString(idx_name))
                        }
                    }
            } catch (ex: Throwable) {
                log.e(ex, "nameSet failed.")
            }
        }
        //	private static final String[] isMuted_projection = new String[]{COL_NAME};
        //	private static final String   isMuted_where = COL_NAME+"=?";
        //	private static final ThreadLocal<String[]> isMuted_where_arg = new ThreadLocal<String[]>() {
        //		@Override protected String[] initialValue() {
        //			return new String[1];
        //		}
        //	};
        //	public static boolean isMuted( String app_name ){
        //		if( app_name == null ) return false;
        //		try{
        //			String[] where_arg = isMuted_where_arg.get();
        //			where_arg[0] = app_name;
        //			Cursor cursor = App1.getDB().query( table, isMuted_projection,isMuted_where , where_arg, null, null, null );
        //			try{
        //				if( cursor.moveToFirst() ){
        //					return true;
        //				}
        //			}finally{
        //				cursor.close();
        //			}
        //		}catch( Throwable ex ){
        //			warning.e( ex, "load failed." );
        //		}
        //		return false;
        //	}
    }
}
