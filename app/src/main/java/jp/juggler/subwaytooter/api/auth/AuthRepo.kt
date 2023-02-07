package jp.juggler.subwaytooter.api.auth

import android.content.Context
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.notification.checkNotificationImmediate
import jp.juggler.subwaytooter.notification.checkNotificationImmediateAll
import jp.juggler.subwaytooter.pref.PrefL
import jp.juggler.subwaytooter.pref.lazyContext
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.appDatabase
import jp.juggler.util.log.LogCategory

val Context.authRepo
    get() = AuthRepo(
        context = this,
        daoAcctColor = AcctColor.Access(appDatabase),
        daoSavedAccount = SavedAccount.Access(appDatabase, lazyContext),
    )

class AuthRepo(
    private val context: Context = lazyContext,
    private val daoAcctColor: AcctColor.Access =
        AcctColor.Access(appDatabase),
    private val daoSavedAccount: SavedAccount.Access =
        SavedAccount.Access(appDatabase, lazyContext),
) {
    companion object {
        private val log = LogCategory("AuthRepo")
    }

    /**
     * ユーザ登録の確認手順が完了しているかどうか
     *
     * - マストドン以外だと何もしないはず
     */
    suspend fun checkConfirmed(item: SavedAccount, client: TootApiClient) {
        // 承認待ち状態ではないならチェックしない
        if (item.loginAccount?.id != EntityId.CONFIRMING) return

        // DBに保存されていないならチェックしない
        if (item.isInvalidId) return

        // アクセストークンがないならチェックしない
        val accessToken = item.bearerAccessToken ?: return

        // ユーザ情報を取得してみる。承認済みなら読めるはず
        // 読めなければ例外が出る
        val userJson = client.verifyAccount(
            accessToken = accessToken,
            outTokenInfo = null,
            misskeyVersion = 0, // Mastodon only
        )
        // 読めたらアプリ内の記録を更新する
        TootParser(context, item).account(userJson)?.let { ta ->
            item.loginAccount = ta
            daoSavedAccount.save(item)
            checkNotificationImmediateAll(context, onlyEnqueue = true)
            checkNotificationImmediate(context, item.db_id)
        }
    }

    fun accountRemove(account: SavedAccount) {
        // if account is default account of tablet mode,
        // reset default.
        if (account.db_id == PrefL.lpTabletTootDefaultAccount.value) {
            PrefL.lpTabletTootDefaultAccount.value = -1L
        }
        daoSavedAccount.delete(account.db_id)
        // appServerUnregister(context.applicationContextSafe, account)
    }

    fun updateTokenInfo(item: SavedAccount, auth2Result: Auth2Result) {
        item.tokenJson = auth2Result.tokenJson
        item.loginAccount = auth2Result.tootAccount
        item.misskeyVersion = auth2Result.tootInstance.misskeyVersionMajor
        daoSavedAccount.save(item)
    }

    // notification_tagがもう使われてない
//    private fun appServerUnregister(context: Context, account: SavedAccount) {
//        launchIO {
//            try {
//                val installId = PrefDevice.from(context).getString(PrefDevice.KEY_INSTALL_ID, null)
//                if (installId?.isEmpty() != false) {
//                    error("missing install_id")
//                }
//
//                val tag = "" // notification_tagはもう使われてない
//                if (tag.isNullO) {
//                    error("missing notification_tag")
//                }
//
//                val call = App1.ok_http_client.newCall(
//                    "instance_url=${
//                        "https://${account.apiHost.ascii}".encodePercent()
//                    }&app_id=${
//                        context.packageName.encodePercent()
//                    }&tag=$tag"
//                        .toFormRequestBody()
//                        .toPost()
//                        .url("$APP_SERVER/unregister")
//                        .build()
//                )
//
//                val response = call.await()
//                if (!response.isSuccessful) {
//                    log.e("appServerUnregister: $response")
//                }
//            } catch (ex: Throwable) {
//                log.e(ex, "appServerUnregister failed.")
//            }
//        }
//    }
}
