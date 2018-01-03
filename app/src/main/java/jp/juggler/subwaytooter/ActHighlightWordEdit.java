package jp.juggler.subwaytooter;

import android.app.Activity;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import com.jrummyapps.android.colorpicker.ColorPickerDialog;
import com.jrummyapps.android.colorpicker.ColorPickerDialogListener;

import org.json.JSONException;
import org.json.JSONObject;

import jp.juggler.subwaytooter.table.HighlightWord;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class ActHighlightWordEdit extends AppCompatActivity
	implements View.OnClickListener, ColorPickerDialogListener, CompoundButton.OnCheckedChangeListener
{
	static final LogCategory log = new LogCategory( "ActHighlightWordEdit" );
	
	static final String EXTRA_ITEM = "item";
	
	public static void open( @NonNull Activity activity, int request_code, @NonNull HighlightWord item ){
		try{
			Intent intent = new Intent( activity, ActHighlightWordEdit.class );
			intent.putExtra( EXTRA_ITEM, item.encodeJson().toString() );
			activity.startActivityForResult( intent, request_code );
		}catch( JSONException ex ){
			throw new RuntimeException( ex );
		}
		
	}
	
	HighlightWord item;
	
	private void makeResult(){
		try{
			Intent data = new Intent();
			data.putExtra( EXTRA_ITEM, item.encodeJson().toString() );
			setResult( RESULT_OK, data );
		}catch( JSONException ex ){
			throw new RuntimeException( ex );
		}
	}
	
	@Override public void onBackPressed(){
		makeResult();
		super.onBackPressed();
	}
	
	@Override protected void onCreate( @Nullable Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		App1.setActivityTheme( this, false );
		initUI();
		
		try{
			if( savedInstanceState != null ){
				item = new HighlightWord( new JSONObject( savedInstanceState.getString( EXTRA_ITEM ) ) );
			}else{
				item = new HighlightWord( new JSONObject( getIntent().getStringExtra( EXTRA_ITEM ) ) );
			}
		}catch( Throwable ex ){
			throw new RuntimeException( "can't loading data", ex );
		}
		
		showSampleText();
		
	}
	
	@Override protected void onDestroy(){
		super.onDestroy();
	}
	
	static final int REQUEST_CODE_NOTIFICATION_SOUND = 2;
	
	@Override protected void onActivityResult( int requestCode, int resultCode, Intent data ){
		switch( requestCode ){
		default:
			super.onActivityResult( requestCode, resultCode, data );
			break;
		
		case REQUEST_CODE_NOTIFICATION_SOUND:{
			if( resultCode == RESULT_OK ){
				// RINGTONE_PICKERからの選択されたデータを取得する
				Uri uri = (Uri) Utils.getExtraObject( data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI );
				if( uri != null ){
					item.sound_uri = uri.toString();
					item.sound_type = HighlightWord.SOUND_TYPE_CUSTOM;
					swSound.setChecked( true );
				}
			}
			break;
		}
		}
	}
	
	private TextView tvName;
	private Switch swSound;
	
	private void initUI(){
		setContentView( R.layout.act_highlight_edit );
		
		tvName = findViewById( R.id.tvName );
		swSound = findViewById( R.id.swSound );
		swSound.setOnCheckedChangeListener( this );

		findViewById( R.id.btnTextColorEdit ).setOnClickListener( this );
		findViewById( R.id.btnTextColorReset ).setOnClickListener( this );
		findViewById( R.id.btnBackgroundColorEdit ).setOnClickListener( this );
		findViewById( R.id.btnBackgroundColorReset ).setOnClickListener( this );
		findViewById( R.id.btnNotificationSoundEdit ).setOnClickListener( this );
		findViewById( R.id.btnNotificationSoundReset ).setOnClickListener( this );
	}
	
	boolean bBusy = false;
	private void showSampleText(){
		bBusy = true;
		try{
			
			swSound.setChecked( item.sound_type != HighlightWord.SOUND_TYPE_NONE );
			
			tvName.setText( item.name );
			
			int c = item.color_bg;
			if( c == 0 ){
				tvName.setBackgroundColor( 0 );
			}else{
				tvName.setBackgroundColor( c );
			}
			
			c = item.color_fg;
			if( c == 0 ){
				tvName.setTextColor( Styler.getAttributeColor( this, android.R.attr.textColorPrimary ) );
			}else{
				tvName.setTextColor( c );
			}
		}finally{
			bBusy = false;
		}
	}
	
	@Override public void onClick( View v ){
		
		switch( v.getId() ){
		
		case R.id.btnTextColorEdit:
			openColorPicker( COLOR_DIALOG_ID_TEXT, item.color_fg );
			break;
		
		case R.id.btnTextColorReset:
			item.color_fg = 0;
			showSampleText();
			break;
		
		case R.id.btnBackgroundColorEdit:
			openColorPicker( COLOR_DIALOG_ID_BACKGROUND, item.color_bg );
			break;
		
		case R.id.btnBackgroundColorReset:
			item.color_bg = 0;
			showSampleText();
			break;
		
		case R.id.btnNotificationSoundEdit:
			openNotificationSoundPicker();
			break;
		
		case R.id.btnNotificationSoundReset:
			item.sound_uri = null;
			item.sound_type = swSound.isChecked() ? HighlightWord.SOUND_TYPE_DEFAULT : HighlightWord.SOUND_TYPE_NONE;
			break;
		}
		
	}
	
	@Override public void onCheckedChanged( CompoundButton buttonView, boolean isChecked ){
		if( bBusy ) return;
		
		if( ! isChecked ){
			item.sound_type = HighlightWord.SOUND_TYPE_NONE;
		}else{
			item.sound_type = TextUtils.isEmpty( item.sound_uri ) ? HighlightWord.SOUND_TYPE_DEFAULT : HighlightWord.SOUND_TYPE_CUSTOM;
		}
	}
	
	//////////////////////////////////////////////////////////////////
	// using ColorPickerDialog
	
	private static final int COLOR_DIALOG_ID_TEXT = 1;
	private static final int COLOR_DIALOG_ID_BACKGROUND = 2;
	
	private void openColorPicker( int id, int initial_color ){
		ColorPickerDialog.Builder builder = ColorPickerDialog.newBuilder()
			.setDialogType( ColorPickerDialog.TYPE_CUSTOM )
			.setAllowPresets( true )
			.setShowAlphaSlider(  id == COLOR_DIALOG_ID_BACKGROUND )
			.setDialogId( id );
		
		if( initial_color != 0 ) builder.setColor( initial_color );
		
		builder.show( this );
		
	}
	
	@Override public void onDialogDismissed( int dialogId ){
	}
	
	@Override public void onColorSelected( int dialogId, int color ){
		switch( dialogId ){
		case COLOR_DIALOG_ID_TEXT:
			item.color_fg = 0xff000000 | color;
			break;
		case COLOR_DIALOG_ID_BACKGROUND:
			item.color_bg = color == 0 ? 0x01000000 : color;
			break;
		}
		showSampleText();
	}
	
	//////////////////////////////////////////////////////////////////
	
	private void openNotificationSoundPicker(){
		Intent intent = new Intent( RingtoneManager.ACTION_RINGTONE_PICKER );
		intent.putExtra( RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION );
		intent.putExtra( RingtoneManager.EXTRA_RINGTONE_TITLE, R.string.notification_sound );
		intent.putExtra( RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false );
		intent.putExtra( RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false );
		try{
			Uri uri = TextUtils.isEmpty( item.sound_uri ) ? null : Uri.parse( item.sound_uri );
			if( uri != null ){
				intent.putExtra( RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri );
			}
		}catch( Throwable ignored ){
		}
		
		Intent chooser = Intent.createChooser( intent, getString( R.string.notification_sound ) );
		startActivityForResult( chooser, REQUEST_CODE_NOTIFICATION_SOUND );
	}
}
