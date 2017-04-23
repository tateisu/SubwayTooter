package jp.juggler.subwaytooter;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.os.AsyncTaskCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.dialog.LoginForm;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class ActAccountSetting extends AppCompatActivity implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
	static final LogCategory log = new LogCategory( "ActAccountSetting" );
	
	static final String KEY_ACCOUNT_DB_ID = "account_db_id";
	
	public static void open( Context context, SavedAccount ai ){
		Intent intent = new Intent( context, ActAccountSetting.class );
		intent.putExtra( KEY_ACCOUNT_DB_ID, ai.db_id );
		context.startActivity( intent );
	}
	
	SavedAccount account;
	
	@Override
	protected void onCreate( @Nullable Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		initUI();
		account = SavedAccount.loadAccount( log, getIntent().getLongExtra( KEY_ACCOUNT_DB_ID, - 1L ) );
		if( account == null ) finish();
		loadUIFromData( account );
		
	}
	
	TextView tvInstance;
	TextView tvUser;
	View btnAccessToken;
	View btnAccountRemove;
	Button btnVisibility;
	Switch swConfirmBeforeBoost;
	Switch swNSFWOpen;
	
	private void initUI(){
		setContentView( R.layout.act_account_setting );
		tvInstance = (TextView) findViewById( R.id.tvInstance );
		tvUser = (TextView) findViewById( R.id.tvUser );
		btnAccessToken = findViewById( R.id.btnAccessToken );
		btnAccountRemove = findViewById( R.id.btnAccountRemove );
		btnVisibility = (Button) findViewById( R.id.btnVisibility );
		swConfirmBeforeBoost = (Switch) findViewById( R.id.swConfirmBeforeBoost );
		swNSFWOpen = (Switch) findViewById( R.id.swNSFWOpen );
		
		btnAccessToken.setOnClickListener( this );
		btnAccountRemove.setOnClickListener( this );
		btnVisibility.setOnClickListener( this );
		
		swNSFWOpen.setOnCheckedChangeListener( this );
		swConfirmBeforeBoost.setOnCheckedChangeListener( this );
	}
	
	private void loadUIFromData( SavedAccount a ){
		tvInstance.setText( a.host );
		tvUser.setText( a.user );
		
		String sv = a.visibility;
		if( sv != null ){
			visibility = sv;
		}
		swConfirmBeforeBoost.setChecked( a.confirm_boost );
		swNSFWOpen.setChecked( a.dont_hide_nsfw );
		
		updateVisibility();
	}
	
	private void saveUIToData(){
		account.visibility = visibility;
		account.confirm_boost = swConfirmBeforeBoost.isChecked();
		account.dont_hide_nsfw = swNSFWOpen.isChecked();
		account.saveSetting();
	}
	
	@Override
	public void onCheckedChanged( CompoundButton buttonView, boolean isChecked ){
		saveUIToData();
	}
	
	@Override
	public void onClick( View v ){
		switch( v.getId() ){
		case R.id.btnAccessToken:
			performAccessToken();
			break;
		case R.id.btnAccountRemove:
			performAccountRemove();
			break;
		case R.id.btnVisibility:
			performVisibility();
			break;
		}
	}
	
	///////////////////////////////////////////////////
	
	String visibility = TootStatus.VISIBILITY_PUBLIC;
	
	private void updateVisibility(){
		btnVisibility.setText( Styler.getVisibilityString( this, visibility ) );
	}
	
	private void performVisibility(){
		final String[] caption_list = new String[]{
			getString( R.string.visibility_public ),
			getString( R.string.visibility_unlisted ),
			getString( R.string.visibility_private ),
			getString( R.string.visibility_direct ),
		};

//		public static final String VISIBILITY_PUBLIC ="public";
//		public static final String VISIBILITY_UNLISTED ="unlisted";
//		public static final String VISIBILITY_PRIVATE ="private";
//		public static final String VISIBILITY_DIRECT ="direct";
		
		new AlertDialog.Builder( this )
			.setTitle( R.string.choose_visibility )
			.setItems( caption_list, new DialogInterface.OnClickListener() {
				@Override
				public void onClick( DialogInterface dialog, int which ){
					switch( which ){
					case 0:
						visibility = TootStatus.VISIBILITY_PUBLIC;
						break;
					case 1:
						visibility = TootStatus.VISIBILITY_UNLISTED;
						break;
					case 2:
						visibility = TootStatus.VISIBILITY_PRIVATE;
						break;
					case 3:
						visibility = TootStatus.VISIBILITY_DIRECT;
						break;
					}
					updateVisibility();
					saveUIToData();
				}
			} )
			.setNegativeButton( R.string.cancel, null )
			.show();
		
	}
	
	///////////////////////////////////////////////////
	private void performAccountRemove(){
		new AlertDialog.Builder( this )
			.setTitle( R.string.confirm )
			.setMessage( R.string.confirm_account_remove )
			.setNegativeButton( R.string.cancel, null )
			.setPositiveButton( R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick( DialogInterface dialog, int which ){
					account.delete();
					finish();
				}
			} )
			.show();
		
	}
	
	///////////////////////////////////////////////////
	private void performAccessToken(){
		
		LoginForm.showLoginForm( this, account.host, new LoginForm.LoginFormCallback() {
			
			@Override
			public void startLogin( final Dialog dialog, final String instance, final String user_mail, final String password ){
				
				final ProgressDialog progress = new ProgressDialog( ActAccountSetting.this );
				
				final AsyncTask< Void, String, TootApiResult > task = new AsyncTask< Void, String, TootApiResult >() {
					
					long row_id;
					
					@Override
					protected TootApiResult doInBackground( Void... params ){
						TootApiClient api_client = new TootApiClient( ActAccountSetting.this, new TootApiClient.Callback() {
							@Override
							public boolean isApiCancelled(){
								return isCancelled();
							}
							
							@Override
							public void publishApiProgress( final String s ){
								Utils.runOnMainThread( new Runnable() {
									@Override
									public void run(){
										progress.setMessage( s );
									}
								} );
							}
						} );
						
						api_client.setUserInfo( instance, user_mail, password );
						
						TootApiResult result = api_client.request( "/api/v1/accounts/verify_credentials" );
						if( result != null && result.object != null ){
							TootAccount ta = TootAccount.parse( log, result.object );
							
							if( ! ta.username.equals( account.username ) ){
								return new TootApiResult( getString( R.string.user_name_not_match ) );
							}
							
							account.updateTokenInfo( result.token_info );
							row_id = account.db_id;
						}
						return result;
					}
					
					@Override
					protected void onPostExecute( TootApiResult result ){
						progress.dismiss();
						
						if( result == null ){
							// cancelled.
						}else if( result.object == null ){
							Utils.showToast( ActAccountSetting.this, true, result.error );
							log.e( result.error );
						}else{
							Utils.showToast( ActAccountSetting.this, false, R.string.access_token_updated );
							dialog.dismiss();
						}
					}
				};
				progress.setIndeterminate( true );
				progress.setCancelable( true );
				progress.setOnCancelListener( new DialogInterface.OnCancelListener() {
					@Override
					public void onCancel( DialogInterface dialog ){
						task.cancel( true );
					}
				} );
				progress.show();
				AsyncTaskCompat.executeParallel( task );
			}
		} );
		
	}

	///////////////////////////////////////////////////
	
}
