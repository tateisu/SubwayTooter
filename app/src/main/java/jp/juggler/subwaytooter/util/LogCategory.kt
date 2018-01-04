package jp.juggler.subwaytooter.util

import android.content.ContentValues
import android.content.res.Resources

import java.util.Collections
import java.util.IdentityHashMap

import jp.juggler.subwaytooter.table.LogData

class LogCategory(private val category : String) {
	
	companion object {
		
		/**
		 * Caption  for labeling causative exception stack traces
		 */
		private const val CAUSE_CAPTION = "Caused by: "
		
		/**
		 * Caption for labeling suppressed exception stack traces
		 */
		private const val SUPPRESSED_CAPTION = "Suppressed: "
	}
	
	private val cv = ContentValues()
	
//	fun addLog(level : Int, message : String) {
//		synchronized(cv) {
//			LogData.insert(cv, System.currentTimeMillis(), level, category, message)
//		}
//	}
	
	fun e(fmt : String, vararg args : Any?) {
		val message = if( args.isEmpty() ) fmt else String.format(fmt, *args)
		synchronized(cv) {
			LogData.insert(cv, System.currentTimeMillis(), LogData.LEVEL_ERROR, category, message)
		}
	}
	
	fun w(fmt : String, vararg args : Any?) {
		val message = if( args.isEmpty() ) fmt else String.format(fmt, *args)
		synchronized(cv) {
			LogData.insert(cv, System.currentTimeMillis(), LogData.LEVEL_WARNING, category, message)
		}
	}
	
	fun i(fmt : String, vararg args : Any?) {
		val message = if( args.isEmpty() ) fmt else String.format(fmt, *args)
		synchronized(cv) {
			LogData.insert(cv, System.currentTimeMillis(), LogData.LEVEL_INFO, category, message)
		}
	}
	
	fun v(fmt : String, vararg args : Any?) {
		val message = if( args.isEmpty() ) fmt else String.format(fmt, *args)
		synchronized(cv) {
			LogData.insert(cv, System.currentTimeMillis(), LogData.LEVEL_VERBOSE, category, message)
		}
	}
	
	fun d(fmt : String, vararg args : Any?) {
		val message = if( args.isEmpty() ) fmt else String.format(fmt, *args)
		synchronized(cv) {
			LogData.insert(cv, System.currentTimeMillis(), LogData.LEVEL_DEBUG, category, message)
		}
	}
	
	fun h(fmt : String, vararg args : Any?) {
		val message = if( args.isEmpty() ) fmt else String.format(fmt, *args)
		synchronized(cv) {
			LogData.insert(cv, System.currentTimeMillis(), LogData.LEVEL_HEARTBEAT, category, message)
		}
	}
	
	fun f(fmt : String, vararg args : Any?) {
		val message = if( args.isEmpty() ) fmt else String.format(fmt, *args)
		synchronized(cv) {
			LogData.insert(cv, System.currentTimeMillis(), LogData.LEVEL_FLOOD, category, message)
		}
	}

	////////////////////////
	// getString()
	
	fun e(res : Resources, string_id : Int, vararg args : Any?) {
		val fmt = res.getString(string_id, *args)
		synchronized(cv) {
			LogData.insert(cv, System.currentTimeMillis(), LogData.LEVEL_ERROR, category, fmt)
		}
	}
	
	fun w(res : Resources, string_id : Int, vararg args : Any?) {
		val fmt = res.getString(string_id, *args)
		synchronized(cv) {
			LogData.insert(cv, System.currentTimeMillis(), LogData.LEVEL_WARNING, category, fmt)
		}
	}
	
	fun i(res : Resources, string_id : Int, vararg args : Any?) {
		val fmt = res.getString(string_id, *args)
		synchronized(cv) {
			LogData.insert(cv, System.currentTimeMillis(), LogData.LEVEL_INFO, category, fmt)
		}
	}
	
	fun v(res : Resources, string_id : Int, vararg args : Any?) {
		val fmt = res.getString(string_id, *args)
		synchronized(cv) {
			LogData.insert(cv, System.currentTimeMillis(), LogData.LEVEL_VERBOSE, category, fmt)
		}
	}
	
	fun d(res : Resources, string_id : Int, vararg args : Any?) {
		val fmt = res.getString(string_id, *args)
		synchronized(cv) {
			LogData.insert(cv, System.currentTimeMillis(), LogData.LEVEL_DEBUG, category, fmt)
		}
	}
	
	fun h(res : Resources, string_id : Int, vararg args : Any?) {
		val fmt = res.getString(string_id, *args)
		synchronized(cv) {
			LogData.insert(cv, System.currentTimeMillis(), LogData.LEVEL_HEARTBEAT, category, fmt)
		}
	}
	
	fun f(res : Resources, string_id : Int, vararg args : Any?) {
		val fmt = res.getString(string_id, *args)
		synchronized(cv) {
			LogData.insert(cv, System.currentTimeMillis(), LogData.LEVEL_FLOOD, category, fmt)
		}
	}
	
	////////////////////////
	// exception

	fun e(ex : Throwable, fmt : String, vararg args : Any?) {
		val message = if( args.isEmpty() ) fmt else String.format(fmt, *args)
		synchronized(cv) {
			LogData.insert(cv, System.currentTimeMillis(), LogData.LEVEL_ERROR, category, message + String.format(":%s %s", ex.javaClass.simpleName, ex.message))
		}
	}
	
	fun e(ex : Throwable, res : Resources, string_id : Int, vararg args : Any?) {
		val message = res.getString(string_id, *args)
		synchronized(cv) {
			LogData.insert(cv, System.currentTimeMillis(), LogData.LEVEL_ERROR, category, message + String.format(":%s %s", ex.javaClass.simpleName, ex.message))
		}
	}
	
	fun trace(ex : Throwable) {
		//// ex.printStackTrace();
		
		// Guard against malicious overrides of Throwable.equals by
		// using a Set with identity equality semantics.
		val dejaVu = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
		dejaVu.add(ex)
		
		// Print our stack trace
		e(ex.toString())
		
		val trace = ex.stackTrace
		for(traceElement in trace) {
			e("\tat " + traceElement)
		}
		
		// Print suppressed exceptions, if any
		for(se in ex.suppressed)
			printEnclosedStackTrace(se, trace, SUPPRESSED_CAPTION, "\t", dejaVu)
		
		// Print cause, if any
		val ourCause = ex.cause
		if(ourCause != null)
			printEnclosedStackTrace(ourCause, trace, CAUSE_CAPTION, "", dejaVu)
	}
	
	/**
	 * Print our stack trace as an enclosed exception for the specified
	 * stack trace.
	 */
	private fun printEnclosedStackTrace(
		ex : Throwable, enclosingTrace : Array<StackTraceElement>, caption : String, prefix : String, dejaVu : MutableSet<Throwable>
	) {
		if(dejaVu.contains(ex)) {
			e("\t[CIRCULAR REFERENCE:$ex]")
		} else {
			dejaVu.add(ex)
			// Compute number of frames in common between this and enclosing trace
			val trace = ex.stackTrace
			var m = trace.size - 1
			var n = enclosingTrace.size - 1
			while(m >= 0 && n >= 0 && trace[m] == enclosingTrace[n]) {
				m --
				n --
			}
			val framesInCommon = trace.size - 1 - m
			
			// Print our stack trace
			e(prefix + caption + ex)
			for(i in 0 .. m)
				e(prefix + "\tat " + trace[i])
			if(framesInCommon != 0)
				e("$prefix\t... $framesInCommon more")
			
			// Print suppressed exceptions, if any
			for(ex2 in ex.suppressed)
				printEnclosedStackTrace(ex2, trace, SUPPRESSED_CAPTION, prefix + "\t", dejaVu)
			
			// Print cause, if any
			val ourCause = ex.cause
			if(ourCause != null)
				printEnclosedStackTrace(ourCause, trace, CAUSE_CAPTION, prefix, dejaVu)
		}
	}
	

	
}