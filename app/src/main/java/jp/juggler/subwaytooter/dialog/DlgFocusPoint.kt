package jp.juggler.subwaytooter.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.TootAttachment
import jp.juggler.subwaytooter.view.FocusPointView
import jp.juggler.util.*

@SuppressLint("InflateParams")
class DlgFocusPoint(
    val activity: AppCompatActivity,
    val attachment: TootAttachment,
) : View.OnClickListener {

    companion object {

        val log = LogCategory("DlgFocusPoint")
    }

    val dialog: Dialog
    private val focusPointView: FocusPointView
    var bitmap: Bitmap? = null

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

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btnClose -> dialog.dismissSafe()
        }
    }

    fun setCallback(callback: (x: Float, y: Float) -> Unit): DlgFocusPoint {
        focusPointView.callback = callback
        return this
    }

    fun show() {
        val url = attachment.preview_url
        if (url == null) {
            activity.showToast(false, "missing image url")
            return
        }

        val options = BitmapFactory.Options()

        fun decodeBitmap(
            data: ByteArray,
            @Suppress("SameParameterValue") pixelMax: Int,
        ): Bitmap? {
            options.inJustDecodeBounds = true
            options.inScaled = false
            options.outWidth = 0
            options.outHeight = 0
            BitmapFactory.decodeByteArray(data, 0, data.size, options)
            var w = options.outWidth
            var h = options.outHeight
            if (w <= 0 || h <= 0) {
                log.e("can't decode bounds.")
                return null
            }
            var bits = 0
            while (w > pixelMax || h > pixelMax) {
                ++bits
                w = w shr 1
                h = h shr 1
            }
            options.inJustDecodeBounds = false
            options.inSampleSize = 1 shl bits
            return BitmapFactory.decodeByteArray(data, 0, data.size, options)
        }

        launchMain {
            var resultBitmap: Bitmap? = null
            val result = activity.runApiTask { client ->
                try {
                    val (result, data) = client.getHttpBytes(url)
                    data?.let {
                        resultBitmap = decodeBitmap(it, 1024)
                            ?: return@runApiTask TootApiResult("image decode failed.")
                    }
                    result
                } catch (ex: Throwable) {
                    TootApiResult(ex.withCaption("preview loading failed."))
                }
            }
            val bitmap = resultBitmap
            when {
                bitmap == null -> {
                    activity.showToast(true, result?.error ?: "?")
                    dialog.dismissSafe()
                }
                activity.isFinishing -> {
                    bitmap.recycle()
                    dialog.dismissSafe()
                }
                else -> {
                    this@DlgFocusPoint.bitmap = bitmap
                    focusPointView.setAttachment(attachment, bitmap)
                    dialog.window?.setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT
                    )
                    dialog.show()
                }
            }
        }
    }
}
