package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import androidx.annotation.StringRes
import androidx.collection.LruCache
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.util.*

class AcctColor {

    private var acctAscii: String
    private var acctPretty: String
    var color_fg: Int = 0
    var color_bg: Int = 0
    var nicknameSave: String? = null
    var notification_sound: String? = null

    val nickname: String
        get() = nicknameSave.notEmpty() ?: acctPretty

    constructor(
        acctAscii: String,
        acctPretty: String,
        nicknameSave: String,
        color_fg: Int,
        color_bg: Int,
        notification_sound: String?,
    ) {
        this.acctAscii = acctAscii
        this.acctPretty = acctPretty
        this.nicknameSave = nicknameSave
        this.color_fg = color_fg
        this.color_bg = color_bg
        this.notification_sound = notification_sound
    }

    private constructor(acctAscii: String, acctPretty: String) {
        this.acctAscii = acctAscii
        this.acctPretty = acctPretty
    }

    fun save(now: Long) {

        val key = acctAscii.lowercase()

        try {
            val cv = ContentValues()
            cv.put(COL_TIME_SAVE, now)
            cv.put(COL_ACCT, key)
            cv.put(COL_COLOR_FG, color_fg)
            cv.put(COL_COLOR_BG, color_bg)
            cv.put(COL_NICKNAME, nicknameSave ?: "")
            cv.put(
                COL_NOTIFICATION_SOUND,
                if (notification_sound == null) "" else notification_sound
            )
            App1.database.replace(table, null, cv)
            mMemoryCache.remove(key)
        } catch (ex: Throwable) {
            log.trace(ex)
            log.e(ex, "save failed.")
        }
    }

    companion object : TableCompanion {

        private val log = LogCategory("AcctColor")

        const val table = "acct_color"
        private const val COL_TIME_SAVE = "time_save"
        private const val COL_ACCT = "ac" //@who@host ascii文字の大文字小文字は(sqliteにより)同一視される
        private const val COL_COLOR_FG = "cf" // 未設定なら0、それ以外は色
        private const val COL_COLOR_BG = "cb" // 未設定なら0、それ以外は色
        private const val COL_NICKNAME = "nick" // 未設定ならnullか空文字列
        private const val COL_NOTIFICATION_SOUND = "notification_sound" // 未設定ならnullか空文字列

        private const val CHAR_REPLACE: Char = 0x328A.toChar()

        private const val load_where = "$COL_ACCT=?"

        private val load_where_arg = object : ThreadLocal<Array<String?>>() {
            override fun initialValue(): Array<String?> {
                return arrayOfNulls(1)
            }
        }

        private val mMemoryCache = LruCache<String, AcctColor>(2048)

        override fun onDBCreate(db: SQLiteDatabase) {
            log.d("onDBCreate!")
            db.execSQL(
                """create table if not exists $table
                (_id INTEGER PRIMARY KEY
                ,$COL_TIME_SAVE integer not null
                ,$COL_ACCT text not null
                ,$COL_COLOR_FG integer
                ,$COL_COLOR_BG integer
                ,$COL_NICKNAME text
                ,$COL_NOTIFICATION_SOUND text default ''
                )""".trimIndent()
            )
            db.execSQL("create unique index if not exists ${table}_acct on $table($COL_ACCT)")
            db.execSQL("create index if not exists ${table}_time on $table($COL_TIME_SAVE)")
        }

        override fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 9 && newVersion >= 9) {
                onDBCreate(db)
                return
            }

            if (oldVersion < 17 && newVersion >= 17) {
                try {
                    db.execSQL("alter table $table add column $COL_NOTIFICATION_SOUND text default ''")
                } catch (ex: Throwable) {
                    log.trace(ex)
                }
            }
        }

        fun load(a: SavedAccount, who: TootAccount) = load(a.getFullAcct(who))
        fun load(a: SavedAccount) = load(a.acct)
        fun load(acct: Acct) = load(acct.ascii, acct.pretty)

        fun load(acctAscii: String, acctPretty: String): AcctColor {
            val key = acctAscii.lowercase()
            val cached: AcctColor? = mMemoryCache.get(key)
            if (cached != null) return cached

            try {
                val where_arg = load_where_arg.get() ?: arrayOfNulls<String?>(1)
                where_arg[0] = key
                App1.database.query(table, null, load_where, where_arg, null, null, null)
                    .use { cursor ->
                        if (cursor.moveToNext()) {

                            val ac = AcctColor(key, acctPretty)

                            ac.color_fg = cursor.getIntOrNull(COL_COLOR_FG) ?: 0
                            ac.color_bg = cursor.getIntOrNull(COL_COLOR_BG) ?: 0
                            ac.nicknameSave = cursor.getStringOrNull(COL_NICKNAME)
                            ac.notification_sound = cursor.getStringOrNull(COL_NOTIFICATION_SOUND)

                            mMemoryCache.put(key, ac)
                            return ac
                        }
                    }
            } catch (ex: Throwable) {
                log.trace(ex)
                log.e(ex, "load failed.")
            }

            log.d("lruCache size=${mMemoryCache.size()},hit=${mMemoryCache.hitCount()},miss=${mMemoryCache.missCount()}")
            val ac = AcctColor(key, acctPretty)
            mMemoryCache.put(key, ac)
            return ac
        }

//		fun getNickname(acct : String) : String {
//			val ac = load(acct)
//			val nickname = ac.nickname
//			return if(nickname != null && nickname.isNotEmpty()) nickname.sanitizeBDI() else acct
//		}

        private fun getNickname(acctAscii: String, acctPretty: String): String =
            load(acctAscii, acctPretty).nickname

        fun getNickname(acct: Acct): String =
            getNickname(acct.ascii, acct.pretty)

        fun getNickname(sa: SavedAccount): String =
            getNickname(sa.acct)

        fun getNickname(sa: SavedAccount, who: TootAccount): String =
            getNickname(sa.getFullAcct(who))

        fun getNicknameWithColor(sa: SavedAccount, who: TootAccount) =
            getNicknameWithColor(sa.getFullAcct(who))

        //		fun getNicknameWithColor(sa:SavedAccount,acctArg:String)  =
//			getNicknameWithColor(sa.getFullAcct(Acct.parse(acctArg)))
        fun getNicknameWithColor(acct: Acct) =
            getNicknameWithColor(acct.ascii, acct.pretty)

        private fun getNicknameWithColor(acctAscii: String, acctPretty: String): CharSequence {
            val ac = load(acctAscii, acctPretty)
            val sb = SpannableStringBuilder(ac.nickname.sanitizeBDI())
            val start = 0
            val end = sb.length
            if (ac.color_fg != 0) {
                sb.setSpan(
                    ForegroundColorSpan(ac.color_fg),
                    start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            if (ac.color_bg != 0) {
                sb.setSpan(
                    BackgroundColorSpan(ac.color_bg),
                    start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            return sb
        }

        fun getNotificationSound(acct: Acct): String? {
            return load(acct).notification_sound?.notEmpty()
        }

//		fun getNotificationSound(acctAscii : String) : String? {
//			return load(acctAscii,"").notification_sound?.notEmpty()
//			// acctPretty is not used in this case
//		}

        fun hasNickname(ac: AcctColor?): Boolean =
            null != ac?.nicknameSave?.notEmpty()

        fun hasColorForeground(ac: AcctColor?) = (ac?.color_fg ?: 0) != 0

        fun hasColorBackground(ac: AcctColor?) = (ac?.color_bg ?: 0) != 0

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
                if (ac.color_fg != 0) {
                    sb.setSpan(
                        ForegroundColorSpan(ac.color_fg),
                        i,
                        i + name.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                if (ac.color_bg != 0) {
                    sb.setSpan(
                        BackgroundColorSpan(ac.color_bg),
                        i,
                        i + name.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            return sb
        }
    }
}
