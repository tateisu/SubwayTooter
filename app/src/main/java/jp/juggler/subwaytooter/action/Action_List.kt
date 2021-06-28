package jp.juggler.subwaytooter.action

import android.app.Dialog
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.actmain.addColumn
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.MisskeyAntenna
import jp.juggler.subwaytooter.api.entity.TimelineItem
import jp.juggler.subwaytooter.api.entity.TootList
import jp.juggler.subwaytooter.api.entity.parseItem
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.column.onListListUpdated
import jp.juggler.subwaytooter.column.onListNameUpdated
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.dialog.DlgConfirm
import jp.juggler.subwaytooter.dialog.DlgTextInput
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.*
import okhttp3.Request

fun ActMain.clickListTl(pos: Int, accessInfo: SavedAccount, item: TimelineItem?) {
    when (item) {
        is TootList -> addColumn(pos, accessInfo, ColumnType.LIST_TL, item.id)
        is MisskeyAntenna -> addColumn(pos, accessInfo, ColumnType.MISSKEY_ANTENNA_TL, item.id)
    }
}

fun ActMain.clickListMoreButton(pos: Int, accessInfo: SavedAccount, item: TimelineItem?) {
    when (item) {
        is TootList -> {
            ActionsDialog()
                .addAction(getString(R.string.list_timeline)) {
                    addColumn(pos, accessInfo, ColumnType.LIST_TL, item.id)
                }
                .addAction(getString(R.string.list_member)) {
                    addColumn(
                        false,
                        pos,
                        accessInfo,
                        ColumnType.LIST_MEMBER,
                        item.id
                    )
                }
                .addAction(getString(R.string.rename)) {
                    listRename(accessInfo, item)
                }
                .addAction(getString(R.string.delete)) {
                    listDelete(accessInfo, item)
                }
                .show(this, item.title)
        }

        is MisskeyAntenna -> {
            // XXX
        }
    }
}

fun interface ListOnCreatedCallback {
    fun onCreated(list: TootList)
}

// リストを作成する
fun ActMain.listCreate(
    accessInfo: SavedAccount,
    title: String,
    callback: ListOnCreatedCallback?,
) {
    launchMain {
        var resultList: TootList? = null
        runApiTask(accessInfo) { client ->
            if (accessInfo.isMisskey) {
                client.request(
                    "/api/users/lists/create",
                    accessInfo.putMisskeyApiToken().apply {
                        put("title", title)
                        put("name", title)
                    }
                        .toPostRequestBuilder()
                )
            } else {
                client.request(
                    "/api/v1/lists",
                    jsonObject {
                        put("title", title)
                    }
                        .toPostRequestBuilder()
                )
            }?.also { result ->
                client.publishApiProgress(getString(R.string.parsing_response))
                resultList = parseItem(
                    ::TootList,
                    TootParser(this, accessInfo),
                    result.jsonObject
                )
            }
        }?.let { result ->
            when (val list = resultList) {
                null -> showToast(false, result.error)

                else -> {
                    for (column in appState.columnList) {
                        column.onListListUpdated(accessInfo)
                    }
                    showToast(false, R.string.list_created)
                    callback?.onCreated(list)
                }
            }
        }
    }
}

// リストを削除する
fun ActMain.listDelete(
    accessInfo: SavedAccount,
    list: TootList,
    bConfirmed: Boolean = false,
) {
    if (!bConfirmed) {
        DlgConfirm.openSimple(
            this,
            getString(R.string.list_delete_confirm, list.title)
        ) {
            listDelete(accessInfo, list, bConfirmed = true)
        }
        return
    }

    launchMain {
        runApiTask(accessInfo) { client ->
            if (accessInfo.isMisskey) {
                client.request(
                    "/api/users/lists/delete",
                    accessInfo.putMisskeyApiToken().apply {
                        put("listId", list.id)
                    }
                        .toPostRequestBuilder()
                )
                // 204 no content
            } else {
                client.request(
                    "/api/v1/lists/${list.id}",
                    Request.Builder().delete()
                )
            }
        }?.let { result ->

            when (result.jsonObject) {
                null -> showToast(false, result.error)

                else -> {
                    for (column in appState.columnList) {
                        column.onListListUpdated(accessInfo)
                    }
                    showToast(false, R.string.delete_succeeded)
                }
            }
        }
    }
}

fun ActMain.listRename(
    accessInfo: SavedAccount,
    item: TootList,
) {

    DlgTextInput.show(
        this,
        getString(R.string.rename),
        item.title,
        callback = object : DlgTextInput.Callback {
            override fun onEmptyError() {
                showToast(false, R.string.list_name_empty)
            }

            override fun onOK(dialog: Dialog, text: String) {
                launchMain {
                    var resultList: TootList? = null
                    runApiTask(accessInfo) { client ->
                        if (accessInfo.isMisskey) {
                            client.request(
                                "/api/users/lists/update",
                                accessInfo.putMisskeyApiToken().apply {
                                    put("listId", item.id)
                                    put("title", text)
                                }
                                    .toPostRequestBuilder()
                            )
                        } else {
                            client.request(
                                "/api/v1/lists/${item.id}",
                                jsonObject {
                                    put("title", text)
                                }

                                    .toPutRequestBuilder()
                            )
                        }?.also { result ->
                            client.publishApiProgress(getString(R.string.parsing_response))
                            resultList = parseItem(
                                ::TootList,
                                TootParser(this, accessInfo),
                                result.jsonObject
                            )
                        }
                    }?.let { result ->
                        when (val list = resultList) {
                            null -> showToast(false, result.error)
                            else -> {
                                for (column in appState.columnList) {
                                    column.onListNameUpdated(accessInfo, list)
                                }
                                dialog.dismissSafe()
                            }
                        }
                    }
                }
            }
        }
    )
}
