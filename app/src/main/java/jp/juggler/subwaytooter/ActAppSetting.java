package jp.juggler.subwaytooter;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.JsonWriter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.jrummyapps.android.colorpicker.ColorPickerDialog;
import com.jrummyapps.android.colorpicker.ColorPickerDialogListener;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.FileWriterWithEncoding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

import jp.juggler.subwaytooter.table.AcctColor;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class ActAppSetting extends AppCompatActivity
	implements CompoundButton.OnCheckedChangeListener
	, AdapterView.OnItemSelectedListener
	, View.OnClickListener
	, ColorPickerDialogListener, TextWatcher
{
	static final LogCategory log = new LogCategory( "ActAppSetting" );
	
	public static void open( ActMain activity, int request_code ){
		activity.startActivityForResult( new Intent( activity, ActAppSetting.class ), request_code );
	}
	
	SharedPreferences pref;
	
	@Override protected void onPause(){
		super.onPause();
		
		// DefaultAccount の Spinnerの値を復元するため、このタイミングでも保存することになった
		saveUIToData();
	}
	
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
	Switch swDontUseStreaming;
	Switch swDontRefreshOnResume;
	Switch swDontScreenOff;
	Switch swDisableTabletMode;
	Switch swDontCropMediaThumb;
	Switch swPriorChrome;
	Switch swPostButtonBarTop;
	Switch swDontDuplicationCheck;
	Switch swQuickTootBar;
	Switch swEnableGifAnimation;
	Switch swMentionFullAcct;
	Switch swRelativeTimestamp;
	Switch swDontUseActionButtonWithQuickTootBar;
	Switch swShortAcctLocalUser;
	Switch swDisableEmojiAnimation;
	
	Spinner spBackButtonAction;
	Spinner spUITheme;
	Spinner spResizeImage;
	Spinner spRefreshAfterToot;
	Spinner spDefaultAccount;
	
	CheckBox cbNotificationSound;
	CheckBox cbNotificationVibration;
	CheckBox cbNotificationLED;
	
	static final int BACK_ASK_ALWAYS = 0;
	static final int BACK_CLOSE_COLUMN = 1;
	static final int BACK_OPEN_COLUMN_LIST = 2;
	static final int BACK_EXIT_APP = 3;
	
	int footer_button_bg_color;
	int footer_button_fg_color;
	int footer_tab_bg_color;
	int footer_tab_divider_color;
	int footer_tab_indicator_color;
	
	ImageView ivFooterToot;
	ImageView ivFooterMenu;
	View llFooterBG;
	View vFooterDivider1;
	View vFooterDivider2;
	View vIndicator;
	
	EditText etColumnWidth;
	EditText etMediaThumbHeight;
	EditText etClientName;
	EditText etQuoteNameFormat;
	EditText etAutoCWLines;
	
	TextView tvTimelineFontUrl;
	String timeline_font;
	TextView tvTimelineFontBoldUrl;
	String timeline_font_bold;
	
	EditText etTimelineFontSize;
	EditText etAcctFontSize;
	TextView tvTimelineFontSize;
	TextView tvAcctFontSize;
	EditText etAvatarIconSize;
	
	static final float default_timeline_font_size = 14f;
	static final float default_acct_font_size = 12f;
	
	private void initUI(){
		setContentView( R.layout.act_app_setting );
		
		Styler.fixHorizontalPadding( findViewById( R.id.svContent ) );
		
		swDontConfirmBeforeCloseColumn = findViewById( R.id.swDontConfirmBeforeCloseColumn );
		swDontConfirmBeforeCloseColumn.setOnCheckedChangeListener( this );
		
		swPriorLocalURL = findViewById( R.id.swPriorLocalURL );
		swPriorLocalURL.setOnCheckedChangeListener( this );
		
		swDisableFastScroller = findViewById( R.id.swDisableFastScroller );
		swDisableFastScroller.setOnCheckedChangeListener( this );
		
		swSimpleList = findViewById( R.id.swSimpleList );
		swSimpleList.setOnCheckedChangeListener( this );
		
		swExitAppWhenCloseProtectedColumn = findViewById( R.id.swExitAppWhenCloseProtectedColumn );
		swExitAppWhenCloseProtectedColumn.setOnCheckedChangeListener( this );
		
		swShowFollowButtonInButtonBar = findViewById( R.id.swShowFollowButtonInButtonBar );
		swShowFollowButtonInButtonBar.setOnCheckedChangeListener( this );
		
		swDontRound = findViewById( R.id.swDontRound );
		swDontRound.setOnCheckedChangeListener( this );
		
		swDontUseStreaming = findViewById( R.id.swDontUseStreaming );
		swDontUseStreaming.setOnCheckedChangeListener( this );
		
		swDontRefreshOnResume = findViewById( R.id.swDontRefreshOnResume );
		swDontRefreshOnResume.setOnCheckedChangeListener( this );
		
		swDontScreenOff = findViewById( R.id.swDontScreenOff );
		swDontScreenOff.setOnCheckedChangeListener( this );
		
		swDisableTabletMode = findViewById( R.id.swDisableTabletMode );
		swDisableTabletMode.setOnCheckedChangeListener( this );
		
		swDontCropMediaThumb = findViewById( R.id.swDontCropMediaThumb );
		swDontCropMediaThumb.setOnCheckedChangeListener( this );
		
		swPriorChrome = findViewById( R.id.swPriorChrome );
		swPriorChrome.setOnCheckedChangeListener( this );
		
		swPostButtonBarTop = findViewById( R.id.swPostButtonBarTop );
		swPostButtonBarTop.setOnCheckedChangeListener( this );
		
		swDontDuplicationCheck = findViewById( R.id.swDontDuplicationCheck );
		swDontDuplicationCheck.setOnCheckedChangeListener( this );
		
		swQuickTootBar = findViewById( R.id.swQuickTootBar );
		swQuickTootBar.setOnCheckedChangeListener( this );
		
		swEnableGifAnimation = findViewById( R.id.swEnableGifAnimation );
		swEnableGifAnimation.setOnCheckedChangeListener( this );
		
		swMentionFullAcct = findViewById( R.id.swMentionFullAcct );
		swMentionFullAcct.setOnCheckedChangeListener( this );
		
		swRelativeTimestamp = findViewById( R.id.swRelativeTimestamp );
		swRelativeTimestamp.setOnCheckedChangeListener( this );
		
		swDontUseActionButtonWithQuickTootBar = findViewById( R.id.swDontUseActionButtonWithQuickTootBar );
		swDontUseActionButtonWithQuickTootBar.setOnCheckedChangeListener( this );
		
		swShortAcctLocalUser = findViewById( R.id.swShortAcctLocalUser );
		swShortAcctLocalUser.setOnCheckedChangeListener( this );
		
		swDisableEmojiAnimation = findViewById( R.id.swDisableEmojiAnimation );
		swDisableEmojiAnimation.setOnCheckedChangeListener( this );
		
		cbNotificationSound = findViewById( R.id.cbNotificationSound );
		cbNotificationVibration = findViewById( R.id.cbNotificationVibration );
		cbNotificationLED = findViewById( R.id.cbNotificationLED );
		cbNotificationSound.setOnCheckedChangeListener( this );
		cbNotificationVibration.setOnCheckedChangeListener( this );
		cbNotificationLED.setOnCheckedChangeListener( this );
		
		boolean bBefore8 = Build.VERSION.SDK_INT < 26;
		cbNotificationSound.setEnabled( bBefore8 );
		cbNotificationVibration.setEnabled( bBefore8 );
		cbNotificationLED.setEnabled( bBefore8 );
		
		{
			String[] caption_list = new String[]{
				getString( R.string.ask_always ),
				getString( R.string.close_column ),
				getString( R.string.open_column_list ),
				getString( R.string.app_exit ),
			};
			ArrayAdapter< String > adapter = new ArrayAdapter<>( this, android.R.layout.simple_spinner_item, caption_list );
			adapter.setDropDownViewResource( R.layout.lv_spinner_dropdown );
			spBackButtonAction = findViewById( R.id.spBackButtonAction );
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
			spUITheme = findViewById( R.id.spUITheme );
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
			spResizeImage = findViewById( R.id.spResizeImage );
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
			spRefreshAfterToot = findViewById( R.id.spRefreshAfterToot );
			spRefreshAfterToot.setAdapter( adapter );
			spRefreshAfterToot.setOnItemSelectedListener( this );
		}
		
		{
			
			AccountAdapter adapter = new AccountAdapter();
			spDefaultAccount = findViewById( R.id.spDefaultAccount );
			spDefaultAccount.setAdapter( adapter );
			spDefaultAccount.setOnItemSelectedListener( this );
		}
		
		findViewById( R.id.btnFooterBackgroundEdit ).setOnClickListener( this );
		findViewById( R.id.btnFooterBackgroundReset ).setOnClickListener( this );
		findViewById( R.id.btnFooterForegroundColorEdit ).setOnClickListener( this );
		findViewById( R.id.btnFooterForegroundColorReset ).setOnClickListener( this );
		findViewById( R.id.btnTabBackgroundColorEdit ).setOnClickListener( this );
		findViewById( R.id.btnTabBackgroundColorReset ).setOnClickListener( this );
		findViewById( R.id.btnTabDividerColorEdit ).setOnClickListener( this );
		findViewById( R.id.btnTabDividerColorReset ).setOnClickListener( this );
		findViewById( R.id.btnTabIndicatorColorEdit ).setOnClickListener( this );
		findViewById( R.id.btnTabIndicatorColorReset ).setOnClickListener( this );
		
		findViewById( R.id.btnTimelineFontEdit ).setOnClickListener( this );
		findViewById( R.id.btnTimelineFontReset ).setOnClickListener( this );
		findViewById( R.id.btnTimelineFontBoldEdit ).setOnClickListener( this );
		findViewById( R.id.btnTimelineFontBoldReset ).setOnClickListener( this );
		findViewById( R.id.btnSettingExport ).setOnClickListener( this );
		findViewById( R.id.btnSettingImport ).setOnClickListener( this );
		findViewById( R.id.btnCustomStreamListenerEdit ).setOnClickListener( this );
		findViewById( R.id.btnCustomStreamListenerReset ).setOnClickListener( this );
		
		ivFooterToot = findViewById( R.id.ivFooterToot );
		ivFooterMenu = findViewById( R.id.ivFooterMenu );
		llFooterBG = findViewById( R.id.llFooterBG );
		vFooterDivider1 = findViewById( R.id.vFooterDivider1 );
		vFooterDivider2 = findViewById( R.id.vFooterDivider2 );
		vIndicator = findViewById( R.id.vIndicator );
		
		etColumnWidth = findViewById( R.id.etColumnWidth );
		etColumnWidth.addTextChangedListener( this );
		
		etMediaThumbHeight = findViewById( R.id.etMediaThumbHeight );
		etMediaThumbHeight.addTextChangedListener( this );
		
		etClientName = findViewById( R.id.etClientName );
		etClientName.addTextChangedListener( this );
		
		etQuoteNameFormat = findViewById( R.id.etQuoteNameFormat );
		etQuoteNameFormat.addTextChangedListener( this );
		
		etAutoCWLines = findViewById( R.id.etAutoCWLines );
		etAutoCWLines.addTextChangedListener( this );
		
		tvTimelineFontSize = findViewById( R.id.tvTimelineFontSize );
		tvAcctFontSize = findViewById( R.id.tvAcctFontSize );
		
		etTimelineFontSize = findViewById( R.id.etTimelineFontSize );
		etTimelineFontSize.addTextChangedListener( new SizeCheckTextWatcher( tvTimelineFontSize, etTimelineFontSize, default_timeline_font_size ) );
		
		etAcctFontSize = findViewById( R.id.etAcctFontSize );
		etAcctFontSize.addTextChangedListener( new SizeCheckTextWatcher( tvAcctFontSize, etAcctFontSize, default_acct_font_size ) );
		
		etAvatarIconSize = findViewById( R.id.etAvatarIconSize );
		
		tvTimelineFontUrl = findViewById( R.id.tvTimelineFontUrl );
		tvTimelineFontBoldUrl = findViewById( R.id.tvTimelineFontBoldUrl );
		
	}
	
	boolean load_busy;
	
	private void loadUIFromData(){
		load_busy = true;
		
		swDontConfirmBeforeCloseColumn.setChecked( pref.getBoolean( Pref.KEY_DONT_CONFIRM_BEFORE_CLOSE_COLUMN, false ) );
		swPriorLocalURL.setChecked( pref.getBoolean( Pref.KEY_PRIOR_LOCAL_URL, false ) );
		swSimpleList.setChecked( pref.getBoolean( Pref.KEY_SIMPLE_LIST, false ) );
		swExitAppWhenCloseProtectedColumn.setChecked( pref.getBoolean( Pref.KEY_EXIT_APP_WHEN_CLOSE_PROTECTED_COLUMN, false ) );
		swShowFollowButtonInButtonBar.setChecked( pref.getBoolean( Pref.KEY_SHOW_FOLLOW_BUTTON_IN_BUTTON_BAR, false ) );
		swDontRound.setChecked( pref.getBoolean( Pref.KEY_DONT_ROUND, false ) );
		swDontUseStreaming.setChecked( pref.getBoolean( Pref.KEY_DONT_USE_STREAMING, false ) );
		swDontRefreshOnResume.setChecked( pref.getBoolean( Pref.KEY_DONT_REFRESH_ON_RESUME, false ) );
		swDontScreenOff.setChecked( pref.getBoolean( Pref.KEY_DONT_SCREEN_OFF, false ) );
		swDisableTabletMode.setChecked( pref.getBoolean( Pref.KEY_DISABLE_TABLET_MODE, false ) );
		swDontCropMediaThumb.setChecked( pref.getBoolean( Pref.KEY_DONT_CROP_MEDIA_THUMBNAIL, false ) );
		swPostButtonBarTop.setChecked( pref.getBoolean( Pref.KEY_POST_BUTTON_BAR_AT_TOP, false ) );
		swDontDuplicationCheck.setChecked( pref.getBoolean( Pref.KEY_DONT_DUPLICATION_CHECK, false ) );
		swQuickTootBar.setChecked( pref.getBoolean( Pref.KEY_QUICK_TOOT_BAR, false ) );
		swEnableGifAnimation.setChecked( pref.getBoolean( Pref.KEY_ENABLE_GIF_ANIMATION, false ) );
		swMentionFullAcct.setChecked( pref.getBoolean( Pref.KEY_MENTION_FULL_ACCT, false ) );
		swRelativeTimestamp.setChecked( pref.getBoolean( Pref.KEY_RELATIVE_TIMESTAMP, false ) );
		swDontUseActionButtonWithQuickTootBar.setChecked( pref.getBoolean( Pref.KEY_DONT_USE_ACTION_BUTTON, false ) );
		swShortAcctLocalUser.setChecked( pref.getBoolean( Pref.KEY_SHORT_ACCT_LOCAL_USER, false ) );
		swDisableEmojiAnimation.setChecked( pref.getBoolean( Pref.KEY_DISABLE_EMOJI_ANIMATION, false ) );
		
		// Switch with default true
		swDisableFastScroller.setChecked( pref.getBoolean( Pref.KEY_DISABLE_FAST_SCROLLER, true ) );
		swPriorChrome.setChecked( pref.getBoolean( Pref.KEY_PRIOR_CHROME, true ) );
		
		cbNotificationSound.setChecked( pref.getBoolean( Pref.KEY_NOTIFICATION_SOUND, true ) );
		cbNotificationVibration.setChecked( pref.getBoolean( Pref.KEY_NOTIFICATION_VIBRATION, true ) );
		cbNotificationLED.setChecked( pref.getBoolean( Pref.KEY_NOTIFICATION_LED, true ) );
		
		spBackButtonAction.setSelection( pref.getInt( Pref.KEY_BACK_BUTTON_ACTION, 0 ) );
		spUITheme.setSelection( pref.getInt( Pref.KEY_UI_THEME, 0 ) );
		spResizeImage.setSelection( pref.getInt( Pref.KEY_RESIZE_IMAGE, 4 ) );
		spRefreshAfterToot.setSelection( pref.getInt( Pref.KEY_REFRESH_AFTER_TOOT, 0 ) );
		
		spDefaultAccount.setSelection(
			( (AccountAdapter) spDefaultAccount.getAdapter() ).getIndexFromId( pref.getLong( Pref.KEY_TABLET_TOOT_DEFAULT_ACCOUNT, - 1L ) )
		);
		
		footer_button_bg_color = pref.getInt( Pref.KEY_FOOTER_BUTTON_BG_COLOR, 0 );
		footer_button_fg_color = pref.getInt( Pref.KEY_FOOTER_BUTTON_FG_COLOR, 0 );
		footer_tab_bg_color = pref.getInt( Pref.KEY_FOOTER_TAB_BG_COLOR, 0 );
		footer_tab_divider_color = pref.getInt( Pref.KEY_FOOTER_TAB_DIVIDER_COLOR, 0 );
		footer_tab_indicator_color = pref.getInt( Pref.KEY_FOOTER_TAB_INDICATOR_COLOR, 0 );
		
		etColumnWidth.setText( pref.getString( Pref.KEY_COLUMN_WIDTH, "" ) );
		etMediaThumbHeight.setText( pref.getString( Pref.KEY_MEDIA_THUMB_HEIGHT, "" ) );
		etClientName.setText( pref.getString( Pref.KEY_CLIENT_NAME, "" ) );
		etQuoteNameFormat.setText( pref.getString( Pref.KEY_QUOTE_NAME_FORMAT, "" ) );
		etAutoCWLines.setText( pref.getString( Pref.KEY_AUTO_CW_LINES, "0" ) );
		etAvatarIconSize.setText( pref.getString( Pref.KEY_AVATAR_ICON_SIZE, "48" ) );
		
		etTimelineFontSize.setText( formatFontSize( pref.getFloat( Pref.KEY_TIMELINE_FONT_SIZE, Float.NaN ) ) );
		etAcctFontSize.setText( formatFontSize( pref.getFloat( Pref.KEY_ACCT_FONT_SIZE, Float.NaN ) ) );
		
		timeline_font = pref.getString( Pref.KEY_TIMELINE_FONT, "" );
		timeline_font_bold = pref.getString( Pref.KEY_TIMELINE_FONT_BOLD, "" );
		
		load_busy = false;
		
		showFooterColor();
		showTimelineFont( tvTimelineFontUrl, timeline_font );
		showTimelineFont( tvTimelineFontBoldUrl, timeline_font_bold );
		
		showFontSize( tvTimelineFontSize, etTimelineFontSize, default_timeline_font_size );
		showFontSize( tvAcctFontSize, etAcctFontSize, default_acct_font_size );
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
			.putBoolean( Pref.KEY_DONT_USE_STREAMING, swDontUseStreaming.isChecked() )
			.putBoolean( Pref.KEY_DONT_REFRESH_ON_RESUME, swDontRefreshOnResume.isChecked() )
			.putBoolean( Pref.KEY_DONT_SCREEN_OFF, swDontScreenOff.isChecked() )
			.putBoolean( Pref.KEY_DISABLE_TABLET_MODE, swDisableTabletMode.isChecked() )
			.putBoolean( Pref.KEY_DONT_CROP_MEDIA_THUMBNAIL, swDontCropMediaThumb.isChecked() )
			.putBoolean( Pref.KEY_PRIOR_CHROME, swPriorChrome.isChecked() )
			.putBoolean( Pref.KEY_POST_BUTTON_BAR_AT_TOP, swPostButtonBarTop.isChecked() )
			.putBoolean( Pref.KEY_DONT_DUPLICATION_CHECK, swDontDuplicationCheck.isChecked() )
			.putBoolean( Pref.KEY_QUICK_TOOT_BAR, swQuickTootBar.isChecked() )
			.putBoolean( Pref.KEY_ENABLE_GIF_ANIMATION, swEnableGifAnimation.isChecked() )
			.putBoolean( Pref.KEY_MENTION_FULL_ACCT, swMentionFullAcct.isChecked() )
			.putBoolean( Pref.KEY_RELATIVE_TIMESTAMP, swRelativeTimestamp.isChecked() )
			.putBoolean( Pref.KEY_DONT_USE_ACTION_BUTTON, swDontUseActionButtonWithQuickTootBar.isChecked() )
			.putBoolean( Pref.KEY_SHORT_ACCT_LOCAL_USER, swShortAcctLocalUser.isChecked() )
			.putBoolean( Pref.KEY_DISABLE_EMOJI_ANIMATION, swDisableEmojiAnimation.isChecked() )
			
			.putBoolean( Pref.KEY_NOTIFICATION_SOUND, cbNotificationSound.isChecked() )
			.putBoolean( Pref.KEY_NOTIFICATION_VIBRATION, cbNotificationVibration.isChecked() )
			.putBoolean( Pref.KEY_NOTIFICATION_LED, cbNotificationLED.isChecked() )
			
			.putInt( Pref.KEY_BACK_BUTTON_ACTION, spBackButtonAction.getSelectedItemPosition() )
			.putInt( Pref.KEY_UI_THEME, spUITheme.getSelectedItemPosition() )
			.putInt( Pref.KEY_RESIZE_IMAGE, spResizeImage.getSelectedItemPosition() )
			.putInt( Pref.KEY_REFRESH_AFTER_TOOT, spRefreshAfterToot.getSelectedItemPosition() )
			
			.putInt( Pref.KEY_FOOTER_BUTTON_BG_COLOR, footer_button_bg_color )
			.putInt( Pref.KEY_FOOTER_BUTTON_FG_COLOR, footer_button_fg_color )
			.putInt( Pref.KEY_FOOTER_TAB_BG_COLOR, footer_tab_bg_color )
			.putInt( Pref.KEY_FOOTER_TAB_DIVIDER_COLOR, footer_tab_divider_color )
			.putInt( Pref.KEY_FOOTER_TAB_INDICATOR_COLOR, footer_tab_indicator_color )
			
			.putLong( Pref.KEY_TABLET_TOOT_DEFAULT_ACCOUNT, ( (AccountAdapter) spDefaultAccount.getAdapter() )
				.getIdFromIndex( spDefaultAccount.getSelectedItemPosition() ) )
			
			.putString( Pref.KEY_TIMELINE_FONT, timeline_font )
			.putString( Pref.KEY_TIMELINE_FONT_BOLD, timeline_font_bold )
			.putString( Pref.KEY_COLUMN_WIDTH, etColumnWidth.getText().toString().trim() )
			.putString( Pref.KEY_MEDIA_THUMB_HEIGHT, etMediaThumbHeight.getText().toString().trim() )
			.putString( Pref.KEY_CLIENT_NAME, etClientName.getText().toString().trim() )
			.putString( Pref.KEY_QUOTE_NAME_FORMAT, etQuoteNameFormat.getText().toString() ) // not trimmed
			.putString( Pref.KEY_AUTO_CW_LINES, etAutoCWLines.getText().toString() ) // not trimmed
			.putString( Pref.KEY_AVATAR_ICON_SIZE, etAvatarIconSize.getText().toString().trim() )
			
			.putFloat( Pref.KEY_TIMELINE_FONT_SIZE, parseFontSize( etTimelineFontSize.getText().toString().trim() ) )
			.putFloat( Pref.KEY_ACCT_FONT_SIZE, parseFontSize( etAcctFontSize.getText().toString().trim() ) )
			
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
	
	static final int COLOR_DIALOG_ID_FOOTER_BUTTON_BG = 1;
	static final int COLOR_DIALOG_ID_FOOTER_BUTTON_FG = 2;
	static final int COLOR_DIALOG_ID_FOOTER_TAB_BG = 3;
	static final int COLOR_DIALOG_ID_FOOTER_TAB_DIVIDER = 4;
	static final int COLOR_DIALOG_ID_FOOTER_TAB_INDICATOR = 5;
	
	@Override public void onClick( View v ){
		switch( v.getId() ){
		
		case R.id.btnFooterBackgroundEdit:
			openColorPicker( COLOR_DIALOG_ID_FOOTER_BUTTON_BG, footer_button_bg_color, false );
			break;
		
		case R.id.btnFooterBackgroundReset:
			footer_button_bg_color = 0;
			saveUIToData();
			showFooterColor();
			break;
		
		case R.id.btnFooterForegroundColorEdit:
			openColorPicker( COLOR_DIALOG_ID_FOOTER_BUTTON_FG, footer_button_fg_color, false );
			break;
		
		case R.id.btnFooterForegroundColorReset:
			footer_button_fg_color = 0;
			saveUIToData();
			showFooterColor();
			break;
		
		case R.id.btnTabBackgroundColorEdit:
			openColorPicker( COLOR_DIALOG_ID_FOOTER_TAB_BG, footer_tab_bg_color, false );
			break;
		
		case R.id.btnTabBackgroundColorReset:
			footer_tab_bg_color = 0;
			saveUIToData();
			showFooterColor();
			break;
		
		case R.id.btnTabDividerColorEdit:
			openColorPicker( COLOR_DIALOG_ID_FOOTER_TAB_DIVIDER, footer_tab_divider_color, false );
			break;
		
		case R.id.btnTabDividerColorReset:
			footer_tab_divider_color = 0;
			saveUIToData();
			showFooterColor();
			break;
		
		case R.id.btnTabIndicatorColorEdit:
			openColorPicker( COLOR_DIALOG_ID_FOOTER_TAB_INDICATOR, footer_tab_indicator_color, true );
			break;
		
		case R.id.btnTabIndicatorColorReset:
			footer_tab_indicator_color = 0;
			saveUIToData();
			showFooterColor();
			break;
		
		case R.id.btnTimelineFontReset:
			timeline_font = "";
			saveUIToData();
			showTimelineFont( tvTimelineFontUrl, timeline_font );
			break;
		
		case R.id.btnTimelineFontEdit:
			try{
				Intent intent = new Intent( Intent.ACTION_OPEN_DOCUMENT );
				intent.addCategory( Intent.CATEGORY_OPENABLE );
				intent.setType( "*/*" );
				startActivityForResult( intent, REQUEST_CODE_TIMELINE_FONT );
			}catch( Throwable ex ){
				Utils.showToast( this, ex, "could not open picker for font" );
			}
			break;
		
		case R.id.btnTimelineFontBoldReset:
			timeline_font_bold = "";
			saveUIToData();
			showTimelineFont( tvTimelineFontBoldUrl, timeline_font_bold );
			break;
		
		case R.id.btnTimelineFontBoldEdit:
			try{
				Intent intent = new Intent( Intent.ACTION_OPEN_DOCUMENT );
				intent.addCategory( Intent.CATEGORY_OPENABLE );
				intent.setType( "*/*" );
				startActivityForResult( intent, REQUEST_CODE_TIMELINE_FONT_BOLD );
			}catch( Throwable ex ){
				Utils.showToast( this, ex, "could not open picker for font" );
			}
			break;
		
		case R.id.btnSettingExport:
			exportAppData();
			break;
		
		case R.id.btnSettingImport:
			importAppData();
			break;
		
		case R.id.btnCustomStreamListenerEdit:
			ActCustomStreamListener.open( this );
			break;
		
		case R.id.btnCustomStreamListenerReset:
			pref
				.edit()
				.remove( Pref.KEY_STREAM_LISTENER_CONFIG_URL )
				.remove( Pref.KEY_STREAM_LISTENER_SECRET )
				.remove( Pref.KEY_STREAM_LISTENER_CONFIG_DATA )
				.apply();
			SavedAccount.clearRegistrationCache();
			PollingWorker.queueUpdateListener( this );
			Utils.showToast( this, false, getString( R.string.custom_stream_listener_was_reset ) );
			break;
			
		}
	}
	
	static final int REQUEST_CODE_TIMELINE_FONT = 1;
	static final int REQUEST_CODE_TIMELINE_FONT_BOLD = 2;
	static final int REQUEST_CODE_APP_DATA_EXPORT = 3;
	static final int REQUEST_CODE_APP_DATA_IMPORT = 4;
	
	@Override protected void onActivityResult( int requestCode, int resultCode, Intent data ){
		if( resultCode == RESULT_OK && data != null && requestCode == REQUEST_CODE_TIMELINE_FONT ){
			File file = saveTimelineFont( data.getData(), "TimelineFont" );
			if( file != null ){
				timeline_font = file.getAbsolutePath();
				saveUIToData();
				showTimelineFont( tvTimelineFontUrl, timeline_font );
			}
		}else if( resultCode == RESULT_OK && data != null && requestCode == REQUEST_CODE_TIMELINE_FONT_BOLD ){
			File file = saveTimelineFont( data.getData(), "TimelineFontBold" );
			if( file != null ){
				timeline_font_bold = file.getAbsolutePath();
				saveUIToData();
				showTimelineFont( tvTimelineFontBoldUrl, timeline_font_bold );
			}
		}else if( resultCode == RESULT_OK && requestCode == REQUEST_CODE_APP_DATA_IMPORT ){
			if( data != null ){
				Uri uri = data.getData();
				if( uri != null ){
					getContentResolver().takePersistableUriPermission( uri, Intent.FLAG_GRANT_READ_URI_PERMISSION );
					importAppData( false, uri );
				}
			}
		}
		super.onActivityResult( requestCode, resultCode, data );
	}
	
	void openColorPicker( int id, int color, boolean bShowAlphaSlider ){
		ColorPickerDialog.Builder builder = ColorPickerDialog.newBuilder()
			.setDialogType( ColorPickerDialog.TYPE_CUSTOM )
			.setAllowPresets( true )
			.setShowAlphaSlider( bShowAlphaSlider )
			.setDialogId( id );
		if( color != 0 ) builder.setColor( color );
		builder.show( this );
	}
	
	@Override public void onColorSelected( int dialogId, @ColorInt int color ){
		switch( dialogId ){
		
		case COLOR_DIALOG_ID_FOOTER_BUTTON_BG:
			footer_button_bg_color = 0xff000000 | color;
			saveUIToData();
			showFooterColor();
			break;
		
		case COLOR_DIALOG_ID_FOOTER_BUTTON_FG:
			footer_button_fg_color = 0xff000000 | color;
			saveUIToData();
			showFooterColor();
			break;
		
		case COLOR_DIALOG_ID_FOOTER_TAB_BG:
			footer_tab_bg_color = 0xff000000 | color;
			saveUIToData();
			showFooterColor();
			break;
		
		case COLOR_DIALOG_ID_FOOTER_TAB_DIVIDER:
			footer_tab_divider_color = 0xff000000 | color;
			saveUIToData();
			showFooterColor();
			break;
		case COLOR_DIALOG_ID_FOOTER_TAB_INDICATOR:
			if( color == 0 ) color = 0x01000000;
			footer_tab_indicator_color = color;
			saveUIToData();
			showFooterColor();
			break;
		}
	}
	
	@Override public void onDialogDismissed( int dialogId ){
	}
	
	private void showFooterColor(){
		
		int c = footer_button_bg_color;
		if( c == 0 ){
			ivFooterToot.setBackgroundResource( R.drawable.btn_bg_ddd );
			ivFooterMenu.setBackgroundResource( R.drawable.btn_bg_ddd );
		}else{
			int fg = ( footer_button_fg_color != 0
				? footer_button_fg_color
				: Styler.getAttributeColor( this, R.attr.colorRippleEffect ) );
			ViewCompat.setBackground( ivFooterToot, Styler.getAdaptiveRippleDrawable( c, fg ) );
			ViewCompat.setBackground( ivFooterMenu, Styler.getAdaptiveRippleDrawable( c, fg ) );
		}
		
		c = footer_button_fg_color;
		if( c == 0 ){
			Styler.setIconDefaultColor( this, ivFooterToot, R.attr.ic_edit );
			Styler.setIconDefaultColor( this, ivFooterMenu, R.attr.ic_hamburger );
		}else{
			Styler.setIconCustomColor( this, ivFooterToot, c, R.attr.ic_edit );
			Styler.setIconCustomColor( this, ivFooterMenu, c, R.attr.ic_hamburger );
		}
		
		c = footer_tab_bg_color;
		if( c == 0 ){
			llFooterBG.setBackgroundColor( Styler.getAttributeColor( this, R.attr.colorColumnStripBackground ) );
		}else{
			llFooterBG.setBackgroundColor( c );
		}
		
		c = footer_tab_divider_color;
		if( c == 0 ){
			vFooterDivider1.setBackgroundColor( Styler.getAttributeColor( this, R.attr.colorImageButton ) );
			vFooterDivider2.setBackgroundColor( Styler.getAttributeColor( this, R.attr.colorImageButton ) );
		}else{
			vFooterDivider1.setBackgroundColor( c );
			vFooterDivider2.setBackgroundColor( c );
		}
		
		c = footer_tab_indicator_color;
		if( c == 0 ){
			vIndicator.setBackgroundColor( Styler.getAttributeColor( this, R.attr.colorAccent ) );
		}else{
			vIndicator.setBackgroundColor( c );
		}
	}
	
	@Override public void beforeTextChanged( CharSequence s, int start, int count, int after ){
		
	}
	
	@Override public void onTextChanged( CharSequence s, int start, int before, int count ){
		
	}
	
	@Override public void afterTextChanged( Editable s ){
		saveUIToData();
	}
	
	private class SizeCheckTextWatcher implements TextWatcher {
		TextView sample;
		EditText et;
		float default_size_sp;
		
		SizeCheckTextWatcher( TextView sample, EditText et, float default_size_sp ){
			this.sample = sample;
			this.et = et;
			this.default_size_sp = default_size_sp;
		}
		
		@Override public void beforeTextChanged( CharSequence s, int start, int count, int after ){
			
		}
		
		@Override public void onTextChanged( CharSequence s, int start, int before, int count ){
			
		}
		
		@Override public void afterTextChanged( Editable s ){
			saveUIToData();
			showFontSize( sample, et, default_size_sp );
		}
	}
	
	private String formatFontSize( float fv ){
		if( Float.isNaN( fv ) ){
			return "";
		}else{
			return String.format( Locale.getDefault(), "%.1f", fv );
		}
	}
	
	private float parseFontSize( String src ){
		try{
			if( ! TextUtils.isEmpty( src ) ){
				NumberFormat format = NumberFormat.getInstance( Locale.getDefault() );
				Number number = format.parse( src );
				float f = number.floatValue();
				if( ! Float.isNaN( f ) ){
					if( f < 0f ) f = 0f;
					if( f > 999f ) f = 999f;
					return f;
				}
			}
		}catch( Throwable ex ){
			log.trace( ex );
		}
		return Float.NaN;
	}
	
	private void showFontSize( TextView sample, EditText et, float default_sp ){
		float fv = parseFontSize( et.getText().toString().trim() );
		if( Float.isNaN( fv ) ){
			sample.setTextSize( default_sp );
		}else{
			if( fv < 1f ) fv = 1f;
			sample.setTextSize( fv );
		}
	}
	
	private void showTimelineFont(
		TextView tvFontUrl
		, String font_url
	){
		try{
			if( ! TextUtils.isEmpty( font_url ) ){
				
				tvFontUrl.setTypeface( Typeface.DEFAULT );
				Typeface face = Typeface.createFromFile( font_url );
				tvFontUrl.setTypeface( face );
				tvFontUrl.setText( font_url );
				return;
			}
		}catch( Throwable ex ){
			log.trace( ex );
		}
		// fallback
		tvFontUrl.setText( getString( R.string.not_selected ) );
		tvFontUrl.setTypeface( Typeface.DEFAULT );
	}
	
	private @Nullable File saveTimelineFont( @Nullable Uri uri, @NonNull String file_name ){
		try{
			if( uri == null ){
				Utils.showToast( this, false, "missing uri." );
				return null;
			}
			
			getContentResolver().takePersistableUriPermission( uri, Intent.FLAG_GRANT_READ_URI_PERMISSION );
			
			File dir = getFilesDir();
			//noinspection ResultOfMethodCallIgnored
			dir.mkdir();
			
			File tmp_file = new File( dir, file_name + ".tmp" );
			
			InputStream is = getContentResolver().openInputStream( uri );
			if( is == null ){
				Utils.showToast( this, false, "openInputStream returns null. uri=%s", uri );
				return null;
			}
			
			try{
				FileOutputStream os = new FileOutputStream( tmp_file );
				try{
					IOUtils.copy( is, os );
				}finally{
					IOUtils.closeQuietly( os );
				}
			}finally{
				IOUtils.closeQuietly( is );
			}
			
			Typeface face = Typeface.createFromFile( tmp_file );
			if( face == null ){
				Utils.showToast( this, false, "Typeface.createFromFile() failed." );
				return null;
			}
			
			File file = new File( dir, file_name );
			if( ! tmp_file.renameTo( file ) ){
				Utils.showToast( this, false, "File operation failed." );
				return null;
			}
			
			return file;
		}catch( Throwable ex ){
			log.trace( ex );
			Utils.showToast( this, ex, "saveTimelineFont failed." );
			return null;
		}
	}
	
	private void exportAppData(){
		//noinspection deprecation
		final ProgressDialog progress = new ProgressDialog( this );
		
		final AsyncTask< Void, String, File > task = new AsyncTask< Void, String, File >() {
			
			@Override protected File doInBackground( Void... params ){
				try{
					File cache_dir = getCacheDir();
					//noinspection ResultOfMethodCallIgnored
					cache_dir.mkdir();
					
					File file = new File( cache_dir, "SubwayTooter." + android.os.Process.myPid() + "." + android.os.Process.myTid() + ".json" );
					FileWriterWithEncoding w = new FileWriterWithEncoding( file, "UTF-8" );
					try{
						JsonWriter jw = new JsonWriter( w );
						AppDataExporter.encodeAppData( ActAppSetting.this, jw );
						jw.flush();
					}finally{
						IOUtils.closeQuietly( w );
					}
					return file;
				}catch( Throwable ex ){
					log.trace( ex );
					Utils.showToast( ActAppSetting.this, ex, "exportAppData failed." );
				}
				return null;
			}
			
			@Override protected void onCancelled( File result ){
				super.onCancelled( result );
			}
			
			@Override protected void onPostExecute( File result ){
				progress.dismiss();
				
				if( isCancelled() || result == null ){
					// cancelled.
					return;
				}
				
				try{
					Uri uri = FileProvider.getUriForFile( ActAppSetting.this, App1.FILE_PROVIDER_AUTHORITY, result );
					Intent intent = new Intent( Intent.ACTION_SEND );
					intent.setType( getContentResolver().getType( uri ) );
					intent.putExtra( Intent.EXTRA_SUBJECT, "SubwayTooter app data" );
					intent.putExtra( Intent.EXTRA_STREAM, uri );
					
					intent.addFlags( Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION );
					startActivityForResult( intent, REQUEST_CODE_APP_DATA_EXPORT );
				}catch( Throwable ex ){
					log.trace( ex );
					Utils.showToast( ActAppSetting.this, ex, "exportAppData failed." );
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
	
	private void importAppData(){
		try{
			Intent intent = new Intent( Intent.ACTION_OPEN_DOCUMENT );
			intent.addCategory( Intent.CATEGORY_OPENABLE );
			intent.setType( "*/*" );
			startActivityForResult( intent, REQUEST_CODE_APP_DATA_IMPORT );
		}catch( Throwable ex ){
			Utils.showToast( this, ex, "importAppData(1) failed." );
		}
	}
	
	private void importAppData( boolean bConfirm, final Uri uri ){
		
		String type = getContentResolver().getType( uri );
		log.d( "importAppData type=%s", type );
		
		if( ! bConfirm ){
			new AlertDialog.Builder( this )
				.setMessage( getString( R.string.app_data_import_confirm ) )
				.setNegativeButton( R.string.cancel, null )
				.setPositiveButton( R.string.ok, new DialogInterface.OnClickListener() {
					@Override public void onClick( DialogInterface dialog, int which ){
						importAppData( true, uri );
					}
				} )
				.show();
			return;
		}
		
		Intent data = new Intent();
		data.setData( uri );
		setResult( ActMain.RESULT_APP_DATA_IMPORT, data );
		finish();
		
	}
	
	private class AccountAdapter extends BaseAdapter {
		
		final ArrayList< SavedAccount > list = new ArrayList<>();
		
		AccountAdapter(){
			for( SavedAccount a : SavedAccount.loadAccountList( ActAppSetting.this, log ) ){
				if( a.isPseudo() ) continue;
				list.add( a );
			}
			SavedAccount.sort( list );
		}
		
		@Override public int getCount(){
			return 1 + list.size();
		}
		
		@Override public Object getItem( int position ){
			return position == 0 ? null : list.get( position - 1 );
		}
		
		@Override public long getItemId( int position ){
			return 0;
		}
		
		@Override public View getView( int position, View view, ViewGroup parent ){
			if( view == null )
				view = getLayoutInflater().inflate( android.R.layout.simple_spinner_item, parent, false );
			( (TextView) view.findViewById( android.R.id.text1 ) ).setText(
				position == 0 ? getString( R.string.ask_always ) : AcctColor.getNickname( list.get( position - 1 ).acct )
			);
			return view;
		}
		
		@Override public View getDropDownView( int position, View view, ViewGroup parent ){
			if( view == null )
				view = getLayoutInflater().inflate( R.layout.lv_spinner_dropdown, parent, false );
			( (TextView) view.findViewById( android.R.id.text1 ) ).setText(
				position == 0 ? getString( R.string.ask_always ) : AcctColor.getNickname( list.get( position - 1 ).acct )
			);
			return view;
		}
		
		int getIndexFromId( long db_id ){
			for( int i = 0, ie = list.size() ; i < ie ; ++ i ){
				if( list.get( i ).db_id == db_id ) return i + 1;
			}
			return 0;
		}
		
		long getIdFromIndex( int position ){
			if( position > 0 ) return list.get( position - 1 ).db_id;
			return - 1L;
		}
	}
}
