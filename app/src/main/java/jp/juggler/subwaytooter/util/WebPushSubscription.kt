package jp.juggler.subwaytooter.util

import android.content.Context
import jp.juggler.subwaytooter.PollingWorker
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.table.SavedAccount
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

class WebPushSubscription(val context : Context,val verbose: Boolean = false) {
	
	var enabled : Boolean = false
	var subscribed : Boolean = false
	var flags = 0
	
	val log : String
		get() = sb.toString()
	
	private val sb = StringBuilder()
	
	private fun addLog(s : String) {
		if(sb.isNotEmpty()) sb.append('\n')
		sb.append(s)
	}
	
	private fun updateSubscription_sub(client : TootApiClient, account : SavedAccount) : TootApiResult? {
		try {
			
			if(account.notification_boost) flags += 1
			if(account.notification_favourite) flags += 2
			if(account.notification_follow) flags += 4
			if(account.notification_mention) flags += 8
			
			// 疑似アカウントの確認
			if(account.isPseudo) {
				return TootApiResult(error = context.getString(R.string.pseudo_account_not_supported))
			}
			
			// インスタンスバージョンの確認
			var r = client.getInstanceInformation2()
			val ti = r?.data as? TootInstance ?: return r
			if(! ti.isEnoughVersion(TootInstance.VERSION_2_4)) {
				return TootApiResult(error = context.getString(R.string.instance_does_not_support_push_api,ti.version))
			}
			
			// FCMのデバイスIDを取得
			val device_id = PollingWorker.getDeviceId(context)
				?: return TootApiResult(error = context.getString(R.string.missing_fcm_device_id))
			
			// インストールIDを取得
			val install_id = PollingWorker.prepareInstallId(context)
				?: return TootApiResult(error = context.getString(R.string.missing_install_id))
			
			// アクセストークンの優先権を取得
			r = client.http(
				Request.Builder()
					.url("${PollingWorker.APP_SERVER}/webpushtokencheck")
					.post(
						RequestBody.create(
							TootApiClient.MEDIA_TYPE_JSON,
							JSONObject().also {
								it.put("token_digest", account.getAccessToken()?.digestSHA256())
								it.put("install_id", install_id)
							}.toString()
						)
					)
					.build()
			)
			
			var res = r?.response ?: return r
			
			if(res.code() != 200) {
				return TootApiResult(error = context.getString(R.string.token_exported))
			}
			
			enabled = true

			
			// TODO 現在の購読状態を取得できれば良いのに…
			
			if(flags == 0) {
				// delete subscription
				r = client.request(
					"/api/v1/push/subscription",
					Request.Builder().delete()
				)
				
				res = r?.response ?: return r
				
				when(res.code()) {
					200 -> {
						addLog(context.getString(R.string.push_subscription_deleted))
						return r
					}
					
					404 -> {
						enabled = false
						return if(verbose){
							addLog(context.getString(R.string.missing_push_api))
							r
						}else{
							// バックグラウンド実行時は別にコレでも構わないので正常終了扱いとする
							TootApiResult()
						}
					}
					
					403 -> {
						enabled = false
						return if(verbose){
							addLog(context.getString(R.string.missing_push_scope))
							r
						}else{
							// バックグラウンド実行時は別にコレでも構わないので正常終了扱いとする
							TootApiResult()
						}
					}
					
				}
				addLog("${res.request()}")
				addLog("${res.code()} ${res.message()}")
				val json = r?.jsonObject
				if(json != null) {
					addLog(json.toString())
				}
				return r
			}
			
			// FCM経由での配信に必要なパラメータ
			val endpoint =
				"${PollingWorker.APP_SERVER}/webpushcallback/${device_id.encodePercent()}/${account.acct.encodePercent()}/$flags"
			
			// プッシュ通知の登録
			var json : JSONObject? = JSONObject().also {
				it.put("subscription", JSONObject().also {
					it.put("endpoint", endpoint )
					it.put("keys", JSONObject().also {
						it.put(
							"p256dh",
							"BBEUVi7Ehdzzpe_ZvlzzkQnhujNJuBKH1R0xYg7XdAKNFKQG9Gpm0TSGRGSuaU7LUFKX-uz8YW0hAshifDCkPuE"
						)
						it.put("auth", "iRdmDrOS6eK6xvG1H6KshQ")
					})
				})
				it.put("data", JSONObject().also {
					it.put("alerts", JSONObject().also {
						it.put("follow", account.notification_follow)
						it.put("favourite", account.notification_favourite)
						it.put("reblog", account.notification_boost)
						it.put("mention", account.notification_mention)
					})
				})
			}
			
			r = client.request(
				"/api/v1/push/subscription",
				Request.Builder().post(
					RequestBody.create(
						TootApiClient.MEDIA_TYPE_JSON,
						json.toString()
					)
				)
			)
			
			res = r?.response ?: return r
			
			when(res.code()) {
				404 -> {
					addLog(context.getString(R.string.missing_push_api))
					return r
				}
				
				403 -> {
					addLog(context.getString(R.string.missing_push_scope))
					return r
				}
				
				200 -> {
					subscribed = true
					addLog(context.getString(R.string.push_subscription_updated))
					return r
				}
			}
			json = r?.jsonObject
			if(json != null) {
				addLog(json.toString())
			}
			
			return r
		} catch(ex : Throwable) {
			return TootApiResult(error = ex.withCaption("error."))
		}
	}
	
	fun updateSubscription(client : TootApiClient, account : SavedAccount) : TootApiResult? {
		val result = updateSubscription_sub(client, account)
		val e = result?.error
		if(e != null) addLog(e)
		return result
	}
}