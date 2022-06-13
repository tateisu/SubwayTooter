package jp.juggler.subwaytooter.column

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.columnviewholder.onListListUpdated
import jp.juggler.subwaytooter.notification.onNotificationCleared
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.BucketList
import jp.juggler.subwaytooter.util.matchHost
import jp.juggler.util.*
import kotlinx.coroutines.launch
import kotlin.collections.set

private val log = LogCategory("ColumnActions")

/*
    なんらかアクションを行った後にカラムデータを更新する処理など
*/

// 予約した投稿を削除した後の処理
fun Column.onScheduleDeleted(item: TootScheduled) {
    val tmpList = ArrayList<TimelineItem>(listData.size)
    for (o in listData) {
        if (o === item) continue
        tmpList.add(o)
    }
    if (tmpList.size != listData.size) {
        listData.clear()
        listData.addAll(tmpList)
        fireShowContent(reason = "onScheduleDeleted")
    }
}

// ステータスが削除された時に呼ばれる
fun Column.onStatusRemoved(tlHost: Host, statusId: EntityId) {

    if (isDispose.get() || bInitialLoading || bRefreshLoading) return

    if (!accessInfo.matchHost(tlHost)) return

    val tmpList = ArrayList<TimelineItem>(listData.size)
    for (o in listData) {
        if (o is TootStatus) {
            if (statusId == o.id) continue
            if (statusId == (o.reblog?.id ?: -1L)) continue
        } else if (o is TootNotification) {
            val s = o.status
            if (s != null) {
                if (statusId == s.id) continue
                if (statusId == (s.reblog?.id ?: -1L)) continue
            }
        }

        tmpList.add(o)
    }
    if (tmpList.size != listData.size) {
        listData.clear()
        listData.addAll(tmpList)
        fireShowContent(reason = "removeStatus")
    }
}

// ブーストやお気に入りの更新に使う。ステータスを列挙する。
fun Column.findStatus(
    targetApDomain: Host,
    targetStatusId: EntityId,
    callback: (account: SavedAccount, status: TootStatus) -> Boolean,
    // callback return true if rebind view required
) {
    if (!accessInfo.matchHost(targetApDomain)) return

    var bChanged = false

    fun procStatus(status: TootStatus?) {
        if (status != null) {
            if (targetStatusId == status.id) {
                if (callback(accessInfo, status)) bChanged = true
            }
            procStatus(status.reblog)
        }
    }

    for (data in listData) {
        when (data) {
            is TootNotification -> procStatus(data.status)
            is TootStatus -> procStatus(data)
        }
    }

    if (bChanged) fireRebindAdapterItems()
}

private const val INVALID_ACCOUNT = -1L

// ミュート、ブロックが成功した時に呼ばれる
// リストメンバーカラムでメンバーをリストから除去した時に呼ばれる
fun Column.removeAccountInTimeline(
    targetAccount: SavedAccount,
    whoId: EntityId,
    removeFromUserList: Boolean = false,
) {
    if (targetAccount != accessInfo) return

    val tmpList = ArrayList<TimelineItem>(listData.size)
    for (o in listData) {
        if (o is TootStatus) {
            if (whoId == (o.account.id)) continue
            if (whoId == (o.reblog?.account?.id ?: INVALID_ACCOUNT)) continue
        } else if (o is TootNotification) {
            if (whoId == (o.account?.id ?: INVALID_ACCOUNT)) continue
            if (whoId == (o.status?.account?.id ?: INVALID_ACCOUNT)) continue
            if (whoId == (o.status?.reblog?.account?.id ?: INVALID_ACCOUNT)) continue
        } else if (o is TootAccountRef && removeFromUserList) {
            if (whoId == o.get().id) continue
        }

        tmpList.add(o)
    }
    if (tmpList.size != listData.size) {
        listData.clear()
        listData.addAll(tmpList)
        fireShowContent(reason = "removeAccountInTimeline")
    }
}

// ミュート、ブロックが成功した時に呼ばれる
// リストメンバーカラムでメンバーをリストから除去した時に呼ばれる
// require full acct
fun Column.removeAccountInTimelinePseudo(acct: Acct) {

    val tmpList = ArrayList<TimelineItem>(listData.size)
    for (o in listData) {
        if (o is TootStatus) {
            if (acct == accessInfo.getFullAcct(o.account)) continue
            if (acct == accessInfo.getFullAcct(o.reblog?.account)) continue
        } else if (o is TootNotification) {
            if (acct == accessInfo.getFullAcct(o.account)) continue
            if (acct == accessInfo.getFullAcct(o.status?.account)) continue
            if (acct == accessInfo.getFullAcct(o.status?.reblog?.account)) continue
        }

        tmpList.add(o)
    }
    if (tmpList.size != listData.size) {
        listData.clear()
        listData.addAll(tmpList)
        fireShowContent(reason = "removeAccountInTimelinePseudo")
    }
}

// misskeyカラムやプロフカラムでブロック成功した時に呼ばれる
fun Column.updateFollowIcons(targetAccount: SavedAccount) {
    if (targetAccount != accessInfo) return

    fireShowContent(reason = "updateFollowIcons", reset = true)
}

// ユーザのブロック、ミュート、フォロー推奨の削除、フォローリクエストの承認/却下などから呼ばれる
fun Column.removeUser(targetAccount: SavedAccount, columnType: ColumnType, whoId: EntityId) {
    if (type == columnType && targetAccount == accessInfo) {
        val tmpList = ArrayList<TimelineItem>(listData.size)
        for (o in listData) {
            if (o is TootAccountRef) {
                if (o.get().id == whoId) continue
            }
            tmpList.add(o)
        }
        if (tmpList.size != listData.size) {
            listData.clear()
            listData.addAll(tmpList)
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

    listData.clear()
    duplicateMap.clear()
    fireShowContent(reason = "removeNotifications", reset = true)

    EndlessScope.launch {
        try {
            onNotificationCleared(context, accessInfo.db_id)
        } catch (ex: Throwable) {
            log.trace(ex, "onNotificationCleared failed.")
        }
    }
}

// 通知を削除した後に呼ばれる
fun Column.removeNotificationOne(targetAccount: SavedAccount, notification: TootNotification) {
    if (!isNotificationColumn) return

    if (accessInfo != targetAccount) return

    val tmpList = ArrayList<TimelineItem>(listData.size)
    for (o in listData) {
        if (o is TootNotification) {
            if (o.id == notification.id) continue
        }

        tmpList.add(o)
    }

    if (tmpList.size != listData.size) {
        listData.clear()
        listData.addAll(tmpList)
        fireShowContent(reason = "removeNotificationOne")
    }
}

fun Column.onMuteUpdated() {

    val checker = { status: TootStatus? -> status?.checkMuted() ?: false }

    val tmpList = ArrayList<TimelineItem>(listData.size)
    for (o in listData) {
        if (o is TootStatus) {
            if (checker(o)) continue
        }
        if (o is TootNotification) {
            if (checker(o.status)) continue
        }
        tmpList.add(o)
    }
    if (tmpList.size != listData.size) {
        listData.clear()
        listData.addAll(tmpList)
        fireShowContent(reason = "onMuteUpdated")
    }
}

fun Column.replaceStatus(statusId: EntityId, statusJson: JsonObject) {
    if (type == ColumnType.STATUS_HISTORY) return

    fun createStatus() =
        TootParser(context, accessInfo).status(statusJson)
            ?: error("replaceStatus: parse failed.")

    val tmpList = ArrayList(listData)
    var changed = false
    for (i in 0 until tmpList.size) {
        when (val item = tmpList[i]) {
            is TootStatus -> {
                if (item.id == statusId) {
                    tmpList[i] = createStatus()
                    changed = true
                } else if (item.reblog?.id == statusId) {
                    item.reblog = createStatus().also { it.reblogParent = item }
                    changed = true
                }
            }
            is TootNotification -> {
                if (item.status?.id == statusId) {
                    item.status = createStatus()
                    changed = true
                }
            }
        }
    }
    if (changed) {
        listData.clear()
        listData.addAll(tmpList)
        fireShowContent(reason = "replaceStatus")
    }
}

fun Column.onHideFavouriteNotification(acct: Acct) {
    if (!isNotificationColumn) return

    val tmpList = ArrayList<TimelineItem>(listData.size)

    for (o in listData) {
        if (o is TootNotification && o.type != TootNotification.TYPE_MENTION) {
            val who = o.account
            if (who != null) {
                val whoAcct = accessInfo.getFullAcct(who)
                if (whoAcct == acct) continue
            }
        }
        tmpList.add(o)
    }

    if (tmpList.size != listData.size) {
        listData.clear()
        listData.addAll(tmpList)
        fireShowContent(reason = "onHideFavouriteNotification")
    }
}

fun Column.onDomainBlockChanged(
    targetAccount: SavedAccount,
    domain: Host,
    bBlocked: Boolean,
) {

    if (targetAccount.apiHost != accessInfo.apiHost) return
    if (accessInfo.isPseudo) return

    if (type == ColumnType.DOMAIN_BLOCKS) {
        // ドメインブロック一覧を読み直す
        startLoading()
        return
    }

    if (bBlocked) {
        // ブロックしたのとドメイン部分が一致するアカウントからのステータスと通知をすべて除去する
        val checker =
            { account: TootAccount? -> if (account == null) false else account.acct.host == domain }

        val tmpList = ArrayList<TimelineItem>(listData.size)

        for (o in listData) {
            if (o is TootStatus) {
                if (checker(o.account)) continue
                if (checker(o.reblog?.account)) continue
            } else if (o is TootNotification) {
                if (checker(o.account)) continue
                if (checker(o.status?.account)) continue
                if (checker(o.status?.reblog?.account)) continue
            }
            tmpList.add(o)
        }
        if (tmpList.size != listData.size) {
            listData.clear()
            listData.addAll(tmpList)
            fireShowContent(reason = "onDomainBlockChanged")
        }
    }
}

fun Column.onListListUpdated(account: SavedAccount) {
    if (account != accessInfo) return
    if (type == ColumnType.LIST_LIST || type == ColumnType.MISSKEY_ANTENNA_LIST) {
        startLoading()
        val vh = viewHolder
        vh?.onListListUpdated()
    }
}

fun Column.onListNameUpdated(account: SavedAccount, item: TootList) {
    if (account != accessInfo) return
    if (type == ColumnType.LIST_LIST) {
        startLoading()
    } else if (type == ColumnType.LIST_TL || type == ColumnType.LIST_MEMBER) {
        if (item.id == profileId) {
            this.listInfo = item
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
    listId: EntityId,
    who: TootAccount,
    bAdd: Boolean,
) {
    if (type == ColumnType.LIST_TL && accessInfo == account && listId == profileId) {
        if (!bAdd) {
            removeAccountInTimeline(account, who.id)
        }
    } else if (type == ColumnType.LIST_MEMBER && accessInfo == account && listId == profileId) {
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
    listNew: ArrayList<TimelineItem>,
    listData: BucketList<TimelineItem>,
) {

    val newMap = HashMap<EntityId, TootConversationSummary>().apply {
        for (o in listNew) {
            if (o is TootConversationSummary) this[o.id] = o
        }
    }

    if (listData.isEmpty() || newMap.isEmpty()) return

    val removeSet = HashSet<EntityId>()
    for (i in listData.size - 1 downTo 0) {
        val o = listData[i] as? TootConversationSummary ?: continue
        val newItem = newMap[o.id] ?: continue

        if (o.last_status.uri == newItem.last_status.uri) {
            // 投稿が同じなので順序を入れ替えず、その場所で更新する
            changeList.add(AdapterChange(AdapterChangeType.RangeChange, i, 1))
            listData[i] = newItem
            removeSet.add(newItem.id)
            log.d("replaceConversationSummary: in-place update")
        } else {
            // 投稿が異なるので古い方を削除して、リストの順序を変える
            changeList.add(AdapterChange(AdapterChangeType.RangeRemove, i, 1))
            listData.removeAt(i)
            log.d("replaceConversationSummary: order change")
        }
    }

    val it = listNew.iterator()
    while (it.hasNext()) {
        val o = it.next() as? TootConversationSummary ?: continue
        if (removeSet.contains(o.id)) it.remove()
    }
}
