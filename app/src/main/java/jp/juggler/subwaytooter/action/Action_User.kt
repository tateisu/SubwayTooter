package jp.juggler.subwaytooter.action

import android.app.AlertDialog
import org.json.JSONObject

import java.util.Locale

import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.ActPost
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Column
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootTask
import jp.juggler.subwaytooter.api.TootTaskRunner
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootRelationShip
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.parseItem
import jp.juggler.subwaytooter.dialog.AccountPicker
import jp.juggler.subwaytooter.dialog.ReportForm
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.util.*
import okhttp3.Request
import okhttp3.RequestBody

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
				
				val request_builder = Request.Builder().post(
					if(! bMute)
						RequestBody.create(TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED, "")
					else if(bMuteNotification)
						RequestBody.create(
							TootApiClient.MEDIA_TYPE_JSON,
							"{\"notifications\": true}"
						)
					else
						RequestBody.create(
							TootApiClient.MEDIA_TYPE_JSON,
							"{\"notifications\": false}"
						)
				)
				
				val result = client.request(
					"/api/v1/accounts/" + who.id + if(bMute) "/mute" else "/unmute",
					request_builder
				)
				val jsonObject = result?.jsonObject
				if(jsonObject != null) {
					relation =
						saveUserRelation(access_info, parseItem(::TootRelationShip, jsonObject))
				}
				return result
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
						if(relation.muting) {
							if(column.column_type == Column.TYPE_PROFILE) {
								// プロフページのトゥートはミュートしてても見れる
								continue
							} else {
								column.removeAccountInTimeline(access_info, who.id)
							}
						} else {
							// 「ミュートしたユーザ」カラムからユーザを除去
							column.removeUser(access_info, Column.TYPE_MUTES, who.id)
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
		activity : ActMain, access_info : SavedAccount, who : TootAccount, bBlock : Boolean
	) {
		if(access_info.isMe(who)) {
			showToast(activity, false, R.string.it_is_you)
			return
		}
		
		TootTaskRunner(activity).run(access_info, object : TootTask {
			
			var relation : UserRelation? = null
			override fun background(client : TootApiClient) : TootApiResult? {
				
				val request_builder = Request.Builder().post(
					RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED, "" // 空データ
					)
				)
				
				val result = client.request(
					"/api/v1/accounts/" + who.id + if(bBlock) "/block" else "/unblock",
					request_builder
				)
				val jsonObject = result?.jsonObject
				if(jsonObject != null) {
					relation =
						saveUserRelation(access_info, parseItem(::TootRelationShip, jsonObject))
				}
				
				return result
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
						if(relation.blocking) {
							if(column.column_type == Column.TYPE_PROFILE) {
								// プロフページのトゥートはブロックしてても見れる
								continue
							} else {
								column.removeAccountInTimeline(access_info, who.id)
							}
						} else {
							//「ブロックしたユーザ」カラムのリストから消える
							column.removeUser(access_info, Column.TYPE_BLOCKS, who.id)
						}
					}
					
					showToast(
						activity,
						false,
						if(relation.blocking) R.string.block_succeeded else R.string.unblock_succeeded
					)
					
				} else {
					showToast(activity, false, result.error)
				}
			}
		})
		
	}
	
	// 今のアカウントでユーザプロフを開く
	fun profileLocal(
		activity : ActMain,
		pos : Int,
		access_info : SavedAccount,
		who : TootAccount
	) {
		when {
			access_info.isMisskey -> activity.addColumn(pos, access_info, Column.TYPE_PROFILE, who.id)
			access_info.isPseudo -> profileFromAnotherAccount(activity, pos, access_info, who)
			else -> activity.addColumn(pos, access_info, Column.TYPE_PROFILE, who.id)
		}
	}
	
	// URLからユーザを検索してプロフを開く
	private fun profileFromUrl(
		activity : ActMain,
		pos : Int,
		access_info : SavedAccount,
		who_url : String
	) {
		TootTaskRunner(activity).run(access_info, object : TootTask {
			
			var who_local : TootAccount? = null
			override fun background(client : TootApiClient) : TootApiResult? {
				
				val path = String.format(
					Locale.JAPAN,
					Column.PATH_SEARCH,
					who_url.encodePercent()
				) + "&resolve=1"
				val result = client.request(path)
				val jsonObject = result?.jsonObject
				
				if(jsonObject != null) {
					val tmp = TootParser(activity, access_info).results(jsonObject)
					if(tmp != null && tmp.accounts.isNotEmpty()) {
						who_local = tmp.accounts[0].get()
					} else {
						return TootApiResult(activity.getString(R.string.user_id_conversion_failed))
					}
				}
				
				return result
			}
			
			override fun handleResult(result : TootApiResult?) {
				result ?: return // cancelled.
				
				val wl = who_local
				if(wl != null) {
					activity.addColumn(pos, access_info, Column.TYPE_PROFILE, wl.id)
				} else {
					showToast(activity, true, result.error)
					
					// 仕方ないのでchrome tab で開く
					App1.openCustomTab(activity, who_url)
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
	
	// Intent-FilterからUser URL で指定されたユーザのプロフを開く
	// openChromeTabからUser URL で指定されたユーザのプロフを開く
	fun profile(
		activity : ActMain,
		pos : Int,
		access_info : SavedAccount?,
		url : String,
		host : String,
		user : String
	) {
		// リンクタップした文脈のアカウントが疑似でないなら
		if(access_info != null && ! access_info.isPseudo) {
			if(access_info.host.equals(host, ignoreCase = true)) {
				// 文脈のアカウントと同じインスタンスなら、アカウントIDを探して開いてしまう
				findAccountByName(activity, access_info, host, user) { who : TootAccount? ->
					if(who != null) {
						profileLocal(activity, pos, access_info, who)
					} else {
						// ダメならchromeで開く
						App1.openCustomTab(activity, url)
					}
				}
			} else {
				// 文脈のアカウント異なるインスタンスなら、別アカウントで開く
				profileFromUrl(activity, pos, access_info, url)
			}
			return
		}
		
		// 文脈がない、もしくは疑似アカウントだった
		
		// 疑似ではないアカウントの一覧
		
		if(! SavedAccount.hasRealAccount()) {
			// 疑似アカウントではユーザ情報APIを呼べないし検索APIも使えない
			// chrome tab で開くしかない
			App1.openCustomTab(activity, url)
		} else {
			// アカウントを選択して開く
			AccountPicker.pick(
				activity,
				bAllowPseudo = false,
				bAuto = false,
				message = activity.getString(
					R.string.account_picker_open_user_who,
					AcctColor.getNickname("$user@$host")
				),
				accountListArg = makeAccountListNonPseudo(activity, host)
			) { ai ->
				profileFromUrl(
					activity,
					pos,
					ai,
					url
				)
			}
		}
	}
	
	// 通報フォームを開く
	fun reportForm(
		activity : ActMain, access_info : SavedAccount, who : TootAccount, status : TootStatus
	) {
		ReportForm.showReportForm(activity, access_info, who, status) { dialog, comment, forward ->
			report(activity, access_info, who, status, comment, forward) { _ ->
				// 成功したらダイアログを閉じる
				try {
					dialog.dismiss()
				} catch(ignored : Throwable) {
					// IllegalArgumentException がたまに出る
				}
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
				val sb = (
					"account_id=" + who.id.toString()
						+ "&comment=" + comment.encodePercent()
						+ "&status_ids[]=" + status.id.toString()
						+ "&forward=" + if(forward) "true" else "false"
					)
				
				val request_builder = Request.Builder().post(
					RequestBody.create(
						TootApiClient.MEDIA_TYPE_FORM_URL_ENCODED, sb
					)
				)
				
				return client.request("/api/v1/reports", request_builder)
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
			
			var relation : TootRelationShip? = null
			override fun background(client : TootApiClient) : TootApiResult? {
				
				val content = JSONObject()
				try {
					content.put("reblogs", bShow)
				} catch(ex : Throwable) {
					return TootApiResult(ex.withCaption("json encoding error"))
				}
				
				val request_builder = Request.Builder().post(
					RequestBody.create(
						TootApiClient.MEDIA_TYPE_JSON, content.toString()
					)
				)
				
				val result =
					client.request("/api/v1/accounts/" + who.id + "/follow", request_builder)
				val jsonObject = result?.jsonObject
				if(jsonObject != null) {
					relation = parseItem(::TootRelationShip, jsonObject)
				}
				return result
			}
			
			override fun handleResult(result : TootApiResult?) {
				
				if(result == null) return  // cancelled.
				
				if(relation != null) {
					saveUserRelation(access_info, relation)
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
			activity, ActMain.REQUEST_CODE_POST, account.db_id, initial_text
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
		bConfirmed :Boolean = false
	) {
		if(!bConfirmed){
			
			val name = who.decodeDisplayName(activity)
			AlertDialog.Builder(activity)
				.setMessage( name.intoStringResource(activity,R.string.delete_succeeded_confirm))
				.setNegativeButton(R.string.cancel,null)
				.setPositiveButton(R.string.ok){ _ , _  ->
					deleteSuggestion(activity,access_info,who,bConfirmed=true)
				}
				.show()
			return
		}
		TootTaskRunner(activity).run(access_info, object : TootTask {
			override fun background(client : TootApiClient) : TootApiResult? {
				return client.request("/api/v1/suggestions/${who.id}",Request.Builder().delete())
			}
			
			override fun handleResult(result : TootApiResult?) {
				// cancelled
				result ?: return
				
				// error
				val error = result.error
				if( error != null ){
					showToast(activity,true,result.error)
					return
				}
				
				showToast(activity,false,R.string.delete_succeeded)

				// update suggestion column
				for( column in activity.app_state.column_list){
					column.removeUser(access_info,Column.TYPE_FOLLOW_SUGGESTION,who.id)
				}
			}
		})
	}
}
