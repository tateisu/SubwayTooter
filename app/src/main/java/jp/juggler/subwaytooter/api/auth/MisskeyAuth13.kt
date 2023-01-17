package jp.juggler.subwaytooter.api.auth

import android.net.Uri
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.auth.MisskeyAuth10.Companion.encodeScopeArray
import jp.juggler.subwaytooter.api.auth.MisskeyAuth10.Companion.getScopeArrayMisskey
import jp.juggler.subwaytooter.api.entity.Host
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.pref.PrefDevice
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory
import java.util.*

/**
 * miauth と呼ばれている認証手順。
 * STではMisskey 13から使用する。
 */
class MisskeyAuth13(override val client: TootApiClient) : AuthBase() {
    companion object {
        private val log = LogCategory("MisskeyMiAuth")
        private const val appIconUrl = "https://m1j.zzz.ac/subwaytooter-miauth-icon.png"
        private const val callbackUrl = "subwaytooter://miauth/auth_callback"

        fun isCallbackUrl(uriStr: String) = uriStr.startsWith(callbackUrl)
    }

    private val api10 = MisskeyAuthApi10(client)
    private val api13 = MisskeyAuthApi13(client)

    // 認証されたアカウントのユーザ情報を取得する
    override suspend fun verifyAccount(
        accessToken: String,
        outTokenJson: JsonObject?,
        misskeyVersion: Int,
    ): JsonObject = api10.verifyAccount(
        apiHost = apiHost ?: error("missing apiHost"),
        accessToken = accessToken,
    ).also {
        // ユーザ情報を読めたならtokenInfoを保存する
        outTokenJson?.apply {
            put(KEY_AUTH_VERSION, AUTH_VERSION)
            put(KEY_API_KEY_MISSKEY, accessToken)
            put(KEY_MISSKEY_VERSION, misskeyVersion)
        }
    }

    /**
     * クライアントを登録してブラウザで開くURLを生成する
     * 成功したら TootApiResult.data にURL文字列を格納すること
     */
    override suspend fun authStep1(
        ti: TootInstance?,
        forceUpdateClient: Boolean,
    ): Uri {
        val apiHost = apiHost ?: error("missing apiHost")

        val sessionId = UUID.randomUUID().toString()

        PrefDevice.from(client.context).edit().apply {
            putString(PrefDevice.LAST_AUTH_INSTANCE, apiHost.ascii)
            putString(PrefDevice.LAST_AUTH_SECRET, sessionId)
            when (val account = account) {
                null -> remove(PrefDevice.LAST_AUTH_DB_ID)
                else -> putLong(PrefDevice.LAST_AUTH_DB_ID, account.db_id)
            }
        }.apply()

        return api13.createAuthUrl(
            apiHost = apiHost,
            clientName = clientName,
            iconUrl = appIconUrl,
            callbackUrl = callbackUrl,
            permission = getScopeArrayMisskey(ti).encodeScopeArray(),
            sessionId = sessionId,
        )
    }

    override suspend fun authStep2(uri: Uri): Auth2Result {

        // 認証開始時に保存した情報
        val prefDevice = PrefDevice.from(client.context)
        val savedSessionId = prefDevice.getString(PrefDevice.LAST_AUTH_SECRET, null)

        val apiHost = prefDevice.getString(PrefDevice.LAST_AUTH_INSTANCE, null)
            ?.let { Host.parse(it) }
            ?: error("missing apiHost")

        when (val dbId = prefDevice.getLong(PrefDevice.LAST_AUTH_DB_ID, -1L)) {
            // new registration
            -1L -> client.apiHost = apiHost

            // update access token
            else -> {
                val sa = SavedAccount.loadAccount(context, dbId)
                    ?: error("missing account db_id=$dbId")
                client.account = sa
            }
        }

        // コールバックURLに含まれるセッションID
        val sessionId = uri.getQueryParameter("session").notEmpty()
            ?: error("missing sessionId in callback URL")

        if (sessionId != savedSessionId) {
            error("auth session id not match.")
        }

        val (ti, r2) = TootInstance.get(client)
        ti ?: error("missing server information. ${r2?.error}")

        val misskeyVersion = ti.misskeyVersionMajor

        val data = api13.checkAuthSession(apiHost, sessionId)

        val ok = data.boolean("ok")
        if (ok != true) {
            error("Authentication result is not ok. [$ok]")
        }

        val apiKey = data.string("token")
            ?: error("missing token.")
        log.i("apiKey=$apiKey")

        val accountJson = data.jsonObject("user")
            ?: error("missing user.")

        val user = TootParser(context, linkHelper = LinkHelper.create(ti))
            .account(accountJson)
            ?: error("can't parse user json.")

        prefDevice.edit().remove(PrefDevice.LAST_AUTH_SECRET).apply()

        return Auth2Result(
            tootInstance = ti,
            accountJson = accountJson,
            tokenJson = JsonObject().apply {
                user.id.putTo(this, KEY_USER_ID)
                put(KEY_MISSKEY_VERSION, misskeyVersion)
                put(KEY_AUTH_VERSION, AUTH_VERSION)
                put(KEY_API_KEY_MISSKEY, apiKey)
            },
            tootAccount = user,
        )
    }
}
