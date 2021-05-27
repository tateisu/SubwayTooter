package jp.juggler.subwaytooter.action

import androidx.appcompat.app.AlertDialog
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.launchMain
import jp.juggler.util.showToast
import jp.juggler.util.toFormRequestBody
import jp.juggler.util.toPost

fun ActMain.notificationDeleteOne(
    access_info: SavedAccount,
    notification: TootNotification
) {
    launchMain {
        runApiTask(access_info) { client ->
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
                    for (column in app_state.columnList) {
                        column.removeNotificationOne(access_info, notification)
                    }
                    showToast(true, R.string.delete_succeeded)
                }
            }
        }
    }
}

fun ActMain.notificationDeleteAll(
    target_account: SavedAccount,
    bConfirmed: Boolean = false
) {
    if (!bConfirmed) {
        AlertDialog.Builder(this)
            .setMessage(R.string.confirm_delete_notification)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                notificationDeleteAll(target_account, true)
            }
            .show()
        return
    }

    launchMain {
        runApiTask(target_account) { client ->
            client.request(
                "/api/v1/notifications/clear",
                "".toFormRequestBody().toPost()
            )
        }?.let { result ->
            when (result.jsonObject) {
                null -> showToast(false, result.error)

                else -> {
                    // ok. api have return empty object.
                    for (column in app_state.columnList) {
                        if (column.isNotificationColumn && column.access_info == target_account) {
                            column.removeNotifications()
                        }
                    }
                    showToast(false, R.string.delete_succeeded)
                }
            }
        }
    }
}
