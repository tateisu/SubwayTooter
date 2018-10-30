package jp.juggler.subwaytooter

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import jp.juggler.subwaytooter.util.getIntOrNull
import jp.juggler.subwaytooter.util.getStringOrNull

import jp.juggler.subwaytooter.util.showToast

class DownloadReceiver : BroadcastReceiver() {
	override fun onReceive(context : Context, intent : Intent?) {
		intent ?: return

		val action = intent.action ?: return
		
		if(DownloadManager.ACTION_DOWNLOAD_COMPLETE == action) {
			val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0L)
			
			val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager?
				?: throw NotImplementedError("missing DownloadManager system service")
			
			val query = DownloadManager.Query().setFilterById(id)
			downloadManager.query(query)?.use { cursor ->
				if(cursor.moveToFirst()) {
					
					val title = cursor.getStringOrNull(DownloadManager.COLUMN_TITLE)
					
					if(DownloadManager.STATUS_SUCCESSFUL == cursor.getIntOrNull(DownloadManager.COLUMN_STATUS) ) {
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
						
						showToast(context, false, context.getString(R.string.download_complete, title))
					} else {
						showToast(context, false, context.getString(R.string.download_failed, title))
					}
				}
			}
		}
	}
}
