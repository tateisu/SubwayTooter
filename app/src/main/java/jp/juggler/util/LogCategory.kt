package jp.juggler.util

import android.content.res.Resources
import android.util.Log

class LogCategory(category : String) {
	
	companion object {
		private const val TAG = "SubwayTooter"
		
		private fun format(fmt : String, args : Array<out Any?>) =
			if(args.isEmpty()) fmt else String.format(fmt, *args)
		
		private fun format(res : Resources, string_id : Int, args : Array<out Any?>) =
			res.getString(string_id, *args)
		
		private fun Throwable.withCaption(caption : String) =
			"${caption} :${javaClass.simpleName} ${message}"
	}
	
	private val tag = "$TAG:$category"
	
	fun e(fmt : String, vararg args : Any?) {
		Log.e(tag, format(fmt, args))
	}
	
	fun w(fmt : String, vararg args : Any?) {
		Log.w(tag, format(fmt, args))
	}
	
	fun i(fmt : String, vararg args : Any?) {
		Log.i(tag, format(fmt, args))
	}
	
	fun d(fmt : String, vararg args : Any?) {
		Log.d(tag, format(fmt, args))
	}
	
	fun v(fmt : String, vararg args : Any?) {
		Log.v(tag, format(fmt, args))
	}
	
	////////////////////////
	// getString()
	
	fun e(res : Resources, string_id : Int, vararg args : Any?) {
		Log.e(tag, format(res, string_id, args))
	}
	
	fun w(res : Resources, string_id : Int, vararg args : Any?) {
		Log.w(tag, format(res, string_id, args))
	}
	
	fun i(res : Resources, string_id : Int, vararg args : Any?) {
		Log.i(tag, format(res, string_id, args))
	}
	
	fun d(res : Resources, string_id : Int, vararg args : Any?) {
		Log.d(tag, format(res, string_id, args))
	}
	
	fun v(res : Resources, string_id : Int, vararg args : Any?) {
		Log.v(tag, format(res, string_id, args))
	}
	////////////////////////
	// exception
	
	fun e(ex : Throwable, fmt : String, vararg args : Any?) {
		Log.e(tag, ex.withCaption(format(fmt, args)))
	}
	
	fun w(ex : Throwable, fmt : String, vararg args : Any?) {
		Log.w(tag, ex.withCaption(format(fmt, args)))
	}
	
	fun i(ex : Throwable, fmt : String, vararg args : Any?) {
		Log.i(tag, ex.withCaption(format(fmt, args)))
	}
	
	fun d(ex : Throwable, fmt : String, vararg args : Any?) {
		Log.d(tag, ex.withCaption(format(fmt, args)))
	}
	
	fun v(ex : Throwable, fmt : String, vararg args : Any?) {
		Log.v(tag, ex.withCaption(format(fmt, args)))
	}
	////////////////////////
	// stack trace
	
	fun trace(ex : Throwable, fmt : String, vararg args : Any?) {
		Log.e(tag, format(fmt, args), ex)
	}
	
	fun trace(ex : Throwable) {
		Log.e(tag, "exception.", ex)
	}
}