package jp.juggler.subwaytooter.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.util.Utils;

/**
 * Created by tateisu on 2017/04/23.
 */

public class ReportForm {
	
	public interface ReportFormCallback{
		void startReport( Dialog dialog, String comment);
	}
	
	public static void showReportForm( final Activity activity, TootAccount who, TootStatus status , final ReportFormCallback callback){
		final View view = activity.getLayoutInflater().inflate( R.layout.dlg_report_user, null, false );
		
		final TextView tvUser = (TextView) view.findViewById( R.id.tvUser );
		final TextView	tvStatus= (TextView) view.findViewById( R.id.tvStatus );
		final EditText etComment= (EditText) view.findViewById( R.id.etComment );
			
		tvUser.setText( who.acct );
		tvStatus.setText( status == null ? "" : status.decoded_content);
		
		final Dialog dialog = new Dialog( activity );
		dialog.setContentView( view );
		view.findViewById( R.id.btnOk ).setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View v ){
				final String comment = etComment.getText().toString().trim();
				if( TextUtils.isEmpty( comment ) ){
					Utils.showToast( activity, true, R.string.comment_empty );
					return;
				}

				callback.startReport( dialog,comment );
			}
		} );
		view.findViewById( R.id.btnCancel ).setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View v ){
				dialog.cancel();
			}
		} );
		
		//noinspection ConstantConditions
		dialog.getWindow().setLayout( WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT );
		dialog.show();
	}
}
