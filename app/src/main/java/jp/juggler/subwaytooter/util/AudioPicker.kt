package jp.juggler.subwaytooter.util

import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.R
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.data.UriAndType
import jp.juggler.util.data.checkMimeTypeAndGrant
import jp.juggler.util.data.intentGetContent
import jp.juggler.util.log.LogCategory
import jp.juggler.util.ui.ActivityResultHandler
import jp.juggler.util.ui.isOk
import jp.juggler.util.ui.launch

class AudioPicker(
    private val onPicked: suspend (list: List<UriAndType>?) -> Unit,
) {
    companion object {
        private val log = LogCategory("AudioPicker")
    }

    private lateinit var activity: AppCompatActivity

    private val prPickAudio = permissionSpecAudioPicker.requester {
        activity.launchAndShowError {
            open()
        }
    }
    private val arPickAudio = ActivityResultHandler(log) { r ->
        activity.launchAndShowError {
            if (r.isOk) {
                onPicked(r.data?.checkMimeTypeAndGrant(activity.contentResolver))
            }
        }
    }

    fun register(activity: AppCompatActivity) {
        this.activity = activity
        prPickAudio.register(activity)
        arPickAudio.register(activity)
    }

    fun open() {
        if (!prPickAudio.checkOrLaunch()) return
        intentGetContent(
            allowMultiple = true,
            caption = activity.getString(R.string.pick_audios),
            mimeTypes = arrayOf("audio/*"),
        ).launch(arPickAudio)
    }
}
