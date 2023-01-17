package jp.juggler.subwaytooter.columnviewholder

import android.view.View
import android.widget.ImageView
import androidx.core.net.toUri
import com.omadahealth.github.swipyrefreshlayout.library.SwipyRefreshLayoutDirection
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.actmain.closePopup
import jp.juggler.subwaytooter.column.*
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.pref.PrefI
import jp.juggler.subwaytooter.util.endPadding
import jp.juggler.subwaytooter.util.startPadding
import jp.juggler.subwaytooter.view.ListDivider
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.data.notZero
import jp.juggler.util.log.LogCategory
import jp.juggler.util.media.createResizedBitmap
import jp.juggler.util.ui.attrColor
import jp.juggler.util.ui.createRoundDrawable
import jp.juggler.util.ui.isCheckedNoAnime
import jp.juggler.util.ui.vg
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.bottomPadding
import org.jetbrains.anko.topPadding

private val log = LogCategory("ColumnViewHolderLifeCycle")

fun ColumnViewHolder.closeBitmaps() {
    try {
        ivColumnBackgroundImage.visibility = View.GONE
        ivColumnBackgroundImage.setImageDrawable(null)

        lastImageBitmap?.recycle()
        lastImageBitmap = null

        lastImageTask?.cancel()
        lastImageTask = null

        lastImageUri = null
    } catch (ex: Throwable) {
        log.e(ex, "closeBitmaps failed.")
    }
}

fun ColumnViewHolder.loadBackgroundImage(iv: ImageView, url: String?) {
    try {
        if (url == null || url.isEmpty() || PrefB.bpDontShowColumnBackgroundImage(activity.pref)) {
            // 指定がないなら閉じる
            closeBitmaps()
            return
        }

        if (url == lastImageUri) {
            // 今表示してるのと同じ
            return
        }

        // 直前の処理をキャンセルする。Bitmapも破棄する
        closeBitmaps()

        // ロード開始
        lastImageUri = url
        val screenW = iv.resources.displayMetrics.widthPixels
        val screenH = iv.resources.displayMetrics.heightPixels

        // 非同期処理を開始
        lastImageTask = launchMain {
            val bitmap = try {
                withContext(AppDispatchers.IO) {
                    try {
                        createResizedBitmap(
                            activity,
                            url.toUri(),
                            when {
                                screenW > screenH -> screenW
                                else -> screenH
                            },
                        )
                    } catch (ex: Throwable) {
                        log.e(ex, "createResizedBitmap failed.")
                        null
                    }
                }
            } catch (ex: Throwable) {
                log.w(ex, "loadBackgroundImage failed.")
                null
            }
            if (bitmap != null) {
                if (!coroutineContext.isActive || url != lastImageUri) {
                    bitmap.recycle()
                } else {
                    lastImageBitmap = bitmap
                    iv.setImageBitmap(lastImageBitmap)
                    iv.visibility = View.VISIBLE
                }
            }
        }
    } catch (ex: Throwable) {
        log.e(ex, "loadBackgroundImage failed.")
    }
}

fun ColumnViewHolder.onPageDestroy(pageIdx: Int) {
    // タブレットモードの場合、onPageCreateより前に呼ばれる
    val column = this.column
    if (column != null) {
        ColumnViewHolder.log.d("onPageDestroy [$pageIdx] ${tvColumnName.text}")
        saveScrollPosition()
        listView.adapter = null
        column.removeColumnViewHolder(this)
        this.column = null
    }

    closeBitmaps()

    activity.closePopup()
}

fun ColumnViewHolder.onPageCreate(column: Column, pageIdx: Int, pageCount: Int) {
    bindingBusy = true
    try {
        this.column = column
        this.pageIdx = pageIdx

        ColumnViewHolder.log.d("onPageCreate [$pageIdx] ${column.getColumnName(true)}")

        val bSimpleList = !column.isConversation && PrefB.bpSimpleList(activity.pref)

        tvColumnIndex.text = activity.getString(R.string.column_index, pageIdx + 1, pageCount)
        tvColumnStatus.text = "?"
        ivColumnIcon.setImageResource(column.getIconId())

        listView.adapter = null
        if (listView.itemDecorationCount == 0) {
            listView.addItemDecoration(ListDivider(activity))
        }

        val statusAdapter = ItemListAdapter(activity, column, this, bSimpleList)
        this.statusAdapter = statusAdapter

        val isNotificationColumn = column.isNotificationColumn

        // 添付メディアや正規表現のフィルタ
        val bAllowFilter = column.canStatusFilter()

        showColumnSetting(false)

        for (invalidator in emojiQueryInvalidatorList) {
            invalidator.register(null)
        }
        emojiQueryInvalidatorList.clear()

        for (invalidator in extraInvalidatorList) {
            invalidator.register(null)
        }
        extraInvalidatorList.clear()

        cbDontCloseColumn.isCheckedNoAnime = column.dontClose
        cbShowMediaDescription.isCheckedNoAnime = column.showMediaDescription
        cbRemoteOnly.isCheckedNoAnime = column.remoteOnly
        cbWithAttachment.isCheckedNoAnime = column.withAttachment
        cbWithHighlight.isCheckedNoAnime = column.withHighlight
        cbDontShowBoost.isCheckedNoAnime = column.dontShowBoost
        cbDontShowFollow.isCheckedNoAnime = column.dontShowFollow
        cbDontShowFavourite.isCheckedNoAnime = column.dontShowFavourite
        cbDontShowReply.isCheckedNoAnime = column.dontShowReply
        cbDontShowReaction.isCheckedNoAnime = column.dontShowReaction
        cbDontShowVote.isCheckedNoAnime = column.dontShowVote
        cbDontShowNormalToot.isCheckedNoAnime = column.dontShowNormalToot
        cbDontShowNonPublicToot.isCheckedNoAnime = column.dontShowNonPublicToot
        cbInstanceLocal.isCheckedNoAnime = column.instanceLocal
        cbDontStreaming.isCheckedNoAnime = column.dontStreaming
        cbDontAutoRefresh.isCheckedNoAnime = column.dontAutoRefresh
        cbHideMediaDefault.isCheckedNoAnime = column.hideMediaDefault
        cbSystemNotificationNotRelated.isCheckedNoAnime = column.systemNotificationNotRelated
        cbEnableSpeech.isCheckedNoAnime = column.enableSpeech
        cbOldApi.isCheckedNoAnime = column.useOldApi

        etRegexFilter.setText(column.regexText)
        etSearch.setText(column.searchQuery)
        cbResolve.isCheckedNoAnime = column.searchResolve

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

        cbDontStreaming.vg(column.canStreamingType())
        cbDontAutoRefresh.vg(column.canAutoRefresh())
        cbHideMediaDefault.vg(column.canNSFWDefault())
        cbSystemNotificationNotRelated.vg(column.isNotificationColumn)
        cbEnableSpeech.vg(column.canSpeech())
        cbOldApi.vg(column.type == ColumnType.DIRECT_MESSAGES)

        btnDeleteNotification.vg(column.isNotificationColumn)

        when {
            column.isSearchColumn -> {
                llSearch.vg(true)

                flEmoji.vg(false)
                tvEmojiDesc.vg(false)
                btnEmojiAdd.vg(false)

                etSearch.vg(true)
                btnSearchClear.vg(PrefB.bpShowSearchClear(activity.pref))
                cbResolve.vg(column.type == ColumnType.SEARCH)
            }

            column.type == ColumnType.REACTIONS && column.accessInfo.isMastodon -> {
                llSearch.vg(true)

                flEmoji.vg(true)
                tvEmojiDesc.vg(true)
                btnEmojiAdd.vg(true)

                etSearch.vg(false)
                btnSearchClear.vg(false)
                cbResolve.vg(false)
            }

            else -> llSearch.vg(false)
        }

        llListList.vg(column.type == ColumnType.LIST_LIST)

        llHashtagExtra.vg(column.hasHashtagExtra)
        etHashtagExtraAny.setText(column.hashtagAny)
        etHashtagExtraAll.setText(column.hashtagAll)
        etHashtagExtraNone.setText(column.hashtagNone)

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
        listView.adapter = statusAdapter

        //XXX FastScrollerのサポートを諦める。ライブラリはいくつかあるんだけど、設定でON/OFFできなかったり頭文字バブルを無効にできなかったり
        // listView.isFastScrollEnabled = ! PrefB.bpDisableFastScroller(Pref.pref(activity))

        column.addColumnViewHolder(this)

        lastAnnouncementShown = -1L

        fun dip(dp: Int): Int = (activity.density * dp + 0.5f).toInt()
        val context = activity

        val announcementsBgColor = PrefI.ipAnnouncementsBgColor().notZero()
            ?: context.attrColor(R.attr.colorSearchFormBackground)

        btnAnnouncementsCutout.apply {
            color = announcementsBgColor
        }

        llAnnouncementsBox.apply {
            background = createRoundDrawable(dip(6).toFloat(), announcementsBgColor)
            val padV = dip(2)
            setPadding(0, padV, 0, padV)
        }

        val searchBgColor = PrefI.ipSearchBgColor().notZero()
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
        bindingBusy = false
    }
}
