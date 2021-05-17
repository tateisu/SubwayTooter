package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.view.View
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.TimelineItem
import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.streaming.streamSpec
import jp.juggler.util.getAdaptiveRippleDrawable
import jp.juggler.util.isMainThread
import jp.juggler.util.notZero
import jp.juggler.util.showToast
import org.jetbrains.anko.backgroundDrawable
import kotlin.math.min

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
    ColumnType.LIST_LIST,
    ColumnType.TREND_TAG,
    ColumnType.FOLLOW_SUGGESTION,
    ColumnType.PROFILE_DIRECTORY -> true

    ColumnType.LIST_MEMBER,
    ColumnType.MUTES,
    ColumnType.FOLLOW_REQUESTS -> isMisskey

    else -> false
}

// カラム操作的にリフレッシュを許容するかどうか
fun Column.canRefreshTopBySwipe(): Boolean =
    canReloadWhenRefreshTop() ||
        when (type) {
            ColumnType.CONVERSATION,
            ColumnType.INSTANCE_INFORMATION -> false
            else -> true
        }

// カラム操作的にリフレッシュを許容するかどうか
fun Column.canRefreshBottomBySwipe(): Boolean = when (type) {
    ColumnType.LIST_LIST,
    ColumnType.CONVERSATION,
    ColumnType.INSTANCE_INFORMATION,
    ColumnType.KEYWORD_FILTER,
    ColumnType.SEARCH,
    ColumnType.TREND_TAG,
    ColumnType.FOLLOW_SUGGESTION -> false

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

fun Column.getIconId(): Int = type.iconId(access_info.acct)

fun Column.getColumnName(long: Boolean) =
    type.name2(this, long) ?: type.name1(context)

fun Column.getContentColor() = content_color.notZero() ?: Column.defaultColorContentText

fun Column.getAcctColor() = acct_color.notZero() ?: Column.defaultColorContentAcct

fun Column.getHeaderPageNumberColor() = header_fg_color.notZero() ?: Column.defaultColorHeaderPageNumber

fun Column.getHeaderNameColor() = header_fg_color.notZero() ?: Column.defaultColorHeaderName

fun Column.getHeaderBackgroundColor() = header_bg_color.notZero() ?: Column.defaultColorHeaderBg

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

fun Column.hasMultipleViewHolder(): Boolean = _holder_list.size > 1

fun Column.addColumnViewHolder(cvh: ColumnViewHolder) {

    // 現在のリストにあるなら削除する
    removeColumnViewHolder(cvh)

    // 最後に追加されたものが先頭にくるようにする
    // 呼び出しの後に必ず追加されているようにする
    _holder_list.addFirst(cvh)
}

fun Column.removeColumnViewHolder(cvh: ColumnViewHolder) {
    val it = _holder_list.iterator()
    while (it.hasNext()) {
        if (cvh == it.next()) it.remove()
    }
}

fun Column.removeColumnViewHolderByActivity(activity: ActMain) {
    val it = _holder_list.iterator()
    while (it.hasNext()) {
        val cvh = it.next()
        if (cvh.activity == activity) {
            it.remove()
        }
    }
}

fun Column.fireShowContent(
    reason: String,
    changeList: List<AdapterChange>? = null,
    reset: Boolean = false
) {
    if (!isMainThread) {
        throw RuntimeException("fireShowContent: not on main thread.")
    }
    viewHolder?.showContent(reason, changeList, reset)
}

fun Column.fireShowColumnHeader() {
    if (!isMainThread) {
        throw RuntimeException("fireShowColumnHeader: not on main thread.")
    }
    viewHolder?.showColumnHeader()
}

fun Column.fireShowColumnStatus() {
    if (!isMainThread) {
        throw RuntimeException("fireShowColumnStatus: not on main thread.")
    }
    viewHolder?.showColumnStatus()
}

fun Column.fireColumnColor() {
    if (!isMainThread) {
        throw RuntimeException("fireColumnColor: not on main thread.")
    }
    viewHolder?.showColumnColor()
}

fun Column.fireRelativeTime() {
    if (!isMainThread) {
        throw RuntimeException("fireRelativeTime: not on main thread.")
    }
    viewHolder?.updateRelativeTime()
}

fun Column.fireRebindAdapterItems() {
    if (!isMainThread) {
        throw RuntimeException("fireRelativeTime: not on main thread.")
    }
    viewHolder?.rebindAdapterItems()
}

/////////////////////////////////////////////////////////////////

// ActMain の表示開始時に呼ばれる
fun Column.onActivityStart() {

    // 破棄されたカラムなら何もしない
    if (is_dispose.get()) {
        Column.log.d("onStart: column was disposed.")
        return
    }

    // 未初期化なら何もしない
    if (!bFirstInitialized) {
        Column.log.d("onStart: column is not initialized.")
        return
    }

    // 初期ロード中なら何もしない
    if (bInitialLoading) {
        Column.log.d("onStart: column is in initial loading.")
        return
    }

    // フィルタ一覧のリロードが必要
    if (filter_reload_required) {
        filter_reload_required = false
        startLoading()
        return
    }

    // 始端リフレッシュの最中だった
    // リフレッシュ終了時に自動でストリーミング開始するはず
    if (bRefreshingTop) {
        Column.log.d("onStart: bRefreshingTop is true.")
        return
    }

    if (!bRefreshLoading
        && canAutoRefresh()
        && !Pref.bpDontRefreshOnResume(app_state.pref)
        && !dont_auto_refresh
    ) {
        // リフレッシュしてからストリーミング開始
        Column.log.d("onStart: start auto refresh.")
        startRefresh(bSilent = true, bBottom = false)
    } else if (isSearchColumn) {
        // 検索カラムはリフレッシュもストリーミングもないが、表示開始のタイミングでリストの再描画を行いたい
        fireShowContent(reason = "Column onStart isSearchColumn", reset = true)
    } else if (canStartStreaming() && streamSpec != null) {
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

fun Column.startLoading() {
    cancelLastTask()

    initFilter()

    Column.showOpenSticker = Pref.bpOpenSticker(app_state.pref)

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

    duplicate_map.clear()
    list_data.clear()
    fireShowContent(reason = "loading start", reset = true)

    @SuppressLint("StaticFieldLeak")
    val task = ColumnTask_Loading(this)
    this.lastTask = task
    task.start()
}

fun Column.startRefresh(
    bSilent: Boolean,
    bBottom: Boolean,
    posted_status_id: EntityId? = null,
    refresh_after_toot: Int = -1
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
        startLoading()
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
    val task = ColumnTask_Refresh(this, bSilent, bBottom, posted_status_id, refresh_after_toot)
    this.lastTask = task
    task.start()
    fireShowColumnStatus()
}

fun Column.startRefreshForPost(
    refresh_after_post: Int,
    posted_status_id: EntityId,
    posted_reply_id: EntityId?
) {
    when (type) {
        ColumnType.HOME, ColumnType.LOCAL, ColumnType.FEDERATE, ColumnType.DIRECT_MESSAGES, ColumnType.MISSKEY_HYBRID -> {
            startRefresh(
                bSilent = true,
                bBottom = false,
                posted_status_id = posted_status_id,
                refresh_after_toot = refresh_after_post
            )
        }

        ColumnType.PROFILE -> {
            if (profile_tab == ProfileTab.Status
                && profile_id == access_info.loginAccount?.id
            ) {
                startRefresh(
                    bSilent = true,
                    bBottom = false,
                    posted_status_id = posted_status_id,
                    refresh_after_toot = refresh_after_post
                )
            }
        }

        ColumnType.CONVERSATION -> {
            // 会話への返信が行われたなら会話を更新する
            try {
                if (posted_reply_id != null) {
                    for (item in list_data) {
                        if (item is TootStatus && item.id == posted_reply_id) {
                            startLoading()
                            break
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }

        else -> {

        }
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


// misskeyのキャプチャの対象となる投稿IDのリストを作る
// カラム内データの上(最新)から40件をキャプチャ対象とする
fun Column.updateMisskeyCapture() {
    if (!isMisskey) return

    val streamConnection = app_state.streamManager.getConnection(this)
        ?: return

    val max = 40
    val list = ArrayList<EntityId>(max * 2) // リブログなどで膨れる場合がある

    fun add(s: TootStatus?) {
        s ?: return
        list.add(s.id)
        add(s.reblog)
        add(s.reply)
    }

    for (i in 0 until min(max, list_data.size)) {
        val o = list_data[i]
        if (o is TootStatus) {
            add(o)
        } else if (o is TootNotification) {
            add(o.status)
        }
    }

    if (list.isNotEmpty()) streamConnection.misskeySetCapture(list)
}
