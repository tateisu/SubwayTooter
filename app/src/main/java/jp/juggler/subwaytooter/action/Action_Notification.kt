package jp.juggler.subwaytooter.action

import androidx.appcompat.app.AlertDialog
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Column
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootTask
import jp.juggler.subwaytooter.api.TootTaskRunner
import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.showToast
import jp.juggler.util.toPost
import jp.juggler.util.toRequestBody

object Action_Notification {
	
	fun deleteAll(
		activity : ActMain, target_account : SavedAccount, bConfirmed : Boolean
	) {
		if(! bConfirmed) {
			AlertDialog.Builder(activity)
				.setMessage(R.string.confirm_delete_notification)
				.setNegativeButton(R.string.cancel, null)
				.setPositiveButton(R.string.ok) { _, _ ->
					deleteAll(activity, target_account, true)
				}
				.show()
			return
		}
		TootTaskRunner(activity).run(target_account, object : TootTask {
			override fun background(client : TootApiClient) : TootApiResult? {
				// 空データを送る
				return client.request(
					"/api/v1/notifications/clear",
					"".toRequestBody().toPost()
				)
			}
			
			override fun handleResult(result : TootApiResult?) {
				if(result == null) return  // cancelled.
				
				if(result.jsonObject != null) {
					// ok. api have return empty object.
					for(column in App1.getAppState(activity).column_list) {
						if(column.isNotificationColumn && column.access_info.acct == target_account.acct) {
							column.removeNotifications()
						}
					}
					showToast(activity, false, R.string.delete_succeeded)
				} else {
					showToast(activity, false, result.error)
				}
				
			}
		})
	}
	
	fun deleteOne(
		activity : ActMain, access_info : SavedAccount, notification : TootNotification
	) {
		TootTaskRunner(activity).run(access_info, object : TootTask {
			override fun background(client : TootApiClient) : TootApiResult? {
				return client.request(
					"/api/v1/notifications/dismiss",
					"id=${notification.id}".toRequestBody().toPost()
				)
			}
			
			override fun handleResult(result : TootApiResult?) {
				if(result == null) return // cancelled.
				
				if(result.jsonObject != null) {
					// 成功したら空オブジェクトが返される
					for(column in App1.getAppState(activity).column_list) {
						column.removeNotificationOne(access_info, notification)
					}
					showToast(activity, true, R.string.delete_succeeded)
				} else {
					showToast(activity, true, result.error)
				}
				
			}
		})
	}
	
}
