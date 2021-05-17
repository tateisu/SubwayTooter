package jp.juggler.subwaytooter

import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.notification.PollingWorker
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.matchHost
import jp.juggler.util.ellipsizeDot3

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


fun StringBuilder.appendHashtagExtra(column: Column): StringBuilder {
    val limit = (Column.HASHTAG_ELLIPSIZE * 2 - kotlin.math.min(length, Column.HASHTAG_ELLIPSIZE)) / 3
    if (column.hashtag_any.isNotBlank()) append(' ').append(
        column.context.getString(
            R.string.hashtag_title_any,
            column.hashtag_any.ellipsizeDot3(limit)
        )
    )
    if (column.hashtag_all.isNotBlank()) append(' ').append(
        column.context.getString(
            R.string.hashtag_title_all,
            column.hashtag_all.ellipsizeDot3(limit)
        )
    )
    if (column.hashtag_none.isNotBlank()) append(' ').append(
        column.context.getString(
            R.string.hashtag_title_none,
            column.hashtag_none.ellipsizeDot3(limit)
        )
    )
    return this
}
