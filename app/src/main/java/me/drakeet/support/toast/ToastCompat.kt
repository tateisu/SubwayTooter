/*
	original implementation is https://github.com/PureWriter/ToastCompat

	modification:
	- convert from Java to Kotlin
	- because Android 11's Toast.getView() returns null, we need to support view==null case.
	- only in case of API 25 device, we have to create custom context and set it to view.
*/

package me.drakeet.support.toast

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.util.Log
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowManager.BadTokenException
import android.widget.Toast
import androidx.annotation.StringRes
import jp.juggler.util.LogCategory

fun interface BadTokenListener {

    fun onBadTokenCaught(toast: Toast)
}

internal class SafeToastContext(base: Context, private val toast: Toast) : ContextWrapper(base) {

    companion object {

        private const val TAG = "WindowManagerWrapper"
    }

    private var badTokenListener: BadTokenListener? = null

    fun setBadTokenListener(badTokenListener: BadTokenListener?) {
        this.badTokenListener = badTokenListener
    }

    override fun getApplicationContext(): Context =
        ApplicationContextWrapper(baseContext.applicationContext)

    private inner class ApplicationContextWrapper(base: Context) : ContextWrapper(base) {

        override fun getSystemService(name: String): Any? =
            if (WINDOW_SERVICE == name) {
                // noinspection ConstantConditions
                WindowManagerWrapper(baseContext.getSystemService(name) as WindowManager)
            } else {
                super.getSystemService(name)
            }
    }

    private inner class WindowManagerWrapper(private val base: WindowManager) : WindowManager {

        @Suppress("DEPRECATION")
        @Deprecated("Use Context.getDisplay() instead.")
        override fun getDefaultDisplay(): Display? =
            base.defaultDisplay

        override fun removeViewImmediate(view: View) =
            base.removeViewImmediate(view)

        override fun updateViewLayout(view: View, params: ViewGroup.LayoutParams) =
            base.updateViewLayout(view, params)

        override fun removeView(view: View) =
            base.removeView(view)

        override fun addView(view: View, params: ViewGroup.LayoutParams) {
            try {
                Log.d(TAG, "WindowManager's addView(view, params) has been hooked.")
                base.addView(view, params)
            } catch (e: BadTokenException) {
                e.message?.let { Log.i(TAG, it) }
                badTokenListener?.onBadTokenCaught(toast)
            } catch (throwable: Throwable) {
                Log.e(TAG, "[addView]", throwable)
            }
        }
    }
}

@Suppress("TooManyFunctions")
class ToastCompat private constructor(
    context: Context,
    private val base: Toast,
) : Toast(context) {

    companion object {
        private val log = LogCategory("ToastCompat")

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
        fun makeText(context: Context, @StringRes resId: Int, duration: Int) =
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
        @Suppress("ShowToast", "DEPRECATION")
        fun makeText(context: Context, text: CharSequence?, duration: Int): ToastCompat {
            // We cannot pass the SafeToastContext to Toast.makeText() because
            // the View will unwrap the base context and we are in vain.
            val base = Toast.makeText(context, text, duration)
            return ToastCompat(context, base)
        }
    }

    @Suppress("DEPRECATION")
    fun setBadTokenListener(listener: BadTokenListener?): ToastCompat {
        (base.view?.context as? SafeToastContext)
            ?.setBadTokenListener(listener)
        return this
    }

    @Suppress("DEPRECATION")
    @Deprecated(message = "Custom toast views are deprecated in API level 30.")
    override fun setView(view: View) {
        base.view = view
    }

    @Suppress("DEPRECATION")
    @Deprecated(message = "Custom toast views are deprecated in API level 30.")
    override fun getView(): View? = base.view

    override fun show() = base.show()

    override fun setDuration(duration: Int) {
        base.duration = duration
    }

    override fun setGravity(gravity: Int, xOffset: Int, yOffset: Int) =
        base.setGravity(gravity, xOffset, yOffset)

    override fun setMargin(horizontalMargin: Float, verticalMargin: Float) =
        base.setMargin(horizontalMargin, verticalMargin)

    override fun setText(resId: Int) = base.setText(resId)
    override fun setText(s: CharSequence) = base.setText(s)
    override fun getHorizontalMargin() = base.horizontalMargin
    override fun getVerticalMargin() = base.verticalMargin
    override fun getDuration() = base.duration
    override fun getGravity() = base.gravity
    override fun getXOffset() = base.xOffset
    override fun getYOffset() = base.yOffset
}
