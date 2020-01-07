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
import jp.juggler.util.*
import okhttp3.Request

class PushSubscriptionHelper(
	val context : Context,
	val account : SavedAccount,
	val verbose : Boolean = false
) {
	
	companion object {
		private const val ERROR_PREVENT_FREQUENTLY_CHECK =
			"prevent frequently subscription check."
		
		private val lastCheckedMap : HashMap<String, Long> = HashMap()
		
		fun clearLastCheck(account : SavedAccount) {
			synchronized(lastCheckedMap) {
				lastCheckedMap.remove(account.acct)
			}
		}
		
		private fun Boolean.booleanToInt(trueValue : Int, falseValue : Int = 0) =
			if(this) trueValue else falseValue
	}
	
	private fun isRecentlyChecked() : Boolean {
		if(verbose) return false
		val now = System.currentTimeMillis()
		val acct = account.acct
		synchronized(lastCheckedMap) {
			val lastChecked = lastCheckedMap[acct]
			val rv = lastChecked != null && now - lastChecked < 3600000L
			if(! rv) lastCheckedMap[acct] = now
			return rv
		}
	}
	
	val flags : Int
	
	private var subscribed : Boolean = false
	
	init {
		this.flags = account.notification_boost.booleanToInt(1) +
			account.notification_favourite.booleanToInt(2) +
			account.notification_follow.booleanToInt(4) +
			account.notification_mention.booleanToInt(8) +
			(account.isMisskey && account.notification_reaction).booleanToInt(16) +
			account.notification_vote.booleanToInt(32) +
			account.notification_follow_request.booleanToInt(64)
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
	
	private fun updateServerKey(
		client : TootApiClient,
		clientIdentifier : String,
		serverKey : String?
	) : TootApiResult? {
		
		if(serverKey == null) {
			return TootApiResult(context.getString(R.string.push_notification_server_key_missing))
		} else if(serverKey.isEmpty()) {
			return TootApiResult(context.getString(R.string.push_notification_server_key_empty))
		}
		
		// 既に登録済みの値と同じなら何もしない
		val oldKey = SubscriptionServerKey.find(clientIdentifier)
		if(oldKey != serverKey) {
			
			// サーバキーをアプリサーバに登録
			val r = client.http(
				JsonObject().apply{
					put("client_id", clientIdentifier)
					put("server_key", serverKey)
				}
					.toPostRequestBuilder()
					.url("${PollingWorker.APP_SERVER}/webpushserverkey")
					.build()
			
			)
			val res = r?.response
			when(res?.code) {
				
				null -> {
				}
				
				200 -> {
					// 登録できたサーバーキーをアプリ内DBに保存
					SubscriptionServerKey.save(clientIdentifier, serverKey)
					addLog("(server public key is registered.)")
				}
				
				else -> {
					addLog("(server public key registration failed.)")
					addLog("${res.code} ${res.message}")
				}
			}
		}
		return TootApiResult()
	}
	
	// アプリサーバにendpoint URLの変更を伝える
	private fun registerEndpoint(
		client : TootApiClient,
		deviceId : String,
		endpoint : String
	) : TootApiResult? {
		
		if( account.last_push_endpoint == endpoint) return TootApiResult()
		
		return client.http(
			jsonObject {
				put("acct", account.acct)
				put("deviceId", deviceId)
				put("endpoint", endpoint)
			}
				.toPostRequestBuilder()
				.url("${PollingWorker.APP_SERVER}/webpushendpoint")
				.build()
		)?.also{ result ->
			val res = result.response
			if(res!=null){
				val code = res.code
				if( code in 200 until 300 ) {
					account.updateLastPushEndpoint(endpoint)
				}else{
					result.caption = "(SubwayTooter App server)"
					client.readBodyString(result)
				}
			}
		}
	}
	
	private fun updateSubscriptionMisskey(client : TootApiClient) : TootApiResult? {
		
		// 現在の購読状態を取得できないので、毎回購読の更新を行う
		// FCMのデバイスIDを取得
		val device_id = PollingWorker.getDeviceId(context)
			?: return TootApiResult(error = context.getString(R.string.missing_fcm_device_id))
		
		// アクセストークン
		val accessToken = account.misskeyApiToken
			?: return TootApiResult(error = "missing misskeyApiToken.")
		
		// インストールIDを取得
		val install_id = PollingWorker.prepareInstallId(context)
			?: return TootApiResult(error = context.getString(R.string.missing_install_id))
		
		// クライアント識別子
		val clientIdentifier = "$accessToken$install_id".digestSHA256Base64Url()
		
		// 購読が不要な場合
		// アプリサーバが410を返せるように状態を通知する
		if(flags == 0) return registerEndpoint(client,device_id,"none")?.also{
			if(it.error ==null && verbose ) addLog(context.getString(R.string.push_subscription_updated))
		}

		/*
		   https://github.com/syuilo/misskey/blob/master/src/services/create-notification.ts#L46
		   Misskeyは通知に既読の概念があり、イベント発生後2秒たっても未読の時だけプッシュ通知が発生する。
		   STでプッシュ通知を試すにはSTの画面を非表示にする必要があるのでWebUIを使って投稿していたが、
		   WebUIを開いていると通知はすぐ既読になるのでプッシュ通知は発生しない。
		   プッシュ通知のテスト時はST2台を使い、片方をプッシュ通知の受信チェック、もう片方を投稿などの作業に使うことになる。
		*/
		
		// https://github.com/syuilo/misskey/issues/2541
		// https://github.com/syuilo/misskey/commit/4c6fb60dd25d7e2865fc7c4d97728593ffc3c902
		// 2018/9/1 の上記コミット以降、Misskeyでもサーバ公開鍵を得られるようになった
		
		val endpoint =
			"${PollingWorker.APP_SERVER}/webpushcallback/${device_id.encodePercent()}/${account.acct.encodePercent()}/$flags/$clientIdentifier/misskey"

		// アプリサーバが過去のendpoint urlに410を返せるよう、状態を通知する
		val r = registerEndpoint(client,device_id,endpoint.toUri().encodedPath!!)
		if(r==null || r.error!=null) return r
		
		// 購読
		return client.request(
			"/api/sw/register",
			account.putMisskeyApiToken().apply{
				put("endpoint", endpoint)
				put("auth", "iRdmDrOS6eK6xvG1H6KshQ")
				put(
					"publickey",
					"BBEUVi7Ehdzzpe_ZvlzzkQnhujNJuBKH1R0xYg7XdAKNFKQG9Gpm0TSGRGSuaU7LUFKX-uz8YW0hAshifDCkPuE"
				)
			}
				.toPostRequestBuilder()
		)?.also { result ->
			val jsonObject = result.jsonObject
			if(jsonObject == null) {
				addLog("API error.")
			} else {
				if(verbose) addLog(context.getString(R.string.push_subscription_updated))
				subscribed = true
				return updateServerKey(
					client,
					clientIdentifier,
					jsonObject.parseString("key") ?: "3q2+rw"
				)
			}
		}
	}
	
	private fun updateSubscriptionMastodon(client : TootApiClient) : TootApiResult? {
		// 現在の購読状態を取得
		// https://github.com/tootsuite/mastodon/pull/7471
		// https://github.com/tootsuite/mastodon/pull/7472
		var r = client.request("/api/v1/push/subscription")
		var res = r?.response ?: return r // cancelled or missing response
		var subscription404 = false
		when(res.code) {
			200 -> {
				if(r.error?.isNotEmpty() == true && r.jsonObject == null) {
					// Pleromaが200応用でエラーHTMLを返す
					addLog(context.getString(R.string.instance_does_not_support_push_api_pleroma))
					return r
				}
				// たぶん購読が存在する
			}
			
			404 -> {
				subscription404 = true
				// この時点では存在しないのが購読なのかAPIなのか分からない
			}
			
			403 -> {
				// アクセストークンにpushスコープがない
				if(flags != 0 || verbose)
					addLog(context.getString(R.string.missing_push_scope))
				return r
			}
			
			in 400 until 500 -> {
				addLog(context.getString(R.string.instance_does_not_support_push_api_pleroma))
				return r
			}
			
			else -> {
				addLog("${res.request}")
				addLog("${res.code} ${res.message}")
			}
		}
		
		val oldSubscription = parseItem(::TootPushSubscription, r.jsonObject)
		
		if(oldSubscription == null) {
			
			// 現在の購読状況が分からない場合はインスタンスのバージョンを調べる必要がある
			val (ti, result) = TootInstance.get(client)
			ti ?: return result
			
			// 2.4.0rc1 未満にはプッシュ購読APIはない
			if(! ti.versionGE(TootInstance.VERSION_2_4_0_rc1))
				return TootApiResult(
					context.getString(R.string.instance_does_not_support_push_api, ti.version)
				)
			
			if(subscription404 && flags == 0) {
				when {
					ti.versionGE(TootInstance.VERSION_2_4_0_rc2) -> {
						// 購読が不要で現在の状況が404だった場合
						// 2.4.0rc2以降では「購読が存在しない」を示すので何もしなくてよい
						if(verbose) addLog(context.getString(R.string.push_subscription_not_exists))
						return TootApiResult()
					}
					
					else -> {
						// 2.4.0rc1では「APIが存在しない」と「購読が存在しない」を判別できない
					}
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
		
		if(oldSubscription?.endpoint == endpoint) {
			// 既に登録済みで、endpointも一致している
			subscribed = true
			if(verbose) addLog(context.getString(R.string.push_subscription_already_exists))
			return updateServerKey(client, clientIdentifier, oldSubscription.server_key)
		}
		
		// アクセストークンの優先権を取得
		r = client.http(
			jsonObject{
				put("token_digest", tokenDigest)
				put("install_id", install_id)
			}
				.toPostRequestBuilder()
				.url("${PollingWorker.APP_SERVER}/webpushtokencheck")
				.build()
		) ?: return null
		
		res = r.response ?: return r
		if(res.code != 200) {
			if(res.code == 403) addLog(context.getString(R.string.token_exported))
			r.caption = "(SubwayTooter App server)"
			client.readBodyString(r)
			return r
		}
		
		return if(flags == 0) {
			// 通知設定が全てカラなので、購読を取り消したい
			
			r = client.request("/api/v1/push/subscription", Request.Builder().delete())
			res = r?.response ?: return r
			
			when(res.code) {
				200 -> {
					if(verbose) addLog(context.getString(R.string.push_subscription_deleted))
					TootApiResult()
				}
				
				404 -> {
					if(verbose) {
						addLog(context.getString(R.string.missing_push_api))
						r
					} else {
						TootApiResult()
					}
				}
				
				403 -> {
					addLog(context.getString(R.string.missing_push_scope))
					r
				}
				
				else -> {
					addLog("${res.request}")
					addLog("${res.code} ${res.message}")
					r
				}
			}
			
		} else {
			// 通知設定が空ではないので購読を行いたい
			
			r = client.request(
				"/api/v1/push/subscription",
				JsonObject().apply {
					put("subscription", JsonObject().apply {
						put("endpoint", endpoint)
						put("keys", JsonObject().apply {
							put(
								"p256dh",
								"BBEUVi7Ehdzzpe_ZvlzzkQnhujNJuBKH1R0xYg7XdAKNFKQG9Gpm0TSGRGSuaU7LUFKX-uz8YW0hAshifDCkPuE"
							)
							put("auth", "iRdmDrOS6eK6xvG1H6KshQ")
						})
					})
					put("data", JsonObject().apply {
						put("alerts", JsonObject().apply {
							put("follow", account.notification_follow)
							put("favourite", account.notification_favourite)
							put("reblog", account.notification_boost)
							put("mention", account.notification_mention)
							put("poll", account.notification_vote)
							put("follow_request", account.notification_follow_request)
						})
					})
				}
					.toPostRequestBuilder()
			) ?: return null
			
			res = r.response ?: return r
			
			when(res.code) {
				404 -> {
					addLog(context.getString(R.string.missing_push_api))
					r
				}
				
				403 -> {
					addLog(context.getString(R.string.missing_push_scope))
					r
				}
				
				200 -> {
					val newSubscription = parseItem(::TootPushSubscription, r.jsonObject)
						?: return r.setError("parse error.")
					
					subscribed = true
					if(verbose) addLog(context.getString(R.string.push_subscription_updated))
					
					return updateServerKey(
						client,
						clientIdentifier,
						newSubscription.server_key
					)
				}
				
				else -> {
					addLog(r.jsonObject?.toString())
					r
				}
			}
		}
	}
	
	fun updateSubscription(client : TootApiClient) : TootApiResult? =
		try {
			when {
				isRecentlyChecked() ->
					TootApiResult(ERROR_PREVENT_FREQUENTLY_CHECK)
				
				account.isPseudo ->
					TootApiResult(context.getString(R.string.pseudo_account_not_supported))
				
				account.isMisskey ->
					updateSubscriptionMisskey(client)
				
				else ->
					updateSubscriptionMastodon(client)
			}
		} catch(ex : Throwable) {
			TootApiResult(ex.withCaption("error."))
		}?.apply {
			
			if(error != null) addLog("$error $requestInfo".trimEnd())
			
			// update error text on account table
			val log = log
			when {
				
				log.contains(ERROR_PREVENT_FREQUENTLY_CHECK) -> {
					// don't update if check was skipped.
				}
				
				subscribed || log.isEmpty() ->
					// clear error text if succeeded or no error log
					if(account.last_subscription_error != null)
						account.updateSubscriptionError(null)
				
				else ->
					// record error text
					account.updateSubscriptionError(log)
			}
		}
}