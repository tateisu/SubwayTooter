package jp.juggler.subwaytooter.action

import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.DlgConfirm
import jp.juggler.subwaytooter.dialog.pickAccount
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.*
import okhttp3.Request
import java.util.*
import kotlin.math.max

// お気に入りの非同期処理
fun ActMain.favourite(
    access_info: SavedAccount,
    arg_status: TootStatus,
    crossAccountMode: CrossAccountMode,
    callback: () -> Unit,
    bSet: Boolean = true,
    bConfirmed: Boolean = false
) {
    if (app_state.isBusyFav(access_info, arg_status)) {
        showToast(false, R.string.wait_previous_operation)
        return
    }

    // 必要なら確認を出す
    if (!bConfirmed && access_info.isMastodon) {
        DlgConfirm.open(
            this,
            getString(
                when (bSet) {
                    true -> R.string.confirm_favourite_from
                    else -> R.string.confirm_unfavourite_from
                },
                AcctColor.getNickname(access_info)
            ),
            object : DlgConfirm.Callback {

                override fun onOK() {
                    favourite(
                        access_info,
                        arg_status,
                        crossAccountMode,
                        callback,
                        bSet = bSet,
                        bConfirmed = true
                    )
                }

                override var isConfirmEnabled: Boolean
                    get() = when (bSet) {
                        true -> access_info.confirm_favourite
                        else -> access_info.confirm_unfavourite

                    }
                    set(value) {
                        when (bSet) {
                            true -> access_info.confirm_favourite = value
                            else -> access_info.confirm_unfavourite = value
                        }
                        access_info.saveSetting()
                        reloadAccountSetting(access_info)
                    }

            })
        return
    }

    //
    app_state.setBusyFav(access_info, arg_status)

    // ファボ表示を更新中にする
    showColumnMatchAccount(access_info)

    launchMain {
        var resultStatus: TootStatus? = null
        val result = runApiTask(
            access_info,
            progressStyle = ApiTask.PROGRESS_NONE
        ) { client ->
            val target_status = if (crossAccountMode.isRemote) {

                val (result, status) = client.syncStatus(access_info, arg_status)
                status ?: return@runApiTask result
                if (status.favourited) {
                    return@runApiTask TootApiResult(getString(R.string.already_favourited))
                }
                status
            } else {
                arg_status
            }

            if (access_info.isMisskey) {
                client.request(
                    if (bSet) {
                        "/api/notes/favorites/create"
                    } else {
                        "/api/notes/favorites/delete"
                    },
                    access_info.putMisskeyApiToken().apply {
                        put("noteId", target_status.id.toString())
                    }
                        .toPostRequestBuilder()
                )?.also { result ->
                    // 正常レスポンスは 204 no content
                    // 既にお気に入り済みならエラー文字列に "already favorited" が返る
                    if (result.response?.code == 204
                        || result.error?.contains("already favorited") == true
                        || result.error?.contains("already not favorited") == true
                    ) {
                        // 成功した
                        resultStatus = target_status.apply {
                            favourited = bSet
                        }
                    }
                }
            } else {
                client.request(
                    "/api/v1/statuses/${target_status.id}/${if (bSet) "favourite" else "unfavourite"}",
                    "".toFormRequestBody().toPost()
                )?.also { result ->
                    resultStatus = TootParser(this, access_info).status(result.jsonObject)
                }
            }
        }

        app_state.resetBusyFav(access_info, arg_status)

        if (result != null) {
            when (val new_status = resultStatus) {
                null -> showToast(true, result.error)
                else -> {

                    val old_count = arg_status.favourites_count
                    val new_count = new_status.favourites_count
                    if (old_count != null && new_count != null) {
                        if (access_info.isMisskey) {
                            new_status.favourited = bSet
                        }
                        if (bSet && new_status.favourited && new_count <= old_count) {
                            // 星をつけたのにカウントが上がらないのは違和感あるので、表示をいじる
                            new_status.favourites_count = old_count + 1L
                        } else if (!bSet && !new_status.favourited && new_count >= old_count) {
                            // 星を外したのにカウントが下がらないのは違和感あるので、表示をいじる
                            // 0未満にはならない
                            new_status.favourites_count =
                                if (old_count < 1L) 0L else old_count - 1L
                        }
                    }

                    for (column in app_state.columnList) {
                        column.findStatus(
                            access_info.apDomain,
                            new_status.id
                        ) { account, status ->

                            // 同タンス別アカウントでもカウントは変化する
                            status.favourites_count = new_status.favourites_count

                            // 同アカウントならfav状態を変化させる
                            if (access_info == account) {
                                status.favourited = new_status.favourited
                            }

                            true
                        }
                    }
                    callback()
                }
            }
        }

        // 結果に関わらず、更新中状態から復帰させる
        showColumnMatchAccount(access_info)
    }
}

// アカウントを選んでお気に入り
fun ActMain.favouriteFromAnotherAccount(
    timeline_account: SavedAccount,
    status: TootStatus?
) {
    status ?: return
    launchMain {
        pickAccount(
            bAllowPseudo = false,
            bAuto = false,
            message = getString(R.string.account_picker_favourite),
            accountListArg = accountListNonPseudo(timeline_account.apDomain)
        )?.let { action_account ->
            favourite(
                action_account,
                status,
                calcCrossAccountMode(timeline_account, action_account),
                callback = favourite_complete_callback
            )
        }
    }
}

////////////////////////////////////////////////////////////////

// お気に入りの非同期処理
fun ActMain.bookmark(
    access_info: SavedAccount,
    arg_status: TootStatus,
    crossAccountMode: CrossAccountMode,
    callback: () -> Unit,
    bSet: Boolean = true,
    bConfirmed: Boolean = false
) {
    if (app_state.isBusyFav(access_info, arg_status)) {
        showToast(false, R.string.wait_previous_operation)
        return
    }
    if (access_info.isMisskey) {
        showToast(false, R.string.misskey_account_not_supported)
        return
    }

    // 必要なら確認を出す
    // ブックマークは解除する時だけ確認する
    if (!bConfirmed && !bSet) {
        DlgConfirm.openSimple(
            this,
            getString(
                R.string.confirm_unbookmark_from,
                AcctColor.getNickname(access_info)
            )
        ) {
            bookmark(

                access_info,
                arg_status,
                crossAccountMode,
                callback,
                bSet = bSet,
                bConfirmed = true
            )
        }
        return
    }

    //
    app_state.setBusyBookmark(access_info, arg_status)

    // ファボ表示を更新中にする
    showColumnMatchAccount(access_info)

    //
    launchMain {
        var resultStatus: TootStatus? = null
        val result = runApiTask(access_info, progressStyle = ApiTask.PROGRESS_NONE) { client ->
            val target_status = if (crossAccountMode.isRemote) {
                val (result, status) = client.syncStatus(access_info, arg_status)
                status ?: return@runApiTask result
                if (status.bookmarked) {
                    return@runApiTask TootApiResult(getString(R.string.already_bookmarked))
                }
                status
            } else {
                arg_status
            }

            client.request(
                "/api/v1/statuses/${target_status.id}/${if (bSet) "bookmark" else "unbookmark"}",
                "".toFormRequestBody().toPost()
            )?.also { result ->
                resultStatus = TootParser(this, access_info).status(result.jsonObject)
            }
        }

        app_state.resetBusyBookmark(access_info, arg_status)

        if (result != null) {
            when (val new_status = resultStatus) {
                null -> showToast(true, result.error)
                else -> {
                    for (column in app_state.columnList) {
                        column.findStatus(
                            access_info.apDomain,
                            new_status.id
                        ) { account, status ->

                            // 同アカウントならブックマーク状態を伝播する
                            if (access_info == account) {
                                status.bookmarked = new_status.bookmarked
                            }

                            true
                        }
                    }
                    callback()
                }
            }
        }
        // 結果に関わらず、更新中状態から復帰させる
        showColumnMatchAccount(access_info)
    }
}

// アカウントを選んでブックマーク
fun ActMain.bookmarkFromAnotherAccount(
    timeline_account: SavedAccount,
    status: TootStatus?
) {
    status ?: return

    launchMain {
        pickAccount(
            bAllowPseudo = false,
            bAuto = false,
            message = getString(R.string.account_picker_bookmark),
            accountListArg = accountListNonPseudo(timeline_account.apDomain)
        )?.let { action_account ->
            bookmark(
                action_account,
                status,
                calcCrossAccountMode(timeline_account, action_account),
                callback = bookmark_complete_callback
            )
        }
    }
}

///////////////////////////////////////////////////////////////

fun ActMain.boost(
    access_info: SavedAccount,
    arg_status: TootStatus,
    status_owner: Acct,
    crossAccountMode: CrossAccountMode,
    bSet: Boolean = true,
    bConfirmed: Boolean = false,
    visibility: TootVisibility? = null,
    callback: () -> Unit
) {

    // アカウントからステータスにブースト操作を行っているなら、何もしない
    if (app_state.isBusyBoost(access_info, arg_status)) {
        showToast(false, R.string.wait_previous_operation)
        return
    }

    // Mastodonは非公開トゥートをブーストできるのは本人だけ
    val isPrivateToot = access_info.isMastodon &&
        arg_status.visibility == TootVisibility.PrivateFollowers

    if (isPrivateToot && access_info.acct != status_owner) {
        showToast(false, R.string.boost_private_toot_not_allowed)
        return
    }
    // DMとかのブーストはAPI側がエラーを出すだろう？

    // 必要なら確認を出す
    if (!bConfirmed) {
        DlgConfirm.open(
            this,
            getString(
                when {
                    !bSet -> R.string.confirm_unboost_from
                    isPrivateToot -> R.string.confirm_boost_private_from
                    visibility == TootVisibility.PrivateFollowers -> R.string.confirm_private_boost_from
                    else -> R.string.confirm_boost_from
                },
                AcctColor.getNickname(access_info)
            ),
            object : DlgConfirm.Callback {
                override fun onOK() {
                    boost(

                        access_info,
                        arg_status,
                        status_owner,
                        crossAccountMode,
                        bSet = bSet,
                        bConfirmed = true,
                        visibility = visibility,
                        callback = callback,
                    )
                }

                override var isConfirmEnabled: Boolean
                    get() = when (bSet) {
                        true -> access_info.confirm_boost
                        else -> access_info.confirm_unboost
                    }
                    set(value) {
                        when (bSet) {
                            true -> access_info.confirm_boost = value
                            else -> access_info.confirm_unboost = value
                        }
                        access_info.saveSetting()
                        reloadAccountSetting(access_info)
                    }
            })
        return
    }

    app_state.setBusyBoost(access_info, arg_status)
    // ブースト表示を更新中にする
    showColumnMatchAccount(access_info)
    // misskeyは非公開トゥートをブーストできないっぽい

    launchMain {
        var resultStatus: TootStatus? = null
        var resultUnrenoteId: EntityId? = null
        val result = runApiTask(access_info, progressStyle = ApiTask.PROGRESS_NONE) { client ->

            val parser = TootParser(this, access_info)

            val target_status = if (crossAccountMode.isRemote) {
                val (result, status) = client.syncStatus(access_info, arg_status)
                if (status == null) return@runApiTask result
                if (status.reblogged) {
                    return@runApiTask TootApiResult(getString(R.string.already_boosted))
                }
                status
            } else {
                // 既に自タンスのステータスがある
                arg_status
            }

            if (access_info.isMisskey) {
                if (!bSet) {
                    val myRenoteId = target_status.myRenoteId
                        ?: return@runApiTask TootApiResult("missing renote id.")

                    client.request(
                        "/api/notes/delete",
                        access_info.putMisskeyApiToken().apply {
                            put("noteId", myRenoteId.toString())
                            put("renoteId", target_status.id.toString())
                        }
                            .toPostRequestBuilder()
                    )
                        ?.also {
                            if (it.response?.code == 204)
                                resultUnrenoteId = myRenoteId
                        }
                } else {
                    client.request(
                        "/api/notes/create",
                        access_info.putMisskeyApiToken().apply {
                            put("renoteId", target_status.id.toString())
                        }
                            .toPostRequestBuilder()
                    )
                        ?.also { result ->
                            val jsonObject = result.jsonObject
                            if (jsonObject != null) {
                                val outerStatus = parser.status(
                                    jsonObject.jsonObject("createdNote")
                                        ?: jsonObject
                                )
                                val innerStatus = outerStatus?.reblog ?: outerStatus
                                if (outerStatus != null && innerStatus != null && outerStatus != innerStatus) {
                                    innerStatus.myRenoteId = outerStatus.id
                                    innerStatus.reblogged = true
                                }
                                // renoteそのものではなくrenoteされた元noteが欲しい
                                resultStatus = innerStatus
                            }
                        }
                }

            } else {
                val b = JsonObject().apply {
                    if (visibility != null) put("visibility", visibility.strMastodon)
                }.toPostRequestBuilder()

                client.request(
                    "/api/v1/statuses/${target_status.id}/${if (bSet) "reblog" else "unreblog"}",
                    b
                )?.also { result ->
                    // reblogはreblogを表すStatusを返す
                    // unreblogはreblogしたStatusを返す
                    val s = parser.status(result.jsonObject)
                    resultStatus = s?.reblog ?: s
                }
            }
        }

        app_state.resetBusyBoost(access_info, arg_status)

        if (result != null) {
            val unrenoteId = resultUnrenoteId
            val new_status = resultStatus
            when {
                // Misskeyでunrenoteに成功した
                unrenoteId != null -> {

                    // 星を外したのにカウントが下がらないのは違和感あるので、表示をいじる
                    // 0未満にはならない
                    val count = max(0, (arg_status.reblogs_count ?: 1) - 1)

                    for (column in app_state.columnList) {
                        column.findStatus(
                            access_info.apDomain,
                            arg_status.id
                        ) { account, status ->

                            // 同タンス別アカウントでもカウントは変化する
                            status.reblogs_count = count

                            // 同アカウントならreblogged状態を変化させる
                            if (access_info == account && status.myRenoteId == unrenoteId) {
                                status.myRenoteId = null
                                status.reblogged = false
                            }
                            true
                        }
                    }
                    callback()
                }

                // 処理に成功した
                new_status != null -> {
                    // カウント数は遅延があるみたいなので、恣意的に表示を変更する
                    // ブーストカウント数を加工する
                    val old_count = arg_status.reblogs_count
                    val new_count = new_status.reblogs_count
                    if (old_count != null && new_count != null) {
                        if (bSet && new_status.reblogged && new_count <= old_count) {
                            // 星をつけたのにカウントが上がらないのは違和感あるので、表示をいじる
                            new_status.reblogs_count = old_count + 1
                        } else if (!bSet && !new_status.reblogged && new_count >= old_count) {
                            // 星を外したのにカウントが下がらないのは違和感あるので、表示をいじる
                            // 0未満にはならない
                            new_status.reblogs_count = if (old_count < 1) 0 else old_count - 1
                        }

                    }

                    for (column in app_state.columnList) {
                        column.findStatus(
                            access_info.apDomain,
                            new_status.id
                        ) { account, status ->

                            // 同タンス別アカウントでもカウントは変化する
                            status.reblogs_count = new_status.reblogs_count

                            if (access_info == account) {

                                // 同アカウントならreblog状態を変化させる
                                when {
                                    access_info.isMastodon ->
                                        status.reblogged = new_status.reblogged

                                    bSet && status.myRenoteId == null -> {
                                        status.myRenoteId = new_status.myRenoteId
                                        status.reblogged = true
                                    }
                                }
                                // Misskey のunrenote時はここを通らない
                            }
                            true
                        }
                    }
                    callback()
                }

                else -> showToast(true, result.error)
            }
        }

        // 結果に関わらず、更新中状態から復帰させる
        showColumnMatchAccount(access_info)
    }
}

fun ActMain.boostFromAnotherAccount(
    timeline_account: SavedAccount,
    status: TootStatus?
) {
    status ?: return
    launchMain {
        val status_owner = timeline_account.getFullAcct(status.account)

        val isPrivateToot = timeline_account.isMastodon &&
            status.visibility == TootVisibility.PrivateFollowers

        if (isPrivateToot) {
            val list = ArrayList<SavedAccount>()
            for (a in SavedAccount.loadAccountList(applicationContext)) {
                if (a.acct == status_owner) list.add(a)
            }
            if (list.isEmpty()) {
                showToast(false, R.string.boost_private_toot_not_allowed)
                return@launchMain
            }

            pickAccount(
                bAllowPseudo = false,
                bAuto = false,
                message = getString(R.string.account_picker_boost),
                accountListArg = list
            )?.let { action_account ->
                boost(
                    action_account,
                    status,
                    status_owner,
                    calcCrossAccountMode(timeline_account, action_account),
                    callback = boost_complete_callback
                )
            }
        } else {
            pickAccount(
                bAllowPseudo = false,
                bAuto = false,
                message = getString(R.string.account_picker_boost),
                accountListArg = accountListNonPseudo(timeline_account.apDomain)
            )?.let { action_account ->
                boost(
                    action_account,
                    status,
                    status_owner,
                    calcCrossAccountMode(timeline_account, action_account),
                    callback = boost_complete_callback
                )
            }
        }
    }
}


fun ActMain.statusDelete(
    access_info: SavedAccount,
    status_id: EntityId
) {
    launchMain {
        runApiTask(access_info) { client ->
            if (access_info.isMisskey) {
                client.request(
                    "/api/notes/delete",
                    access_info.putMisskeyApiToken().apply {
                        put("noteId", status_id)
                    }
                        .toPostRequestBuilder()
                )
                // 204 no content
            } else {
                client.request(
                    "/api/v1/statuses/$status_id",
                    Request.Builder().delete()
                )
            }
        }?.let { result ->

            if (result.jsonObject != null) {
                showToast(false, R.string.delete_succeeded)
                for (column in app_state.columnList) {
                    column.onStatusRemoved(access_info.apDomain, status_id)
                }
            } else {
                showToast(false, result.error)
            }
        }
    }
}

////////////////////////////////////////
// profile pin

fun ActMain.statusPin(
    access_info: SavedAccount,
    status: TootStatus,
    bSet: Boolean
) {
    launchMain {
        var resultStatus: TootStatus? = null
        runApiTask(
            access_info,
            progressPrefix = getString(R.string.profile_pin_progress)
        ) { client ->
            client.request(
                "/api/v1/statuses/${status.id}/${if (bSet) "pin" else "unpin"}",
                "".toFormRequestBody().toPost()
            )?.also { result ->
                resultStatus = TootParser(this, access_info).status(result.jsonObject)
            }
        }?.let { result ->
            when (val new_status = resultStatus) {
                null -> showToast(true, result.error)
                else -> {
                    for (column in app_state.columnList) {
                        if (access_info == column.access_info) {
                            column.findStatus(
                                access_info.apDomain,
                                new_status.id
                            ) { _, status ->
                                status.pinned = bSet
                                true
                            }
                        }
                    }
                    showColumnMatchAccount(access_info)
                }
            }
        }
    }
}

// 投稿画面を開く。初期テキストを指定する
fun ActMain.statusRedraft(
    accessInfo: SavedAccount,
    status: TootStatus
){
    post_helper.closeAcctPopup()

    when {
        accessInfo.isMisskey ->
            openActPostImpl(
                accessInfo.db_id,
                redraft_status = status,
                reply_status = status.reply
            )

        status.in_reply_to_id == null ->
            openActPostImpl(
                accessInfo.db_id,
                redraft_status = status
            )

        else -> launchMain {
            var resultStatus: TootStatus? = null
            runApiTask(accessInfo) { client ->
                client.request("/api/v1/statuses/${status.in_reply_to_id}")
                    ?.also { resultStatus = TootParser(this, accessInfo).status(it.jsonObject) }
            }?.let { result ->
                when (val reply_status = resultStatus) {
                    null -> showToast(
                        true,
                        "${getString(R.string.cant_sync_toot)} : ${result.error ?: "(no information)"}"
                    )
                    else -> openActPostImpl(
                        accessInfo.db_id,
                        redraft_status = status,
                        reply_status = reply_status
                    )
                }
            }
        }
    }
}

fun ActMain.scheduledPostDelete(
    access_info: SavedAccount,
    item: TootScheduled,
    bConfirmed: Boolean = false,
    callback: () -> Unit
) {
    val act = this@scheduledPostDelete
    if (!bConfirmed) {
        DlgConfirm.openSimple(
            act,
            getString(R.string.scheduled_status_delete_confirm)
        ) {
            scheduledPostDelete(
                access_info,
                item,
                bConfirmed = true,
                callback = callback
            )
        }
        return
    }
    launchMain {
        runApiTask(access_info) { client ->
            client.request(
                "/api/v1/scheduled_statuses/${item.id}",
                Request.Builder().delete()
            )
        }?.let { result ->
            when (val error = result.error) {
                null -> callback()
                else -> showToast(true, error)
            }
        }
    }
}

fun ActMain.scheduledPostEdit(
    access_info: SavedAccount,
    item: TootScheduled
) {
    launchMain {
        var resultStatus: TootStatus? = null
        runApiTask(access_info) { client ->
            val reply_status_id = item.in_reply_to_id ?: return@runApiTask TootApiResult()
            client.request("/api/v1/statuses/$reply_status_id")?.also { result ->
                resultStatus = TootParser(this, access_info).status(result.jsonObject)
            }
        }?.let { result ->
            when (val error = result.error) {
                null -> openActPostImpl(
                    access_info.db_id,
                    scheduledStatus = item,
                    reply_status = resultStatus
                )

                else -> showToast(true, error)
            }
        }
    }
}
