package jp.juggler.subwaytooter.api

import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.table.SavedAccount
import java.util.concurrent.ConcurrentHashMap

object MisskeyAccountDetailMap {

    private class AccountKey(
        val dbId: Long,
        val id: EntityId,
    ) {

        override fun hashCode(): Int {
            val h1 = (dbId xor dbId.ushr(32)).toInt()
            val h2 = id.hashCode()
            return (h1 xor h2)
        }

        override fun equals(other: Any?): Boolean {
            return other is AccountKey && other.dbId == dbId && other.id == id
        }
    }

    private val accountMap = ConcurrentHashMap<AccountKey, TootAccount>()

    fun fromAccount(parser: TootParser, src: TootAccount, id: EntityId) {
        // SavedAccountが不明なら何もしない
        val access_info = parser.linkHelper as? SavedAccount ?: return

        // アカウントのjsonがフォロー数を含まないなら何もしない
        if ((src.followers_count ?: -1) < 0L) return

        val key = AccountKey(access_info.db_id, id)
        accountMap[key] = src
    }

    fun get(accessInfo: SavedAccount, id: EntityId): TootAccount? {
        return accountMap[AccountKey(accessInfo.db_id, id)]
    }
}
