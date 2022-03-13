package jp.juggler.subwaytooter.appsetting

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.BaseColumns
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.AppState
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.column.Column
import jp.juggler.subwaytooter.column.ColumnEncoder
import jp.juggler.subwaytooter.column.getBackgroundImageDir
import jp.juggler.subwaytooter.global.appDatabase
import jp.juggler.subwaytooter.pref.PrefL
import jp.juggler.subwaytooter.pref.impl.*
import jp.juggler.subwaytooter.pref.put
import jp.juggler.subwaytooter.table.*
import jp.juggler.util.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object AppDataExporter {

    internal val log = LogCategory("AppDataExporter")

    private const val MAGIC_NAN = -76287755398823900.0

    private const val KEY_PREF = "pref"
    private const val KEY_ACCOUNT = "account"
    private const val KEY_COLUMN = "column"
    private const val KEY_ACCT_COLOR = "acct_color"
    private const val KEY_MUTED_APP = "muted_app"
    private const val KEY_MUTED_WORD = "muted_word"
    private const val KEY_FAV_MUTE = "fav_mute"

    // v3.4.5で廃止 private const val KEY_CLIENT_INFO = "client_info2"
    private const val KEY_HIGHLIGHT_WORD = "highlight_word"

    @Throws(IOException::class, JsonException::class)
    private fun JsonWriter.writeJsonValue(v: Any?) {
        when (v) {
            null -> nullValue()
            is String -> value(v)
            is Boolean -> value(v)
            is Number -> value(v)
            is EntityId -> value(v.toString())

            is JsonObject -> {
                beginObject()
                for (entry in v.entries) {
                    name(entry.key)
                    writeJsonValue(entry.value)
                }
                endObject()
            }

            is JsonArray -> {
                beginArray()
                for (value in v) {
                    writeJsonValue(v)
                }
                endArray()
            }

            else -> error("writeJsonValue: bad value type: $v")
        }
    }

    @Throws(IOException::class, JsonException::class)
    private fun JsonReader.readJsonValue(): Any? {
        return when (peek()) {
            JsonToken.NULL -> {
                nextNull()
                null
            }

            JsonToken.STRING -> nextString()
            JsonToken.BOOLEAN -> nextBoolean()
            JsonToken.NUMBER -> nextDouble()
            JsonToken.BEGIN_OBJECT -> jsonObject {
                beginObject()
                while (hasNext()) {
                    val name = nextName()
                    val value = readJsonValue()
                    put(name, value)
                }
                endObject()
            }
            JsonToken.BEGIN_ARRAY -> jsonArray {
                beginArray()
                while (hasNext()) {
                    add(readJsonValue())
                }
                endArray()
            }
            else -> null
        }
    }

    @Throws(IOException::class)
    private fun writeFromTable(writer: JsonWriter, jsonKey: String, table: String) {

        writer.name(jsonKey)
        writer.beginArray()

        appDatabase.query(table, null, null, null, null, null, null)
            ?.use { cursor ->
                val names = ArrayList<String>()
                val column_count = cursor.columnCount
                for (i in 0 until column_count) {
                    names.add(cursor.getColumnName(i))
                }
                while (cursor.moveToNext()) {
                    writer.beginObject()

                    for (i in 0 until column_count) {
                        when (cursor.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> {
                                writer.name(names[i])
                                writer.nullValue()
                            }

                            Cursor.FIELD_TYPE_INTEGER -> {
                                writer.name(names[i])
                                writer.value(cursor.getLong(i))
                            }

                            Cursor.FIELD_TYPE_STRING -> {
                                writer.name(names[i])
                                writer.value(cursor.getString(i))
                            }

                            Cursor.FIELD_TYPE_FLOAT -> {
                                val d = cursor.getDouble(i)
                                if (d.isNaN() || d.isInfinite()) {
                                    log.w("column ${names[i]} is nan or infinite value.")
                                } else {
                                    writer.name(names[i])
                                    writer.value(d)
                                }
                            }

                            Cursor.FIELD_TYPE_BLOB -> log.w("column ${names[i]} is blob.")
                        }
                    }

                    writer.endObject()
                }
            }
        writer.endArray()
    }

    @Throws(IOException::class)
    private fun importTable(reader: JsonReader, table: String, idMap: HashMap<Long, Long>?) {
        val db = appDatabase
        if (table == SavedAccount.table) {
            SavedAccount.onDBDelete(db)
            SavedAccount.onDBCreate(db)
        }

        db.execSQL("BEGIN TRANSACTION")
        try {
            db.execSQL("delete from $table")

            val cv = ContentValues()

            reader.beginArray()
            while (reader.hasNext()) {

                var old_id = -1L
                cv.clear()

                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    if (name == null) {
                        reader.skipValue()
                        continue
                    }

                    if (BaseColumns._ID == name) {
                        old_id = reader.nextLong()
                        continue
                    }

                    if (SavedAccount.table == table) {
                        // 一時的に存在したが現在のDBスキーマにはない項目は読み飛ばす
                        if ("nickname" == name || "color" == name) {
                            reader.skipValue()
                            continue
                        }

                        // リアルタイム通知に関連する項目は読み飛ばす
                        if (SavedAccount.COL_NOTIFICATION_TAG.name == name ||
                            SavedAccount.COL_REGISTER_KEY.name == name ||
                            SavedAccount.COL_REGISTER_TIME.name == name
                        ) {
                            reader.skipValue()
                            continue
                        }
                    }

                    when (reader.peek()) {
                        JsonToken.NULL -> {
                            reader.skipValue()
                            cv.putNull(name)
                        }

                        JsonToken.BOOLEAN -> cv.put(name, if (reader.nextBoolean()) 1 else 0)

                        JsonToken.NUMBER -> cv.put(name, reader.nextLong())

                        JsonToken.STRING -> cv.put(name, reader.nextString())

                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                val new_id =
                    db.insertWithOnConflict(table, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                if (new_id == -1L) error("importTable: invalid row_id")
                idMap?.put(old_id, new_id)
            }
            reader.endArray()
            db.execSQL("COMMIT TRANSACTION")
        } catch (ex: Throwable) {
            log.trace(ex)
            log.e(ex, "importTable failed.")
            try {
                db.execSQL("ROLLBACK TRANSACTION")
            } catch (ignored: Throwable) {
            }

            throw ex
        }
    }

    @Throws(IOException::class)
    private fun writePref(writer: JsonWriter, pref: SharedPreferences) {
        writer.name(KEY_PREF)
        writer.beginObject()
        for ((k, v) in pref.all) {
            writer.name(k)
            when (v) {
                null -> writer.nullValue()
                is String -> writer.value(v)
                is Boolean -> writer.value(v)
                is Number -> when {
                    (v is Double && v.isNaN()) -> writer.value(MAGIC_NAN)
                    (v is Float && v.isNaN()) -> writer.value(MAGIC_NAN)
                    else -> writer.value(v)
                }
                else -> error("writePref: bad data type. key=$k, type=${v.javaClass.simpleName}")
            }
        }
        writer.endObject()
    }

    @Throws(IOException::class)
    private fun importPref(reader: JsonReader, pref: SharedPreferences) {
        val e = pref.edit()
        reader.beginObject()
        while (reader.hasNext()) {
            val k = reader.nextName() ?: error("importPref: name is null")

            val token = reader.peek()
            if (token == JsonToken.NULL) {
                reader.nextNull()
                e.remove(k)
                continue
            }

            when (val prefItem = BasePref.allPref[k]) {
                is BooleanPref -> e.putBoolean(k, reader.nextBoolean())
                is IntPref -> e.putInt(k, reader.nextInt())
                is LongPref -> e.putLong(k, reader.nextLong())

                is StringPref -> if (prefItem.skipImport) {
                    reader.skipValue()
                    e.remove(k)
                } else {
                    e.putString(k, reader.nextString())
                }

                is FloatPref -> {
                    val dv = reader.nextDouble()
                    e.putFloat(k, if (dv <= MAGIC_NAN) Float.NaN else dv.toFloat())
                }

                else -> {
                    // ignore or force reset
                    reader.skipValue()
                    e.remove(k)
                }
            }
        }
        reader.endObject()
        e.apply()
    }

    @Throws(IOException::class, JsonException::class)
    private fun writeColumn(appState: AppState, writer: JsonWriter) {
        writer.name(KEY_COLUMN)
        writer.beginArray()
        for (column in appState.columnList) {
            writer.writeJsonValue(jsonObject { ColumnEncoder.encode(column, this, 0) })
        }
        writer.endArray()
    }

    @Throws(IOException::class, JsonException::class)
    private fun readColumn(
        appState: AppState,
        reader: JsonReader,
        idMap: HashMap<Long, Long>,
    ) = ArrayList<Column>().also { result ->
        reader.beginArray()
        while (reader.hasNext()) {
            val item: JsonObject = reader.readJsonValue().cast()!!

            // DB上のアカウントIDが変化したので置き換える
            when (val old_id = item.long(ColumnEncoder.KEY_ACCOUNT_ROW_ID) ?: -1L) {

                // 検索カラムのアカウントIDはNAアカウントと紐ついている。変換の必要はない
                -1L -> {
                }

                else -> item[ColumnEncoder.KEY_ACCOUNT_ROW_ID] = idMap[old_id]
                    ?: error("readColumn: can't convert account id")
            }

            try {
                result.add(Column(appState, item))
            } catch (ex: Throwable) {
                log.trace(ex)
                log.e(ex, "column load failed.")
                throw ex
            }
        }
        reader.endArray()
    }

    @Throws(IOException::class, JsonException::class)
    fun encodeAppData(context: Context, writer: JsonWriter) {
        writer.setIndent(" ")
        writer.beginObject()

        val app_state = App1.getAppState(context)

        writePref(writer, app_state.pref)

        writeFromTable(writer, KEY_ACCOUNT, SavedAccount.table)
        writeFromTable(writer, KEY_ACCT_COLOR, AcctColor.table)
        writeFromTable(writer, KEY_MUTED_APP, MutedApp.table)
        writeFromTable(writer, KEY_MUTED_WORD, MutedWord.table)
        writeFromTable(writer, KEY_FAV_MUTE, FavMute.table)
        writeFromTable(writer, KEY_HIGHLIGHT_WORD, HighlightWord.table)

        writeColumn(app_state, writer)

        writer.endObject()
    }

    @SuppressLint("UseSparseArrays")
    @Throws(IOException::class, JsonException::class)
    internal fun decodeAppData(context: Context, reader: JsonReader): ArrayList<Column> {

        var result: ArrayList<Column>? = null

        val app_state = App1.getAppState(context)
        reader.beginObject()

        val account_id_map = HashMap<Long, Long>()

        while (reader.hasNext()) {
            when (reader.nextName()) {
                KEY_PREF -> importPref(reader, app_state.pref)
                KEY_ACCOUNT -> importTable(reader, SavedAccount.table, account_id_map)

                KEY_ACCT_COLOR -> {
                    importTable(reader, AcctColor.table, null)
                    AcctColor.clearMemoryCache()
                }

                KEY_MUTED_APP -> importTable(reader, MutedApp.table, null)
                KEY_MUTED_WORD -> importTable(reader, MutedWord.table, null)
                KEY_FAV_MUTE -> importTable(reader, FavMute.table, null)
                KEY_HIGHLIGHT_WORD -> importTable(reader, HighlightWord.table, null)
                KEY_COLUMN -> result = readColumn(app_state, reader, account_id_map)

                // 端末間でクライアントIDを再利用することはできなくなった
                // KEY_CLIENT_INFO -> importTable(reader, ClientInfo.table, null)

                else -> reader.skipValue()
            }
        }

        run {
            val old_id = PrefL.lpTabletTootDefaultAccount(app_state.pref)
            if (old_id != -1L) {
                val new_id = account_id_map[old_id]
                app_state.pref.edit().put(PrefL.lpTabletTootDefaultAccount, new_id ?: -1L).apply()
            }
        }

        if (result == null) error("import data does not includes column list!")

        return result
    }

    fun saveBackgroundImage(
        context: Context,
        zipStream: ZipOutputStream,
        column: Column,
    ) {
        try {
            column.columnBgImage.mayUri()?.let { uri ->
                context.contentResolver.openInputStream(uri)?.use { inStream ->
                    zipStream.putNextEntry(ZipEntry("background-image/${column.columnId}"))
                    try {
                        inStream.copyTo(zipStream)
                    } finally {
                        zipStream.closeEntry()
                    }
                }
            }
        } catch (ex: Throwable) {
            log.trace(ex)
        }
    }

    private val reBackgroundImage = "background-image/(.+)".asciiPattern()

    // エントリが背景画像のソレなら真を返す
    // column.column_bg_image を更新する場合がある
    fun restoreBackgroundImage(
        context: Context,
        columnList: ArrayList<Column>?,
        inStream: InputStream,
        entryName: String,
    ): Boolean {

        // entryName がバックグラウンド画像のそれと一致するか
        val m = reBackgroundImage.matcher(entryName)
        if (!m.find()) return false

        try {
            val id = m.groupEx(1)
            val column = columnList?.find { it.columnId == id }
            if (column == null) {
                log.e("missing column for id $id")
            } else {
                val backgroundDir = getBackgroundImageDir(context)
                val file =
                    File(backgroundDir, "${column.columnId}:${System.currentTimeMillis()}")
                FileOutputStream(file).use { outStream ->
                    inStream.copyTo(outStream)
                }
                column.columnBgImage = Uri.fromFile(file).toString()
            }
        } catch (ex: Throwable) {
            log.trace(ex)
        }

        return true
    }
}
