package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.PowerManager
import android.os.SystemClock
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat

import com.google.firebase.iid.FirebaseInstanceId
import jp.juggler.subwaytooter.api.TootApiCallback

import org.hjson.JsonObject
import org.hjson.JsonValue
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.Collections
import java.util.Comparator
import java.util.HashSet
import java.util.LinkedList
import java.util.TreeSet
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.EntityIdLong
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.table.*
import jp.juggler.subwaytooter.util.*
import okhttp3.Call
import okhttp3.Request
import okhttp3.RequestBody

class PollingWorker private constructor(contextArg : Context) {
	
	interface JobStatusCallback {
		fun onStatus(sv : String)
	}
	
	companion object {
		internal val log = LogCategory("PollingWorker")
		
		private const val FCM_SENDER_ID = "433682361381"
		private const val FCM_SCOPE = "FCM"
		
		const val NOTIFICATION_ID = 1
		const val NOTIFICATION_ID_ERROR = 3
		
		// Notification のJSONObject を日時でソートするためにデータを追加する
		const val KEY_TIME = "<>time"
		
		val mBusyAppDataImportBefore = AtomicBoolean(false)
		val mBusyAppDataImportAfter = AtomicBoolean(false)
		
		const val EXTRA_DB_ID = "db_id"
		const val EXTRA_TAG = "tag"
		const val EXTRA_TASK_ID = "task_id"
		
		const val APP_SERVER = "https://mastodon-msg.juggler.jp"
		
		const val PATH_NOTIFICATIONS = "/api/v1/notifications"
		
		internal val inject_queue = ConcurrentLinkedQueue<InjectData>()
		
		// ジョブID
		const val JOB_POLLING = 1
		private const val JOB_TASK = 2
		const val JOB_FCM = 3
		
		// タスクID
		const val TASK_POLLING = 1
		const val TASK_DATA_INJECTED = 2
		const val TASK_NOTIFICATION_CLEAR = 3
		const val TASK_APP_DATA_IMPORT_BEFORE = 4
		const val TASK_APP_DATA_IMPORT_AFTER = 5
		const val TASK_FCM_DEVICE_TOKEN = 6
		const val TASK_FCM_MESSAGE = 7
		const val TASK_BOOT_COMPLETED = 8
		const val TASK_PACKAGE_REPLACED = 9
		const val TASK_NOTIFICATION_DELETE = 10
		const val TASK_NOTIFICATION_CLICK = 11
		private const val TASK_UPDATE_NOTIFICATION = 12
		private const val TASK_UPDATE_LISTENER = 13
		
		@SuppressLint("StaticFieldLeak")
		private var sInstance : PollingWorker? = null
		
		fun getInstance(applicationContext : Context) : PollingWorker {
			var s = sInstance
			if(s == null) {
				s = PollingWorker(applicationContext)
				sInstance = s
			}
			return s
		}
		
		fun getDeviceId(context : Context) : String? {
			// 設定ファイルに保持されていたらそれを使う
			val prefDevice = PrefDevice.prefDevice(context)
			var device_token = prefDevice.getString(PrefDevice.KEY_DEVICE_TOKEN, null)
			if(device_token?.isNotEmpty() == true) return device_token
			
			try {
				// FirebaseのAPIから取得する
				device_token = FirebaseInstanceId.getInstance().getToken(FCM_SENDER_ID, FCM_SCOPE)
				if(device_token?.isNotEmpty() == true) {
					prefDevice
						.edit()
						.putString(PrefDevice.KEY_DEVICE_TOKEN, device_token)
						.apply()
					return device_token
				}
				log.e("getDeviceId: missing device token.")
				return null
			} catch(ex : Throwable) {
				log.trace(ex, "getDeviceId: could not get device token.")
				return null
			}
		}
		
		// インストールIDを生成する前に、各データの通知登録キャッシュをクリアする
		// トークンがまだ生成されていない場合、このメソッドは null を返します。
		fun prepareInstallId(
			context : Context,
			job : JobItem? = null
		) : String? {
			val prefDevice = PrefDevice.prefDevice(context)
			
			var sv = prefDevice.getString(PrefDevice.KEY_INSTALL_ID, null)
			if(sv?.isNotEmpty() == true) return sv
			
			SavedAccount.clearRegistrationCache()
			
			try {
				var device_token = prefDevice.getString(PrefDevice.KEY_DEVICE_TOKEN, null)
				if(device_token?.isEmpty() != false) {
					try {
						device_token =
							FirebaseInstanceId.getInstance().getToken(FCM_SENDER_ID, FCM_SCOPE)
						if(device_token == null || device_token.isEmpty()) {
							log.e("getInstallId: missing device token.")
							return null
						} else {
							prefDevice.edit().putString(PrefDevice.KEY_DEVICE_TOKEN, device_token)
								.apply()
						}
					} catch(ex : Throwable) {
						log.trace(ex, "getInstallId: could not get device token.")
						return null
					}
				}
				
				val request = Request.Builder()
					.url("$APP_SERVER/counter")
					.build()
				
				val call = App1.ok_http_client.newCall(request)
				if(job != null) {
					job.current_call = call
				}
				
				val response = call.execute()
				val body = response.body()?.string()
				
				if(! response.isSuccessful || body?.isEmpty() != false) {
					log.e(
						TootApiClient.formatResponse(
							response,
							"getInstallId: get /counter failed."
						)
					)
					return null
				}
				
				sv = (device_token + UUID.randomUUID() + body).digestSHA256Base64Url()
				prefDevice.edit().putString(PrefDevice.KEY_INSTALL_ID, sv).apply()
				
				return sv
				
			} catch(ex : Throwable) {
				log.trace(ex, "prepareInstallId failed.")
			}
			return null
		}
		
		//////////////////////////////////////////////////////////////////////
		// タスクの管理
		
		val task_list = TaskList()
		
		fun scheduleJob(context : Context, job_id : Int) {
			
			val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE)
				as? JobScheduler
				?: throw NotImplementedError("missing JobScheduler system service")
			
			val component = ComponentName(context, PollingService::class.java)
			
			val builder = JobInfo.Builder(job_id, component)
				.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
			
			if(job_id == JOB_POLLING) {
				val intervalMillis =
					60000L * Pref.spPullNotificationCheckInterval.toInt(Pref.pref(context))
				
				if(Build.VERSION.SDK_INT >= 24) {
					/// val minInterval = JobInfo.getMinPeriodMillis() // 15 min
					/// val minFlex = JobInfo.getMinFlexMillis()  // 5 min
					val minInterval = 300000L // 5 min
					val minFlex = 60000L // 1 min
					builder.setPeriodic(
						Math.max(minInterval, intervalMillis),
						Math.max(minFlex, intervalMillis shr 1)
					)
				} else {
					val minInterval = 300000L // 5 min
					builder.setPeriodic(Math.max(minInterval, intervalMillis))
				}
				builder.setPersisted(true)
			} else {
				builder
					.setMinimumLatency(0)
					.setOverrideDeadline(60000L)
			}
			val jobInfo = builder.build()
			scheduler.schedule(jobInfo)
		}
		
		// タスクの追加
		private fun addTask(
			context : Context,
			removeOld : Boolean,
			task_id : Int,
			taskDataArg : JSONObject?
		) {
			try {
				val taskData = taskDataArg ?: JSONObject()
				taskData.put(EXTRA_TASK_ID, task_id)
				task_list.addLast(context, removeOld, taskData)
				scheduleJob(context, JOB_TASK)
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			
		}
		
		fun queueUpdateListener(context : Context) {
			addTask(context, true, TASK_UPDATE_LISTENER, null)
		}
		
		fun queueUpdateNotification(context : Context) {
			addTask(context, true, TASK_UPDATE_NOTIFICATION, null)
		}
		
		fun injectData(
			context : Context,
			account : SavedAccount,
			src : ArrayList<TootNotification>
		) {
			
			if(src.isEmpty()) return
			
			val id = InjectData()
			id.account_db_id = account.db_id
			id.list.addAll(src)
			inject_queue.add(id)
			
			addTask(context, true, TASK_DATA_INJECTED, null)
		}
		
		fun queueNotificationCleared(context : Context, db_id : Long) {
			try {
				val data = JSONObject()
				data.putOpt(EXTRA_DB_ID, db_id)
				addTask(context, true, TASK_NOTIFICATION_CLEAR, data)
			} catch(ex : JSONException) {
				log.trace(ex)
			}
			
		}
		
		fun queueNotificationDeleted(context : Context, db_id : Long) {
			try {
				val data = JSONObject()
				data.putOpt(EXTRA_DB_ID, db_id)
				addTask(context, true, TASK_NOTIFICATION_DELETE, data)
			} catch(ex : JSONException) {
				log.trace(ex)
			}
			
		}
		
		fun queueNotificationClicked(context : Context, db_id : Long) {
			try {
				val data = JSONObject()
				data.putOpt(EXTRA_DB_ID, db_id)
				addTask(context, true, TASK_NOTIFICATION_CLICK, data)
			} catch(ex : JSONException) {
				log.trace(ex)
			}
			
		}
		
		fun queueAppDataImportBefore(context : Context) {
			mBusyAppDataImportBefore.set(true)
			mBusyAppDataImportAfter.set(true)
			addTask(context, false, TASK_APP_DATA_IMPORT_BEFORE, null)
		}
		
		fun queueAppDataImportAfter(context : Context) {
			addTask(context, false, TASK_APP_DATA_IMPORT_AFTER, null)
		}
		
		fun queueFCMTokenUpdated(context : Context) {
			addTask(context, true, TASK_FCM_DEVICE_TOKEN, null)
		}
		
		fun queueBootCompleted(context : Context) {
			addTask(context, true, TASK_BOOT_COMPLETED, null)
		}
		
		fun queuePackageReplaced(context : Context) {
			addTask(context, true, TASK_PACKAGE_REPLACED, null)
		}
		
		internal val job_status = AtomicReference<String>(null)
		
		fun handleFCMMessage(context : Context, tag : String?, callback : JobStatusCallback) {
			log.d("handleFCMMessage: start. tag=$tag")
			val time_start = SystemClock.elapsedRealtime()
			
			callback.onStatus("=>")
			
			// タスクを追加
			val data = JSONObject()
			try {
				if(tag != null) data.putOpt(EXTRA_TAG, tag)
				data.put(EXTRA_TASK_ID, TASK_FCM_MESSAGE)
			} catch(ignored : JSONException) {
			}
			
			task_list.addLast(context, true, data)
			
			callback.onStatus("==>")
			
			// 疑似ジョブを開始
			val pw = getInstance(context)
			pw.addJob(JOB_FCM, false)
			
			// 疑似ジョブが終了するまで待機する
			while(true) {
				// ジョブが完了した？
				val now = SystemClock.elapsedRealtime()
				if(! pw.hasJob(JOB_FCM)) {
					log.d(
						"handleFCMMessage: JOB_FCM completed. time=%.2f",
						(now - time_start) / 1000f
					)
					break
				}
				// ジョブの状況を通知する
				var sv : String? = job_status.get()
				if(sv == null) sv = "(null)"
				callback.onStatus(sv)
				
				// 少し待機
				try {
					Thread.sleep(50L)
				} catch(ex : InterruptedException) {
					log.e(ex, "handleFCMMessage: blocking is interrupted.")
					break
				}
				
			}
		}
		
		private fun EntityId.isLatestThan(previous : EntityId?) = when(previous) {
			null -> true
			else -> this > previous
		}
		
		private fun getEntityOrderId(account : SavedAccount, src : JSONObject) : EntityId {
			return when {
				! account.isMisskey -> EntityId.mayDefault(src.parseLong("id"))
				
				else -> {
					val created_at = src.parseString("createdAt")
					when(created_at) {
						null -> EntityId.defaultLong
						else -> EntityIdLong(TootStatus.parseTime(created_at))
					}
				}
			}
		}
	}
	
	internal val context : Context
	private val appState : AppState
	internal val handler : Handler
	internal val pref : SharedPreferences
	internal val connectivityManager : ConnectivityManager
	internal val notification_manager : NotificationManager
	internal val scheduler : JobScheduler
	private val power_manager : PowerManager?
	internal val power_lock : PowerManager.WakeLock
	private val wifi_manager : WifiManager?
	internal val wifi_lock : WifiManager.WifiLock
	
	private var worker : Worker
	
	internal val job_list = LinkedList<JobItem>()
	
	internal class Data(val access_info : SavedAccount, val notification : TootNotification)
	
	internal class InjectData {
		var account_db_id : Long = 0
		val list = ArrayList<TootNotification>()
	}
	
	init {
		log.d("ctor")
		
		val context = contextArg.applicationContext
		
		this.context = context
		
		this.connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
			?: error("missing ConnectivityManager system service")
		
		this.notification_manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
			?: error("missing NotificationManager system service")
		
		this.scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
			?: error("missing JobScheduler system service")
		
		this.power_manager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
			?: error("missing PowerManager system service")
		
		// WifiManagerの取得時はgetApplicationContext を使わないとlintに怒られる
		this.wifi_manager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
			?: error("missing WifiManager system service")
		
		power_lock = power_manager.newWakeLock(
			PowerManager.PARTIAL_WAKE_LOCK,
			PollingWorker::class.java.name
		)
		power_lock.setReferenceCounted(false)
		
		wifi_lock = wifi_manager.createWifiLock(PollingWorker::class.java.name)
		wifi_lock.setReferenceCounted(false)
		
		// クラッシュレポートによると App1.onCreate より前にここを通る場合がある
		// データベースへアクセスできるようにする
		this.appState = App1.prepare(context)
		this.pref = App1.pref
		this.handler = Handler(context.mainLooper)
		
		//
		worker = Worker()
		worker.start()
	}
	
	inner class Worker : WorkerBase() {
		
		val bThreadCancelled = AtomicBoolean(false)
		
		override fun cancel() {
			bThreadCancelled.set(true)
			notifyEx()
		}
		
		@SuppressLint("WakelockTimeout")
		private fun acquirePowerLock() {
			log.d("acquire power lock...")
			try {
				if(! power_lock.isHeld) {
					power_lock.acquire()
				}
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			
			try {
				if(! wifi_lock.isHeld) {
					wifi_lock.acquire()
				}
			} catch(ex : Throwable) {
				log.trace(ex)
			}
		}
		
		private fun releasePowerLock() {
			log.d("release power lock...")
			try {
				if(power_lock.isHeld) {
					power_lock.release()
				}
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			
			try {
				if(wifi_lock.isHeld) {
					wifi_lock.release()
				}
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			
		}
		
		override fun run() {
			log.d("worker thread start.")
			job_status.set("worker thread start.")
			while(! bThreadCancelled.get()) {
				try {
					val item : JobItem? = synchronized(job_list) {
						for(ji in job_list) {
							if(bThreadCancelled.get()) break
							if(ji.mJobCancelled_.get()) continue
							if(ji.mWorkerAttached.compareAndSet(false, true)) {
								return@synchronized ji
							}
						}
						null
					}
					
					if(item == null) {
						job_status.set("no job to run.")
						waitEx(86400000L)
						continue
					}
					
					job_status.set("start job " + item.jobId)
					acquirePowerLock()
					try {
						item.refWorker.set(this@Worker)
						item.run()
					} finally {
						job_status.set("end job " + item.jobId)
						item.refWorker.set(null)
						releasePowerLock()
					}
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
			}
			job_status.set("worker thread end.")
			log.d("worker thread end.")
		}
	}
	
	//////////////////////////////////////////////////////////////////////
	// ジョブの管理
	
	// JobService#onDestroy から呼ばれる
	fun onJobServiceDestroy() {
		log.d("onJobServiceDestroy")
		
		synchronized(job_list) {
			val it = job_list.iterator()
			while(it.hasNext()) {
				val item = it.next()
				if(item.jobId != JOB_FCM) {
					it.remove()
					item.cancel(false)
				}
			}
		}
	}
	
	// JobService#onStartJob から呼ばれる
	fun onStartJob(jobService : JobService, params : JobParameters) : Boolean {
		val item = JobItem(jobService, params)
		addJob(item, true)
		return true
		// return True if your context needs to process the work (on a separate thread).
		// return False if there's no more work to be done for this job.
	}
	
	// FCMメッセージイベントから呼ばれる
	private fun hasJob(jobId : Int) : Boolean {
		synchronized(job_list) {
			for(item in job_list) {
				if(item.jobId == jobId) return true
			}
		}
		return false
	}
	
	// FCMメッセージイベントから呼ばれる
	private fun addJob(jobId : Int, bRemoveOld : Boolean) {
		addJob(JobItem(jobId), bRemoveOld)
	}
	
	private fun addJob(item : JobItem, bRemoveOld : Boolean) {
		val jobId = item.jobId
		
		// 同じジョブ番号がジョブリストにあるか？
		synchronized(job_list) {
			if(bRemoveOld) {
				val it = job_list.iterator()
				while(it.hasNext()) {
					val itemOld = it.next()
					if(itemOld.jobId == jobId) {
						log.w("addJob: jobId=$jobId, old job cancelled.")
						// 同じジョブをすぐに始めるのだからrescheduleはfalse
						itemOld.cancel(false)
						it.remove()
					}
				}
			}
			log.d("addJob: jobId=$jobId, add to list.")
			job_list.add(item)
		}
		
		worker.notifyEx()
	}
	
	// JobService#onStopJob から呼ばれる
	fun onStopJob(params : JobParameters) : Boolean {
		val jobId = params.jobId
		
		// 同じジョブ番号がジョブリストにあるか？
		synchronized(job_list) {
			val it = job_list.iterator()
			while(it.hasNext()) {
				val item = it.next()
				if(item.jobId == jobId) {
					log.w("onStopJob: jobId=${jobId}, set cancel flag.")
					// リソースがなくてStopされるのだからrescheduleはtrue
					item.cancel(true)
					it.remove()
					return item.mReschedule.get()
				}
			}
		}
		
		// 該当するジョブを依頼されていない
		log.w("onStopJob: jobId=${jobId}, not started..")
		return false
		// return True to indicate to the JobManager whether you'd like to reschedule this job based on the retry criteria provided at job creation-time.
		// return False to drop the job. Regardless of the value returned, your job must stop executing.
	}
	
	internal class JobCancelledException : RuntimeException("job is cancelled.")
	
	inner class JobItem {
		val jobId : Int
		private val refJobService : WeakReference<JobService>?
		private val jobParams : JobParameters?
		val mJobCancelled_ = AtomicBoolean()
		val mReschedule = AtomicBoolean()
		val mWorkerAttached = AtomicBoolean()
		
		val bPollingRequired = AtomicBoolean(false)
		lateinit var muted_app : HashSet<String>
		lateinit var muted_word : WordTrieTree
		lateinit var favMuteSet : HashSet<String>
		var bPollingComplete = false
		var install_id : String? = null
		
		var current_call : Call? = null
		
		val refWorker = AtomicReference<Worker>(null)
		
		val isJobCancelled : Boolean
			get() {
				if(mJobCancelled_.get()) return true
				val worker = refWorker.get()
				return worker != null && worker.bThreadCancelled.get()
			}
		
		constructor(jobService : JobService, params : JobParameters) {
			this.jobParams = params
			this.jobId = params.jobId
			this.refJobService = WeakReference(jobService)
		}
		
		constructor(jobId : Int) {
			this.jobId = jobId
			this.jobParams = null
			this.refJobService = null
		}
		
		fun notifyWorkerThread() {
			val worker = refWorker.get()
			worker?.notifyEx()
		}
		
		fun waitWorkerThread(ms : Long) {
			val worker = refWorker.get()
			worker?.waitEx(ms)
		}
		
		fun cancel(bReschedule : Boolean) {
			mJobCancelled_.set(true)
			mReschedule.set(bReschedule)
			current_call?.cancel()
			notifyWorkerThread()
		}
		
		fun run() {
			
			job_status.set("job start.")
			try {
				log.d("(JobItem.run jobId=${jobId}")
				if(isJobCancelled) throw JobCancelledException()
				
				job_status.set("check network status..")
				
				val net_wait_start = SystemClock.elapsedRealtime()
				while(! checkNetwork()) {
					if(isJobCancelled) throw JobCancelledException()
					val now = SystemClock.elapsedRealtime()
					val delta = now - net_wait_start
					if(delta >= 10000L) {
						log.d("network state timeout.")
						break
					}
					waitWorkerThread(333L)
				}
				
				muted_app = MutedApp.nameSet
				muted_word = MutedWord.nameSet
				favMuteSet = FavMute.acctSet
				
				// タスクがあれば処理する
				while(true) {
					if(isJobCancelled) throw JobCancelledException()
					val data = task_list.next(context) ?: break
					val task_id = data.optInt(EXTRA_TASK_ID, 0)
					TaskRunner().runTask(this@JobItem, task_id, data)
				}
				
				if(! isJobCancelled && ! bPollingComplete && jobId == JOB_POLLING) {
					// タスクがなかった場合でも定期実行ジョブからの実行ならポーリングを行う
					TaskRunner().runTask(this@JobItem, TASK_POLLING, JSONObject())
				}
				job_status.set("make next schedule.")
				
				log.d("pollingComplete=${bPollingComplete},isJobCancelled=${isJobCancelled},bPollingRequired=${bPollingRequired.get()}")
				
				if(! isJobCancelled && bPollingComplete) {
					// ポーリングが完了した
					if(! bPollingRequired.get()) {
						// Pull通知を必要とするアカウントが存在しないなら、スケジュール登録を解除する
						log.d("polling job is no longer required.")
						try {
							scheduler.cancel(JOB_POLLING)
						} catch(ex : Throwable) {
							log.trace(ex)
						}
					} else if(! scheduler.allPendingJobs.any { it.id == JOB_POLLING }) {
						// まだスケジュールされてないなら登録する
						log.d("registering polling job…")
						scheduleJob(context, JOB_POLLING)
					}
				}
			} catch(ex : JobCancelledException) {
				log.e("job execution cancelled.")
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "job execution failed.")
			} finally {
				job_status.set("job finished.")
			}
			// ジョブ終了報告
			if(! isJobCancelled) {
				handler.post(Runnable {
					if(isJobCancelled) return@Runnable
					
					synchronized(job_list) {
						job_list.remove(this@JobItem)
					}
					
					try {
						val jobService = refJobService?.get()
						if(jobService != null) {
							val willReschedule = mReschedule.get()
							log.d("sending jobFinished. willReschedule=$willReschedule")
							jobService.jobFinished(jobParams, willReschedule)
						}
					} catch(ex : Throwable) {
						log.trace(ex, "jobFinished failed(1).")
					}
				})
			}
			log.d(")JobItem.run jobId=${jobId}, cancel=${isJobCancelled}")
		}
		
		private fun checkNetwork() : Boolean {
			return App1.getAppState(context).networkTracker.isConnected
		}
	}
	
	internal inner class TaskRunner {
		
		var mCustomStreamListenerSecret : String? = null
		var mCustomStreamListenerSettingString : String? = null
		private var mCustomStreamListenerSetting : JsonObject? = null
		
		lateinit var job : JobItem
		private var taskId : Int = 0
		
		val error_instance = ArrayList<String>()
		
		fun runTask(job : JobItem, taskId : Int, taskData : JSONObject) {
			try {
				log.d("(runTask: taskId=${taskId}")
				job_status.set("start task $taskId")
				
				this.job = job
				this.taskId = taskId
				
				var process_db_id = - 1L //
				
				when(taskId) {
					TASK_APP_DATA_IMPORT_BEFORE -> {
						scheduler.cancelAll()
						for(a in SavedAccount.loadAccountList(context)) {
							try {
								val notification_tag = a.db_id.toString()
								notification_manager.cancel(notification_tag, NOTIFICATION_ID)
							} catch(ex : Throwable) {
								log.trace(ex)
							}
							
						}
						mBusyAppDataImportBefore.set(false)
						return
					}
					
					TASK_APP_DATA_IMPORT_AFTER -> {
						mBusyAppDataImportAfter.set(false)
						mBusyAppDataImportBefore.set(false)
						NotificationTracking.resetPostAll()
						// fall
					}
					
				}
				
				// アプリデータのインポート処理がビジーな間、他のジョブは実行されない
				if(mBusyAppDataImportBefore.get() || mBusyAppDataImportAfter.get()) return
				
				// タスクによってはポーリング前にすることがある
				when(taskId) {
					TASK_DATA_INJECTED -> processInjectedData()
					
					TASK_BOOT_COMPLETED -> NotificationTracking.resetPostAll()
					
					TASK_PACKAGE_REPLACED -> NotificationTracking.resetPostAll()
					
					// デバイストークンが更新された
					TASK_FCM_DEVICE_TOKEN -> {
					}
					
					// プッシュ通知が届いた
					TASK_FCM_MESSAGE -> {
						var bDone = false
						val tag = taskData.parseString(EXTRA_TAG)
						if(tag != null) {
							if(tag.startsWith("acct<>")) {
								val acct = tag.substring(6)
								val sa = SavedAccount.loadAccountByAcct(context, acct)
								if(sa != null) {
									NotificationTracking.resetLastLoad(sa.db_id)
									process_db_id = sa.db_id
									bDone = true
								}
							}
							if(! bDone) {
								for(sa in SavedAccount.loadByTag(context, tag)) {
									NotificationTracking.resetLastLoad(sa.db_id)
									process_db_id = sa.db_id
									bDone = true
								}
							}
						}
						if(! bDone) {
							// タグにマッチする情報がなかった場合、全部読み直す
							NotificationTracking.resetLastLoad()
						}
					}
					
					TASK_NOTIFICATION_CLEAR -> {
						val db_id = taskData.parseLong(EXTRA_DB_ID)
						log.d("Notification clear! db_id=$db_id")
						if(db_id != null) {
							deleteCacheData(db_id)
						}
					}
					
					TASK_NOTIFICATION_DELETE -> {
						val db_id = taskData.parseLong(EXTRA_DB_ID)
						log.d("Notification deleted! db_id=$db_id")
						if(db_id != null) {
							NotificationTracking.updateRead(db_id)
						}
						return
					}
					
					TASK_NOTIFICATION_CLICK -> {
						val db_id = taskData.parseLong(EXTRA_DB_ID)
						log.d("Notification clicked! db_id=$db_id")
						if(db_id != null) {
							// 通知をキャンセル
							notification_manager.cancel(db_id.toString(), NOTIFICATION_ID)
							// DB更新処理
							NotificationTracking.updateRead(db_id)
							
						}
						return
					}
					
				}
				
				loadCustomStreamListenerSetting()
				
				job_status.set("make install id")
				
				// インストールIDを生成する
				// インストールID生成時にSavedAccountテーブルを操作することがあるので
				// アカウントリストの取得より先に行う
				if(job.install_id == null) {
					job.install_id = prepareInstallId(context, job)
				}
				
				// アカウント別に処理スレッドを作る
				job_status.set("create account thread")
				val thread_list = LinkedList<AccountThread>()
				for(_a in SavedAccount.loadAccountList(context)) {
					if(_a.isPseudo) continue
					if(process_db_id != - 1L && _a.db_id != process_db_id) continue
					val t = AccountThread(_a)
					thread_list.add(t)
					t.start()
				}
				while(true) {
					// 同じホスト名が重複しないようにSetに集める
					val liveSet = TreeSet<String>()
					for(t in thread_list) {
						if(! t.isAlive) continue
						if(job.isJobCancelled) t.cancel()
						liveSet.add(t.account.host)
					}
					if(liveSet.isEmpty()) break
					
					job_status.set("waiting " + liveSet.joinToString(", "))
					job.waitWorkerThread(if(job.isJobCancelled) 100L else 1000L)
				}
				
				synchronized(error_instance) {
					createErrorNotification(error_instance)
				}
				
				if(! job.isJobCancelled) job.bPollingComplete = true
				
			} catch(ex : Throwable) {
				log.trace(ex, "task execution failed.")
			} finally {
				log.d(")runTask: taskId=$taskId")
				job_status.set("end task $taskId")
			}
		}
		
		private fun createErrorNotification(error_instance : ArrayList<String>) {
			if(error_instance.isEmpty()) {
				return
			}
			
			// 通知タップ時のPendingIntent
			val intent_click = Intent(context, ActCallback::class.java)
			intent_click.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			val pi_click = PendingIntent.getActivity(
				context,
				3,
				intent_click,
				PendingIntent.FLAG_UPDATE_CURRENT
			)
			
			val builder = if(Build.VERSION.SDK_INT >= 26) {
				// Android 8 から、通知のスタイルはユーザが管理することになった
				// NotificationChannel を端末に登録しておけば、チャネルごとに管理画面が作られる
				val channel = NotificationHelper.createNotificationChannel(
					context,
					"ErrorNotification",
					"Error",
					null,
					2 /* NotificationManager.IMPORTANCE_LOW */
				)
				NotificationCompat.Builder(context, channel.id)
			} else {
				NotificationCompat.Builder(context, "not_used")
			}
			
			builder
				.setContentIntent(pi_click)
				.setAutoCancel(true)
				.setSmallIcon(R.drawable.ic_notification) // ここは常に白テーマのアイコンを使う
				.setColor(
					ContextCompat.getColor(
						context,
						R.color.Light_colorAccent
					)
				) // ここは常に白テーマの色を使う
				.setWhen(System.currentTimeMillis())
				.setGroup(context.packageName + ":" + "Error")
			
			run {
				val header = context.getString(R.string.error_notification_title)
				val summary = context.getString(R.string.error_notification_summary)
				
				builder
					.setContentTitle(header)
					.setContentText(summary + ": " + error_instance[0])
				
				val style = NotificationCompat.InboxStyle()
					.setBigContentTitle(header)
					.setSummaryText(summary)
				for(i in 0 .. 4) {
					if(i >= error_instance.size) break
					style.addLine(error_instance[i])
				}
				builder.setStyle(style)
			}
			notification_manager.notify(NOTIFICATION_ID_ERROR, builder.build())
		}
		
		private fun loadCustomStreamListenerSetting() {
			mCustomStreamListenerSetting = null
			mCustomStreamListenerSecret = null
			val jsonString = Pref.spStreamListenerConfigData(pref)
			mCustomStreamListenerSettingString = jsonString
			if(jsonString.isNotEmpty()) {
				try {
					mCustomStreamListenerSetting = JsonValue.readHjson(jsonString).asObject()
					mCustomStreamListenerSecret = Pref.spStreamListenerSecret(pref)
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
			}
		}
		
		internal inner class AccountThread(
			val account : SavedAccount
		) : Thread(), CurrentCallCallback {
			
			private var current_call : Call? = null
			
			private val client = TootApiClient(context, callback = object : TootApiCallback {
				override val isApiCancelled : Boolean
					get() = job.isJobCancelled
			})
			
			private val duplicate_check = HashSet<EntityId>()
			private val dstListJson = ArrayList<JSONObject>()
			private val dstListData = ArrayList<Data>()
			private val favMuteSet : HashSet<String> get() = job.favMuteSet
			private lateinit var nr : NotificationTracking
			private lateinit var parser : TootParser
			
			private var nid_last_show : EntityId? = null
			
			init {
				client.currentCallCallback = this
			}
			
			override fun onCallCreated(call : Call) {
				current_call = call
			}
			
			fun cancel() {
				try {
					current_call?.cancel()
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
			}
			
			override fun run() {
				try {
					// 疑似アカウントはチェック対象外
					if(account.isPseudo) return
					
					client.account = account
					
					val wps = PushSubscriptionHelper(context, account)
					if(wps.flags != 0) {
						job.bPollingRequired.set(true)
					}
					
					wps.updateSubscription(client)
					val wps_log = wps.log
					if(wps_log.isNotEmpty()) {
						log.d("PushSubscriptionHelper: ${account.acct} $wps_log")
					}
					
					if(job.isJobCancelled) return
					if(wps.flags == 0) {
						if(! account.isMisskey) unregisterDeviceToken()
						return
					}
					
					if(wps.subscribed) {
						if(! account.isMisskey) unregisterDeviceToken()
					} else {
						if(! account.isMisskey) registerDeviceToken()
					}
					
					if(job.isJobCancelled) return
					
					checkAccount()
					
					if(job.isJobCancelled) return
					
					showNotification(dstListData)
					
				} catch(ex : Throwable) {
					log.trace(ex)
				} finally {
					job.notifyWorkerThread()
				}
			}
			
			private fun unregisterDeviceToken() {
				try {
					if(SavedAccount.REGISTER_KEY_UNREGISTERED == account.register_key) {
						log.d("unregisterDeviceToken: already unregistered.")
						return
					}
					
					// ネットワーク的な事情でインストールIDを取得できなかったのなら、何もしない
					val install_id = job.install_id
					if(install_id?.isEmpty() != false) {
						log.d("unregisterDeviceToken: missing install_id")
						return
					}
					
					val tag = account.notification_tag
					if(tag?.isEmpty() != false) {
						log.d("unregisterDeviceToken: missing notification_tag")
						return
					}
					
					val post_data = ("instance_url=" + ("https://" + account.host).encodePercent()
						+ "&app_id=" + context.packageName.encodePercent()
						+ "&tag=" + tag)
					
					val request = Request.Builder()
						.url("$APP_SERVER/unregister")
						.post(
							RequestBody.create(
								TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED,
								post_data
							)
						)
						.build()
					
					val call = App1.ok_http_client.newCall(request)
					current_call = call
					
					val response = call.execute()
					
					log.d("unregisterDeviceToken: %s", response)
					
					if(response.isSuccessful) {
						account.register_key = SavedAccount.REGISTER_KEY_UNREGISTERED
						account.register_time = 0L
						account.saveRegisterKey()
					}
					
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
			}
			
			private fun registerDeviceToken() {
				try {
					// 設定によってはデバイストークンやアクセストークンを送信しない
					if(! Pref.bpSendAccessTokenToAppServer(Pref.pref(context))) {
						log.d("registerDeviceToken: SendAccessTokenToAppServer is not set.")
						return
					}
					
					// ネットワーク的な事情でインストールIDを取得できなかったのなら、何もしない
					val install_id = job.install_id
					if(install_id?.isEmpty() != false) {
						log.d("registerDeviceToken: missing install id")
						return
					}
					
					val prefDevice = PrefDevice.prefDevice(context)
					
					val device_token = prefDevice.getString(PrefDevice.KEY_DEVICE_TOKEN, null)
					if(device_token?.isEmpty() != false) {
						log.d("registerDeviceToken: missing device_token")
						return
					}
					
					val access_token = account.getAccessToken()
					if(access_token?.isEmpty() != false) {
						log.d("registerDeviceToken: missing access_token")
						return
					}
					
					var tag : String? = account.notification_tag
					
					if(SavedAccount.REGISTER_KEY_UNREGISTERED == account.register_key) {
						tag = null
					}
					
					if(tag?.isEmpty() != false) {
						account.notification_tag =
							(job.install_id + account.db_id + account.acct).digestSHA256Hex()
						tag = account.notification_tag
						account.saveNotificationTag()
					}
					
					val reg_key = (tag
						+ access_token
						+ device_token
						+ (if(mCustomStreamListenerSecret == null) "" else mCustomStreamListenerSecret)
						+ if(mCustomStreamListenerSettingString == null) "" else mCustomStreamListenerSettingString
						).digestSHA256Hex()
					
					val now = System.currentTimeMillis()
					if(reg_key == account.register_key && now - account.register_time < 3600000 * 3) {
						// タグやトークンが同一なら、前回登録に成功してから一定時間は再登録しない
						log.d("registerDeviceToken: already registered.")
						return
					}
					
					val post_data = StringBuilder()
						.append("instance_url=").append(("https://" + account.host).encodePercent())
						.append("&app_id=").append(context.packageName.encodePercent())
						.append("&tag=").append(tag)
						.append("&access_token=").append(access_token)
						.append("&device_token=").append(device_token)
					
					val jsonString = mCustomStreamListenerSettingString
					val appSecret = mCustomStreamListenerSecret
					
					if(jsonString != null && appSecret != null) {
						post_data.append("&user_config=").append(jsonString.encodePercent())
						post_data.append("&app_secret=").append(appSecret.encodePercent())
					}
					
					val request = Request.Builder()
						.url("$APP_SERVER/register")
						.post(
							RequestBody.create(
								TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED,
								post_data.toString()
							)
						)
						.build()
					
					val call = App1.ok_http_client.newCall(request)
					current_call = call
					
					val response = call.execute()
					
					var body : String? = null
					try {
						body = response.body()?.string()
					} catch(ignored : Throwable) {
					}
					
					log.d("registerDeviceToken: %s (%s)", response, body ?: "")
					
					val code = response.code()
					
					if(response.isSuccessful || code >= 400 && code < 500) {
						// 登録できた時も4xxエラーだった時もDBに記録する
						account.register_key = reg_key
						account.register_time = now
						account.saveRegisterKey()
					}
					
				} catch(ex : Throwable) {
					log.trace(ex)
				}
			}
			
			private fun checkAccount() {
				this.nr = NotificationTracking.load(account.db_id)
				this.parser = TootParser(context, account)
				
				// まずキャッシュされたデータを処理する
				try {
					val last_data = nr.last_data
					if(last_data != null) {
						val array = last_data.toJsonArray()
						for(i in array.length() - 1 downTo 0) {
							if(job.isJobCancelled) return
							val src = array.optJSONObject(i)
							update_sub(src)
						}
					}
				} catch(ex : JSONException) {
					log.trace(ex)
				}
				
				
				if(job.isJobCancelled) return
				
				// 前回の更新から一定時刻が経過したら新しいデータを読んでリストに追加する
				val now = System.currentTimeMillis()
				if(now - nr.last_load >= 60000L * 2) {
					nr.last_load = now
					
					for(nTry in 0 .. 3) {
						if(job.isJobCancelled) return
						
						val result = if(account.isMisskey) {
							val params = account.putMisskeyApiToken(JSONObject())
							client.request("/api/i/notifications", params.toPostRequestBuilder())
						} else {
							val path = when {
								nid_last_show != null -> "$PATH_NOTIFICATIONS?since_id=$nid_last_show"
								else -> PATH_NOTIFICATIONS
							}
							
							client.request(path)
						}
						if(result == null) {
							log.d("cancelled.")
							break
						}
						val array = result.jsonArray
						if(array != null) {
							try {
								for(i in array.length() - 1 downTo 0) {
									val src = array.optJSONObject(i)
									update_sub(src)
								}
							} catch(ex : JSONException) {
								log.trace(ex)
							}
							
							break
						} else {
							log.d("error. ${result.error}")
							
							val sv = result.error
							if(sv?.contains("Timeout") == true && ! account.dont_show_timeout) {
								synchronized(error_instance) {
									var bFound = false
									for(x in error_instance) {
										if(x == sv) {
											bFound = true
											break
										}
									}
									if(! bFound) {
										error_instance.add(sv)
									}
								}
							}
						}
					}
				}
				
				if(job.isJobCancelled) return
				
				dstListJson.sortWith(Comparator { a, b ->
					val la = a.parseLong(KEY_TIME) ?: 0
					val lb = b.parseLong(KEY_TIME) ?: 0
					// 新しい順
					return@Comparator if(la < lb) 1 else if(la > lb) - 1 else 0
				})
				
				if(job.isJobCancelled) return
				
				val d = JSONArray()
				for(i in 0 .. 9) {
					if(i >= dstListJson.size) break
					d.put(dstListJson[i])
				}
				nr.last_data = d.toString()
				nr.save()
			}
			
			@Throws(JSONException::class)
			private fun update_sub(src : JSONObject) {
				
				if(nr.nid_read == null || nr.nid_show == null) {
					log.d("update_sub[${account.db_id}], nid_read=${nr.nid_read}, nid_show=${nr.nid_show}")
				}
				
				val id = getEntityOrderId(account, src)
				
				if(id.isDefault || duplicate_check.contains(id)) return
				duplicate_check.add(id)
				
				if(id.isLatestThan(nid_last_show)) {
					nid_last_show = id
				}
				
				if(! id.isLatestThan(nr.nid_read)) {
					// 既読のID以下なら何もしない
					return
				}
				
				log.d("update_sub: found data that id=${id}, > read id ${nr.nid_read}")
				
				if(id.isLatestThan(nr.nid_show)) {
					log.d("update_sub: found new data that id=${id}, greater than shown id ${nr.nid_show}")
					// 種別チェックより先に「表示済み」idの更新を行う
					nr.nid_show = id
				}
				
				val type = src.parseString("type")
				
				if(! account.canNotificationShowing(type)) return
				
				val notification = parser.notification(src) ?: return
				
				// アプリミュートと単語ミュート
				if(notification.status?.checkMuted() == true) {
					return
				}
				
				// ふぁぼ魔ミュート
				when(type) {
					TootNotification.TYPE_REBLOG, TootNotification.TYPE_FAVOURITE, TootNotification.TYPE_FOLLOW -> {
						val who = notification.account
						if(who != null && favMuteSet.contains(account.getFullAcct(who))) {
							log.d("${account.getFullAcct(who)} is in favMuteSet.")
							return
						}
					}
				}
				
				//
				val data = Data(account, notification)
				dstListData.add(data)
				//
				src.put(KEY_TIME, data.notification.time_created_at)
				dstListJson.add(src)
			}
			
			private fun getNotificationLine(type : String, display_name : CharSequence) =
				when(type) {
					TootNotification.TYPE_MENTION,
					TootNotification.TYPE_REPLY ->
						"- " + context.getString(R.string.display_name_replied_by, display_name)
					
					TootNotification.TYPE_RENOTE,
					TootNotification.TYPE_REBLOG ->
						"- " + context.getString(R.string.display_name_boosted_by, display_name)
					
					TootNotification.TYPE_QUOTE ->
						"- " + context.getString(R.string.display_name_quoted_by, display_name)
					
					TootNotification.TYPE_FOLLOW ->
						"- " + context.getString(R.string.display_name_followed_by, display_name)
					
					TootNotification.TYPE_UNFOLLOW ->
						"- " + context.getString(R.string.display_name_unfollowed_by, display_name)
					
					TootNotification.TYPE_FAVOURITE ->
						"- " + context.getString(R.string.display_name_favourited_by, display_name)
					
					TootNotification.TYPE_REACTION ->
						"- " + context.getString(R.string.display_name_reaction_by, display_name)
					
					TootNotification.TYPE_VOTE ->
						"- " + context.getString(R.string.display_name_voted_by, display_name)
					
					TootNotification.TYPE_FOLLOW_REQUEST ->
						"- " + context.getString(
							R.string.display_name_follow_request_by,
							display_name
						)
					
					else -> "- " + "?"
				}
			
			private fun showNotification(data_list : ArrayList<Data>) {
				
				val notification_tag = account.db_id.toString()
				if(data_list.isEmpty()) {
					log.d("showNotification[${account.acct}] cancel notification.")
					notification_manager.cancel(notification_tag, NOTIFICATION_ID)
					return
				}
				
				Collections.sort(data_list, Comparator { a, b ->
					val la = a.notification.time_created_at
					val lb = b.notification.time_created_at
					// 新しい順
					if(la < lb) return@Comparator + 1
					if(la > lb) - 1 else 0
				})
				var item = data_list[0]
				
				val nt = NotificationTracking.load(account.db_id)
				
				if(item.notification.time_created_at == nt.post_time
					&& item.notification.id == nt.post_id
				) {
					// 先頭にあるデータが同じなら、通知を更新しない
					// このマーカーは端末再起動時にリセットされるので、再起動後は通知が出るはず
					
					log.d("showNotification[${account.acct}] id=${item.notification.id} is already shown.")
					
					return
				}
				
				nt.updatePost(item.notification.id, item.notification.time_created_at)
				
				log.d("showNotification[${account.acct}] creating notification(1)")
				
				// 通知タップ時のPendingIntent
				val intent_click = Intent(context, ActCallback::class.java)
				intent_click.action = ActCallback.ACTION_NOTIFICATION_CLICK
				intent_click.data =
					Uri.parse("subwaytooter://notification_click/?db_id=" + account.db_id)
				intent_click.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				val pi_click = PendingIntent.getActivity(
					context,
					256 + account.db_id.toInt(),
					intent_click,
					PendingIntent.FLAG_UPDATE_CURRENT
				)
				
				// 通知を消去した時のPendingIntent
				val intent_delete = Intent(context, EventReceiver::class.java)
				intent_delete.action = EventReceiver.ACTION_NOTIFICATION_DELETE
				intent_delete.putExtra(EXTRA_DB_ID, account.db_id)
				val pi_delete = PendingIntent.getBroadcast(
					context,
					Integer.MAX_VALUE - account.db_id.toInt(),
					intent_delete,
					PendingIntent.FLAG_UPDATE_CURRENT
				)
				
				log.d("showNotification[${account.acct}] creating notification(2)")
				
				val builder = if(Build.VERSION.SDK_INT >= 26) {
					// Android 8 から、通知のスタイルはユーザが管理することになった
					// NotificationChannel を端末に登録しておけば、チャネルごとに管理画面が作られる
					val channel = NotificationHelper.createNotificationChannel(context, account)
					NotificationCompat.Builder(context, channel.id)
				} else {
					NotificationCompat.Builder(context, "not_used")
				}
				
				builder
					.setContentIntent(pi_click)
					.setDeleteIntent(pi_delete)
					.setAutoCancel(true)
					.setSmallIcon(R.drawable.ic_notification) // ここは常に白テーマのアイコンを使う
					.setColor(
						ContextCompat.getColor(
							context,
							R.color.Light_colorAccent
						)
					) // ここは常に白テーマの色を使う
					.setWhen(item.notification.time_created_at)
				
				// Android 7.0 ではグループを指定しないと勝手に通知が束ねられてしまう。
				// 束ねられた通知をタップしても pi_click が実行されないので困るため、
				// アカウント別にグループキーを設定する
				builder.setGroup(context.packageName + ":" + account.acct)
				
				log.d("showNotification[${account.acct}] creating notification(3)")
				
				if(Build.VERSION.SDK_INT < 26) {
					
					var iv = 0
					
					if(Pref.bpNotificationSound(pref)) {
						
						var sound_uri : Uri? = null
						
						try {
							val acct = item.access_info.getFullAcct(item.notification.account)
							val sv = AcctColor.getNotificationSound(acct)
							sound_uri = if(sv == null || sv.isEmpty()) null else Uri.parse(sv)
						} catch(ex : Throwable) {
							log.trace(ex)
						}
						
						if(sound_uri == null) {
							try {
								val sv = account.sound_uri
								sound_uri = if(sv.isEmpty()) null else Uri.parse(sv)
							} catch(ex : Throwable) {
								log.trace(ex)
							}
							
						}
						
						var bSoundSet = false
						if(sound_uri != null) {
							try {
								builder.setSound(sound_uri)
								bSoundSet = true
							} catch(ex : Throwable) {
								log.trace(ex)
							}
							
						}
						if(! bSoundSet) {
							iv = iv or NotificationCompat.DEFAULT_SOUND
						}
					}
					
					log.d("showNotification[${account.acct}] creating notification(4)")
					
					if(Pref.bpNotificationVibration(pref)) {
						iv = iv or NotificationCompat.DEFAULT_VIBRATE
					}
					
					log.d("showNotification[${account.acct}] creating notification(5)")
					
					if(Pref.bpNotificationLED(pref)) {
						iv = iv or NotificationCompat.DEFAULT_LIGHTS
					}
					
					log.d("showNotification[${account.acct}] creating notification(6)")
					
					builder.setDefaults(iv)
				}
				
				log.d("showNotification[${account.acct}] creating notification(7)")
				
				var a = getNotificationLine(
					item.notification.type,
					item.notification.accountRef?.decoded_display_name ?: "?"
				)
				val acct = item.access_info.acct
				if(data_list.size == 1) {
					builder.setContentTitle(a)
					builder.setContentText(acct)
				} else {
					val header = context.getString(R.string.notification_count, data_list.size)
					builder.setContentTitle(header)
						.setContentText(a)
					
					val style = NotificationCompat.InboxStyle()
						.setBigContentTitle(header)
						.setSummaryText(acct)
					for(i in 0 .. 4) {
						if(i >= data_list.size) break
						item = data_list[i]
						a = getNotificationLine(
							item.notification.type,
							item.notification.accountRef?.decoded_display_name ?: "?"
						)
						style.addLine(a)
					}
					builder.setStyle(style)
				}
				
				log.d("showNotification[${account.acct}] set notification...")
				
				notification_manager.notify(notification_tag, NOTIFICATION_ID, builder.build())
			}
		}
		
		private fun processInjectedData() {
			while(inject_queue.size > 0) {
				
				val data = inject_queue.poll()
				
				val account = SavedAccount.loadAccount(context, data.account_db_id) ?: continue
				
				val nr = NotificationTracking.load(data.account_db_id)
				
				val duplicate_check = HashSet<EntityId>()
				
				val dst_array = ArrayList<JSONObject>()
				try {
					// まずキャッシュされたデータを処理する
					val last_data = nr.last_data
					if(last_data != null) {
						val array = last_data.toJsonArray()
						for(i in array.length() - 1 downTo 0) {
							val src = array.optJSONObject(i)
							val id = getEntityOrderId(account, src)
							if(id.notDefault) {
								dst_array.add(src)
								duplicate_check.add(id)
								log.d("add old. id=${id}")
							}
						}
					}
				} catch(ex : JSONException) {
					log.trace(ex)
				}
				
				for(item in data.list) {
					try {
						if(duplicate_check.contains(item.id)) {
							log.d("skip duplicate. id=${item.id}")
							continue
						}
						duplicate_check.add(item.id)
						
						val type = item.type
						if(! account.canNotificationShowing(type)) {
							log.d("skip by setting. id=${item.id}")
							continue
						}
						
						//
						val src = item.json
						src.put(KEY_TIME, item.time_created_at)
						dst_array.add(src)
					} catch(ex : JSONException) {
						log.trace(ex)
					}
					
				}
				
				// 新しい順にソート
				Collections.sort(dst_array, Comparator { a, b ->
					val la = a.parseLong(KEY_TIME) ?: 0
					val lb = b.parseLong(KEY_TIME) ?: 0
					// 新しい順
					if(la < lb) return@Comparator + 1
					if(la > lb) - 1 else 0
				})
				
				// 最新10件を保存
				val d = JSONArray()
				for(i in 0 .. 9) {
					if(i >= dst_array.size) {
						log.d("inject $i data.")
						break
					}
					d.put(dst_array[i])
				}
				nr.last_data = d.toString()
				
				nr.save()
			}
		}
		
		private fun deleteCacheData(db_id : Long) {
			
			if(SavedAccount.loadAccount(context, db_id) == null) {
				// 無効なdb_id
				return
			}
			
			val nr = NotificationTracking.load(db_id)
			nr.last_data = JSONArray().toString()
			nr.save()
		}
		
	}
	
}
