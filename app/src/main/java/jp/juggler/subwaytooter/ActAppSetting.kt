package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.v4.content.FileProvider
import android.support.v4.view.ViewCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.JsonWriter
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView

import com.jrummyapps.android.colorpicker.ColorPickerDialog
import com.jrummyapps.android.colorpicker.ColorPickerDialogListener

import org.apache.commons.io.IOUtils
import org.apache.commons.io.output.FileWriterWithEncoding

import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.ArrayList
import java.util.Locale

import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils

class ActAppSetting : AppCompatActivity()
	, CompoundButton.OnCheckedChangeListener
	, AdapterView.OnItemSelectedListener
	, View.OnClickListener
	, ColorPickerDialogListener
	, TextWatcher {
	
	companion object {
		internal val log = LogCategory("ActAppSetting")
		
		internal const val BACK_ASK_ALWAYS = 0
		internal const val BACK_CLOSE_COLUMN = 1
		internal const val BACK_OPEN_COLUMN_LIST = 2
		internal const val BACK_EXIT_APP = 3
		
		internal const val default_timeline_font_size = 14f
		internal const val default_acct_font_size = 12f
		
		internal const val COLOR_DIALOG_ID_FOOTER_BUTTON_BG = 1
		internal const val COLOR_DIALOG_ID_FOOTER_BUTTON_FG = 2
		internal const val COLOR_DIALOG_ID_FOOTER_TAB_BG = 3
		internal const val COLOR_DIALOG_ID_FOOTER_TAB_DIVIDER = 4
		internal const val COLOR_DIALOG_ID_FOOTER_TAB_INDICATOR = 5
		
		internal const val REQUEST_CODE_TIMELINE_FONT = 1
		internal const val REQUEST_CODE_TIMELINE_FONT_BOLD = 2
		internal const val REQUEST_CODE_APP_DATA_EXPORT = 3
		internal const val REQUEST_CODE_APP_DATA_IMPORT = 4
		
		const val colorFF000000 : Int = (0xff shl 24)
		
		fun open(activity : ActMain, request_code : Int) {
			activity.startActivityForResult(Intent(activity, ActAppSetting::class.java), request_code)
		}
		
	}
	
	internal lateinit var pref : SharedPreferences
	
	private lateinit var swDontConfirmBeforeCloseColumn : Switch
	private lateinit var swPriorLocalURL : Switch
	private lateinit var swDisableFastScroller : Switch
	private lateinit var swSimpleList : Switch
	private lateinit var swExitAppWhenCloseProtectedColumn : Switch
	private lateinit var swShowFollowButtonInButtonBar : Switch
	private lateinit var swDontRound : Switch
	private lateinit var swDontUseStreaming : Switch
	private lateinit var swDontRefreshOnResume : Switch
	private lateinit var swDontScreenOff : Switch
	private lateinit var swDisableTabletMode : Switch
	private lateinit var swDontCropMediaThumb : Switch
	private lateinit var swPriorChrome : Switch
	private lateinit var swPostButtonBarTop : Switch
	private lateinit var swDontDuplicationCheck : Switch
	private lateinit var swQuickTootBar : Switch
	private lateinit var swEnableGifAnimation : Switch
	private lateinit var swMentionFullAcct : Switch
	private lateinit var swRelativeTimestamp : Switch
	private lateinit var swDontUseActionButtonWithQuickTootBar : Switch
	private lateinit var swShortAcctLocalUser : Switch
	private lateinit var swDisableEmojiAnimation : Switch
	private lateinit var swAllowNonSpaceBeforeEmojiShortcode : Switch
	private lateinit var swUseInternalMediaViewer : Switch
	
	private lateinit var spBackButtonAction : Spinner
	private lateinit var spUITheme : Spinner
	private lateinit var spResizeImage : Spinner
	private lateinit var spRefreshAfterToot : Spinner
	private lateinit var spDefaultAccount : Spinner
	
	private lateinit var cbNotificationSound : CheckBox
	private lateinit var cbNotificationVibration : CheckBox
	private lateinit var cbNotificationLED : CheckBox
	
	private var footer_button_bg_color : Int = 0
	private var footer_button_fg_color : Int = 0
	private var footer_tab_bg_color : Int = 0
	private var footer_tab_divider_color : Int = 0
	private var footer_tab_indicator_color : Int = 0
	
	private lateinit var ivFooterToot : ImageView
	private lateinit var ivFooterMenu : ImageView
	private lateinit var llFooterBG : View
	private lateinit var vFooterDivider1 : View
	private lateinit var vFooterDivider2 : View
	private lateinit var vIndicator : View
	
	private lateinit var etColumnWidth : EditText
	private lateinit var etMediaThumbHeight : EditText
	private lateinit var etClientName : EditText
	private lateinit var etQuoteNameFormat : EditText
	private lateinit var etAutoCWLines : EditText
	private lateinit var etMediaSizeMax : EditText
	
	private lateinit var tvTimelineFontUrl : TextView
	private var timeline_font : String? = null
	private lateinit var tvTimelineFontBoldUrl : TextView
	private var timeline_font_bold : String? = null
	
	private lateinit var etTimelineFontSize : EditText
	private lateinit var etAcctFontSize : EditText
	private lateinit var tvTimelineFontSize : TextView
	private lateinit var tvAcctFontSize : TextView
	private lateinit var etAvatarIconSize : EditText
	
	private var load_busy : Boolean = false
	
	override fun onPause() {
		super.onPause()
		
		// DefaultAccount の Spinnerの値を復元するため、このタイミングでも保存することになった
		saveUIToData()
	}
	
	override fun onCreate(savedInstanceState : Bundle?) {
		super.onCreate(savedInstanceState)
		App1.setActivityTheme(this, false)
		initUI()
		pref = Pref.pref(this)
		
		loadUIFromData()
	}
	
	private fun initUI() {
		setContentView(R.layout.act_app_setting)
		
		Styler.fixHorizontalPadding(findViewById(R.id.svContent))
		
		swDontConfirmBeforeCloseColumn = findViewById(R.id.swDontConfirmBeforeCloseColumn)
		swDontConfirmBeforeCloseColumn.setOnCheckedChangeListener(this)
		
		swPriorLocalURL = findViewById(R.id.swPriorLocalURL)
		swPriorLocalURL.setOnCheckedChangeListener(this)
		
		swDisableFastScroller = findViewById(R.id.swDisableFastScroller)
		swDisableFastScroller.setOnCheckedChangeListener(this)
		
		swSimpleList = findViewById(R.id.swSimpleList)
		swSimpleList.setOnCheckedChangeListener(this)
		
		swExitAppWhenCloseProtectedColumn = findViewById(R.id.swExitAppWhenCloseProtectedColumn)
		swExitAppWhenCloseProtectedColumn.setOnCheckedChangeListener(this)
		
		swShowFollowButtonInButtonBar = findViewById(R.id.swShowFollowButtonInButtonBar)
		swShowFollowButtonInButtonBar.setOnCheckedChangeListener(this)
		
		swDontRound = findViewById(R.id.swDontRound)
		swDontRound.setOnCheckedChangeListener(this)
		
		swDontUseStreaming = findViewById(R.id.swDontUseStreaming)
		swDontUseStreaming.setOnCheckedChangeListener(this)
		
		swDontRefreshOnResume = findViewById(R.id.swDontRefreshOnResume)
		swDontRefreshOnResume.setOnCheckedChangeListener(this)
		
		swDontScreenOff = findViewById(R.id.swDontScreenOff)
		swDontScreenOff.setOnCheckedChangeListener(this)
		
		swDisableTabletMode = findViewById(R.id.swDisableTabletMode)
		swDisableTabletMode.setOnCheckedChangeListener(this)
		
		swDontCropMediaThumb = findViewById(R.id.swDontCropMediaThumb)
		swDontCropMediaThumb.setOnCheckedChangeListener(this)
		
		swPriorChrome = findViewById(R.id.swPriorChrome)
		swPriorChrome.setOnCheckedChangeListener(this)
		
		swPostButtonBarTop = findViewById(R.id.swPostButtonBarTop)
		swPostButtonBarTop.setOnCheckedChangeListener(this)
		
		swDontDuplicationCheck = findViewById(R.id.swDontDuplicationCheck)
		swDontDuplicationCheck.setOnCheckedChangeListener(this)
		
		swQuickTootBar = findViewById(R.id.swQuickTootBar)
		swQuickTootBar.setOnCheckedChangeListener(this)
		
		swEnableGifAnimation = findViewById(R.id.swEnableGifAnimation)
		swEnableGifAnimation.setOnCheckedChangeListener(this)
		
		swMentionFullAcct = findViewById(R.id.swMentionFullAcct)
		swMentionFullAcct.setOnCheckedChangeListener(this)
		
		swRelativeTimestamp = findViewById(R.id.swRelativeTimestamp)
		swRelativeTimestamp.setOnCheckedChangeListener(this)
		
		swDontUseActionButtonWithQuickTootBar = findViewById(R.id.swDontUseActionButtonWithQuickTootBar)
		swDontUseActionButtonWithQuickTootBar.setOnCheckedChangeListener(this)
		
		swShortAcctLocalUser = findViewById(R.id.swShortAcctLocalUser)
		swShortAcctLocalUser.setOnCheckedChangeListener(this)
		
		swDisableEmojiAnimation = findViewById(R.id.swDisableEmojiAnimation)
		swDisableEmojiAnimation.setOnCheckedChangeListener(this)
		
		swAllowNonSpaceBeforeEmojiShortcode = findViewById(R.id.swAllowNonSpaceBeforeEmojiShortcode)
		swAllowNonSpaceBeforeEmojiShortcode.setOnCheckedChangeListener(this)
		
		swUseInternalMediaViewer = findViewById(R.id.swUseInternalMediaViewer)
		swUseInternalMediaViewer.setOnCheckedChangeListener(this)
		
		cbNotificationSound = findViewById(R.id.cbNotificationSound)
		cbNotificationVibration = findViewById(R.id.cbNotificationVibration)
		cbNotificationLED = findViewById(R.id.cbNotificationLED)
		cbNotificationSound.setOnCheckedChangeListener(this)
		cbNotificationVibration.setOnCheckedChangeListener(this)
		cbNotificationLED.setOnCheckedChangeListener(this)
		
		val bBefore8 = Build.VERSION.SDK_INT < 26
		cbNotificationSound.isEnabled = bBefore8
		cbNotificationVibration.isEnabled = bBefore8
		cbNotificationLED.isEnabled = bBefore8
		
		run {
			val caption_list = arrayOf(getString(R.string.ask_always), getString(R.string.close_column), getString(R.string.open_column_list), getString(R.string.app_exit))
			val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, caption_list)
			adapter.setDropDownViewResource(R.layout.lv_spinner_dropdown)
			spBackButtonAction = findViewById(R.id.spBackButtonAction)
			spBackButtonAction.adapter = adapter
			spBackButtonAction.onItemSelectedListener = this
		}
		
		run {
			val caption_list = arrayOf(getString(R.string.theme_light), getString(R.string.theme_dark))
			val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, caption_list)
			adapter.setDropDownViewResource(R.layout.lv_spinner_dropdown)
			spUITheme = findViewById(R.id.spUITheme)
			spUITheme.adapter = adapter
			spUITheme.onItemSelectedListener = this
		}
		run {
			val caption_list = arrayOf(getString(R.string.dont_resize), getString(R.string.long_side_pixel, 640), getString(R.string.long_side_pixel, 800), getString(R.string.long_side_pixel, 1024), getString(R.string.long_side_pixel, 1280)) //// サーバ側でさらに縮小されるようなので、1280より上は用意しない
			//	Integer.toString( 1600 ),
			//	Integer.toString( 2048 ),
			val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, caption_list)
			adapter.setDropDownViewResource(R.layout.lv_spinner_dropdown)
			spResizeImage = findViewById(R.id.spResizeImage)
			spResizeImage.adapter = adapter
			spResizeImage.onItemSelectedListener = this
		}
		
		run {
			val caption_list = arrayOf(getString(R.string.refresh_scroll_to_toot), getString(R.string.refresh_no_scroll), getString(R.string.dont_refresh))
			val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, caption_list)
			adapter.setDropDownViewResource(R.layout.lv_spinner_dropdown)
			spRefreshAfterToot = findViewById(R.id.spRefreshAfterToot)
			spRefreshAfterToot.adapter = adapter
			spRefreshAfterToot.onItemSelectedListener = this
		}
		
		run {
			
			val adapter = AccountAdapter()
			spDefaultAccount = findViewById(R.id.spDefaultAccount)
			spDefaultAccount.adapter = adapter
			spDefaultAccount.onItemSelectedListener = this
		}
		
		findViewById<View>(R.id.btnFooterBackgroundEdit).setOnClickListener(this)
		findViewById<View>(R.id.btnFooterBackgroundReset).setOnClickListener(this)
		findViewById<View>(R.id.btnFooterForegroundColorEdit).setOnClickListener(this)
		findViewById<View>(R.id.btnFooterForegroundColorReset).setOnClickListener(this)
		findViewById<View>(R.id.btnTabBackgroundColorEdit).setOnClickListener(this)
		findViewById<View>(R.id.btnTabBackgroundColorReset).setOnClickListener(this)
		findViewById<View>(R.id.btnTabDividerColorEdit).setOnClickListener(this)
		findViewById<View>(R.id.btnTabDividerColorReset).setOnClickListener(this)
		findViewById<View>(R.id.btnTabIndicatorColorEdit).setOnClickListener(this)
		findViewById<View>(R.id.btnTabIndicatorColorReset).setOnClickListener(this)
		
		findViewById<View>(R.id.btnTimelineFontEdit).setOnClickListener(this)
		findViewById<View>(R.id.btnTimelineFontReset).setOnClickListener(this)
		findViewById<View>(R.id.btnTimelineFontBoldEdit).setOnClickListener(this)
		findViewById<View>(R.id.btnTimelineFontBoldReset).setOnClickListener(this)
		findViewById<View>(R.id.btnSettingExport).setOnClickListener(this)
		findViewById<View>(R.id.btnSettingImport).setOnClickListener(this)
		findViewById<View>(R.id.btnCustomStreamListenerEdit).setOnClickListener(this)
		findViewById<View>(R.id.btnCustomStreamListenerReset).setOnClickListener(this)
		
		ivFooterToot = findViewById(R.id.ivFooterToot)
		ivFooterMenu = findViewById(R.id.ivFooterMenu)
		llFooterBG = findViewById(R.id.llFooterBG)
		vFooterDivider1 = findViewById(R.id.vFooterDivider1)
		vFooterDivider2 = findViewById(R.id.vFooterDivider2)
		vIndicator = findViewById(R.id.vIndicator)
		
		etColumnWidth = findViewById(R.id.etColumnWidth)
		etColumnWidth.addTextChangedListener(this)
		
		etMediaThumbHeight = findViewById(R.id.etMediaThumbHeight)
		etMediaThumbHeight.addTextChangedListener(this)
		
		etClientName = findViewById(R.id.etClientName)
		etClientName.addTextChangedListener(this)
		
		etQuoteNameFormat = findViewById(R.id.etQuoteNameFormat)
		etQuoteNameFormat.addTextChangedListener(this)
		
		etAutoCWLines = findViewById(R.id.etAutoCWLines)
		etAutoCWLines.addTextChangedListener(this)
		
		etMediaSizeMax = findViewById(R.id.etMediaSizeMax)
		etMediaSizeMax.addTextChangedListener(this)
		
		tvTimelineFontSize = findViewById(R.id.tvTimelineFontSize)
		tvAcctFontSize = findViewById(R.id.tvAcctFontSize)
		
		etTimelineFontSize = findViewById(R.id.etTimelineFontSize)
		etTimelineFontSize.addTextChangedListener(SizeCheckTextWatcher(tvTimelineFontSize, etTimelineFontSize, default_timeline_font_size))
		
		etAcctFontSize = findViewById(R.id.etAcctFontSize)
		etAcctFontSize.addTextChangedListener(SizeCheckTextWatcher(tvAcctFontSize, etAcctFontSize, default_acct_font_size))
		
		etAvatarIconSize = findViewById(R.id.etAvatarIconSize)
		
		tvTimelineFontUrl = findViewById(R.id.tvTimelineFontUrl)
		tvTimelineFontBoldUrl = findViewById(R.id.tvTimelineFontBoldUrl)
		
	}
	
	private fun loadUIFromData() {
		load_busy = true
		
		swDontConfirmBeforeCloseColumn.isChecked = pref.getBoolean(Pref.KEY_DONT_CONFIRM_BEFORE_CLOSE_COLUMN, false)
		swPriorLocalURL.isChecked = pref.getBoolean(Pref.KEY_PRIOR_LOCAL_URL, false)
		swSimpleList.isChecked = pref.getBoolean(Pref.KEY_SIMPLE_LIST, true)
		swExitAppWhenCloseProtectedColumn.isChecked = pref.getBoolean(Pref.KEY_EXIT_APP_WHEN_CLOSE_PROTECTED_COLUMN, false)
		swShowFollowButtonInButtonBar.isChecked = pref.getBoolean(Pref.KEY_SHOW_FOLLOW_BUTTON_IN_BUTTON_BAR, false)
		swDontRound.isChecked = pref.getBoolean(Pref.KEY_DONT_ROUND, false)
		swDontUseStreaming.isChecked = pref.getBoolean(Pref.KEY_DONT_USE_STREAMING, false)
		swDontRefreshOnResume.isChecked = pref.getBoolean(Pref.KEY_DONT_REFRESH_ON_RESUME, false)
		swDontScreenOff.isChecked = pref.getBoolean(Pref.KEY_DONT_SCREEN_OFF, false)
		swDisableTabletMode.isChecked = pref.getBoolean(Pref.KEY_DISABLE_TABLET_MODE, false)
		swDontCropMediaThumb.isChecked = pref.getBoolean(Pref.KEY_DONT_CROP_MEDIA_THUMBNAIL, false)
		swPostButtonBarTop.isChecked = pref.getBoolean(Pref.KEY_POST_BUTTON_BAR_AT_TOP, false)
		swDontDuplicationCheck.isChecked = pref.getBoolean(Pref.KEY_DONT_DUPLICATION_CHECK, false)
		swQuickTootBar.isChecked = pref.getBoolean(Pref.KEY_QUICK_TOOT_BAR, false)
		swEnableGifAnimation.isChecked = pref.getBoolean(Pref.KEY_ENABLE_GIF_ANIMATION, false)
		swMentionFullAcct.isChecked = pref.getBoolean(Pref.KEY_MENTION_FULL_ACCT, false)
		swRelativeTimestamp.isChecked = pref.getBoolean(Pref.KEY_RELATIVE_TIMESTAMP, false)
		swDontUseActionButtonWithQuickTootBar.isChecked = pref.getBoolean(Pref.KEY_DONT_USE_ACTION_BUTTON, false)
		swShortAcctLocalUser.isChecked = pref.getBoolean(Pref.KEY_SHORT_ACCT_LOCAL_USER, false)
		swDisableEmojiAnimation.isChecked = pref.getBoolean(Pref.KEY_DISABLE_EMOJI_ANIMATION, false)
		swAllowNonSpaceBeforeEmojiShortcode.isChecked = pref.getBoolean(Pref.KEY_ALLOW_NON_SPACE_BEFORE_EMOJI_SHORTCODE, false)
		swUseInternalMediaViewer.isChecked = pref.getBoolean(Pref.KEY_USE_INTERNAL_MEDIA_VIEWER, true)
		// Switch with default true
		swDisableFastScroller.isChecked = pref.getBoolean(Pref.KEY_DISABLE_FAST_SCROLLER, true)
		swPriorChrome.isChecked = pref.getBoolean(Pref.KEY_PRIOR_CHROME, true)
		
		cbNotificationSound.isChecked = pref.getBoolean(Pref.KEY_NOTIFICATION_SOUND, true)
		cbNotificationVibration.isChecked = pref.getBoolean(Pref.KEY_NOTIFICATION_VIBRATION, true)
		cbNotificationLED.isChecked = pref.getBoolean(Pref.KEY_NOTIFICATION_LED, true)
		
		spBackButtonAction.setSelection(pref.getInt(Pref.KEY_BACK_BUTTON_ACTION, 0))
		spUITheme.setSelection(pref.getInt(Pref.KEY_UI_THEME, 0))
		spResizeImage.setSelection(pref.getInt(Pref.KEY_RESIZE_IMAGE, 4))
		spRefreshAfterToot.setSelection(pref.getInt(Pref.KEY_REFRESH_AFTER_TOOT, 0))
		
		spDefaultAccount.setSelection(
			(spDefaultAccount.adapter as AccountAdapter).getIndexFromId(pref.getLong(Pref.KEY_TABLET_TOOT_DEFAULT_ACCOUNT, - 1L))
		)
		
		footer_button_bg_color = pref.getInt(Pref.KEY_FOOTER_BUTTON_BG_COLOR, 0)
		footer_button_fg_color = pref.getInt(Pref.KEY_FOOTER_BUTTON_FG_COLOR, 0)
		footer_tab_bg_color = pref.getInt(Pref.KEY_FOOTER_TAB_BG_COLOR, 0)
		footer_tab_divider_color = pref.getInt(Pref.KEY_FOOTER_TAB_DIVIDER_COLOR, 0)
		footer_tab_indicator_color = pref.getInt(Pref.KEY_FOOTER_TAB_INDICATOR_COLOR, 0)
		
		etColumnWidth.setText(pref.getString(Pref.KEY_COLUMN_WIDTH, ""))
		etMediaThumbHeight.setText(pref.getString(Pref.KEY_MEDIA_THUMB_HEIGHT, ""))
		etClientName.setText(pref.getString(Pref.KEY_CLIENT_NAME, ""))
		etQuoteNameFormat.setText(pref.getString(Pref.KEY_QUOTE_NAME_FORMAT, ""))
		etAutoCWLines.setText(pref.getString(Pref.KEY_AUTO_CW_LINES, "0"))
		etAvatarIconSize.setText(pref.getString(Pref.KEY_AVATAR_ICON_SIZE, "48"))
		
		etMediaSizeMax.setText(pref.getString(Pref.KEY_MEDIA_SIZE_MAX, "8"))
		
		etTimelineFontSize.setText(formatFontSize(pref.getFloat(Pref.KEY_TIMELINE_FONT_SIZE, Float.NaN)))
		etAcctFontSize.setText(formatFontSize(pref.getFloat(Pref.KEY_ACCT_FONT_SIZE, Float.NaN)))
		
		timeline_font = pref.getString(Pref.KEY_TIMELINE_FONT, "")
		timeline_font_bold = pref.getString(Pref.KEY_TIMELINE_FONT_BOLD, "")
		
		load_busy = false
		
		showFooterColor()
		showTimelineFont(tvTimelineFontUrl, timeline_font)
		showTimelineFont(tvTimelineFontBoldUrl, timeline_font_bold)
		
		showFontSize(tvTimelineFontSize, etTimelineFontSize, default_timeline_font_size)
		showFontSize(tvAcctFontSize, etAcctFontSize, default_acct_font_size)
	}
	
	private fun saveUIToData() {
		if(load_busy) return
		pref.edit()
			.putBoolean(Pref.KEY_DONT_CONFIRM_BEFORE_CLOSE_COLUMN, swDontConfirmBeforeCloseColumn.isChecked)
			.putBoolean(Pref.KEY_PRIOR_LOCAL_URL, swPriorLocalURL.isChecked)
			.putBoolean(Pref.KEY_DISABLE_FAST_SCROLLER, swDisableFastScroller.isChecked)
			.putBoolean(Pref.KEY_SIMPLE_LIST, swSimpleList.isChecked)
			.putBoolean(Pref.KEY_EXIT_APP_WHEN_CLOSE_PROTECTED_COLUMN, swExitAppWhenCloseProtectedColumn.isChecked)
			.putBoolean(Pref.KEY_SHOW_FOLLOW_BUTTON_IN_BUTTON_BAR, swShowFollowButtonInButtonBar.isChecked)
			.putBoolean(Pref.KEY_DONT_ROUND, swDontRound.isChecked)
			.putBoolean(Pref.KEY_DONT_USE_STREAMING, swDontUseStreaming.isChecked)
			.putBoolean(Pref.KEY_DONT_REFRESH_ON_RESUME, swDontRefreshOnResume.isChecked)
			.putBoolean(Pref.KEY_DONT_SCREEN_OFF, swDontScreenOff.isChecked)
			.putBoolean(Pref.KEY_DISABLE_TABLET_MODE, swDisableTabletMode.isChecked)
			.putBoolean(Pref.KEY_DONT_CROP_MEDIA_THUMBNAIL, swDontCropMediaThumb.isChecked)
			.putBoolean(Pref.KEY_PRIOR_CHROME, swPriorChrome.isChecked)
			.putBoolean(Pref.KEY_POST_BUTTON_BAR_AT_TOP, swPostButtonBarTop.isChecked)
			.putBoolean(Pref.KEY_DONT_DUPLICATION_CHECK, swDontDuplicationCheck.isChecked)
			.putBoolean(Pref.KEY_QUICK_TOOT_BAR, swQuickTootBar.isChecked)
			.putBoolean(Pref.KEY_ENABLE_GIF_ANIMATION, swEnableGifAnimation.isChecked)
			.putBoolean(Pref.KEY_MENTION_FULL_ACCT, swMentionFullAcct.isChecked)
			.putBoolean(Pref.KEY_RELATIVE_TIMESTAMP, swRelativeTimestamp.isChecked)
			.putBoolean(Pref.KEY_DONT_USE_ACTION_BUTTON, swDontUseActionButtonWithQuickTootBar.isChecked)
			.putBoolean(Pref.KEY_SHORT_ACCT_LOCAL_USER, swShortAcctLocalUser.isChecked)
			.putBoolean(Pref.KEY_DISABLE_EMOJI_ANIMATION, swDisableEmojiAnimation.isChecked)
			.putBoolean(Pref.KEY_ALLOW_NON_SPACE_BEFORE_EMOJI_SHORTCODE, swAllowNonSpaceBeforeEmojiShortcode.isChecked)
			.putBoolean(Pref.KEY_USE_INTERNAL_MEDIA_VIEWER, swUseInternalMediaViewer.isChecked)
			
			.putBoolean(Pref.KEY_NOTIFICATION_SOUND, cbNotificationSound.isChecked)
			.putBoolean(Pref.KEY_NOTIFICATION_VIBRATION, cbNotificationVibration.isChecked)
			.putBoolean(Pref.KEY_NOTIFICATION_LED, cbNotificationLED.isChecked)
			
			.putInt(Pref.KEY_BACK_BUTTON_ACTION, spBackButtonAction.selectedItemPosition)
			.putInt(Pref.KEY_UI_THEME, spUITheme.selectedItemPosition)
			.putInt(Pref.KEY_RESIZE_IMAGE, spResizeImage.selectedItemPosition)
			.putInt(Pref.KEY_REFRESH_AFTER_TOOT, spRefreshAfterToot.selectedItemPosition)
			
			.putInt(Pref.KEY_FOOTER_BUTTON_BG_COLOR, footer_button_bg_color)
			.putInt(Pref.KEY_FOOTER_BUTTON_FG_COLOR, footer_button_fg_color)
			.putInt(Pref.KEY_FOOTER_TAB_BG_COLOR, footer_tab_bg_color)
			.putInt(Pref.KEY_FOOTER_TAB_DIVIDER_COLOR, footer_tab_divider_color)
			.putInt(Pref.KEY_FOOTER_TAB_INDICATOR_COLOR, footer_tab_indicator_color)
			
			.putLong(Pref.KEY_TABLET_TOOT_DEFAULT_ACCOUNT, (spDefaultAccount.adapter as AccountAdapter)
				.getIdFromIndex(spDefaultAccount.selectedItemPosition))
			
			.putString(Pref.KEY_TIMELINE_FONT, timeline_font)
			.putString(Pref.KEY_TIMELINE_FONT_BOLD, timeline_font_bold)
			.putString(Pref.KEY_COLUMN_WIDTH, etColumnWidth.text.toString().trim { it <= ' ' })
			.putString(Pref.KEY_MEDIA_THUMB_HEIGHT, etMediaThumbHeight.text.toString().trim { it <= ' ' })
			.putString(Pref.KEY_CLIENT_NAME, etClientName.text.toString().trim { it <= ' ' })
			.putString(Pref.KEY_QUOTE_NAME_FORMAT, etQuoteNameFormat.text.toString()) // not trimmed
			.putString(Pref.KEY_AUTO_CW_LINES, etAutoCWLines.text.toString()) // not trimmed
			.putString(Pref.KEY_AVATAR_ICON_SIZE, etAvatarIconSize.text.toString().trim { it <= ' ' })
			.putString(Pref.KEY_MEDIA_SIZE_MAX, etMediaSizeMax.text.toString()) // not trimmed
			
			.putFloat(Pref.KEY_TIMELINE_FONT_SIZE, parseFontSize(etTimelineFontSize.text.toString().trim { it <= ' ' }))
			.putFloat(Pref.KEY_ACCT_FONT_SIZE, parseFontSize(etAcctFontSize.text.toString().trim { it <= ' ' }))
			
			.apply()
		
	}
	
	override fun onCheckedChanged(buttonView : CompoundButton, isChecked : Boolean) {
		saveUIToData()
	}
	
	override fun onItemSelected(parent : AdapterView<*>, view : View, position : Int, id : Long) {
		saveUIToData()
	}
	
	override fun onNothingSelected(parent : AdapterView<*>) {}
	
	override fun onClick(v : View) {
		when(v.id) {
			
			R.id.btnFooterBackgroundEdit -> openColorPicker(COLOR_DIALOG_ID_FOOTER_BUTTON_BG, footer_button_bg_color, false)
			
			R.id.btnFooterBackgroundReset -> {
				footer_button_bg_color = 0
				saveUIToData()
				showFooterColor()
			}
			
			R.id.btnFooterForegroundColorEdit -> openColorPicker(COLOR_DIALOG_ID_FOOTER_BUTTON_FG, footer_button_fg_color, false)
			
			R.id.btnFooterForegroundColorReset -> {
				footer_button_fg_color = 0
				saveUIToData()
				showFooterColor()
			}
			
			R.id.btnTabBackgroundColorEdit -> openColorPicker(COLOR_DIALOG_ID_FOOTER_TAB_BG, footer_tab_bg_color, false)
			
			R.id.btnTabBackgroundColorReset -> {
				footer_tab_bg_color = 0
				saveUIToData()
				showFooterColor()
			}
			
			R.id.btnTabDividerColorEdit -> openColorPicker(COLOR_DIALOG_ID_FOOTER_TAB_DIVIDER, footer_tab_divider_color, false)
			
			R.id.btnTabDividerColorReset -> {
				footer_tab_divider_color = 0
				saveUIToData()
				showFooterColor()
			}
			
			R.id.btnTabIndicatorColorEdit -> openColorPicker(COLOR_DIALOG_ID_FOOTER_TAB_INDICATOR, footer_tab_indicator_color, true)
			
			R.id.btnTabIndicatorColorReset -> {
				footer_tab_indicator_color = 0
				saveUIToData()
				showFooterColor()
			}
			
			R.id.btnTimelineFontReset -> {
				timeline_font = ""
				saveUIToData()
				showTimelineFont(tvTimelineFontUrl, timeline_font)
			}
			
			R.id.btnTimelineFontEdit -> try {
				val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
				intent.addCategory(Intent.CATEGORY_OPENABLE)
				intent.type = "*/*"
				startActivityForResult(intent, REQUEST_CODE_TIMELINE_FONT)
			} catch(ex : Throwable) {
				Utils.showToast(this, ex, "could not open picker for font")
			}
			
			R.id.btnTimelineFontBoldReset -> {
				timeline_font_bold = ""
				saveUIToData()
				showTimelineFont(tvTimelineFontBoldUrl, timeline_font_bold)
			}
			
			R.id.btnTimelineFontBoldEdit -> try {
				val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
				intent.addCategory(Intent.CATEGORY_OPENABLE)
				intent.type = "*/*"
				startActivityForResult(intent, REQUEST_CODE_TIMELINE_FONT_BOLD)
			} catch(ex : Throwable) {
				Utils.showToast(this, ex, "could not open picker for font")
			}
			
			R.id.btnSettingExport -> exportAppData()
			
			R.id.btnSettingImport -> importAppData()
			
			R.id.btnCustomStreamListenerEdit -> ActCustomStreamListener.open(this)
			
			R.id.btnCustomStreamListenerReset -> {
				pref
					.edit()
					.remove(Pref.KEY_STREAM_LISTENER_CONFIG_URL)
					.remove(Pref.KEY_STREAM_LISTENER_SECRET)
					.remove(Pref.KEY_STREAM_LISTENER_CONFIG_DATA)
					.apply()
				SavedAccount.clearRegistrationCache()
				PollingWorker.queueUpdateListener(this)
				Utils.showToast(this, false, getString(R.string.custom_stream_listener_was_reset))
			}
		}
	}
	
	override fun onActivityResult(requestCode : Int, resultCode : Int, data : Intent?) {
		if(resultCode == RESULT_OK && data != null && requestCode == REQUEST_CODE_TIMELINE_FONT) {
			val file = saveTimelineFont(data.data, "TimelineFont")
			if(file != null) {
				timeline_font = file.absolutePath
				saveUIToData()
				showTimelineFont(tvTimelineFontUrl, timeline_font)
			}
		} else if(resultCode == RESULT_OK && data != null && requestCode == REQUEST_CODE_TIMELINE_FONT_BOLD) {
			val file = saveTimelineFont(data.data, "TimelineFontBold")
			if(file != null) {
				timeline_font_bold = file.absolutePath
				saveUIToData()
				showTimelineFont(tvTimelineFontBoldUrl, timeline_font_bold)
			}
		} else if(resultCode == RESULT_OK && requestCode == REQUEST_CODE_APP_DATA_IMPORT) {
			if(data != null) {
				val uri = data.data
				if(uri != null) {
					contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
					importAppData(false, uri)
				}
			}
		}
		super.onActivityResult(requestCode, resultCode, data)
	}
	
	private fun openColorPicker(id : Int, color : Int, bShowAlphaSlider : Boolean) {
		val builder = ColorPickerDialog.newBuilder()
			.setDialogType(ColorPickerDialog.TYPE_CUSTOM)
			.setAllowPresets(true)
			.setShowAlphaSlider(bShowAlphaSlider)
			.setDialogId(id)
		if(color != 0) builder.setColor(color)
		builder.show(this)
	}
	
	override fun onColorSelected(dialogId : Int, @ColorInt colorSelected : Int) {
		when(dialogId) {
			
			COLOR_DIALOG_ID_FOOTER_BUTTON_BG -> {
				footer_button_bg_color = colorFF000000 or colorSelected
				saveUIToData()
				showFooterColor()
			}
			
			COLOR_DIALOG_ID_FOOTER_BUTTON_FG -> {
				footer_button_fg_color = colorFF000000 or colorSelected
				saveUIToData()
				showFooterColor()
			}
			
			COLOR_DIALOG_ID_FOOTER_TAB_BG -> {
				footer_tab_bg_color = colorFF000000 or colorSelected
				saveUIToData()
				showFooterColor()
			}
			
			COLOR_DIALOG_ID_FOOTER_TAB_DIVIDER -> {
				footer_tab_divider_color = colorFF000000 or colorSelected
				saveUIToData()
				showFooterColor()
			}
			
			COLOR_DIALOG_ID_FOOTER_TAB_INDICATOR -> {
				footer_tab_indicator_color = if(colorSelected == 0) 0x01000000 else colorSelected
				saveUIToData()
				showFooterColor()
			}
		}
	}
	
	override fun onDialogDismissed(dialogId : Int) {}
	
	private fun showFooterColor() {
		
		var c = footer_button_bg_color
		if(c == 0) {
			ivFooterToot.setBackgroundResource(R.drawable.btn_bg_ddd)
			ivFooterMenu.setBackgroundResource(R.drawable.btn_bg_ddd)
		} else {
			val fg = if(footer_button_fg_color != 0)
				footer_button_fg_color
			else
				Styler.getAttributeColor(this, R.attr.colorRippleEffect)
			ViewCompat.setBackground(ivFooterToot, Styler.getAdaptiveRippleDrawable(c, fg))
			ViewCompat.setBackground(ivFooterMenu, Styler.getAdaptiveRippleDrawable(c, fg))
		}
		
		c = footer_button_fg_color
		if(c == 0) {
			Styler.setIconDefaultColor(this, ivFooterToot, R.attr.ic_edit)
			Styler.setIconDefaultColor(this, ivFooterMenu, R.attr.ic_hamburger)
		} else {
			Styler.setIconCustomColor(this, ivFooterToot, c, R.attr.ic_edit)
			Styler.setIconCustomColor(this, ivFooterMenu, c, R.attr.ic_hamburger)
		}
		
		c = footer_tab_bg_color
		if(c == 0) {
			llFooterBG.setBackgroundColor(Styler.getAttributeColor(this, R.attr.colorColumnStripBackground))
		} else {
			llFooterBG.setBackgroundColor(c)
		}
		
		c = footer_tab_divider_color
		if(c == 0) {
			vFooterDivider1.setBackgroundColor(Styler.getAttributeColor(this, R.attr.colorImageButton))
			vFooterDivider2.setBackgroundColor(Styler.getAttributeColor(this, R.attr.colorImageButton))
		} else {
			vFooterDivider1.setBackgroundColor(c)
			vFooterDivider2.setBackgroundColor(c)
		}
		
		c = footer_tab_indicator_color
		if(c == 0) {
			vIndicator.setBackgroundColor(Styler.getAttributeColor(this, R.attr.colorAccent))
		} else {
			vIndicator.setBackgroundColor(c)
		}
	}
	
	override fun beforeTextChanged(s : CharSequence, start : Int, count : Int, after : Int) {
	
	}
	
	override fun onTextChanged(s : CharSequence, start : Int, before : Int, count : Int) {
	
	}
	
	override fun afterTextChanged(s : Editable) {
		saveUIToData()
	}
	
	private inner class SizeCheckTextWatcher internal constructor(internal val sample : TextView, internal val et : EditText, internal val default_size_sp : Float) : TextWatcher {
		
		override fun beforeTextChanged(s : CharSequence, start : Int, count : Int, after : Int) {
		
		}
		
		override fun onTextChanged(s : CharSequence, start : Int, before : Int, count : Int) {
		
		}
		
		override fun afterTextChanged(s : Editable) {
			saveUIToData()
			showFontSize(sample, et, default_size_sp)
		}
	}
	
	private fun formatFontSize(fv : Float) : String {
		return if(fv.isNaN()) {
			""
		} else {
			String.format(Locale.getDefault(), "%.1f", fv)
		}
	}
	
	private fun parseFontSize(src : String) : Float {
		try {
			if(! TextUtils.isEmpty(src)) {
				val f = NumberFormat.getInstance(Locale.getDefault()).parse(src).toFloat()
				return when {
					f.isNaN() -> Float.NaN
					f < 0f -> 0f
					f > 999f -> 999f
					else -> f
				}
			}
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
		return Float.NaN
	}
	
	private fun showFontSize(sample : TextView, et : EditText, default_sp : Float) {
		var fv = parseFontSize(et.text.toString().trim { it <= ' ' })
		if(fv.isNaN() ) {
			sample.textSize = default_sp
		} else {
			if(fv < 1f) fv = 1f
			sample.textSize = fv
		}
	}
	
	private fun showTimelineFont(
		tvFontUrl : TextView, font_url : String?
	) {
		try {
			if(! TextUtils.isEmpty(font_url)) {
				
				tvFontUrl.typeface = Typeface.DEFAULT
				val face = Typeface.createFromFile(font_url)
				tvFontUrl.typeface = face
				tvFontUrl.text = font_url
				return
			}
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
		// fallback
		tvFontUrl.text = getString(R.string.not_selected)
		tvFontUrl.typeface = Typeface.DEFAULT
	}
	
	private fun saveTimelineFont(uri : Uri?, file_name : String) : File? {
		try {
			if(uri == null) {
				Utils.showToast(this, false, "missing uri.")
				return null
			}
			
			contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
			
			val dir = filesDir
			
			dir.mkdir()
			
			val tmp_file = File(dir, file_name + ".tmp")
			
			val source = contentResolver.openInputStream(uri) // nullable
			if(source == null) {
				Utils.showToast(this, false, "openInputStream returns null. uri=%s", uri)
				return null
			} else {
				source.use { inStream ->
					FileOutputStream(tmp_file).use { outStream ->
						IOUtils.copy(inStream, outStream)
					}
				}
			}
			
			val face = Typeface.createFromFile(tmp_file)
			if(face == null) {
				Utils.showToast(this, false, "Typeface.createFromFile() failed.")
				return null
			}
			
			val file = File(dir, file_name)
			if(! tmp_file.renameTo(file)) {
				Utils.showToast(this, false, "File operation failed.")
				return null
			}
			
			return file
		} catch(ex : Throwable) {
			log.trace(ex)
			Utils.showToast(this, ex, "saveTimelineFont failed.")
			return null
		}
		
	}
	
	private fun exportAppData() {
		
		@Suppress("DEPRECATION")
		val progress = ProgressDialog(this)
		
		val task = @SuppressLint("StaticFieldLeak")
		object : AsyncTask<Void, String, File>() {
			
			override fun doInBackground(vararg params : Void) : File? {
				try {
					val cache_dir = cacheDir
					
					cache_dir.mkdir()
					
					val file = File(cache_dir, "SubwayTooter." + android.os.Process.myPid() + "." + android.os.Process.myTid() + ".json")
					FileWriterWithEncoding(file, "UTF-8").use { w ->
						val jw = JsonWriter(w)
						AppDataExporter.encodeAppData(this@ActAppSetting, jw)
						jw.flush()
					}
					return file
				} catch(ex : Throwable) {
					log.trace(ex)
					Utils.showToast(this@ActAppSetting, ex, "exportAppData failed.")
				}
				
				return null
			}
			
			override fun onCancelled(result : File) {
				super.onPostExecute(result)
			}
			
			override fun onPostExecute(result : File?) {
				progress.dismiss()
				
				if(isCancelled || result == null) {
					// cancelled.
					return
				}
				
				try {
					val uri = FileProvider.getUriForFile(this@ActAppSetting, App1.FILE_PROVIDER_AUTHORITY, result)
					val intent = Intent(Intent.ACTION_SEND)
					intent.type = contentResolver.getType(uri)
					intent.putExtra(Intent.EXTRA_SUBJECT, "SubwayTooter app data")
					intent.putExtra(Intent.EXTRA_STREAM, uri)
					
					intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
					startActivityForResult(intent, REQUEST_CODE_APP_DATA_EXPORT)
				} catch(ex : Throwable) {
					log.trace(ex)
					Utils.showToast(this@ActAppSetting, ex, "exportAppData failed.")
				}
				
			}
		}
		
		progress.isIndeterminate = true
		progress.setCancelable(true)
		progress.setOnCancelListener { task.cancel(true) }
		progress.show()
		task.executeOnExecutor(App1.task_executor)
	}
	
	private fun importAppData() {
		try {
			val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
			intent.addCategory(Intent.CATEGORY_OPENABLE)
			intent.type = "*/*"
			startActivityForResult(intent, REQUEST_CODE_APP_DATA_IMPORT)
		} catch(ex : Throwable) {
			Utils.showToast(this, ex, "importAppData(1) failed.")
		}
		
	}
	
	private fun importAppData(bConfirm : Boolean, uri : Uri) {
		
		val type = contentResolver.getType(uri)
		log.d("importAppData type=%s", type)
		
		if(! bConfirm) {
			AlertDialog.Builder(this)
				.setMessage(getString(R.string.app_data_import_confirm))
				.setNegativeButton(R.string.cancel, null)
				.setPositiveButton(R.string.ok) { _, _ -> importAppData(true, uri) }
				.show()
			return
		}
		
		val data = Intent()
		data.data = uri
		setResult(ActMain.RESULT_APP_DATA_IMPORT, data)
		finish()
		
	}
	
	private inner class AccountAdapter internal constructor() : BaseAdapter() {
		
		internal val list = ArrayList<SavedAccount>()
		
		init {
			for(a in SavedAccount.loadAccountList(this@ActAppSetting)) {
				if(a.isPseudo) continue
				list.add(a)
			}
			SavedAccount.sort(list)
		}
		
		override fun getCount() : Int {
			return 1 + list.size
		}
		
		override fun getItem(position : Int) : Any? {
			return if(position == 0) null else list[position - 1]
		}
		
		override fun getItemId(position : Int) : Long {
			return 0
		}
		
		override fun getView(position : Int, viewOld : View?, parent : ViewGroup) : View {
			val view = viewOld ?: layoutInflater.inflate(android.R.layout.simple_spinner_item, parent, false)
			(view.findViewById<View>(android.R.id.text1) as TextView).text =
				if(position == 0)
					getString(R.string.ask_always)
				else
					AcctColor.getNickname(list[position - 1].acct)
			return view
		}
		
		override fun getDropDownView(position : Int, viewOld : View?, parent : ViewGroup) : View {
			val view = viewOld ?: layoutInflater.inflate(R.layout.lv_spinner_dropdown, parent, false)
			(view.findViewById<View>(android.R.id.text1) as TextView).text =
				if(position == 0)
					getString(R.string.ask_always)
				else
					AcctColor.getNickname(list[position - 1].acct)
			return view
		}
		
		internal fun getIndexFromId(db_id : Long) : Int {
			var i = 0
			val ie = list.size
			while(i < ie) {
				if(list[i].db_id == db_id) return i + 1
				++ i
			}
			return 0
		}
		
		internal fun getIdFromIndex(position : Int) : Long {
			return if(position > 0) list[position - 1].db_id else - 1L
		}
	}
	
}
