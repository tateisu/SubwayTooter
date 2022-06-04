package jp.juggler.subwaytooter.action

import androidx.appcompat.app.AlertDialog
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.actmain.reloadAccountSetting
import jp.juggler.subwaytooter.actmain.showColumnMatchAccount
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.column.fireRebindAdapterItems
import jp.juggler.subwaytooter.column.removeUser
import jp.juggler.subwaytooter.dialog.DlgConfirm.confirm
import jp.juggler.subwaytooter.dialog.pickAccount
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.util.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun ActMain.clickFollowInfo(
    pos: Int,
    accessInfo: SavedAccount,
    whoRef: TootAccountRef?,
    forceMenu: Boolean = false,
    contextMenuOpener: ActMain.(whoRef: TootAccountRef) -> Unit,
) {
    whoRef ?: return
    if (forceMenu || accessInfo.isPseudo) {
        contextMenuOpener(this, whoRef)
    } else {
        userProfileLocal(pos, accessInfo, whoRef.get())
    }
}

fun ActMain.clickFollow(
    pos: Int,
    accessInfo: SavedAccount,
    whoRef: TootAccountRef,
    relation: UserRelation?,
) {
    relation ?: return
    val who = whoRef.get()
    when {
        accessInfo.isPseudo ->
            followFromAnotherAccount(pos, accessInfo, who)
        relation.blocking || relation.muting ->
            Unit // 何もしない
        accessInfo.isMisskey && relation.getRequested(who) && !relation.getFollowing(who) ->
            followRequestDelete(pos,
                accessInfo,
                whoRef,
                callback = cancelFollowRequestCompleteCallback)
        relation.getFollowing(who) || relation.getRequested(who) ->
            follow(pos, accessInfo, whoRef, bFollow = false, callback = unfollowCompleteCallback)
        else ->
            follow(pos, accessInfo, whoRef, bFollow = true, callback = followCompleteCallback)
    }
}

fun ActMain.clickFollowRequestAccept(
    accessInfo: SavedAccount,
    whoRef: TootAccountRef?,
    accept: Boolean,
) {
    val who = whoRef?.get() ?: return
    launchAndShowError {
        confirm(
            when {
                accept -> R.string.follow_accept_confirm
                else -> R.string.follow_deny_confirm
            },
            AcctColor.getNickname(accessInfo, who)
        )
        followRequestAuthorize(accessInfo, whoRef, accept)
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////

fun ActMain.follow(
    pos: Int,
    accessInfo: SavedAccount,
    whoRef: TootAccountRef,
    bFollow: Boolean = true,
    bConfirmMoved: Boolean = false,
    bConfirmed: Boolean = false,
    callback: () -> Unit = {},
) {
    val activity = this@follow
    val who = whoRef.get()

    if (accessInfo.isMe(who)) {
        showToast(false, R.string.it_is_you)
        return
    }

    launchAndShowError {
        if (!bConfirmMoved && bFollow && who.moved != null) {
            val selected = suspendCancellableCoroutine<Int> { cont ->
                try {
                    val dialog = AlertDialog.Builder(activity)
                        .setMessage(
                            getString(
                                R.string.jump_moved_user,
                                accessInfo.getFullAcct(who),
                                accessInfo.getFullAcct(who.moved)
                            )
                        )
                        .setPositiveButton(R.string.ok) { _, _ ->
                            cont.resume(R.string.ok)
                        }
                        .setNeutralButton(R.string.ignore_suggestion) { _, _ ->
                            cont.resume(R.string.ignore_suggestion)
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .create()
                    dialog.setOnDismissListener {
                        if (cont.isActive) cont.resumeWithException(CancellationException())
                    }
                    cont.invokeOnCancellation { dialog.dismissSafe() }
                    dialog.show()
                } catch (ex: Throwable) {
                    cont.resumeWithException(ex)
                }
            }
            when (selected) {
                R.string.ok -> {
                    userProfileFromAnotherAccount(
                        pos,
                        accessInfo,
                        who.moved
                    )
                    return@launchAndShowError
                }
                R.string.ignore_suggestion -> Unit // fall thru
            }
        } else if (!bConfirmed) {
            if (bFollow && who.locked) {
                confirm(
                    getString(
                        R.string.confirm_follow_request_who_from,
                        whoRef.decoded_display_name,
                        AcctColor.getNickname(accessInfo)
                    ),
                    accessInfo.confirm_follow_locked,
                ) { newConfirmEnabled ->
                    accessInfo.confirm_follow_locked = newConfirmEnabled
                    accessInfo.saveSetting()
                    activity.reloadAccountSetting(accessInfo)
                }
            } else if (bFollow) {
                confirm(
                    getString(
                        R.string.confirm_follow_who_from,
                        whoRef.decoded_display_name,
                        AcctColor.getNickname(accessInfo)
                    ),
                    accessInfo.confirm_follow
                ) { newConfirmEnabled ->
                    accessInfo.confirm_follow = newConfirmEnabled
                    accessInfo.saveSetting()
                    activity.reloadAccountSetting(accessInfo)
                }
            } else {
                confirm(
                    getString(
                        R.string.confirm_unfollow_who_from,
                        whoRef.decoded_display_name,
                        AcctColor.getNickname(accessInfo)
                    ),
                    accessInfo.confirm_unfollow
                ) { newConfirmEnabled ->
                    accessInfo.confirm_unfollow = newConfirmEnabled
                    accessInfo.saveSetting()
                    activity.reloadAccountSetting(accessInfo)
                }
            }
        }

        var resultRelation: UserRelation? = null
        runApiTask(accessInfo, progressStyle = ApiTask.PROGRESS_NONE) { client ->
            val parser = TootParser(activity, accessInfo)

            var userId = who.id
            if (who.isRemote) {

                // リモートユーザの確認

                val skipAccountSync = if (accessInfo.isMisskey) {
                    // Misskey の /users/show はリモートユーザに関して404を返すので
                    // userIdからリモートﾕｰｻﾞを照合することはできない。
                    // ただし検索APIがエラーになるかどうかは未確認
                    false
                } else {
                    // https://github.com/tateisu/SubwayTooter/issues/124
                    // によると、閉じたタンスのユーザを同期しようとすると検索APIがエラーを返す
                    // この問題を回避するため、手持ちのuserIdで照合したユーザのacctが目的のユーザと同じなら
                    // 検索APIを呼び出さないようにする
                    val result = client.request("/api/v1/accounts/$userId")
                        ?: return@runApiTask null
                    who.acct == parser.account(result.jsonObject)?.acct
                }

                if (!skipAccountSync) {
                    // 同タンスのIDではなかった場合、検索APIを使う
                    val (result, ar) = client.syncAccountByAcct(accessInfo, who.acct)
                    val user = ar?.get() ?: return@runApiTask result
                    userId = user.id
                }
            }

            if (accessInfo.isMisskey) {

                client.request(
                    when {
                        bFollow -> "/api/following/create"
                        else -> "/api/following/delete"
                    },
                    accessInfo.putMisskeyApiToken().apply {
                        put("userId", userId)
                    }
                        .toPostRequestBuilder()
                )?.also { result ->

                    fun saveFollow(f: Boolean) {
                        val ur = UserRelation.load(accessInfo.db_id, userId)
                        ur.following = f
                        UserRelation.save1Misskey(
                            System.currentTimeMillis(),
                            accessInfo.db_id,
                            userId.toString(),
                            ur
                        )
                        resultRelation = ur
                    }

                    val error = result.error
                    when {
                        // success
                        error == null -> saveFollow(bFollow)

                        // already followed/unfollowed
                        error.contains("already following") -> saveFollow(bFollow)
                        error.contains("already not following") -> saveFollow(bFollow)

                        // else something error
                    }
                }
            } else {
                client.request(
                    "/api/v1/accounts/$userId/${if (bFollow) "follow" else "unfollow"}",
                    "".toFormRequestBody().toPost()
                )?.also { result ->
                    val newRelation = parseItem(::TootRelationShip, parser, result.jsonObject)
                    resultRelation = accessInfo.saveUserRelation(newRelation)
                }
            }
        }?.let { result ->
            val relation = resultRelation
            when {
                relation != null -> {

                    when {
                        // 鍵付きアカウントにフォローリクエストを申請した状態
                        bFollow && relation.getRequested(who) ->
                            showToast(false, R.string.follow_requested)
                        !bFollow && relation.getRequested(who) ->
                            showToast(false, R.string.follow_request_cant_remove_by_sender)

                        // ローカル操作成功、もしくはリモートフォロー成功
                        else -> callback()
                    }
                    showColumnMatchAccount(accessInfo)
                }
                bFollow && who.locked && (result.response?.code ?: -1) == 422 ->
                    showToast(false, R.string.cant_follow_locked_user)

                else -> showToast(false, result.error)
            }
        }
    }
}

// acct で指定したユーザをリモートフォローする
private fun ActMain.followRemote(
    accessInfo: SavedAccount,
    acct: Acct,
    locked: Boolean,
    bConfirmed: Boolean = false,
    callback: () -> Unit = {},
) {
    if (accessInfo.isMe(acct)) {
        showToast(false, R.string.it_is_you)
        return
    }

    launchAndShowError {

        if (!bConfirmed) {
            if (locked) {
                confirm(
                    getString(
                        R.string.confirm_follow_request_who_from,
                        AcctColor.getNickname(acct),
                        AcctColor.getNickname(accessInfo)
                    ),
                    accessInfo.confirm_follow_locked,
                ) { newConfirmEnabled ->
                    accessInfo.confirm_follow_locked = newConfirmEnabled
                    accessInfo.saveSetting()
                    reloadAccountSetting(accessInfo)
                }
            } else {
                confirm(
                    getString(
                        R.string.confirm_follow_who_from,
                        AcctColor.getNickname(acct),
                        AcctColor.getNickname(accessInfo)
                    ),
                    accessInfo.confirm_follow
                ) { newConfirmEnabled ->
                    accessInfo.confirm_follow = newConfirmEnabled
                    accessInfo.saveSetting()
                    reloadAccountSetting(accessInfo)
                }
            }
        }

        var resultRelation: UserRelation? = null
        runApiTask(accessInfo, progressStyle = ApiTask.PROGRESS_NONE) { client ->
            val parser = TootParser(this, accessInfo)

            val (r2, ar) = client.syncAccountByAcct(accessInfo, acct)
            val user = ar?.get() ?: return@runApiTask r2
            val userId = user.id

            if (accessInfo.isMisskey) {
                client.request(
                    "/api/following/create",
                    accessInfo.putMisskeyApiToken().apply {
                        put("userId", userId)
                    }.toPostRequestBuilder()
                ).also { result ->
                    if (result?.error?.contains("already following") == true ||
                        result?.error?.contains("already not following") == true
                    ) {
                        // DBから読み直して値を変更する
                        resultRelation = UserRelation.load(accessInfo.db_id, userId)
                            .apply { following = true }
                    } else {
                        // parserに残ってるRelationをDBに保存する
                        parser.account(result?.jsonObject)?.let {
                            resultRelation = accessInfo.saveUserRelationMisskey(it.id, parser)
                        }
                    }
                }
            } else {
                client.request(
                    "/api/v1/accounts/$userId/follow",
                    "".toFormRequestBody().toPost()
                )?.also { result ->
                    parseItem(::TootRelationShip, parser, result.jsonObject)?.let {
                        resultRelation = accessInfo.saveUserRelation(it)
                    }
                }
            }
        }?.let { result ->
            when {
                resultRelation != null -> {
                    callback()
                    showColumnMatchAccount(accessInfo)
                }

                locked && (result.response?.code ?: -1) == 422 ->
                    showToast(false, R.string.cant_follow_locked_user)

                else ->
                    showToast(false, result.error)
            }
        }
    }
}

fun ActMain.followFromAnotherAccount(
    pos: Int,
    accessInfo: SavedAccount,
    account: TootAccount?,
    bConfirmMoved: Boolean = false,
) {
    account ?: return

    if (!bConfirmMoved && account.moved != null) {
        AlertDialog.Builder(this)
            .setMessage(
                getString(
                    R.string.jump_moved_user,
                    accessInfo.getFullAcct(account),
                    accessInfo.getFullAcct(account.moved)
                )
            )
            .setPositiveButton(R.string.ok) { _, _ ->
                userProfileFromAnotherAccount(pos, accessInfo, account.moved)
            }
            .setNeutralButton(R.string.ignore_suggestion) { _, _ ->
                followFromAnotherAccount(
                    pos,
                    accessInfo,
                    account,
                    bConfirmMoved = true //CHANGED
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        return
    }

    val whoAcct = accessInfo.getFullAcct(account)
    launchMain {
        pickAccount(
            bAuto = false,
            message = getString(R.string.account_picker_follow),
            accountListArg = accountListNonPseudo(account.apiHost)
        )?.let {
            followRemote(
                it,
                whoAcct,
                account.locked,
                callback = followCompleteCallback
            )
        }
    }
}

fun ActMain.followRequestAuthorize(
    accessInfo: SavedAccount,
    whoRef: TootAccountRef,
    bAllow: Boolean,
) {
    val who = whoRef.get()
    if (accessInfo.isMe(who)) {
        showToast(false, R.string.it_is_you)
        return
    }

    launchMain {
        runApiTask(accessInfo) { client ->
            val parser = TootParser(this, accessInfo)

            if (accessInfo.isMisskey) {
                client.request(
                    "/api/following/requests/${if (bAllow) "accept" else "reject"}",
                    accessInfo.putMisskeyApiToken().apply {
                        put("userId", who.id)
                    }
                        .toPostRequestBuilder()
                ).also { result ->
                    val user = parser.account(result?.jsonObject)
                    if (user != null) {
                        // parserに残ってるRelationをDBに保存する
                        accessInfo.saveUserRelationMisskey(user.id, parser)
                    }
                    // 読めなくてもエラー処理は行わない
                }
            } else {
                client.request(
                    "/api/v1/follow_requests/${who.id}/${if (bAllow) "authorize" else "reject"}",
                    "".toFormRequestBody().toPost()
                )?.also { result ->
                    // Mastodon 3.0.0 から更新されたリレーションを返す
                    // https//github.com/tootsuite/mastodon/pull/11800
                    val newRelation = parseItem(::TootRelationShip, parser, result.jsonObject)
                    accessInfo.saveUserRelation(newRelation)
                    // 読めなくてもエラー処理は行わない
                }
            }
        }?.let { result ->
            when (result.jsonObject) {
                null -> showToast(false, result.error)

                else -> {
                    for (column in appState.columnList) {
                        column.removeUser(accessInfo, ColumnType.FOLLOW_REQUESTS, who.id)

                        // 他のカラムでもフォロー状態の表示更新が必要
                        if (column.accessInfo == accessInfo &&
                            column.type != ColumnType.FOLLOW_REQUESTS
                        ) {
                            column.fireRebindAdapterItems()
                        }
                    }

                    showToast(
                        false,
                        if (bAllow) R.string.follow_request_authorized else R.string.follow_request_rejected,
                        whoRef.decoded_display_name
                    )
                }
            }
        }
    }
}

fun ActMain.followRequestDelete(
    pos: Int,
    accessInfo: SavedAccount,
    whoRef: TootAccountRef,
    bConfirmed: Boolean = false,
    callback: () -> Unit = {},
) {
    if (!accessInfo.isMisskey) {
        follow(
            pos,
            accessInfo,
            whoRef,
            bFollow = false,
            bConfirmed = bConfirmed,
            callback = callback
        )
        return
    }

    val who = whoRef.get()

    if (accessInfo.isMe(who)) {
        showToast(false, R.string.it_is_you)
        return
    }

    launchAndShowError {
        if (!bConfirmed) {
            confirm(
                R.string.confirm_cancel_follow_request_who_from,
                whoRef.decoded_display_name,
                AcctColor.getNickname(accessInfo)
            )
        }

        var resultRelation: UserRelation? = null
        runApiTask(accessInfo, progressStyle = ApiTask.PROGRESS_NONE) { client ->
            if (!accessInfo.isMisskey) {
                TootApiResult("Mastodon has no API to cancel follow request")
            } else {

                val parser = TootParser(this, accessInfo)

                var userId: EntityId = who.id

                // リモートユーザの同期
                if (who.isRemote) {
                    val (result, ar) = client.syncAccountByAcct(accessInfo, who.acct)
                    val user = ar?.get() ?: return@runApiTask result
                    userId = user.id
                }

                client.request(
                    "/api/following/requests/cancel", accessInfo.putMisskeyApiToken().apply {
                        put("userId", userId)
                    }
                        .toPostRequestBuilder()
                )?.also { result ->
                    parser.account(result.jsonObject)?.let {
                        // parserに残ってるRelationをDBに保存する
                        resultRelation = accessInfo.saveUserRelationMisskey(it.id, parser)
                    }
                }
            }
        }?.let { result ->
            when (resultRelation) {
                null -> showToast(false, result.error)
                else -> {
                    // ローカル操作成功、もしくはリモートフォロー成功
                    callback()
                    showColumnMatchAccount(accessInfo)
                }
            }
        }
    }
}
