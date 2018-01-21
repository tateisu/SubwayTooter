package jp.juggler.subwaytooter.util

import android.content.Context
import android.os.Handler
import android.os.SystemClock

import java.util.ArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.api.entity.CustomEmoji
import jp.juggler.subwaytooter.api.entity.parseList

class CustomEmojiLister(internal val context : Context) {
	
	companion object {
		
		private val log = LogCategory("CustomEmojiLister")
		
		internal const val CACHE_MAX = 50
		
		internal const val ERROR_EXPIRE = 60000L * 5
		
		private val elapsedTime : Long
			get() = SystemClock.elapsedRealtime()
	}
	
	internal class CacheItem(val instance : String, var list : ArrayList<CustomEmoji>?) {
		
		// 参照された時刻
		var time_used : Long = 0
		
		// ロードした時刻
		var time_update : Long = 0
		
		init {
			time_update = elapsedTime
			time_used = time_update
		}
	}
	
	internal class Request(
		val instance : String,
		val onListLoaded : (list : ArrayList<CustomEmoji>) -> Unit?
	)
	
	// 成功キャッシュ
	internal val cache = ConcurrentHashMap<String, CacheItem>()
	
	// エラーキャッシュ
	internal val cache_error = ConcurrentHashMap<String, Long>()
	
	private val cache_error_item = CacheItem("error", null)
	
	// ロード要求
	internal val queue = ConcurrentLinkedQueue<Request>()
	
	private val handler : Handler
	
	private val worker : Worker
	
	init {
		this.handler = Handler(context.mainLooper)
		this.worker = Worker()
		worker.start()
	}
	
	private fun getCached(now : Long, instance : String) : CacheItem? {
		
		// 成功キャッシュ
		val item = cache[instance]
		if(item != null && now - item.time_update <= ERROR_EXPIRE) {
			item.time_used = now
			return item
		}
		
		// エラーキャッシュ
		val time_error = cache_error[instance]
		if(time_error != null && now < time_error + ERROR_EXPIRE) {
			return cache_error_item
		}
		
		return null
	}
	
	fun getList(
		_instance : String,
		onListLoaded : (list : ArrayList<CustomEmoji>) -> Unit
	) : ArrayList<CustomEmoji>? {
		try {
			if(_instance.isEmpty()) return null
			val instance = _instance.toLowerCase()
			
			synchronized(cache) {
				val item = getCached(elapsedTime, instance)
				if(item != null) return item.list
			}
			
			queue.add(Request(instance, onListLoaded))
			worker.notifyEx()
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		return null
	}
	
	private inner class Worker : WorkerBase() {
		
		override fun cancel() {
			// このスレッドはキャンセルされない。プロセスが生きている限り動き続ける。
		}
		
		override fun run() {
			while(true) {
				try {
					// リクエストを取得する
					val request = queue.poll()
					if(request == null) {
						// なければ待機
						waitEx(86400000L)
						continue
					}
					
					val cached = synchronized(cache) {
						val item = getCached(elapsedTime, request.instance)
						return@synchronized if(item != null) {
							val list = item.list
							if(list != null) {
								fireCallback(request, list)
							}
							true
						} else {
							// キャッシュにはなかった
							sweep_cache()
							false
						}
					}
					if(cached) continue
					
					var list : ArrayList<CustomEmoji>? = null
					try {
						val data =
							App1.getHttpCachedString("https://" + request.instance + "/api/v1/custom_emojis")
						if(data != null) {
							list = decodeEmojiList(data, request.instance)
						}
					} catch(ex : Throwable) {
						log.trace(ex)
					}
					
					synchronized(cache) {
						val now = elapsedTime
						if(list == null) {
							cache_error.put(request.instance, now)
						} else {
							var item : CacheItem? = cache[request.instance]
							if(item == null) {
								item = CacheItem(request.instance, list)
								cache[request.instance] = item
							} else {
								item.list = list
								item.time_update = now
							}
							fireCallback(request, list)
						}
					}
				} catch(ex : Throwable) {
					log.trace(ex)
					waitEx(3000L)
				}
			}
		}
		
		private fun fireCallback(request : Request, list : ArrayList<CustomEmoji>) {
			handler.post { request.onListLoaded(list) }
		}
		
		// キャッシュの掃除
		private fun sweep_cache() {
			// 超過してる数
			val over = cache.size - CACHE_MAX
			if(over <= 0) return
			
			// 古い要素を一時リストに集める
			val now = elapsedTime
			val list = ArrayList<CacheItem>(over)
			for(item in cache.values) {
				if(now - item.time_used > 1000L) list.add(item)
			}
			
			// 昇順ソート
			list.sortBy { it.time_used }
			
			// 古い物から順に捨てる
			var removed = 0
			for(item in list) {
				cache.remove(item.instance)
				if(++ removed >= over) break
			}
		}
		
		private fun decodeEmojiList(data : String, instance : String) : ArrayList<CustomEmoji>? {
			return try {
				val list = parseList(::CustomEmoji, data.toJsonArray() )
				list.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER, { it.shortcode }))
				list
			} catch(ex : Throwable) {
				log.e(ex, "decodeEmojiList failed. instance=%s", instance)
				null
			}
		}
		
	}
	
}
