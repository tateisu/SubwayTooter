package jp.juggler.subwaytooter.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import jp.juggler.subwaytooter.pref.PrefDevice
import jp.juggler.subwaytooter.pref.prefDevice
import jp.juggler.util.log.LogCategory
import jp.juggler.util.os.applicationContextSafe
import jp.juggler.util.os.checkAppForeground

/**
 * FCMのイベントを受け取るサービス。
 * - IntentServiceの一種なのでワーカースレッドから呼ばれる。runBlockingして良し。
 */
class MyFcmService : FirebaseMessagingService() {
    companion object {
        private val log = LogCategory("MyFcmService")
    }

    /**
     * FCMデバイストークンが更新された
     */
    override fun onNewToken(token: String) {
        try {
            checkAppForeground("MyFcmService.onNewToken")
            val context = applicationContextSafe
            when (context.prefDevice.pushDistributor) {
                null, "", PrefDevice.PUSH_DISTRIBUTOR_FCM -> {
                    PushWorker.enqueueRegisterEndpoint(context)
                }
            }
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
            applicationContextSafe.pushRepo.handleFcmMessage(remoteMessage.data)
        } catch (ex: Throwable) {
            log.e(ex, "onMessageReceived failed.")
        } finally {
            checkAppForeground("MyFcmService.onMessageReceived")
        }
    }
}
