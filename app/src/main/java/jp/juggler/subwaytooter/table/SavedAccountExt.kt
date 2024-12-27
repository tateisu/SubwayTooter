package jp.juggler.subwaytooter.table

import android.content.Context
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.entity.InstanceCapability
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.util.data.b2i
import jp.juggler.util.log.LogCategory
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import java.util.concurrent.ConcurrentHashMap

private val log = LogCategory("SavedAccountExt")

/**
 * ニックネームをキャッシュするので、ソートする際に作り直すこと
 */
fun createAccountComparator() = object : Comparator<SavedAccount> {

    val mapNickname = ConcurrentHashMap<Acct, String>()

    val SavedAccount.nicknameCached
        get() = mapNickname.getOrPut(acct) { daoAcctColor.getNickname(acct) }

    override fun compare(a: SavedAccount, b: SavedAccount): Int {
        var i: Int

        // NA > !NA
        i = a.isNA.b2i() - b.isNA.b2i()
        if (i != 0) return i

        // pseudo > real
        i = a.isPseudo.b2i() - b.isPseudo.b2i()
        if (i != 0) return i

        val sa = a.nicknameCached
        val sb = b.nicknameCached
        return sa.compareTo(sb, ignoreCase = true)
    }
}

fun List<SavedAccount>.sortedByNickname() = sortedWith(createAccountComparator())

fun MutableList<SavedAccount>.sortInplaceByNickname() =
    sortWith(createAccountComparator())

fun accountListReorder(
    src: List<SavedAccount>,
    pickupHost: Host?,
    filter: (SavedAccount) -> Boolean = { true },
): MutableList<SavedAccount> {
    val listSameHost = java.util.ArrayList<SavedAccount>()
    val listOtherHost = java.util.ArrayList<SavedAccount>()
    for (a in src) {
        if (!filter(a)) continue
        when (pickupHost) {
            null, a.apDomain, a.apiHost -> listSameHost
            else -> listOtherHost
        }.add(a)
    }
    listSameHost.sortWith(createAccountComparator())
    listOtherHost.sortWith(createAccountComparator())
    listSameHost.addAll(listOtherHost)
    return listSameHost
}

// 疑似アカ以外のアカウントのリスト
fun accountListNonPseudo(
    pickupHost: Host?,
) = accountListReorder(
    daoSavedAccount.loadAccountList(),
    pickupHost
) { !it.isPseudo }

// 条件でフィルタする。サーバ情報を読む場合がある。
suspend fun Context.accountListWithFilter(
    pickupHost: Host?,
    check: suspend (TootApiClient, SavedAccount) -> Boolean,
): MutableList<SavedAccount>? {
    var resultList: MutableList<SavedAccount>? = null
    runApiTask { client ->
        supervisorScope {
            resultList = daoSavedAccount.loadAccountList()
                .map {
                    async {
                        try {
                            if (check(client, it)) it else null
                        } catch (ex: Throwable) {
                            log.e(ex, "accountListWithFilter failed.")
                            null
                        }
                    }
                }
                .mapNotNull { it.await() }
                .let { accountListReorder(it, pickupHost) }
        }
        if (client.isApiCancelled()) null else TootApiResult()
    }
    return resultList
}

suspend fun Context.accountListCanQuote(pickupHost: Host? = null) =
    accountListWithFilter(pickupHost) { client, a ->
        when {
            client.isApiCancelled() -> false
            a.isPseudo -> false
            a.isMisskey -> true
            else -> {
                val (ti, ri) = TootInstance.getEx(client.copy(), account = a)
                if (ti == null) {
                    ri?.error?.let { log.w(it) }
                    false
                } else InstanceCapability.quote(ti)
            }
        }
    }

suspend fun Context.accountListCanReaction(pickupHost: Host? = null) =
    accountListWithFilter(pickupHost) { client, a ->
        when {
            client.isApiCancelled() -> false
            a.isPseudo -> false
            a.isMisskey -> true
            else -> {
                val (ti, ri) = TootInstance.getEx(client.copy(), account = a)
                if (ti == null) {
                    ri?.error?.let { log.w(it) }
                    false
                } else InstanceCapability.canEmojiReaction(a, ti)
            }
        }
    }

suspend fun Context.accountListCanSeeMyReactions(pickupHost: Host? = null) =
    accountListWithFilter(pickupHost) { client, a ->
        when {
            client.isApiCancelled() -> false
            a.isPseudo -> false
            else -> {
                val (ti, ri) = TootInstance.getEx(client.copy(), account = a)
                if (ti == null) {
                    ri?.error?.let { log.w(it) }
                    false
                } else InstanceCapability.listMyReactions(a, ti)
            }
        }
    }
