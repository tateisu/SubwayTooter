package jp.juggler.subwaytooter

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import jp.juggler.subwaytooter.itemviewholder.AdditionalButtonsPosition
import jp.juggler.util.optInt

fun Context.pref(): SharedPreferences =
    this.getSharedPreferences(this.packageName + "_preferences", Context.MODE_PRIVATE)

@Suppress("EqualsOrHashCode")
abstract class BasePref<T>(val key: String, val defVal: T) {

    companion object {
        // キー名と設定項目のマップ。インポートやアプリ設定で使う
        val allPref = HashMap<String, BasePref<*>>()
    }

    init {
        when {
            allPref[key] != null -> error("Preference key duplicate: $key")
            else -> {
                @Suppress("LeakingThis")
                allPref[key] = this
            }
        }
    }

    abstract fun put(editor: SharedPreferences.Editor, v: T)
    abstract operator fun invoke(pref: SharedPreferences): T

    override fun equals(other: Any?) =
        this === other

    override fun hashCode(): Int = key.hashCode()

    operator fun invoke(context: Context): T =
        invoke(context.pref())

    fun remove(e: SharedPreferences.Editor): SharedPreferences.Editor =
        e.remove(key)

    fun removeDefault(pref: SharedPreferences, e: SharedPreferences.Editor) =
        if (pref.contains(key) && this.invoke(pref) == defVal) {
            e.remove(key)
            true
        } else {
            false
        }
}

fun SharedPreferences.Editor.remove(item: BasePref<*>): SharedPreferences.Editor {
    item.remove(this)
    return this
}

class BooleanPref(key: String, defVal: Boolean) : BasePref<Boolean>(key, defVal) {

    override operator fun invoke(pref: SharedPreferences): Boolean =
        pref.getBoolean(key, defVal)

    // put if value is not default, remove if value is same to default
    override fun put(editor: SharedPreferences.Editor, v: Boolean) {
        if (v == defVal) editor.remove(key) else editor.putBoolean(key, v)
    }
}

class IntPref(key: String, defVal: Int) : BasePref<Int>(key, defVal) {

    override operator fun invoke(pref: SharedPreferences): Int =
        pref.getInt(key, defVal)

    override fun put(editor: SharedPreferences.Editor, v: Int) {
        if (v == defVal) editor.remove(key) else editor.putInt(key, v)
    }
}

class LongPref(key: String, defVal: Long) : BasePref<Long>(key, defVal) {

    override operator fun invoke(pref: SharedPreferences): Long =
        pref.getLong(key, defVal)

    override fun put(editor: SharedPreferences.Editor, v: Long) {
        if (v == defVal) editor.remove(key) else editor.putLong(key, v)
    }
}

class FloatPref(key: String, defVal: Float) : BasePref<Float>(key, defVal) {

    override operator fun invoke(pref: SharedPreferences): Float =
        pref.getFloat(key, defVal)

    override fun put(editor: SharedPreferences.Editor, v: Float) {
        if (v == defVal) editor.remove(key) else editor.putFloat(key, v)
    }
}

class StringPref(
    key: String,
    defVal: String,
    val skipImport: Boolean = false,
) : BasePref<String>(key, defVal) {

    override operator fun invoke(pref: SharedPreferences): String =
        pref.getString(key, defVal) ?: defVal

    override fun put(editor: SharedPreferences.Editor, v: String) {
        if (v == defVal) editor.remove(key) else editor.putString(key, v)
    }

    fun toInt(pref: SharedPreferences) = invoke(pref).optInt() ?: defVal.toInt()
}

fun SharedPreferences.Editor.put(item: BooleanPref, v: Boolean) =
    this.apply { item.put(this, v) }

fun SharedPreferences.Editor.put(item: StringPref, v: String) =
    this.apply { item.put(this, v) }

fun SharedPreferences.Editor.put(item: IntPref, v: Int) =
    this.apply { item.put(this, v) }

fun SharedPreferences.Editor.put(item: LongPref, v: Long) =
    this.apply { item.put(this, v) }

fun SharedPreferences.Editor.put(item: FloatPref, v: Float) =
    this.apply { item.put(this, v) }

object PrefB {
    // boolean

    val bpDisableEmojiAnimation = BooleanPref(
        "disable_emoji_animation",
        false
    )

    // val bpDisableFastScroller = BooleanPref("disable_fast_scroller", true, 0) // R.id.swDisableFastScroller)

    val bpDisableTabletMode = BooleanPref(
        "disable_tablet_mode",
        false
    )

    val bpDontConfirmBeforeCloseColumn = BooleanPref(
        "DontConfirmBeforeCloseColumn",
        false
    )

    val bpDontCropMediaThumb = BooleanPref(
        "DontCropMediaThumb",
        true
    )

    val bpDontDuplicationCheck = BooleanPref(
        "dont_duplication_check",
        false
    )

    val bpDontRefreshOnResume = BooleanPref(
        "dont_refresh_on_resume",
        false
    )

    val bpDontRound = BooleanPref(
        "dont_round",
        false
    )

    val bpDontScreenOff = BooleanPref(
        "dont_screen_off",
        false
    )

    val bpDontUseActionButtonWithQuickPostBar = BooleanPref(
        "dont_use_action_button",
        false
    )

    val bpDontUseStreaming = BooleanPref(
        "dont_use_streaming",
        false
    )

    val bpEnableGifAnimation = BooleanPref(
        "enable_gif_animation",
        false
    )

    val bpExitAppWhenCloseProtectedColumn = BooleanPref(
        "ExitAppWhenCloseProtectedColumn",
        false
    )

    val bpMentionFullAcct = BooleanPref(
        "mention_full_acct",
        false
    )

    val bpNotificationLED = BooleanPref(
        "notification_led",
        true
    )

    val bpNotificationSound = BooleanPref(
        "notification_sound",
        true
    )

    val bpNotificationVibration = BooleanPref(
        "notification_vibration",
        true
    )

    val bpPostButtonBarTop = BooleanPref(
        "post_button_bar_at_top",
        true
    )

    val bpPriorChrome = BooleanPref(
        "prior_chrome",
        true
    )
    val bpDontUseCustomTabs = BooleanPref(
        "DontUseCustomTabs",
        false
    )
    val bpPriorLocalURL = BooleanPref(
        "prior_local_url",
        false
    )

    val bpQuickPostBar = BooleanPref(
        "quick_toot_bar",
        false
    )

    val bpRelativeTimestamp = BooleanPref(
        "relative_timestamp",
        true
    )

    val bpShortAcctLocalUser = BooleanPref(
        "short_acct_local_user",
        true
    )

    val bpShowFollowButtonInButtonBar = BooleanPref(
        "ShowFollowButtonInButtonBar",
        false
    )

    val bpSimpleList = BooleanPref(
        "simple_list",
        true
    )

    val bpUseInternalMediaViewer = BooleanPref(
        "use_internal_media_viewer",
        true
    )

    val bpShowAppName = BooleanPref(
        "show_app_name",
        false
    )
    val bpShowLanguage = BooleanPref(
        "ShowLanguage",
        false
    )
    val bpForceGap = BooleanPref(
        "force_gap",
        false
    )

    val bpShareViewPool = BooleanPref(
        "ShareViewPool",
        true
    )

    val bpAllowColumnDuplication = BooleanPref(
        "AllowColumnDuplication",
        true
    )

    val bpAppendAttachmentUrlToContent = BooleanPref(
        "AppendAttachmentUrlToContent",
        false
    )

    val bpVerticalArrangeThumbnails = BooleanPref(
        "VerticalArrangeThumbnails",
        false
    )

    val bpDontShowPreviewCard = BooleanPref(
        "DontShowPreviewCard",
        false
    )

    val bpScrollTopFromColumnStrip = BooleanPref(
        "ScrollTopFromColumnStrip",
        false
    )

    val bpOpenSticker = BooleanPref(
        "InstanceTicker", // 歴史的な理由でキー名が異なる
        false
    )

    val bpLinksInContextMenu = BooleanPref(
        "LinksInContextMenu",
        false
    )
    val bpMoveNotificationsQuickFilter = BooleanPref(
        "MoveNotificationsQuickFilter",
        false
    )
    val bpShowAcctInSystemNotification = BooleanPref(
        "ShowAcctInSystemNotification",
        false
    )
    val bpShowLinkUnderline = BooleanPref(
        "ShowLinkUnderline",
        false
    )

    val bpShowSearchClear = BooleanPref(
        "ShowSearchClear",
        false
    )

    val bpDontRemoveDeletedToot = BooleanPref(
        "DontRemoveDeletedToot",
        false
    )

    val bpDontShowColumnBackgroundImage = BooleanPref(
        "DontShowColumnBackgroundImage",
        false
    )

    val bpCustomEmojiSeparatorZwsp = BooleanPref(
        "CustomEmojiSeparatorZwsp",
        false
    )

    val bpShowTranslateButton = BooleanPref(
        "ShowTranslateButton",
        false
    )

    val bpDirectoryLastActive = BooleanPref(
        "DirectoryLastActive",
        true
    )

    val bpDirectoryFollowers = BooleanPref(
        "DirectoryFollowers",
        true
    )

    val bpDirectoryTootCount = BooleanPref(
        "DirectoryTootCount",
        true
    )
    val bpDirectoryNote = BooleanPref(
        "DirectoryNote",
        true
    )

    val bpWarnHashtagAsciiAndNonAscii = BooleanPref(
        "WarnHashtagAsciiAndNonAscii",
        false
    )

    val bpEnablePixelfed = BooleanPref(
        "EnablePixelfed",
        false
    )

    val bpQuickTootOmitAccountSelection = BooleanPref(
        "QuickTootOmitAccountSelection",
        false
    )

    val bpSeparateReplyNotificationGroup = BooleanPref(
        "SeparateReplyNotificationGroup",
        false
    )

    val bpAlwaysExpandContextMenuItems = BooleanPref(
        "AlwaysExpandContextMenuItems",
        false
    )

    val bpShowBookmarkButton = BooleanPref(
        "ShowBookmarkButton2",
        true
    )

    val bpShowFilteredWord = BooleanPref(
        "ShowFilteredWord",
        false
    )

    val bpEnableDomainTimeline = BooleanPref(
        "EnableDomainTimeline",
        false
    )

    val bpDivideNotification = BooleanPref(
        "DivideNotification",
        false
    )

    val bpHideFollowCount = BooleanPref(
        "HideFollowCount",
        false
    )

    val bpEmojioneShortcode = BooleanPref(
        "EmojioneShortcode",
        true
    )

    val bpEmojiPickerCloseOnSelected = BooleanPref(
        "EmojiPickerCloseOnSelected",
        true
    )

    val bpCheckBetaVersion = BooleanPref(
        "CheckBetaVersion",
        false
    )

    val bpIgnoreTextInSharedMedia = BooleanPref(
        "IgnoreTextInSharedMedia",
        false
    )

    val bpEmojiPickerCategoryOther = BooleanPref(
        "EmojiPickerCategoryOther",
        false
    )

    val bpInAppUnicodeEmoji = BooleanPref(
        "InAppUnicodeEmoji",
        true
    )

    val bpKeepReactionSpace = BooleanPref(
        "KeepReactionSpace",
        false
    )

    val bpMultiWindowPost = BooleanPref(
        "MultiWindowPost",
        false
    )

    val bpManyWindowPost = BooleanPref(
        "ManyWindowPost",
        false
    )

    val bpMfmDecorationEnabled = BooleanPref(
        "MfmDecorationEnabled",
        true
    )
    val bpMfmDecorationShowUnsupportedMarkup = BooleanPref(
        "MfmDecorationShowUnsupportedMarkup",
        true
    )
}

object PrefI {
    // int

    val ipBackButtonAction = IntPref("back_button_action", 0)

    @Suppress("unused")
    const val BACK_ASK_ALWAYS = 0
    const val BACK_CLOSE_COLUMN = 1
    const val BACK_OPEN_COLUMN_LIST = 2
    const val BACK_EXIT_APP = 3

    val ipUiTheme = IntPref("ui_theme", 0)

//	val ipResizeImage = IntPref("resize_image", 4)

    const val RC_SIMPLE = 0
    const val RC_ACTUAL = 1

    @Suppress("unused")
    const val RC_NONE = 2

    val ipRepliesCount = IntPref("RepliesCount", RC_SIMPLE)
    val ipBoostsCount = IntPref("BoostsCount", RC_ACTUAL)
    val ipFavouritesCount = IntPref("FavouritesCount", RC_ACTUAL)

    val ipRefreshAfterToot = IntPref("refresh_after_toot", 0)
    const val RAT_REFRESH_SCROLL = 0

    @Suppress("unused")
    const val RAT_REFRESH_DONT_SCROLL = 1
    const val RAT_DONT_REFRESH = 2

    @Suppress("unused")
    const val VS_BY_ACCOUNT = 0
    const val VS_MASTODON = 1
    const val VS_MISSKEY = 2
    val ipVisibilityStyle = IntPref("ipVisibilityStyle", VS_BY_ACCOUNT)

    val ipAdditionalButtonsPosition =
        IntPref("AdditionalButtonsPosition", AdditionalButtonsPosition.End.idx)

    val ipFooterButtonBgColor = IntPref("footer_button_bg_color", 0)
    val ipFooterButtonFgColor = IntPref("footer_button_fg_color", 0)
    val ipFooterTabBgColor = IntPref("footer_tab_bg_color", 0)
    val ipFooterTabDividerColor = IntPref("footer_tab_divider_color", 0)
    val ipFooterTabIndicatorColor = IntPref("footer_tab_indicator_color", 0)
    val ipListDividerColor = IntPref("listDividerColor", 0)
    val ipLastColumnPos = IntPref("last_column_pos", -1)
    val ipBoostButtonJustify = IntPref("ipBoostButtonJustify", 0) // 0=左,1=中央,2=右

    private const val JWCP_DEFAULT = 0
    const val JWCP_START = 1
    const val JWCP_END = 2
    val ipJustifyWindowContentPortrait =
        IntPref("JustifyWindowContentPortrait", JWCP_DEFAULT) // 0=default,1=start,2=end

    const val GSP_HEAD = 0
    private const val GSP_TAIL = 1
    val ipGapHeadScrollPosition = IntPref("GapHeadScrollPosition", GSP_TAIL)
    val ipGapTailScrollPosition = IntPref("GapTailScrollPosition", GSP_TAIL)

    val ipLinkColor = IntPref("LinkColor", 0)

    val ipSwitchOnColor = IntPref("SwitchOnColor", Color.BLACK or 0x0080ff)

    val ipButtonBoostedColor = IntPref("ButtonBoostedColor", 0)
    val ipButtonFavoritedColor = IntPref("ButtonFavoritedColor", 0)
    val ipButtonBookmarkedColor = IntPref("ButtonBookmarkedColor", 0)
    val ipButtonFollowingColor = IntPref("ButtonFollowingColor", 0)
    val ipButtonFollowRequestColor = IntPref("ButtonFollowRequestColor", 0)
    val ipButtonReactionedColor = IntPref("ButtonReactionedColor", 0)

    val ipStatusBarColor = IntPref("StatusBarColor", 0)
    val ipNavigationBarColor = IntPref("NavigationBarColor", 0)

    val ipTootColorUnlisted = IntPref("ipTootColorUnlisted", 0)
    val ipTootColorFollower = IntPref("ipTootColorFollower", 0)
    val ipTootColorDirectUser = IntPref("ipTootColorDirectUser", 0)
    val ipTootColorDirectMe = IntPref("ipTootColorDirectMe", 0)

    val ipEventBgColorBoost = IntPref("EventBgColorBoost", 0)
    val ipEventBgColorFavourite = IntPref("EventBgColorFavourite", 0)
    val ipEventBgColorBookmark = IntPref("EventBgColorBookmark", 0)
    val ipEventBgColorFollow = IntPref("EventBgColorFollow", 0)
    val ipEventBgColorMention = IntPref("EventBgColorMention", 0)
    val ipEventBgColorUnfollow = IntPref("EventBgColorUnfollow", 0)
    val ipEventBgColorReaction = IntPref("EventBgColorReaction", 0)
    val ipEventBgColorQuote = IntPref("EventBgColorQuote", 0)
    val ipEventBgColorVote = IntPref("EventBgColorVote", 0)
    val ipEventBgColorFollowRequest = IntPref("EventBgColorFollowRequest", 0)
    val ipEventBgColorStatus = IntPref("EventBgColorStatus", 0)

    val ipEventBgColorGap = IntPref("EventBgColorGap", 0)

    val ipCcdHeaderBg = IntPref("ipCcdHeaderBg", 0)
    val ipCcdHeaderFg = IntPref("ipCcdHeaderFg", 0)
    val ipCcdContentBg = IntPref("ipCcdContentBg", 0)
    val ipCcdContentAcct = IntPref("ipCcdContentAcct", 0)
    val ipCcdContentText = IntPref("ipCcdContentText", 0)

    val ipSearchBgColor = IntPref("SearchBgColor", 0)
    val ipAnnouncementsBgColor = IntPref("AnnouncementsBgColor", 0)
    val ipConversationMainTootBgColor = IntPref("ConversationMainTootBgColor", 0)
    val ipVerifiedLinkBgColor = IntPref("VerifiedLinkBgColor", 0)
    val ipVerifiedLinkFgColor = IntPref("VerifiedLinkFgColor", 0)

    //	val ipTrendTagCountShowing = IntPref("TrendTagCountShowing", 0)
    //	const val TTCS_WEEKLY = 0
    //	const val TTCS_DAILY = 1
}

object PrefS {

    // string
    val spColumnWidth = StringPref("ColumnWidth", "")
    val spMediaThumbHeight = StringPref("MediaThumbHeight", "")
    val spClientName = StringPref("client_name", "")
    val spQuoteNameFormat = StringPref("quote_name_format", "")
    val spAutoCWLines = StringPref("auto_cw_lines", "0")
    val spCardDescriptionLength = StringPref("CardDescriptionLength", "64")
    val spAvatarIconSize = StringPref("avatar_icon_size", "48")
    val spNotificationTlIconSize = StringPref("notification_tl_icon_size", "24")
    val spBoostButtonSize = StringPref("BoostButtonSize", "35")
    val spReplyIconSize = StringPref("ReplyIconSize", "24")
    val spHeaderIconSize = StringPref("HeaderIconSize", "24")
    val spStripIconSize = StringPref("StripIconSize", "30")

    // val spMediaSizeMax = StringPref("max_media_size", "8")
    // val spMovieSizeMax = StringPref("max_movie_size", "40")
    // val spMediaSizeMaxPixelfed = StringPref("MediaSizeMaxPixelfed", "15")

    val spTimelineFont = StringPref("timeline_font", "", skipImport = true)
    val spTimelineFontBold = StringPref("timeline_font_bold", "", skipImport = true)
    val spMspUserToken = StringPref("mastodon_search_portal_user_token", "")
    val spEmojiPickerRecent = StringPref("emoji_picker_recent", "")
    val spRoundRatio = StringPref("round_ratio", "33")
    val spBoostAlpha = StringPref("BoostAlpha", "60")

    val spScreenBottomPadding = StringPref("ScreenBottomPadding", "8")

    val spPullNotificationCheckInterval = StringPref("PullNotificationCheckInterval", "15")
    val spUserAgent = StringPref("UserAgent", "")

    val spMediaReadTimeout = StringPref("spMediaReadTimeout", "60")
    val spAgreedPrivacyPolicyDigest = StringPref("spAgreedPrivacyPolicyDigest", "")

    val spTimeZone = StringPref("TimeZone", "")

    val spQuickTootMacro = StringPref("QuickTootMacro", "")
    val spQuickTootVisibility = StringPref("QuickTootVisibility", "")

    val spTranslateAppComponent = StringPref("TranslateAppComponent", "")
    val spCustomShare1 = StringPref("CustomShare1", "")
    val spCustomShare2 = StringPref("CustomShare2", "")
    val spCustomShare3 = StringPref("CustomShare3", "")
    // val spWebBrowser = StringPref("WebBrowser", "")

    val spTimelineSpacing = StringPref("TimelineSpacing", "")
}

object PrefL {

    // long
    val lpTabletTootDefaultAccount = LongPref("tablet_toot_default_account", -1L)
}

object PrefF {
    // float

    val fpTimelineFontSize = FloatPref("timeline_font_size", Float.NaN)
    val fpAcctFontSize = FloatPref("acct_font_size", Float.NaN)
    val fpNotificationTlFontSize = FloatPref("notification_tl_font_size", Float.NaN)
    val fpHeaderTextSize = FloatPref("HeaderTextSize", Float.NaN)
    internal const val default_timeline_font_size = 14f
    internal const val default_acct_font_size = 12f
    internal const val default_notification_tl_font_size = 14f
    internal const val default_header_font_size = 14f
}
