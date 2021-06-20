package jp.juggler.subwaytooter.action

import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.TootFilter
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.dialog.DlgConfirm
import jp.juggler.subwaytooter.onFilterDeleted
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.launchMain
import jp.juggler.util.showToast
import okhttp3.Request

// private val log = LogCategory("Action_Filter")

fun ActMain.filterDelete(
    accessInfo: SavedAccount,
    filter: TootFilter,
    bConfirmed: Boolean = false
) {
    if (!bConfirmed) {
        DlgConfirm.openSimple(
            this,
            getString(R.string.filter_delete_confirm, filter.phrase)
        ) {
            filterDelete(accessInfo, filter, bConfirmed = true)
        }
        return
    }

    launchMain {
        var resultFilterList: ArrayList<TootFilter>? = null
        runApiTask(accessInfo) { client ->
            var result = client.request("/api/v1/filters/${filter.id}", Request.Builder().delete())
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
