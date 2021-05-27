package jp.juggler.subwaytooter.action

import androidx.appcompat.app.AlertDialog
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.DlgConfirm
import jp.juggler.subwaytooter.dialog.pickAccount
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.util.*

fun ActMain.follow(
    pos: Int,
    access_info: SavedAccount,
    whoRef: TootAccountRef,
    bFollow: Boolean = true,
    bConfirmMoved: Boolean = false,
    bConfirmed: Boolean = false,
    callback: () -> Unit = {}
) {
    val activity = this@follow
    val who = whoRef.get()

    if (access_info.isMe(who)) {
        showToast(false, R.string.it_is_you)
        return
    }

    if (!bConfirmMoved && bFollow && who.moved != null) {
        AlertDialog.Builder(activity)
            .setMessage(
                getString(
                    R.string.jump_moved_user,
                    access_info.getFullAcct(who),
                    access_info.getFullAcct(who.moved)
                )
            )
            .setPositiveButton(R.string.ok) { _, _ ->
                userProfileFromAnotherAccount(
                    pos,
                    access_info,
                    who.moved
                )
            }
            .setNeutralButton(R.string.ignore_suggestion) { _, _ ->
                follow(
                    pos,
                    access_info,
                    whoRef,
                    bFollow = bFollow,
                    bConfirmMoved = true, // CHANGED
                    bConfirmed = bConfirmed,
                    callback = callback
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        return
    }

    if (!bConfirmed) {
        if (bFollow && who.locked) {
            DlgConfirm.open(
                activity,
                activity.getString(
                    R.string.confirm_follow_request_who_from,
                    whoRef.decoded_display_name,
                    AcctColor.getNickname(access_info)
                ),
                object : DlgConfirm.Callback {

                    override fun onOK() {
                        follow(
                            pos,
                            access_info,
                            whoRef,
                            bFollow = bFollow,
                            bConfirmMoved = bConfirmMoved,
                            bConfirmed = true, // CHANGED
                            callback = callback
                        )
                    }

                    override var isConfirmEnabled: Boolean
                        get() = access_info.confirm_follow_locked
                        set(value) {
                            access_info.confirm_follow_locked = value
                            access_info.saveSetting()
                            activity.reloadAccountSetting(access_info)
                        }
                })
            return
        } else if (bFollow) {
            DlgConfirm.open(
                activity,
                getString(
                    R.string.confirm_follow_who_from,
                    whoRef.decoded_display_name,
                    AcctColor.getNickname(access_info)
                ),
                object : DlgConfirm.Callback {

                    override fun onOK() {
                        follow(
                            pos,
                            access_info,
                            whoRef,
                            bFollow = bFollow,
                            bConfirmMoved = bConfirmMoved,
                            bConfirmed = true, //CHANGED
                            callback = callback
                        )
                    }

                    override var isConfirmEnabled: Boolean
                        get() = access_info.confirm_follow
                        set(value) {
                            access_info.confirm_follow = value
                            access_info.saveSetting()
                            activity.reloadAccountSetting(access_info)
                        }
                })
            return
        } else {
            DlgConfirm.open(
                activity,
                getString(
                    R.string.confirm_unfollow_who_from,
                    whoRef.decoded_display_name,
                    AcctColor.getNickname(access_info)
                ),
                object : DlgConfirm.Callback {

                    override fun onOK() {
                        follow(
                            pos,
                            access_info,
                            whoRef,
                            bFollow = bFollow,
                            bConfirmMoved = bConfirmMoved,
                            bConfirmed = true, // CHANGED
                            callback = callback
                        )
                    }

                    override var isConfirmEnabled: Boolean
                        get() = access_info.confirm_unfollow
                        set(value) {
                            access_info.confirm_unfollow = value
                            access_info.saveSetting()
                            activity.reloadAccountSetting(access_info)
                        }
                })
            return
        }
    }

    launchMain {
        var resultRelation: UserRelation? = null
        runApiTask(access_info, progressStyle = ApiTask.PROGRESS_NONE) { client ->
            val parser = TootParser(activity, access_info)

            var userId = who.id
            if (who.isRemote) {

                // リモートユーザの確認

                val skipAccountSync = if (access_info.isMisskey) {
                    // Misskey の /users/show はリモートユーザに関して404を返すので
                    // userIdからリモートﾕｰｻﾞを照合することはできない。
                    // ただし検索APIがエラーになるかどうかは未確認
                    false
                } else {
                    // https://github.com/tateisu/SubwayTooter/issues/124
                    // によると、閉じたタンスのユーザを同期しようとすると検索APIがエラーを返す
                    // この問題を回避するため、手持ちのuserIdで照合したユーザのacctが目的のユーザと同じなら
                    // 検索APIを呼び出さないようにする
                    val result = client.request("/api/v1/accounts/${userId}")
                        ?: return@runApiTask null
                    who.acct == parser.account(result.jsonObject)?.acct
                }

                if (!skipAccountSync) {
                    // 同タンスのIDではなかった場合、検索APIを使う
                    val (result, ar) = client.syncAccountByAcct(access_info, who.acct)
                    val user = ar?.get() ?: return@runApiTask result
                    userId = user.id
                }
            }

            if (access_info.isMisskey) {

                client.request(
                    when {
                        bFollow -> "/api/following/create"
                        else -> "/api/following/delete"
                    },
                    access_info.putMisskeyApiToken().apply {
                        put("userId", userId)
                    }
                        .toPostRequestBuilder()
                )?.also { result ->

                    fun saveFollow(f: Boolean) {
                        val ur = UserRelation.load(access_info.db_id, userId)
                        ur.following = f
                        UserRelation.save1Misskey(
                            System.currentTimeMillis(),
                            access_info.db_id,
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
                    "/api/v1/accounts/${userId}/${if (bFollow) "follow" else "unfollow"}",
                    "".toFormRequestBody().toPost()
                )?.also { result ->
                    val newRelation = parseItem(::TootRelationShip, parser, result.jsonObject)
                    resultRelation = access_info.saveUserRelation(newRelation)
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
                    showColumnMatchAccount(access_info)
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
    access_info: SavedAccount,
    acct: Acct,
    locked: Boolean,
    bConfirmed: Boolean = false,
    callback: () -> Unit = {}
) {
    val activity = this@followRemote

    if (access_info.isMe(acct)) {
        showToast(false, R.string.it_is_you)
        return
    }

    if (!bConfirmed) {
        if (locked) {
            DlgConfirm.open(
                activity,
                getString(
                    R.string.confirm_follow_request_who_from,
                    AcctColor.getNickname(acct),
                    AcctColor.getNickname(access_info)
                ),
                object : DlgConfirm.Callback {
                    override fun onOK() {
                        followRemote(

                            access_info,
                            acct,
                            locked,
                            bConfirmed = true, //CHANGE
                            callback = callback
                        )
                    }

                    override var isConfirmEnabled: Boolean
                        get() = access_info.confirm_follow_locked
                        set(value) {
                            access_info.confirm_follow_locked = value
                            access_info.saveSetting()
                            reloadAccountSetting(access_info)
                        }
                })
            return
        } else {
            DlgConfirm.open(
                activity,
                getString(
                    R.string.confirm_follow_who_from,
                    AcctColor.getNickname(acct),
                    AcctColor.getNickname(access_info)
                ),
                object : DlgConfirm.Callback {

                    override fun onOK() {
                        followRemote(

                            access_info,
                            acct,
                            locked,
                            bConfirmed = true, //CHANGE
                            callback = callback
                        )
                    }

                    override var isConfirmEnabled: Boolean
                        get() = access_info.confirm_follow
                        set(value) {
                            access_info.confirm_follow = value
                            access_info.saveSetting()
                            reloadAccountSetting(access_info)
                        }
                })
            return
        }
    }

    launchMain {
        var resultRelation: UserRelation? = null
        runApiTask(access_info, progressStyle = ApiTask.PROGRESS_NONE) { client ->
            val parser = TootParser(this, access_info)

            val (r2, ar) = client.syncAccountByAcct(access_info, acct)
            val user = ar?.get() ?: return@runApiTask r2
            val userId = user.id

            if (access_info.isMisskey) {
                client.request(
                    "/api/following/create",
                    access_info.putMisskeyApiToken().apply {
                        put("userId", userId)
                    }.toPostRequestBuilder()
                ).also { result ->
                    if (result?.error?.contains("already following") == true
                        || result?.error?.contains("already not following") == true
                    ) {
                        // DBから読み直して値を変更する
                        resultRelation = UserRelation.load(access_info.db_id, userId)
                            .apply { following = true }
                    } else {
                        // parserに残ってるRelationをDBに保存する
                        parser.account(result?.jsonObject)?.let {
                            resultRelation = access_info.saveUserRelationMisskey(it.id, parser)
                        }
                    }
                }
            } else {
                client.request(
                    "/api/v1/accounts/${userId}/follow",
                    "".toFormRequestBody().toPost()
                )?.also { result ->
                    parseItem(::TootRelationShip, parser, result.jsonObject)?.let {
                        resultRelation = access_info.saveUserRelation(it)
                    }
                }
            }
        }?.let { result ->
            when {
                resultRelation != null -> {
                    callback()
                    showColumnMatchAccount(access_info)
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
    access_info: SavedAccount,
    account: TootAccount?,
    bConfirmMoved: Boolean = false
) {
    account ?: return

    if (!bConfirmMoved && account.moved != null) {
        AlertDialog.Builder(this)
            .setMessage(
                getString(
                    R.string.jump_moved_user,
                    access_info.getFullAcct(account),
                    access_info.getFullAcct(account.moved)
                )
            )
            .setPositiveButton(R.string.ok) { _, _ ->
                userProfileFromAnotherAccount(pos, access_info, account.moved)
            }
            .setNeutralButton(R.string.ignore_suggestion) { _, _ ->
                followFromAnotherAccount(
                    pos,
                    access_info,
                    account,
                    bConfirmMoved = true //CHANGED
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        return
    }

    val who_acct = access_info.getFullAcct(account)
    launchMain {
        pickAccount(
            bAuto = false,
            message = getString(R.string.account_picker_follow),
            accountListArg = accountListNonPseudo(account.apiHost)
        )?.let {
            followRemote(
                it,
                who_acct,
                account.locked,
                callback = follow_complete_callback
            )
        }
    }
}

fun ActMain.followRequestAuthorize(
    access_info: SavedAccount,
    whoRef: TootAccountRef,
    bAllow: Boolean
) {
    val who = whoRef.get()
    if (access_info.isMe(who)) {
        showToast(false, R.string.it_is_you)
        return
    }

    launchMain {
        runApiTask(access_info) { client ->
            val parser = TootParser(this, access_info)

            if (access_info.isMisskey) {
                client.request(
                    "/api/following/requests/${if (bAllow) "accept" else "reject"}",
                    access_info.putMisskeyApiToken().apply {
                        put("userId", who.id)
                    }
                        .toPostRequestBuilder()
                ).also { result ->
                    val user = parser.account(result?.jsonObject)
                    if (user != null) {
                        // parserに残ってるRelationをDBに保存する
                        access_info.saveUserRelationMisskey(user.id, parser)
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
                    access_info.saveUserRelation(newRelation)
                    // 読めなくてもエラー処理は行わない
                }
            }
        }?.let { result ->
            when (result.jsonObject) {
                null -> showToast(false, result.error)

                else -> {
                    for (column in app_state.columnList) {
                        column.removeUser(access_info, ColumnType.FOLLOW_REQUESTS, who.id)

                        // 他のカラムでもフォロー状態の表示更新が必要
                        if (column.access_info == access_info
                            && column.type != ColumnType.FOLLOW_REQUESTS
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
    access_info: SavedAccount,
    whoRef: TootAccountRef,
    bConfirmed: Boolean = false,
    callback: () -> Unit = {}
) {
    if (!access_info.isMisskey) {
        follow(
            pos,
            access_info,
            whoRef,
            bFollow = false,
            bConfirmed = bConfirmed,
            callback = callback
        )
        return
    }

    val who = whoRef.get()

    if (access_info.isMe(who)) {
        showToast(false, R.string.it_is_you)
        return
    }

    if (!bConfirmed) {
        DlgConfirm.openSimple(
            this,
            getString(
                R.string.confirm_cancel_follow_request_who_from,
                whoRef.decoded_display_name,
                AcctColor.getNickname(access_info)
            )
        ) {
            followRequestDelete(
                pos,
                access_info,
                whoRef,
                bConfirmed = true, // CHANGED
                callback = callback
            )
        }
        return
    }

    launchMain {
        var resultRelation: UserRelation? = null
        runApiTask(access_info, progressStyle = ApiTask.PROGRESS_NONE) { client ->
            if (!access_info.isMisskey) {
                TootApiResult("Mastodon has no API to cancel follow request")
            } else {

                val parser = TootParser(this, access_info)

                var userId: EntityId = who.id

                // リモートユーザの同期
                if (who.isRemote) {
                    val (result, ar) = client.syncAccountByAcct(access_info, who.acct)
                    val user = ar?.get() ?: return@runApiTask result
                    userId = user.id
                }

                client.request(
                    "/api/following/requests/cancel", access_info.putMisskeyApiToken().apply {
                        put("userId", userId)
                    }
                        .toPostRequestBuilder()
                )?.also { result ->
                    parser.account(result.jsonObject)?.let {
                        // parserに残ってるRelationをDBに保存する
                        resultRelation = access_info.saveUserRelationMisskey(it.id, parser)
                    }
                }
            }
        }?.let { result ->

            when (resultRelation) {
                null -> showToast(false, result.error)
                else -> {
                    // ローカル操作成功、もしくはリモートフォロー成功
                    callback()
                    showColumnMatchAccount(access_info)
                }
            }
        }
    }
}
