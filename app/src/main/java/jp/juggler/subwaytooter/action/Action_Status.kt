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
    accessInfo: SavedAccount,
    statusArg: TootStatus,
    crossAccountMode: CrossAccountMode,
    callback: () -> Unit,
    bSet: Boolean = true,
    bConfirmed: Boolean = false,
) {
    if (appState.isBusyFav(accessInfo, statusArg)) {
        showToast(false, R.string.wait_previous_operation)
        return
    }

    // 必要なら確認を出す
    if (!bConfirmed && accessInfo.isMastodon) {
        DlgConfirm.open(
            this,
            getString(
                when (bSet) {
                    true -> R.string.confirm_favourite_from
                    else -> R.string.confirm_unfavourite_from
                },
                AcctColor.getNickname(accessInfo)
            ),
            object : DlgConfirm.Callback {

                override fun onOK() {
                    favourite(
                        accessInfo,
                        statusArg,
                        crossAccountMode,
                        callback,
                        bSet = bSet,
                        bConfirmed = true
                    )
                }

                override var isConfirmEnabled: Boolean
                    get() = when (bSet) {
                        true -> accessInfo.confirm_favourite
                        else -> accessInfo.confirm_unfavourite
                    }
                    set(value) {
                        when (bSet) {
                            true -> accessInfo.confirm_favourite = value
                            else -> accessInfo.confirm_unfavourite = value
                        }
                        accessInfo.saveSetting()
                        reloadAccountSetting(accessInfo)
                    }
            })
        return
    }

    //
    appState.setBusyFav(accessInfo, statusArg)

    // ファボ表示を更新中にする
    showColumnMatchAccount(accessInfo)

    launchMain {
        var resultStatus: TootStatus? = null
        val result = runApiTask(
            accessInfo,
            progressStyle = ApiTask.PROGRESS_NONE
        ) { client ->
            val targetStatus = if (crossAccountMode.isRemote) {
                val (result, status) = client.syncStatus(accessInfo, statusArg)
                status ?: return@runApiTask result
                if (status.favourited) {
                    return@runApiTask TootApiResult(getString(R.string.already_favourited))
                }
                status
            } else {
                statusArg
            }

            if (accessInfo.isMisskey) {
                client.request(
                    if (bSet) {
                        "/api/notes/favorites/create"
                    } else {
                        "/api/notes/favorites/delete"
                    },
                    accessInfo.putMisskeyApiToken().apply {
                        put("noteId", targetStatus.id.toString())
                    }
                        .toPostRequestBuilder()
                )?.also { result ->
                    // 正常レスポンスは 204 no content
                    // 既にお気に入り済みならエラー文字列に "already favorited" が返る
                    if (result.response?.code == 204 ||
                        result.error?.contains("already favorited") == true ||
                        result.error?.contains("already not favorited") == true
                    ) {
                        // 成功した
                        resultStatus = targetStatus.apply { favourited = bSet }
                    }
                }
            } else {
                client.request(
                    "/api/v1/statuses/${targetStatus.id}/${if (bSet) "favourite" else "unfavourite"}",
                    "".toFormRequestBody().toPost()
                )?.also { result ->
                    resultStatus = TootParser(this, accessInfo).status(result.jsonObject)
                }
            }
        }

        appState.resetBusyFav(accessInfo, statusArg)

        if (result != null) {
            when (val newStatus = resultStatus) {
                null -> showToast(true, result.error)
                else -> {
                    val oldCount = statusArg.favourites_count
                    val newCount = newStatus.favourites_count
                    if (oldCount != null && newCount != null) {
                        if (accessInfo.isMisskey) {
                            newStatus.favourited = bSet
                        }
                        if (bSet && newStatus.favourited && newCount <= oldCount) {
                            // 星をつけたのにカウントが上がらないのは違和感あるので、表示をいじる
                            newStatus.favourites_count = oldCount + 1L
                        } else if (!bSet && !newStatus.favourited && newCount >= oldCount) {
                            // 星を外したのにカウントが下がらないのは違和感あるので、表示をいじる
                            // 0未満にはならない
                            newStatus.favourites_count =
                                if (oldCount < 1L) 0L else oldCount - 1L
                        }
                    }

                    for (column in appState.columnList) {
                        column.findStatus(
                            accessInfo.apDomain,
                            newStatus.id
                        ) { account, status ->

                            // 同タンス別アカウントでもカウントは変化する
                            status.favourites_count = newStatus.favourites_count

                            // 同アカウントならfav状態を変化させる
                            if (accessInfo == account) {
                                status.favourited = newStatus.favourited
                            }

                            true
                        }
                    }
                    callback()
                }
            }
        }

        // 結果に関わらず、更新中状態から復帰させる
        showColumnMatchAccount(accessInfo)
    }
}

// アカウントを選んでお気に入り
fun ActMain.favouriteFromAnotherAccount(
    timelineAccount: SavedAccount,
    status: TootStatus?,
) {
    status ?: return
    launchMain {
        pickAccount(
            bAllowPseudo = false,
            bAuto = false,
            message = getString(R.string.account_picker_favourite),
            accountListArg = accountListNonPseudo(timelineAccount.apDomain)
        )?.let { action_account ->
            favourite(
                action_account,
                status,
                calcCrossAccountMode(timelineAccount, action_account),
                callback = favouriteCompleteCallback
            )
        }
    }
}

////////////////////////////////////////////////////////////////

// お気に入りの非同期処理
fun ActMain.bookmark(
    accessInfo: SavedAccount,
    statusArg: TootStatus,
    crossAccountMode: CrossAccountMode,
    callback: () -> Unit,
    bSet: Boolean = true,
    bConfirmed: Boolean = false,
) {
    if (appState.isBusyFav(accessInfo, statusArg)) {
        showToast(false, R.string.wait_previous_operation)
        return
    }
    if (accessInfo.isMisskey) {
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
                AcctColor.getNickname(accessInfo)
            )
        ) {
            bookmark(

                accessInfo,
                statusArg,
                crossAccountMode,
                callback,
                bSet = bSet,
                bConfirmed = true
            )
        }
        return
    }

    //
    appState.setBusyBookmark(accessInfo, statusArg)

    // ファボ表示を更新中にする
    showColumnMatchAccount(accessInfo)

    //
    launchMain {
        var resultStatus: TootStatus? = null
        val result = runApiTask(accessInfo, progressStyle = ApiTask.PROGRESS_NONE) { client ->
            val targetStatus = if (crossAccountMode.isRemote) {
                val (result, status) = client.syncStatus(accessInfo, statusArg)
                status ?: return@runApiTask result
                if (status.bookmarked) {
                    return@runApiTask TootApiResult(getString(R.string.already_bookmarked))
                }
                status
            } else {
                statusArg
            }

            client.request(
                "/api/v1/statuses/${targetStatus.id}/${if (bSet) "bookmark" else "unbookmark"}",
                "".toFormRequestBody().toPost()
            )?.also { result ->
                resultStatus = TootParser(this, accessInfo).status(result.jsonObject)
            }
        }

        appState.resetBusyBookmark(accessInfo, statusArg)

        if (result != null) {
            when (val newStatus = resultStatus) {
                null -> showToast(true, result.error)
                else -> {
                    for (column in appState.columnList) {
                        column.findStatus(
                            accessInfo.apDomain,
                            newStatus.id
                        ) { account, status ->

                            // 同アカウントならブックマーク状態を伝播する
                            if (accessInfo == account) {
                                status.bookmarked = newStatus.bookmarked
                            }

                            true
                        }
                    }
                    callback()
                }
            }
        }
        // 結果に関わらず、更新中状態から復帰させる
        showColumnMatchAccount(accessInfo)
    }
}

// アカウントを選んでブックマーク
fun ActMain.bookmarkFromAnotherAccount(
    timelineAccount: SavedAccount,
    status: TootStatus?,
) {
    status ?: return

    launchMain {
        pickAccount(
            bAllowPseudo = false,
            bAuto = false,
            message = getString(R.string.account_picker_bookmark),
            accountListArg = accountListNonPseudo(timelineAccount.apDomain)
        )?.let { action_account ->
            bookmark(
                action_account,
                status,
                calcCrossAccountMode(timelineAccount, action_account),
                callback = bookmarkCompleteCallback
            )
        }
    }
}

///////////////////////////////////////////////////////////////

fun ActMain.boost(
    accessInfo: SavedAccount,
    statusArg: TootStatus,
    statusOwner: Acct,
    crossAccountMode: CrossAccountMode,
    bSet: Boolean = true,
    bConfirmed: Boolean = false,
    visibility: TootVisibility? = null,
    callback: () -> Unit,
) {

    // アカウントからステータスにブースト操作を行っているなら、何もしない
    if (appState.isBusyBoost(accessInfo, statusArg)) {
        showToast(false, R.string.wait_previous_operation)
        return
    }

    // Mastodonは非公開トゥートをブーストできるのは本人だけ
    val isPrivateToot = accessInfo.isMastodon &&
        statusArg.visibility == TootVisibility.PrivateFollowers

    if (isPrivateToot && accessInfo.acct != statusOwner) {
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
                AcctColor.getNickname(accessInfo)
            ),
            object : DlgConfirm.Callback {
                override fun onOK() {
                    boost(

                        accessInfo,
                        statusArg,
                        statusOwner,
                        crossAccountMode,
                        bSet = bSet,
                        bConfirmed = true,
                        visibility = visibility,
                        callback = callback,
                    )
                }

                override var isConfirmEnabled: Boolean
                    get() = when (bSet) {
                        true -> accessInfo.confirm_boost
                        else -> accessInfo.confirm_unboost
                    }
                    set(value) {
                        when (bSet) {
                            true -> accessInfo.confirm_boost = value
                            else -> accessInfo.confirm_unboost = value
                        }
                        accessInfo.saveSetting()
                        reloadAccountSetting(accessInfo)
                    }
            })
        return
    }

    appState.setBusyBoost(accessInfo, statusArg)
    // ブースト表示を更新中にする
    showColumnMatchAccount(accessInfo)
    // misskeyは非公開トゥートをブーストできないっぽい

    launchMain {
        var resultStatus: TootStatus? = null
        var resultUnrenoteId: EntityId? = null
        val result = runApiTask(accessInfo, progressStyle = ApiTask.PROGRESS_NONE) { client ->

            val parser = TootParser(this, accessInfo)

            val targetStatus = if (crossAccountMode.isRemote) {
                val (result, status) = client.syncStatus(accessInfo, statusArg)
                if (status == null) return@runApiTask result
                if (status.reblogged) {
                    return@runApiTask TootApiResult(getString(R.string.already_boosted))
                }
                status
            } else {
                // 既に自タンスのステータスがある
                statusArg
            }

            if (accessInfo.isMisskey) {
                if (!bSet) {
                    val myRenoteId = targetStatus.myRenoteId
                        ?: return@runApiTask TootApiResult("missing renote id.")

                    client.request(
                        "/api/notes/delete",
                        accessInfo.putMisskeyApiToken().apply {
                            put("noteId", myRenoteId.toString())
                            put("renoteId", targetStatus.id.toString())
                        }
                            .toPostRequestBuilder()
                    )
                        ?.also {
                            if (it.response?.code == 204) {
                                resultUnrenoteId = myRenoteId
                            }
                        }
                } else {
                    client.request(
                        "/api/notes/create",
                        accessInfo.putMisskeyApiToken().apply {
                            put("renoteId", targetStatus.id.toString())
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
                    "/api/v1/statuses/${targetStatus.id}/${if (bSet) "reblog" else "unreblog"}",
                    b
                )?.also { result ->
                    // reblogはreblogを表すStatusを返す
                    // unreblogはreblogしたStatusを返す
                    val s = parser.status(result.jsonObject)
                    resultStatus = s?.reblog ?: s
                }
            }
        }

        appState.resetBusyBoost(accessInfo, statusArg)

        if (result != null) {
            val unrenoteId = resultUnrenoteId
            val newStatus = resultStatus
            when {
                // Misskeyでunrenoteに成功した
                unrenoteId != null -> {

                    // 星を外したのにカウントが下がらないのは違和感あるので、表示をいじる
                    // 0未満にはならない
                    val count = max(0, (statusArg.reblogs_count ?: 1) - 1)

                    for (column in appState.columnList) {
                        column.findStatus(
                            accessInfo.apDomain,
                            statusArg.id
                        ) { account, status ->

                            // 同タンス別アカウントでもカウントは変化する
                            status.reblogs_count = count

                            // 同アカウントならreblogged状態を変化させる
                            if (accessInfo == account && status.myRenoteId == unrenoteId) {
                                status.myRenoteId = null
                                status.reblogged = false
                            }
                            true
                        }
                    }
                    callback()
                }

                // 処理に成功した
                newStatus != null -> {
                    // カウント数は遅延があるみたいなので、恣意的に表示を変更する
                    // ブーストカウント数を加工する
                    val oldCount = statusArg.reblogs_count
                    val newCount = newStatus.reblogs_count
                    if (oldCount != null && newCount != null) {
                        if (bSet && newStatus.reblogged && newCount <= oldCount) {
                            // 星をつけたのにカウントが上がらないのは違和感あるので、表示をいじる
                            newStatus.reblogs_count = oldCount + 1
                        } else if (!bSet && !newStatus.reblogged && newCount >= oldCount) {
                            // 星を外したのにカウントが下がらないのは違和感あるので、表示をいじる
                            // 0未満にはならない
                            newStatus.reblogs_count = if (oldCount < 1) 0 else oldCount - 1
                        }
                    }

                    for (column in appState.columnList) {
                        column.findStatus(
                            accessInfo.apDomain,
                            newStatus.id
                        ) { account, status ->

                            // 同タンス別アカウントでもカウントは変化する
                            status.reblogs_count = newStatus.reblogs_count

                            if (accessInfo == account) {

                                // 同アカウントならreblog状態を変化させる
                                when {
                                    accessInfo.isMastodon ->
                                        status.reblogged = newStatus.reblogged

                                    bSet && status.myRenoteId == null -> {
                                        status.myRenoteId = newStatus.myRenoteId
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
        showColumnMatchAccount(accessInfo)
    }
}

fun ActMain.boostFromAnotherAccount(
    timelineAccount: SavedAccount,
    status: TootStatus?,
) {
    status ?: return
    launchMain {
        val statusOwner = timelineAccount.getFullAcct(status.account)

        val isPrivateToot = timelineAccount.isMastodon &&
            status.visibility == TootVisibility.PrivateFollowers

        if (isPrivateToot) {
            val list = ArrayList<SavedAccount>()
            for (a in SavedAccount.loadAccountList(applicationContext)) {
                if (a.acct == statusOwner) list.add(a)
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
                    statusOwner,
                    calcCrossAccountMode(timelineAccount, action_account),
                    callback = boostCompleteCallback
                )
            }
        } else {
            pickAccount(
                bAllowPseudo = false,
                bAuto = false,
                message = getString(R.string.account_picker_boost),
                accountListArg = accountListNonPseudo(timelineAccount.apDomain)
            )?.let { action_account ->
                boost(
                    action_account,
                    status,
                    statusOwner,
                    calcCrossAccountMode(timelineAccount, action_account),
                    callback = boostCompleteCallback
                )
            }
        }
    }
}

fun ActMain.statusDelete(
    accessInfo: SavedAccount,
    statusId: EntityId,
) {
    launchMain {
        runApiTask(accessInfo) { client ->
            if (accessInfo.isMisskey) {
                client.request(
                    "/api/notes/delete",
                    accessInfo.putMisskeyApiToken().apply {
                        put("noteId", statusId)
                    }
                        .toPostRequestBuilder()
                )
                // 204 no content
            } else {
                client.request(
                    "/api/v1/statuses/$statusId",
                    Request.Builder().delete()
                )
            }
        }?.let { result ->

            if (result.jsonObject != null) {
                showToast(false, R.string.delete_succeeded)
                for (column in appState.columnList) {
                    column.onStatusRemoved(accessInfo.apDomain, statusId)
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
    accessInfo: SavedAccount,
    status: TootStatus,
    bSet: Boolean,
) {
    launchMain {
        var resultStatus: TootStatus? = null
        runApiTask(
            accessInfo,
            progressPrefix = getString(R.string.profile_pin_progress)
        ) { client ->
            client.request(
                "/api/v1/statuses/${status.id}/${if (bSet) "pin" else "unpin"}",
                "".toFormRequestBody().toPost()
            )?.also { result ->
                resultStatus = TootParser(this, accessInfo).status(result.jsonObject)
            }
        }?.let { result ->
            when (val newStatus = resultStatus) {
                null -> showToast(true, result.error)
                else -> {
                    for (column in appState.columnList) {
                        if (accessInfo == column.accessInfo) {
                            column.findStatus(
                                accessInfo.apDomain,
                                newStatus.id
                            ) { _, status ->
                                status.pinned = bSet
                                true
                            }
                        }
                    }
                    showColumnMatchAccount(accessInfo)
                }
            }
        }
    }
}

// 投稿画面を開く。初期テキストを指定する
fun ActMain.statusRedraft(
    accessInfo: SavedAccount,
    status: TootStatus,
) {
    postHelper.closeAcctPopup()

    when {
        accessInfo.isMisskey ->
            openActPostImpl(
                accessInfo.db_id,
                redraftStatus = status,
                replyStatus = status.reply
            )

        status.in_reply_to_id == null ->
            openActPostImpl(
                accessInfo.db_id,
                redraftStatus = status
            )

        else -> launchMain {
            var resultStatus: TootStatus? = null
            runApiTask(accessInfo) { client ->
                client.request("/api/v1/statuses/${status.in_reply_to_id}")
                    ?.also { resultStatus = TootParser(this, accessInfo).status(it.jsonObject) }
            }?.let { result ->
                when (val replyStatus = resultStatus) {
                    null -> showToast(
                        true,
                        "${getString(R.string.cant_sync_toot)} : ${result.error ?: "(no information)"}"
                    )
                    else -> openActPostImpl(
                        accessInfo.db_id,
                        redraftStatus = status,
                        replyStatus = replyStatus
                    )
                }
            }
        }
    }
}

fun ActMain.scheduledPostDelete(
    accessInfo: SavedAccount,
    item: TootScheduled,
    bConfirmed: Boolean = false,
    callback: () -> Unit,
) {
    val act = this@scheduledPostDelete
    if (!bConfirmed) {
        DlgConfirm.openSimple(
            act,
            getString(R.string.scheduled_status_delete_confirm)
        ) {
            scheduledPostDelete(
                accessInfo,
                item,
                bConfirmed = true,
                callback = callback
            )
        }
        return
    }
    launchMain {
        runApiTask(accessInfo) { client ->
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
    accessInfo: SavedAccount,
    item: TootScheduled,
) {
    launchMain {
        var resultStatus: TootStatus? = null
        runApiTask(accessInfo) { client ->
            val replyStatusId = item.inReplyToId ?: return@runApiTask TootApiResult()
            client.request("/api/v1/statuses/$replyStatusId")?.also { result ->
                resultStatus = TootParser(this, accessInfo).status(result.jsonObject)
            }
        }?.let { result ->
            when (val error = result.error) {
                null -> openActPostImpl(
                    accessInfo.db_id,
                    scheduledStatus = item,
                    replyStatus = resultStatus
                )

                else -> showToast(true, error)
            }
        }
    }
}
