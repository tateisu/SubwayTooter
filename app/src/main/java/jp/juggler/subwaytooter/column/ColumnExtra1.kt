package jp.juggler.subwaytooter.column

import android.annotation.SuppressLint
import android.view.View
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.TimelineItem
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.columnviewholder.ColumnViewHolder
import jp.juggler.subwaytooter.columnviewholder.scrollToTop
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.util.data.notZero
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.ui.getAdaptiveRippleDrawable
import org.jetbrains.anko.backgroundDrawable

private val log = LogCategory("ColumnExtra1")

///////////////////////////////////////////////////
// ViewHolderとの連携

fun Column.canRemoteOnly() = when (type) {
    ColumnType.FEDERATE, ColumnType.FEDERATED_AROUND -> true
    else -> false
}

fun Column.canReloadWhenRefreshTop(): Boolean = when (type) {

    ColumnType.KEYWORD_FILTER,
    ColumnType.SEARCH,
    ColumnType.SEARCH_MSP,
    ColumnType.SEARCH_TS,
    ColumnType.SEARCH_NOTESTOCK,
    ColumnType.CONVERSATION,
    ColumnType.CONVERSATION_WITH_REFERENCE,
    ColumnType.LIST_LIST,
    ColumnType.TREND_TAG,
    ColumnType.FOLLOW_SUGGESTION,
    ColumnType.PROFILE_DIRECTORY,
    ColumnType.STATUS_HISTORY,
    ColumnType.AGG_BOOSTS,
        -> true

    ColumnType.LIST_MEMBER,
    ColumnType.MUTES,
    ColumnType.FOLLOW_REQUESTS,
        -> isMisskey

    else -> false
}

// カラム操作的にリフレッシュを許容するかどうか
fun Column.canRefreshTopBySwipe(): Boolean =
    canReloadWhenRefreshTop() ||
            when (type) {
                ColumnType.CONVERSATION,
                ColumnType.CONVERSATION_WITH_REFERENCE,
                ColumnType.INSTANCE_INFORMATION,
                    -> false

                else -> true
            }

// カラム操作的に下端リフレッシュを許容するかどうか
fun Column.canRefreshBottomBySwipe(): Boolean = when (type) {
    ColumnType.LIST_LIST,
    ColumnType.CONVERSATION,
    ColumnType.CONVERSATION_WITH_REFERENCE,
    ColumnType.INSTANCE_INFORMATION,
    ColumnType.KEYWORD_FILTER,
    ColumnType.SEARCH,
    ColumnType.TREND_TAG,
    ColumnType.FOLLOW_SUGGESTION,
    ColumnType.STATUS_HISTORY,
    ColumnType.AGG_BOOSTS,
        -> false

    ColumnType.FOLLOW_REQUESTS -> isMisskey

    ColumnType.LIST_MEMBER -> !isMisskey

    else -> true
}

// データ的にリフレッシュを許容するかどうか
fun Column.canRefreshTop(): Boolean = when (pagingType) {
    ColumnPagingType.Default -> idRecent != null
    else -> false
}

// データ的にリフレッシュを許容するかどうか
fun Column.canRefreshBottom(): Boolean = when (pagingType) {
    ColumnPagingType.Default, ColumnPagingType.Cursor -> idOld != null
    ColumnPagingType.None -> false
    ColumnPagingType.Offset -> true
}

fun Column.getIconId(): Int = type.iconId(accessInfo.acct)

fun Column.getColumnName(long: Boolean) =
    type.name2(this, long) ?: type.name1(context)

fun Column.getContentColor() = contentColor.notZero() ?: Column.defaultColorContentText

fun Column.getAcctColor() = acctColor.notZero() ?: Column.defaultColorContentAcct

fun Column.getHeaderPageNumberColor() =
    headerFgColor.notZero() ?: Column.defaultColorHeaderPageNumber

fun Column.getHeaderNameColor() = headerFgColor.notZero() ?: Column.defaultColorHeaderName

fun Column.getHeaderBackgroundColor() = headerBgColor.notZero() ?: Column.defaultColorHeaderBg

fun Column.setHeaderBackground(view: View) {
    view.backgroundDrawable = getAdaptiveRippleDrawable(
        getHeaderBackgroundColor(),
        getHeaderNameColor()
    )
}

val Column.hasHashtagExtra: Boolean
    get() = when {
        isMisskey -> false
        type == ColumnType.HASHTAG -> true
        // ColumnType.HASHTAG_FROM_ACCT は追加のタグを指定しても結果に反映されない
        else -> false
    }

fun Column.getHeaderDesc(): String {
    var cache = cacheHeaderDesc
    if (cache != null) return cache
    cache = when (type) {
        ColumnType.SEARCH -> context.getString(R.string.search_desc_mastodon_api)
        ColumnType.SEARCH_MSP -> loadSearchDesc(
            R.raw.search_desc_msp_en,
            R.raw.search_desc_msp_ja
        )

        ColumnType.SEARCH_TS -> loadSearchDesc(
            R.raw.search_desc_ts_en,
            R.raw.search_desc_ts_ja
        )

        ColumnType.SEARCH_NOTESTOCK -> loadSearchDesc(
            R.raw.search_desc_notestock_en,
            R.raw.search_desc_notestock_ja
        )

        else -> ""
    }
    cacheHeaderDesc = cache
    return cache
}

fun Column.hasMultipleViewHolder(): Boolean = listViewHolder.size > 1

fun Column.addColumnViewHolder(cvh: ColumnViewHolder) {

    // 現在のリストにあるなら削除する
    removeColumnViewHolder(cvh)

    // 最後に追加されたものが先頭にくるようにする
    // 呼び出しの後に必ず追加されているようにする
    listViewHolder.addFirst(cvh)
}

/////////////////////////////////////////////////////////////////

// ActMain の表示開始時に呼ばれる
fun Column.onActivityStart() {

    // 破棄されたカラムなら何もしない
    if (isDispose.get()) {
        log.d("onStart: column was disposed.")
        return
    }

    // 未初期化なら何もしない
    if (!bFirstInitialized) {
        log.d("onStart: column is not initialized.")
        return
    }

    // 初期ロード中なら何もしない
    if (bInitialLoading) {
        log.d("onStart: column is in initial loading.")
        return
    }

    // フィルタ一覧のリロードが必要
    if (filterReloadRequired) {
        filterReloadRequired = false
        startLoading(ColumnLoadReason.ContentInvalidated)
        return
    }

    // 始端リフレッシュの最中だった
    // リフレッシュ終了時に自動でストリーミング開始するはず
    if (bRefreshingTop) {
        log.d("onStart: bRefreshingTop is true.")
        return
    }

    if (!bRefreshLoading &&
        canAutoRefresh() &&
        !PrefB.bpDontRefreshOnResume.value &&
        !dontAutoRefresh
    ) {
        // リフレッシュしてからストリーミング開始
        log.d("onStart: start auto refresh.")
        startRefresh(bSilent = true, bBottom = false)
    } else if (isSearchColumn) {
        // 検索カラムはリフレッシュもストリーミングもないが、表示開始のタイミングでリストの再描画を行いたい
        fireShowContent(reason = "Column onStart isSearchColumn", reset = true)
    } else if (canStreamingState() && canStreamingType()) {
        // ギャップつきでストリーミング開始
        this.bPutGap = true
        fireShowColumnStatus()
    }
}

fun Column.cancelLastTask() {
    if (lastTask != null) {
        lastTask?.cancel()
        lastTask = null
        //
        bInitialLoading = false
        bRefreshLoading = false
        mInitialLoadingError = context.getString(R.string.cancelled)
    }
}

//	@Nullable String parseMaxId( TootApiResult result ){
//		if( result != null && result.link_older != null ){
//			Matcher m = reMaxId.matcher( result.link_older );
//			if( m.get() ) return m.group( 1 );
//		}
//		return null;
//	}

fun Column.startLoading(reason: ColumnLoadReason) {
    // ブースト集約カラムは手動開始するまでロード開始しない
    if (type == ColumnType.AGG_BOOSTS) {
        when (reason) {
            // 自動的にロード開始することはない
            ColumnLoadReason.PageSelect,
            ColumnLoadReason.RefreshAfterPost ,
            ColumnLoadReason.OpenPush ,
                ->{
                    // initializng 表示を除去する
                    if(!bFirstInitialized){
                        clear()
                    }
                    return
                }

            // コンテンツの表示を継続できない場合はカラムの内容をクリアする
            ColumnLoadReason.ContentInvalidated,
            ColumnLoadReason.SettingChange,
            ColumnLoadReason.TokenUpdated,
                -> {
                clear()
                return
            }

            // 開始ボタンを押したり、明示的なリロード操作を行った場合はロードする
            ColumnLoadReason.PullToRefresh,
            ColumnLoadReason.ForceReload,
                -> Unit
        }
    }
    // 以下の理由では未ロードの場合だけロード開始する
    when (reason) {
        ColumnLoadReason.PageSelect,
        ColumnLoadReason.OpenPush,
            -> if (bFirstInitialized) return

        else -> Unit
    }

    cancelLastTask()

    initFilter()

    Column.showOpenSticker = PrefB.bpOpenSticker.value

    mRefreshLoadingErrorPopupState = 0
    mRefreshLoadingError = ""
    mInitialLoadingError = ""
    bFirstInitialized = true
    bInitialLoading = true
    bRefreshLoading = false
    idOld = null
    idRecent = null
    offsetNext = 0
    pagingType = ColumnPagingType.Default

    duplicateMap.clear()
    listData.clear()
    fireShowContent(reason = "loading start", reset = true)

    @SuppressLint("StaticFieldLeak")
    val task = ColumnTask_Loading(this)
    this.lastTask = task
    task.start()
}

fun Column.clear() {
    cancelLastTask()
    initFilter()
    Column.showOpenSticker = PrefB.bpOpenSticker.value

    mRefreshLoadingErrorPopupState = 0
    mRefreshLoadingError = ""
    mInitialLoadingError = ""
    bFirstInitialized = true
    bInitialLoading = true
    bRefreshLoading = false
    idOld = null
    idRecent = null
    offsetNext = 0
    pagingType = ColumnPagingType.Default

    duplicateMap.clear()
    listData.clear()
    //
    bInitialLoading = false
    lastTask = null
    fireShowContent(reason = "loading updated", reset = true)
    // 初期ロードの直後は先頭に移動する
    viewHolder?.scrollToTop()
    updateMisskeyCapture()
}

fun Column.startRefresh(
    bSilent: Boolean,
    bBottom: Boolean,
    postedStatusId: EntityId? = null,
    refreshAfterToot: Int = -1,
) {
    if (lastTask != null) {
        if (!bSilent) {
            context.showToast(true, R.string.column_is_busy)
            val holder = viewHolder
            if (holder != null) holder.refreshLayout.isRefreshing = false
        }
        return
    } else if (bBottom && !canRefreshBottom()) {
        if (!bSilent) {
            context.showToast(true, R.string.end_of_list)
            val holder = viewHolder
            if (holder != null) holder.refreshLayout.isRefreshing = false
        }
        return
    } else if (!bBottom && !canRefreshTop()) {
        val holder = viewHolder
        if (holder != null) holder.refreshLayout.isRefreshing = false
        startLoading(ColumnLoadReason.PullToRefresh)
        return
    }

    if (bSilent) {
        val holder = viewHolder
        if (holder != null) {
            holder.refreshLayout.isRefreshing = true
        }
    }

    if (!bBottom) {
        bRefreshingTop = true
    }

    bRefreshLoading = true
    mRefreshLoadingError = ""

    @SuppressLint("StaticFieldLeak")
    val task = ColumnTask_Refresh(this, bSilent, bBottom, postedStatusId, refreshAfterToot)
    this.lastTask = task
    task.start()
    fireShowColumnStatus()
}

fun Column.startRefreshForPost(
    refreshAfterPost: Int,
    postedStatusId: EntityId,
    postedReplyId: EntityId?,
) {
    when (type) {
        ColumnType.HOME,
        ColumnType.LOCAL,
        ColumnType.FEDERATE,
        ColumnType.DIRECT_MESSAGES,
        ColumnType.MISSKEY_HYBRID,
            -> startRefresh(
            bSilent = true,
            bBottom = false,
            postedStatusId = postedStatusId,
            refreshAfterToot = refreshAfterPost
        )

        ColumnType.PROFILE -> {
            if (profileTab == ProfileTab.Status && profileId == accessInfo.loginAccount?.id) {
                startRefresh(
                    bSilent = true,
                    bBottom = false,
                    postedStatusId = postedStatusId,
                    refreshAfterToot = refreshAfterPost
                )
            }
        }

        ColumnType.CONVERSATION,
        ColumnType.CONVERSATION_WITH_REFERENCE,
            -> {
            // 会話への返信が行われたなら会話を更新する
            try {
                if (postedReplyId != null) {
                    for (item in listData) {
                        if (item is TootStatus && item.id == postedReplyId) {
                            startLoading(ColumnLoadReason.ContentInvalidated)
                            break
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }

        else -> Unit
    }
}

fun Column.startGap(gap: TimelineItem?, isHead: Boolean) {

    if (gap == null) {
        context.showToast(true, "gap is null")
        return
    }

    if (lastTask != null) {
        context.showToast(true, R.string.column_is_busy)
        return
    }

    @Suppress("UNNECESSARY_SAFE_CALL")
    viewHolder?.refreshLayout?.isRefreshing = true

    bRefreshLoading = true
    mRefreshLoadingError = ""

    @SuppressLint("StaticFieldLeak")
    val task = ColumnTask_Gap(this, gap, isHead = isHead)
    this.lastTask = task
    task.start()
    fireShowColumnStatus()
}
