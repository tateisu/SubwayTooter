package jp.juggler.subwaytooter.pref

import jp.juggler.subwaytooter.pref.impl.StringPref

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
    val spApiReadTimeout = StringPref("spApiReadTimeout", "60")

    val spAgreedPrivacyPolicyDigest = StringPref("spAgreedPrivacyPolicyDigest", "")

    val spTimeZone = StringPref("TimeZone", "")

    val spQuickTootMacro = StringPref("QuickTootMacro", "")
    val spQuickTootVisibility = StringPref("QuickTootVisibility", "")

    val spTranslateAppComponent = StringPref("TranslateAppComponent", "")
    val spCustomShare1 = StringPref("CustomShare1", "")
    val spCustomShare2 = StringPref("CustomShare2", "")
    val spCustomShare3 = StringPref("CustomShare3", "")
    val spCustomShare4 = StringPref("CustomShare4", "")
    val spCustomShare5 = StringPref("CustomShare5", "")
    val spCustomShare6 = StringPref("CustomShare6", "")
    val spCustomShare7 = StringPref("CustomShare7", "")
    val spCustomShare8 = StringPref("CustomShare8", "")
    val spCustomShare9 = StringPref("CustomShare9", "")
    val spCustomShare10 = StringPref("CustomShare10", "")

    // val spWebBrowser = StringPref("WebBrowser", "")

    val spTimelineSpacing = StringPref("TimelineSpacing", "")

    val spEventTextAlpha = StringPref("EventTextAlpha", "")

    val spEmojiSizeMastodon = StringPref("EmojiSizeMastodon", "100")
    val spEmojiSizeMisskey = StringPref("EmojiSizeMisskey", "250")
    val spEmojiSizeReaction = StringPref("EmojiSizeReaction", "150")
    val spEmojiSizeUserName = StringPref("EmojiSizeUserName", "100")
    val spEmojiPixels = StringPref("spEmojiPixels", "128")
}
