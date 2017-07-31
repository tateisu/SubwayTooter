package jp.juggler.subwaytooter.dialog;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.util.Utils;

public class DlgAccessToken {
	
	public interface Callback {
		void startCheck( Dialog dialog, String access_token );
	}
	
	public static void show( @NonNull  final Activity activity ,@NonNull final Callback callback ){
		@SuppressLint("InflateParams") final View view = activity.getLayoutInflater().inflate( R.layout.dlg_access_token, null, false );
		final EditText etToken = (EditText) view.findViewById( R.id.etToken );
		final View btnOk = view.findViewById( R.id.btnOk );
		
		etToken.setOnEditorActionListener( new TextView.OnEditorActionListener() {
				@Override public boolean onEditorAction( TextView v, int actionId, KeyEvent event ){
					if( actionId == EditorInfo.IME_ACTION_DONE ){
						btnOk.performClick();
						return true;
					}
					return false;
				}
			} );

		final Dialog dialog = new Dialog( activity );
		dialog.setContentView( view );
		btnOk.setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View v ){
				final String token = etToken.getText().toString().trim();
				
				if( TextUtils.isEmpty( token ) ){
					Utils.showToast( activity, true, R.string.token_not_specified );
					return;
				}
				callback.startCheck( dialog, token );
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

