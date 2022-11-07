//package jp.juggler.subwaytooter.notification
//
//import android.app.Service
//import android.content.Context
//import android.content.Intent
//import android.os.IBinder
//import android.os.SystemClock
//import androidx.core.content.ContextCompat
//import jp.juggler.subwaytooter.global.appDispatchers
//import jp.juggler.subwaytooter.notification.CheckerWakeLocks.Companion.checkerWakeLocks
//import jp.juggler.util.EmptyScope
//import jp.juggler.util.LogCategory
//import jp.juggler.util.launchMain
//import kotlinx.coroutines.channels.Channel
//import kotlinx.coroutines.channels.ClosedReceiveChannelException
//import kotlinx.coroutines.launch
//import kotlin.math.max
//
//class ForegroundPollingService : Service() {
//    companion object {
//        private val log = LogCategory("ForegroundPollingService")
//        private const val NOTIFICATION_ID_FOREGROUND_POLLING = 4
//        private const val EXTRA_ACCOUNT_DB_ID = "accountDbId"
//        private const val EXTRA_MESSAGE_ID = "messageId"
//
//        fun start(
//            context: Context,
//            messageId: String?,
//            dbId: Long,
//        ) {
//            val intent = Intent(context, ForegroundPollingService::class.java).apply {
//                putExtra(EXTRA_ACCOUNT_DB_ID, dbId)
//                putExtra(EXTRA_MESSAGE_ID, messageId)
//            }
//            ContextCompat.startForegroundService(context, intent)
//        }
//    }
//
//    private class Item(
//        val accountDbId: Long,
//        var lastRequired: Long = 0L,
//        var lastHandled: Long = 0L,
//        var lastStartId: Int = 0,
//    )
//
//    private val map = HashMap<Long, Item>()
//    private val channel = Channel<Long>()
//
//    override fun onBind(intent: Intent?): IBinder? = null
//
//    override fun onCreate() {
//        log.i("onCreate")
//        super.onCreate()
//        checkerWakeLocks(this).acquireWakeLocks()
//    }
//
//    override fun onDestroy() {
//        log.i("onDestroy")
//        super.onDestroy()
//        checkerWakeLocks(this).releasePowerLocks()
//        channel.close()
//    }
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        val accountDbId = intent?.getLongExtra(EXTRA_ACCOUNT_DB_ID, -1L) ?: -1L
//        val now = SystemClock.elapsedRealtime()
//        log.i("onStartCommand startId=$startId, accountDbId=$accountDbId")
//        synchronized(map) {
//            map.getOrPut(accountDbId) {
//                Item(accountDbId = accountDbId)
//            }.apply {
//                lastRequired = now
//                lastStartId = startId
//            }
//        }
//        launchMain {
//            try {
//                channel.send(now)
//            } catch (ex: Throwable) {
//                log.trace(ex)
//            }
//        }
//        return START_NOT_STICKY
//    }
//
//    init {
//        EmptyScope.launch(appDispatchers.default) {
//            var lastStartId = 0
//            while (true) {
//                try {
//                    val target = synchronized(map) {
//                        map.values
//                            .filter { it.lastRequired > it.lastHandled }
//                            .minByOrNull { it.lastRequired }
//                            ?.also { it.lastHandled = it.lastRequired }
//                    }
//                    if (target != null) {
//                        lastStartId = max(lastStartId, target.lastStartId)
//                        check(target.accountDbId)
//                        continue
//                    }
//                    log.i("stopSelf lastStartId=$lastStartId")
//                    if (lastStartId != 0) stopSelf(lastStartId)
//                    channel.receive()
//                } catch (ignored: ClosedReceiveChannelException) {
//                    log.i("channel closed.")
//                    break
//                } catch (ex: Throwable) {
//                    log.trace(ex)
//                }
//            }
//        }
//    }
//
//    private suspend fun check(accountDbId: Long) {
//        try {
//            PollingChecker(
//                context = this@ForegroundPollingService,
//                accountDbId = accountDbId
//            ).check { a, s ->
//                val text = "[${a.acct.pretty}]${s.desc}"
//                CheckerNotification.showMessage(this, text) {
//                    startForeground(NOTIFICATION_ID_FOREGROUND_POLLING, it)
//                }
//            }
//        } catch (ex: Throwable) {
//            log.trace(ex)
//        }
//    }
//}
