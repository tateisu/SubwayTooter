package jp.juggler.subwaytooter.api

import android.app.Activity
import android.content.Context
import android.os.AsyncTask
import android.os.Handler
import android.os.SystemClock

import java.lang.ref.WeakReference
import java.text.NumberFormat

import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.dialog.ProgressDialogEx
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.dismissSafe

/*
	非同期タスク(TootTask)を実行します。
	- 内部でAsyncTaskを使います。Android Lintの警告を抑制します
	- ProgressDialogを表示します。抑制することも可能です。
	- TootApiClientの初期化を行います
	- TootApiClientからの進捗イベントをProgressDialogに伝達します。
*/

@Suppress("DEPRECATION", "MemberVisibilityCanPrivate")
class TootTaskRunner(
	context : Context,
	private val progress_style : Int = PROGRESS_SPINNER,
	private val progressSetupCallback : (progress : ProgressDialogEx) -> Unit = { _ -> }
) : TootApiCallback {
	
	companion object {
		
		const val PROGRESS_NONE = - 1
		const val PROGRESS_SPINNER = ProgressDialogEx.STYLE_SPINNER
		const val PROGRESS_HORIZONTAL = ProgressDialogEx.STYLE_HORIZONTAL
		
		private val percent_format : NumberFormat by lazy {
			val v = NumberFormat.getPercentInstance()
			v.maximumFractionDigits = 0
			v
		}
	}
	
	private class ProgressInfo {
		
		// HORIZONTALスタイルの場合、初期メッセージがないと後からメッセージを指定しても表示されない
		internal var message = " "
		internal var isIndeterminate = true
		internal var value = 0
		internal var max = 1
	}
	
	// has AsyncTask
	private class MyTask(
		private val runner : TootTaskRunner
	) : AsyncTask<Void, Void, TootApiResult?>() {
		
		var isActive : Boolean = true
		
		override fun doInBackground(vararg voids : Void) : TootApiResult? {
			val callback = runner.callback
			return if(callback == null) {
				TootApiResult("callback is null")
			} else {
				callback.background(runner.client)
			}
		}
		
		override fun onCancelled(result : TootApiResult?) {
			onPostExecute(result)
		}
		
		override fun onPostExecute(result : TootApiResult?) {
			isActive = false
			runner.dismissProgress()
			runner.callback?.handleResult(result)
		}
	}
	
	private val handler : Handler
	private val client : TootApiClient
	private val task : MyTask
	private val info = ProgressInfo()
	private var progress : ProgressDialogEx? = null
	private var progress_prefix : String? = null
	
	private val refContext : WeakReference<Context>
	
	private var callback : TootTask? = null
	private var last_message_shown : Long = 0
	
	private val proc_progress_message = object : Runnable {
		override fun run() {
			synchronized(this) {
				if(progress?.isShowing == true) {
					showProgressMessage()
				}
			}
		}
	}
	
	init {
		this.refContext = WeakReference(context)
		this.handler = Handler(context.mainLooper)
		this.client = TootApiClient(context, callback = this)
		this.task = MyTask(this)
	}
	
	val isActive : Boolean
		get() = task.isActive
	
	fun run(callback : TootTask) : TootTaskRunner {
		openProgress()
		
		this.callback = callback
		
		task.executeOnExecutor(App1.task_executor)
		
		return this
	}
	
	fun run(access_info : SavedAccount, callback : TootTask) : TootTaskRunner {
		client.account = access_info
		return run(callback)
	}
	
	fun run(instance : String, callback : TootTask) : TootTaskRunner {
		client.instance = instance
		return run(callback)
		
	}
	
	fun progressPrefix(s : String) : TootTaskRunner {
		this.progress_prefix = s
		return this
	}
	
	//////////////////////////////////////////////////////
	// implements TootApiClient.Callback
	
	override val isApiCancelled : Boolean
		get() = task.isCancelled
	
	override fun publishApiProgress(s : String) {
		synchronized(this) {
			info.message = s
			info.isIndeterminate = true
		}
		delayProgressMessage()
	}
	
	override fun publishApiProgressRatio(value : Int, max : Int) {
		synchronized(this) {
			info.isIndeterminate = false
			info.value = value
			info.max = max
		}
		delayProgressMessage()
	}
	
	//////////////////////////////////////////////////////
	// ProgressDialog
	
	private fun openProgress() {
		// open progress
		if(progress_style != PROGRESS_NONE) {
			val context = refContext.get()
			if(context != null && context is Activity) {
				val progress = ProgressDialogEx(context)
				this.progress = progress
				progress.setCancelable(true)
				progress.setOnCancelListener { task.cancel(true) }
				progress.setProgressStyle(progress_style)
				progressSetupCallback(progress)
				showProgressMessage()
				progress.show()
			}
		}
	}
	
	// ダイアログを閉じる
	private fun dismissProgress() {
		progress?.dismissSafe()
		progress = null
	}
	
	// ダイアログのメッセージを更新する
	// 初期化時とメッセージ更新時に呼ばれる
	private fun showProgressMessage() {
		val progress = this.progress ?: return
		
		val message = info.message.trim { it <= ' ' }
		val progress_prefix = this.progress_prefix
		progress.setMessage(
			if(progress_prefix == null || progress_prefix.isEmpty()) {
				message
			} else if(message.isEmpty()) {
				progress_prefix
			} else {
				"$progress_prefix\n$message"
			}
		)
		
		progress.isIndeterminate = info.isIndeterminate
		if(info.isIndeterminate) {
			progress.setProgressNumberFormat(null)
			progress.setProgressPercentFormat(null)
		} else {
			progress.progress = info.value
			progress.max = info.max
			progress.setProgressNumberFormat("%1$,d / %2$,d")
			progress.setProgressPercentFormat(percent_format)
		}
		
		last_message_shown = SystemClock.elapsedRealtime()
	}
	
	// 少し後にダイアログのメッセージを更新する
	// あまり頻繁に更新せず、しかし繰り返し呼ばれ続けても時々は更新したい
	// どのスレッドから呼ばれるか分からない
	private fun delayProgressMessage() {
		var wait = 100L + last_message_shown - SystemClock.elapsedRealtime()
		wait = if(wait < 0L) 0L else if(wait > 100L) 100L else wait
		
		synchronized(this) {
			handler.removeCallbacks(proc_progress_message)
			handler.postDelayed(proc_progress_message, wait)
		}
	}
	
}
