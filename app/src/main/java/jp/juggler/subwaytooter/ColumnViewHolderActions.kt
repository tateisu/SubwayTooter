package jp.juggler.subwaytooter

import android.view.View
import android.widget.CompoundButton
import jp.juggler.subwaytooter.action.Action_Account
import jp.juggler.subwaytooter.action.Action_List
import jp.juggler.subwaytooter.action.Action_Notification
import jp.juggler.subwaytooter.api.entity.TootAnnouncement
import jp.juggler.util.hideKeyboard
import jp.juggler.util.isCheckedNoAnime
import jp.juggler.util.showToast
import jp.juggler.util.withCaption
import java.util.regex.Pattern

fun ColumnViewHolder.onListListUpdated() {
    etListName.setText("")
}

fun ColumnViewHolder.checkRegexFilterError(src: String): String? {
    try {
        if (src.isEmpty()) {
            return null
        }
        val m = Pattern.compile(src).matcher("")
        if (m.find()) {
            // 空文字列にマッチする正規表現はエラー扱いにする
            // そうしないとCWの警告テキストにマッチしてしまう
            return activity.getString(R.string.regex_filter_matches_empty_string)
        }
        return null
    } catch (ex: Throwable) {
        val message = ex.message
        return if (message != null && message.isNotEmpty()) {
            message
        } else {
            ex.withCaption(activity.resources, R.string.regex_error)
        }
    }
}

fun ColumnViewHolder.isRegexValid(): Boolean {
    val s = etRegexFilter.text.toString()
    val error = checkRegexFilterError(s)
    tvRegexFilterError.text = error ?: ""
    return error == null
}


fun ColumnViewHolder.onCheckedChangedImpl(view: CompoundButton?, isChecked: Boolean) {
    view ?: return

    val column = this.column

    if (binding_busy || column == null || status_adapter == null) return

    // カラムを追加/削除したときに ColumnからColumnViewHolderへの参照が外れることがある
    // リロードやリフレッシュ操作で直るようにする
    column.addColumnViewHolder(this)

    when (view) {

        cbDontCloseColumn -> {
            column.dont_close = isChecked
            showColumnCloseButton()
            activity.app_state.saveColumnList()
        }

        cbWithAttachment -> {
            column.with_attachment = isChecked
            activity.app_state.saveColumnList()
            column.startLoading()
        }

        cbRemoteOnly -> {
            column.remote_only = isChecked
            activity.app_state.saveColumnList()
            column.startLoading()
        }

        cbWithHighlight -> {
            column.with_highlight = isChecked
            activity.app_state.saveColumnList()
            column.startLoading()
        }

        cbDontShowBoost -> {
            column.dont_show_boost = isChecked
            activity.app_state.saveColumnList()
            column.startLoading()
        }

        cbDontShowReply -> {
            column.dont_show_reply = isChecked
            activity.app_state.saveColumnList()
            column.startLoading()
        }

        cbDontShowReaction -> {
            column.dont_show_reaction = isChecked
            activity.app_state.saveColumnList()
            column.startLoading()
        }

        cbDontShowVote -> {
            column.dont_show_vote = isChecked
            activity.app_state.saveColumnList()
            column.startLoading()
        }

        cbDontShowNormalToot -> {
            column.dont_show_normal_toot = isChecked
            activity.app_state.saveColumnList()
            column.startLoading()
        }

        cbDontShowNonPublicToot -> {
            column.dont_show_non_public_toot = isChecked
            activity.app_state.saveColumnList()
            column.startLoading()
        }

        cbDontShowFavourite -> {
            column.dont_show_favourite = isChecked
            activity.app_state.saveColumnList()
            column.startLoading()
        }

        cbDontShowFollow -> {
            column.dont_show_follow = isChecked
            activity.app_state.saveColumnList()
            column.startLoading()
        }

        cbInstanceLocal -> {
            column.instance_local = isChecked
            activity.app_state.saveColumnList()
            column.startLoading()
        }

        cbDontStreaming -> {
            column.dont_streaming = isChecked
            activity.app_state.saveColumnList()
            activity.app_state.streamManager.updateStreamingColumns()
        }

        cbDontAutoRefresh -> {
            column.dont_auto_refresh = isChecked
            activity.app_state.saveColumnList()
        }

        cbHideMediaDefault -> {
            column.hide_media_default = isChecked
            activity.app_state.saveColumnList()
            column.fireShowContent(reason = "HideMediaDefault in ColumnSetting", reset = true)
        }

        cbSystemNotificationNotRelated -> {
            column.system_notification_not_related = isChecked
            activity.app_state.saveColumnList()
        }

        cbEnableSpeech -> {
            column.enable_speech = isChecked
            activity.app_state.saveColumnList()
        }

        cbOldApi -> {
            column.use_old_api = isChecked
            activity.app_state.saveColumnList()
            column.startLoading()
        }
    }
}

fun ColumnViewHolder.onClickImpl(v: View?) {
    v?: return

    val column = this.column
    val status_adapter = this.status_adapter
    if (binding_busy || column == null || status_adapter == null) return

    // カラムを追加/削除したときに ColumnからColumnViewHolderへの参照が外れることがある
    // リロードやリフレッシュ操作で直るようにする
    column.addColumnViewHolder(this)

    when (v) {
        btnColumnClose -> activity.closeColumn(column)

        btnColumnReload -> {
            App1.custom_emoji_cache.clearErrorCache()

            if (column.isSearchColumn) {
                etSearch.hideKeyboard()
                etSearch.setText(column.search_query)
                cbResolve.isCheckedNoAnime = column.search_resolve
            }
            refreshLayout.isRefreshing = false
            column.startLoading()
        }

        btnSearch -> {
            etSearch.hideKeyboard()
            column.search_query = etSearch.text.toString().trim { it <= ' ' }
            column.search_resolve = cbResolve.isChecked
            activity.app_state.saveColumnList()
            column.startLoading()
        }

        btnSearchClear -> {
            etSearch.setText("")
            column.search_query = ""
            column.search_resolve = cbResolve.isChecked
            activity.app_state.saveColumnList()
            column.startLoading()
        }

        llColumnHeader -> scrollToTop2()

        btnColumnSetting -> {
            if (showColumnSetting(!isColumnSettingShown)) {
                hideAnnouncements()
            }
        }

        btnDeleteNotification -> Action_Notification.deleteAll(
            activity,
            column.access_info,
            false
        )

        btnColor ->
            activity.app_state.columnIndex(column)?.let {
                ActColumnCustomize.open(activity, it, ActMain.REQUEST_CODE_COLUMN_COLOR)
            }

        btnLanguageFilter ->
            activity.app_state.columnIndex(column)?.let {
                ActLanguageFilter.open(activity, it, ActMain.REQUEST_CODE_LANGUAGE_FILTER)
            }

        btnListAdd -> {
            val tv = etListName.text.toString().trim { it <= ' ' }
            if (tv.isEmpty()) {
                activity.showToast(true, R.string.list_name_empty)
                return
            }
            Action_List.create(activity, column.access_info, tv, null)
        }

        llRefreshError -> {
            column.mRefreshLoadingErrorPopupState = 1 - column.mRefreshLoadingErrorPopupState
            showRefreshError()
        }

        btnQuickFilterAll -> clickQuickFilter(Column.QUICK_FILTER_ALL)
        btnQuickFilterMention -> clickQuickFilter(Column.QUICK_FILTER_MENTION)
        btnQuickFilterFavourite -> clickQuickFilter(Column.QUICK_FILTER_FAVOURITE)
        btnQuickFilterBoost -> clickQuickFilter(Column.QUICK_FILTER_BOOST)
        btnQuickFilterFollow -> clickQuickFilter(Column.QUICK_FILTER_FOLLOW)
        btnQuickFilterPost -> clickQuickFilter(Column.QUICK_FILTER_POST)
        btnQuickFilterReaction -> clickQuickFilter(Column.QUICK_FILTER_REACTION)
        btnQuickFilterVote -> clickQuickFilter(Column.QUICK_FILTER_VOTE)

        btnAnnouncements -> toggleAnnouncements()

        btnAnnouncementsPrev -> {
            column.announcementId =
                TootAnnouncement.move(column.announcements, column.announcementId, -1)
            activity.app_state.saveColumnList()
            showAnnouncements()
        }

        btnAnnouncementsNext -> {
            column.announcementId =
                TootAnnouncement.move(column.announcements, column.announcementId, +1)
            activity.app_state.saveColumnList()
            showAnnouncements()
        }

        btnConfirmMail -> {
            Action_Account.resendConfirmMail(activity, column.access_info)

        }
    }
}

fun ColumnViewHolder.onLongClickImpl(v: View?): Boolean {
    v?: return false
    return when (v) {
        btnColumnClose ->
            activity.app_state.columnIndex(column)?.let {
                activity.closeColumnAll(it)
                true
            } ?: false

        else -> false
    }
}

