package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.collection.LruCache
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.util.LogCategory
import jp.juggler.util.getIntOrNull
import jp.juggler.util.getStringOrNull
import jp.juggler.util.sanitizeBDI
import java.util.*

class AcctColor {
	
	var acct : String
	var color_fg : Int = 0
	var color_bg : Int = 0
	var nickname : String? = null
	var notification_sound : String? = null
	
	constructor(
		acct : String,
		nickname : String,
		color_fg : Int,
		color_bg : Int,
		notification_sound : String?
	) {
		this.acct = acct
		this.nickname = nickname
		this.color_fg = color_fg
		this.color_bg = color_bg
		this.notification_sound = notification_sound
	}
	
	private constructor(acct : String) {
		this.acct = acct
	}
	
	fun save(now : Long) {
		
		acct = acct.toLowerCase(Locale.ENGLISH)
		
		try {
			val cv = ContentValues()
			cv.put(COL_TIME_SAVE, now)
			cv.put(COL_ACCT, acct)
			cv.put(COL_COLOR_FG, color_fg)
			cv.put(COL_COLOR_BG, color_bg)
			cv.put(COL_NICKNAME, if(nickname == null) "" else nickname)
			cv.put(
				COL_NOTIFICATION_SOUND,
				if(notification_sound == null) "" else notification_sound
			)
			App1.database.replace(table, null, cv)
			mMemoryCache.remove(acct)
		} catch(ex : Throwable) {
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
		
		private const val CHAR_REPLACE : Char = 0x328A.toChar()
		
		private const val load_where = "$COL_ACCT=?"
		
		private val load_where_arg = object : ThreadLocal<Array<String?>>() {
			override fun initialValue() : Array<String?> {
				return arrayOfNulls(1)
			}
		}
		
		private val mMemoryCache = LruCache<String, AcctColor>(2048)
		
		override fun onDBCreate(db : SQLiteDatabase) {
			log.d("onDBCreate!")
			db.execSQL(
				"create table if not exists " + table
					+ "(_id INTEGER PRIMARY KEY"
					+ "," + COL_TIME_SAVE + " integer not null"
					+ "," + COL_ACCT + " text not null"
					+ "," + COL_COLOR_FG + " integer"
					+ "," + COL_COLOR_BG + " integer"
					+ "," + COL_NICKNAME + " text "
					+ "," + COL_NOTIFICATION_SOUND + " text default ''"
					+ ")"
			)
			db.execSQL(
				"create unique index if not exists " + table + "_acct on " + table + "(" + COL_ACCT + ")"
			)
			db.execSQL(
				"create index if not exists " + table + "_time on " + table + "(" + COL_TIME_SAVE + ")"
			)
		}
		
		override fun onDBUpgrade(db : SQLiteDatabase, oldVersion : Int, newVersion : Int) {
			if(oldVersion < 9 && newVersion >= 9) {
				onDBCreate(db)
				return
			}
			
			if(oldVersion < 17 && newVersion >= 17) {
				try {
					db.execSQL("alter table $table add column $COL_NOTIFICATION_SOUND text default ''")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
			}
		}
		
		fun load(acctArg : String) : AcctColor {
			val acct = acctArg.toLowerCase(Locale.ENGLISH)
			val cached : AcctColor? = mMemoryCache.get(acct)
			if(cached != null) return cached
			
			try {
				val where_arg = load_where_arg.get() ?: arrayOfNulls<String?>(1)
				where_arg[0] = acct
				App1.database.query(table, null, load_where, where_arg, null, null, null)
					.use { cursor ->
						if(cursor.moveToNext()) {
							
							val ac = AcctColor(acct)
							
							ac.color_fg = cursor.getIntOrNull(COL_COLOR_FG) ?: 0
							ac.color_bg = cursor.getIntOrNull(COL_COLOR_BG) ?: 0
							ac.nickname = cursor.getStringOrNull(COL_NICKNAME)
							ac.notification_sound = cursor.getStringOrNull(COL_NOTIFICATION_SOUND)
							
							mMemoryCache.put(acct, ac)
							return ac
						}
						
					}
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "load failed.")
			}
			
			log.d(
				"lruCache size=%s,hit=%s,miss=%s",
				mMemoryCache.size(),
				mMemoryCache.hitCount(),
				mMemoryCache.missCount()
			)
			val ac = AcctColor(acct)
			mMemoryCache.put(acct, ac)
			return ac
		}
		
//		fun getNickname(acct : String) : String {
//			val ac = load(acct)
//			val nickname = ac.nickname
//			return if(nickname != null && nickname.isNotEmpty()) nickname.sanitizeBDI() else acct
//		}
		fun getNickname(acct:String,prettyAcct:String) : String {
			val ac = load(acct)
			val nickname = ac.nickname
			return if(nickname != null && nickname.isNotEmpty()) nickname.sanitizeBDI() else prettyAcct
		}
		fun getNickname(sa:SavedAccount) : String = getNickname(sa.acctAscii,sa.acctPretty)

		fun getNickname(sa:SavedAccount,who:TootAccount) : String = getNickname(sa.getFullAcct(who),sa.getFullPrettyAcct(who))
		
		fun getNicknameWithColor(sa:SavedAccount,who:TootAccount) : CharSequence =
			getNicknameWithColor(sa.getFullAcct(who),sa.getFullPrettyAcct(who))
		
		fun getNicknameWithColor(sa:SavedAccount,acctArg:String) : CharSequence {
			val(acct,prettyAcct)=TootAccount.acctAndPrettyAcct(sa.getFullAcct(acctArg))
			return getNicknameWithColor(acct,prettyAcct)
		}
		
		fun getNicknameWithColor(acct : String,prettyAcct : String) : CharSequence {
			val ac = load(acct)
			val nickname = ac.nickname
			val name = if(nickname == null || nickname.isEmpty()) prettyAcct else nickname.sanitizeBDI()
			val sb = SpannableStringBuilder(name)
			val start = 0
			val end = sb.length
			if(ac.color_fg != 0) {
				sb.setSpan(
					ForegroundColorSpan(ac.color_fg),
					start, end,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				)
			}
			if(ac.color_bg != 0) {
				sb.setSpan(
					BackgroundColorSpan(ac.color_bg),
					start, end,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				)
			}
			return sb
		}
		
		fun getNotificationSound(acct : String) : String? {
			val ac = load(acct)
			val notification_sound = ac.notification_sound
			return if(notification_sound != null && notification_sound.isNotEmpty()) notification_sound else null
		}
		
		fun hasNickname(ac : AcctColor?) : Boolean {
			val nickname = ac?.nickname
			return nickname != null && nickname.isNotEmpty()
		}
		
		fun hasColorForeground(ac : AcctColor?) = (ac?.color_fg ?: 0) != 0
		
		fun hasColorBackground(ac : AcctColor?) = (ac?.color_bg ?: 0) != 0
		
		fun clearMemoryCache() {
			mMemoryCache.evictAll()
		}
		
		fun getStringWithNickname(
			context : Context,
			string_id : Int,
			acct : String,
			prettyAcct:String
		) : CharSequence {
			val ac = load(acct)
			val nickname = ac.nickname
			val name = if(nickname == null || nickname.isEmpty()) prettyAcct else nickname.sanitizeBDI()
			val sb = SpannableStringBuilder(
				context.getString(
					string_id,
					String(charArrayOf(CHAR_REPLACE))
				)
			)
			for(i in sb.length - 1 downTo 0) {
				val c = sb[i]
				if(c != CHAR_REPLACE) continue
				sb.replace(i, i + 1, name)
				if(ac.color_fg != 0) {
					sb.setSpan(
						ForegroundColorSpan(ac.color_fg),
						i,
						i + name.length,
						Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
					)
				}
				if(ac.color_bg != 0) {
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
