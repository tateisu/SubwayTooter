package jp.juggler.subwaytooter

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import jp.juggler.subwaytooter.notification.PollingChecker
import jp.juggler.subwaytooter.notification.restartAllWorker
import jp.juggler.subwaytooter.pref.PrefDevice
import jp.juggler.subwaytooter.table.NotificationCache
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.LogCategory
import kotlinx.coroutines.runBlocking
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

    override fun onNewToken(token: String) {
        try {
            log.w("onTokenRefresh: token=$token")
            PrefDevice.from(this).edit().putString(PrefDevice.KEY_DEVICE_TOKEN, token).apply()
            restartAllWorker(this)
        } catch (ex: Throwable) {
            log.trace(ex, "onNewToken failed")
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val context = this

        val messageId = remoteMessage.messageId ?: return
        if (isDuplicateMessage(messageId)) return

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

        log.i("accounts.size=${accounts.size} thred=${Thread.currentThread().name}")
        runBlocking {
            accounts.forEach {
                check(it.db_id)
            }
        }
    }

    private suspend fun check(accountDbId: Long) {
        try {
            PollingChecker(
                context = this,
                accountDbId = accountDbId
            ).check { a, s ->
                val text = "[${a.acct.pretty}]${s.desc}"
                log.i(text)
            }
        } catch (ex: Throwable) {
            log.trace(ex)
        }
    }
}
