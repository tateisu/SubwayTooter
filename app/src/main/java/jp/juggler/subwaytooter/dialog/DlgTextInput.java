package jp.juggler.subwaytooter.dialog;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import jp.juggler.subwaytooter.R;

public class DlgTextInput {
	
	public interface Callback {
		void onOK( Dialog dialog, String text );
		
		void onEmptyError();
	}
	
	public static void show(
		@NonNull final Activity activity
		, @NonNull CharSequence caption
	    , @Nullable CharSequence initial_text
		, @NonNull final Callback callback
	){
		@SuppressLint("InflateParams") final View view = activity.getLayoutInflater().inflate( R.layout.dlg_text_input, null, false );
		final EditText etInput = view.findViewById( R.id.etInput );
		final View btnOk = view.findViewById( R.id.btnOk );
		final TextView tvCaption = view.findViewById( R.id.tvCaption );
		
		tvCaption.setText( caption );
		if( !TextUtils.isEmpty( initial_text) ){
			etInput.setText( initial_text );
			etInput.setSelection( initial_text.length() );
		}
		
		etInput.setOnEditorActionListener( new TextView.OnEditorActionListener() {
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
				final String token = etInput.getText().toString().trim();
				
				if( TextUtils.isEmpty( token ) ){
					callback.onEmptyError();
				}else{
					callback.onOK( dialog, token );
				}
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

