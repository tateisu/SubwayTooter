package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import jp.juggler.subwaytooter.api.ApiPath
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.NotificationType.Companion.toNotificationType
import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.util.data.*
import jp.juggler.util.log.LogCategory
import jp.juggler.util.network.toPostRequestBuilder

class NotificationCache(
    private val account_db_id: Long,
) {

    private var id = -1L

    // サーバから通知を取得した時刻
    private var last_load = 0L

    // 通知のリスト
    var data = ArrayList<JsonObject>()

    companion object : TableCompanion {

        private val log = LogCategory("NotificationCache")

        override val table = "noti_cache"

        private const val COL_ID = BaseColumns._ID

        // アカウントDBの行ID。 サーバ側のIDではない
        private const val COL_ACCOUNT_DB_ID = "a"

        // サーバから通知を取得した時刻
        private const val COL_LAST_LOAD = "l"

        // サーバから最後に読んだデータ。既読は排除されてるかも
        private const val COL_DATA = "d"

        // サーバから最後に読んだデータ。既読は排除されてるかも
        private const val COL_SINCE_ID = "si" // 使わなくなった

        override fun onDBCreate(db: SQLiteDatabase) {

            db.execSQL(
                """
				create table if not exists $table
				($COL_ID INTEGER PRIMARY KEY
				,$COL_ACCOUNT_DB_ID integer not null
				,$COL_LAST_LOAD integer default 0
				,$COL_DATA text
				,$COL_SINCE_ID text
				)
				"""
            )
            db.execSQL(
                "create unique index if not exists ${table}_a on $table ($COL_ACCOUNT_DB_ID)"
            )
        }

        override fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 41 && newVersion >= 41) {
                onDBCreate(db)
                return
            }
        }

        private const val WHERE_AID = "$COL_ACCOUNT_DB_ID=?"

        private const val KEY_TIME_CREATED_AT = "<>KEY_TIME_CREATED_AT"

        fun getEntityOrderId(account: SavedAccount, src: JsonObject): EntityId =
            if (account.isMisskey) {
                // 今のMisskeyはIDをIDとして使っても問題ないのだが、
                // ST的には既読チェックの値の内容が大幅に変わると困るのだった
                when (val created_at = src.string("createdAt")) {
                    null -> EntityId.DEFAULT
                    else -> EntityId(TootStatus.parseTime(created_at).toString())
                }
            } else {
                EntityId.mayDefault(src.string("id"))
            }

        fun parseNotificationType(accessInfo: SavedAccount, src: JsonObject) =
            (when {
                accessInfo.isMisskey -> src.string("type")
                else -> src.string("type")
            } ?: "?").toNotificationType()

        private fun makeNotificationUrlMastodon(
            flags: Int,
            sinceId: EntityId?,
        ) = StringBuilder(ApiPath.PATH_NOTIFICATIONS).apply {
            // already contain "?limit=XX"
            if (sinceId != null) append("&since_id=$sinceId")

            fun noBit(v: Int, mask: Int) = (v and mask) != mask

            if (noBit(flags, 1)) append("&exclude_types[]=reblog")
            if (noBit(flags, 2)) append("&exclude_types[]=favourite")
            if (noBit(flags, 4)) append("&exclude_types[]=follow")
            if (noBit(flags, 8)) append("&exclude_types[]=mention")
            // if(noBit(flags,16)) /* mastodon has no reaction */
            if (noBit(flags, 32)) append("&exclude_types[]=poll")
        }.toString()

        fun parseNotificationTime(accessInfo: SavedAccount, src: JsonObject): Long =
            when {
                accessInfo.isMisskey -> TootStatus.parseTime(src.string("createdAt"))
                else -> TootStatus.parseTime(src.string("created_at"))
            }
    }

    class Access(val db: SQLiteDatabase) {

        fun resetLastLoad(dbId: Long) {
            try {
                val cv = ContentValues()
                cv.put(COL_LAST_LOAD, 0L)
                db.update(table, cv, WHERE_AID, arrayOf(dbId.toString()))
            } catch (ex: Throwable) {
                log.e(ex, "resetLastLoad(db_id) failed.")
            }
        }

        fun resetLastLoad() {
            try {
                val cv = ContentValues()
                cv.put(COL_LAST_LOAD, 0L)
                db.update(table, cv, null, null)
            } catch (ex: Throwable) {
                log.e(ex, "resetLastLoad() failed.")
            }
        }

        fun deleteCache(dbId: Long) {
            try {
                val cv = ContentValues()
                cv.put(COL_ACCOUNT_DB_ID, dbId)
                cv.put(COL_LAST_LOAD, 0L)
                cv.putNull(COL_DATA)
                db.replaceOrThrow(table, null, cv)
            } catch (ex: Throwable) {
                log.e(ex, "deleteCache failed.")
            }
        }

        fun save(item: NotificationCache) {
            try {
                item.run {
                    val cv = ContentValues()
                    cv.put(COL_ACCOUNT_DB_ID, account_db_id)
                    cv.put(COL_LAST_LOAD, last_load)
                    cv.put(COL_DATA, data.toJsonArray().toString())

                    val rv = db.replaceOrThrow(table, null, cv)
                    if (rv != -1L && id == -1L) id = rv
                }
            } catch (ex: Throwable) {
                log.e(ex, "save failed.")
            }
        }

        // load into item
        fun loadInto(item: NotificationCache) {
            try {
                item.run {
                    (db.query(
                        table,
                        null,
                        WHERE_AID,
                        arrayOf(account_db_id.toString()),
                        null,
                        null,
                        null
                    ) ?: error("null cursor")).use { cursor ->
                        if (!cursor.moveToFirst()) error("load: empty cursor.")
                        // 先にIDとlast_loadを読む
                        this.id = cursor.getLong(COL_ID)
                        this.last_load = cursor.getLong(COL_LAST_LOAD)
                        // データを読む箇所は失敗するかもしれない
                        cursor.getStringOrNull(COL_DATA)
                            ?.decodeJsonArray()
                            ?.objectList()
                            ?.let { data.addAll(it) }
                    }
                }
            } catch (ex: Throwable) {
                if (ex.message?.contains("empty cursor") == true) {
                    // アカウント追加直後に起きるはず
                    log.w(ex, "empty cursor. (maybe first loading)")
                } else {
                    log.e(ex, "load failed.")
                }
            }
        }

        fun inject(
            nc: NotificationCache,
            account: SavedAccount,
            list: List<TootNotification>,
        ) {
            try {
                val jsonList = list.map { it.json }
                jsonList.forEach { item ->
                    item[KEY_TIME_CREATED_AT] = parseNotificationTime(account, item)
                }
                nc.data.addAll(jsonList)
                nc.normalize(account)
            } catch (ex: Throwable) {
                log.e(ex, "inject failed.")
            } finally {
                save(nc)
            }
        }
    }

    private fun normalize(account: SavedAccount) {

        // 新しい順に並べる
        data.sortWith { a, b ->
            val la = a.optLong(KEY_TIME_CREATED_AT)
            val lb = b.optLong(KEY_TIME_CREATED_AT)
            when {
                la < lb -> 1
                la > lb -> -1
                else -> 0
            }
        }

        val typeCount = HashMap<String, Int>()
        val it = data.iterator()
        val duplicateMap = HashSet<EntityId>()
        while (it.hasNext()) {
            val item = it.next()

            val id = getEntityOrderId(account, item)
            if (id.isDefault) {
                it.remove()
                continue
            }

            // skip duplicated
            if (duplicateMap.contains(id)) {
                it.remove()
                continue
            }
            duplicateMap.add(id)

            val type = parseNotificationType(account, item)

            // 通知しないタイプなら取り除く
            if (!account.isNotificationEnabled(type)) {
                it.remove()
                continue
            }

            // 種類別に一定件数を保持する
            val count = 1 + (typeCount[type.code] ?: 0)
            if (count > 60) {
                it.remove()
                continue
            }
            typeCount[type.code] = count
        }
    }

    suspend fun requestAsync(
        dao: Access,
        client: TootApiClient,
        account: SavedAccount,
        flags: Int,
        onError: suspend (TootApiResult) -> Unit,
    ) {
        try {
            val now = System.currentTimeMillis()

            // 前回の更新から一定時刻が経過するまでは処理しない
            val remain = last_load + 120000L - now
            if (remain > 0) {
                log.w("${account.acct} requestAsync: skipped. remain=${remain}ms.")
                return
            }

            this.last_load = now

            // キャッシュ更新時は全データの最新データより新しいものを読みたい
            val newestId = filterLatestId(account) { true }

            val result = if (account.isMisskey) {
                client.request(
                    "/api/i/notifications",
                    account.putMisskeyApiToken().apply {
                        // MisskeyはsinceIdを指定すると未読範囲の古い方から読んでしまう
                        // sinceIdを指定しない

                        // 未読クリアフラグがデフォルトtrueなのをfalseにする
                        // https://github.com/misskey-dev/misskey/issues/8906
                        put("markAsRead", false)
                    }.toPostRequestBuilder()
                )
            } else {
                val path = makeNotificationUrlMastodon(flags, newestId)
                client.request(path)
            }

            if (result == null) {
                log.d("${account.acct} cancelled.")
                return
            }

            val array = result.jsonArray
            if (array != null) {
                daoAccountNotificationStatus.updateNotificationError(
                    account.acct,
                    null,
                )

                // データをマージする
                array.objectList().forEach { item ->
                    try {
                        item[KEY_TIME_CREATED_AT] = parseNotificationTime(account, item)
                        data.add(item)
                    } catch (ex: Throwable) {
                        log.e(ex, "${account.acct} parseNotificationTime failed.")
                    }
                }

                normalize(account)
                return
            } else {
                log.w("${account.acct} error. ${result.response?.code} ${result.error} ${result.requestInfo}")
                onError(result)
            }
        } catch (ex: Throwable) {
            log.e(ex, "${account.acct} requestAsync failed.")
        } finally {
            dao.save(this)
        }
    }

    inline fun filterLatestId(account: SavedAccount, predicate: (JsonObject) -> Boolean) =
        data
            .filter { predicate(it) }
            .mapNotNull { getEntityOrderId(account, it).takeIf { id -> !id.isDefault } }
            .reduceOrNull { a, b -> maxComparable(a, b) }

    //
    //
    //
    //	fun updatePost(post_id : EntityId, post_time : Long) {
    //		this.post_id = post_id
    //		this.post_time = post_time
    //		try {
    //			val cv = ContentValues()
    //			post_id.putTo(cv, COL_POST_ID)
    //			cv.put(COL_POST_TIME, post_time)
    //			val rows = db.update(table, cv, WHERE_AID, arrayOf(account_db_id.toString()))
    //			log.d(
    //				"updatePost account_db_id=%s,post=%s,%s last_data=%s,update_rows=%s"
    //				, account_db_id
    //				, post_id
    //				, post_time
    //				, last_data?.length
    //				, rows
    //			)
    //
    //		} catch(ex : Throwable) {
    //			log.e(ex, "updatePost failed.")
    //		}
    //
    //	}
}
