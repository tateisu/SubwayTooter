package jp.juggler.subwaytooter.api.auth

import android.net.Uri
import androidx.core.net.toUri
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.send
import jp.juggler.subwaytooter.column.encodeQuery
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.buildJsonObject
import jp.juggler.util.data.jsonObjectOf
import jp.juggler.util.data.notEmpty
import jp.juggler.util.network.toFormRequestBody
import jp.juggler.util.network.toPost
import okhttp3.Request

class MastodonAuthApi(
    val client: TootApiClient,
) {
    /**
     * クライアントアプリをサーバに登録する
     */
    suspend fun registerClient(
        apiHost: Host,
        scopeString: String,
        clientName: String,
        callbackUrl: String,
    ): JsonObject = jsonObjectOf(
        "client_name" to clientName,
        "redirect_uris" to callbackUrl,
        "scopes" to scopeString,
    ).encodeQuery().toFormRequestBody().toPost()
        .url("https://${apiHost.ascii}/api/v1/apps")
        .build()
        .send(client, errorSuffix = apiHost.pretty)
        .readJsonObject()

    /**
     * サーバ上に登録されたアプリを参照する client credential を作成する
     */
    suspend fun createClientCredential(
        apiHost: Host,
        clientId: String,
        clientSecret: String,
        callbackUrl: String,
    ): JsonObject = buildJsonObject {
        put("grant_type", "client_credentials")
        put("scope", "read write") // 空白は + に変換されること
        put("client_id", clientId)
        put("client_secret", clientSecret)
        put("redirect_uri", callbackUrl)
    }.encodeQuery().toFormRequestBody().toPost()
        .url("https://${apiHost.ascii}/oauth/token")
        .build()
        .send(client, errorSuffix = apiHost.pretty)
        .readJsonObject()

    /**
     * client credentialを使って、サーバ上に登録されたクライアントアプリの情報を取得する
     * - クライアント情報がまだ有効か調べるのに使う
     */
    // client_credentialがまだ有効か調べる
    suspend fun verifyClientCredential(
        apiHost: Host,
        clientCredential: String,
    ): JsonObject = Request.Builder()
        .url("https://${apiHost.ascii}/api/v1/apps/verify_credentials")
        .header("Authorization", "Bearer $clientCredential")
        .build()
        .send(client, errorSuffix = apiHost.pretty)
        .readJsonObject()

    /**
     * client credentialを削除する
     * - クライアント情報そのものは消えない…
     */
    suspend fun revokeClientCredential(
        apiHost: Host,
        clientId: String,
        clientSecret: String,
        clientCredential: String,
    ): JsonObject = buildJsonObject {
        put("client_id", clientId)
        put("client_secret", clientSecret)
        put("token", clientCredential)
    }.encodeQuery().toFormRequestBody().toPost()
        .url("https://${apiHost.ascii}/oauth/revoke")
        .build()
        .send(client, errorSuffix = apiHost.pretty)
        .readJsonObject()

    // 認証ページURLを作る
    fun createAuthUrl(
        apiHost: Host,
        scopeString: String,
        callbackUrl: String,
        clientId: String,
        state: String,
    ): Uri = buildJsonObject {
        put("client_id", clientId)
        put("response_type", "code")
        put("redirect_uri", callbackUrl)
        put("scope", scopeString)
        put("state", state)
        put("grant_type", "authorization_code")
        put("approval_prompt", "force")
        put("force_login", "true")
        //		+"&access_type=offline"
    }.encodeQuery()
        .let { "https://${apiHost.ascii}/oauth/authorize?$it" }
        .toUri()

    /**
     * ブラウザから帰ってきたコードを使い、認証の続きを行う
     */
    suspend fun authStep2(
        apiHost: Host,
        clientId: String,
        clientSecret: String,
        scopeString: String,
        callbackUrl: String,
        code: String,
    ): JsonObject = jsonObjectOf(
        "grant_type" to "authorization_code",
        "code" to code,
        "client_id" to clientId,
        "client_secret" to clientSecret,
        "scope" to scopeString,
        "redirect_uri" to callbackUrl,
    ).encodeQuery().toFormRequestBody().toPost()
        .url("https://${apiHost.ascii}/oauth/token")
        .build()
        .send(client)
        .readJsonObject()

    // 認証されたアカウントのユーザ情報を取得する
    suspend fun verifyAccount(
        apiHost: Host,
        accessToken: String,
    ): JsonObject = Request.Builder()
        .url("https://${apiHost.ascii}/api/v1/accounts/verify_credentials")
        .header("Authorization", "Bearer $accessToken")
        .build()
        .send(client, errorSuffix = apiHost.pretty)
        .readJsonObject()

    /**
     * ユーザ登録API
     * アクセストークンはあるがアカウントIDがない状態になる。
     */
    suspend fun createUser(
        apiHost: Host,
        clientCredential: String,
        params: CreateUserParams,
    ) = buildJsonObject {
        put("username", params.username)
        put("email", params.email)
        put("password", params.password)
        put("agreement", params.agreement)
        params.reason?.notEmpty()?.let { put("reason", it) }
    }.encodeQuery().toFormRequestBody().toPost()
        .url("https://${apiHost.ascii}/api/v1/accounts")
        .header("Authorization", "Bearer $clientCredential")
        .build()
        .send(client, errorSuffix = apiHost.pretty)
        .readJsonObject()
}
