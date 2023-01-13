package jp.juggler.subwaytooter.action

import androidx.appcompat.app.AlertDialog
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.actmain.addColumn
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.column.isNotificationColumn
import jp.juggler.subwaytooter.column.removeNotificationOne
import jp.juggler.subwaytooter.column.removeNotifications
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.log.showToast
import jp.juggler.util.network.toFormRequestBody
import jp.juggler.util.network.toPost

fun ActMain.clickNotificationFrom(
    pos: Int,
    accessInfo: SavedAccount,
    who: TootAccount,
) {
    if (accessInfo.isMisskey) {
        showToast(false, R.string.misskey_account_not_supported)
    } else {
        accessInfo.getFullAcct(who).validFull()?.let {
            addColumn(
                pos,
                accessInfo,
                ColumnType.NOTIFICATION_FROM_ACCT,
                it
            )
        }
    }
}

fun ActMain.notificationDeleteOne(
    accessInfo: SavedAccount,
    notification: TootNotification?,
) {
    notification ?: return

    launchMain {
        runApiTask(accessInfo) { client ->
            // https://github.com/tootsuite/mastodon/commit/30f5bcf3e749be9651ed39a07b893f70605f8a39
            // 2種類のAPIがあり、片方は除去された

            // まず新しいAPIを試す
            val result = client.request(
                "/api/v1/notifications/${notification.id}/dismiss",
                "".toFormRequestBody().toPost()
            )

            when (result?.response?.code) {

                // 新しいAPIがない場合、古いAPIを試す
                422 -> client.request(
                    "/api/v1/notifications/dismiss",
                    "id=${notification.id}".toFormRequestBody().toPost()
                )

                else -> result
            }
        }?.let { result ->
            when (result.jsonObject) {
                null -> showToast(true, result.error)
                else -> {
                    // 成功したら空オブジェクトが返される
                    for (column in appState.columnList) {
                        column.removeNotificationOne(accessInfo, notification)
                    }
                    showToast(true, R.string.delete_succeeded)
                }
            }
        }
    }
}

fun ActMain.notificationDeleteAll(
    targetAccount: SavedAccount,
    bConfirmed: Boolean = false,
) {
    if (!bConfirmed) {
        AlertDialog.Builder(this)
            .setMessage(R.string.confirm_delete_notification)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                notificationDeleteAll(targetAccount, true)
            }
            .show()
        return
    }

    launchMain {
        runApiTask(targetAccount) { client ->
            client.request(
                "/api/v1/notifications/clear",
                "".toFormRequestBody().toPost()
            )
        }?.let { result ->
            when (result.jsonObject) {
                null -> showToast(false, result.error)

                else -> {
                    // ok. api have return empty object.
                    for (column in appState.columnList) {
                        if (column.isNotificationColumn && column.accessInfo == targetAccount) {
                            column.removeNotifications()
                        }
                    }
                    showToast(false, R.string.delete_succeeded)
                }
            }
        }
    }
}
