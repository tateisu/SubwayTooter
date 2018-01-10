package jp.juggler.subwaytooter.action

import android.net.Uri

import org.json.JSONArray
import org.json.JSONObject

import java.util.regex.Pattern

import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootTask
import jp.juggler.subwaytooter.api.TootTaskRunner
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootRelationShip
import jp.juggler.subwaytooter.api.entity.parseList
import jp.juggler.subwaytooter.dialog.DlgConfirm
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.Utils
import okhttp3.Request
import okhttp3.RequestBody

object Action_ListMember {
	
	private val reFollowError = Pattern.compile("follow", Pattern.CASE_INSENSITIVE)
	
	interface Callback {
		fun onListMemberUpdated(willRegistered : Boolean, bSuccess : Boolean)
	}
	
	fun add(
		activity : ActMain, access_info : SavedAccount, list_id : Long, local_who : TootAccount, bFollow : Boolean, callback : Callback?
	) {
		TootTaskRunner(activity).run(access_info, object : TootTask {
			override fun background(client : TootApiClient) : TootApiResult? {
				
				if(access_info.isMe(local_who)) {
					return TootApiResult(activity.getString(R.string.it_is_you))
				}
				
				var result : TootApiResult?
				
				if(bFollow) {
					val relation : TootRelationShip?
					if(access_info.isLocalUser(local_who)) {
						val request_builder = Request.Builder().post(
							RequestBody.create(
								TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED, "" // 空データ
							))
						
						result = client.request("/api/v1/accounts/" + local_who.id + "/follow", request_builder)
					} else {
						// リモートフォローする
						val request_builder = Request.Builder().post(
							RequestBody.create(
								TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED, "uri=" + Uri.encode(local_who.acct)
							))
						
						result = client.request("/api/v1/follows", request_builder)
						val jsonObject = result?.jsonObject ?:return result
						
						val a = TootAccount.parse(activity, access_info,jsonObject) ?: return TootApiResult("parse error.")
						
						// リモートフォローの後にリレーションシップを取得しなおす
						result = client.request("/api/v1/accounts/relationships?id[]=" + a.id)
					}
					val jsonArray = result?.jsonArray ?: return result
					
					val relation_list = parseList(::TootRelationShip,jsonArray)
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
				
				val content = JSONObject()
				try {
					val account_ids = JSONArray()
					account_ids.put(local_who.id.toString())
					content.put("account_ids", account_ids)
				} catch(ex : Throwable) {
					return TootApiResult(Utils.formatError(ex, "can't encoding json parameter."))
				}
				
				val request_builder = Request.Builder().post(
					RequestBody.create(
						TootApiClient.MEDIA_TYPE_JSON, content.toString()
					))
				
				return client.request("/api/v1/lists/$list_id/accounts", request_builder)
				
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
						
						Utils.showToast(activity, false, R.string.list_member_added)
						
						bSuccess = true
						
					} else {
						val response = result.response
						val error = result.error
						if(response != null
							&& response .code() == 422
							&& error != null && reFollowError.matcher(error ).find()) {
							
							if(! bFollow) {
								DlgConfirm.openSimple(
									activity, activity.getString(R.string.list_retry_with_follow, access_info.getFullAcct(local_who))
								) { Action_ListMember.add(activity, access_info, list_id, local_who, true, callback) }
							} else {
								android.app.AlertDialog.Builder(activity)
									.setCancelable(true)
									.setMessage(R.string.cant_add_list_follow_requesting)
									.setNeutralButton(R.string.close, null)
									.show()
							}
							return
						}
						
						Utils.showToast(activity, true, error)
						
					}
				} finally {
					callback?.onListMemberUpdated(true, bSuccess)
				}
				
			}
		})
	}
	
	fun delete(
		activity : ActMain, access_info : SavedAccount, list_id : Long, local_who : TootAccount, callback : Callback?
	) {
		TootTaskRunner(activity).run(access_info, object : TootTask {
			override fun background(client : TootApiClient) : TootApiResult? {
				return client.request(
					"/api/v1/lists/" + list_id + "/accounts?account_ids[]=" + local_who.id, Request.Builder().delete()
				)
			}
			
			override fun handleResult(result : TootApiResult?) {
				var bSuccess = false
				
				try {
					
					if(result == null) return  // cancelled.
					
					if(result.jsonObject != null) {
						
						for(column in App1.getAppState(activity).column_list) {
							column.onListMemberUpdated(access_info, list_id, local_who, false)
						}
						
						Utils.showToast(activity, false, R.string.delete_succeeded)
						
						bSuccess = true
						
					} else {
						Utils.showToast(activity, false, result.error)
					}
				} finally {
					callback?.onListMemberUpdated(false, bSuccess)
				}
				
			}
		})
	}
}
