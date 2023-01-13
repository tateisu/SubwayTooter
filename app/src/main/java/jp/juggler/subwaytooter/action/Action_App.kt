package jp.juggler.subwaytooter.action

import android.app.AlertDialog
import android.net.Uri
import jp.juggler.subwaytooter.ActColumnList
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.actmain.currentColumn
import jp.juggler.subwaytooter.actmain.handleOtherUri
import jp.juggler.subwaytooter.api.entity.TootApplication
import jp.juggler.subwaytooter.dialog.DlgOpenUrl
import jp.juggler.subwaytooter.table.MutedApp
import jp.juggler.util.log.showToast
import jp.juggler.util.ui.dismissSafe

// カラム一覧を開く
fun ActMain.openColumnList() =
    arColumnList.launch(ActColumnList.createIntent(this, currentColumn))

// アプリをミュートする
fun ActMain.appMute(
    application: TootApplication?,
    confirmed: Boolean = false,
) {
    application ?: return
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
    appState.onMuteUpdated()
    showToast(false, R.string.app_was_muted)
}

fun ActMain.openColumnFromUrl() {
    DlgOpenUrl.show(this) { dialog, url ->
        try {
            if (handleOtherUri(Uri.parse(url))) {
                dialog.dismissSafe()
            }
        } catch (ex: Throwable) {
            showToast(ex, R.string.url_parse_failed)
        }
    }
}
