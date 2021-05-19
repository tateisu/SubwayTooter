package jp.juggler.subwaytooter

import android.view.View
import android.widget.ImageView
import com.omadahealth.github.swipyrefreshlayout.library.SwipyRefreshLayoutDirection
import jp.juggler.subwaytooter.streaming.canSpeech
import jp.juggler.subwaytooter.streaming.canStreaming
import jp.juggler.subwaytooter.util.endPadding
import jp.juggler.subwaytooter.util.startPadding
import jp.juggler.subwaytooter.view.ListDivider
import jp.juggler.util.*
import kotlinx.coroutines.*
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.bottomPadding
import org.jetbrains.anko.topPadding

fun ColumnViewHolder.closeBitmaps() {
    try {
        ivColumnBackgroundImage.visibility = View.GONE
        ivColumnBackgroundImage.setImageDrawable(null)

        last_image_bitmap?.recycle()
        last_image_bitmap = null

        last_image_task?.cancel()
        last_image_task = null

        last_image_uri = null

    } catch (ex: Throwable) {
        ColumnViewHolder.log.trace(ex)
    }

}

fun ColumnViewHolder.loadBackgroundImage(iv: ImageView, url: String?) {
    try {
        if (url == null || url.isEmpty() || Pref.bpDontShowColumnBackgroundImage(activity.pref)) {
            // 指定がないなら閉じる
            closeBitmaps()
            return
        }

        if (url == last_image_uri) {
            // 今表示してるのと同じ
            return
        }

        // 直前の処理をキャンセルする。Bitmapも破棄する
        closeBitmaps()

        // ロード開始
        last_image_uri = url
        val screen_w = iv.resources.displayMetrics.widthPixels
        val screen_h = iv.resources.displayMetrics.heightPixels

        // 非同期処理を開始
        last_image_task = GlobalScope.launch(Dispatchers.Main) {
            val bitmap = try {
                withContext(Dispatchers.IO) {
                    try {
                        createResizedBitmap(
                            activity, url.toUri(),
                            if (screen_w > screen_h)
                                screen_w
                            else
                                screen_h
                        )
                    } catch (ex: Throwable) {
                        ColumnViewHolder.log.trace(ex)
                        null
                    }
                }
            } catch (ex: Throwable) {
                null
            }
            if (bitmap != null) {
                if (!coroutineContext.isActive || url != last_image_uri) {
                    bitmap.recycle()
                } else {
                    last_image_bitmap = bitmap
                    iv.setImageBitmap(last_image_bitmap)
                    iv.visibility = View.VISIBLE
                }
            }
        }
    } catch (ex: Throwable) {
        ColumnViewHolder.log.trace(ex)
    }

}


fun ColumnViewHolder.onPageDestroy(page_idx: Int) {
    // タブレットモードの場合、onPageCreateより前に呼ばれる
    val column = this.column
    if (column != null) {
        ColumnViewHolder.log.d("onPageDestroy [%d] %s", page_idx, tvColumnName.text)
        saveScrollPosition()
        listView.adapter = null
        column.removeColumnViewHolder(this)
        this.column = null
    }

    closeBitmaps()

    activity.closeListItemPopup()
}

fun ColumnViewHolder.onPageCreate(column: Column, page_idx: Int, page_count: Int) {
    binding_busy = true
    try {
        this.column = column
        this.page_idx = page_idx

        ColumnViewHolder.log.d("onPageCreate [%d] %s", page_idx, column.getColumnName(true))

        val bSimpleList =
            column.type != ColumnType.CONVERSATION && Pref.bpSimpleList(activity.pref)

        tvColumnIndex.text = activity.getString(R.string.column_index, page_idx + 1, page_count)
        tvColumnStatus.text = "?"
        ivColumnIcon.setImageResource(column.getIconId())

        listView.adapter = null
        if (listView.itemDecorationCount == 0) {
            listView.addItemDecoration(ListDivider(activity))
        }

        val status_adapter = ItemListAdapter(activity, column, this, bSimpleList)
        this.status_adapter = status_adapter

        val isNotificationColumn = column.isNotificationColumn

        // 添付メディアや正規表現のフィルタ
        val bAllowFilter = column.canStatusFilter()

        showColumnSetting(false)



        cbDontCloseColumn.isCheckedNoAnime = column.dont_close
        cbRemoteOnly.isCheckedNoAnime = column.remote_only
        cbWithAttachment.isCheckedNoAnime = column.with_attachment
        cbWithHighlight.isCheckedNoAnime = column.with_highlight
        cbDontShowBoost.isCheckedNoAnime = column.dont_show_boost
        cbDontShowFollow.isCheckedNoAnime = column.dont_show_follow
        cbDontShowFavourite.isCheckedNoAnime = column.dont_show_favourite
        cbDontShowReply.isCheckedNoAnime = column.dont_show_reply
        cbDontShowReaction.isCheckedNoAnime = column.dont_show_reaction
        cbDontShowVote.isCheckedNoAnime = column.dont_show_vote
        cbDontShowNormalToot.isCheckedNoAnime = column.dont_show_normal_toot
        cbDontShowNonPublicToot.isCheckedNoAnime = column.dont_show_non_public_toot
        cbInstanceLocal.isCheckedNoAnime = column.instance_local
        cbDontStreaming.isCheckedNoAnime = column.dont_streaming
        cbDontAutoRefresh.isCheckedNoAnime = column.dont_auto_refresh
        cbHideMediaDefault.isCheckedNoAnime = column.hide_media_default
        cbSystemNotificationNotRelated.isCheckedNoAnime = column.system_notification_not_related
        cbEnableSpeech.isCheckedNoAnime = column.enable_speech
        cbOldApi.isCheckedNoAnime = column.use_old_api

        etRegexFilter.setText(column.regex_text)
        etSearch.setText(column.search_query)
        cbResolve.isCheckedNoAnime = column.search_resolve

        cbRemoteOnly.vg(column.canRemoteOnly())

        cbWithAttachment.vg(bAllowFilter)
        cbWithHighlight.vg(bAllowFilter)
        etRegexFilter.vg(bAllowFilter)
        llRegexFilter.vg(bAllowFilter)
        btnLanguageFilter.vg(bAllowFilter)

        cbDontShowBoost.vg(column.canFilterBoost())
        cbDontShowReply.vg(column.canFilterReply())
        cbDontShowNormalToot.vg(column.canFilterNormalToot())
        cbDontShowNonPublicToot.vg(column.canFilterNonPublicToot())
        cbDontShowReaction.vg(isNotificationColumn && column.isMisskey)
        cbDontShowVote.vg(isNotificationColumn)
        cbDontShowFavourite.vg(isNotificationColumn && !column.isMisskey)
        cbDontShowFollow.vg(isNotificationColumn)

        cbInstanceLocal.vg(column.type == ColumnType.HASHTAG)


        cbDontStreaming.vg(column.canStreaming())
        cbDontAutoRefresh.vg(column.canAutoRefresh())
        cbHideMediaDefault.vg(column.canNSFWDefault())
        cbSystemNotificationNotRelated.vg(column.isNotificationColumn)
        cbEnableSpeech.vg(column.canSpeech())
        cbOldApi.vg(column.type == ColumnType.DIRECT_MESSAGES)


        btnDeleteNotification.vg(column.isNotificationColumn)

        llSearch.vg(column.isSearchColumn)?.let {
            btnSearchClear.vg(Pref.bpShowSearchClear(activity.pref))
        }

        llListList.vg(column.type == ColumnType.LIST_LIST)
        cbResolve.vg(column.type == ColumnType.SEARCH)

        llHashtagExtra.vg(column.hasHashtagExtra)
        etHashtagExtraAny.setText(column.hashtag_any)
        etHashtagExtraAll.setText(column.hashtag_all)
        etHashtagExtraNone.setText(column.hashtag_none)

        // tvRegexFilterErrorの表示を更新
        if (bAllowFilter) {
            isRegexValid()
        }

        val canRefreshTop = column.canRefreshTopBySwipe()
        val canRefreshBottom = column.canRefreshBottomBySwipe()

        refreshLayout.isEnabled = canRefreshTop || canRefreshBottom
        refreshLayout.direction = if (canRefreshTop && canRefreshBottom) {
            SwipyRefreshLayoutDirection.BOTH
        } else if (canRefreshTop) {
            SwipyRefreshLayoutDirection.TOP
        } else {
            SwipyRefreshLayoutDirection.BOTTOM
        }

        bRefreshErrorWillShown = false
        llRefreshError.clearAnimation()
        llRefreshError.visibility = View.GONE

        //
        listView.adapter = status_adapter

        //XXX FastScrollerのサポートを諦める。ライブラリはいくつかあるんだけど、設定でON/OFFできなかったり頭文字バブルを無効にできなかったり
        // listView.isFastScrollEnabled = ! Pref.bpDisableFastScroller(Pref.pref(activity))

        column.addColumnViewHolder(this)

        lastAnnouncementShown = -1L

        fun dip(dp: Int): Int = (activity.density * dp + 0.5f).toInt()
        val context = activity

        val announcementsBgColor = Pref.ipAnnouncementsBgColor(App1.pref).notZero()
            ?: context.attrColor(R.attr.colorSearchFormBackground)

        btnAnnouncementsCutout.apply {
            color = announcementsBgColor
        }

        llAnnouncementsBox.apply {
            background = createRoundDrawable(dip(6).toFloat(), announcementsBgColor)
            val pad_tb = dip(2)
            setPadding(0, pad_tb, 0, pad_tb)
        }

        val searchBgColor = Pref.ipSearchBgColor(App1.pref).notZero()
            ?: context.attrColor(R.attr.colorSearchFormBackground)

        llSearch.apply {
            backgroundColor = searchBgColor
            startPadding = dip(12)
            endPadding = dip(12)
            topPadding = dip(3)
            bottomPadding = dip(3)
        }

        llListList.apply {
            backgroundColor = searchBgColor
            startPadding = dip(12)
            endPadding = dip(12)
            topPadding = dip(3)
            bottomPadding = dip(3)
        }

        showColumnColor()

        showContent(reason = "onPageCreate", reset = true)
    } finally {
        binding_busy = false
    }
}
