package jp.juggler.subwaytooter.util

abstract class WorkerBase(private val waiter:Any?=null) : Thread() {

	abstract fun cancel()
	abstract override fun run()
	
	fun waitEx(ms : Long) {
		WaitNotifyHelper.waitEx(waiter ?: this,ms)
	}

	fun notifyEx() {
		WaitNotifyHelper.notifyEx(waiter ?:this)
	}
}
