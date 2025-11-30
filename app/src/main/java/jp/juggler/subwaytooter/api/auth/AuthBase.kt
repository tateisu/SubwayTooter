package jp.juggler.subwaytooter.api.auth

import android.net.Uri
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.entity.InstanceType
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.pref.PrefS
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.notBlank

abstract class AuthBase {
    companion object {
        private const val DEFAULT_CLIENT_NAME = "SubwayTooter"
        const val appNameId = "SubwayTooter"
        const val appDescription = "Android app for federated SNS"

        // 20181225 3=>4 client credentialの取得時にもscopeの取得が必要になった
        // 20190147 4=>5 client id とユーザIDが同じだと同じアクセストークンが返ってくるので複数端末の利用で困る。
        // AUTH_VERSIONが古いclient情報は使わない。また、インポートの対象にしない。
        const val AUTH_VERSION = 5

        const val KEY_CLIENT_CREDENTIAL = "SubwayTooterClientCredential"
        const val KEY_CLIENT_SCOPE = "SubwayTooterClientScope"
        const val KEY_AUTH_VERSION = "SubwayTooterAuthVersion"
        const val KEY_IS_MISSKEY = "isMisskey" // for ClientInfo
        const val KEY_MISSKEY_VERSION = "isMisskey" // for tokenInfo,TootInstance
        const val KEY_MISSKEY_APP_SECRET = "secret"
        const val KEY_API_KEY_MISSKEY = "apiKeyMisskey"
        const val KEY_USER_ID = "userId"

        var testClientName: String? = null

        val clientName
            get() = arrayOf(
                testClientName,
                PrefS.spClientName.value,
            ).firstNotNullOfOrNull { it.notBlank() }
                ?: DEFAULT_CLIENT_NAME

        fun findAuthForVerifyAccount(client: TootApiClient, misskeyVersionMajor: Int) =
            when {
                // https://mastodon.juggler.jp/@tateisu/109819635248751031
                // https://github.com/misskey-dev/misskey/issues/9825
                // https://github.com/misskey-dev/misskey/commit/788ae2f6ca37d297e912bfba02821543e8566522
                // misskeyVersionMajor >= 13 -> MisskeyAuth13(client)
                misskeyVersionMajor > 0 -> AuthMisskey10(client)
                else -> AuthMastodon(client)
            }

        fun findAuthForAuthStep1(client: TootApiClient, ti: TootInstance?, ri: TootApiResult?) =
            ti?.let { findAuthForVerifyAccount(client, ti.misskeyVersionMajor) }
                ?: when (ri?.response?.code) {
                    // インスタンス情報を取得できないが、マストドンだと分かる場合がある
                    // https://github.com/tateisu/SubwayTooter/issues/155
                    // Mastodon's WHITELIST_MODE
                    401 -> AuthMastodon(client)
                    else -> null
                }

        fun findAuthForAuthCallback(client: TootApiClient, callbackUrl: String) =
            when {
                AuthMisskey10.isCallbackUrl(callbackUrl) -> AuthMisskey10(client)
                AuthMisskey13.isCallbackUrl(callbackUrl) -> AuthMisskey13(client)
                else -> AuthMastodon(client)
            }

        fun findAuthForCreateUser(client: TootApiClient, ti: TootInstance?) =
            when (ti?.instanceType) {
                InstanceType.Mastodon -> AuthMastodon(client)
                else -> null
            }
    }

    protected abstract val client: TootApiClient

    protected val apiHost get() = client.apiHost
    protected val account get() = client.account
    protected val context get() = client.context

    /**
     * クライアントを登録してブラウザで開くURLを生成する
     * 成功したら TootApiResult.data にURL文字列を格納すること
     */
    abstract suspend fun authStep1(
        // サーバ情報。Mastodonのホワイトリストモードではnullかもしれない
        ti: TootInstance?,
        // (Mastodon)クライアントを強制的に登録しなおす
        forceUpdateClient: Boolean,
    ): Uri

    /**
     * ブラウザから戻ってきたコールバックURLを使い認証の続きを行う
     */
    abstract suspend fun authStep2(uri: Uri): Auth2Result

    /**
     * アクセストークンを手動入力した場合、それを使って本人のユーザ情報を取得する
     */
    abstract suspend fun verifyAccount(
        accessToken: String,
        outTokenJson: JsonObject?,
        misskeyVersion: Int,
    ): JsonObject
}
