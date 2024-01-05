package jp.juggler.subwaytooter.util

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.dialog.actionsDialog
import jp.juggler.subwaytooter.kJson
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.data.GetContentResultEntry
import jp.juggler.util.data.UriSerializer
import jp.juggler.util.data.handleGetContentResult
import jp.juggler.util.data.intentGetContent
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.ui.ActivityResultHandler
import jp.juggler.util.ui.isNotOk
import jp.juggler.util.ui.isOk
import kotlinx.serialization.Serializable
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
        suspend fun onPickAttachment(uri: Uri, mimeType: String? = null)
        suspend fun onPickCustomThumbnail(attachmentId: String?, src: GetContentResultEntry)
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

    private var pickThumbnailLauncher: ActivityResultLauncher<PickVisualMediaRequest>? = null

    private val pickThumbnailCallback = ActivityResultCallback<Uri?> {
        handleThumbnailResult(it)
    }

    private var pickVisualMediaLauncher: ActivityResultLauncher<PickVisualMediaRequest>? = null

    private val pickVisualMediaCallback = ActivityResultCallback<List<Uri>?> { uris ->
        uris?.handleGetContentResult(activity.contentResolver)?.pickAll()
    }

    private val prPickAudio = permissionSpecAudioPicker.requester { openAudioPicker() }
    private val arPickAudio = ActivityResultHandler(log) { r ->
        if (r.isNotOk) return@ActivityResultHandler
        r.data?.handleGetContentResult(activity.contentResolver)?.pickAll()
    }

    private val prCamera = permissionSpecCamera.requester { openStillCamera() }
    private val arCamera = ActivityResultHandler(log) { handleCameraResult(it) }

    private val prCapture = permissionSpecCapture.requester { openPicker() }
    private val arCapture = ActivityResultHandler(log) { handleCaptureResult(it) }

    init {
        // must register all ARHs before onStart
        prPickAudio.register(activity)
        arPickAudio.register(activity)

        prCamera.register(activity)
        arCamera.register(activity)

        arCapture.register(activity)

        pickVisualMediaLauncher = activity.registerForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(4),
            pickVisualMediaCallback,
        )
        pickThumbnailLauncher = activity.registerForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
            pickThumbnailCallback,
        )
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
        activity.run {
            launchAndShowError {
                actionsDialog {
                    action(getString(R.string.pick_images_or_video)) {
                        openVisualMediaPicker()
                    }
                    action(getString(R.string.pick_audios)) {
                        openAudioPicker()
                    }
                    action(getString(R.string.image_capture)) {
                        openStillCamera()
                    }
                    action(getString(R.string.video_capture)) {
                        performCapture(
                            MediaStore.ACTION_VIDEO_CAPTURE,
                            "can't open video capture app."
                        )
                    }
                    action(getString(R.string.voice_capture)) {
                        performCapture(
                            MediaStore.Audio.Media.RECORD_SOUND_ACTION,
                            "can't open voice capture app."
                        )
                    }
                }
            }
        }
    }

    private fun openVisualMediaPicker() {
        (pickVisualMediaLauncher
            ?: error("pickVisualMediaLauncher is not registered."))
            .launch(
                PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageAndVideo
                )
            )
    }

    private fun openAudioPicker() {
        if (!prPickAudio.checkOrLaunch()) return
        activity.launchAndShowError {
            val intent = intentGetContent(
                allowMultiple = true,
                caption = activity.getString(R.string.pick_audios),
                mimeTypes = arrayOf("audio/*"),
            )
            arPickAudio.launch(intent)
        }
    }

    private fun openStillCamera() {
        if (!prCamera.checkOrLaunch()) return
        activity.launchAndShowError {
            val newUri = activity.contentResolver.insert(
                /* url = */
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                /* values = */
                ContentValues().apply {
                    put(MediaStore.Images.Media.TITLE, "${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                },
            ).also { states.uriCameraImage = it }

            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, newUri)
            }

            arCamera.launch(intent)
        }
    }

    private fun handleCameraResult(r: ActivityResult) {
        activity.launchAndShowError {
            when {
                r.isOk -> when (val uri = r.data?.data ?: states.uriCameraImage) {
                    null -> activity.showToast(false, "missing image uri")
                    else -> callback.onPickAttachment(uri)
                }
                // 失敗したら DBからデータを削除
                else -> states.uriCameraImage?.let { uri ->
                    activity.contentResolver.delete(uri, null, null)
                    states.uriCameraImage = null
                }
            }
        }
    }

    /**
     * 動画や音声をキャプチャする
     * - Uriは呼び出し先に任せっきり
     */
    private fun performCapture(
        action: String,
        errorCaption: String,
    ) {
        if (!prCapture.checkOrLaunch()) return
        try {
            arCapture.launch(Intent(action))
        } catch (ex: Throwable) {
            log.e(ex, errorCaption)
            activity.showToast(ex, errorCaption)
        }
    }

    private fun handleCaptureResult(r: ActivityResult) {
        activity.launchAndShowError {
            if (r.isOk) {
                when (val uri = r.data?.data) {
                    null -> activity.showToast(false, "missing media uri")
                    else -> callback.onPickAttachment(uri)
                }
            }
        }
    }

    private fun List<GetContentResultEntry>.pickAll() {
        activity.launchAndShowError {
            forEach { callback.onPickAttachment(it.uri, it.mimeType) }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////
    // Mastodon's custom thumbnail

    fun openCustomThumbnail(attachmentId: String?) {
        states.customThumbnailTargetId = attachmentId
            ?: error("attachmentId is null")
        activity.launchAndShowError {
            (pickThumbnailLauncher
                ?: error("pickThumbnailLauncher is not registered."))
                .launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                    )
                )
        }
    }

    private fun handleThumbnailResult(uri: Uri?) {
        uri ?: return
        activity.launchAndShowError {
            listOf(uri).handleGetContentResult(activity.contentResolver).firstOrNull()?.let {
                callback.onPickCustomThumbnail(states.customThumbnailTargetId, it)
            }
        }
    }
}
