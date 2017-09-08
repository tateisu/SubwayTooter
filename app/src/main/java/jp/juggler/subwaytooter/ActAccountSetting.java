package jp.juggler.subwaytooter;
	
	import android.Manifest;
	import android.app.Activity;
	import android.app.NotificationChannel;
	import android.app.ProgressDialog;
	import android.content.ClipData;
	import android.content.ContentValues;
	import android.content.DialogInterface;
	import android.content.Intent;
	import android.content.SharedPreferences;
	import android.content.pm.PackageManager;
	import android.graphics.Bitmap;
	import android.media.RingtoneManager;
	import android.net.Uri;
	import android.os.AsyncTask;
	import android.os.Build;
	import android.os.Bundle;
	import android.provider.MediaStore;
	import android.provider.Settings;
	import android.support.annotation.NonNull;
	import android.support.annotation.Nullable;
	import android.support.v4.app.ActivityCompat;
	import android.support.v4.content.ContextCompat;
	import android.support.v7.app.AlertDialog;
	import android.support.v7.app.AppCompatActivity;
	import android.text.TextUtils;
	import android.util.Base64;
	import android.util.Base64OutputStream;
	import android.view.View;
	import android.widget.Button;
	import android.widget.CheckBox;
	import android.widget.CompoundButton;
	import android.widget.EditText;
	import android.widget.Switch;
	import android.widget.TextView;
	
	import org.apache.commons.io.IOUtils;
	
	import java.io.ByteArrayOutputStream;
	import java.io.File;
	import java.io.FileInputStream;
	import java.io.FileOutputStream;
	import java.io.IOException;
	import java.io.InputStream;
	
	import jp.juggler.subwaytooter.api.TootApiClient;
	import jp.juggler.subwaytooter.api.TootApiResult;
	import jp.juggler.subwaytooter.api.entity.TootAccount;
	import jp.juggler.subwaytooter.api.entity.TootStatus;
	import jp.juggler.subwaytooter.dialog.ActionsDialog;
	import jp.juggler.subwaytooter.table.AcctColor;
	import jp.juggler.subwaytooter.table.SavedAccount;
	import jp.juggler.subwaytooter.util.Emojione;
	import jp.juggler.subwaytooter.util.HTMLDecoder;
	import jp.juggler.subwaytooter.util.LogCategory;
	import jp.juggler.subwaytooter.util.NotificationHelper;
	import jp.juggler.subwaytooter.util.Utils;
	import jp.juggler.subwaytooter.view.MyNetworkImageView;
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
	SharedPreferences pref;
	
	@Override
	protected void onCreate( @Nullable Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		
		App1.setActivityTheme( this, false );
		this.pref = App1.pref;
		
		initUI();
		account = SavedAccount.loadAccount( this, log, getIntent().getLongExtra( KEY_ACCOUNT_DB_ID, - 1L ) );
		if( account == null ) finish();
		loadUIFromData( account );
		
		initializeProfile();
		
		btnOpenBrowser.setText( getString( R.string.open_instance_website, account.host ) );
	}
	
	@Override protected void onStop(){
		PollingWorker.queueUpdateNotification( this );
		super.onStop();
	}
	
	static final int REQUEST_CODE_ACCT_CUSTOMIZE = 1;
	static final int REQUEST_CODE_NOTIFICATION_SOUND = 2;
	private static final int REQUEST_CODE_AVATAR_ATTACHMENT = 3;
	private static final int REQUEST_CODE_HEADER_ATTACHMENT = 4;
	private static final int REQUEST_CODE_AVATAR_CAMERA = 5;
	private static final int REQUEST_CODE_HEADER_CAMERA = 6;
	
	@Override protected void onActivityResult( int requestCode, int resultCode, Intent data ){
		switch( requestCode ){
		default:
			super.onActivityResult( requestCode, resultCode, data );
			break;
		case REQUEST_CODE_ACCT_CUSTOMIZE:{
			if( resultCode == RESULT_OK ){
				showAcctColor();
			}
			break;
		}
		case REQUEST_CODE_NOTIFICATION_SOUND:{
			if( resultCode == RESULT_OK ){
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
			break;
		}
		case REQUEST_CODE_AVATAR_ATTACHMENT:
		case REQUEST_CODE_HEADER_ATTACHMENT:{
			
			if( resultCode == RESULT_OK && data != null ){
				Uri uri = data.getData();
				if( uri != null ){
					// 単一選択
					String type = data.getType();
					if( TextUtils.isEmpty( type ) ){
						type = getContentResolver().getType( uri );
					}
					addAttachment( requestCode, uri, type );
					break;
				}
				ClipData cd = data.getClipData();
				if( cd != null ){
					int count = cd.getItemCount();
					if( count > 0 ){
						ClipData.Item item = cd.getItemAt( 0 );
						uri = item.getUri();
						String type = getContentResolver().getType( uri );
						addAttachment( requestCode, uri, type );
					}
				}
			}
			break;
		}
		case REQUEST_CODE_AVATAR_CAMERA:
		case REQUEST_CODE_HEADER_CAMERA:{
			
			if( resultCode != RESULT_OK ){
				// 失敗したら DBからデータを削除
				if( uriCameraImage != null ){
					getContentResolver().delete( uriCameraImage, null, null );
					uriCameraImage = null;
				}
			}else{
				// 画像のURL
				Uri uri = ( data == null ? null : data.getData() );
				if( uri == null ) uri = uriCameraImage;
				
				if( uri != null ){
					String type = getContentResolver().getType( uri );
					addAttachment( requestCode, uri, type );
				}
			}
			break;
		}
		}
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
	Button btnNotificationStyleEdit;
	
	String notification_sound_uri;
	
	MyNetworkImageView ivProfileHeader;
	MyNetworkImageView ivProfileAvatar;
	View btnProfileAvatar;
	View btnProfileHeader;
	EditText etDisplayName;
	View btnDisplayName;
	EditText etNote;
	View btnNote;
	
	private void initUI(){
		setContentView( R.layout.act_account_setting );
		
		Styler.fixHorizontalPadding( findViewById( R.id.svContent ) );
		
		tvInstance = findViewById( R.id.tvInstance );
		tvUser = findViewById( R.id.tvUser );
		btnAccessToken = findViewById( R.id.btnAccessToken );
		btnInputAccessToken = findViewById( R.id.btnInputAccessToken );
		btnAccountRemove = findViewById( R.id.btnAccountRemove );
		btnVisibility = findViewById( R.id.btnVisibility );
		swNSFWOpen = findViewById( R.id.swNSFWOpen );
		btnOpenBrowser = findViewById( R.id.btnOpenBrowser );
		cbNotificationMention = findViewById( R.id.cbNotificationMention );
		cbNotificationBoost = findViewById( R.id.cbNotificationBoost );
		cbNotificationFavourite = findViewById( R.id.cbNotificationFavourite );
		cbNotificationFollow = findViewById( R.id.cbNotificationFollow );
		
		cbConfirmFollow = findViewById( R.id.cbConfirmFollow );
		cbConfirmFollowLockedUser = findViewById( R.id.cbConfirmFollowLockedUser );
		cbConfirmUnfollow = findViewById( R.id.cbConfirmUnfollow );
		cbConfirmBoost = findViewById( R.id.cbConfirmBoost );
		cbConfirmToot = findViewById( R.id.cbConfirmToot );
		
		tvUserCustom = findViewById( R.id.tvUserCustom );
		btnUserCustom = findViewById( R.id.btnUserCustom );
		
		ivProfileHeader = findViewById( R.id.ivProfileHeader );
		ivProfileAvatar = findViewById( R.id.ivProfileAvatar );
		btnProfileAvatar = findViewById( R.id.btnProfileAvatar );
		btnProfileHeader = findViewById( R.id.btnProfileHeader );
		etDisplayName = findViewById( R.id.etDisplayName );
		btnDisplayName = findViewById( R.id.btnDisplayName );
		etNote = findViewById( R.id.etNote );
		btnNote = findViewById( R.id.btnNote );
		
		btnOpenBrowser.setOnClickListener( this );
		btnAccessToken.setOnClickListener( this );
		btnInputAccessToken.setOnClickListener( this );
		btnAccountRemove.setOnClickListener( this );
		btnVisibility.setOnClickListener( this );
		btnUserCustom.setOnClickListener( this );
		btnProfileAvatar.setOnClickListener( this );
		btnProfileHeader.setOnClickListener( this );
		btnDisplayName.setOnClickListener( this );
		btnNote.setOnClickListener( this );
		
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
		
		btnNotificationSoundEdit = findViewById( R.id.btnNotificationSoundEdit );
		btnNotificationSoundReset = findViewById( R.id.btnNotificationSoundReset );
		btnNotificationStyleEdit = findViewById( R.id.btnNotificationStyleEdit );
		btnNotificationSoundEdit.setOnClickListener( this );
		btnNotificationSoundReset.setOnClickListener( this );
		btnNotificationStyleEdit.setOnClickListener( this );
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
		
		btnNotificationSoundEdit.setEnabled( Build.VERSION.SDK_INT < 26 && enabled );
		btnNotificationSoundReset.setEnabled( Build.VERSION.SDK_INT < 26 && enabled );
		btnNotificationStyleEdit.setEnabled( Build.VERSION.SDK_INT >= 26 && enabled );
		
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
		
		case R.id.btnProfileAvatar:
			pickAvatarImage();
			break;
		
		case R.id.btnProfileHeader:
			pickHeaderImage();
			break;
		
		case R.id.btnDisplayName:
			sendDisplayName(false);
			break;
		
		case R.id.btnNote:
			sendNote(false);
			break;
			
		case R.id.btnNotificationStyleEdit:
			if( Build.VERSION.SDK_INT >= 26 ){
				NotificationChannel channel = NotificationHelper.createNotificationChannel( this,account );
				Intent intent = new Intent( Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS );
				intent.putExtra( Settings.EXTRA_CHANNEL_ID, channel.getId() );
				intent.putExtra( Settings.EXTRA_APP_PACKAGE, getPackageName() );
				startActivity( intent );
			}
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
									.url( PollingWorker.APP_SERVER + "/unregister" )
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
		
		//noinspection deprecation
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
	
	//////////////////////////////////////////////////////////////////////////
	
	private void initializeProfile(){
		// 初期状態
		ivProfileAvatar.setErrorImageResId( Styler.getAttributeResourceId( this, R.attr.ic_question ) );
		ivProfileAvatar.setDefaultImageResId( Styler.getAttributeResourceId( this, R.attr.ic_question ) );
		etDisplayName.setText( "(loading...)" );
		etNote.setText( "(loading...)" );
		// 初期状態では編集不可能
		btnProfileAvatar.setEnabled( false );
		btnProfileHeader.setEnabled( false );
		etDisplayName.setEnabled( false );
		btnDisplayName.setEnabled( false );
		etNote.setEnabled( false );
		btnNote.setEnabled( false );
		// 疑似アカウントなら編集不可のまま
		if( account.isPseudo() ) return;
		
		loadProfile();
	}
	
	void loadProfile(){
		// サーバから情報をロードする
		
		//noinspection deprecation
		final ProgressDialog progress = new ProgressDialog( this );
		
		final AsyncTask< Void, Void, TootApiResult > task = new AsyncTask< Void, Void, TootApiResult >() {
			
			TootAccount data;
			
			@Override protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( ActAccountSetting.this, new TootApiClient.Callback() {
					@Override public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override public void publishApiProgress( final String s ){
					}
				} );
				client.setAccount( account );
				
				TootApiResult result = client.request( "/api/v1/accounts/verify_credentials" );
				if( result != null && result.object != null ){
					data = TootAccount.parse( ActAccountSetting.this, account, result.object );
					if( data == null ) return new TootApiResult( "TootAccount parse failed." );
				}
				return result;
			}
			
			@Override
			protected void onCancelled( TootApiResult result ){
				super.onPostExecute( result );
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				try{
					progress.dismiss();
				}catch( Throwable ignored ){
				}
				if( result == null ){
					// cancelled.
				}else if( data != null ){
					showProfile( data );
				}else{
					Utils.showToast( ActAccountSetting.this, true, result.error );
				}
			}
			
		};
		task.executeOnExecutor( App1.task_executor );
		progress.setIndeterminate( true );
		progress.setOnDismissListener( new DialogInterface.OnDismissListener() {
			@Override public void onDismiss( DialogInterface dialog ){
				task.cancel( true );
			}
		} );
		progress.show();
	}
	
	void showProfile( TootAccount src ){
		ivProfileAvatar.setImageUrl( App1.pref, 16f, src.avatar_static, src.avatar );
		ivProfileHeader.setImageUrl( App1.pref, 0f, src.header_static, src.header );
		
		etDisplayName.setText( Emojione.decodeEmoji( this, src.display_name == null ? "" : src.display_name ) );
		
		if( src.source != null && src.source.note != null ){
			etNote.setText( Emojione.decodeEmoji( this, src.source.note ) );
		}else if( src.note != null ){
			etNote.setText( HTMLDecoder.decodeHTML( ActAccountSetting.this, account, src.note, false, false, null ,null) );
		}else{
			etNote.setText( "" );
		}
		
		// 編集可能にする
		btnProfileAvatar.setEnabled( true );
		btnProfileHeader.setEnabled( true );
		etDisplayName.setEnabled( true );
		btnDisplayName.setEnabled( true );
		etNote.setEnabled( true );
		btnNote.setEnabled( true );
	}
	
	void updateCredential( final String form_data ){
		
		//noinspection deprecation
		final ProgressDialog progress = new ProgressDialog( this );
		
		final AsyncTask< Void, Void, TootApiResult > task = new AsyncTask< Void, Void, TootApiResult >() {
			
			TootAccount data;
			
			@Override protected TootApiResult doInBackground( Void... params ){
				TootApiClient client = new TootApiClient( ActAccountSetting.this, new TootApiClient.Callback() {
					@Override public boolean isApiCancelled(){
						return isCancelled();
					}
					
					@Override public void publishApiProgress( final String s ){
					}
				} );
				client.setAccount( account );
				
				Request.Builder request_builder = new Request.Builder()
					.patch( RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED
						, form_data
					) );
				
				TootApiResult result = client.request( "/api/v1/accounts/update_credentials", request_builder );
				if( result != null && result.object != null ){
					data = TootAccount.parse( ActAccountSetting.this, account, result.object );
					if( data == null ) return new TootApiResult( "TootAccount parse failed." );
				}
				return result;
			}
			
			@Override
			protected void onCancelled( TootApiResult result ){
				super.onPostExecute( result );
			}
			
			@Override
			protected void onPostExecute( TootApiResult result ){
				try{
					progress.dismiss();
				}catch( Throwable ignored ){
				}
				
				if( result == null ){
					// cancelled.
				}else if( data != null ){
					showProfile( data );
				}else{
					Utils.showToast( ActAccountSetting.this, true, result.error );
				}
			}
			
		};
		task.executeOnExecutor( App1.task_executor );
		progress.setIndeterminate( true );
		progress.setOnDismissListener( new DialogInterface.OnDismissListener() {
			@Override public void onDismiss( DialogInterface dialog ){
				task.cancel( true );
			}
		} );
		progress.show();
	}
	
	static final int max_length_display_name = 30;
	static final int max_length_note = 160;
	
	private void sendDisplayName(boolean bConfirmed){
		String sv = etDisplayName.getText().toString();
		if( !bConfirmed ){
			int length = sv.codePointCount( 0,sv.length() );
			if( length > max_length_display_name ){
				new AlertDialog.Builder( this )
					.setMessage( getString(R.string.length_warning
						,getString(R.string.display_name)
						,length,max_length_display_name
					))
					.setNegativeButton( R.string.cancel ,null)
					.setPositiveButton( R.string.ok, new DialogInterface.OnClickListener() {
						@Override public void onClick( DialogInterface dialogInterface, int i ){
							sendDisplayName(true);
						}
					} )
					.setCancelable( true )
					.show();
				return;
			}
		}
		updateCredential( "display_name=" + Uri.encode(sv ) );
	}
	
	private void sendNote(boolean bConfirmed){
		String sv = etNote.getText().toString();
		if( !bConfirmed ){
			int length = sv.codePointCount( 0,sv.length() );
			if( length > max_length_note ){
				new AlertDialog.Builder( this )
					.setMessage( getString(R.string.length_warning
						,getString(R.string.note)
						,length,max_length_note
					))
					.setNegativeButton( R.string.cancel ,null)
					.setPositiveButton( R.string.ok, new DialogInterface.OnClickListener() {
						@Override public void onClick( DialogInterface dialogInterface, int i ){
							sendNote(true);
						}
					} )
					.setCancelable( true )
					.show();
				return;
			}
		}
		updateCredential( "note=" + Uri.encode(sv ) );
	}
	
	private static final int PERMISSION_REQUEST_AVATAR = 1;
	private static final int PERMISSION_REQUEST_HEADER = 2;
	
	private void pickAvatarImage(){
		openPicker( PERMISSION_REQUEST_AVATAR );
	}
	
	private void pickHeaderImage(){
		openPicker( PERMISSION_REQUEST_HEADER );
	}
	
	void openPicker( final int permission_request_code ){
		int permissionCheck = ContextCompat.checkSelfPermission( this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE );
		if( permissionCheck != PackageManager.PERMISSION_GRANTED ){
			preparePermission( permission_request_code );
			return;
		}
		
		ActionsDialog a = new ActionsDialog();
		a.addAction( getString( R.string.image_pick ), new Runnable() {
			@Override public void run(){
				performAttachment( permission_request_code == PERMISSION_REQUEST_AVATAR ? REQUEST_CODE_AVATAR_ATTACHMENT : REQUEST_CODE_HEADER_ATTACHMENT );
			}
		} );
		a.addAction( getString( R.string.image_capture ), new Runnable() {
			@Override public void run(){
				performCamera( permission_request_code == PERMISSION_REQUEST_AVATAR ? REQUEST_CODE_AVATAR_CAMERA : REQUEST_CODE_HEADER_CAMERA );
			}
		} );
		a.show( this, null );
	}
	
	private void preparePermission( int request_code ){
		if( Build.VERSION.SDK_INT >= 23 ){
			// No explanation needed, we can request the permission.
			
			ActivityCompat.requestPermissions( this
				, new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE }
				, request_code
			);
			return;
		}
		Utils.showToast( this, true, R.string.missing_storage_permission );
	}
	
	@Override public void onRequestPermissionsResult(
		int requestCode
		, @NonNull String permissions[]
		, @NonNull int[] grantResults
	){
		switch( requestCode ){
		case PERMISSION_REQUEST_AVATAR:
		case PERMISSION_REQUEST_HEADER:
			// If request is cancelled, the result arrays are empty.
			if( grantResults.length > 0 &&
				grantResults[ 0 ] == PackageManager.PERMISSION_GRANTED
				){
				openPicker( requestCode );
			}else{
				Utils.showToast( this, true, R.string.missing_storage_permission );
			}
			break;
		}
	}
	
	private void performAttachment( final int request_code ){
		// SAFのIntentで開く
		try{
			Intent intent = new Intent( Intent.ACTION_OPEN_DOCUMENT );
			intent.addCategory( Intent.CATEGORY_OPENABLE );
			intent.setType( "*/*" );
			intent.putExtra( Intent.EXTRA_ALLOW_MULTIPLE, false );
			intent.putExtra( Intent.EXTRA_MIME_TYPES, new String[]{ "image/*", "video/*" } );
			startActivityForResult( intent
				, request_code );
		}catch( Throwable ex ){
			log.trace( ex );
			Utils.showToast( this, ex, "ACTION_OPEN_DOCUMENT failed." );
		}
	}
	
	Uri uriCameraImage;
	
	private void performCamera( final int request_code ){
		
		try{
			// カメラで撮影
			String filename = System.currentTimeMillis() + ".jpg";
			ContentValues values = new ContentValues();
			values.put( MediaStore.Images.Media.TITLE, filename );
			values.put( MediaStore.Images.Media.MIME_TYPE, "image/jpeg" );
			uriCameraImage = getContentResolver().insert( MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values );
			
			Intent intent = new Intent( MediaStore.ACTION_IMAGE_CAPTURE );
			intent.putExtra( MediaStore.EXTRA_OUTPUT, uriCameraImage );
			
			startActivityForResult( intent, request_code );
		}catch( Throwable ex ){
			log.trace( ex );
			Utils.showToast( this, ex, "opening camera app failed." );
		}
	}
	
	interface InputStreamOpener {
		InputStream open() throws IOException;
		
		String getMimeType();
		
		void deleteTempFile();
	}
	
	static final String MIME_TYPE_JPEG = "image/jpeg";
	static final String MIME_TYPE_PNG = "image/png";
	
	private InputStreamOpener createOpener( final Uri uri, final String mime_type ){
		//noinspection LoopStatementThatDoesntLoop
		for( ; ; ){
			try{
				
				// 画像の種別
				boolean is_jpeg = MIME_TYPE_JPEG.equals( mime_type );
				boolean is_png = MIME_TYPE_PNG.equals( mime_type );
				if( ! is_jpeg && ! is_png ){
					log.d( "createOpener: source is not jpeg or png" );
					break;
				}
				
				// 設定からリサイズ指定を読む
				int resize_to = 1280;
				
				Bitmap bitmap = Utils.createResizedBitmap( log, this, uri, false, resize_to );
				if( bitmap != null ){
					try{
						File cache_dir = getExternalCacheDir();
						if( cache_dir == null ){
							Utils.showToast( this, false, "getExternalCacheDir returns null." );
							break;
						}
						
						//noinspection ResultOfMethodCallIgnored
						cache_dir.mkdir();
						
						final File temp_file = new File( cache_dir, "tmp." + Thread.currentThread().getId() );
						FileOutputStream os = new FileOutputStream( temp_file );
						try{
							if( is_jpeg ){
								bitmap.compress( Bitmap.CompressFormat.JPEG, 95, os );
							}else{
								bitmap.compress( Bitmap.CompressFormat.PNG, 100, os );
							}
						}finally{
							os.close();
						}
						
						return new InputStreamOpener() {
							@Override public InputStream open() throws IOException{
								return new FileInputStream( temp_file );
							}
							
							@Override public String getMimeType(){
								return mime_type;
							}
							
							@Override public void deleteTempFile(){
								//noinspection ResultOfMethodCallIgnored
								temp_file.delete();
							}
						};
					}finally{
						bitmap.recycle();
					}
				}
				
			}catch( Throwable ex ){
				log.trace( ex );
				Utils.showToast( this, ex, "Resizing image failed." );
			}
			
			break;
		}
		return new InputStreamOpener() {
			@Override public InputStream open() throws IOException{
				return getContentResolver().openInputStream( uri );
			}
			
			@Override public String getMimeType(){
				return mime_type;
			}
			
			@Override public void deleteTempFile(){
				
			}
		};
	}
	
	void addAttachment( final int request_code, final Uri uri, final String mime_type ){
		
		if( mime_type == null ){
			Utils.showToast( this, false, "mime type is not provided." );
			return;
		}
		
		if( ! mime_type.startsWith( "image/" ) ){
			Utils.showToast( this, false, "mime type is not image." );
			return;
		}
		
		new AsyncTask< Void, Void, String >() {
			
			@Override protected String doInBackground( Void... params ){
				try{
					final InputStreamOpener opener = createOpener( uri, mime_type );
					try{
						InputStream is = opener.open();
						try{
							ByteArrayOutputStream bao = new ByteArrayOutputStream();
							//
							bao.write( Utils.encodeUTF8( "data:" + opener.getMimeType() + ";base64," ) );
							//
							Base64OutputStream base64 = new Base64OutputStream( bao, Base64.NO_WRAP );
							try{
								IOUtils.copy( is, base64 );
							}finally{
								base64.close();
							}
							String value = Utils.decodeUTF8( bao.toByteArray() );
							
							switch( request_code ){
							case REQUEST_CODE_AVATAR_ATTACHMENT:
							case REQUEST_CODE_AVATAR_CAMERA:
								return "avatar=" + Uri.encode( value );
							case REQUEST_CODE_HEADER_ATTACHMENT:
							case REQUEST_CODE_HEADER_CAMERA:
								return "header=" + Uri.encode( value );
							}
						}finally{
							IOUtils.closeQuietly( is );
						}
					}finally{
						opener.deleteTempFile();
					}
					
				}catch( Throwable ex ){
					Utils.showToast( ActAccountSetting.this, ex, "image converting failed." );
				}
				return null;
			}
			
			@Override
			protected void onPostExecute( String form_data ){
				if( form_data != null ){
					updateCredential( form_data );
				}
			}
			
		}.executeOnExecutor( App1.task_executor );
	}
	
}

