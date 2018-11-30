package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.annotation.IdRes
import android.support.v4.content.FileProvider
import android.support.v4.view.ViewCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.util.JsonWriter
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.jrummyapps.android.colorpicker.ColorPickerDialog
import com.jrummyapps.android.colorpicker.ColorPickerDialogListener
import jp.juggler.subwaytooter.dialog.ProgressDialogEx
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.*
import org.apache.commons.io.IOUtils
import org.jetbrains.anko.textColor
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.NumberFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
		internal const val default_notification_tl_font_size = 14f
		
		internal const val COLOR_DIALOG_ID_FOOTER_BUTTON_BG = 1
		internal const val COLOR_DIALOG_ID_FOOTER_BUTTON_FG = 2
		internal const val COLOR_DIALOG_ID_FOOTER_TAB_BG = 3
		internal const val COLOR_DIALOG_ID_FOOTER_TAB_DIVIDER = 4
		internal const val COLOR_DIALOG_ID_FOOTER_TAB_INDICATOR = 5
		internal const val COLOR_DIALOG_ID_LIST_DIVIDER = 6
		
		internal const val COLOR_DIALOG_ID_TOOT_BG_UNLISTED = 7
		internal const val COLOR_DIALOG_ID_TOOT_BG_FOLLOWER = 8
		internal const val COLOR_DIALOG_ID_TOOT_BG_DIRECT_USER = 9
		internal const val COLOR_DIALOG_ID_TOOT_BG_DIRECT_ME = 10
		internal const val COLOR_DIALOG_ID_LINK = 11
		
		internal const val COLOR_DIALOG_ID_COLUMN_HEADER_BG = 12
		internal const val COLOR_DIALOG_ID_COLUMN_HEADER_FG = 13
		internal const val COLOR_DIALOG_ID_COLUMN_BG = 14
		internal const val COLOR_DIALOG_ID_COLUMN_ACCT = 15
		internal const val COLOR_DIALOG_ID_COLUMN_TEXT = 16
		
		internal const val REQUEST_CODE_TIMELINE_FONT = 1
		internal const val REQUEST_CODE_TIMELINE_FONT_BOLD = 2
		internal const val REQUEST_CODE_APP_DATA_EXPORT = 3
		internal const val REQUEST_CODE_APP_DATA_IMPORT = 4
		
		const val colorFF000000 : Int = (0xff shl 24)
		
		fun open(activity : ActMain, request_code : Int) {
			activity.startActivityForResult(
				Intent(activity, ActAppSetting::class.java),
				request_code
			)
		}
		
		private val reLinefeed = Regex("[\\x0d\\x0a]+")
		
	}
	
	internal lateinit var pref : SharedPreferences
	
	class BooleanViewInfo(
		val info : BooleanPref,
		val view : CompoundButton
	)
	
	private val booleanViewList = ArrayList<BooleanViewInfo>()
	
	private lateinit var spBackButtonAction : Spinner
	private lateinit var spUITheme : Spinner
	private lateinit var spResizeImage : Spinner
	private lateinit var spRefreshAfterToot : Spinner
	private lateinit var spDefaultAccount : Spinner
	private lateinit var spRepliesCount : Spinner
	private lateinit var spVisibilityStyle : Spinner
	private lateinit var spBoostButtonJustify : Spinner
	private var footer_button_bg_color : Int = 0
	private var footer_button_fg_color : Int = 0
	private var footer_tab_bg_color : Int = 0
	private var footer_tab_divider_color : Int = 0
	private var footer_tab_indicator_color : Int = 0
	private var list_divider_color : Int = 0
	private var toot_color_unlisted : Int = 0
	private var toot_color_follower : Int = 0
	private var toot_color_direct_user : Int = 0
	private var toot_color_direct_me : Int = 0
	private var link_color : Int = 0
	
	private var color_column_header_bg : Int = 0
	private var color_column_header_fg : Int = 0
	private var color_column_bg : Int = 0
	private var color_column_acct : Int = 0
	private var color_column_text : Int = 0
	
	private lateinit var ivFooterToot : ImageView
	private lateinit var ivFooterMenu : ImageView
	private lateinit var llFooterBG : View
	private lateinit var vFooterDivider1 : View
	private lateinit var vFooterDivider2 : View
	private lateinit var vIndicator : View
	
	private lateinit var etColumnWidth : EditText
	private lateinit var etMediaThumbHeight : EditText
	private lateinit var etClientName : EditText
	private lateinit var etUserAgent : EditText
	private lateinit var etQuoteNameFormat : EditText
	private lateinit var etAutoCWLines : EditText
	private lateinit var etCardDescriptionLength : EditText
	private lateinit var etMediaSizeMax : EditText
	private lateinit var etMovieSizeMax : EditText
	private lateinit var etRoundRatio : EditText
	private lateinit var etBoostAlpha : EditText
	private lateinit var etMediaReadTimeout : EditText
	
	private lateinit var tvTimelineFontUrl : TextView
	private var timeline_font : String? = null
	private lateinit var tvTimelineFontBoldUrl : TextView
	private var timeline_font_bold : String? = null
	
	private lateinit var etTimelineFontSize : EditText
	private lateinit var etAcctFontSize : EditText
	private lateinit var tvTimelineFontSize : TextView
	private lateinit var tvAcctFontSize : TextView
	private lateinit var etAvatarIconSize : EditText
	
	private lateinit var etPullNotificationCheckInterval : EditText
	
	private lateinit var etNotificationTlFontSize : EditText
	private lateinit var tvNotificationTlFontSize : TextView
	private lateinit var etNotificationTlIconSize : EditText
	
	private lateinit var etBoostButtonSize : EditText
	
	private lateinit var tvUserAgentError : TextView
	
	private lateinit var llColumnHeader : View
	private lateinit var ivColumnHeader : ImageView
	private lateinit var tvColumnName : TextView
	private lateinit var flColumnBackground : View
	private lateinit var tvSampleAcct : TextView
	private lateinit var tvSampleContent : TextView
	
	private var load_busy : Boolean = false
	
	override fun onPause() {
		super.onPause()
		
		// DefaultAccount の Spinnerの値を復元するため、このタイミングでも保存することになった
		saveUIToData()
		
		// Pull通知チェック間隔を変更したかもしれないのでジョブを再設定する
		try {
			PollingWorker.scheduleJob(this, PollingWorker.JOB_POLLING)
		} catch(ex : Throwable) {
			log.trace(ex, "PollingWorker.scheduleJob failed.")
		}
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
		
		// initialize Switch and CheckBox
		for(info in Pref.map.values) {
			if(info is BooleanPref && info.id != 0) {
				val view = findViewById<CompoundButton>(info.id)
				view.setOnCheckedChangeListener(this)
				booleanViewList.add(BooleanViewInfo(info, view))
			}
		}
		
		val bBefore8 = Build.VERSION.SDK_INT < 26
		for(si in booleanViewList) {
			when(si.info) {
				Pref.bpNotificationLED,
				Pref.bpNotificationVibration,
				Pref.bpNotificationSound -> si.view.isEnabled = bBefore8
			}
		}
		
		spBackButtonAction = initSpinner(
			R.id.spBackButtonAction
			, getString(R.string.ask_always)
			, getString(R.string.close_column)
			, getString(R.string.open_column_list)
			, getString(R.string.app_exit)
		)
		
		spRepliesCount = initSpinner(
			R.id.spRepliesCount
			, getString(R.string.replies_count_simple)
			, getString(R.string.replies_count_actual)
			, getString(R.string.replies_count_none)
		)
		
		spVisibilityStyle = initSpinner(
			R.id.spVisibilityStyle
			, getString(R.string.visibility_style_by_account)
			, getString(R.string.mastodon)
			, getString(R.string.misskey)
		)
		spBoostButtonJustify = initSpinner(
			R.id.spBoostButtonJustify
			, getString(R.string.start)
			, getString(R.string.center)
			, getString(R.string.end)
		)
		spUITheme = initSpinner(
			R.id.spUITheme
			, getString(R.string.theme_light)
			, getString(R.string.theme_dark)
		)
		
		spResizeImage = initSpinner(
			R.id.spResizeImage
			, getString(R.string.dont_resize)
			, getString(R.string.long_side_pixel, 640)
			, getString(R.string.long_side_pixel, 800)
			, getString(R.string.long_side_pixel, 1024)
			, getString(R.string.long_side_pixel, 1280)
			// サーバ側でさらに縮小されるようなので、1280より上は用意しない
		)
		
		spRefreshAfterToot = initSpinner(
			R.id.spRefreshAfterToot
			, getString(R.string.refresh_scroll_to_toot)
			, getString(R.string.refresh_no_scroll)
			, getString(R.string.dont_refresh)
		)
		
		spDefaultAccount = findViewById<Spinner>(R.id.spDefaultAccount).also {
			it.adapter = AccountAdapter()
			it.onItemSelectedListener = this@ActAppSetting
		}
		
		
		intArrayOf(
			R.id.btnFooterBackgroundEdit
			, R.id.btnFooterBackgroundReset
			, R.id.btnFooterForegroundColorEdit
			, R.id.btnFooterForegroundColorReset
			, R.id.btnTabBackgroundColorEdit
			, R.id.btnTabBackgroundColorReset
			, R.id.btnTabDividerColorEdit
			, R.id.btnTabDividerColorReset
			, R.id.btnTabIndicatorColorEdit
			, R.id.btnTabIndicatorColorReset
			, R.id.btnListDividerColorEdit
			, R.id.btnListDividerColorReset
			, R.id.btnBackgroundColorUnlistedEdit
			, R.id.btnBackgroundColorUnlistedReset
			, R.id.btnBackgroundColorFollowerEdit
			, R.id.btnBackgroundColorFollowerReset
			, R.id.btnBackgroundColorDirectWithUserEdit
			, R.id.btnBackgroundColorDirectWithUserReset
			, R.id.btnBackgroundColorDirectNoUserEdit
			, R.id.btnBackgroundColorDirectNoUserReset
			, R.id.btnLinkColorEdit
			, R.id.btnLinkColorReset
			, R.id.btnTimelineFontEdit
			, R.id.btnTimelineFontReset
			, R.id.btnTimelineFontBoldEdit
			, R.id.btnTimelineFontBoldReset
			, R.id.btnSettingExport
			, R.id.btnSettingImport
			, R.id.btnCustomStreamListenerEdit
			, R.id.btnCustomStreamListenerReset
			, R.id.btnCcdHeaderBackgroundEdit
			, R.id.btnCcdHeaderBackgroundReset
			, R.id.btnCcdHeaderForegroundEdit
			, R.id.btnCcdHeaderForegroundReset
			, R.id.btnCcdContentBackgroundEdit
			, R.id.btnCcdContentBackgroundReset
			, R.id.btnCcdContentAcctEdit
			, R.id.btnCcdContentAcctReset
			, R.id.btnCcdContentTextEdit
			, R.id.btnCcdContentTextReset
		).forEach {
			findViewById<View>(it).setOnClickListener(this)
		}
		
		
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
		
		etUserAgent = findViewById(R.id.etUserAgent)
		etUserAgent.addTextChangedListener(this)
		
		etQuoteNameFormat = findViewById(R.id.etQuoteNameFormat)
		etQuoteNameFormat.addTextChangedListener(this)
		
		etAutoCWLines = findViewById(R.id.etAutoCWLines)
		etAutoCWLines.addTextChangedListener(this)
		
		etCardDescriptionLength = findViewById(R.id.etCardDescriptionLength)
		etCardDescriptionLength.addTextChangedListener(this)
		
		etMediaSizeMax = findViewById(R.id.etMediaSizeMax)
		etMediaSizeMax.addTextChangedListener(this)
		
		etMovieSizeMax = findViewById(R.id.etMovieSizeMax)
		etMovieSizeMax.addTextChangedListener(this)
		
		etRoundRatio = findViewById(R.id.etRoundRatio)
		etRoundRatio.addTextChangedListener(this)
		
		etBoostAlpha = findViewById(R.id.etBoostAlpha)
		etBoostAlpha.addTextChangedListener(this)
		
		etMediaReadTimeout = findViewById(R.id.etMediaReadTimeout)
		etMediaReadTimeout.addTextChangedListener(this)
		
		tvTimelineFontSize = findViewById(R.id.tvTimelineFontSize)
		tvAcctFontSize = findViewById(R.id.tvAcctFontSize)
		tvNotificationTlFontSize = findViewById(R.id.tvNotificationTlFontSize)
		
		etTimelineFontSize = findViewById(R.id.etTimelineFontSize)
		etTimelineFontSize.addTextChangedListener(
			SizeCheckTextWatcher(
				tvTimelineFontSize,
				etTimelineFontSize,
				default_timeline_font_size
			)
		)
		
		etAcctFontSize = findViewById(R.id.etAcctFontSize)
		etAcctFontSize.addTextChangedListener(
			SizeCheckTextWatcher(
				tvAcctFontSize,
				etAcctFontSize,
				default_acct_font_size
			)
		)
		
		etNotificationTlFontSize = findViewById(R.id.etNotificationTlFontSize)
		etNotificationTlFontSize.addTextChangedListener(
			SizeCheckTextWatcher(
				tvNotificationTlFontSize,
				etNotificationTlFontSize,
				default_notification_tl_font_size
			)
		)
		
		etAvatarIconSize = findViewById(R.id.etAvatarIconSize)
		etNotificationTlIconSize = findViewById(R.id.etNotificationTlIconSize)
		etPullNotificationCheckInterval = findViewById(R.id.etPullNotificationCheckInterval)
		
		etBoostButtonSize = findViewById(R.id.etBoostButtonSize)
		
		tvTimelineFontUrl = findViewById(R.id.tvTimelineFontUrl)
		tvTimelineFontBoldUrl = findViewById(R.id.tvTimelineFontBoldUrl)
		
		
		tvUserAgentError = findViewById(R.id.tvUserAgentError)
		
		llColumnHeader = findViewById(R.id.llColumnHeader)
		ivColumnHeader = findViewById(R.id.ivColumnHeader)
		tvColumnName = findViewById(R.id.tvColumnName)
		flColumnBackground = findViewById(R.id.flColumnBackground)
		tvSampleAcct = findViewById(R.id.tvSampleAcct)
		tvSampleContent = findViewById(R.id.tvSampleContent)
		
	}
	
	private fun initSpinner(@IdRes viewId : Int, vararg captions : String) : Spinner {
		val caption_list : Array<String> = arrayOf(*captions)
		val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, caption_list)
		adapter.setDropDownViewResource(R.layout.lv_spinner_dropdown)
		val sp : Spinner = findViewById(viewId)
		sp.adapter = adapter
		sp.onItemSelectedListener = this
		return sp
	}
	
	private fun loadUIFromData() {
		load_busy = true
		
		for(si in booleanViewList) {
			si.view.isChecked = si.info(pref)
		}
		
		spBackButtonAction.setSelection(Pref.ipBackButtonAction(pref))
		spRepliesCount.setSelection(Pref.ipRepliesCount(pref))
		spVisibilityStyle.setSelection(Pref.ipVisibilityStyle(pref))
		spBoostButtonJustify.setSelection(Pref.ipBoostButtonJustify(pref))
		spUITheme.setSelection(Pref.ipUiTheme(pref))
		spResizeImage.setSelection(Pref.ipResizeImage(pref))
		spRefreshAfterToot.setSelection(Pref.ipRefreshAfterToot(pref))
		
		
		spDefaultAccount.setSelection(
			(spDefaultAccount.adapter as AccountAdapter).getIndexFromId(
				Pref.lpTabletTootDefaultAccount(
					pref
				)
			)
		)
		
		footer_button_bg_color = Pref.ipFooterButtonBgColor(pref)
		footer_button_fg_color = Pref.ipFooterButtonFgColor(pref)
		footer_tab_bg_color = Pref.ipFooterTabBgColor(pref)
		footer_tab_divider_color = Pref.ipFooterTabDividerColor(pref)
		footer_tab_indicator_color = Pref.ipFooterTabIndicatorColor(pref)
		list_divider_color = Pref.ipListDividerColor(pref)
		toot_color_unlisted = Pref.ipTootColorUnlisted(pref)
		toot_color_follower = Pref.ipTootColorFollower(pref)
		toot_color_direct_user = Pref.ipTootColorDirectUser(pref)
		toot_color_direct_me = Pref.ipTootColorDirectMe(pref)
		link_color = Pref.ipLinkColor(pref)
		
		color_column_header_bg = Pref.ipCcdHeaderBg(pref)
		color_column_header_fg = Pref.ipCcdHeaderFg(pref)
		color_column_bg = Pref.ipCcdContentBg(pref)
		color_column_acct = Pref.ipCcdContentAcct(pref)
		color_column_text = Pref.ipCcdContentText(pref)
		
		etColumnWidth.setText(Pref.spColumnWidth(pref))
		etMediaThumbHeight.setText(Pref.spMediaThumbHeight(pref))
		etClientName.setText(Pref.spClientName(pref))
		etUserAgent.setText(Pref.spUserAgent(pref))
		etQuoteNameFormat.setText(Pref.spQuoteNameFormat(pref))
		etAutoCWLines.setText(Pref.spAutoCWLines(pref))
		etCardDescriptionLength.setText(Pref.spCardDescriptionLength(pref))
		etAvatarIconSize.setText(Pref.spAvatarIconSize(pref))
		etNotificationTlIconSize.setText(Pref.spNotificationTlIconSize(pref))
		etBoostButtonSize.setText(Pref.spBoostButtonSize(pref))
		etPullNotificationCheckInterval.setText(Pref.spPullNotificationCheckInterval(pref))
		
		etMediaSizeMax.setText(Pref.spMediaSizeMax(pref))
		etMovieSizeMax.setText(Pref.spMovieSizeMax(pref))
		etRoundRatio.setText(Pref.spRoundRatio(pref))
		etBoostAlpha.setText(Pref.spBoostAlpha(pref))
		
		etMediaReadTimeout.setText(Pref.spMediaReadTimeout(pref))
		
		timeline_font = Pref.spTimelineFont(pref)
		timeline_font_bold = Pref.spTimelineFontBold(pref)
		
		etTimelineFontSize.setText(formatFontSize(Pref.fpTimelineFontSize(pref)))
		etAcctFontSize.setText(formatFontSize(Pref.fpAcctFontSize(pref)))
		etNotificationTlFontSize.setText(formatFontSize(Pref.fpNotificationTlFontSize(pref)))
		
		etUserAgent.hint = App1.userAgentDefault
		
		load_busy = false
		
		showFooterColor()
		showTimelineFont(tvTimelineFontUrl, timeline_font)
		showTimelineFont(tvTimelineFontBoldUrl, timeline_font_bold)
		
		showFontSize(tvTimelineFontSize, etTimelineFontSize, default_timeline_font_size)
		showFontSize(tvAcctFontSize, etAcctFontSize, default_acct_font_size)
		showFontSize(
			tvNotificationTlFontSize,
			etNotificationTlFontSize,
			default_notification_tl_font_size
		)
		
		showUserAgentError()
		showColumnSample()
		showColumnHeaderSample()
	}
	
	private fun saveUIToData() {
		if(load_busy) return
		
		val e = pref.edit()
		
		for(si in booleanViewList) {
			e.putBoolean(si.info.key, si.view.isChecked)
		}
		
		e
			.put(
				Pref.lpTabletTootDefaultAccount,
				(spDefaultAccount.adapter as AccountAdapter)
					.getIdFromIndex(spDefaultAccount.selectedItemPosition)
			)
			
			.put(
				Pref.fpTimelineFontSize,
				parseFontSize(etTimelineFontSize.text.toString().trim { it <= ' ' })
			)
			.put(
				Pref.fpAcctFontSize,
				parseFontSize(etAcctFontSize.text.toString().trim { it <= ' ' })
			)
			.put(
				Pref.fpNotificationTlFontSize,
				parseFontSize(etNotificationTlFontSize.text.toString().trim { it <= ' ' })
			)
			
			.put(Pref.spColumnWidth, etColumnWidth.text.toString().trim { it <= ' ' })
			.put(Pref.spMediaThumbHeight, etMediaThumbHeight.text.toString().trim { it <= ' ' })
			.put(Pref.spClientName, etClientName.text.toString().trim { it <= ' ' })
			.put(
				Pref.spUserAgent,
				etUserAgent.text.toString().replace(reLinefeed, " ").trim { it <= ' ' })
			.put(Pref.spQuoteNameFormat, etQuoteNameFormat.text.toString()) // not trimmed
			.put(Pref.spAutoCWLines, etAutoCWLines.text.toString().trim { it <= ' ' })
			.put(
				Pref.spCardDescriptionLength,
				etCardDescriptionLength.text.toString().trim { it <= ' ' })
			.put(Pref.spAvatarIconSize, etAvatarIconSize.text.toString().trim { it <= ' ' })
			.put(
				Pref.spNotificationTlIconSize,
				etNotificationTlIconSize.text.toString().trim { it <= ' ' })
			.put(
				Pref.spBoostButtonSize,
				etBoostButtonSize.text.toString().trim { it <= ' ' })
			.put(
				Pref.spPullNotificationCheckInterval,
				etPullNotificationCheckInterval.text.toString().trim { it <= ' ' })
			.put(Pref.spMediaSizeMax, etMediaSizeMax.text.toString().trim { it <= ' ' })
			.put(Pref.spMovieSizeMax, etMovieSizeMax.text.toString().trim { it <= ' ' })
			
			.put(Pref.spRoundRatio, etRoundRatio.text.toString().trim { it <= ' ' })
			.put(Pref.spBoostAlpha, etBoostAlpha.text.toString().trim { it <= ' ' })
			
			.put(Pref.spMediaReadTimeout, etMediaReadTimeout.text.toString().trim { it <= ' ' })
			
			.put(Pref.spTimelineFont, timeline_font ?: "")
			.put(Pref.spTimelineFontBold, timeline_font_bold ?: "")
			
			.put(Pref.ipBackButtonAction, spBackButtonAction.selectedItemPosition)
			.put(Pref.ipRepliesCount, spRepliesCount.selectedItemPosition)
			.put(Pref.ipVisibilityStyle, spVisibilityStyle.selectedItemPosition)
			.put(Pref.ipBoostButtonJustify, spBoostButtonJustify.selectedItemPosition)
			.put(Pref.ipUiTheme, spUITheme.selectedItemPosition)
			.put(Pref.ipResizeImage, spResizeImage.selectedItemPosition)
			.put(Pref.ipRefreshAfterToot, spRefreshAfterToot.selectedItemPosition)
			.put(Pref.ipFooterButtonBgColor, footer_button_bg_color)
			.put(Pref.ipFooterButtonFgColor, footer_button_fg_color)
			.put(Pref.ipFooterTabBgColor, footer_tab_bg_color)
			.put(Pref.ipFooterTabDividerColor, footer_tab_divider_color)
			.put(Pref.ipFooterTabIndicatorColor, footer_tab_indicator_color)
			.put(Pref.ipListDividerColor, list_divider_color)
			
			.put(Pref.ipTootColorUnlisted, toot_color_unlisted)
			.put(Pref.ipTootColorFollower, toot_color_follower)
			.put(Pref.ipTootColorDirectUser, toot_color_direct_user)
			.put(Pref.ipTootColorDirectMe, toot_color_direct_me)
			
			.put(Pref.ipLinkColor, link_color)
			
			.put(Pref.ipCcdHeaderBg, color_column_header_bg)
			.put(Pref.ipCcdHeaderFg, color_column_header_fg)
			.put(Pref.ipCcdContentBg, color_column_bg)
			.put(Pref.ipCcdContentAcct, color_column_acct)
			.put(Pref.ipCcdContentText, color_column_text)
			
			.apply()
		
		showUserAgentError()
		
	}
	
	private fun showUserAgentError() {
		val m = App1.reNotAllowedInUserAgent.matcher(etUserAgent.text.toString())
		tvUserAgentError.text = when(m.find()) {
			true -> getString(R.string.user_agent_error, m.group())
			else -> ""
		}
	}
	
	private fun showColumnHeaderSample() {
		val header_bg = when {
			color_column_header_bg != 0 -> color_column_header_bg
			else -> getAttributeColor(this, R.attr.color_column_header)
		}
		
		val header_fg = when {
			color_column_header_fg != 0 -> color_column_header_fg
			else -> getAttributeColor(this, R.attr.colorColumnHeaderName)
		}
		
		ViewCompat.setBackground(
			llColumnHeader,
			getAdaptiveRippleDrawable(header_bg, header_fg)
		)
		
		tvColumnName.textColor = header_fg
		setIconAttr(
			this,
			ivColumnHeader,
			R.attr.btn_federate_tl,
			color = header_fg
		)
	}
	
	private fun showColumnSample() {
		
		var c = when {
			color_column_bg != 0 -> color_column_bg
			else -> 0
		}
		flColumnBackground.setBackgroundColor(c)
		
		c = when {
			color_column_acct != 0 -> color_column_acct
			else -> getAttributeColor(this, R.attr.colorTimeSmall)
		}
		tvSampleAcct.setTextColor(c)
		
		c = when {
			color_column_text != 0 -> color_column_text
			else -> getAttributeColor(this, R.attr.colorContentText)
		}
		tvSampleContent.setTextColor(c)
		
	}
	
	override fun onCheckedChanged(buttonView : CompoundButton, isChecked : Boolean) {
		saveUIToData()
	}
	
	override fun onItemSelected(parent : AdapterView<*>, view : View?, position : Int, id : Long) {
		// view may null.
		saveUIToData()
	}
	
	override fun onNothingSelected(parent : AdapterView<*>) {}
	
	override fun onClick(v : View) {
		when(v.id) {
			
			R.id.btnFooterBackgroundEdit -> openColorPicker(
				COLOR_DIALOG_ID_FOOTER_BUTTON_BG,
				footer_button_bg_color,
				false
			)
			
			R.id.btnFooterBackgroundReset -> {
				footer_button_bg_color = 0
				saveUIToData()
				showFooterColor()
			}
			
			R.id.btnFooterForegroundColorEdit -> openColorPicker(
				COLOR_DIALOG_ID_FOOTER_BUTTON_FG,
				footer_button_fg_color,
				false
			)
			
			R.id.btnFooterForegroundColorReset -> {
				footer_button_fg_color = 0
				saveUIToData()
				showFooterColor()
			}
			
			R.id.btnTabBackgroundColorEdit -> openColorPicker(
				COLOR_DIALOG_ID_FOOTER_TAB_BG,
				footer_tab_bg_color,
				false
			)
			
			R.id.btnTabBackgroundColorReset -> {
				footer_tab_bg_color = 0
				saveUIToData()
				showFooterColor()
			}
			
			R.id.btnTabDividerColorEdit -> openColorPicker(
				COLOR_DIALOG_ID_FOOTER_TAB_DIVIDER,
				footer_tab_divider_color,
				false
			)
			
			R.id.btnTabDividerColorReset -> {
				footer_tab_divider_color = 0
				saveUIToData()
				showFooterColor()
			}
			
			R.id.btnTabIndicatorColorEdit -> openColorPicker(
				COLOR_DIALOG_ID_FOOTER_TAB_INDICATOR,
				footer_tab_indicator_color,
				true
			)
			
			R.id.btnTabIndicatorColorReset -> {
				footer_tab_indicator_color = 0
				saveUIToData()
				showFooterColor()
			}
			
			R.id.btnListDividerColorEdit -> openColorPicker(
				COLOR_DIALOG_ID_LIST_DIVIDER,
				list_divider_color,
				true
			)
			
			R.id.btnListDividerColorReset -> {
				list_divider_color = 0
				saveUIToData()
			}
			
			R.id.btnBackgroundColorUnlistedEdit -> openColorPicker(
				COLOR_DIALOG_ID_TOOT_BG_UNLISTED,
				toot_color_unlisted,
				true
			)
			
			R.id.btnBackgroundColorUnlistedReset -> {
				toot_color_unlisted = 0
				saveUIToData()
			}
			
			R.id.btnBackgroundColorFollowerEdit -> openColorPicker(
				COLOR_DIALOG_ID_TOOT_BG_FOLLOWER,
				toot_color_follower,
				true
			)
			
			R.id.btnBackgroundColorFollowerReset -> {
				toot_color_follower = 0
				saveUIToData()
			}
			
			R.id.btnBackgroundColorDirectWithUserEdit -> openColorPicker(
				COLOR_DIALOG_ID_TOOT_BG_DIRECT_USER,
				toot_color_direct_user,
				true
			)
			
			R.id.btnBackgroundColorDirectWithUserReset -> {
				toot_color_direct_user = 0
				saveUIToData()
			}
			
			R.id.btnBackgroundColorDirectNoUserEdit -> openColorPicker(
				COLOR_DIALOG_ID_TOOT_BG_DIRECT_ME,
				toot_color_direct_me,
				true
			)
			
			R.id.btnBackgroundColorDirectNoUserReset -> {
				toot_color_direct_me = 0
				saveUIToData()
			}
			
			R.id.btnLinkColorEdit -> openColorPicker(
				COLOR_DIALOG_ID_LINK,
				link_color,
				true
			)
			
			R.id.btnLinkColorReset -> {
				link_color = 0
				saveUIToData()
			}
			
			R.id.btnCcdHeaderBackgroundEdit -> openColorPicker(
				COLOR_DIALOG_ID_COLUMN_HEADER_BG,
				color_column_header_bg,
				false
			)
			
			R.id.btnCcdHeaderBackgroundReset -> {
				color_column_header_bg = 0
				saveUIToData()
				showColumnHeaderSample()
			}
			
			R.id.btnCcdHeaderForegroundEdit -> openColorPicker(
				COLOR_DIALOG_ID_COLUMN_HEADER_FG,
				color_column_header_fg,
				false
			)
			
			R.id.btnCcdHeaderForegroundReset -> {
				color_column_header_fg = 0
				saveUIToData()
				showColumnHeaderSample()
			}
			
			R.id.btnCcdContentBackgroundEdit -> openColorPicker(
				COLOR_DIALOG_ID_COLUMN_BG,
				color_column_bg,
				false
			)
			
			R.id.btnCcdContentBackgroundReset -> {
				color_column_bg = 0
				saveUIToData()
				showColumnSample()
			}
			
			R.id.btnCcdContentAcctEdit -> openColorPicker(
				COLOR_DIALOG_ID_COLUMN_ACCT,
				color_column_acct,
				true
			)
			
			R.id.btnCcdContentAcctReset -> {
				color_column_acct = 0
				saveUIToData()
				showColumnSample()
			}
			
			R.id.btnCcdContentTextEdit -> openColorPicker(
				COLOR_DIALOG_ID_COLUMN_TEXT,
				color_column_text,
				true
			)
			
			R.id.btnCcdContentTextReset -> {
				color_column_text = 0
				saveUIToData()
				showColumnSample()
			}
			
			R.id.btnTimelineFontReset -> {
				timeline_font = ""
				saveUIToData()
				showTimelineFont(tvTimelineFontUrl, timeline_font)
			}
			
			R.id.btnTimelineFontEdit -> try {
				val intent = intentOpenDocument("*/*")
				startActivityForResult(intent, REQUEST_CODE_TIMELINE_FONT)
			} catch(ex : Throwable) {
				showToast(this, ex, "could not open picker for font")
			}
			
			R.id.btnTimelineFontBoldReset -> {
				timeline_font_bold = ""
				saveUIToData()
				showTimelineFont(tvTimelineFontBoldUrl, timeline_font_bold)
			}
			
			R.id.btnTimelineFontBoldEdit -> try {
				val intent = intentOpenDocument("*/*")
				startActivityForResult(intent, REQUEST_CODE_TIMELINE_FONT_BOLD)
			} catch(ex : Throwable) {
				showToast(this, ex, "could not open picker for font")
			}
			
			R.id.btnSettingExport -> exportAppData()
			
			R.id.btnSettingImport -> importAppData()
			
			R.id.btnCustomStreamListenerEdit -> ActCustomStreamListener.open(this)
			
			R.id.btnCustomStreamListenerReset -> {
				pref.edit()
					.remove(Pref.spStreamListenerConfigUrl)
					.remove(Pref.spStreamListenerSecret)
					.remove(Pref.spStreamListenerConfigData)
					.apply()
				SavedAccount.clearRegistrationCache()
				PollingWorker.queueUpdateListener(this)
				showToast(this, false, getString(R.string.custom_stream_listener_was_reset))
			}
		}
	}
	
	override fun onActivityResult(requestCode : Int, resultCode : Int, data : Intent?) {
		if(resultCode == RESULT_OK && data != null && requestCode == REQUEST_CODE_TIMELINE_FONT) {
			data.handleGetContentResult(contentResolver).firstOrNull()?.uri?.let {
				val file = saveTimelineFont(it, "TimelineFont")
				if(file != null) {
					timeline_font = file.absolutePath
					saveUIToData()
					showTimelineFont(tvTimelineFontUrl, timeline_font)
				}
			}
		} else if(resultCode == RESULT_OK && data != null && requestCode == REQUEST_CODE_TIMELINE_FONT_BOLD) {
			data.handleGetContentResult(contentResolver).firstOrNull()?.uri?.let {
				val file = saveTimelineFont(it, "TimelineFontBold")
				if(file != null) {
					timeline_font_bold = file.absolutePath
					saveUIToData()
					showTimelineFont(tvTimelineFontBoldUrl, timeline_font_bold)
				}
			}
		} else if(resultCode == RESULT_OK && data != null && requestCode == REQUEST_CODE_APP_DATA_IMPORT) {
			data.handleGetContentResult(contentResolver).firstOrNull()?.uri?.let {
				importAppData(false, it)
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
		val colorOpaque = colorFF000000 or colorSelected
		val colorAlpha = if(colorSelected == 0) 0x01000000 else colorSelected
		when(dialogId) {
			
			COLOR_DIALOG_ID_FOOTER_BUTTON_BG -> {
				footer_button_bg_color = colorOpaque
				saveUIToData()
				showFooterColor()
			}
			
			COLOR_DIALOG_ID_FOOTER_BUTTON_FG -> {
				footer_button_fg_color = colorOpaque
				saveUIToData()
				showFooterColor()
			}
			
			COLOR_DIALOG_ID_FOOTER_TAB_BG -> {
				footer_tab_bg_color = colorOpaque
				saveUIToData()
				showFooterColor()
			}
			
			COLOR_DIALOG_ID_FOOTER_TAB_DIVIDER -> {
				footer_tab_divider_color = colorOpaque
				saveUIToData()
				showFooterColor()
			}
			
			COLOR_DIALOG_ID_FOOTER_TAB_INDICATOR -> {
				footer_tab_indicator_color = colorAlpha
				saveUIToData()
				showFooterColor()
			}
			
			COLOR_DIALOG_ID_LIST_DIVIDER -> {
				list_divider_color = colorAlpha
				saveUIToData()
			}
			
			COLOR_DIALOG_ID_TOOT_BG_UNLISTED -> {
				toot_color_unlisted = colorAlpha
				saveUIToData()
			}
			
			COLOR_DIALOG_ID_TOOT_BG_FOLLOWER -> {
				toot_color_follower = colorAlpha
				saveUIToData()
			}
			
			COLOR_DIALOG_ID_TOOT_BG_DIRECT_USER -> {
				toot_color_direct_user = colorAlpha
				saveUIToData()
			}
			
			COLOR_DIALOG_ID_TOOT_BG_DIRECT_ME -> {
				toot_color_direct_me = colorAlpha
				saveUIToData()
			}
			
			COLOR_DIALOG_ID_LINK -> {
				link_color = colorAlpha
				saveUIToData()
			}
			
			COLOR_DIALOG_ID_COLUMN_HEADER_BG -> {
				color_column_header_bg = colorOpaque
				saveUIToData()
				showColumnHeaderSample()
			}
			
			COLOR_DIALOG_ID_COLUMN_HEADER_FG -> {
				color_column_header_fg = colorOpaque
				saveUIToData()
				showColumnHeaderSample()
			}
			
			COLOR_DIALOG_ID_COLUMN_BG -> {
				color_column_bg = colorOpaque
				saveUIToData()
				showColumnSample()
			}
			
			COLOR_DIALOG_ID_COLUMN_ACCT -> {
				color_column_acct = colorAlpha
				saveUIToData()
				showColumnSample()
			}
			
			COLOR_DIALOG_ID_COLUMN_TEXT -> {
				color_column_text = colorAlpha
				saveUIToData()
				showColumnSample()
			}
		}
	}
	
	override fun onDialogDismissed(dialogId : Int) {}
	
	private fun showFooterColor() {
		
		var c = footer_button_bg_color
		if(c == 0) {
			ivFooterToot.setBackgroundResource(R.drawable.bg_button_cw)
			ivFooterMenu.setBackgroundResource(R.drawable.bg_button_cw)
		} else {
			val fg = if(footer_button_fg_color != 0)
				footer_button_fg_color
			else
				getAttributeColor(this, R.attr.colorRippleEffect)
			ViewCompat.setBackground(ivFooterToot, getAdaptiveRippleDrawable(c, fg))
			ViewCompat.setBackground(ivFooterMenu, getAdaptiveRippleDrawable(c, fg))
		}
		
		c = footer_button_fg_color
		if(c == 0) {
			setIconAttr(this, ivFooterToot, R.attr.ic_edit)
			setIconAttr(this, ivFooterMenu, R.attr.ic_hamburger)
		} else {
			setIconAttr(this, ivFooterToot, R.attr.ic_edit, color = c)
			setIconAttr(this, ivFooterMenu, R.attr.ic_hamburger, color = c)
		}
		
		c = footer_tab_bg_color
		if(c == 0) {
			llFooterBG.setBackgroundColor(
				getAttributeColor(
					this,
					R.attr.colorColumnStripBackground
				)
			)
		} else {
			llFooterBG.setBackgroundColor(c)
		}
		
		c = footer_tab_divider_color
		if(c == 0) {
			vFooterDivider1.setBackgroundColor(
				getAttributeColor(this,R.attr.colorImageButton)
			)
			vFooterDivider2.setBackgroundColor(
				getAttributeColor(this,R.attr.colorImageButton)
			)
		} else {
			vFooterDivider1.setBackgroundColor(c)
			vFooterDivider2.setBackgroundColor(c)
		}
		
		c = footer_tab_indicator_color
		if(c == 0) {
			vIndicator.setBackgroundColor(getAttributeColor(this, R.attr.colorAccent))
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
	
	private inner class SizeCheckTextWatcher internal constructor(
		internal val sample : TextView,
		internal val et : EditText,
		internal val default_size_sp : Float
	) : TextWatcher {
		
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
			if(src.isNotEmpty()) {
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
		if(fv.isNaN()) {
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
			if(font_url?.isNotEmpty() == true) {
				
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
				showToast(this, false, "missing uri.")
				return null
			}
			
			contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
			
			val dir = filesDir
			
			dir.mkdir()
			
			val tmp_file = File(dir, "$file_name.tmp")
			
			val source = contentResolver.openInputStream(uri) // nullable
			if(source == null) {
				showToast(this, false, "openInputStream returns null. uri=%s", uri)
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
				showToast(this, false, "Typeface.createFromFile() failed.")
				return null
			}
			
			val file = File(dir, file_name)
			if(! tmp_file.renameTo(file)) {
				showToast(this, false, "File operation failed.")
				return null
			}
			
			return file
		} catch(ex : Throwable) {
			log.trace(ex)
			showToast(this, ex, "saveTimelineFont failed.")
			return null
		}
		
	}
	
	private fun exportAppData() {
		
		@Suppress("DEPRECATION")
		val progress = ProgressDialogEx(this)
		
		val task = @SuppressLint("StaticFieldLeak")
		object : AsyncTask<Void, String, File?>() {
			
			override fun doInBackground(vararg params : Void) : File? {
				try {
					val cache_dir = cacheDir
					cache_dir.mkdir()
					
					val file = File(
						cache_dir,
						"SubwayTooter.${android.os.Process.myPid()}.${android.os.Process.myTid()}.zip"
					)
					
					// ZipOutputStreamオブジェクトの作成
					ZipOutputStream(FileOutputStream(file)).use { zipStream ->
						
						// アプリデータjson
						zipStream.putNextEntry(ZipEntry("AppData.json"))
						try {
							val jw = JsonWriter(OutputStreamWriter(zipStream, "UTF-8"))
							AppDataExporter.encodeAppData(this@ActAppSetting, jw)
							jw.flush()
						} finally {
							zipStream.closeEntry()
						}
						
						// カラム背景画像
						val appState = App1.getAppState(this@ActAppSetting)
						for(column in appState.column_list) {
							AppDataExporter.saveBackgroundImage(
								this@ActAppSetting,
								zipStream,
								column
							)
						}
					}
					
					return file
				} catch(ex : Throwable) {
					log.trace(ex)
					showToast(this@ActAppSetting, ex, "exportAppData failed.")
				}
				
				return null
			}
			
			override fun onCancelled(result : File?) {
				onPostExecute(result)
			}
			
			override fun onPostExecute(result : File?) {
				progress.dismiss()
				
				if(isCancelled || result == null) {
					// cancelled.
					return
				}
				
				try {
					val uri = FileProvider.getUriForFile(
						this@ActAppSetting,
						App1.FILE_PROVIDER_AUTHORITY,
						result
					)
					val intent = Intent(Intent.ACTION_SEND)
					intent.type = contentResolver.getType(uri)
					intent.putExtra(Intent.EXTRA_SUBJECT, "SubwayTooter app data")
					intent.putExtra(Intent.EXTRA_STREAM, uri)
					
					intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
					startActivityForResult(intent, REQUEST_CODE_APP_DATA_EXPORT)
				} catch(ex : Throwable) {
					log.trace(ex)
					showToast(this@ActAppSetting, ex, "exportAppData failed.")
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
			val intent = intentOpenDocument("*/*")
			startActivityForResult(intent, REQUEST_CODE_APP_DATA_IMPORT)
		} catch(ex : Throwable) {
			showToast(this, ex, "importAppData(1) failed.")
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
			val view = viewOld ?: layoutInflater.inflate(
				android.R.layout.simple_spinner_item,
				parent,
				false
			)
			view.findViewById<TextView>(android.R.id.text1).text =
				if(position == 0)
					getString(R.string.ask_always)
				else
					AcctColor.getNickname(list[position - 1].acct)
			return view
		}
		
		override fun getDropDownView(position : Int, viewOld : View?, parent : ViewGroup) : View {
			val view =
				viewOld ?: layoutInflater.inflate(R.layout.lv_spinner_dropdown, parent, false)
			view.findViewById<TextView>(android.R.id.text1).text =
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
