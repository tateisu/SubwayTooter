package jp.juggler.subwaytooter.util

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.dialog.ProgressDialogEx
import jp.juggler.util.LogCategory
import jp.juggler.util.dismissSafe
import jp.juggler.util.showToast
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

abstract class AsyncActivity : AppCompatActivity(), CoroutineScope {
	
	companion object {
		
		private val log = LogCategory("AsyncActivity")
	}
	
	private lateinit var job : Job
	
	override val coroutineContext : CoroutineContext
		get() = job + Dispatchers.Main
	
	override fun onCreate(savedInstanceState : Bundle?) {
		job = Job()
		super.onCreate(savedInstanceState)
	}
	
	override fun onDestroy() {
		super.onDestroy()
		(job + Dispatchers.Default).cancel()
	}
	
	fun <T : Any?> runWithProgress(
		caption : String,
		doInBackground : suspend CoroutineScope.(ProgressDialogEx) -> T,
		afterProc : suspend CoroutineScope.(result : T) -> Unit = {},
		progressInitializer : suspend CoroutineScope.(ProgressDialogEx) -> Unit = {},
		preProc : suspend CoroutineScope.() -> Unit = {},
		postProc : suspend CoroutineScope.() -> Unit = {}
	) {
		
		val progress = ProgressDialogEx(this)
		
		val task = async(Dispatchers.IO) {
			doInBackground(progress)
		}
		
		launch {
			try {
				preProc()
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			progress.setCancelable(true)
			progress.setOnCancelListener { task.cancel() }
			progress.isIndeterminateEx = true
			progress.setMessageEx("${caption}…")
			progressInitializer(progress)
			progress.show()
			
			try {
				val result = try {
					task.await()
				} catch(ex : CancellationException) {
					null
				}
				if(result != null) afterProc(result)
			} catch(ex : Throwable) {
				showToast(ex, "$caption failed.")
			} finally {
				progress.dismissSafe()
				try {
					postProc()
				} catch(ex : Throwable) {
					log.trace(ex)
				}
			}
		}
	}
}
