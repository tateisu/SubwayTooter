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
import jp.juggler.util.data.*
import jp.juggler.util.log.LogCategory

class UserRelation(
    var id: Long = 0L,
    var timeSave: Long = 0L,
    // SavedAccount のDB_ID。 疑似アカウント用のエントリは -2L
    var dbId: Long = 0L,
    // ターゲットアカウントのID
    var whoId: String = "",
    // 認証ユーザからのフォロー状態にある
    var following: Boolean = false,
    // 認証ユーザは被フォロー状態にある
    var followed_by: Boolean = false,
    // 認証ユーザからブロックした
    var blocking: Boolean = false,
    // 認証ユーザからブロックされた(Misskeyのみ。Mastodonでは常にfalse)
    var blocked_by: Boolean = false,
    var muting: Boolean = false,
    // 認証ユーザからのフォローは申請中である
    var requested: Boolean = false,
    // 相手から認証ユーザへのフォローリクエスト申請中(Misskeyのみ。Mastodonでは常にfalse)
    var requested_by: Boolean = false,
    // このユーザからのブーストをTLに表示する
    var following_reblogs: Int = 0,
    // ユーザをプロフィールで紹介する
    var endorsed: Boolean = false,
    // ログインユーザが該当ユーザに対して何かメモができる
    var note: String? = null,
    // ユーザの投稿を通知する
    var notifying: Boolean = false,
) {
    // 認証ユーザからのフォロー状態
    fun getFollowing(who: TootAccount?): Boolean {
        return if (requested && !following && who != null && !who.locked) true else following
    }

    // 認証ユーザからのフォローリクエスト申請中状態
    fun getRequested(who: TootAccount?): Boolean {
        return if (requested && !following && who != null && !who.locked) false else requested
    }

    companion object : TableCompanion {
        private val log = LogCategory("UserRelation")
        override val table = "user_relation_misskey"

        private const val COL_ID = BaseColumns._ID
        private const val COL_TIME_SAVE = "time_save"
        private const val COL_DB_ID = "db_id"
        private const val COL_WHO_ID = "who_id"
        private const val COL_FOLLOWING = "following"
        private const val COL_FOLLOWED_BY = "followed_by"
        private const val COL_BLOCKING = "blocking"
        private const val COL_MUTING = "muting"
        private const val COL_REQUESTED = "requested"
        private const val COL_FOLLOWING_REBLOGS = "following_reblogs"
        private const val COL_ENDORSED = "endorsed"
        private const val COL_BLOCKED_BY = "blocked_by"
        private const val COL_REQUESTED_BY = "requested_by"
        private const val COL_NOTE = "note"
        private const val COL_NOTIFYING = "notifying"

        val columnList= MetaColumns(table, 30).apply {
            column( 0, COL_ID, "INTEGER PRIMARY KEY")
            column( 0, COL_TIME_SAVE, "integer not null")
            column( 0, COL_DB_ID, "integer not null")
            column( 0, COL_WHO_ID, "text not null")
            column( 0, COL_FOLLOWING, "integer not null")
            column( 0, COL_FOLLOWED_BY, "integer not null")
            column( 0, COL_BLOCKING, "integer not null")
            column( 0, COL_MUTING, "integer not null")
            column( 0, COL_REQUESTED, "integer not null")
            column( 0, COL_FOLLOWING_REBLOGS, "integer not null")
            column( 32, COL_ENDORSED, "integer default 0")
            column( 34, COL_BLOCKED_BY, "integer default 0")
            column( 35, COL_REQUESTED_BY, "integer default 0")
            column( 55, COL_NOTE, "text default null")
            column( 58, COL_NOTIFYING, "integer default 0")
            createExtra = {
                arrayOf(
                    "create unique index if not exists ${table}_id on $table($COL_DB_ID,$COL_WHO_ID)",
                    "create index if not exists ${table}_time on $table($COL_TIME_SAVE)",
                )
            }
            deleteBeforeCreate = true
        }

        override fun onDBCreate(db: SQLiteDatabase) =
            columnList.onDBCreate(db)

        override fun onDBUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
            columnList.onDBUpgrade(db, oldVersion, newVersion)

        const val DB_ID_PSEUDO = -2L

        const val REBLOG_HIDE =
            0 // don't show the boosts from target account will be shown on authorized user's home TL.
        const val REBLOG_SHOW =
            1 // show the boosts from target account will be shown on authorized user's home TL.
        const val REBLOG_UNKNOWN = 2 // not following, or instance don't support hide reblog.

        private val mMemoryCache = androidx.collection.LruCache<String, UserRelation>(2048)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    class ColIdx(cursor: Cursor) {
        val idxId = cursor.getColumnIndexOrThrow(COL_ID)
        val idxTimeSave = cursor.getColumnIndexOrThrow(COL_TIME_SAVE)
        val idxDbId = cursor.getColumnIndexOrThrow(COL_DB_ID)
        val idxWhoId = cursor.getColumnIndexOrThrow(COL_WHO_ID)
        val idxFollowing = cursor.getColumnIndexOrThrow(COL_FOLLOWING)
        val idxFollowedBy = cursor.getColumnIndexOrThrow(COL_FOLLOWED_BY)
        val idxBlocking = cursor.getColumnIndexOrThrow(COL_BLOCKING)
        val idxMuting = cursor.getColumnIndexOrThrow(COL_MUTING)
        val idxRequested = cursor.getColumnIndexOrThrow(COL_REQUESTED)
        val idxFollowingReblogs = cursor.getColumnIndexOrThrow(COL_FOLLOWING_REBLOGS)
        val idxEndorsed = cursor.getColumnIndexOrThrow(COL_ENDORSED)
        val idxBlockedBy = cursor.getColumnIndexOrThrow(COL_BLOCKED_BY)
        val idxRequestedBy = cursor.getColumnIndexOrThrow(COL_REQUESTED_BY)
        val idxNote = cursor.getColumnIndexOrThrow(COL_NOTE)
        val idxNotifying = cursor.getColumnIndexOrThrow(COL_NOTIFYING)
        fun readRow(cursor: Cursor) = UserRelation(
            id = cursor.getLong(idxId),
            timeSave = cursor.getLong(idxTimeSave),
            dbId = cursor.getLong(idxDbId),
            whoId = cursor.getString(idxWhoId),
            following = cursor.getBoolean(idxFollowing),
            followed_by = cursor.getBoolean(idxFollowedBy),
            blocking = cursor.getBoolean(idxBlocking),
            muting = cursor.getBoolean(idxMuting),
            requested = cursor.getBoolean(idxRequested),
            following_reblogs = cursor.getInt(idxFollowingReblogs),
            endorsed = cursor.getBoolean(idxEndorsed),
            blocked_by = cursor.getBoolean(idxBlockedBy),
            requested_by = cursor.getBoolean(idxRequestedBy),
            note = cursor.getStringOrNull(idxNote),
            notifying = cursor.getBoolean(idxNotifying),
        )

        fun readOne(cursor: Cursor) =
            when {
                cursor.moveToNext() -> readRow(cursor)
                else -> null
            }

        fun readAll(cursor: Cursor) = buildList {
            while (cursor.moveToNext()) {
                add(readRow(cursor))
            }
        }
    }

    class Access(val db: SQLiteDatabase) {
        fun deleteOld(now: Long) {
            try {
                val expire = now - 86400000L * 365
                db.delete(table, "$COL_TIME_SAVE<?", arrayOf(expire.toString()))
            } catch (ex: Throwable) {
                log.e(ex, "deleteOld failed.")
            }

            try {
                // 旧型式のテーブルの古いデータの削除だけは時々回す
                val table = "user_relation"
                val COL_TIME_SAVE = "time_save"
                val expire = now - 86400000L * 365
                db.delete(table, "$COL_TIME_SAVE<?", arrayOf(expire.toString()))
            } catch (_: Throwable) {
            }
        }

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

        private fun key(dbId: Long, whoId: String) = "$dbId:$whoId"
        private fun key(dbId: Long, whoId: EntityId) = key(dbId, whoId.toString())

        fun save1Mastodon(now: Long, dbId: Long, src: TootRelationShip): UserRelation {
            val whoIdString: String = src.id.toString()
            try {
                ContentValues().apply {
                    fromTootRelationShip(src)
                    put(COL_TIME_SAVE, now)
                    put(COL_DB_ID, dbId)
                    put(COL_WHO_ID, whoIdString)
                }.replaceTo(db, table)
                mMemoryCache.remove(key(dbId, whoIdString))
            } catch (ex: Throwable) {
                log.e(ex, "save failed.")
            }
            return load(dbId, whoIdString)
        }

        fun saveListMastodon(now: Long, dbId: Long, srcList: Iterable<TootRelationShip>) {
            db.execSQL("BEGIN TRANSACTION")
            val bOK = try {
                val cv = ContentValues().apply {
                    put(COL_TIME_SAVE, now)
                    put(COL_DB_ID, dbId)
                }
                for (src in srcList) {
                    cv.fromTootRelationShip(src)
                    cv.put(COL_WHO_ID, src.id.toString())
                    cv.replaceTo(db, table)
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
                    fromUserRelation(src)
                    put(COL_TIME_SAVE, now)
                    put(COL_DB_ID, dbId)
                    put(COL_WHO_ID, whoId)
                }.replaceTo(db, table)
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
            db.execSQL("BEGIN TRANSACTION")
            val bOK = try {
                val cv = ContentValues().apply {
                    put(COL_TIME_SAVE, now)
                    put(COL_DB_ID, dbId)
                }
                for (i in start until end) {
                    val entry = srcList[i]
                    val whoId = entry.key.toString()
                    val src = entry.value
                    cv.fromUserRelation(src)
                    cv.put(COL_WHO_ID, whoId)
                    cv.replaceTo(db, table)
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
            db.execSQL("BEGIN TRANSACTION")
            val bOK = try {
                val cv = ContentValues().apply {
                    put(COL_TIME_SAVE, now)
                    put(COL_DB_ID, dbId)
                }
                for (src in list) {
                    cv.fromTootRelationShip(src)
                    cv.put(COL_WHO_ID, src.id.toString())
                    cv.replaceTo(db, table)
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

        fun saveUserRelation(a: SavedAccount, src: TootRelationShip?): UserRelation? {
            src ?: return null
            val now = System.currentTimeMillis()
            return save1Mastodon(now, a.db_id, src)
        }

        fun saveUserRelationMisskey(
            a: SavedAccount,
            whoId: EntityId,
            parser: TootParser,
        ): UserRelation? {
            val now = System.currentTimeMillis()
            val relation = parser.getMisskeyUserRelation(whoId)
            save1Misskey(now, a.db_id, whoId.toString(), relation)
            return relation
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
                db.rawQuery(
                    "select * from $table where $COL_DB_ID=? and $COL_WHO_ID=?",
                    arrayOf(dbId.toString(), whoId)
                )?.use { ColIdx(it).readOne(it) }
                    ?.let { return it }
            } catch (ex: Throwable) {
                log.e(ex, "load failed.")
            }
            return UserRelation()
        }

        /**
         * srcの情報をparser内部のmisskeyUserRelationMapに追加する
         * - MisskeyはUserエンティティにユーザリレーションが含まれたり含まれなかったりする
         */
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

        fun savePseudo(acct: String, src: UserRelation) =
            save1Misskey(System.currentTimeMillis(), DB_ID_PSEUDO, acct, src)

        fun deletePseudo(rowId: Long) {
            try {
                db.deleteById(table, rowId.toString(), COL_ID)
            } catch (ex: Throwable) {
                log.e(ex, "deletePseudo failed. rowId=$rowId")
            }
        }

        fun loadPseudo(acct: Acct) = load(DB_ID_PSEUDO, acct.ascii)

        fun listPseudoMuted() =
            db.rawQuery(
                "select $COL_ID,$COL_WHO_ID from $table where $COL_DB_ID=$DB_ID_PSEUDO and ($COL_MUTING=1 or $COL_BLOCKING=1) order by $COL_WHO_ID asc",
                emptyArray()
            )?.use {
                ColIdx(it).readAll(it)
            } ?: emptyList()
    }
}
