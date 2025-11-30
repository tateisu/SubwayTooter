package jp.juggler.subwaytooter.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Handler
import android.os.SystemClock
import com.caverock.androidsvg.SVG
import jp.juggler.apng.ApngFrames
import jp.juggler.apng.ApngFrames.Companion.scaleEmojiSize
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.pref.PrefS
import jp.juggler.subwaytooter.table.EmojiCacheDbOpenHelper
import jp.juggler.subwaytooter.table.daoImageAspect
import jp.juggler.util.coroutine.EmptyScope
import jp.juggler.util.data.clip
import jp.juggler.util.log.LogCategory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.lang.ref.WeakReference
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.max

class CustomEmojiCache(
    val context: Context,
    private val handler: Handler,
) {

    companion object {

        private val log = LogCategory("CustomEmojiCache")

        internal const val DEBUG = false
        internal const val CACHE_MAX = 512 // 使用中のビットマップは掃除しないので、頻度によってはこれより多くなることもある
        internal const val ERROR_EXPIRE = 60000L * 10

        private val elapsedTime: Long
            get() = SystemClock.elapsedRealtime()
    }

    private class CacheItem(val url: String, var frames: ApngFrames?) {
        var timeUsed: Long = elapsedTime
    }

    private class Request(
        val refTarget: WeakReference<Any>,
        val url: String,
        val onLoadComplete: (ApngFrames?) -> Unit,
    )

    // APNGデコード済のキャッシュデータ
    private val cache = ConcurrentHashMap<String, CacheItem>()

    // エラーキャッシュ
    private val cacheError = ConcurrentHashMap<String, Long>()
    private val cacheErrorItem = CacheItem("error", null)

    // リクエストキュー
    // キャンセル操作の都合上、アクセス時に排他が必要
    private val queue = LinkedList<Request>()

    private val emojiCacheDatabase = EmojiCacheDbOpenHelper(context)

    private var lastSweepDbCache = 0L

    private val workerLock = Channel<Unit>(capacity = 8)

    // 他の変数より後に初期化すること
    private val workers =
        (1..4).map { Worker(workerLock) }.toList()

    // ネットワーク接続が切り替わったタイミングでエラーキャッシュをクリアする
    fun onNetworkChanged() {
        cacheError.clear()
    }

    // カラムのリロードボタンを押したタイミングでエラーキャッシュをクリアする
    fun clearErrorCache() {
        cacheError.clear()
    }

    // tag_target を持つリクエストまたはtagがGCされたリクエストをキューから除去する
    fun cancelRequest(refTarget: WeakReference<Any>) {

        val targetTag = refTarget.get() ?: return

        synchronized(queue) {
            val it = queue.iterator()
            while (it.hasNext()) {
                val request = it.next()
                val tag = request.refTarget.get()
                if (tag === null || tag === targetTag) it.remove()
            }
        }
    }

    private fun getCached(now: Long, url: String): CacheItem? {

        // 成功キャッシュ
        val item = cache[url]
        if (item != null) {
            item.timeUsed = now
            return item
        }

        // エラーキャッシュ
        val timeError = cacheError[url]
        if (timeError != null && now < timeError + ERROR_EXPIRE) {
            return cacheErrorItem
        }

        return null
    }

    fun getFrames(
        refDrawTarget: WeakReference<Any>?,
        url: String,
        onLoadComplete: (ApngFrames?) -> Unit,
    ): ApngFrames? {
        try {
            if (refDrawTarget?.get() == null) {
                log.e("draw: DrawTarget is null ")
                return null
            }

            cancelRequest(refDrawTarget)

            // APNG frame cache
            synchronized(cache) {
                getCached(elapsedTime, url)?.let { return it.frames }
            }

            // キャンセル操作の都合上、排他が必要
            synchronized(queue) {
                queue.addLast(Request(refDrawTarget, url, onLoadComplete))
            }

            workers.first().notifyEx()
        } catch (ex: Throwable) {
            log.e(ex, "getFrames failed.")
            // たまにcache変数がなぜかnullになる端末があるらしい
        }
        return null
    }

    fun delete() {
        synchronized(cache) {
            for (entry in cache.entries) {
                entry.value.frames?.dispose()
            }
            cache.clear()
            cacheError.clear()
        }
        emojiCacheDatabase.deleteDatabase()
    }

    private inner class Worker(waiter: Channel<Unit>) : WorkerBase(waiter) {

        override fun cancel() {
            // このスレッドはプロセスが生きてる限りキャンセルされない
        }

        override suspend fun run() {
            var ts: Long
            var te: Long
            while (true) {
                try {
                    var queueSize: Int
                    val request = synchronized(queue) {
                        val x = if (queue.isNotEmpty()) queue.removeFirst() else null
                        queueSize = queue.size
                        x
                    }

                    if (request == null) {
                        if (DEBUG) log.d("wait")

                        synchronized(cache) {
                            val now = System.currentTimeMillis()
                            if (now - lastSweepDbCache >= TimeUnit.DAYS.toMillis(1)) {
                                lastSweepDbCache = now
                                emojiCacheDatabase.access { sweep(now) }
                            }
                        }

                        ts = elapsedTime
                        waitEx(86400000L)
                        te = elapsedTime
                        if (te - ts >= 200L) log.d("sleep ${te - ts}ms")
                        continue
                    }

                    // 描画先がGCされたなら何もしない
                    request.refTarget.get() ?: continue

                    ts = elapsedTime
                    var cacheSize: Int = -1
                    val cacheUsed = synchronized(cache) {
                        val now = elapsedTime
                        val item = getCached(now, request.url)
                        if (item != null) {
                            if (item.frames != null) {
                                fireCallback(request, item.frames)
                            }
                            return@synchronized true
                        }
                        sweepCache(now)
                        cacheSize = cache.size
                        return@synchronized false
                    }
                    te = elapsedTime
                    if (te - ts >= 200L) log.d("cache_used? ${te - ts}ms")

                    if (cacheUsed) continue

                    if (DEBUG) log.d("start get image. queue_size=$queueSize, cache_size=$cacheSize url=${request.url}")

                    val now = System.currentTimeMillis()

                    // データベースからロードしてみる
                    ts = elapsedTime
                    val dbCache = emojiCacheDatabase.access { load(request.url, now) }
                    te = elapsedTime
                    if (te - ts >= 200L) log.d("DbCache.load ${te - ts}ms")

                    var data = dbCache?.data

                    // データベースにblobがなければHTTPリクエスト
                    if (data == null) {
                        ts = elapsedTime
                        data = try {
                            App1.getHttpCached(request.url)
                        } catch (ex: Throwable) {
                            log.w("get failed. url=${request.url}")
                            null
                        }
                        te = elapsedTime
                        if (te - ts >= 200L) log.d("image get? ${te - ts}ms")

                        if (data != null) {
                            emojiCacheDatabase.access { update(request.url, data) }
                        }
                    }

                    ts = elapsedTime
                    val frames = try {
                        data?.let { decodeImage(it, request.url) }
                    } catch (ex: Throwable) {
                        log.e(ex, "decode failed.")
                        null
                    }
                    te = elapsedTime
                    if (te - ts >= 200L) log.d("image decode? ${te - ts}ms")

                    ts = elapsedTime
                    synchronized(cache) {
                        if (frames == null) {
                            cacheError[request.url] = elapsedTime
                        } else {
                            var item: CacheItem? = cache[request.url]
                            if (item == null) {
                                // 新しいキャッシュ項目
                                item = CacheItem(request.url, frames)
                                cache[request.url] = item
                            } else {
                                // 古いキャッシュを更新する
                                item.frames?.dispose()
                                item.frames = frames
                            }
                            fireCallback(request, item.frames)
                        }
                    }
                    te = elapsedTime
                    if (te - ts >= 200L) log.d("update_cache ${te - ts}ms")
                } catch (ex: Throwable) {
                    log.e(ex, "can't load custom emojis.")

                    // Fujitsu F-01H（F01H）, 2048MB RAM, Android 6.0
                    // java.lang.NullPointerException:
                    // at java.util.concurrent.ConcurrentHashMap.get (ConcurrentHashMap.java:772)
                    // at jp.juggler.subwaytooter.util.CustomEmojiCache$Worker.run (CustomEmojiCache.java:183)

                    waitEx(3000L)
                }
            }
        }

        private fun fireCallback(request: Request, frames: ApngFrames?) {
            handler.post { request.onLoadComplete(frames) }
            EmptyScope.launch {
                try {
                    frames?.aspect?.let { daoImageAspect.save(request.url, it) }
                } catch (ex: Throwable) {
                    log.e(ex, "aspect save failed.")
                }
            }
        }

        private fun sweepCache(now: Long) {

            // キャッシュ限界を超過した数
            val over = cache.size - CACHE_MAX

            // 超過した数がある程度大きくなるまで掃除しない
            if (over <= CACHE_MAX / 2) return

            // 掃除する候補
            val list = ArrayList<CacheItem>()
            for (item in cache.values) {
                // 最近使われていないものが掃除対象
                if (now - item.timeUsed > 1000L) list.add(item)
            }

            // 昇順ソート
            list.sortBy { it.timeUsed }

            // 古い物から順に捨てる
            var removed = 0
            for (item in list) {
                cache.remove(item.url)
                item.frames?.dispose()
                if (++removed >= over) break
            }
        }

        private fun decodeImage(data: ByteArray, url: String): ApngFrames? {
            val errors = ArrayList<Throwable>()

            val maxSize = PrefS.spEmojiPixels.toInt().clip(16, 1024).toFloat()

            try {
                // APNG,AWebP,AGIF
                val x = ApngFrames.parse(maxSize) { ByteArrayInputStream(data) }
                if (x != null) return x
                error("ApngFrames.parse returns null.")
            } catch (ex: Throwable) {
                if (DEBUG) log.e(ex, "decodeAPNG failed.")
                errors.add(ex)
            }

            // 通常のビットマップでのロードを試みる
            try {
                val b = decodeBitmap(data, maxSize)
                if (b != null) return ApngFrames(b)
                error("decodeBitmap returns null.")
            } catch (ex: Throwable) {
                if (DEBUG) log.e(ex, "decodeBitmap failed.")
                errors.add(ex)
            }

            // SVGのロードを試みる
            try {
                val b = decodeSVG(url, data, maxSize)
                if (b != null) return ApngFrames(b)
                error("decodeSVG returns null.")
            } catch (ex: Throwable) {
                if (DEBUG) log.e(ex, "decodeSVG failed.")
                errors.add(ex)
            }

            // 全部ダメだった
            log.e("decode failed. url=$url, errors=${
                errors.joinToString(", ") { "${it.javaClass} ${it.message}" }
            }")
            return null
        }

        private val options = BitmapFactory.Options()

        private fun decodeBitmap(
            data: ByteArray,
            maxPixels: Float,
        ): Bitmap? {
            options.inJustDecodeBounds = true
            options.inScaled = false
            options.outWidth = 0
            options.outHeight = 0
            BitmapFactory.decodeByteArray(data, 0, data.size, options)
            var srcW = options.outWidth
            var srcH = options.outHeight
            if (srcW <= 0 || srcH <= 0) error("decodeBitmap: can't decode bounds.")

            val (preferW, preferH) = scaleEmojiSize(
                srcW.toFloat(),
                srcH.toFloat(),
                maxPixels
            )
            var bits = 0
            while (srcW > preferW || srcW > preferH) {
                ++bits
                srcW = srcW shr 1
                srcH = srcH shr 1
            }
            options.inJustDecodeBounds = false
            options.inSampleSize = 1 shl bits
            return BitmapFactory.decodeByteArray(data, 0, data.size, options)
        }

        private fun decodeSVG(
            url: String,
            data: ByteArray,
            maxSize: Float,
        ): Bitmap? {
            try {
                val svg = SVG.getFromInputStream(ByteArrayInputStream(data))

                val (wDst, hDst) = scaleEmojiSize(
                    // the width in pixels, or -1 if there is no width available.
                    svg.documentWidth,
                    // the height in pixels, or -1 if there is no height available.
                    svg.documentHeight,
                    maxSize
                )
                val wCeil = max(1f, ceil(wDst))
                val hCeil = max(1f, ceil(hDst))

                // Create a Bitmap to render our SVG to
                val b = Bitmap.createBitmap(wCeil.toInt(), hCeil.toInt(), Bitmap.Config.ARGB_8888)
                // Create a Canvas to use for rendering
                val canvas = Canvas(b)

                svg.renderToCanvas(
                    canvas,
                    if (wDst >= hDst) {
                        RectF(0f, hCeil - hDst, wDst, hDst) // 後半はw,hを指定する
                    } else {
                        RectF(wCeil - wDst, 0f, wDst, hDst) // 後半はw,hを指定する
                    }
                )
                return b
            } catch (ex: Throwable) {
                log.e(ex, "decodeSVG failed. $url")
            }
            return null
        }
    }
}
