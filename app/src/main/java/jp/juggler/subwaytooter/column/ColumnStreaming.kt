package jp.juggler.subwaytooter.column

import android.os.SystemClock
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.DedupMode
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.columnviewholder.*
import jp.juggler.subwaytooter.notification.injectData
import jp.juggler.subwaytooter.streaming.StreamManager
import jp.juggler.subwaytooter.streaming.StreamStatus
import jp.juggler.subwaytooter.util.ScrollPosition
import jp.juggler.util.*
import kotlin.math.max
import kotlin.math.min

private val log = LogCategory("ColumnStreaming")

// 別スレッドから呼ばれるが大丈夫か
fun Column.canStreamingState() = when {
    // 未初期化なら何もしない
    !bFirstInitialized -> {
        if (StreamManager.traceDelivery) log.v("canStartStreaming: column is not initialized.")
        false
    }

    // 初期ロード中なら何もしない
    bInitialLoading -> {
        if (StreamManager.traceDelivery) log.v("canStartStreaming: is in initial loading.")
        false
    }

    else -> true
}

fun Column.canStreamingTypeSub(): Boolean =
    when {
        accessInfo.isMisskey -> type.canStreamingMisskey(this)
        else -> type.canStreamingMastodon(this)
    }

fun Column.canStreamingType() = when {
    accessInfo.isNA -> false
    accessInfo.isPseudo -> isPublicStream && canStreamingTypeSub()
    else -> canStreamingTypeSub()
}

fun Column.canSpeech() =
    canStreamingType() && !isNotificationColumn

fun Column.canHandleStreamingMessage() =
    !isDispose.get() && canStreamingState()

//
// ストリーミングイベント経由で呼ばれるColumnメソッド
//

// ストリーミング経由でキューに溜まったデータをUIに反映する
fun Column.mergeStreamingMessage() {
    val handler = appState.handler

    // 未初期化や初期ロード中ならキューをクリアして何もしない
    if (!canHandleStreamingMessage()) {
        streamDataQueue.clear()
        handler.removeCallbacks(procMergeStreamingMessage)
        return
    }

    // 前回マージしてから暫くは待機してリトライ
    // カラムがビジー状態なら待機してリトライ
    val now = SystemClock.elapsedRealtime()
    var remain = lastShowStreamData.get() + 333L - now
    if (bRefreshLoading) remain = max(333L, remain)
    if (remain > 0) {
        handler.removeCallbacks(procMergeStreamingMessage)
        handler.postDelayed(procMergeStreamingMessage, remain)
        return
    }

    lastShowStreamData.set(now)

    // read items while queue is not empty
    val tmpList = ArrayList<TimelineItem>()
        .apply { while (true) add(streamDataQueue.poll() ?: break) }.notEmpty()
        ?: return

    // キューから読めた件数が0の場合を除き、少し後に再処理させることでマージ漏れを防ぐ
    handler.postDelayed(procMergeStreamingMessage, 333L)

    // orderId順ソートを徹底する
    tmpList.sortByDescending { it.getOrderId() }

    // 既にカラム中にあるデータは除去する
    val listNew = duplicateMap.filterDuplicate(tmpList).notEmpty() ?: return

    sendToSpeech(listNew)
    injectToPollingWorker(listNew)

    // 最新のIDをsince_idとして覚える(ソートはしない)
    var newIdMax: EntityId? = null
    var newIdMin: EntityId? = null
    for (o in listNew) {
        try {
            val id = o.getOrderId()
            if (id.toString().isEmpty()) continue
            if (newIdMax == null || id > newIdMax) newIdMax = id
            if (newIdMin == null || id < newIdMin) newIdMin = id
        } catch (ex: Throwable) {
            // IDを取得できないタイプのオブジェクトだった
            // ストリームに来るのは通知かステータスだから、多分ここは通らない
            log.e(ex, "mergeStreamingMessage failed.")
        }
    }

    val tmpRecent = idRecent
    val tmpNewMax = newIdMax

    if (tmpNewMax != null && (tmpRecent?.compareTo(tmpNewMax) ?: -1) == -1) {
        idRecent = tmpNewMax
        // XXX: コレはリフレッシュ時に取得漏れを引き起こすのでは…？
        // しかしコレなしだとリフレッシュ時に大量に読むことになる…
    }

    val holder = viewHolder

    // 事前にスクロール位置を覚えておく
    val holderSp: ScrollPosition? = holder?.scrollPosition

    // idx番目の要素がListViewの上端から何ピクセル下にあるか
    var restoreIdx = -2
    var restoreY = 0
    if (holder != null) {
        if (listData.size > 0) {
            try {
                restoreIdx = holder.findFirstVisibleListItem()
                restoreY = holder.getListItemOffset(restoreIdx)
            } catch (ex: IndexOutOfBoundsException) {
                log.w(ex, "findFirstVisibleListItem failed.")
                restoreIdx = -2
                restoreY = 0
            }
        }
    }

    // 画面復帰時の自動リフレッシュではギャップが残る可能性がある
    if (bPutGap) {
        bPutGap = false
        addGapAfterStreaming(listNew, newIdMin)
    }

    val changeList = ArrayList<AdapterChange>()

    replaceConversationSummary(changeList, listNew, listData)

    val added = listNew.size  // may 0

    var doneSound = false
    for (o in listNew) {
        if (o is TootStatus) {
            o.highlightSound?.let {
                if (!doneSound) {
                    doneSound = true
                    App1.sound(it)
                }
            }
            o.highlightSpeech?.let {
                appState.addSpeech(it.name, dedupMode = DedupMode.RecentExpire)
            }
        }
    }

    changeList.add(AdapterChange(AdapterChangeType.RangeInsert, 0, added))
    listData.addAll(0, listNew)

    fireShowContent(reason = "mergeStreamingMessage", changeList = changeList)
    scrollAfterStreaming(added, holderSp, restoreIdx, restoreY)
    updateMisskeyCapture()
}

// 通知カラムならストリーミング経由で届いたデータを通知ワーカーに伝達する
private fun Column.injectToPollingWorker(listNew: ArrayList<TimelineItem>) {
    if (!isNotificationColumn) return
    listNew.mapNotNull { it as? TootNotification }.notEmpty()
        ?.let { injectData(context, accessInfo, it) }
}

private fun Column.sendToSpeech(listNew: ArrayList<TimelineItem>) {
    if (!enableSpeech) return
    listNew.mapNotNull { it as? TootStatus }
        .forEach { appState.addSpeech(it.reblog ?: it) }
}

private fun Column.addGapAfterStreaming(listNew: ArrayList<TimelineItem>, newIdMin: EntityId?) {
    try {
        if (listData.size > 0 && newIdMin != null) {
            val since = listData[0].getOrderId()
            if (newIdMin > since) {
                val gap = TootGap(newIdMin, since)
                listNew.add(gap)
            }
        }
    } catch (ex: Throwable) {
        log.e(ex, "can't put gap.")
    }
}

private fun Column.scrollAfterStreaming(
    added: Int,
    holderSp: ScrollPosition?,
    restoreIdx: Int,
    restoreY: Int,
) {
    val holder = viewHolder
    if (holder == null) {
        val scrollSave = this.scrollSave
        when {
            // スクロール位置が先頭なら先頭のまま
            scrollSave == null || scrollSave.isHead -> Unit

            // 現在の要素が表示され続けるようにしたい
            else -> scrollSave.adapterIndex += added
        }
    } else {
        when {
            holderSp == null -> {
                // スクロール位置が先頭なら先頭にする
                log.d("mergeStreamingMessage: has VH. missing scroll position.")
                viewHolder?.scrollToTop()
            }

            holderSp.isHead -> {
                // スクロール位置が先頭なら先頭にする
                log.d("mergeStreamingMessage: has VH. keep head. $holderSp")
                holder.setScrollPosition(ScrollPosition())
            }

            restoreIdx < -1 -> {
                // 可視範囲の検出に失敗
                log.d("mergeStreamingMessage: has VH. can't get visible range.")
            }

            else -> {
                // 現在の要素が表示され続けるようにしたい
                log.d("mergeStreamingMessage: has VH. added=$added")
                holder.setListItemTop(restoreIdx + added, restoreY)
            }
        }
    }
}

fun Column.runOnMainLooperForStreamingEvent(proc: () -> Unit) {
    runOnMainLooper {
        if (canHandleStreamingMessage()) proc()
    }
}

fun Column.onStreamStatusChanged(status: StreamStatus) {
    log.d(
        "onStreamStatusChanged status=$status, bFirstInitialized=$bFirstInitialized, bInitialLoading=$bInitialLoading, column=${accessInfo.acct}/${
            getColumnName(true)
        }"
    )

    if (status == StreamStatus.Subscribed) {
        updateMisskeyCapture()
    }

    runOnMainLooperForStreamingEvent {
        if (isDispose.get()) return@runOnMainLooperForStreamingEvent
        fireShowColumnStatus()
    }
}

fun Column.onStreamingTimelineItem(item: TimelineItem) {
    if (StreamManager.traceDelivery) log.v("${accessInfo.acct} onTimelineItem")
    if (!canHandleStreamingMessage()) return

    when (item) {
        is TootConversationSummary -> {
            if (type != ColumnType.DIRECT_MESSAGES) return
            if (isFiltered(item.last_status)) return
            if (useOldApi) {
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

    streamDataQueue.add(item)
    appState.handler.post(procMergeStreamingMessage)
}

private fun Column.scanStatusById(
    caption: String,
    statusId: EntityId,
    block: (s: TootStatus) -> Boolean, // データを変更したら真
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

    listData.forEachIndexed { i, v ->
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
    val myId = EntityId.from(accessInfo.token_info, TootApiClient.KEY_USER_ID)
    if (myId == null) {
        log.w("onNoteUpdated: missing my userId. updating access token is recommenced!!")
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
    val announcementId = reaction.announcement_id ?: return
    val announcement = announcements?.find { it.id == announcementId } ?: return

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

// misskeyのキャプチャの対象となる投稿IDのリストを作る
// カラム内データの上(最新)から40件をキャプチャ対象とする
fun Column.updateMisskeyCapture() {
    if (!isMisskey) return

    val streamConnection = appState.streamManager.getConnection(this)
        ?: return

    val max = 40
    val list = ArrayList<EntityId>(max * 2) // リブログなどで膨れる場合がある

    fun add(s: TootStatus?) {
        s ?: return
        list.add(s.id)
        add(s.reblog)
        add(s.reply)
    }

    for (i in 0 until min(max, listData.size)) {
        val o = listData[i]
        if (o is TootStatus) {
            add(o)
        } else if (o is TootNotification) {
            add(o.status)
        }
    }

    if (list.isNotEmpty()) streamConnection.misskeySetCapture(list)
}
