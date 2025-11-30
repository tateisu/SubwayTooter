package jp.juggler.util.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import androidx.annotation.IntRange
import jp.juggler.util.log.LogCategory

private val log = LogCategory("ColumnMeta")

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

fun Cursor.getLongOrNull(@IntRange(from = 0) idx: Int) = when {
    idx < 0 -> error("getIntOrNull: invalid index $idx")
    isNull(idx) -> null
    else -> getLong(idx)
}

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

fun Cursor.columnIndexOrThrow(key: String) =
    getColumnIndex(key).takeIf { it >= 0L } ?: error("missing column $key")

/////////////////////////////////////////////////////////////

interface TableCompanion {
    val table: String
    fun onDBCreate(db: SQLiteDatabase)
    fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int)
}

private class ColumnMeta(
    val version: Int,
    val name: String,
    val typeSpec: String,
) : Comparable<ColumnMeta> {

    val primary = typeSpec.contains("primary", ignoreCase = true)

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
}

class MetaColumns(
    val table: String,
    private val initialVersion: Int,
    var createExtra: () -> Array<String> = { emptyArray() },
    var deleteBeforeCreate: Boolean = false,
) {
    companion object {
        const val TS_INT_PRIMARY_KEY = "INTEGER PRIMARY KEY"
        const val TS_INT_PRIMARY_KEY_NOT_NULL = "INTEGER NOT NULL PRIMARY KEY"

        const val TS_EMPTY = "text default ''"
        const val TS_EMPTY_NOT_NULL = "text not null default ''"
        const val TS_ZERO = "integer default 0"
        const val TS_ZERO_NOT_NULL = "integer not null default 0"
        const val TS_TRUE = "integer default 1"
        const val TS_TEXT_NULL = "blob default null"
        const val TS_BLOB_NULL = "blob default null"
    }

    private val columns = ArrayList<ColumnMeta>()

    val maxVersion: Int
        get() = columns.maxOfOrNull { it.version } ?: 0

    fun createTableSql() =
        listOf(
            "create table if not exists $table (${
                columns.sorted().joinToString(",") { "${it.name} ${it.typeSpec}" }
            })",
            *(createExtra())
        )

    private fun columnAddSql(c: ColumnMeta) =
        "alter table $table add column ${c.name} ${c.typeSpec}"

    private fun filterColumns(oldVersion: Int, newVersion: Int) =
        columns.sorted().filter { oldVersion < it.version && newVersion >= it.version }

    // alter add のSQLのリストを返す。dbが非nullなら既存カラムの存在チェックを行う。
    fun upgradeSql(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int): List<String> {
        val columnNames = buildSet {
            db?.rawQuery(
                "PRAGMA table_info('$table')",
                emptyArray(),
            )?.use { cursor ->
                val idxName = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    add(cursor.getString(idxName))
                }
            }
        }
        return filterColumns(oldVersion, newVersion).mapNotNull { c ->
            if (columnNames.contains(c.name)) {
                log.w("[${table}.${c.name}] skip alter add because column already exists.")
                null
            } else {
                columnAddSql(c)
            }
        }
    }

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
        upgradeSql(db, oldVersion, newVersion).forEach {
            try {
                db.execSQL(it)
            } catch (ex: Throwable) {
                log.e(ex, "execSQL failed. $it")
            }
        }
    }

    fun column(version: Int, name: String, typeSpec: String) =
        columns.add(ColumnMeta(version = version, name = name, typeSpec = typeSpec))
}

//fun Cursor.getInt(key: ColumnMeta) = getInt(key.name)
//fun Cursor.getBoolean(key: ColumnMeta, defVal: Boolean = false) =
//    getIntOrNull(key.name)?.i2b() ?: defVal

fun Cursor.getBoolean(key: String, defVal: Boolean = false) =
    getIntOrNull(key)?.i2b() ?: defVal

fun Cursor.getBoolean(idx: Int, defVal: Boolean = false) =
    getIntOrNull(idx)?.i2b() ?: defVal

//fun Cursor.getLong(key: ColumnMeta) = getLong(key.name)
//fun Cursor.getIntOrNull(key: ColumnMeta) = getIntOrNull(key.name)
//fun Cursor.getString(key: ColumnMeta): String = getString(key.name)
//fun Cursor.getStringOrNull(key: ColumnMeta): String? = getStringOrNull(key.name)

fun ContentValues.replaceTo(db: SQLiteDatabase, table: String) =
    db.replace(table, null, this)

fun ContentValues.updateTo(
    db: SQLiteDatabase,
    table: String,
    id: String,
    colName: String = BaseColumns._ID,
) = db.update(table, this, "$colName=?", arrayOf(id))

fun SQLiteDatabase.deleteById(table: String, id: String, colName: String = BaseColumns._ID) =
    delete(table, "$colName=?", arrayOf(id))

fun SQLiteDatabase.queryById(
    table: String,
    id: String,
    colName: String = BaseColumns._ID,
): Cursor? = rawQuery("select * from $table where $colName=?", arrayOf(id))

fun SQLiteDatabase.queryAll(
    table: String,
    orderPhrase: String,
): Cursor? = rawQuery("select * from $table order by $orderPhrase", emptyArray())
