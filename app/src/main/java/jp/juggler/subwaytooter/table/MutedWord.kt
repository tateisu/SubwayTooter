package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import jp.juggler.util.data.*
import jp.juggler.util.log.LogCategory

class MutedWord(
    var id: Long = 0L,
    var name: String = "",
    var timeSave: Long = 0L,
) {
    companion object : TableCompanion {
        private val log = LogCategory("MutedWord")
        override val table = "word_mute"
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
            )""".trimIndent()
            )
            db.execSQL("create unique index if not exists ${table}_name on $table(name)")
        }

        override fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 11 && newVersion >= 11) {
                onDBCreate(db)
            }
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    class ColIdx(cursor: Cursor) {
        val idxId = cursor.getColumnIndexOrThrow(COL_ID)
        val idxName = cursor.getColumnIndexOrThrow(COL_NAME)
        val idxTimeSave = cursor.getColumnIndexOrThrow(COL_TIME_SAVE)
        fun readRow(cursor: Cursor) = MutedWord(
            id = cursor.getLong(idxId),
            name = cursor.getStringOrNull(idxName) ?: "",
            timeSave = cursor.getLong(idxTimeSave),
        )

        fun readAll(cursor: Cursor) = buildList {
            while (cursor.moveToNext()) {
                add(readRow(cursor))
            }
        }
    }

    class Access(val db: SQLiteDatabase) {
        fun save(word: String?) {
            try {
                word ?: return
                ContentValues().apply {
                    put(COL_NAME, word)
                    put(COL_TIME_SAVE, System.currentTimeMillis())
                }.replaceTo(db, table)
            } catch (ex: Throwable) {
                log.e(ex, "save failed.")
            }
        }

        fun delete(name: String) {
            try {
                db.deleteById(table, name, COL_NAME)
            } catch (ex: Throwable) {
                log.e(ex, "delete failed.")
            }
        }

        fun nameSet() = WordTrieTree().also { dst ->
            try {
                db.rawQuery("select $COL_NAME from $table", emptyArray())
                    ?.use { cursor ->
                        val idxName = cursor.getColumnIndex(COL_NAME)
                        while (cursor.moveToNext()) {
                            dst.add(cursor.getString(idxName))
                        }
                    }
            } catch (ex: Throwable) {
                log.e(ex, "nameSet failed.")
            }
        }

        fun listAll() =
            db.queryAll(table, "$COL_NAME asc")
                ?.use { ColIdx(it).readAll(it) }
                ?: emptyList()

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