package jp.juggler.subwaytooter.util

import jp.juggler.util.EndlessScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

abstract class WorkerBase(
	private val waiter : Channel<Unit> = Channel(capacity = Channel.CONFLATED)
) {

	private val suspendJob : Job

	abstract fun cancel()
	abstract suspend fun run()

	suspend fun waitEx(ms : Long) = try {
		withTimeout(ms) { waiter.receive() }
	}catch( ex:TimeoutCancellationException){
		null
	}

	fun notifyEx() = EndlessScope.launch { waiter.send(Unit) }

	val isAlive :Boolean
		get() = suspendJob.isActive

	init{
		suspendJob = EndlessScope.launch(Dispatchers.Default) { run() }
	}
}
