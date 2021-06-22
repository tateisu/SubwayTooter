package jp.juggler.subwaytooter.action

import android.app.AlertDialog
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.DlgConfirm
import jp.juggler.subwaytooter.onListMemberUpdated
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.*
import okhttp3.Request
import java.util.regex.Pattern

private val reFollowError422 = "follow".asciiPattern(Pattern.CASE_INSENSITIVE)
private val reFollowError404 = "Record not found".asciiPattern(Pattern.CASE_INSENSITIVE)

fun ActMain.listMemberAdd(
    accessInfo: SavedAccount,
    listId: EntityId,
    localWho: TootAccount,
    bFollow: Boolean = false,
    onListMemberUpdated: (willRegistered: Boolean, bSuccess: Boolean) -> Unit = { _, _ -> },
) {
    launchMain {
        runApiTask(accessInfo) { client ->
            val parser = TootParser(this, accessInfo)

            var userId = localWho.id

            if (accessInfo.isMisskey) {
                // misskeyのリストはフォロー無関係

                client.request(
                    "/api/users/lists/push",
                    accessInfo.putMisskeyApiToken().apply {
                        put("listId", listId)
                        put("userId", localWho.id)
                    }.toPostRequestBuilder()
                )
                // 204 no content
            } else {

                val isMe = accessInfo.isMe(localWho)
                if (isMe) {
                    val (ti, ri) = TootInstance.get(client)
                    ti ?: return@runApiTask ri
                    if (!ti.versionGE(TootInstance.VERSION_3_1_0_rc1)) {
                        return@runApiTask TootApiResult(getString(R.string.it_is_you))
                    }
                } else if (bFollow) {
                    // リモートユーザの解決
                    if (!accessInfo.isLocalUser(localWho)) {
                        val (r2, ar) = client.syncAccountByAcct(accessInfo, localWho.acct)
                        val user = ar?.get() ?: return@runApiTask r2
                        userId = user.id
                    }

                    val result = client.request(
                        "/api/v1/accounts/$userId/follow",
                        "".toFormRequestBody().toPost()
                    ) ?: return@runApiTask null

                    val relation = accessInfo.saveUserRelation(
                        parseItem(::TootRelationShip, parser, result.jsonObject)
                    ) ?: return@runApiTask TootApiResult("parse error.")

                    if (!relation.following) {
                        @Suppress("ControlFlowWithEmptyBody")
                        if (relation.requested) {
                            return@runApiTask TootApiResult(getString(R.string.cant_add_list_follow_requesting))
                        } else {
                            // リモートフォローの場合、正常ケースでもここを通る場合がある
                            // 何もしてはいけない…
                        }
                    }
                }

                // リストメンバー追加

                client.request(
                    "/api/v1/lists/$listId/accounts",
                    jsonObject {
                        put(
                            "account_ids",
                            jsonArray {
                                add(userId.toString())
                            }
                        )
                    }
                        .toPostRequestBuilder()
                )
            }
        }.let { result -> // may null
            var bSuccess = false

            try {
                val response = result?.response
                val error = result?.error

                fun isNotFollowed(): Boolean {
                    response ?: return false
                    error ?: return false
                    return when {
                        response.code == 422 && reFollowError422.matcher(error).find() -> true
                        response.code == 404 && reFollowError404.matcher(error).find() -> true
                        else -> false
                    }
                }

                when {
                    // cancelled.
                    result == null -> {
                    }
                    result.jsonObject != null -> {
                        for (column in appState.columnList) {
                            // リストメンバー追加イベントをカラムに伝達
                            column.onListMemberUpdated(accessInfo, listId, localWho, true)
                        }
                        // フォロー状態の更新を表示に反映させる
                        if (bFollow) showColumnMatchAccount(accessInfo)

                        showToast(false, R.string.list_member_added)

                        bSuccess = true
                    }

                    isNotFollowed() -> {
                        if (!bFollow) {
                            DlgConfirm.openSimple(
                                this@listMemberAdd,
                                getString(
                                    R.string.list_retry_with_follow,
                                    accessInfo.getFullAcct(localWho)
                                )
                            ) {
                                listMemberAdd(
                                    accessInfo,
                                    listId,
                                    localWho,
                                    bFollow = true,
                                    onListMemberUpdated = onListMemberUpdated
                                )
                            }
                        } else {
                            AlertDialog.Builder(this@listMemberAdd)
                                .setCancelable(true)
                                .setMessage(R.string.cant_add_list_follow_requesting)
                                .setNeutralButton(R.string.close, null)
                                .show()
                        }
                    }

                    else -> showToast(true, error)
                }
            } finally {
                onListMemberUpdated(true, bSuccess)
            }
        }
    }
}

fun ActMain.listMemberDelete(
    accessInfo: SavedAccount,
    listId: EntityId,
    localWho: TootAccount,
    onListMemberDeleted:  (willRegistered: Boolean, bSuccess: Boolean) -> Unit = { _, _ -> },
) {
    launchMain {
        runApiTask(accessInfo) { client ->
            if (accessInfo.isMisskey) {
                client.request(
                    "/api/users/lists/pull",
                    accessInfo.putMisskeyApiToken().apply {
                        put("listId", listId.toString())
                        put("userId", localWho.id.toString())
                    }
                        .toPostRequestBuilder()
                )
            } else {
                client.request(
                    "/api/v1/lists/$listId/accounts?account_ids[]=${localWho.id}",
                    Request.Builder().delete()
                )
            }
        }.let { result -> //may null

            var bSuccess = false
            try {
                when {
                    result == null -> {
                        // cancelled.
                    }

                    result.jsonObject == null ->
                        showToast(false, result.error)

                    else -> {
                        for (column in appState.columnList) {
                            column.onListMemberUpdated(accessInfo, listId, localWho, false)
                        }
                        showToast(false, R.string.delete_succeeded)
                        bSuccess = true
                    }
                }
            } finally {
                onListMemberDeleted(false, bSuccess)
            }
        }
    }
}
