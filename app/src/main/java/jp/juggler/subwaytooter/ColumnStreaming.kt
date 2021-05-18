package jp.juggler.subwaytooter

import android.os.SystemClock
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.notification.PollingWorker
import jp.juggler.subwaytooter.streaming.StreamManager
import jp.juggler.subwaytooter.streaming.StreamStatus
import jp.juggler.subwaytooter.util.ScrollPosition
import jp.juggler.util.runOnMainLooper
import kotlin.math.max

// 別スレッドから呼ばれるが大丈夫か
fun Column.canStartStreaming() = when {
    // 未初期化なら何もしない
    !bFirstInitialized -> {
        if (StreamManager.traceDelivery) Column.log.v("canStartStreaming: column is not initialized.")
        false
    }

    // 初期ロード中なら何もしない
    bInitialLoading -> {
        if (StreamManager.traceDelivery) Column.log.v("canStartStreaming: is in initial loading.")
        false
    }

    else -> true
}

fun Column.canHandleStreamingMessage() = !is_dispose.get() && canStartStreaming()


//
// ストリーミングイベント経由で呼ばれるColumnメソッド
//

// ストリーミング経由でキューに溜まったデータをUIに反映する
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

fun Column.runOnMainLooperForStreamingEvent(proc: () -> Unit) {
    runOnMainLooper {
        if (canHandleStreamingMessage()) proc()
    }
}

fun Column.onStreamStatusChanged(status: StreamStatus) {
    Column.log.d(
        "onStreamStatusChanged status=${status}, bFirstInitialized=$bFirstInitialized, bInitialLoading=$bInitialLoading, column=${access_info.acct}/${
            getColumnName(
                true
            )
        }"
    )

    if (status == StreamStatus.Subscribed) {
        updateMisskeyCapture()
    }

    runOnMainLooperForStreamingEvent {
        if (is_dispose.get()) return@runOnMainLooperForStreamingEvent
        fireShowColumnStatus()
    }
}

fun Column.onStreamingTimelineItem(item: TimelineItem) {
    if (StreamManager.traceDelivery) Column.log.v("${access_info.acct} onTimelineItem")
    if (!canHandleStreamingMessage()) return

    when (item) {
        is TootConversationSummary -> {
            if (type != ColumnType.DIRECT_MESSAGES) return
            if (isFiltered(item.last_status)) return
            if (use_old_api) {
                useConversationSummaryStreaming = false
                return
            } else {
                useConversationSummaryStreaming = true
            }
        }

        is TootNotification -> {
            if (!isNotificationColumn) return
            if (isFiltered(item)) return
        }

        is TootStatus -> {
            if (isNotificationColumn) return

            // マストドン2.6.0形式のDMカラム用イベントを利用したならば、その直後に発生する普通の投稿イベントを無視する
            if (useConversationSummaryStreaming) return

            // マストドンはLTLに外部ユーザの投稿を表示しない
            if (type == ColumnType.LOCAL && isMastodon && item.account.isRemote) return

            if (isFiltered(item)) return
        }
    }

    stream_data_queue.add(item)
    app_state.handler.post(procMergeStreamingMessage)
}

private fun Column.scanStatusById(
    caption: String,
    statusId: EntityId,
    block: (s: TootStatus) -> Boolean // データを変更したら真
) {
    val changeList = ArrayList<AdapterChange>()

    fun scanStatus1(s: TootStatus?, idx: Int) {
        s ?: return
        if (s.id == statusId) {
            if (block(s)) {
                changeList.add(AdapterChange(AdapterChangeType.RangeChange, idx, 1))
            }
        }
        scanStatus1(s.reblog, idx)
        scanStatus1(s.reply, idx)
    }

    list_data.forEachIndexed { i,v ->
        when (v) {
            is TootStatus -> scanStatus1(v, i)
            is TootNotification -> scanStatus1(v.status, i)
        }
    }

    if (changeList.isNotEmpty()) {
        fireShowContent(reason = caption, changeList = changeList)
    }
}

// Fedibird 絵文字リアクション機能
// APIの戻り値や通知データに新しいステータス情報が含まれるので、カラム中の該当する投稿のリアクション情報を更新する
// 自分によるリアクションは通知されない
// リアクション削除は通知されない
// 絵文字リアクションを手動で追加/削除した後に呼ばれる
// ストリーミングイベント受信時、該当アカウントのカラム全て対して呼ばれる
fun Column.updateEmojiReactionByApiResponse(newStatus: TootStatus?) {
    newStatus ?: return
    val newReactionSet = newStatus.reactionSet ?: TootReactionSet(isMisskey = false)
    scanStatusById("updateEmojiReactionByApiResponse", newStatus.id) { s ->
        s.updateReactionMastodon(newReactionSet)
        true
    }
}

// Fedibird 絵文字リアクション機能
// サーバ上で処理されたリアクション全てがuserストリームに送られる
// status_id がある
// me はない
fun Column.updateEmojiReactionByEvent(reaction: TootReaction) {
    val statusId = reaction.status_id ?: return
    scanStatusById("updateEmojiReactionByEvent", statusId) { s ->
        s.updateReactionMastodonByEvent(reaction)
        true
    }
}

fun Column.onMisskeyNoteUpdated(ev: MisskeyNoteUpdate) {
    // userId が自分かどうか調べる
    // アクセストークンの更新をして自分のuserIdが分かる状態でないとキャプチャ結果を反映させない
    // （でないとリアクションの2重カウントなどが発生してしまう)
    val myId = EntityId.from(access_info.token_info, TootApiClient.KEY_USER_ID)
    if (myId == null) {
        Column.log.w("onNoteUpdated: missing my userId. updating access token is recommenced!!")
    }

    val byMe = myId == ev.userId

    val caption = "onNoteUpdated ${ev.type}"
    val statusId = ev.noteId
    when (ev.type) {
        MisskeyNoteUpdate.Type.REACTION ->
            scanStatusById(caption, statusId) { s ->
                s.increaseReactionMisskey(ev.reaction, byMe, ev.emoji, "onNoteUpdated ${ev.userId}")
            }

        MisskeyNoteUpdate.Type.UNREACTION ->
            scanStatusById(caption, statusId) { s ->
                s.decreaseReactionMisskey(ev.reaction, byMe, "onNoteUpdated ${ev.userId}")
            }

        MisskeyNoteUpdate.Type.VOTED ->
            scanStatusById(caption, statusId) { s ->
                s.enquete?.increaseVote(context, ev.choice, byMe) ?: false
            }

        MisskeyNoteUpdate.Type.DELETED ->
            scanStatusById(caption, statusId) { s ->
                s.markDeleted(context, ev.deletedAt)
            }
    }
}

// サーバ告知が更新されたらストリーミングイベント経由で呼ばれる
fun Column.onAnnouncementUpdate(item: TootAnnouncement) {
    if (type != ColumnType.HOME) return

    val list = announcements
    if (list == null) {
        announcements = mutableListOf(item)
    } else {
        list.add(
            0,
            list.indexOfFirst { it.id == item.id }
                .takeIf { it != -1 }
                ?.let { TootAnnouncement.merge(list.removeAt(it), item) }
                ?: item
        )
    }
    announcementUpdated = SystemClock.elapsedRealtime()
    fireShowColumnHeader()
}

// サーバ告知が更新されたらストリーミングイベント経由で呼ばれる
fun Column.onAnnouncementDelete(id: EntityId) {
    announcements?.iterator()?.let {
        while (it.hasNext()) {
            val item = it.next()
            if (item.id != id) continue
            it.remove()
            announcementUpdated = SystemClock.elapsedRealtime()
            fireShowColumnHeader()
            break
        }
    }
}

// サーバ告知にリアクションがついたら、ストリーミングイベント経由で呼ばれる
fun Column.onAnnouncementReaction(reaction: TootReaction) {
    // find announcement
    val announcement_id = reaction.announcement_id ?: return
    val announcement = announcements?.find { it.id == announcement_id } ?: return

    // find reaction
    val index = announcement.reactions?.indexOfFirst { it.name == reaction.name }
    when {
        reaction.count <= 0L -> {
            if (index != null && index != -1) announcement.reactions?.removeAt(index)
        }

        index == null -> {
            announcement.reactions = ArrayList<TootReaction>().apply {
                add(reaction)
            }
        }

        index == -1 -> announcement.reactions?.add(reaction)

        else -> announcement.reactions?.get(index)?.let { old ->
            old.count = reaction.count
            // ストリーミングイベントにはmeが含まれないので、oldにあるmeは変更されない
        }
    }
    announcementUpdated = SystemClock.elapsedRealtime()
    fireShowColumnHeader()
}
