package jp.juggler.subwaytooter.api.push

import jp.juggler.subwaytooter.api.await
import jp.juggler.subwaytooter.api.readJsonObject
import jp.juggler.subwaytooter.column.encodeQuery
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.buildJsonObject
import jp.juggler.util.data.jsonArrayOf
import jp.juggler.util.network.toPostRequestBuilder
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * アプリサーバのAPI
 */
class ApiPushAppServer(
    private val okHttp: OkHttpClient,
    private val appServerPrefix: String = "https://mastodon-msg.juggler.jp/api/v2",
) {
    /**
     * 中継エンドポイントが無効になったら削除する
     */
    suspend fun endpointRemove(
        upUrl: String? = null,
        fcmToken: String? = null,
        hashId: String? = null,
    ): JsonObject = buildJsonObject {
        upUrl?.let { put("upUrl", it) }
        fcmToken?.let { put("fcmToken", it) }
        hashId?.let { put("hashId", it) }
    }.encodeQuery().let {
        Request.Builder()
            .url("$appServerPrefix/endpoint/remove?$it")
    }.delete().build()
        .await(okHttp)
        .readJsonObject()

    /**
     * エンドポイントとアカウントハッシュをアプリサーバに登録する
     */
    suspend fun endpointUpsert(
        upUrl: String?,
        fcmToken: String?,
        acctHashList: List<String>,
    ): JsonObject =
        buildJsonObject {
            upUrl?.let { put("upUrl", it) }
            fcmToken?.let { put("fcmToken", it) }
            put("acctHashList", jsonArrayOf(*(acctHashList.toTypedArray())))
        }.toPostRequestBuilder()
            .url("$appServerPrefix/endpoint/upsert")
            .build()
            .await(okHttp)
            .readJsonObject()

    suspend fun getLargeObject(
        largeObjectId: String,
    ): ByteArray? = withContext(AppDispatchers.IO) {
        Request.Builder()
            .url("$appServerPrefix/l/$largeObjectId")
            .build()
            .await(okHttp)
            .body.bytes()
    }
}
