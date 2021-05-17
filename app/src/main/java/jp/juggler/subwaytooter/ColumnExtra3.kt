package jp.juggler.subwaytooter

import android.content.Context
import android.os.Environment
import android.os.SystemClock
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.notification.PollingWorker
import jp.juggler.subwaytooter.streaming.streamSpec
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.ScrollPosition
import jp.juggler.util.findOrNull
import jp.juggler.util.groupEx
import jp.juggler.util.isMainThread
import java.io.File
import kotlin.math.max

fun Column.getIconId(): Int = type.iconId(access_info.acct)

fun Column.getColumnName(long: Boolean) =
    type.name2(this, long) ?: type.name1(context)



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

fun Column.hasMultipleViewHolder(): Boolean = _holder_list.size > 1

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

//
suspend fun Column.updateRelation(
    client: TootApiClient,
    list: ArrayList<TimelineItem>?,
    whoRef: TootAccountRef?,
    parser: TootParser
) {
    if (access_info.isPseudo) return

    val env = UpdateRelationEnv(this)

    env.add(whoRef)

    list?.forEach {
        when (it) {
            is TootAccountRef -> env.add(it)
            is TootStatus -> env.add(it)
            is TootNotification -> env.add(it)
            is TootConversationSummary -> env.add(it.last_status)
        }
    }
    env.update(client, parser)
}

fun Column.parseRange(
    result: TootApiResult?,
    list: List<TimelineItem>?
): Pair<EntityId?, EntityId?> {
    var idMin: EntityId? = null
    var idMax: EntityId? = null

    if (isMisskey && list != null) {
        // MisskeyはLinkヘッダがないので、常にデータからIDを読む

        for (item in list) {
            // injectされたデータをデータ範囲に追加しない
            if (item.isInjected()) continue

            val id = item.getOrderId()
            if (id.notDefaultOrConfirming) {
                if (idMin == null || id < idMin) idMin = id
                if (idMax == null || id > idMax) idMax = id
            }
        }
    } else {
        // Linkヘッダを読む
        idMin = Column.reMaxId.matcher(result?.link_older ?: "").findOrNull()
            ?.let {
                EntityId(it.groupEx(1)!!)
            }

        idMax = Column.reMinId.matcher(result?.link_newer ?: "").findOrNull()
            ?.let {
                // min_idとsince_idの読み分けは現在利用してない it.groupEx(1)=="min_id"
                EntityId(it.groupEx(2)!!)
            }
    }

    return Pair(idMin, idMax)
}
// int scroll_hack;

// return true if list bottom may have unread remain
fun Column.saveRange(
    bBottom: Boolean,
    bTop: Boolean,
    result: TootApiResult?,
    list: List<TimelineItem>?
): Boolean {
    val (idMin, idMax) = parseRange(result, list)

    var hasBottomRemain = false

    if (bBottom) when (idMin) {
        null -> idOld = null // リストの終端
        else -> {
            val i = idOld?.compareTo(idMin)
            if (i == null || i > 0) {
                idOld = idMin
                hasBottomRemain = true
            }
        }
    }

    if (bTop) when (idMax) {
        null -> {
            // リロードを許容するため、取得内容がカラでもidRecentを変更しない
        }

        else -> {
            val i = idRecent?.compareTo(idMax)
            if (i == null || i < 0) {
                idRecent = idMax
            }
        }
    }

    return hasBottomRemain
}

// return true if list bottom may have unread remain
fun Column.saveRangeBottom(result: TootApiResult?, list: List<TimelineItem>?) =
    saveRange(true, bTop = false, result = result, list = list)

// return true if list bottom may have unread remain
fun Column.saveRangeTop(result: TootApiResult?, list: List<TimelineItem>?) =
    saveRange(false, bTop = true, result = result, list = list)

fun Column.addRange(
    bBottom: Boolean,
    path: String,
    delimiter: Char = if (-1 == path.indexOf('?')) '?' else '&'
) = if (bBottom) {
    if (idOld != null) "$path${delimiter}max_id=${idOld}" else path
} else {
    if (idRecent != null) "$path${delimiter}since_id=${idRecent}" else path
}

fun Column.addRangeMin(
    path: String,
    delimiter: Char = if (-1 != path.indexOf('?')) '&' else '?'
) = if (idRecent == null) path else "$path${delimiter}min_id=${idRecent}"

fun Column.toAdapterIndex(listIndex: Int): Int {
    return if (type.headerType != null) listIndex + 1 else listIndex
}

fun Column.toListIndex(adapterIndex: Int): Int {
    return if (type.headerType != null) adapterIndex - 1 else adapterIndex
}


////////////////////////////////////////////////////////////////////////
// Streaming

fun Column.onStart() {

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

fun Column.saveScrollPosition() {
    try {
        if (viewHolder?.saveScrollPosition() == true) {
            val ss = this.scroll_save
            if (ss != null) {
                val idx = toListIndex(ss.adapterIndex)
                if (0 <= idx && idx < list_data.size) {
                    val item = list_data[idx]
                    this.last_viewing_item_id = item.getOrderId()
                    // とりあえず保存はするが
                    // TLデータそのものを永続化しないかぎり出番はないっぽい
                }
            }
        }
    } catch (ex: Throwable) {
        Column.log.e(ex, "can't get last_viewing_item_id.")
    }
}

fun Column.getNotificationTypeString(): String {
    val sb = StringBuilder()
    sb.append("(")

    when (quick_filter) {
        Column.QUICK_FILTER_ALL -> {
            var n = 0
            if (!dont_show_reply) {
                if (n++ > 0) sb.append(", ")
                sb.append(context.getString(R.string.notification_type_mention))
            }
            if (!dont_show_follow) {
                if (n++ > 0) sb.append(", ")
                sb.append(context.getString(R.string.notification_type_follow))
            }
            if (!dont_show_boost) {
                if (n++ > 0) sb.append(", ")
                sb.append(context.getString(R.string.notification_type_boost))
            }
            if (!dont_show_favourite) {
                if (n++ > 0) sb.append(", ")
                sb.append(context.getString(R.string.notification_type_favourite))
            }
            if (isMisskey && !dont_show_reaction) {
                if (n++ > 0) sb.append(", ")
                sb.append(context.getString(R.string.notification_type_reaction))
            }
            if (!dont_show_vote) {
                if (n++ > 0) sb.append(", ")
                sb.append(context.getString(R.string.notification_type_vote))
            }
            val n_max = if (isMisskey) {
                6
            } else {
                5
            }
            if (n == 0 || n == n_max) return "" // 全部か皆無なら部分表記は要らない
        }

        Column.QUICK_FILTER_MENTION -> sb.append(context.getString(R.string.notification_type_mention))
        Column.QUICK_FILTER_FAVOURITE -> sb.append(context.getString(R.string.notification_type_favourite))
        Column.QUICK_FILTER_BOOST -> sb.append(context.getString(R.string.notification_type_boost))
        Column.QUICK_FILTER_FOLLOW -> sb.append(context.getString(R.string.notification_type_follow))
        Column.QUICK_FILTER_REACTION -> sb.append(context.getString(R.string.notification_type_reaction))
        Column.QUICK_FILTER_VOTE -> sb.append(context.getString(R.string.notification_type_vote))
        Column.QUICK_FILTER_POST -> sb.append(context.getString(R.string.notification_type_post))
    }

    sb.append(")")
    return sb.toString()
}

fun Column.mergeStreamingMessage() {
    val handler = app_state.handler

    // 未初期化や初期ロード中ならキューをクリアして何もしない
    if (!canHandleStreamingMessage()) {
        stream_data_queue.clear()
        handler.removeCallbacks(procMergeStreamingMessage)
        return
    }

    // 前回マージしてから暫くは待機してリトライ
    // カラムがビジー状態なら待機してリトライ
    val now = SystemClock.elapsedRealtime()
    var remain = last_show_stream_data.get() + 333L - now
    if (bRefreshLoading) remain = max(333L, remain)
    if (remain > 0) {
        handler.removeCallbacks(procMergeStreamingMessage)
        handler.postDelayed(procMergeStreamingMessage, remain)
        return
    }

    last_show_stream_data.set(now)

    val tmpList = ArrayList<TimelineItem>()
    while (true) tmpList.add(stream_data_queue.poll() ?: break)
    if (tmpList.isEmpty()) return

    // キューから読めた件数が0の場合を除き、少し後に再処理させることでマージ漏れを防ぐ
    handler.postDelayed(procMergeStreamingMessage, 333L)

    // ストリーミングされるデータは全てID順に並んでいるはず
    tmpList.sortByDescending { it.getOrderId() }

    val list_new = duplicate_map.filterDuplicate(tmpList)
    if (list_new.isEmpty()) return

    for (item in list_new) {
        if (enable_speech && item is TootStatus) {
            app_state.addSpeech(item.reblog ?: item)
        }
    }

    // 通知カラムならストリーミング経由で届いたデータを通知ワーカーに伝達する
    if (isNotificationColumn) {
        val list = ArrayList<TootNotification>()
        for (o in list_new) {
            if (o is TootNotification) {
                list.add(o)
            }
        }
        if (list.isNotEmpty()) {
            PollingWorker.injectData(context, access_info, list)
        }
    }

    // 最新のIDをsince_idとして覚える(ソートはしない)
    var new_id_max: EntityId? = null
    var new_id_min: EntityId? = null
    for (o in list_new) {
        try {
            val id = o.getOrderId()
            if (id.toString().isEmpty()) continue
            if (new_id_max == null || id > new_id_max) new_id_max = id
            if (new_id_min == null || id < new_id_min) new_id_min = id
        } catch (ex: Throwable) {
            // IDを取得できないタイプのオブジェクトだった
            // ストリームに来るのは通知かステータスだから、多分ここは通らない
            Column.log.trace(ex)
        }
    }

    val tmpRecent = idRecent
    val tmpNewMax = new_id_max

    if (tmpNewMax != null && (tmpRecent?.compareTo(tmpNewMax) ?: -1) == -1) {
        idRecent = tmpNewMax
        // XXX: コレはリフレッシュ時に取得漏れを引き起こすのでは…？
        // しかしコレなしだとリフレッシュ時に大量に読むことになる…
    }

    val holder = viewHolder

    // 事前にスクロール位置を覚えておく
    val holder_sp: ScrollPosition? = holder?.scrollPosition

    // idx番目の要素がListViewの上端から何ピクセル下にあるか
    var restore_idx = -2
    var restore_y = 0
    if (holder != null) {
        if (list_data.size > 0) {
            try {
                restore_idx = holder.findFirstVisibleListItem()
                restore_y = holder.getListItemOffset(restore_idx)
            } catch (ex: IndexOutOfBoundsException) {
                restore_idx = -2
                restore_y = 0
            }
        }
    }

    // 画面復帰時の自動リフレッシュではギャップが残る可能性がある
    if (bPutGap) {
        bPutGap = false
        try {
            if (list_data.size > 0 && new_id_min != null) {
                val since = list_data[0].getOrderId()
                if (new_id_min > since) {
                    val gap = TootGap(new_id_min, since)
                    list_new.add(gap)
                }
            }
        } catch (ex: Throwable) {
            Column.log.e(ex, "can't put gap.")
        }

    }

    val changeList = ArrayList<AdapterChange>()

    replaceConversationSummary(changeList, list_new, list_data)

    val added = list_new.size  // may 0

    var doneSound = false
    for (o in list_new) {
        if (o is TootStatus) {
            o.highlightSound?.let {
                if (!doneSound) {
                    doneSound = true
                    App1.sound(it)
                }
            }
            o.highlightSpeech?.let {
                app_state.addSpeech(it.name, dedupMode = DedupMode.RecentExpire)
            }
        }
    }

    changeList.add(AdapterChange(AdapterChangeType.RangeInsert, 0, added))
    list_data.addAll(0, list_new)

    fireShowContent(reason = "mergeStreamingMessage", changeList = changeList)

    if (holder != null) {
        when {
            holder_sp == null -> {
                // スクロール位置が先頭なら先頭にする
                Column.log.d("mergeStreamingMessage: has VH. missing scroll position.")
                viewHolder?.scrollToTop()
            }

            holder_sp.isHead -> {
                // スクロール位置が先頭なら先頭にする
                Column.log.d("mergeStreamingMessage: has VH. keep head. $holder_sp")
                holder.setScrollPosition(ScrollPosition())
            }

            restore_idx < -1 -> {
                // 可視範囲の検出に失敗
                Column.log.d("mergeStreamingMessage: has VH. can't get visible range.")
            }

            else -> {
                // 現在の要素が表示され続けるようにしたい
                Column.log.d("mergeStreamingMessage: has VH. added=$added")
                holder.setListItemTop(restore_idx + added, restore_y)
            }
        }
    } else {
        val scroll_save = this.scroll_save
        when {
            // スクロール位置が先頭なら先頭のまま
            scroll_save == null || scroll_save.isHead -> {
            }

            // 現在の要素が表示され続けるようにしたい
            else -> scroll_save.adapterIndex += added
        }
    }

    updateMisskeyCapture()
}

private const val DIR_BACKGROUND_IMAGE = "columnBackground"

fun getBackgroundImageDir(context: Context): File {
    val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    if (externalDir == null) {
        Column.log.e("getExternalFilesDir is null.")
    } else {
        val state = Environment.getExternalStorageState()
        if (state != Environment.MEDIA_MOUNTED) {
            Column.log.e("getExternalStorageState: ${state}")
        } else {
            Column.log.i("externalDir: ${externalDir}")
            externalDir.mkdir()
            val backgroundDir = File(externalDir, DIR_BACKGROUND_IMAGE)
            backgroundDir.mkdir()
            Column.log.i("backgroundDir: ${backgroundDir} exists=${backgroundDir.exists()}")
            return backgroundDir
        }
    }
    val backgroundDir = context.getDir(DIR_BACKGROUND_IMAGE, Context.MODE_PRIVATE)
    Column.log.i("backgroundDir: ${backgroundDir} exists=${backgroundDir.exists()}")
    return backgroundDir
}
