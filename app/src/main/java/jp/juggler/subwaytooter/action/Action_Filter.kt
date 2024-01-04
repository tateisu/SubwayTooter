package jp.juggler.subwaytooter.action

import jp.juggler.subwaytooter.ActKeywordFilter
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.ApiPath
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootApiResultException
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.TootFilter
import jp.juggler.subwaytooter.api.runApiTask2
import jp.juggler.subwaytooter.column.onFilterDeleted
import jp.juggler.subwaytooter.dialog.DlgConfirm.confirm
import jp.juggler.subwaytooter.dialog.actionsDialog
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.log.showToast

// private val log = LogCategory("Action_Filter")

fun ActMain.openFilterMenu(accessInfo: SavedAccount, item: TootFilter?) {
    item ?: return
    val activity = this
    launchAndShowError {
        actionsDialog(getString(R.string.filter_of, item.displayString)) {
            action(getString(R.string.edit)) {
                ActKeywordFilter.open(activity, accessInfo, item.id)
            }
            action(getString(R.string.delete)) {
                filterDelete(accessInfo, item)
            }
        }
    }
}

suspend fun TootApiClient.filterDelete(filterId: EntityId): TootApiResult {
    for (path in arrayOf(
        "/api/v2/filters/${filterId}",
        "/api/v1/filters/${filterId}",
    )) {
        try {
            return requestOrThrow(path = path)
        } catch (ex: TootApiResultException) {
            when (ex.result?.response?.code) {
                404 -> continue
                else -> throw ex
            }
        }
    }
    error("missing filter APIs.")
}

suspend fun TootApiClient.filterLoad(): List<TootFilter> {
    for (path in arrayOf(
        ApiPath.PATH_FILTERS_V2,
        ApiPath.PATH_FILTERS_V1,
    )) {
        try {
            val jsonArray = requestOrThrow(path).jsonArray
                ?: error("API response has no jsonArray.")
            return TootFilter.parseList(jsonArray)
                ?: error("TootFilter.parseList returns null.")
        } catch (ex: TootApiResultException) {
            when (ex.result?.response?.code) {
                404 -> continue
                else -> throw ex
            }
        }
    }
    error("missing filter APIs.")
}

fun ActMain.filterDelete(
    accessInfo: SavedAccount,
    filter: TootFilter,
) = launchAndShowError {
    confirm(R.string.filter_delete_confirm, filter.displayString)
    val newFilters = runApiTask2(accessInfo) { client ->
        client.filterDelete(filter.id)
        client.filterLoad()
    }
    showToast(false, R.string.delete_succeeded)
    for (column in appState.columnList) {
        if (column.accessInfo == accessInfo) {
            column.onFilterDeleted(filter, newFilters)
        }
    }
}
