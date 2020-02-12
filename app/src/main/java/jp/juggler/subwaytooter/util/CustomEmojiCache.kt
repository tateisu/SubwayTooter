package jp.juggler.subwaytooter.util

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Handler
import android.os.SystemClock
import android.provider.BaseColumns
import com.caverock.androidsvg.SVG
import jp.juggler.apng.ApngFrames
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.table.TableCompanion
import jp.juggler.util.LogCategory
import java.io.ByteArrayInputStream
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

class CustomEmojiCache(val context : Context) {
	
	companion object {
		
		private val log = LogCategory("CustomEmojiCache")
		
		internal const val DEBUG = false
		internal const val CACHE_MAX = 512 // 使用中のビットマップは掃除しないので、頻度によってはこれより多くなることもある
		internal const val ERROR_EXPIRE = 60000L * 10
		
		private val elapsedTime : Long
			get() = SystemClock.elapsedRealtime()
		
		// カスタム絵文字のキャッシュ専用のデータベースファイルを作る
		// (DB破損などの際に削除してしまえるようにする)
		private const val CACHE_DB_NAME = "emoji_cache_db"
		private const val CACHE_DB_VERSION = 1
	}
	
	private class DbCache(
		val id : Long,
		val timeUsed : Long,
		val data : ByteArray
	) {
		
		companion object : TableCompanion {
			
			const val table = "custom_emoji_cache"
			
			const val COL_ID = BaseColumns._ID
			const val COL_TIME_SAVE = "time_save"
			const val COL_TIME_USED = "time_used"
			const val COL_URL = "url"
			const val COL_DATA = "data"
			
			override fun onDBCreate(db : SQLiteDatabase) {
				db.execSQL(
					"""create table if not exists $table
						($COL_ID INTEGER PRIMARY KEY
						,$COL_TIME_SAVE integer not null
						,$COL_TIME_USED integer not null
						,$COL_URL text not null
						,$COL_DATA blob not null
						)""".trimIndent()
				)
				db.execSQL("create unique index if not exists ${table}_url on ${table}($COL_URL)")
				db.execSQL("create index if not exists ${table}_old on ${table}($COL_TIME_USED)")
			}
			
			override fun onDBUpgrade(
				db : SQLiteDatabase,
				oldVersion : Int,
				newVersion : Int
			) {
			}
			
			fun load(db : SQLiteDatabase, url : String, now : Long) =
				db.rawQuery(
					"select $COL_ID,$COL_TIME_USED,$COL_DATA from $table where $COL_URL=?",
					arrayOf(url)
				)?.use { cursor ->
					if(cursor.count == 0)
						null
					else {
						cursor.moveToNext()
						DbCache(
							id = cursor.getLong(cursor.getColumnIndex(COL_ID)),
							timeUsed = cursor.getLong(cursor.getColumnIndex(COL_TIME_USED)),
							data = cursor.getBlob(cursor.getColumnIndex(COL_DATA))
						).apply {
							if(now - timeUsed >= 5 * 3600000L) {
								db.update(
									table,
									ContentValues().apply {
										put(COL_TIME_USED, now)
									},
									"$COL_ID=?",
									arrayOf(id.toString())
								)
							}
						}
					}
				}
			
			fun sweep(db : SQLiteDatabase, now : Long) {
				val expire = now - TimeUnit.DAYS.toMillis(30)
				db.delete(
					table,
					"$COL_TIME_USED < ?",
					arrayOf(expire.toString())
				)
			}
			
			fun update(db : SQLiteDatabase, url : String, data : ByteArray) {
				val now = System.currentTimeMillis()
				db.replace(table,
					null,
					ContentValues().apply {
						put(COL_URL, url)
						put(COL_DATA, data)
						put(COL_TIME_USED, now)
						put(COL_TIME_SAVE, now)
					}
				)
			}
		}
	}
	
	private class DbOpenHelper(val context : Context) :
		SQLiteOpenHelper(context, CACHE_DB_NAME, null, CACHE_DB_VERSION) {
		
		private val tables = arrayOf(DbCache)
		override fun onCreate(db : SQLiteDatabase) =
			tables.forEach { it.onDBCreate(db) }
		
		override fun onUpgrade(db : SQLiteDatabase, oldVersion : Int, newVersion : Int) =
			tables.forEach { it.onDBUpgrade(db, oldVersion, newVersion) }
		
		fun deleteDatabase() {
			try {
				close()
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			try {
				SQLiteDatabase.deleteDatabase(context.getDatabasePath(databaseName))
			} catch(ex : Throwable) {
				log.trace(ex)
			}
		}
	}
	
	private class CacheItem(val url : String, var frames : ApngFrames?) {
		var time_used : Long = elapsedTime
	}
	
	private class Request(
		val refTarget : WeakReference<Any>,
		val url : String,
		val onLoadComplete : () -> Unit
	)
	
	// APNGデコード済のキャッシュデータ
	private val cache = ConcurrentHashMap<String, CacheItem>()
	
	// エラーキャッシュ
	private val cache_error = ConcurrentHashMap<String, Long>()
	private val cache_error_item = CacheItem("error", null)
	
	// リクエストキュー
	// キャンセル操作の都合上、アクセス時に排他が必要
	private val queue = LinkedList<Request>()
	
	private val handler = Handler(context.mainLooper)
	
	private val dbOpenHelper = DbOpenHelper(context)
	
	private var lastSweepDbCache = 0L
	
	private val workerLock = Any()

	// 他の変数より後に初期化すること
	private val workers =
		(1 .. 4).map { Worker(workerLock).apply { start() } }.toList()
	
	// DB処理を行い、SQLiteDatabaseCorruptExceptionを検出したらDBを削除してリトライする
	private fun <T : Any> useDbCache(block : (SQLiteDatabase) -> T?) : T? {
		for(nTry in (1 .. 3)) {
			try {
				val db = dbOpenHelper.writableDatabase ?: break
				return block(db)
			} catch(ex : SQLiteDatabaseCorruptException) {
				log.trace(ex)
				dbOpenHelper.deleteDatabase()
			} catch(ex : Throwable) {
				log.trace(ex)
				break
			}
		}
		return null
	}
	
	// ネットワーク接続が切り替わったタイミングでエラーキャッシュをクリアする
	fun onNetworkChanged() {
		cache_error.clear()
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
		} catch(ex : Throwable) {
			log.trace(ex)
			// たまにcache変数がなぜかnullになる端末があるらしい
		}
		return null
	}
	
	fun delete() {
		synchronized(cache) {
			for(entry in cache.entries) {
				entry.value.frames?.dispose()
			}
			cache.clear()
			cache_error.clear()
		}
		dbOpenHelper.deleteDatabase()
	}
	
	private inner class Worker(waiter : Any?) : WorkerBase(waiter) {
		
		override fun cancel() {
			// このスレッドはプロセスが生きてる限りキャンセルされない
		}
		
		override fun run() {
			var ts : Long
			var te : Long
			while(true) {
				try {
					var queue_size : Int
					val request = synchronized(queue) {
						val x = if(queue.isNotEmpty()) queue.removeFirst() else null
						queue_size = queue.size
						x
					}
					
					if(request == null) {
						if(DEBUG) log.d("wait")
						
						synchronized(cache) {
							val now = System.currentTimeMillis()
							if(now - lastSweepDbCache >= TimeUnit.DAYS.toMillis(1)) {
								lastSweepDbCache = now
								useDbCache { DbCache.sweep(it, now) }
							}
						}
						
						ts = elapsedTime
						waitEx(86400000L)
						te = elapsedTime
						if(te - ts >= 200L) log.d("sleep ${te - ts}ms")
						continue
					}
					
					// 描画先がGCされたなら何もしない
					request.refTarget.get() ?: continue
					
					ts = elapsedTime
					var cache_size : Int = - 1
					val cache_used = synchronized(cache) {
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
					}
					te = elapsedTime
					if(te - ts >= 200L) log.d("cache_used? ${te - ts}ms")
					
					if(cache_used) continue
					
					if(DEBUG)
						log.d(
							"start get image. queue_size=%d, cache_size=%d url=%s",
							queue_size,
							cache_size,
							request.url
						)
					
					val now = System.currentTimeMillis()
					
					// データベースからロードしてみる
					ts = elapsedTime
					val dbCache = useDbCache { DbCache.load(it, request.url, now) }
					te = elapsedTime
					if(te - ts >= 200L) log.d("DbCache.load ${te - ts}ms")
					
					var data = dbCache?.data
					
					// データベースにblobがなければHTTPリクエスト
					if(data == null) {
						ts = elapsedTime
						data = try {
							App1.getHttpCached(request.url)
						} catch(ex : Throwable) {
							log.e("get failed. url=%s", request.url)
							log.trace(ex)
							null
						}
						te = elapsedTime
						if(te - ts >= 200L) log.d("image get? ${te - ts}ms")
						
						if(data != null) {
							useDbCache { db ->
								DbCache.update(db, request.url, data)
							}
						}
					}
					
					ts = elapsedTime
					val frames = try {
						data?.let { decodeAPNG(it, request.url) }
					} catch(ex : Throwable) {
						log.e(ex, "decode failed.")
						null
					}
					te = elapsedTime
					if(te - ts >= 200L) log.d("image decode? ${te - ts}ms")
					
					ts = elapsedTime
					synchronized(cache) {
						if(frames == null) {
							cache_error[request.url] = elapsedTime
						} else {
							var item : CacheItem? = cache[request.url]
							if(item == null) {
								// 新しいキャッシュ項目
								item = CacheItem(request.url, frames)
								cache[request.url] = item
							} else {
								// 古いキャッシュを更新する
								item.frames?.dispose()
								item.frames = frames
							}
							fireCallback(request)
						}
					}
					te = elapsedTime
					if(te - ts >= 200L) log.d("update_cache ${te - ts}ms")
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
				val x = ApngFrames.parse(64) { ByteArrayInputStream(data) }
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
				val b = decodeSVG(url, data, 128.toFloat())
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
		
		private fun decodeBitmap(
			data : ByteArray,
			@Suppress("SameParameterValue") pixel_max : Int
		) : Bitmap? {
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
		
		private fun decodeSVG(
			url : String,
			data : ByteArray,
			@Suppress("SameParameterValue") pixelMax : Float
		) : Bitmap? {
			try {
				val svg = SVG.getFromInputStream(ByteArrayInputStream(data))
				
				val src_w =
					svg.documentWidth // the width in pixels, or -1 if there is no width available.
				val src_h =
					svg.documentHeight // the height in pixels, or -1 if there is no height available.
				val aspect = if(src_w <= 0f || src_h <= 0f) {
					// widthやheightの情報がない
					1f
				} else {
					src_w / src_h
				}
				
				val dst_w : Float
				val dst_h : Float
				if(aspect >= 1f) {
					dst_w = pixelMax
					dst_h = pixelMax / aspect
				} else {
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
						RectF(0f, h_ceil - dst_h, dst_w, dst_h) // 後半はw,hを指定する
					} else {
						RectF(w_ceil - dst_w, 0f, dst_w, dst_h) // 後半はw,hを指定する
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
