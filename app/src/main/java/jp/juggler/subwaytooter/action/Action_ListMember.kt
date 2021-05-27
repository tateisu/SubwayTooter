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

fun interface ListMemberCallback {
    fun onListMemberUpdated(willRegistered: Boolean, bSuccess: Boolean)
}

private val reFollowError422 = "follow".asciiPattern(Pattern.CASE_INSENSITIVE)
private val reFollowError404 = "Record not found".asciiPattern(Pattern.CASE_INSENSITIVE)

fun ActMain.listMemberAdd(
    access_info: SavedAccount,
    list_id: EntityId,
    local_who: TootAccount,
    bFollow: Boolean = false,
    callback: ListMemberCallback?
) {
    launchMain {
        runApiTask(access_info) { client ->
            val parser = TootParser(this, access_info)

            var userId = local_who.id

            if (access_info.isMisskey) {
                // misskeyのリストはフォロー無関係

                client.request(
                    "/api/users/lists/push",
                    access_info.putMisskeyApiToken().apply {
                        put("listId", list_id)
                        put("userId", local_who.id)

                    }.toPostRequestBuilder()
                )
                // 204 no content
            } else {

                val isMe = access_info.isMe(local_who)
                if (isMe) {
                    val (ti, ri) = TootInstance.get(client)
                    ti ?: return@runApiTask ri
                    if (!ti.versionGE(TootInstance.VERSION_3_1_0_rc1)) {
                        return@runApiTask TootApiResult(getString(R.string.it_is_you))
                    }
                } else if (bFollow) {
                    // リモートユーザの解決
                    if (!access_info.isLocalUser(local_who)) {
                        val (r2, ar) = client.syncAccountByAcct(access_info, local_who.acct)
                        val user = ar?.get() ?: return@runApiTask r2
                        userId = user.id
                    }

                    val result = client.request(
                        "/api/v1/accounts/$userId/follow",
                        "".toFormRequestBody().toPost()
                    ) ?: return@runApiTask null

                    val relation = access_info.saveUserRelation(
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
                    "/api/v1/lists/$list_id/accounts",
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

                fun isNotFollowed():Boolean{
                    response ?: return false
                    error?: return false
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
                        for (column in app_state.columnList) {
                            // リストメンバー追加イベントをカラムに伝達
                            column.onListMemberUpdated(access_info, list_id, local_who, true)
                        }
                        // フォロー状態の更新を表示に反映させる
                        if (bFollow) showColumnMatchAccount(access_info)

                        showToast(false, R.string.list_member_added)

                        bSuccess = true
                    }

                    isNotFollowed() -> {
                        if (!bFollow) {
                            DlgConfirm.openSimple(
                                this@listMemberAdd,
                                getString(
                                    R.string.list_retry_with_follow,
                                    access_info.getFullAcct(local_who)
                                )
                            ) {
                                listMemberAdd(
                                    access_info,
                                    list_id,
                                    local_who,
                                    bFollow = true,
                                    callback = callback
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
                callback?.onListMemberUpdated(true, bSuccess)
            }
        }
    }
}

fun ActMain.listMemberDelete(
    access_info: SavedAccount,
    list_id: EntityId,
    local_who: TootAccount,
    callback: ListMemberCallback?
) {
    launchMain {
        runApiTask(access_info) { client ->
            if (access_info.isMisskey) {
                client.request(
                    "/api/users/lists/pull",
                    access_info.putMisskeyApiToken().apply {
                        put("listId", list_id.toString())
                        put("userId", local_who.id.toString())
                    }
                        .toPostRequestBuilder()
                )
            } else {
                client.request(
                    "/api/v1/lists/${list_id}/accounts?account_ids[]=${local_who.id}",
                    Request.Builder().delete()
                )
            }
        }.let { result -> //may null

            var bSuccess = false
            try {
                when {
                    // cancelled.
                    result == null -> {

                    }
                    result.jsonObject == null ->
                        showToast(false, result.error)

                    else -> {
                        for (column in app_state.columnList) {
                            column.onListMemberUpdated(access_info, list_id, local_who, false)
                        }
                        showToast(false, R.string.delete_succeeded)
                        bSuccess = true
                    }
                }
            } finally {
                callback?.onListMemberUpdated(false, bSuccess)
            }
        }
    }
}

