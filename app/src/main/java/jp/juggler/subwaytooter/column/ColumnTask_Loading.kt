package jp.juggler.subwaytooter.column

import android.os.SystemClock
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.api.finder.*
import jp.juggler.subwaytooter.columnviewholder.scrollToTop
import jp.juggler.subwaytooter.notification.injectData
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.util.OpenSticker
import jp.juggler.util.*
import jp.juggler.util.coroutine.runOnMainLooper
import jp.juggler.util.coroutine.runOnMainLooperDelayed
import jp.juggler.util.data.JsonArray
import jp.juggler.util.data.JsonObject
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.withCaption
import jp.juggler.util.network.toPostRequestBuilder
import java.util.*

@Suppress("ClassNaming")
class ColumnTask_Loading(
    columnArg: Column,
) : ColumnTask(columnArg, ColumnTaskType.LOADING) {

    companion object {
        private val log = LogCategory("CT_Loading")
    }

    internal var listPinned: ArrayList<TimelineItem>? = null

    override suspend fun background(): TootApiResult? {
        ctStarted.set(true)

        if (PrefB.bpOpenSticker(pref)) {
            OpenSticker.loadAndWait()
        }

        val client = TootApiClient(context, callback = object : TootApiCallback {
            override suspend fun isApiCancelled() = isCancelled || column.isDispose.get()

            override suspend fun publishApiProgress(s: String) {
                runOnMainLooper {
                    if (isCancelled) return@runOnMainLooper
                    column.taskProgress = s
                    column.fireShowContent(reason = "loading progress", changeList = ArrayList())
                }
            }
        })

        client.account = accessInfo

        try {
            val result = accessInfo.checkConfirmed(context, client)
            if (result == null || result.error != null) return result

            column.keywordFilterTrees = column.encodeFilterTree(column.loadFilter2(client))

            if (!accessInfo.isNA) {
                val (instance, instanceResult) = TootInstance.get(client)
                instance ?: return instanceResult
                if (instance.instanceType == InstanceType.Pixelfed) {
                    return TootApiResult("currently Pixelfed instance is not supported.")
                }
            }

            return column.type.loading(this, client)
        } catch (ex: Throwable) {
            return TootApiResult(ex.withCaption("loading failed."))
        } finally {

            try {
                column.updateRelation(client, listTmp, column.whoAccount, parser)
            } catch (ex: Throwable) {
                log.e(ex, "updateRelation failed.")
            }
            ctClosed.set(true)
            runOnMainLooperDelayed(333L) {
                if (!isCancelled) column.fireShowColumnStatus()
            }
        }
    }

    override suspend fun handleResult(result: TootApiResult?) {
        if (column.isDispose.get()) return

        if (isCancelled || result == null) {
            return
        }

        column.bInitialLoading = false
        column.lastTask = null

        if (result.error != null) {
            column.mInitialLoadingError = "${result.error} ${result.requestInfo}".trim()
        } else {
            column.duplicateMap.clear()
            column.listData.clear()
            val listTmp = this.listTmp
            if (listTmp != null) {
                val listPinned = this.listPinned
                if (listPinned?.isNotEmpty() == true) {
                    val listNew = column.duplicateMap.filterDuplicate(listPinned)
                    column.listData.addAll(listNew)
                }

                val listNew = when (column.type) {

                    // 検索カラムはIDによる重複排除が不可能
                    ColumnType.SEARCH -> listTmp

                    // 編集履歴は投稿日時で重複排除する
                    ColumnType.STATUS_HISTORY -> column.duplicateMap.filterDuplicateByCreatedAt(
                        listTmp
                    )

                    // 他のカラムは重複排除してから追加
                    else -> column.duplicateMap.filterDuplicate(listTmp)
                }

                column.listData.addAll(listNew)
            }

            // 初回ロード完了時はストリーミングを開始させる場合がある
            column.appState.streamManager.updateStreamingColumns()
        }

        column.fireShowContent(reason = "loading updated", reset = true)

        // 初期ロードの直後は先頭に移動する
        column.viewHolder?.scrollToTop()

        column.updateMisskeyCapture()
    }

    /////////////////////////////////////////////////////////////////

    private fun addEmptyMessage(emptyMessage: String? = null) {
        if (emptyMessage != null && listTmp?.isEmpty() == true) {
            // フォロー/フォロワー一覧には警告の表示が必要だった
            val who = column.whoAccount?.get()
            if (!accessInfo.isMe(who)) {
                if (who != null && accessInfo.isRemoteUser(who)) {
                    listTmp?.add(
                        TootMessageHolder(
                            context.getString(R.string.follow_follower_list_may_restrict)
                        )
                    )
                }
                listTmp?.add(TootMessageHolder(emptyMessage))
            }
        }
    }

    private suspend fun <T : TimelineItem> loadMisskeyMaxId(
        logCaption: String,
        requester: suspend (EntityId?, EntityId?) -> TootApiResult?,
        arrayFinder: (JsonObject) -> JsonArray?,
        emptyMessage: String? = null,
        listParser: (parser: TootParser, jsonArray: JsonArray) -> List<T>,
        adder: (List<T>, Boolean) -> Unit,
        initialMaxId: EntityId? = null,
    ): TootApiResult? {
        val addToHead = false

        fun parseResult(result: TootApiResult?): Boolean {
            val first = listTmp?.isEmpty() != false

            result ?: return log.d("$logCaption: cancelled.")

            result.jsonObject?.let {
                if (column.pagingType == ColumnPagingType.Cursor) {
                    column.idOld = EntityId.mayNull(it.string("next"))
                }
                result.data = arrayFinder(it)
            }

            val array = result.jsonArray
                ?: return log.w("$logCaption: missing item list")

            val src = listParser(parser, array)
            if (listTmp == null) listTmp = ArrayList(src.size)
            adder(src, addToHead)

            if (first) addEmptyMessage(emptyMessage)

            val more = when (column.pagingType) {
                ColumnPagingType.Default ->
                    if (first) {
                        column.saveRange(bBottom = true, bTop = true, result = result, list = src)
                    } else {
                        column.saveRangeBottom(result, src)
                    }

                ColumnPagingType.Offset -> {
                    column.offsetNext += src.size
                    true
                }
                else -> true
            }
            return when {
                !more -> log.d("$logCaption: no more items")
                src.isEmpty() -> log.d("$logCaption: empty list")
                else -> true
            }
        }

        val timeStart = SystemClock.elapsedRealtime()
        // 初回の取得
        val firstResult = requester(initialMaxId, null)
        var more = parseResult(firstResult)
        // フィルタなどが有効な場合は2回目以降の取得
        while (more) more = when {
            isCancelled ->
                log.d("$logCaption: cancelled.")
            !column.isFilterEnabled ->
                log.d("$logCaption: isFiltered is false.")
            column.idOld == null ->
                log.d("$logCaption: idOld is empty.")
            (listTmp?.size ?: 0) >= Column.LOOP_READ_ENOUGH ->
                log.d("$logCaption: read enough data.")
            SystemClock.elapsedRealtime() - timeStart > Column.LOOP_TIMEOUT ->
                log.d("$logCaption: timeout.")
            else -> parseResult(requester(column.idOld, null))
        }
        return firstResult
    }

    private suspend fun <T : TimelineItem> loadMisskeyMinId(
        logCaption: String,
        requester: suspend (EntityId?, EntityId?) -> TootApiResult?,
        arrayFinder: (JsonObject) -> JsonArray?,
        emptyMessage: String? = null,

        listParser: (parser: TootParser, jsonArray: JsonArray) -> List<T>,
        adder: (List<T>, Boolean) -> Unit,
        initialMinId: EntityId? = null,
    ): TootApiResult? {

        val addToHead = true

        fun parseResult(result: TootApiResult?): Boolean {
            val first = listTmp?.isEmpty() != false

            result ?: return log.d("$logCaption: cancelled")

            result.jsonObject?.let { result.data = arrayFinder(it) }

            val array = result.jsonArray
                ?: return log.w("$logCaption: missing item list")

            val src = listParser(parser, array)
            if (listTmp == null) listTmp = ArrayList(src.size)
            adder(src, addToHead)

            if (first && emptyMessage != null && listTmp?.isEmpty() == true) {
                // フォロー/フォロワー一覧には警告の表示が必要だった
                val who = column.whoAccount?.get()
                if (!accessInfo.isMe(who)) {
                    if (who != null && accessInfo.isRemoteUser(who)) {
                        listTmp?.add(
                            TootMessageHolder(
                                context.getString(R.string.follow_follower_list_may_restrict)
                            )
                        )
                    }
                    listTmp?.add(TootMessageHolder(emptyMessage))
                }
            }

            val more = if (first) {
                column.saveRange(bBottom = true, bTop = true, result = result, list = src)
            } else {
                column.saveRangeTop(result, src)
                true
            }

            return when {
                !more -> log.d("$logCaption: no more items.")
                src.isEmpty() -> log.d("$logCaption: empty item list.")
                else -> true
            }
        }

        val timeStart = SystemClock.elapsedRealtime()

        // 初回の取得
        val firstResult = requester(null, initialMinId)
        var more = parseResult(firstResult)

        // フィルタなどが有効な場合は2回目以降の取得
        while (more) more = when {
            isCancelled ->
                log.d("$logCaption: cancelled.")
            !column.isFilterEnabled ->
                log.d("$logCaption: isFiltered is false.")
            column.idRecent == null ->
                log.d("$logCaption: idRecent is empty.")
            (listTmp?.size ?: 0) >= Column.LOOP_READ_ENOUGH ->
                log.d("$logCaption: read enough data.")
            SystemClock.elapsedRealtime() - timeStart > Column.LOOP_TIMEOUT ->
                log.d("$logCaption: timeout.")
            else -> parseResult(requester(null, column.idRecent))
        }

        listTmp?.sortByDescending { it.getOrderId() }

        return firstResult
    }

    private suspend fun <T : TimelineItem> loadMastodonMaxId(
        logCaption: String,
        requester: suspend (maxId: EntityId?, minId: EntityId?) -> TootApiResult?,
        arrayFinder: (JsonObject) -> JsonArray?,
        emptyMessage: String? = null,

        listParser: (parser: TootParser, jsonArray: JsonArray) -> List<T>,
        adder: (List<T>, Boolean) -> Unit,
        initialMaxId: EntityId? = null,
    ): TootApiResult? {
        val addToHead = false

        fun parseResult(result: TootApiResult?): Boolean {
            val first = listTmp?.isEmpty() != false

            result ?: return log.d("$logCaption: cancelled.")

            result.jsonObject?.let {
                if (column.pagingType == ColumnPagingType.Cursor) {
                    column.idOld = EntityId.mayNull(it.string("next"))
                }
                result.data = arrayFinder(it)
            }

            val array = result.jsonArray
                ?: return log.w("$logCaption: missing item list")

            val src = listParser(parser, array)

            if (listTmp == null) listTmp = ArrayList(src.size)
            adder(src, addToHead)

            if (first) addEmptyMessage(emptyMessage)

            val more = when (column.pagingType) {
                ColumnPagingType.Default ->
                    if (first) {
                        column.saveRange(bBottom = true, bTop = true, result = result, list = src)
                    } else {
                        column.saveRangeBottom(result, src)
                    }
                ColumnPagingType.Offset -> {
                    // idOldがないので2回目以降は発生しない
                    column.offsetNext += src.size
                    true
                }
                else -> true
            }

            return when {
                !more -> log.d("$logCaption: no more items.")
                src.isEmpty() -> log.d("$logCaption: empty list.")
                else -> true
            }
        }

        // 初回の取得
        val timeStart = SystemClock.elapsedRealtime()
        val firstResult = requester(initialMaxId, null)
        var more = parseResult(firstResult)
        // フィルタなどが有効な場合は2回目以降の取得
        while (more) more = when {
            isCancelled ->
                log.d("$logCaption: cancelled.")
            !column.isFilterEnabled ->
                log.d("$logCaption: isFiltered is false.")
            column.idOld == null ->
                log.d("$logCaption: idOld is empty.")
            (listTmp?.size ?: 0) >= Column.LOOP_READ_ENOUGH ->
                log.d("$logCaption: read enough data.")
            SystemClock.elapsedRealtime() - timeStart > Column.LOOP_TIMEOUT ->
                log.d("$logCaption: timeout.")
            else -> parseResult(requester(column.idOld, null))
        }
        return firstResult
    }

    private suspend fun <T : TimelineItem> loadMastodonMinId(
        logCaption: String,
        requester: suspend (maxId: EntityId?, minId: EntityId?) -> TootApiResult?,
        arrayFinder: (JsonObject) -> JsonArray?,
        emptyMessage: String? = null,
        listParser: (parser: TootParser, jsonArray: JsonArray) -> List<T>,
        adder: (List<T>, Boolean) -> Unit,
        initialMinId: EntityId? = null,
    ): TootApiResult? {
        val addToHead = true

        fun parseResult(result: TootApiResult?): Boolean {
            val first = listTmp?.isEmpty() != false

            result ?: return log.d("cancelled.")

            result.jsonObject?.let { result.data = arrayFinder(it) }

            val array = result.jsonArray
                ?: return log.d("$logCaption: missing item list")

            val src = listParser(parser, array)

            if (listTmp == null) listTmp = ArrayList(src.size)
            adder(src, addToHead)

            // フォロー/フォロワー一覧には警告の表示が必要だった
            if (first && emptyMessage != null && listTmp?.isEmpty() == true) {
                val who = column.whoAccount?.get()
                if (!accessInfo.isMe(who)) {
                    if (who != null && accessInfo.isRemoteUser(who)) {
                        listTmp?.add(
                            TootMessageHolder(
                                context.getString(R.string.follow_follower_list_may_restrict)
                            )
                        )
                    }
                    listTmp?.add(TootMessageHolder(emptyMessage))
                }
            }

            val more = if (first) {
                column.saveRange(bBottom = true, bTop = true, result = result, list = src)
            } else {
                column.saveRangeTop(result, src)
                true
            }

            return when {
                !more -> log.d("$logCaption: no more items.")
                src.isEmpty() -> log.d("$logCaption: empty item list.")
                else -> true
            }
        }

        val timeStart = SystemClock.elapsedRealtime()

        // 初回の取得
        val firstResult = requester(null, initialMinId)
        var more = parseResult(firstResult)

        // フィルタなどが有効な場合は2回目以降の取得
        while (more) more = when {
            isCancelled ->
                log.d("$logCaption: cancelled.")
            !column.isFilterEnabled ->
                log.d("$logCaption: isFiltered is false.")
            column.idRecent == null ->
                log.d("$logCaption: idRecent is empty.")
            (listTmp?.size ?: 0) >= Column.LOOP_READ_ENOUGH ->
                log.d("$logCaption: read enough data.")
            SystemClock.elapsedRealtime() - timeStart > Column.LOOP_TIMEOUT ->
                log.d("$logCaption: timeout.")
            else -> parseResult(requester(null, column.idRecent))
        }
        return firstResult
    }

    /////////////////////////////////////////////////////////////////
    // functions that called from ColumnType.loading lambda.

    suspend fun getStatusesPinned(client: TootApiClient, pathBase: String) {
        val result = client.request(pathBase)
        val jsonArray = result?.jsonArray
        if (jsonArray != null) {
            //
            val src = TootParser(
                context,
                accessInfo,
                pinned = true,
                highlightTrie = highlightTrie
            ).statusList(jsonArray)

            this.listPinned = addWithFilterStatus(null, src)

            // pinned tootにはページングの概念はない
        }
        log.d("getStatusesPinned: list size=${listPinned?.size ?: -1}")
    }

    suspend fun getStatusList(
        client: TootApiClient,
        pathBase: String?,
        initialMinId: EntityId? = null,
        initialMaxId: EntityId? = null,
        misskeyParams: JsonObject? = null,
        arrayFinder: (JsonObject) -> JsonArray? =
            nullArrayFinder,
        listParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootStatus> =
            defaultStatusListParser,
    ): TootApiResult? {

        pathBase ?: return null // cancelled.

        val logCaption = "getStatusList"

        val adder: (List<TootStatus>, Boolean) -> Unit = { src, head ->
            this.listTmp = addWithFilterStatus(listTmp, src, head = head)
        }

        return if (isMisskey) {
            val params = misskeyParams ?: column.makeMisskeyTimelineParameter(parser)
            val requester: suspend (EntityId?, EntityId?) -> TootApiResult? = { maxId, minId ->
                client.request(
                    pathBase,
                    params.apply {
                        when {
                            maxId != null -> putMisskeyUntil(maxId)
                            minId != null -> putMisskeySince(minId)
                        }
                    }.toPostRequestBuilder()
                )
            }

            when {
                initialMinId != null -> loadMisskeyMinId(
                    logCaption = logCaption,
                    requester = requester,
                    arrayFinder = arrayFinder,
                    listParser = listParser,
                    adder = adder,
                    initialMinId = initialMinId
                )
                else -> loadMisskeyMaxId(
                    logCaption = logCaption,
                    requester = requester,
                    arrayFinder = arrayFinder,
                    listParser = listParser,
                    adder = adder,
                    initialMaxId = initialMaxId
                )
            }
        } else {
            val delimiter = if (-1 == pathBase.indexOf('?')) '?' else '&'
            val requester: suspend (maxId: EntityId?, minId: EntityId?) -> TootApiResult? =
                { maxId, minId ->
                    client.request(
                        when {
                            maxId != null -> "$pathBase${delimiter}max_id=$maxId"
                            minId != null -> "$pathBase${delimiter}min_id=$minId"
                            else -> pathBase
                        }
                    )
                }
            when {
                initialMinId != null -> loadMastodonMinId(
                    logCaption = logCaption,
                    requester = requester,
                    arrayFinder = arrayFinder,
                    listParser = listParser,
                    adder = adder,
                    initialMinId = initialMinId
                )
                else -> loadMastodonMaxId(
                    logCaption = logCaption,
                    requester = requester,
                    arrayFinder = arrayFinder,
                    listParser = listParser,
                    adder = adder,
                    initialMaxId = initialMaxId
                )
            }
        }
    }

    suspend fun getNotificationList(
        client: TootApiClient,
        fromAcct: String? = null,
        initialMinId: EntityId? = null,
        initialMaxId: EntityId? = null,
    ): TootApiResult? {

        val logCaption = "getNotificationList"

        val pathBase = column.makeNotificationUrl(client, fromAcct)

        val arrayFinder = nullArrayFinder

        val listParser: (TootParser, JsonArray) -> List<TootNotification> =
            defaultNotificationListParser

        val adder: (List<TootNotification>, Boolean) -> Unit =
            { src, head -> addWithFilterNotification(listTmp, src, head = head) }

        return if (isMisskey) {
            val params = column
                .makeMisskeyBaseParameter(parser)
                .addMisskeyNotificationFilter()

            val requester: suspend (EntityId?, EntityId?) -> TootApiResult? = { maxId, minId ->
                client.request(
                    pathBase,
                    params.apply {
                        when {
                            maxId != null -> putMisskeyUntil(maxId)
                            minId != null -> putMisskeySince(minId)
                        }
                    }.toPostRequestBuilder()
                )
            }

            when {
                initialMinId != null -> loadMisskeyMinId(
                    logCaption = logCaption,
                    requester = requester,
                    arrayFinder = arrayFinder,
                    listParser = listParser,
                    adder = adder,
                    initialMinId = initialMinId
                )
                else -> loadMisskeyMaxId(
                    logCaption = logCaption,
                    requester = requester,
                    arrayFinder = arrayFinder,
                    listParser = listParser,
                    adder = adder,
                    initialMaxId = initialMaxId
                )
            }
        } else {
            val delimiter = if (-1 == pathBase.indexOf('?')) '?' else '&'
            val requester: suspend (EntityId?, EntityId?) -> TootApiResult? = { maxId, minId ->
                client.request(
                    when {
                        maxId != null -> "$pathBase${delimiter}max_id=$maxId"
                        minId != null -> "$pathBase${delimiter}min_id=$minId"
                        else -> pathBase
                    }
                )
            }
            when {
                initialMinId != null -> loadMastodonMinId(
                    logCaption = logCaption,
                    requester = requester,
                    arrayFinder = arrayFinder,
                    listParser = listParser,
                    adder = adder,
                    initialMinId = initialMinId
                )
                else -> loadMastodonMaxId(
                    logCaption = logCaption,
                    requester = requester,
                    arrayFinder = arrayFinder,
                    listParser = listParser,
                    adder = adder,
                    initialMaxId = initialMaxId
                )
            }
        }.also {
            listTmp?.mapNotNull { it as? TootNotification }?.let {
                injectData(context, accessInfo, it)
            }
        }
    }

    suspend fun getAccountList(
        client: TootApiClient,
        pathBase: String,
        emptyMessage: String? = null,
        misskeyParams: JsonObject? = null,
        arrayFinder: (JsonObject) -> JsonArray? =
            nullArrayFinder,
        listParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootAccountRef> =
            defaultAccountListParser,
        initialMinId: EntityId? = null,
        initialMaxId: EntityId? = null,
    ): TootApiResult? {

        val logCaption = "getAccountList"

        val adder: (List<TootAccountRef>, Boolean) -> Unit =
            { src, head -> addAll(listTmp, src, head = head) }

        return if (isMisskey) {
            val params = misskeyParams ?: column.makeMisskeyBaseParameter(parser)

            val requester: suspend (EntityId?, EntityId?) -> TootApiResult? = { maxId, minId ->
                client.request(
                    pathBase,
                    params.apply {
                        when {
                            maxId != null -> putMisskeyUntil(maxId)
                            minId != null -> putMisskeySince(minId)
                        }
                    }.toPostRequestBuilder()
                )
            }

            when {
                initialMinId != null -> loadMisskeyMinId(
                    logCaption = logCaption,
                    requester = requester,
                    arrayFinder = arrayFinder,
                    emptyMessage = emptyMessage,
                    listParser = listParser,
                    adder = adder,
                    initialMinId = initialMinId
                )
                else -> loadMisskeyMaxId(
                    logCaption = logCaption,
                    requester = requester,
                    arrayFinder = arrayFinder,
                    emptyMessage = emptyMessage,
                    listParser = listParser,
                    adder = adder,
                    initialMaxId = initialMaxId
                )
            }
        } else {
            val delimiter = if (-1 == pathBase.indexOf('?')) '?' else '&'
            val requester: suspend (EntityId?, EntityId?) -> TootApiResult? = { maxId, minId ->
                client.request(
                    when {
                        maxId != null -> "$pathBase${delimiter}max_id=$maxId"
                        minId != null -> "$pathBase${delimiter}min_id=$minId"
                        else -> pathBase
                    }
                )
            }
            when {
                initialMinId != null -> loadMastodonMinId(
                    logCaption = logCaption,
                    requester = requester,
                    arrayFinder = arrayFinder,
                    emptyMessage = emptyMessage,
                    listParser = listParser,
                    adder = adder,
                    initialMinId = initialMinId
                )
                else -> loadMastodonMaxId(
                    logCaption = logCaption,
                    requester = requester,
                    arrayFinder = arrayFinder,
                    emptyMessage = emptyMessage,
                    listParser = listParser,
                    adder = adder,
                    initialMaxId = initialMaxId
                )
            }
        }
    }

    suspend fun getConversationSummary(
        client: TootApiClient,
        pathBase: String,
        initialMinId: EntityId? = null,
        initialMaxId: EntityId? = null,
        misskeyParams: JsonObject? = null,
        listParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootConversationSummary> =
            defaultConversationSummaryListParser,
    ): TootApiResult? {

        val logCaption = "getConversationSummary"

        val arrayFinder = nullArrayFinder

        val adder: (List<TootConversationSummary>, Boolean) -> Unit =
            { src, head -> addWithFilterConversationSummary(listTmp, src, head = head) }

        return if (isMisskey) {
            val params = misskeyParams ?: column.makeMisskeyTimelineParameter(parser)

            val requester: suspend (EntityId?, EntityId?) -> TootApiResult? = { maxId, minId ->
                client.request(
                    pathBase,
                    params.apply {
                        when {
                            maxId != null -> putMisskeyUntil(maxId)
                            minId != null -> putMisskeySince(minId)
                        }
                    }.toPostRequestBuilder()
                )
            }

            when {
                initialMinId != null -> loadMisskeyMinId(
                    logCaption = logCaption,
                    requester = requester,
                    arrayFinder = arrayFinder,
                    listParser = listParser,
                    adder = adder,
                    initialMinId = initialMinId
                )
                else -> loadMisskeyMaxId(
                    logCaption = logCaption,
                    requester = requester,
                    arrayFinder = arrayFinder,
                    listParser = listParser,
                    adder = adder,
                    initialMaxId = initialMaxId
                )
            }
        } else {
            val delimiter = if (-1 == pathBase.indexOf('?')) '?' else '&'
            val requester: suspend (EntityId?, EntityId?) -> TootApiResult? = { maxId, minId ->
                client.request(
                    when {
                        maxId != null -> "$pathBase${delimiter}max_id=$maxId"
                        minId != null -> "$pathBase${delimiter}min_id=$minId"
                        else -> pathBase
                    }
                )
            }
            when {
                initialMinId != null -> loadMastodonMinId(
                    logCaption = logCaption,
                    requester = requester,
                    arrayFinder = arrayFinder,
                    listParser = listParser,
                    adder = adder,
                    initialMinId = initialMinId
                )
                else -> loadMastodonMaxId(
                    logCaption = logCaption,
                    requester = requester,
                    arrayFinder = arrayFinder,
                    listParser = listParser,
                    adder = adder,
                    initialMaxId = initialMaxId
                )
            }
        }
    }

    suspend fun getDomainBlockList(
        client: TootApiClient,
        pathBase: String,
    ) = client.request(pathBase)?.also { result ->
        val src = TootDomainBlock.parseList(result.jsonArray)
        column.saveRange(bBottom = true, bTop = true, result = result, list = src)
        listTmp = addAll(null, src)
    }

    suspend fun getReportList(
        client: TootApiClient,
        pathBase: String,
    ) = client.request(pathBase)?.also { result ->
        val src = parseList(::TootReport, result.jsonArray)
        column.saveRange(bBottom = true, bTop = true, result = result, list = src)
        listTmp = addAll(null, src)
    }

    suspend fun getScheduledStatuses(client: TootApiClient): TootApiResult? {
        val result = client.request(ApiPath.PATH_SCHEDULED_STATUSES)
        val src = parseList(::TootScheduled, parser, result?.jsonArray)
        listTmp = addAll(listTmp, src)

        column.saveRange(bBottom = true, bTop = true, result = result, list = src)

        return result
    }

    suspend fun getEditHistory(client: TootApiClient): TootApiResult? {
        // ページングなし
        val result = client.request("/api/v1/statuses/${column.statusId}/history")

        // TootStatusとしては不足している情報があるのを補う
        TootStatus.supplyEditHistory(result?.jsonArray, column.originalStatus)

        val src = parser.statusList(result?.jsonArray).reversed()
        listTmp = addAll(listTmp, src)
        column.saveRange(bBottom = true, bTop = true, result = result, list = src)
        return result
    }

    suspend fun getFollowedHashtags(client: TootApiClient): TootApiResult? {
        val result = client.request("/api/v1/followed_tags")
        val src = parser.tagList(result?.jsonArray)
        listTmp = addAll(listTmp, src)
        column.saveRange(bBottom = true, bTop = true, result = result, list = src)
        return result
    }

    suspend fun getListList(
        client: TootApiClient,
        pathBase: String,
        misskeyParams: JsonObject? = null,
    ): TootApiResult? {
        val result = if (misskeyParams != null) {
            client.request(pathBase, misskeyParams.toPostRequestBuilder())
        } else {
            client.request(pathBase)
        }
        if (result != null) {
            val src = parseList(::TootList, parser, result.jsonArray)
            src.sort()
            column.saveRange(bBottom = true, bTop = true, result = result, list = src)
            this.listTmp = addAll(null, src)
        }
        return result
    }

    suspend fun getFilterList(
        client: TootApiClient,
        pathBase: String,
    ): TootApiResult? {
        val result = client.request(pathBase)
        if (result != null) {
            val src = TootFilter.parseList(result.jsonArray)
            this.listTmp = addAll(null, src?: emptyList())
        }
        return result
    }

    suspend fun getAntennaList(
        client: TootApiClient,
        pathBase: String,
        misskeyParams: JsonObject? = null,
    ): TootApiResult? {
        val result = if (misskeyParams != null) {
            client.request(pathBase, misskeyParams.toPostRequestBuilder())
        } else {
            client.request(pathBase)
        }
        if (result != null) {
            val src = parseList(::MisskeyAntenna, result.jsonArray)
            column.saveRange(bBottom = true, bTop = true, result = result, list = src)
            this.listTmp = addAll(null, src)
        }
        return result
    }

    suspend fun getPublicTlAroundTime(
        client: TootApiClient,
        url: String,
    ): TootApiResult? {
        // (Mastodonのみ対応)

        val (instance, instanceResult) = TootInstance.get(client)
        instance ?: return instanceResult

        // ステータスIDに該当するトゥート
        // タンスをまたいだりすると存在しないかもしれないが、エラーは出さない
        var result: TootApiResult? =
            client.request(String.format(Locale.JAPAN, ApiPath.PATH_STATUSES, column.statusId))
        val targetStatus = parser.status(result?.jsonObject)
        if (targetStatus != null) {
            listTmp = addOne(listTmp, targetStatus)
        }

        column.idOld = null
        column.idRecent = null

        var bInstanceTooOld = false
        if (instance.versionGE(TootInstance.VERSION_2_6_0)) {
            // 指定より新しいトゥート
            result = getStatusList(client, url, initialMinId = column.statusId)
            if (result == null || result.error != null) return result
        } else {
            bInstanceTooOld = true
        }

        // 指定位置より古いトゥート
        result = getStatusList(client, url, initialMaxId = column.statusId)
        if (result == null || result.error != null) return result

        listTmp?.sortByDescending { it.getOrderId() }
        if (bInstanceTooOld) {
            listTmp?.add(
                0,
                TootMessageHolder(context.getString(R.string.around_toot_limitation_warning))
            )
        }

        return result
    }

    suspend fun getAccountTlAroundTime(client: TootApiClient): TootApiResult? {
        // (Mastodonのみ対応)

        val (instance, instanceResult) = TootInstance.get(client)
        instance ?: return instanceResult

        // ステータスIDに該当するトゥート
        // タンスをまたいだりすると存在しないかもしれない
        var result: TootApiResult? =
            client.request(String.format(Locale.JAPAN, ApiPath.PATH_STATUSES, column.statusId))
        val targetStatus = parser.status(result?.jsonObject) ?: return result
        listTmp = addOne(listTmp, targetStatus)

        // ↑のトゥートのアカウントのID
        column.profileId = targetStatus.account.id

        val path = column.makeProfileStatusesUrl(column.profileId)
        column.idOld = null
        column.idRecent = null

        var bInstanceTooOld = false
        if (instance.versionGE(TootInstance.VERSION_2_6_0)) {
            // 指定より新しいトゥート
            result = getStatusList(client, path, initialMinId = column.statusId)
            if (result == null || result.error != null) return result
        } else {
            bInstanceTooOld = true
        }

        // 指定位置より古いトゥート
        result = getStatusList(client, path, initialMaxId = column.statusId)
        if (result == null || result.error != null) return result

        listTmp?.sortByDescending { it.getOrderId() }
        if (bInstanceTooOld) {
            listTmp?.add(
                0,
                TootMessageHolder(context.getString(R.string.around_toot_limitation_warning))
            )
        }

        return result
    }

    suspend fun getConversation(
        client: TootApiClient,
        withReference: Boolean = false,
    ): TootApiResult? {
        return if (isMisskey) {
            // 指定された発言そのもの
            val queryParams = column.makeMisskeyBaseParameter(parser).apply {
                put("noteId", column.statusId)
            }

            var result = client.request(
                "/api/notes/show", queryParams.toPostRequestBuilder()
            )
            val jsonObject = result?.jsonObject ?: return result
            val targetStatus = parser.status(jsonObject)
                ?: return TootApiResult("TootStatus parse failed.")
            targetStatus.conversation_main = true

            // 祖先
            val listAsc = ArrayList<TootStatus>()
            while (true) {
                if (client.isApiCancelled()) return null
                queryParams["offset"] = listAsc.size
                result = client.request(
                    "/api/notes/conversation", queryParams.toPostRequestBuilder()
                )
                val jsonArray = result?.jsonArray ?: return result
                val src = parser.statusList(jsonArray)
                if (src.isEmpty()) break
                listAsc.addAll(src)
            }

            // 直接の子リプライ。(子孫をたどることまではしない)
            val listDesc = ArrayList<TootStatus>()
            val idSet = HashSet<EntityId>()
            var untilId: EntityId? = null

            while (true) {
                if (client.isApiCancelled()) return null

                when {
                    untilId == null -> {
                        queryParams.remove("untilId")
                        queryParams.remove("offset")
                    }

                    misskeyVersion >= 11 -> {
                        queryParams["untilId"] = untilId.toString()
                    }

                    else -> queryParams["offset"] = listDesc.size
                }

                result = client.request(
                    "/api/notes/replies", queryParams.toPostRequestBuilder()
                )
                val jsonArray = result?.jsonArray ?: return result
                val src = parser.statusList(jsonArray)
                untilId = null
                for (status in src) {
                    if (idSet.contains(status.id)) continue
                    idSet.add(status.id)
                    listDesc.add(status)
                    untilId = status.id
                }
                if (untilId == null) break
            }

            // 一つのリストにまとめる
            this.listTmp = ArrayList<TimelineItem>(
                listAsc.size + listDesc.size + 2
            ).apply {
                addAll(listAsc.sortedBy { it.time_created_at })
                add(targetStatus)
                addAll(listDesc.sortedBy { it.time_created_at })
                add(TootMessageHolder(context.getString(R.string.misskey_cant_show_all_descendants)))
            }

            //
            result
        } else {
            // 指定された発言そのもの
            var result = client.request(
                String.format(Locale.JAPAN, ApiPath.PATH_STATUSES, column.statusId)
            )
            var jsonObject = result?.jsonObject ?: return result
            val targetStatus = parser.status(jsonObject)
                ?: return TootApiResult("TootStatus parse failed.")

            // 前後の会話
            result = client.request(
                "/api/v1/statuses/${column.statusId}/context${
                    when (withReference) {
                        true -> "?with_reference=true"
                        else -> ""
                    }
                }"
            )
            jsonObject = result?.jsonObject ?: return result
            val conversationContext =
                parseItem(::TootContext, parser, jsonObject)

            // 一つのリストにまとめる
            targetStatus.conversation_main = true
            if (conversationContext != null) {

                this.listTmp = ArrayList(
                    (conversationContext.ancestors?.size ?: 0) +
                            (conversationContext.descendants?.size ?: 0) +
                            1
                )

                if (conversationContext.references != null) {
                    addWithFilterStatus(this.listTmp, conversationContext.references)
                }

                if (conversationContext.ancestors != null) {
                    addWithFilterStatus(this.listTmp, conversationContext.ancestors)
                }

                addOne(listTmp, targetStatus)

                if (conversationContext.descendants != null) {
                    addWithFilterStatus(this.listTmp, conversationContext.descendants)
                }
            } else {
                this.listTmp = addOne(this.listTmp, targetStatus)
                this.listTmp = addOne(
                    this.listTmp,
                    TootMessageHolder(context.getString(R.string.toot_context_parse_failed))
                )
            }

            result
        }
    }

    suspend fun getSearch(client: TootApiClient): TootApiResult? {
        return if (isMisskey) {
            var result: TootApiResult? = TootApiResult()
            val parser = TootParser(context, accessInfo)

            listTmp = ArrayList()

            val queryAccount = column.searchQuery.trim().replace("^@".toRegex(), "")
            if (queryAccount.isNotEmpty()) {
                result = client.request(
                    "/api/users/search",
                    accessInfo.putMisskeyApiToken().apply {
                        put("query", queryAccount)
                        put("localOnly", !column.searchResolve)
                    }.toPostRequestBuilder()
                )
                val jsonArray = result?.jsonArray
                if (jsonArray != null) {
                    val src =
                        TootParser(context, accessInfo).accountList(jsonArray)
                    listTmp = addAll(listTmp, src)
                }
            }

            val queryTag = column.searchQuery.trim().replace("^#".toRegex(), "")
            if (queryTag.isNotEmpty()) {
                result = client.request(
                    "/api/hashtags/search",
                    accessInfo.putMisskeyApiToken().apply {
                        put("query", queryTag)
                    }.toPostRequestBuilder()
                )
                val jsonArray = result?.jsonArray
                if (jsonArray != null) {
                    val src = TootTag.parseList(parser, jsonArray)
                    listTmp = addAll(listTmp, src)
                }
            }
            if (column.searchQuery.isNotEmpty()) {
                result = client.request(
                    "/api/notes/search",
                    accessInfo.putMisskeyApiToken().apply {
                        put("query", column.searchQuery)
                    }
                        .toPostRequestBuilder()
                )
                val jsonArray = result?.jsonArray
                if (jsonArray != null) {
                    val src = parser.statusList(jsonArray)
                    listTmp = addWithFilterStatus(listTmp, src)
                    if (src.isNotEmpty()) {
                        val (ti, _) = TootInstance.get(client)
                        if (ti?.versionGE(TootInstance.MISSKEY_VERSION_12) == true) {
                            addOne(listTmp, TootSearchGap(TootSearchGap.SearchType.Status))
                        }
                    }
                }
            }

            // 検索機能が無効だとsearch_query が 400を返すが、他のAPIがデータを返したら成功したことにする
            if (listTmp?.isNotEmpty() == true) {
                TootApiResult()
            } else {
                result
            }
        } else {
            if (accessInfo.isPseudo) {
                // 1.5.0rc からマストドンの検索APIは認証を要求するようになった
                return TootApiResult(context.getString(R.string.search_is_not_available_on_pseudo_account))
            }

            val (instance, instanceResult) = TootInstance.get(client)
            instance ?: return instanceResult

            val (apiResult, searchResult) = client.requestMastodonSearch(
                parser,
                q = column.searchQuery,
                resolve = column.searchResolve,
            )
            if (searchResult != null) {
                listTmp = ArrayList()
                addAll(listTmp, searchResult.hashtags)
                if (searchResult.searchApiVersion >= 2 && searchResult.hashtags.isNotEmpty()) {
                    addOne(listTmp, TootSearchGap(TootSearchGap.SearchType.Hashtag))
                }
                addAll(listTmp, searchResult.accounts)
                if (searchResult.searchApiVersion >= 2 && searchResult.accounts.isNotEmpty()) {
                    addOne(listTmp, TootSearchGap(TootSearchGap.SearchType.Account))
                }
                addAll(listTmp, searchResult.statuses)
                if (searchResult.searchApiVersion >= 2 && searchResult.statuses.isNotEmpty()) {
                    addOne(listTmp, TootSearchGap(TootSearchGap.SearchType.Status))
                }
            }
            return apiResult
        }
    }
}
