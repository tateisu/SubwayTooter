package jp.juggler.subwaytooter.api.push

import jp.juggler.subwaytooter.api.await
import jp.juggler.subwaytooter.api.readJsonObject
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.buildJsonObject
import jp.juggler.util.network.toPostRequestBuilder
import okhttp3.OkHttpClient

class ApiPushMisskey(
    private val okHttp: OkHttpClient,
) {

    /**
     * エンドポイントURLを指定してプッシュ購読の情報を取得する
     */
    suspend fun getPushSubscription(
        a: SavedAccount,
        endpoint: String,
    ): JsonObject = buildJsonObject {
        a.misskeyApiToken?.let { put("i", it) }
        put("endpoint", endpoint)
    }.toPostRequestBuilder()
        .url("https://${a.apiHost}/api/sw/show-registration")
        .build()
        .await(okHttp)
        .readJsonObject()

    suspend fun deletePushSubscription(
        a: SavedAccount,
        endpoint: String,
    ): JsonObject = buildJsonObject {
        a.misskeyApiToken?.let { put("i", it) }
        put("endpoint", endpoint)
    }.toPostRequestBuilder()
        .url("https://${a.apiHost}/api/sw/unregister")
        .build()
        .await(okHttp)
        .readJsonObject()

    /**
     * プッシュ購読を更新する。
     * endpointのURLはクエリに使われる。変更できるのはsendReadMessageだけ。
     */
    suspend fun updatePushSubscription(
        a: SavedAccount,
        endpoint: String,
        sendReadMessage: Boolean,
    ): JsonObject = buildJsonObject {
        a.misskeyApiToken?.let { put("i", it) }
        put("endpoint", endpoint)
        put("sendReadMessage", sendReadMessage)
    }.toPostRequestBuilder()
        .url("https://${a.apiHost}/api/sw/update-registration")
        .build()
        .await(okHttp)
        .readJsonObject()

    suspend fun createPushSubscription(
        a: SavedAccount,
        endpoint: String,
        auth: String,
        publicKey: String,
        sendReadMessage: Boolean,
    ): JsonObject = buildJsonObject {
        a.misskeyApiToken?.let { put("i", it) }
        put("endpoint", endpoint)
        put("auth", auth)
        put("publickey", publicKey)
        put("sendReadMessage", sendReadMessage)
    }.toPostRequestBuilder()
        .url("https://${a.apiHost}/api/sw/register")
        .build()
        .await(okHttp)
        .readJsonObject()
}
