package jp.juggler.subwaytooter

import android.os.SystemClock
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.notification.PollingWorker
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.BucketList
import jp.juggler.subwaytooter.util.matchHost
import java.util.HashMap
import java.util.HashSet

/*
    なんらかアクションを行った後にカラムデータを更新する処理など
*/

// 予約した投稿を削除した後の処理
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

// 絵文字リアクションを手動で追加/削除した後に呼ばれる
// ストリーミングイベント受信時、該当アカウントのカラム全て対して呼ばれる
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

// ブーストやお気に入りの更新に使う。ステータスを列挙する。
fun Column.findStatus(
    target_apDomain: Host,
    target_status_id: EntityId,
    callback: (account: SavedAccount, status: TootStatus) -> Boolean
    // callback return true if rebind view required
) {
    if (!access_info.matchHost(target_apDomain)) return

    var bChanged = false

    fun procStatus(status: TootStatus?) {
        if (status != null) {
            if (target_status_id == status.id) {
                if (callback(access_info, status)) bChanged = true
            }
            procStatus(status.reblog)
        }
    }

    for (data in list_data) {
        when (data) {
            is TootNotification -> procStatus(data.status)
            is TootStatus -> procStatus(data)
        }
    }

    if (bChanged) fireRebindAdapterItems()
}

// ミュート、ブロックが成功した時に呼ばれる
// リストメンバーカラムでメンバーをリストから除去した時に呼ばれる
fun Column.removeAccountInTimeline(
    target_account: SavedAccount,
    who_id: EntityId,
    removeFromUserList: Boolean = false
) {
    if (target_account != access_info) return

    val INVALID_ACCOUNT = -1L

    val tmp_list = ArrayList<TimelineItem>(list_data.size)
    for (o in list_data) {
        if (o is TootStatus) {
            if (who_id == (o.account.id)) continue
            if (who_id == (o.reblog?.account?.id ?: INVALID_ACCOUNT)) continue
        } else if (o is TootNotification) {
            if (who_id == (o.account?.id ?: INVALID_ACCOUNT)) continue
            if (who_id == (o.status?.account?.id ?: INVALID_ACCOUNT)) continue
            if (who_id == (o.status?.reblog?.account?.id ?: INVALID_ACCOUNT)) continue
        } else if (o is TootAccountRef && removeFromUserList) {
            if (who_id == o.get().id) continue
        }

        tmp_list.add(o)
    }
    if (tmp_list.size != list_data.size) {
        list_data.clear()
        list_data.addAll(tmp_list)
        fireShowContent(reason = "removeAccountInTimeline")
    }
}

// ミュート、ブロックが成功した時に呼ばれる
// リストメンバーカラムでメンバーをリストから除去した時に呼ばれる
// require full acct
fun Column.removeAccountInTimelinePseudo(acct: Acct) {

    val tmp_list = ArrayList<TimelineItem>(list_data.size)
    for (o in list_data) {
        if (o is TootStatus) {
            if (acct == access_info.getFullAcct(o.account)) continue
            if (acct == access_info.getFullAcct(o.reblog?.account)) continue
        } else if (o is TootNotification) {
            if (acct == access_info.getFullAcct(o.account)) continue
            if (acct == access_info.getFullAcct(o.status?.account)) continue
            if (acct == access_info.getFullAcct(o.status?.reblog?.account)) continue
        }

        tmp_list.add(o)
    }
    if (tmp_list.size != list_data.size) {
        list_data.clear()
        list_data.addAll(tmp_list)
        fireShowContent(reason = "removeAccountInTimelinePseudo")
    }
}

// misskeyカラムやプロフカラムでブロック成功した時に呼ばれる
fun Column.updateFollowIcons(target_account: SavedAccount) {
    if (target_account != access_info) return

    fireShowContent(reason = "updateFollowIcons", reset = true)
}

// ユーザのブロック、ミュート、フォロー推奨の削除、フォローリクエストの承認/却下などから呼ばれる
fun Column.removeUser(targetAccount: SavedAccount, columnType: ColumnType, who_id: EntityId) {
    if (type == columnType && targetAccount == access_info) {
        val tmp_list = ArrayList<TimelineItem>(list_data.size)
        for (o in list_data) {
            if (o is TootAccountRef) {
                if (o.get().id == who_id) continue
            }
            tmp_list.add(o)
        }
        if (tmp_list.size != list_data.size) {
            list_data.clear()
            list_data.addAll(tmp_list)
            fireShowContent(reason = "removeUser")
        }
    }
}

// 通知カラムの通知を全て削除した後に呼ばれる
fun Column.removeNotifications() {
    cancelLastTask()

    mRefreshLoadingErrorPopupState = 0
    mRefreshLoadingError = ""
    bRefreshLoading = false
    mInitialLoadingError = ""
    bInitialLoading = false
    idOld = null
    idRecent = null
    offsetNext = 0
    pagingType = ColumnPagingType.Default

    list_data.clear()
    duplicate_map.clear()
    fireShowContent(reason = "removeNotifications", reset = true)

    PollingWorker.queueNotificationCleared(context, access_info.db_id)
}

// 通知を削除した後に呼ばれる
fun Column.removeNotificationOne(target_account: SavedAccount, notification: TootNotification) {
    if (!isNotificationColumn) return

    if (access_info != target_account) return

    val tmp_list = ArrayList<TimelineItem>(list_data.size)
    for (o in list_data) {
        if (o is TootNotification) {
            if (o.id == notification.id) continue
        }

        tmp_list.add(o)
    }

    if (tmp_list.size != list_data.size) {
        list_data.clear()
        list_data.addAll(tmp_list)
        fireShowContent(reason = "removeNotificationOne")
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

