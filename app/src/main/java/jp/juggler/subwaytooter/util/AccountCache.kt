package jp.juggler.subwaytooter.util

import android.content.Context
import android.os.SystemClock
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.auth.authRepo
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.runApiTask2
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.log.LogCategory
import jp.juggler.util.network.toPostRequestBuilder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object AccountCache {
    private val log = LogCategory("AccountCache")

    private class CacheItem(val timeCached: Long, val result: TootAccount?)

    private val cacheMutex = Mutex()
    private val map = HashMap<String, CacheItem>()
    private const val CACHE_EXPIRE_SUCCESS = 300_000L
    private const val CACHE_EXPIRE_ERROR = 120_000L

    suspend fun load(context: Context, src: SavedAccount?): TootAccount? {
        src ?: return null
        return withContext(AppDispatchers.IO) {
            cacheMutex.withLock {
                val key = src.acct.ascii
                try {
                    val cached = map[key]
                    if (cached != null) {
                        val now = SystemClock.elapsedRealtime()
                        val expire =
                            if (cached.result != null) CACHE_EXPIRE_SUCCESS else CACHE_EXPIRE_ERROR
                        if (cached.timeCached >= now - expire) return@withLock cached.result
                    }
                    val newAccount = context.runApiTask2(src) { client ->
                        val json = if (src.isMisskey) {
                            val result = client.request(
                                "/api/i",
                                src.putMisskeyApiToken().toPostRequestBuilder()
                            ) ?: return@runApiTask2 null
                            result.error?.let { error(it) }
                            result.jsonObject
                        } else {
                            // 承認待ち状態のチェック
                            authRepo.checkConfirmed(src, client)

                            val result = client.request(
                                "/api/v1/accounts/verify_credentials"
                            ) ?: return@runApiTask2 null
                            result.error?.let { error(it) }
                            result.jsonObject
                        }
                        TootParser(this, src).account(json) ?: error("parse error.")
                    }
                    map[key] = CacheItem(SystemClock.elapsedRealtime(), newAccount)
                    return@withLock newAccount
                } catch (ex: Throwable) {
                    log.e(ex, "failed.")
                    map[key] = CacheItem(SystemClock.elapsedRealtime(), null)
                    return@withLock null
                }
            }
        }
    }
}
