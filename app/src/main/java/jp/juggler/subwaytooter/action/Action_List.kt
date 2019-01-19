package jp.juggler.subwaytooter.action

import android.app.Dialog
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.TootList
import jp.juggler.subwaytooter.api.entity.parseItem
import jp.juggler.subwaytooter.dialog.DlgConfirm
import jp.juggler.subwaytooter.dialog.DlgTextInput
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.dismissSafe
import jp.juggler.util.showToast
import jp.juggler.util.toPostRequestBuilder
import jp.juggler.util.toPutRequestBuilder
import okhttp3.Request
import org.json.JSONObject

object Action_List {
	
	interface CreateCallback {
		fun onCreated(list : TootList)
	}
	
	// リストを作成する
	fun create(
		activity : ActMain, access_info : SavedAccount, title : String, callback : CreateCallback?
	) {
		TootTaskRunner(activity).run(access_info, object : TootTask {
			
			var list : TootList? = null
			override fun background(client : TootApiClient) : TootApiResult? {
				
				val result = if(access_info.isMisskey) {
					client.request(
						"/api/users/lists/create",
						access_info.putMisskeyApiToken()
							.put("title", title)
							.toPostRequestBuilder()
					)
				} else {
					client.request(
						"/api/v1/lists",
						JSONObject()
							.put("title", title)
							.toPostRequestBuilder()
					)
				}
				
				client.publishApiProgress(activity.getString(R.string.parsing_response))
				list = parseItem(::TootList, TootParser(activity, access_info), result?.jsonObject)
				
				return result
			}
			
			override fun handleResult(result : TootApiResult?) {
				if(result == null) return  // cancelled.
				
				val list = this.list
				if(list != null) {
					
					for(column in activity.app_state.column_list) {
						column.onListListUpdated(access_info)
					}
					
					showToast(activity, false, R.string.list_created)
					
					callback?.onCreated(list)
				} else {
					showToast(activity, false, result.error)
				}
			}
		})
	}
	
	// リストを削除する
	fun delete(
		activity : ActMain,
		access_info : SavedAccount,
		list : TootList,
		bConfirmed : Boolean = false
	) {
		
		if(! bConfirmed) {
			DlgConfirm.openSimple(
				activity
				, activity.getString(R.string.list_delete_confirm, list.title)
			) {
				delete(activity, access_info, list, bConfirmed = true)
			}
			return
		}
		
		TootTaskRunner(activity).run(access_info, object : TootTask {
			override fun background(client : TootApiClient) : TootApiResult? {
				return if(access_info.isMisskey) {
					val params = access_info.putMisskeyApiToken()
						.put("listId", list.id)
					client.request("/api/users/lists/delete", params.toPostRequestBuilder())
					// 204 no content
				} else {
					client.request("/api/v1/lists/{list.id}", Request.Builder().delete())
				}
			}
			
			override fun handleResult(result : TootApiResult?) {
				if(result == null) return  // cancelled.
				
				if(result.jsonObject != null) {
					
					for(column in activity.app_state.column_list) {
						column.onListListUpdated(access_info)
					}
					
					showToast(activity, false, R.string.delete_succeeded)
					
				} else {
					showToast(activity, false, result.error)
				}
			}
		})
	}
	
	fun rename(
		activity : ActMain,
		access_info : SavedAccount,
		item : TootList
	) {
		
		DlgTextInput.show(
			activity,
			activity.getString(R.string.rename),
			item.title,
			object : DlgTextInput.Callback {
				override fun onEmptyError() {
					showToast(activity, false, R.string.list_name_empty)
				}
				
				override fun onOK(dialog : Dialog, text : String) {
					
					TootTaskRunner(activity).run(access_info, object : TootTask {
						var list : TootList? = null
						
						override fun background(client : TootApiClient) : TootApiResult? {
							val result = if(access_info.isMisskey) {
								client.request(
									"/api/users/lists/update",
									access_info.putMisskeyApiToken()
										.put("listId", item.id)
										.put("title", text)
										.toPostRequestBuilder()
								)
							} else {
								client.request(
									"/api/v1/lists/${item.id}",
									JSONObject()
										.put("title", text)
										.toPutRequestBuilder()
								)
							}
							
							client.publishApiProgress(activity.getString(R.string.parsing_response))
							list = parseItem(
								::TootList,
								TootParser(activity, access_info),
								result?.jsonObject
							)
							
							return result
						}
						
						override fun handleResult(result : TootApiResult?) {
							if(result == null) return  // cancelled.
							
							val list = this.list
							if(list != null) {
								for(column in activity.app_state.column_list) {
									column.onListNameUpdated(access_info, list)
								}
								dialog.dismissSafe()
							} else {
								showToast(activity, false, result.error)
							}
						}
					})
				}
			}
		)
	}
}
