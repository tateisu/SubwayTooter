package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import jp.juggler.subwaytooter.global.appDatabase
import jp.juggler.util.LogCategory
import jp.juggler.util.TableCompanion
import jp.juggler.util.WordTrieTree

object MutedWord : TableCompanion {

    private val log = LogCategory("MutedWord")

    override val table = "word_mute"
    const val COL_ID = "_id"
    const val COL_NAME = "name"
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

    fun save(word: String?) {
        if (word == null) return
        try {
            val now = System.currentTimeMillis()

            val cv = ContentValues()
            cv.put(COL_NAME, word)
            cv.put(COL_TIME_SAVE, now)
            appDatabase.replace(table, null, cv)
        } catch (ex: Throwable) {
            log.e(ex, "save failed.")
        }
    }

    fun createCursor(): Cursor {
        return appDatabase.query(table, null, null, null, null, null, "$COL_NAME asc")
    }

    fun delete(name: String) {
        try {
            appDatabase.delete(table, "$COL_NAME=?", arrayOf(name))
        } catch (ex: Throwable) {
            log.e(ex, "delete failed.")
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

    val nameSet: WordTrieTree
        get() {
            val dst = WordTrieTree()
            try {
                appDatabase.query(table, null, null, null, null, null, null)
                    .use { cursor ->
                        val idx_name = cursor.getColumnIndex(COL_NAME)
                        while (cursor.moveToNext()) {
                            val s = cursor.getString(idx_name)
                            dst.add(s)
                        }
                    }
            } catch (ex: Throwable) {
                log.trace(ex)
            }

            return dst
        }
}
