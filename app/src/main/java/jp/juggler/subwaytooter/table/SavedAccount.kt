package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.auth.Auth2Result
import jp.juggler.subwaytooter.api.auth.AuthBase
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.notification.checkNotificationImmediate
import jp.juggler.subwaytooter.notification.checkNotificationImmediateAll
import jp.juggler.subwaytooter.pref.lazyContext
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.util.data.*
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.errorEx
import jp.juggler.util.log.showToast
import jp.juggler.util.log.withCaption
import jp.juggler.util.media.MovideResizeMode
import jp.juggler.util.media.MovieResizeConfig
import jp.juggler.util.media.ResizeConfig
import jp.juggler.util.media.ResizeType
import kotlin.math.max

class SavedAccount(
    val db_id: Long,
    acctArg: String,
    apiHostArg: String? = null,
    apDomainArg: String? = null,
    var token_info: JsonObject? = null,
    var loginAccount: TootAccount? = null, // 疑似アカウントではnull
    override var misskeyVersion: Int = 0,
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
    var notification_update: Boolean = true
    var sound_uri = ""

    var confirm_follow: Boolean = false
    var confirm_follow_locked: Boolean = false
    var confirm_unfollow: Boolean = false
    var confirm_post: Boolean = false
    var confirm_reaction: Boolean = true
    var confirm_unbookmark: Boolean = true

    var notification_tag: String? = null
    private var register_key: String? = null
    private var register_time: Long = 0
    var default_text: String = ""

    var default_sensitive = false
    var expand_cw = false

    var max_toot_chars = 0

    private var lastNotificationError: String? = null
    private var last_subscription_error: String? = null
    private var last_push_endpoint: String? = null

    var image_resize: String? = null
    var image_max_megabytes: String? = null
    var movie_max_megabytes: String? = null

    var push_policy: String? = null

    private var extraJson = JsonObject()

    private val jsonDelegates = JsonDelegates { extraJson }

    @JsonPropInt("movieTranscodeMode", 0)
    var movieTranscodeMode by jsonDelegates.int

    @JsonPropString("movieTranscodeBitrate", "2000000")
    var movieTranscodeBitrate by jsonDelegates.string

    @JsonPropString("movieTranscodeFramerate", "30")
    var movieTranscodeFramerate by jsonDelegates.string

    @JsonPropString("movieTranscodeSquarePixels", "2304000")
    var movieTranscodeSquarePixels by jsonDelegates.string

    @JsonPropString("lang2", LANG_WEB)
    var lang by jsonDelegates.string

    @JsonPropBoolean("notification_status_reference", true)
    var notification_status_reference by jsonDelegates.boolean

    init {
        log.i("ctor acctArg $acctArg")

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
        confirm_unbookmark = cursor.getBoolean(COL_CONFIRM_UNBOOKMARK)

        notification_mention = cursor.getBoolean(COL_NOTIFICATION_MENTION)
        notification_boost = cursor.getBoolean(COL_NOTIFICATION_BOOST)
        notification_favourite = cursor.getBoolean(COL_NOTIFICATION_FAVOURITE)
        notification_follow = cursor.getBoolean(COL_NOTIFICATION_FOLLOW)
        notification_follow_request = cursor.getBoolean(COL_NOTIFICATION_FOLLOW_REQUEST)
        notification_reaction = cursor.getBoolean(COL_NOTIFICATION_REACTION)
        notification_vote = cursor.getBoolean(COL_NOTIFICATION_VOTE)
        notification_post = cursor.getBoolean(COL_NOTIFICATION_POST)
        notification_update = cursor.getBoolean(COL_NOTIFICATION_UPDATE)

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
        cursor.getStringOrNull(COL_EXTRA_JSON)?.decodeJsonObject()
            ?.let { extraJson = it }
    }

    val isNA: Boolean
        get() = acct == Acct.UNKNOWN

    val isPseudo: Boolean
        get() = username == "?"

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

        override val table = "access_info"
        private const val COL_ID = BaseColumns._ID
        private const val COL_HOST = "h"
        private const val COL_DOMAIN = "d"
        private const val COL_USER = "u"
        private const val COL_ACCOUNT = "a"
        private const val COL_TOKEN = "t"
        private const val COL_VISIBILITY = "visibility"
        private const val COL_CONFIRM_BOOST = "confirm_boost"
        private const val COL_DONT_HIDE_NSFW = "dont_hide_nsfw"
        private const val COL_NOTIFICATION_MENTION = "notification_mention"
        private const val COL_NOTIFICATION_BOOST = "notification_boost"
        private const val COL_NOTIFICATION_FAVOURITE = "notification_favourite"
        private const val COL_NOTIFICATION_FOLLOW = "notification_follow"
        private const val COL_NOTIFICATION_FOLLOW_REQUEST = "notification_follow_request"
        private const val COL_NOTIFICATION_REACTION = "notification_reaction"
        private const val COL_NOTIFICATION_VOTE = "notification_vote"
        private const val COL_NOTIFICATION_POST = "notification_post"
        private const val COL_NOTIFICATION_UPDATE = "notification_update"
        private const val COL_CONFIRM_FOLLOW = "confirm_follow"
        private const val COL_CONFIRM_FOLLOW_LOCKED = "confirm_follow_locked"
        private const val COL_CONFIRM_UNFOLLOW = "confirm_unfollow"
        private const val COL_CONFIRM_POST = "confirm_post"
        private const val COL_CONFIRM_FAVOURITE = "confirm_favourite"
        private const val COL_CONFIRM_UNBOOST = "confirm_unboost"
        private const val COL_CONFIRM_UNFAVOURITE = "confirm_unfavourite"
        private const val COL_CONFIRM_REACTION = "confirm_reaction"
        private const val COL_CONFIRM_UNBOOKMARK = "confirm_unbookmark"

        // スキーマ13から
        const val COL_NOTIFICATION_TAG = "notification_server"
        const val COL_REGISTER_KEY = "register_key"
        const val COL_REGISTER_TIME = "register_time"
        private const val COL_SOUND_URI = "sound_uri"
        private const val COL_DONT_SHOW_TIMEOUT = "dont_show_timeout"
        private const val COL_DEFAULT_TEXT = "default_text"
        private const val COL_MISSKEY_VERSION = "is_misskey"
        private const val COL_DEFAULT_SENSITIVE = "default_sensitive"
        private const val COL_EXPAND_CW = "expand_cw"
        private const val COL_MAX_TOOT_CHARS = "max_toot_chars"
        private const val COL_LAST_NOTIFICATION_ERROR = "last_notification_error"
        private const val COL_LAST_SUBSCRIPTION_ERROR = "last_subscription_error"
        private const val COL_LAST_PUSH_ENDPOINT = "last_push_endpoint"
        private const val COL_IMAGE_RESIZE = "image_resize"
        private const val COL_IMAGE_MAX_MEGABYTES = "image_max_megabytes"
        private const val COL_MOVIE_MAX_MEGABYTES = "movie_max_megabytes"
        private const val COL_PUSH_POLICY = "push_policy"
        private const val COL_EXTRA_JSON = "extra_json"

        // COL_MISSKEY_VERSIONのカラム名がおかしいのは、昔はboolean扱いだったから
        // 0: not misskey
        // 1: old(v10) misskey
        // 11: misskey v11

        val columnList = ColumnMeta.List(table, 0).apply {
            ColumnMeta(this, 0, COL_ID, "INTEGER PRIMARY KEY")
            ColumnMeta(this, 0, COL_HOST, "text not null")
            ColumnMeta(this, 56, COL_DOMAIN, "text")
            ColumnMeta(this, 0, COL_USER, "text not null")
            ColumnMeta(this, 0, COL_ACCOUNT, "text not null")
            ColumnMeta(this, 0, COL_TOKEN, "text not null")
            ColumnMeta(this, 0, COL_VISIBILITY, "text")
            ColumnMeta(this, 0, COL_CONFIRM_BOOST, ColumnMeta.TS_TRUE)
            ColumnMeta(this, 0, COL_DONT_HIDE_NSFW, ColumnMeta.TS_ZERO)
            ColumnMeta(this, 2, COL_NOTIFICATION_MENTION, ColumnMeta.TS_TRUE)
            ColumnMeta(this, 2, COL_NOTIFICATION_BOOST, ColumnMeta.TS_TRUE)
            ColumnMeta(this, 2, COL_NOTIFICATION_FAVOURITE, ColumnMeta.TS_TRUE)
            ColumnMeta(this, 2, COL_NOTIFICATION_FOLLOW, ColumnMeta.TS_TRUE)
            ColumnMeta(this, 44, COL_NOTIFICATION_FOLLOW_REQUEST, ColumnMeta.TS_TRUE)
            ColumnMeta(this, 33, COL_NOTIFICATION_REACTION, ColumnMeta.TS_TRUE)
            ColumnMeta(this, 33, COL_NOTIFICATION_VOTE, ColumnMeta.TS_TRUE)
            ColumnMeta(this, 57, COL_NOTIFICATION_POST, ColumnMeta.TS_TRUE)
            ColumnMeta(this, 64, COL_NOTIFICATION_UPDATE, ColumnMeta.TS_TRUE)
            ColumnMeta(this, 10, COL_CONFIRM_FOLLOW, ColumnMeta.TS_TRUE)
            ColumnMeta(this, 10, COL_CONFIRM_FOLLOW_LOCKED, ColumnMeta.TS_TRUE)
            ColumnMeta(this, 10, COL_CONFIRM_UNFOLLOW, ColumnMeta.TS_TRUE)
            ColumnMeta(this, 10, COL_CONFIRM_POST, ColumnMeta.TS_TRUE)
            ColumnMeta(this, 23, COL_CONFIRM_FAVOURITE, ColumnMeta.TS_TRUE)
            ColumnMeta(this, 24, COL_CONFIRM_UNBOOST, ColumnMeta.TS_TRUE)
            ColumnMeta(this, 24, COL_CONFIRM_UNFAVOURITE, ColumnMeta.TS_TRUE)
            ColumnMeta(this, 61, COL_CONFIRM_REACTION, ColumnMeta.TS_TRUE)
            ColumnMeta(this, 62, COL_CONFIRM_UNBOOKMARK, ColumnMeta.TS_TRUE)
            ColumnMeta(this, 13, COL_NOTIFICATION_TAG, ColumnMeta.TS_EMPTY)
            ColumnMeta(this, 14, COL_REGISTER_KEY, ColumnMeta.TS_EMPTY)
            ColumnMeta(this, 14, COL_REGISTER_TIME, ColumnMeta.TS_ZERO)
            ColumnMeta(this, 16, COL_SOUND_URI, ColumnMeta.TS_EMPTY)
            ColumnMeta(this, 18, COL_DONT_SHOW_TIMEOUT, ColumnMeta.TS_ZERO)
            ColumnMeta(this, 27, COL_DEFAULT_TEXT, ColumnMeta.TS_EMPTY)
            ColumnMeta(this, 28, COL_MISSKEY_VERSION, ColumnMeta.TS_ZERO)
            ColumnMeta(this, 38, COL_DEFAULT_SENSITIVE, ColumnMeta.TS_ZERO)
            ColumnMeta(this, 38, COL_EXPAND_CW, ColumnMeta.TS_ZERO)
            ColumnMeta(this, 39, COL_MAX_TOOT_CHARS, ColumnMeta.TS_ZERO)
            ColumnMeta(this, 42, COL_LAST_NOTIFICATION_ERROR, "text")
            ColumnMeta(this, 45, COL_LAST_SUBSCRIPTION_ERROR, "text")
            ColumnMeta(this, 46, COL_LAST_PUSH_ENDPOINT, "text")
            ColumnMeta(this, 59, COL_IMAGE_RESIZE, "text default null")
            ColumnMeta(this, 59, COL_IMAGE_MAX_MEGABYTES, "text default null")
            ColumnMeta(this, 59, COL_MOVIE_MAX_MEGABYTES, "text default null")
            ColumnMeta(this, 60, COL_PUSH_POLICY, "text default null")
            ColumnMeta(this, 63, COL_EXTRA_JSON, "text default null")
            createExtra = {
                arrayOf(
                    "create index if not exists ${table}_user on $table(u)",
                    "create index if not exists ${table}_host on $table(h,u)"
                )
            }
        }

        // アプリデータのインポート時に呼ばれる
        fun onDBDelete(db: SQLiteDatabase) {
            try {
                db.execSQL("drop table if exists $table")
            } catch (ex: Throwable) {
                log.e(ex, "can't delete table $table.")
            }
        }

        override fun onDBCreate(db: SQLiteDatabase) =
            columnList.onDBCreate(db)

        override fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
            columnList.onDBUpgrade(db, oldVersion, newVersion)

        /////////////////////////////////

        const val INVALID_DB_ID = -1L

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
            SavedAccount(-1L, "?@?").apply {
                notification_follow = false
                notification_follow_request = false
                notification_favourite = false
                notification_boost = false
                notification_mention = false
                notification_reaction = false
                notification_vote = false
                notification_post = false
                notification_update = false
            }
        }

        private fun parse(context: Context, cursor: Cursor): SavedAccount? {
            return try {
                SavedAccount(context, cursor)
            } catch (ex: Throwable) {
                log.e(ex, "parse failed.")
                null
            }
        }

        const val LANG_WEB = "(web)"
        const val LANG_DEVICE = "(device)"

        private const val REGISTER_KEY_UNREGISTERED = "unregistered"
    }

    class Access(
        val db: SQLiteDatabase,
        val context: Context,
    ) {
        fun saveNew(
            acct: String,
            host: String,
            domain: String,
            account: JsonObject,
            token: JsonObject,
            misskeyVersion: Int = 0,
        ): Long {
            try {
                return ContentValues().apply {
                    put(COL_USER, acct)
                    put(COL_HOST, host)
                    put(COL_DOMAIN, domain)
                    put(COL_ACCOUNT, account.toString())
                    put(COL_TOKEN, token.toString())
                    put(COL_MISSKEY_VERSION, misskeyVersion)
                }.let { db.insert(table, null, it) }
            } catch (ex: Throwable) {
                log.e(ex, "SavedAccount.insert failed.")
                errorEx(ex, "SavedAccount.insert failed.")
            }
        }

        fun updateTokenInfo(item: SavedAccount, auth2Result: Auth2Result) {
            item.run {
                if (db_id == INVALID_DB_ID) error("updateTokenInfo: missing db_id")

                this.token_info = auth2Result.tokenJson
                this.loginAccount = auth2Result.tootAccount

                ContentValues().apply {
                    put(COL_TOKEN, auth2Result.tokenJson.toString())
                    put(COL_ACCOUNT, auth2Result.accountJson.toString())
                    put(COL_MISSKEY_VERSION, auth2Result.tootInstance.misskeyVersionMajor)
                }.let { db.update(table, it, "$COL_ID=?", arrayOf(db_id.toString())) }
            }
        }

        /**
         * ユーザ登録の確認手順が完了しているかどうか
         *
         * - マストドン以外だと何もしないはず
         */
        suspend fun checkConfirmed(item: SavedAccount, client: TootApiClient) {
            item.run {
                // 承認待ち状態ではないならチェックしない
                if (loginAccount?.id != EntityId.CONFIRMING) return

                // DBに保存されていないならチェックしない
                if (db_id == INVALID_DB_ID) return

                // アクセストークンがないならチェックしない
                val accessToken = bearerAccessToken ?: return

                // ユーザ情報を取得してみる。承認済みなら読めるはず
                // 読めなければ例外が出る
                val userJson = client.verifyAccount(
                    accessToken = accessToken,
                    outTokenInfo = null,
                    misskeyVersion = 0, // Mastodon only
                )
                // 読めたらアプリ内の記録を更新する
                TootParser(context, this).account(userJson)?.let { ta ->
                    this.loginAccount = ta
                    db.update(
                        table,
                        ContentValues().apply {
                            put(COL_ACCOUNT, userJson.toString())
                        },
                        "$COL_ID=?",
                        arrayOf(db_id.toString())
                    )
                    checkNotificationImmediateAll(context, onlySubscription = true)
                    checkNotificationImmediate(context, db_id)
                }
            }
        }

        fun saveSetting(item: SavedAccount) {
            item.run {

                if (db_id == INVALID_DB_ID) error("saveSetting: missing db_id")

                ContentValues().apply {
                    put(COL_VISIBILITY, visibility.id.toString())

                    put(COL_DONT_HIDE_NSFW, dont_hide_nsfw)
                    put(COL_DONT_SHOW_TIMEOUT, dont_show_timeout)
                    put(COL_NOTIFICATION_MENTION, notification_mention)
                    put(COL_NOTIFICATION_BOOST, notification_boost)
                    put(COL_NOTIFICATION_FAVOURITE, notification_favourite)
                    put(COL_NOTIFICATION_FOLLOW, notification_follow)
                    put(COL_NOTIFICATION_FOLLOW_REQUEST, notification_follow_request)
                    put(COL_NOTIFICATION_REACTION, notification_reaction)
                    put(COL_NOTIFICATION_VOTE, notification_vote)
                    put(COL_NOTIFICATION_POST, notification_post)
                    put(COL_NOTIFICATION_UPDATE, notification_update)

                    put(COL_CONFIRM_BOOST, confirm_boost)
                    put(COL_CONFIRM_FAVOURITE, confirm_favourite)
                    put(COL_CONFIRM_UNBOOST, confirm_unboost)
                    put(COL_CONFIRM_UNFAVOURITE, confirm_unfavourite)
                    put(COL_CONFIRM_FOLLOW, confirm_follow)
                    put(COL_CONFIRM_FOLLOW_LOCKED, confirm_follow_locked)
                    put(COL_CONFIRM_UNFOLLOW, confirm_unfollow)
                    put(COL_CONFIRM_POST, confirm_post)
                    put(COL_CONFIRM_REACTION, confirm_reaction)
                    put(COL_CONFIRM_UNBOOKMARK, confirm_unbookmark)

                    put(COL_SOUND_URI, sound_uri)
                    put(COL_DEFAULT_TEXT, default_text)

                    put(COL_DEFAULT_SENSITIVE, default_sensitive)
                    put(COL_EXPAND_CW, expand_cw)
                    put(COL_MAX_TOOT_CHARS, max_toot_chars)

                    put(COL_IMAGE_RESIZE, image_resize)
                    put(COL_IMAGE_MAX_MEGABYTES, image_max_megabytes)
                    put(COL_MOVIE_MAX_MEGABYTES, movie_max_megabytes)
                    put(COL_PUSH_POLICY, push_policy)
                    put(COL_EXTRA_JSON, extraJson.toString())

                    // 以下のデータはUIからは更新しない
                    // notification_tag
                    // register_key
                }.let { db.update(table, it, "$COL_ID=?", arrayOf(db_id.toString())) }
            }
        }

        // onResumeの時に設定を読み直す
        fun reloadSetting(item: SavedAccount, newData: SavedAccount? = null) {
            item.run {

                if (db_id == INVALID_DB_ID) error("SavedAccount.reloadSetting missing db_id")

                // DBから削除されてるかもしれない
                val b = newData ?: loadAccount(db_id) ?: return

                this.visibility = b.visibility
                this.confirm_boost = b.confirm_boost
                this.confirm_favourite = b.confirm_favourite
                this.confirm_unboost = b.confirm_unboost
                this.confirm_unfavourite = b.confirm_unfavourite
                this.confirm_post = b.confirm_post
                this.confirm_reaction = b.confirm_reaction
                this.confirm_unbookmark = b.confirm_unbookmark

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
                this.notification_update = b.notification_update
                this.notification_status_reference = b.notification_status_reference

                this.notification_tag = b.notification_tag
                this.default_text = b.default_text
                this.default_sensitive = b.default_sensitive
                this.expand_cw = b.expand_cw

                this.sound_uri = b.sound_uri

                this.image_resize = b.image_resize
                this.image_max_megabytes = b.image_max_megabytes
                this.movie_max_megabytes = b.movie_max_megabytes
                this.push_policy = b.push_policy

                this.movieTranscodeMode = b.movieTranscodeMode
                this.movieTranscodeBitrate = b.movieTranscodeBitrate
                this.movieTranscodeFramerate = b.movieTranscodeFramerate
                this.movieTranscodeSquarePixels = b.movieTranscodeSquarePixels
                this.lang = b.lang
            }
        }



        fun delete(dbId: Long) {
            try {
                db.deleteById(table, dbId.toString(), COL_ID)
            } catch (ex: Throwable) {
                log.e(ex, "SavedAccount.delete failed.")
                errorEx(ex, "SavedAccount.delete failed.")
            }
        }

        fun clearRegistrationCache() {
            ContentValues().apply {
                put(COL_REGISTER_KEY, REGISTER_KEY_UNREGISTERED)
                put(COL_REGISTER_TIME, 0L)
            }.let { db.update(table, it, null, null) }
        }

        fun loadAccount(dbId: Long): SavedAccount? =
            try {
                db.query(
                    table,
                    null,
                    "$COL_ID=?",
                    arrayOf(dbId.toString()),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    when {
                        cursor.moveToFirst() -> parse(lazyContext, cursor)
                        else -> {
                            log.e("moveToFirst failed. db_id=$dbId")
                            null
                        }
                    }
                }
            } catch (ex: Throwable) {
                log.e(ex, "loadAccount failed.")
                null
            }

        fun loadAccountList() =
            ArrayList<SavedAccount>().also { result ->
                try {
                    db.query(
                        table,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            parse(lazyContext, cursor)?.let { result.add(it) }
                        }
                    }
                } catch (ex: Throwable) {
                    log.e(ex, "loadAccountList failed.")
                    lazyContext.showToast(
                        true,
                        ex.withCaption("(SubwayTooter) broken in-app database?")
                    )
                }
            }

        fun loadByTag(tag: String): ArrayList<SavedAccount> {
            val result = ArrayList<SavedAccount>()
            try {
                db.query(
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
                log.e(ex, "loadByTag failed.")
                errorEx(ex, "SavedAccount.loadByTag failed.")
            }

            return result
        }

        /**
         * acctを指定してアカウントを取得する
         */
        fun loadAccountByAcct(fullAcct: Acct) =
            try {
                db.query(
                    table,
                    null,
                    "$COL_USER=?",
                    arrayOf(fullAcct.ascii),
                    null,
                    null,
                    null
                ).use { cursor ->
                    if (cursor.moveToNext()) parse(context, cursor) else null
                }
            } catch (ex: Throwable) {
                log.e(ex, "loadAccountByAcct failed.")
                null
            }

        fun hasRealAccount(): Boolean {
            try {
                db.query(
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
                log.e(ex, "hasNonPseudoAccount failed.")
            }

            return false
        }

        fun isSingleAccount(): Boolean =
            try {
                db.rawQuery(
                    "select count(*) from $table where $COL_USER NOT LIKE '?@%' limit 1",
                    emptyArray()
                )?.use {
                    it.moveToNext() && it.getInt(0) == 1
                } ?: false
            } catch (ex: Throwable) {
                log.e(ex, "getCount failed.")
                errorEx(ex, "SavedAccount.getCount failed.")
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

        fun sweepBuggieData() {
            // https://github.com/tateisu/SubwayTooter/issues/107
            // COL_ACCOUNTの内容がおかしければ削除する

            val list = ArrayList<Long>()
            try {
                db.query(
                    table,
                    null,
                    "$COL_ACCOUNT like ?",
                    arrayOf("jp.juggler.subwaytooter.api.entity.TootAccount@%"),
                    null,
                    null,
                    null
                ).use { cursor ->
                    val idxId = cursor.getColumnIndexOrThrow(COL_ID)
                    while (cursor.moveToNext()) {
                        list.add(cursor.getLong(idxId))
                    }
                }
            } catch (ex: Throwable) {
                log.e(ex, "sweepBuggieData failed.")
            }

            list.forEach {
                try {
                    db.delete(table, "$COL_ID=?", arrayOf(it.toString()))
                } catch (ex: Throwable) {
                    log.e(ex, "sweepBuggieData failed.")
                }
            }
        }

    }

    val bearerAccessToken
        get() = token_info?.string("access_token")

    val misskeyApiToken: String?
        get() = token_info?.string(AuthBase.KEY_API_KEY_MISSKEY)

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
        TootNotification.TYPE_ADMIN_SIGNUP,
        TootNotification.TYPE_ADMIN_REPORT,
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

        TootNotification.TYPE_UPDATE -> notification_update

        TootNotification.TYPE_STATUS_REFERENCE -> notification_status_reference

        else -> false
    }

    val isConfirmed: Boolean
        get() {
            val myId = this.loginAccount?.id
            return myId != EntityId.CONFIRMING
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

    fun getMovieResizeConfig() =
        MovieResizeConfig(
            mode = MovideResizeMode.fromInt(movieTranscodeMode),
            limitBitrate = movieTranscodeBitrate.toLongOrNull()
                ?.takeIf { it >= 100_000L } ?: 2_000_000L,
            limitFrameRate = movieTranscodeFramerate.toIntOrNull()
                ?.takeIf { it >= 1 } ?: 30,
            limitSquarePixels = movieTranscodeSquarePixels.toIntOrNull()
                ?.takeIf { it > 0 } ?: 2304000,
        )
}
