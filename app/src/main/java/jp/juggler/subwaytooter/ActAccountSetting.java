package jp.juggler.subwaytooter;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import jp.juggler.subwaytooter.api.TootApiClient;
import jp.juggler.subwaytooter.api.TootApiResult;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.dialog.DlgAccessToken;
import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ActAccountSetting extends AppCompatActivity
	implements View.OnClickListener
	, CompoundButton.OnCheckedChangeListener
{
	
	static final LogCategory log = new LogCategory( "ActAccountSetting" );
	
	static final String KEY_ACCOUNT_DB_ID = "account_db_id";
	
	public static void open( Activity activity, SavedAccount ai, int requestCode ){
		Intent intent = new Intent( activity, ActAccountSetting.class );
		intent.putExtra( KEY_ACCOUNT_DB_ID, ai.db_id );
		activity.startActivityForResult( intent, requestCode );
	}
	
	SavedAccount account;
	
	@Override
	protected void onCreate( @Nullable Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		App1.setActivityTheme( this, false );
		initUI();
		account = SavedAccount.loadAccount( this, log, getIntent().getLongExtra( KEY_ACCOUNT_DB_ID, - 1L ) );
		if( account == null ) finish();
		loadUIFromData( account );
		
		btnOpenBrowser.setText( getString( R.string.open_instance_website, account.host ) );
	}
	
	@Override protected void onStop(){
		AlarmService.startCheck( this );
		super.onStop();
	}
	
	static final int REQUEST_CODE_ACCT_CUSTOMIZE = 1;
	static final int REQUEST_CODE_NOTIFICATION_SOUND = 2;
	
	@Override protected void onActivityResult( int requestCode, int resultCode, Intent data ){
		if( requestCode == REQUEST_CODE_ACCT_CUSTOMIZE && resultCode == RESULT_OK ){
			showAcctColor();
		}else if( resultCode == RESULT_OK && requestCode == REQUEST_CODE_NOTIFICATION_SOUND ){
			// RINGTONE_PICKERからの選択されたデータを取得する
			Uri uri = (Uri) data.getExtras().get( RingtoneManager.EXTRA_RINGTONE_PICKED_URI );
			if( uri != null ){
				notification_sound_uri = uri.toString();
				saveUIToData();
				//			Ringtone ringtone = RingtoneManager.getRingtone(getApplicationContext(), uri);
				//			TextView ringView = (TextView) findViewById(R.id.ringtone);
				//			ringView.setText(ringtone.getTitle(getApplicationContext()));
				//			ringtone.setStreamType(AudioManager.STREAM_ALARM);
				//			ringtone.play();
				//			SystemClock.sleep(1000);
				//			ringtone.stop();
			}
		}
		super.onActivityResult( requestCode, resultCode, data );
	}
	
	TextView tvInstance;
	TextView tvUser;
	View btnAccessToken;
	View btnInputAccessToken;
	View btnAccountRemove;
	Button btnVisibility;
	Switch swNSFWOpen;
	Button btnOpenBrowser;
	CheckBox cbNotificationMention;
	CheckBox cbNotificationBoost;
	CheckBox cbNotificationFavourite;
	CheckBox cbNotificationFollow;
	
	CheckBox cbConfirmFollow;
	CheckBox cbConfirmFollowLockedUser;
	CheckBox cbConfirmUnfollow;
	CheckBox cbConfirmBoost;
	CheckBox cbConfirmToot;
	
	TextView tvUserCustom;
	View btnUserCustom;
	String full_acct;
	
	Button btnNotificationSoundEdit;
	Button btnNotificationSoundReset;
	
	String notification_sound_uri;
	
	private void initUI(){
		setContentView( R.layout.act_account_setting );
		
		Styler.fixHorizontalPadding( findViewById( R.id.svContent ) );
		
		tvInstance = (TextView) findViewById( R.id.tvInstance );
		tvUser = (TextView) findViewById( R.id.tvUser );
		btnAccessToken = findViewById( R.id.btnAccessToken );
		btnInputAccessToken = findViewById( R.id.btnInputAccessToken );
		btnAccountRemove = findViewById( R.id.btnAccountRemove );
		btnVisibility = (Button) findViewById( R.id.btnVisibility );
		swNSFWOpen = (Switch) findViewById( R.id.swNSFWOpen );
		btnOpenBrowser = (Button) findViewById( R.id.btnOpenBrowser );
		cbNotificationMention = (CheckBox) findViewById( R.id.cbNotificationMention );
		cbNotificationBoost = (CheckBox) findViewById( R.id.cbNotificationBoost );
		cbNotificationFavourite = (CheckBox) findViewById( R.id.cbNotificationFavourite );
		cbNotificationFollow = (CheckBox) findViewById( R.id.cbNotificationFollow );
		
		cbConfirmFollow = (CheckBox) findViewById( R.id.cbConfirmFollow );
		cbConfirmFollowLockedUser = (CheckBox) findViewById( R.id.cbConfirmFollowLockedUser );
		cbConfirmUnfollow = (CheckBox) findViewById( R.id.cbConfirmUnfollow );
		cbConfirmBoost = (CheckBox) findViewById( R.id.cbConfirmBoost );
		cbConfirmToot = (CheckBox) findViewById( R.id.cbConfirmToot );
		
		tvUserCustom = (TextView) findViewById( R.id.tvUserCustom );
		btnUserCustom = findViewById( R.id.btnUserCustom );
		
		btnOpenBrowser.setOnClickListener( this );
		btnAccessToken.setOnClickListener( this );
		btnInputAccessToken.setOnClickListener( this );
		btnAccountRemove.setOnClickListener( this );
		btnVisibility.setOnClickListener( this );
		btnUserCustom.setOnClickListener( this );
		
		swNSFWOpen.setOnCheckedChangeListener( this );
		cbNotificationMention.setOnCheckedChangeListener( this );
		cbNotificationBoost.setOnCheckedChangeListener( this );
		cbNotificationFavourite.setOnCheckedChangeListener( this );
		cbNotificationFollow.setOnCheckedChangeListener( this );
		
		cbConfirmFollow.setOnCheckedChangeListener( this );
		cbConfirmFollowLockedUser.setOnCheckedChangeListener( this );
		cbConfirmUnfollow.setOnCheckedChangeListener( this );
		cbConfirmBoost.setOnCheckedChangeListener( this );
		cbConfirmToot.setOnCheckedChangeListener( this );
		
		btnNotificationSoundEdit = (Button) findViewById( R.id.btnNotificationSoundEdit );
		btnNotificationSoundReset = (Button) findViewById( R.id.btnNotificationSoundReset );
		btnNotificationSoundEdit.setOnClickListener( this );
		btnNotificationSoundReset.setOnClickListener( this );
	}
	
	boolean loading = false;
	
	private void loadUIFromData( SavedAccount a ){
		this.full_acct = a.acct;
		
		tvInstance.setText( a.host );
		tvUser.setText( a.acct );
		
		String sv = a.visibility;
		if( sv != null ){
			visibility = sv;
		}
		
		loading = true;
		
		swNSFWOpen.setChecked( a.dont_hide_nsfw );
		cbNotificationMention.setChecked( a.notification_mention );
		cbNotificationBoost.setChecked( a.notification_boost );
		cbNotificationFavourite.setChecked( a.notification_favourite );
		cbNotificationFollow.setChecked( a.notification_follow );
		
		cbConfirmFollow.setChecked( a.confirm_follow );
		cbConfirmFollowLockedUser.setChecked( a.confirm_follow_locked );
		cbConfirmUnfollow.setChecked( a.confirm_unfollow );
		cbConfirmBoost.setChecked( a.confirm_boost );
		cbConfirmToot.setChecked( a.confirm_post );
		
		notification_sound_uri = a.sound_uri;
		
		loading = false;
		
		boolean enabled = ! a.isPseudo();
		btnAccessToken.setEnabled( enabled );
		btnInputAccessToken.setEnabled( enabled );
		btnVisibility.setEnabled( enabled );
		btnVisibility.setEnabled( enabled );
		btnVisibility.setEnabled( enabled );
		btnNotificationSoundEdit.setEnabled( enabled );
		btnNotificationSoundReset.setEnabled( enabled );
		
		cbNotificationMention.setEnabled( enabled );
		cbNotificationBoost.setEnabled( enabled );
		cbNotificationFavourite.setEnabled( enabled );
		cbNotificationFollow.setEnabled( enabled );
		
		cbConfirmFollow.setEnabled( enabled );
		cbConfirmFollowLockedUser.setEnabled( enabled );
		cbConfirmUnfollow.setEnabled( enabled );
		cbConfirmBoost.setEnabled( enabled );
		cbConfirmToot.setEnabled( enabled );
		
		updateVisibility();
		showAcctColor();
	}
	
	private void showAcctColor(){
		AcctColor ac = AcctColor.load( full_acct );
		tvUserCustom.setText( ac != null && ! TextUtils.isEmpty( ac.nickname ) ? ac.nickname : full_acct );
		tvUserCustom.setTextColor( ac != null && ac.color_fg != 0 ? ac.color_fg : Styler.getAttributeColor( this, R.attr.colorTimeSmall ) );
		tvUserCustom.setBackgroundColor( ac != null && ac.color_bg != 0 ? ac.color_bg : 0 );
	}
	
	private void saveUIToData(){
		if( loading ) return;
		account.visibility = visibility;
		account.dont_hide_nsfw = swNSFWOpen.isChecked();
		account.notification_mention = cbNotificationMention.isChecked();
		account.notification_boost = cbNotificationBoost.isChecked();
		account.notification_favourite = cbNotificationFavourite.isChecked();
		account.notification_follow = cbNotificationFollow.isChecked();
		
		account.sound_uri = notification_sound_uri == null ? "" : notification_sound_uri;
		
		account.confirm_follow = cbConfirmFollow.isChecked();
		account.confirm_follow_locked = cbConfirmFollowLockedUser.isChecked();
		account.confirm_unfollow = cbConfirmUnfollow.isChecked();
		account.confirm_boost = cbConfirmBoost.isChecked();
		account.confirm_post = cbConfirmToot.isChecked();
		
		account.saveSetting();
		
	}
	
	@Override public void onCheckedChanged( CompoundButton buttonView, boolean isChecked ){
		saveUIToData();
	}
	
	@Override public void onClick( View v ){
		switch( v.getId() ){
		case R.id.btnAccessToken:
			performAccessToken();
			break;
		case R.id.btnInputAccessToken:
			inputAccessToken();
			break;
		
		case R.id.btnAccountRemove:
			performAccountRemove();
			break;
		case R.id.btnVisibility:
			performVisibility();
			break;
		case R.id.btnOpenBrowser:
			open_browser( "https://" + account.host + "/" );
			break;
		
		case R.id.btnUserCustom:
			ActNickname.open( this, full_acct, false, REQUEST_CODE_ACCT_CUSTOMIZE );
			break;
		
		case R.id.btnNotificationSoundEdit:
			openNotificationSoundPicker();
			break;
		
		case R.id.btnNotificationSoundReset:
			notification_sound_uri = "";
			saveUIToData();
			break;
		}
	}
	
	void open_browser( String url ){
		try{
			Intent intent = new Intent( Intent.ACTION_VIEW, Uri.parse( url ) );
			startActivity( intent );
		}catch( Throwable ex ){
			log.trace( ex );
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
				@Override public void onClick( DialogInterface dialog, int which ){
					account.delete();
					
					SharedPreferences pref = Pref.pref( ActAccountSetting.this );
					if( account.db_id == pref.getLong( Pref.KEY_TABLET_TOOT_DEFAULT_ACCOUNT, - 1L ) ){
						pref.edit().putLong( Pref.KEY_TABLET_TOOT_DEFAULT_ACCOUNT, - 1L ).apply();
					}
					
					finish();
					
					new AsyncTask< Void, Void, String >() {
						
						void unregister(){
							try{
								
								String install_id = PrefDevice.prefDevice( ActAccountSetting.this ).getString( PrefDevice.KEY_INSTALL_ID, null );
								if( TextUtils.isEmpty( install_id ) ){
									log.d( "performAccountRemove: missing install_id" );
									return;
								}
								
								String tag = account.notification_tag;
								if( TextUtils.isEmpty( tag ) ){
									log.d( "performAccountRemove: missing notification_tag" );
									return;
								}
								
								String post_data = "instance_url=" + Uri.encode( "https://" + account.host )
									+ "&app_id=" + Uri.encode( getPackageName() )
									+ "&tag=" + tag;
								
								Request request = new Request.Builder()
									.url( AlarmService.APP_SERVER + "/unregister" )
									.post( RequestBody.create( TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED, post_data ) )
									.build();
								
								Call call = App1.ok_http_client.newCall( request );
								
								Response response = call.execute();
								
								log.e( "performAccountRemove: %s", response );
								
							}catch( Throwable ex ){
								log.trace( ex );
							}
						}
						
						@Override protected String doInBackground( Void... params ){
							unregister();
							return null;
						}
						
						@Override protected void onCancelled( String s ){
							onPostExecute( s );
						}
						
						@Override protected void onPostExecute( String s ){
						}
					}.executeOnExecutor( App1.task_executor );
				}
			} )
			.show();
		
	}
	
	///////////////////////////////////////////////////
	private void performAccessToken(){
		
		final ProgressDialog progress = new ProgressDialog( ActAccountSetting.this );
		
		final AsyncTask< Void, String, TootApiResult > task = new AsyncTask< Void, String, TootApiResult >() {
			
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
				
				api_client.setAccount( account );
				return api_client.authorize1( Pref.pref( ActAccountSetting.this ).getString( Pref.KEY_CLIENT_NAME, "" ) );
			}
			
			@Override protected void onPostExecute( TootApiResult result ){
				progress.dismiss();
				
				if( result == null ){
					// cancelled.
				}else if( result.error != null ){
					// エラー？
					String sv = result.error;
					if( sv.startsWith( "https" ) ){
						// OAuth認証が必要
						Intent data = new Intent();
						data.setData( Uri.parse( sv ) );
						setResult( RESULT_OK, data );
						finish();
						return;
					}
					// 他のエラー
					Utils.showToast( ActAccountSetting.this, true, sv );
					log.e( result.error );
				}else{
					// 多分ここは通らないはず
					Utils.showToast( ActAccountSetting.this, false, R.string.access_token_updated_for );
				}
			}
		};
		progress.setIndeterminate( true );
		progress.setCancelable( true );
		progress.setOnCancelListener( new DialogInterface.OnCancelListener() {
			@Override public void onCancel( DialogInterface dialog ){
				task.cancel( true );
			}
		} );
		progress.show();
		task.executeOnExecutor( App1.task_executor );
	}
	
	static final int RESULT_INPUT_ACCESS_TOKEN = RESULT_FIRST_USER + 10;
	static final String EXTRA_DB_ID = "db_id";
	
	private void inputAccessToken(){
		
		Intent data = new Intent();
		data.putExtra( EXTRA_DB_ID, account.db_id );
		setResult( RESULT_INPUT_ACCESS_TOKEN, data );
		finish();
	}
	
	private void openNotificationSoundPicker(){
		Intent intent = new Intent( RingtoneManager.ACTION_RINGTONE_PICKER );
		intent.putExtra( RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION );
		intent.putExtra( RingtoneManager.EXTRA_RINGTONE_TITLE, R.string.notification_sound );
		intent.putExtra( RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false );
		intent.putExtra( RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false );
		try{
			Uri uri = TextUtils.isEmpty( notification_sound_uri ) ? null : Uri.parse( notification_sound_uri );
			if( uri != null ){
				intent.putExtra( RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri );
			}
		}catch( Throwable ignored ){
		}
		
		Intent chooser = Intent.createChooser( intent, getString( R.string.notification_sound ) );
		startActivityForResult( chooser, REQUEST_CODE_NOTIFICATION_SOUND );
	}
	
}

