package jp.juggler.subwaytooter.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.text.InputType;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.util.Utils;

public class LoginForm {
	
	public interface LoginFormCallback{
		void startLogin(Dialog dialog,String instance);
	}
	
	public static void showLoginForm( final Activity activity, String instance , final LoginFormCallback callback){
		final View view = activity.getLayoutInflater().inflate( R.layout.dlg_account_add, null, false );
		final AutoCompleteTextView etInstance = (AutoCompleteTextView) view.findViewById( R.id.etInstance );
		final View btnOk = view.findViewById( R.id.btnOk );
			
		if( !TextUtils.isEmpty( instance ) ){
			etInstance.setText(instance);
			etInstance.setInputType( InputType.TYPE_NULL );
			etInstance.setEnabled( false );
			etInstance.setFocusable( false );
		}else{
			etInstance.setOnEditorActionListener( new TextView.OnEditorActionListener() {
				@Override public boolean onEditorAction( TextView v, int actionId, KeyEvent event ){
					if( actionId == EditorInfo.IME_ACTION_DONE ){
						btnOk.performClick();
						return true;
					}
					return false;
				}
			} );
		}
		final Dialog dialog = new Dialog( activity );
		dialog.setContentView( view );
		btnOk.setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View v ){
				final String instance = etInstance.getText().toString().trim();

				if( TextUtils.isEmpty( instance ) ){
					Utils.showToast( activity, true, R.string.instance_not_specified );
					return;
				}else if( instance.contains( "/" )){
					Utils.showToast( activity, true, R.string.instance_not_need_slash );
					return;
				}
				callback.startLogin( dialog,instance );
			}
		} );
		view.findViewById( R.id.btnCancel ).setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View v ){
				dialog.cancel();
			}
		} );
		
		ArrayList<String> instance_list = new ArrayList<>(  );
		try{
			InputStream is = activity.getResources().openRawResource( R.raw.server_list );
			try{
				BufferedReader br = new BufferedReader( new InputStreamReader( is,"UTF-8" ) );
				for(;;){
					String s  = br.readLine();
					if( s == null ) break;
					s= s.trim();
					if( s.length() > 0 ) instance_list.add( s );
				}
			}finally{
				try{
					is.close();
				}catch(Throwable ignored){
					
				}
			}
		}catch(Throwable ex){
			ex.printStackTrace(  );
		}
		
		ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, R.layout.lv_spinner_dropdown, instance_list);
		adapter.setDropDownViewResource( R.layout.lv_spinner_dropdown );
		etInstance.setAdapter(adapter);
		
		//noinspection ConstantConditions
		dialog.getWindow().setLayout( WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT );
		dialog.show();
	}

}
