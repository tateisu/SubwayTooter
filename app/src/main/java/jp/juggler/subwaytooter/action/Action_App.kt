package jp.juggler.subwaytooter.action

import android.app.AlertDialog
import jp.juggler.subwaytooter.ActColumnList
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootApplication
import jp.juggler.subwaytooter.table.MutedApp
import jp.juggler.util.showToast

// カラム一覧を開く
fun ActMain.openColumnList() =
    arColumnList.launch(ActColumnList.createIntent(this, currentColumn))

// アプリをミュートする
fun ActMain.appMute(
    application: TootApplication,
    confirmed: Boolean = false
) {
    if (!confirmed) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.mute_application_confirm, application.name))
            .setPositiveButton(R.string.ok) { _, _ ->
                appMute(application, confirmed = true)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        return
    }
    MutedApp.save(application.name)
    app_state.onMuteUpdated()
    showToast(false, R.string.app_was_muted)

}


