package jp.juggler.subwaytooter.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import jp.juggler.subwaytooter.R
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast

private val log = LogCategory("ClipboardUtils")

fun CharSequence.copyToClipboard(context: Context) {
    try {
        // Gets a handle to the clipboard service.
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: throw NotImplementedError("missing ClipboardManager system service")

        // Creates a new text clip to put on the clipboard
        val clip = ClipData.newPlainText("text", this)

        // Set the clipboard's primary clip.

        clipboard.setPrimaryClip(clip)

        if (Build.VERSION.SDK_INT < 33) {
            context.showToast(false, R.string.copy_complete)
            // API 33以上はOSがクリップボード使用メッセージをだすので、アプリはトーストを出さない
        }
    } catch (ex: Throwable) {
        log.e(ex, "copy failed.")
        context.showToast(ex, "copy failed.")
    }
}
