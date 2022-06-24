package jp.juggler.subwaytooter.util

import android.content.Context
import android.os.Handler
import android.os.SystemClock
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.api.entity.parseList
import jp.juggler.subwaytooter.emoji.CustomEmoji
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.*
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

    private val cacheErrorItem = CacheItem("error", emptyList(), emptyList())

    // ロード要求
    internal val queue = ConcurrentLinkedQueue<Request>()

    private val worker = Worker()

    // ネットワーク接続が変化したらエラーキャッシュをクリア
    fun onNetworkChanged() {
        cacheError.clear()
    }

    private fun getCached(now: Long, accessInfo: SavedAccount): CacheItem? {
        val host = accessInfo.apiHost.ascii

        // 成功キャッシュ
        val item = cache[host]
        if (item != null && now - item.timeUpdate <= ERROR_EXPIRE) {
            item.timeUsed = now
            return item
        }

        // エラーキャッシュ
        val timeError = cacheError[host]
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
                log.trace(ex, "getList failed.")
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
                        log.trace(request.caller, "caller's call stack")
                        request.cont.resumeWithException(ex)
                    }
                } catch (ex: Throwable) {
                    log.trace(ex)
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
            val data = if (accessInfo.isMisskey) {
                App1.getHttpCachedString(
                    "https://$cacheKey/api/meta",
                    accessInfo = accessInfo
                ) { builder ->
                    builder.post(JsonObject().toRequestBody())
                }
            } else {
                App1.getHttpCachedString(
                    "https://$cacheKey/api/v1/custom_emojis",
                    accessInfo = accessInfo
                )
            }
            var list: List<CustomEmoji>? = null
            var listWithAlias: List<CustomEmoji>? = null
            if (data != null) {
                val a = decodeEmojiList(data, accessInfo)
                list = a
                listWithAlias = makeListWithAlias(a)
            }
            return synchronized(cache) {
                val now = elapsedTime
                if (list == null || listWithAlias == null) {
                    cacheError[cacheKey] = now
                    error("can't load custom emoji for ${accessInfo.apiHost}")
                } else {
                    var item = cache[cacheKey]
                    if (item == null) {
                        item = CacheItem(cacheKey, list, listWithAlias)
                        cache[cacheKey] = item
                    } else {
                        item.list = list
                        item.listWithAliases = listWithAlias
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
            val now = elapsedTime
            val list = ArrayList<CacheItem>(over)
            for (item in cache.values) {
                if (now - item.timeUsed > 1000L) list.add(item)
            }

            // 昇順ソート
            list.sortBy { it.timeUsed }

            // 古い物から順に捨てる
            var removed = 0
            for (item in list) {
                cache.remove(item.key)
                if (++removed >= over) break
            }
        }

        private fun decodeEmojiList(
            data: String,
            accessInfo: SavedAccount,
        ): List<CustomEmoji> =
            if (accessInfo.isMisskey) {
                parseList(
                    CustomEmoji.decodeMisskey,
                    accessInfo.apDomain,
                    data.decodeJsonObject().jsonArray("emojis")
                )
            } else {
                parseList(
                    CustomEmoji.decode,
                    accessInfo.apDomain,
                    data.decodeJsonArray()
                )
            }.apply {
                sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.shortcode })
            }

        private fun makeListWithAlias(
            list: List<CustomEmoji>,
        ) = ArrayList<CustomEmoji>().apply {
            addAll(list)
            for (item in list) {
                val aliases = item.aliases ?: continue
                for (alias in aliases) {
                    if (alias.equals(item.shortcode, ignoreCase = true)) continue
                    add(item.makeAlias(alias))
                }
            }
            sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.alias ?: it.shortcode })
        }
    }
}
