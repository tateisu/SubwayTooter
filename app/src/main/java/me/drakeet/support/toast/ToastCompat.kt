package me.drakeet.support.toast

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes

// original implementation is https://github.com/PureWriter/ToastCompat
// Android 11 でgetViewがnullを返すことが増えたので
/**
 * @author drakeet
 */
/**
 * Construct an empty Toast object.  You must call [.setView] before you
 * can call [.show].
 *
 * @param context The context to use.  Usually your [android.app.Application]
 * or [android.app.Activity] object.
 * @param baseToast The base toast
 */
@Suppress("DEPRECATION")
class ToastCompat(
	context : Context,
	private val baseToast : Toast
) : Toast(context) {
	
	companion object {
		
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
		fun makeText(context : Context, @StringRes resId : Int, duration : Int) : Toast {
			return makeText(context, context.resources.getText(resId), duration)
		}
		
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
	}
	
	fun setBadTokenListener(listener : BadTokenListener?) : ToastCompat {
		(baseToast.view?.context as? SafeToastContext)
			?.setBadTokenListener(listener)
		return this
	}
	
	override fun setView(view : View) {
		baseToast.view = view
		setContextCompat(baseToast.view) { SafeToastContext(view.context, this) }
	}
	
	override fun getView() : View? = baseToast.view
	
	override fun show() = baseToast.show()
	
	override fun setDuration(duration : Int) {
		baseToast.duration = duration
	}
	
	override fun setGravity(gravity : Int, xOffset : Int, yOffset : Int) =
		baseToast.setGravity(gravity, xOffset, yOffset)
	
	override fun setMargin(horizontalMargin : Float, verticalMargin : Float) =
		baseToast.setMargin(horizontalMargin, verticalMargin)
	
	override fun setText(resId : Int) =
		baseToast.setText(resId)
	
	override fun setText(s : CharSequence) =
		baseToast.setText(s)
	
	override fun getHorizontalMargin() : Float =
		baseToast.horizontalMargin
	
	override fun getVerticalMargin() : Float =
		baseToast.verticalMargin
	
	override fun getDuration() : Int =
		baseToast.duration
	
	override fun getGravity() : Int =
		baseToast.gravity
	
	override fun getXOffset() : Int =
		baseToast.xOffset
	
	override fun getYOffset() : Int =
		baseToast.yOffset
}