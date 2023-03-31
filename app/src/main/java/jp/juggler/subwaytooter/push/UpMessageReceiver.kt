package jp.juggler.subwaytooter.push

import android.content.Context
import jp.juggler.subwaytooter.notification.dialogOrAlert
import jp.juggler.subwaytooter.push.PushWorker.Companion.enqueueUpEndpoint
import jp.juggler.util.log.LogCategory
import jp.juggler.util.os.checkAppForeground
import kotlinx.coroutines.runBlocking
import org.unifiedpush.android.connector.MessagingReceiver

/**
 * UnifiedPush のイベントを処理するレシーバー。
 * - メインスレッドで呼ばれてコルーチン的に辛い。
 * - データ保存だけして残りはUpWorkでバックグラウンド処理する。
 */
class UpMessageReceiver : MessagingReceiver() {
    companion object {
        private val log = LogCategory("UpMessageReceiver")
    }

    /**
     * registerApp が完了すると呼ばれる
     * - メインスレッドで呼ばれる
     * - UIから操作した直後なので、だいたいフォアグラウンド状態だからrunBlockingしなくてもいいかな。
     */
    override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
        try {
            enqueueUpEndpoint(context, endpoint)
        } catch (ex: Throwable) {
            log.e(ex, "onNewEndpoint failed.")
        }
    }

    /**
     * registerAppに失敗した
     * - 呼ばれるのを見たことがない…
     */
    override fun onRegistrationFailed(context: Context, instance: String) {
        checkAppForeground("UpMessageReceiver.onRegistrationFailed")
        context.dialogOrAlert("onRegistrationFailed: instance=$instance, thread=${Thread.currentThread().name}")
    }

    /**
     * 登録解除が完了したら呼ばれる
     * - メインスレッドで呼ばれる
     * - ntfyアプリ上から購読を削除した場合に呼ばれた。
     * - 特に何もしなくていいかな…
     */
    override fun onUnregistered(context: Context, instance: String) {
        checkAppForeground("UpMessageReceiver.onUnregistered")
    }

    /**
     * メッセージを受信した
     * - メインスレッドで呼ばれる
     * - runBlocking するべきかしないべきか迷う…
     * - これ契機でのサービス起動とかないはず。
     * - アイコンのロードが失敗するのかもしれない
     */
    override fun onMessage(context: Context, message: ByteArray, instance: String) {
        checkAppForeground("UpMessageReceiver.onMessage")
        runBlocking {
            try {
                context.pushRepo.saveUpMessage(message)
            } catch (ex: Throwable) {
                log.e(ex, "onMessage failed.")
            }
        }
    }
}
