// original implementation is https://github.com/PureWriter/ToastCompat
/*
	modification:
	- convert from java to kotlin
	- because Android 11's Toast.getView() returns null, we need to support view==null case.
	- only in case of API 25 device, we have to create custom context and set it to view.
*/

package me.drakeet.support.toast

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowManager.BadTokenException
import android.widget.Toast
import androidx.annotation.StringRes

fun interface BadTokenListener {
	
	fun onBadTokenCaught(toast : Toast)
}

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
			} else {
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

@Suppress("DEPRECATION")
class ToastCompat private constructor(
	context : Context,
	private val original : Toast
) : Toast(context) {
	
	companion object {
		
		private fun setContextCompat(view : View?, contextCreator : () -> Context) {
			if(view != null && Build.VERSION.SDK_INT == 25) {
				try {
					val field = View::class.java.getDeclaredField("mContext")
					field.isAccessible = true
					field[view] = contextCreator()
				} catch(throwable : Throwable) {
					throwable.printStackTrace()
				}
			}
		}
		
		/**
		 * Make a standard toast that just contains a text view with the text from a resource.
		 *
		 * @param context The context to use.  Usually your [android.app.Application]
		 * or [android.app.Activity] object.
		 * @param resId The resource id of the string resource to use.  Can be formatted text.
		 * @param duration How long to display the message.  Either [.LENGTH_SHORT] or
		 * [.LENGTH_LONG]
		 * @throws Resources.NotFoundException if the resource can't be found.
		 */
		@Suppress("unused")
		fun makeText(context : Context, @StringRes resId : Int, duration : Int) =
			makeText(context, context.resources.getText(resId), duration)
		
		/**
		 * Make a standard toast that just contains a text view.
		 *
		 * @param context The context to use.  Usually your [android.app.Application]
		 * or [android.app.Activity] object.
		 * @param text The text to show.  Can be formatted text.
		 * @param duration How long to display the message.  Either [.LENGTH_SHORT] or
		 * [.LENGTH_LONG]
		 */
		@SuppressLint("ShowToast")
		fun makeText(context : Context, text : CharSequence?, duration : Int) : ToastCompat {
			// We cannot pass the SafeToastContext to Toast.makeText() because
			// the View will unwrap the base context and we are in vain.
			val toast = Toast.makeText(context, text, duration)
			setContextCompat(toast.view) { SafeToastContext(context, toast) }
			return ToastCompat(context, toast)
		}
		
	}
	
	fun setBadTokenListener(listener : BadTokenListener?) : ToastCompat {
		(original.view?.context as? SafeToastContext)
			?.setBadTokenListener(listener)
		return this
	}
	
	override fun setView(view : View) {
		original.view = view
		setContextCompat(original.view) { SafeToastContext(view.context, original) }
	}
	
	override fun getView() : View? =
		original.view
	
	override fun show() =
		original.show()
	
	override fun setDuration(duration : Int) {
		original.duration = duration
	}
	
	override fun setGravity(gravity : Int, xOffset : Int, yOffset : Int) =
		original.setGravity(gravity, xOffset, yOffset)
	
	override fun setMargin(horizontalMargin : Float, verticalMargin : Float) =
		original.setMargin(horizontalMargin, verticalMargin)
	
	override fun setText(resId : Int) =
		original.setText(resId)
	
	override fun setText(s : CharSequence) =
		original.setText(s)
	
	override fun getHorizontalMargin() =
		original.horizontalMargin
	
	override fun getVerticalMargin() =
		original.verticalMargin
	
	override fun getDuration() =
		original.duration
	
	override fun getGravity() =
		original.gravity
	
	override fun getXOffset() =
		original.xOffset
	
	override fun getYOffset() =
		original.yOffset
}
