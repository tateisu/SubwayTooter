package jp.juggler.subwaytooter.util

import android.os.SystemClock

class Benchmark(
	val log : LogCategory,
	val caption :String,
	val minMs :Long = 33L
){
	val timeStart = SystemClock.elapsedRealtime()
	
	fun report(){
		val duration = SystemClock.elapsedRealtime() - timeStart
		if(duration >= minMs) log.d("$caption ${duration}ms")
	}
}
