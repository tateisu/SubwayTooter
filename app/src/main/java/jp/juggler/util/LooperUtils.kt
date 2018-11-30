package jp.juggler.util

import android.os.Handler
import android.os.Looper

////////////////////////////////////////////////////////////////////
// threading

val isMainThread : Boolean get() = Looper.getMainLooper().thread === Thread.currentThread()

fun runOnMainLooper(proc : () -> Unit) {
	val looper = Looper.getMainLooper()
	if(looper.thread === Thread.currentThread()) {
		proc()
	} else {
		Handler(looper).post { proc() }
	}
}

fun runOnMainLooperDelayed(delayMs : Long, proc : () -> Unit) {
	val looper = Looper.getMainLooper()
	Handler(looper).postDelayed({ proc() }, delayMs)
}
