package jp.juggler.subwaytooter.action

import android.net.Uri
import android.support.v7.app.AlertDialog

import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootTask
import jp.juggler.subwaytooter.api.TootTaskRunner
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootRelationShip
import jp.juggler.subwaytooter.dialog.AccountPicker
import jp.juggler.subwaytooter.dialog.DlgConfirm
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.util.Utils
import okhttp3.Request
import okhttp3.RequestBody

object Action_Follow {
	
	fun follow(
		activity : ActMain,
		pos : Int,
		access_info : SavedAccount,
		who : TootAccount,
		bFollow : Boolean,
		callback : RelationChangedCallback?
	) {
		follow(activity, pos, access_info, who, bFollow, false, false, callback)
	}
	
	private fun follow(
		activity : ActMain,
		pos : Int,
		access_info : SavedAccount,
		who : TootAccount,
		bFollow : Boolean,
		bConfirmMoved : Boolean,
		bConfirmed : Boolean,
		callback : RelationChangedCallback?
	) {
		if(access_info.isMe(who)) {
			Utils.showToast(activity, false, R.string.it_is_you)
			return
		}
		
		if(! bConfirmMoved && bFollow && who.moved != null) {
			AlertDialog.Builder(activity)
				.setMessage(activity.getString(R.string.jump_moved_user, access_info.getFullAcct(who), access_info.getFullAcct(who.moved)
				))
				.setPositiveButton(R.string.ok) { _, _ -> Action_User.profileFromAnotherAccount(activity, pos, access_info, who.moved) }
				.setNeutralButton(R.string.ignore_suggestion) { _, _ -> follow(activity, pos, access_info, who, true, true, false, callback) }
				.setNegativeButton(android.R.string.cancel, null)
				.show()
			return
		}
		
		if(! bConfirmed) {
			if(bFollow && who.locked) {
				DlgConfirm.open(
					activity,
					activity.getString(R.string.confirm_follow_request_who_from, who.decoded_display_name, AcctColor.getNickname(access_info.acct))
					, object : DlgConfirm.Callback {
					
					override fun onOK() {
						follow(activity, pos, access_info, who, bFollow, bConfirmMoved, true, callback)
					}
					
					override var isConfirmEnabled : Boolean
						get() = access_info.confirm_follow_locked
						set(value) {
							access_info.confirm_follow_locked = value
							access_info.saveSetting()
							activity.reloadAccountSetting(access_info)
						}
				})
				return
			} else if(bFollow) {
				val msg = activity.getString(R.string.confirm_follow_who_from, who.decoded_display_name, AcctColor.getNickname(access_info.acct))
				
				DlgConfirm.open(
					activity,
					msg,
					object : DlgConfirm.Callback {
						
						override fun onOK() {
							follow(activity, pos, access_info, who, bFollow, bConfirmMoved, true, callback)
						}
						
						override var isConfirmEnabled : Boolean
							get() = access_info.confirm_follow
							set(value) {
								access_info.confirm_follow = value
								access_info.saveSetting()
								activity.reloadAccountSetting(access_info)
							}
					})
				return
			} else {
				DlgConfirm.open(
					activity,
					activity.getString(R.string.confirm_unfollow_who_from, who.decoded_display_name, AcctColor.getNickname(access_info.acct)),
					object : DlgConfirm.Callback {
						
						override fun onOK() {
							follow(activity, pos, access_info, who, bFollow, bConfirmMoved, true, callback)
						}
						
						override var isConfirmEnabled : Boolean
							get() = access_info.confirm_unfollow
							set(value) {
								access_info.confirm_unfollow = value
								access_info.saveSetting()
								activity.reloadAccountSetting(access_info)
							}
					})
				return
			}
		}
		
		TootTaskRunner(activity, false).run(access_info, object : TootTask {
			
			internal var relation : UserRelation? = null
			override fun background(client : TootApiClient) : TootApiResult? {
				var result : TootApiResult?
				
				if(bFollow and who.acct.contains("@")) {
					
					// リモートフォローする
					val request_builder = Request.Builder().post(
						RequestBody.create(
							TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED, "uri=" + Uri.encode(who.acct)
						))
					
					result = client.request("/api/v1/follows", request_builder)
					val jsonObject = result?.jsonObject
					if(jsonObject != null) {
						val remote_who = TootAccount.parse(activity, access_info, jsonObject)
						if(remote_who != null) {
							val rr = ActionUtils.loadRelation1(client, access_info, remote_who.id)
							result = rr.result
							relation = rr.relation
						}
					}
					
				} else {
					
					// ローカルでフォロー/アンフォローする
					
					val request_builder = Request.Builder().post(
						RequestBody.create(
							TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED, "" // 空データ
						))
					result = client.request("/api/v1/accounts/" + who.id
						+ if(bFollow) "/follow" else "/unfollow", request_builder)
					val jsonObject = result?.jsonObject
					if(jsonObject != null) {
						relation = ActionUtils.saveUserRelation(access_info, TootRelationShip.parse(jsonObject))
					}
				}
				
				return result
			}
			
			override fun handleResult(result : TootApiResult?) {
				
				if(result == null) return  // cancelled.
				
				if(relation != null) {
					
					activity.showColumnMatchAccount(access_info)
					
					if(bFollow && relation !!.getRequested(who)) {
						// 鍵付きアカウントにフォローリクエストを申請した状態
						Utils.showToast(activity, false, R.string.follow_requested)
					} else if(! bFollow && relation !!.getRequested(who)) {
						Utils.showToast(activity, false, R.string.follow_request_cant_remove_by_sender)
					} else {
						// ローカル操作成功、もしくはリモートフォロー成功
						if(callback != null) callback()
					}
					
				} else if(bFollow && who.locked && result.response != null && result.response !!.code() == 422) {
					Utils.showToast(activity, false, R.string.cant_follow_locked_user)
				} else {
					Utils.showToast(activity, false, result.error)
				}
				
			}
		})
	}
	
	// acct で指定したユーザをリモートフォローする
	private fun followRemote(
		activity : ActMain, access_info : SavedAccount, acct : String, locked : Boolean, callback : RelationChangedCallback?
	) {
		followRemote(activity, access_info, acct, locked, false, callback)
	}
	
	// acct で指定したユーザをリモートフォローする
	private fun followRemote(
		activity : ActMain, access_info : SavedAccount, acct : String, locked : Boolean, bConfirmed : Boolean, callback : RelationChangedCallback?
	) {
		if(access_info.isMe(acct)) {
			Utils.showToast(activity, false, R.string.it_is_you)
			return
		}
		
		if(! bConfirmed) {
			if(locked) {
				DlgConfirm.open(activity, activity.getString(R.string.confirm_follow_request_who_from, AcctColor.getNickname(acct), AcctColor.getNickname(access_info.acct)), object : DlgConfirm.Callback {
					override fun onOK() {
						followRemote(activity, access_info, acct, locked, true, callback)
					}

					override var isConfirmEnabled : Boolean
						get() = access_info.confirm_follow_locked
						set(value) {
							access_info.confirm_follow_locked = value
							access_info.saveSetting()
							activity.reloadAccountSetting(access_info)
						}
				})
				return
			} else {
				DlgConfirm.open(activity, activity.getString(R.string.confirm_follow_who_from, AcctColor.getNickname(acct), AcctColor.getNickname(access_info.acct)), object : DlgConfirm.Callback {
					
					override fun onOK() {
						followRemote(activity, access_info, acct, locked, true, callback)
					}
					
					override var isConfirmEnabled : Boolean
						get() = access_info.confirm_follow
						set(value) {
							access_info.confirm_follow = value
							access_info.saveSetting()
							activity.reloadAccountSetting(access_info)
						}
				})
				return
			}
		}
		
		TootTaskRunner(activity, false).run(access_info, object : TootTask {
			
			internal var remote_who : TootAccount? = null
			internal var relation : UserRelation? = null
			override fun background(client : TootApiClient) : TootApiResult? {
				
				val request_builder = Request.Builder().post(
					RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED, "uri=" + Uri.encode(acct)
					))
				
				var result = client.request("/api/v1/follows", request_builder)
				val jsonObject = result?.jsonObject
				if(jsonObject != null) {
					val remote_who = TootAccount.parse(activity, access_info,jsonObject)
					if(remote_who != null) {
						this.remote_who = remote_who
						val rr = ActionUtils.loadRelation1(client, access_info, remote_who .id)
						result = rr.result
						relation = rr.relation
					}
				}
				
				return result
			}
			
			override fun handleResult(result : TootApiResult?) {
				
				if(result == null) return  // cancelled.
				
				if(relation != null) {
					
					activity.showColumnMatchAccount(access_info)
					
					if(callback != null) callback()
					
				} else if(locked && result.response != null && result.response !!.code() == 422) {
					Utils.showToast(activity, false, R.string.cant_follow_locked_user)
				} else {
					Utils.showToast(activity, false, result.error)
				}
				
			}
		})
	}
	
	fun followFromAnotherAccount(
		activity : ActMain, pos : Int, access_info : SavedAccount, account : TootAccount?
	) {
		followFromAnotherAccount(activity, pos, access_info, account, false)
	}
	
	private fun followFromAnotherAccount(
		activity : ActMain, pos : Int, access_info : SavedAccount, account : TootAccount?, bConfirmMoved : Boolean
	) {
		if(account == null) return
		
		if(! bConfirmMoved && account.moved != null) {
			AlertDialog.Builder(activity)
				.setMessage(activity.getString(R.string.jump_moved_user, access_info.getFullAcct(account), access_info.getFullAcct(account.moved)
				))
				.setPositiveButton(R.string.ok) { _, _ -> Action_User.profileFromAnotherAccount(activity, pos, access_info, account.moved) }
				.setNeutralButton(R.string.ignore_suggestion) { _, _ -> followFromAnotherAccount(activity, pos, access_info, account, true) }
				.setNegativeButton(android.R.string.cancel, null)
				.show()
			return
		}
		
		val who_host = access_info.getAccountHost(account)
		val who_acct = access_info.getFullAcct(account)
		AccountPicker.pick(activity, false, false, activity.getString(R.string.account_picker_follow), ActionUtils.makeAccountListNonPseudo(activity, who_host)) { ai -> followRemote(activity, ai, who_acct, account.locked, activity.follow_complete_callback) }
	}
	
	fun authorizeFollowRequest(
		activity : ActMain, access_info : SavedAccount, who : TootAccount, bAllow : Boolean
	) {
		if(access_info.isMe(who)) {
			Utils.showToast(activity, false, R.string.it_is_you)
			return
		}
		
		TootTaskRunner(activity, true).run(access_info, object : TootTask {
			override fun background(client : TootApiClient) : TootApiResult? {
				val request_builder = Request.Builder().post(
					RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED, "" // 空データ
					))
				
				return client.request(
					"/api/v1/follow_requests/" + who.id + if(bAllow) "/authorize" else "/reject", request_builder)
			}
			
			override fun handleResult(result : TootApiResult?) {
				if(result == null) return  // cancelled.
				
				val jsonObject = result.jsonObject
				if(jsonObject != null) {
					for(column in App1.getAppState(activity).column_list) {
						column.removeFollowRequest(access_info, who.id)
					}
					
					Utils.showToast(activity, false, if(bAllow) R.string.follow_request_authorized else R.string.follow_request_rejected, who.decoded_display_name)
				} else {
					Utils.showToast(activity, false, result.error)
				}
			}
		})
	}
}
