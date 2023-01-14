package jp.juggler.subwaytooter

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import jp.juggler.util.data.getIntOrNull
import jp.juggler.util.data.getStringOrNull
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.long

class DownloadReceiver : BroadcastReceiver() {

    companion object {

        private val log = LogCategory("DownloadReceiver")
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            DownloadManager.ACTION_DOWNLOAD_COMPLETE -> {

                val downloadManager =
                    context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

                try {
                    val id = intent.long(DownloadManager.EXTRA_DOWNLOAD_ID)
                        ?: error("missing download id")

                    val query = DownloadManager.Query().setFilterById(id)

                    downloadManager.query(query)?.use { cursor ->
                        if (!cursor.moveToFirst()) {
                            log.e("cursor.moveToFirst() failed.")
                            return
                        }
                        val title = cursor.getStringOrNull(DownloadManager.COLUMN_TITLE)
                        val status = cursor.getIntOrNull(DownloadManager.COLUMN_STATUS)
                        context.showToast(
                            false,
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                context.getString(R.string.download_complete, title)
                            } else {
                                context.getString(R.string.download_failed, title)
                            }
                        )
                        /*
                            ダウンロード完了通知がシステムからのものと重複することがある

                            - (Aubee elm. Android 5.1) don't shows toast.
                            - (Samsung Galaxy S8+ Android 7.0) don't show toast.
                            - (Kyocera AndroidOne Android 8.0 S2) don't show toast.
                            - (LGE LGL24 Android 5.0.2) SHOWS toast.
                            - (LGE LGV32 Android 6.0) SHOWS toast.

                            maybe it depends on customization by device maker. not depends on OS version.

                            重複を回避する方法はなさそうだ…
                        */
                    }
                } catch (ex: Throwable) {
                    log.e(ex, "downloadManager.query() failed.")
                }
            }
        }
    }
}
