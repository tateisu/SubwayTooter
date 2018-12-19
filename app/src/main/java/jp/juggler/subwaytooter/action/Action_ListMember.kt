package jp.juggler.subwaytooter.action

import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootRelationShip
import jp.juggler.subwaytooter.api.entity.parseList
import jp.juggler.subwaytooter.dialog.DlgConfirm
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.*
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

object Action_ListMember {
	
	private val reFollowError = Pattern.compile("follow", Pattern.CASE_INSENSITIVE)
	
	interface Callback {
		fun onListMemberUpdated(willRegistered : Boolean, bSuccess : Boolean)
	}
	
	fun add(
		activity : ActMain,
		access_info : SavedAccount,
		list_id : EntityId,
		local_who : TootAccount,
		bFollow : Boolean = false,
		callback : Callback?
	) {
		if(access_info.isMe(local_who)) {
			showToast(activity, false, R.string.it_is_you)
			return
		}
		
		TootTaskRunner(activity).run(access_info, object : TootTask {
			override fun background(client : TootApiClient) : TootApiResult? {
				
				var result : TootApiResult?
				
				val parser = TootParser(activity, access_info)
				
				return if(access_info.isMisskey) {
					// misskeyのリストはフォロー無関係
					
					val params = access_info.putMisskeyApiToken(JSONObject())
						.put("listId", list_id)
						.put("userId", local_who.id)
					
					client.request("/api/users/lists/push", params.toPostRequestBuilder())
					// 204 no content
				} else {
					if(bFollow) {
						val relation : TootRelationShip?
						if(access_info.isLocalUser(local_who)) {
							
							result = client.request(
								"/api/v1/accounts/" + local_who.id + "/follow",
								"".toRequestBody().toPost()
							)
						} else {
							// リモートフォローする
							result = client.request(
								"/api/v1/follows",
								"uri=${local_who.acct.encodePercent()}".toRequestBody().toPost()
							)
							
							val jsonObject = result?.jsonObject ?: return result
							
							val a = parser.account(jsonObject)
								?: return result.setError("parse error.")
							
							// リモートフォローの後にリレーションシップを取得しなおす
							result = client.request("/api/v1/accounts/relationships?id[]=" + a.id)
						}
						val jsonArray = result?.jsonArray ?: return result
						
						val relation_list = parseList(::TootRelationShip, parser, jsonArray)
						relation = if(relation_list.isEmpty()) null else relation_list[0]
						
						if(relation == null) {
							return TootApiResult("parse error.")
						}
						saveUserRelation(access_info, relation)
						
						if(! relation.following) {
							if(relation.requested) {
								return TootApiResult(activity.getString(R.string.cant_add_list_follow_requesting))
							} else {
								// リモートフォローの場合、正常ケースでもここを通る場合がある
								// 何もしてはいけない…
							}
						}
					}
					
					// リストメンバー追加
					
					client.request(
						"/api/v1/lists/$list_id/accounts",
						JSONObject().put(
							"account_ids",
							JSONArray().put(
								local_who.id.toString()
							)
						)
							.toPostRequestBuilder()
					)
				}
			}
			
			override fun handleResult(result : TootApiResult?) {
				var bSuccess = false
				
				try {
					
					if(result == null) return  // cancelled.
					
					if(result.jsonObject != null) {
						for(column in App1.getAppState(activity).column_list) {
							// リストメンバー追加イベントをカラムに伝達
							column.onListMemberUpdated(access_info, list_id, local_who, true)
						}
						// フォロー状態の更新を表示に反映させる
						if(bFollow) activity.showColumnMatchAccount(access_info)
						
						showToast(activity, false, R.string.list_member_added)
						
						bSuccess = true
						
					} else {
						val response = result.response
						val error = result.error
						if(response != null
							&& response.code() == 422
							&& error != null
							&& reFollowError.matcher(error).find()
						) {
							
							if(! bFollow) {
								DlgConfirm.openSimple(
									activity,
									activity.getString(
										R.string.list_retry_with_follow,
										access_info.getFullAcct(local_who)
									)
								) {
									Action_ListMember.add(
										activity,
										access_info,
										list_id,
										local_who,
										bFollow = true,
										callback = callback
									)
								}
							} else {
								android.app.AlertDialog.Builder(activity)
									.setCancelable(true)
									.setMessage(R.string.cant_add_list_follow_requesting)
									.setNeutralButton(R.string.close, null)
									.show()
							}
							return
						}
						
						showToast(activity, true, error)
						
					}
				} finally {
					callback?.onListMemberUpdated(true, bSuccess)
				}
				
			}
		})
	}
	
	fun delete(
		activity : ActMain,
		access_info : SavedAccount,
		list_id : EntityId,
		local_who : TootAccount,
		callback : Callback?
	) {
		TootTaskRunner(activity).run(access_info, object : TootTask {
			override fun background(client : TootApiClient) : TootApiResult? {
				return if(access_info.isMisskey) {
					client.request(
						"/api/users/lists/pull",
						access_info.putMisskeyApiToken()
							.put("listId",list_id.toString())
							.put("userId",local_who.id.toString())
							.toPostRequestBuilder()
					)
				}else{
					client.request(
						"/api/v1/lists/" + list_id + "/accounts?account_ids[]=" + local_who.id,
						Request.Builder().delete()
					)
				}
			}
			
			override fun handleResult(result : TootApiResult?) {
				var bSuccess = false
				
				try {
					
					if(result == null) return  // cancelled.
					
					if(result.jsonObject != null) {
						
						for(column in App1.getAppState(activity).column_list) {
							column.onListMemberUpdated(access_info, list_id, local_who, false)
						}
						
						showToast(activity, false, R.string.delete_succeeded)
						
						bSuccess = true
						
					} else {
						showToast(activity, false, result.error)
					}
				} finally {
					callback?.onListMemberUpdated(false, bSuccess)
				}
				
			}
		})
	}
}
