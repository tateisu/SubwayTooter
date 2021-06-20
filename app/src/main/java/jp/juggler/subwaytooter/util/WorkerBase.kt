package jp.juggler.subwaytooter.util

import jp.juggler.util.launchDefault
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout

abstract class WorkerBase(
    private val waiter: Channel<Unit> = Channel(capacity = Channel.CONFLATED)
) {

    private val suspendJob: Job

    abstract fun cancel()

    abstract suspend fun run()

    suspend fun waitEx(ms: Long) = try {
        withTimeout(ms) { waiter.receive() }
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
