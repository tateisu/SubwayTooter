package jp.juggler.subwaytooter.util

import jp.juggler.util.coroutine.AppDispatchers.withTimeoutSafe
import jp.juggler.util.coroutine.launchDefault
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel

abstract class WorkerBase(
    private val waiter: Channel<Unit> = Channel(capacity = Channel.CONFLATED),
) {

    private val suspendJob: Job

    abstract fun cancel()

    abstract suspend fun run()

    suspend fun waitEx(ms: Long) = try {
        withTimeoutSafe(ms) { waiter.receive() }
    } catch (ignored: TimeoutCancellationException) {
        null
    }

    fun notifyEx() = waiter.trySend(Unit)

    val isAlive: Boolean
        get() = suspendJob.isActive

    init {
        suspendJob = launchDefault { run() }
    }
}
