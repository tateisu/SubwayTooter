package me.drakeet.support.toast

import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowManager.BadTokenException
import android.widget.Toast

/**
 * @author drakeet
 */
internal class SafeToastContext(base : Context, private val toast : Toast) : ContextWrapper(base) {
	
	companion object {
		private const val TAG = "WindowManagerWrapper"
	}
	
	private var badTokenListener : BadTokenListener? = null
	
	fun setBadTokenListener(badTokenListener : BadTokenListener?) {
		this.badTokenListener = badTokenListener
	}
	
	override fun getApplicationContext() : Context =
		ApplicationContextWrapper(baseContext.applicationContext)

	inner class ApplicationContextWrapper(base : Context) : ContextWrapper(base) {
		
		override fun getSystemService(name : String) : Any? =
			if(WINDOW_SERVICE == name) {
				// noinspection ConstantConditions
				WindowManagerWrapper(baseContext.getSystemService(name) as WindowManager)
			} else{
				super.getSystemService(name)
			}
	}
	
	inner class WindowManagerWrapper(private val base : WindowManager) : WindowManager {
		
		@Suppress("DEPRECATION")
		override fun getDefaultDisplay() : Display? =
			base.defaultDisplay
		
		override fun removeViewImmediate(view : View) =
			base.removeViewImmediate(view)
		
		override fun updateViewLayout(view : View, params : ViewGroup.LayoutParams) =
			base.updateViewLayout(view, params)
		
		override fun removeView(view : View) =
			base.removeView(view)
		
		override fun addView(view : View, params : ViewGroup.LayoutParams) {
			try {
				Log.d(TAG, "WindowManager's addView(view, params) has been hooked.")
				base.addView(view, params)
			} catch(e : BadTokenException) {
				e.message?.let { Log.i(TAG, it) }
				badTokenListener?.onBadTokenCaught(toast)
			} catch(throwable : Throwable) {
				Log.e(TAG, "[addView]", throwable)
			}
		}
	}
}