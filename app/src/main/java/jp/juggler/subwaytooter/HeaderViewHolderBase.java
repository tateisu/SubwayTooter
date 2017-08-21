package jp.juggler.subwaytooter;

import android.support.annotation.NonNull;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

abstract class HeaderViewHolderBase {
	
	private static final LogCategory log = new LogCategory( "HeaderViewHolderBase" );
	
	
	@NonNull final ActMain activity;
	@NonNull final Column column;
	@NonNull final SavedAccount access_info;
	@NonNull final View viewRoot;
	
	HeaderViewHolderBase( @NonNull ActMain arg_activity, @NonNull Column column, @NonNull View viewRoot ){
		this.activity = arg_activity;
		this.column = column;
		this.access_info = column.access_info;
		this.viewRoot = viewRoot;
		
		viewRoot.setTag( this );
		
		if( activity.timeline_font != null ){
			Utils.scanView( viewRoot, new Utils.ScanViewCallback() {
				@Override public void onScanView( View v ){
					try{
						if( v instanceof Button ){
							// ボタンは太字なので触らない
						}else if( v instanceof TextView ){
							( (TextView) v ).setTypeface( activity.timeline_font );
						}
					}catch( Throwable ex ){
						log.trace( ex );
					}
				}
			} );
		}
		
	}
	
	abstract void showColor();
	
	abstract void bindAccount( TootAccount who_account );
}
