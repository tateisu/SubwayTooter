package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import jp.juggler.subwaytooter.App1
import jp.juggler.util.*
import java.util.concurrent.atomic.AtomicReference

class HighlightWord {

    companion object : TableCompanion {

        private val log = LogCategory("HighlightWord")

        const val SOUND_TYPE_NONE = 0
        const val SOUND_TYPE_DEFAULT = 1
        const val SOUND_TYPE_CUSTOM = 2

        override val table = "highlight_word"
        const val COL_ID = BaseColumns._ID
        const val COL_NAME = "name"
        private const val COL_TIME_SAVE = "time_save"
        private const val COL_COLOR_BG = "color_bg"
        private const val COL_COLOR_FG = "color_fg"
        private const val COL_SOUND_TYPE = "sound_type"
        private const val COL_SOUND_URI = "sound_uri"
        private const val COL_SPEECH = "speech"

        private const val selection_name = "$COL_NAME=?"
        private const val selection_speech = "$COL_SPEECH<>0"
        private const val selection_id = "$COL_ID=?"

        private val columns_name = arrayOf(COL_NAME)

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
                    log.trace(ex)
                }
            }
        }

        fun load(name: String): HighlightWord? {
            try {
                App1.database.query(table, null, selection_name, arrayOf(name), null, null, null)
                    .use { cursor ->
                        if (cursor.moveToNext()) {
                            return HighlightWord(cursor)
                        }
                    }
            } catch (ex: Throwable) {
                log.trace(ex)
            }

            return null
        }

        fun load(id: Long): HighlightWord? {
            try {
                App1.database.query(table, null, selection_id, arrayOf(id.toString()), null, null, null)
                    .use { cursor ->
                        if (cursor.moveToNext()) {
                            return HighlightWord(cursor)
                        }
                    }
            } catch (ex: Throwable) {
                log.trace(ex)
            }

            return null
        }

        fun createCursor(): Cursor {
            return App1.database.query(table, null, null, null, null, null, "$COL_NAME asc")
        }

        val nameSet: WordTrieTree?
            get() {
                val dst = WordTrieTree()
                try {
                    App1.database.query(table, columns_name, null, null, null, null, null)
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

                return if (dst.isEmpty) null else dst
            }

        private val hasTextToSpeechHighlightWordCache = AtomicReference<Boolean>(null)

        fun hasTextToSpeechHighlightWord(): Boolean {
            synchronized(this) {
                var cached = hasTextToSpeechHighlightWordCache.get()
                if (cached == null) {
                    cached = false
                    try {
                        App1.database.query(
                            table,
                            columns_name,
                            selection_speech,
                            null,
                            null,
                            null,
                            null
                        )
                            .use { cursor ->
                                while (cursor.moveToNext()) {
                                    cached = true
                                }
                            }
                    } catch (ex: Throwable) {
                        log.trace(ex)
                    }
                    hasTextToSpeechHighlightWordCache.set(cached)
                }
                return cached
            }
        }
    }

    var id = -1L
    var name: String
    var color_bg = 0
    var color_fg = 0
    var sound_type = 0
    var sound_uri: String? = null
    var speech = 0

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

    constructor(src: JsonObject) {
        this.id = src.long(COL_ID) ?: -1L
        this.name = src.stringOrThrow(COL_NAME)
        this.color_bg = src.optInt(COL_COLOR_BG)
        this.color_fg = src.optInt(COL_COLOR_FG)
        this.sound_type = src.optInt(COL_SOUND_TYPE)
        this.sound_uri = src.string(COL_SOUND_URI)
        this.speech = src.optInt(COL_SPEECH)
    }

    constructor(name: String) {
        this.name = name
        this.sound_type = SOUND_TYPE_DEFAULT
        this.color_fg = -0x10000
    }

    constructor(cursor: Cursor) {
        this.id = cursor.getLong(COL_ID)
        this.name = cursor.getString(COL_NAME)
        this.color_bg = cursor.getInt(COL_COLOR_BG)
        this.color_fg = cursor.getInt(COL_COLOR_FG)
        this.sound_type = cursor.getInt(COL_SOUND_TYPE)
        this.sound_uri = cursor.getStringOrNull(COL_SOUND_URI)
        this.speech = cursor.getInt(COL_SPEECH)
    }

    fun save(context: Context) {
        if (name.isEmpty()) error("HighlightWord.save(): name is empty")

        try {
            val cv = ContentValues()
            cv.put(COL_NAME, name)
            cv.put(COL_TIME_SAVE, System.currentTimeMillis())
            cv.put(COL_COLOR_BG, color_bg)
            cv.put(COL_COLOR_FG, color_fg)
            cv.put(COL_SOUND_TYPE, sound_type)

            val sound_uri = this.sound_uri
            if (sound_uri?.isEmpty() != false) {
                cv.putNull(COL_SOUND_URI)
            } else {
                cv.put(COL_SOUND_URI, sound_uri)
            }
            cv.put(COL_SPEECH, speech)

            if (id == -1L) {
                id = App1.database.replace(table, null, cv)
            } else {
                App1.database.update(table, cv, selection_id, arrayOf(id.toString()))
            }
        } catch (ex: Throwable) {
            log.e(ex, "save failed.")
        }

        synchronized(Companion) {
            hasTextToSpeechHighlightWordCache.set(null)
        }
        App1.getAppState(context).enableSpeech()
    }

    fun delete(context: Context) {
        try {
            App1.database.delete(table, selection_id, arrayOf(id.toString()))
        } catch (ex: Throwable) {
            log.e(ex, "delete failed.")
        }
        synchronized(Companion) {
            hasTextToSpeechHighlightWordCache.set(null)
        }
        App1.getAppState(context).enableSpeech()
    }
}
