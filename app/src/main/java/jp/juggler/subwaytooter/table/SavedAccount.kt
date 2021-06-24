package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.notification.PollingWorker
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.util.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max

class SavedAccount(
    val db_id: Long,
    acctArg: String,
    apiHostArg: String? = null,
    apDomainArg: String? = null,
    var token_info: JsonObject? = null,
    var loginAccount: TootAccount? = null, // 疑似アカウントではnull
    override val misskeyVersion: Int = 0,
) : LinkHelper {

    // SavedAccountのロード時にhostを供給する必要があった
    override val apiHost: Host
    //	fun findAcct(url : String?) : String? = null
    //	fun colorFromAcct(acct : String?) : AcctColor? = null

    override val apDomain: Host

    val username: String

    val acct: Acct

    var visibility: TootVisibility = TootVisibility.Public
    var confirm_boost: Boolean = false
    var confirm_favourite: Boolean = false
    var confirm_unboost: Boolean = false
    var confirm_unfavourite: Boolean = false

    var dont_hide_nsfw: Boolean = false
    var dont_show_timeout: Boolean = false

    var notification_mention: Boolean = false
    var notification_boost: Boolean = false
    var notification_favourite: Boolean = false
    var notification_follow: Boolean = false
    var notification_follow_request: Boolean = false
    var notification_reaction: Boolean = false
    var notification_vote: Boolean = false
    var notification_post: Boolean = false
    var sound_uri = ""

    var confirm_follow: Boolean = false
    var confirm_follow_locked: Boolean = false
    var confirm_unfollow: Boolean = false
    var confirm_post: Boolean = false
    var confirm_reaction: Boolean = true

    var notification_tag: String? = null
    private var register_key: String? = null
    private var register_time: Long = 0
    var default_text: String = ""

    var default_sensitive = false
    var expand_cw = false

    var max_toot_chars = 0

    var lastNotificationError: String? = null
    var last_subscription_error: String? = null
    var last_push_endpoint: String? = null

    var image_resize: String? = null
    var image_max_megabytes: String? = null
    var movie_max_megabytes: String? = null

    var push_policy: String? = null

    init {
        val tmpAcct = Acct.parse(acctArg)
        this.username = tmpAcct.username
        if (username.isEmpty()) error("missing username in acct")

        val tmpApiHost = apiHostArg?.notEmpty()?.let { Host.parse(it) }
        val tmpApDomain = apDomainArg?.notEmpty()?.let { Host.parse(it) }

        this.apiHost = tmpApiHost ?: tmpApDomain ?: tmpAcct.host ?: error("missing apiHost")
        this.apDomain = tmpApDomain ?: tmpApiHost ?: tmpAcct.host ?: error("missing apDomain")

        this.acct = tmpAcct.followHost(apDomain)
    }

    constructor(context: Context, cursor: Cursor) : this(
        db_id = cursor.getLong(COL_ID), // db_id
        acctArg = cursor.getString(COL_USER), // acct
        apiHostArg = cursor.getStringOrNull(COL_HOST), // host
        apDomainArg = cursor.getStringOrNull(COL_DOMAIN), // host
        misskeyVersion = cursor.getInt(COL_MISSKEY_VERSION)
    ) {
        val strAccount = cursor.getString(COL_ACCOUNT)
        val jsonAccount = strAccount.decodeJsonObject()

        loginAccount = if (jsonAccount["id"] == null) {
            null // 疑似アカウント
        } else {
            TootParser(
                context,
                LinkHelper.create(
                    apiHostArg = this@SavedAccount.apiHost,
                    apDomainArg = this@SavedAccount.apDomain,
                    misskeyVersion = misskeyVersion
                )
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
        confirm_reaction = cursor.getBoolean(COL_CONFIRM_REACTION)

        notification_mention = cursor.getBoolean(COL_NOTIFICATION_MENTION)
        notification_boost = cursor.getBoolean(COL_NOTIFICATION_BOOST)
        notification_favourite = cursor.getBoolean(COL_NOTIFICATION_FAVOURITE)
        notification_follow = cursor.getBoolean(COL_NOTIFICATION_FOLLOW)
        notification_follow_request = cursor.getBoolean(COL_NOTIFICATION_FOLLOW_REQUEST)
        notification_reaction = cursor.getBoolean(COL_NOTIFICATION_REACTION)
        notification_vote = cursor.getBoolean(COL_NOTIFICATION_VOTE)
        notification_post = cursor.getBoolean(COL_NOTIFICATION_POST)

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

        lastNotificationError = cursor.getStringOrNull(COL_LAST_NOTIFICATION_ERROR)
        last_subscription_error = cursor.getStringOrNull(COL_LAST_SUBSCRIPTION_ERROR)
        last_push_endpoint = cursor.getStringOrNull(COL_LAST_PUSH_ENDPOINT)

        image_resize = cursor.getStringOrNull(COL_IMAGE_RESIZE)
        image_max_megabytes = cursor.getStringOrNull(COL_IMAGE_MAX_MEGABYTES)
        movie_max_megabytes = cursor.getStringOrNull(COL_MOVIE_MAX_MEGABYTES)
        push_policy = cursor.getStringOrNull(COL_PUSH_POLICY)
    }

    val isNA: Boolean
        get() = acct == Acct.UNKNOWN

    val isPseudo: Boolean
        get() = username == "?"

    fun delete() {
        try {
            App1.database.delete(table, "$COL_ID=?", arrayOf(db_id.toString()))
        } catch (ex: Throwable) {
            log.trace(ex)
            errorEx(ex, "SavedAccount.delete failed.")
        }
    }

    fun updateTokenInfo(tokenInfoArg: JsonObject?) {

        if (db_id == INVALID_DB_ID) error("updateTokenInfo: missing db_id")

        val token_info = tokenInfoArg ?: JsonObject()
        this.token_info = token_info

        val cv = ContentValues()
        COL_TOKEN.putTo(cv, token_info.toString())
        App1.database.update(table, cv, "$COL_ID=?", arrayOf(db_id.toString()))
    }

    fun saveSetting() {

        if (db_id == INVALID_DB_ID) error("saveSetting: missing db_id")

        val cv = ContentValues()
        COL_VISIBILITY.putTo(cv, visibility.id.toString())
        COL_CONFIRM_BOOST.putTo(cv, confirm_boost.b2i())
        COL_CONFIRM_FAVOURITE.putTo(cv, confirm_favourite.b2i())
        COL_CONFIRM_UNBOOST.putTo(cv, confirm_unboost.b2i())
        COL_CONFIRM_UNFAVOURITE.putTo(cv, confirm_unfavourite.b2i())

        COL_DONT_HIDE_NSFW.putTo(cv, dont_hide_nsfw.b2i())
        COL_DONT_SHOW_TIMEOUT.putTo(cv, dont_show_timeout.b2i())
        COL_NOTIFICATION_MENTION.putTo(cv, notification_mention.b2i())
        COL_NOTIFICATION_BOOST.putTo(cv, notification_boost.b2i())
        COL_NOTIFICATION_FAVOURITE.putTo(cv, notification_favourite.b2i())
        COL_NOTIFICATION_FOLLOW.putTo(cv, notification_follow.b2i())
        COL_NOTIFICATION_FOLLOW_REQUEST.putTo(cv, notification_follow_request.b2i())
        COL_NOTIFICATION_REACTION.putTo(cv, notification_reaction.b2i())
        COL_NOTIFICATION_VOTE.putTo(cv, notification_vote.b2i())
        COL_NOTIFICATION_POST.putTo(cv, notification_post.b2i())

        COL_CONFIRM_FOLLOW.putTo(cv, confirm_follow.b2i())
        COL_CONFIRM_FOLLOW_LOCKED.putTo(cv, confirm_follow_locked.b2i())
        COL_CONFIRM_UNFOLLOW.putTo(cv, confirm_unfollow.b2i())
        COL_CONFIRM_POST.putTo(cv, confirm_post.b2i())
        COL_CONFIRM_REACTION.putTo(cv, confirm_reaction.b2i())

        COL_SOUND_URI.putTo(cv, sound_uri)
        COL_DEFAULT_TEXT.putTo(cv, default_text)

        COL_DEFAULT_SENSITIVE.putTo(cv, default_sensitive.b2i())
        COL_EXPAND_CW.putTo(cv, expand_cw.b2i())
        COL_MAX_TOOT_CHARS.putTo(cv, max_toot_chars)

        COL_IMAGE_RESIZE.putTo(cv, image_resize)
        COL_IMAGE_MAX_MEGABYTES.putTo(cv, image_max_megabytes)
        COL_MOVIE_MAX_MEGABYTES.putTo(cv, movie_max_megabytes)
        COL_PUSH_POLICY.putTo(cv, push_policy)

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
    fun reloadSetting(context: Context, newData: SavedAccount? = null) {

        if (db_id == INVALID_DB_ID) error("SavedAccount.reloadSetting missing db_id")

        // DBから削除されてるかもしれない
        val b = newData ?: loadAccount(context, db_id) ?: return

        this.visibility = b.visibility
        this.confirm_boost = b.confirm_boost
        this.confirm_favourite = b.confirm_favourite
        this.confirm_unboost = b.confirm_unboost
        this.confirm_unfavourite = b.confirm_unfavourite
        this.confirm_post = b.confirm_post
        this.confirm_reaction = b.confirm_reaction

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
        this.notification_post = b.notification_post

        this.notification_tag = b.notification_tag
        this.default_text = b.default_text
        this.default_sensitive = b.default_sensitive
        this.expand_cw = b.expand_cw

        this.sound_uri = b.sound_uri

        this.image_resize = b.image_resize
        this.image_max_megabytes = b.image_max_megabytes
        this.movie_max_megabytes = b.movie_max_megabytes
        this.push_policy = b.push_policy
    }

    fun getFullAcct(who: TootAccount?) = getFullAcct(who?.acct)

    fun isRemoteUser(who: TootAccount): Boolean = !isLocalUser(who.acct)
    fun isLocalUser(who: TootAccount?): Boolean = isLocalUser(who?.acct)
    private fun isLocalUser(acct: Acct?): Boolean {
        acct ?: return false
        return acct.host == null || acct.host == this.apDomain
    }

    //	fun isRemoteUser(acct : String) : Boolean {
    //		return ! isLocalUser(acct)
    //	}

    fun isMe(who: TootAccount?): Boolean = isMe(who?.acct)
    //	fun isMe(who_acct : String) : Boolean  = isMe(Acct.parse(who_acct))

    fun isMe(acct: Acct?): Boolean {
        acct ?: return false
        if (acct.username != this.acct.username) return false
        return acct.host == null || acct.host == this.acct.host
    }

    fun supplyBaseUrl(url: String?): String? {
        return when {
            url == null || url.isEmpty() -> return null
            url[0] == '/' -> "https://${apiHost.ascii}$url"
            else -> url
        }
    }

    fun isNicoru(account: TootAccount?): Boolean = account?.apiHost == Host.FRIENDS_NICO

    companion object : TableCompanion {

        private val log = LogCategory("SavedAccount")

        const val table = "access_info"

        private val columnList = ColumnMeta.List(table)

        private val COL_ID = ColumnMeta(columnList, 0, BaseColumns._ID, "INTEGER PRIMARY KEY", primary = true)
        private val COL_HOST = ColumnMeta(columnList, 0, "h", "text not null")
        private val COL_DOMAIN = ColumnMeta(columnList, 56, "d", "text")
        private val COL_USER = ColumnMeta(columnList, 0, "u", "text not null")
        private val COL_ACCOUNT = ColumnMeta(columnList, 0, "a", "text not null")
        private val COL_TOKEN = ColumnMeta(columnList, 0, "t", "text not null")

        private val COL_VISIBILITY = ColumnMeta(columnList, 0, "visibility", "text")
        private val COL_CONFIRM_BOOST = ColumnMeta(columnList, 0, "confirm_boost", ColumnMeta.TS_TRUE)
        private val COL_DONT_HIDE_NSFW = ColumnMeta(columnList, 0, "dont_hide_nsfw", ColumnMeta.TS_ZERO)

        private val COL_NOTIFICATION_MENTION = ColumnMeta(columnList, 2, "notification_mention", ColumnMeta.TS_TRUE)
        private val COL_NOTIFICATION_BOOST = ColumnMeta(columnList, 2, "notification_boost", ColumnMeta.TS_TRUE)
        private val COL_NOTIFICATION_FAVOURITE = ColumnMeta(columnList, 2, "notification_favourite", ColumnMeta.TS_TRUE)
        private val COL_NOTIFICATION_FOLLOW = ColumnMeta(columnList, 2, "notification_follow", ColumnMeta.TS_TRUE)
        private val COL_NOTIFICATION_FOLLOW_REQUEST =
            ColumnMeta(columnList, 44, "notification_follow_request", ColumnMeta.TS_TRUE)
        private val COL_NOTIFICATION_REACTION = ColumnMeta(columnList, 33, "notification_reaction", ColumnMeta.TS_TRUE)
        private val COL_NOTIFICATION_VOTE = ColumnMeta(columnList, 33, "notification_vote", ColumnMeta.TS_TRUE)
        private val COL_NOTIFICATION_POST = ColumnMeta(columnList, 57, "notification_post", ColumnMeta.TS_TRUE)

        private val COL_CONFIRM_FOLLOW = ColumnMeta(columnList, 10, "confirm_follow", ColumnMeta.TS_TRUE)
        private val COL_CONFIRM_FOLLOW_LOCKED = ColumnMeta(columnList, 10, "confirm_follow_locked", ColumnMeta.TS_TRUE)
        private val COL_CONFIRM_UNFOLLOW = ColumnMeta(columnList, 10, "confirm_unfollow", ColumnMeta.TS_TRUE)
        private val COL_CONFIRM_POST = ColumnMeta(columnList, 10, "confirm_post", ColumnMeta.TS_TRUE)
        private val COL_CONFIRM_FAVOURITE = ColumnMeta(columnList, 23, "confirm_favourite", ColumnMeta.TS_TRUE)
        private val COL_CONFIRM_UNBOOST = ColumnMeta(columnList, 24, "confirm_unboost", ColumnMeta.TS_TRUE)
        private val COL_CONFIRM_UNFAVOURITE = ColumnMeta(columnList, 24, "confirm_unfavourite", ColumnMeta.TS_TRUE)
        private val COL_CONFIRM_REACTION = ColumnMeta(columnList, 61, "confirm_reaction", ColumnMeta.TS_TRUE)

        // スキーマ13から
        val COL_NOTIFICATION_TAG = ColumnMeta(columnList, 13, "notification_server", ColumnMeta.TS_EMPTY)

        // スキーマ14から
        val COL_REGISTER_KEY = ColumnMeta(columnList, 14, "register_key", ColumnMeta.TS_EMPTY)
        val COL_REGISTER_TIME = ColumnMeta(columnList, 14, "register_time", ColumnMeta.TS_ZERO)

        // スキーマ16から
        private val COL_SOUND_URI = ColumnMeta(columnList, 16, "sound_uri", ColumnMeta.TS_EMPTY)

        // スキーマ18から
        private val COL_DONT_SHOW_TIMEOUT = ColumnMeta(columnList, 18, "dont_show_timeout", ColumnMeta.TS_ZERO)

        // スキーマ27から
        private val COL_DEFAULT_TEXT = ColumnMeta(columnList, 27, "default_text", ColumnMeta.TS_EMPTY)

        // スキーマ28から
        private val COL_MISSKEY_VERSION = ColumnMeta(columnList, 28, "is_misskey", ColumnMeta.TS_ZERO)
        // カラム名がおかしいのは、昔はboolean扱いだったから
        // 0: not misskey
        // 1: old(v10) misskey
        // 11: misskey v11

        private val COL_DEFAULT_SENSITIVE = ColumnMeta(columnList, 38, "default_sensitive", ColumnMeta.TS_ZERO)
        private val COL_EXPAND_CW = ColumnMeta(columnList, 38, "expand_cw", ColumnMeta.TS_ZERO)
        private val COL_MAX_TOOT_CHARS = ColumnMeta(columnList, 39, "max_toot_chars", ColumnMeta.TS_ZERO)

        private val COL_LAST_NOTIFICATION_ERROR = ColumnMeta(columnList, 42, "last_notification_error", "text")
        private val COL_LAST_SUBSCRIPTION_ERROR = ColumnMeta(columnList, 45, "last_subscription_error", "text")
        private val COL_LAST_PUSH_ENDPOINT = ColumnMeta(columnList, 46, "last_push_endpoint", "text")

        private val COL_IMAGE_RESIZE = ColumnMeta(columnList, 59, "image_resize", "text default null")
        private val COL_IMAGE_MAX_MEGABYTES = ColumnMeta(columnList, 59, "image_max_megabytes", "text default null")
        private val COL_MOVIE_MAX_MEGABYTES = ColumnMeta(columnList, 59, "movie_max_megabytes", "text default null")

        private val COL_PUSH_POLICY = ColumnMeta(columnList, 60, "push_policy", "text default null")

        /////////////////////////////////
        // login information
        const val INVALID_DB_ID = -1L

        // アプリデータのインポート時に呼ばれる
        fun onDBDelete(db: SQLiteDatabase) {
            try {
                db.execSQL("drop table if exists $table")
            } catch (ex: Throwable) {
                log.trace(ex)
            }
        }

        override fun onDBCreate(db: SQLiteDatabase) {
            db.execSQL("create table if not exists $table (${columnList.createParams()})")
            db.execSQL("create index if not exists ${table}_user on $table(u)")
            db.execSQL("create index if not exists ${table}_host on $table(h,u)")
        }

        override fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            columnList.addColumns(db, oldVersion, newVersion)
        }

        val defaultResizeConfig = ResizeConfig(ResizeType.LongSide, 1280)

        internal val resizeConfigList = arrayOf(
            ResizeConfig(ResizeType.None, 0),

            ResizeConfig(ResizeType.LongSide, 640),
            ResizeConfig(ResizeType.LongSide, 800),
            ResizeConfig(ResizeType.LongSide, 1024),
            ResizeConfig(ResizeType.LongSide, 1280),
            ResizeConfig(ResizeType.LongSide, 1600),
            ResizeConfig(ResizeType.LongSide, 2048),

            ResizeConfig(ResizeType.SquarePixel, 640),
            ResizeConfig(ResizeType.SquarePixel, 800),
            ResizeConfig(ResizeType.SquarePixel, 1024),
            ResizeConfig(ResizeType.SquarePixel, 1280),
            ResizeConfig(ResizeType.SquarePixel, 1440, R.string.size_1920_1080),
            ResizeConfig(ResizeType.SquarePixel, 1600),
            ResizeConfig(ResizeType.SquarePixel, 2048)
        )

        // 横断検索用の、何とも紐ついていないアカウント
        // 保存しない。
        val na: SavedAccount by lazy {
            val dst = SavedAccount(-1L, "?@?")
            dst.notification_follow = false
            dst.notification_follow_request = false
            dst.notification_favourite = false
            dst.notification_boost = false
            dst.notification_mention = false
            dst.notification_reaction = false
            dst.notification_vote = false
            dst.notification_post = false

            dst
        }

        private fun parse(context: Context, cursor: Cursor): SavedAccount? {
            return try {
                SavedAccount(context, cursor)
            } catch (ex: Throwable) {
                log.trace(ex)
                log.e(ex, "parse failed.")
                null
            }
        }

        fun insert(
            acct: String,
            host: String,
            domain: String?,
            account: JsonObject,
            token: JsonObject,
            misskeyVersion: Int = 0,
        ): Long {
            try {
                val cv = ContentValues()
                COL_USER.putTo(cv, acct)
                COL_HOST.putTo(cv, host)
                COL_DOMAIN.putTo(cv, domain)
                COL_ACCOUNT.putTo(cv, account.toString())
                COL_TOKEN.putTo(cv, token.toString())
                COL_MISSKEY_VERSION.putTo(cv, misskeyVersion)
                return App1.database.insert(table, null, cv)
            } catch (ex: Throwable) {
                log.trace(ex)
                errorEx(ex, "SavedAccount.insert failed.")
            }
        }

        private const val REGISTER_KEY_UNREGISTERED = "unregistered"

        fun clearRegistrationCache() {
            val cv = ContentValues()
            COL_REGISTER_KEY.putTo(cv, REGISTER_KEY_UNREGISTERED)
            COL_REGISTER_TIME.putTo(cv, 0L)
            App1.database.update(table, cv, null, null)
        }

        fun loadAccount(context: Context, dbId: Long): SavedAccount? {
            try {
                App1.database.query(
                    table,
                    null,
                    "$COL_ID=?",
                    arrayOf(dbId.toString()),
                    null,
                    null,
                    null
                )
                    .use { cursor ->
                        if (cursor.moveToFirst()) {
                            return parse(context, cursor)
                        }
                        log.e("moveToFirst failed. db_id=$dbId")
                    }
            } catch (ex: Throwable) {
                log.trace(ex)
                log.e(ex, "loadAccount failed.")
            }

            return null
        }

        fun loadAccountList(context: Context) =
            ArrayList<SavedAccount>().also { result ->
                try {
                    App1.database.query(
                        table,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            parse(context, cursor)?.let { result.add(it) }
                        }
                    }
                } catch (ex: Throwable) {
                    log.trace(ex)
                    log.e(ex, "loadAccountList failed.")
                    context.showToast(true, ex.withCaption("(SubwayTooter) broken in-app database?"))
                }
            }

        fun loadByTag(context: Context, tag: String): ArrayList<SavedAccount> {
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
                        while (cursor.moveToNext()) {
                            val a = parse(context, cursor)
                            if (a != null) result.add(a)
                        }
                    }
            } catch (ex: Throwable) {
                log.trace(ex)
                log.e(ex, "loadByTag failed.")
                errorEx(ex, "SavedAccount.loadByTag failed.")
            }

            return result
        }

        fun loadAccountByAcct(context: Context, fullAcct: String): SavedAccount? {
            try {
                App1.database.query(
                    table,
                    null,
                    "$COL_USER=?",
                    arrayOf(fullAcct),
                    null,
                    null,
                    null
                )
                    .use { cursor ->
                        if (cursor.moveToNext()) {
                            return parse(context, cursor)
                        }
                    }
            } catch (ex: Throwable) {
                log.trace(ex)
                log.e(ex, "loadAccountByAcct failed.")
            }

            return null
        }

        fun hasRealAccount(): Boolean {
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
                        if (cursor.moveToNext()) {
                            return true
                        }
                    }
            } catch (ex: Throwable) {
                log.trace(ex)
                log.e(ex, "hasNonPseudoAccount failed.")
            }

            return false
        }

        val count: Long
            get() {
                try {
                    App1.database.query(table, arrayOf("count(*)"), null, null, null, null, null)
                        .use { cursor ->
                            if (cursor.moveToNext()) {
                                return cursor.getLong(0)
                            }
                        }
                } catch (ex: Throwable) {
                    log.trace(ex)
                    log.e(ex, "getCount failed.")
                    errorEx(ex, "SavedAccount.getCount failed.")
                }

                return 0L
            }

        fun isNicoru(acct: Acct): Boolean {
            return acct.host == Host.FRIENDS_NICO
        }

        //		private fun charAtLower(src : CharSequence, pos : Int) : Char {
        //			val c = src[pos]
        //			return if(c >= 'a' && c <= 'z') c - ('a' - 'A') else c
        //		}
        //
        //		@Suppress("SameParameterValue")
        //		private fun host_match(
        //			a : CharSequence,
        //			a_startArg : Int,
        //			b : CharSequence,
        //			b_startArg : Int
        //		) : Boolean {
        //			var a_start = a_startArg
        //			var b_start = b_startArg
        //
        //			val a_end = a.length
        //			val b_end = b.length
        //
        //			var a_remain = a_end - a_start
        //			val b_remain = b_end - b_start
        //
        //			// 文字数が違う
        //			if(a_remain != b_remain) return false
        //
        //			// 文字数がゼロ
        //			if(a_remain <= 0) return true
        //
        //			// 末尾の文字が違う
        //			if(charAtLower(a, a_end - 1) != charAtLower(b, b_end - 1)) return false
        //
        //			// 先頭からチェック
        //			while(a_remain -- > 0) {
        //				if(charAtLower(a, a_start ++) != charAtLower(b, b_start ++)) return false
        //			}
        //
        //			return true
        //		}

        private val account_comparator = Comparator<SavedAccount> { a, b ->
            var i: Int

            // NA > !NA
            i = a.isNA.b2i() - b.isNA.b2i()
            if (i != 0) return@Comparator i

            // pseudo > real
            i = a.isPseudo.b2i() - b.isPseudo.b2i()
            if (i != 0) return@Comparator i

            val sa = AcctColor.getNickname(a)
            val sb = AcctColor.getNickname(b)
            sa.compareTo(sb, ignoreCase = true)
        }

        fun sort(accountList: MutableList<SavedAccount>) {
            Collections.sort(accountList, account_comparator)
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
                    while (cursor.moveToNext()) {
                        list.add(COL_ID.getLong(cursor))
                    }
                }
            } catch (ex: Throwable) {
                log.trace(ex)
                log.e(ex, "sweepBuggieData failed.")
            }

            list.forEach {
                try {
                    App1.database.delete(table, "$COL_ID=?", arrayOf(it.toString()))
                } catch (ex: Throwable) {
                    log.trace(ex)
                    log.e(ex, "sweepBuggieData failed.")
                }
            }
        }
    }

    fun getAccessToken(): String? {
        return token_info?.string("access_token")
    }

    val misskeyApiToken: String?
        get() = token_info?.string(TootApiClient.KEY_API_KEY_MISSKEY)

    fun putMisskeyApiToken(params: JsonObject = JsonObject()): JsonObject {
        val apiKey = misskeyApiToken
        if (apiKey?.isNotEmpty() == true) params["i"] = apiKey
        return params
    }

    fun canNotificationShowing(type: String?) = when (type) {

        TootNotification.TYPE_MENTION,
        TootNotification.TYPE_REPLY,
        -> notification_mention

        TootNotification.TYPE_REBLOG,
        TootNotification.TYPE_RENOTE,
        TootNotification.TYPE_QUOTE,
        -> notification_boost

        TootNotification.TYPE_FAVOURITE -> notification_favourite

        TootNotification.TYPE_FOLLOW,
        TootNotification.TYPE_UNFOLLOW,
        -> notification_follow

        TootNotification.TYPE_FOLLOW_REQUEST,
        TootNotification.TYPE_FOLLOW_REQUEST_MISSKEY,
        TootNotification.TYPE_FOLLOW_REQUEST_ACCEPTED_MISSKEY,
        -> notification_follow_request

        TootNotification.TYPE_EMOJI_REACTION_PLEROMA,
        TootNotification.TYPE_EMOJI_REACTION,
        TootNotification.TYPE_REACTION,
        -> notification_reaction

        TootNotification.TYPE_VOTE,
        TootNotification.TYPE_POLL,
        TootNotification.TYPE_POLL_VOTE_MISSKEY,
        -> notification_vote

        TootNotification.TYPE_STATUS -> notification_post

        else -> false
    }

    val isConfirmed: Boolean
        get() {
            val myId = this.loginAccount?.id
            return myId != EntityId.CONFIRMING
        }

    suspend fun checkConfirmed(context: Context, client: TootApiClient): TootApiResult? {
        try {
            val myId = this.loginAccount?.id
            if (db_id != INVALID_DB_ID && myId == EntityId.CONFIRMING) {
                val accessToken = getAccessToken()
                if (accessToken != null) {
                    val result = client.getUserCredential(accessToken)
                    if (result == null || result.error != null) return result
                    val ta = TootParser(context, this).account(result.jsonObject)
                    if (ta != null) {
                        this.loginAccount = ta
                        val cv = ContentValues()
                        COL_ACCOUNT.putTo(cv, result.jsonObject.toString())
                        App1.database.update(table, cv, "$COL_ID=?", arrayOf(db_id.toString()))
                        PollingWorker.queueUpdateNotification(context)
                    }
                }
            }
            return TootApiResult()
        } catch (ex: Throwable) {
            log.trace(ex)
            return TootApiResult(ex.withCaption("account confirmation failed."))
        }
    }

    private fun updateSingleString(col: ColumnMeta, value: String?) {
        if (db_id != INVALID_DB_ID) {
            val cv = ContentValues()
            col.putTo(cv, value)
            App1.database.update(table, cv, "$COL_ID=?", arrayOf(db_id.toString()))
        }
    }

    fun updateNotificationError(text: String?) {
        this.lastNotificationError = text
        updateSingleString(COL_LAST_NOTIFICATION_ERROR, text)
    }

    fun updateSubscriptionError(text: String?) {
        this.last_subscription_error = text
        updateSingleString(COL_LAST_SUBSCRIPTION_ERROR, text)
    }

    fun updateLastPushEndpoint(text: String?) {
        this.last_push_endpoint = text
        updateSingleString(COL_LAST_PUSH_ENDPOINT, text)
    }

    override fun equals(other: Any?): Boolean =
        when (other) {
            is SavedAccount -> acct == other.acct
            else -> false
        }

    override fun hashCode(): Int = acct.hashCode()

    fun getResizeConfig() =
        resizeConfigList.find { it.spec == this.image_resize } ?: defaultResizeConfig

    fun getMovieMaxBytes(ti: TootInstance) = 1000000 * max(
        1,
        this.movie_max_megabytes?.toIntOrNull()
            ?: if (ti.instanceType == InstanceType.Pixelfed) 15 else 40
    )

    fun getImageMaxBytes(ti: TootInstance) = 1000000 * max(
        1,
        this.image_max_megabytes?.toIntOrNull()
            ?: if (ti.instanceType == InstanceType.Pixelfed) 15 else 8
    )
}
