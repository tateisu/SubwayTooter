package jp.juggler.subwaytooter.notification

import android.app.job.JobParameters
import android.app.job.JobService

// JobSchedulerから起動されるサービス。
class PollingService : JobService() {

    val pollingWorker by lazy { PollingWorker.getInstance(applicationContext) }

    override fun onDestroy() {
        super.onDestroy()
        pollingWorker.onJobServiceDestroy()
    }

    override fun onStartJob(params: JobParameters): Boolean {
        return pollingWorker.onStartJob(this, params)
    }

    override fun onStopJob(params: JobParameters): Boolean {
        return pollingWorker.onStopJob(params)
    }
}
