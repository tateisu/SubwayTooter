package jp.juggler.subwaytooter.util

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.global.appDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext

abstract class AsyncActivity : AppCompatActivity(), CoroutineScope {
    private lateinit var activityJob: Job

    override val coroutineContext: CoroutineContext
        get() = activityJob + appDispatchers.main

    override fun onCreate(savedInstanceState: Bundle?) {
        activityJob = Job()
        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        (activityJob + appDispatchers.default).cancel()
    }
}
