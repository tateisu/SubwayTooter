package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Bitmap
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import net.glxn.qrgen.android.QRCode

@SuppressLint("StaticFieldLeak")
object DlgQRCode {
	
	internal interface QrCodeCallback {
		fun onQrCode(bitmap : Bitmap?)
	}
	
	private fun makeQrCode(
		activity : ActMain,
		size : Int,
		url : String,
		callback : QrCodeCallback
	) {
		activity.runWithProgress(
			"making QR code",
			{
				QRCode.from(url).withSize(size, size).bitmap()
			},
			{
				if(it != null) callback.onQrCode(it)
			},
			progressInitializer = {
				it.setMessageEx(activity.getString(R.string.generating_qr_code))
			}
		)
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
