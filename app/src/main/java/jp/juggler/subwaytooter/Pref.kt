package jp.juggler.subwaytooter

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import jp.juggler.subwaytooter.util.optInt

object Pref {
	
	fun pref(context : Context) : SharedPreferences {
		return PreferenceManager.getDefaultSharedPreferences(context)
	}
	
	// キー名と設定項目のマップ。インポートやアプリ設定で使う
	val map = HashMap<String, BasePref<*>>()
	
	@Suppress("EqualsOrHashCode")
	abstract class BasePref<T>(val key : String) {
		
		init {
			@Suppress("LeakingThis")
			map[this.key] = this
		}
		
		override fun equals(other : Any?) : Boolean {
			return this === other
		}
		
		fun remove(e : SharedPreferences.Editor) {
			e.remove(key)
		}
		
		abstract fun put(editor : SharedPreferences.Editor, v : T)
		abstract fun invoke(pref : SharedPreferences) : T
		
		operator fun invoke(context : Context) : T {
			return invoke(pref(context))
		}
		
	}
	
	class BooleanPref(
		key : String,
		private val defVal : Boolean,
		val id : Int
	) : BasePref<Boolean>(key) {
		
		override operator fun invoke(pref : SharedPreferences) : Boolean {
			return pref.getBoolean(key, defVal)
		}
		
		override fun put(editor : SharedPreferences.Editor, v : Boolean) {
			editor.putBoolean(key, v)
		}
	}
	
	class IntPref(key : String, private val defVal : Int) : BasePref<Int>(key) {
		
		override operator fun invoke(pref : SharedPreferences) : Int {
			return pref.getInt(key, defVal)
		}
		
		override fun put(editor : SharedPreferences.Editor, v : Int) {
			editor.putInt(key, v)
		}
	}
	
	class LongPref(key : String, private val defVal : Long) : BasePref<Long>(key) {
		
		override operator fun invoke(pref : SharedPreferences) : Long {
			return pref.getLong(key, defVal)
		}
		
		override fun put(editor : SharedPreferences.Editor, v : Long) {
			editor.putLong(key, v)
		}
	}
	
	class FloatPref(key : String, private val defVal : Float) : BasePref<Float>(key) {
		
		override operator fun invoke(pref : SharedPreferences) : Float {
			return pref.getFloat(key, defVal)
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
		
		override operator fun invoke(pref : SharedPreferences) : String {
			return pref.getString(key, defVal)
		}
		
		override fun put(editor : SharedPreferences.Editor, v : String) {
			editor.putString(key, v)
		}
		
		fun optInt(pref : SharedPreferences) = invoke(pref).optInt() ?: defVal.optInt()
	}
	
	// boolean
	
	val bpAllowNonSpaceBeforeEmojiShortcode = BooleanPref(
		"allow_non_space_before_emoji_shortcode",
		false,
		R.id.swAllowNonSpaceBeforeEmojiShortcode
	)
	
	val bpDisableEmojiAnimation = BooleanPref(
		"disable_emoji_animation",
		false,
		R.id.swDisableEmojiAnimation
	)
	
	// val bpDisableFastScroller = BooleanPref("disable_fast_scroller", true, 0) // R.id.swDisableFastScroller)
	
	val bpDisableTabletMode = BooleanPref(
		"disable_tablet_mode",
		false,
		R.id.swDisableTabletMode
	)
	
	val bpDontConfirmBeforeCloseColumn = BooleanPref(
		"DontConfirmBeforeCloseColumn",
		false,
		R.id.swDontConfirmBeforeCloseColumn
	)
	
	val bpDontCropMediaThumb = BooleanPref(
		"DontCropMediaThumb",
		false,
		R.id.swDontCropMediaThumb
	)
	
	val bpDontDuplicationCheck = BooleanPref(
		"dont_duplication_check",
		false,
		R.id.swDontDuplicationCheck
	)
	
	val bpDontRefreshOnResume = BooleanPref(
		"dont_refresh_on_resume",
		false,
		R.id.swDontRefreshOnResume
	)
	
	val bpDontRound = BooleanPref(
		"dont_round",
		false,
		R.id.swDontRound
	)
	
	val bpDontScreenOff = BooleanPref(
		"dont_screen_off",
		false,
		R.id.swDontScreenOff
	)
	
	val bpDontUseActionButtonWithQuickTootBar = BooleanPref(
		"dont_use_action_button",
		false,
		R.id.swDontUseActionButtonWithQuickTootBar
	)
	
	val bpDontUseStreaming = BooleanPref(
		"dont_use_streaming",
		false,
		R.id.swDontUseStreaming
	)
	
	val bpEnableGifAnimation = BooleanPref(
		"enable_gif_animation",
		false,
		R.id.swEnableGifAnimation
	)
	
	val bpExitAppWhenCloseProtectedColumn = BooleanPref(
		"ExitAppWhenCloseProtectedColumn",
		false,
		R.id.swExitAppWhenCloseProtectedColumn
	)
	
	val bpMentionFullAcct = BooleanPref(
		"mention_full_acct",
		false,
		R.id.swMentionFullAcct
	)
	
	val bpNotificationLED = BooleanPref(
		"notification_led",
		true,
		R.id.cbNotificationLED
	)
	
	val bpNotificationSound = BooleanPref(
		"notification_sound",
		true,
		R.id.cbNotificationSound
	)
	
	val bpNotificationVibration = BooleanPref(
		"notification_vibration",
		true,
		R.id.cbNotificationVibration
	)
	
	val bpPostButtonBarTop = BooleanPref(
		"post_button_bar_at_top",
		true,
		R.id.swPostButtonBarTop
	)
	
	val bpPriorChrome = BooleanPref(
		"prior_chrome",
		true,
		R.id.swPriorChrome
	)
	
	val bpPriorLocalURL = BooleanPref(
		"prior_local_url",
		false,
		R.id.swPriorLocalURL
	)
	
	val bpQuickTootBar = BooleanPref(
		"quick_toot_bar",
		false,
		R.id.swQuickTootBar
	)
	
	val bpRelativeTimestamp = BooleanPref(
		"relative_timestamp",
		true,
		R.id.swRelativeTimestamp
	)
	
	val bpShortAcctLocalUser = BooleanPref(
		"short_acct_local_user",
		true,
		R.id.swShortAcctLocalUser
	)
	
	val bpShowFollowButtonInButtonBar = BooleanPref(
		"ShowFollowButtonInButtonBar",
		false,
		R.id.swShowFollowButtonInButtonBar
	)
	
	val bpSimpleList = BooleanPref(
		"simple_list",
		true,
		R.id.swSimpleList
	)
	
	val bpUseInternalMediaViewer = BooleanPref(
		"use_internal_media_viewer",
		true,
		R.id.swUseInternalMediaViewer
	)
	
	val bpShowAppName = BooleanPref(
		"show_app_name",
		false,
		R.id.swShowAppName
	)
	
	val bpForceGap = BooleanPref(
		"force_gap",
		false,
		R.id.swForceGap
	)
	
	val bpShareViewPool = BooleanPref(
		"ShareViewPool",
		true,
		R.id.swShareViewPool
	)
	
	val bpAllowColumnDuplication = BooleanPref(
		"AllowColumnDuplication",
		true,
		R.id.swShareViewPool
	)
	
	val bpAppendAttachmentUrlToContent = BooleanPref(
		"AppendAttachmentUrlToContent",
		false,
		R.id.swAppendAttachmentUrlToContent
	)
	
	
	// int
	
	val ipBackButtonAction = IntPref("back_button_action", 0)
	val ipUiTheme = IntPref("ui_theme", 0)
	val ipResizeImage = IntPref("resize_image", 4)
	
	val ipRefreshAfterToot = IntPref("refresh_after_toot", 0)
	
	val ipFooterButtonBgColor = IntPref("footer_button_bg_color", 0)
	val ipFooterButtonFgColor = IntPref("footer_button_fg_color", 0)
	val ipFooterTabBgColor = IntPref("footer_tab_bg_color", 0)
	val ipFooterTabDividerColor = IntPref("footer_tab_divider_color", 0)
	val ipFooterTabIndicatorColor = IntPref("footer_tab_indicator_color", 0)
	val ipLastColumnPos = IntPref("last_column_pos", - 1)
	
	// ipRefreshAfterToot の値
	const val RAT_REFRESH_SCROLL = 0
	const val RAT_REFRESH_DONT_SCROLL = 1
	const val RAT_DONT_REFRESH = 2
	
	// string
	
	val spColumnWidth = StringPref("ColumnWidth", "")
	val spMediaThumbHeight = StringPref("MediaThumbHeight", "")
	val spClientName = StringPref("client_name", "")
	val spQuoteNameFormat = StringPref("quote_name_format", "")
	val spAutoCWLines = StringPref("auto_cw_lines", "0")
	val spAvatarIconSize = StringPref("avatar_icon_size", "48")
	val spMediaSizeMax = StringPref("max_media_size", "8")
	val spTimelineFont = StringPref("timeline_font", "", skipImport = true)
	val spTimelineFontBold = StringPref("timeline_font_bold", "", skipImport = true)
	val spStreamListenerSecret = StringPref("stream_listener_secret", "")
	val spStreamListenerConfigUrl = StringPref("stream_listener_config_url", "")
	val spStreamListenerConfigData = StringPref("stream_listener_config_data", "")
	val spMspUserToken = StringPref("mastodon_search_portal_user_token", "")
	val spEmojiPickerRecent = StringPref("emoji_picker_recent", "")
	val spRoundRatio = StringPref("round_ratio", "33")
	
	// long
	
	val lpTabletTootDefaultAccount = LongPref("tablet_toot_default_account", - 1L)
	
	// float
	
	val fpTimelineFontSize = FloatPref("timeline_font_size", Float.NaN)
	val fpAcctFontSize = FloatPref("acct_font_size", Float.NaN)
	
}

fun <T> SharedPreferences.Editor.put(item : Pref.BasePref<T>, v : T) : SharedPreferences.Editor {
	item.put(this, v)
	return this
}

fun SharedPreferences.Editor.remove(item : Pref.BasePref<*>) : SharedPreferences.Editor {
	item.remove(this)
	return this
}
