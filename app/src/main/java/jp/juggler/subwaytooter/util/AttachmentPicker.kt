package jp.juggler.subwaytooter.util

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.kJson
import jp.juggler.util.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.*
import kotlinx.serialization.Serializable

class AttachmentPicker(
    val activity: AppCompatActivity,
    val callback: Callback,
) {
    companion object {
        private val log = LogCategory("AttachmentPicker")
        private val permissions = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        private const val PERMISSION_REQUEST_CODE = 1
    }

    // callback after media selected
    interface Callback {
        fun onPickAttachment(uri: Uri, mimeType: String? = null)
        fun onPickCustomThumbnail(src: GetContentResultEntry)
    }

    // actions after permission granted
    enum class AfterPermission { Attachment, CustomThumbnail, }

    @Serializable
    data class States(

        @Serializable(with = UriSerializer::class)
        var uriCameraImage: Uri? = null,

        var afterPermission: AfterPermission = AfterPermission.Attachment,
    )

    private var states = States()

    ////////////////////////////////////////////////////////////////////////
    // activity result handlers

    private val arAttachmentChooser = activity.activityResultHandler { ar ->
        if (ar?.resultCode == AppCompatActivity.RESULT_OK) {
            ar.data?.handleGetContentResult(contentResolver)?.pickAll()
        }
    }

    private val arCamera = activity.activityResultHandler { ar ->
        if (ar?.resultCode == AppCompatActivity.RESULT_OK) {
            // 画像のURL
            when (val uri = ar.data?.data ?: states.uriCameraImage) {
                null -> showToast(false, "missing image uri")
                else -> callback.onPickAttachment(uri)
            }
        } else {
            // 失敗したら DBからデータを削除
            states.uriCameraImage?.let { uri ->
                contentResolver.delete(uri, null, null)
                states.uriCameraImage = null
            }
        }
    }

    private val arCapture = activity.activityResultHandler { ar ->
        if (ar?.resultCode == AppCompatActivity.RESULT_OK) {
            ar.data?.data?.let { callback.onPickAttachment(it) }
        }
    }

    private val arCustomThumbnail = activity.activityResultHandler { ar ->
        if (ar?.resultCode == AppCompatActivity.RESULT_OK) {
            ar.data
                ?.handleGetContentResult(contentResolver)
                ?.firstOrNull()
                ?.let { callback.onPickCustomThumbnail(it) }
        }
    }

    init {
        // must register all ARHs before onStart
        arAttachmentChooser.register(activity, log)
        arCamera.register(activity, log)
        arCapture.register(activity, log)
        arCustomThumbnail.register(activity, log)
    }

    ////////////////////////////////////////////////////////////////////////
    // states

    fun reset() {
        states.uriCameraImage = null
    }

    fun encodeState(): String {
        val encoded = kJson.encodeToString(states)
        val decoded = kJson.decodeFromString<States>(encoded)
        log.d("encodeState: ${decoded.uriCameraImage},${decoded.afterPermission},$encoded")
        return encoded
    }

    fun restoreState(encoded: String) {
        states = kJson.decodeFromString(encoded)
        log.d("restoreState: ${states.uriCameraImage},${states.afterPermission},$encoded")
    }

    ////////////////////////////////////////////////////////////////////////
    // permission check
    // (current implementation does not auto restart actions after got permission

    // returns true if permission granted, false if not granted, (may request permissions)
    private fun checkPermission(afterPermission: AfterPermission): Boolean {
        states.afterPermission = afterPermission
        if (permissions.all {
                ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
            }) return true

        if (Build.VERSION.SDK_INT >= 23) {
            ActivityCompat.requestPermissions(activity, permissions, PERMISSION_REQUEST_CODE)
        } else {
            activity.showToast(true, R.string.missing_permission_to_access_media)
        }
        return false
    }

    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {

        if (requestCode != PERMISSION_REQUEST_CODE) return

        if ((permissions.indices).any { grantResults.elementAtOrNull(it) != PackageManager.PERMISSION_GRANTED }) {
            activity.showToast(true, R.string.missing_permission_to_access_media)
            return
        }

        when (states.afterPermission) {
            AfterPermission.Attachment -> openPicker()
            AfterPermission.CustomThumbnail -> openCustomThumbnail()
        }
    }

    ////////////////////////////////////////////////////////////////////////

    fun openPicker() {
        if (!checkPermission(AfterPermission.Attachment)) return

        //		permissionCheck = ContextCompat.checkSelfPermission( this, Manifest.permission.CAMERA );
        //		if( permissionCheck != PackageManager.PERMISSION_GRANTED ){
        //			preparePermission();
        //			return;
        //		}

        with(activity) {
            val a = ActionsDialog()
            a.addAction(getString(R.string.pick_images)) {
                openAttachmentChooser(R.string.pick_images, "image/*", "video/*")
            }
            a.addAction(getString(R.string.pick_videos)) {
                openAttachmentChooser(R.string.pick_videos, "video/*")
            }
            a.addAction(getString(R.string.pick_audios)) {
                openAttachmentChooser(R.string.pick_audios, "audio/*")
            }
            a.addAction(getString(R.string.image_capture)) {
                performCamera()
            }
            a.addAction(getString(R.string.video_capture)) {
                performCapture(
                    MediaStore.ACTION_VIDEO_CAPTURE,
                    "can't open video capture app."
                )
            }
            a.addAction(getString(R.string.voice_capture)) {
                performCapture(
                    MediaStore.Audio.Media.RECORD_SOUND_ACTION,
                    "can't open voice capture app."
                )
            }
            a.show(this, null)
        }
    }

    private fun openAttachmentChooser(titleId: Int, vararg mimeTypes: String) {
        // SAFのIntentで開く
        try {
            val intent = intentGetContent(true, activity.getString(titleId), mimeTypes)
            arAttachmentChooser.launch(intent)
        } catch (ex: Throwable) {
            log.trace(ex)
            activity.showToast(ex, "ACTION_GET_CONTENT failed.")
        }
    }

    private fun performCamera() {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.TITLE, "${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }

            val newUri = activity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                .also { states.uriCameraImage = it }

            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, newUri)
            }

            arCamera.launch(intent)
        } catch (ex: Throwable) {
            log.trace(ex)
            activity.showToast(ex, "opening camera app failed.")
        }
    }

    private fun performCapture(action: String, errorCaption: String) {
        try {
            arCapture.launch(Intent(action))
        } catch (ex: Throwable) {
            log.trace(ex)
            activity.showToast(ex, errorCaption)
        }
    }

    private fun ArrayList<GetContentResultEntry>.pickAll() =
        forEach { callback.onPickAttachment(it.uri, it.mimeType) }

    ///////////////////////////////////////////////////////////////////////////////
    // Mastodon's custom thumbnail

    fun openCustomThumbnail() {
        if (!checkPermission(AfterPermission.CustomThumbnail)) return

        // SAFのIntentで開く
        try {
            arCustomThumbnail.launch(
                intentGetContent(false, activity.getString(R.string.pick_images), arrayOf("image/*"))
            )
        } catch (ex: Throwable) {
            log.trace(ex)
            activity.showToast(ex, "ACTION_GET_CONTENT failed.")
        }
    }
}
