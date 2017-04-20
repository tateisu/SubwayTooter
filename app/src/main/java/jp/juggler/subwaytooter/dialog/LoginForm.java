package jp.juggler.subwaytooter.dialog;

import android.app.Dialog;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import jp.juggler.subwaytooter.ActMain;
import jp.juggler.subwaytooter.R;
import jp.juggler.subwaytooter.util.Utils;

/**
 * Created by tateisu on 2017/04/16.
 */

public class LoginForm {
	
	public interface LoginFormCallback{
		void startLogin(Dialog dialog,String instance,String user_main,String password);
	}
	
	public static void showLoginForm(final ActMain activity,final LoginFormCallback callback){
		final View view = activity.getLayoutInflater().inflate( R.layout.dlg_account_add, null, false );
		final EditText etInstance = (EditText) view.findViewById( R.id.etInstance );
		final EditText etUserMail = (EditText) view.findViewById( R.id.etUserMail );
		final EditText etUserPassword = (EditText) view.findViewById( R.id.etUserPassword );
		final Dialog dialog = new Dialog( activity );
		dialog.setContentView( view );
		view.findViewById( R.id.btnOk ).setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View v ){
				final String instance = etInstance.getText().toString().trim();
				final String user_mail = etUserMail.getText().toString().trim();
				final String password = etUserPassword.getText().toString().trim();
				if( TextUtils.isEmpty( instance ) ){
					Utils.showToast( activity, true, R.string.instance_not_specified );
					return;
				}
				if( TextUtils.isEmpty( user_mail ) ){
					Utils.showToast(activity, true, R.string.mail_not_specified );
					return;
				}
				if( TextUtils.isEmpty( password ) ){
					Utils.showToast( activity, true, R.string.password_not_specified );
					return;
				}
				callback.startLogin( dialog,instance,user_mail,password );
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
