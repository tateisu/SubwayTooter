package jp.juggler.subwaytooter.table

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootRelationShip
import jp.juggler.subwaytooter.global.appDatabase
import jp.juggler.util.data.*
import jp.juggler.util.log.LogCategory

class UserRelation {

    var following = false   // 認証ユーザからのフォロー状態にある
    var followed_by = false // 認証ユーザは被フォロー状態にある
    var blocking = false // 認証ユーザからブロックした
    var blocked_by = false // 認証ユーザからブロックされた(Misskeyのみ。Mastodonでは常にfalse)
    var muting = false
    var requested = false  // 認証ユーザからのフォローは申請中である
    var requested_by = false  // 相手から認証ユーザへのフォローリクエスト申請中(Misskeyのみ。Mastodonでは常にfalse)
    var following_reblogs = 0 // このユーザからのブーストをTLに表示する
    var endorsed = false // ユーザをプロフィールで紹介する
    var notifying = false // ユーザの投稿を通知する
    var note: String? = null

    // 認証ユーザからのフォロー状態
    fun getFollowing(who: TootAccount?): Boolean {
        return if (requested && !following && who != null && !who.locked) true else following
    }

    // 認証ユーザからのフォローリクエスト申請中状態
    fun getRequested(who: TootAccount?): Boolean {
        return if (requested && !following && who != null && !who.locked) false else requested
    }

    companion object : TableCompanion {

        const val REBLOG_HIDE =
            0 // don't show the boosts from target account will be shown on authorized user's home TL.
        const val REBLOG_SHOW =
            1 // show the boosts from target account will be shown on authorized user's home TL.
        const val REBLOG_UNKNOWN = 2 // not following, or instance don't support hide reblog.

        private val mMemoryCache = androidx.collection.LruCache<String, UserRelation>(2048)

        private val log = LogCategory("UserRelationMisskey")

        override val table = "user_relation_misskey"

        val columnList: ColumnMeta.List = ColumnMeta.List(table, 30).apply {
            createExtra = {
                arrayOf(
                    "create unique index if not exists ${table}_id on $table($COL_DB_ID,$COL_WHO_ID)",
                    "create index if not exists ${table}_time on $table($COL_TIME_SAVE)",
                )
            }
            deleteBeforeCreate = true
        }

        val COL_ID =
            ColumnMeta(columnList, 0, BaseColumns._ID, "INTEGER PRIMARY KEY", primary = true)
        private val COL_TIME_SAVE = ColumnMeta(columnList, 0, "time_save", "integer not null")

        // SavedAccount のDB_ID。 疑似アカウント用のエントリは -2L
        private val COL_DB_ID = ColumnMeta(columnList, 0, "db_id", "integer not null")

        // ターゲットアカウントのID
        val COL_WHO_ID = ColumnMeta(columnList, 0, "who_id", "text not null")
        private val COL_FOLLOWING = ColumnMeta(columnList, 0, "following", "integer not null")
        private val COL_FOLLOWED_BY = ColumnMeta(columnList, 0, "followed_by", "integer not null")
        private val COL_BLOCKING = ColumnMeta(columnList, 0, "blocking", "integer not null")
        private val COL_MUTING = ColumnMeta(columnList, 0, "muting", "integer not null")
        private val COL_REQUESTED = ColumnMeta(columnList, 0, "requested", "integer not null")
        private val COL_FOLLOWING_REBLOGS =
            ColumnMeta(columnList, 0, "following_reblogs", "integer not null")
        private val COL_ENDORSED = ColumnMeta(columnList, 32, "endorsed", "integer default 0")
        private val COL_BLOCKED_BY = ColumnMeta(columnList, 34, "blocked_by", "integer default 0")
        private val COL_REQUESTED_BY =
            ColumnMeta(columnList, 35, "requested_by", "integer default 0")
        private val COL_NOTE = ColumnMeta(columnList, 55, "note", "text default null")
        private val COL_NOTIFYING = ColumnMeta(columnList, 58, "notifying", "integer default 0")

        private const val DB_ID_PSEUDO = -2L

        override fun onDBCreate(db: SQLiteDatabase) =
            columnList.onDBCreate(db)

        override fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
            columnList.onDBUpgrade(db, oldVersion, newVersion)

        fun deleteOld(now: Long) {
            try {
                val expire = now - 86400000L * 365
                appDatabase.delete(table, "$COL_TIME_SAVE<?", arrayOf(expire.toString()))
            } catch (ex: Throwable) {
                log.e(ex, "deleteOld failed.")
            }

            try {
                // 旧型式のテーブルの古いデータの削除だけは時々回す
                val table = "user_relation"
                val COL_TIME_SAVE = "time_save"
                val expire = now - 86400000L * 365
                appDatabase.delete(table, "$COL_TIME_SAVE<?", arrayOf(expire.toString()))
            } catch (_: Throwable) {
            }
        }

        private fun key(dbId: Long, whoId: String) = "$dbId:$whoId"
        private fun key(dbId: Long, whoId: EntityId) = key(dbId, whoId.toString())

        private fun ContentValues.fromUserRelation(src: UserRelation) {
            put(COL_FOLLOWING, src.following)
            put(COL_FOLLOWED_BY, src.followed_by)
            put(COL_BLOCKING, src.blocking)
            put(COL_MUTING, src.muting)
            put(COL_REQUESTED, src.requested)
            put(COL_FOLLOWING_REBLOGS, src.following_reblogs)
            put(COL_ENDORSED, src.endorsed)
            put(COL_BLOCKED_BY, src.blocked_by)
            put(COL_REQUESTED_BY, src.requested_by)
            put(COL_NOTIFYING, src.notifying)
            put(COL_NOTE, src.note) // may null
        }

        private fun ContentValues.fromTootRelationShip(src: TootRelationShip) {
            put(COL_FOLLOWING, src.following)
            put(COL_FOLLOWED_BY, src.followed_by)
            put(COL_BLOCKING, src.blocking)
            put(COL_MUTING, src.muting)
            put(COL_REQUESTED, src.requested)
            put(COL_FOLLOWING_REBLOGS, src.showing_reblogs)
            put(COL_ENDORSED, src.endorsed)
            put(COL_BLOCKED_BY, src.blocked_by)
            put(COL_REQUESTED_BY, src.requested_by)
            put(COL_NOTIFYING, src.notifying)
            put(COL_NOTE, src.note) // may null
        }

        // マストドン用
        fun save1Mastodon(now: Long, dbId: Long, src: TootRelationShip): UserRelation {
            val id: String = src.id.toString()
            try {
                ContentValues().apply {
                    put(COL_TIME_SAVE, now)
                    put(COL_DB_ID, dbId)
                    put(COL_WHO_ID, id)
                    fromTootRelationShip(src)
                }.let { appDatabase.replaceOrThrow(table, null, it) }
                mMemoryCache.remove(key(dbId, id))
            } catch (ex: Throwable) {
                log.e(ex, "save failed.")
            }
            return load(dbId, id)
        }

        // マストドン用
        fun saveListMastodon(now: Long, dbId: Long, srcList: Iterable<TootRelationShip>) {
            val db = appDatabase
            db.execSQL("BEGIN TRANSACTION")

            val bOK = try {
                val cv = ContentValues()
                cv.put(COL_TIME_SAVE, now)
                cv.put(COL_DB_ID, dbId)
                for (src in srcList) {
                    val id = src.id.toString()
                    cv.put(COL_WHO_ID, id)
                    cv.fromTootRelationShip(src)
                    db.replaceOrThrow(table, null, cv)
                }
                true
            } catch (ex: Throwable) {
                log.e(ex, "saveList failed.")
                false
            }

            when {
                !bOK -> db.execSQL("ROLLBACK TRANSACTION")
                else -> {
                    db.execSQL("COMMIT TRANSACTION")
                    for (src in srcList) {
                        mMemoryCache.remove(key(dbId, src.id))
                    }
                }
            }
        }

        fun save1Misskey(now: Long, dbId: Long, whoId: String, src: UserRelation?) {
            src ?: return
            try {
                ContentValues().apply {
                    put(COL_TIME_SAVE, now)
                    put(COL_DB_ID, dbId)
                    put(COL_WHO_ID, whoId)
                    fromUserRelation(src)
                }.let { appDatabase.replaceOrThrow(table, null, it) }
                mMemoryCache.remove(key(dbId, whoId))
            } catch (ex: Throwable) {
                log.e(ex, "save failed.")
            }
        }

        fun saveListMisskey(
            now: Long,
            dbId: Long,
            srcList: List<Map.Entry<EntityId, UserRelation>>,
            start: Int,
            end: Int,
        ) {
            val db = appDatabase
            db.execSQL("BEGIN TRANSACTION")
            val bOK = try {
                val cv = ContentValues()
                cv.put(COL_TIME_SAVE, now)
                cv.put(COL_DB_ID, dbId)
                for (i in start until end) {
                    val entry = srcList[i]
                    val id = entry.key
                    val src = entry.value
                    cv.put(COL_WHO_ID, id.toString())
                    cv.fromUserRelation(src)
                    db.replaceOrThrow(table, null, cv)
                }
                true
            } catch (ex: Throwable) {
                log.e(ex, "saveList failed.")
                false
            }

            when {
                !bOK -> db.execSQL("ROLLBACK TRANSACTION")
                else -> {
                    db.execSQL("COMMIT TRANSACTION")
                    for (i in start until end) {
                        val entry = srcList[i]
                        val key = key(dbId, entry.key)
                        mMemoryCache.remove(key)
                    }
                }
            }
        }

        // Misskeyのリレーション取得APIから
        fun saveListMisskeyRelationApi(now: Long, dbId: Long, list: ArrayList<TootRelationShip>) {
            val db = appDatabase
            db.execSQL("BEGIN TRANSACTION")
            val bOK = try {
                val cv = ContentValues()
                cv.put(COL_TIME_SAVE, now)
                cv.put(COL_DB_ID, dbId)
                for (src in list) {
                    val id = src.id.toString()
                    cv.put(COL_WHO_ID, id)
                    cv.fromTootRelationShip(src)
                    db.replace(table, null, cv)
                }
                true
            } catch (ex: Throwable) {
                log.e(ex, "saveListMisskeyRelationApi failed.")
                false
            }
            when {
                !bOK -> db.execSQL("ROLLBACK TRANSACTION")
                else -> {
                    db.execSQL("COMMIT TRANSACTION")
                    for (src in list) {
                        mMemoryCache.remove(key(dbId, src.id))
                    }
                }
            }
        }

        private val loadWhere = "$COL_DB_ID=? and $COL_WHO_ID=?"

        private val loadWhereArg = object : ThreadLocal<Array<String?>>() {
            override fun initialValue(): Array<String?> = Array(2) { null }
        }

        fun load(dbId: Long, whoId: EntityId): UserRelation {
            //
            val key = key(dbId, whoId)
            val cached: UserRelation? = mMemoryCache.get(key)
            if (cached != null) return cached

            val dst = load(dbId, whoId.toString())

            mMemoryCache.put(key, dst)
            return dst
        }

        fun load(dbId: Long, whoId: String): UserRelation {

            try {
                val where_arg = loadWhereArg.get() ?: arrayOfNulls<String?>(2)
                where_arg[0] = dbId.toString()
                where_arg[1] = whoId
                appDatabase.query(table, null, loadWhere, where_arg, null, null, null)
                    .use { cursor ->
                        if (cursor.moveToNext()) {
                            val dst = UserRelation()
                            dst.following = cursor.getBoolean(COL_FOLLOWING)
                            dst.followed_by = cursor.getBoolean(COL_FOLLOWED_BY)
                            dst.blocking = cursor.getBoolean(COL_BLOCKING)
                            dst.muting = cursor.getBoolean(COL_MUTING)
                            dst.requested = cursor.getBoolean(COL_REQUESTED)
                            dst.following_reblogs = cursor.getInt(COL_FOLLOWING_REBLOGS)
                            dst.endorsed = cursor.getBoolean(COL_ENDORSED)
                            dst.blocked_by = cursor.getBoolean(COL_BLOCKED_BY)
                            dst.requested_by = cursor.getBoolean(COL_REQUESTED_BY)
                            dst.notifying = cursor.getBoolean(COL_NOTIFYING)

                            dst.note = cursor.getStringOrNull(COL_NOTE)
                            return dst
                        }
                    }
            } catch (ex: Throwable) {
                log.e(ex, "load failed.")
            }
            return UserRelation()
        }

        // MisskeyはUserエンティティにユーザリレーションが含まれたり含まれなかったりする
        fun fromAccount(parser: TootParser, src: JsonObject, id: EntityId) {

            // アカウントのjsonがユーザリレーションを含まないなら何もしない
            src["isFollowing"] ?: return

            // プロフカラムで ユーザのプロフ(A)とアカウントTL(B)を順に取得すると
            // (A)ではisBlockingに情報が入っているが、(B)では情報が入っていない
            // 対策として(A)でリレーションを取得済みのユーザは(B)のタイミングではリレーションを読み捨てる

            val map = parser.misskeyUserRelationMap
            if (map.containsKey(id)) return

            map[id] = UserRelation().apply {
                following = src.optBoolean("isFollowing")
                followed_by = src.optBoolean("isFollowed")
                muting = src.optBoolean("isMuted")
                blocking = src.optBoolean("isBlocking")
                blocked_by = src.optBoolean("isBlocked")
                endorsed = false
                requested = src.optBoolean("hasPendingFollowRequestFromYou")
                requested_by = src.optBoolean("hasPendingFollowRequestToYou")
            }
        }

        fun loadPseudo(acct: Acct) = load(DB_ID_PSEUDO, acct.ascii)

        fun createCursorPseudo(): Cursor =
            appDatabase.query(
                table,
                arrayOf(COL_ID.name, COL_WHO_ID.name),
                "$COL_DB_ID=$DB_ID_PSEUDO and ( $COL_MUTING=1 or $COL_BLOCKING=1 )",
                null,
                null,
                null,
                "$COL_WHO_ID asc"
            )

        fun deletePseudo(rowId: Long) {
            try {
                appDatabase.delete(table, "$COL_ID=$rowId", null)
            } catch (ex: Throwable) {
                log.e(ex, "deletePseudo failed. rowId=$rowId")
            }
        }
    }

    fun savePseudo(acct: String) =
        save1Misskey(System.currentTimeMillis(), DB_ID_PSEUDO, acct, this)
}
