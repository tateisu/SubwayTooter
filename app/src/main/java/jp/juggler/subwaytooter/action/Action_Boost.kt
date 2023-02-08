package jp.juggler.subwaytooter.action

import android.content.Context
import androidx.appcompat.app.AlertDialog
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.actmain.addColumn
import jp.juggler.subwaytooter.actmain.reloadAccountSetting
import jp.juggler.subwaytooter.actmain.showColumnMatchAccount
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.api.entity.TootVisibility
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.column.findStatus
import jp.juggler.subwaytooter.dialog.DlgConfirm.confirm
import jp.juggler.subwaytooter.dialog.pickAccount
import jp.juggler.subwaytooter.getVisibilityCaption
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.accountListNonPseudo
import jp.juggler.subwaytooter.table.daoAcctColor
import jp.juggler.subwaytooter.table.daoSavedAccount
import jp.juggler.subwaytooter.util.emptyCallback
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.data.JsonObject
import jp.juggler.util.log.showToast
import jp.juggler.util.network.toPostRequestBuilder
import kotlin.math.max

private class BoostImpl(
    val activity: ActMain,
    val accessInfo: SavedAccount,
    val statusArg: TootStatus,
    val statusOwner: Acct,
    val crossAccountMode: CrossAccountMode,
    val bSet: Boolean = true,
    val visibility: TootVisibility? = null,
    val callback: () -> Unit,
) {
    val parser = TootParser(activity, accessInfo)
    var resultStatus: TootStatus? = null
    var resultUnrenoteId: EntityId? = null

    // Mastodonは非公開トゥートをブーストできるのは本人だけ
    private val isPrivateToot = accessInfo.isMastodon &&
            statusArg.visibility == TootVisibility.PrivateFollowers

    private var bConfirmed = false

    private fun preCheck(): Boolean {

        // アカウントからステータスにブースト操作を行っているなら、何もしない
        if (activity.appState.isBusyBoost(accessInfo, statusArg)) {
            activity.showToast(false, R.string.wait_previous_operation)
            return false
        }

        if (isPrivateToot && accessInfo.acct != statusOwner) {
            activity.showToast(false, R.string.boost_private_toot_not_allowed)
            return false
        }
        // DMとかのブーストはAPI側がエラーを出すだろう？

        return true
    }

    private suspend fun Context.syncStatus(client: TootApiClient) =
        if (!crossAccountMode.isRemote) {
            // 既に自タンスのステータスがある
            statusArg
        } else {
            val (result, status) = client.syncStatus(accessInfo, statusArg)
            when {
                status == null -> errorApiResult(result)
                status.reblogged -> errorApiResult(getString(R.string.already_boosted))
                else -> status
            }
        }

    // ブースト結果をUIに反映させる
    private fun after(result: TootApiResult?, newStatus: TootStatus?, unrenoteId: EntityId?) {
        result ?: return // cancelled.
        when {
            // Misskeyでunrenoteに成功した
            unrenoteId != null -> {
                // 星を外したのにカウントが下がらないのは違和感あるので、表示をいじる
                // 0未満にしない
                val count = max(0, (statusArg.reblogs_count ?: 1) - 1)
                for (column in activity.appState.columnList) {
                    column.findStatus(accessInfo.apDomain, statusArg.id) { account, status ->
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
                        // 0未満にはしない
                        newStatus.reblogs_count = if (oldCount < 1) 0 else oldCount - 1
                    }
                }

                for (column in activity.appState.columnList) {
                    column.findStatus(accessInfo.apDomain, newStatus.id) { account, status ->
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
                                // Misskey のunrenote時はここを通らない
                            }
                        }
                        true
                    }
                }
                callback()
            }

            else -> activity.showToast(true, result.error)
        }
    }

    suspend fun boostApi(client: TootApiClient, targetStatus: TootStatus): TootApiResult? =
        if (accessInfo.isMisskey) {
            if (!bSet) {
                val myRenoteId = targetStatus.myRenoteId ?: errorApiResult("missing renote id.")

                client.request(
                    "/api/notes/delete",
                    accessInfo.putMisskeyApiToken().apply {
                        put("noteId", myRenoteId.toString())
                        put("renoteId", targetStatus.id.toString())
                    }.toPostRequestBuilder()
                )?.also {
                    if (it.response?.code == 204) {
                        resultUnrenoteId = myRenoteId
                    }
                }
            } else {
                client.request(
                    "/api/notes/create",
                    accessInfo.putMisskeyApiToken().apply {
                        put("renoteId", targetStatus.id.toString())
                    }.toPostRequestBuilder()
                )?.also { result ->
                    val jsonObject = result.jsonObject
                    if (jsonObject != null) {
                        val outerStatus =
                            parser.status(jsonObject.jsonObject("createdNote") ?: jsonObject)
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

    fun run() {
        activity.launchAndShowError {
            if (!preCheck()) return@launchAndShowError

            if (!bConfirmed) {
                activity.confirm(
                    activity.getString(
                        when {
                            !bSet -> R.string.confirm_unboost_from
                            isPrivateToot -> R.string.confirm_boost_private_from
                            visibility == TootVisibility.PrivateFollowers -> R.string.confirm_private_boost_from
                            else -> R.string.confirm_boost_from
                        },
                        daoAcctColor.getNickname(accessInfo)
                    ),
                    when (bSet) {
                        true -> accessInfo.confirmBoost
                        else -> accessInfo.confirmUnboost
                    }
                ) { newConfirmEnabled ->
                    when (bSet) {
                        true -> accessInfo.confirmBoost = newConfirmEnabled
                        else -> accessInfo.confirmUnboost = newConfirmEnabled
                    }
                    daoSavedAccount.save(accessInfo)
                    activity.reloadAccountSetting(accessInfo)
                }
            }

            // ブースト表示を更新中にする
            activity.appState.setBusyBoost(accessInfo, statusArg)
            activity.showColumnMatchAccount(accessInfo)

            val result =
                activity.runApiTask(
                    accessInfo,
                    progressStyle = ApiTask.PROGRESS_NONE
                ) { client ->
                    try {
                        val targetStatus = syncStatus(client)
                        boostApi(client, targetStatus)
                    } catch (ex: TootApiResultException) {
                        ex.result
                    }
                }
            // 更新中状態をリセット
            activity.appState.resetBusyBoost(accessInfo, statusArg)
            // カラムデータの書き換え
            after(result, resultStatus, resultUnrenoteId)
            // result == null の場合でも更新中表示の解除が必要になる
            activity.showColumnMatchAccount(accessInfo)
        }
    }
}

fun ActMain.boost(
    accessInfo: SavedAccount,
    statusArg: TootStatus,
    statusOwner: Acct,
    crossAccountMode: CrossAccountMode,
    bSet: Boolean = true,
    visibility: TootVisibility? = null,
    callback: () -> Unit,
) {
    BoostImpl(
        activity = this,
        accessInfo = accessInfo,
        statusArg = statusArg,
        statusOwner = statusOwner,
        crossAccountMode = crossAccountMode,
        bSet = bSet,
        visibility = visibility,
        callback = callback,
    ).run()
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
            for (a in daoSavedAccount.loadAccountList()) {
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

fun ActMain.clickBoostWithVisibility(
    accessInfo: SavedAccount,
    status: TootStatus?,
) {
    status ?: return
    val list = if (accessInfo.isMisskey) {
        arrayOf(
            TootVisibility.Public,
            TootVisibility.UnlistedHome,
            TootVisibility.PrivateFollowers,
            TootVisibility.LocalPublic,
            TootVisibility.LocalHome,
            TootVisibility.LocalFollowers,
            TootVisibility.DirectSpecified,
            TootVisibility.DirectPrivate
        )
    } else {
        arrayOf(
            TootVisibility.Public,
            TootVisibility.UnlistedHome,
            TootVisibility.PrivateFollowers
        )
    }
    val captionList = list
        .map { getVisibilityCaption(this, accessInfo.isMisskey, it) }
        .toTypedArray()

    AlertDialog.Builder(this)
        .setTitle(R.string.choose_visibility)
        .setItems(captionList) { _, which ->
            if (which in list.indices) {
                boost(
                    accessInfo,
                    status,
                    accessInfo.getFullAcct(status.account),
                    CrossAccountMode.SameAccount,
                    visibility = list[which],
                    callback = boostCompleteCallback,
                )
            }
        }
        .setNegativeButton(R.string.cancel, null)
        .show()
}

fun ActMain.clickBoostBy(
    pos: Int,
    accessInfo: SavedAccount,
    status: TootStatus?,
    columnType: ColumnType = ColumnType.BOOSTED_BY,
) {
    status ?: return
    addColumn(false, pos, accessInfo, columnType, params= arrayOf(status.id))
}

fun ActMain.clickBoost(accessInfo: SavedAccount, status: TootStatus, willToast: Boolean) {
    if (accessInfo.isPseudo) {
        boostFromAnotherAccount(accessInfo, status)
    } else {
        // トグル動作
        val bSet = !status.reblogged

        boost(
            accessInfo,
            status,
            accessInfo.getFullAcct(status.account),
            CrossAccountMode.SameAccount,
            bSet = bSet,
            callback = when {
                !willToast -> emptyCallback
                // 簡略表示なら結果をトースト表示
                bSet -> boostCompleteCallback
                else -> unboostCompleteCallback
            },
        )
    }
}
