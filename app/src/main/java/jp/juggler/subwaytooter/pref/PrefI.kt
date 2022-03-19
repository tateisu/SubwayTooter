package jp.juggler.subwaytooter.pref

import android.graphics.Color
import jp.juggler.subwaytooter.itemviewholder.AdditionalButtonsPosition
import jp.juggler.subwaytooter.pref.impl.IntPref

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
    val ipEventBgColorUpdate = IntPref("EventBgColorUpdate", 0)
    val ipEventBgColorStatusReference = IntPref("EventBgColorStatusReference", 0)

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

    val ipMediaBackground = IntPref("MediaBackground", 1)
}
