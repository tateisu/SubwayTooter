package jp.juggler.subwaytooter;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class Pref {
	
	
	public static SharedPreferences pref( Context context ){
		return PreferenceManager.getDefaultSharedPreferences( context );
	}
	
	private static final String KEY_BACK_TO_COLUMN_LIST = "BackToColumnList"; // 使わなくなった
	
	static final int RAT_REFRESH_SCROLL = 0;
	static final int RAT_REFRESH__DONT_SCROLL = 1;
	static final int RAT_DONT_REFRESH = 2;
	
	static final String KEY_DONT_CONFIRM_BEFORE_CLOSE_COLUMN = "DontConfirmBeforeCloseColumn";
	
	static final String KEY_BACK_BUTTON_ACTION = "back_button_action";
	static final String KEY_PRIOR_LOCAL_URL = "prior_local_url";
	static final String KEY_DISABLE_FAST_SCROLLER = "disable_fast_scroller";
	static final String KEY_UI_THEME = "ui_theme";
	static final String KEY_SIMPLE_LIST = "simple_list";
	static final String KEY_NOTIFICATION_SOUND = "notification_sound";
	static final String KEY_NOTIFICATION_VIBRATION = "notification_vibration";
	static final String KEY_NOTIFICATION_LED = "notification_led";
	static final String KEY_EXIT_APP_WHEN_CLOSE_PROTECTED_COLUMN = "ExitAppWhenCloseProtectedColumn";
	static final String KEY_RESIZE_IMAGE = "resize_image";
	static final String KEY_SHOW_FOLLOW_BUTTON_IN_BUTTON_BAR = "ShowFollowButtonInButtonBar";
	static final String KEY_REFRESH_AFTER_TOOT = "refresh_after_toot";
	public static final String KEY_DONT_ROUND = "dont_round";
	
	static final String KEY_FOOTER_BUTTON_BG_COLOR = "footer_button_bg_color";
	static final String KEY_FOOTER_BUTTON_FG_COLOR = "footer_button_fg_color";
	static final String KEY_FOOTER_TAB_BG_COLOR = "footer_tab_bg_color";
	static final String KEY_FOOTER_TAB_DIVIDER_COLOR = "footer_tab_divider_color";
	static final String KEY_FOOTER_TAB_INDICATOR_COLOR = "footer_tab_indicator_color";
	
	static final String KEY_DONT_USE_STREAMING = "dont_use_streaming";
	static final String KEY_DONT_REFRESH_ON_RESUME = "dont_refresh_on_resume";
	static final String KEY_DONT_SCREEN_OFF = "dont_screen_off";
	static final String KEY_DISABLE_TABLET_MODE = "disable_tablet_mode";
	
	static final String KEY_COLUMN_WIDTH = "ColumnWidth";
	static final String KEY_MEDIA_THUMB_HEIGHT = "MediaThumbHeight";
	static final String KEY_TIMELINE_FONT = "timeline_font";
	static final String KEY_TIMELINE_FONT_BOLD = "timeline_font_bold";
	
	static final String KEY_DONT_CROP_MEDIA_THUMBNAIL = "DontCropMediaThumb";
	
	static final String KEY_STREAM_LISTENER_SECRET = "stream_listener_secret";
	static final String KEY_STREAM_LISTENER_CONFIG_URL = "stream_listener_config_url";
	static final String KEY_STREAM_LISTENER_CONFIG_DATA = "stream_listener_config_data";
	static final String KEY_TABLET_TOOT_DEFAULT_ACCOUNT = "tablet_toot_default_account";
	
	static final String KEY_PRIOR_CHROME = "prior_chrome";
	
	static final String KEY_POST_BUTTON_BAR_AT_TOP = "post_button_bar_at_top";
	
	static final String KEY_CLIENT_NAME = "client_name";
	
	public static final String KEY_MASTODON_SEARCH_PORTAL_USER_TOKEN = "mastodon_search_portal_user_token";
	
	static final String KEY_LAST_COLUMN_POS = "last_column_pos";
	
	public static final String KEY_TIMELINE_FONT_SIZE = "timeline_font_size";
	public static final String KEY_ACCT_FONT_SIZE = "acct_font_size";
	
	public static final String KEY_DONT_DUPLICATION_CHECK = "dont_duplication_check";
	public static final String KEY_QUICK_TOOT_BAR = "quick_toot_bar";
	
	public static final String KEY_QUOTE_NAME_FORMAT = "quote_name_format";
	
	public static final String KEY_ENABLE_GIF_ANIMATION = "enable_gif_animation";
	
	public static final String KEY_MENTION_FULL_ACCT = "mention_full_acct";
	
	public static final String KEY_RELATIVE_TIMESTAMP = "relative_timestamp";
	
	public static final String KEY_DONT_USE_ACTION_BUTTON = "dont_use_action_button";
	
	public static final String KEY_AUTO_CW_LINES = "auto_cw_lines";
	
	public static final String KEY_SHORT_ACCT_LOCAL_USER = "short_acct_local_user";
	
	public static final String KEY_AVATAR_ICON_SIZE = "avatar_icon_size";
	
	public static final String KEY_EMOJI_PICKER_RECENT = "emoji_picker_recent";
	
	public static final String KEY_DISABLE_EMOJI_ANIMATION = "disable_emoji_animation";
	
	public static final String KEY_ALLOW_NON_SPACE_BEFORE_EMOJI_SHORTCODE = "allow_non_space_before_emoji_shortcode";
	
	public static final String KEY_MEDIA_SIZE_MAX = "max_media_size";

	// 項目を追加したらAppDataExporter#importPref のswitch文も更新すること
}
