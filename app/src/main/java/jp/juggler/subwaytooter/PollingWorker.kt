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
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.MutedApp
import jp.juggler.subwaytooter.table.MutedWord
import jp.juggler.subwaytooter.table.NotificationTracking
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.*
import okhttp3.Call
import okhttp3.Request
import okhttp3.RequestBody

class PollingWorker private constructor(c : Context) {
	
	interface JobStatusCallback {
		fun onStatus(sv : String)
	}
	
	companion object {
		internal val log = LogCategory("PollingWorker")
		
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
			if( s == null ){
				s = PollingWorker(applicationContext)
				sInstance = s
			}
			return s
		}
		
		//////////////////////////////////////////////////////////////////////
		// タスクの管理
		
		val task_list = TaskList()
		
		fun scheduleJob(context : Context, job_id : Int) {
			
			val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
				?: throw NotImplementedError("missing JobScheduler system service")
			
			val component = ComponentName(context, PollingService::class.java)
			
			val builder = JobInfo.Builder(job_id, component)
				.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
			
			if(job_id == JOB_POLLING) {
				if(Build.VERSION.SDK_INT >= 24) {
					builder.setPeriodic(60000L * 5, 60000L * 10)
				} else {
					builder.setPeriodic(60000L * 5)
				}
				builder.setPersisted(true)
			} else {
				builder
					.setMinimumLatency(0)
					.setOverrideDeadline(60000L)
			}
			
			scheduler.schedule(builder.build())
		}
		
		// タスクの追加
		private fun addTask(context : Context, removeOld : Boolean, task_id : Int, taskDataArg : JSONObject?) {
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
		
		fun injectData(context : Context, account_db_id : Long, src : ArrayList<TootNotification>) {
			
			if(src.isEmpty()) return
			
			val id = InjectData()
			id.account_db_id = account_db_id
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
			log.d("handleFCMMessage: start. tag=%s", tag)
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
					log.d("handleFCMMessage: JOB_FCM completed. time=%.2f", (now - time_start) / 1000f)
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
	}
	
	internal val context : Context
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
		
		val context = c.applicationContext
		this.context = context
		
		this.connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
			?: throw NotImplementedError("missing ConnectivityManager system service")
		
		this.notification_manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
			?: throw NotImplementedError("missing NotificationManager system service")
		
		this.scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
			?: throw NotImplementedError("missing JobScheduler system service")
		
		this.power_manager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
			?: throw NotImplementedError("missing PowerManager system service")
		
		// WifiManagerの取得時はgetApplicationContext を使わないとlintに怒られる
		this.wifi_manager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
			?: throw NotImplementedError("missing WifiManager system service")
		
		power_lock = power_manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, PollingWorker::class.java.name)
		power_lock.setReferenceCounted(false)
		
		wifi_lock = wifi_manager.createWifiLock(PollingWorker::class.java.name)
		wifi_lock.setReferenceCounted(false)
		
		// クラッシュレポートによると App1.onCreate より前にここを通る場合がある
		// データベースへアクセスできるようにする
		App1.prepare(context)
		this.pref = App1.pref
		this.handler = Handler(context.mainLooper)
		
		//
		worker = Worker()
		worker.start()
	}
	
	internal inner class Worker : WorkerBase() {
		
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
			log.e("worker thread start.")
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
			log.e("worker thread end.")
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
						log.w("addJob: jobId=%s, old job cancelled.", jobId)
						// 同じジョブをすぐに始めるのだからrescheduleはfalse
						itemOld.cancel(false)
						it.remove()
					}
				}
			}
			log.d("addJob: jobId=%s, add to list.", jobId)
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
					log.w("onStopJob: jobId=%s, set cancel flag.")
					// リソースがなくてStopされるのだからrescheduleはtrue
					item.cancel(true)
					it.remove()
					return item.mReschedule.get()
				}
			}
		}
		
		// 該当するジョブを依頼されていない
		log.w("onStopJob: jobId=%s, not started..")
		return false
		// return True to indicate to the JobManager whether you'd like to reschedule this job based on the retry criteria provided at job creation-time.
		// return False to drop the job. Regardless of the value returned, your job must stop executing.
	}
	
	internal class JobCancelledException : RuntimeException("job is cancelled.")
	
	internal inner class JobItem {
		val jobId : Int
		private val refJobService : WeakReference<JobService>?
		private val jobParams : JobParameters?
		val mJobCancelled_ = AtomicBoolean()
		val mReschedule = AtomicBoolean()
		val mWorkerAttached = AtomicBoolean()
		
		val bPollingRequired = AtomicBoolean(false)
		lateinit var muted_app : HashSet<String>
		lateinit var muted_word : WordTrieTree
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
				log.d("(JobItem.run jobId=%s", jobId)
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
				
				// タスクがあれば処理する
				while(true) {
					if(isJobCancelled) throw JobCancelledException()
					val data = task_list.next(context) ?: break
					val task_id = data.optInt(EXTRA_TASK_ID, 0)
					TaskRunner().runTask(this@JobItem, task_id, data)
				}
				
				if(! isJobCancelled && ! bPollingComplete && jobId == JOB_POLLING) {
					// タスクがなかった場合でも定期実行ジョブからの実行ならポーリングを行う
					TaskRunner().runTask(this@JobItem, TASK_POLLING, null)
				}
				job_status.set("make next schedule.")
				
				if(! isJobCancelled && bPollingComplete) {
					// ポーリングが完了したのならポーリングが必要かどうかに合わせてジョブのスケジュールを変更する
					if(! bPollingRequired.get()) {
						log.d("polling job is no longer required.")
						try {
							scheduler.cancel(JOB_POLLING)
						} catch(ex : Throwable) {
							log.trace(ex)
						}
						
					} else {
						var bRegistered = false
						for(info in scheduler.allPendingJobs) {
							if(info.id == JOB_POLLING) {
								bRegistered = true
								break
							}
						}
						if(! bRegistered) {
							scheduleJob(context, JOB_POLLING)
							log.d("polling job is registered!")
						}
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
							log.d("sending jobFinished. reschedule=%s", mReschedule.get())
							jobService.jobFinished(jobParams, mReschedule.get())
						}
					} catch(ex : Throwable) {
						log.trace(ex)
						log.e(ex, "jobFinished failed(1).")
					}
				})
			}
			log.d(")JobItem.run jobId=%s, cancel=%s", jobId, isJobCancelled)
		}
		
		private fun checkNetwork() : Boolean {
			val ni = connectivityManager.activeNetworkInfo
			return if(ni == null) {
				log.d("checkNetwork: getActiveNetworkInfo() returns null.")
				false
			} else {
				val state = ni.state
				val detail = ni.detailedState
				log.d("checkNetwork: state=%s,detail=%s", state, detail)
				if(state != NetworkInfo.State.CONNECTED) {
					log.d("checkNetwork: not connected.")
					false
				} else {
					true
				}
			}
		}
	}
	
	internal inner class TaskRunner {
		
		var mCustomStreamListenerSecret : String? = null
		var mCustomStreamListenerSettingString : String? = null
		private var mCustomStreamListenerSetting : JsonObject? = null
		
		lateinit var job : JobItem
		private var taskId : Int = 0
		
		val error_instance = ArrayList<String>()
		
		// インストールIDを生成する前に、各データの通知登録キャッシュをクリアする
		// トークンがまだ生成されていない場合、このメソッドは null を返します。
		private val installId : String?
			get() {
				val prefDevice = PrefDevice.prefDevice(context)
				
				var sv = prefDevice.getString(PrefDevice.KEY_INSTALL_ID, null)
				if( sv != null && sv.isNotEmpty() ) return sv
				SavedAccount.clearRegistrationCache()
				
				try {
					var device_token = prefDevice.getString(PrefDevice.KEY_DEVICE_TOKEN, null)
					if( device_token == null || device_token.isEmpty() ) {
						try {
							device_token = FirebaseInstanceId.getInstance().token
							if( device_token == null || device_token.isEmpty()) {
								log.e("getInstallId: missing device token.")
								return null
							} else {
								prefDevice.edit().putString(PrefDevice.KEY_DEVICE_TOKEN, device_token).apply()
							}
						} catch(ex : Throwable) {
							log.e("getInstallId: could not get device token.")
							log.trace(ex)
							return null
						}
						
					}
					
					val request = Request.Builder()
						.url(APP_SERVER + "/counter")
						.build()
					
					val call = App1.ok_http_client.newCall(request)
					job.current_call = call
					
					val response = call.execute()
					val body = response.body()?.string()
					
					if(! response.isSuccessful || body?.isEmpty() != false ) {
						log.e(TootApiClient.formatResponse(response, "getInstallId: get /counter failed."))
						return null
					}
					
					sv = Utils.digestSHA256(device_token + UUID.randomUUID() + body)
					prefDevice.edit().putString(PrefDevice.KEY_INSTALL_ID, sv).apply()
					
					return sv
					
				} catch(ex : Throwable) {
					log.trace(ex)
					return null
				}
				
			}
		
		fun runTask(job : JobItem, taskId : Int, taskData : JSONObject?) {
			try {
				log.e("(runTask: taskId=%s", taskId)
				job_status.set("start task " + taskId)
				
				this.job = job
				this.taskId = taskId
				
				var process_db_id = - 1L
				
				if(taskId == TASK_APP_DATA_IMPORT_BEFORE) {
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
				} else if(taskId == TASK_APP_DATA_IMPORT_AFTER) {
					NotificationTracking.resetPostAll()
					mBusyAppDataImportAfter.set(false)
					// fall
				}
				
				// アプリデータのインポート処理がビジーな間、他のジョブは実行されない
				if(mBusyAppDataImportBefore.get()) return
				if(mBusyAppDataImportAfter.get()) return
				
				if(taskId == TASK_FCM_DEVICE_TOKEN) {
					// デバイストークンが更新された
					// アプリサーバへの登録をやり直す
					
				} else if(taskId == TASK_FCM_MESSAGE) {
					var bDone = false
					val tag = Utils.optStringX(taskData,EXTRA_TAG)
					if(tag != null) {
						for(sa in SavedAccount.loadByTag(context, tag)) {
							NotificationTracking.resetLastLoad(sa.db_id)
							process_db_id = sa.db_id
							bDone = true
						}
					}
					if(! bDone) {
						// タグにマッチする情報がなかった場合、全部読み直す
						NotificationTracking.resetLastLoad()
					}
					
				} else if(taskId == TASK_NOTIFICATION_CLEAR) {
					val db_id = Utils.optLongX(taskData, EXTRA_DB_ID, - 1L)
					deleteCacheData(db_id)
					
				} else if(taskId == TASK_DATA_INJECTED) {
					processInjectedData()
					
				} else if(taskId == TASK_BOOT_COMPLETED) {
					NotificationTracking.resetPostAll()
				} else if(taskId == TASK_PACKAGE_REPLACED) {
					NotificationTracking.resetPostAll()
					
				} else if(taskId == TASK_NOTIFICATION_DELETE) {
					val db_id = Utils.optLongX(taskData, EXTRA_DB_ID, - 1L)
					log.d("Notification deleted! db_id=%s", db_id)
					NotificationTracking.updateRead(db_id)
					return
				} else if(taskId == TASK_NOTIFICATION_CLICK) {
					val db_id = Utils.optLongX(taskData, EXTRA_DB_ID, - 1L)
					log.d("Notification clicked! db_id=%s", db_id)
					
					// 通知をキャンセル
					notification_manager.cancel(db_id.toString(), NOTIFICATION_ID)
					// DB更新処理
					NotificationTracking.updateRead(db_id)
					return
				}
				
				loadCustomStreamListenerSetting()
				
				job_status.set("make install id")
				
				// インストールIDを生成する
				// インストールID生成時にSavedAccountテーブルを操作することがあるので
				// アカウントリストの取得より先に行う
				if(job.install_id == null) {
					job.install_id = installId
				}
				
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
					val set = TreeSet<String>()
					val it = thread_list.iterator()
					while(it.hasNext()) {
						val t = it.next()
						if(! t.isAlive) {
							it.remove()
							continue
						}
						set.add(t.account.host)
						if(job.isJobCancelled) {
							t.cancel()
						}
					}
					val remain = thread_list.size
					if(remain <= 0) break
					//
					val sb = StringBuilder()
					for(s in set) {
						if(sb.isNotEmpty()) sb.append(", ")
						sb.append(s)
					}
					job_status.set("waiting " + sb.toString())
					//
					job.waitWorkerThread(if(job.isJobCancelled) 50L else 1000L)
				}
				
				synchronized(error_instance) {
					createErrorNotification(error_instance)
				}
				
				if(! job.isJobCancelled) job.bPollingComplete = true
				
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "task execution failed.")
			} finally {
				log.e(")runTask: taskId=%s", taskId)
				job_status.set("end task " + taskId)
			}
		}
		
		private fun createErrorNotification(error_instance : ArrayList<String>) {
			if(error_instance.isEmpty()) {
				return
			}
			
			// 通知タップ時のPendingIntent
			val intent_click = Intent(context, ActCallback::class.java)
			intent_click.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			val pi_click = PendingIntent.getActivity(context, 3, intent_click, PendingIntent.FLAG_UPDATE_CURRENT)
			
			val builder = if(Build.VERSION.SDK_INT >= 26) {
				// Android 8 から、通知のスタイルはユーザが管理することになった
				// NotificationChannel を端末に登録しておけば、チャネルごとに管理画面が作られる
				val channel = NotificationHelper.createNotificationChannel(
					context, "ErrorNotification", "Error", null, 2 /* NotificationManager.IMPORTANCE_LOW */
				)
				NotificationCompat.Builder(context, channel.id)
			} else {
				NotificationCompat.Builder(context, "not_used")
			}
			
			builder
				.setContentIntent(pi_click)
				.setAutoCancel(true)
				.setSmallIcon(R.drawable.ic_notification) // ここは常に白テーマのアイコンを使う
				.setColor(ContextCompat.getColor(context, R.color.Light_colorAccent)) // ここは常に白テーマの色を使う
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
			val jsonString = pref.getString(Pref.KEY_STREAM_LISTENER_CONFIG_DATA, null)
			mCustomStreamListenerSettingString = jsonString
			if( jsonString != null && jsonString.isNotEmpty() ) {
				try {
					mCustomStreamListenerSetting = JsonValue.readHjson(jsonString).asObject()
					mCustomStreamListenerSecret = pref.getString(Pref.KEY_STREAM_LISTENER_SECRET, null)
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
			}
		}
		
		internal inner class AccountThread(val account : SavedAccount) : Thread(), CurrentCallCallback {
			
			private var current_call : Call? = null
			
			val client = TootApiClient(context, callback=object : TootApiCallback {
				override val isApiCancelled : Boolean
					get() = job.isJobCancelled
			})
			
			private lateinit var nr : NotificationTracking
			private val duplicate_check = HashSet<Long>()
			private val dst_array = ArrayList<JSONObject>()
			
			private var nid_last_show = - 1L
			
			init {
				client.currentCallCallback = this
			}
			
			override fun onCallCreated(call : Call) {
				current_call = call
			}
			
			fun cancel() {
				try {
					current_call ?.cancel()
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
			}
			
			override fun run() {
				try {
					if(account.isPseudo) return
					
					if(! account.notification_mention
						&& ! account.notification_boost
						&& ! account.notification_favourite
						&& ! account.notification_follow) {
						unregisterDeviceToken()
						return
					}
					
					if(registerDeviceToken()) {
						return
					}
					
					job.bPollingRequired.set(true)
					
					if(job.isJobCancelled) return
					
					val data_list = ArrayList<Data>()
					checkAccount(data_list, job.muted_app, job.muted_word)
					
					if(job.isJobCancelled) return
					
					showNotification(data_list)
					
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
					if( install_id == null || install_id.isEmpty() ) {
						log.d("unregisterDeviceToken: missing install_id")
						return
					}
					
					val tag = account.notification_tag
					if( tag == null || tag.isEmpty() ) {
						log.d("unregisterDeviceToken: missing notification_tag")
						return
					}
					
					val post_data = ("instance_url=" + Uri.encode("https://" + account.host)
						+ "&app_id=" + Uri.encode(context.packageName)
						+ "&tag=" + tag)
					
					val request = Request.Builder()
						.url(APP_SERVER + "/unregister")
						.post(RequestBody.create(TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED, post_data))
						.build()
					
					val call = App1.ok_http_client.newCall(request)
					current_call = call
					
					val response = call.execute()
					
					log.e("unregisterDeviceToken: %s", response)
					
					if(response.isSuccessful) {
						account.register_key = SavedAccount.REGISTER_KEY_UNREGISTERED
						account.register_time = 0L
						account.saveRegisterKey()
					}
					
				} catch(ex : Throwable) {
					log.trace(ex)
				}
				
			}
			
			// 定期的な通知更新が不要なら真を返す
			private fun registerDeviceToken() : Boolean {
				try {
					// ネットワーク的な事情でインストールIDを取得できなかったのなら、何もしない
					val install_id = job.install_id
					if(install_id == null || install_id.isEmpty() ) {
						log.d("registerDeviceToken: missing install id")
						return false
					}
					
					val prefDevice = PrefDevice.prefDevice(context)
					
					val device_token = prefDevice.getString(PrefDevice.KEY_DEVICE_TOKEN, null)
					if(device_token == null || device_token.isEmpty() ) {
						log.d("registerDeviceToken: missing device_token")
						return false
					}
					
					val access_token = account.getAccessToken()
					if( access_token == null || access_token.isEmpty()) {
						log.d("registerDeviceToken: missing access_token")
						return false
					}
					
					var tag : String? = account.notification_tag
					
					if(SavedAccount.REGISTER_KEY_UNREGISTERED == account.register_key) {
						tag = null
					}
					
					if( tag == null || tag.isEmpty() ) {
						account.notification_tag = Utils.digestSHA256(job.install_id + account.db_id + account.acct)
						tag = account.notification_tag
						account.saveNotificationTag()
					}
					
					val reg_key = Utils.digestSHA256(
						tag
							+ access_token
							+ device_token
							+ (if(mCustomStreamListenerSecret == null) "" else mCustomStreamListenerSecret)
							+ if(mCustomStreamListenerSettingString == null) "" else mCustomStreamListenerSettingString
					)
					val now = System.currentTimeMillis()
					if(reg_key == account.register_key && now - account.register_time < 3600000 * 3) {
						// タグやトークンが同一なら、前回登録に成功してから一定時間は再登録しない
						log.d("registerDeviceToken: already registered.")
						return false
					}
					
					// サーバ情報APIを使う
					val post_data = StringBuilder()
					
					post_data.append("instance_url=").append(Uri.encode("https://" + account.host))
					
					post_data.append("&app_id=").append(Uri.encode(context.packageName))
					
					post_data.append("&tag=").append(tag)
					
					post_data.append("&access_token=").append(access_token)
					
					post_data.append("&device_token=").append(device_token)
					
					val jsonString = mCustomStreamListenerSettingString
					val appSecret = mCustomStreamListenerSecret
					
					if( jsonString !=null && appSecret != null ) {
						post_data.append("&user_config=").append(Uri.encode(jsonString))
						post_data.append("&app_secret=").append(Uri.encode(appSecret))
					}
					
					val request = Request.Builder()
						.url(APP_SERVER + "/register")
						.post(RequestBody.create(TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED, post_data.toString()))
						.build()
					
					val call = App1.ok_http_client.newCall(request)
					current_call = call
					
					val response = call.execute()
					
					var body : String? = null
					try {
						body = response.body()?.string()
					} catch(ignored : Throwable) {
					}
					
					log.e("registerDeviceToken: %s (%s)", response, body ?: "" )
					
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
				
				return false
			}
			
			private fun checkAccount(
				data_list : ArrayList<Data>, muted_app : HashSet<String>, muted_word : WordTrieTree
			) {
				nr = NotificationTracking.load(account.db_id)
				
				val parser = TootParser(context, account)
				
				// まずキャッシュされたデータを処理する
				if(nr.last_data != null) {
					try {
						val array = JSONArray(nr.last_data)
						for(i in array.length() - 1 downTo 0) {
							if(job.isJobCancelled) return
							val src = array.optJSONObject(i)
							update_sub(src, data_list, muted_app, muted_word, parser)
						}
					} catch(ex : JSONException) {
						log.trace(ex)
					}
					
				}
				
				if(job.isJobCancelled) return
				
				// 前回の更新から一定時刻が経過したら新しいデータを読んでリストに追加する
				val now = System.currentTimeMillis()
				if(now - nr.last_load >= 60000L * 2) {
					nr.last_load = now
					
					client.account = account
					
					for(nTry in 0 .. 3) {
						if(job.isJobCancelled) return
						
						var path = PATH_NOTIFICATIONS
						if(nid_last_show != - 1L) {
							path = path + "?since_id=" + nid_last_show
						}
						
						val result = client.request(path)
						if(result == null) {
							log.d("cancelled.")
							break
						}
						val array = result.jsonArray
						if(array != null) {
							try {
								for(i in array.length() - 1 downTo 0) {
									val src = array.optJSONObject(i)
									update_sub(src, data_list, muted_app, muted_word, parser)
								}
							} catch(ex : JSONException) {
								log.trace(ex)
							}
							
							break
						} else {
							log.d("error. %s", result.error)
							
							val sv = result.error
							if(sv != null && sv.contains("Timeout") && ! account.dont_show_timeout) {
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
				
				Collections.sort(dst_array, Comparator { a, b ->
					val la = Utils.optLongX(a, KEY_TIME, 0)
					val lb = Utils.optLongX(b, KEY_TIME, 0)
					// 新しい順
					if(la < lb) return@Comparator + 1
					if(la > lb) - 1 else 0
				})
				
				if(job.isJobCancelled) return
				
				val d = JSONArray()
				for(i in 0 .. 9) {
					if(i >= dst_array.size) break
					d.put(dst_array[i])
				}
				nr.last_data = d.toString()
				nr.save()
			}
			
			@Throws(JSONException::class)
			private fun update_sub(
				src : JSONObject,
				data_list : ArrayList<Data>,
				muted_app : HashSet<String>,
				muted_word : WordTrieTree,
				parser : TootParser
			) {
				
				if(nr.nid_read == 0L || nr.nid_show == 0L) {
					log.d("update_sub account_db_id=%s, nid_read=%s, nid_show=%s", account.db_id, nr.nid_read, nr.nid_show)
				}
				
				val id = Utils.optLongX(src, "id")
				
				if(duplicate_check.contains(id)) return
				duplicate_check.add(id)
				
				if(id > nid_last_show) {
					nid_last_show = id
				}
				
				val type = Utils.optStringX(src, "type")
				
				if(id <= nr.nid_read) {
					// log.d("update_sub: ignore data that id=%s, <= read id %s ",id,nr.nid_read);
					return
				} else {
					log.d("update_sub: found data that id=%s, > read id %s ", id, nr.nid_read)
				}
				
				if(id > nr.nid_show) {
					log.d("update_sub: found new data that id=%s, greater than shown id %s ", id, nr.nid_show)
					// 種別チェックより先に「表示済み」idの更新を行う
					nr.nid_show = id
				}
				
				if(! account.notification_mention && TootNotification.TYPE_MENTION == type
					|| ! account.notification_boost && TootNotification.TYPE_REBLOG == type
					|| ! account.notification_favourite && TootNotification.TYPE_FAVOURITE == type
					|| ! account.notification_follow && TootNotification.TYPE_FOLLOW == type) {
					return
				}
				
				val notification = parser.notification(src) ?: return
				
				run {
					val status = notification.status
					if(status != null) {
						if(status.checkMuted(muted_app, muted_word)) {
							return
						}
					}
				}
				
				//
				val data = Data(account, notification)
				data_list.add(data)
				//
				src.put(KEY_TIME, data.notification.time_created_at)
				dst_array.add(src)
			}
			
			private fun getNotificationLine(type : String, display_name : CharSequence) : String {
				if(TootNotification.TYPE_FAVOURITE == type) {
					return "- " + context.getString(R.string.display_name_favourited_by, display_name)
				}
				if(TootNotification.TYPE_REBLOG == type) {
					return "- " + context.getString(R.string.display_name_boosted_by, display_name)
				}
				if(TootNotification.TYPE_MENTION == type) {
					return "- " + context.getString(R.string.display_name_replied_by, display_name)
				}
				return if(TootNotification.TYPE_FOLLOW == type) {
					"- " + context.getString(R.string.display_name_followed_by, display_name)
				} else "- " + "?"
			}
			
			private fun showNotification(data_list : ArrayList<Data>) {
				
				val notification_tag = account.db_id.toString()
				if(data_list.isEmpty()) {
					log.d("showNotification[%s] cancel notification.", account.acct)
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
				
				if(item.notification.time_created_at == nt.post_time && item.notification.id == nt.post_id) {
					// 先頭にあるデータが同じなら、通知を更新しない
					// このマーカーは端末再起動時にリセットされるので、再起動後は通知が出るはず
					
					log.d("showNotification[%s] id=%s is already shown.", account.acct, item.notification.id)
					
					return
				}
				
				nt.updatePost(item.notification.id, item.notification.time_created_at)
				
				log.d("showNotification[%s] creating notification(1)", account.acct)
				
				// 通知タップ時のPendingIntent
				val intent_click = Intent(context, ActCallback::class.java)
				intent_click.action = ActCallback.ACTION_NOTIFICATION_CLICK
				intent_click.data = Uri.parse("subwaytooter://notification_click/?db_id=" + account.db_id)
				intent_click.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				val pi_click = PendingIntent.getActivity(context, 256 + account.db_id.toInt(), intent_click, PendingIntent.FLAG_UPDATE_CURRENT)
				
				// 通知を消去した時のPendingIntent
				val intent_delete = Intent(context, EventReceiver::class.java)
				intent_delete.action = EventReceiver.ACTION_NOTIFICATION_DELETE
				intent_delete.putExtra(EXTRA_DB_ID, account.db_id)
				val pi_delete = PendingIntent.getBroadcast(context, Integer.MAX_VALUE - account.db_id.toInt(), intent_delete, PendingIntent.FLAG_UPDATE_CURRENT)
				
				log.d("showNotification[%s] creating notification(2)", account.acct)
				
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
					.setColor(ContextCompat.getColor(context, R.color.Light_colorAccent)) // ここは常に白テーマの色を使う
					.setWhen(item.notification.time_created_at)
				
				// Android 7.0 ではグループを指定しないと勝手に通知が束ねられてしまう。
				// 束ねられた通知をタップしても pi_click が実行されないので困るため、
				// アカウント別にグループキーを設定する
				builder.setGroup(context.packageName + ":" + account.acct)
				
				log.d("showNotification[%s] creating notification(3)", account.acct)
				
				if(Build.VERSION.SDK_INT < 26) {
					
					var iv = 0
					
					if(pref.getBoolean(Pref.KEY_NOTIFICATION_SOUND, true)) {
						
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
					
					log.d("showNotification[%s] creating notification(4)", account.acct)
					
					if(pref.getBoolean(Pref.KEY_NOTIFICATION_VIBRATION, true)) {
						iv = iv or NotificationCompat.DEFAULT_VIBRATE
					}
					
					log.d("showNotification[%s] creating notification(5)", account.acct)
					
					if(pref.getBoolean(Pref.KEY_NOTIFICATION_LED, true)) {
						iv = iv or NotificationCompat.DEFAULT_LIGHTS
					}
					
					log.d("showNotification[%s] creating notification(6)", account.acct)
					
					builder.setDefaults(iv)
				}
				
				log.d("showNotification[%s] creating notification(7)", account.acct)
				
				var a = getNotificationLine(item.notification.type, item.notification.account?.decoded_display_name ?: "?")
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
						a = getNotificationLine(item.notification.type, item.notification.account?.decoded_display_name ?: "?")
						style.addLine(a)
					}
					builder.setStyle(style)
				}
				
				log.d("showNotification[%s] set notification...", account.acct)
				
				notification_manager.notify(notification_tag, NOTIFICATION_ID, builder.build())
			}
		}
		
		private fun processInjectedData() {
			while(inject_queue.size > 0) {
				
				val data = inject_queue.poll()
				
				val account = SavedAccount.loadAccount(context, data.account_db_id) ?: continue
				
				val nr = NotificationTracking.load(data.account_db_id)
				
				val duplicate_check = HashSet<Long>()
				
				val dst_array = ArrayList<JSONObject>()
				if(nr.last_data != null) {
					// まずキャッシュされたデータを処理する
					try {
						val array = JSONArray(nr.last_data)
						for(i in array.length() - 1 downTo 0) {
							val src = array.optJSONObject(i)
							val id = Utils.optLongX(src, "id")
							dst_array.add(src)
							duplicate_check.add(id)
							log.d("add old. id=%s", id)
						}
					} catch(ex : JSONException) {
						log.trace(ex)
					}
					
				}
				for(item in data.list) {
					try {
						if(duplicate_check.contains(item.id)) {
							log.d("skip duplicate. id=%s", item.id)
							continue
						}
						duplicate_check.add(item.id)
						
						val type = item.type
						
						if(! account.notification_mention && TootNotification.TYPE_MENTION == type
							|| ! account.notification_boost && TootNotification.TYPE_REBLOG == type
							|| ! account.notification_favourite && TootNotification.TYPE_FAVOURITE == type
							|| ! account.notification_follow && TootNotification.TYPE_FOLLOW == type) {
							log.d("skip by setting. id=%s", item.id)
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
					val la = Utils.optLongX(a, KEY_TIME, 0)
					val lb = Utils.optLongX(b, KEY_TIME, 0)
					// 新しい順
					if(la < lb) return@Comparator + 1
					if(la > lb) - 1 else 0
				})
				
				// 最新10件を保存
				val d = JSONArray()
				for(i in 0 .. 9) {
					if(i >= dst_array.size) {
						log.d("inject %s data", i)
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
