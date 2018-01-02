package jp.juggler.subwaytooter.action;

import android.support.annotation.NonNull;

import jp.juggler.subwaytooter.ActColumnList;
import jp.juggler.subwaytooter.ActMain;
import jp.juggler.subwaytooter.App1;
import jp.juggler.subwaytooter.Column;
import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.api.entity.TootApplication;
import jp.juggler.subwaytooter.table.MutedApp;
import jp.juggler.subwaytooter.util.Utils;

public class Action_App {
	
	// カラム一覧を開く
	public static void columnList( @NonNull final ActMain activity ){
		ActColumnList.open( activity, activity.getCurrentColumn(), ActMain.REQUEST_CODE_COLUMN_LIST );
	}
	
	// アプリをミュートする
	public static void muteApp(
		@NonNull final ActMain activity
		, @NonNull TootApplication application
	){
		MutedApp.save( application.name );
		for( Column column : App1.app_state.column_list ){
			column.onMuteAppUpdated();
		}
		Utils.showToast( activity, false, R.string.app_was_muted );
	}
	
}
