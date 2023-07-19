package jp.juggler.subwaytooter.api.auth

import android.net.Uri
import androidx.core.net.toUri
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.pref.prefDevice
import jp.juggler.subwaytooter.push.FcmFlavor
import jp.juggler.subwaytooter.table.daoClientInfo
import jp.juggler.subwaytooter.table.daoSavedAccount
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.util.data.JsonArray
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.buildJsonArray
import jp.juggler.util.data.cast
import jp.juggler.util.data.digestSHA256
import jp.juggler.util.data.encodeHexLower
import jp.juggler.util.data.encodeUTF8
import jp.juggler.util.data.notBlank
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory

class AuthMisskey10(override val client: TootApiClient) : AuthBase() {
    companion object {
        private val log = LogCategory("MisskeyOldAuth")
        private const val callbackUrl = "${FcmFlavor.CUSTOM_SCHEME}://misskey/auth_callback"

        fun isCallbackUrl(uriStr: String) =
            uriStr.startsWith(callbackUrl) ||
                    uriStr.startsWith("misskeyclientproto://misskeyclientproto/auth_callback")

        fun getScopeArrayMisskey(ti: TootInstance?) =
            buildJsonArray {
                if (ti != null && !ti.versionGE(TootInstance.MISSKEY_VERSION_11)) {
                    // https://github.com/syuilo/misskey/issues/2341
                    // Misskey 10まで
                    arrayOf(
                        "account-read",
                        "account-write",
                        "account/read",
                        "account/write",
                        "drive-read",
                        "drive-write",
                        "favorite-read",
                        "favorite-write",
                        "favorites-read",
                        "following-read",
                        "following-write",
                        "messaging-read",
                        "messaging-write",
                        "note-read",
                        "note-write",
                        "notification-read",
                        "notification-write",
                        "reaction-read",
                        "reaction-write",
                        "vote-read",
                        "vote-write"
                    )
                } else {
                    // Misskey 11以降
                    // https://github.com/syuilo/misskey/blob/master/src/server/api/kinds.ts
                    arrayOf(
                        "read:account",
                        "write:account",
                        "read:blocks",
                        "write:blocks",
                        "read:drive",
                        "write:drive",
                        "read:favorites",
                        "write:favorites",
                        "read:following",
                        "write:following",
                        "read:messaging",
                        "write:messaging",
                        "read:mutes",
                        "write:mutes",
                        "write:notes",
                        "read:notifications",
                        "write:notifications",
                        "read:reactions",
                        "write:reactions",
                        "write:votes"
                    )
                }.toMutableSet().forEach { add(it) }
                // APIのエラーを回避するため、重複を排除する
            }

        fun JsonArray.encodeScopeArray() =
            stringArrayList().sorted().joinToString(",")

        fun compareScopeArray(a: JsonArray, b: JsonArray?) =
            a.encodeScopeArray() == b?.encodeScopeArray()
    }

    val api = ApiAuthMisskey10(client)

    /**
     * Misskey v12 までの認証に使うURLを生成する
     *
     * {"token":"0ba88e2d-4b7d-4599-8d90-dc341a005637","url":"https://misskey.xyz/auth/0ba88e2d-4b7d-4599-8d90-dc341a005637"}
     */
    private suspend fun createAuthUri(apiHost: Host, appSecret: String): Uri {
        context.prefDevice.saveLastAuth(
            host = apiHost.ascii,
            secret = appSecret,
            dbId = account?.db_id, //nullable
        )

        return api.authSessionGenerate(apiHost, appSecret)
            .string("url").notEmpty()?.toUri()
            ?: error("missing 'url' in session/generate.")
    }

    /**
     * クライアントを登録してブラウザで開くURLを生成する
     * 成功したら TootApiResult.data にURL文字列を格納すること
     *
     * @param ti サーバ情報。Mastodonのホワイトリストモードではnullかもしれない
     * @param forceUpdateClient (Mastodon)クライアントを強制的に登録しなおす
     */
    override suspend fun authStep1(
        ti: TootInstance?,
        forceUpdateClient: Boolean,
    ): Uri {
        if (!PrefB.bpEnableDeprecatedSomething.value) {
            error(context.getString(R.string.misskey_support_end))
        }

        val apiHost = apiHost ?: error("missing apiHost")

        val clientInfo = daoClientInfo.load(apiHost, clientName)

        // スコープ一覧を取得する
        val scopeArray = getScopeArrayMisskey(ti)

        if (clientInfo != null &&
            AUTH_VERSION == clientInfo.int(KEY_AUTH_VERSION) &&
            clientInfo.boolean(KEY_IS_MISSKEY) == true
        ) {
            val appSecret = clientInfo.string(KEY_MISSKEY_APP_SECRET)

            val appId = clientInfo.string("id")
                ?: error("missing app id")

            // tmpClientInfo はsecretを含まないので保存してはいけない
            val tmpClientInfo = try {
                api.appShow(apiHost, appId)
            } catch (ex: Throwable) {
                // アプリ情報の取得に失敗しても致命的ではない
                log.e(ex, "can't get app info, but continue…")
                null
            }

            // - アプリが登録済みで
            // - クライアント名が一致してて
            // - パーミッションが同じ
            // ならクライアント情報を再利用する
            if (tmpClientInfo != null &&
                clientName == tmpClientInfo.string("name") &&
                compareScopeArray(scopeArray, tmpClientInfo["permission"].cast()) &&
                appSecret?.isNotEmpty() == true
            ) return createAuthUri(apiHost, appSecret)

            // XXX appSecretを使ってクライアント情報を削除できるようにするべきだが、該当するAPIが存在しない
        }

        val appJson = api.appCreate(
            apiHost = apiHost,
            appNameId = appNameId,
            appDescription = appDescription,
            clientName = clientName,
            scopeArray = scopeArray,
            callbackUrl = callbackUrl,
        ).apply {
            put(KEY_IS_MISSKEY, true)
            put(KEY_AUTH_VERSION, AUTH_VERSION)
        }

        val appSecret = appJson.string(KEY_MISSKEY_APP_SECRET)
            .notBlank() ?: error(context.getString(R.string.cant_get_misskey_app_secret))

        daoClientInfo.save(apiHost, clientName, appJson.toString())

        return createAuthUri(apiHost, appSecret)
    }

    /**
     * Misskey(v12まで)の認証コールバックUriを処理する
     */
    override suspend fun authStep2(uri: Uri): Auth2Result {

        val prefDevice = context.prefDevice

        val token = uri.getQueryParameter("token")
            ?.notBlank() ?: error("missing token in callback URL")

        val hostStr = prefDevice.lastAuthInstance
            ?.notBlank() ?: error("missing instance name.")

        val apiHost = Host.parse(hostStr)

        when (val dbId = prefDevice.lastAuthDbId) {
            // new registration
            null -> client.apiHost = apiHost
            // update access token
            else -> daoSavedAccount.loadAccount(dbId)?.also {
                client.account = it
            } ?: error("missing account db_id=$dbId")
        }

        val ti = TootInstance.getOrThrow(client)

        val parser = TootParser(
            context,
            linkHelper = LinkHelper.create(ti)
        )

        val clientInfo = daoClientInfo.load(apiHost, clientName)
            ?.notEmpty() ?: error("missing client id")

        val appSecret = clientInfo.string(KEY_MISSKEY_APP_SECRET)
            ?.notEmpty() ?: error(context.getString(R.string.cant_get_misskey_app_secret))

        val tokenInfo = api.authSessionUserKey(
            apiHost,
            appSecret,
            token,
        )

        // {"accessToken":"...","user":{…}}

        val accessToken = tokenInfo.string("accessToken")
            ?.notBlank() ?: error("missing accessToken in the userkey response.")

        val accountJson = tokenInfo["user"].cast<JsonObject>()
            ?: error("missing user in the userkey response.")

        tokenInfo.remove("user")

        return Auth2Result(
            tootInstance = ti,
            tokenJson = tokenInfo.also {
                EntityId.mayNull(accountJson.string("id"))?.putTo(it, KEY_USER_ID)
                it[KEY_MISSKEY_VERSION] = ti.misskeyVersionMajor
                it[KEY_AUTH_VERSION] = AUTH_VERSION
                val apiKey = "$accessToken$appSecret".encodeUTF8().digestSHA256().encodeHexLower()
                it[KEY_API_KEY_MISSKEY] = apiKey
            },
            accountJson = accountJson,
            tootAccount = parser.account(accountJson)
                ?: error("can't parse user information"),
        )
    }

    /**
     * アクセストークンを指定してユーザ情報を取得する。
     * - アクセストークンの手動入力などで使われる
     *
     * 副作用：ユーザ情報を取得できたら outTokenInfo にアクセストークンを格納する。
     */
    override suspend fun verifyAccount(
        accessToken: String,
        outTokenJson: JsonObject?,
        misskeyVersion: Int,
    ): JsonObject = api.verifyAccount(
        apiHost = apiHost ?: error("missing apiHost"),
        accessToken = accessToken
    ).also {
        // ユーザ情報が読めたら outTokenInfo にアクセストークンを保存する
        outTokenJson?.apply {
            put(KEY_AUTH_VERSION, AUTH_VERSION)
            put(KEY_API_KEY_MISSKEY, accessToken)
            put(KEY_MISSKEY_VERSION, misskeyVersion)
        }
    }
}
