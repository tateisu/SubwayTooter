package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.util.data.*
import jp.juggler.util.log.LogCategory

class AccountNotificationStatus(
    // DB上のID
    var id: Long = 0L,
    // 該当ユーザのacct
    var acct: String = "",
    // acctのハッシュ値
    var acctHash: String = "",
    // アプリサーバから受け取ったハッシュ
    var appServerHash: String? = null,
    // プッシュ購読時に作成した秘密鍵
    var pushKeyPrivate: ByteArray? = null,
    // プッシュ購読時に作成した公開鍵
    var pushKeyPublic: ByteArray? = null,
    // プッシュ購読時に作成した乱数データ
    var pushAuthSecret: ByteArray? = null,
    // プッシュ購読時に取得したサーバ公開鍵
    var pushServerKey: ByteArray? = null,
    // Push購読時にSNSサーバに指定したコールバックURL
    // Misskeyで過去の購読を参照するために必要
    var lastPushEndpoint: String? = null,
    // Pull通知チェックや通知を出す処理で発生したエラーの情報
    var lastNotificationError: String? = null,
    // Push購読で発生したエラーの情報
    var lastSubscriptionError: String? = null,
) {
    companion object : TableCompanion {
        private val log = LogCategory("AccountNotificationStatus")
        private const val TABLE = "account_notification_status"
        override val table = TABLE
        private const val COL_ID = BaseColumns._ID
        private const val COL_ACCT = "a"
        private const val COL_ACCT_HASH = "ah"
        private const val COL_APP_SERVER_HASH = "ash"
        private const val COL_PUSH_KEY_PRIVATE = "pk_private"
        private const val COL_PUSH_KEY_PUBLIC = "pk_public"
        private const val COL_PUSH_AUTH_SECRET = "pk_auth_secret"
        private const val COL_PUSH_SERVER_KEY = "pk_server_key"
        private const val COL_LAST_PUSH_ENDPOINT = "lpe"
        private const val COL_LAST_NOTIFICATION_ERROR = "last_notification_error"
        private const val COL_LAST_SUBSCRIPTION_ERROR = "last_subscription_error"

        val columnList = ColumnMeta.List(table = TABLE, initialVersion = 65).apply {
            ColumnMeta(this, 0, COL_ID, ColumnMeta.TS_INT_PRIMARY_KEY_NOT_NULL)
            ColumnMeta(this, 0, COL_ACCT, ColumnMeta.TS_EMPTY_NOT_NULL)
            ColumnMeta(this, 0, COL_ACCT_HASH, ColumnMeta.TS_EMPTY_NOT_NULL)
            ColumnMeta(this, 0, COL_APP_SERVER_HASH, ColumnMeta.TS_TEXT_NULL)
            ColumnMeta(this, 0, COL_PUSH_KEY_PRIVATE, ColumnMeta.TS_BLOB_NULL)
            ColumnMeta(this, 0, COL_PUSH_KEY_PUBLIC, ColumnMeta.TS_BLOB_NULL)
            ColumnMeta(this, 0, COL_PUSH_AUTH_SECRET, ColumnMeta.TS_BLOB_NULL)
            ColumnMeta(this, 0, COL_PUSH_SERVER_KEY, ColumnMeta.TS_BLOB_NULL)
            ColumnMeta(this, 0, COL_LAST_PUSH_ENDPOINT, ColumnMeta.TS_TEXT_NULL)
            ColumnMeta(this, 0, COL_LAST_NOTIFICATION_ERROR, ColumnMeta.TS_TEXT_NULL)
            ColumnMeta(this, 0, COL_LAST_SUBSCRIPTION_ERROR, ColumnMeta.TS_TEXT_NULL)

            createExtra = {
                arrayOf(
                    "create unique index if not exists ${TABLE}_la on $TABLE($COL_ACCT)",
                    "create index if not exists ${TABLE}_ah on $TABLE($COL_ACCT_HASH)",
                    "create index if not exists ${TABLE}_ash on $TABLE($COL_APP_SERVER_HASH)",
                )
            }
        }

        override fun onDBCreate(db: SQLiteDatabase) {
            columnList.onDBCreate(db)
        }

        override fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 65 && newVersion >= 65) {
                onDBCreate(db)
            }
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    class ColIdx(cursor: Cursor) {
        val idxId = cursor.getColumnIndex(COL_ID)
        val idxAcct = cursor.getColumnIndex(COL_ACCT)
        val idxAcctHash = cursor.getColumnIndex(COL_ACCT_HASH)
        val idxAppServerHash = cursor.getColumnIndex(COL_APP_SERVER_HASH)
        val idxPushKeyPrivate = cursor.getColumnIndex(COL_PUSH_KEY_PRIVATE)
        val idxPushKeyPublic = cursor.getColumnIndex(COL_PUSH_KEY_PUBLIC)
        val idxPushAuthSecret = cursor.getColumnIndex(COL_PUSH_AUTH_SECRET)
        val idxPushServerKey = cursor.getColumnIndex(COL_PUSH_SERVER_KEY)
        val idxLastPushEndpoint = cursor.getColumnIndex(COL_LAST_PUSH_ENDPOINT)
        val idxLastNotificationError = cursor.getColumnIndex(COL_LAST_NOTIFICATION_ERROR)
        val idxLastSubscriptionError = cursor.getColumnIndex(COL_LAST_SUBSCRIPTION_ERROR)
        fun readRow(cursor: Cursor?) =
            try {
                cursor ?: error("cursor is null!")
                AccountNotificationStatus(
                    id = cursor.getLong(idxId),
                    acct = cursor.getString(idxAcct),
                    acctHash = cursor.getString(idxAcctHash),
                    appServerHash = cursor.getStringOrNull(idxAppServerHash),
                    pushKeyPrivate = cursor.getBlobOrNull(idxPushKeyPrivate),
                    pushKeyPublic = cursor.getBlobOrNull(idxPushKeyPublic),
                    pushAuthSecret = cursor.getBlobOrNull(idxPushAuthSecret),
                    pushServerKey = cursor.getBlobOrNull(idxPushServerKey),
                    lastPushEndpoint = cursor.getStringOrNull(idxLastPushEndpoint),
                    lastNotificationError = cursor.getStringOrNull(idxLastNotificationError),
                    lastSubscriptionError = cursor.getStringOrNull(idxLastSubscriptionError),
                )
            } catch (ex: Throwable) {
                log.e("readRow failed.")
                null
            }
    }

    // ID以外のカラムをContentValuesに変換する
    fun toContentValues() = ContentValues().apply {
        put(COL_ACCT, acct)
        put(COL_ACCT_HASH, acctHash)
        put(COL_APP_SERVER_HASH, appServerHash)
        put(COL_PUSH_KEY_PRIVATE, pushKeyPrivate)
        put(COL_PUSH_KEY_PUBLIC, pushKeyPublic)
        put(COL_PUSH_AUTH_SECRET, pushAuthSecret)
        put(COL_PUSH_SERVER_KEY, pushServerKey)
        put(COL_LAST_PUSH_ENDPOINT, lastPushEndpoint)
        put(COL_LAST_NOTIFICATION_ERROR, lastNotificationError)
        put(COL_LAST_SUBSCRIPTION_ERROR, lastSubscriptionError)
    }

    class Access(val db: SQLiteDatabase) {
        fun replace(item: AccountNotificationStatus) =
            item.toContentValues().replaceTo(db, TABLE).also { item.id = it }

        private fun Cursor?.readOne() = when (this?.moveToNext()) {
            true -> ColIdx(this).readRow(this)
            else -> null
        }

        fun findByAcctHash(acctHash: String) =
            db.queryById(TABLE, acctHash, COL_ACCT_HASH)?.use { it.readOne() }

        fun load(acct: Acct) =
            db.queryById(TABLE, acct.ascii, COL_ACCT)?.use { it.readOne() }

        fun appServerHash(acct: Acct): String? =
            load(acct)?.appServerHash

        fun lastEndpointUrl(acct: Acct): String? =
            load(acct)?.lastPushEndpoint

        private fun newInstance(acct: Acct) =
            AccountNotificationStatus(
                acct = acct.ascii,
                acctHash = acct.ascii.encodeUTF8().digestSHA256().encodeBase64Url()
            )

        fun loadOrCreate(acct: Acct): AccountNotificationStatus {
            load(acct)?.let { return it }
            return newInstance(acct).also { replace(it) }
        }

        private fun idOrCreate(acct: Acct) = loadOrCreate(acct).id

        /**
         * プッシュ購読の更新後にURLとキーを保存する
         */
        fun savePushKey(
            acct: Acct,
            lastPushEndpoint: String,
            pushKeyPrivate: ByteArray,
            pushKeyPublic: ByteArray,
            pushAuthSecret: ByteArray,
            pushServerKey: ByteArray,
        ) = ContentValues().apply {
            put(COL_LAST_PUSH_ENDPOINT, lastPushEndpoint)
            put(COL_PUSH_KEY_PRIVATE, pushKeyPrivate)
            put(COL_PUSH_KEY_PUBLIC, pushKeyPublic)
            put(COL_PUSH_AUTH_SECRET, pushAuthSecret)
            put(COL_PUSH_SERVER_KEY, pushServerKey)
        }.updateTo(db, TABLE, idOrCreate(acct).toString())

        fun deleteLastEndpointUrl(acct: Acct) =
            db.deleteById(TABLE, acct.ascii, COL_ACCT)

        fun savePushKey(
            acct: Acct,
            pushKeyPrivate: ByteArray,
            pushKeyPublic: ByteArray,
            pushAuthSecret: ByteArray,
        ) = ContentValues().apply {
            put(COL_PUSH_KEY_PRIVATE, pushKeyPrivate)
            put(COL_PUSH_KEY_PUBLIC, pushKeyPublic)
            put(COL_PUSH_AUTH_SECRET, pushAuthSecret)
        }.updateTo(db, TABLE, idOrCreate(acct).toString())

        fun saveServerKey(
            acct: Acct,
            lastPushEndpoint: String,
            pushServerKey: ByteArray,
        ) = ContentValues().apply {
            put(COL_LAST_PUSH_ENDPOINT, lastPushEndpoint)
            put(COL_PUSH_SERVER_KEY, pushServerKey)
        }.updateTo(db, TABLE, idOrCreate(acct).toString())

        fun deletePushKey(acct: Acct) =
            ContentValues().apply {
                putNull(COL_PUSH_KEY_PRIVATE)
                putNull(COL_PUSH_KEY_PUBLIC)
                putNull(COL_PUSH_AUTH_SECRET)
                putNull(COL_PUSH_SERVER_KEY)
            }.updateTo(db, TABLE, idOrCreate(acct).toString())

        private fun mapAcctToHash() = buildMap {
            db.rawQuery("select $COL_ACCT,$COL_ACCT_HASH from $TABLE", emptyArray())
                ?.use { cursor ->
                    val idxAcct = cursor.getColumnIndex(COL_ACCT)
                    val idxActHash = cursor.getColumnIndex(COL_ACCT_HASH)
                    while (cursor.moveToNext()) {
                        put(cursor.getString(idxAcct), cursor.getString(idxActHash))
                    }
                }
        }

        // returns map of acctHash to acct
        fun updateAcctHash(accts: Iterable<Acct>) =
            buildMap {
                val mapAcctToHash = mapAcctToHash()
                for (acct in accts) {
                    val hash = mapAcctToHash[acct.ascii] ?: loadOrCreate(acct).acctHash
                    put(hash, acct)
                }
            }

        fun saveAppServerHash(id: Long, appServerHash: String) =
            ContentValues().apply {
                put(COL_APP_SERVER_HASH, appServerHash)
            }.updateTo(db, TABLE, id.toString())


        private fun updateSingleString(acct:Acct, col: String, value: String?) {
            ContentValues().apply{
                put(col, value)
            }.updateTo(db, TABLE,acct.ascii, COL_ACCT)
        }
        fun updateNotificationError(acct:Acct, text: String?) {
            updateSingleString(acct, COL_LAST_NOTIFICATION_ERROR, text)
        }

        fun updateSubscriptionError(acct:Acct, text: String?) {
            updateSingleString(acct, COL_LAST_SUBSCRIPTION_ERROR, text)
        }
    }
}
