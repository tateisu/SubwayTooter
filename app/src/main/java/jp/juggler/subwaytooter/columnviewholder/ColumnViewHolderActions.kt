package jp.juggler.subwaytooter.columnviewholder

import android.view.View
import android.widget.CompoundButton
import jp.juggler.subwaytooter.ActColumnCustomize
import jp.juggler.subwaytooter.ActLanguageFilter
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.action.accountResendConfirmMail
import jp.juggler.subwaytooter.action.listCreate
import jp.juggler.subwaytooter.action.notificationDeleteAll
import jp.juggler.subwaytooter.actmain.closeColumn
import jp.juggler.subwaytooter.actmain.closeColumnAll
import jp.juggler.subwaytooter.api.entity.TootAnnouncement
import jp.juggler.subwaytooter.column.*
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

    if (bindingBusy || column == null || statusAdapter == null) return

    // カラムを追加/削除したときに ColumnからColumnViewHolderへの参照が外れることがある
    // リロードやリフレッシュ操作で直るようにする
    column.addColumnViewHolder(this)

    when (view) {

        cbDontCloseColumn -> {
            column.dontClose = isChecked
            showColumnCloseButton()
            activity.appState.saveColumnList()
        }

        cbShowMediaDescription -> {
            column.showMediaDescription = isChecked
            activity.appState.saveColumnList()
            column.startLoading()
        }

        cbWithAttachment -> {
            column.withAttachment = isChecked
            activity.appState.saveColumnList()
            column.startLoading()
        }

        cbRemoteOnly -> {
            column.remoteOnly = isChecked
            activity.appState.saveColumnList()
            column.startLoading()
        }

        cbWithHighlight -> {
            column.withHighlight = isChecked
            activity.appState.saveColumnList()
            column.startLoading()
        }

        cbDontShowBoost -> {
            column.dontShowBoost = isChecked
            activity.appState.saveColumnList()
            column.startLoading()
        }

        cbDontShowReply -> {
            column.dontShowReply = isChecked
            activity.appState.saveColumnList()
            column.startLoading()
        }

        cbDontShowReaction -> {
            column.dontShowReaction = isChecked
            activity.appState.saveColumnList()
            column.startLoading()
        }

        cbDontShowVote -> {
            column.dontShowVote = isChecked
            activity.appState.saveColumnList()
            column.startLoading()
        }

        cbDontShowNormalToot -> {
            column.dontShowNormalToot = isChecked
            activity.appState.saveColumnList()
            column.startLoading()
        }

        cbDontShowNonPublicToot -> {
            column.dontShowNonPublicToot = isChecked
            activity.appState.saveColumnList()
            column.startLoading()
        }

        cbDontShowFavourite -> {
            column.dontShowFavourite = isChecked
            activity.appState.saveColumnList()
            column.startLoading()
        }

        cbDontShowFollow -> {
            column.dontShowFollow = isChecked
            activity.appState.saveColumnList()
            column.startLoading()
        }

        cbInstanceLocal -> {
            column.instanceLocal = isChecked
            activity.appState.saveColumnList()
            column.startLoading()
        }

        cbDontStreaming -> {
            column.dontStreaming = isChecked
            activity.appState.saveColumnList()
            activity.appState.streamManager.updateStreamingColumns()
        }

        cbDontAutoRefresh -> {
            column.dontAutoRefresh = isChecked
            activity.appState.saveColumnList()
        }

        cbHideMediaDefault -> {
            column.hideMediaDefault = isChecked
            activity.appState.saveColumnList()
            column.fireShowContent(reason = "HideMediaDefault in ColumnSetting", reset = true)
        }

        cbSystemNotificationNotRelated -> {
            column.systemNotificationNotRelated = isChecked
            activity.appState.saveColumnList()
        }

        cbEnableSpeech -> {
            column.enableSpeech = isChecked
            activity.appState.saveColumnList()
        }

        cbOldApi -> {
            column.useOldApi = isChecked
            activity.appState.saveColumnList()
            column.startLoading()
        }
    }
}

fun ColumnViewHolder.onClickImpl(v: View?) {
    v ?: return

    val column = this.column
    val statusAdapter = this.statusAdapter
    if (bindingBusy || column == null || statusAdapter == null) return

    // カラムを追加/削除したときに ColumnからColumnViewHolderへの参照が外れることがある
    // リロードやリフレッシュ操作で直るようにする
    column.addColumnViewHolder(this)

    when (v) {
        btnColumnClose -> activity.closeColumn(column)

        btnColumnReload -> {
            App1.custom_emoji_cache.clearErrorCache()

            if (column.isSearchColumn) {
                etSearch.hideKeyboard()
                etSearch.setText(column.searchQuery)
                cbResolve.isCheckedNoAnime = column.searchResolve
            } else if (column.type == ColumnType.REACTIONS) {
                updateReactionQueryView()
            }
            refreshLayout.isRefreshing = false
            column.startLoading()
        }

        btnSearch -> {
            if (column.isSearchColumn) {
                etSearch.hideKeyboard()
                column.searchQuery = etSearch.text.toString().trim { it <= ' ' }
                column.searchResolve = cbResolve.isChecked
            }
            activity.appState.saveColumnList()
            column.startLoading()
        }

        btnSearchClear -> {
            column.searchQuery = ""
            column.searchResolve = cbResolve.isChecked
            etSearch.setText("")
            flEmoji.removeAllViews()
            activity.appState.saveColumnList()
            column.startLoading()
        }

        llColumnHeader -> scrollToTop2()

        btnColumnSetting -> {
            if (showColumnSetting(!isColumnSettingShown)) {
                hideAnnouncements()
            }
        }

        btnDeleteNotification ->
            activity.notificationDeleteAll(column.accessInfo)

        btnColor ->
            activity.appState.columnIndex(column)?.let { colIdx ->

                activity.arColumnColor.launch(
                    ActColumnCustomize.createIntent(activity, colIdx)
                )
            }

        btnLanguageFilter ->
            activity.appState.columnIndex(column)?.let { colIdx ->

                activity.arLanguageFilter.launch(
                    ActLanguageFilter.createIntent(activity, colIdx)
                )
            }

        btnListAdd -> {
            val tv = etListName.text.toString().trim { it <= ' ' }
            if (tv.isEmpty()) {
                activity.showToast(true, R.string.list_name_empty)
                return
            }
            activity.listCreate(column.accessInfo, tv, null)
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
            activity.appState.saveColumnList()
            showAnnouncements()
        }

        btnAnnouncementsNext -> {
            column.announcementId =
                TootAnnouncement.move(column.announcements, column.announcementId, +1)
            activity.appState.saveColumnList()
            showAnnouncements()
        }

        btnConfirmMail -> {
            activity.accountResendConfirmMail(column.accessInfo)
        }

        btnEmojiAdd -> {
            addEmojiQuery()
        }
    }
}

fun ColumnViewHolder.onLongClickImpl(v: View?): Boolean = when (v) {
    btnColumnClose ->
        activity.appState.columnIndex(column)?.let {
            activity.closeColumnAll(it)
            true
        } ?: false

    else -> false
}
