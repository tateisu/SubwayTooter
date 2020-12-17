package jp.juggler.util

import android.os.Handler
import android.os.Looper

////////////////////////////////////////////////////////////////////
// threading

val isMainThread : Boolean get() = Looper.getMainLooper().thread === Thread.currentThread()

// メインスレッドから呼び出された場合、それがsynchronizedの中だと GlobalScope.launch を使うのは良くない
fun runOnMainLooper(proc : () -> Unit) {
	val looper = Looper.getMainLooper()
	if(looper.thread === Thread.currentThread()) {
		proc()
	} else {
		Handler(looper).post { proc() }
	}
}

fun runOnMainLooperDelayed(delayMs : Long, proc : () -> Unit) {
	Handler(Looper.getMainLooper()).postDelayed({ proc() }, delayMs)
}
