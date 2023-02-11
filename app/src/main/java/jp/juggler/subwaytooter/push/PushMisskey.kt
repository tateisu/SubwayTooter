package jp.juggler.subwaytooter.push

import android.content.Context
import jp.juggler.crypt.defaultSecurityProvider
import jp.juggler.crypt.encodeP256Dh
import jp.juggler.crypt.generateKeyPair
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.ApiError
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.TootAccount.Companion.tootAccount
import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.api.entity.parseItem
import jp.juggler.subwaytooter.api.push.ApiPushMisskey
import jp.juggler.subwaytooter.pref.PrefDevice
import jp.juggler.subwaytooter.pref.lazyContext
import jp.juggler.subwaytooter.pref.prefDevice
import jp.juggler.subwaytooter.table.*
import jp.juggler.util.data.*
import jp.juggler.util.log.LogCategory
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.Provider
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey

class PushMisskey(
    private val context: Context,
    private val api: ApiPushMisskey,
    private val provider: Provider =
        defaultSecurityProvider,
    override val prefDevice: PrefDevice =
        lazyContext.prefDevice,
    override val daoStatus: AccountNotificationStatus.Access =
        AccountNotificationStatus.Access(appDatabase),
) : PushBase() {
    companion object {
        private val log = LogCategory("PushMisskey")
    }

    override suspend fun updateSubscription(
        subLog: SubscriptionLogger,
        account: SavedAccount,
        willRemoveSubscription: Boolean,
        forceUpdate: Boolean,
    ): String? {
        val newUrl = snsCallbackUrl(account)

        val lastEndpointUrl = daoStatus.lastEndpointUrl(account.acct)
            ?: newUrl

        var status = daoStatus.load(account.acct)

        @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
        var hasEmptySubscription = false

        if (!lastEndpointUrl.isNullOrEmpty()) {
            val lastSubscription = when (lastEndpointUrl) {
                null, "" -> null
                else -> try {
                    subLog.i("check current subscription…")
                    // Misskeyは2022/12/18に現在の購読を確認するAPIができた
                    api.getPushSubscription(account, lastEndpointUrl)
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
                            api.updatePushSubscription(account, newUrl, sendReadMessage = false)
                            subLog.i(R.string.push_subscription_off_unread_notification)
                        }
                    }
                    return null
                } else {
                    // 古い購読はあったが、削除したい
                    api.deletePushSubscription(account, lastEndpointUrl)
                    daoStatus.deleteLastEndpointUrl(account.acct)
                    if (willRemoveSubscription) {
                        subLog.i(R.string.push_subscription_delete_current)
                        return null
                    }
                }
            }
        }
        if (newUrl == null) {
            return when {
                willRemoveSubscription -> {
                    subLog.i(R.string.push_subscription_app_server_hash_missing_but_ok)
                    null
                }
                else -> context.getString(
                    R.string.push_subscription_app_server_hash_missing_error
                )
            }
        } else if (willRemoveSubscription) {
            // 購読を解除したい。
            // hasEmptySubscription が真なら購読はないが、
            // とりあえず何か届いても確実に読めないようにする
            when (status?.pushKeyPrivate) {
                null -> subLog.i(R.string.push_subscription_is_not_required)
                else -> {
                    daoStatus.deletePushKey(account.acct)
                    subLog.i(R.string.push_subscription_is_not_required_delete_secret_keys)
                }
            }
            return null
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
                account.acct,
                pushKeyPrivate = keyPair.private.encoded,
                pushKeyPublic = p256dh,
                pushAuthSecret = auth,
            )
            status = daoStatus.load(account.acct)
        }

        // 購読する
        subLog.i(R.string.push_subscription_creating)
        status!!
        val json = api.createPushSubscription(
            a = account,
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
                acct = account.acct,
                lastPushEndpoint = newUrl,
                pushServerKey = serverKey,
            )
            subLog.i(R.string.push_subscription_server_key_saved)
        }
        subLog.i(R.string.push_subscription_completed)
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
        val accessToken = account.misskeyApiToken?.notEmpty() ?: return false
        val clientIdentifier = "$accessToken$installId".digestSHA256Base64Url()
        return clientIdentifier == clientIdentifierOld
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
        val json = pm.messageJson ?: error("missign messageJson")

        when (val eventType = json.string("type")) {
            "notification" -> {
                val body = json.jsonObject("body")
                    ?: error("missing body of notification")
                val parser = TootParser(context, a)

                val whoJson = body.jsonObject("user")
                var who = parseItem(whoJson) { tootAccount(parser, it) }

                body.jsonObject("note")?.let { noteJson ->
                    if (noteJson["user"] == null) {
                        noteJson["user"] = when (noteJson.string("userId")) {
                            null, "" -> null
                            who?.id?.toString() -> whoJson
                            a.loginAccount?.id?.toString() -> a.loginAccount?.json
                            else -> null
                        }
                    }
                }

                val notification = parser.notification(body)
                    ?: error("can't parse notification. json=$body")

                who = notification.account

                // アプリミュートと単語ミュート
                if (notification.status?.checkMuted() == true) {
                    error("this message is muted by app or word.")
                }

                // ふぁぼ魔ミュート
                when (notification.type) {
                    TootNotification.TYPE_REBLOG,
                    TootNotification.TYPE_FAVOURITE,
                    TootNotification.TYPE_FOLLOW,
                    TootNotification.TYPE_FOLLOW_REQUEST,
                    TootNotification.TYPE_FOLLOW_REQUEST_MISSKEY,
                    -> {
                        val whoAcct = a.getFullAcct(who)
                        if (TootStatus.favMuteSet?.contains(whoAcct) == true) {
                            error("muted by favMuteSet ${whoAcct.pretty}")
                        }
                    }
                }

                // バッジ画像のURLはない。通知種別により決まる
                pm.iconSmall = null
                pm.iconLarge = a.supplyBaseUrl(who?.avatar_static)
                pm.notificationType = notification.type
                pm.notificationId = notification.id.toString()

                json.long("dateTime")?.let { pm.timestamp = it }

                pm.text = arrayOf(
                    notification.getNotificationLine(context),
                ).mapNotNull { it.trim().notBlank() }
                    .joinToString("\n")
                    .ellipsizeDot3(128)

                pm.textExpand = arrayOf(
                    pm.text,
                    notification.status?.decoded_content,
                ).mapNotNull { it?.trim()?.notBlank() }
                    .joinToString("\n")
                    .ellipsizeDot3(400)
            }

            // 通知以外のイベントは全部無視したい
            else -> error("謎のイベント $eventType json=$json")
        }
    }
}
/*

Misskey13
{
	"type": "notification",
	"body": {
		"id": "9ayflq5wj4",
		"createdAt": "2023-02-07T23:22:38.132Z",
		"type": "reaction",
		"isRead": false,
		"userId": "80jbzppr37",
		"user": {
			"id": "80jbzppr37",
			"name": "tateisu🔧",
			"username": "tateisu",
			"host": "fedibird.com",
			"avatarUrl": "https://nos3.arkjp.net/avatar.webp?url=https%3A%2F%2Fs3.fedibird.com%2Faccounts%2Favatars%2F000%2F010%2F223%2Foriginal%2Fb7ace6ef7eaaf49f.png&avatar=1",
			"avatarBlurhash": "yMMHS-t71NWX~qx]%2yEf6i_kCoKn%M{tSkCoJaeM{ayoeyEWBxtt7IAWBWqShkCi_WBt7jZRkMxayt6aeWray%Mxvj[oeofM|WBRj",
			"isBot": false,
			"isCat": false,
			"instance": {
				"name": "Fedibird",
				"softwareName": "fedibird",
				"softwareVersion": "0.1",
				"iconUrl": "https://fedibird.com/android-chrome-192x192.png",
				"faviconUrl": "https://fedibird.com/favicon.ico",
				"themeColor": "#282c37"
			},
			"emojis": {},
			"onlineStatus": "unknown"
		},
		"note": {
			"id": "9aybef5b1d",
			"createdAt": "2023-02-07T21:24:58.799Z",
			"userId": "7rm6y6thc1",
			"text": "(📎1)",
			"visibility": "public",
			"localOnly": false,
			"renoteCount": 0,
			"repliesCount": 0,
			"reactions": {
				"👍": 1,
				":kakkoii@.:": 1,
				":utsukushii@.:": 1
			},
			"reactionEmojis": {
				"blobcatlobster_MUDAMUDAMUDA@fedibird.com": "https://nos3.arkjp.net/emoji.webp?url=https%3A%2F%2Fs3.fedibird.com%2Fcustom_emojis%2Fimages%2F000%2F151%2F856%2Foriginal%2F936dd0a34673cb19.png"
			},
			"fileIds": [
				"9aybedosdl"
			],
			"files": [...],
			],
			"replyId": null,
			"renoteId": null
		},
		"reaction": "👍"
	},
	"userId": "7rm6y6thc1",
	"dateTime": 1675812160174
}


 */