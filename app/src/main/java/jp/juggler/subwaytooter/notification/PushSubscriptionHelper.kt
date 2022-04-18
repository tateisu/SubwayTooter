package jp.juggler.subwaytooter.notification

import android.content.Context
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.SubscriptionServerKey
import jp.juggler.util.*
import okhttp3.Request
import okhttp3.Response

class PushSubscriptionHelper(
    val context: Context,
    val account: SavedAccount,
    val verbose: Boolean = false,
) {
    companion object {

        private val log = LogCategory("PushSubscriptionHelper")

        private const val ERROR_PREVENT_FREQUENTLY_CHECK =
            "prevent frequently subscription check."

        private val lastCheckedMap: HashMap<String, Long> = HashMap()

        fun clearLastCheck(account: SavedAccount) {
            synchronized(lastCheckedMap) {
                lastCheckedMap.remove(account.acct.ascii)
            }
        }

        private fun Boolean.booleanToInt(trueValue: Int, falseValue: Int = 0) =
            if (this) trueValue else falseValue

        private fun Response.closeQuietly() =
            try {
                close()
            } catch (_: Throwable) {
            }
    }

    val flags = account.notification_boost.booleanToInt(1) +
            account.notification_favourite.booleanToInt(2) +
            account.notification_follow.booleanToInt(4) +
            account.notification_mention.booleanToInt(8) +
            account.notification_reaction.booleanToInt(16) +
            account.notification_vote.booleanToInt(32) +
            account.notification_follow_request.booleanToInt(64) +
            account.notification_post.booleanToInt(128) +
            account.notification_update.booleanToInt(256)

    private val logBuffer = StringBuilder()

    val logString: String
        get() = logBuffer.toString()

    private var subscribed: Boolean = false

    private fun addLog(s: String?) {
        if (s?.isNotEmpty() == true) {
            if (logBuffer.isNotEmpty()) logBuffer.append('\n')
            logBuffer.append(s)
        }
    }

    private fun isRecentlyChecked(): Boolean {
        if (verbose) return false
        val now = System.currentTimeMillis()
        val acctAscii = account.acct.ascii
        synchronized(lastCheckedMap) {
            val lastChecked = lastCheckedMap[acctAscii]
            val rv = lastChecked != null && now - lastChecked < 3600000L
            if (!rv) lastCheckedMap[acctAscii] = now
            return rv
        }
    }

    private suspend fun updateServerKey(
        client: TootApiClient,
        clientIdentifier: String,
        serverKey: String?,
    ): TootApiResult {

        if (serverKey == null) {
            return TootApiResult(context.getString(R.string.push_notification_server_key_missing))
        } else if (serverKey.isEmpty()) {
            return TootApiResult(context.getString(R.string.push_notification_server_key_empty))
        }

        // 既に登録済みの値と同じなら何もしない
        val oldKey = SubscriptionServerKey.find(clientIdentifier)
        if (oldKey != serverKey) {

            // サーバキーをアプリサーバに登録
            client.http(
                JsonObject().apply {
                    put("client_id", clientIdentifier)
                    put("server_key", serverKey)
                }
                    .toPostRequestBuilder()
                    .url("${PollingWorker.APP_SERVER}/webpushserverkey")
                    .build()

            ).also { result ->
                result.response?.let { res ->
                    when (res.code.also { res.close() }) {

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
            }
        }
        return TootApiResult()
    }

    // アプリサーバにendpoint URLの変更を伝える
    private suspend fun registerEndpoint(
        client: TootApiClient,
        deviceId: String,
        endpoint: String,
    ): TootApiResult {

        if (account.last_push_endpoint == endpoint) return TootApiResult()

        return client.http(
            jsonObject {
                put("acct", account.acct.ascii)
                put("deviceId", deviceId)
                put("endpoint", endpoint)
            }
                .toPostRequestBuilder()
                .url("${PollingWorker.APP_SERVER}/webpushendpoint")
                .build()
        ).also { result ->
            result.response?.let { res ->
                when (res.code.also { res.close() }) {
                    in 200 until 300 -> {
                        account.updateLastPushEndpoint(endpoint)
                    }
                    else -> {
                        result.caption = "(SubwayTooter App server)"
                        client.readBodyString(result)
                    }
                }
            }
        }
    }

    suspend fun updateSubscription(client: TootApiClient, force: Boolean = false): TootApiResult? =
        try {
            when {
                isRecentlyChecked() ->
                    TootApiResult(ERROR_PREVENT_FREQUENTLY_CHECK)

                account.isPseudo ->
                    TootApiResult(context.getString(R.string.pseudo_account_not_supported))

                account.isMisskey ->
                    updateSubscriptionMisskey(client)

                else ->
                    updateSubscriptionMastodon(client, force)
            }
        } catch (ex: Throwable) {
            TootApiResult(ex.withCaption("error."))
        }?.apply {

            if (error != null) addLog("$error $requestInfo".trimEnd())

            // update error text on account table
            val log = logString
            when {

                log.contains(ERROR_PREVENT_FREQUENTLY_CHECK) -> {
                    // don't update if check was skipped.
                }

                subscribed || log.isEmpty() ->
                    // clear error text if succeeded or no error log
                    if (account.last_subscription_error != null) {
                        account.updateSubscriptionError(null)
                    }

                else ->
                    // record error text
                    account.updateSubscriptionError(log)
            }
        }

    private suspend fun updateSubscriptionMisskey(client: TootApiClient): TootApiResult? {

        // 現在の購読状態を取得できないので、毎回購読の更新を行う
        // FCMのデバイスIDを取得
        val deviceId = PollingWorker.getFirebaseMessagingToken(context)
            ?: return TootApiResult(error = context.getString(R.string.missing_fcm_device_id))

        // アクセストークン
        val accessToken = account.misskeyApiToken
            ?: return TootApiResult(error = "missing misskeyApiToken.")

        // インストールIDを取得
        val installId = PollingWorker.prepareInstallId(context)
            ?: return TootApiResult(error = context.getString(R.string.missing_install_id))

        // クライアント識別子
        val clientIdentifier = "$accessToken$installId".digestSHA256Base64Url()

        // 購読が不要な場合
        // アプリサーバが410を返せるように状態を通知する
        if (flags == 0) return registerEndpoint(client, deviceId, "none").also {
            if (it.error == null && verbose) addLog(context.getString(R.string.push_subscription_updated))
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
            "${PollingWorker.APP_SERVER}/webpushcallback/${deviceId.encodePercent()}/${account.acct.ascii.encodePercent()}/$flags/$clientIdentifier/misskey"

        // アプリサーバが過去のendpoint urlに410を返せるよう、状態を通知する
        val r = registerEndpoint(client, deviceId, endpoint.toUri().encodedPath!!)
        if (r.error != null) return r

        // 購読
        @Suppress("SpellCheckingInspection")
        return client.request(
            "/api/sw/register",
            account.putMisskeyApiToken().apply {
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
            if (jsonObject == null) {
                addLog("API error.")
            } else {
                if (verbose) addLog(context.getString(R.string.push_subscription_updated))
                subscribed = true
                return updateServerKey(
                    client,
                    clientIdentifier,
                    jsonObject.string("key") ?: "3q2+rw"
                )
            }
        }
    }

    private suspend fun updateSubscriptionMastodon(
        client: TootApiClient,
        force: Boolean,
    ): TootApiResult? {

        // 現在の購読状態を取得
        // https://github.com/tootsuite/mastodon/pull/7471
        // https://github.com/tootsuite/mastodon/pull/7472

        val subscription404: Boolean
        val oldSubscription: TootPushSubscription?
        checkCurrentSubscription(client).let {
            if (it.failed) return it.result
            subscription404 = it.is404
            oldSubscription = parseItem(::TootPushSubscription, it.result?.jsonObject)
        }

        if (oldSubscription == null) {
            log.i("${account.acct}: oldSubscription is null")
            val (ti, result) = TootInstance.get(client)
            ti ?: return result
            checkInstanceVersionMastodon(ti, subscription404)?.let { return it }
        }

        // FCMのデバイスIDを取得
        val deviceId = PollingWorker.getFirebaseMessagingToken(context)
            ?: return TootApiResult(error = context.getString(R.string.missing_fcm_device_id))

        // インストールIDを取得
        val installId = PollingWorker.prepareInstallId(context)
            ?: return TootApiResult(error = context.getString(R.string.missing_install_id))

        // アクセストークン
        val accessToken = account.getAccessToken()
            ?: return TootApiResult(error = "missing access token.")

        // アクセストークンのダイジェスト
        val tokenDigest = accessToken.digestSHA256Base64Url()

        // クライアント識別子
        val clientIdentifier = "$accessToken$installId".digestSHA256Base64Url()

        val endpoint =
            "${PollingWorker.APP_SERVER}/webpushcallback/${deviceId.encodePercent()}/${account.acct.ascii.encodePercent()}/$flags/$clientIdentifier"

        val newAlerts = JsonObject().apply {
            put("follow", account.notification_follow)
            put(TootNotification.TYPE_ADMIN_SIGNUP, account.notification_follow)
            put("favourite", account.notification_favourite)
            put("reblog", account.notification_boost)
            put("mention", account.notification_mention)
            put("poll", account.notification_vote)
            put("follow_request", account.notification_follow_request)
            put("status", account.notification_post)
            put("update", account.notification_update)
            put("emoji_reaction", account.notification_reaction) // fedibird拡張
        }

        if (!force) {
            canSkipSubscriptionMastodon(
                client = client,
                clientIdentifier = clientIdentifier,
                endpoint = endpoint,
                oldSubscription = oldSubscription,
                newAlerts = newAlerts,
            )?.let { return it }
        }

        // アクセストークンの優先権を取得
        checkDeviceHasPriority(
            client,
            tokenDigest = tokenDigest,
            installId = installId,
        ).let {
            if (it.failed) return it.result
        }

        return when (flags) {
            // 通知設定が全てカラなので、購読を取り消したい
            0 -> unsubscribeMastodon(client)

            // 通知設定が空ではないので購読を行いたい
            else -> subscribeMastodon(
                client = client,
                clientIdentifier = clientIdentifier,
                endpoint = endpoint,
                newAlerts = newAlerts
            )
        }
    }

    private class CheckDeviceHasPriorityResult(
        val result: TootApiResult?,
        val failed: Boolean,
    )

    private suspend fun checkDeviceHasPriority(
        client: TootApiClient,
        tokenDigest: String,
        installId: String,
    ): CheckDeviceHasPriorityResult {
        val r = client.http(
            jsonObject {
                put("token_digest", tokenDigest)
                put("install_id", installId)
            }
                .toPostRequestBuilder()
                .url("${PollingWorker.APP_SERVER}/webpushtokencheck")
                .build()
        )

        fun rvError() = CheckDeviceHasPriorityResult(r, true)
        fun rvOk() = CheckDeviceHasPriorityResult(r, false)

        val res = r.response ?: return rvError()
        return when {
            res.code != 200 -> {
                if (res.code == 403) addLog(context.getString(R.string.token_exported))
                r.caption = "(SubwayTooter App server)"
                client.readBodyString(r)
                rvError()
            }
            else -> {
                res.closeQuietly()
                rvOk()
            }
        }
    }

    // returns null if no error
    private fun checkInstanceVersionMastodon(
        ti: TootInstance,
        subscription404: Boolean,
    ): TootApiResult? {

        // 2.4.0rc1 未満にはプッシュ購読APIはない
        if (!ti.versionGE(TootInstance.VERSION_2_4_0_rc1)) {
            return TootApiResult(
                context.getString(R.string.instance_does_not_support_push_api, ti.version)
            )
        }

        if (subscription404 && flags == 0) {
            when {
                ti.versionGE(TootInstance.VERSION_2_4_0_rc2) -> {
                    // 購読が不要で現在の状況が404だった場合
                    // 2.4.0rc2以降では「購読が存在しない」を示すので何もしなくてよい
                    if (verbose) addLog(context.getString(R.string.push_subscription_not_exists))
                    return TootApiResult()
                }

                else -> {
                    // 2.4.0rc1では「APIが存在しない」と「購読が存在しない」を判別できない
                }
            }
        }
        return null
    }

    private class CheckCurrentSubscriptionResult(
        val result: TootApiResult?,
        val failed: Boolean,
        val is404: Boolean,
    )

    @Suppress("BooleanLiteralArgument")
    private suspend fun checkCurrentSubscription(client: TootApiClient): CheckCurrentSubscriptionResult {
        val r = client.request("/api/v1/push/subscription")
        fun rvError() = CheckCurrentSubscriptionResult(r, true, false)
        fun rvOk() = CheckCurrentSubscriptionResult(r, false, false)
        fun rv404() = CheckCurrentSubscriptionResult(r, false, true)
        val res = r?.response ?: return rvError() // cancelled or missing response

        if (res.code != 200) log.i("${account.acct}: check existing subscription: code=${res.code}")

        return when (res.code) {
            200 -> {
                if (r.error?.isNotEmpty() == true && r.jsonObject == null) {
                    // Pleromaが200応答でもエラーHTMLを返す場合がある
                    addLog(context.getString(R.string.instance_does_not_support_push_api_pleroma))
                    rvError()
                } else {
                    // たぶん購読が存在する
                    rvOk()
                }
            }

            // この時点では存在しないのが購読なのかAPIなのか分からない
            404 -> rv404()

            403 -> {
                // アクセストークンにpushスコープがない
                if (flags != 0 || verbose) addLog(context.getString(R.string.missing_push_scope))
                rvError()
            }

            in 400 until 500 -> {
                addLog(context.getString(R.string.instance_does_not_support_push_api_pleroma))
                rvError()
            }

            else -> {
                addLog("${res.request}")
                addLog("${res.code} ${res.message}")
                rvOk() // 後でリトライする
            }
        }
    }

    private suspend fun canSkipSubscriptionMastodon(
        client: TootApiClient,
        clientIdentifier: String,
        endpoint: String,
        oldSubscription: TootPushSubscription?,
        newAlerts: JsonObject,
    ): TootApiResult? {

        // 購読を解除したいのに古い購読があるのなら、購読の更新が必要
        if (flags == 0 && oldSubscription != null) return null

        // endpoint URLが合わないなら購読の更新が必要
        if (oldSubscription?.endpoint != endpoint) return null

        suspend fun makeSkipResult(): TootApiResult {
            // 既に登録済みで、endpointも一致している
            subscribed = true
            if (verbose) addLog(context.getString(R.string.push_subscription_already_exists))
            return updateServerKey(client, clientIdentifier, oldSubscription.server_key)
        }

        // STがstatus通知に対応した時期に古いサーバでここを通ると
        // flagsの値が変わりendpoint URLも変わった状態で購読を自動更新してしまう
        // しかしそのタイミングではサーバは古いのでサーバ側の購読内容は変化しなかった。

        // サーバ上の購読アラートのリスト
        var alertsOld = oldSubscription.alerts.entries
            .mapNotNull { if (it.value) it.key else null }
            .sorted()

        // 期待する購読アラートのリスト
        var alertsNew = newAlerts.entries
            .mapNotNull { pair -> pair.key.takeIf { pair.value == true } }
            .sorted()

        // 両方に共通するアラートは除去する
        val bothHave = alertsOld.filter { alertsNew.contains(it) }
        alertsOld = alertsOld.filter { !bothHave.contains(it) }
        alertsNew = alertsNew.filter { !bothHave.contains(it) }

        // サーバのバージョンを調べる前に、この時点でalertsが一致するか確認する
        if (alertsOld.joinToString(",") == alertsNew.joinToString(",")) {
            log.v("${account.acct}: same alerts(1)")
            return makeSkipResult()
        }

        // ここでサーバのバージョンによって対応が変わる
        val (ti, result) = TootInstance.get(client)
        ti ?: return result

        // サーバが知らないアラート種別は比較対象から除去する
        fun Iterable<String>.knownOnly() = filter {
            when (it) {
                "follow",
                "mention",
                "favourite",
                "reblog",
                -> true
                "poll",
                -> ti.versionGE(TootInstance.VERSION_2_8_0_rc1)
                "follow_request",
                -> ti.versionGE(TootInstance.VERSION_3_1_0_rc1)
                "status",
                -> ti.versionGE(TootInstance.VERSION_3_3_0_rc1)
                "emoji_reaction" ->
                    ti.versionGE(TootInstance.VERSION_3_4_0_rc1) &&
                            InstanceCapability.emojiReaction(account, ti)
                "update",
                TootNotification.TYPE_ADMIN_SIGNUP,
                -> ti.versionGE(TootInstance.VERSION_3_5_0_rc1)

                else -> {
                    log.w("${account.acct}: unknown alert '$it'. server version='${ti.version}'")
                    false // 未知のアラートの差異は比較しない
                }
            }
        }
        alertsOld = alertsOld.knownOnly()
        alertsNew = alertsNew.knownOnly()

        return if (alertsOld.joinToString(",") == alertsNew.joinToString(",")) {
            log.v("${account.acct}: same alerts(2)")
            makeSkipResult()
        } else {
            addLog("${account.acct}: alerts not match. account=${account.acct.pretty} old=${alertsOld.sorted()}, new=${alertsNew.sorted()}")
            null
        }
    }

    private suspend fun unsubscribeMastodon(
        client: TootApiClient,
    ): TootApiResult? {

        val r = client.request("/api/v1/push/subscription", Request.Builder().delete())
        val res = r?.response ?: return r

        return when (res.code) {
            200 -> {
                if (verbose) addLog(context.getString(R.string.push_subscription_deleted))
                TootApiResult()
            }

            404 -> {
                if (verbose) {
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
    }

    private suspend fun subscribeMastodon(
        client: TootApiClient,
        clientIdentifier: String,
        endpoint: String,
        newAlerts: JsonObject,
    ): TootApiResult? {
        @Suppress("SpellCheckingInspection")
        val params = JsonObject().apply {
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
                put("alerts", newAlerts)
                account.push_policy?.let { put("policy", it) }
            })
        }

        val r = client.request(
            "/api/v1/push/subscription",
            params.toPostRequestBuilder()
        ) ?: return null

        val res = r.response ?: return r

        return when (res.code) {
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
                if (verbose) addLog(context.getString(R.string.push_subscription_updated))

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
