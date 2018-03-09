package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.view.WindowManager
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootTask
import jp.juggler.subwaytooter.api.TootTaskRunner
import jp.juggler.subwaytooter.api.entity.TootAttachment
import jp.juggler.subwaytooter.util.*
import jp.juggler.subwaytooter.view.FocusPointView

@SuppressLint("InflateParams")
class DlgFocusPoint(val activity : Activity, val attachment : TootAttachment) :
	View.OnClickListener {
	
	companion object {
		val log = LogCategory("DlgFocusPoint")
	}
	
	val dialog : Dialog
	val focusPointView : FocusPointView
	var bitmap : Bitmap? = null
	
	init {
		val viewRoot = activity.layoutInflater.inflate(R.layout.dlg_focus_point, null, false)
		focusPointView = viewRoot.findViewById(R.id.ivFocus)
		viewRoot.findViewById<View>(R.id.btnClose).setOnClickListener(this)
		
		this.dialog = Dialog(activity)
		dialog.setContentView(viewRoot)
		dialog.setOnDismissListener {
			bitmap?.recycle()
		}
	}
	
	override fun onClick(v : View) {
		when(v.id) {
			R.id.btnClose -> dialog.dismiss()
		}
	}
	
	fun setCallback(callback : FocusPointView.Callback?) : DlgFocusPoint {
		focusPointView.callback = callback
		return this
	}
	
	fun show() {
		val url = attachment.preview_url
		if(url == null) {
			showToast(activity, false, "missing image url")
			return
		}
		
		TootTaskRunner(activity).run(object : TootTask {
			
			private val options = BitmapFactory.Options()
			
			private fun decodeBitmap(data : ByteArray, pixel_max : Int) : Bitmap? {
				options.inJustDecodeBounds = true
				options.inScaled = false
				options.outWidth = 0
				options.outHeight = 0
				BitmapFactory.decodeByteArray(data, 0, data.size, options)
				var w = options.outWidth
				var h = options.outHeight
				if(w <= 0 || h <= 0) {
					log.e("can't decode bounds.")
					return null
				}
				var bits = 0
				while(w > pixel_max || h > pixel_max) {
					++ bits
					w = w shr 1
					h = h shr 1
				}
				options.inJustDecodeBounds = false
				options.inSampleSize = 1 shl bits
				return BitmapFactory.decodeByteArray(data, 0, data.size, options)
			}
			
			var bitmap : Bitmap? = null
			
			override fun background(client : TootApiClient) : TootApiResult? {
				
				val result = client.getHttpBytes(url)
				
				try {
					val data = result?.data as? ByteArray ?: return result
					bitmap = decodeBitmap(data, 1024)
					if(bitmap == null) return TootApiResult("image decode failed.")
				} catch(ex : Throwable) {
					return TootApiResult(ex.withCaption("preview loading failed."))
				}
				
				return result
			}
			
			override fun handleResult(result : TootApiResult?) {
				val bitmap = this.bitmap
				if(bitmap == null) {
					showToast(activity, true, result?.error ?: "?")
					try {
						dialog.dismiss()
					} catch(ignored : Throwable) {
					
					}
					return
				}
				
				if(activity.isFinishing) {
					bitmap.recycle()
					try {
						dialog.dismiss()
					} catch(ignored : Throwable) {
					
					}
					return
				}
				
				this@DlgFocusPoint.bitmap = bitmap
				
				focusPointView.setAttachment(attachment, bitmap)
				
				dialog.window?.setLayout(
					WindowManager.LayoutParams.MATCH_PARENT,
					WindowManager.LayoutParams.MATCH_PARENT
				)
				dialog.show()
			}
		})
		
	}
}
