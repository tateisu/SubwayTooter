package jp.juggler.subwaytooter.push

import jp.juggler.crypt.defaultSecurityProvider
import jp.juggler.crypt.encodeP256Dh
import jp.juggler.crypt.generateKeyPair
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.ApiError
import jp.juggler.subwaytooter.api.push.ApiPushMisskey
import jp.juggler.subwaytooter.pref.PrefDevice
import jp.juggler.subwaytooter.pref.lazyContext
import jp.juggler.subwaytooter.pref.prefDevice
import jp.juggler.subwaytooter.push.PushRepo.Companion.followDomain
import jp.juggler.subwaytooter.table.*
import jp.juggler.util.data.*

import java.security.Provider
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey

class PushMisskey(
    private val api: ApiPushMisskey,
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
        val newUrl = snsCallbackUrl(a)

        val lastEndpointUrl = daoStatus.lastEndpointUrl(a.acct)
            ?: newUrl

        var status = daoStatus.load(a.acct)

        @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
        var hasEmptySubscription = false

        if (!lastEndpointUrl.isNullOrEmpty()) {
            val lastSubscription = when (lastEndpointUrl) {
                null, "" -> null
                else -> try {
                    // Misskeyは2022/12/18に現在の購読を確認するAPIができた
                    api.getPushSubscription(a, lastEndpointUrl)
                    // 購読がない => 空オブジェクト (v13 drdr.club でそんな感じ)
                } catch (ex: Throwable) {
                    // APIがない => 404 (v10 めいすきーのソースと動作で確認)
                    when ((ex as? ApiError)?.response?.code) {
                        in 400 until 500 -> null
                        else -> throw ex
                    }
                }
            }

            if (lastSubscription != null) {
                if (lastSubscription.size == 0) {
                    // 購読がないと空レスポンスになり、アプリ側で空オブジェクトに変換される
                    @Suppress("UNUSED_VALUE")
                    hasEmptySubscription = true
                } else if (lastEndpointUrl == newUrl && !willRemoveSubscription && !forceUpdate) {
                    when (lastSubscription.boolean("sendReadMessage")) {
                        false -> subLog.i(R.string.push_subscription_keep_using)
                        else -> {
                            // 未読クリア通知はオフにしたい
                            api.updatePushSubscription(a, newUrl, sendReadMessage = false)
                            subLog.i(R.string.push_subscription_off_unread_notification)
                        }
                    }
                    return
                } else {
                    // 古い購読はあったが、削除したい
                    api.deletePushSubscription(a, lastEndpointUrl)
                    daoStatus.deleteLastEndpointUrl(a.acct)
                    if (willRemoveSubscription) {
                        subLog.i(R.string.push_subscription_delete_current)
                        return
                    }
                }
            }
        }
        if (newUrl == null) {
            if (willRemoveSubscription) {
                subLog.i(R.string.push_subscription_app_server_hash_missing_but_ok)
            } else {
                subLog.e(R.string.push_subscription_app_server_hash_missing_error)
            }
            return
        } else if (willRemoveSubscription) {
            // 購読を解除したい。
            // hasEmptySubscription が真なら購読はないが、
            // とりあえず何か届いても確実に読めないようにする
            when (status?.pushKeyPrivate) {
                null -> subLog.i(R.string.push_subscription_is_not_required)
                else -> {
                    daoStatus.deletePushKey(a.acct)
                    subLog.i(R.string.push_subscription_is_not_required_delete_secret_keys)
                }
            }
            return
        }

        // 鍵がなければ作る
        if (status?.pushKeyPrivate == null ||
            status.pushKeyPublic == null ||
            status.pushAuthSecret == null
        ) {
            subLog.i(R.string.push_subscription_creating_new_secret_key)
            val keyPair = provider.generateKeyPair()
            val auth = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val p256dh = encodeP256Dh(keyPair.public as ECPublicKey)
            daoStatus.savePushKey(
                a.acct,
                pushKeyPrivate = keyPair.private.encoded,
                pushKeyPublic = p256dh,
                pushAuthSecret = auth,
            )
            status = daoStatus.load(a.acct)
        }

        // 購読する
        subLog.i(R.string.push_subscription_creating)
        status!!
        val json = api.createPushSubscription(
            a = a,
            endpoint = newUrl,
            auth = status.pushAuthSecret!!.encodeBase64Url(),
            publicKey = status.pushKeyPublic!!.encodeBase64Url(),
            sendReadMessage = false,
        )
        // https://github.com/syuilo/misskey/issues/2541
        // https://github.com/syuilo/misskey/commit/4c6fb60dd25d7e2865fc7c4d97728593ffc3c902
        // 2018/9/1 の上記コミット以降、Misskeyでもサーバ公開鍵を得られるようになった
        val serverKey = json.string("key")
            ?.notEmpty()?.decodeBase64()
            ?: error("missing server key in response of sw/register API.")
        if (!serverKey.contentEquals(status.pushServerKey)) {
            daoStatus.saveServerKey(
                acct = a.acct,
                lastPushEndpoint = newUrl,
                pushServerKey = serverKey,
            )
            subLog.i(R.string.push_subscription_server_key_saved)
        }
        subLog.i(R.string.push_subscription_completed)
    }

    /*
       https://github.com/syuilo/misskey/blob/master/src/services/create-notification.ts#L46
       Misskeyは通知に既読の概念があり、イベント発生後2秒たっても未読の時だけプッシュ通知が発生する。
       WebUIを開いていると通知はすぐ既読になるのでプッシュ通知は発生しない。
       プッシュ通知のテスト時はST2台を使い、片方をプッシュ通知の受信チェック、もう片方を投稿などの作業に使うことになる。
    */
    override suspend fun formatPushMessage(
        a: SavedAccount,
        pm: PushMessage,
    ) {
        val json = pm.messageJson ?: return
        val apiHost = a.apiHost

        pm.iconSmall = null // バッジ画像のURLはない。通知種別により決まる

        json.long("dateTime")?.let {
            pm.timestamp = it
        }

        val body = json.jsonObject("body")

        val user = body?.jsonObject("user")

        pm.iconLarge = user?.string("avatarUrl").followDomain(apiHost)

        when (val eventType = json.string("type")) {
            "notification" -> {
                val notificationType = body?.string("type")

                pm.notificationType = notificationType

                pm.text = arrayOf(
                    user?.string("username"),
                    notificationType,
                    body?.string("text")?.takeIf {
                        when (notificationType) {
                            "mention", "quote" -> true
                            else -> false
                        }
                    }
                ).mapNotNull { it?.trim()?.notBlank() }.joinToString("\n").ellipsizeDot3(128)
                pm.textExpand = arrayOf(
                    user?.string("username"),
                    notificationType,
                    body?.string("text")?.takeIf {
                        when (notificationType) {
                            "mention", "quote" -> true
                            else -> false
                        }
                    }
                ).mapNotNull { it?.trim()?.notBlank() }.joinToString("\n").ellipsizeDot3(400)
            }
            // 通知以外のイベントは全部無視したい
            else -> error("謎のイベント $eventType user=${user?.string("username")}")
        }
    }
}
