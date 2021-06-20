package jp.juggler.subwaytooter.api

import jp.juggler.subwaytooter.api.entity.ServiceType
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootAccountRef
import java.util.concurrent.ConcurrentHashMap

object TootAccountMap {

    private fun getKey(parser: TootParser, who: TootAccount): String {
        val acct = parser.getFullAcct(who.acct)
        val watcher = when (parser.serviceType) {
            ServiceType.TOOTSEARCH -> "?tootsearch"
            ServiceType.MSP -> "?msp"
            ServiceType.NOTESTOCK -> "?notestock"
            else -> requireNotNull(parser.apDomain).ascii
        }
        return "$acct!$watcher"
    }

    private val accountMap = ConcurrentHashMap<Int, TootAccount>()
    private var serialSeed = Integer.MIN_VALUE
    private val accountEnum = HashMap<String, Int>()

    fun register(parser: TootParser, who: TootAccount): Int {
        val key = getKey(parser, who)
        val id = synchronized(accountEnum) {
            accountEnum[key] ?: (++serialSeed).also { accountEnum[key] = it }
        }
        accountMap[id] = who
        return id
    }

    fun find(mapId: Int) = accountMap[mapId]!!
    fun find(ref: TootAccountRef) = accountMap[ref.mapId]!!
}
