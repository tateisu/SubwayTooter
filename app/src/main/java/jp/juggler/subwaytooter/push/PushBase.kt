package jp.juggler.subwaytooter.push

import android.content.Context
import androidx.annotation.StringRes
import jp.juggler.subwaytooter.pref.PrefDevice
import jp.juggler.subwaytooter.table.AccountNotificationStatus
import jp.juggler.subwaytooter.table.PushMessage
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.data.digestSHA256
import jp.juggler.util.data.encodeBase64Url
import jp.juggler.util.data.encodeUTF8
import jp.juggler.util.data.notEmpty

/**
 * PushMastodon, PushMisskey13 のベースクラス
 */
abstract class PushBase {
    companion object {
        const val appServerUrlPrefix = "https://mastodon-msg.juggler.jp/api/v2/m"
    }

    interface SubscriptionLogger {
        val context: Context
        fun i(msg: String)
        fun i(@StringRes stringId: Int) = i(context.getString(stringId))

        fun e(msg: String)
        fun e(@StringRes stringId: Int) = i(context.getString(stringId))
        fun e(ex: Throwable, msg: String)
    }

    protected abstract val prefDevice: PrefDevice
    protected abstract val daoStatus: AccountNotificationStatus.Access

    // 購読の確認と更新
    // 失敗したらエラーメッセージを返す。成功したらnull
    abstract suspend fun updateSubscription(
        subLog: SubscriptionLogger,
        account: SavedAccount,
        willRemoveSubscription: Boolean,
        forceUpdate:Boolean,
    ):String?

    // プッシュメッセージのJSONデータを通知用に整形
    abstract suspend fun formatPushMessage(
        a: SavedAccount,
        pm: PushMessage,
    )

    fun deviceHash(a: SavedAccount) =
        "${prefDevice.installIdv2},${a.acct}".encodeUTF8().digestSHA256().encodeBase64Url()

    fun snsCallbackUrl(a: SavedAccount): String? =
        daoStatus.appServerHash(a.acct)?.notEmpty()?.let {
            "$appServerUrlPrefix/a_${it}/dh_${deviceHash(a)}"
        }
}
