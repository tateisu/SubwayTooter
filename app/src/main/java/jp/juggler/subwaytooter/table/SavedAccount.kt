package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.PollingWorker
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.util.*
import java.util.*

class SavedAccount(
	val db_id : Long,
	acctArg : String,
	hostArg : String? = null,
	var token_info : JsonObject? = null,
	var loginAccount : TootAccount? = null, // 疑似アカウントではnull
	override val misskeyVersion : Int = 0
) : LinkHelper {
	
	val username : String
	
	override val host : Host
	val acct : Acct
	
	var visibility : TootVisibility = TootVisibility.Public
	var confirm_boost : Boolean = false
	var confirm_favourite : Boolean = false
	var confirm_unboost : Boolean = false
	var confirm_unfavourite : Boolean = false
	
	var dont_hide_nsfw : Boolean = false
	var dont_show_timeout : Boolean = false
	
	var notification_mention : Boolean = false
	var notification_boost : Boolean = false
	var notification_favourite : Boolean = false
	var notification_follow : Boolean = false
	var notification_follow_request : Boolean = false
	var notification_reaction : Boolean = false
	var notification_vote : Boolean = false
	var sound_uri = ""
	
	var confirm_follow : Boolean = false
	var confirm_follow_locked : Boolean = false
	var confirm_unfollow : Boolean = false
	var confirm_post : Boolean = false
	
	var notification_tag : String? = null
	private var register_key : String? = null
	private var register_time : Long = 0
	var default_text : String = ""
	
	var default_sensitive = false
	var expand_cw = false
	
	var max_toot_chars = 0
	
	var last_notification_error : String? = null
	var last_subscription_error : String? = null
	var last_push_endpoint : String? = null
	
	
	init {
		val tmpAcct = Acct.parse(acctArg)
		val tmpHost = hostArg?.notEmpty()?.let{ Host.parse(it)}
		this.host = tmpHost ?: tmpAcct.host ?: error("missing host in acct")
		
		this.username = tmpAcct.username
		this.acct = tmpAcct.followHost(host)

		if(username.isEmpty()) throw RuntimeException("missing username in acct")
	}
	
	constructor(context : Context, cursor : Cursor) : this(
		cursor.getLong(COL_ID), // db_id
		cursor.getString(COL_USER), // acct
		cursor.getString(COL_HOST) // host
		, misskeyVersion = cursor.getInt(COL_MISSKEY_VERSION)
	) {
		val strAccount = cursor.getString(COL_ACCOUNT)
		val jsonAccount = strAccount.decodeJsonObject()
		
		loginAccount = if(jsonAccount["id"] == null) {
			null // 疑似アカウント
		} else {
			TootParser(
				context,
				LinkHelper.newLinkHelper(this@SavedAccount.host, misskeyVersion = misskeyVersion)
			).account(jsonAccount)
				?: error("missing loginAccount for $strAccount")
		}
		
		val sv = cursor.getStringOrNull(COL_VISIBILITY)
		visibility = TootVisibility.parseSavedVisibility(sv) ?: TootVisibility.Public
		
		confirm_boost = cursor.getBoolean(COL_CONFIRM_BOOST)
		confirm_favourite = cursor.getBoolean(COL_CONFIRM_FAVOURITE)
		confirm_unboost = cursor.getBoolean(COL_CONFIRM_UNBOOST)
		confirm_unfavourite = cursor.getBoolean(COL_CONFIRM_UNFAVOURITE)
		confirm_follow = cursor.getBoolean(COL_CONFIRM_FOLLOW)
		confirm_follow_locked = cursor.getBoolean(COL_CONFIRM_FOLLOW_LOCKED)
		confirm_unfollow = cursor.getBoolean(COL_CONFIRM_UNFOLLOW)
		confirm_post = cursor.getBoolean(COL_CONFIRM_POST)
		
		notification_mention = cursor.getBoolean(COL_NOTIFICATION_MENTION)
		notification_boost = cursor.getBoolean(COL_NOTIFICATION_BOOST)
		notification_favourite = cursor.getBoolean(COL_NOTIFICATION_FAVOURITE)
		notification_follow = cursor.getBoolean(COL_NOTIFICATION_FOLLOW)
		notification_follow_request = cursor.getBoolean(COL_NOTIFICATION_FOLLOW_REQUEST)
		notification_reaction = cursor.getBoolean(COL_NOTIFICATION_REACTION)
		notification_vote = cursor.getBoolean(COL_NOTIFICATION_VOTE)
		
		dont_hide_nsfw = cursor.getBoolean(COL_DONT_HIDE_NSFW)
		dont_show_timeout = cursor.getBoolean(COL_DONT_SHOW_TIMEOUT)
		
		notification_tag = cursor.getStringOrNull(COL_NOTIFICATION_TAG)
		
		register_key = cursor.getStringOrNull(COL_REGISTER_KEY)
		
		register_time = cursor.getLong(COL_REGISTER_TIME)
		
		token_info = cursor.getString(COL_TOKEN).decodeJsonObject()
		
		sound_uri = cursor.getString(COL_SOUND_URI)
		
		default_text = cursor.getStringOrNull(COL_DEFAULT_TEXT) ?: ""
		
		default_sensitive = cursor.getBoolean(COL_DEFAULT_SENSITIVE)
		expand_cw = cursor.getBoolean(COL_EXPAND_CW)
		max_toot_chars = cursor.getInt(COL_MAX_TOOT_CHARS)
		
		last_notification_error = cursor.getStringOrNull(COL_LAST_NOTIFICATION_ERROR)
		last_subscription_error = cursor.getStringOrNull(COL_LAST_SUBSCRIPTION_ERROR)
		last_push_endpoint = cursor.getStringOrNull(COL_LAST_PUSH_ENDPOINT)
	}
	
	val isNA : Boolean
		get() = acct == Acct.UNKNOWN
	
	val isPseudo : Boolean
		get() = username == "?"
	
	fun delete() {
		try {
			App1.database.delete(table, "$COL_ID=?", arrayOf(db_id.toString()))
		} catch(ex : Throwable) {
			log.trace(ex)
			throw RuntimeException("SavedAccount.delete failed.", ex)
		}
		
	}
	
	fun updateTokenInfo(tokenInfoArg : JsonObject?) {
		
		if(db_id == INVALID_DB_ID) throw RuntimeException("updateTokenInfo: missing db_id")
		
		val token_info = tokenInfoArg ?: JsonObject()
		this.token_info = token_info
		
		val cv = ContentValues()
		cv.put(COL_TOKEN, token_info.toString())
		App1.database.update(table, cv, "$COL_ID=?", arrayOf(db_id.toString()))
	}
	
	fun saveSetting() {
		
		if(db_id == INVALID_DB_ID) throw RuntimeException("saveSetting: missing db_id")
		
		val cv = ContentValues()
		cv.put(COL_VISIBILITY, visibility.id.toString())
		cv.put(COL_CONFIRM_BOOST, confirm_boost.b2i())
		cv.put(COL_CONFIRM_FAVOURITE, confirm_favourite.b2i())
		cv.put(COL_CONFIRM_UNBOOST, confirm_unboost.b2i())
		cv.put(COL_CONFIRM_UNFAVOURITE, confirm_unfavourite.b2i())
		
		cv.put(COL_DONT_HIDE_NSFW, dont_hide_nsfw.b2i())
		cv.put(COL_DONT_SHOW_TIMEOUT, dont_show_timeout.b2i())
		cv.put(COL_NOTIFICATION_MENTION, notification_mention.b2i())
		cv.put(COL_NOTIFICATION_BOOST, notification_boost.b2i())
		cv.put(COL_NOTIFICATION_FAVOURITE, notification_favourite.b2i())
		cv.put(COL_NOTIFICATION_FOLLOW, notification_follow.b2i())
		cv.put(COL_NOTIFICATION_FOLLOW_REQUEST, notification_follow_request.b2i())
		cv.put(COL_NOTIFICATION_REACTION, notification_reaction.b2i())
		cv.put(COL_NOTIFICATION_VOTE, notification_vote.b2i())
		
		cv.put(COL_CONFIRM_FOLLOW, confirm_follow.b2i())
		cv.put(COL_CONFIRM_FOLLOW_LOCKED, confirm_follow_locked.b2i())
		cv.put(COL_CONFIRM_UNFOLLOW, confirm_unfollow.b2i())
		cv.put(COL_CONFIRM_POST, confirm_post.b2i())
		
		cv.put(COL_SOUND_URI, sound_uri)
		cv.put(COL_DEFAULT_TEXT, default_text)
		
		cv.put(COL_DEFAULT_SENSITIVE, default_sensitive.b2i())
		cv.put(COL_EXPAND_CW, expand_cw.b2i())
		cv.put(COL_MAX_TOOT_CHARS, max_toot_chars)
		
		// UIからは更新しない
		// notification_tag
		// register_key
		
		App1.database.update(table, cv, "$COL_ID=?", arrayOf(db_id.toString()))
	}
	
	//	fun saveNotificationTag() {
	//		if(db_id == INVALID_DB_ID)
	//			throw RuntimeException("SavedAccount.saveNotificationTag missing db_id")
	//
	//		val cv = ContentValues()
	//		cv.put(COL_NOTIFICATION_TAG, notification_tag)
	//
	//		App1.database.update(table, cv, "$COL_ID=?", arrayOf(db_id.toString()))
	//	}
	//
	//	fun saveRegisterKey() {
	//		if(db_id == INVALID_DB_ID)
	//			throw RuntimeException("SavedAccount.saveRegisterKey missing db_id")
	//
	//		val cv = ContentValues()
	//		cv.put(COL_REGISTER_KEY, register_key)
	//		cv.put(COL_REGISTER_TIME, register_time)
	//
	//		App1.database.update(table, cv, "$COL_ID=?", arrayOf(db_id.toString()))
	//	}
	
	// onResumeの時に設定を読み直す
	fun reloadSetting(context : Context) {
		
		if(db_id == INVALID_DB_ID)
			throw RuntimeException("SavedAccount.reloadSetting missing db_id")
		
		// DBから削除されてるかもしれない
		val b = loadAccount(context, db_id) ?: return
		
		this.visibility = b.visibility
		this.confirm_boost = b.confirm_boost
		this.confirm_favourite = b.confirm_favourite
		this.confirm_unboost = b.confirm_unboost
		this.confirm_unfavourite = b.confirm_unfavourite
		
		this.dont_hide_nsfw = b.dont_hide_nsfw
		this.dont_show_timeout = b.dont_show_timeout
		this.token_info = b.token_info
		this.notification_mention = b.notification_mention
		this.notification_boost = b.notification_boost
		this.notification_favourite = b.notification_favourite
		this.notification_follow = b.notification_follow
		this.notification_follow_request = b.notification_follow_request
		this.notification_reaction = b.notification_reaction
		this.notification_vote = b.notification_vote
		this.notification_tag = b.notification_tag
		this.default_text = b.default_text
		this.default_sensitive = b.default_sensitive
		this.expand_cw = b.expand_cw
		
		this.sound_uri = b.sound_uri
	}
	
	fun getFullAcct(who : TootAccount?) = getFullAcct(who?.acct)
	
	fun isRemoteUser(who : TootAccount) : Boolean = ! isLocalUser(who.acct)
	fun isLocalUser(who : TootAccount?) : Boolean = isLocalUser(who?.acct)
	private fun isLocalUser(acct : Acct?) : Boolean {
		acct ?: return false
		return acct.host == null || acct.host == this.host
	}
	
	
	//	fun isRemoteUser(acct : String) : Boolean {
	//		return ! isLocalUser(acct)
	//	}
	
	fun isMe(who : TootAccount?) : Boolean = isMe(who?.acct)
	fun isMe(who_acct : String) : Boolean  = isMe(Acct.parse(who_acct))

	fun isMe(who_acct : Acct?):Boolean{
		who_acct?:return false
		if( who_acct.username != this.acct.username) return false
		return who_acct.host == null || who_acct.host == this.acct.host
	}

	fun supplyBaseUrl(url : String?) : String? {
		return when {
			url == null || url.isEmpty() -> return null
			url[0] == '/' -> "https://${host.ascii}$url"
			else -> url
		}
	}
	
	fun isNicoru(account : TootAccount?) : Boolean = account?.host == Host.FRIENDS_NICO
	
	companion object : TableCompanion {
		private val log = LogCategory("SavedAccount")
		
		const val table = "access_info"
		
		private const val COL_ID = BaseColumns._ID
		private const val COL_HOST = "h"
		private const val COL_USER = "u"
		private const val COL_ACCOUNT = "a"
		private const val COL_TOKEN = "t"
		
		private const val COL_VISIBILITY = "visibility"
		private const val COL_CONFIRM_BOOST = "confirm_boost"
		private const val COL_DONT_HIDE_NSFW = "dont_hide_nsfw"
		
		private const val COL_NOTIFICATION_MENTION = "notification_mention" // スキーマ2
		private const val COL_NOTIFICATION_BOOST = "notification_boost" // スキーマ2
		private const val COL_NOTIFICATION_FAVOURITE = "notification_favourite" // スキーマ2
		private const val COL_NOTIFICATION_FOLLOW = "notification_follow" // スキーマ2
		private const val COL_NOTIFICATION_FOLLOW_REQUEST = "notification_follow_request" // スキーマ44
		private const val COL_NOTIFICATION_REACTION = "notification_reaction" // スキーマ33
		private const val COL_NOTIFICATION_VOTE = "notification_vote" // スキーマ33
		
		private const val COL_CONFIRM_FOLLOW = "confirm_follow" // スキーマ10
		private const val COL_CONFIRM_FOLLOW_LOCKED = "confirm_follow_locked" // スキーマ10
		private const val COL_CONFIRM_UNFOLLOW = "confirm_unfollow" // スキーマ10
		private const val COL_CONFIRM_POST = "confirm_post" // スキーマ10
		private const val COL_CONFIRM_FAVOURITE = "confirm_favourite" // スキーマ23
		private const val COL_CONFIRM_UNBOOST = "confirm_unboost" // スキーマ24
		private const val COL_CONFIRM_UNFAVOURITE = "confirm_unfavourite" // スキーマ24
		
		// スキーマ13から
		const val COL_NOTIFICATION_TAG = "notification_server"
		
		// スキーマ14から
		const val COL_REGISTER_KEY = "register_key"
		const val COL_REGISTER_TIME = "register_time"
		
		// スキーマ16から
		private const val COL_SOUND_URI = "sound_uri"
		
		// スキーマ18から
		private const val COL_DONT_SHOW_TIMEOUT = "dont_show_timeout"
		
		// スキーマ27から
		private const val COL_DEFAULT_TEXT = "default_text"
		
		// スキーマ28から
		private const val COL_MISSKEY_VERSION = "is_misskey" // カラム名がおかしいのは、昔はboolean扱いだったから
		// 0: not misskey
		// 1: old(v10) misskey
		// 11: misskey v11
		
		private const val COL_DEFAULT_SENSITIVE = "default_sensitive"
		private const val COL_EXPAND_CW = "expand_cw"
		private const val COL_MAX_TOOT_CHARS = "max_toot_chars"
		
		private const val COL_LAST_NOTIFICATION_ERROR = "last_notification_error" // スキーマ42
		private const val COL_LAST_SUBSCRIPTION_ERROR = "last_subscription_error" // スキーマ45
		private const val COL_LAST_PUSH_ENDPOINT = "last_push_endpoint" // スキーマ46
		
		/////////////////////////////////
		// login information
		const val INVALID_DB_ID = - 1L
		
		// アプリデータのインポート時に呼ばれる
		fun onDBDelete(db : SQLiteDatabase) {
			try {
				db.execSQL("drop table if exists $table")
			} catch(ex : Throwable) {
				log.trace(ex)
			}
		}
		
		override fun onDBCreate(db : SQLiteDatabase) {
			db.execSQL(
				"create table if not exists $table"
					+ "($COL_ID INTEGER PRIMARY KEY"
					+ ",$COL_USER text not null"
					+ ",$COL_HOST text not null"
					+ ",$COL_ACCOUNT text not null"
					+ ",$COL_TOKEN text not null"
					+ ",$COL_VISIBILITY text"
					+ ",$COL_CONFIRM_BOOST integer default 1"
					+ ",$COL_DONT_HIDE_NSFW integer default 0"
					
					// 以下はDBスキーマ2で追加
					+ ",$COL_NOTIFICATION_MENTION integer default 1"
					+ ",$COL_NOTIFICATION_BOOST integer default 1"
					+ ",$COL_NOTIFICATION_FAVOURITE integer default 1"
					+ ",$COL_NOTIFICATION_FOLLOW integer default 1"
					
					// 以下はDBスキーマ10で更新
					+ ",$COL_CONFIRM_FOLLOW integer default 1"
					+ ",$COL_CONFIRM_FOLLOW_LOCKED integer default 1"
					+ ",$COL_CONFIRM_UNFOLLOW integer default 1"
					+ ",$COL_CONFIRM_POST integer default 1"
					
					// 以下はDBスキーマ13で更新
					+ ",$COL_NOTIFICATION_TAG text default ''"
					
					// 以下はDBスキーマ14で更新
					+ ",$COL_REGISTER_KEY text default ''"
					+ ",$COL_REGISTER_TIME integer default 0"
					
					// 以下はDBスキーマ16で更新
					+ ",$COL_SOUND_URI text default ''"
					
					// 以下はDBスキーマ18で更新
					+ ",$COL_DONT_SHOW_TIMEOUT integer default 0"
					
					// 以下はDBスキーマ23で更新
					+ ",$COL_CONFIRM_FAVOURITE integer default 1"
					
					// 以下はDBスキーマ24で更新
					+ ",$COL_CONFIRM_UNBOOST integer default 1"
					+ ",$COL_CONFIRM_UNFAVOURITE integer default 1"
					
					// 以下はDBスキーマ27で更新
					+ ",$COL_DEFAULT_TEXT text default ''"
					
					// 以下はDBスキーマ28で更新
					+ ",$COL_MISSKEY_VERSION integer default 0"
					
					// スキーマ33から
					+ ",$COL_NOTIFICATION_REACTION integer default 1"
					+ ",$COL_NOTIFICATION_VOTE integer default 1"
					
					// スキーマ37から
					+ ",$COL_DEFAULT_SENSITIVE integer default 0"
					+ ",$COL_EXPAND_CW integer default 0"
					
					// スキーマ39から
					+ ",$COL_MAX_TOOT_CHARS integer default 0"
					
					// スキーマ42から
					+ ",$COL_LAST_NOTIFICATION_ERROR text"
					
					// スキーマ44から
					+ ",$COL_NOTIFICATION_FOLLOW_REQUEST integer default 1"
					
					// スキーマ45から
					+ ",$COL_LAST_SUBSCRIPTION_ERROR text"
					
					// スキーマ46から
					+ ",$COL_LAST_PUSH_ENDPOINT text"
					
					+ ")"
			)
			db.execSQL("create index if not exists ${table}_user on ${table}(u)")
			db.execSQL("create index if not exists ${table}_host on ${table}(h,u)")
		}
		
		override fun onDBUpgrade(db : SQLiteDatabase, oldVersion : Int, newVersion : Int) {
			if(oldVersion < 2 && newVersion >= 2) {
				try {
					db.execSQL("alter table $table add column notification_mention integer default 1")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
				try {
					db.execSQL("alter table $table add column notification_boost integer default 1")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
				try {
					db.execSQL("alter table $table add column notification_favourite integer default 1")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
				try {
					db.execSQL("alter table $table add column notification_follow integer default 1")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
			}
			if(oldVersion < 10 && newVersion >= 10) {
				try {
					db.execSQL("alter table $table add column $COL_CONFIRM_FOLLOW integer default 1")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
				try {
					db.execSQL("alter table $table add column $COL_CONFIRM_FOLLOW_LOCKED integer default 1")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
				try {
					db.execSQL("alter table $table add column $COL_CONFIRM_UNFOLLOW integer default 1")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
				try {
					db.execSQL("alter table $table add column $COL_CONFIRM_POST integer default 1")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
			}
			if(oldVersion < 13 && newVersion >= 13) {
				try {
					db.execSQL("alter table $table add column $COL_NOTIFICATION_TAG text default ''")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
			}
			if(oldVersion < 14 && newVersion >= 14) {
				try {
					db.execSQL("alter table $table add column $COL_REGISTER_KEY text default ''")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
				try {
					db.execSQL("alter table $table add column $COL_REGISTER_TIME integer default 0")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
			}
			if(oldVersion < 16 && newVersion >= 16) {
				try {
					db.execSQL("alter table $table add column $COL_SOUND_URI text default ''")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
			}
			if(oldVersion < 18 && newVersion >= 18) {
				try {
					db.execSQL("alter table $table add column $COL_DONT_SHOW_TIMEOUT integer default 0")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
			}
			if(oldVersion < 23 && newVersion >= 23) {
				try {
					db.execSQL("alter table $table add column $COL_CONFIRM_FAVOURITE integer default 1")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
			}
			if(oldVersion < 24 && newVersion >= 24) {
				try {
					db.execSQL("alter table $table add column $COL_CONFIRM_UNFAVOURITE integer default 1")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				try {
					db.execSQL("alter table $table add column $COL_CONFIRM_UNBOOST integer default 1")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
			}
			if(oldVersion < 27 && newVersion >= 27) {
				try {
					db.execSQL("alter table $table add column $COL_DEFAULT_TEXT text default ''")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
			}
			if(oldVersion < 28 && newVersion >= 28) {
				try {
					db.execSQL("alter table $table add column $COL_MISSKEY_VERSION integer default 0")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
			}
			if(oldVersion < 33 && newVersion >= 33) {
				try {
					db.execSQL("alter table $table add column $COL_NOTIFICATION_REACTION integer default 1")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				try {
					db.execSQL("alter table $table add column $COL_NOTIFICATION_VOTE integer default 1")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
			}
			if(oldVersion < 38 && newVersion >= 38) {
				try {
					db.execSQL("alter table $table add column $COL_DEFAULT_SENSITIVE integer default 0")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				try {
					db.execSQL("alter table $table add column $COL_EXPAND_CW integer default 0")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
			}
			
			if(oldVersion < 39 && newVersion >= 39) {
				try {
					db.execSQL("alter table $table add column $COL_MAX_TOOT_CHARS integer default 0")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
			}
			
			if(oldVersion < 42 && newVersion >= 42) {
				try {
					db.execSQL("alter table $table add column $COL_LAST_NOTIFICATION_ERROR text")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
			}
			
			if(oldVersion < 44 && newVersion >= 44) {
				try {
					db.execSQL("alter table $table add column $COL_NOTIFICATION_FOLLOW_REQUEST integer default 1")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
			}
			
			if(oldVersion < 45 && newVersion >= 45) {
				try {
					db.execSQL("alter table $table add column $COL_LAST_SUBSCRIPTION_ERROR text")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
			}
			if(oldVersion < 46 && newVersion >= 46) {
				try {
					db.execSQL("alter table $table add column $COL_LAST_PUSH_ENDPOINT text")
				} catch(ex : Throwable) {
					log.trace(ex)
				}
			}
			
		}
		
		// 横断検索用の、何とも紐ついていないアカウント
		// 保存しない。
		val na : SavedAccount by lazy {
			val dst = SavedAccount(- 1L, "?@?")
			dst.notification_follow = false
			dst.notification_follow_request = false
			dst.notification_favourite = false
			dst.notification_boost = false
			dst.notification_mention = false
			dst.notification_reaction = false
			dst.notification_vote = false
			
			dst
		}
		
		private fun parse(context : Context, cursor : Cursor) : SavedAccount? {
			return try {
				SavedAccount(context, cursor)
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "parse failed.")
				null
			}
		}
		
		fun insert(
			host : String,
			acct : String,
			account : JsonObject,
			token : JsonObject,
			misskeyVersion : Int = 0
		) : Long {
			try {
				val cv = ContentValues()
				cv.put(COL_HOST, host)
				cv.put(COL_USER, acct)
				cv.put(COL_ACCOUNT, account.toString())
				cv.put(COL_TOKEN, token.toString())
				cv.put(COL_MISSKEY_VERSION, misskeyVersion)
				return App1.database.insert(table, null, cv)
			} catch(ex : Throwable) {
				log.trace(ex)
				throw RuntimeException("SavedAccount.insert failed.", ex)
			}
			
		}
		
		private const val REGISTER_KEY_UNREGISTERED = "unregistered"
		
		fun clearRegistrationCache() {
			val cv = ContentValues()
			cv.put(COL_REGISTER_KEY, REGISTER_KEY_UNREGISTERED)
			cv.put(COL_REGISTER_TIME, 0L)
			App1.database.update(table, cv, null, null)
		}
		
		fun loadAccount(context : Context, db_id : Long) : SavedAccount? {
			try {
				App1.database.query(
					table,
					null,
					"$COL_ID=?",
					arrayOf(db_id.toString()),
					null,
					null,
					null
				)
					.use { cursor ->
						if(cursor.moveToFirst()) {
							return parse(context, cursor)
						}
						log.e("moveToFirst failed. db_id=$db_id")
					}
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "loadAccount failed.")
			}
			
			return null
		}
		
		fun loadAccountList(context : Context) : ArrayList<SavedAccount> {
			val result = ArrayList<SavedAccount>()
			try {
				App1.database.query(
					table,
					null,
					null,
					null,
					null,
					null,
					null
				)
					.use { cursor ->
						while(cursor.moveToNext()) {
							val a = parse(context, cursor)
							if(a != null) result.add(a)
						}
					}
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "loadAccountList failed.")
				showToast(context, true, ex.withCaption("(SubwayTooter) broken in-app database?"))
			}
			
			return result
		}
		
		fun loadByTag(context : Context, tag : String) : ArrayList<SavedAccount> {
			val result = ArrayList<SavedAccount>()
			try {
				App1.database.query(
					table,
					null,
					"$COL_NOTIFICATION_TAG=?",
					arrayOf(tag),
					null,
					null,
					null
				)
					.use { cursor ->
						while(cursor.moveToNext()) {
							val a = parse(context, cursor)
							if(a != null) result.add(a)
						}
						
					}
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "loadByTag failed.")
				throw RuntimeException("SavedAccount.loadByTag failed.", ex)
			}
			
			return result
		}
		
		fun loadAccountByAcct(context : Context, full_acct : String) : SavedAccount? {
			try {
				App1.database.query(
					table,
					null,
					"$COL_USER=?",
					arrayOf(full_acct),
					null,
					null,
					null
				)
					.use { cursor ->
						if(cursor.moveToNext()) {
							return parse(context, cursor)
						}
					}
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "loadAccountByAcct failed.")
			}
			
			return null
		}
		
		fun hasRealAccount() : Boolean {
			try {
				App1.database.query(
					table,
					null,
					"$COL_USER NOT LIKE '?@%'",
					null,
					null,
					null,
					null,
					"1"
				)
					.use { cursor ->
						if(cursor.moveToNext()) {
							return true
						}
					}
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "hasNonPseudoAccount failed.")
			}
			
			return false
		}
		
		val count : Long
			get() {
				try {
					App1.database.query(table, arrayOf("count(*)"), null, null, null, null, null)
						.use { cursor ->
							if(cursor.moveToNext()) {
								return cursor.getLong(0)
							}
						}
				} catch(ex : Throwable) {
					log.trace(ex)
					log.e(ex, "getCount failed.")
					throw RuntimeException("SavedAccount.getCount failed.", ex)
				}
				
				return 0L
			}
		
		fun isNicoru(acct:Acct) : Boolean {
			return acct.host == Host.FRIENDS_NICO
		}
		
		private fun charAtLower(src : CharSequence, pos : Int) : Char {
			val c = src[pos]
			return if(c >= 'a' && c <= 'z') c - ('a' - 'A') else c
		}
		
		@Suppress("SameParameterValue")
		private fun host_match(
			a : CharSequence,
			a_startArg : Int,
			b : CharSequence,
			b_startArg : Int
		) : Boolean {
			var a_start = a_startArg
			var b_start = b_startArg
			
			val a_end = a.length
			val b_end = b.length
			
			var a_remain = a_end - a_start
			val b_remain = b_end - b_start
			
			// 文字数が違う
			if(a_remain != b_remain) return false
			
			// 文字数がゼロ
			if(a_remain <= 0) return true
			
			// 末尾の文字が違う
			if(charAtLower(a, a_end - 1) != charAtLower(b, b_end - 1)) return false
			
			// 先頭からチェック
			while(a_remain -- > 0) {
				if(charAtLower(a, a_start ++) != charAtLower(b, b_start ++)) return false
			}
			
			return true
		}
		
		private val account_comparator = Comparator<SavedAccount> { a, b ->
			var i : Int
			
			// NA > !NA
			i = a.isNA.b2i() - b.isNA.b2i()
			if(i != 0) return@Comparator i
			
			// pseudo > real
			i = a.isPseudo.b2i() - b.isPseudo.b2i()
			if(i != 0) return@Comparator i
			
			val sa = AcctColor.getNickname(a)
			val sb = AcctColor.getNickname(b)
			sa.compareTo(sb, ignoreCase = true)
		}
		
		fun sort(account_list : MutableList<SavedAccount>) {
			Collections.sort(account_list, account_comparator)
		}
		
		fun sweepBuggieData() {
			// https://github.com/tateisu/SubwayTooter/issues/107
			// COL_ACCOUNTの内容がおかしければ削除する
			
			val list = ArrayList<Long>()
			try {
				App1.database.query(
					table,
					null,
					"$COL_ACCOUNT like ?",
					arrayOf("jp.juggler.subwaytooter.api.entity.TootAccount@%"),
					null,
					null,
					null
				).use { cursor ->
					while(cursor.moveToNext()) {
						list.add(cursor.getLong(COL_ID))
					}
				}
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "sweepBuggieData failed.")
			}
			
			list.forEach {
				try {
					App1.database.delete(table, "$COL_ID=?", arrayOf(it.toString()))
				} catch(ex : Throwable) {
					log.trace(ex)
					log.e(ex, "sweepBuggieData failed.")
				}
			}
		}
	}
	
	fun getAccessToken() : String? {
		return token_info?.string("access_token")
	}
	
	val misskeyApiToken : String?
		get() = token_info?.string(TootApiClient.KEY_API_KEY_MISSKEY)
	
	fun putMisskeyApiToken(params : JsonObject = JsonObject()) : JsonObject {
		val apiKey = misskeyApiToken
		if(apiKey?.isNotEmpty() == true) params["i"] = apiKey
		return params
	}
	
	fun canNotificationShowing(type : String?) = when(type) {
		
		TootNotification.TYPE_MENTION,
		TootNotification.TYPE_REPLY -> notification_mention
		
		TootNotification.TYPE_REBLOG,
		TootNotification.TYPE_RENOTE,
		TootNotification.TYPE_QUOTE -> notification_boost
		
		TootNotification.TYPE_FAVOURITE -> notification_favourite
		
		TootNotification.TYPE_FOLLOW,
		TootNotification.TYPE_UNFOLLOW -> notification_follow
		
		TootNotification.TYPE_FOLLOW_REQUEST,
		TootNotification.TYPE_FOLLOW_REQUEST_MISSKEY -> notification_follow_request
		
		TootNotification.TYPE_REACTION -> notification_reaction
		
		TootNotification.TYPE_VOTE, TootNotification.TYPE_POLL -> notification_vote
		
		else -> false
	}
	
	val isConfirmed : Boolean
		get() {
			val myId = this.loginAccount?.id
			return myId != EntityId.CONFIRMING
		}
	
	fun checkConfirmed(context : Context, client : TootApiClient) : TootApiResult? {
		try {
			val myId = this.loginAccount?.id
			if(db_id != INVALID_DB_ID && myId == EntityId.CONFIRMING) {
				val accessToken = getAccessToken()
				if(accessToken != null) {
					val result = client.getUserCredential(accessToken)
					if(result == null || result.error != null) return result
					val ta = TootParser(context, this).account(result.jsonObject)
					if(ta != null) {
						this.loginAccount = ta
						val cv = ContentValues()
						cv.put(COL_ACCOUNT, result.jsonObject.toString())
						App1.database.update(table, cv, "$COL_ID=?", arrayOf(db_id.toString()))
						PollingWorker.queueUpdateNotification(context)
					}
				}
			}
			return TootApiResult()
		} catch(ex : Throwable) {
			log.trace(ex)
			return TootApiResult(ex.withCaption("account confirmation failed."))
		}
	}
	
	fun updateNotificationError(text : String?) {
		this.last_notification_error = text
		if(db_id != INVALID_DB_ID){
			val cv = ContentValues()
			when(text) {
				null -> cv.putNull(COL_LAST_NOTIFICATION_ERROR)
				else -> cv.put(COL_LAST_NOTIFICATION_ERROR, text)
			}
			App1.database.update(table, cv, "$COL_ID=?", arrayOf(db_id.toString()))
		}
	}
	
	fun updateSubscriptionError(text : String?) {
		this.last_subscription_error = text
		if(db_id != INVALID_DB_ID){
			val cv = ContentValues()
			when(text) {
				null -> cv.putNull(COL_LAST_SUBSCRIPTION_ERROR)
				else -> cv.put(COL_LAST_SUBSCRIPTION_ERROR, text)
			}
			App1.database.update(table, cv, "$COL_ID=?", arrayOf(db_id.toString()))
		}
	}
	
	fun updateLastPushEndpoint(text : String?) {
		this.last_push_endpoint = text
		if(db_id != INVALID_DB_ID){
			val cv = ContentValues()
			when(text) {
				null -> cv.putNull(COL_LAST_PUSH_ENDPOINT)
				else -> cv.put(COL_LAST_PUSH_ENDPOINT, text)
			}
			App1.database.update(table, cv, "$COL_ID=?", arrayOf(db_id.toString()))
		}
	}
	
	override fun equals(other : Any?) : Boolean =
		when(other) {
			is SavedAccount -> acct == other.acct
			else -> false
		}
	
	override fun hashCode() : Int = acct.hashCode()
	
	
}
