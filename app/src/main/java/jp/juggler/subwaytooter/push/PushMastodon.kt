package jp.juggler.subwaytooter.push

import jp.juggler.crypt.defaultSecurityProvider
import jp.juggler.crypt.encodeP256Dh
import jp.juggler.crypt.generateKeyPair
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.ApiError
import jp.juggler.subwaytooter.api.push.ApiPushMastodon
import jp.juggler.subwaytooter.pref.PrefDevice
import jp.juggler.subwaytooter.pref.lazyContext
import jp.juggler.subwaytooter.pref.prefDevice
import jp.juggler.subwaytooter.push.PushRepo.Companion.followDomain
import jp.juggler.subwaytooter.table.*
import jp.juggler.util.data.decodeBase64
import jp.juggler.util.data.encodeBase64Url
import jp.juggler.util.data.notBlank
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory
import jp.juggler.util.time.parseTimeIso8601
import java.security.Provider
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey

private val log = LogCategory("PushMastodon")

class PushMastodon(
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
        a: SavedAccount,
        willRemoveSubscription: Boolean,
        forceUpdate: Boolean,
    ) {
        val deviceHash = deviceHash(a)
        val newUrl = snsCallbackUrl(a) // appServerHashを参照する
        if (newUrl.isNullOrEmpty()) {
            if (willRemoveSubscription) {
                val msg =
                    lazyContext.getString(R.string.push_subscription_app_server_hash_missing_but_ok)
                subLog.i(msg)
            } else {
                val msg =
                    lazyContext.getString(R.string.push_subscription_app_server_hash_missing_error)
                subLog.e(msg)
                daoAccountNotificationStatus.updateSubscriptionError(
                    a.acct,
                    msg
                )
            }
            return
        }

        val oldSubscription = try {
            api.getPushSubscription(a)
        } catch (ex: Throwable) {
            if ((ex as? ApiError)?.response?.code == 404) {
                null
            } else {
                throw ex
            }
        }
        log.i("${a.acct} oldSubscription=${oldSubscription}")

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
                if (params["dh"] != deviceHash) {
                    // この端末で作成した購読ではない。
                    log.w("deviceHash not match. keep it for other devices. ${a.acct} $oldEndpointUrl")
                    subLog.e(R.string.push_subscription_exists_but_not_created_by_this_device)
                    return
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
                    api.deletePushSubscription(a)
                }
            }
            return
        }

        val alerts = ApiPushMastodon.alertTypes.associateWith { true }

        if (newUrl == oldEndpointUrl && !forceUpdate) {
            // エンドポイントURLに変化なし
            // TODO: Alert種別による変更
            subLog.i(R.string.push_subscription_keep_using)
            return
        }

        subLog.i(R.string.push_subscription_creating)
        val keyPair = provider.generateKeyPair()
        val auth = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val p256dh = encodeP256Dh(keyPair.public as ECPublicKey)
        val response = api.createPushSubscription(
            a = a,
            endpointUrl = newUrl,
            p256dh = p256dh.encodeBase64Url(),
            auth = auth.encodeBase64Url(),
            alerts = alerts,
            policy = "all",
        )
        val serverKeyStr = response.string("server_key")
            ?: error("missing server_key.")

        val serverKey = serverKeyStr.decodeBase64()

        // p256dhは65バイトのはず
        // authは16バイトのはず
        // serverKeyは65バイトのはず

        // 登録できたらアカウントに覚える
        daoStatus.savePushKey(
            acct = a.acct,
            pushKeyPrivate = keyPair.private.encoded,
            pushKeyPublic = p256dh,
            pushAuthSecret = auth,
            pushServerKey = serverKey,
            lastPushEndpoint = newUrl,
        )
        subLog.i(R.string.push_subscription_completed)
    }

    override suspend fun formatPushMessage(
        a: SavedAccount,
        pm: PushMessage,
    ) {
        val json = pm.messageJson ?: return
        val apiHost = a.apiHost

        pm.notificationType = json.string("notification_type")
        pm.iconLarge = json.string("icon").followDomain(apiHost)
        pm.text = arrayOf(
            // あなたのトゥートが tateisu 🤹 さんにお気に入り登録されました
            json.string("title"),
            // 対象の投稿の本文？
            json.string("body"),
            // 対象の投稿の本文？ (古い
            json.jsonObject("data")?.string("content"),
        ).mapNotNull { it?.trim()?.notBlank() }.joinToString("\n")
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

                pm.iconSmall = json.string("badge").followDomain(apiHost)
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
    }
}
