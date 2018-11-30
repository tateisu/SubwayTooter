package jp.juggler.util

import android.content.Context
import android.widget.Toast
import java.lang.ref.WeakReference
import me.drakeet.support.toast.ToastCompat

object ToastUtils {
	private val log = LogCategory("ToastUtils")
	private var refToast : WeakReference<Toast>? = null
	
	internal fun showToastImpl(context : Context, bLong : Boolean, message : String) {
		runOnMainLooper {
			
			// 前回のトーストの表示を終了する
			try {
				refToast?.get()?.cancel()
			} catch(ex : Throwable) {
				log.trace(ex)
			} finally {
				refToast = null
			}
			
			// 新しいトーストを作る
			try {
				val duration = if(bLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
				val t = ToastCompat.makeText(context, message, duration)
				t.setBadTokenListener {}
				t.show()
				refToast = WeakReference(t)
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			
			// コールスタックの外側でエラーになる…
			// android.view.WindowManager$BadTokenException:
			// at android.view.ViewRootImpl.setView (ViewRootImpl.java:679)
			// at android.view.WindowManagerGlobal.addView (WindowManagerGlobal.java:342)
			// at android.view.WindowManagerImpl.addView (WindowManagerImpl.java:94)
			// at android.widget.Toast$TN.handleShow (Toast.java:435)
			// at android.widget.Toast$TN$2.handleMessage (Toast.java:345)
		}
	}
	
}

fun showToast(context : Context, bLong : Boolean, fmt : String?, vararg args : Any) {
	val msg = if(fmt == null) "(null)" else if(args.isEmpty()) fmt else String.format(fmt, *args)
	ToastUtils.showToastImpl(context, bLong, msg)
}

fun showToast(context : Context, ex : Throwable, fmt : String?, vararg args : Any) {
	ToastUtils.showToastImpl(context, true, ex.withCaption(fmt, *args))
}

fun showToast(context : Context, bLong : Boolean, string_id : Int, vararg args : Any) {
	ToastUtils.showToastImpl(context, bLong, context.getString(string_id, *args))
}

fun showToast(context : Context, ex : Throwable, string_id : Int, vararg args : Any) {
	ToastUtils.showToastImpl(context, true, ex.withCaption(context.resources, string_id, *args))
}
