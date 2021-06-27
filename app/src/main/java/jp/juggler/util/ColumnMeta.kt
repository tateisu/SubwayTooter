package jp.juggler.util

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

/////////////////////////////////////////////////////////////
// SQLite にBooleanをそのまま保存することはできないのでInt型との変換が必要になる

// boolean to integer
fun Boolean.b2i() = if (this) 1 else 0

// integer to boolean
fun Int.i2b() = this != 0

fun Cursor.getBoolean(keyIdx: Int) =
    getInt(keyIdx).i2b()

fun Cursor.getBoolean(key: String) =
    getBoolean(getColumnIndex(key))

fun Cursor.getInt(key: String) =
    getInt(getColumnIndex(key))

fun Cursor.getIntOrNull(idx: Int) =
    if (isNull(idx)) null else getInt(idx)

fun Cursor.getIntOrNull(key: String) =
    getIntOrNull(getColumnIndex(key))

fun Cursor.getLong(key: String) =
    getLong(getColumnIndex(key))

//fun Cursor.getLongOrNull(idx:Int) =
//	if(isNull(idx)) null else getLong(idx)

//fun Cursor.getLongOrNull(key:String) =
//	getLongOrNull(getColumnIndex(key))

fun Cursor.getString(key: String): String =
    getString(getColumnIndex(key))

fun Cursor.getStringOrNull(keyIdx: Int) =
    if (isNull(keyIdx)) null else getString(keyIdx)

fun Cursor.getStringOrNull(key: String) =
    getStringOrNull(getColumnIndex(key))

fun ContentValues.putOrNull(key: String, value: String?) =
    if (value == null) putNull(key) else put(key, value)

/////////////////////////////////////////////////////////////

interface TableCompanion {
    fun onDBCreate(db: SQLiteDatabase)
    fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int)
}

class ColumnMeta(
    list: List,
    val version: Int,
    val name: String,
    val typeSpec: String,
    val primary: Boolean = false,
) : Comparable<ColumnMeta> {
    companion object {
        private val log = LogCategory("ColumnMeta")

        const val TS_EMPTY = "text default ''"
        const val TS_ZERO = "integer default 0"
        const val TS_TRUE = "integer default 1"
    }

    class List(val table: String) : ArrayList<ColumnMeta>() {
        fun createParams(): String =
            sorted().joinToString(",") { "${it.name} ${it.typeSpec}" }

        val maxVersion: Int
            get() = this.maxOfOrNull{ it.version} ?: 0

        fun addColumnsSql(oldVersion: Int, newVersion: Int) =
            sorted()
                .filter { oldVersion < it.version && newVersion >= it.version }
                .map { "alter table $table add column ${it.name} ${it.typeSpec}" }

        fun addColumns(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            for (sql in addColumnsSql(oldVersion, newVersion)) {
                try {
                    db.execSQL(sql)
                } catch (ex: Throwable) {
                    log.trace(ex, "addColumns failed. $sql")
                }
            }
        }
    }

    // テーブル作成時のソート
    override fun compareTo(other: ColumnMeta): Int {
        // プライマリキーを先頭にする
        val ia = if (this.primary) -1 else 0
        val ib = if (other.primary) -1 else 0
        ia.compareTo(ib).notZero()?.let{ return it}

        // 残りはカラム名順
        return name.compareTo(other.name)
    }

    override fun toString(): String = name
    override fun hashCode(): Int = name.hashCode()

    override fun equals(other: Any?): Boolean = when (other) {
        is ColumnMeta -> name == other.name
        else -> name == other
    }

    init {
        list.add(this)
    }

    @Suppress("unused")
    fun putNullTo(cv: ContentValues) = cv.putNull(name)
    fun putTo(cv: ContentValues, v: Boolean?) = cv.put(name, v)
    fun putTo(cv: ContentValues, v: String?) = cv.put(name, v)
    fun putTo(cv: ContentValues, v: Byte?) = cv.put(name, v)
    fun putTo(cv: ContentValues, v: Short?) = cv.put(name, v)
    fun putTo(cv: ContentValues, v: Int?) = cv.put(name, v)
    fun putTo(cv: ContentValues, v: Long?) = cv.put(name, v)
    fun putTo(cv: ContentValues, v: Float?) = cv.put(name, v)
    fun putTo(cv: ContentValues, v: Double?) = cv.put(name, v)
    fun putTo(cv: ContentValues, v: ByteArray?) = cv.put(name, v)

    fun getIndex(cursor: Cursor) = cursor.getColumnIndex(name)
    fun getLong(cursor: Cursor) = cursor.getLong(getIndex(cursor))
}

fun ContentValues.putNull(key: ColumnMeta) = putNull(key.name)
fun ContentValues.put(key: ColumnMeta, v: Boolean?) = put(key.name, v)
fun ContentValues.put(key: ColumnMeta, v: String?) = put(key.name, v)
fun ContentValues.put(key: ColumnMeta, v: Byte?) = put(key.name, v)
fun ContentValues.put(key: ColumnMeta, v: Short?) = put(key.name, v)
fun ContentValues.put(key: ColumnMeta, v: Int?) = put(key.name, v)
fun ContentValues.put(key: ColumnMeta, v: Long?) = put(key.name, v)
fun ContentValues.put(key: ColumnMeta, v: Float?) = put(key.name, v)
fun ContentValues.put(key: ColumnMeta, v: Double?) = put(key.name, v)
fun ContentValues.put(key: ColumnMeta, v: ByteArray?) = put(key.name, v)

fun Cursor.getInt(key: ColumnMeta) = getInt(getColumnIndex(key.name))

fun Cursor.getBoolean(key: ColumnMeta) = getBoolean(getColumnIndex(key.name))
fun Cursor.getLong(key: ColumnMeta) = getLong(getColumnIndex(key.name))

@Suppress("unused")
fun Cursor.getIntOrNull(key: ColumnMeta) = getIntOrNull(getColumnIndex(key.name))
fun Cursor.getString(key: ColumnMeta): String = getString(getColumnIndex(key.name))
fun Cursor.getStringOrNull(key: ColumnMeta): String? {
    val idx = key.getIndex(this)
    return if (isNull(idx)) null else getString(idx)
}
