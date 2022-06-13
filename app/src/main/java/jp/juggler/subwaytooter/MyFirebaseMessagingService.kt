package jp.juggler.subwaytooter

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import jp.juggler.subwaytooter.notification.ForegroundPollingService
import jp.juggler.subwaytooter.notification.restartAllWorker
import jp.juggler.subwaytooter.pref.PrefDevice
import jp.juggler.subwaytooter.table.NotificationCache
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.LogCategory
import java.util.*

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        internal val log = LogCategory("MyFirebaseMessagingService")

        private val pushMessageStatus = LinkedList<String>()

        // Pushメッセージが処理済みか調べる
        private fun isDuplicateMessage(messageId: String) =
            synchronized(pushMessageStatus) {
                when (pushMessageStatus.contains(messageId)) {
                    true -> true
                    else -> {
                        pushMessageStatus.addFirst(messageId)
                        while (pushMessageStatus.size > 100) {
                            pushMessageStatus.removeLast()
                        }
                        false
                    }
                }
            }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val context = this

        val accounts = ArrayList<SavedAccount>()
        for ((key, value) in remoteMessage.data) {
            log.w("onMessageReceived: $key=$value")
            when (key) {
                "notification_tag" -> {
                    SavedAccount.loadByTag(context, value).forEach { sa ->
                        NotificationCache.resetLastLoad(sa.db_id)
                        accounts.add(sa)
                    }
                }
                "acct" -> {
                    SavedAccount.loadAccountByAcct(context, value)?.let { sa ->
                        NotificationCache.resetLastLoad(sa.db_id)
                        accounts.add(sa)
                    }
                }
            }
        }

        if (accounts.isEmpty()) {
            // タグにマッチする情報がなかった場合、全部読み直す
            NotificationCache.resetLastLoad()
            accounts.addAll(SavedAccount.loadAccountList(context))
        }
        log.i("accounts.size=${accounts.size}")
        accounts.forEach {
            ForegroundPollingService.start(this, remoteMessage.messageId, it.db_id)
        }
    }

    override fun onNewToken(token: String) {
        try {
            log.w("onTokenRefresh: token=$token")
            PrefDevice.from(this).edit().putString(PrefDevice.KEY_DEVICE_TOKEN, token).apply()
            restartAllWorker(this)
        } catch (ex: Throwable) {
            log.trace(ex, "onNewToken failed")
        }
    }
}
