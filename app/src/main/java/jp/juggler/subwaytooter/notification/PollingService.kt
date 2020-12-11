package jp.juggler.subwaytooter.notification

import android.app.job.JobParameters
import android.app.job.JobService

// JobSchedulerから起動されるサービス。
class PollingService : JobService() {
	
	private lateinit var polling_worker : PollingWorker
	
	override fun onCreate() {
		super.onCreate()
		polling_worker = PollingWorker.getInstance(applicationContext)
	}
	
	override fun onDestroy() {
		super.onDestroy()
		polling_worker.onJobServiceDestroy()
	}
	
	override fun onStartJob(params : JobParameters) : Boolean {
		return polling_worker.onStartJob(this, params)
	}
	
	override fun onStopJob(params : JobParameters) : Boolean {
		return polling_worker.onStopJob(params)
	}
}
