package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Bitmap
import android.os.AsyncTask
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.util.LogCategory
import jp.juggler.util.dismissSafe
import jp.juggler.util.showToast
import net.glxn.qrgen.android.QRCode

@SuppressLint("StaticFieldLeak")
object DlgQRCode {
	
	private val log = LogCategory("DlgQRCode")
	
	internal interface QrCodeCallback {
		fun onQrCode(bitmap : Bitmap?)
	}
	
	private fun makeQrCode(
		activity : ActMain,
		size : Int,
		url : String,
		callback : QrCodeCallback
	) {
		@Suppress("DEPRECATION")
		val progress = ProgressDialogEx(activity)
		val task = object : AsyncTask<Void, Void, Bitmap?>() {
			
			override fun doInBackground(vararg params : Void) : Bitmap? {
				return try {
					QRCode.from(url).withSize(size, size).bitmap()
				} catch(ex : Throwable) {
					log.trace(ex)
					showToast(activity, ex, "makeQrCode failed.")
					null
				}
			}
			
			override fun onCancelled(result : Bitmap?) {
				onPostExecute(result)
			}
			
			override fun onPostExecute(result : Bitmap?) {
				progress.dismissSafe()
				if(result != null) {
					callback.onQrCode(result)
				}
			}
			
		}
		progress.isIndeterminateEx = true
		progress.setCancelable(true)
		progress.setMessageEx(activity.getString(R.string.generating_qr_code))
		progress.setOnCancelListener { task.cancel(true) }
		progress.show()
		
		task.executeOnExecutor(App1.task_executor)
	}
	
	fun open(activity : ActMain, message : CharSequence, url : String) {
		
		val size = (0.5f + 240f * activity.density).toInt()
		makeQrCode(activity, size, url, object : QrCodeCallback {
			
			@SuppressLint("InflateParams")
			override fun onQrCode(bitmap : Bitmap?) {
				
				val viewRoot = activity.layoutInflater.inflate(R.layout.dlg_qr_code, null, false)
				val dialog = Dialog(activity)
				dialog.setContentView(viewRoot)
				dialog.setCancelable(true)
				dialog.setCanceledOnTouchOutside(true)
				
				var tv = viewRoot.findViewById<TextView>(R.id.tvMessage)
				tv.text = message
				
				tv = viewRoot.findViewById(R.id.tvUrl)
				tv.text = "[ $url ]" // なぜか素のURLだと@以降が表示されない
				
				val iv = viewRoot.findViewById<ImageView>(R.id.ivQrCode)
				iv.setImageBitmap(bitmap)
				
				dialog.setOnDismissListener {
					iv.setImageDrawable(null)
					bitmap?.recycle()
				}
				
				viewRoot.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.cancel() }
				
				dialog.show()
				
			}
		})
		
	}
	
}
