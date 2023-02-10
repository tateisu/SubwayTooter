package jp.juggler.subwaytooter.pref

import jp.juggler.subwaytooter.pref.impl.BooleanPref

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
        true
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

    val bpImageAnimationEnable = BooleanPref(
        "enable_gif_animation",
        false
    )

    val bpExitAppWhenCloseProtectedColumn = BooleanPref(
        "ExitAppWhenCloseProtectedColumn",
        true
    )

    val bpMentionFullAcct = BooleanPref(
        "mention_full_acct",
        false
    )

//    val bpNotificationLED = BooleanPref(
//        "notification_led",
//        true
//    )

//    val bpNotificationSound = BooleanPref(
//        "notification_sound",
//        true
//    )

//    val bpNotificationVibration = BooleanPref(
//        "notification_vibration",
//        true
//    )

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
        true
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

    val bpUseTwemoji = BooleanPref(
        "UseTwemoji",
        false
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
    val bpTabletSnap = BooleanPref(
        "bpTabletSnap",
        true
    )

    val bpMfmDecorationEnabled = BooleanPref(
        "MfmDecorationEnabled",
        true
    )
    val bpMfmDecorationShowUnsupportedMarkup = BooleanPref(
        "MfmDecorationShowUnsupportedMarkup",
        true
    )
    val bpMisskeyNotificationCheck = BooleanPref(
        "MisskeyNotificationCheck",
        false
    )

    val bpShowUsernameFilteredPost = BooleanPref(
        "ShowUsernameFilteredPost",
        false
    )
}
