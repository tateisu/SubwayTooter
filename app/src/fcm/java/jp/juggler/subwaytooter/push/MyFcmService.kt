package jp.juggler.subwaytooter.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import jp.juggler.util.log.LogCategory
import jp.juggler.util.os.checkAppForeground
import kotlinx.coroutines.runBlocking

/**
 * FCMのイベントを受け取るサービス。
 * - IntentServiceの一種なのでワーカースレッドから呼ばれる。runBlockingして良し。
 */
class MyFcmService : FirebaseMessagingService() {
    companion object{
        private val log = LogCategory("MyFcmService")
    }

    /**
     * FCMデバイストークンが更新された
     */
    override fun onNewToken(token: String) {
        try {
            checkAppForeground("MyFcmService.onNewToken")
            fcmHandler.onTokenChanged(token)
        } catch (ex: Throwable) {
            log.e(ex, "onNewToken failed.")
        } finally {
            checkAppForeground("MyFcmService.onNewToken")
        }
    }

    /**
     * メッセージを受信した
     * - ワーカースレッドから呼ばれる。runBlockingして良し。
     * - IntentServiceの一種なので、呼び出しの間はネットワークを使えるなどある
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        try {
            checkAppForeground("MyFcmService.onMessageReceived")
            runBlocking {
                fcmHandler.onMessageReceived( remoteMessage.data)
            }
        } catch (ex: Throwable) {
            log.e(ex, "onMessageReceived failed.")
        } finally {
            checkAppForeground("MyFcmService.onMessageReceived")
        }
    }
}
