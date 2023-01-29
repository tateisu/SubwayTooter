package jp.juggler.subwaytooter.api.auth

import android.net.Uri
import jp.juggler.subwaytooter.api.SendException
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.entity.InstanceType
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.table.ClientInfo
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.buildJsonObject
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.errorEx

class MastodonAuth(override val client: TootApiClient) : AuthBase() {

    companion object {
        private val log = LogCategory("MastodonAuth")

        const val callbackUrl = "subwaytooter://oauth/"

        fun mastodonScope(ti: TootInstance?) = when {
            // 古いサーバ
            ti?.versionGE(TootInstance.VERSION_2_4_0_rc1) == false -> "read write follow"

            // 新しいサーバか、AUTHORIZED_FETCH(3.0.0以降)によりサーバ情報を取得できなかった
            else -> "read write follow push"

            // 過去の試行錯誤かな
            // ti.versionGE(TootInstance.VERSION_2_7_0_rc1) -> "read+write+follow+push+create"
        }
    }

    val api = MastodonAuthApi(client)

    // クライアントアプリの登録を確認するためのトークンを生成する
    // oAuth2 Client Credentials の取得
    // https://github.com/doorkeeper-gem/doorkeeper/wiki/Client-Credentials-flow
    // このトークンはAPIを呼び出すたびに新しく生成される…
    private suspend fun createClientCredentialToken(
        apiHost: Host,
        savedClientInfo: JsonObject,
    ): String {
        val credentialInfo = api.createClientCredential(
            apiHost = apiHost,
            clientId = savedClientInfo.string("client_id")
                ?: error("missing client_id"),
            clientSecret = savedClientInfo.string("client_secret")
                ?: error("missing client_secret"),
            callbackUrl = callbackUrl,
        )

        log.d("credentialInfo: $credentialInfo")

        return credentialInfo.string("access_token")
            ?.notEmpty() ?: error("missing client credential.")
    }

    private suspend fun prepareClientCredential(
        apiHost: Host,
        clientInfo: JsonObject,
        clientName: String,
    ): String? {
        // 既にcredentialを持っているならそれを返す
        clientInfo.string(KEY_CLIENT_CREDENTIAL)
            .notEmpty()?.let { return it }

        // token in clientCredential
        val clientCredential = try {
            createClientCredentialToken(apiHost, clientInfo)
        } catch (ex: Throwable) {
            if ((ex as? SendException)?.response?.code == 422) {
                // https://github.com/tateisu/SubwayTooter/issues/156
                // some servers not support to get client_credentials.
                // just ignore error and skip.
                return null
            } else {
                throw ex
            }
        }
        clientInfo[KEY_CLIENT_CREDENTIAL] = clientCredential
        ClientInfo.save(apiHost, clientName, clientInfo.toString())
        return clientCredential
    }

    // result.JsonObject に credentialつきのclient_info を格納して返す
    private suspend fun prepareClientImpl(
        apiHost: Host,
        clientName: String,
        tootInstance: TootInstance?,
        forceUpdateClient: Boolean,
    ): JsonObject {
        var clientInfo = ClientInfo.load(apiHost, clientName)

        // スコープ一覧を取得する
        val scopeString = mastodonScope(tootInstance)

        when {
            // 古いクライアント情報は使わない。削除もしない。
            AUTH_VERSION != clientInfo?.int(KEY_AUTH_VERSION) -> Unit

            // Misskeyにはclient情報をまだ利用できるかどうか調べる手段がないので、再利用しない
            clientInfo.boolean(KEY_IS_MISSKEY) == true -> Unit

            else -> {
                val clientCredential = prepareClientCredential(apiHost, clientInfo, clientName)
                // client_credential があるならcredentialがまだ使えるか確認する
                if (!clientCredential.isNullOrEmpty()) {

                    // 存在確認するだけで、結果は使ってない
                    api.verifyClientCredential(apiHost, clientCredential)

                    // 過去にはスコープを+で連結したものを保存していた
                    val oldScope = clientInfo.string(KEY_CLIENT_SCOPE)
                        ?.replace("+", " ")

                    when {
                        // クライアント情報を再利用する
                        !forceUpdateClient && oldScope == scopeString -> return clientInfo

                        else -> try {
                            // マストドン2.4でスコープが追加された
                            // 取得時のスコープ指定がマッチしない(もしくは記録されていない)ならクライアント情報を再利用してはいけない
                            ClientInfo.delete(apiHost, clientName)

                            // クライアントアプリ情報そのものはまだサーバに残っているが、明示的に消す方法は現状存在しない
                            // client credential だけは消せる
                            api.revokeClientCredential(
                                apiHost = apiHost,
                                clientId = clientInfo.string("client_id")
                                    ?: error("revokeClientCredential: missing client_id"),
                                clientSecret = clientInfo.string("client_secret")
                                    ?: error("revokeClientCredential: missing client_secret"),
                                clientCredential = clientCredential,
                            )
                        } catch (ex: Throwable) {
                            // クライアント情報の削除処理はエラーが起きても無視する
                            log.w(ex, "can't delete client information.")
                        }
                    }
                }
            }
        }

        clientInfo = api.registerClient(apiHost, scopeString, clientName, callbackUrl).apply {
            // {"id":999,"redirect_uri":"urn:ietf:wg:oauth:2.0:oob","client_id":"******","client_secret":"******"}
            put(KEY_AUTH_VERSION, AUTH_VERSION)
            put(KEY_CLIENT_SCOPE, scopeString)
        }
        // client credentialを取得して保存する
        // この時点ではまだ client credential がないので、必ず更新と保存が行われる
        prepareClientCredential(apiHost, clientInfo, clientName)

        return clientInfo
    }

    /**
     * アクセストークン手動入力でアカウントを更新する場合、アカウントの情報を取得する
     * auth2の後にユーザ情報を知るためにも使われる
     *
     * 副作用：ユーザ情報を取得できたらoutTokenInfoを更新する
     */
    override suspend fun verifyAccount(
        accessToken: String,
        outTokenJson: JsonObject?,
        misskeyVersion: Int,
    ): JsonObject = api.verifyAccount(
        apiHost = apiHost ?: error("verifyAccount: missing apiHost."),
        accessToken = accessToken,
    ).also {
        // APIレスポンスが成功したら、そのデータとは無関係に
        // アクセストークンをtokenInfoに格納する。
        outTokenJson?.apply {
            put(KEY_AUTH_VERSION, AUTH_VERSION)
            put("access_token", accessToken)
        }
    }

    /**
     * クライアントを登録してブラウザで開くURLを生成する
     * 成功したら TootApiResult.data にURL文字列を格納すること
     * @param ti サーバ情報。Mastodonのホワイトリストモードではnullかもしれない
     * @param forceUpdateClient  (Mastodon)クライアントを強制的に登録しなおす
     */
    override suspend fun authStep1(
        ti: TootInstance?,
        forceUpdateClient: Boolean,
    ): Uri {
        if (ti?.instanceType == InstanceType.Pixelfed) {
            error("currently Pixelfed instance is not supported.")
        }

        val apiHost = apiHost ?: error("authStep1: missing apiHost")

        val clientJson = prepareClientImpl(
            apiHost = apiHost,
            clientName = clientName,
            ti,
            forceUpdateClient,
        )

        val accountDbId = account?.db_id?.takeIf { it >= 0L }

        val state = listOf(
            "random:${System.currentTimeMillis()}",
            when (accountDbId) {
                null -> "host:${apiHost.ascii}"
                else -> "db:$accountDbId"
            }
        ).joinToString(",")

        return api.createAuthUrl(
            apiHost = apiHost,
            scopeString = mastodonScope(ti),
            callbackUrl = callbackUrl,
            clientId = clientJson.string("client_id")
                ?: error("missing client_id"),
            state = state,
        )
    }

    /**
     * 認証コールバックURLを受け取り、サーバにアクセスして認証を終わらせる。
     */
    override suspend fun authStep2(uri: Uri): Auth2Result {
        // Mastodon 認証コールバック

        // エラー時
        // subwaytooter://oauth(\d*)/
        // ?error=access_denied
        // &error_description=%E3%83%AA%E3%82%BD%E3%83%BC%E3%82%B9%E3%81%AE%E6%89%80%E6%9C%89%E8%80%85%E3%81%BE%E3%81%9F%E3%81%AF%E8%AA%8D%E8%A8%BC%E3%82%B5%E3%83%BC%E3%83%90%E3%83%BC%E3%81%8C%E8%A6%81%E6%B1%82%E3%82%92%E6%8B%92%E5%90%A6%E3%81%97%E3%81%BE%E3%81%97%E3%81%9F%E3%80%82
        // &state=db%3A3
        arrayOf("error_description", "error")
            .mapNotNull { uri.getQueryParameter(it)?.trim()?.notEmpty() }
            .notEmpty()
            ?.let { error(it.joinToString("\n")) }

        // subwaytooter://oauth(\d*)/
        //    ?code=113cc036e078ac500d3d0d3ad345cd8181456ab087abc67270d40f40a4e9e3c2
        //    &state=host%3Amastodon.juggler.jp

        val code = uri.getQueryParameter("code")
            ?.trim()?.notEmpty() ?: error("missing code in callback url.")

        val cols = uri.getQueryParameter("state")
            ?.trim()?.notEmpty() ?: error("missing state in callback url.")

        for (param in cols.split(",")) {
            when {
                param.startsWith("db:") -> try {
                    val dataId = param.substring(3).toLong(10)
                    val sa = SavedAccount.loadAccount(context, dataId)
                        ?: error("missing account db_id=$dataId")
                    client.account = sa
                } catch (ex: Throwable) {
                    errorEx(ex, "invalide state.db in callback parameter.")
                }

                param.startsWith("host:") -> {
                    val host = Host.parse(param.substring(5))
                    client.apiHost = host
                }
                // ignore other parameter
            }
        }

        val apiHost = client.apiHost
            ?: error("can't get apiHost from callback parameter.")

        val clientInfo = ClientInfo.load(apiHost, clientName)
            ?: error("can't find client info for apiHost=$apiHost, clientName=$clientName")

        val tokenInfo = api.authStep2(
            apiHost = apiHost,
            clientId = clientInfo.string("client_id")
                ?: error("handleOAuth2Callback: missing client_id"),
            clientSecret = clientInfo.string("client_secret")
                ?.notEmpty() ?: error("handleOAuth2Callback: missing client_secret"),
            scopeString = clientInfo.string(KEY_CLIENT_SCOPE)
                ?.notEmpty() ?: error("handleOAuth2Callback: missing scopeString"),
            callbackUrl = callbackUrl,
            code = code,
        )
        // {"access_token":"******","token_type":"bearer","scope":"read","created_at":1492334641}

        val accessToken = tokenInfo.string("access_token")
            ?.notEmpty() ?: error("can't parse access token.")

        val accountJson = verifyAccount(
            accessToken = accessToken,
            outTokenJson = tokenInfo,
            misskeyVersion = 0
        )

        val ti = TootInstance.getExOrThrow(client, forceAccessToken = accessToken)
        val parser = TootParser(context, linkHelper = LinkHelper.create(ti))
        return Auth2Result(
            tootInstance = ti,
            tokenJson = tokenInfo,
            accountJson = accountJson,
            tootAccount = parser.account(accountJson)
                ?: error("can't parse user information.")
        )
    }

    /**
     * サーバにアプリ情報を登録する。
     * ユーザ作成の手前で呼ばれる。
     */
    suspend fun prepareClient(
        tootInstance: TootInstance,
    ): JsonObject = prepareClientImpl(
        apiHost = apiHost ?: error("prepareClient: missing apiHost"),
        clientName = clientName,
        tootInstance = tootInstance,
        forceUpdateClient = false
    )

    suspend fun createUser(
        clientInfo: JsonObject,
        params: CreateUserParams,
    ): Auth2Result {
        val apiHost = apiHost ?: error("createUser: missing apiHost")

        val tokenJson = api.createUser(
            apiHost = apiHost,
            clientCredential = clientInfo.string(KEY_CLIENT_CREDENTIAL)
                ?: error("createUser: missing client credential"),
            params = params,
        )

        val accessToken = tokenJson.string("access_token")
            ?: error("can't get user access token")

        val ti = TootInstance.getExOrThrow(client, forceAccessToken = accessToken)
        val parser = TootParser(context, linkHelper = LinkHelper.create(ti))

        val accountJson = try {
            verifyAccount(
                accessToken = accessToken,
                outTokenJson = tokenJson,
                misskeyVersion = 0,  // Mastodon限定
            )
            // メール確認が不要な場合は成功する
        } catch (ex: Throwable) {
            // メール確認がまだなら、verifyAccount は失敗する
            log.e(ex, "createUser: can't verify account.")
            buildJsonObject {
                put("id", EntityId.CONFIRMING.toString())
                put("username", params.username)
                put("acct", params.username)
                put("url", "https://$apiHost/@${params.username}")
            }
        }
        return Auth2Result(
            tootInstance = ti,
            tokenJson = tokenJson,
            accountJson = accountJson,
            tootAccount = parser.account(accountJson)
                ?: error("can't verify user information."),
        )
    }
}
