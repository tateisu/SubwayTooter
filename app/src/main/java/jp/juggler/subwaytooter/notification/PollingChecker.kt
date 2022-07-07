package jp.juggler.subwaytooter.notification

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootApiCallback
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.notification.CheckerWakeLocks.Companion.checkerWakeLocks
import jp.juggler.subwaytooter.notification.MessageNotification.getMessageNotifications
import jp.juggler.subwaytooter.notification.MessageNotification.removeMessageNotification
import jp.juggler.subwaytooter.notification.MessageNotification.setNotificationSound25
import jp.juggler.subwaytooter.notification.MessageNotification.showMessageNotification
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.table.*
import jp.juggler.util.JsonObject
import jp.juggler.util.LogCategory
import jp.juggler.util.notEmpty
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
                        log.trace(ex)
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

    private fun NotificationData.getNotificationLine(): String {

        val name = when (PrefB.bpShowAcctInSystemNotification()) {
            false -> notification.accountRef?.decoded_display_name

            true -> {
                val acctPretty = notification.accountRef?.get()?.acct?.pretty
                if (acctPretty?.isNotEmpty() == true) {
                    "@$acctPretty"
                } else {
                    null
                }
            }
        } ?: "?"

        return "- " + when (notification.type) {
            TootNotification.TYPE_MENTION,
            TootNotification.TYPE_REPLY,
            -> context.getString(R.string.display_name_replied_by, name)

            TootNotification.TYPE_RENOTE,
            TootNotification.TYPE_REBLOG,
            -> context.getString(R.string.display_name_boosted_by, name)

            TootNotification.TYPE_QUOTE,
            -> context.getString(R.string.display_name_quoted_by, name)

            TootNotification.TYPE_STATUS,
            -> context.getString(R.string.display_name_posted_by, name)

            TootNotification.TYPE_UPDATE,
            -> context.getString(R.string.display_name_updates_post, name)

            TootNotification.TYPE_STATUS_REFERENCE,
            -> context.getString(R.string.display_name_references_post, name)

            TootNotification.TYPE_FOLLOW,
            -> context.getString(R.string.display_name_followed_by, name)

            TootNotification.TYPE_UNFOLLOW,
            -> context.getString(R.string.display_name_unfollowed_by, name)

            TootNotification.TYPE_ADMIN_SIGNUP,
            -> context.getString(R.string.display_name_signed_up, name)

            TootNotification.TYPE_FAVOURITE,
            -> context.getString(R.string.display_name_favourited_by, name)

            TootNotification.TYPE_EMOJI_REACTION_PLEROMA,
            TootNotification.TYPE_EMOJI_REACTION,
            TootNotification.TYPE_REACTION,
            -> context.getString(R.string.display_name_reaction_by, name)

            TootNotification.TYPE_VOTE,
            TootNotification.TYPE_POLL_VOTE_MISSKEY,
            -> context.getString(R.string.display_name_voted_by, name)

            TootNotification.TYPE_FOLLOW_REQUEST,
            TootNotification.TYPE_FOLLOW_REQUEST_MISSKEY,
            -> context.getString(R.string.display_name_follow_request_by, name)

            TootNotification.TYPE_FOLLOW_REQUEST_ACCEPTED_MISSKEY,
            -> context.getString(R.string.display_name_follow_request_accepted_by, name)

            TootNotification.TYPE_POLL,
            -> context.getString(R.string.end_of_polling_from, name)

            else -> "?"
        }
    }

    private fun createPolicyFilter(
        account: SavedAccount,
    ): (TootNotification) -> Boolean = when (account.push_policy) {

        "followed" -> { it ->
            val who = it.account
            when {
                who == null -> true
                account.isMe(who) -> true

                else -> UserRelation.load(account.db_id, who.id).following
            }
        }

        "follower" -> { it ->
            val who = it.account
            when {
                it.type == TootNotification.TYPE_FOLLOW ||
                        it.type == TootNotification.TYPE_FOLLOW_REQUEST -> true

                who == null -> true
                account.isMe(who) -> true

                else -> UserRelation.load(account.db_id, who.id).followed_by
            }
        }

        "none" -> { _ -> false }

        else -> { _ -> true }
    }

    suspend fun check(
        checkNetwork: Boolean = true,
        onlySubscription: Boolean = false,
        progress: suspend (SavedAccount, PollingState) -> Unit,
    ) {
        try {
            addActiveChecker(this@PollingChecker)

            // double check
            if (importProtector.get()) {
                log.w("aborted by importProtector.")
                return
            }

            withContext(Dispatchers.Default + checkJob) {
                if (importProtector.get()) {
                    log.w("aborted by importProtector.")
                    return@withContext
                }

                val account = SavedAccount.loadAccount(context, accountDbId)
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
                    if (TootStatus.muted_app == null) {
                        TootStatus.muted_app = MutedApp.nameSet
                    }
                    if (TootStatus.muted_word == null) {
                        TootStatus.muted_word = MutedWord.nameSet
                    }
                }

                // installIdとデバイストークンの取得
                val deviceToken = loadFirebaseMessagingToken(context)
                loadInstallId(context, account, deviceToken, progress)

                val favMuteSet = commonMutex.withLock {
                    FavMute.acctSet
                }

                accountMutex(accountDbId).withLock {
                    val wps = PushSubscriptionHelper(context, account)
                    if (wps.flags != 0) {
                        progress(account, PollingState.CheckServerInformation)
                        val (instance, instanceResult) = TootInstance.get(client)
                        if (instance == null) {
                            account.updateNotificationError("${instanceResult?.error} ${instanceResult?.requestInfo}".trim())
                            error("can't get server information. ${instanceResult?.error} ${instanceResult?.requestInfo}".trim())
                        }
                    }

                    wps.updateSubscription(client, progress = progress)
                        ?: throw CancellationException()

                    val wpsLog = wps.logString
                    if (wpsLog.isNotEmpty()) {
                        log.w("subsctiption warning: ${account.acct.pretty} $wpsLog")
                    }

                    if (wps.flags == 0) {
                        if (account.lastNotificationError != null) {
                            account.updateNotificationError(null)
                        }
                        log.i("notification check not required.")
                        return@withLock
                    }
                    progress(account, PollingState.CheckNotifications)
                    PollingWorker2.enqueuePolling(context)
                    if (onlySubscription) {
                        log.i("exit due to onlySubscription")
                        return@withLock
                    }

                    injectData.notEmpty()?.let { list ->
                        log.d("processInjectedData ${account.acct} size=${list.size}")
                        NotificationCache(accountDbId).apply {
                            load()
                            inject(account, list)
                        }
                    }

                    cache = NotificationCache(account.db_id).apply {
                        load()
                        if( account.isMisskey && ! PrefB.bpMisskeyNotificationCheck() ){
                            log.d("skip misskey server. ${account.acct}")
                        }else{
                            requestAsync(
                                client,
                                account,
                                wps.flags,
                            ) { result ->
                                account.updateNotificationError("${result.error} ${result.requestInfo}".trim())
                                if (result.error?.contains("Timeout") == true &&
                                    !account.dont_show_timeout
                                ) {
                                    progress(account, PollingState.Timeout)
                                }
                            }
                        }
                    }

                    if (PrefB.bpSeparateReplyNotificationGroup()) {
                        var tr = TrackingRunner(
                            account = account,
                            favMuteSet = favMuteSet,
                            trackingType = TrackingType.NotReply,
                            trackingName = MessageNotification.TRACKING_NAME_DEFAULT
                        )
                        tr.checkAccount()
                        yield()
                        tr.updateNotification()
                        //
                        tr = TrackingRunner(
                            account = account,
                            favMuteSet = favMuteSet,
                            trackingType = TrackingType.Reply,
                            trackingName = MessageNotification.TRACKING_NAME_REPLY
                        )
                        tr.checkAccount()
                        yield()
                        tr.updateNotification()
                    } else {
                        val tr = TrackingRunner(
                            account = account,
                            favMuteSet = favMuteSet,
                            trackingType = TrackingType.All,
                            trackingName = MessageNotification.TRACKING_NAME_DEFAULT
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
        val favMuteSet: HashSet<Acct>,
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

            this.nr =
                NotificationTracking.load(account.acct.pretty, account.db_id, trackingName)

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
            nr.save(account.acct.pretty)
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

            // アプリミュートと単語ミュート
            if (notification.status?.checkMuted() == true) return

            // ふぁぼ魔ミュート
            when (notification.type) {
                TootNotification.TYPE_REBLOG,
                TootNotification.TYPE_FAVOURITE,
                TootNotification.TYPE_FOLLOW,
                TootNotification.TYPE_FOLLOW_REQUEST,
                TootNotification.TYPE_FOLLOW_REQUEST_MISSKEY,
                TootNotification.TYPE_ADMIN_SIGNUP,
                -> {
                    val who = notification.account
                    if (who != null && favMuteSet.contains(account.getFullAcct(who))) {
                        log.d("${account.getFullAcct(who)} is in favMuteSet.")
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

            val nt = NotificationTracking.load(account.acct.pretty, account.db_id, trackingName)
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

                        Build.VERSION.SDK_INT >= 23 && PrefB.bpDivideNotification() -> {
                            updateNotificationDivided(notificationTag, nt)
                            nt.updatePost(
                                first.notification.id,
                                first.notification.time_created_at
                            )
                        }

                        else -> {
                            updateNotificationMerged(notificationTag, first)
                            nt.updatePost(
                                first.notification.id,
                                first.notification.time_created_at
                            )
                        }
                    }
                }
            }
        }

        @TargetApi(23)
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

                notificationManager.showMessageNotification(
                    context,
                    account,
                    trackingName,
                    trackingType,
                    itemTag,
                    notificationId = item.notification.id.toString()
                ) { builder ->
                    builder.setWhen(item.notification.time_created_at)
                    val summary = item.getNotificationLine()
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
                    if (Build.VERSION.SDK_INT < 26) setNotificationSound25(account, builder, item)
                }
            }
            // リストにない通知は消さない。ある通知をユーザが指で削除した際に他の通知が残ってほしい場合がある
        }

        private fun updateNotificationMerged(
            notificationTag: String,
            first: NotificationData,
        ) {
            log.d("updateNotificationMerged[${account.acct.pretty}] creating notification(1)")
            notificationManager.showMessageNotification(
                context,
                account,
                trackingName,
                trackingType,
                notificationTag
            ) { builder ->
                builder.setWhen(first.notification.time_created_at)
                val a = first.getNotificationLine()
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
                        style.addLine(dataList[i].getNotificationLine())
                    }

                    builder.setStyle(style)
                }
                if (Build.VERSION.SDK_INT < 26) setNotificationSound25(account, builder, first)
            }
        }
    }
}
