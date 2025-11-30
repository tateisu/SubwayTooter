package jp.juggler.util.coroutine

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext

abstract class AsyncActivity : AppCompatActivity(), CoroutineScope {
    private lateinit var activityJob: Job

    override val coroutineContext: CoroutineContext
        get() = activityJob + AppDispatchers.MainImmediate

    override fun onCreate(savedInstanceState: Bundle?) {
        activityJob = Job()
        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        (activityJob + AppDispatchers.DEFAULT).cancel()
    }
}
