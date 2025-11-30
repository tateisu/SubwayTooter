package jp.juggler.subwaytooter.action

import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.actmain.addColumn
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.MisskeyAntenna
import jp.juggler.subwaytooter.api.entity.TimelineItem
import jp.juggler.subwaytooter.api.entity.TootList
import jp.juggler.subwaytooter.api.entity.parseItem
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.column.onListListUpdated
import jp.juggler.subwaytooter.column.onListNameUpdated
import jp.juggler.subwaytooter.dialog.DlgConfirm.confirm
import jp.juggler.subwaytooter.dialog.actionsDialog
import jp.juggler.subwaytooter.dialog.showTextInputDialog
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.data.buildJsonObject
import jp.juggler.util.log.showToast
import jp.juggler.util.network.toPostRequestBuilder
import jp.juggler.util.network.toPutRequestBuilder
import okhttp3.Request

fun ActMain.clickListTl(pos: Int, accessInfo: SavedAccount, item: TimelineItem?) {
    when (item) {
        is TootList ->
            addColumn(pos, accessInfo, ColumnType.LIST_TL, params = arrayOf(item.id))

        is MisskeyAntenna ->
            addColumn(pos, accessInfo, ColumnType.MISSKEY_ANTENNA_TL, params = arrayOf(item.id))
    }
}

fun ActMain.clickListMoreButton(pos: Int, accessInfo: SavedAccount, item: TimelineItem?) {
    when (item) {
        is TootList -> {
            launchAndShowError {
                actionsDialog(item.title) {
                    action(getString(R.string.list_timeline)) {
                        addColumn(pos, accessInfo, ColumnType.LIST_TL, params = arrayOf(item.id))
                    }
                    action(getString(R.string.list_member)) {
                        addColumn(
                            false,
                            pos,
                            accessInfo,
                            ColumnType.LIST_MEMBER,
                            params = arrayOf(item.id)
                        )
                    }
                    action(getString(R.string.rename)) {
                        listRename(accessInfo, item)
                    }
                    action(getString(R.string.delete)) {
                        listDelete(accessInfo, item)
                    }
                }
            }
        }

        is MisskeyAntenna -> {
            // XXX
        }
    }
}

// リストを作成する
suspend fun ActMain.listCreate(
    accessInfo: SavedAccount,
    title: String,
): TootList? {
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
                buildJsonObject {
                    put("title", title)
                }.toPostRequestBuilder()
            )
        }?.also { result ->
            client.publishApiProgress(getString(R.string.parsing_response))
            resultList = parseItem(result.jsonObject) {
                TootList(
                    TootParser(this, accessInfo),
                    it
                )
            }
        }
    }?.let { result ->
        when (resultList) {
            null -> showToast(false, result.error)
            else -> {
                for (column in appState.columnList) {
                    column.onListListUpdated(accessInfo)
                }
                showToast(false, R.string.list_created)
            }
        }
    }
    return resultList
}

// リストを削除する
fun ActMain.listDelete(
    accessInfo: SavedAccount,
    list: TootList,
    bConfirmed: Boolean = false,
) {
    launchAndShowError {
        if (!bConfirmed) {
            confirm(R.string.list_delete_confirm, list.title)
        }
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

suspend fun ActMain.listRename(
    accessInfo: SavedAccount,
    item: TootList,
) {
    showTextInputDialog(
        title = getString(R.string.rename),
        initialText = item.title,
        onEmptyText = { showToast(false, R.string.list_name_empty) },
    ) { text ->
        var resultList: TootList? = null
        val result = runApiTask(accessInfo) { client ->
            if (accessInfo.isMisskey) {
                client.request(
                    "/api/users/lists/update",
                    accessInfo.putMisskeyApiToken().apply {
                        put("listId", item.id)
                        put("title", text)
                    }.toPostRequestBuilder()
                )
            } else {
                client.request(
                    "/api/v1/lists/${item.id}",
                    buildJsonObject {
                        put("title", text)
                    }.toPutRequestBuilder()
                )
            }?.also { result ->
                client.publishApiProgress(getString(R.string.parsing_response))
                resultList = parseItem(result.jsonObject) {
                    TootList(
                        TootParser(this, accessInfo),
                        it
                    )
                }
            }
        }
        result ?: return@showTextInputDialog true
        when (val list = resultList) {
            null -> {
                showToast(false, result.error)
                false
            }

            else -> {
                for (column in appState.columnList) {
                    column.onListNameUpdated(accessInfo, list)
                }
                true
            }
        }
    }
}
