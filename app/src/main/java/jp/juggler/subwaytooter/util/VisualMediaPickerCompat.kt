package jp.juggler.subwaytooter.util

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.pref.PrefDevice
import jp.juggler.subwaytooter.pref.prefDevice
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.data.UriAndType
import jp.juggler.util.data.checkMimeTypeAndGrant
import jp.juggler.util.data.intentGetContent
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showError
import jp.juggler.util.ui.isOk

class VisualMediaPickerCompat(
    private val onPicked: suspend (entries: List<UriAndType>?) -> Unit,
) {
    companion object {
        private val log = LogCategory("VisualMediaPickerCompat")
    }

    private var activity: AppCompatActivity? = null

    private var prefDevice: PrefDevice? = null

    private var pickMedia1: ActivityResultLauncher<PickVisualMediaRequest>? = null

    private var pickMediaMultiple: ActivityResultLauncher<PickVisualMediaRequest>? = null

    private var arSafPicker: ActivityResultLauncher<Intent>? = null

    /**
     * SAFのピッカーを使うか判定するのに使う
     */
    private val prSafPickerImage = when {
        // 34以降でも権限をチェック
        Build.VERSION.SDK_INT >= 33 -> PermissionSpec(
            permissions = listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
            ),
            deniedId = R.string.permission_denied_media_access,
            rationalId = R.string.permission_rational_media_access,
        )

        else -> PermissionSpec(
            permissions = listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            deniedId = R.string.permission_denied_media_access,
            rationalId = R.string.permission_rational_media_access,
        )
    }.requester {
        openSafPicker(
            multiple = prefDevice?.mediaPickerMultiple ?: false,
            allowVideo = false,
        )
    }

    /**
     * SAFのピッカーを使うか判定するのに使う
     */
    private val prSafPickerImageAndVideo = when {
        // 34以降でも権限をチェック
        Build.VERSION.SDK_INT >= 33 -> PermissionSpec(
            permissions = listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
            ),
            deniedId = R.string.permission_denied_media_access,
            rationalId = R.string.permission_rational_media_access,
        )

        else -> PermissionSpec(
            permissions = listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            deniedId = R.string.permission_denied_media_access,
            rationalId = R.string.permission_rational_media_access,
        )
    }.requester {
        openSafPicker(
            multiple = prefDevice?.mediaPickerMultiple ?: false,
            allowVideo = true,
        )
    }

    fun register(activity: AppCompatActivity, multipleLimit: Int = 4) {

        prefDevice = activity.prefDevice

        this.activity = activity

        pickMedia1 = activity.registerForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            activity.launchAndShowError {
                onPicked((uri?.let { listOf(it) })?.checkMimeTypeAndGrant(activity.contentResolver))
            }
        }

        pickMediaMultiple = activity.registerForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(multipleLimit),
        ) { uris ->
            activity.launchAndShowError {
                onPicked(uris?.notEmpty()?.checkMimeTypeAndGrant(activity.contentResolver))
            }
        }

        prSafPickerImage.register(activity)
        prSafPickerImageAndVideo.register(activity)

        arSafPicker = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { r ->
            activity.launchAndShowError {
                if (r.isOk) {
                    onPicked(
                        r.data?.checkMimeTypeAndGrant(activity.contentResolver)
                    )
                }
            }
        }
    }

    fun open(
        multiple: Boolean = false,
        allowVideo: Boolean = false,
    ) {
        // SAFのピッカーに必要な権限を持っているか?
        val hasSafPermissions = try {
            when {
                allowVideo -> prSafPickerImageAndVideo
                else -> prSafPickerImage
            }.hasPermissions()
        } catch (ex: Throwable) {
            log.e(ex, "can't check media permissions.")
            activity?.showError(ex, "can't check media permissions.")
            return
        }

        when {
            // API 33まで、またはAPI34以降でも権限があればSAFのピッカーを使う
            Build.VERSION.SDK_INT < 34 || hasSafPermissions -> openSafPicker(
                multiple = multiple,
                allowVideo = allowVideo,
            )

            // API34以降で権限が不十分ならJetPack Activity の写真選択ツールを使う
            else -> openVisualMediaPicker(
                multiple = multiple,
                allowVideo = allowVideo,
            )
        }
    }

    private fun openVisualMediaPicker(
        multiple: Boolean,
        allowVideo: Boolean,
    ) {
        log.i("openVisualMediaPicker multiple=$multiple, allowVideo=$allowVideo")
        val mediaType = when (allowVideo) {
            true -> ActivityResultContracts.PickVisualMedia.ImageAndVideo
            else -> ActivityResultContracts.PickVisualMedia.ImageOnly
        }
        val pickerLauncher = when {
            multiple -> pickMediaMultiple
            else -> pickMedia1
        }
        pickerLauncher ?: error("openVisualMediaPicker: pickerLauncher is not registered.")
        pickerLauncher.launch(PickVisualMediaRequest(mediaType))
    }

    private fun openSafPicker(
        multiple: Boolean,
        allowVideo: Boolean,
    ) {
        log.i("openSafPicker multiple=$multiple, allowVideo=$allowVideo")
        val activity = this.activity
            ?: error("missing activity")

        prefDevice?.mediaPickerMultiple = multiple

        val permissionRequester = when {
            allowVideo -> prSafPickerImageAndVideo
            else -> prSafPickerImage
        }

        if (!permissionRequester.checkOrLaunch()) return

        // SAFのIntentで開く
        try {
            val captionId = when {
                multiple -> when {
                    allowVideo -> R.string.pick_images_or_video
                    else -> R.string.pick_images
                }

                else -> R.string.pick_image
            }
            val intent = intentGetContent(
                allowMultiple = true,
                caption = activity.getString(captionId),
                mimeTypes = when {
                    allowVideo -> arrayOf("image/*", "video/*")
                    else -> arrayOf("image/*")
                }
            )
            arSafPicker!!.launch(intent)
        } catch (ex: Throwable) {
            activity.showError(ex, "openVisualMediaPicker33 failed.")
        }
    }
}
