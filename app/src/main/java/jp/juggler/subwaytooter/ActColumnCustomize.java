package jp.juggler.subwaytooter;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.jrummyapps.android.colorpicker.ColorPickerDialog;
import com.jrummyapps.android.colorpicker.ColorPickerDialogListener;

import java.util.Locale;

import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class ActColumnCustomize extends AppCompatActivity
	implements View.OnClickListener, ColorPickerDialogListener
{
	static final LogCategory log = new LogCategory( "ActColumnCustomize" );
	
	static final String EXTRA_COLUMN_INDEX = "column_index";
	
	public static void open( ActMain activity, int idx, int request_code ){
		Intent intent = new Intent( activity, ActColumnCustomize.class );
		intent.putExtra( EXTRA_COLUMN_INDEX, idx );
		activity.startActivityForResult( intent, request_code );
		
	}
	
	@Override public void onBackPressed(){
		makeResult();
		super.onBackPressed();
	}
	
	private void makeResult(){
		Intent data = new Intent();
		data.putExtra( EXTRA_COLUMN_INDEX, column_index );
		setResult( RESULT_OK, data );
	}
	
	int column_index;
	Column column;
	AppState app_state;
	float density;
	
	@Override protected void onCreate( @Nullable Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		App1.setActivityTheme( this, false );
		initUI();
		
		app_state = App1.getAppState( this );
		density = app_state.density;
		column_index = getIntent().getIntExtra( EXTRA_COLUMN_INDEX, 0 );
		column = app_state.column_list.get( column_index );
		
		show();
	}
	
	@Override protected void onDestroy(){
		closeBitmaps();
		super.onDestroy();
	}
	
	static final int COLOR_DIALOG_ID_HEADER_BACKGROUND = 1;
	static final int COLOR_DIALOG_ID_HEADER_FOREGROUND = 2;
	static final int COLOR_DIALOG_ID_COLUMN_BACKGROUND = 3;
	
	@Override public void onClick( View v ){
		ColorPickerDialog.Builder builder;
		switch( v.getId() ){
		
		case R.id.btnHeaderBackgroundEdit:
			builder = ColorPickerDialog.newBuilder()
				.setDialogType( ColorPickerDialog.TYPE_CUSTOM )
				.setAllowPresets( true )
				.setShowAlphaSlider( false )
				.setDialogId( COLOR_DIALOG_ID_HEADER_BACKGROUND )
			;
			if( column.header_bg_color != 0 ) builder.setColor( column.header_bg_color );
			builder.show( this );
			break;
		
		case R.id.btnHeaderBackgroundReset:
			column.header_bg_color = 0;
			show();
			break;
		
		case R.id.btnHeaderTextEdit:
			builder = ColorPickerDialog.newBuilder()
				.setDialogType( ColorPickerDialog.TYPE_CUSTOM )
				.setAllowPresets( true )
				.setShowAlphaSlider( false )
				.setDialogId( COLOR_DIALOG_ID_HEADER_FOREGROUND )
			;
			if( column.header_fg_color != 0 ) builder.setColor( column.header_fg_color );
			builder.show( this );
			break;
		
		case R.id.btnHeaderTextReset:
			column.header_fg_color = 0;
			show();
			break;
		
		case R.id.btnColumnBackgroundColor:
			builder = ColorPickerDialog.newBuilder()
				.setDialogType( ColorPickerDialog.TYPE_CUSTOM )
				.setAllowPresets( true )
				.setShowAlphaSlider( false )
				.setDialogId( COLOR_DIALOG_ID_COLUMN_BACKGROUND )
			;
			if( column.column_bg_color != 0 ) builder.setColor( column.column_bg_color );
			builder.show( this );
			break;
		
		case R.id.btnColumnBackgroundColorReset:
			column.column_bg_color = 0;
			show();
			break;
		
		case R.id.btnColumnBackgroundImage:
			Intent intent = new Intent( Intent.ACTION_OPEN_DOCUMENT );
			intent.addCategory( Intent.CATEGORY_OPENABLE );
			intent.setType( "image/*" );
			startActivityForResult( intent, REQUEST_CODE_PICK_BACKGROUND );
			break;
		
		case R.id.btnColumnBackgroundImageReset:
			column.column_bg_image = null;
			show();
			break;
		}
	}
	
	@Override public void onColorSelected( int dialogId, @ColorInt int color ){
		switch( dialogId ){
		case COLOR_DIALOG_ID_HEADER_BACKGROUND:
			column.header_bg_color = 0xff000000 | color;
			break;
		case COLOR_DIALOG_ID_HEADER_FOREGROUND:
			column.header_fg_color = 0xff000000 | color;
			break;
		case COLOR_DIALOG_ID_COLUMN_BACKGROUND:
			column.column_bg_color = 0xff000000 | color;
			break;
		}
		show();
	}
	
	@Override public void onDialogDismissed( int dialogId ){
	}
	
	static final int REQUEST_CODE_PICK_BACKGROUND = 1;
	
	@Override protected void onActivityResult( int requestCode, int resultCode, Intent data ){
		if( requestCode == REQUEST_CODE_PICK_BACKGROUND && resultCode == RESULT_OK ){
			if( data != null ){
				Uri uri = data.getData();
				if( uri != null ){
					getContentResolver().takePersistableUriPermission( uri, Intent.FLAG_GRANT_READ_URI_PERMISSION );
					column.column_bg_image = uri.toString();
					show();
				}
			}
		}
	}
	
	View flColumnBackground;
	ImageView ivColumnBackground;
	SeekBar sbColumnBackgroundAlpha;
	View llColumnHeader;
	ImageView ivColumnHeader;
	TextView tvColumnName;
	EditText etAlpha;
	
	static final int PROGRESS_MAX = 65536;
	
	private void initUI(){
		setContentView( R.layout.act_column_customize );
		
		Styler.fixHorizontalPadding( findViewById( R.id.svContent ) );
		
		findViewById( R.id.btnHeaderBackgroundEdit ).setOnClickListener( this );
		findViewById( R.id.btnHeaderBackgroundReset ).setOnClickListener( this );
		findViewById( R.id.btnHeaderTextEdit ).setOnClickListener( this );
		findViewById( R.id.btnHeaderTextReset ).setOnClickListener( this );
		findViewById( R.id.btnColumnBackgroundColor ).setOnClickListener( this );
		findViewById( R.id.btnColumnBackgroundColorReset ).setOnClickListener( this );
		findViewById( R.id.btnColumnBackgroundImage ).setOnClickListener( this );
		findViewById( R.id.btnColumnBackgroundImageReset ).setOnClickListener( this );
		
		llColumnHeader = findViewById( R.id.llColumnHeader );
		ivColumnHeader = (ImageView) findViewById( R.id.ivColumnHeader );
		tvColumnName = (TextView) findViewById( R.id.tvColumnName );
		
		flColumnBackground = findViewById( R.id.flColumnBackground );
		ivColumnBackground = (ImageView) findViewById( R.id.ivColumnBackground );
		
		sbColumnBackgroundAlpha = (SeekBar) findViewById( R.id.sbColumnBackgroundAlpha );
		sbColumnBackgroundAlpha.setMax( PROGRESS_MAX );
		
		sbColumnBackgroundAlpha.setOnSeekBarChangeListener( new SeekBar.OnSeekBarChangeListener() {
			@Override public void onStartTrackingTouch( SeekBar seekBar ){
			}
			
			@Override public void onStopTrackingTouch( SeekBar seekBar ){
				
			}
			
			@Override
			public void onProgressChanged( SeekBar seekBar, int progress, boolean fromUser ){
				if( loading_busy ) return;
				if( ! fromUser ) return;
				column.column_bg_image_alpha = progress / (float) PROGRESS_MAX;
				ivColumnBackground.setAlpha( column.column_bg_image_alpha );
				etAlpha.setText( String.format( Locale.JAPAN, "%.4f", column.column_bg_image_alpha ) );
			}
			
		} );
		
		etAlpha = (EditText) findViewById( R.id.etAlpha );
		etAlpha.addTextChangedListener( new TextWatcher() {
			@Override
			public void beforeTextChanged( CharSequence s, int start, int count, int after ){
				
			}
			
			@Override public void onTextChanged( CharSequence s, int start, int before, int count ){
				
			}
			
			@Override public void afterTextChanged( Editable s ){
				if( loading_busy ) return;
				try{
					float f = Float.parseFloat( etAlpha.getText().toString() );
					if( ! Float.isNaN( f ) ){
						if( f < 0f ) f = 0f;
						if( f > 1f ) f = 1f;
						column.column_bg_image_alpha = f;
						ivColumnBackground.setAlpha( column.column_bg_image_alpha );
						sbColumnBackgroundAlpha.setProgress( (int) ( 0.5f + f * PROGRESS_MAX ) );
					}
				}catch( Throwable ex ){
					log.e( ex, "alpha parse failed." );
				}
			}
		} );
	}
	
	boolean loading_busy;
	
	private void show(){
		try{
			loading_busy = true;
			int c = column.header_bg_color;
			if( c == 0 ){
				llColumnHeader.setBackgroundResource( R.drawable.btn_bg_ddd );
			}else{
				ViewCompat.setBackground( llColumnHeader, Styler.getAdaptiveRippleDrawable(
					c,
					( column.header_fg_color != 0 ? column.header_fg_color :
						Styler.getAttributeColor( this, R.attr.colorRippleEffect ) )
				) );
			}
			
			c = column.header_fg_color;
			if( c == 0 ){
				tvColumnName.setTextColor( Styler.getAttributeColor( this, android.R.attr.textColorPrimary ) );
				Styler.setIconDefaultColor( this, ivColumnHeader, Column.getIconAttrId( column.column_type ) );
			}else{
				tvColumnName.setTextColor( c );
				Styler.setIconCustomColor( this, ivColumnHeader, c, Column.getIconAttrId( column.column_type ) );
			}
			
			tvColumnName.setText( column.getColumnName( false ) );
			
			if( column.column_bg_color != 0 ){
				flColumnBackground.setBackgroundColor( column.column_bg_color );
			}else{
				ViewCompat.setBackground( flColumnBackground, null );
			}
			
			float alpha = column.column_bg_image_alpha;
			if( Float.isNaN( alpha ) ){
				alpha = column.column_bg_image_alpha = 1f;
			}
			ivColumnBackground.setAlpha( alpha );
			sbColumnBackgroundAlpha.setProgress( (int) ( 0.5f + alpha * PROGRESS_MAX ) );
			
			etAlpha.setText( String.format( Locale.getDefault(), "%.4f", column.column_bg_image_alpha ) );
			
			loadImage( ivColumnBackground, column.column_bg_image );
		}finally{
			loading_busy = false;
		}
	}
	
	String last_image_uri;
	Bitmap last_image_bitmap;
	
	private void closeBitmaps(){
		try{
			ivColumnBackground.setImageDrawable( null );
			last_image_uri = null;
			if( last_image_bitmap != null ){
				last_image_bitmap.recycle();
				last_image_bitmap = null;
			}
		}catch( Throwable ex ){
			ex.printStackTrace();
			
		}
	}
	
	private void loadImage( ImageView ivColumnBackground, String url ){
		try{
			if( TextUtils.isEmpty( url ) ){
				closeBitmaps();
				return;
				
			}else if( url.equals( last_image_uri ) ){
				// 今表示してるのと同じ
				return;
			}
			
			// 直前のBitmapを掃除する
			closeBitmaps();
			
			// 画像をロードして、成功したら表示してURLを覚える
			int resize_max = (int) ( 0.5f + 64f * density );
			Uri uri = Uri.parse( url );
			last_image_bitmap = Utils.createResizedBitmap( log, this, uri, false, resize_max );
			if( last_image_bitmap != null ){
				ivColumnBackground.setImageBitmap( last_image_bitmap );
				last_image_uri = url;
			}
			
		}catch( Throwable ex ){
			ex.printStackTrace();
		}
	}
}
