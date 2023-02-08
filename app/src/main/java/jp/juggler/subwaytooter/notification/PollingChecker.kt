package jp.juggler.subwaytooter.notification

import android.content.Context
import androidx.core.app.NotificationCompat
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootApiCallback
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.notification.CheckerWakeLocks.Companion.checkerWakeLocks
import jp.juggler.subwaytooter.notification.PullNotification.getMessageNotifications
import jp.juggler.subwaytooter.notification.PullNotification.removeMessageNotification
import jp.juggler.subwaytooter.notification.PullNotification.showMessageNotification
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.table.*
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.notEmpty
import jp.juggler.util.log.LogCategory
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.math.min

/**
 * 1アカウント,1回ごとの通知チェック処理
 */
class PollingChecker(
    val context: Context,
    private val accountDbId: Long,
    private var injectData: List<TootNotification> = emptyList(),
) {
    companion object {
        val log = LogCategory("PollingChecker")

        val commonMutex = Mutex()

        private val mutexMap = ConcurrentHashMap<Long, Mutex>()
        fun accountMutex(accountDbId: Long): Mutex = mutexMap.getOrPut(accountDbId) { Mutex() }

        private val activeCheckers = LinkedList<PollingChecker>()

        private fun addActiveChecker(c: PollingChecker) {
            synchronized(activeCheckers) {
                activeCheckers.add(c)
            }
        }

        private fun removeActiveChecker(c: PollingChecker) {
            synchronized(activeCheckers) {
                activeCheckers.remove(c)
            }
        }

        suspend fun cancelAllChecker() {
            synchronized(activeCheckers) {
                for (c in activeCheckers) {
                    try {
                        c.checkJob.cancel()
                    } catch (ex: Throwable) {
                        log.e(ex, "checkJob.cancel failed.")
                    }
                }
            }
            while (true) {
                val activeCount = synchronized(activeCheckers) {
                    activeCheckers.size
                }
                if (activeCount == 0) break
                log.w("cancelAllChecker: waiting activeCheckers. $activeCount")
                delay(333L)
            }
        }
    }

    private val wakelocks = checkerWakeLocks(context)

    private lateinit var cache: NotificationCache

    private val client = TootApiClient(
        context,
        callback = object : TootApiCallback {
            override suspend fun isApiCancelled(): Boolean {
                if (!coroutineContext.isActive) {
                    log.w("coroutineContext is not active!")
                }
                return !coroutineContext.isActive
            }
        }
    )

    private val checkJob = Job()

    private fun createPolicyFilter(
        account: SavedAccount,
    ): (TootNotification) -> Boolean = when (account.pushPolicy) {

        "followed" -> { it ->
            val who = it.account
            when {
                who == null -> true
                account.isMe(who) -> true

                else -> daoUserRelation.load(account.db_id, who.id).following
            }
        }

        "follower" -> { it ->
            val who = it.account
            when {
                it.type == TootNotification.TYPE_FOLLOW ||
                        it.type == TootNotification.TYPE_FOLLOW_REQUEST -> true

                who == null -> true
                account.isMe(who) -> true

                else -> daoUserRelation.load(account.db_id, who.id).followed_by
            }
        }

        "none" -> { _ -> false }

        else -> { _ -> true }
    }

    /**
     *
     * @param onlyEnqueue Workerの定期実行ON/OFFの更新だけを行う
     */
    suspend fun check(
        checkNetwork: Boolean = true,
        onlyEnqueue: Boolean = false,
        progress: suspend (SavedAccount, PollingState) -> Unit,
    ) {
        try {
            addActiveChecker(this@PollingChecker)

            // double check
            if (importProtector.get()) {
                log.w("aborted by importProtector.")
                return
            }

            withContext(AppDispatchers.DEFAULT + checkJob) {
                if (importProtector.get()) {
                    log.w("aborted by importProtector.")
                    return@withContext
                }

                val account = daoSavedAccount.loadAccount(accountDbId)
                if (account == null || account.isPseudo || !account.isConfirmed) {
                    // 疑似アカウントはチェック対象外
                    // 未確認アカウントはチェック対象外
                    return@withContext
                }

                client.account = account

                if (checkNetwork) {
                    progress(account, PollingState.CheckNetworkConnection)
                    wakelocks.checkConnection()
                }

                commonMutex.withLock {
                    // グローバル変数の暖気
                    TootStatus.updateMuteData()
                }

//                // installIdとデバイストークンの取得
//                val deviceToken = loadFirebaseMessagingToken(context)
//                loadInstallId(context, account, deviceToken, progress)

                accountMutex(accountDbId).withLock {
                    if (!account.isRequiredPullCheck()) {
                        // 通知チェックの定期実行が不要なら
                        // 通知表示のエラーをクリアする
                        daoAccountNotificationStatus.updateNotificationError(
                            account.acct,
                            null
                        )
                        log.i("notification check not required.")
                        return@withLock
                    }

                    progress(account, PollingState.CheckNotifications)

                    PollingWorker2.enqueuePolling(context)
                    if (onlyEnqueue) {
                        // 定期実行状態の更新だけを行うフラグ
                        log.i("exit due to onlyEnqueue")
                        return@withLock
                    }

                    injectData.notEmpty()?.let { list ->
                        log.d("processInjectedData ${account.acct} size=${list.size}")
                        val nc = NotificationCache(accountDbId)
                        daoNotificationCache.loadInto(nc)
                        daoNotificationCache.inject(nc, account, list)
                    }

                    cache = NotificationCache(account.db_id).apply {
                        daoNotificationCache.loadInto(this)
                        requestAsync(
                            daoNotificationCache,
                            client,
                            account,
                            account.notificationFlags(),
                        ) { result ->
                            // 通知取得のエラーを保存する
                            daoAccountNotificationStatus.updateNotificationError(
                                account.acct,
                                "${result.error} ${result.requestInfo}".trim()
                            )
                            if (result.error?.contains("Timeout") == true &&
                                !account.dontShowTimeout
                            ) {
                                progress(account, PollingState.Timeout)
                            }
                        }
                    }

                    if (PrefB.bpSeparateReplyNotificationGroup.value) {
                        var tr = TrackingRunner(
                            account = account,
                            trackingType = TrackingType.NotReply,
                            trackingName = PullNotification.TRACKING_NAME_DEFAULT
                        )
                        tr.checkAccount()
                        yield()
                        tr.updateNotification()
                        //
                        tr = TrackingRunner(
                            account = account,
                            trackingType = TrackingType.Reply,
                            trackingName = PullNotification.TRACKING_NAME_REPLY
                        )
                        tr.checkAccount()
                        yield()
                        tr.updateNotification()
                    } else {
                        val tr = TrackingRunner(
                            account = account,
                            trackingType = TrackingType.All,
                            trackingName = PullNotification.TRACKING_NAME_DEFAULT
                        )
                        tr.checkAccount()
                        yield()
                        tr.updateNotification()
                    }
                }
            }
        } finally {
            removeActiveChecker(this)
        }
    }

    inner class TrackingRunner(
        val account: SavedAccount,
        var trackingType: TrackingType = TrackingType.All,
        var trackingName: String = "",
    ) {
        private val notificationManager = wakelocks.notificationManager

        private lateinit var nr: NotificationTracking
        private val duplicateCheck = HashSet<EntityId>()
        private val dstListData = LinkedList<NotificationData>()
        val policyFilter = createPolicyFilter(account)

        private val parser = TootParser(context, account)

        suspend fun checkAccount() {

            fun JsonObject.isMention() =
                when (NotificationCache.parseNotificationType(account, this)) {
                    TootNotification.TYPE_REPLY, TootNotification.TYPE_MENTION -> true
                    else -> false
                }

            val jsonList = when (trackingType) {
                TrackingType.All -> cache.data
                TrackingType.Reply -> cache.data.filter { it.isMention() }
                TrackingType.NotReply -> cache.data.filter { !it.isMention() }
            }

            this.nr = daoNotificationTracking
                .load(account.acct, account.db_id, trackingName)

            // 新しい順に並んでいる。先頭から10件までを処理する。ただし処理順序は古い方から
            val size = min(10, jsonList.size)
            for (i in (0 until size).reversed()) {
                yield()
                updateSub(jsonList[i])
            }
            yield()

            // 種別チェックより先に、cache中の最新のIDを「最後に表示した通知」に指定する
            // nid_show は通知タップ時に参照されるので、通知を表示する際は必ず更新・保存する必要がある
            // 種別チェックより優先する
            val latestId = cache.filterLatestId(account) {
                when (trackingType) {
                    TrackingType.Reply -> it.isMention()
                    TrackingType.NotReply -> !it.isMention()
                    else -> true
                }
            }
            if (latestId != null) nr.nid_show = latestId
            daoNotificationTracking.save(account.acct, nr)
        }

        private fun updateSub(src: JsonObject) {

            val id = NotificationCache.getEntityOrderId(account, src)
            if (id.isDefault || duplicateCheck.contains(id)) return
            duplicateCheck.add(id)

            // タップ・削除した通知のIDと同じか古いなら対象外
            if (!id.isNewerThan(nr.nid_read)) {
                log.d("update_sub: ${account.acct} skip old notification $id")
                return
            }

            log.d("update_sub: found data that id=$id, > read id ${nr.nid_read}")

            val notification = parser.notification(src) ?: return

            // プッシュ通知で既出なら通知しない
            // プルの場合同じ通知が何度もここを通るので、既出フラグを立てない
            if (daoNotificationShown.isDuplicate(account.acct, notification.id.toString())) {
                log.i("update_sub: skip duplicate. ${account.acct} ${notification.id}")
                return
            }

            // アプリミュートと単語ミュート
            if (notification.status?.checkMuted() == true) return

            // ふぁぼ魔ミュート
            when (notification.type) {
                TootNotification.TYPE_REBLOG,
                TootNotification.TYPE_FAVOURITE,
                TootNotification.TYPE_FOLLOW,
                TootNotification.TYPE_FOLLOW_REQUEST,
                TootNotification.TYPE_FOLLOW_REQUEST_MISSKEY,
                -> {
                    val whoAcct = notification.account
                        ?.let { account.getFullAcct(it) }
                    if (whoAcct?.let { TootStatus.favMuteSet?.contains(it) } == true) {
                        log.d("${whoAcct.pretty} is in favMuteSet.")
                        return
                    }
                }
            }

            // Mastodon 3.4.0rc1 push policy
            if (!policyFilter(notification)) return

            // 後から処理したものが先頭に来る
            dstListData.add(0, NotificationData(account, notification))
        }

        fun updateNotification() {
            val notificationTag = when (trackingName) {
                "" -> "${account.db_id}/_"
                else -> "${account.db_id}/$trackingName"
            }

            val nt = daoNotificationTracking
                .load(account.acct, account.db_id, trackingName)
            when (val first = dstListData.firstOrNull()) {
                null -> {
                    log.d("showNotification[${account.acct.pretty}/$notificationTag] cancel notification.")
                    notificationManager.removeMessageNotification(account, notificationTag)
                }
                else -> {
                    when {
                        // 先頭にあるデータが同じなら、通知を更新しない
                        // このマーカーは端末再起動時にリセットされるので、再起動後は通知が出るはず
                        first.notification.time_created_at == nt.post_time && first.notification.id == nt.post_id ->
                            log.d("showNotification[${account.acct.pretty}] id=${first.notification.id} is already shown.")

                        PrefB.bpDivideNotification.value -> {
                            updateNotificationDivided(notificationTag, nt)
                            daoNotificationTracking.updatePost(
                                first.notification.id,
                                first.notification.time_created_at,
                                nt,
                            )
                        }

                        else -> {
                            updateNotificationMerged(notificationTag, first)
                            daoNotificationTracking.updatePost(
                                first.notification.id,
                                first.notification.time_created_at,
                                nt,
                            )
                        }
                    }
                }
            }
        }

        private fun updateNotificationDivided(
            notificationTag: String,
            nt: NotificationTracking,
        ) {
            log.d("updateNotificationDivided[${account.acct.pretty}] creating notification(1)")

            val activeNotificationMap = notificationManager.getMessageNotifications(notificationTag)

            val lastPostTime = nt.post_time
            val lastPostId = nt.post_id

            for (item in dstListData.reversed()) {
                val itemTag = "$notificationTag/${item.notification.id}"

                if (lastPostId != null &&
                    item.notification.time_created_at <= lastPostTime &&
                    item.notification.id <= lastPostId
                ) {
                    // 掲載済みデータより古い通知は再表示しない
                    log.d("ignore $itemTag ${item.notification.time_created_at} <= $lastPostTime && ${item.notification.id} <= $lastPostId")
                    continue
                }

                // ignore if already showing
                if (activeNotificationMap.remove(itemTag) != null) {
                    log.d("ignore $itemTag is in activeNotificationMap")
                    continue
                }

                showMessageNotification(
                    context,
                    account,
                    trackingType,
                    itemTag,
                    notificationId = item.notification.id.toString()
                ) { builder ->
                    builder.setWhen(item.notification.time_created_at)
                    val summary = item.notification.getNotificationLine(context)
                    builder.setContentTitle(summary)
                    when (val content = item.notification.status?.decoded_content?.notEmpty()) {
                        null -> builder.setContentText(item.accessInfo.acct.pretty)
                        else -> {
                            val style = NotificationCompat.BigTextStyle()
                                .setBigContentTitle(summary)
                                .setSummaryText(item.accessInfo.acct.pretty)
                                .bigText(content)
                            builder.setStyle(style)
                        }
                    }
                }
            }
            // リストにない通知は消さない。ある通知をユーザが指で削除した際に他の通知が残ってほしい場合がある
        }

        private fun updateNotificationMerged(
            notificationTag: String,
            first: NotificationData,
        ) {
            log.d("updateNotificationMerged[${account.acct.pretty}] creating notification(1)")
            showMessageNotification(
                context,
                account,
                trackingType,
                notificationTag
            ) { builder ->
                builder.setWhen(first.notification.time_created_at)
                val a = first.notification.getNotificationLine(context)
                val dataList = dstListData
                if (dataList.size == 1) {
                    builder.setContentTitle(a)
                    builder.setContentText(account.acct.pretty)
                } else {
                    val header = context.getString(R.string.notification_count, dataList.size)
                    builder.setContentTitle(header).setContentText(a)

                    val style = NotificationCompat.InboxStyle()
                        .setBigContentTitle(header)
                        .setSummaryText(account.acct.pretty)

                    for (i in 0 until min(4, dataList.size)) {
                        style.addLine(dataList[i].notification.getNotificationLine(context))
                    }

                    builder.setStyle(style)
                }
            }
        }
    }
}
