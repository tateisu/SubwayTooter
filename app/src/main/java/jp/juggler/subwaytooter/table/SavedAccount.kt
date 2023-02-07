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

/**
 * ユーザが操作するアカウント
 * - Access.saveNew で insert。直後にロードし直す
 * - 通知の更新状況などは AccountNotificationStatus に保存するようになった
 */
class SavedAccount(
    val db_id: Long,

    acctArg: String,
    apiHostArg: String? = null,
    apDomainArg: String? = null,

    var confirmBoost: Boolean = false,
    var confirmFavourite: Boolean = false,
    var confirmFollow: Boolean = false,
    var confirmFollowLocked: Boolean = false,
    var confirmPost: Boolean = false,
    var confirmReaction: Boolean = true,
    var confirmUnbookmark: Boolean = true,
    var confirmUnboost: Boolean = false,
    var confirmUnfavourite: Boolean = false,
    var confirmUnfollow: Boolean = false,
    var defaultSensitive: Boolean = false,
    var defaultText: String = "",
    var dontHideNsfw: Boolean = false,
    var dontShowTimeout: Boolean = false,
    var expandCw: Boolean = false,
    var extraJson: JsonObject = JsonObject(),
    var imageMaxMegabytes: String? = null,
    var imageResize: String? = null,
    var loginAccount: TootAccount? = null, // 疑似アカウントではnull
    var maxTootChars: Int = 0,
    var movieMaxMegabytes: String? = null,
    var notificationBoost: Boolean = false,
    var notificationFavourite: Boolean = false,
    var notificationFollow: Boolean = false,
    var notificationFollowRequest: Boolean = false,
    var notificationMention: Boolean = false,
    var notificationPost: Boolean = false,
    var notificationReaction: Boolean = false,
    var notificationUpdate: Boolean = true,
    var notificationVote: Boolean = false,
    var pushPolicy: String? = null,
    var tokenJson: JsonObject? = null,
    var visibility: TootVisibility = TootVisibility.Public,
//    var soundUri: String = "",
// private var lastNotificationError: String? = null,
// private var last_push_endpoint: String? = null,
// private var last_subscription_error: String? = null,
// private var register_key: String? = null
// private var register_time: Long = 0
// var notification_tag: String? = null

    override var misskeyVersion: Int = 0,
) : LinkHelper {

    // SavedAccountのロード時にhostを供給する必要があった
    override val apiHost: Host
    override val apDomain: Host

    val username: String
    val acct: Acct

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
    var notificationStatusReference by jsonDelegates.boolean

    @JsonPropBoolean("notificationPushEnable", true)
    var notificationPushEnable by jsonDelegates.boolean

    @JsonPropBoolean("notificationPullEnable", false)
    var notificationPullEnable by jsonDelegates.boolean

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
        misskeyVersion = cursor.getInt(COL_MISSKEY_VERSION),
        confirmBoost = cursor.getBoolean(COL_CONFIRM_BOOST),
        confirmFavourite = cursor.getBoolean(COL_CONFIRM_FAVOURITE),
        confirmFollow = cursor.getBoolean(COL_CONFIRM_FOLLOW),
        confirmFollowLocked = cursor.getBoolean(COL_CONFIRM_FOLLOW_LOCKED),
        confirmPost = cursor.getBoolean(COL_CONFIRM_POST),
        confirmReaction = cursor.getBoolean(COL_CONFIRM_REACTION),
        confirmUnbookmark = cursor.getBoolean(COL_CONFIRM_UNBOOKMARK),
        confirmUnboost = cursor.getBoolean(COL_CONFIRM_UNBOOST),
        confirmUnfavourite = cursor.getBoolean(COL_CONFIRM_UNFAVOURITE),
        confirmUnfollow = cursor.getBoolean(COL_CONFIRM_UNFOLLOW),
        defaultSensitive = cursor.getBoolean(COL_DEFAULT_SENSITIVE),
        defaultText = cursor.getStringOrNull(COL_DEFAULT_TEXT) ?: "",
        dontHideNsfw = cursor.getBoolean(COL_DONT_HIDE_NSFW),
        dontShowTimeout = cursor.getBoolean(COL_DONT_SHOW_TIMEOUT),
        expandCw = cursor.getBoolean(COL_EXPAND_CW),
        extraJson = cursor.getStringOrNull(COL_EXTRA_JSON)?.decodeJsonObject() ?: JsonObject(),
        imageMaxMegabytes = cursor.getStringOrNull(COL_IMAGE_MAX_MEGABYTES),
        imageResize = cursor.getStringOrNull(COL_IMAGE_RESIZE),
        maxTootChars = cursor.getInt(COL_MAX_TOOT_CHARS),
        movieMaxMegabytes = cursor.getStringOrNull(COL_MOVIE_MAX_MEGABYTES),
        notificationBoost = cursor.getBoolean(COL_NOTIFICATION_BOOST),
        notificationFavourite = cursor.getBoolean(COL_NOTIFICATION_FAVOURITE),
        notificationFollow = cursor.getBoolean(COL_NOTIFICATION_FOLLOW),
        notificationFollowRequest = cursor.getBoolean(COL_NOTIFICATION_FOLLOW_REQUEST),
        notificationMention = cursor.getBoolean(COL_NOTIFICATION_MENTION),
        notificationPost = cursor.getBoolean(COL_NOTIFICATION_POST),
        notificationReaction = cursor.getBoolean(COL_NOTIFICATION_REACTION),
        notificationUpdate = cursor.getBoolean(COL_NOTIFICATION_UPDATE),
        notificationVote = cursor.getBoolean(COL_NOTIFICATION_VOTE),
        pushPolicy = cursor.getStringOrNull(COL_PUSH_POLICY),
//        soundUri = cursor.getString(COL_SOUND_URI),
        tokenJson = cursor.getString(COL_TOKEN).decodeJsonObject(),
        visibility = TootVisibility.parseSavedVisibility(cursor.getStringOrNull(COL_VISIBILITY))
            ?: TootVisibility.Public,
        //        lastNotificationError = cursor.getStringOrNull(COL_LAST_NOTIFICATION_ERROR)
        //        last_push_endpoint = cursor.getStringOrNull(COL_LAST_PUSH_ENDPOINT)
        //        last_subscription_error = cursor.getStringOrNull(COL_LAST_SUBSCRIPTION_ERROR)
        //        notification_tag = cursor.getStringOrNull(COL_NOTIFICATION_TAG)
        //        register_key = cursor.getStringOrNull(COL_REGISTER_KEY)
        //        register_time = cursor.getLong(COL_REGISTER_TIME)

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
    }

    companion object : TableCompanion {

        private val log = LogCategory("SavedAccount")
        override val table = "access_info"

        private const val COL_ID = BaseColumns._ID
        private const val COL_HOST = "h"
        private const val COL_DOMAIN = "d"
        private const val COL_USER = "u"
        private const val COL_ACCOUNT = "a"
        private const val COL_TOKEN = "t"
        private const val COL_CONFIRM_BOOST = "confirm_boost"
        private const val COL_CONFIRM_FAVOURITE = "confirm_favourite"
        private const val COL_CONFIRM_FOLLOW = "confirm_follow"
        private const val COL_CONFIRM_FOLLOW_LOCKED = "confirm_follow_locked"
        private const val COL_CONFIRM_POST = "confirm_post"
        private const val COL_CONFIRM_REACTION = "confirm_reaction"
        private const val COL_CONFIRM_UNBOOKMARK = "confirm_unbookmark"
        private const val COL_CONFIRM_UNBOOST = "confirm_unboost"
        private const val COL_CONFIRM_UNFAVOURITE = "confirm_unfavourite"
        private const val COL_CONFIRM_UNFOLLOW = "confirm_unfollow"
        private const val COL_DEFAULT_SENSITIVE = "default_sensitive"
        private const val COL_DEFAULT_TEXT = "default_text"
        private const val COL_DONT_HIDE_NSFW = "dont_hide_nsfw"
        private const val COL_DONT_SHOW_TIMEOUT = "dont_show_timeout"
        private const val COL_EXPAND_CW = "expand_cw"
        private const val COL_EXTRA_JSON = "extra_json"
        private const val COL_IMAGE_MAX_MEGABYTES = "image_max_megabytes"
        private const val COL_IMAGE_RESIZE = "image_resize"
        private const val COL_MAX_TOOT_CHARS = "max_toot_chars"
        private const val COL_MISSKEY_VERSION = "is_misskey"
        private const val COL_MOVIE_MAX_MEGABYTES = "movie_max_megabytes"
        private const val COL_NOTIFICATION_BOOST = "notification_boost"
        private const val COL_NOTIFICATION_FAVOURITE = "notification_favourite"
        private const val COL_NOTIFICATION_FOLLOW = "notification_follow"
        private const val COL_NOTIFICATION_FOLLOW_REQUEST = "notification_follow_request"
        private const val COL_NOTIFICATION_MENTION = "notification_mention"
        private const val COL_NOTIFICATION_POST = "notification_post"
        private const val COL_NOTIFICATION_REACTION = "notification_reaction"
        private const val COL_NOTIFICATION_TAG = "notification_server"
        private const val COL_NOTIFICATION_UPDATE = "notification_update"
        private const val COL_NOTIFICATION_VOTE = "notification_vote"
        private const val COL_PUSH_POLICY = "push_policy"
        private const val COL_VISIBILITY = "visibility"
//        private const val COL_SOUND_URI = "sound_uri"
//        private const val COL_LAST_NOTIFICATION_ERROR = "last_notification_error"
//        private const val COL_LAST_PUSH_ENDPOINT = "last_push_endpoint"
//        private const val COL_LAST_SUBSCRIPTION_ERROR = "last_subscription_error"
//        private const val COL_REGISTER_KEY = "register_key"
//        private const val COL_REGISTER_TIME = "register_time"

        // COL_MISSKEY_VERSIONのカラム名がおかしいのは、昔はboolean扱いだったから
        // 0: not misskey
        // 1: old(v10) misskey
        // 11: misskey v11

        val columnList = MetaColumns(table, 0).apply {
            column(0, COL_ID, "INTEGER PRIMARY KEY")
            column(0, COL_ACCOUNT, "text not null")
            column(0, COL_CONFIRM_BOOST, MetaColumns.TS_TRUE)
            column(0, COL_DONT_HIDE_NSFW, MetaColumns.TS_ZERO)
            column(0, COL_HOST, "text not null")
            column(0, COL_TOKEN, "text not null")
            column(0, COL_USER, "text not null")
            column(0, COL_VISIBILITY, "text")
            column(2, COL_NOTIFICATION_BOOST, MetaColumns.TS_TRUE)
            column(2, COL_NOTIFICATION_FAVOURITE, MetaColumns.TS_TRUE)
            column(2, COL_NOTIFICATION_FOLLOW, MetaColumns.TS_TRUE)
            column(2, COL_NOTIFICATION_MENTION, MetaColumns.TS_TRUE)
            column(10, COL_CONFIRM_FOLLOW, MetaColumns.TS_TRUE)
            column(10, COL_CONFIRM_FOLLOW_LOCKED, MetaColumns.TS_TRUE)
            column(10, COL_CONFIRM_POST, MetaColumns.TS_TRUE)
            column(10, COL_CONFIRM_UNFOLLOW, MetaColumns.TS_TRUE)
            column(13, COL_NOTIFICATION_TAG, MetaColumns.TS_EMPTY)
//            column(14, COL_REGISTER_KEY, MetaColumns.TS_EMPTY)
//            column(14, COL_REGISTER_TIME, MetaColumns.TS_ZERO)
//            column(16, COL_SOUND_URI, MetaColumns.TS_EMPTY)
            column(18, COL_DONT_SHOW_TIMEOUT, MetaColumns.TS_ZERO)
            column(23, COL_CONFIRM_FAVOURITE, MetaColumns.TS_TRUE)
            column(24, COL_CONFIRM_UNBOOST, MetaColumns.TS_TRUE)
            column(24, COL_CONFIRM_UNFAVOURITE, MetaColumns.TS_TRUE)
            column(27, COL_DEFAULT_TEXT, MetaColumns.TS_EMPTY)
            column(28, COL_MISSKEY_VERSION, MetaColumns.TS_ZERO)
            column(33, COL_NOTIFICATION_REACTION, MetaColumns.TS_TRUE)
            column(33, COL_NOTIFICATION_VOTE, MetaColumns.TS_TRUE)
            column(38, COL_DEFAULT_SENSITIVE, MetaColumns.TS_ZERO)
            column(38, COL_EXPAND_CW, MetaColumns.TS_ZERO)
            column(39, COL_MAX_TOOT_CHARS, MetaColumns.TS_ZERO)
//            column(42, COL_LAST_NOTIFICATION_ERROR, "text")
            column(44, COL_NOTIFICATION_FOLLOW_REQUEST, MetaColumns.TS_TRUE)
//            column(45, COL_LAST_SUBSCRIPTION_ERROR, "text")
//            column(46, COL_LAST_PUSH_ENDPOINT, "text")
            column(56, COL_DOMAIN, "text")
            column(57, COL_NOTIFICATION_POST, MetaColumns.TS_TRUE)
            column(59, COL_IMAGE_MAX_MEGABYTES, "text default null")
            column(59, COL_IMAGE_RESIZE, "text default null")
            column(59, COL_MOVIE_MAX_MEGABYTES, "text default null")
            column(60, COL_PUSH_POLICY, "text default null")
            column(61, COL_CONFIRM_REACTION, MetaColumns.TS_TRUE)
            column(62, COL_CONFIRM_UNBOOKMARK, MetaColumns.TS_TRUE)
            column(63, COL_EXTRA_JSON, "text default null")
            column(64, COL_NOTIFICATION_UPDATE, MetaColumns.TS_TRUE)
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

        const val LANG_WEB = "(web)"
        const val LANG_DEVICE = "(device)"

        // 横断検索用の、何とも紐ついていないアカウント
        // 保存しない。
        val na by lazy {
            SavedAccount(-1L, "?@?").apply {
                notificationFollow = false
                notificationFollowRequest = false
                notificationFavourite = false
                notificationBoost = false
                notificationMention = false
                notificationReaction = false
                notificationVote = false
                notificationPost = false
                notificationUpdate = false
            }
        }

        private fun parse(context: Context, cursor: Cursor) =
            try {
                SavedAccount(context, cursor)
            } catch (ex: Throwable) {
                log.e(ex, "parse failed.")
                null
            }

        private fun Boolean.booleanToInt(trueValue: Int, falseValue: Int = 0) =
            if (this) trueValue else falseValue
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
                if (isInvalidId) error("updateTokenInfo: missing db_id")

                this.tokenJson = auth2Result.tokenJson
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
                if (isInvalidId) return

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
                    checkNotificationImmediateAll(context, onlyEnqueue = true)
                    checkNotificationImmediate(context, db_id)
                }
            }
        }

        fun save(item: SavedAccount) {
            item.run {
                if (isInvalidId) error("saveSetting: missing db_id")

                ContentValues().apply {
                    put(COL_CONFIRM_BOOST, confirmBoost)
                    put(COL_CONFIRM_FAVOURITE, confirmFavourite)
                    put(COL_CONFIRM_FOLLOW, confirmFollow)
                    put(COL_CONFIRM_FOLLOW_LOCKED, confirmFollowLocked)
                    put(COL_CONFIRM_POST, confirmPost)
                    put(COL_CONFIRM_REACTION, confirmReaction)
                    put(COL_CONFIRM_UNBOOKMARK, confirmUnbookmark)
                    put(COL_CONFIRM_UNBOOST, confirmUnboost)
                    put(COL_CONFIRM_UNFAVOURITE, confirmUnfavourite)
                    put(COL_CONFIRM_UNFOLLOW, confirmUnfollow)
                    put(COL_DEFAULT_SENSITIVE, defaultSensitive)
                    put(COL_DEFAULT_TEXT, defaultText)
                    put(COL_DONT_HIDE_NSFW, dontHideNsfw)
                    put(COL_DONT_SHOW_TIMEOUT, dontShowTimeout)
                    put(COL_EXPAND_CW, expandCw)
                    put(COL_EXTRA_JSON, extraJson.toString())
                    put(COL_IMAGE_MAX_MEGABYTES, imageMaxMegabytes)
                    put(COL_IMAGE_RESIZE, imageResize)
                    put(COL_MAX_TOOT_CHARS, maxTootChars)
                    put(COL_MOVIE_MAX_MEGABYTES, movieMaxMegabytes)
                    put(COL_NOTIFICATION_BOOST, notificationBoost)
                    put(COL_NOTIFICATION_FAVOURITE, notificationFavourite)
                    put(COL_NOTIFICATION_FOLLOW, notificationFollow)
                    put(COL_NOTIFICATION_FOLLOW_REQUEST, notificationFollowRequest)
                    put(COL_NOTIFICATION_MENTION, notificationMention)
                    put(COL_NOTIFICATION_POST, notificationPost)
                    put(COL_NOTIFICATION_REACTION, notificationReaction)
                    put(COL_NOTIFICATION_UPDATE, notificationUpdate)
                    put(COL_NOTIFICATION_VOTE, notificationVote)
                    put(COL_PUSH_POLICY, pushPolicy)
//                    put(COL_SOUND_URI, soundUri)
                    put(COL_VISIBILITY, visibility.id.toString())
                }.let { db.update(table, it, "$COL_ID=?", arrayOf(db_id.toString())) }
            }
        }

        // onResumeの時に設定を読み直す
        fun reloadSetting(item: SavedAccount, newData: SavedAccount? = null) {
            item.run {

                if (isInvalidId) error("SavedAccount.reloadSetting missing db_id")

                // DBから削除されてるかもしれない
                val b = newData ?: loadAccount(db_id) ?: return

                this.confirmBoost = b.confirmBoost
                this.confirmFavourite = b.confirmFavourite
                this.confirmPost = b.confirmPost
                this.confirmReaction = b.confirmReaction
                this.confirmUnbookmark = b.confirmUnbookmark
                this.confirmUnboost = b.confirmUnboost
                this.confirmUnfavourite = b.confirmUnfavourite
                this.defaultSensitive = b.defaultSensitive
                this.defaultText = b.defaultText
                this.dontHideNsfw = b.dontHideNsfw
                this.dontShowTimeout = b.dontShowTimeout
                this.expandCw = b.expandCw
                this.imageMaxMegabytes = b.imageMaxMegabytes
                this.imageResize = b.imageResize
                this.lang = b.lang
                this.movieTranscodeBitrate = b.movieTranscodeBitrate
                this.movieTranscodeFramerate = b.movieTranscodeFramerate
                this.movieTranscodeMode = b.movieTranscodeMode
                this.movieTranscodeSquarePixels = b.movieTranscodeSquarePixels
                this.movieMaxMegabytes = b.movieMaxMegabytes
                this.notificationBoost = b.notificationBoost
                this.notificationFavourite = b.notificationFavourite
                this.notificationFollow = b.notificationFollow
                this.notificationFollowRequest = b.notificationFollowRequest
                this.notificationMention = b.notificationMention
                this.notificationPost = b.notificationPost
                this.notificationReaction = b.notificationReaction
                this.notificationStatusReference = b.notificationStatusReference
                this.notificationUpdate = b.notificationUpdate
                this.notificationVote = b.notificationVote
                this.pushPolicy = b.pushPolicy
                this.tokenJson = b.tokenJson
                this.visibility = b.visibility
                this.notificationPushEnable = b.notificationPushEnable
                this.notificationPullEnable = b.notificationPullEnable

                //                this.soundUri = b.soundUri
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

//        fun clearRegistrationCache() {
//            ContentValues().apply {
//                put(COL_REGISTER_KEY, REGISTER_KEY_UNREGISTERED)
//                put(COL_REGISTER_TIME, 0L)
//            }.let { db.update(table, it, null, null) }
//        }

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

//        fun loadByTag(tag: String): ArrayList<SavedAccount> {
//            val result = ArrayList<SavedAccount>()
//            try {
//                db.query(
//                    table,
//                    null,
//                    "$COL_NOTIFICATION_TAG=?",
//                    arrayOf(tag),
//                    null,
//                    null,
//                    null
//                )
//                    .use { cursor ->
//                        while (cursor.moveToNext()) {
//                            val a = parse(context, cursor)
//                            if (a != null) result.add(a)
//                        }
//                    }
//            } catch (ex: Throwable) {
//                log.e(ex, "loadByTag failed.")
//                errorEx(ex, "SavedAccount.loadByTag failed.")
//            }
//
//            return result
//        }

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

    // Notestock検索カラムなど、特定のSNSサーバと紐ついていないアカウントなら真
    val isNA: Boolean
        get() = acct == Acct.UNKNOWN

    // 実在しない疑似アカウントなら真。NAの場合も真を返す
    val isPseudo: Boolean
        get() = username == "?"

    // DB用のIDが無効なら真
    val isInvalidId: Boolean
        get() = db_id <= 0L

    // Mastodonのユーザ作成の承認まち状態ではないなら真
    val isConfirmed: Boolean
        get() = EntityId.CONFIRMING != loginAccount?.id

    // Mastodon用のアクセストークン
    val bearerAccessToken: String?
        get() = tokenJson?.string("access_token")

    // Misskey用のAPIトークン
    val misskeyApiToken: String?
        get() = tokenJson?.string(AuthBase.KEY_API_KEY_MISSKEY)

    override fun hashCode(): Int = acct.hashCode()

    override fun equals(other: Any?): Boolean =
        when (other) {
            is SavedAccount -> acct == other.acct
            else -> false
        }

    // APIリクエスト用のJsonObjectに misskeyApiToken を格納する
    fun putMisskeyApiToken(params: JsonObject = JsonObject()): JsonObject {
        val apiKey = misskeyApiToken
        if (apiKey?.isNotEmpty() == true) params["i"] = apiKey
        return params
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

    // URLが相対指定だった場合にスキーマとホスト名を補う
    fun supplyBaseUrl(url: String?): String? =
        when {
            url.isNullOrEmpty() -> null
            url[0] == '/' -> "https://${apiHost.ascii}$url"
            else -> url
        }

    fun isNicoru(account: TootAccount?) =
        account?.apiHost == Host.FRIENDS_NICO

    fun notificationFlags() = 0 +
            notificationBoost.booleanToInt(1) +
            notificationFavourite.booleanToInt(2) +
            notificationFollow.booleanToInt(4) +
            notificationMention.booleanToInt(8) +
            notificationReaction.booleanToInt(16) +
            notificationVote.booleanToInt(32) +
            notificationFollowRequest.booleanToInt(64) +
            notificationPost.booleanToInt(128) +
            notificationUpdate.booleanToInt(256) +
            notificationStatusReference.booleanToInt(512)

    fun canNotificationShowing(type: String?) = when (type) {

        TootNotification.TYPE_MENTION,
        TootNotification.TYPE_REPLY,
        -> notificationMention

        TootNotification.TYPE_REBLOG,
        TootNotification.TYPE_RENOTE,
        TootNotification.TYPE_QUOTE,
        -> notificationBoost

        TootNotification.TYPE_FAVOURITE -> notificationFavourite

        TootNotification.TYPE_FOLLOW,
        TootNotification.TYPE_UNFOLLOW,
        TootNotification.TYPE_ADMIN_SIGNUP,
        TootNotification.TYPE_ADMIN_REPORT,
        -> notificationFollow

        TootNotification.TYPE_FOLLOW_REQUEST,
        TootNotification.TYPE_FOLLOW_REQUEST_MISSKEY,
        TootNotification.TYPE_FOLLOW_REQUEST_ACCEPTED_MISSKEY,
        -> notificationFollowRequest

        TootNotification.TYPE_EMOJI_REACTION_PLEROMA,
        TootNotification.TYPE_EMOJI_REACTION,
        TootNotification.TYPE_REACTION,
        -> notificationReaction

        TootNotification.TYPE_VOTE,
        TootNotification.TYPE_POLL,
        TootNotification.TYPE_POLL_VOTE_MISSKEY,
        -> notificationVote

        TootNotification.TYPE_STATUS -> notificationPost

        TootNotification.TYPE_UPDATE -> notificationUpdate

        TootNotification.TYPE_STATUS_REFERENCE -> notificationStatusReference

        else -> false
    }

    fun getResizeConfig() =
        resizeConfigList.find { it.spec == this.imageResize } ?: defaultResizeConfig

    fun getMovieMaxBytes(ti: TootInstance) = 1000000 * max(
        1,
        this.movieMaxMegabytes?.toIntOrNull()
            ?: if (ti.instanceType == InstanceType.Pixelfed) 15 else 40
    )

    fun getImageMaxBytes(ti: TootInstance) = 1000000 * max(
        1,
        this.imageMaxMegabytes?.toIntOrNull()
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

    fun isRequiredPushSubscription() = (notificationFlags() != 0) && notificationPushEnable
    fun isRequiredPullCheck() = (notificationFlags() != 0) && notificationPullEnable
}
