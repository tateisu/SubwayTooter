package jp.juggler.subwaytooter

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.annotation.IdRes
import android.support.v4.view.ViewCompat
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.jrummyapps.android.colorpicker.ColorPickerDialog
import com.jrummyapps.android.colorpicker.ColorPickerDialogListener
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.*
import org.apache.commons.io.IOUtils
import org.jetbrains.anko.textColor
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.*

class ActAppSettingChild : AppCompatActivity()
	, CompoundButton.OnCheckedChangeListener
	, AdapterView.OnItemSelectedListener
	, View.OnClickListener
	, ColorPickerDialogListener
	, TextWatcher {
	
	companion object {
		internal val log = LogCategory("ActAppSettingChild")
		
		private const val EXTRA_LAYOUT_ID = "layoutId"
		private const val EXTRA_TITLE_ID = "titleId"
		
		fun open(activity : AppCompatActivity, requestCode : Int, layoutId : Int, titleId : Int) {
			activity.startActivityForResult(
				Intent(activity, ActAppSettingChild::class.java).apply {
					putExtra(EXTRA_LAYOUT_ID, layoutId)
					putExtra(EXTRA_TITLE_ID, titleId)
				},
				requestCode
			)
		}
		
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
		
		
		private val reLinefeed = Regex("[\\x0d\\x0a]+")
		
	}
	
	internal lateinit var pref : SharedPreferences
	
	class BooleanViewInfo(
		val info : BooleanPref,
		val view : CompoundButton
	)
	
	private val booleanViewList = ArrayList<BooleanViewInfo>()
	
	private var spBackButtonAction : Spinner? = null
	private var spUITheme : Spinner? = null
	private var spResizeImage : Spinner? = null
	private var spRefreshAfterToot : Spinner? = null
	private var spDefaultAccount : Spinner? = null
	private var spRepliesCount : Spinner? = null
	private var spVisibilityStyle : Spinner? = null
	private var spBoostButtonJustify : Spinner? = null
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
	
	private var ivFooterToot : ImageView? = null
	private var ivFooterMenu : ImageView? = null
	private var llFooterBG : View? = null
	private var vFooterDivider1 : View? = null
	private var vFooterDivider2 : View? = null
	private var vIndicator : View? = null
	
	private var etColumnWidth : EditText? = null
	private var etMediaThumbHeight : EditText? = null
	private var etClientName : EditText? = null
	private var etUserAgent : EditText? = null
	private var etQuoteNameFormat : EditText? = null
	private var etAutoCWLines : EditText? = null
	private var etCardDescriptionLength : EditText? = null
	private var etMediaSizeMax : EditText? = null
	private var etMovieSizeMax : EditText? = null
	private var etRoundRatio : EditText? = null
	private var etBoostAlpha : EditText? = null
	private var etMediaReadTimeout : EditText? = null
	
	private var tvTimelineFontUrl : TextView? = null
	private var timeline_font : String? = null
	private var tvTimelineFontBoldUrl : TextView? = null
	private var timeline_font_bold : String? = null
	
	private var etTimelineFontSize : EditText? = null
	private var etAcctFontSize : EditText? = null
	private var tvTimelineFontSize : TextView? = null
	private var tvAcctFontSize : TextView? = null
	private var etAvatarIconSize : EditText? = null
	
	private var etPullNotificationCheckInterval : EditText? = null
	
	private var etNotificationTlFontSize : EditText? = null
	private var tvNotificationTlFontSize : TextView? = null
	private var etNotificationTlIconSize : EditText? = null
	
	private var etBoostButtonSize : EditText? = null
	
	private var tvUserAgentError : TextView? = null
	
	private var llColumnHeader : View? = null
	private var ivColumnHeader : ImageView? = null
	private var tvColumnName : TextView? = null
	private var flColumnBackground : View? = null
	private var tvSampleAcct : TextView? = null
	private var tvSampleContent : TextView? = null
	
	private var load_busy : Boolean = false
	
	private var hasTimelineFontUi = false
	private var hasFooterColorUi = false
	private var hasListDividerColorUi = false
	private var hasTootBackgroundColorUi = false
	private var hasLinkColorUi = false
	private var hasColumnColorDefaultUi = false
	
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

		pref = Pref.pref(this)
		
		val intent = this.intent
		val layoutId = intent.getIntExtra(EXTRA_LAYOUT_ID,0)
		val titleId = intent.getIntExtra(EXTRA_TITLE_ID,0)

		this.title = getString(titleId)

		setContentView(layoutId)

		initUI()
		
		loadUIFromData()
	}
	
	private fun initUI() {
		
		Styler.fixHorizontalPadding(findViewById(R.id.svContent))
		
		// initialize Switch and CheckBox
		for(info in Pref.map.values) {
			if(info is BooleanPref && info.id != 0) {
				val view = findViewById<CompoundButton>(info.id)
				if(view != null) {
					view.setOnCheckedChangeListener(this)
					booleanViewList.add(BooleanViewInfo(info, view))
				}
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
		
		spDefaultAccount = findViewById<Spinner>(R.id.spDefaultAccount)?.also {
			it.adapter = AccountAdapter()
			it.onItemSelectedListener = this@ActAppSettingChild
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
		,R.id.btnInstanceTickerCopyright
		).forEach {
			findViewById<View>(it)?.setOnClickListener(this)
		}
		
		hasTimelineFontUi = null != findViewById(R.id.btnTimelineFontEdit)
		hasFooterColorUi = null != findViewById(R.id.btnTabDividerColorEdit)
		hasListDividerColorUi = null != findViewById(R.id.btnListDividerColorEdit)
		hasTootBackgroundColorUi = null != findViewById(R.id.btnBackgroundColorUnlistedEdit)
		hasLinkColorUi = null != findViewById(R.id.btnLinkColorEdit)
		hasColumnColorDefaultUi = null != findViewById(R.id.btnCcdHeaderBackgroundEdit)
		
		ivFooterToot = findViewById(R.id.ivFooterToot)
		ivFooterMenu = findViewById(R.id.ivFooterMenu)
		llFooterBG = findViewById(R.id.llFooterBG)
		vFooterDivider1 = findViewById(R.id.vFooterDivider1)
		vFooterDivider2 = findViewById(R.id.vFooterDivider2)
		vIndicator = findViewById(R.id.vIndicator)
		
		etColumnWidth = findViewById(R.id.etColumnWidth)
		etColumnWidth?.addTextChangedListener(this)
		
		etMediaThumbHeight = findViewById(R.id.etMediaThumbHeight)
		etMediaThumbHeight?.addTextChangedListener(this)
		
		etClientName = findViewById(R.id.etClientName)
		etClientName?.addTextChangedListener(this)
		
		etUserAgent = findViewById(R.id.etUserAgent)
		etUserAgent?.addTextChangedListener(this)
		
		etQuoteNameFormat = findViewById(R.id.etQuoteNameFormat)
		etQuoteNameFormat?.addTextChangedListener(this)
		
		etAutoCWLines = findViewById(R.id.etAutoCWLines)
		etAutoCWLines?.addTextChangedListener(this)
		
		etCardDescriptionLength = findViewById(R.id.etCardDescriptionLength)
		etCardDescriptionLength?.addTextChangedListener(this)
		
		etMediaSizeMax = findViewById(R.id.etMediaSizeMax)
		etMediaSizeMax?.addTextChangedListener(this)
		
		etMovieSizeMax = findViewById(R.id.etMovieSizeMax)
		etMovieSizeMax?.addTextChangedListener(this)
		
		etRoundRatio = findViewById(R.id.etRoundRatio)
		etRoundRatio?.addTextChangedListener(this)
		
		etBoostAlpha = findViewById(R.id.etBoostAlpha)
		etBoostAlpha?.addTextChangedListener(this)
		
		etMediaReadTimeout = findViewById(R.id.etMediaReadTimeout)
		etMediaReadTimeout?.addTextChangedListener(this)
		
		tvTimelineFontSize = findViewById(R.id.tvTimelineFontSize)
		tvAcctFontSize = findViewById(R.id.tvAcctFontSize)
		tvNotificationTlFontSize = findViewById(R.id.tvNotificationTlFontSize)
		
		etTimelineFontSize = findViewById(R.id.etTimelineFontSize)
		etTimelineFontSize?.addTextChangedListener(
			SizeCheckTextWatcher(
				tvTimelineFontSize !!,
				etTimelineFontSize !!,
				Pref.default_timeline_font_size
			)
		)
		
		etAcctFontSize = findViewById(R.id.etAcctFontSize)
		etAcctFontSize?.addTextChangedListener(
			SizeCheckTextWatcher(
				tvAcctFontSize !!,
				etAcctFontSize !!,
				Pref.default_acct_font_size
			)
		)
		
		etNotificationTlFontSize = findViewById(R.id.etNotificationTlFontSize)
		etNotificationTlFontSize?.addTextChangedListener(
			SizeCheckTextWatcher(
				tvNotificationTlFontSize !!,
				etNotificationTlFontSize !!,
				Pref.default_notification_tl_font_size
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
	
	private fun initSpinner(@IdRes viewId : Int, vararg captions : String) : Spinner? {
		val sp : Spinner = findViewById(viewId) ?: return null

		val caption_list : Array<String> = arrayOf(*captions)
		val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, caption_list)
		adapter.setDropDownViewResource(R.layout.lv_spinner_dropdown)
		sp.adapter = adapter
		sp.onItemSelectedListener = this

		return sp
	}
	
	private fun loadUIFromData() {
		load_busy = true
		
		for(si in booleanViewList) {
			si.view.isChecked = si.info(pref)
		}
		
		spBackButtonAction?.setSelection(Pref.ipBackButtonAction(pref))
		spRepliesCount?.setSelection(Pref.ipRepliesCount(pref))
		spVisibilityStyle?.setSelection(Pref.ipVisibilityStyle(pref))
		spBoostButtonJustify?.setSelection(Pref.ipBoostButtonJustify(pref))
		spUITheme?.setSelection(Pref.ipUiTheme(pref))
		spResizeImage?.setSelection(Pref.ipResizeImage(pref))
		spRefreshAfterToot?.setSelection(Pref.ipRefreshAfterToot(pref))
		
		
		spDefaultAccount?.setSelection(
			(spDefaultAccount?.adapter as? AccountAdapter)
				?.getIndexFromId(Pref.lpTabletTootDefaultAccount(pref))
				?: 0
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
		
		etColumnWidth?.setText(Pref.spColumnWidth(pref))
		etMediaThumbHeight?.setText(Pref.spMediaThumbHeight(pref))
		etClientName?.setText(Pref.spClientName(pref))
		etUserAgent?.setText(Pref.spUserAgent(pref))
		etQuoteNameFormat?.setText(Pref.spQuoteNameFormat(pref))
		etAutoCWLines?.setText(Pref.spAutoCWLines(pref))
		etCardDescriptionLength?.setText(Pref.spCardDescriptionLength(pref))
		etAvatarIconSize?.setText(Pref.spAvatarIconSize(pref))
		etNotificationTlIconSize?.setText(Pref.spNotificationTlIconSize(pref))
		etBoostButtonSize?.setText(Pref.spBoostButtonSize(pref))
		etPullNotificationCheckInterval?.setText(Pref.spPullNotificationCheckInterval(pref))
		
		etMediaSizeMax?.setText(Pref.spMediaSizeMax(pref))
		etMovieSizeMax?.setText(Pref.spMovieSizeMax(pref))
		etRoundRatio?.setText(Pref.spRoundRatio(pref))
		etBoostAlpha?.setText(Pref.spBoostAlpha(pref))
		
		etMediaReadTimeout?.setText(Pref.spMediaReadTimeout(pref))
		
		timeline_font = Pref.spTimelineFont(pref)
		timeline_font_bold = Pref.spTimelineFontBold(pref)
		
		etTimelineFontSize?.setText(formatFontSize(Pref.fpTimelineFontSize(pref)))
		etAcctFontSize?.setText(formatFontSize(Pref.fpAcctFontSize(pref)))
		etNotificationTlFontSize?.setText(formatFontSize(Pref.fpNotificationTlFontSize(pref)))
		
		etUserAgent?.hint = App1.userAgentDefault
		
		load_busy = false
		
		showFooterColor()
		showTimelineFont(tvTimelineFontUrl, timeline_font)
		showTimelineFont(tvTimelineFontBoldUrl, timeline_font_bold)
		
		showFontSize(tvTimelineFontSize, etTimelineFontSize, Pref.default_timeline_font_size)
		showFontSize(tvAcctFontSize, etAcctFontSize, Pref.default_acct_font_size)
		showFontSize(
			tvNotificationTlFontSize,
			etNotificationTlFontSize,
			Pref.default_notification_tl_font_size
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
		
		spDefaultAccount?.let {
			e.put(
				Pref.lpTabletTootDefaultAccount,
				(it.adapter as AccountAdapter).getIdFromIndex(it.selectedItemPosition)
			)
		}
		
		fun putFontSize(fp : FloatPref, et : EditText?) {
			et ?: return
			e.put(fp, parseFontSize(et.text.toString().trim()))
		}
		putFontSize(Pref.fpTimelineFontSize, etTimelineFontSize)
		putFontSize(Pref.fpAcctFontSize, etAcctFontSize)
		putFontSize(Pref.fpNotificationTlFontSize, etNotificationTlFontSize)
		putFontSize(Pref.fpNotificationTlFontSize, etNotificationTlFontSize)
		
		fun putText(sp : StringPref, et : EditText?, filter : (String) -> String = { it.trim() }) {
			et ?: return
			e.put(sp, filter(et.text.toString()))
		}
		putText(Pref.spColumnWidth, etColumnWidth)
		putText(Pref.spMediaThumbHeight, etMediaThumbHeight)
		putText(Pref.spClientName, etClientName)
		putText(Pref.spUserAgent, etUserAgent) { it.replace(reLinefeed, " ").trim() }
		putText(Pref.spQuoteNameFormat, etQuoteNameFormat) { it } // don't trim
		putText(Pref.spAutoCWLines, etAutoCWLines)
		putText(Pref.spCardDescriptionLength, etCardDescriptionLength)
		putText(Pref.spAvatarIconSize, etAvatarIconSize)
		putText(Pref.spNotificationTlIconSize, etNotificationTlIconSize)
		putText(Pref.spBoostButtonSize, etBoostButtonSize)
		putText(Pref.spPullNotificationCheckInterval, etPullNotificationCheckInterval)
		putText(Pref.spMediaSizeMax, etMediaSizeMax)
		putText(Pref.spMovieSizeMax, etMovieSizeMax)
		putText(Pref.spRoundRatio, etRoundRatio)
		putText(Pref.spBoostAlpha, etBoostAlpha)
		putText(Pref.spMediaReadTimeout, etMediaReadTimeout)
		
		fun putIf(hasUi : Boolean, sp : StringPref, value : String) {
			if(! hasUi) return
			e.put(sp, value)
		}
		putIf(hasTimelineFontUi, Pref.spTimelineFont, timeline_font ?: "")
		putIf(hasTimelineFontUi, Pref.spTimelineFontBold, timeline_font_bold ?: "")
		
		fun putSpinner(ip : IntPref, sp : Spinner?) {
			sp ?: return
			e.put(ip, sp.selectedItemPosition)
		}
		
		putSpinner(Pref.ipBackButtonAction, spBackButtonAction)
		putSpinner(Pref.ipRepliesCount, spRepliesCount)
		putSpinner(Pref.ipVisibilityStyle, spVisibilityStyle)
		putSpinner(Pref.ipBoostButtonJustify, spBoostButtonJustify)
		putSpinner(Pref.ipUiTheme, spUITheme)
		putSpinner(Pref.ipResizeImage, spResizeImage)
		putSpinner(Pref.ipRefreshAfterToot, spRefreshAfterToot)
		
		fun putIf(hasUi : Boolean, sp : IntPref, value : Int) {
			if(! hasUi) return
			e.put(sp, value)
		}
		
		putIf(hasFooterColorUi, Pref.ipFooterButtonBgColor, footer_button_bg_color)
		putIf(hasFooterColorUi, Pref.ipFooterButtonFgColor, footer_button_fg_color)
		putIf(hasFooterColorUi, Pref.ipFooterTabBgColor, footer_tab_bg_color)
		putIf(hasFooterColorUi, Pref.ipFooterTabDividerColor, footer_tab_divider_color)
		putIf(hasFooterColorUi, Pref.ipFooterTabIndicatorColor, footer_tab_indicator_color)
		
		putIf(hasListDividerColorUi, Pref.ipListDividerColor, list_divider_color)
		
		putIf(hasTootBackgroundColorUi, Pref.ipTootColorUnlisted, toot_color_unlisted)
		putIf(hasTootBackgroundColorUi, Pref.ipTootColorFollower, toot_color_follower)
		putIf(hasTootBackgroundColorUi, Pref.ipTootColorDirectUser, toot_color_direct_user)
		putIf(hasTootBackgroundColorUi, Pref.ipTootColorDirectMe, toot_color_direct_me)
		
		putIf(hasLinkColorUi, Pref.ipLinkColor, link_color)
		
		putIf(hasColumnColorDefaultUi, Pref.ipCcdHeaderBg, color_column_header_bg)
		putIf(hasColumnColorDefaultUi, Pref.ipCcdHeaderFg, color_column_header_fg)
		putIf(hasColumnColorDefaultUi, Pref.ipCcdContentBg, color_column_bg)
		putIf(hasColumnColorDefaultUi, Pref.ipCcdContentAcct, color_column_acct)
		putIf(hasColumnColorDefaultUi, Pref.ipCcdContentText, color_column_text)
		
		e.apply()
		showUserAgentError()
	}
	
	private fun showUserAgentError() {
		etUserAgent?.let { et ->
			val m = App1.reNotAllowedInUserAgent.matcher(et.text.toString())
			tvUserAgentError !!.text = when(m.find()) {
				true -> getString(R.string.user_agent_error, m.group())
				else -> ""
			}
		}
	}
	
	private fun showColumnHeaderSample() {
		llColumnHeader ?: return
		
		val header_bg = when {
			color_column_header_bg != 0 -> color_column_header_bg
			else -> getAttributeColor(this, R.attr.color_column_header)
		}
		
		val header_fg = when {
			color_column_header_fg != 0 -> color_column_header_fg
			else -> getAttributeColor(this, R.attr.colorColumnHeaderName)
		}
		
		ViewCompat.setBackground(
			llColumnHeader !!,
			getAdaptiveRippleDrawable(header_bg, header_fg)
		)
		
		tvColumnName !!.textColor = header_fg
		setIconAttr(
			this,
			ivColumnHeader !!,
			R.attr.btn_federate_tl,
			color = header_fg
		)
	}
	
	private fun showColumnSample() {
		flColumnBackground ?: return
		
		var c = when {
			color_column_bg != 0 -> color_column_bg
			else -> 0
		}
		flColumnBackground !!.setBackgroundColor(c)
		
		c = when {
			color_column_acct != 0 -> color_column_acct
			else -> getAttributeColor(this, R.attr.colorTimeSmall)
		}
		tvSampleAcct !!.setTextColor(c)
		
		c = when {
			color_column_text != 0 -> color_column_text
			else -> getAttributeColor(this, R.attr.colorContentText)
		}
		tvSampleContent !!.setTextColor(c)
		
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
			
			R.id.btnInstanceTickerCopyright -> App1.openBrowser(this@ActAppSettingChild,"https://cdn.weep.me/mastodon/")
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
		val colorOpaque = colorSelected or Color.BLACK
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
		ivFooterToot ?: return
		
		var c = footer_button_bg_color
		if(c == 0) {
			ivFooterToot !!.setBackgroundResource(R.drawable.bg_button_cw)
			ivFooterMenu !!.setBackgroundResource(R.drawable.bg_button_cw)
		} else {
			val fg = if(footer_button_fg_color != 0)
				footer_button_fg_color
			else
				getAttributeColor(this, R.attr.colorRippleEffect)
			ViewCompat.setBackground(ivFooterToot !!, getAdaptiveRippleDrawable(c, fg))
			ViewCompat.setBackground(ivFooterMenu !!, getAdaptiveRippleDrawable(c, fg))
		}
		
		c = footer_button_fg_color
		if(c == 0) {
			setIconAttr(this, ivFooterToot !!, R.attr.ic_edit)
			setIconAttr(this, ivFooterMenu !!, R.attr.ic_hamburger)
		} else {
			setIconAttr(this, ivFooterToot !!, R.attr.ic_edit, color = c)
			setIconAttr(this, ivFooterMenu !!, R.attr.ic_hamburger, color = c)
		}
		
		c = footer_tab_bg_color
		if(c == 0) {
			llFooterBG !!.setBackgroundColor(
				getAttributeColor(
					this,
					R.attr.colorColumnStripBackground
				)
			)
		} else {
			llFooterBG !!.setBackgroundColor(c)
		}
		
		c = footer_tab_divider_color
		if(c == 0) {
			vFooterDivider1 !!.setBackgroundColor(
				getAttributeColor(this, R.attr.colorImageButton)
			)
			vFooterDivider2 !!.setBackgroundColor(
				getAttributeColor(this, R.attr.colorImageButton)
			)
		} else {
			vFooterDivider1 !!.setBackgroundColor(c)
			vFooterDivider2 !!.setBackgroundColor(c)
		}
		
		c = footer_tab_indicator_color
		if(c == 0) {
			vIndicator !!.setBackgroundColor(getAttributeColor(this, R.attr.colorAccent))
		} else {
			vIndicator !!.setBackgroundColor(c)
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
	
	private fun showFontSize(
		sample : TextView?,
		et : EditText?,
		default_sp : Float
	) {
		sample ?: return
		et ?: return
		var fv = parseFontSize(et.text.toString().trim())
		if(fv.isNaN()) {
			sample.textSize = default_sp
		} else {
			if(fv < 1f) fv = 1f
			sample.textSize = fv
		}
	}
	
	private fun showTimelineFont(
		tvFontUrl : TextView?,
		font_url : String?
	) {
		tvFontUrl ?: return
		
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
	
	private inner class AccountAdapter internal constructor() : BaseAdapter() {
		
		internal val list = ArrayList<SavedAccount>()
		
		init {
			for(a in SavedAccount.loadAccountList(this@ActAppSettingChild)) {
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
