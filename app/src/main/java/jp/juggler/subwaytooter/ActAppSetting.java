package jp.juggler.subwaytooter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Switch;

public class ActAppSetting extends AppCompatActivity implements CompoundButton.OnCheckedChangeListener, AdapterView.OnItemSelectedListener {
	
	public static void open( Context context ){
		context.startActivity( new Intent( context, ActAppSetting.class ) );
	}
	
	SharedPreferences pref;
	
	@Override
	protected void onCreate( @Nullable Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		App1.setActivityTheme( this, false );
		initUI();
		pref = Pref.pref( this );
		
		loadUIFromData();
		
	}
	
	Switch swDontConfirmBeforeCloseColumn;
	Switch swPriorLocalURL;
	Switch swDisableFastScroller;
	Switch swSimpleList;
	Switch swExitAppWhenCloseProtectedColumn;
	Switch swShowFollowButtonInButtonBar;
	Switch swDontRound;
	
	
	Spinner spBackButtonAction;
	Spinner spUITheme;
	Spinner spResizeImage;
	Spinner spRefreshAfterToot;
	
	CheckBox cbNotificationSound;
	CheckBox cbNotificationVibration;
	CheckBox cbNotificationLED;
	
	static final int BACK_ASK_ALWAYS = 0;
	static final int BACK_CLOSE_COLUMN = 1;
	static final int BACK_OPEN_COLUMN_LIST = 2;
	static final int BACK_EXIT_APP = 3;
	
	private void initUI(){
		setContentView( R.layout.act_app_setting );
		swDontConfirmBeforeCloseColumn = (Switch) findViewById( R.id.swDontConfirmBeforeCloseColumn );
		swDontConfirmBeforeCloseColumn.setOnCheckedChangeListener( this );
		
		swPriorLocalURL = (Switch) findViewById( R.id.swPriorLocalURL );
		swPriorLocalURL.setOnCheckedChangeListener( this );
		
		swDisableFastScroller = (Switch) findViewById( R.id.swDisableFastScroller );
		swDisableFastScroller.setOnCheckedChangeListener( this );
		
		swSimpleList = (Switch) findViewById( R.id.swSimpleList );
		swSimpleList.setOnCheckedChangeListener( this );
		
		swExitAppWhenCloseProtectedColumn = (Switch) findViewById( R.id.swExitAppWhenCloseProtectedColumn );
		swExitAppWhenCloseProtectedColumn.setOnCheckedChangeListener( this );
		
		swShowFollowButtonInButtonBar = (Switch) findViewById( R.id.swShowFollowButtonInButtonBar );
		swShowFollowButtonInButtonBar.setOnCheckedChangeListener( this );
		
		swDontRound = (Switch) findViewById( R.id.swDontRound );
		swDontRound.setOnCheckedChangeListener( this );
		
		cbNotificationSound = (CheckBox) findViewById( R.id.cbNotificationSound );
		cbNotificationVibration = (CheckBox) findViewById( R.id.cbNotificationVibration );
		cbNotificationLED = (CheckBox) findViewById( R.id.cbNotificationLED );
		cbNotificationSound.setOnCheckedChangeListener( this );
		cbNotificationVibration.setOnCheckedChangeListener( this );
		cbNotificationLED.setOnCheckedChangeListener( this );
		
		{
			String[] caption_list = new String[]{
				getString( R.string.ask_always ),
				getString( R.string.close_column ),
				getString( R.string.open_column_list ),
				getString( R.string.app_exit ),
			};
			ArrayAdapter< String > adapter = new ArrayAdapter<>( this, android.R.layout.simple_spinner_item, caption_list );
			adapter.setDropDownViewResource( R.layout.lv_spinner_dropdown );
			spBackButtonAction = (Spinner) findViewById( R.id.spBackButtonAction );
			spBackButtonAction.setAdapter( adapter );
			spBackButtonAction.setOnItemSelectedListener( this );
		}
		
		{
			String[] caption_list = new String[]{
				getString( R.string.theme_light ),
				getString( R.string.theme_dark ),
			};
			ArrayAdapter< String > adapter = new ArrayAdapter<>( this, android.R.layout.simple_spinner_item, caption_list );
			adapter.setDropDownViewResource( R.layout.lv_spinner_dropdown );
			spUITheme = (Spinner) findViewById( R.id.spUITheme );
			spUITheme.setAdapter( adapter );
			spUITheme.setOnItemSelectedListener( this );
		}
		{
			String[] caption_list = new String[]{
				getString( R.string.dont_resize ),
				getString( R.string.long_side_pixel, 640 ),
				getString( R.string.long_side_pixel, 800 ),
				getString( R.string.long_side_pixel, 1024 ),
				getString( R.string.long_side_pixel, 1280 ),
				//// サーバ側でさらに縮小されるようなので、1280より上は用意しない
				//	Integer.toString( 1600 ),
				//	Integer.toString( 2048 ),
			};
			ArrayAdapter< String > adapter = new ArrayAdapter<>( this, android.R.layout.simple_spinner_item, caption_list );
			adapter.setDropDownViewResource( R.layout.lv_spinner_dropdown );
			spResizeImage = (Spinner) findViewById( R.id.spResizeImage );
			spResizeImage.setAdapter( adapter );
			spResizeImage.setOnItemSelectedListener( this );
		}
		
		{
			String[] caption_list = new String[]{
				getString( R.string.refresh_scroll_to_toot ),
				getString( R.string.refresh_no_scroll ),
				getString( R.string.dont_refresh ),
			};
			ArrayAdapter< String > adapter = new ArrayAdapter<>( this, android.R.layout.simple_spinner_item, caption_list );
			adapter.setDropDownViewResource( R.layout.lv_spinner_dropdown );
			spRefreshAfterToot = (Spinner) findViewById( R.id.spRefreshAfterToot );
			spRefreshAfterToot.setAdapter( adapter );
			spRefreshAfterToot.setOnItemSelectedListener( this );
		}
		
	}
	
	boolean load_busy;
	
	private void loadUIFromData(){
		load_busy = true;
		
		swDontConfirmBeforeCloseColumn.setChecked( pref.getBoolean( Pref.KEY_DONT_CONFIRM_BEFORE_CLOSE_COLUMN, false ) );
		swPriorLocalURL.setChecked( pref.getBoolean( Pref.KEY_PRIOR_LOCAL_URL, false ) );
		swDisableFastScroller.setChecked( pref.getBoolean( Pref.KEY_DISABLE_FAST_SCROLLER, true ) );
		swSimpleList.setChecked( pref.getBoolean( Pref.KEY_SIMPLE_LIST, false ) );
		swExitAppWhenCloseProtectedColumn.setChecked( pref.getBoolean( Pref.KEY_EXIT_APP_WHEN_CLOSE_PROTECTED_COLUMN, false ) );
		swShowFollowButtonInButtonBar.setChecked( pref.getBoolean( Pref.KEY_SHOW_FOLLOW_BUTTON_IN_BUTTON_BAR, false ) );
		swDontRound.setChecked( pref.getBoolean( Pref.KEY_DONT_ROUND, false ) );
		
		cbNotificationSound.setChecked( pref.getBoolean( Pref.KEY_NOTIFICATION_SOUND, true ) );
		cbNotificationVibration.setChecked( pref.getBoolean( Pref.KEY_NOTIFICATION_VIBRATION, true ) );
		cbNotificationLED.setChecked( pref.getBoolean( Pref.KEY_NOTIFICATION_LED, true ) );
		
		spBackButtonAction.setSelection( pref.getInt( Pref.KEY_BACK_BUTTON_ACTION, 0 ) );
		spUITheme.setSelection( pref.getInt( Pref.KEY_UI_THEME, 0 ) );
		spResizeImage.setSelection( pref.getInt( Pref.KEY_RESIZE_IMAGE, 4 ) );
		spRefreshAfterToot.setSelection( pref.getInt( Pref.KEY_REFRESH_AFTER_TOOT, 0 ) );
		load_busy = false;
	}
	
	private void saveUIToData(){
		if( load_busy ) return;
		pref.edit()
			.putBoolean( Pref.KEY_DONT_CONFIRM_BEFORE_CLOSE_COLUMN, swDontConfirmBeforeCloseColumn.isChecked() )
			.putBoolean( Pref.KEY_PRIOR_LOCAL_URL, swPriorLocalURL.isChecked() )
			.putBoolean( Pref.KEY_DISABLE_FAST_SCROLLER, swDisableFastScroller.isChecked() )
			.putBoolean( Pref.KEY_SIMPLE_LIST, swSimpleList.isChecked() )
			.putBoolean( Pref.KEY_EXIT_APP_WHEN_CLOSE_PROTECTED_COLUMN, swExitAppWhenCloseProtectedColumn.isChecked() )
			.putBoolean( Pref.KEY_SHOW_FOLLOW_BUTTON_IN_BUTTON_BAR, swShowFollowButtonInButtonBar.isChecked() )
			.putBoolean( Pref.KEY_DONT_ROUND, swDontRound.isChecked() )
		

			.putBoolean( Pref.KEY_NOTIFICATION_SOUND, cbNotificationSound.isChecked() )
			.putBoolean( Pref.KEY_NOTIFICATION_VIBRATION, cbNotificationVibration.isChecked() )
			.putBoolean( Pref.KEY_NOTIFICATION_LED, cbNotificationLED.isChecked() )
			
			.putInt( Pref.KEY_BACK_BUTTON_ACTION, spBackButtonAction.getSelectedItemPosition() )
			.putInt( Pref.KEY_UI_THEME, spUITheme.getSelectedItemPosition() )
			.putInt( Pref.KEY_RESIZE_IMAGE, spResizeImage.getSelectedItemPosition() )
			.putInt( Pref.KEY_REFRESH_AFTER_TOOT, spRefreshAfterToot.getSelectedItemPosition() )
			.apply();
	}
	
	@Override public void onCheckedChanged( CompoundButton buttonView, boolean isChecked ){
		saveUIToData();
	}
	
	@Override
	public void onItemSelected( AdapterView< ? > parent, View view, int position, long id ){
		saveUIToData();
	}
	
	@Override public void onNothingSelected( AdapterView< ? > parent ){
	}
}
