package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import androidx.room.*
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.util.*
import jp.juggler.util.data.*
import jp.juggler.util.log.LogCategory
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.TimeUnit

data class PushMessage(
    // DBの主ID
    var id: Long = 0L,
    // 通知を受け取るアカウントのacct。通知のタイトルでもある
    var loginAcct: String? = null,
    // 通知情報に含まれるタイムスタンプ
    var timestamp: Long = System.currentTimeMillis(),
    // 通知を受信/保存した時刻
    var timeSave: Long = System.currentTimeMillis(),
    // 通知を開いた/削除した時刻
    var timeDismiss: Long = 0L,
    // 通知ID。(loginAcct + notificationId) で重複排除する。
    var notificationId: String? = null,
    // 通知の種別。小アイコン、アクセント色、Misskeyの文言に影響する
    var notificationType: String? = null,
    // 通知表示の本文
    var text: String? = null,
    // 展開表示した本文
    var textExpand: String? = null,
    // 小アイコンURL。昔のMastodonはバッジ画像が提供されていた。
    var iconSmall: String? = null,
    // 大アイコンURL。通知の原因となったユーザのアイコン画像。
    var iconLarge: String? = null,
    // WebPushで送られたJSONデータ
    var messageJson: JsonObject? = null,
    // WebPushのデコードに使うJSONデータ
    var headerJson: JsonObject? = null,
    // アプリサーバから送られてきたバイナリデータ
    var rawBody: ByteArray? = null,
) {
    companion object : TableCompanion {
        private val log = LogCategory("PushMessage")
        const val TABLE = "push_message"
        override val table = TABLE
        private const val COL_ID = BaseColumns._ID
        private const val COL_LOGIN_ACCT = "login_acct"
        private const val COL_TIMESTAMP = "timestamp"
        private const val COL_TIME_SAVE = "time_save"
        private const val COL_TIME_DISMISS = "time_dismiss"
        private const val COL_NOTIFICATION_ID = "notification_id"
        private const val COL_NOTIFICATION_TYPE = "notification_type"
        private const val COL_TEXT = "text"
        private const val COL_TEXT_EXPAND = "text_expand"
        private const val COL_ICON_SMALL = "icon_small"
        private const val COL_ICON_LARGE = "icon_large"
        private const val COL_MESSAGE_JSON = "message_json"
        private const val COL_HEADER_JSON = "header_json"
        private const val COL_RAW_BODY = "raw_body"

        val columnList = MetaColumns(TABLE, initialVersion = 65).apply {
            deleteBeforeCreate = true
            column(0, COL_ID, MetaColumns.TS_INT_PRIMARY_KEY_NOT_NULL)
            column(0, COL_LOGIN_ACCT, MetaColumns.TS_TEXT_NULL)
            column(0, COL_TIMESTAMP, MetaColumns.TS_ZERO_NOT_NULL)
            column(0, COL_TIME_SAVE, MetaColumns.TS_ZERO_NOT_NULL)
            column(0, COL_TIME_DISMISS, MetaColumns.TS_ZERO_NOT_NULL)
            column(0, COL_NOTIFICATION_ID, MetaColumns.TS_TEXT_NULL)
            column(0, COL_NOTIFICATION_TYPE, MetaColumns.TS_TEXT_NULL)
            column(0, COL_TEXT, MetaColumns.TS_TEXT_NULL)
            column(0, COL_TEXT_EXPAND, MetaColumns.TS_TEXT_NULL)
            column(0, COL_ICON_SMALL, MetaColumns.TS_TEXT_NULL)
            column(0, COL_ICON_LARGE, MetaColumns.TS_TEXT_NULL)
            column(0, COL_MESSAGE_JSON, MetaColumns.TS_TEXT_NULL)
            column(0, COL_HEADER_JSON, MetaColumns.TS_TEXT_NULL)
            column(0, COL_RAW_BODY, MetaColumns.TS_BLOB_NULL)
            createExtra={
                arrayOf(
                    "create index if not exists ${TABLE}_save on $TABLE($COL_TIME_SAVE)",
                    "create index if not exists ${TABLE}_acct_dismiss on $TABLE($COL_LOGIN_ACCT,$COL_TIME_DISMISS)",
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
            columnList.onDBUpgrade(db, oldVersion, newVersion)
        }

        @Suppress("MemberVisibilityCanBePrivate")
        val flowDataChanged = MutableStateFlow(0L)

        private fun fireDataChanged() {
            flowDataChanged.value = System.currentTimeMillis()
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    class ColIdx(cursor: Cursor) {
        val idxId = cursor.getColumnIndex(COL_ID)
        val idxLoginAcct = cursor.getColumnIndex(COL_LOGIN_ACCT)
        val idxTimestamp = cursor.getColumnIndex(COL_TIMESTAMP)
        val idxTimeSave = cursor.getColumnIndex(COL_TIME_SAVE)
        val idxTimeDismiss = cursor.getColumnIndex(COL_TIME_DISMISS)
        val idxNotificationId = cursor.getColumnIndex(COL_NOTIFICATION_ID)
        val idxNotificationType = cursor.getColumnIndex(COL_NOTIFICATION_TYPE)
        val idxText = cursor.getColumnIndex(COL_TEXT)
        val idxTextExpand = cursor.getColumnIndex(COL_TEXT_EXPAND)
        val idxIconSmall = cursor.getColumnIndex(COL_ICON_SMALL)
        val idxIconLarge = cursor.getColumnIndex(COL_ICON_LARGE)
        val idxMessageJson = cursor.getColumnIndex(COL_MESSAGE_JSON)
        val idxHeaderJson = cursor.getColumnIndex(COL_HEADER_JSON)
        val idxRawBody = cursor.getColumnIndex(COL_RAW_BODY)

        fun readRow(cursor: Cursor) =
            PushMessage(
                id = cursor.getLong(idxId),
                loginAcct = cursor.getStringOrNull(idxLoginAcct),
                timestamp = cursor.getLong(idxTimestamp),
                timeSave = cursor.getLong(idxTimeSave),
                timeDismiss = cursor.getLong(idxTimeDismiss),
                notificationId = cursor.getStringOrNull(idxNotificationId),
                notificationType = cursor.getStringOrNull(idxNotificationType),
                text = cursor.getStringOrNull(idxText),
                textExpand = cursor.getStringOrNull(idxTextExpand),
                iconSmall = cursor.getStringOrNull(idxIconSmall),
                iconLarge = cursor.getStringOrNull(idxIconLarge),
                messageJson = cursor.getStringOrNull(idxMessageJson)?.decodeJsonObject(),
                headerJson = cursor.getStringOrNull(idxHeaderJson)?.decodeJsonObject(),
                rawBody = cursor.getBlobOrNull(idxRawBody),
            )

        fun readOne(cursor: Cursor) =
            when (cursor.moveToNext()) {
                true -> readRow(cursor)
                else -> null
            }

        fun readAll(cursor: Cursor) = buildList {
            while (cursor.moveToNext()) {
                add(readRow(cursor))
            }
        }
    }

    // ID以外のカラムをContentValuesに変換する
    fun toContentValues() = ContentValues().apply {
        put(COL_LOGIN_ACCT, loginAcct)
        put(COL_TIMESTAMP, timestamp)
        put(COL_TIME_SAVE, timeSave)
        put(COL_TIME_DISMISS, timeDismiss)
        put(COL_NOTIFICATION_ID, notificationId)
        put(COL_NOTIFICATION_TYPE, notificationType)
        put(COL_TEXT, text)
        put(COL_TEXT_EXPAND, textExpand)
        put(COL_ICON_SMALL, iconSmall)
        put(COL_ICON_LARGE, iconLarge)
        put(COL_MESSAGE_JSON, messageJson?.toString())
        put(COL_HEADER_JSON, headerJson?.toString())
        put(COL_RAW_BODY, rawBody)
    }

    class Access(val db: SQLiteDatabase) {
        // return id of new row
        fun replace(item: PushMessage) =
            item.toContentValues().replaceTo(db, TABLE)
                .also { item.id = it }
                .also { fireDataChanged() }

        fun update(vararg items: PushMessage) =
            items.sumOf { it.toContentValues().updateTo(db, TABLE, it.id.toString()) }
                .also { fireDataChanged() }

        fun delete(id: Long) = db.deleteById(TABLE, id.toString())
            .also { fireDataChanged() }

        fun save(a: PushMessage): Long {
            when (a.id) {
                0L -> a.id = replace(a)
                else -> update(a)
            }
            fireDataChanged()
            return a.id
        }

        fun find(messageId: Long): PushMessage? =
            db.rawQuery(
                "select * from $TABLE where $COL_ID=?",
                arrayOf(messageId.toString())
            )?.use { ColIdx(it).readOne(it) }

        fun dismiss(messageId: Long) {
            val pm = find(messageId) ?: return
            if (pm.timeDismiss == 0L) {
                pm.timeDismiss = System.currentTimeMillis()
                update(pm)
            }
        }

        fun dismissByAcct(acct: Acct) {
            db.execSQL(
                "update $table set $COL_TIME_DISMISS=? where $COL_LOGIN_ACCT=? and $COL_TIME_DISMISS=0",
                arrayOf(System.currentTimeMillis().toString(), acct.ascii),
            )
        }

        fun deleteOld(now: Long) {
            try {
                val expire = now - TimeUnit.DAYS.toMillis(30)
                db.execSQL("delete from $TABLE where $COL_TIME_SAVE < $expire")
                fireDataChanged()
            } catch (ex: Throwable) {
                log.e(ex, "sweep failed.")
            }
        }

        fun listAll(): List<PushMessage> =
            db.queryAll(TABLE, "$COL_TIME_SAVE desc")
                ?.use { ColIdx(it).readAll(it) }
                ?: emptyList()
    }

    override fun hashCode() = if (id == 0L) super.hashCode() else id.hashCode()

    override fun equals(other: Any?) =
        id == (other as? PushMessage)?.id
}
