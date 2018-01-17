package jp.juggler.subwaytooter

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager

object Pref {
	
	fun pref(context : Context) : SharedPreferences {
		return PreferenceManager.getDefaultSharedPreferences(context)
	}
	
	@Suppress("EqualsOrHashCode")
	abstract class BasePref<in T>(val key : String) {
		
		override fun equals(other : Any?) : Boolean {
			return this === other
		}
		
		fun remove(e : SharedPreferences.Editor) {
			e.remove(key)
		}
		
		abstract fun put(editor : SharedPreferences.Editor, v : T)
	}
	
	class BooleanPref(
		key : String,
		private val defVal : Boolean,
		val id : Int
	) : BasePref<Boolean>(key) {
		
		operator fun invoke(pref : SharedPreferences) : Boolean {
			return pref.getBoolean(key, defVal)
		}
		
		operator fun invoke(context : Context) : Boolean {
			return pref(context).getBoolean(key, defVal)
		}
		
		override fun put(editor : SharedPreferences.Editor, v : Boolean) {
			editor.putBoolean(key, v)
		}
	}
	
	class IntPref(
		key : String,
		private val defVal : Int
	) : BasePref<Int>(key) {
		
		operator fun invoke(pref : SharedPreferences) : Int {
			return pref.getInt(key, defVal)
		}
		
		operator fun invoke(context : Context) : Int {
			return pref(context).getInt(key, defVal)
		}
		
		override fun put(editor : SharedPreferences.Editor, v : Int) {
			editor.putInt(key, v)
		}
	}
	
	class LongPref(
		key : String,
		private val defVal : Long
	) : BasePref<Long>(key) {
		
		operator fun invoke(pref : SharedPreferences) : Long {
			return pref.getLong(key, defVal)
		}
		
		operator fun invoke(context : Context) : Long {
			return pref(context).getLong(key, defVal)
		}
		
		override fun put(editor : SharedPreferences.Editor, v : Long) {
			editor.putLong(key, v)
		}
	}
	
	class FloatPref(
		key : String,
		private val defVal : Float
	) : BasePref<Float>(key) {
		
		operator fun invoke(pref : SharedPreferences) : Float {
			return pref.getFloat(key, defVal)
		}
		
		operator fun invoke(context : Context) : Float {
			return pref(context).getFloat(key, defVal)
		}
		
		override fun put(editor : SharedPreferences.Editor, v : Float) {
			editor.putFloat(key, v)
		}
	}
	
	class StringPref(
		key : String,
		private val defVal : String,
		val skipImport : Boolean = false
	) : BasePref<String>(key) {
		
		operator fun invoke(pref : SharedPreferences) : String {
			return pref.getString(key, defVal)
		}
		
		operator fun invoke(context : Context) : String {
			return pref(context).getString(key, defVal)
		}
		
		override fun put(editor : SharedPreferences.Editor, v : String) {
			editor.putString(key, v)
		}
		
	}
	
	///////////////////////////////////////////////////////////////////////////////////
	
	// インポート時に使う。キー名に対応した設定項目を返す
	val map = HashMap<String, BasePref<*>>()
	
	private fun <T : BasePref<*>> register(item : T) : T {
		map.put(item.key, item)
		return item
	}
	
	///////////////////////////////////////////////////////////////////////////////////
	
	// boolean
	
	val bpAllowNonSpaceBeforeEmojiShortcode = register(BooleanPref("allow_non_space_before_emoji_shortcode", false, R.id.swAllowNonSpaceBeforeEmojiShortcode))
	val bpDisableEmojiAnimation = register(BooleanPref("disable_emoji_animation", false, R.id.swDisableEmojiAnimation))
	val bpDisableFastScroller = register(BooleanPref("disable_fast_scroller", true, R.id.swDisableFastScroller))
	val bpDisableTabletMode = register(BooleanPref("disable_tablet_mode", false, R.id.swDisableTabletMode))
	val bpDontConfirmBeforeCloseColumn = register(BooleanPref("DontConfirmBeforeCloseColumn", false, R.id.swDontConfirmBeforeCloseColumn))
	val bpDontCropMediaThumb = register(BooleanPref("DontCropMediaThumb", false, R.id.swDontCropMediaThumb))
	val bpDontDuplicationCheck = register(BooleanPref("dont_duplication_check", false, R.id.swDontDuplicationCheck))
	val bpDontRefreshOnResume = register(BooleanPref("dont_refresh_on_resume", false, R.id.swDontRefreshOnResume))
	val bpDontRound = register(BooleanPref("dont_round", false, R.id.swDontRound))
	val bpDontScreenOff = register(BooleanPref("dont_screen_off", false, R.id.swDontScreenOff))
	val bpDontUseActionButtonWithQuickTootBar = register(BooleanPref("dont_use_action_button", false, R.id.swDontUseActionButtonWithQuickTootBar))
	val bpDontUseStreaming = register(BooleanPref("dont_use_streaming", false, R.id.swDontUseStreaming))
	val bpEnableGifAnimation = register(BooleanPref("enable_gif_animation", false, R.id.swEnableGifAnimation))
	val bpExitAppWhenCloseProtectedColumn = register(BooleanPref("ExitAppWhenCloseProtectedColumn", false, R.id.swExitAppWhenCloseProtectedColumn))
	val bpMentionFullAcct = register(BooleanPref("mention_full_acct", false, R.id.swMentionFullAcct))
	val bpPostButtonBarTop = register(BooleanPref("post_button_bar_at_top", false, R.id.swPostButtonBarTop))
	val bpPriorChrome = register(BooleanPref("prior_chrome", true, R.id.swPriorChrome))
	val bpPriorLocalURL = register(BooleanPref("prior_local_url", false, R.id.swPriorLocalURL))
	val bpQuickTootBar = register(BooleanPref("quick_toot_bar", false, R.id.swQuickTootBar))
	val bpRelativeTimestamp = register(BooleanPref("relative_timestamp", true, R.id.swRelativeTimestamp))
	val bpShortAcctLocalUser = register(BooleanPref("short_acct_local_user", true, R.id.swShortAcctLocalUser))
	val bpShowFollowButtonInButtonBar = register(BooleanPref("ShowFollowButtonInButtonBar", false, R.id.swShowFollowButtonInButtonBar))
	val bpSimpleList = register(BooleanPref("simple_list", true, R.id.swSimpleList))
	val bpUseInternalMediaViewer = register(BooleanPref("use_internal_media_viewer", true, R.id.swUseInternalMediaViewer))
	val bpShowAppName = register(BooleanPref("show_app_name", false, R.id.swShowAppName))
	val bpNotificationSound = register(BooleanPref("notification_sound", true, R.id.cbNotificationSound))
	val bpNotificationVibration = register(BooleanPref("notification_vibration", true, R.id.cbNotificationVibration))
	val bpNotificationLED = register(BooleanPref("notification_led", true, R.id.cbNotificationLED))
	
	// int
	
	val ipBackButtonAction = register(IntPref("back_button_action", 0))
	val ipUiTheme = register(IntPref("ui_theme", 0))
	val ipResizeImage = register(IntPref("resize_image", 4))
	
	val ipRefreshAfterToot = register(IntPref("refresh_after_toot", 0))
	
	val ipFooterButtonBgColor = register(IntPref("footer_button_bg_color", 0))
	val ipFooterButtonFgColor = register(IntPref("footer_button_fg_color", 0))
	val ipFooterTabBgColor = register(IntPref("footer_tab_bg_color", 0))
	val ipFooterTabDividerColor = register(IntPref("footer_tab_divider_color", 0))
	val ipFooterTabIndicatorColor = register(IntPref("footer_tab_indicator_color", 0))
	val ipLastColumnPos = register(IntPref("last_column_pos", - 1))
	
	// ipRefreshAfterToot の値
	const val RAT_REFRESH_SCROLL = 0
	const val RAT_REFRESH_DONT_SCROLL = 1
	const val RAT_DONT_REFRESH = 2
	
	// string
	
	val spColumnWidth = register(StringPref("ColumnWidth", ""))
	val spMediaThumbHeight = register(StringPref("MediaThumbHeight", ""))
	val spClientName = register(StringPref("client_name", ""))
	val spQuoteNameFormat = register(StringPref("quote_name_format", ""))
	val spAutoCWLines = register(StringPref("auto_cw_lines", "0"))
	val spAvatarIconSize = register(StringPref("avatar_icon_size", "48"))
	val spMediaSizeMax = register(StringPref("max_media_size", "8"))
	val spTimelineFont = register(StringPref("timeline_font", "", skipImport = true))
	val spTimelineFontBold = register(StringPref("timeline_font_bold", "", skipImport = true))
	val spStreamListenerSecret = register(StringPref("stream_listener_secret", ""))
	val spStreamListenerConfigUrl = register(StringPref("stream_listener_config_url", ""))
	val spStreamListenerConfigData = register(StringPref("stream_listener_config_data", ""))
	val spMspUserToken = register(StringPref("mastodon_search_portal_user_token", ""))
	val spEmojiPickerRecent = register(StringPref("emoji_picker_recent", ""))
	
	// long
	
	val lpTabletTootDefaultAccount = register(LongPref("tablet_toot_default_account", - 1L))
	
	// float
	
	val fpTimelineFontSize = register(FloatPref("timeline_font_size", Float.NaN))
	val fpAcctFontSize = register(FloatPref("acct_font_size", Float.NaN))
	
}

fun <T> SharedPreferences.Editor.put(item : Pref.BasePref<T>, v : T) : SharedPreferences.Editor {
	item.put(this, v)
	return this
}

fun SharedPreferences.Editor.remove(item : Pref.BasePref<*>) : SharedPreferences.Editor {
	item.remove(this)
	return this
}
