package jp.juggler.subwaytooter.util

import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.dialog.actionsDialog
import jp.juggler.subwaytooter.kJson
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.data.UriAndType
import jp.juggler.util.log.LogCategory
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
        suspend fun onPickAttachment(item: UriAndType)
        suspend fun onPickCustomThumbnail(attachmentId: String?, src: UriAndType?)
    }

    //    // actions after permission granted
    //    enum class AfterPermission { Attachment, CustomThumbnail, }

    @Serializable
    data class States(
        var customThumbnailTargetId: String? = null,
    )

    private var states = States()

    ////////////////////////////////////////////////////////////////////////
    // activity result handlers

    private val visualMediaPickerThumbnail = VisualMediaPickerCompat {
        callback.onPickCustomThumbnail(states.customThumbnailTargetId, it?.firstOrNull())
    }

    private val visualMediaPickerAttachment = VisualMediaPickerCompat { it?.pickAll() }

    private val audioPicker = AudioPicker { it?.pickAll() }

    private val cameraOpener = CameraOpener { callback.onPickAttachment(it) }

    private val captureOpener = CaptureOpener { callback.onPickAttachment(it) }

    init {
        visualMediaPickerAttachment.register(activity)
        visualMediaPickerThumbnail.register(activity)
        cameraOpener.register(activity)
        audioPicker.register(activity)
        captureOpener.register(activity)
    }

    ////////////////////////////////////////////////////////////////////////
    // states

    fun reset() {
        cameraOpener.reset()
    }

    fun encodeState(): String {
        val encoded = kJson.encodeToString(states)
        val decoded = kJson.decodeFromString<States>(encoded)
        log.d("encodeState: states=$states, encoded=$encoded, decoded=$decoded")
        return encoded
    }

    fun restoreState(encoded: String) {
        states = kJson.decodeFromString(encoded)
        log.d("restoreState: states=$states, encoded=$encoded")
    }

    ////////////////////////////////////////////////////////////////////////

    fun openPicker() {
        activity.run {
            launchAndShowError {
                actionsDialog {
                    action(getString(R.string.pick_images_or_video)) {
                        visualMediaPickerAttachment.open(
                            multiple = true,
                            allowVideo = true,
                        )
                    }
                    action(getString(R.string.pick_audios)) {
                        audioPicker.open()
                    }
                    action(getString(R.string.image_capture)) {
                        cameraOpener.open()
                    }
                    action(getString(R.string.video_capture)) {
                        captureOpener.open(
                            MediaStore.ACTION_VIDEO_CAPTURE,
                            "can't open video capture app."
                        )
                    }
                    action(getString(R.string.voice_capture)) {
                        captureOpener.open(
                            MediaStore.Audio.Media.RECORD_SOUND_ACTION,
                            "can't open voice capture app."
                        )
                    }
                }
            }
        }
    }

    private suspend fun List<UriAndType>.pickAll() {
        forEach { callback.onPickAttachment(it) }
    }

    // ActPostAttachmentから呼ばれる
    fun openThumbnailPicker(pa: PostAttachment) {
        states.customThumbnailTargetId =
            pa.attachment?.id?.toString()
                ?: error("attachmentId is null")
        visualMediaPickerThumbnail.open()
    }
}
