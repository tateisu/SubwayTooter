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
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.dialog.AccountPicker
import jp.juggler.subwaytooter.dialog.DlgTextInput
import jp.juggler.subwaytooter.dialog.LoginForm
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.showToast
import org.json.JSONObject

object Action_Account {
	
	@Suppress("unused")
	private val log = LogCategory("Action_Account")
	
	// アカウントの追加
	fun add(activity : ActMain) {
		
		LoginForm.showLoginForm(
			activity,
			null
		) { dialog, instance, bPseudoAccount, bInputAccessToken ->
			TootTaskRunner(activity).run(instance, object : TootTask {
				
				override fun background(client : TootApiClient) : TootApiResult? {
					return if(bPseudoAccount || bInputAccessToken) {
						client.getInstanceInformation()
					} else {
						client.authentication1(Pref.spClientName(activity))
					}
				}
				
				override fun handleResult(result : TootApiResult?) {
					if(result == null) return  // cancelled.
					
					val data = result.data
					if(data is String) {
						// ブラウザ用URLが生成された
						val intent = Intent()
						intent.data = Uri.parse(data)
						activity.startAccessTokenUpdate(intent)
						try {
							dialog.dismiss()
						} catch(ignored : Throwable) {
							// IllegalArgumentException がたまに出る
						}
					} else if(data is JSONObject) {
						
						// インスタンスを確認できた
						if(bInputAccessToken) {
							
							// アクセストークンの手動入力
							DlgTextInput.show(
								activity,
								activity.getString(R.string.access_token),
								null,
								object : DlgTextInput.Callback {
									
									@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
									override fun onOK(
										dialog_token : Dialog,
										text : String
									) {
										
										// dialog引数が二つあるのに注意
										activity.checkAccessToken(
											dialog,
											dialog_token,
											instance,
											text,
											null
										)
										
									}
									
									override fun onEmptyError() {
										showToast(activity, true, R.string.token_not_specified)
									}
								}
							)
							
						} else {
							// 疑似アカウントを追加
							val a = addPseudoAccount(activity, instance, data.optBoolean("isMisskey",false))
							if(a != null) {
								showToast(activity, false, R.string.server_confirmed)
								val pos = App1.getAppState(activity).column_list.size
								activity.addColumn(pos, a, Column.TYPE_LOCAL)
								
								try {
									dialog.dismiss()
								} catch(ignored : Throwable) {
									// IllegalArgumentException がたまに出る
								}
								
							}
						}
					} else {
						val error = result.error ?: "(no information)"
						if(error.contains("SSLHandshakeException")
							&& (Build.VERSION.RELEASE.startsWith("7.0")
								|| Build.VERSION.RELEASE.startsWith("7.1") && ! Build.VERSION.RELEASE.startsWith(
								"7.1."
							)
								)
						) {
							AlertDialog.Builder(activity)
								.setMessage(error + "\n\n" + activity.getString(R.string.ssl_bug_7_0))
								.setNeutralButton(R.string.close, null)
								.show()
						} else {
							showToast(activity, true, error)
						}
					}
				}
			})
		}
		
	}
	
	// アカウント設定
	fun setting(activity : ActMain) {
		AccountPicker.pick(
			activity,
			bAllowPseudo = true,
			bAuto = true,
			message = activity.getString(R.string.account_picker_open_setting)
		) { ai -> ActAccountSetting.open(activity, ai, ActMain.REQUEST_CODE_ACCOUNT_SETTING) }
	}
	
	// アカウントを選んでタイムラインカラムを追加
	fun timeline(
		activity : ActMain, pos : Int, bAllowPseudo : Boolean, type : Int, vararg args : Any
	) {
		AccountPicker.pick(
			activity,
			bAllowPseudo = bAllowPseudo,
			bAuto = true,
			message = activity.getString(
				R.string.account_picker_add_timeline_of,
				Column.getColumnTypeName(activity, type)
			)
		) { ai ->
			when(type) {
				Column.TYPE_PROFILE -> {
					val id = ai.loginAccount?.id
					if(id != null) activity.addColumn(pos, ai, type, id)
				}
				
				else -> activity.addColumn(pos, ai, type, *args)
			}
		}
	}
	
	// 投稿画面を開く。初期テキストを指定する
	fun openPost(
		activity : ActMain,
		initial_text : String? = activity.quickTootText
	) {
		activity.post_helper.closeAcctPopup()
		
		val db_id = activity.currentPostTargetId
		if(db_id != - 1L) {
			ActPost.open(activity, ActMain.REQUEST_CODE_POST, db_id, initial_text)
		} else {
			AccountPicker.pick(
				activity,
				bAllowPseudo = false,
				bAuto = true,
				message = activity.getString(R.string.account_picker_toot)
			) { ai -> ActPost.open(activity, ActMain.REQUEST_CODE_POST, ai.db_id, initial_text) }
		}
	}
}
