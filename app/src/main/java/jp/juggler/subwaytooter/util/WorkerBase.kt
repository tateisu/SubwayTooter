package jp.juggler.subwaytooter.util

abstract class WorkerBase : Thread() {

	abstract fun cancel()
	abstract override fun run()
	
	fun waitEx(ms : Long) {
		WaitNotifyHelper.waitEx(this,ms)
	}

	fun notifyEx() {
		WaitNotifyHelper.notifyEx(this)
	}
}
