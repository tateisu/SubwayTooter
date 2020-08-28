package jp.juggler.subwaytooter

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.view.View
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatImageView
import jp.juggler.subwaytooter.action.CustomShareTarget
import jp.juggler.util.*

enum class SettingType(val id : Int) {
	Path(0),
	Divider(1),
	Switch(2),
	EditText(3),
	Spinner(4),
	ColorOpaque(5),
	ColorAlpha(6),
	Action(7),
	Sample(8),
	Group(9),
	TextWithSelector(10),
	CheckBox(11),
	Section(12)
}

class AppSettingItem(
	val parent : AppSettingItem?,
	val type : SettingType,
	@StringRes val caption : Int,
	val pref : BasePref<*>? = null
) {
	
	@StringRes
	var desc : Int = 0
	var descClickSet = false
	var descClick : ActAppSetting.() -> Unit = {}
		set(value) {
			field = value
			descClickSet = true
		}
	
	var getError : ActAppSetting.(String) -> String? = { null }
	
	// may be open exportAppData() or importAppData()
	var action : ActAppSetting.() -> Unit = {}
	
	var changed : ActAppSetting.() -> Unit = {}
	
	// used for EditText
	var inputType = InputTypeEx.text
	
	var sampleLayoutId : Int = 0
	var sampleUpdate : (ActAppSetting, View) -> Unit = { _, _ -> }
	
	var spinnerArgs : IntArray? = null
	var spinnerArgsProc : (ActAppSetting) -> List<String> = { _ -> emptyList() }
	var spinnerInitializer : ActAppSetting.(Spinner) -> Unit = {}
	var spinnerOnSelected : ActAppSetting.(Spinner, Int) -> Unit = { _, _ -> }
	
	var enabled : Boolean = true
	
	var onClickEdit : ActAppSetting.() -> Unit = {}
	var onClickReset : ActAppSetting.() -> Unit = {}
	var showTextView : ActAppSetting.(TextView) -> Unit = {}
	
	// for EditText
	var hint : String? = null
	var filter : (String) -> String = { it.trim() }
	var captionFontSize : ActAppSetting.() -> Float? = { null }
	var captionSpacing : ActAppSetting.() -> Float? = { null }
	
	// cast before save
	var toFloat : ActAppSetting.(String) -> Float = { 0f }
	var fromFloat : ActAppSetting.(Float) -> String = { it.toString() }
	
	val items = ArrayList<AppSettingItem>()
	
	fun section(
		@StringRes caption : Int,
		initializer : AppSettingItem.() -> Unit = {}
	) {
		items.add(AppSettingItem(this, SettingType.Section, caption).apply { initializer() })
	}
	
	fun group(
		@StringRes caption : Int,
		initializer : AppSettingItem.() -> Unit = {}
	) {
		items.add(AppSettingItem(this, SettingType.Group, caption).apply { initializer() })
	}
	
	fun item(
		type : SettingType,
		pref : BasePref<*>?,
		@StringRes caption : Int,
		initializer : AppSettingItem.() -> Unit = {}
	) : AppSettingItem {
		val item = AppSettingItem(this, type, caption, pref).apply { initializer() }
		items.add(item)
		return item
	}
	
	fun spinner(
		pref : IntPref,
		@StringRes caption : Int,
		vararg args : Int
	) = item(SettingType.Spinner, pref, caption) {
		spinnerArgs = args
	}
	
	fun spinner(
		pref : IntPref,
		@StringRes caption : Int,
		argsProc : (ActAppSetting) -> List<String>
	) = item(SettingType.Spinner, pref, caption) {
		spinnerArgsProc = argsProc
	}
	
	fun sw(
		pref : BooleanPref,
		@StringRes caption : Int,
		initializer : AppSettingItem.() -> Unit = {}
	) = item(SettingType.Switch, pref, caption, initializer)
	
	fun checkbox(
		pref : BooleanPref,
		@StringRes caption : Int,
		initializer : AppSettingItem.() -> Unit = {}
	) = item(SettingType.CheckBox, pref, caption, initializer)
	
	fun action(
		@StringRes caption : Int,
		initializer : AppSettingItem.() -> Unit = {}
	) = item(SettingType.Action, null, caption, initializer)
	
	fun colorOpaque(
		pref : IntPref,
		@StringRes caption : Int,
		initializer : AppSettingItem.() -> Unit = {}
	) = item(SettingType.ColorOpaque, pref, caption, initializer)
	
	fun colorAlpha(
		pref : IntPref,
		@StringRes caption : Int,
		initializer : AppSettingItem.() -> Unit = {}
	) = item(SettingType.ColorAlpha, pref, caption, initializer)
	
	fun text(
		pref : StringPref,
		@StringRes caption : Int,
		inputType : Int,
		initializer : AppSettingItem.() -> Unit = {}
	) = item(SettingType.EditText, pref, caption) {
		this.inputType = inputType
		this.initializer()
	}
	
	fun textX(
		pref : BasePref<*>,
		@StringRes caption : Int,
		inputType : Int,
		initializer : AppSettingItem.() -> Unit = {}
	) = item(SettingType.EditText, pref, caption) {
		this.inputType = inputType
		this.initializer()
	}
	
	fun sample(
		sampleLayoutId : Int = 0,
		sampleUpdate : (ActAppSetting, View) -> Unit = { _, _ -> }
		// ,initializer : AppSettingItem.() -> Unit = {}
	) = item(SettingType.Sample, pref, caption) {
		this.sampleLayoutId = sampleLayoutId
		this.sampleUpdate = sampleUpdate
	}
	
	fun scan(block : (AppSettingItem) -> Unit) {
		block(this)
		for(item in items) item.scan(block)
	}
	
	companion object {
		
		var SAMPLE_CCD_HEADER : AppSettingItem? = null
		var SAMPLE_CCD_BODY : AppSettingItem? = null
		var SAMPLE_FOOTER : AppSettingItem? = null
		
		var CUSTOM_TRANSLATE : AppSettingItem? = null
		var CUSTOM_SHARE_1 : AppSettingItem? = null
		var CUSTOM_SHARE_2 : AppSettingItem? = null
		var CUSTOM_SHARE_3 : AppSettingItem? = null
		
		var TIMELINE_FONT : AppSettingItem? = null
		var TIMELINE_FONT_BOLD : AppSettingItem? = null
		
		var FONT_SIZE_TIMELINE : AppSettingItem? = null
		var FONT_SIZE_NOTIFICATION_TL : AppSettingItem? = null
	}
}

val appSettingRoot = AppSettingItem(null, SettingType.Section, R.string.app_setting).apply {
	
	section(R.string.notifications) {
		
		group(R.string.notification_style_before_oreo) {
			
			checkbox(Pref.bpNotificationSound, R.string.sound) {
				enabled = Build.VERSION.SDK_INT < 26
			}
			
			checkbox(Pref.bpNotificationVibration, R.string.vibration) {
				enabled = Build.VERSION.SDK_INT < 26
			}
			
			checkbox(Pref.bpNotificationLED, R.string.led) {
				enabled = Build.VERSION.SDK_INT < 26
			}
			
			sample(R.layout.setting_sample_notification_desc)
		}
		
		text(
			Pref.spPullNotificationCheckInterval,
			R.string.pull_notification_check_interval,
			InputTypeEx.number
		)
		
		sw(Pref.bpShowAcctInSystemNotification, R.string.show_acct_in_system_notification)
		
		sw(Pref.bpSeparateReplyNotificationGroup, R.string.separate_notification_group_for_reply) {
			enabled = Build.VERSION.SDK_INT >= 26
		}
		
		sw(Pref.bpDivideNotification, R.string.divide_notification)
	}
	
	section(R.string.behavior) {
		
		sw(Pref.bpDontConfirmBeforeCloseColumn, R.string.dont_confirm_before_close_column)
		
		spinner(
			Pref.ipBackButtonAction,
			R.string.back_button_action,
			R.string.ask_always,
			R.string.close_column,
			R.string.open_column_list,
			R.string.app_exit
		)
		
		sw(Pref.bpExitAppWhenCloseProtectedColumn, R.string.exit_app_when_close_protected_column)
		sw(Pref.bpScrollTopFromColumnStrip, R.string.scroll_top_from_column_strip)
		sw(Pref.bpDontScreenOff, R.string.dont_screen_off)
		sw(Pref.bpDontUseCustomTabs, R.string.dont_use_custom_tabs)
		sw(Pref.bpPriorChrome, R.string.prior_chrome_custom_tabs)
		sw(Pref.bpAllowColumnDuplication, R.string.allow_column_duplication)
		sw(Pref.bpForceGap, R.string.force_gap_when_refresh)
		
		text(Pref.spClientName, R.string.client_name, InputTypeEx.text)
		
		text(Pref.spUserAgent, R.string.user_agent, InputTypeEx.textMultiLine) {
			hint = App1.userAgentDefault
			filter = { it.replace(ActAppSetting.reLinefeed, " ").trim() }
			getError = {
				val m = App1.reNotAllowedInUserAgent.matcher(it)
				when(m.find()) {
					true -> getString(R.string.user_agent_error, m.group())
					else -> null
				}
			}
		}
		
		sw(Pref.bpDontRemoveDeletedToot, R.string.dont_remove_deleted_toot_from_timeline)
		sw(Pref.bpCustomEmojiSeparatorZwsp, R.string.custom_emoji_separator_zwsp)
		sw(Pref.bpShowTranslateButton, R.string.show_translate_button)
		
		AppSettingItem.CUSTOM_TRANSLATE = item(
			SettingType.TextWithSelector,
			Pref.spTranslateAppComponent,
			R.string.translation_app
		) {
			val target = CustomShareTarget.Translate
			onClickEdit = { openCustomShareChooser(target) }
			onClickReset = { setCustomShare(target, "") }
			showTextView = { showCustomShareIcon(it, target) }
		}
		
		AppSettingItem.CUSTOM_SHARE_1 = item(
			SettingType.TextWithSelector,
			Pref.spCustomShare1,
			R.string.custom_share_button_1
		) {
			val target = CustomShareTarget.CustomShare1
			onClickEdit = { openCustomShareChooser(target) }
			onClickReset = { setCustomShare(target, "") }
			showTextView = { showCustomShareIcon(it, target) }
		}
		AppSettingItem.CUSTOM_SHARE_2 = item(
			SettingType.TextWithSelector,
			Pref.spCustomShare2,
			R.string.custom_share_button_2
		) {
			val target = CustomShareTarget.CustomShare2
			onClickEdit = { openCustomShareChooser(target) }
			onClickReset = { setCustomShare(target, "") }
			showTextView = { showCustomShareIcon(it, target) }
		}
		AppSettingItem.CUSTOM_SHARE_3 = item(
			SettingType.TextWithSelector,
			Pref.spCustomShare3,
			R.string.custom_share_button_3
		) {
			val target = CustomShareTarget.CustomShare3
			onClickEdit = { openCustomShareChooser(target) }
			onClickReset = { setCustomShare(target, "") }
			showTextView = { showCustomShareIcon(it, target) }
		}
		
		spinner(
			Pref.ipAdditionalButtonsPosition,
			R.string.additional_buttons_position,
			R.string.top,
			R.string.bottom,
			R.string.start,
			R.string.end
		)
		
		sw(Pref.bpEnablePixelfed, R.string.enable_connect_to_pixelfed_server)
		sw(Pref.bpShowFilteredWord, R.string.show_filtered_word)
		sw(Pref.bpEnableDomainTimeline, R.string.enable_domain_timeline)
	}
	
	section(R.string.post) {
		
		spinner(Pref.ipResizeImage, R.string.resize_image) { activity ->
			ActPost.resizeConfigList.map {
				when(it.type) {
					ResizeType.None -> activity.getString(R.string.dont_resize)
					ResizeType.LongSide -> activity.getString(R.string.long_side_pixel, it.size)
					ResizeType.SquarePixel -> activity.getString(
						R.string.resize_square_pixels,
						it.size * it.size,
						it.size
					)
				}
			}
		}
		
		text(Pref.spMediaSizeMax, R.string.media_attachment_max_byte_size, InputTypeEx.number)
		text(Pref.spMovieSizeMax, R.string.media_attachment_max_byte_size_movie, InputTypeEx.number)
		text(
			Pref.spMediaSizeMaxPixelfed,
			R.string.media_attachment_max_byte_size_pixelfed,
			InputTypeEx.number
		)
		
		spinner(
			Pref.ipRefreshAfterToot,
			R.string.refresh_after_toot,
			R.string.refresh_scroll_to_toot,
			R.string.refresh_no_scroll,
			R.string.dont_refresh
		)
		
		sw(Pref.bpPostButtonBarTop, R.string.show_post_button_bar_top)
		
		sw(
			Pref.bpDontDuplicationCheck,
			R.string.dont_add_duplication_check_header
		)
		
		sw(Pref.bpQuickTootBar, R.string.show_quick_toot_bar)
		
		sw(
			Pref.bpDontUseActionButtonWithQuickTootBar,
			R.string.dont_use_action_button_with_quick_toot_bar
		)
		
		text(Pref.spQuoteNameFormat, R.string.format_of_quote_name, InputTypeEx.text) {
			filter = { it } // don't trim
		}
		
		sw(
			Pref.bpAppendAttachmentUrlToContent,
			R.string.append_attachment_url_to_content
		)
		
		sw(
			Pref.bpWarnHashtagAsciiAndNonAscii,
			R.string.warn_hashtag_ascii_and_non_ascii
		)
		
		sw(
			Pref.bpEmojiPickerCloseOnSelected,
			R.string.close_emoji_picker_when_selected
		)
	}
	
	section(R.string.tablet_mode) {
		
		sw(Pref.bpDisableTabletMode, R.string.disable_tablet_mode)
		
		text(Pref.spColumnWidth, R.string.minimum_column_width, InputTypeEx.number)
		
		item(
			SettingType.Spinner,
			Pref.lpTabletTootDefaultAccount,
			R.string.toot_button_default_account
		) {
			val lp = pref.cast<LongPref>() !!
			spinnerInitializer = { spinner ->
				val adapter = AccountAdapter()
				spinner.adapter = adapter
				spinner.setSelection(adapter.getIndexFromId(lp(pref)))
			}
			spinnerOnSelected = { spinner, index ->
				val adapter = spinner.adapter.cast<ActAppSetting.AccountAdapter>()
					?: error("spinnerOnSelected: missing AccountAdapter")
				pref.edit().put(lp, adapter.getIdFromIndex(index)).apply()
			}
		}
		
		sw(
			Pref.bpQuickTootOmitAccountSelection,
			R.string.quick_toot_omit_account_selection
		)
		
		spinner(
			Pref.ipJustifyWindowContentPortrait,
			R.string.justify_window_content_portrait,
			R.string.default_,
			R.string.start,
			R.string.end
		)
	}
	
	section(R.string.media_attachment) {
		sw(Pref.bpUseInternalMediaViewer, R.string.use_internal_media_viewer)
		sw(Pref.bpPriorLocalURL, R.string.prior_local_url_when_open_attachment)
		text(Pref.spMediaThumbHeight, R.string.media_thumbnail_height, InputTypeEx.number)
		sw(Pref.bpDontCropMediaThumb, R.string.dont_crop_media_thumbnail)
		sw(Pref.bpVerticalArrangeThumbnails, R.string.thumbnails_arrange_vertically)
	}
	
	section(R.string.animation) {
		sw(Pref.bpEnableGifAnimation, R.string.enable_gif_animation)
		sw(Pref.bpDisableEmojiAnimation, R.string.disable_custom_emoji_animation)
	}
	
	section(R.string.appearance) {
		sw(Pref.bpSimpleList, R.string.simple_list)
		sw(Pref.bpShowFollowButtonInButtonBar, R.string.show_follow_button_in_button_bar)
		sw(Pref.bpDontShowPreviewCard, R.string.dont_show_preview_card)
		sw(Pref.bpShortAcctLocalUser, R.string.short_acct_local_user)
		sw(Pref.bpMentionFullAcct, R.string.mention_full_acct)
		sw(Pref.bpRelativeTimestamp, R.string.relative_timestamp)
		
		item(
			SettingType.Spinner,
			Pref.spTimeZone,
			R.string.timezone
		) {
			val sp : StringPref = pref.cast() !!
			spinnerInitializer = { spinner ->
				val adapter = TimeZoneAdapter()
				spinner.adapter = adapter
				spinner.setSelection(adapter.getIndexFromId(sp(pref)))
			}
			spinnerOnSelected = { spinner, index ->
				val adapter = spinner.adapter.cast<ActAppSetting.TimeZoneAdapter>()
					?: error("spinnerOnSelected: missing TimeZoneAdapter")
				pref.edit().put(sp, adapter.getIdFromIndex(index)).apply()
			}
		}
		
		sw(Pref.bpShowAppName, R.string.always_show_application)
		sw(Pref.bpShowLanguage, R.string.always_show_language)
		text(Pref.spAutoCWLines, R.string.auto_cw, InputTypeEx.number)
		text(Pref.spCardDescriptionLength, R.string.card_description_length, InputTypeEx.number)
		
		spinner(
			Pref.ipRepliesCount,
			R.string.display_replies_count,
			R.string.replies_count_simple,
			R.string.replies_count_actual,
			R.string.replies_count_none
		)
		spinner(
			Pref.ipBoostsCount,
			R.string.display_boost_count,
			R.string.replies_count_simple,
			R.string.replies_count_actual,
			R.string.replies_count_none
		)
		spinner(
			Pref.ipFavouritesCount,
			R.string.display_favourite_count,
			R.string.replies_count_simple,
			R.string.replies_count_actual,
			R.string.replies_count_none
		)
		
		spinner(
			Pref.ipVisibilityStyle,
			R.string.visibility_style,
			R.string.visibility_style_by_account,
			R.string.mastodon,
			R.string.misskey
		)
		
		AppSettingItem.TIMELINE_FONT = item(
			SettingType.TextWithSelector,
			Pref.spTimelineFont,
			R.string.timeline_font
		) {
			val item = this
			onClickEdit = {
				try {
					val intent = intentOpenDocument("*/*")
					startActivityForResult(intent, ActAppSetting.REQUEST_CODE_TIMELINE_FONT)
				} catch(ex : Throwable) {
					showToast(this, ex, "could not open picker for font")
				}
			}
			onClickReset = {
				pref.edit().remove(item.pref?.key).apply()
				showTimelineFont(item)
			}
			showTextView = { showTimelineFont(item, it) }
		}
		
		
		AppSettingItem.TIMELINE_FONT_BOLD = item(
			SettingType.TextWithSelector,
			Pref.spTimelineFontBold,
			R.string.timeline_font_bold
		) {
			val item = this
			onClickEdit = {
				try {
					val intent = intentOpenDocument("*/*")
					startActivityForResult(intent, ActAppSetting.REQUEST_CODE_TIMELINE_FONT_BOLD)
				} catch(ex : Throwable) {
					showToast(this, ex, "could not open picker for font")
				}
			}
			onClickReset = {
				pref.edit().remove(item.pref?.key).apply()
				showTimelineFont(AppSettingItem.TIMELINE_FONT_BOLD)
			}
			showTextView = { showTimelineFont(item, it) }
		}
		
		AppSettingItem.FONT_SIZE_TIMELINE = textX(
			Pref.fpTimelineFontSize,
			R.string.timeline_font_size,
			InputTypeEx.numberDecimal
		) {
			
			val item = this
			val fp : FloatPref = item.pref.cast() !!
			
			toFloat = { parseFontSize(it) }
			fromFloat = { formatFontSize(it) }
			
			captionFontSize = {
				val fv = fp(pref)
				when {
					! fv.isFinite() -> Pref.default_timeline_font_size
					fv < 1f -> 1f
					else -> fv
				}
			}
			captionSpacing = {
				Pref.spTimelineSpacing(pref).toFloatOrNull()
			}
			changed = {
				findItemViewHolder(item)?.updateCaption()
			}
		}
		
		textX(Pref.fpAcctFontSize, R.string.acct_font_size, InputTypeEx.numberDecimal) {
			val item = this
			val fp : FloatPref = item.pref.cast() !!
			
			toFloat = { parseFontSize(it) }
			fromFloat = { formatFontSize(it) }
			
			captionFontSize = {
				val fv = fp(pref)
				when {
					! fv.isFinite() -> Pref.default_acct_font_size
					fv < 1f -> 1f
					else -> fv
				}
			}
			
			changed = { findItemViewHolder(item)?.updateCaption() }
		}
		
		AppSettingItem.FONT_SIZE_NOTIFICATION_TL = textX(
			Pref.fpNotificationTlFontSize,
			R.string.notification_tl_font_size,
			InputTypeEx.numberDecimal
		) {
			val item = this
			val fp : FloatPref = item.pref.cast() !!
			
			toFloat = { parseFontSize(it) }
			fromFloat = { formatFontSize(it) }
			
			captionFontSize = {
				val fv = fp(pref)
				when {
					! fv.isFinite() -> Pref.default_notification_tl_font_size
					fv < 1f -> 1f
					else -> fv
				}
			}
			captionSpacing = {
				Pref.spTimelineSpacing(pref).toFloatOrNull()
			}
			changed = {
				findItemViewHolder(item)?.updateCaption()
			}
		}
		
		text(
			Pref.spNotificationTlIconSize,
			R.string.notification_tl_icon_size,
			InputTypeEx.numberDecimal
		)
		
		text(Pref.spTimelineSpacing, R.string.timeline_line_spacing, InputTypeEx.numberDecimal) {
			changed = {
				findItemViewHolder(AppSettingItem.FONT_SIZE_TIMELINE)?.updateCaption()
				findItemViewHolder(AppSettingItem.FONT_SIZE_NOTIFICATION_TL)?.updateCaption()
			}
		}
		
		text(Pref.spBoostButtonSize, R.string.boost_button_size, InputTypeEx.numberDecimal)
		
		spinner(
			Pref.ipBoostButtonJustify,
			R.string.boost_button_alignment,
			R.string.start,
			R.string.center,
			R.string.end
		)
		
		text(Pref.spAvatarIconSize, R.string.avatar_icon_size, InputTypeEx.numberDecimal)
		text(Pref.spRoundRatio, R.string.avatar_icon_round_ratio, InputTypeEx.numberDecimal)
		sw(Pref.bpDontRound, R.string.avatar_icon_dont_round)
		text(Pref.spReplyIconSize, R.string.reply_icon_size, InputTypeEx.numberDecimal)
		text(Pref.spHeaderIconSize, R.string.header_icon_size, InputTypeEx.numberDecimal)
		textX(Pref.fpHeaderTextSize, R.string.header_text_size, InputTypeEx.numberDecimal) {
			val item = this
			val fp : FloatPref = item.pref.cast() !!
			
			toFloat = { parseFontSize(it) }
			fromFloat = { formatFontSize(it) }
			
			captionFontSize = {
				val fv = fp(pref)
				when {
					! fv.isFinite() -> Pref.default_header_font_size
					fv < 1f -> 1f
					else -> fv
				}
			}
			
			changed = {
				findItemViewHolder(item)?.updateCaption()
			}
		}
		
		text(Pref.spStripIconSize, R.string.strip_icon_size, InputTypeEx.numberDecimal)
		
		text(Pref.spScreenBottomPadding, R.string.screen_bottom_padding, InputTypeEx.numberDecimal)
		
		
		
		sw(Pref.bpInstanceTicker, R.string.show_instance_ticker) {
			desc = R.string.instance_ticker_copyright
			descClick = {
				App1.openBrowser(
					this,
					"https://github.com/MiyonMiyon/InstanceTicker_List"
				)
			}
		}
		
		sw(Pref.bpLinksInContextMenu, R.string.show_links_in_context_menu)
		sw(Pref.bpShowLinkUnderline, R.string.show_link_underline)
		sw(
			Pref.bpMoveNotificationsQuickFilter,
			R.string.move_notifications_quick_filter_to_column_setting
		)
		sw(Pref.bpShowSearchClear, R.string.show_clear_button_in_search_bar)
		sw(
			Pref.bpDontShowColumnBackgroundImage,
			R.string.dont_show_column_background_image
		)
		
		group(R.string.show_in_directory) {
			checkbox(Pref.bpDirectoryLastActive, R.string.last_active)
			checkbox(Pref.bpDirectoryFollowers, R.string.followers)
			checkbox(Pref.bpDirectoryTootCount, R.string.toot_count)
			checkbox(Pref.bpDirectoryNote, R.string.note)
		}
		
		sw(
			Pref.bpAlwaysExpandContextMenuItems,
			R.string.always_expand_context_menu_sub_items
		)
		sw(Pref.bpShowBookmarkButton, R.string.show_bookmark_button)
		sw(Pref.bpHideFollowCount, R.string.hide_followers_count)
		sw(Pref.bpEmojioneShortcode, R.string.emojione_shortcode_support) {
			desc = R.string.emojione_shortcode_support_desc
		}
	}
	
	section(R.string.color) {
		
		spinner(
			Pref.ipUiTheme,
			R.string.ui_theme,
			R.string.theme_light,
			R.string.theme_dark
		)
		
		colorAlpha(Pref.ipListDividerColor, R.string.list_divider_color)
		colorAlpha(Pref.ipLinkColor, R.string.link_color)
		
		group(R.string.toot_background_color) {
			colorAlpha(Pref.ipTootColorUnlisted, R.string.unlisted_visibility)
			colorAlpha(Pref.ipTootColorFollower, R.string.followers_visibility)
			colorAlpha(Pref.ipTootColorDirectUser, R.string.direct_with_user_visibility)
			colorAlpha(Pref.ipTootColorDirectMe, R.string.direct_only_me_visibility)
		}
		
		group(R.string.event_background_color) {
			colorAlpha(Pref.ipEventBgColorBoost, R.string.boost)
			colorAlpha(Pref.ipEventBgColorFavourite, R.string.favourites)
			colorAlpha(Pref.ipEventBgColorBookmark, R.string.bookmarks)
			colorAlpha(Pref.ipEventBgColorMention, R.string.reply)
			colorAlpha(Pref.ipEventBgColorFollow, R.string.follow)
			colorAlpha(Pref.ipEventBgColorUnfollow, R.string.unfollow_misskey)
			colorAlpha(Pref.ipEventBgColorFollowRequest, R.string.follow_request)
			colorAlpha(Pref.ipEventBgColorReaction, R.string.reaction)
			colorAlpha(Pref.ipEventBgColorQuote, R.string.quote_renote)
			colorAlpha(Pref.ipEventBgColorVote, R.string.vote_polls)
			colorAlpha(
				Pref.ipConversationMainTootBgColor,
				R.string.conversation_main_toot_background_color
			)
		}
		
		group(R.string.button_accent_color) {
			colorAlpha(Pref.ipButtonBoostedColor, R.string.boost)
			colorAlpha(Pref.ipButtonFavoritedColor, R.string.favourites)
			colorAlpha(Pref.ipButtonBookmarkedColor, R.string.bookmarks)
			colorAlpha(Pref.ipButtonFollowingColor, R.string.follow)
			colorAlpha(Pref.ipButtonFollowRequestColor, R.string.follow_request)
		}
		
		group(R.string.column_color_default) {
			AppSettingItem.SAMPLE_CCD_HEADER =
				sample(R.layout.setting_sample_column_header) { activity, viewRoot ->
					
					val llColumnHeader : View = viewRoot.findViewById(R.id.llColumnHeader)
					val ivColumnHeader : ImageView = viewRoot.findViewById(R.id.ivColumnHeader)
					val tvColumnName : TextView = viewRoot.findViewById(R.id.tvColumnName)
					
					val color_column_header_bg = Pref.ipCcdHeaderBg(activity.pref)
					val color_column_header_fg = Pref.ipCcdHeaderFg(activity.pref)
					
					val header_bg = when {
						color_column_header_bg != 0 -> color_column_header_bg
						else -> getAttributeColor(activity, R.attr.color_column_header)
					}
					
					val header_fg = when {
						color_column_header_fg != 0 -> color_column_header_fg
						else -> getAttributeColor(activity, R.attr.colorColumnHeaderName)
					}
					
					llColumnHeader.background = getAdaptiveRippleDrawable(header_bg, header_fg)
					
					tvColumnName.setTextColor(header_fg)
					ivColumnHeader.setImageResource(R.drawable.ic_bike)
					ivColumnHeader.imageTintList = ColorStateList.valueOf(header_fg)
				}
			
			colorOpaque(Pref.ipCcdHeaderBg, R.string.header_background_color) {
				changed = { showSample(AppSettingItem.SAMPLE_CCD_HEADER) }
			}
			colorOpaque(Pref.ipCcdHeaderFg, R.string.header_foreground_color) {
				changed = { showSample(AppSettingItem.SAMPLE_CCD_HEADER) }
			}
			
			AppSettingItem.SAMPLE_CCD_BODY =
				sample(R.layout.setting_sample_column_body) { activity, viewRoot ->
					val flColumnBackground : View = viewRoot.findViewById(R.id.flColumnBackground)
					val tvSampleAcct : TextView = viewRoot.findViewById(R.id.tvSampleAcct)
					val tvSampleContent : TextView = viewRoot.findViewById(R.id.tvSampleContent)
					
					val color_column_bg = Pref.ipCcdContentBg(activity.pref)
					val color_column_acct = Pref.ipCcdContentAcct(activity.pref)
					val color_column_text = Pref.ipCcdContentText(activity.pref)
					
					flColumnBackground.setBackgroundColor(color_column_bg) // may 0
					
					tvSampleAcct.setTextColor(
						color_column_acct.notZero()
							?: getAttributeColor(activity, R.attr.colorTimeSmall)
					)
					
					tvSampleContent.setTextColor(
						color_column_text.notZero()
							?: getAttributeColor(activity, R.attr.colorContentText)
					)
				}
			
			colorOpaque(Pref.ipCcdContentBg, R.string.content_background_color) {
				changed = { showSample(AppSettingItem.SAMPLE_CCD_BODY) }
			}
			colorAlpha(Pref.ipCcdContentAcct, R.string.content_acct_color) {
				changed = { showSample(AppSettingItem.SAMPLE_CCD_BODY) }
			}
			colorAlpha(Pref.ipCcdContentText, R.string.content_text_color) {
				changed = { showSample(AppSettingItem.SAMPLE_CCD_BODY) }
			}
		}
		
		text(Pref.spBoostAlpha, R.string.boost_button_alpha, InputTypeEx.numberDecimal)
		
		group(R.string.footer_color) {
			AppSettingItem.SAMPLE_FOOTER =
				sample(R.layout.setting_sample_footer) { activity, viewRoot ->
					val pref = activity.pref
					val footer_button_bg_color = Pref.ipFooterButtonBgColor(pref)
					val footer_button_fg_color = Pref.ipFooterButtonFgColor(pref)
					val footer_tab_bg_color = Pref.ipFooterTabBgColor(pref)
					val footer_tab_divider_color = Pref.ipFooterTabDividerColor(pref)
					val footer_tab_indicator_color = Pref.ipFooterTabIndicatorColor(pref)
					
					val ivFooterToot : AppCompatImageView = viewRoot.findViewById(R.id.ivFooterToot)
					val ivFooterMenu : AppCompatImageView = viewRoot.findViewById(R.id.ivFooterMenu)
					val llFooterBG : View = viewRoot.findViewById(R.id.llFooterBG)
					val vFooterDivider1 : View = viewRoot.findViewById(R.id.vFooterDivider1)
					val vFooterDivider2 : View = viewRoot.findViewById(R.id.vFooterDivider2)
					val vIndicator : View = viewRoot.findViewById(R.id.vIndicator)
					
					val colorBg = footer_button_bg_color.notZero() ?: getAttributeColor(
						activity,
						R.attr.colorStatusButtonsPopupBg
					)
					val colorRipple =
						footer_button_fg_color.notZero() ?: getAttributeColor(
							activity,
							R.attr.colorRippleEffect
						)
					ivFooterToot.background =
						getAdaptiveRippleDrawableRound(activity, colorBg, colorRipple)
					ivFooterMenu.background =
						getAdaptiveRippleDrawableRound(activity, colorBg, colorRipple)
					
					val csl = ColorStateList.valueOf(
						footer_button_fg_color.notZero()
							?: getAttributeColor(activity, R.attr.colorVectorDrawable)
					)
					ivFooterToot.imageTintList = csl
					ivFooterMenu.imageTintList = csl
					
					llFooterBG.setBackgroundColor(
						footer_tab_bg_color.notZero()
							?: getAttributeColor(activity, R.attr.colorColumnStripBackground)
					)
					
					val c =
						footer_tab_divider_color.notZero() ?: getAttributeColor(
							activity,
							R.attr.colorImageButton
						)
					vFooterDivider1.setBackgroundColor(c)
					vFooterDivider2.setBackgroundColor(c)
					
					vIndicator.setBackgroundColor(
						footer_tab_indicator_color.notZero()
							?: getAttributeColor(activity, R.attr.colorAccent)
					)
				}
			
			colorOpaque(Pref.ipFooterButtonBgColor, R.string.button_background_color) {
				changed = { showSample(AppSettingItem.SAMPLE_FOOTER) }
			}
			colorOpaque(Pref.ipFooterButtonFgColor, R.string.button_foreground_color) {
				changed = { showSample(AppSettingItem.SAMPLE_FOOTER) }
			}
			colorOpaque(Pref.ipFooterTabBgColor, R.string.quick_toot_bar_background_color) {
				changed = { showSample(AppSettingItem.SAMPLE_FOOTER) }
			}
			colorOpaque(Pref.ipFooterTabDividerColor, R.string.tab_divider_color) {
				changed = { showSample(AppSettingItem.SAMPLE_FOOTER) }
			}
			colorAlpha(Pref.ipFooterTabIndicatorColor, R.string.tab_indicator_color) {
				changed = { showSample(AppSettingItem.SAMPLE_FOOTER) }
			}
		}
		
		colorOpaque(Pref.ipSwitchOnColor, R.string.switch_button_color) {
			changed = { setSwitchColor() }
		}
		
		colorOpaque(Pref.ipStatusBarColor, R.string.status_bar_color) {
			changed = { App1.setStatusBarColor(this) }
		}
		
		colorOpaque(Pref.ipNavigationBarColor, R.string.navigation_bar_color) {
			changed = { App1.setStatusBarColor(this) }
		}
		
		colorOpaque(Pref.ipSearchBgColor, R.string.search_bar_background_color)
		colorAlpha(Pref.ipAnnouncementsBgColor, R.string.announcement_background_color)
		colorAlpha(Pref.ipVerifiedLinkBgColor, R.string.verified_link_background_color)
		colorAlpha(Pref.ipVerifiedLinkFgColor, R.string.verified_link_foreground_color)
	}
	
	section(R.string.performance) {
		sw(Pref.bpShareViewPool, R.string.share_view_pool)
		sw(Pref.bpDontUseStreaming, R.string.dont_use_streaming_api)
		sw(Pref.bpDontRefreshOnResume, R.string.dont_refresh_on_activity_resume)
		text(Pref.spMediaReadTimeout, R.string.timeout_for_embed_media_viewer, InputTypeEx.number)
		action(R.string.delete_custom_emoji_cache) {
			action = {
				App1.custom_emoji_cache.delete()
			}
		}
	}
	
	section(R.string.developer_options) {
		action(R.string.drawable_list) {
			action = { startActivity(Intent(this, ActDrawableList::class.java)) }
		}
		sw(Pref.bpCheckBetaVersion, R.string.check_beta_release)
		
	}
	
	action(R.string.app_data_export) {
		action = { exportAppData() }
	}
	
	action(R.string.app_data_import) {
		action = { importAppData1() }
		desc = R.string.app_data_import_desc
	}
}
