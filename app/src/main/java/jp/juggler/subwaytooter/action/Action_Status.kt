package jp.juggler.subwaytooter.action

import android.content.Intent
import androidx.appcompat.app.AlertDialog
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.actmain.addColumn
import jp.juggler.subwaytooter.actmain.reloadAccountSetting
import jp.juggler.subwaytooter.actmain.showColumnMatchAccount
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.TootScheduled
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.column.*
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.dialog.DlgConfirm.confirm
import jp.juggler.subwaytooter.dialog.pickAccount
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.emptyCallback
import jp.juggler.util.*
import kotlinx.coroutines.CancellationException
import okhttp3.Request

fun ActMain.clickStatusDelete(
    accessInfo: SavedAccount,
    status: TootStatus?,
) {
    status ?: return
    AlertDialog.Builder(this)
        .setMessage(getString(R.string.confirm_delete_status))
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(R.string.ok) { _, _ -> statusDelete(accessInfo, status.id) }
        .show()
}

fun ActMain.clickBookmark(accessInfo: SavedAccount, status: TootStatus, willToast: Boolean) {
    if (accessInfo.isPseudo) {
        bookmarkFromAnotherAccount(accessInfo, status)
    } else {
        // トグル動作
        val bSet = !status.bookmarked

        bookmark(
            accessInfo,
            status,
            CrossAccountMode.SameAccount,
            bSet = bSet,
            callback = when {
                !willToast -> emptyCallback
                // 簡略表示なら結果をトースト表示
                bSet -> bookmarkCompleteCallback
                else -> unbookmarkCompleteCallback
            },
        )
    }
}

fun ActMain.clickFavourite(accessInfo: SavedAccount, status: TootStatus, willToast: Boolean) {
    if (accessInfo.isPseudo) {
        favouriteFromAnotherAccount(accessInfo, status)
    } else {
        // トグル動作
        val bSet = !status.favourited

        favourite(
            accessInfo,
            status,
            CrossAccountMode.SameAccount,
            bSet = bSet,
            callback = when {
                !willToast -> emptyCallback
                // 簡略表示なら結果をトースト表示
                bSet -> favouriteCompleteCallback
                else -> unfavouriteCompleteCallback
            },
        )
    }
}

fun ActMain.clickScheduledToot(accessInfo: SavedAccount, item: TootScheduled, column: Column) {
    ActionsDialog()
        .addAction(getString(R.string.edit)) {
            scheduledPostEdit(accessInfo, item)
        }
        .addAction(getString(R.string.delete)) {
            launchAndShowError {
                scheduledPostDelete(accessInfo, item)
                column.onScheduleDeleted(item)
                showToast(false, R.string.scheduled_post_deleted)
            }
        }
        .show(this)
}

fun ActMain.launchActText(intent: Intent) = arActText.launch(intent)

///////////////////////////////////////////////////////////////
// お気に入りの非同期処理
fun ActMain.favourite(
    accessInfo: SavedAccount,
    statusArg: TootStatus,
    crossAccountMode: CrossAccountMode,
    callback: () -> Unit,
    bSet: Boolean = true,
) {
    launchAndShowError {

        if (appState.isBusyFav(accessInfo, statusArg)) {
            showToast(false, R.string.wait_previous_operation)
            return@launchAndShowError
        }

        // 必要なら確認を出す
        if (accessInfo.isMastodon) {
            confirm(
                getString(
                    when (bSet) {
                        true -> R.string.confirm_favourite_from
                        else -> R.string.confirm_unfavourite_from
                    },
                    AcctColor.getNickname(accessInfo)
                ),
                when (bSet) {
                    true -> accessInfo.confirm_favourite
                    else -> accessInfo.confirm_unfavourite
                }
            ) { newConfirmEnabled ->
                when (bSet) {
                    true -> accessInfo.confirm_favourite = newConfirmEnabled
                    else -> accessInfo.confirm_unfavourite = newConfirmEnabled
                }
                accessInfo.saveSetting()
                reloadAccountSetting(accessInfo)
            }
        }

        //
        appState.setBusyFav(accessInfo, statusArg)

        // ファボ表示を更新中にする
        showColumnMatchAccount(accessInfo)

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
) {
    if (appState.isBusyFav(accessInfo, statusArg)) {
        showToast(false, R.string.wait_previous_operation)
        return
    }
    if (accessInfo.isMisskey) {
        showToast(false, R.string.misskey_account_not_supported)
        return
    }
    launchAndShowError {

        // 必要なら確認を出す
        // ブックマークは解除する時だけ確認する
        if (!bSet) {
            confirm(
                getString(
                    R.string.confirm_unbookmark_from,
                    AcctColor.getNickname(accessInfo)
                ),
                accessInfo.confirm_unbookmark
            ) { newConfirmEnabled ->
                accessInfo.confirm_unbookmark = newConfirmEnabled
                accessInfo.saveSetting()
                reloadAccountSetting(accessInfo)
            }
        }

        //
        appState.setBusyBookmark(accessInfo, statusArg)

        // ファボ表示を更新中にする
        showColumnMatchAccount(accessInfo)

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
    status: TootStatus?,
    bSet: Boolean,
) {
    status ?: return

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
    status: TootStatus?,
) {
    status ?: return

    completionHelper.closeAcctPopup()

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

// 投稿画面を開く。初期テキストを指定する
fun ActMain.statusEdit(
    accessInfo: SavedAccount,
    status: TootStatus?,
) {
    status ?: return

    completionHelper.closeAcctPopup()

    when {
        accessInfo.isMisskey ->
            openActPostImpl(
                accessInfo.db_id,
                editStatus = status,
                replyStatus = status.reply
            )

        status.in_reply_to_id == null ->
            openActPostImpl(
                accessInfo.db_id,
                editStatus = status
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
                        editStatus = status,
                        replyStatus = replyStatus
                    )
                }
            }
        }
    }
}

suspend fun ActMain.scheduledPostDelete(
    accessInfo: SavedAccount,
    item: TootScheduled,
    bConfirmed: Boolean = false,
) {
    if (!bConfirmed) {
        confirm(R.string.scheduled_status_delete_confirm)
    }
    val result = runApiTask(accessInfo) { client ->
        client.request(
            "/api/v1/scheduled_statuses/${item.id}",
            Request.Builder().delete()
        )
    } ?: throw CancellationException("scheduledPostDelete cancelled.")
    result.error?.notEmpty()?.let { error(it) }
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

// アカウントを選んでタイムラインカラムを追加
fun ActMain.openStatusHistory(
    pos: Int,
    accessInfo: SavedAccount,
    status: TootStatus,
) {
    addColumn(pos, accessInfo, ColumnType.STATUS_HISTORY, status.id, status.json)
}
