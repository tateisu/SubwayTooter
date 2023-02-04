package jp.juggler.subwaytooter.action

import android.net.Uri
import jp.juggler.subwaytooter.ActColumnList
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.actmain.currentColumn
import jp.juggler.subwaytooter.actmain.handleOtherUri
import jp.juggler.subwaytooter.api.entity.TootApplication
import jp.juggler.subwaytooter.dialog.DlgConfirm.confirm
import jp.juggler.subwaytooter.dialog.DlgOpenUrl
import jp.juggler.subwaytooter.table.daoMutedApp
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.log.showToast
import jp.juggler.util.ui.dismissSafe

// カラム一覧を開く
fun ActMain.openColumnList() =
    arColumnList.launch(ActColumnList.createIntent(this, currentColumn))

// アプリをミュートする
fun ActMain.appMute(application: TootApplication?) = launchAndShowError {
    application ?: return@launchAndShowError
    confirm(R.string.mute_application_confirm, application.name)
    daoMutedApp.save(application.name)
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
