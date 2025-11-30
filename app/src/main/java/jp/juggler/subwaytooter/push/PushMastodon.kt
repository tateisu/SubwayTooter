package jp.juggler.subwaytooter.push

import android.content.Context
import jp.juggler.crypt.defaultSecurityProvider
import jp.juggler.crypt.encodeP256Dh
import jp.juggler.crypt.generateKeyPair
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.ApiError
import jp.juggler.subwaytooter.api.TootApiCallback
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.auth.AuthMastodon
import jp.juggler.subwaytooter.api.entity.InstanceCapability
import jp.juggler.subwaytooter.api.entity.NotificationType
import jp.juggler.subwaytooter.api.entity.NotificationType.Companion.toNotificationType
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.api.entity.TootPushSubscription
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.api.push.ApiPushMastodon
import jp.juggler.subwaytooter.pref.PrefDevice
import jp.juggler.subwaytooter.pref.lazyContext
import jp.juggler.subwaytooter.pref.prefDevice
import jp.juggler.subwaytooter.table.AccountNotificationStatus
import jp.juggler.subwaytooter.table.PushMessage
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.appDatabase
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.decodeBase64
import jp.juggler.util.data.digestSHA256Base64Url
import jp.juggler.util.data.ellipsizeDot3
import jp.juggler.util.data.encodeBase64Url
import jp.juggler.util.data.notBlank
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory
import jp.juggler.util.time.parseTimeIso8601
import kotlinx.coroutines.isActive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.Provider
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import kotlin.String
import kotlin.coroutines.coroutineContext

private val log = LogCategory("PushMastodon")

class PushMastodon(
    private val context: Context,
    private val api: ApiPushMastodon,
    private val provider: Provider =
        defaultSecurityProvider,
    override val prefDevice: PrefDevice =
        lazyContext.prefDevice,
    override val daoStatus: AccountNotificationStatus.Access =
        AccountNotificationStatus.Access(appDatabase),
) : PushBase() {
    override suspend fun updateSubscription(
        subLog: SubscriptionLogger,
        account: SavedAccount,
        willRemoveSubscription: Boolean,
        forceUpdate: Boolean,
    ): String? {
        val deviceHash = deviceHash(account)
        val newUrl = snsCallbackUrl(account) // appServerHashを参照する
        if (newUrl.isNullOrEmpty()) {
            return when {
                willRemoveSubscription -> {
                    val msg = lazyContext.getString(
                        R.string.push_subscription_app_server_hash_missing_but_ok
                    )
                    subLog.i(msg)
                    null
                }

                else -> lazyContext.getString(
                    R.string.push_subscription_app_server_hash_missing_error
                )
            }
        }

        if (AuthMastodon.DEBUG_AUTH) log.i("DEBUG_AUTH bearerAccessToken=${account.bearerAccessToken} ${account.acct}")
        val oldSubscription = try {
            subLog.i("check current subscription…")
            api.getPushSubscription(account)
        } catch (ex: Throwable) {
            if ((ex as? ApiError)?.response?.code == 404) {
                null
            } else {
                throw ex
            }
        }
        val oldEndpointUrl = oldSubscription?.string("endpoint")
        when (oldEndpointUrl) {
            // 購読がない。作ってもよい
            null -> Unit
            else -> {
                val params = buildMap {
                    if (oldEndpointUrl.startsWith(appServerUrlPrefix)) {
                        oldEndpointUrl.substring(appServerUrlPrefix.length)
                            .split("/")
                            .forEach { pair ->
                                val cols = pair.split("_", limit = 2)
                                cols.elementAtOrNull(0)?.notEmpty()?.let { k ->
                                    put(k, cols.elementAtOrNull(1) ?: "")
                                }
                            }
                    }
                }
                if (params["dh"] != deviceHash && !isOldSubscription(account, oldEndpointUrl)) {
                    // この端末で作成した購読ではない。
                    log.w("deviceHash not match. keep it for other devices. ${account.acct} $oldEndpointUrl")
                    return context.getString(
                        R.string.push_subscription_exists_but_not_created_by_this_device
                    )
                }
            }
        }

        if (willRemoveSubscription) {
            when (oldSubscription) {
                null -> {
                    subLog.i(R.string.push_subscription_is_not_required)
                }

                else -> {
                    subLog.i(R.string.push_subscription_delete_current)
                    api.deletePushSubscription(account)
                }
            }
            return null
        }

        // サーバのバージョンを見て、サーバの知らないalertを無視して比較する
        val client = TootApiClient(context, callback = object : TootApiCallback {
            override suspend fun isApiCancelled(): Boolean = !coroutineContext.isActive
        })
        client.account = account
        val ti = TootInstance.getExOrThrow(client)

        try {
            account.disableNotificationsByServer(ti)
        } catch (ex: Throwable) {
            log.w(ex, "disableNotificationsByServer failed.")
        }

        val newAlerts = account.alerts(ti)

        val isSameAlert = isSameAlerts(
            subLog = subLog,
            account = account,
            ti = ti,
            oldSubscriptionJson = oldSubscription,
            newAlerts = newAlerts,
        )

        // https://github.com/mastodon/mastodon/pull/23210
        // ポリシーの変更をチェックできるようになるのは4.1くらい？
        val isSamePolicy = true // account.pushPolicy == oldSubscription.

        if (!forceUpdate && isSameAlert && isSamePolicy &&
            newUrl == oldEndpointUrl
        ) {
            // 現在の更新を使い続ける
            subLog.i(R.string.push_subscription_keep_using)
            return null
        }

        log.i("${account.acct} oldSubscription=$oldSubscription")

        if (newUrl == oldEndpointUrl) {
            subLog.i(R.string.push_subscription_exists_updateing)
            api.updatePushSubscriptionData(
                a = account,
                alerts = newAlerts,
                policy = account.pushPolicy ?: "all",
            )
            subLog.i(R.string.push_subscription_updated)
        } else {
            subLog.i(R.string.push_subscription_creating)
            val keyPair = provider.generateKeyPair()
            val auth = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val p256dh = encodeP256Dh(keyPair.public as ECPublicKey)
            val response = api.createPushSubscription(
                a = account,
                endpointUrl = newUrl,
                p256dh = p256dh.encodeBase64Url(),
                auth = auth.encodeBase64Url(),
                alerts = newAlerts,
                policy = account.pushPolicy ?: "all",
            )
            val serverKeyStr = response.string("server_key")
                ?: error("missing server_key.")

            val serverKey = serverKeyStr.decodeBase64()

            // p256dhは65バイトのはず
            // authは16バイトのはず
            // serverKeyは65バイトのはず

            // 登録できたらアカウントに覚える
            daoStatus.savePushKey(
                acct = account.acct,
                pushKeyPrivate = keyPair.private.encoded,
                pushKeyPublic = p256dh,
                pushAuthSecret = auth,
                pushServerKey = serverKey,
                lastPushEndpoint = newUrl,
            )
            subLog.i(R.string.push_subscription_completed)
        }
        return null
    }

    private fun isOldSubscription(account: SavedAccount, url: String): Boolean {
        //        https://mastodon-msg.juggler.jp
        //        /webpushcallback
        //        /{ deviceId(FCM token) }
        //        /{ acct }
        //        /{flags }
        //        /{ client identifier}

        val clientIdentifierOld = url.toHttpUrlOrNull()?.pathSegments?.elementAtOrNull(4)
            ?: return false
        val installId = prefDevice.installIdV1?.notEmpty() ?: return false
        val accessToken = account.bearerAccessToken?.notEmpty() ?: return false
        val clientIdentifier = "$accessToken$installId".digestSHA256Base64Url()
        return clientIdentifier == clientIdentifierOld
    }

    private fun isSameAlerts(
        subLog: SubscriptionLogger,
        account: SavedAccount,
        ti: TootInstance,
        oldSubscriptionJson: JsonObject?,
        newAlerts: JsonObject,
    ): Boolean {
        oldSubscriptionJson ?: return false
        val oldSubscription = TootPushSubscription(oldSubscriptionJson)

        // STがstatus通知に対応した時期に古いサーバでここを通ると
        // flagsの値が変わりendpoint URLも変わった状態で購読を自動更新してしまう
        // しかしそのタイミングではサーバは古いのでサーバ側の購読内容は変化しなかった。

        // 既存の購読のアラートのリスト
        var alertsOld = oldSubscription.alerts.entries
            .mapNotNull { if (it.value) it.key else null }
            .sorted()

        // 期待する購読アラートのリスト
        var alertsNew = newAlerts.entries
            .mapNotNull { pair -> pair.key.takeIf { pair.value == true } }
            .sorted()

        // 両方に共通するアラートは除去する
        // サーバが知らないアラートは除去する
        val bothHave = alertsOld.filter { alertsNew.contains(it) }
        alertsOld =
            alertsOld.filter { !bothHave.contains(it) }.knownOnly(account, ti)
        alertsNew =
            alertsNew.filter { !bothHave.contains(it) }.knownOnly(account, ti)
        return if (alertsOld.joinToString(",") == alertsNew.joinToString(",")) {
            true
        } else {
            log.i("${account.acct}: changed. old=${alertsOld.sorted()}, new=${alertsNew.sorted()}")
            subLog.i("notification type set changed.")
            false
        }
    }

    // サーバが知らないアラート種別は比較対象から除去する
    // サーバから取得できるalertsがおかしいサーバのバージョンなどあり、
    // 購読時に指定可能かどうかとは微妙に条件が異なる
    private fun Iterable<String>.knownOnly(
        account: SavedAccount,
        ti: TootInstance,
    ) = filter {
        when(val type = it.toNotificationType()) {
            // 未知のアラートの差異は比較しない。でないと購読を何度も繰り返すことになる
            is NotificationType.Unknown -> {
                log.w("${account.acct}: unknown alert '$it'. server version='${ti.version}'")
                false
            }

            // 投稿の編集の通知は3.5.0rcから使えるはずだが、
            // この比較ではバージョン4未満なら比較対象から除外する。
            // 何らかの不具合へのワークアラウンドだったような…
            NotificationType.Update -> ti.versionGE(TootInstance.VERSION_4_0_0)

            // 管理者向けのユーザサインアップ通知はalertから読めない時期があった。
            // よって4.0.0未満では比較対象から除外する。
            NotificationType.AdminSignup -> ti.versionGE(TootInstance.VERSION_4_0_0)

            // 他はalertsを組み立てるときと同じコードで判定する
            else -> canSubscribe(type, account, ti)
        }
    }

    /**
     * ある通知種別をalertsで購読できるなら真
     */
    private fun canSubscribe(
        type:NotificationType,
        account: SavedAccount,
        ti: TootInstance,
    ):Boolean = when(type) {
        // 昔からあった
        NotificationType.Follow -> true
        NotificationType.Favourite -> true
        NotificationType.Mention -> true
        NotificationType.Reblog -> true

        // Mastodon 2.8 投票完了
        NotificationType.Poll -> ti.versionGE(TootInstance.VERSION_2_8_0_rc1)

        // Mastodon 3.1.0 フォローリクエストを受信した
        NotificationType.FollowRequest -> ti.versionGE(TootInstance.VERSION_3_1_0_rc1)

        // Mastodon 3.3.0 指定ユーザからの投稿
        NotificationType.Status -> ti.versionGE(TootInstance.VERSION_3_3_0_rc1)

        // Mastodon 3.5.0 Fav/Boost/Reply した投稿が編集された
        NotificationType.Update -> ti.versionGE(TootInstance.VERSION_3_5_0_rc1)

        // Mastodon 3.5.0 管理者向け、ユーザサインアップ
        NotificationType.AdminSignup -> ti.versionGE(TootInstance.VERSION_3_5_0_rc1)

        // Mastodon 4.0.0 管理者向け、ユーザが通報を作成した
        NotificationType.AdminReport -> ti.versionGE(TootInstance.VERSION_4_0_0)

        // (Mastodon 4.3) サーバ間の関係が断絶した。
        NotificationType.SeveredRelationships -> ti.versionGE(TootInstance.VERSION_4_3_0)

        // Fedibird 絵文字リアクション通知
        NotificationType.EmojiReactionFedibird ->
            InstanceCapability.canReceiveEmojiReactionFedibird(ti)

        // Pleroma 絵文字リアクション通知
        NotificationType.EmojiReactionPleroma ->
            InstanceCapability.canReceiveEmojiReactionPleroma(ti)

        // Fedibird 投稿の参照の通知
        NotificationType.StatusReference ->
            InstanceCapability.statusReference(account, ti)

        // Fedibird 予約投稿の通知
        NotificationType.ScheduledStatus ->
            InstanceCapability.canReceiveScheduledStatus(account, ti)

        // https://github.com/fedibird/mastodon/blob/fedibird/app/controllers/api/v1/push/subscriptions_controller.rb#L55
        // https://github.com/fedibird/mastodon/blob/fedibird/app/models/notification.rb

        // 他、Misskey用の通知タイプなどはMastodonのプッシュ購読対象ではない
        else -> false
    }

    /**
     * プッシュ通知種別ごとに購読の有無を指定するJsonObjectを作成する
     */
    private fun SavedAccount.alerts(ti: TootInstance) = JsonObject().also { dst ->
        for (type in NotificationType.allKnown) {
            if( canSubscribe(type,this,ti)){
                // 購読可能なAlertのcodeごとにtrue,falseを設定する
                dst[type.code] = isNotificationEnabled(type)
                // Note: 未知の通知はallKnownには含まれない
            }
        }
    }

    override suspend fun formatPushMessage(
        a: SavedAccount,
        pm: PushMessage,
    ) {
        val json = pm.messageJson ?: error("missing messageJson")

        pm.notificationType = json.string("notification_type")
        pm.iconLarge = a.supplyBaseUrl(json.string("icon"))

        pm.text = arrayOf(
            // あなたのトゥートが tateisu 🤹 さんにお気に入り登録されました
            json.string("title"),
        ).mapNotNull { it?.trim()?.notBlank() }
            .joinToString("\n")
            .ellipsizeDot3(128)

        pm.textExpand = arrayOf(
            // あなたのトゥートが tateisu 🤹 さんにお気に入り登録されました
            json.string("title"),
            // 対象の投稿の本文？
            json.string("body"),
            // 対象の投稿の本文？ (古い
            json.jsonObject("data")?.string("content"),
        ).mapNotNull { it?.trim()?.notBlank() }
            .joinToString("\n")
            .ellipsizeDot3(400)

        when {
            pm.notificationType.isNullOrEmpty() -> {
                // old mastodon
                // {
                //  "title": "あなたのトゥートが tateisu 🤹 さんにお気に入り登録されました",
                //  "image": null,
                //  "badge": "https://mastodon2.juggler.jp/badge.png",
                //  "tag": 84,
                //  "timestamp": "2018-05-11T17:06:42.887Z",
                //  "icon": "/system/accounts/avatars/000/000/003/original/72f1da33539be11e.jpg",
                //  "data": {
                //      "content": ":enemy_bullet:",
                //      "nsfw": null,
                //      "url": "https://mastodon2.juggler.jp/web/statuses/98793123081777841",
                //      "actions": [],
                //      "access_token": null,
                //      "message": "%{count} 件の通知",
                //      "dir": "ltr"
                //  }
                // }

                json.string("timestamp")?.parseTimeIso8601()
                    ?.let { pm.timestamp = it }

                // 重複排除は完全に諦める
                pm.notificationId = pm.timestamp.toString()

                pm.iconSmall = a.supplyBaseUrl(json.string("badge"))
            }

            else -> {
                // Mastodon 4.0
                // {
                //  "access_token": "***",
                //  "preferred_locale": "ja",
                //  "notification_id": 341897,
                //  "notification_type": "favourite",
                //  "icon": "https://m1j.zzz.ac/aed1...e5343f2e7b.png",
                //  "title": "tateisu⛏️@テスト鯖 :ct080:さんにお気に入りに登録されました",
                //  "body": "テスト"
                // }

                pm.notificationId = json.string("notification_id")

                // - iconSmall は通知タイプに合わせてアプリが用意するらしい
                // - タイムスタンプ情報はない。
            }
        }

        // 通知のミュートについて:
        // - アプリ名がないのでアプリ名ミュートは使えない
        // - notification.user のfull acct がないのでふぁぼ魔ミュートは行えない
        // - テキスト本文のミュートは…部分的には可能

        if (pm.textExpand?.let { TootStatus.muted_word?.matchShort(it) } == true) {
            error("muted by text word.")
        }

//        // ふぁぼ魔ミュート
//        when ( pm.notificationType) {
//            NotificationType.REBLOG,
//            NotificationType.FAVOURITE,
//            NotificationType.FOLLOW,
//            NotificationType.FOLLOW_REQUEST,
//            NotificationType.FOLLOW_REQUEST_MISSKEY,
//            -> {
//                val whoAcct = a.getFullAcct(user)
//                if (TootStatus.favMuteSet?.contains(whoAcct) == true) {
//                    error("muted by favMuteSet ${whoAcct.pretty}")
//                }
//            }
//        }
    }
}
