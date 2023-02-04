package jp.juggler.subwaytooter.action

import jp.juggler.subwaytooter.ActKeywordFilter
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootFilter
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.column.onFilterDeleted
import jp.juggler.subwaytooter.dialog.DlgConfirm.confirm
import jp.juggler.subwaytooter.dialog.actionsDialog
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.log.showToast
import okhttp3.Request

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

fun ActMain.filterDelete(
    accessInfo: SavedAccount,
    filter: TootFilter,
    bConfirmed: Boolean = false,
) {
    launchAndShowError {
        if (!bConfirmed) {
            confirm(R.string.filter_delete_confirm, filter.displayString)
        }

        var resultFilterList: List<TootFilter>? = null
        runApiTask(accessInfo) { client ->
            var result =
                client.request("/api/v1/filters/${filter.id}", Request.Builder().delete())
            if (result != null && result.error == null) {
                result = client.request("/api/v1/filters")
                val jsonArray = result?.jsonArray
                if (jsonArray != null) resultFilterList = TootFilter.parseList(jsonArray)
            }
            result
        }?.let { result ->
            when (val filterList = resultFilterList) {
                null -> showToast(false, result.error)

                else -> {
                    showToast(false, R.string.delete_succeeded)
                    for (column in appState.columnList) {
                        if (column.accessInfo == accessInfo) {
                            column.onFilterDeleted(filter, filterList)
                        }
                    }
                }
            }
        }
    }
}
