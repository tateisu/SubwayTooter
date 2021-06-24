package jp.juggler.subwaytooter.notification

import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import jp.juggler.subwaytooter.ActCallback
import jp.juggler.subwaytooter.EventReceiver
import jp.juggler.subwaytooter.PrefB
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootApiCallback
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.table.*
import jp.juggler.util.*
import kotlinx.coroutines.*
import okhttp3.Call
import java.lang.ref.WeakReference
import java.util.*
import kotlin.math.min

class TaskRunner(
    private val pollingWorker: PollingWorker,
    val job: JobItem,
    private val taskId: TaskId,
    private val taskData: JsonObject,
) {
    companion object {
        private val log = LogCategory("TaskRunner")

        private var workerStatus = ""
            set(value) {
                field = value
                PollingWorker.workerStatus = value
            }
    }

    val context = pollingWorker.context
    val notificationManager = pollingWorker.notificationManager
    val pref = pollingWorker.pref

    val threadList = LinkedList<AccountRunner>()
    val errorInstance = ArrayList<String>()

    private fun createErrorNotification(instanceList: ArrayList<String>) {
        if (instanceList.isEmpty()) return

        // 通知タップ時のPendingIntent
        val clickIntent = Intent(context, ActCallback::class.java)
        // FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY を付与してはいけない
        clickIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val clickPi = PendingIntent.getActivity(
            context,
            3,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            // Android 8 から、通知のスタイルはユーザが管理することになった
            // NotificationChannel を端末に登録しておけば、チャネルごとに管理画面が作られる
            val channel = NotificationHelper.createNotificationChannel(
                context,
                "ErrorNotification",
                "Error",
                null,
                2 /* NotificationManager.IMPORTANCE_LOW */
            )
            NotificationCompat.Builder(context, channel.id)
        } else {
            NotificationCompat.Builder(context, "not_used")
        }

        builder
            .setContentIntent(clickPi)
            .setAutoCancel(true)
            .setSmallIcon(R.drawable.ic_notification) // ここは常に白テーマのアイコンを使う
            .setColor(
                ContextCompat.getColor(
                    context,
                    R.color.Light_colorAccent
                )
            ) // ここは常に白テーマの色を使う
            .setWhen(System.currentTimeMillis())
            .setGroup(context.packageName + ":" + "Error")

        val header = context.getString(R.string.error_notification_title)
        val summary = context.getString(R.string.error_notification_summary)

        builder
            .setContentTitle(header)
            .setContentText(summary + ": " + instanceList[0])

        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(header)
            .setSummaryText(summary)
        for (i in 0..4) {
            if (i >= instanceList.size) break
            style.addLine(instanceList[i])
        }
        builder.setStyle(style)

        notificationManager.notify(PollingWorker.NOTIFICATION_ID_ERROR, builder.build())
    }

    private fun NotificationData.getNotificationLine(): String {

        val name = when (PrefB.bpShowAcctInSystemNotification(pref)) {
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
            ->
                context.getString(R.string.display_name_replied_by, name)

            TootNotification.TYPE_RENOTE,
            TootNotification.TYPE_REBLOG,
            ->
                context.getString(R.string.display_name_boosted_by, name)

            TootNotification.TYPE_QUOTE ->
                context.getString(R.string.display_name_quoted_by, name)

            TootNotification.TYPE_STATUS ->
                context.getString(R.string.display_name_posted_by, name)

            TootNotification.TYPE_FOLLOW ->
                context.getString(R.string.display_name_followed_by, name)

            TootNotification.TYPE_UNFOLLOW ->
                context.getString(R.string.display_name_unfollowed_by, name)

            TootNotification.TYPE_FAVOURITE ->
                context.getString(R.string.display_name_favourited_by, name)

            TootNotification.TYPE_EMOJI_REACTION_PLEROMA,
            TootNotification.TYPE_EMOJI_REACTION,
            TootNotification.TYPE_REACTION,
            ->
                context.getString(R.string.display_name_reaction_by, name)

            TootNotification.TYPE_VOTE,
            TootNotification.TYPE_POLL_VOTE_MISSKEY,
            ->
                context.getString(R.string.display_name_voted_by, name)

            TootNotification.TYPE_FOLLOW_REQUEST,
            TootNotification.TYPE_FOLLOW_REQUEST_MISSKEY,
            ->
                context.getString(R.string.display_name_follow_request_by, name)

            TootNotification.TYPE_FOLLOW_REQUEST_ACCEPTED_MISSKEY ->
                context.getString(R.string.display_name_follow_request_accepted_by, name)

            TootNotification.TYPE_POLL ->
                context.getString(R.string.end_of_polling_from, name)

            else -> "?"
        }
    }

    private fun deleteCacheData(dbId: Long?) {
        if (dbId != null) {
            log.d("Notification clear! db_id=$dbId")
            SavedAccount.loadAccount(context, dbId) ?: return
            NotificationCache.deleteCache(dbId)
        }
    }

    private fun beforePolling(): Boolean {
        // タスクによってはポーリング前にすることがある
        @Suppress("NON_EXHAUSTIVE_WHEN")
        when (taskId) {

            TaskId.BootCompleted ->
                NotificationTracking.resetPostAll()

            TaskId.PackageReplaced ->
                NotificationTracking.resetPostAll()

            TaskId.DataInjected ->
                pollingWorker.processInjectedData(job.injectedAccounts)

            TaskId.ResetTrackingState ->
                NotificationTracking.resetTrackingState(taskData.long(PollingWorker.EXTRA_DB_ID))

            // プッシュ通知が届いた
            TaskId.FcmMessage -> {
                var bDone = false
                val tag = taskData.string(PollingWorker.EXTRA_TAG)
                if (tag != null) {
                    if (tag.startsWith("acct<>")) {
                        val acct = tag.substring(6)
                        val sa = SavedAccount.loadAccountByAcct(context, acct)
                        if (sa != null) {
                            NotificationCache.resetLastLoad(sa.db_id)
                            job.injectedAccounts.add(sa.db_id)
                            bDone = true
                        }
                    }
                    if (!bDone) {
                        for (sa in SavedAccount.loadByTag(context, tag)) {
                            NotificationCache.resetLastLoad(sa.db_id)
                            job.injectedAccounts.add(sa.db_id)
                            bDone = true
                        }
                    }
                }
                if (!bDone) {
                    // タグにマッチする情報がなかった場合、全部読み直す
                    NotificationCache.resetLastLoad()
                }
            }

            TaskId.Clear -> {
                deleteCacheData(taskData.long(PollingWorker.EXTRA_DB_ID))
            }

            TaskId.NotificationDelete -> {
                val dbId = taskData.long(PollingWorker.EXTRA_DB_ID)
                val type =
                    TrackingType.parseStr(taskData.string(PollingWorker.EXTRA_NOTIFICATION_TYPE))
                val typeName = type.typeName
                val id = taskData.string(PollingWorker.EXTRA_NOTIFICATION_ID)
                log.d("Notification deleted! db_id=$dbId,type=$type,id=$id")
                if (dbId != null) {
                    NotificationTracking.updateRead(dbId, typeName)
                }
                return false
            }

            TaskId.NotificationClick -> {
                val dbId = taskData.long(PollingWorker.EXTRA_DB_ID)
                val type =
                    TrackingType.parseStr(taskData.string(PollingWorker.EXTRA_NOTIFICATION_TYPE))
                val typeName = type.typeName
                val id = taskData.string(PollingWorker.EXTRA_NOTIFICATION_ID).notEmpty()
                log.d("Notification clicked! db_id=$dbId,type=$type,id=$id")
                if (dbId != null) {
                    // 通知をキャンセル
                    val notificationTag = when (typeName) {
                        "" -> "$dbId/_"
                        else -> "$dbId/$typeName"
                    }
                    if (id != null) {
                        val itemTag = "$notificationTag/$id"
                        notificationManager.cancel(itemTag, PollingWorker.NOTIFICATION_ID)
                    } else {
                        notificationManager.cancel(
                            notificationTag,
                            PollingWorker.NOTIFICATION_ID
                        )
                    }
                    // DB更新処理
                    NotificationTracking.updateRead(dbId, typeName)
                }
                return false
            }
        }
        return true
    }

    private suspend fun prepareInstallId() {
        // インストールIDを生成する
        // インストールID生成時にSavedAccountテーブルを操作することがあるので
        // アカウントリストの取得より先に行う
        if (job.installId == null) {
            PollingWorker.workerStatus = "make install id"
            job.installId = PollingWorker.prepareInstallId(context, job)
        }
    }

    private suspend fun startForAccount(sa: SavedAccount) {
        if (sa.isPseudo) return
        threadList.add(AccountRunner(sa).apply { start() })
    }

    private suspend fun waitAllAccounts() {
        while (true) {
            // 同じホスト名が重複しないようにSetに集める
            val liveSet = TreeSet<Host>()
            for (t in threadList) {
                if (!t.isActive) continue
                if (job.isJobCancelled) t.cancel()
                liveSet.add(t.account.apiHost)
            }
            if (liveSet.isEmpty()) break
            PollingWorker.workerStatus =
                "waiting ${liveSet.joinToString(", ") { it.pretty }}"
            delay(if (job.isJobCancelled) 100L else 1000L)
        }
    }

    suspend fun runTask() {
        workerStatus = "start task $taskId"

        coroutineScope {
            try {
                if (!beforePolling()) return@coroutineScope

                prepareInstallId()

                // アカウント別に処理スレッドを作る
                PollingWorker.workerStatus = "create account threads"

                if (job.injectedAccounts.isNotEmpty()) {
                    // 更新対象アカウントが限られているなら、そのdb_idだけ処理する
                    job.injectedAccounts.forEach { dbId ->
                        SavedAccount.loadAccount(context, dbId)?.let { startForAccount(it) }
                    }
                } else {
                    // 全てのアカウントを処理する
                    SavedAccount.loadAccountList(context).forEach { startForAccount(it) }
                }

                waitAllAccounts()

                synchronized(errorInstance) {
                    createErrorNotification(errorInstance)
                }

                if (!job.isJobCancelled) job.bPollingComplete = true
            } catch (ex: Throwable) {
                log.trace(ex, "task execution failed.")
            } finally {
                log.d(")runTask: taskId=$taskId")
                PollingWorker.workerStatus = "end task $taskId"
            }
        }
    }

    inner class AccountRunner(val account: SavedAccount) {

        private var suspendJob: Job? = null

        private lateinit var parser: TootParser

        private lateinit var cache: NotificationCache

        private var currentCall: WeakReference<Call>? = null

        private var policyFilter: (TootNotification) -> Boolean = when (account.push_policy) {

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

        ///////////////////

        val isActive: Boolean
            get() = suspendJob?.isActive ?: true

        private val onCallCreated: (Call) -> Unit =
            { currentCall = WeakReference(it) }

        private val client = TootApiClient(context, callback = object : TootApiCallback {
            override val isApiCancelled: Boolean
                get() = job.isJobCancelled || (suspendJob?.isCancelled == true)
        }).apply {
            currentCallCallback = onCallCreated
        }

        private val favMuteSet: HashSet<Acct> get() = job.favMuteSet

        private val onError: (TootApiResult) -> Unit = { result ->
            val sv = result.error
            if (sv?.contains("Timeout") == true && !account.dont_show_timeout) {
                synchronized(errorInstance) {
                    if (!errorInstance.any { it == sv }) errorInstance.add(sv)
                }
            }
        }

        fun cancel() {
            try {
                currentCall?.get()?.cancel()
            } catch (ex: Throwable) {
                log.trace(ex)
            }
        }

        suspend fun start() {
            coroutineScope {
                this@AccountRunner.suspendJob = launch(Dispatchers.IO) {
                    runSuspend()
                }
            }
        }

        private suspend fun runSuspend() {
            try {
                // 疑似アカウントはチェック対象外
                if (account.isPseudo) return

                // 未確認アカウントはチェック対象外
                if (!account.isConfirmed) return

                log.d("${account.acct}: runSuspend start.")

                client.account = account

                val wps = PushSubscriptionHelper(context, account)

                if (wps.flags != 0) {
                    job.bPollingRequired.set(true)

                    val (instance, instanceResult) = TootInstance.get(client)
                    if (instance == null) {
                        if (instanceResult != null) {
                            log.e("${instanceResult.error} ${instanceResult.requestInfo}".trim())
                            account.updateNotificationError("${instanceResult.error} ${instanceResult.requestInfo}".trim())
                        }
                        return
                    }

                    if (job.isJobCancelled) return
                }

                wps.updateSubscription(client) ?: return // cancelled.

                val wpsLog = wps.logString
                if (wpsLog.isNotEmpty()) {
                    log.d("PushSubscriptionHelper: ${account.acct.pretty} $wpsLog")
                }

                if (job.isJobCancelled) return

                if (wps.flags == 0) {
                    if (account.lastNotificationError != null) {
                        account.updateNotificationError(null)
                    }
                    return
                }

                this.cache = NotificationCache(account.db_id).apply {
                    load()
                    requestAsync(
                        client,
                        account,
                        wps.flags,
                        onError = onError,
                        isCancelled = { job.isJobCancelled }
                    )
                }

                if (job.isJobCancelled) return

                this.parser = TootParser(context, account)

                if (PrefB.bpSeparateReplyNotificationGroup(pref)) {
                    var tr = TrackingRunner(
                        trackingType = TrackingType.NotReply,
                        trackingName = NotificationHelper.TRACKING_NAME_DEFAULT
                    )
                    tr.checkAccount()
                    if (job.isJobCancelled) return
                    tr.updateNotification()
                    //
                    tr = TrackingRunner(
                        trackingType = TrackingType.Reply,
                        trackingName = NotificationHelper.TRACKING_NAME_REPLY
                    )
                    tr.checkAccount()
                    if (job.isJobCancelled) return
                    tr.updateNotification()
                } else {
                    val tr = TrackingRunner(
                        trackingType = TrackingType.All,
                        trackingName = NotificationHelper.TRACKING_NAME_DEFAULT
                    )
                    tr.checkAccount()
                    if (job.isJobCancelled) return
                    tr.updateNotification()
                }
                log.i("runSuspend complete normally.")
            } catch (ex: Throwable) {
                log.trace(ex)
            } finally {
                pollingWorker.notifyWorker()
            }
        }

        inner class TrackingRunner(
            var trackingType: TrackingType = TrackingType.All,
            var trackingName: String = "",
        ) {

            private lateinit var nr: NotificationTracking
            private val duplicateCheck = HashSet<EntityId>()
            private val dstListData = LinkedList<NotificationData>()

            fun checkAccount() {

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
                    if (job.isJobCancelled) return
                    updateSub(jsonList[i])
                }
                if (job.isJobCancelled) return

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
                        removeNotification(notificationTag)
                    }
                    else -> {
                        when {
                            // 先頭にあるデータが同じなら、通知を更新しない
                            // このマーカーは端末再起動時にリセットされるので、再起動後は通知が出るはず
                            first.notification.time_created_at == nt.post_time && first.notification.id == nt.post_id ->
                                log.d("showNotification[${account.acct.pretty}] id=${first.notification.id} is already shown.")

                            Build.VERSION.SDK_INT >= 23 && PrefB.bpDivideNotification(pref) -> {
                                updateNotificationDivided(notificationTag, nt)
                                nt.updatePost(first.notification.id, first.notification.time_created_at)
                            }

                            else -> {
                                updateNotificationMerged(notificationTag, first)
                                nt.updatePost(first.notification.id, first.notification.time_created_at)
                            }
                        }
                    }
                }
            }

            private fun removeNotification(notificationTag: String) {
                if (Build.VERSION.SDK_INT >= 23 && PrefB.bpDivideNotification(pref)) {
                    notificationManager.activeNotifications?.filterNotNull()?.filter {
                        it.id == PollingWorker.NOTIFICATION_ID && it.tag.startsWith("$notificationTag/")
                    }?.forEach {
                        log.d("cancel: ${it.tag} context=${account.acct.pretty} $notificationTag")
                        notificationManager.cancel(it.tag, PollingWorker.NOTIFICATION_ID)
                    }
                } else {
                    notificationManager.cancel(notificationTag, PollingWorker.NOTIFICATION_ID)
                }
            }

            @TargetApi(23)
            private fun updateNotificationDivided(notificationTag: String, nt: NotificationTracking) {
                log.d("updateNotificationDivided[${account.acct.pretty}] creating notification(1)")

                val activeNotificationMap = notificationManager.activeNotifications?.filterNotNull()?.filter {
                    it.id == PollingWorker.NOTIFICATION_ID && it.tag.startsWith("$notificationTag/")
                }?.map { Pair(it.tag, it) }?.toMutableMap() ?: mutableMapOf()

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

                    createNotification(itemTag, notificationId = item.notification.id.toString()) { builder ->
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
                        if (Build.VERSION.SDK_INT < 26) setNotificationSound25(builder, item)
                    }
                }
                // リストにない通知は消さない。ある通知をユーザが指で削除した際に他の通知が残ってほしい場合がある
            }

            private fun updateNotificationMerged(
                notificationTag: String,
                first: NotificationData,
            ) {
                log.d("updateNotificationMerged[${account.acct.pretty}] creating notification(1)")
                createNotification(notificationTag) { builder ->
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
                    if (Build.VERSION.SDK_INT < 26) setNotificationSound25(builder, first)
                }
            }

            // Android 8 未満ではチャネルではなく通知に個別にスタイルを設定する
            @TargetApi(25)
            private fun setNotificationSound25(builder: NotificationCompat.Builder, item: NotificationData) {
                var iv = 0
                if (PrefB.bpNotificationSound(pref)) {
                    var soundUri: Uri? = null

                    try {
                        val whoAcct = account.getFullAcct(item.notification.account)
                        soundUri = AcctColor.getNotificationSound(whoAcct).mayUri()
                    } catch (ex: Throwable) {
                        log.trace(ex)
                    }
                    if (soundUri == null) {
                        soundUri = account.sound_uri.mayUri()
                    }

                    var bSoundSet = false
                    if (soundUri != null) {
                        try {
                            builder.setSound(soundUri)
                            bSoundSet = true
                        } catch (ex: Throwable) {
                            log.trace(ex)
                        }
                    }
                    if (!bSoundSet) {
                        iv = iv or NotificationCompat.DEFAULT_SOUND
                    }
                }

                if (PrefB.bpNotificationVibration(pref)) {
                    iv = iv or NotificationCompat.DEFAULT_VIBRATE
                }

                if (PrefB.bpNotificationLED(pref)) {
                    iv = iv or NotificationCompat.DEFAULT_LIGHTS
                }

                builder.setDefaults(iv)
            }

            private fun createNotification(
                notificationTag: String,
                notificationId: String? = null,
                setContent: (builder: NotificationCompat.Builder) -> Unit,
            ) {
                log.d("showNotification[${account.acct.pretty}] creating notification(1)")

                val builder = if (Build.VERSION.SDK_INT >= 26) {
                    // Android 8 から、通知のスタイルはユーザが管理することになった
                    // NotificationChannel を端末に登録しておけば、チャネルごとに管理画面が作られる
                    val channel = NotificationHelper.createNotificationChannel(
                        context,
                        account,
                        trackingName
                    )
                    NotificationCompat.Builder(context, channel.id)
                } else {
                    NotificationCompat.Builder(context, "not_used")
                }

                builder.apply {

                    val params = listOf(
                        "db_id" to account.db_id.toString(),
                        "type" to trackingType.str,
                        "notificationId" to notificationId
                    ).mapNotNull {
                        when (val second = it.second) {
                            null -> null
                            else -> "${it.first.encodePercent()}=${second.encodePercent()}"
                        }
                    }.joinToString("&")

                    val flag = PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)

                    PendingIntent.getActivity(
                        context,
                        257,
                        Intent(context, ActCallback::class.java).apply {
                            data = "subwaytooter://notification_click/?$params".toUri()
                            // FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY を付与してはいけない
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                        flag
                    )?.let { setContentIntent(it) }

                    PendingIntent.getBroadcast(
                        context,
                        257,
                        Intent(context, EventReceiver::class.java).apply {
                            action = EventReceiver.ACTION_NOTIFICATION_DELETE
                            data = "subwaytooter://notification_delete/?$params".toUri()
                        },
                        flag
                    )?.let { setDeleteIntent(it) }

                    setAutoCancel(true)

                    // 常に白テーマのアイコンを使う
                    setSmallIcon(R.drawable.ic_notification)

                    // 常に白テーマの色を使う
                    builder.color = ContextCompat.getColor(context, R.color.Light_colorAccent)

                    // Android 7.0 ではグループを指定しないと勝手に通知が束ねられてしまう。
                    // 束ねられた通知をタップしても pi_click が実行されないので困るため、
                    // アカウント別にグループキーを設定する
                    setGroup(context.packageName + ":" + account.acct.ascii)
                }

                log.d("showNotification[${account.acct.pretty}] creating notification(3)")
                setContent(builder)

                log.d("showNotification[${account.acct.pretty}] set notification...")
                notificationManager.notify(notificationTag, PollingWorker.NOTIFICATION_ID, builder.build())
            }
        }
    }
}
