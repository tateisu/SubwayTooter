package jp.juggler.util

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.annotation.IntRange

/////////////////////////////////////////////////////////////
// SQLite にBooleanをそのまま保存することはできないのでInt型との変換が必要になる

// boolean to integer
fun Boolean.b2i() = if (this) 1 else 0

// integer to boolean
fun Int.i2b() = this != 0

//fun Cursor.getBoolean(keyIdx: Int) =
//    getInt(keyIdx).i2b()
//fun Cursor.getBoolean(key: String) =
//    getBoolean(getColumnIndex(key))

fun Cursor.getInt(key: String) =
    getColumnIndex(key).takeIf { it >= 0 }?.let { getInt(it) }
        ?: error("getInt: missing column named $key")

fun Cursor.getIntOrNull(@IntRange(from = 0) idx: Int) = when {
    idx < 0 -> error("getIntOrNull: invalid index $idx")
    isNull(idx) -> null
    else -> getInt(idx)
}

fun Cursor.getIntOrNull(key: String) =
    getColumnIndex(key).takeIf { it >= 0 }?.let { getIntOrNull(it) }

fun Cursor.getLong(key: String) =
    getColumnIndex(key).takeIf { it >= 0 }?.let { getLong(it) }
        ?: error("getLong: missing column named $key")

//fun Cursor.getLongOrNull(idx:Int) =
//	if(isNull(idx)) null else getLong(idx)

//fun Cursor.getLongOrNull(key:String) =
//	getLongOrNull(getColumnIndex(key))

fun Cursor.getString(key: String): String =
    getColumnIndex(key).takeIf { it >= 0 }?.let { getString(it)!! }
        ?: error("getString: missing column named $key")

fun Cursor.getStringOrNull(keyIdx: Int) =
    if (isNull(keyIdx)) null else getString(keyIdx)

fun Cursor.getStringOrNull(key: String) =
    getStringOrNull(getColumnIndex(key))

fun Cursor.getBlobOrNull(keyIdx: Int) =
    if (isNull(keyIdx)) null else getBlob(keyIdx)

fun Cursor.getBlobOrNull(key: String) =
    getBlobOrNull(getColumnIndex(key))

/////////////////////////////////////////////////////////////

interface TableCompanion {
    val table: String
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

    class List(
        val table: String,
        private val initialVersion: Int,
        var createExtra: () -> Array<String> = { emptyArray() },
        var deleteBeforeCreate: Boolean = false,
    ) : ArrayList<ColumnMeta>() {
        val maxVersion: Int
            get() = this.maxOfOrNull { it.version } ?: 0

        fun createTableSql() =
            listOf(
                "create table if not exists $table (${sorted().joinToString(",") { "${it.name} ${it.typeSpec}" }})",
                *(createExtra())
            )

        fun addColumnsSql(oldVersion: Int, newVersion: Int) =
            sorted()
                .filter { oldVersion < it.version && newVersion >= it.version }
                .map { "alter table $table add column ${it.name} ${it.typeSpec}" }

        fun onDBCreate(db: SQLiteDatabase) {
            log.d("onDBCreate table=$table")
            if (deleteBeforeCreate) db.execSQL("drop table if exists $table")
            createTableSql().forEach { db.execSQL(it) }
        }

        fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < initialVersion && newVersion >= initialVersion) {
                onDBCreate(db)
                return
            }
            addColumnsSql(oldVersion, newVersion).forEach {
                try {
                    db.execSQL(it)
                } catch (ex: Throwable) {
                    log.trace(ex, "execSQL failed. $it")
                }
            }
        }
    }

    // テーブル作成時のソート
    override fun compareTo(other: ColumnMeta): Int {
        // プライマリキーを先頭にする
        (other.primary.b2i() - primary.b2i()).notZero()?.let { return it }
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

    fun getIndex(cursor: Cursor) = cursor.getColumnIndex(name)
    fun getLong(cursor: Cursor) = cursor.getLong(getIndex(cursor))
}

fun ContentValues.putNull(key: ColumnMeta) = putNull(key.name)
fun ContentValues.put(key: ColumnMeta, v: Boolean?) = put(key.name, v?.b2i())
fun ContentValues.put(key: ColumnMeta, v: String?) = put(key.name, v)
fun ContentValues.put(key: ColumnMeta, v: Byte?) = put(key.name, v)
fun ContentValues.put(key: ColumnMeta, v: Short?) = put(key.name, v)
fun ContentValues.put(key: ColumnMeta, v: Int?) = put(key.name, v)
fun ContentValues.put(key: ColumnMeta, v: Long?) = put(key.name, v)
fun ContentValues.put(key: ColumnMeta, v: Float?) = put(key.name, v)
fun ContentValues.put(key: ColumnMeta, v: Double?) = put(key.name, v)
fun ContentValues.put(key: ColumnMeta, v: ByteArray?) = put(key.name, v)

fun Cursor.getInt(key: ColumnMeta) = getInt(key.name)
fun Cursor.getBoolean(key: ColumnMeta) = getInt(key.name).i2b()
fun Cursor.getLong(key: ColumnMeta) = getLong(key.name)
fun Cursor.getIntOrNull(key: ColumnMeta) = getIntOrNull(key.name)
fun Cursor.getString(key: ColumnMeta): String = getString(key.name)
fun Cursor.getStringOrNull(key: ColumnMeta): String? = getStringOrNull(key.name)
