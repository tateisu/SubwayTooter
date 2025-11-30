package jp.juggler.subwaytooter.util

import android.content.ContentValues
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.result.ActivityResult
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.pref.PrefDevice
import jp.juggler.subwaytooter.pref.prefDevice
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.data.UriAndType
import jp.juggler.util.data.checkMimeTypeAndGrant
import jp.juggler.util.log.LogCategory
import jp.juggler.util.ui.ActivityResultHandler
import jp.juggler.util.ui.isOk

class CameraOpener(
    private val onCaptured: suspend (UriAndType) -> Unit,
) {
    companion object {
        private val log = LogCategory("LogCategory")
    }

    private lateinit var activity: AppCompatActivity

    private val prefDevice: PrefDevice
        get() = activity.prefDevice

    private val prCameraImage = permissionSpecCamera.requester { open() }
    private val arCameraImage = ActivityResultHandler(log) { handleCameraResult(it) }

    fun register(activity: AppCompatActivity) {
        this.activity = activity
        prCameraImage.register(activity)
        arCameraImage.register(activity)
    }

    fun reset() {
        prefDevice.cameraOpenerLastUri = null
    }

    fun open() {
        if (!prCameraImage.checkOrLaunch()) return
        // カメラで撮影
        val filename = System.currentTimeMillis().toString() + ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.TITLE, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        val uri = activity.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ).also { prefDevice.cameraOpenerLastUri = it }

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
        }
        arCameraImage.launch(intent)
    }

    private fun handleCameraResult(r: ActivityResult) {
        activity.launchAndShowError {
            when (
                val item = when {
                    r.isOk -> listOfNotNull(
                        r.data?.data
                            ?: prefDevice.cameraOpenerLastUri
                    ).checkMimeTypeAndGrant(activity.contentResolver)

                    else -> null
                }?.firstOrNull()
            ) {
                null -> {
                    // 失敗したら DBからデータを削除
                    prefDevice.cameraOpenerLastUri?.let {
                        activity.contentResolver.delete(it, null, null)
                    }
                    prefDevice.cameraOpenerLastUri = null
                }

                else -> onCaptured(item)
            }
        }
    }
}
