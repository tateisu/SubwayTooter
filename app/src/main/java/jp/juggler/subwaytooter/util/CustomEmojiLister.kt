package jp.juggler.subwaytooter.util

import android.content.Context
import android.os.Handler
import android.os.SystemClock
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.api.entity.parseList
import jp.juggler.subwaytooter.emoji.CustomEmoji
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.decodeJsonArray
import jp.juggler.util.data.decodeJsonObject
import jp.juggler.util.log.LogCategory
import jp.juggler.util.network.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class CustomEmojiLister(
    val context: Context,
    private val handler: Handler,
) {
    companion object {

        private val log = LogCategory("CustomEmojiLister")

        internal const val CACHE_MAX = 50

        internal const val ERROR_EXPIRE = 60000L * 5

        private val elapsedTime: Long
            get() = SystemClock.elapsedRealtime()
    }

    internal class CacheItem(
        val key: String,
        var list: List<CustomEmoji>,
        var listWithAliases: List<CustomEmoji>,
        var mapShortCode: Map<String, CustomEmoji>,
        // ロードした時刻
        var timeUpdate: Long = elapsedTime,
        // 参照された時刻
        var timeUsed: Long = timeUpdate,
    )

    internal class Request(
        val cont: Continuation<List<CustomEmoji>>,
        val accessInfo: SavedAccount,
        val reportWithAliases: Boolean = false,
    ) {
        val caller = Throwable()
        fun resume(item: CacheItem) {
            cont.resume(
                when (reportWithAliases) {
                    true -> item.listWithAliases
                    else -> item.list
                }
            )
        }
    }

    // 成功キャッシュ
    internal val cache = ConcurrentHashMap<String, CacheItem>()

    // エラーキャッシュ
    internal val cacheError = ConcurrentHashMap<String, Long>()

    private val cacheErrorItem = CacheItem(
        key = "error",
        list = emptyList(),
        listWithAliases = emptyList(),
        mapShortCode = emptyMap(),
    )

    // ロード要求
    internal val queue = ConcurrentLinkedQueue<Request>()

    private val worker = Worker()

    // ネットワーク接続が変化したらエラーキャッシュをクリア
    fun onNetworkChanged() {
        cacheError.clear()
    }

    private fun getCached(now: Long, accessInfo: SavedAccount) =
        getCached(now, accessInfo.apiHost.ascii)

    private fun getCached(now: Long, apiHostAscii: String?): CacheItem? {
        apiHostAscii ?: return null

        // 成功キャッシュ
        val item = cache[apiHostAscii]
        if (item != null && now - item.timeUpdate <= ERROR_EXPIRE) {
            item.timeUsed = now
            return item
        }

        // エラーキャッシュ
        val timeError = cacheError[apiHostAscii]
        if (timeError != null && now < timeError + ERROR_EXPIRE) {
            return cacheErrorItem
        }

        return null
    }

    // インスタンス用のカスタム絵文字のリストを取得する
    // または例外を投げる
    suspend fun getList(
        accessInfo: SavedAccount,
        withAliases: Boolean = false,
    ): List<CustomEmoji> {
        synchronized(cache) {
            getCached(elapsedTime, accessInfo)
        }?.let { return it.list }
        return suspendCoroutine { cont ->
            try {
                queue.add(Request(cont, accessInfo, reportWithAliases = withAliases))
                worker.notifyEx()
            } catch (ex: Throwable) {
                cont.resumeWithException(ex)
            }
        }
    }

    fun tryGetList(
        accessInfo: SavedAccount,
        withAliases: Boolean = false,
        callback: ((List<CustomEmoji>) -> Unit)? = null,
    ): List<CustomEmoji>? {
        synchronized(cache) {
            getCached(elapsedTime, accessInfo)
        }?.let { return it.list }
        launchMain {
            try {
                getList(accessInfo, withAliases).let { callback?.invoke(it) }
            } catch (ex: Throwable) {
                log.e(ex, "getList failed.")
            }
        }
        return null
    }

//    suspend fun getMap(accessInfo: SavedAccount) =
//        HashMap<String, CustomEmoji>().apply {
//            getList(accessInfo).forEach { put(it.shortcode, it) }
//        }

    fun getMapNonBlocking(accessInfo: SavedAccount): HashMap<String, CustomEmoji>? =
        tryGetList(accessInfo)?.let {
            HashMap<String, CustomEmoji>().apply {
                it.forEach { put(it.shortcode, it) }
            }
        }

    fun getCachedEmoji(apiHostAscii: String?, shortcode: String): CustomEmoji? {
        val cache = getCached(elapsedTime, apiHostAscii)
        if (cache == null) {
            log.w("getCachedEmoji: missing cache for $apiHostAscii")
            return null
        }
        val emoji = cache.mapShortCode[shortcode]
        if (emoji == null) {
            log.w("getCachedEmoji: missing emoji for $shortcode in $apiHostAscii")
            return null
        }
        return emoji
    }

    private inner class Worker : WorkerBase() {

        override fun cancel() {
            // このスレッドはキャンセルされない。プロセスが生きている限り動き続ける。
        }

        override suspend fun run() {
            while (true) {
                try {
                    // リクエストを取得する
                    val request = queue.poll()
                    if (request == null) {
                        // なければ待機
                        waitEx(86400000L)
                        continue
                    }
                    try {
                        request.resume(handleRequest(request))
                    } catch (ex: Throwable) {
                        log.e(request.caller, "caller's call stack")
                        request.cont.resumeWithException(ex)
                    }
                } catch (ex: Throwable) {
                    log.e(ex, "can't load custom emoji list.")
                    waitEx(3000L)
                }
            }
        }

        private suspend fun handleRequest(request: Request): CacheItem {
            synchronized(cache) {
                (getCached(elapsedTime, request.accessInfo)
                    ?.takeIf { it != cacheErrorItem })
                    .also {
                        if (it == null) {
                            // エラーキャッシュは一定時間で除去される
                            sweepCache()
                        }
                    }
            }?.let { return it }

            val accessInfo = request.accessInfo
            val cacheKey = accessInfo.apiHost.ascii

            // v12のmetaからemojisをパース
            suspend fun misskeyEmojis12(): List<CustomEmoji>? =
                App1.getHttpCachedString(
                    "https://$cacheKey/api/meta",
                    accessInfo = accessInfo
                ) { builder ->
                    builder.post(JsonObject().toRequestBody())
                }?.decodeJsonObject()
                    ?.jsonArray("emojis")
                    ?.let { parseList(it, CustomEmoji::decodeMisskey) }

            // v13のemojisを読む
            suspend fun misskeyEmojis13(): List<CustomEmoji>? =
                App1.getHttpCachedString(
                    "https://$cacheKey/api/emojis",
                    accessInfo = accessInfo,
                    misskeyPost = true,
                ) { builder ->
                    builder.post(JsonObject().toRequestBody())
                }
                    ?.decodeJsonObject()
                    ?.jsonArray("emojis")
                    ?.let { emojis13 ->
                        parseList(emojis13) {
                            CustomEmoji.decodeMisskey13(accessInfo.apiHost, it)
                        }
                    }

            // マストドンのカスタム絵文字一覧を読む
            suspend fun mastodonEmojis() =
                App1.getHttpCachedString(
                    "https://$cacheKey/api/v1/custom_emojis",
                    accessInfo = accessInfo
                )?.let { data ->
                    parseList(data.decodeJsonArray(), CustomEmoji::decodeMastodon)
                }

            val list = when {
                accessInfo.isMastodon -> mastodonEmojis()
                else -> misskeyEmojis12() ?: misskeyEmojis13()
            }?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.shortcode })

            val listWithAlias = list?.let { srcList ->
                ArrayList<CustomEmoji>(srcList).apply {
                    for (item in srcList) {
                        item.aliases
                            ?.filter { !it.equals(item.shortcode, ignoreCase = true) }
                            ?.forEach { add(item.makeAlias(it)) }
                    }
                }
            }?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.alias ?: it.shortcode })

            return synchronized(cache) {
                val now = elapsedTime
                if (list == null || listWithAlias == null) {
                    cacheError[cacheKey] = now
                    error("can't load custom emoji for ${accessInfo.apiHost}")
                } else {
                    val mapShortCode = buildMap {
                        list.forEach { put(it.alias ?: it.shortcode, it) }
                        listWithAlias.forEach { put(it.alias ?: it.shortcode, it) }
                    }
                    var item = cache[cacheKey]
                    if (item == null) {
                        item = CacheItem(cacheKey, list, listWithAlias, mapShortCode)
                        cache[cacheKey] = item
                    } else {
                        item.list = list
                        item.listWithAliases = listWithAlias
                        item.mapShortCode = mapShortCode
                        item.timeUpdate = now
                    }
                    item
                }
            }
        }

        // キャッシュの掃除
        private fun sweepCache() {
            // 超過してる数
            val over = cache.size - CACHE_MAX
            if (over <= 0) return

            // 古い要素を一時リストに集める
            // 昇順ソート
            // 古い物から順に捨てる
            val now = elapsedTime
            cache.entries
                .filter { now - it.value.timeUsed > 1000L }
                .sortedBy { it.value.timeUsed }
                .take(over)
                .forEach { cache.remove(it.key) }
        }
    }
}
