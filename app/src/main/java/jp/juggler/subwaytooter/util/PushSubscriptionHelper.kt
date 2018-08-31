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
import jp.juggler.subwaytooter.table.SubscriptionServerKey
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

class PushSubscriptionHelper(
	val context : Context,
	val account : SavedAccount,
	val verbose : Boolean = false
) {
	
	companion object {
		private val lastCheckedMap : HashMap<String, Long> = HashMap()
	}
	
	private fun preventRapid() : Boolean {
		if(verbose) return true
		val now = System.currentTimeMillis()
		synchronized(lastCheckedMap) {
			val lastChecked = lastCheckedMap[account.acct]
			lastCheckedMap[account.acct] = now
			return (lastChecked == null || now - lastChecked >= 600000L)
		}
	}
	
	val flags : Int
	
	var subscribed : Boolean = false
	
	init {
		var n = 0
		if(account.notification_boost) n += 1
		if(account.notification_favourite) n += 2
		if(account.notification_follow) n += 4
		if(account.notification_mention) n += 8
		if(account.isMisskey && account.notification_reaction) n += 16
		if(account.isMisskey && account.notification_vote) n += 32
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
	
	// returns error string or null
	private fun updateServerKey(
		client : TootApiClient,
		clientIdentifier : String,
		serverKey : String?
	) : TootApiResult? {
		
		if(serverKey == null) {
			return TootApiResult(error = "!! Server public key is missing. There is server misconfiguration , push notification will not work.")
		} else if(serverKey.isEmpty()) {
			return TootApiResult(error = "!! Server public key is empty. There is server misconfiguration, push notification will not work.")
		}
		
		// 既に登録済みの値と同じなら何もしない
		val oldKey = SubscriptionServerKey.find(clientIdentifier)
		if(oldKey != serverKey) {
			
			// サーバキーをアプリサーバに登録
			val r = client.http(
				Request.Builder()
					.url("${PollingWorker.APP_SERVER}/webpushserverkey")
					.post(
						RequestBody.create(
							TootApiClient.MEDIA_TYPE_JSON,
							JSONObject().apply {
								put("client_id", clientIdentifier)
								put("server_key", serverKey)
							}.toString()
						)
					)
					.build()
			)
			val res = r?.response
			if(res != null) {
				when(res.code()) {
					200 -> {
						// 登録できたサーバーキーをアプリ内DBに保存
						SubscriptionServerKey.save(clientIdentifier, serverKey)
						addLog("(server public key is registered.)")
					}
					
					else -> {
						addLog("(server public key registration failed.)")
						addLog("${res.code()} ${res.message()}")
					}
				}
			}
		}
		return TootApiResult()
	}
	
	private fun updateSubscription_sub(client : TootApiClient) : TootApiResult? {
		try {
			if(! preventRapid()) return TootApiResult("updateSubscription: prevent frequently subscription check.")
			
			// 疑似アカウントの確認
			if(account.isPseudo) {
				return TootApiResult(error = context.getString(R.string.pseudo_account_not_supported))
			}
			
			if(account.isMisskey) {
				if(flags == 0) {
					// 現在の購読状態を取得できない
					// 購読を解除できない
					return TootApiResult("Push subscription is not needed. we can't do check current subscription state, and unsubscribe it.")
				}
				
				// 現在の購読状態を取得できないので、毎回購読の更新を行う
				
				// FCMのデバイスIDを取得
				val device_id = PollingWorker.getDeviceId(context)
					?: return TootApiResult(error = context.getString(R.string.missing_fcm_device_id))
				
				//				// アクセストークン
				//				val accessToken = account.misskeyApiToken
				//					?: return TootApiResult(error = "missing misskeyApiToken.")
				//
				//				// インストールIDを取得
				//				val install_id = PollingWorker.prepareInstallId(context)
				//					?: return TootApiResult(error = context.getString(R.string.missing_install_id))
				//
				//				// アクセストークンのダイジェスト
				//				val tokenDigest = accessToken.digestSHA256Base64Url()
				//
				//				// クライアント識別子
				//				val clientIdentifier = "$accessToken$install_id".digestSHA256Base64Url()
				//
				val endpoint =
					"${PollingWorker.APP_SERVER}/webpushcallback/${device_id.encodePercent()}/${account.acct.encodePercent()}/$flags"
				// FIXME 現時点ではサーバキーの検証を行えないので clientIdentifier を指定しない
				// "${PollingWorker.APP_SERVER}/webpushcallback/${device_id.encodePercent()}/${account.acct.encodePercent()}/$flags/$clientIdentifier"
				
				// 購読
				val params = account.putMisskeyApiToken(JSONObject())
					.put("endpoint", endpoint)
					.put("auth", "iRdmDrOS6eK6xvG1H6KshQ")
					.put(
						"publickey",
						"BBEUVi7Ehdzzpe_ZvlzzkQnhujNJuBKH1R0xYg7XdAKNFKQG9Gpm0TSGRGSuaU7LUFKX-uz8YW0hAshifDCkPuE"
					)
				
				val result = client.request("/api/sw/register", params.toPostRequestBuilder())
				if(result?.jsonObject != null) {
					subscribed = true
					
					// Misskeyのプッシュ購読APIはサーバーキーを返さないので
					// プッシュ通知を受け取るendpointはそれが正しいサーバからのものなのか
					// 悪意のある第三者からのものなのか区別できない
					
					if(verbose) {
						addLog(context.getString(R.string.push_subscription_updated))
					}
				} else if(result != null) {
					addLog("subscription API returns error : ${result.error}")
				}
				return result
			} else {
				
				// 現在の購読状態を取得
				// https://github.com/tootsuite/mastodon/pull/7471
				// https://github.com/tootsuite/mastodon/pull/7472
				var r = client.request("/api/v1/push/subscription")
				var res = r?.response ?: return r // cancelled or missing response
				var subscription404 = false
				when(res.code()) {
					200 -> {
						// たぶん購読が存在する
					}
					
					404 -> {
						subscription404 = true
					}
					
					403 -> {
						// アクセストークンにpushスコープがない
						return if(verbose) {
							addLog(context.getString(R.string.missing_push_scope))
							r
						} else {
							if(flags != 0) addLog(context.getString(R.string.missing_push_scope))
							TootApiResult()
						}
					}
					
					else -> {
						addLog("${res.request()}")
						addLog("${res.code()} ${res.message()}")
					}
				}
				
				val oldSubscription = parseItem(::TootPushSubscription, r.jsonObject)
				
				if(oldSubscription == null) {
					
					// 現在の購読状況が分からない場合はインスタンスのバージョンを調べる必要がある
					r = client.parseInstanceInformation(client.getInstanceInformation())
					val ti = r?.data as? TootInstance ?: return r
					
					if(! ti.versionGE(TootInstance.VERSION_2_4_0_rc1)) {
						// 2.4.0rc1 未満にはプッシュ購読APIはない
						return TootApiResult(
							error = context.getString(
								R.string.instance_does_not_support_push_api,
								ti.version
							)
						)
					}
					
					if(subscription404 && flags == 0) {
						if(ti.versionGE(TootInstance.VERSION_2_4_0_rc2)) {
							// 購読が不要で現在の状況が404だった場合
							// 2.4.0rc2以降では「購読が存在しない」を示すので何もしなくてよい
							if(verbose) addLog(context.getString(R.string.push_subscription_not_exists))
							return TootApiResult()
						} else {
							// 2.4.0rc1では「APIが存在しない」と「購読が存在しない」を判別できない
						}
					}
				}
				
				// FCMのデバイスIDを取得
				val device_id = PollingWorker.getDeviceId(context)
					?: return TootApiResult(error = context.getString(R.string.missing_fcm_device_id))
				
				// アクセストークン
				val accessToken = account.getAccessToken()
					?: return TootApiResult(error = "missing access token.")
				
				// インストールIDを取得
				val install_id = PollingWorker.prepareInstallId(context)
					?: return TootApiResult(error = context.getString(R.string.missing_install_id))
				
				// アクセストークンのダイジェスト
				val tokenDigest = accessToken.digestSHA256Base64Url()
				
				// クライアント識別子
				val clientIdentifier = "$accessToken$install_id".digestSHA256Base64Url()
				
				val endpoint =
					"${PollingWorker.APP_SERVER}/webpushcallback/${device_id.encodePercent()}/${account.acct.encodePercent()}/$flags/$clientIdentifier"
				
				if(oldSubscription != null) {
					if(oldSubscription.endpoint == endpoint) {
						// 既に登録済みで、endpointも一致している
						subscribed = true
						if(verbose) addLog(context.getString(R.string.push_subscription_already_exists))
						return updateServerKey(client, clientIdentifier, oldSubscription.server_key)
					}
				}
				
				// アクセストークンの優先権を取得
				r = client.http(
					Request.Builder()
						.url("${PollingWorker.APP_SERVER}/webpushtokencheck")
						.post(
							RequestBody.create(
								TootApiClient.MEDIA_TYPE_JSON,
								JSONObject().also {
									it.put("token_digest", tokenDigest)
									it.put("install_id", install_id)
								}.toString()
							)
						)
						.build()
				)
				
				res = r?.response ?: return r
				if(res.code() != 200) {
					return TootApiResult(error = context.getString(R.string.token_exported))
				}
				
				if(flags == 0) {
					// 通知設定が全てカラなので、購読を取り消したい
					
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
						
						else -> {
							addLog("${res.request()}")
							addLog("${res.code()} ${res.message()}")
							r
						}
					}
					
				} else {
					// 通知設定が空ではないので購読を行いたい
					
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
							
							if(verbose) {
								addLog(context.getString(R.string.push_subscription_updated))
							}
							
							val newSubscription = parseItem(::TootPushSubscription, r?.jsonObject)
							if(newSubscription != null) {
								return updateServerKey(
									client,
									clientIdentifier,
									newSubscription.server_key
								)
							}
							
							TootApiResult()
						}
						
						else -> {
							addLog(r?.jsonObject?.toString())
							r
						}
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