package jp.juggler.subwaytooter.api.auth

import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.send
import jp.juggler.util.data.JsonArray
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.buildJsonObject
import jp.juggler.util.data.jsonObjectOf
import jp.juggler.util.network.toPostRequestBuilder

class ApiAuthMisskey10(val client: TootApiClient) {

    suspend fun appCreate(
        apiHost: Host,
        appNameId: String,
        appDescription: String,
        clientName: String,
        scopeArray: JsonArray,
        callbackUrl: String,
    ) = buildJsonObject {
        put("nameId", appNameId)
        put("name", clientName)
        put("description", appDescription)
        put("callbackUrl", callbackUrl)
        put("permission", scopeArray)
    }.toPostRequestBuilder()
        .url("https://${apiHost.ascii}/api/app/create")
        .build()
        .send(client, errorSuffix = apiHost.pretty)
        .readJsonObject()

    suspend fun appShow(apiHost: Host, appId: String) =
        jsonObjectOf("appId" to appId)
            .toPostRequestBuilder()
            .url("https://${apiHost.ascii}/api/app/show")
            .build()
            .send(client, errorSuffix = apiHost.pretty)
            .readJsonObject()

    suspend fun authSessionGenerate(apiHost: Host, appSecret: String) =
        jsonObjectOf("appSecret" to appSecret)
            .toPostRequestBuilder()
            .url("https://${apiHost.ascii}/api/auth/session/generate")
            .build()
            .send(client, errorSuffix = apiHost.pretty)
            .readJsonObject()

    suspend fun authSessionUserKey(
        apiHost: Host,
        appSecret: String,
        token: String,
    ): JsonObject = jsonObjectOf(
        "appSecret" to appSecret,
        "token" to token,
    ).toPostRequestBuilder()
        .url("https://${apiHost.ascii}/api/auth/session/userkey")
        .build()
        .send(client, errorSuffix = apiHost.pretty)
        .readJsonObject()

    suspend fun verifyAccount(
        apiHost: Host,
        accessToken: String,
    ): JsonObject = jsonObjectOf("i" to accessToken)
        .toPostRequestBuilder()
        .url("https://${apiHost.ascii}/api/i")
        .build()
        .send(client, errorSuffix = apiHost.pretty)
        .readJsonObject()
}
