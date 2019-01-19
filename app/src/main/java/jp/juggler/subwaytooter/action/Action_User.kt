package jp.juggler.subwaytooter.action

import android.app.AlertDialog
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.AccountPicker
import jp.juggler.subwaytooter.dialog.ReportForm
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.table.UserRelationMisskey
import jp.juggler.subwaytooter.util.TootApiResultCallback
import jp.juggler.util.*
import okhttp3.Request
import org.json.JSONObject

object Action_User {
	
	// ユーザをミュート/ミュート解除する
	fun mute(
		activity : ActMain,
		access_info : SavedAccount,
		who : TootAccount,
		bMute : Boolean = true,
		bMuteNotification : Boolean = false
	) {
		
		if(access_info.isMe(who)) {
			showToast(activity, false, R.string.it_is_you)
			return
		}
		
		TootTaskRunner(activity).run(access_info, object : TootTask {
			
			var relation : UserRelation? = null
			override fun background(client : TootApiClient) : TootApiResult? {
				if(access_info.isMisskey) {
					val params = access_info.putMisskeyApiToken(JSONObject())
						.put("userId", who.id.toString())
					
					val result = client.request(
						when(bMute) {
							true -> "/api/mute/create"
							else -> "/api/mute/delete"
						}, params.toPostRequestBuilder()
					)
					if(result?.jsonObject != null) {
						// 204 no content
						
						// update user relation
						val ur = UserRelation.load(access_info.db_id, who.id)
						ur.muting = bMute
						saveUserRelationMisskey(
							access_info,
							who.id,
							TootParser(activity, access_info)
						)
						this.relation = ur
					}
					return result
				} else {
					val result = client.request(
						"/api/v1/accounts/${who.id}/${if(bMute) "mute" else "unmute"}",
						when {
							! bMute -> "".toRequestBody()
							else ->
								JSONObject()
									.put("notifications", bMuteNotification)
									.toRequestBody()
						}.toPost()
					)
					val jsonObject = result?.jsonObject
					if(jsonObject != null) {
						relation =
							saveUserRelation(
								access_info,
								parseItem(
									::TootRelationShip,
									TootParser(activity, access_info),
									jsonObject
								)
							)
					}
					return result
					
				}
			}
			
			override fun handleResult(result : TootApiResult?) {
				if(result == null) return  // cancelled.
				
				val relation = this.relation
				if(relation != null) {
					// 未確認だが、自分をミュートしようとするとリクエストは成功するがレスポンス中のmutingはfalseになるはず
					if(bMute && ! relation.muting) {
						showToast(activity, false, R.string.not_muted)
						return
					}
					
					for(column in App1.getAppState(activity).column_list) {
						if(column.access_info.acct != access_info.acct) continue
						when {
							! relation.muting -> {
								if(column.column_type == Column.TYPE_MUTES) {
									// ミュート解除したら「ミュートしたユーザ」カラムから消える
									column.removeUser(access_info, Column.TYPE_MUTES, who.id)
								} else {
									// 他のカラムではフォローアイコンの表示更新が走る
									column.updateFollowIcons(access_info)
								}
								
							}
							
							column.column_type == Column.TYPE_PROFILE && column.profile_id == who.id -> {
								// 該当ユーザのプロフページのトゥートはミュートしてても見れる
								// しかしフォローアイコンの表示更新は必要
								column.updateFollowIcons(access_info)
							}
							
							else -> {
								// ミュートしたユーザの情報はTLから消える
								column.removeAccountInTimeline(access_info, who.id)
							}
						}
					}
					
					showToast(
						activity,
						false,
						if(relation.muting) R.string.mute_succeeded else R.string.unmute_succeeded
					)
					
				} else {
					showToast(activity, false, result.error)
				}
			}
		})
		
	}
	
	// ユーザをブロック/ブロック解除する
	fun block(
		activity : ActMain,
		access_info : SavedAccount,
		who : TootAccount,
		bBlock : Boolean
	) {
		if(access_info.isMe(who)) {
			showToast(activity, false, R.string.it_is_you)
			return
		}
		
		TootTaskRunner(activity).run(access_info, object : TootTask {
			
			var relation : UserRelation? = null
			override fun background(client : TootApiClient) : TootApiResult? {
				
				if(access_info.isMisskey) {
					val params = access_info.putMisskeyApiToken()
						.put("userId", who.id)
					val result = client.request(
						if(bBlock)
							"/api/blocking/create"
						else
							"/api/blocking/delete",
						params.toPostRequestBuilder()
					)
					
					fun saveBlock(v : Boolean) {
						val ur = UserRelation.load(access_info.db_id, who.id)
						ur.blocking = v
						UserRelationMisskey.save1(
							System.currentTimeMillis(),
							access_info.db_id,
							who.id.toString(),
							ur
						)
						relation = ur
					}
					
					val error = result?.error
					when {
						// cancelled.
						result == null -> {
						}
						
						// success
						error == null -> saveBlock(bBlock)
						
						// already
						error.contains("already blocking") -> saveBlock(bBlock)
						error.contains("already not blocking") -> saveBlock(bBlock)
						
						// else something error
					}
					
					return result
				} else {
					
					val result = client.request(
						"/api/v1/accounts/${who.id}/${if(bBlock) "block" else "unblock"}",
						"".toRequestBody().toPost()
					)
					val jsonObject = result?.jsonObject
					if(jsonObject != null) {
						relation = saveUserRelation(
							access_info,
							parseItem(
								::TootRelationShip,
								TootParser(activity, access_info),
								jsonObject
							)
						)
					}
					
					return result
				}
			}
			
			override fun handleResult(result : TootApiResult?) {
				
				if(result == null) return  // cancelled.
				
				val relation = this.relation
				if(relation != null) {
					
					// 自分をブロックしようとすると、blocking==falseで帰ってくる
					if(bBlock && ! relation.blocking) {
						showToast(activity, false, R.string.not_blocked)
						return
					}
					
					for(column in App1.getAppState(activity).column_list) {
						
						if(column.access_info.acct != access_info.acct) continue
						
						when {
							
							! relation.blocking -> {
								
								if(column.column_type == Column.TYPE_BLOCKS) {
									// ブロック解除したら「ブロックしたユーザ」カラムのリストから消える
									column.removeUser(
										access_info,
										Column.TYPE_BLOCKS,
										who.id
									)
								} else {
									// 他のカラムではフォローアイコンの更新を行う
									column.updateFollowIcons(access_info)
								}
							}
							
							access_info.isMisskey -> {
								// Misskeyのブロックはフォロー解除とフォロー拒否だけなので
								// カラム中の投稿を消すなどの効果はない
								// しかしカラム中のフォローアイコン表示の更新は必要
								column.updateFollowIcons(access_info)
							}
							
							// 該当ユーザのプロフカラムではブロックしててもトゥートを見れる
							// しかしカラム中のフォローアイコン表示の更新は必要
							column.column_type == Column.TYPE_PROFILE && who.id == column.profile_id -> {
								column.updateFollowIcons(access_info)
							}
							
							// MastodonではブロックしたらTLからそのアカウントの投稿が消える
							else -> column.removeAccountInTimeline(access_info, who.id)
						}
					}
					
					showToast(
						activity,
						false,
						if(relation.blocking)
							R.string.block_succeeded
						else
							R.string.unblock_succeeded
					)
					
				} else {
					showToast(activity, false, result.error)
				}
			}
		})
		
	}
	
	//////////////////////////////////////////////////////////////////////////////////////
	
	// URLからユーザを検索してプロフを開く
	private fun profileFromUrl(
		activity : ActMain,
		pos : Int,
		access_info : SavedAccount,
		who_url : String
	) {
		TootTaskRunner(activity).run(access_info, object : TootTask {
			
			var who : TootAccount? = null
			
			override fun background(client : TootApiClient) : TootApiResult? {
				val(result,ar) = client.syncAccountByUrl(access_info, who_url)
				who = ar?.get()
				return result
			}
			
			override fun handleResult(result : TootApiResult?) {
				result ?: return // cancelled.
				
				when(val who = this.who) {
					null -> {
						showToast(activity, true, result.error)
						// 仕方ないのでchrome tab で開く
						App1.openCustomTab(activity, who_url)
					}
					
					else -> activity.addColumn(pos, access_info, Column.TYPE_PROFILE, who.id)
				}
			}
		})
	}
	
	
	
	// アカウントを選んでユーザプロフを開く
	fun profileFromAnotherAccount(
		activity : ActMain,
		pos : Int,
		access_info : SavedAccount,
		who : TootAccount?
	) {
		if(who?.url == null) return
		val who_host = who.host
		
		AccountPicker.pick(
			activity,
			bAllowPseudo = false,
			bAuto = false,
			message = activity.getString(
				R.string.account_picker_open_user_who,
				AcctColor.getNickname(who.acct)
			),
			accountListArg = makeAccountListNonPseudo(activity, who_host)
		) { ai ->
			if(ai.host.equals(access_info.host, ignoreCase = true)) {
				activity.addColumn(pos, ai, Column.TYPE_PROFILE, who.id)
			} else {
				profileFromUrl(activity, pos, ai, who.url)
			}
		}
	}
	
	// 今のアカウントでユーザプロフを開く
	fun profileLocal(
		activity : ActMain,
		pos : Int,
		access_info : SavedAccount,
		who : TootAccount
	) {
		when {
			access_info.isNA -> profileFromAnotherAccount(activity, pos, access_info, who)
			else -> activity.addColumn(pos, access_info, Column.TYPE_PROFILE, who.id)
		}
	}
	
	// User URL で指定されたユーザのプロフを開く
	// Intent-Filter や openChromeTabから 呼ばれる
	fun profile(
		activity : ActMain,
		pos : Int,
		access_info : SavedAccount?,
		url : String,
		host : String,
		user : String
	) {
		if(access_info?.isPseudo == false) {
			// 文脈のアカウントがあり、疑似アカウントではない
			
			if(access_info.host.equals(host, ignoreCase = true)) {
				
				// 文脈のアカウントと同じインスタンスなら、アカウントIDを探して開いてしまう
				
				val acct = "$user@$host"
				TootTaskRunner(activity).run(access_info, object : TootTask {
					
					var who : TootAccount? = null
					
					override fun background(client : TootApiClient) : TootApiResult? {
						val(result,ar) = client.syncAccountByAcct(access_info, acct)
						who = ar?.get()
						return result
					}
					
					override fun handleResult(result : TootApiResult?) {
						result ?: return // cancelled
						when(val who = this.who) {
							null -> {
								// ダメならchromeで開く
								App1.openCustomTab(activity, url)
							}
							
							else -> profileLocal(activity, pos, access_info, who)
						}
					}
				})
			} else {
				// 文脈のアカウントと異なるインスタンスなら、別アカウントで開く
				profileFromUrl(activity, pos, access_info, url)
			}
			return
		}
		
		// 文脈がない、もしくは疑似アカウントだった
		// 疑似アカウントでは検索APIを使えないため、IDが分からない
		
		if(! SavedAccount.hasRealAccount()) {
			// 疑似アカウントしか登録されていない
			// chrome tab で開く
			App1.openCustomTab(activity, url)
		} else {
			// 疑似ではないアカウントの一覧から選択して開く
			AccountPicker.pick(
				activity,
				bAllowPseudo = false,
				bAuto = false,
				message = activity.getString(
					R.string.account_picker_open_user_who,
					AcctColor.getNickname("$user@$host")
				),
				accountListArg = makeAccountListNonPseudo(activity, host)
			) { profileFromUrl(activity, pos, it, url) }
		}
	}
	
	//////////////////////////////////////////////////////////////////////////////////////
	
	// 通報フォームを開く
	fun reportForm(
		activity : ActMain, access_info : SavedAccount, who : TootAccount, status : TootStatus
	) {
		ReportForm.showReportForm(activity, access_info, who, status) { dialog, comment, forward ->
			report(activity, access_info, who, status, comment, forward) {
				// 成功したらダイアログを閉じる
				dialog.dismissSafe()
			}
		}
	}
	
	// 通報する
	private fun report(
		activity : ActMain,
		access_info : SavedAccount,
		who : TootAccount,
		status : TootStatus,
		comment : String,
		forward : Boolean,
		onReportComplete : TootApiResultCallback
	) {
		if(access_info.isMe(who)) {
			showToast(activity, false, R.string.it_is_you)
			return
		}
		
		TootTaskRunner(activity).run(access_info, object : TootTask {
			override fun background(client : TootApiClient) : TootApiResult? {
				return client.request(
					"/api/v1/reports",
					("account_id=" + who.id.toString() +
						"&comment=" + comment.encodePercent() +
						"&status_ids[]=" + status.id.toString() +
						"&forward=" + if(forward) "true" else "false"
						).toRequestBody().toPost()
				)
			}
			
			override fun handleResult(result : TootApiResult?) {
				result ?: return // cancelled.
				
				if(result.jsonObject != null) {
					onReportComplete(result)
					
					showToast(activity, false, R.string.report_completed)
				} else {
					showToast(activity, true, result.error)
				}
			}
		})
	}
	
	// show/hide boosts from (following) user
	fun showBoosts(
		activity : ActMain, access_info : SavedAccount, who : TootAccount, bShow : Boolean
	) {
		if(access_info.isMe(who)) {
			showToast(activity, false, R.string.it_is_you)
			return
		}
		
		TootTaskRunner(activity).run(access_info, object : TootTask {
			
			var relation : UserRelation? = null
			override fun background(client : TootApiClient) : TootApiResult? {
				
				val content = JSONObject()
				try {
					content.put("reblogs", bShow)
				} catch(ex : Throwable) {
					return TootApiResult(ex.withCaption("json encoding error"))
				}
				
				val request_builder = content.toPostRequestBuilder()
				
				val result =
					client.request("/api/v1/accounts/" + who.id + "/follow", request_builder)
				val jsonObject = result?.jsonObject
				if(jsonObject != null) {
					relation =
						saveUserRelation(
							access_info,
							parseItem(
								::TootRelationShip,
								TootParser(activity, access_info),
								jsonObject
							)
						)
				}
				return result
			}
			
			override fun handleResult(result : TootApiResult?) {
				
				if(result == null) return  // cancelled.
				
				if(relation != null) {
					showToast(activity, true, R.string.operation_succeeded)
				} else {
					showToast(activity, true, result.error)
				}
			}
		})
	}
	
	// メンションを含むトゥートを作る
	private fun mention(
		activity : ActMain, account : SavedAccount, initial_text : String
	) {
		ActPost.open(
			activity,
			ActMain.REQUEST_CODE_POST,
			account.db_id,
			initial_text = initial_text
		)
	}
	
	// メンションを含むトゥートを作る
	fun mention(
		activity : ActMain, account : SavedAccount, who : TootAccount
	) {
		mention(activity, account, "@" + account.getFullAcct(who) + " ")
	}
	
	// メンションを含むトゥートを作る
	fun mentionFromAnotherAccount(
		activity : ActMain, access_info : SavedAccount, who : TootAccount?
	) {
		if(who == null) return
		val who_host = who.host
		
		val initial_text = "@" + access_info.getFullAcct(who) + " "
		AccountPicker.pick(
			activity,
			bAllowPseudo = false,
			bAuto = false,
			message = activity.getString(R.string.account_picker_toot),
			accountListArg = makeAccountListNonPseudo(activity, who_host)
		) { ai ->
			mention(activity, ai, initial_text)
		}
	}
	
	fun deleteSuggestion(
		activity : ActMain,
		access_info : SavedAccount,
		who : TootAccount,
		bConfirmed : Boolean = false
	) {
		if(! bConfirmed) {
			
			val name = who.decodeDisplayName(activity)
			AlertDialog.Builder(activity)
				.setMessage(name.intoStringResource(activity, R.string.delete_succeeded_confirm))
				.setNegativeButton(R.string.cancel, null)
				.setPositiveButton(R.string.ok) { _, _ ->
					deleteSuggestion(activity, access_info, who, bConfirmed = true)
				}
				.show()
			return
		}
		TootTaskRunner(activity).run(access_info, object : TootTask {
			override fun background(client : TootApiClient) : TootApiResult? {
				return client.request("/api/v1/suggestions/${who.id}", Request.Builder().delete())
			}
			
			override fun handleResult(result : TootApiResult?) {
				// cancelled
				result ?: return
				
				// error
				val error = result.error
				if(error != null) {
					showToast(activity, true, result.error)
					return
				}
				
				showToast(activity, false, R.string.delete_succeeded)
				
				// update suggestion column
				for(column in activity.app_state.column_list) {
					column.removeUser(access_info, Column.TYPE_FOLLOW_SUGGESTION, who.id)
				}
			}
		})
	}
}
