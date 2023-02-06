package jp.juggler.subwaytooter.notification
//
//import android.content.Context
//import jp.juggler.subwaytooter.R
//import jp.juggler.subwaytooter.api.TootApiClient
//import jp.juggler.subwaytooter.api.TootApiResult
//import jp.juggler.subwaytooter.api.entity.*
//import jp.juggler.subwaytooter.table.SavedAccount
//import jp.juggler.subwaytooter.table.SubscriptionServerKey
//import jp.juggler.subwaytooter.table.appDatabase
//import jp.juggler.util.*
//import jp.juggler.util.data.*
//import jp.juggler.util.log.*
//import jp.juggler.util.network.toPostRequestBuilder
//import jp.juggler.util.ui.*
//import okhttp3.Request
//
//class PushSubscriptionHelper(
//    val context: Context,
//    val account: SavedAccount,
//    private val verbose: Boolean = false,
//    private val daoSubscriptionServerKey: SubscriptionServerKey.Access =
//        SubscriptionServerKey.Access(appDatabase),
//) {
//    companion object {
//        private val log = LogCategory("PushSubscriptionHelper")
//    }
//
//    private val logBuffer = StringBuilder()
//
//    val logString: String
//        get() = logBuffer.toString()
//
//    private var subscribed: Boolean = false
//
//    val flags = account.notificationFlags()
//
//    private fun addLog(s: String?) {
//        if (s?.isNotEmpty() == true) {
//            if (logBuffer.isNotEmpty()) logBuffer.append('\n')
//            logBuffer.append(s)
//        }
//    }
//
//    // アプリサーバにendpoint URLの変更を伝える
//    private suspend fun registerEndpoint(
//        client: TootApiClient,
//        deviceId: String,
//        endpoint: String,
//    ): TootApiResult {
//
//        // deprecated
//        // if (account.last_push_endpoint == endpoint) return TootApiResult()
//
//        return client.http(
//            buildJsonObject {
//                put("acct", account.acct.ascii)
//                put("deviceId", deviceId)
//                put("endpoint", endpoint)
//            }
//                .toPostRequestBuilder()
//                .url("$APP_SERVER/webpushendpoint")
//                .build()
//        ).also { result ->
//            result.response?.let { res ->
//                when (res.code.also { res.close() }) {
//                    in 200 until 300 -> {
//                        // deprecated
//                        // account.updateLastPushEndpoint(endpoint)
//                    }
//                    else -> {
//                        result.caption = "(SubwayTooter App server)"
//                        client.readBodyString(result)
//                    }
//                }
//            }
//        }
//    }
//
////    suspend fun updateSubscription(
////        client: TootApiClient,
////        force: Boolean = false,
////        progress: suspend (SavedAccount, PollingState) -> Unit = { _, _ -> },
////    ): TootApiResult? = try {
////        when {
////            account.isPseudo ->
////                TootApiResult(context.getString(R.string.pseudo_account_not_supported))
////
////            else -> {
////                progress(account, PollingState.CheckPushSubscription)
////                when {
////                    account.isMisskey -> updateSubscriptionMisskey(client)
////                    else -> updateSubscriptionMastodon(client, force)
////                }
////            }
////        }
////    } catch (ex: Throwable) {
////        TootApiResult(ex.withCaption("error."))
////    }?.apply {
////
////        if (error != null) addLog("$error $requestInfo".trimEnd())
////
////        // update error text on account table
////        val log = logString
////        when {
////            log.contains(ERROR_PREVENT_FREQUENTLY_CHECK) -> {
////                // don't update if check was skipped.
////            }
////
////            subscribed || log.isEmpty() -> Unit
////            // clear error text if succeeded or no error log
//////                if (account.last_subscription_error != null) {
//////                    account.updateSubscriptionError(null)
//////                }
////
////            else -> Unit
////            // record error text
//////                account.updateSubscriptionError(log)
////        }
////    }
//
////    private suspend fun updateSubscriptionMisskey(client: TootApiClient): TootApiResult? {
////
////        // 現在の購読状態を取得できないので、毎回購読の更新を行う
////        // FCMのデバイスIDを取得
////        val deviceId = try {
////            loadFirebaseMessagingToken(context)
////        } catch (ex: Throwable) {
////            log.e(ex, "loadFirebaseMessagingToken failed.")
////            return when (ex) {
////                is CancellationException -> null
////                else -> TootApiResult(error = context.getString(R.string.missing_fcm_device_id))
////            }
////        }
////
////        // アクセストークン
////        val accessToken = account.misskeyApiToken
////            ?: return TootApiResult(error = "missing misskeyApiToken.")
////
////        // インストールIDを取得
////        val installId = try {
////            loadInstallId(
////                context,
////                account,
////                deviceId
////            ) { a, s -> log.i("[${a.acct.pretty}]${s.desc}") }
////        } catch (ex: Throwable) {
////            log.e(ex, "loadInstallId failed.")
////            return when (ex) {
////                is CancellationException -> null
////                else -> TootApiResult(error = context.getString(R.string.missing_install_id))
////            }
////        }
////
////        // クライアント識別子
////        val clientIdentifier = "$accessToken$installId".digestSHA256Base64Url()
////
////        // 購読が不要な場合
////        // アプリサーバが410を返せるように状態を通知する
////        if (flags == 0) return registerEndpoint(client, deviceId, "none").also {
////            if (it.error == null && verbose) addLog(context.getString(R.string.push_subscription_updated))
////        }
////
////        /*
////           https://github.com/syuilo/misskey/blob/master/src/services/create-notification.ts#L46
////           Misskeyは通知に既読の概念があり、イベント発生後2秒たっても未読の時だけプッシュ通知が発生する。
////           STでプッシュ通知を試すにはSTの画面を非表示にする必要があるのでWebUIを使って投稿していたが、
////           WebUIを開いていると通知はすぐ既読になるのでプッシュ通知は発生しない。
////           プッシュ通知のテスト時はST2台を使い、片方をプッシュ通知の受信チェック、もう片方を投稿などの作業に使うことになる。
////        */
////
////        // https://github.com/syuilo/misskey/issues/2541
////        // https://github.com/syuilo/misskey/commit/4c6fb60dd25d7e2865fc7c4d97728593ffc3c902
////        // 2018/9/1 の上記コミット以降、Misskeyでもサーバ公開鍵を得られるようになった
////
////        val endpoint =
////            "$APP_SERVER/webpushcallback/${deviceId.encodePercent()}/${account.acct.ascii.encodePercent()}/$flags/$clientIdentifier/misskey"
////
////        // アプリサーバが過去のendpoint urlに410を返せるよう、状態を通知する
////        val r = registerEndpoint(client, deviceId, endpoint.toUri().encodedPath!!)
////        if (r.error != null) return r
////
////        // 購読
////        @Suppress("SpellCheckingInspection")
////        return client.request(
////            "/api/sw/register",
////            account.putMisskeyApiToken().apply {
////                put("endpoint", endpoint)
////                put("auth", "iRdmDrOS6eK6xvG1H6KshQ")
////                put(
////                    "publickey",
////                    "BBEUVi7Ehdzzpe_ZvlzzkQnhujNJuBKH1R0xYg7XdAKNFKQG9Gpm0TSGRGSuaU7LUFKX-uz8YW0hAshifDCkPuE"
////                )
////            }
////                .toPostRequestBuilder()
////        )?.also { result ->
////            val jsonObject = result.jsonObject
////            if (jsonObject == null) {
////                addLog("API error.")
////            } else {
////                if (verbose) addLog(context.getString(R.string.push_subscription_updated))
////                subscribed = true
////                return updateServerKey(
////                    client,
////                    clientIdentifier,
////                    jsonObject.string("key") ?: "3q2+rw"
////                )
////            }
////        }
////    }
//
////    private suspend fun updateSubscriptionMastodon(
////        client: TootApiClient,
////        force: Boolean,
////    ): TootApiResult? {
////
////        // 現在の購読状態を取得
////        // https://github.com/tootsuite/mastodon/pull/7471
////        // https://github.com/tootsuite/mastodon/pull/7472
////
////        val subscription404: Boolean
////        val oldSubscription: TootPushSubscription?
////        checkCurrentSubscription(client).let {
////            if (it.failed) return it.result
////            subscription404 = it.is404
////            oldSubscription = parseItem(::TootPushSubscription, it.result?.jsonObject)
////        }
////
////        if (oldSubscription == null) {
////            log.i("${account.acct}: oldSubscription is null")
////            val (ti, result) = TootInstance.get(client)
////            ti ?: return result
////            checkInstanceVersionMastodon(ti, subscription404)?.let { return it }
////        }
////
////        // FCMのデバイスIDを取得
////        val deviceId = try {
////            loadFirebaseMessagingToken(context)
////        } catch (ex: Throwable) {
////            log.e(ex, "loadFirebaseMessagingToken failed.")
////            return when (ex) {
////                is CancellationException -> null
////                else -> TootApiResult(error = context.getString(R.string.missing_fcm_device_id))
////            }
////        }
////
////        // インストールIDを取得
////        val installId = try {
////            loadInstallId(
////                context,
////                account,
////                deviceId
////            ) { a, s -> log.i("[${a.acct.pretty}]${s.desc}") }
////        } catch (ex: Throwable) {
////            log.e(ex, "loadInstallId failed.")
////            return when (ex) {
////                is CancellationException -> null
////                else -> TootApiResult(error = context.getString(R.string.missing_install_id))
////            }
////        }
////        // アクセストークン
////        val accessToken = account.bearerAccessToken
////            ?: return TootApiResult(error = "missing access token.")
////
////        // アクセストークンのダイジェスト
////        val tokenDigest = accessToken.digestSHA256Base64Url()
////
////        // クライアント識別子
////        val clientIdentifier = "$accessToken$installId".digestSHA256Base64Url()
////
////        val endpoint =
////            "$APP_SERVER/webpushcallback/${deviceId.encodePercent()}/${account.acct.ascii.encodePercent()}/$flags/$clientIdentifier"
////
////        val newAlerts = JsonObject().apply {
////            put("follow", account.notification_follow)
////            put(TootNotification.TYPE_ADMIN_SIGNUP, account.notification_follow)
////            put("favourite", account.notification_favourite)
////            put("reblog", account.notification_boost)
////            put("mention", account.notification_mention)
////            put("poll", account.notification_vote)
////            put("follow_request", account.notification_follow_request)
////            put("status", account.notification_post)
////            put("update", account.notification_update)
////            put("emoji_reaction", account.notification_reaction) // fedibird拡張
////        }
////
////        if (!force) {
////            canSkipSubscriptionMastodon(
////                client = client,
////                clientIdentifier = clientIdentifier,
////                endpoint = endpoint,
////                oldSubscription = oldSubscription,
////                newAlerts = newAlerts,
////            )?.let { return it }
////        }
////
////        // アクセストークンの優先権を取得
////        checkDeviceHasPriority(
////            client,
////            tokenDigest = tokenDigest,
////            installId = installId,
////        ).let {
////            if (it.failed) return it.result
////        }
////
////        return when (flags) {
////            // 通知設定が全てカラなので、購読を取り消したい
////            0 -> unsubscribeMastodon(client)
////
////            // 通知設定が空ではないので購読を行いたい
////            else -> subscribeMastodon(
////                client = client,
////                clientIdentifier = clientIdentifier,
////                endpoint = endpoint,
////                newAlerts = newAlerts
////            )
////        }
////    }
//
//    // returns null if no error
//    private fun checkInstanceVersionMastodon(
//        ti: TootInstance,
//        subscription404: Boolean,
//    ): TootApiResult? {
//
//        // 2.4.0rc1 未満にはプッシュ購読APIはない
//        if (!ti.versionGE(TootInstance.VERSION_2_4_0_rc1)) {
//            return TootApiResult(
//                context.getString(R.string.instance_does_not_support_push_api, ti.version)
//            )
//        }
//
//        if (subscription404 && flags == 0) {
//            when {
//                ti.versionGE(TootInstance.VERSION_2_4_0_rc2) -> {
//                    // 購読が不要で現在の状況が404だった場合
//                    // 2.4.0rc2以降では「購読が存在しない」を示すので何もしなくてよい
//                    if (verbose) addLog(context.getString(R.string.push_subscription_not_exists))
//                    return TootApiResult()
//                }
//
//                else -> {
//                    // 2.4.0rc1では「APIが存在しない」と「購読が存在しない」を判別できない
//                }
//            }
//        }
//        return null
//    }
//
//    private class CheckCurrentSubscriptionResult(
//        val result: TootApiResult?,
//        val failed: Boolean,
//        val is404: Boolean,
//    )
//
//    @Suppress("BooleanLiteralArgument")
//    private suspend fun checkCurrentSubscription(client: TootApiClient): CheckCurrentSubscriptionResult {
//        val r = client.request("/api/v1/push/subscription")
//        fun rvError() = CheckCurrentSubscriptionResult(r, true, false)
//        fun rvOk() = CheckCurrentSubscriptionResult(r, false, false)
//        fun rv404() = CheckCurrentSubscriptionResult(r, false, true)
//        val res = r?.response ?: return rvError() // cancelled or missing response
//
//        if (res.code != 200) log.i("${account.acct}: check existing subscription: code=${res.code}")
//
//        return when (res.code) {
//            200 -> {
//                if (r.error?.isNotEmpty() == true && r.jsonObject == null) {
//                    // Pleromaが200応答でもエラーHTMLを返す場合がある
//                    addLog(context.getString(R.string.instance_does_not_support_push_api_pleroma))
//                    rvError()
//                } else {
//                    // たぶん購読が存在する
//                    rvOk()
//                }
//            }
//
//            // この時点では存在しないのが購読なのかAPIなのか分からない
//            404 -> rv404()
//
//            403 -> {
//                // アクセストークンにpushスコープがない
//                if (flags != 0 || verbose) addLog(context.getString(R.string.missing_push_scope))
//                rvError()
//            }
//
//            in 400 until 500 -> {
//                addLog(context.getString(R.string.instance_does_not_support_push_api_pleroma))
//                rvError()
//            }
//
//            else -> {
//                addLog("${res.request}")
//                addLog("${res.code} ${res.message}")
//                rvOk() // 後でリトライする
//            }
//        }
//    }
//
//    private suspend fun unsubscribeMastodon(
//        client: TootApiClient,
//    ): TootApiResult? {
//
//        val r = client.request("/api/v1/push/subscription", Request.Builder().delete())
//        val res = r?.response ?: return r
//
//        return when (res.code) {
//            200 -> {
//                if (verbose) addLog(context.getString(R.string.push_subscription_deleted))
//                TootApiResult()
//            }
//
//            404 -> {
//                if (verbose) {
//                    addLog(context.getString(R.string.missing_push_api))
//                    r
//                } else {
//                    TootApiResult()
//                }
//            }
//
//            403 -> {
//                addLog(context.getString(R.string.missing_push_scope))
//                r
//            }
//
//            else -> {
//                addLog("${res.request}")
//                addLog("${res.code} ${res.message}")
//                r
//            }
//        }
//    }
//
//    private suspend fun subscribeMastodon(
//        client: TootApiClient,
//        endpoint: String,
//        newAlerts: JsonObject,
//    ): TootApiResult? {
//        @Suppress("SpellCheckingInspection")
//        val params = JsonObject().apply {
//            put("subscription", JsonObject().apply {
//                put("endpoint", endpoint)
//                put("keys", JsonObject().apply {
//                    put(
//                        "p256dh",
//                        "BBEUVi7Ehdzzpe_ZvlzzkQnhujNJuBKH1R0xYg7XdAKNFKQG9Gpm0TSGRGSuaU7LUFKX-uz8YW0hAshifDCkPuE"
//                    )
//                    put("auth", "iRdmDrOS6eK6xvG1H6KshQ")
//                })
//            })
//            put("data", JsonObject().apply {
//                put("alerts", newAlerts)
//                account.pushPolicy?.let { put("policy", it) }
//            })
//        }
//
//        val r = client.request(
//            "/api/v1/push/subscription",
//            params.toPostRequestBuilder()
//        ) ?: return null
//
//        val res = r.response ?: return r
//
//        return when (res.code) {
//            404 -> {
//                addLog(context.getString(R.string.missing_push_api))
//                r
//            }
//
//            403 -> {
//                addLog(context.getString(R.string.missing_push_scope))
//                r
//            }
//
//            200 -> {
//                subscribed = true
//                if (verbose) addLog(context.getString(R.string.push_subscription_updated))
//                return TootApiResult()
//            }
//
//            else -> {
//                addLog(r.jsonObject?.toString())
//                r
//            }
//        }
//    }
//}
