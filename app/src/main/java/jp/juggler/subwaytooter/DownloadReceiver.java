package jp.juggler.subwaytooter;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import jp.juggler.subwaytooter.util.Utils;

public class DownloadReceiver extends BroadcastReceiver {
	@Override public void onReceive( Context context, Intent intent ){
		if( intent == null ) return;
		String action = intent.getAction();
		if( action == null ) return;
		
		if( DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals( action )){
			long id = intent.getLongExtra( DownloadManager.EXTRA_DOWNLOAD_ID, 0L );
			
			DownloadManager downloadManager = (DownloadManager) context.getSystemService( Context.DOWNLOAD_SERVICE );
			if( downloadManager != null ){
				DownloadManager.Query query = new DownloadManager.Query();
				query.setFilterById( id );
				Cursor cursor = downloadManager.query( query );
				try{
					if( cursor.moveToFirst() ){
						int idx_status = cursor.getColumnIndex( DownloadManager.COLUMN_STATUS );
						
						int idx_title = cursor.getColumnIndex( DownloadManager.COLUMN_TITLE );
						String title = cursor.getString( idx_title );
						
						if( DownloadManager.STATUS_SUCCESSFUL == cursor.getInt( idx_status ) ){
							Utils.showToast( context, false, context.getString( R.string.download_complete, title ) );
						}else{
							Utils.showToast( context, false, context.getString( R.string.download_failed, title ) );
						}
					}
				}finally{
					cursor.close();
				}
			}
		}
	}
}
