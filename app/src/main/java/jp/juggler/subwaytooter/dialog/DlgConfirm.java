package jp.juggler.subwaytooter.dialog;

import android.app.Activity;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import jp.juggler.subwaytooter.R;

public class DlgConfirm {
	
	public interface Callback {
		boolean isConfirmEnabled();
		
		void setConfirmEnabled( boolean bv );
		
		void onOK();
	}
	
	public static void open(
		final Activity activity
		, String message
		, final Callback callback
	){
		
		if( ! callback.isConfirmEnabled() ){
			callback.onOK();
			return;
		}
		
		final View view = activity.getLayoutInflater().inflate( R.layout.dlg_confirm, null, false );
		final TextView tvMessage = (TextView) view.findViewById( R.id.tvMessage );
		final CheckBox cbSkipNext = (CheckBox) view.findViewById( R.id.cbSkipNext );
		tvMessage.setText( message );
		
		new AlertDialog.Builder( activity )
			.setView( view )
			.setCancelable( true )
			.setNegativeButton( R.string.cancel, null )
			.setPositiveButton( R.string.ok, new DialogInterface.OnClickListener() {
				@Override public void onClick( DialogInterface dialog, int which ){
					if( cbSkipNext.isChecked() ){
						callback.setConfirmEnabled( false );
					}
					callback.onOK();
				}
			} )
			.show();
	}
	
}

