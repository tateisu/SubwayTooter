package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.os.SystemClock
import android.view.View
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.streaming.StreamManager
import jp.juggler.subwaytooter.table.FavMute
import jp.juggler.subwaytooter.table.HighlightWord
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.util.BucketList
import jp.juggler.subwaytooter.util.matchHost
import jp.juggler.util.*
import org.jetbrains.anko.backgroundDrawable
import java.util.HashMap
import java.util.HashSet
import java.util.regex.Pattern
import kotlin.math.min

enum class ColumnPagingType {
    Default,
    Cursor,
    Offset,
    None,
}

enum class ProfileTab(val id: Int, val ct: ColumnType) {
    Status(0, ColumnType.TabStatus),
    Following(1, ColumnType.TabFollowing),
    Followers(2, ColumnType.TabFollowers)
}

enum class HeaderType(val viewType: Int) {
    Profile(1),
    Search(2),
    Instance(3),
    Filter(4),
    ProfileDirectory(5),
}


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


fun Column.onScheduleDeleted(item: TootScheduled) {
    val tmp_list = ArrayList<TimelineItem>(list_data.size)
    for (o in list_data) {
        if (o === item) continue
        tmp_list.add(o)
    }
    if (tmp_list.size != list_data.size) {
        list_data.clear()
        list_data.addAll(tmp_list)
        fireShowContent(reason = "onScheduleDeleted")
    }
}


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

fun Column.hasHashtagExtra() = when {

    isMisskey -> false

    type == ColumnType.HASHTAG -> true

    // ColumnType.HASHTAG_FROM_ACCT は追加のタグを指定しても結果に反映されない
    else -> false
}

suspend fun Column.loadProfileAccount(client: TootApiClient, parser: TootParser, bForceReload: Boolean): TootApiResult? =
    when {
        // リロード不要なら何もしない
        this.who_account != null && !bForceReload -> null

        isMisskey -> client.request(
            "/api/users/show",
            access_info.putMisskeyApiToken().apply {
                put("userId", profile_id)
            }.toPostRequestBuilder()
        )?.also { result1 ->
            // ユーザリレーションの取り扱いのため、別のparserを作ってはいけない
            parser.misskeyDecodeProfilePin = true
            try {
                TootAccountRef.mayNull(parser, parser.account(result1.jsonObject))?.also { a ->
                    this.who_account = a
                    client.publishApiProgress("") // カラムヘッダの再表示
                }
            } finally {
                parser.misskeyDecodeProfilePin = false
            }
        }

        else -> client.request(
            "/api/v1/accounts/%{profile_id}"
        )?.also { result1 ->
            TootAccountRef.mayNull(parser, parser.account(result1.jsonObject))?.also { a ->
                this.who_account = a

                this.who_featured_tags = null
                client.request("/api/v1/accounts/${profile_id}/featured_tags")
                    ?.also { result2 ->

                        this.who_featured_tags =
                            TootTag.parseListOrNull(parser, result2.jsonArray)
                    }

                client.publishApiProgress("") // カラムヘッダの再表示
            }
        }
    }

fun Column.loadSearchDesc(raw_en: Int, raw_ja: Int): String {
    val res_id = if ("ja" == context.getString(R.string.language_code)) raw_ja else raw_en
    return context.loadRawResource(res_id).decodeUTF8()
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

fun Column.onMisskeyNoteUpdated(ev: MisskeyNoteUpdate) {
    // userId が自分かどうか調べる
    // アクセストークンの更新をして自分のuserIdが分かる状態でないとキャプチャ結果を反映させない
    // （でないとリアクションの2重カウントなどが発生してしまう)
    val myId = EntityId.from(access_info.token_info, TootApiClient.KEY_USER_ID)
    if (myId == null) {
        Column.log.w("onNoteUpdated: missing my userId. updating access token is recommenced!!")
    }

    val byMe = myId == ev.userId

    val changeList = ArrayList<AdapterChange>()

    fun scanStatus1(s: TootStatus?, idx: Int, block: (s: TootStatus) -> Boolean) {
        s ?: return
        if (s.id == ev.noteId) {
            if (block(s)) {
                changeList.add(AdapterChange(AdapterChangeType.RangeChange, idx, 1))
            }
        }
        scanStatus1(s.reblog, idx, block)
        scanStatus1(s.reply, idx, block)
    }

    fun scanStatusAll(block: (s: TootStatus) -> Boolean) {
        for (i in 0 until list_data.size) {
            val o = list_data[i]
            if (o is TootStatus) {
                scanStatus1(o, i, block)
            } else if (o is TootNotification) {
                scanStatus1(o.status, i, block)
            }
        }
    }

    when (ev.type) {
        MisskeyNoteUpdate.Type.REACTION -> scanStatusAll { s ->
            s.increaseReactionMisskey(ev.reaction, byMe, ev.emoji, "onNoteUpdated ${ev.userId}")
        }

        MisskeyNoteUpdate.Type.UNREACTION -> scanStatusAll { s ->
            s.decreaseReactionMisskey(ev.reaction, byMe, "onNoteUpdated ${ev.userId}")
        }

        MisskeyNoteUpdate.Type.VOTED -> scanStatusAll { s ->
            s.enquete?.increaseVote(context, ev.choice, byMe) ?: false
        }

        MisskeyNoteUpdate.Type.DELETED -> scanStatusAll { s ->
            s.markDeleted(context, ev.deletedAt)
        }
    }

    if (changeList.isNotEmpty()) {
        fireShowContent(reason = "onNoteUpdated", changeList = changeList)
    }
}

// Fedibird 絵文字リアクション機能
// APIの戻り値や通知データに新しいステータス情報が含まれるので、カラム中の該当する投稿のリアクション情報を更新する
// 自分によるリアクションは通知されない
// リアクション削除は通知されない
fun Column.updateEmojiReaction(newStatus: TootStatus?) {
    newStatus ?: return
    val statusId = newStatus.id
    val newReactionSet = newStatus.reactionSet ?: TootReactionSet(isMisskey = false)

    val changeList = ArrayList<AdapterChange>()

    fun scanStatus1(s: TootStatus?, idx: Int, block: (s: TootStatus) -> Boolean) {
        s ?: return
        if (s.id == statusId) {
            if (block(s)) {
                changeList.add(AdapterChange(AdapterChangeType.RangeChange, idx, 1))
            }
        }
        scanStatus1(s.reblog, idx, block)
        scanStatus1(s.reply, idx, block)
    }

    fun scanStatusAll(block: (s: TootStatus) -> Boolean) {
        for (i in 0 until list_data.size) {
            val o = list_data[i]
            if (o is TootStatus) {
                scanStatus1(o, i, block)
            } else if (o is TootNotification) {
                scanStatus1(o.status, i, block)
            }
        }
    }

    scanStatusAll { s ->
        s.updateReactionMastodon(newReactionSet)
        true
    }

    if (changeList.isNotEmpty()) {
        fireShowContent(reason = "onEmojiReaction", changeList = changeList)
    }
}

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


fun Column.onDomainBlockChanged(
    target_account: SavedAccount,
    domain: Host,
    bBlocked: Boolean
) {

    if (target_account.apiHost != access_info.apiHost) return
    if (access_info.isPseudo) return

    if (type == ColumnType.DOMAIN_BLOCKS) {
        // ドメインブロック一覧を読み直す
        startLoading()
        return
    }

    if (bBlocked) {
        // ブロックしたのとドメイン部分が一致するアカウントからのステータスと通知をすべて除去する
        val checker =
            { account: TootAccount? -> if (account == null) false else account.acct.host == domain }

        val tmp_list = ArrayList<TimelineItem>(list_data.size)

        for (o in list_data) {
            if (o is TootStatus) {
                if (checker(o.account)) continue
                if (checker(o.reblog?.account)) continue
            } else if (o is TootNotification) {
                if (checker(o.account)) continue
                if (checker(o.status?.account)) continue
                if (checker(o.status?.reblog?.account)) continue
            }
            tmp_list.add(o)
        }
        if (tmp_list.size != list_data.size) {
            list_data.clear()
            list_data.addAll(tmp_list)
            fireShowContent(reason = "onDomainBlockChanged")
        }

    }

}

fun Column.onListListUpdated(account: SavedAccount) {
    if (account != access_info) return
    if (type == ColumnType.LIST_LIST || type == ColumnType.MISSKEY_ANTENNA_LIST) {
        startLoading()
        val vh = viewHolder
        vh?.onListListUpdated()
    }
}

fun Column.onListNameUpdated(account: SavedAccount, item: TootList) {
    if (account != access_info) return
    if (type == ColumnType.LIST_LIST) {
        startLoading()
    } else if (type == ColumnType.LIST_TL || type == ColumnType.LIST_MEMBER) {
        if (item.id == profile_id) {
            this.list_info = item
            fireShowColumnHeader()
        }
    }
}

//	fun onAntennaNameUpdated(account : SavedAccount, item : MisskeyAntenna) {
//		if(account != access_info) return
//		if(type == ColumnType.MISSKEY_ANTENNA_LIST) {
//			startLoading()
//		} else if(type == ColumnType.MISSKEY_ANTENNA_TL) {
//			if(item.id == profile_id) {
//				this.antenna_info = item
//				fireShowColumnHeader()
//			}
//		}
//	}

fun Column.onListMemberUpdated(
    account: SavedAccount,
    list_id: EntityId,
    who: TootAccount,
    bAdd: Boolean
) {
    if (type == ColumnType.LIST_TL && access_info == account && list_id == profile_id) {
        if (!bAdd) {
            removeAccountInTimeline(account, who.id)
        }
    } else if (type == ColumnType.LIST_MEMBER && access_info == account && list_id == profile_id) {
        if (!bAdd) {
            removeAccountInTimeline(account, who.id)
        }
    }
}

fun Column.onMuteUpdated() {

    val checker = { status: TootStatus? -> status?.checkMuted() ?: false }

    val tmp_list = ArrayList<TimelineItem>(list_data.size)
    for (o in list_data) {
        if (o is TootStatus) {
            if (checker(o)) continue
        }
        if (o is TootNotification) {
            if (checker(o.status)) continue
        }
        tmp_list.add(o)
    }
    if (tmp_list.size != list_data.size) {
        list_data.clear()
        list_data.addAll(tmp_list)
        fireShowContent(reason = "onMuteUpdated")
    }
}

fun Column.onHideFavouriteNotification(acct: Acct) {
    if (!isNotificationColumn) return

    val tmp_list = ArrayList<TimelineItem>(list_data.size)

    for (o in list_data) {
        if (o is TootNotification && o.type != TootNotification.TYPE_MENTION) {
            val a = o.account
            if (a != null) {
                val a_acct = access_info.getFullAcct(a)
                if (a_acct == acct) continue
            }
        }
        tmp_list.add(o)
    }
    if (tmp_list.size != list_data.size) {
        list_data.clear()
        list_data.addAll(tmp_list)
        fireShowContent(reason = "onHideFavouriteNotification")
    }
}



// ステータスが削除された時に呼ばれる
fun Column.onStatusRemoved(tl_host: Host, status_id: EntityId) {

    if (is_dispose.get() || bInitialLoading || bRefreshLoading) return

    if (!access_info.matchHost(tl_host)) return

    val tmp_list = ArrayList<TimelineItem>(list_data.size)
    for (o in list_data) {
        if (o is TootStatus) {
            if (status_id == o.id) continue
            if (status_id == (o.reblog?.id ?: -1L)) continue
        } else if (o is TootNotification) {
            val s = o.status
            if (s != null) {
                if (status_id == s.id) continue
                if (status_id == (s.reblog?.id ?: -1L)) continue
            }
        }

        tmp_list.add(o)
    }
    if (tmp_list.size != list_data.size) {
        list_data.clear()
        list_data.addAll(tmp_list)
        fireShowContent(reason = "removeStatus")
    }
}

// 既存データ中の会話サマリ項目と追加データの中にIDが同じものがあれば
// 既存データを入れ替えて追加データから削除するか
// 既存データを削除するかする
fun replaceConversationSummary(
    changeList: ArrayList<AdapterChange>,
    list_new: ArrayList<TimelineItem>,
    list_data: BucketList<TimelineItem>
) {

    val newMap = HashMap<EntityId, TootConversationSummary>().apply {
        for (o in list_new) {
            if (o is TootConversationSummary) this[o.id] = o
        }
    }

    if (list_data.isEmpty() || newMap.isEmpty()) return

    val removeSet = HashSet<EntityId>()
    for (i in list_data.size - 1 downTo 0) {
        val o = list_data[i] as? TootConversationSummary ?: continue
        val newItem = newMap[o.id] ?: continue

        if (o.last_status.uri == newItem.last_status.uri) {
            // 投稿が同じなので順序を入れ替えず、その場所で更新する
            changeList.add(AdapterChange(AdapterChangeType.RangeChange, i, 1))
            list_data[i] = newItem
            removeSet.add(newItem.id)
            Column.log.d("replaceConversationSummary: in-place update")
        } else {
            // 投稿が異なるので古い方を削除して、リストの順序を変える
            changeList.add(AdapterChange(AdapterChangeType.RangeRemove, i, 1))
            list_data.removeAt(i)
            Column.log.d("replaceConversationSummary: order change")
        }
    }

    val it = list_new.iterator()
    while (it.hasNext()) {
        val o = it.next() as? TootConversationSummary ?: continue
        if (removeSet.contains(o.id)) it.remove()
    }
}
