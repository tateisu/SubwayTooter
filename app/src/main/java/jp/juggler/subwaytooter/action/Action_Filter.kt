package jp.juggler.subwaytooter.action

import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootTask
import jp.juggler.subwaytooter.api.TootTaskRunner
import jp.juggler.subwaytooter.api.entity.TootFilter
import jp.juggler.subwaytooter.dialog.DlgConfirm
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.showToast
import okhttp3.Request

object Action_Filter {
	
	// private val log = LogCategory("Action_Filter")
	
	fun delete(
		activity : ActMain,
		access_info : SavedAccount,
		filter : TootFilter,
		bConfirmed : Boolean = false
	) {
		if(! bConfirmed) {
			DlgConfirm.openSimple(
				activity,
				activity.getString(R.string.filter_delete_confirm, filter.phrase)
			) {
				delete(activity, access_info, filter, bConfirmed = true)
			}
			return
		}
		
		TootTaskRunner(activity).run(access_info, object : TootTask {
			
			var filterList : ArrayList<TootFilter>? = null
			
			override suspend fun background(client : TootApiClient) : TootApiResult? {
				var result =
					client.request("/api/v1/filters/${filter.id}", Request.Builder().delete())
				if(result != null && result.error == null) {
					result = client.request("/api/v1/filters")
					val jsonArray = result?.jsonArray
					if(jsonArray != null) filterList = TootFilter.parseList(jsonArray)
				}
				return result
			}
			
			override suspend fun handleResult(result : TootApiResult?) {
				if(result == null) return  // cancelled.
				
				val filterList = this.filterList
				if(filterList != null) {
					activity.showToast(false, R.string.delete_succeeded)
					for(column in App1.getAppState(activity).column_list) {
						if(column.access_info == access_info) {
							column.onFilterDeleted(filter, filterList)
						}
					}
				} else {
					activity.showToast(false, result.error)
				}
				
			}
		})
	}
}
	