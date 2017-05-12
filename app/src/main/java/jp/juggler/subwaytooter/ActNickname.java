package jp.juggler.subwaytooter;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.jrummyapps.android.colorpicker.ColorPickerDialog;
import com.jrummyapps.android.colorpicker.ColorPickerDialogListener;

import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.util.Utils;

public class ActNickname extends AppCompatActivity implements View.OnClickListener, ColorPickerDialogListener {
	
	static final String EXTRA_ACCT = "acct";
	
	public static void open( Activity activity, String full_acct, int requestCode ){
		Intent intent = new Intent( activity, ActNickname.class );
		intent.putExtra( EXTRA_ACCT, full_acct );
		activity.startActivityForResult( intent, requestCode );
	}
	
	@Override public void onBackPressed(){
		setResult( RESULT_OK );
		super.onBackPressed();
	}
	
	String acct;
	int color_fg;
	int color_bg;
	
	@Override protected void onCreate( @Nullable Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		App1.setActivityTheme( this, false );
		initUI();
		
		this.acct = getIntent().getStringExtra( EXTRA_ACCT );
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
	
	private void initUI(){
		setContentView( R.layout.act_nickname );
		
		Styler.fixHorizontalPadding(findViewById( R.id.llContent ));
		
		tvPreview = (TextView) findViewById( R.id.tvPreview );
		tvAcct = (TextView) findViewById( R.id.tvAcct );
		
		etNickname = (EditText) findViewById( R.id.etNickname );
		btnTextColorEdit = findViewById( R.id.btnTextColorEdit );
		btnTextColorReset = findViewById( R.id.btnTextColorReset );
		btnBackgroundColorEdit = findViewById( R.id.btnBackgroundColorEdit );
		btnBackgroundColorReset = findViewById( R.id.btnBackgroundColorReset );
		btnSave = findViewById( R.id.btnSave );
		btnDiscard = findViewById( R.id.btnDiscard );
		
		etNickname = (EditText) findViewById( R.id.etNickname );
		btnTextColorEdit.setOnClickListener( this );
		btnTextColorReset.setOnClickListener( this );
		btnBackgroundColorEdit.setOnClickListener( this );
		btnBackgroundColorReset.setOnClickListener( this );
		btnSave.setOnClickListener( this );
		btnDiscard.setOnClickListener( this );
		
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
		
		tvAcct.setText( acct );
		
		AcctColor ac = AcctColor.load( acct );
		if( ac != null ){
			color_bg = ac.color_bg;
			color_fg = ac.color_fg;
			etNickname.setText( ac.nickname == null ? "" : ac.nickname );
		}
		
		bLoading = false;
		show();
	}
	
	private void save(){
		if( bLoading ) return;
		new AcctColor(
			acct
			,etNickname.getText().toString().trim()
			,color_fg
			,color_bg
		).save( System.currentTimeMillis() );
	}
	
	private void show(){
		String s = etNickname.getText().toString().trim();
		tvPreview.setText( ! TextUtils.isEmpty( s ) ? s : acct );
		int c;
		
		c = color_fg;
		if( c == 0 ) c = Styler.getAttributeColor( this, R.attr.colorAcctSmall );
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
}
