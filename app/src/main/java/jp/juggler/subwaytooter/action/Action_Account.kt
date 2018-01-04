package jp.juggler.subwaytooter.action

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.support.v7.app.AlertDialog

import jp.juggler.subwaytooter.ActAccountSetting
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.ActPost
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Column
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootTask
import jp.juggler.subwaytooter.api.TootTaskRunner
import jp.juggler.subwaytooter.dialog.AccountPicker
import jp.juggler.subwaytooter.dialog.DlgTextInput
import jp.juggler.subwaytooter.dialog.LoginForm
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils

object Action_Account {
	private val log = LogCategory("Action_Account")
	
	// アカウントの追加
	fun add(activity : ActMain) {
		
		LoginForm.showLoginForm(activity, null) { dialog, instance, bPseudoAccount, bInputAccessToken ->
			TootTaskRunner(activity, true).run(instance, object : TootTask {
				
				override fun background(client : TootApiClient) : TootApiResult? {
					return if(bPseudoAccount) {
						client.checkInstance()
					} else {
						val client_name = Pref.pref(activity).getString(Pref.KEY_CLIENT_NAME, "")
						client.authorize1(client_name)
					}
				}
				
				override fun handleResult(result : TootApiResult?) {
					if(result == null) return  // cancelled.
					
					val error = result.error
					if(error != null) {
						// エラーはブラウザ用URLかもしれない
						if(error.startsWith("https")) {
							
							if(bInputAccessToken) {
								// アクセストークンの手動入力
								DlgTextInput.show(activity, activity.getString(R.string.access_token), null, object : DlgTextInput.Callback {

									// インスタンス名を入力するダイアログとアクセストークンを入力するダイアログで既定の変数名が衝突する
									@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
									override fun onOK(
										dialog_token : Dialog,
										text : String) {
										activity.checkAccessToken(dialog, dialog_token, instance, text, null)
									}
									
									override fun onEmptyError() {
										Utils.showToast(activity, true, R.string.token_not_specified)
									}
								})
							} else {
								// OAuth認証が必要
								val data = Intent()
								data.data = Uri.parse(error)
								activity.startAccessTokenUpdate(data)
								try {
									dialog.dismiss()
								} catch(ignored : Throwable) {
									// IllegalArgumentException がたまに出る
								}
								
							}
							return
						}
						
						log.e(error)
						
						if(error.contains("SSLHandshakeException")
							&& (Build.VERSION.RELEASE.startsWith("7.0")
							|| Build.VERSION.RELEASE.startsWith("7.1") && ! Build.VERSION.RELEASE.startsWith("7.1.")
							)
							) {
							AlertDialog.Builder(activity)
								.setMessage(error + "\n\n" + activity.getString(R.string.ssl_bug_7_0))
								.setNeutralButton(R.string.close, null)
								.show()
							return
						}
						
						// 他のエラー
						Utils.showToast(activity, true, error)
					} else {
						
						val a = ActionUtils.addPseudoAccount(activity, instance)
						if(a != null) {
							// 疑似アカウントが追加された
							Utils.showToast(activity, false, R.string.server_confirmed)
							val pos = App1.getAppState(activity).column_list.size
							activity.addColumn(pos, a, Column.TYPE_LOCAL)
							
							try {
								dialog.dismiss()
							} catch(ignored : Throwable) {
								// IllegalArgumentException がたまに出る
							}
							
						}
					}
					
				}
			})
		}
		
	}
	
	// アカウント設定
	fun setting(activity : ActMain) {
		AccountPicker.pick(
			activity, true, true, activity.getString(R.string.account_picker_open_setting)) { ai -> ActAccountSetting.open(activity, ai, ActMain.REQUEST_CODE_ACCOUNT_SETTING) }
	}
	
	// アカウントを選んでタイムラインカラムを追加
	fun timeline(
		activity : ActMain, pos : Int, bAllowPseudo : Boolean, type : Int, vararg args : Any
	) {
		AccountPicker.pick(activity, bAllowPseudo, true, activity.getString(R.string.account_picker_add_timeline_of, Column.getColumnTypeName(activity, type))) { ai ->
			when(type) {
				Column.TYPE_PROFILE -> {
					val id = ai.loginAccount?.id
					if( id != null ) activity.addColumn(pos, ai, type, id)
				}
				else -> activity.addColumn(pos, ai, type, *args)
			}
		}
	}
	
	// 投稿画面を開く。初期テキストを指定する
	@JvmOverloads
	fun openPost(
		activity : ActMain, initial_text : String? = activity.quickTootText
	) {
		activity.post_helper.closeAcctPopup()
		
		val db_id = activity.currentPostTargetId
		if(db_id != - 1L) {
			ActPost.open(activity, ActMain.REQUEST_CODE_POST, db_id, initial_text)
		} else {
			AccountPicker.pick(
				activity, false, true, activity.getString(R.string.account_picker_toot)
			) { ai -> ActPost.open(activity, ActMain.REQUEST_CODE_POST, ai.db_id, initial_text) }
		}
	}
}// 投稿画面を開く。簡易入力があれば初期値はそれになる
