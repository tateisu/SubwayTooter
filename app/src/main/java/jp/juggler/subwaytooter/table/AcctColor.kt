package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import androidx.annotation.StringRes
import androidx.collection.LruCache
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.util.data.*
import jp.juggler.util.log.LogCategory

class AcctColor(
    var id: Long = 0L,
    var timeSave: Long = 0L,
    //@who@host ascii文字の大文字小文字は(sqliteにより)同一視される
    var acctAscii: String = "",
    // 未設定なら0、それ以外は色
    var colorFg: Int = 0,
    // 未設定なら0、それ以外は色
    var colorBg: Int = 0,
    // 未設定ならnullか空文字列
    var nicknameSave: String? = null,
    // 未設定ならnullか空文字列
    var notificationSoundSaved: String? = null,
) {
    var acct = Acct.parse(acctAscii)

    val nickname: String
        get() = nicknameSave.notEmpty() ?: acct.pretty

    val notificationSound: String?
        get() = notificationSoundSaved.notEmpty()

    companion object : TableCompanion {
        private val log = LogCategory("AcctColor")
        override val table = "acct_color"
        private const val COL_ID = BaseColumns._ID
        private const val COL_TIME_SAVE = "time_save"
        private const val COL_ACCT = "ac"
        private const val COL_COLOR_FG = "cf"
        private const val COL_COLOR_BG = "cb"
        private const val COL_NICKNAME = "nick"
        private const val COL_NOTIFICATION_SOUND = "notification_sound"

        val columnList = MetaColumns(table, 9).apply {
            column(0, COL_ID, "INTEGER PRIMARY KEY")
            column(0, COL_TIME_SAVE, "integer not null")
            column(0, COL_ACCT, "text not null")
            column(0, COL_COLOR_FG, "integer")
            column(0, COL_COLOR_BG, "integer")
            column(0, COL_NICKNAME, "text")
            column(17, COL_NOTIFICATION_SOUND, "text default ''")
            createExtra = {
                arrayOf(
                    "create unique index if not exists ${table}_acct on $table($COL_ACCT)",
                    "create index if not exists ${table}_time on $table($COL_TIME_SAVE)",
                )
            }
        }

        override fun onDBCreate(db: SQLiteDatabase) =
            columnList.onDBCreate(db)

        override fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
            columnList.onDBUpgrade(db, oldVersion, newVersion)

        private const val CHAR_REPLACE: Char = 0x328A.toChar()

        private val mMemoryCache = LruCache<String, AcctColor>(2048)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    class ColIdx(cursor: Cursor) {
        val idxId = cursor.getColumnIndexOrThrow(COL_ID)
        val idxTimeSave = cursor.getColumnIndexOrThrow(COL_TIME_SAVE)
        val idxAcct = cursor.getColumnIndexOrThrow(COL_ACCT)
        val idxColorFg = cursor.getColumnIndexOrThrow(COL_COLOR_FG)
        val idxColorBg = cursor.getColumnIndexOrThrow(COL_COLOR_BG)
        val idxNickname = cursor.getColumnIndexOrThrow(COL_NICKNAME)
        val idxNotificationSound = cursor.getColumnIndexOrThrow(COL_NOTIFICATION_SOUND)
        fun readRow(cursor: Cursor) = AcctColor(
            id = cursor.getLong(idxId),
            timeSave = cursor.getLong(idxTimeSave),
            acctAscii = cursor.getString(idxAcct),
            colorFg = cursor.getIntOrNull(idxColorFg) ?: 0,
            colorBg = cursor.getIntOrNull(idxColorBg) ?: 0,
            nicknameSave = cursor.getStringOrNull(idxNickname),
            notificationSoundSaved = cursor.getStringOrNull(idxNotificationSound),
        )

        fun readOne(cursor: Cursor) =
            when {
                cursor.moveToNext() -> readRow(cursor)
                else -> null
            }

        fun readAll(cursor: Cursor) = buildList {
            while (cursor.moveToNext()) {
                add(readRow(cursor))
            }
        }
    }

    fun toContentValues(key: String) = ContentValues().apply {
        put(COL_ACCT, key)
        put(COL_COLOR_FG, colorFg)
        put(COL_COLOR_BG, colorBg)
        put(COL_NICKNAME, nicknameSave ?: "")
        put(COL_NOTIFICATION_SOUND, notificationSoundSaved ?: "")
    }

    class Access(
        val db: SQLiteDatabase,
    ) {

        private val load_where = "$COL_ACCT=?"

        private val load_where_arg = object : ThreadLocal<Array<String?>>() {
            override fun initialValue(): Array<String?> {
                return arrayOfNulls(1)
            }
        }

        fun load(acctAscii: String): AcctColor {
            val key = acctAscii.lowercase()
            mMemoryCache.get(key)?.let { return it }
            try {
                db.queryById(table, key, COL_ACCT)
                    ?.use { ColIdx(it).readOne(it) }
                    ?.also { ac -> mMemoryCache.put(key, ac) }
                    ?.let { return it }
            } catch (ex: Throwable) {
                log.e(ex, "load failed.")
            }
            log.d("lruCache size=${mMemoryCache.size()},hit=${mMemoryCache.hitCount()},miss=${mMemoryCache.missCount()}")
            return AcctColor(acctAscii = key).also { mMemoryCache.put(key, it) }
        }

        fun load(a: SavedAccount, who: TootAccount) = load(a.getFullAcct(who))
        fun load(a: SavedAccount) = load(a.acct)
        fun load(acct: Acct) = load(acct.ascii)

//		fun getNickname(acct : String) : String {
//			val ac = load(acct)
//			val nickname = ac.nickname
//			return if(nickname != null && nickname.isNotEmpty()) nickname.sanitizeBDI() else acct
//		}

        private fun getNicknameWithColor(acctAscii: String): CharSequence {
            val ac = load(acctAscii)
            val sb = SpannableStringBuilder(ac.nickname.sanitizeBDI())
            val start = 0
            val end = sb.length
            if (ac.colorFg != 0) {
                sb.setSpan(
                    ForegroundColorSpan(ac.colorFg),
                    start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            if (ac.colorBg != 0) {
                sb.setSpan(
                    BackgroundColorSpan(ac.colorBg),
                    start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            return sb
        }

        fun getNicknameWithColor(acct: Acct) = getNicknameWithColor(acct.ascii)
        fun getNicknameWithColor(sa: SavedAccount, who: TootAccount) =
            getNicknameWithColor(sa.getFullAcct(who))

        fun getNickname(acctAscii: String) = load(acctAscii).nickname
        fun getNickname(acct: Acct): String = load(acct.ascii).nickname
        fun getNickname(sa: SavedAccount) = load(sa.acct.ascii).nickname

        fun getNickname(sa: SavedAccount, who: TootAccount): String =
            getNickname(sa.getFullAcct(who))

        fun getNotificationSound(acct: Acct) =
            load(acct).notificationSound

        //		fun getNicknameWithColor(sa:SavedAccount,acctArg:String)  =
//			getNicknameWithColor(sa.getFullAcct(Acct.parse(acctArg)))
//		fun getNotificationSound(acctAscii : String) : String? {
//			return load(acctAscii,"").notification_sound?.notEmpty()
//			// acctPretty is not used in this case
//		}

        fun hasNickname(ac: AcctColor?) =
            null != ac?.nicknameSave?.notEmpty()

        fun hasColorForeground(ac: AcctColor?) = (ac?.colorFg ?: 0) != 0
        fun hasColorBackground(ac: AcctColor?) = (ac?.colorBg ?: 0) != 0

        fun clearMemoryCache() {
            mMemoryCache.evictAll()
        }

        fun getStringWithNickname(
            context: Context,
            @StringRes stringId: Int,
            acct: Acct,
        ): CharSequence {
            val ac = load(acct)
            val name = ac.nickname
            val sb = SpannableStringBuilder(
                context.getString(stringId, String(charArrayOf(CHAR_REPLACE)))
            )
            for (i in sb.length - 1 downTo 0) {
                val c = sb[i]
                if (c != CHAR_REPLACE) continue
                sb.replace(i, i + 1, name)
                if (ac.colorFg != 0) {
                    sb.setSpan(
                        ForegroundColorSpan(ac.colorFg),
                        i,
                        i + name.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                if (ac.colorBg != 0) {
                    sb.setSpan(
                        BackgroundColorSpan(ac.colorBg),
                        i,
                        i + name.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            return sb
        }

        fun save(now: Long, item: AcctColor) {

            val key = item.acctAscii.lowercase()

            try {
                item.toContentValues(key)
                    .apply { put(COL_TIME_SAVE, now) }
                    .replaceTo(db, table)
                mMemoryCache.remove(key)
            } catch (ex: Throwable) {
                log.e(ex, "save failed.")
            }
        }
    }
}
