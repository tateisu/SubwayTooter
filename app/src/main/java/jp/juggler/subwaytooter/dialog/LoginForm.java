package jp.juggler.subwaytooter.dialog;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.support.annotation.NonNull;
import android.text.InputType;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Filter;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.util.Utils;

public class LoginForm {
	
	public interface LoginFormCallback {
		void startLogin( Dialog dialog, String instance
			, boolean bPseudoAccount
			, boolean bInputAccessToken
		);
	}
	
	private static class StringArray extends ArrayList< String > {
		
	}
	
	public static void showLoginForm( final Activity activity, final String instance, final LoginFormCallback callback ){
		@SuppressLint("InflateParams") final View view = activity.getLayoutInflater().inflate( R.layout.dlg_account_add, null, false );
		final AutoCompleteTextView etInstance = (AutoCompleteTextView) view.findViewById( R.id.etInstance );
		final View btnOk = view.findViewById( R.id.btnOk );
		final CheckBox cbPseudoAccount = (CheckBox) view.findViewById( R.id.cbPseudoAccount );
		final CheckBox cbInputAccessToken = (CheckBox) view.findViewById( R.id.cbInputAccessToken );
		
		cbPseudoAccount.setOnCheckedChangeListener( new CompoundButton.OnCheckedChangeListener() {
			@Override public void onCheckedChanged( CompoundButton buttonView, boolean isChecked ){
				cbInputAccessToken.setEnabled( ! cbPseudoAccount.isChecked() );
			}
		} );
		
		if( ! TextUtils.isEmpty( instance ) ){
			etInstance.setText( instance );
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
				}else if( instance.contains( "/" ) || instance.contains( "@" ) ){
					Utils.showToast( activity, true, R.string.instance_not_need_slash );
					return;
				}
				callback.startLogin( dialog, instance
					, cbPseudoAccount.isChecked()
					, cbInputAccessToken.isChecked()
				);
			}
		} );
		view.findViewById( R.id.btnCancel ).setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View v ){
				dialog.cancel();
			}
		} );
		
		final ArrayList< String > instance_list = new ArrayList<>();
		try{
			InputStream is = activity.getResources().openRawResource( R.raw.server_list );
			try{
				BufferedReader br = new BufferedReader( new InputStreamReader( is, "UTF-8" ) );
				for( ; ; ){
					String s = br.readLine();
					if( s == null ) break;
					s = s.trim();
					if( s.length() > 0 ) instance_list.add( s.toLowerCase() );
				}
			}finally{
				try{
					is.close();
				}catch( Throwable ignored ){
					
				}
			}
		}catch( Throwable ex ){
			ex.printStackTrace();
		}
		
		ArrayAdapter< String > adapter = new ArrayAdapter< String >(
			activity
			, R.layout.lv_spinner_dropdown
			, new ArrayList< String >()
		)
		{
			@NonNull @Override public Filter getFilter(){
				return nameFilter;
			}
			
			Filter nameFilter = new Filter() {
				@Override public CharSequence convertResultToString( Object value ){
					return (String) value;
				}
				
				@Override protected FilterResults performFiltering( CharSequence constraint ){
					FilterResults result = new FilterResults();
					if( ! TextUtils.isEmpty( constraint ) ){
						String key = constraint.toString().toLowerCase();
						// suggestions リストは毎回生成する必要がある。publishResultsと同時にアクセスされる場合がある
						StringArray suggestions = new StringArray();
						for( String s : instance_list ){
							if( s.contains( key ) ){
								suggestions.add( s );
								if( suggestions.size() >= 20 ) break;
							}
						}
						result.values = suggestions;
						result.count = suggestions.size();
					}
					return result;
				}
				
				@Override
				protected void publishResults( CharSequence constraint, FilterResults results ){
					clear();
					if( results.values instanceof StringArray ){
						for( String s : (StringArray) results.values ){
							add( s );
						}
					}
					notifyDataSetChanged();
				}
			};
		};
		adapter.setDropDownViewResource( R.layout.lv_spinner_dropdown );
		etInstance.setAdapter( adapter );
		
		//noinspection ConstantConditions
		dialog.getWindow().setLayout( WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT );
		dialog.show();
	}
	
}
