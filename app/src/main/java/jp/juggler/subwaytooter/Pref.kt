package jp.juggler.subwaytooter

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager

object Pref {
	fun pref(context : Context) : SharedPreferences {
		return PreferenceManager.getDefaultSharedPreferences(context)
	}
	
	const val KEY_BACK_TO_COLUMN_LIST = "BackToColumnList" // 使わなくなった
	
	const val RAT_REFRESH_SCROLL = 0
	const val RAT_REFRESH_DONT_SCROLL = 1
	const val RAT_DONT_REFRESH = 2
	
	const val KEY_DONT_CONFIRM_BEFORE_CLOSE_COLUMN = "DontConfirmBeforeCloseColumn"
	
	const val KEY_BACK_BUTTON_ACTION = "back_button_action"
	const val KEY_PRIOR_LOCAL_URL = "prior_local_url"
	const val KEY_DISABLE_FAST_SCROLLER = "disable_fast_scroller"
	const val KEY_UI_THEME = "ui_theme"
	const val KEY_SIMPLE_LIST = "simple_list"
	const val KEY_NOTIFICATION_SOUND = "notification_sound"
	const val KEY_NOTIFICATION_VIBRATION = "notification_vibration"
	const val KEY_NOTIFICATION_LED = "notification_led"
	const val KEY_EXIT_APP_WHEN_CLOSE_PROTECTED_COLUMN = "ExitAppWhenCloseProtectedColumn"
	const val KEY_RESIZE_IMAGE = "resize_image"
	const val KEY_SHOW_FOLLOW_BUTTON_IN_BUTTON_BAR = "ShowFollowButtonInButtonBar"
	const val KEY_REFRESH_AFTER_TOOT = "refresh_after_toot"
	const val KEY_DONT_ROUND = "dont_round"
	
	const val KEY_FOOTER_BUTTON_BG_COLOR = "footer_button_bg_color"
	const val KEY_FOOTER_BUTTON_FG_COLOR = "footer_button_fg_color"
	const val KEY_FOOTER_TAB_BG_COLOR = "footer_tab_bg_color"
	const val KEY_FOOTER_TAB_DIVIDER_COLOR = "footer_tab_divider_color"
	const val KEY_FOOTER_TAB_INDICATOR_COLOR = "footer_tab_indicator_color"
	
	const val KEY_DONT_USE_STREAMING = "dont_use_streaming"
	const val KEY_DONT_REFRESH_ON_RESUME = "dont_refresh_on_resume"
	const val KEY_DONT_SCREEN_OFF = "dont_screen_off"
	const val KEY_DISABLE_TABLET_MODE = "disable_tablet_mode"
	
	const val KEY_COLUMN_WIDTH = "ColumnWidth"
	const val KEY_MEDIA_THUMB_HEIGHT = "MediaThumbHeight"
	const val KEY_TIMELINE_FONT = "timeline_font"
	const val KEY_TIMELINE_FONT_BOLD = "timeline_font_bold"
	
	const val KEY_DONT_CROP_MEDIA_THUMBNAIL = "DontCropMediaThumb"
	
	const val KEY_STREAM_LISTENER_SECRET = "stream_listener_secret"
	const val KEY_STREAM_LISTENER_CONFIG_URL = "stream_listener_config_url"
	const val KEY_STREAM_LISTENER_CONFIG_DATA = "stream_listener_config_data"
	const val KEY_TABLET_TOOT_DEFAULT_ACCOUNT = "tablet_toot_default_account"
	
	const val KEY_PRIOR_CHROME = "prior_chrome"
	
	internal const val KEY_POST_BUTTON_BAR_AT_TOP = "post_button_bar_at_top"
	
	const val KEY_CLIENT_NAME = "client_name"
	
	const val KEY_MASTODON_SEARCH_PORTAL_USER_TOKEN = "mastodon_search_portal_user_token"
	
	const val KEY_LAST_COLUMN_POS = "last_column_pos"
	
	const val KEY_TIMELINE_FONT_SIZE = "timeline_font_size"
	const val KEY_ACCT_FONT_SIZE = "acct_font_size"
	
	const val KEY_DONT_DUPLICATION_CHECK = "dont_duplication_check"
	const val KEY_QUICK_TOOT_BAR = "quick_toot_bar"
	
	const val KEY_QUOTE_NAME_FORMAT = "quote_name_format"
	
	const val KEY_ENABLE_GIF_ANIMATION = "enable_gif_animation"
	
	const val KEY_MENTION_FULL_ACCT = "mention_full_acct"
	
	const val KEY_RELATIVE_TIMESTAMP = "relative_timestamp"
	
	const val KEY_DONT_USE_ACTION_BUTTON = "dont_use_action_button"
	
	const val KEY_AUTO_CW_LINES = "auto_cw_lines"
	
	const val KEY_SHORT_ACCT_LOCAL_USER = "short_acct_local_user"
	
	const val KEY_AVATAR_ICON_SIZE = "avatar_icon_size"
	
	const val KEY_EMOJI_PICKER_RECENT = "emoji_picker_recent"
	
	const val KEY_DISABLE_EMOJI_ANIMATION = "disable_emoji_animation"
	
	const val KEY_ALLOW_NON_SPACE_BEFORE_EMOJI_SHORTCODE = "allow_non_space_before_emoji_shortcode"
	
	const val KEY_MEDIA_SIZE_MAX = "max_media_size"
	
	const val KEY_USE_INTERNAL_MEDIA_VIEWER = "use_internal_media_viewer"
	
	// 項目を追加したらAppDataExporter#importPref のswitch文も更新すること
	
}
