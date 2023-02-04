package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import jp.juggler.subwaytooter.App1
import jp.juggler.util.data.*
import jp.juggler.util.log.LogCategory

class HighlightWord(
    var id: Long = -1L,
    var name: String? = null,
    var color_bg: Int = 0,
    var color_fg: Int = 0,
    var sound_type: Int = 0,
    var sound_uri: String? = null,
    var speech: Int = 0,
) {
    constructor(name: String) : this(
        name = name,
        sound_type = SOUND_TYPE_DEFAULT,
        color_fg = -0x10000,
    )

    constructor(src: JsonObject) : this(
        id = src.long(COL_ID) ?: -1L,
        name = src.stringOrThrow(COL_NAME),
        color_bg = src.optInt(COL_COLOR_BG),
        color_fg = src.optInt(COL_COLOR_FG),
        sound_type = src.optInt(COL_SOUND_TYPE),
        sound_uri = src.string(COL_SOUND_URI),
        speech = src.optInt(COL_SPEECH),
    )

    fun encodeJson(): JsonObject {
        val dst = JsonObject()
        dst[COL_ID] = id
        dst[COL_NAME] = name
        dst[COL_COLOR_BG] = color_bg
        dst[COL_COLOR_FG] = color_fg
        dst[COL_SOUND_TYPE] = sound_type
        dst[COL_SPEECH] = speech
        sound_uri?.let { dst[COL_SOUND_URI] = it }
        return dst
    }

    // ID以外のカラムをContentValuesに格納する
    fun toContentValues() = ContentValues().apply {
        if (name.isNullOrBlank()) error("HighlightWord.save(): name is empty")
        put(COL_NAME, name)
        put(COL_TIME_SAVE, System.currentTimeMillis())
        put(COL_COLOR_BG, color_bg)
        put(COL_COLOR_FG, color_fg)
        put(COL_SOUND_TYPE, sound_type)
        put(COL_SOUND_URI, sound_uri.notEmpty())
        put(COL_SPEECH, speech)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    class ColIdx(cursor: Cursor) {
        val idxId = cursor.columnIndexOrThrow(COL_ID)
        val idxName = cursor.columnIndexOrThrow(COL_NAME)
        val idxColorBg = cursor.columnIndexOrThrow(COL_COLOR_BG)
        val idxColorFg = cursor.columnIndexOrThrow(COL_COLOR_FG)
        val idxSountType = cursor.columnIndexOrThrow(COL_SOUND_TYPE)
        val idxSoundUri = cursor.columnIndexOrThrow(COL_SOUND_URI)
        val idxSpeech = cursor.columnIndexOrThrow(COL_SPEECH)

        fun readRow(cursor: Cursor) = HighlightWord(
            id = cursor.getLong(idxId),
            name = cursor.getString(idxName),
            color_bg = cursor.getInt(idxColorBg),
            color_fg = cursor.getInt(idxColorFg),
            sound_type = cursor.getInt(idxSountType),
            sound_uri = cursor.getStringOrNull(idxSoundUri),
            speech = cursor.getInt(idxSpeech),
        )

        fun readAll(cursor: Cursor) = buildList {
            while (cursor.moveToNext()) {
                add(readRow(cursor))
            }
        }

        fun readOne(cursor: Cursor) = when {
            cursor.moveToNext() -> readRow(cursor)
            else -> null
        }
    }

    companion object : TableCompanion {
        private val log = LogCategory("HighlightWord")

        override val table = "highlight_word"
        const val COL_ID = BaseColumns._ID
        const val COL_NAME = "name"
        private const val COL_TIME_SAVE = "time_save"
        private const val COL_COLOR_BG = "color_bg"
        private const val COL_COLOR_FG = "color_fg"
        private const val COL_SOUND_TYPE = "sound_type"
        private const val COL_SOUND_URI = "sound_uri"
        private const val COL_SPEECH = "speech"

        override fun onDBCreate(db: SQLiteDatabase) {
            log.d("onDBCreate!")
            db.execSQL(
                """create table if not exists $table
					($COL_ID INTEGER PRIMARY KEY
					,$COL_NAME text not null
					,$COL_TIME_SAVE integer not null
					,$COL_COLOR_BG integer not null default 0
					,$COL_COLOR_FG integer not null default 0
					,$COL_SOUND_TYPE integer not null default 1
					,$COL_SOUND_URI text default null
					,$COL_SPEECH integer default 0
					)"""
            )
            db.execSQL(
                "create unique index if not exists ${table}_name on $table(name)"
            )
        }

        override fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 21 && newVersion >= 21) {
                onDBCreate(db)
            }
            if (oldVersion < 43 && newVersion >= 43) {
                try {
                    db.execSQL("alter table $table add column $COL_SPEECH integer default 0")
                } catch (ex: Throwable) {
                    log.e(ex, "can't add $COL_SPEECH")
                }
            }
        }

        const val SOUND_TYPE_NONE = 0
        const val SOUND_TYPE_DEFAULT = 1
        const val SOUND_TYPE_CUSTOM = 2
    }

    class Access(val db: SQLiteDatabase) {

        fun load(name: String) = try {
            db.queryById(table, name, COL_NAME)
                ?.use { ColIdx(it).readOne(it) }
        } catch (ex: Throwable) {
            log.e(ex, "load failed. name=$name")
            null
        }

        fun load(id: Long) = try {
            db.queryById(table, id.toString(), COL_ID)
                ?.use { ColIdx(it).readOne(it) }
        } catch (ex: Throwable) {
            log.e(ex, "load failed. id=$id")
            null
        }

        fun listAll() =
            db.queryAll(table, "$COL_NAME asc")
                ?.use { ColIdx(it).readAll(it) }
                ?: emptyList()

        fun nameSet(): WordTrieTree? =
            WordTrieTree().also { dst ->
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
            }.takeIf{ it.isNotEmpty }

        fun hasTextToSpeechHighlightWord(): Boolean = try {
            (db.rawQuery(
                "select $COL_NAME from $table where $COL_SPEECH<>0 limit 1",
                emptyArray()
            )?.use { it.count } ?: 0) > 0
        } catch (ex: Throwable) {
            log.e(ex, "hasTextToSpeechHighlightWord failed.")
            false
        }

        fun save(context: Context, item: HighlightWord) {
            try {
                when (val id = item.id) {
                    -1L -> item.toContentValues().replaceTo(db, table)
                        .also { item.id = it }
                    else -> item.toContentValues().updateTo(db, table, id.toString())
                }
            } catch (ex: Throwable) {
                log.e(ex, "save failed.")
            }
            App1.getAppState(context).enableSpeech()
        }

        fun delete(context: Context, item: HighlightWord) {
            try {
                db.execSQL(
                    "delete from $table where $COL_ID=?",
                    arrayOf(item.id.toString())
                )
            } catch (ex: Throwable) {
                log.e(ex, "delete failed.")
            }
            App1.getAppState(context).enableSpeech()
        }
    }
}
