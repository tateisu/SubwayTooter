package jp.juggler.subwaytooter.util

import android.content.Context
import jp.juggler.subwaytooter.PollingWorker
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.api.entity.TootPushSubscription
import jp.juggler.subwaytooter.api.entity.parseItem
import jp.juggler.subwaytooter.table.SavedAccount
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

class WebPushSubscription(
	val context : Context,
	val account : SavedAccount,
	val verbose : Boolean = false
) {
	
	val flags : Int
	
	var enabled : Boolean = false
	var subscribed : Boolean = false
	
	init {
		var n = 0
		if(account.notification_boost) n += 1
		if(account.notification_favourite) n += 2
		if(account.notification_follow) n += 4
		if(account.notification_mention) n += 8
		this.flags = n
	}
	
	val log : String
		get() = sb.toString()
	
	private val sb = StringBuilder()
	
	private fun addLog(s : String?) {
		if(s?.isNotEmpty() == true) {
			if(sb.isNotEmpty()) sb.append('\n')
			sb.append(s)
		}
	}
	
	private fun updateSubscription_sub(client : TootApiClient) : TootApiResult? {
		try {
			
			// 疑似アカウントの確認
			if(account.isPseudo) {
				return TootApiResult(error = context.getString(R.string.pseudo_account_not_supported))
			}
			
			// インスタンスバージョンの確認
			var r = client.getInstanceInformation2()
			val ti = r?.data as? TootInstance ?: return r
			if(! ti.versionGE(TootInstance.VERSION_2_4_0)) {
				return TootApiResult(
					error = context.getString(
						R.string.instance_does_not_support_push_api,
						ti.version
					)
				)
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
			
			// 現在の購読状態を取得
			// https://github.com/tootsuite/mastodon/issues/7468
			// https://github.com/tootsuite/mastodon/pull/7471
			// https://github.com/tootsuite/mastodon/pull/7472
			r = client.request("/api/v1/push/subscription")
			res = r?.response ?: return r // cancelled or missing response
			var subscriptionNotRegistered = false
			when(res.code()) {
				200 -> {
					// 購読が存在する
				}
				
				404 -> {
					if(ti.versionGE(TootInstance.VERSION_2_4_1)
						|| ti.versionEquals(TootInstance.VERSION_2_4_0)
					) {
						// 2.4.0正式版と2.4.1以降では購読が存在しないと解釈できる
						subscriptionNotRegistered = true
					} else {
						// 2.4.0rc の #7472 以後は購読が存在しないことを示す
						// 2.4.0rc の #7472 未満はAPIがないことを示す
						// コミット単位でバージョン比較する方法はないので、2.4.0正式ではない2.4.0xxxでは存在確認はできない
					}
				}
				
				else -> {
					addLog("${res.request()}")
					addLog("${res.code()} ${res.message()}")
				}
			}
			
			if(flags == 0) {
				// 通知設定が全てカラなので、購読を取り消したい
				
				if(subscriptionNotRegistered) {
					if(verbose) addLog(context.getString(R.string.push_subscription_not_exists))
					return TootApiResult()
				}
				
				// delete subscription
				r = client.request(
					"/api/v1/push/subscription",
					Request.Builder().delete()
				)
				
				res = r?.response ?: return r
				
				return when(res.code()) {
					200 -> {
						if(! verbose) {
							TootApiResult()
						} else {
							addLog(context.getString(R.string.push_subscription_deleted))
							r
						}
					}
					
					404 -> {
						enabled = false
						if(! verbose) {
							TootApiResult()
						} else {
							addLog(context.getString(R.string.missing_push_api))
							r
						}
					}
					
					403 -> {
						enabled = false
						if(! verbose) {
							TootApiResult()
						} else {
							addLog(context.getString(R.string.missing_push_scope))
							r
						}
					}
					
					else -> {
						addLog("${res.request()}")
						addLog("${res.code()} ${res.message()}")
						r
					}
				}
				
			} else {
				// 通知設定が空ではないので購読を行いたい
				
				// FCM経由での配信に必要なパラメータをendpoint URLに埋め込む
				val endpoint =
					"${PollingWorker.APP_SERVER}/webpushcallback/${device_id.encodePercent()}/${account.acct.encodePercent()}/$flags"
				
				// 既に登録済みで、endpointも一致しているなら何もしない
				val oldSubscription = parseItem(::TootPushSubscription, r?.jsonObject)
				if(oldSubscription?.endpoint == endpoint) {
					subscribed = true
					if(verbose) addLog(context.getString(R.string.push_subscription_already_exists))
					return TootApiResult()
				}
				
				// プッシュ通知の登録
				val json = JSONObject().apply {
					put("subscription", JSONObject().apply {
						put("endpoint", endpoint)
						put("keys", JSONObject().apply {
							put(
								"p256dh",
								"BBEUVi7Ehdzzpe_ZvlzzkQnhujNJuBKH1R0xYg7XdAKNFKQG9Gpm0TSGRGSuaU7LUFKX-uz8YW0hAshifDCkPuE"
							)
							put("auth", "iRdmDrOS6eK6xvG1H6KshQ")
						})
					})
					put("data", JSONObject().apply {
						put("alerts", JSONObject().apply {
							put("follow", account.notification_follow)
							put("favourite", account.notification_favourite)
							put("reblog", account.notification_boost)
							put("mention", account.notification_mention)
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
				
				return when(res.code()) {
					404 -> {
						if(! verbose) {
							TootApiResult()
						} else {
							addLog(context.getString(R.string.missing_push_api))
							r
						}
					}
					
					403 -> {
						if(! verbose) {
							TootApiResult()
						} else {
							addLog(context.getString(R.string.missing_push_scope))
							r
						}
					}
					
					200 -> {
						subscribed = true
						if(! verbose) {
							TootApiResult()
						} else {
							addLog(context.getString(R.string.push_subscription_updated))
							r
						}
					}
					
					else -> {
						addLog(r?.jsonObject?.toString())
						r
					}
				}
			}
		} catch(ex : Throwable) {
			return TootApiResult(error = ex.withCaption("error."))
		}
	}
	
	fun updateSubscription(client : TootApiClient) : TootApiResult? {
		val result = updateSubscription_sub(client)
		val e = result?.error
		if(e != null) addLog(e)
		return result
	}
}