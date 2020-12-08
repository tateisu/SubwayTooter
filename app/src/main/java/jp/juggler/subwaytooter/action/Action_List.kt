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
import jp.juggler.util.*
import okhttp3.Request

object Action_List {
	
	fun interface CreateCallback {
		
		fun onCreated(list : TootList)
	}
	
	// リストを作成する
	fun create(
		activity : ActMain,
		access_info : SavedAccount,
		title : String,
		callback : CreateCallback?
	) {
		TootTaskRunner(activity).run(access_info, object : TootTask {
			
			var list : TootList? = null
			override suspend fun background(client : TootApiClient) : TootApiResult? {
				
				val result = if(access_info.isMisskey) {
					client.request(
						"/api/users/lists/create",
						access_info.putMisskeyApiToken().apply {
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
				}
				
				client.publishApiProgress(activity.getString(R.string.parsing_response))
				list = parseItem(::TootList, TootParser(activity, access_info), result?.jsonObject)
				
				return result
			}
			
			override suspend fun handleResult(result : TootApiResult?) {
				if(result == null) return  // cancelled.
				
				val list = this.list
				if(list != null) {
					
					for(column in activity.app_state.column_list) {
						column.onListListUpdated(access_info)
					}
					
					activity.showToast(false, R.string.list_created)
					
					callback?.onCreated(list)
				} else {
					activity.showToast(false, result.error)
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
				activity, activity.getString(R.string.list_delete_confirm, list.title)
			) {
				delete(activity, access_info, list, bConfirmed = true)
			}
			return
		}
		
		TootTaskRunner(activity).run(access_info, object : TootTask {
			override suspend fun background(client : TootApiClient) : TootApiResult? {
				return if(access_info.isMisskey) {
					client.request(
						"/api/users/lists/delete",
						access_info.putMisskeyApiToken().apply {
							put("listId", list.id)
						}
							.toPostRequestBuilder()
					)
					// 204 no content
				} else {
					client.request(
						"/api/v1/lists/{list.id}",
						Request.Builder().delete()
					)
				}
			}
			
			override suspend fun handleResult(result : TootApiResult?) {
				if(result == null) return  // cancelled.
				
				if(result.jsonObject != null) {
					
					for(column in activity.app_state.column_list) {
						column.onListListUpdated(access_info)
					}
					
					activity.showToast(false, R.string.delete_succeeded)
					
				} else {
					activity.showToast(false, result.error)
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
			callback = object : DlgTextInput.Callback {
				override fun onEmptyError() {
					activity.showToast(false, R.string.list_name_empty)
				}
				
				override fun onOK(dialog : Dialog, text : String) {
					
					TootTaskRunner(activity).run(access_info, object : TootTask {
						var list : TootList? = null
						
						override suspend fun background(client : TootApiClient) : TootApiResult? {
							val result = if(access_info.isMisskey) {
								client.request(
									"/api/users/lists/update",
									access_info.putMisskeyApiToken().apply {
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
							}
							
							client.publishApiProgress(activity.getString(R.string.parsing_response))
							list = parseItem(
								::TootList,
								TootParser(activity, access_info),
								result?.jsonObject
							)
							
							return result
						}
						
						override suspend fun handleResult(result : TootApiResult?) {
							if(result == null) return  // cancelled.
							
							val list = this.list
							if(list != null) {
								for(column in activity.app_state.column_list) {
									column.onListNameUpdated(access_info, list)
								}
								dialog.dismissSafe()
							} else {
								activity.showToast(false, result.error)
							}
						}
					})
				}
			}
		)
	}
}
