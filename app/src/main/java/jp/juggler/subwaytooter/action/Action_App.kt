package jp.juggler.subwaytooter.action

import jp.juggler.subwaytooter.ActColumnList
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootApplication
import jp.juggler.subwaytooter.table.MutedApp
import jp.juggler.subwaytooter.util.showToast

object Action_App {
	
	// カラム一覧を開く
	fun columnList(activity : ActMain) {
		ActColumnList.open(activity, activity.currentColumn, ActMain.REQUEST_CODE_COLUMN_LIST)
	}
	
	// アプリをミュートする
	fun muteApp(
		activity : ActMain, application : TootApplication
	) {
		MutedApp.save(application.name)
		App1.getAppState(activity).onMuteUpdated()
		showToast(activity, false, R.string.app_was_muted)
	}
	
}
