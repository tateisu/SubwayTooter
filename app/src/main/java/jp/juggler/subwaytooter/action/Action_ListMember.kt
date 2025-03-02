package jp.juggler.subwaytooter.action

import android.app.AlertDialog
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.actmain.showColumnMatchAccount
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.api.syncAccountByAcct
import jp.juggler.subwaytooter.column.onListMemberUpdated
import jp.juggler.subwaytooter.dialog.DlgConfirm.confirm
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.daoUserRelation
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.data.asciiPattern
import jp.juggler.util.data.buildJsonArray
import jp.juggler.util.data.buildJsonObject
import jp.juggler.util.data.notBlank
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.network.*
import kotlinx.coroutines.CancellationException
import okhttp3.Request
import java.util.regex.Pattern

private val log = LogCategory("Action_ListMember")
private val reFollowError422 = "follow".asciiPattern(Pattern.CASE_INSENSITIVE)
private val reFollowError404 = "Record not found".asciiPattern(Pattern.CASE_INSENSITIVE)

class MemberNotFollowedException : IllegalStateException()

/**
 * リストに追加する。失敗したら例外を投げる。
 */
suspend fun ActMain.listMemberAdd(
    accessInfo: SavedAccount,
    listId: EntityId,
    localWho: TootAccount,
) {
    var i = 0
    while(true){
        ++i
        val result = runApiTask(accessInfo) { client ->
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
                } else if (i ==1) {
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

                    val relation = daoUserRelation.saveUserRelation(
                        accessInfo,
                        parseItem(result.jsonObject) { TootRelationShip(parser, it) }
                    ) ?: return@runApiTask TootApiResult("parse error.")

                    if (!relation.following) {
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
                    buildJsonObject {
                        put(
                            "account_ids",
                            buildJsonArray {
                                add(userId.toString())
                            }
                        )
                    }.toPostRequestBuilder()
                )
            }
        }

        // cancelled.
        result ?: throw CancellationException()

        // 成功
        if( result.jsonObject != null) break

        val response = result.response
        val errorMessage = result.error
        val isNotFollowed = response != null && errorMessage != null && when {
            response.code == 422 && reFollowError422.matcher(errorMessage).find() -> true
            response.code == 404 && reFollowError404.matcher(errorMessage).find() -> true
            else -> false
        }
        when {
            isNotFollowed -> {
                when {
                    // 初回ならフォロー操作を行うかユーザに尋ねてリトライ
                    i==1 -> {
                        confirm(
                            R.string.list_retry_with_follow,
                            accessInfo.getFullAcct(localWho)
                        )
                        continue
                    }
                    // 2回目以降は諦める
                    else ->{
                        // フォロー操作を行ってもダメなら失敗とみなす
                        // フォロー要求が承認されるまでラグがあるかもしれないので、ユーザに伝える
                        AlertDialog.Builder(this@listMemberAdd)
                            .setCancelable(true)
                            .setMessage(R.string.cant_add_list_follow_requesting)
                            .setNeutralButton(R.string.close, null)
                            .show()
                        throw MemberNotFollowedException()
                    }
                }
            }
            // その他のエラー
            else -> error(errorMessage.notBlank() ?: "?")
        }
    }
    // 成功後
    for (column in appState.columnList) {
        // リストメンバー追加イベントをカラムに伝達
        column.onListMemberUpdated(accessInfo, listId, localWho, true)
    }
    // フォロー状態の更新を表示に反映させる
    if (i > 0) showColumnMatchAccount(accessInfo)
    showToast(false, R.string.list_member_added)
}

suspend fun ActMain.listMemberDelete(
    accessInfo: SavedAccount,
    listId: EntityId,
    localWho: TootAccount,
) {
    val result = runApiTask(accessInfo) { client ->
        if (accessInfo.isMisskey) {
            client.request(
                "/api/users/lists/pull",
                accessInfo.putMisskeyApiToken().apply {
                    put("listId", listId.toString())
                    put("userId", localWho.id.toString())
                }.toPostRequestBuilder()
            )
        } else {
            client.request(
                "/api/v1/lists/$listId/accounts?account_ids[]=${localWho.id}",
                Request.Builder().delete()
            )
        }
    }
    when{
        // キャンセル
        result == null -> throw CancellationException()

        // 失敗
        result.jsonObject == null -> error(result.error.notBlank() ?: "?")

        // 成功
        else ->{
            for (column in appState.columnList) {
                column.onListMemberUpdated(accessInfo, listId, localWho, false)
            }
            showToast(false, R.string.delete_succeeded)
        }
    }
}
