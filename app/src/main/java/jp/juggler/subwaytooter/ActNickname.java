package jp.juggler.subwaytooter;

import android.app.Activity;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.jrummyapps.android.colorpicker.ColorPickerDialog;
import com.jrummyapps.android.colorpicker.ColorPickerDialogListener;

import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.util.Utils;

public class ActNickname extends AppCompatActivity implements View.OnClickListener, ColorPickerDialogListener {
	
	static final String EXTRA_ACCT = "acct";
	static final String EXTRA_SHOW_NOTIFICATION_SOUND = "show_notification_sound";
	
	public static void open( Activity activity, String full_acct, boolean bShowNotificationSound, int requestCode ){
		Intent intent = new Intent( activity, ActNickname.class );
		intent.putExtra( EXTRA_ACCT, full_acct );
		intent.putExtra( EXTRA_SHOW_NOTIFICATION_SOUND, bShowNotificationSound );
		activity.startActivityForResult( intent, requestCode );
	}
	
	@Override public void onBackPressed(){
		setResult( RESULT_OK );
		super.onBackPressed();
	}
	
	boolean show_notification_sound;
	String acct;
	int color_fg;
	int color_bg;
	String notification_sound_uri;
	
	@Override protected void onCreate( @Nullable Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		App1.setActivityTheme( this, false );
		
		Intent intent = getIntent();
		this.acct = intent.getStringExtra( EXTRA_ACCT );
		this.show_notification_sound = intent.getBooleanExtra( EXTRA_SHOW_NOTIFICATION_SOUND, false );
		
		initUI();
		
		load();
	}
	
	TextView tvPreview;
	TextView tvAcct;
	EditText etNickname;
	View btnTextColorEdit;
	View btnTextColorReset;
	View btnBackgroundColorEdit;
	View btnBackgroundColorReset;
	View btnSave;
	View btnDiscard;
	Button btnNotificationSoundEdit;
	Button btnNotificationSoundReset;
	
	private void initUI(){
		
		setTitle( getString( show_notification_sound ? R.string.nickname_and_color_and_notification_sound : R.string.nickname_and_color ) );
		setContentView( R.layout.act_nickname );
		
		Styler.fixHorizontalPadding( findViewById( R.id.llContent ) );
		
		tvPreview = findViewById( R.id.tvPreview );
		tvAcct = findViewById( R.id.tvAcct );
		
		etNickname = findViewById( R.id.etNickname );
		btnTextColorEdit = findViewById( R.id.btnTextColorEdit );
		btnTextColorReset = findViewById( R.id.btnTextColorReset );
		btnBackgroundColorEdit = findViewById( R.id.btnBackgroundColorEdit );
		btnBackgroundColorReset = findViewById( R.id.btnBackgroundColorReset );
		btnSave = findViewById( R.id.btnSave );
		btnDiscard = findViewById( R.id.btnDiscard );
		
		etNickname = findViewById( R.id.etNickname );
		btnTextColorEdit.setOnClickListener( this );
		btnTextColorReset.setOnClickListener( this );
		btnBackgroundColorEdit.setOnClickListener( this );
		btnBackgroundColorReset.setOnClickListener( this );
		btnSave.setOnClickListener( this );
		btnDiscard.setOnClickListener( this );
		
		btnNotificationSoundEdit = findViewById( R.id.btnNotificationSoundEdit );
		btnNotificationSoundReset = findViewById( R.id.btnNotificationSoundReset );
		btnNotificationSoundEdit.setOnClickListener( this );
		btnNotificationSoundReset.setOnClickListener( this );
		
		boolean bBefore8 = Build.VERSION.SDK_INT < 26;
		btnNotificationSoundEdit.setEnabled( bBefore8 );
		btnNotificationSoundReset.setEnabled( bBefore8 );
		
		etNickname.addTextChangedListener( new TextWatcher() {
			@Override
			public void beforeTextChanged( CharSequence s, int start, int count, int after ){
				
			}
			
			@Override public void onTextChanged( CharSequence s, int start, int before, int count ){
				
			}
			
			@Override public void afterTextChanged( Editable s ){
				show();
			}
		} );
	}
	
	boolean bLoading = false;
	
	private void load(){
		bLoading = true;
		
		findViewById( R.id.llNotificationSound ).setVisibility( show_notification_sound ? View.VISIBLE : View.GONE );
		
		tvAcct.setText( acct );
		
		AcctColor ac = AcctColor.load( acct );
		if( ac != null ){
			color_bg = ac.color_bg;
			color_fg = ac.color_fg;
			etNickname.setText( ac.nickname == null ? "" : ac.nickname );
			notification_sound_uri = ac.notification_sound;
		}
		
		bLoading = false;
		show();
	}
	
	private void save(){
		if( bLoading ) return;
		new AcctColor(
			acct
			, etNickname.getText().toString().trim()
			, color_fg
			, color_bg
			, notification_sound_uri
		).save( System.currentTimeMillis() );
	}
	
	private void show(){
		String s = etNickname.getText().toString().trim();
		tvPreview.setText( ! TextUtils.isEmpty( s ) ? s : acct );
		int c;
		
		c = color_fg;
		if( c == 0 ) c = Styler.getAttributeColor( this, R.attr.colorTimeSmall );
		tvPreview.setTextColor( c );
		
		c = color_bg;
		tvPreview.setBackgroundColor( c );
	}
	
	@Override public void onClick( View v ){
		ColorPickerDialog.Builder builder;
		switch( v.getId() ){
		case R.id.btnTextColorEdit:
			Utils.hideKeyboard( this, etNickname );
			builder = ColorPickerDialog.newBuilder()
				.setDialogType( ColorPickerDialog.TYPE_CUSTOM )
				.setAllowPresets( true )
				.setShowAlphaSlider( false )
				.setDialogId( 1 )
			;
			if( color_fg != 0 ) builder.setColor( color_fg );
			builder.show( this );
			break;
		case R.id.btnTextColorReset:
			color_fg = 0;
			show();
			break;
		case R.id.btnBackgroundColorEdit:
			Utils.hideKeyboard( this, etNickname );
			builder = ColorPickerDialog.newBuilder()
				.setDialogType( ColorPickerDialog.TYPE_CUSTOM )
				.setAllowPresets( true )
				.setShowAlphaSlider( false )
				.setDialogId( 2 )
			;
			if( color_bg != 0 ) builder.setColor( color_bg );
			builder.show( this );
			break;
		case R.id.btnBackgroundColorReset:
			color_bg = 0;
			show();
			break;
		case R.id.btnSave:
			save();
			setResult( RESULT_OK );
			finish();
			break;
		case R.id.btnDiscard:
			setResult( RESULT_CANCELED );
			finish();
			break;
		
		case R.id.btnNotificationSoundEdit:
			openNotificationSoundPicker();
			break;
		
		case R.id.btnNotificationSoundReset:
			notification_sound_uri = "";
			break;
			
		}
	}
	
	@Override public void onColorSelected( int dialogId, @ColorInt int color ){
		switch( dialogId ){
		case 1:
			color_fg = 0xff000000 | color;
			break;
		case 2:
			color_bg = 0xff000000 | color;
			break;
		}
		show();
	}
	
	@Override public void onDialogDismissed( int dialogId ){
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
	
	static final int REQUEST_CODE_NOTIFICATION_SOUND = 2;
	
	@Override protected void onActivityResult( int requestCode, int resultCode, Intent data ){
		if( resultCode == RESULT_OK && requestCode == REQUEST_CODE_NOTIFICATION_SOUND ){
			// RINGTONE_PICKERからの選択されたデータを取得する
			Uri uri = (Uri) data.getExtras().get( RingtoneManager.EXTRA_RINGTONE_PICKED_URI );
			if( uri != null ){
				notification_sound_uri = uri.toString();
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
}
