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
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.span.NetworkEmojiSpan
import jp.juggler.util.LogCategory
import java.io.ByteArrayInputStream
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

class CustomEmojiCache(internal val context : Context) {
	
	companion object {
		
		private val log = LogCategory("CustomEmojiCache")
		
		internal const val DEBUG = false
		internal const val CACHE_MAX = 512 // 使用中のビットマップは掃除しないので、頻度によってはこれより多くなることもある
		internal const val ERROR_EXPIRE = 60000L * 10
		
		private val elapsedTime : Long
			get() = SystemClock.elapsedRealtime()
	}
	
	private class CacheItem(val url : String, var frames : ApngFrames?) {
		var time_used : Long = elapsedTime
	}
	
	private class Request(
		val refTarget : WeakReference<Any>,
		val url : String,
		val onLoadComplete : () -> Unit
	)
	
	////////////////////////////////
	
	// 成功キャッシュ
	private val cache : ConcurrentHashMap<String, CacheItem>
	
	// エラーキャッシュ
	private val cache_error = ConcurrentHashMap<String, Long>()
	private val cache_error_item = CacheItem("error", null)
	
	// リクエストキュー
	// キャンセル操作の都合上、アクセス時に排他が必要
	private val queue = LinkedList<Request>()
	
	private val handler : Handler
	private val worker : Worker
	
	init {
		handler = Handler(context.mainLooper)
		cache = ConcurrentHashMap()
		worker = Worker()
		worker.start()
	}
	
	// カラムのリロードボタンを押したタイミングでエラーキャッシュをクリアする
	fun clearErrorCache() {
		cache_error.clear()
	}
	
	// tag_target を持つリクエストまたはtagがGCされたリクエストをキューから除去する
	fun cancelRequest(refTarget : WeakReference<Any>) {
		
		val targetTag = refTarget.get() ?: return
		
		synchronized(queue) {
			val it = queue.iterator()
			while(it.hasNext()) {
				val request = it.next()
				val tag = request.refTarget.get()
				if(tag === null || tag === targetTag) it.remove()
			}
		}
	}
	
	private fun getCached(now : Long, url : String) : CacheItem? {
		// 成功キャッシュ
		val item = cache[url]
		if(item != null) {
			item.time_used = now
			return item
		}
		
		// エラーキャッシュ
		val time_error = cache_error[url]
		if(time_error != null && now < time_error + ERROR_EXPIRE) {
			return cache_error_item
		}
		
		return null
	}
	
	fun getFrames(
		refDrawTarget : WeakReference<Any>?,
		url : String,
		onLoadComplete : () -> Unit
	) : ApngFrames? {
		try {
			if(refDrawTarget?.get() == null) {
				NetworkEmojiSpan.log.e("draw: DrawTarget is null ")
				return null
			}
			
			cancelRequest(refDrawTarget)
			
			synchronized(cache) {
				val item = getCached(elapsedTime, url)
				if(item != null) return item.frames
			}
			
			// キャンセル操作の都合上、排他が必要
			synchronized(queue) {
				queue.addLast(Request(refDrawTarget, url, onLoadComplete))
			}
			worker.notifyEx()
		} catch(ex : Throwable) {
			log.trace(ex)
			// たまにcache変数がなぜかnullになる端末があるらしい
		}
		return null
	}
	
	private inner class Worker : WorkerBase() {
		
		override fun cancel() {
			// このスレッドはプロセスが生きてる限りキャンセルされない
		}
		
		override fun run() {
			while(true) {
				try {
					var queue_size : Int
					val request = synchronized(queue) {
						val x = if(queue.isNotEmpty()) queue.removeFirst() else null
						queue_size = queue.size
						return@synchronized x
					}
					
					if(request == null) {
						if(DEBUG) log.d("wait")
						waitEx(86400000L)
						continue
					}
					
					// 描画先がGCされたなら何もしない
					request.refTarget.get() ?: continue
					
					var cache_size : Int = - 1
					if(synchronized(cache) {
							val now = elapsedTime
							val item = getCached(now, request.url)
							if(item != null) {
								if(item.frames != null) {
									fireCallback(request)
								}
								return@synchronized true
							}
							sweep_cache(now)
							cache_size = cache.size
							return@synchronized false
						}) continue
					
					if(DEBUG)
						log.d(
							"start get image. queue_size=%d, cache_size=%d url=%s",
							queue_size,
							cache_size,
							request.url
						)
					
					var frames : ApngFrames? = null
					try {
						val data = App1.getHttpCached(request.url)
						if(data == null) {
							log.e("get failed. url=%s", request.url)
						} else {
							frames = decodeAPNG(data, request.url)
						}
					} catch(ex : Throwable) {
						log.trace(ex)
					}
					
					synchronized(cache) {
						if(frames == null) {
							cache_error.put(request.url, elapsedTime)
						} else {
							// 古いキャッシュがある
							var item : CacheItem? = cache[request.url]
							if(item == null) {
								item = CacheItem(request.url, frames)
								cache[request.url] = item
							} else {
								item.frames?.dispose()
								item.frames = frames
							}
							fireCallback(request)
						}
					}
				} catch(ex : Throwable) {
					log.trace(ex)
					
					// Fujitsu F-01H（F01H）, 2048MB RAM, Android 6.0
					// java.lang.NullPointerException:
					// at java.util.concurrent.ConcurrentHashMap.get (ConcurrentHashMap.java:772)
					// at jp.juggler.subwaytooter.util.CustomEmojiCache$Worker.run (CustomEmojiCache.java:183)
					
					waitEx(3000L)
				}
			}
		}
		
		private fun fireCallback(request : Request) {
			handler.post { request.onLoadComplete() }
		}
		
		private fun sweep_cache(now : Long) {
			
			// キャッシュ限界を超過した数
			val over = cache.size - CACHE_MAX
			
			// 超過した数がある程度大きくなるまで掃除しない
			if(over <= 64) return
			
			// 掃除する候補
			val list = ArrayList<CacheItem>()
			for(item in cache.values) {
				// 最近使われていないものが掃除対象
				if(now - item.time_used > 1000L) list.add(item)
			}
			
			// 昇順ソート
			list.sortBy { it.time_used }
			
			// 古い物から順に捨てる
			var removed = 0
			for(item in list) {
				cache.remove(item.url)
				item.frames?.dispose()
				if(++ removed >= over) break
			}
		}
		
		
		private fun decodeAPNG(data : ByteArray, url : String) : ApngFrames? {
			try {
				// APNGをデコード
				val x = ApngFrames.parse(64){ ByteArrayInputStream(data) }
				if(x != null) return x
				// fall thru
			} catch(ex : Throwable) {
				if(DEBUG) log.trace(ex)
				log.e(ex, "PNG decode failed. %s ", url)
			}
			
			// 通常のビットマップでのロードを試みる
			try {
				val b = decodeBitmap(data, 128)
				if(b != null) {
					if(DEBUG) log.d("bitmap decoded.")
					return ApngFrames(b)
				} else {
					log.e("Bitmap decode returns null. %s", url)
				}
				
				// fall thru
			} catch(ex : Throwable) {
				log.e(ex, "Bitmap decode failed. %s", url)
			}
			
			// SVGのロードを試みる
			try {
				val b = decodeSVG(url,data, 128.toFloat())
				if(b != null) {
					if(DEBUG) log.d("SVG decoded.")
					return ApngFrames(b)
				}
				
				// fall thru
			} catch(ex : Throwable) {
				log.e(ex, "SVG decode failed. %s", url)
			}
			
			return null
		}
		
		private val options = BitmapFactory.Options()
		
		private fun decodeBitmap(data : ByteArray, pixel_max : Int) : Bitmap? {
			options.inJustDecodeBounds = true
			options.inScaled = false
			options.outWidth = 0
			options.outHeight = 0
			BitmapFactory.decodeByteArray(data, 0, data.size, options)
			var w = options.outWidth
			var h = options.outHeight
			if(w <= 0 || h <= 0) {
				log.e("can't decode bounds.")
				return null
			}
			var bits = 0
			while(w > pixel_max || h > pixel_max) {
				++ bits
				w = w shr 1
				h = h shr 1
			}
			options.inJustDecodeBounds = false
			options.inSampleSize = 1 shl bits
			return BitmapFactory.decodeByteArray(data, 0, data.size, options)
		}
		
		private fun decodeSVG(url:String, data : ByteArray, pixelMax : Float) : Bitmap? {
			try {
				val svg = SVG.getFromInputStream(ByteArrayInputStream(data))
				
				val src_w = svg.documentWidth // the width in pixels, or -1 if there is no width available.
				val src_h = svg.documentHeight // the height in pixels, or -1 if there is no height available.
				val aspect = if( src_w <= 0f || src_h <=0f){
					// widthやheightの情報がない
					1f
				}else{
					src_w / src_h
				}
				
				val dst_w : Float
				val dst_h : Float
				if(aspect >= 1f) {
					dst_w = pixelMax
					dst_h = pixelMax / aspect
				}else {
					dst_h = pixelMax
					dst_w = pixelMax * aspect
				}
				val w_ceil = ceil(dst_w)
				val h_ceil = ceil(dst_h)
				
				// Create a Bitmap to render our SVG to
				val b = Bitmap.createBitmap(w_ceil.toInt(), h_ceil.toInt(), Bitmap.Config.ARGB_8888)
				// Create a Canvas to use for rendering
				val canvas = Canvas(b)
				
				svg.renderToCanvas(
					canvas,
					if(aspect >= 1f) {
						RectF(0f, h_ceil-dst_h, dst_w, dst_h) // 後半はw,hを指定する
					} else {
						RectF(w_ceil-dst_w, 0f, dst_w , dst_h) // 後半はw,hを指定する
					}
				)
				return b
			} catch(ex : Throwable) {
				log.e(ex, "decodeSVG failed. $url")
			}
			return null
		}
	}
	
}
