package jp.juggler.subwaytooter.util

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.kJson
import jp.juggler.util.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class AttachmentPicker(
    val activity: AppCompatActivity,
    val callback: Callback,
) {
    companion object {
        private val log = LogCategory("AttachmentPicker")

        private const val PERMISSION_REQUEST_CODE = 1
    }

    // callback after media selected
    interface Callback {
        fun onPickAttachment(uri: Uri, mimeType: String? = null)
        fun onPickCustomThumbnail(pa: PostAttachment, src: GetContentResultEntry)
        fun resumeCustomThumbnailTarget(id: String?): PostAttachment?
    }

    // actions after permission granted
    enum class AfterPermission { Attachment, CustomThumbnail, }

    @Serializable
    data class States(

        @Serializable(with = UriSerializer::class)
        var uriCameraImage: Uri? = null,

        var customThumbnailTargetId: String? = null,
    )

    private var states = States()

    ////////////////////////////////////////////////////////////////////////
    // activity result handlers

    private val arAttachmentChooser = ActivityResultHandler(log) { r ->
        if (r.isNotOk) return@ActivityResultHandler
        r.data?.handleGetContentResult(activity.contentResolver)?.pickAll()
    }

    private val arCamera = ActivityResultHandler(log) { r ->
        if (r.isNotOk) {
            // 失敗したら DBからデータを削除
            states.uriCameraImage?.let { uri ->
                activity.contentResolver.delete(uri, null, null)
                states.uriCameraImage = null
            }
        } else {
            // 画像のURL
            when (val uri = r.data?.data ?: states.uriCameraImage) {
                null -> activity.showToast(false, "missing image uri")
                else -> callback.onPickAttachment(uri)
            }
        }
    }

    private val arCapture = ActivityResultHandler(log) { r ->
        if (r.isNotOk) return@ActivityResultHandler
        r.data?.data?.let { callback.onPickAttachment(it) }
    }

    private val arCustomThumbnail = ActivityResultHandler(log) { r ->
        if (r.isNotOk) return@ActivityResultHandler
        r.data
            ?.handleGetContentResult(activity.contentResolver)
            ?.firstOrNull()
            ?.let {
                callback.resumeCustomThumbnailTarget(states.customThumbnailTargetId)?.let { pa ->
                    callback.onPickCustomThumbnail(pa, it)
                }
            }
    }

    private val prPickAttachment = PermissionRequester(
        permissions = listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
        deniedId = R.string.missing_permission_to_access_media,
    ) { openPicker() }

    private val prPickCustomThumbnail = PermissionRequester(
        permissions = listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
        deniedId = R.string.missing_permission_to_access_media,
    ) {
        callback.resumeCustomThumbnailTarget(states.customThumbnailTargetId)
            ?.let { openCustomThumbnail(it) }
    }

    init {
        // must register all ARHs before onStart
        prPickAttachment.register(activity)
        prPickCustomThumbnail.register(activity)
        arAttachmentChooser.register(activity)
        arCamera.register(activity)
        arCapture.register(activity)
        arCustomThumbnail.register(activity)
    }

    ////////////////////////////////////////////////////////////////////////
    // states

    fun reset() {
        states.uriCameraImage = null
    }

    fun encodeState(): String {
        val encoded = kJson.encodeToString(states)
        val decoded = kJson.decodeFromString<States>(encoded)
        log.d("encodeState: ${decoded.uriCameraImage},$encoded")
        return encoded
    }

    fun restoreState(encoded: String) {
        states = kJson.decodeFromString(encoded)
        log.d("restoreState: ${states.uriCameraImage},$encoded")
    }

    ////////////////////////////////////////////////////////////////////////

    fun openPicker() {
        if (!prPickAttachment.checkOrLaunch()) return

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
            log.e(ex, "openAttachmentChooser failed.")
            activity.showToast(ex, "openAttachmentChooser failed.")
        }
    }

    private fun performCamera() {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.TITLE, "${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }

            val newUri =
                activity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values)
                    .also { states.uriCameraImage = it }

            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, newUri)
            }

            arCamera.launch(intent)
        } catch (ex: Throwable) {
            log.e(ex, "performCamera failed.")
            activity.showToast(ex, "performCamera failed.")
        }
    }

    private fun performCapture(action: String, errorCaption: String) {
        try {
            arCapture.launch(Intent(action))
        } catch (ex: Throwable) {
            log.e(ex, errorCaption)
            activity.showToast(ex, errorCaption)
        }
    }

    private fun ArrayList<GetContentResultEntry>.pickAll() =
        forEach { callback.onPickAttachment(it.uri, it.mimeType) }

    ///////////////////////////////////////////////////////////////////////////////
    // Mastodon's custom thumbnail

    fun openCustomThumbnail(pa: PostAttachment) {
        states.customThumbnailTargetId = pa.attachment?.id?.toString()
        if (!prPickCustomThumbnail.checkOrLaunch()) return

        // SAFのIntentで開く
        try {
            arCustomThumbnail.launch(
                intentGetContent(false,
                    activity.getString(R.string.pick_images),
                    arrayOf("image/*"))
            )
        } catch (ex: Throwable) {
            log.e(ex, "openCustomThumbnail failed.")
            activity.showToast(ex, "openCustomThumbnail failed.")
        }
    }
}
