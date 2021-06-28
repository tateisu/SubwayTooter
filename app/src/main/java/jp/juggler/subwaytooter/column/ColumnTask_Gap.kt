package jp.juggler.subwaytooter.column

import android.os.SystemClock
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.api.finder.*
import jp.juggler.subwaytooter.columnviewholder.getListItemOffset
import jp.juggler.subwaytooter.columnviewholder.setListItemTop
import jp.juggler.subwaytooter.notification.PollingWorker
import jp.juggler.util.*
import java.lang.StringBuilder

@Suppress("ClassNaming")
class ColumnTask_Gap(
    columnArg: Column,
    private val gap: TimelineItem,
    private val isHead: Boolean,
) : ColumnTask(columnArg, ColumnTaskType.GAP) {

    companion object {

        private val log = LogCategory("CT_Gap")

        private val reIToken = """"i":"[^"]+"""".toRegex()

        private fun String.removeIToken() =
            reIToken.replace(this, """"i":"**"""")
    }

    private var maxId: EntityId? = (gap as? TootGap)?.maxId
    private var sinceId: EntityId? = (gap as? TootGap)?.sinceId

    override suspend fun background(): TootApiResult? {
        ctStarted.set(true)

        val client = TootApiClient(context, callback = object : TootApiCallback {
            override val isApiCancelled: Boolean
                get() = isCancelled || column.isDispose.get()

            override suspend fun publishApiProgress(s: String) {
                runOnMainLooper {
                    if (isCancelled) return@runOnMainLooper
                    column.taskProgress = s
                    column.fireShowContent(reason = "gap progress", changeList = ArrayList())
                }
            }
        })

        client.account = accessInfo

        try {
            return column.type.gap(this, client)
        } catch (ex: Throwable) {
            return TootApiResult(ex.withCaption("gap loading failed."))
        } finally {
            try {
                column.updateRelation(client, listTmp, column.whoAccount, parser)
            } catch (ex: Throwable) {
                log.trace(ex)
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

        try {

            column.lastTask = null
            column.bRefreshLoading = false

            val error = result.error
            if (error != null) {
                column.mRefreshLoadingError = error
                column.fireShowContent(reason = "gap error", changeList = ArrayList())
                return
            }

            val listTmp = this.listTmp
            if (listTmp == null) {
                column.fireShowContent(reason = "gap list_tmp is null", changeList = ArrayList())
                return
            }

            val listNew = when (column.type) {

                // 検索カラムはIDによる重複排除が不可能
                ColumnType.SEARCH -> listTmp

                // 他のカラムは重複排除してから追加
                else -> column.duplicateMap.filterDuplicate(listTmp)
            }

            // 0個でもギャップを消すために以下の処理を続ける

            val changeList = ArrayList<AdapterChange>()

            replaceConversationSummary(changeList, listNew, column.listData)

            val added = listNew.size // may 0

            val position = column.listData.indexOf(gap)
            if (position == -1) {
                log.d("gap not found..")
                column.fireShowContent(reason = "gap not found", changeList = ArrayList())
                return
            }

            val iv = when {
                isHead -> PrefI.ipGapHeadScrollPosition
                else -> PrefI.ipGapTailScrollPosition
            }.invoke(pref)
            val scrollHead = iv == PrefI.GSP_HEAD

            if (scrollHead) {
                // ギャップを頭から読んだ場合、スクロール位置の調整は不要

                column.listData.removeAt(position)
                column.listData.addAll(position, listNew)

                changeList.add(AdapterChange(AdapterChangeType.RangeRemove, position))
                if (added > 0) {
                    changeList.add(
                        AdapterChange(
                            AdapterChangeType.RangeInsert,
                            position,
                            added
                        )
                    )
                }
                column.fireShowContent(reason = "gap updated", changeList = changeList)
            } else {
                // ギャップを下から読んだ場合、ギャップの次の要素が画面内で同じ位置になるようスクロール位置を調整する必要がある

                // idx番目の要素がListViewのtopから何ピクセル下にあるか
                var restoreIdx = position + 1
                var restoreY = 0
                val holder = column.viewHolder
                if (holder != null) {
                    try {
                        restoreY = holder.getListItemOffset(restoreIdx)
                    } catch (ex: IndexOutOfBoundsException) {
                        log.w(ex, "getListItemOffset failed.")
                        restoreIdx = position
                        try {
                            restoreY = holder.getListItemOffset(restoreIdx)
                        } catch (ex2: IndexOutOfBoundsException) {
                            log.w(ex2, "getListItemOffset failed.")
                            restoreIdx = -1
                        }
                    }
                }

                column.listData.removeAt(position)
                column.listData.addAll(position, listNew)

                changeList.add(AdapterChange(AdapterChangeType.RangeRemove, position))
                if (added > 0) {
                    changeList.add(
                        AdapterChange(
                            AdapterChangeType.RangeInsert,
                            position,
                            added
                        )
                    )
                }
                column.fireShowContent(reason = "gap updated", changeList = changeList)

                when {

                    // ViewHolderがない
                    holder == null -> {
                        val scrollSave = column.scrollSave
                        if (scrollSave != null) {
                            scrollSave.adapterIndex += added - 1
                        }
                    }

                    // ギャップが画面内にあるなら
                    restoreIdx >= 0 ->
                        holder.setListItemTop(restoreIdx + added - 1, restoreY)

                    // ギャップが画面内にない場合、何もしない
                    else -> {
                    }
                }
            }

            column.updateMisskeyCapture()
        } finally {
            column.fireShowColumnStatus()
        }
    }

    private fun allRangeChecked(logCaption: String): Boolean {
        val tmpMaxId = maxId
        val tmpMinId = sinceId
        if (tmpMaxId != null && tmpMinId != null && tmpMinId >= tmpMaxId) {
            log.d("$logCaption: allRangeChecked.  $tmpMinId >= $tmpMaxId")
            return true
        }
        return false
    }

    // max_id を指定してギャップの上から読む
    private suspend fun <T : TimelineItem> readGapHeadMisskey(
        logCaption: String,
        client: TootApiClient,
        pathBase: String,
        paramsCreator: (EntityId?) -> JsonObject,
        arrayFinder: (JsonObject) -> JsonArray? = { null },
        listParser: (TootParser, JsonArray) -> List<T>,
        adder: (List<T>) -> Unit,
    ): TootApiResult? {
        listTmp = ArrayList()
        val timeStart = SystemClock.elapsedRealtime()
        var result: TootApiResult? = null
        var bAddGap = false

        val olderLimit = sinceId
        while (true) {
            if (isCancelled) {
                log.d("$logCaption: cancelled.")
                break
            }

            if (result != null && SystemClock.elapsedRealtime() - timeStart > Column.LOOP_TIMEOUT) {
                log.d("$logCaption: timeout.")
                bAddGap = true
                break
            }

            if (allRangeChecked(logCaption)) break

            val params = paramsCreator(maxId)

            log.d("$logCaption: $pathBase ${params.toString().removeIToken()}")

            val r2 = client.request(
                pathBase,
                params.toPostRequestBuilder()
            )

            r2?.jsonObject?.let { r2.data = arrayFinder(it) }

            val jsonArray = r2?.jsonArray
            if (jsonArray == null) {
                log.d("$logCaption: error or cancelled. make gap.")

                // 成功データがない場合だけ、今回のエラーを返すようにする
                if (result == null) result = r2

                bAddGap = true

                break
            }

            // 成功した場合はそれを返したい
            result = r2

            var src = listParser(parser, jsonArray)

            if (olderLimit != null) {
                src = src.filter { it.isInjected() || it.getOrderId() > olderLimit }
            }

            if (src.none { !it.isInjected() }) {
                // 直前の取得でカラのデータが帰ってきたら終了
                log.d("$logCaption: empty.")
                break
            }

            // 隙間の最新のステータスIDは取得データ末尾のステータスIDである
            maxId = column.parseRange(result, src).first

            adder(src)
        }

        val sortAllowed = true
        if (sortAllowed) listTmp?.sortByDescending { it.getOrderId() }

        if (bAddGap) addOne(listTmp, TootGap.mayNull(maxId, sinceId))

        return result
    }

    // since_idを指定してギャップの下から読む
    private suspend fun <T : TimelineItem> readGapTailMisskey(
        logCaption: String,
        client: TootApiClient,
        pathBase: String,
        paramsCreator: (EntityId?) -> JsonObject,
        arrayFinder: (JsonObject) -> JsonArray? = { null },
        listParser: (TootParser, JsonArray) -> List<T>,
        adder: (List<T>) -> Unit,
    ): TootApiResult? {
        listTmp = ArrayList()
        val timeStart = SystemClock.elapsedRealtime()
        var result: TootApiResult? = null
        var bAddGap = false
        val newerLimit = maxId
        while (true) {
            if (isCancelled) {
                log.d("$logCaption: cancelled.")
                break
            }

            if (result != null && SystemClock.elapsedRealtime() - timeStart > Column.LOOP_TIMEOUT) {
                log.d("$logCaption: timeout.")
                bAddGap = true
                break
            }

            if (allRangeChecked(logCaption)) break

            val params = paramsCreator(sinceId)

            log.d("$logCaption: $pathBase ${params.toString().removeIToken()}")

            val r2 = client.request(
                pathBase,
                params.toPostRequestBuilder()
            )

            r2?.jsonObject?.let { r2.data = arrayFinder(it) }

            val jsonArray = r2?.jsonArray
            if (jsonArray == null) {
                log.d("$logCaption: error or cancelled. make gap.")

                // 成功データがない場合だけ、今回のエラーを返すようにする
                if (result == null) result = r2

                bAddGap = true

                break
            }

            // 成功した場合はそれを返したい
            result = r2

            var src = listParser(parser, jsonArray)

            if (newerLimit != null) {
                src = src.filter { it.isInjected() || it.getOrderId() < newerLimit }
            }

            if (src.none { !it.isInjected() }) {
                // 直前の取得でカラのデータが帰ってきたら終了
                log.d("$logCaption: empty.")
                break
            }

            // 隙間の最新のステータスIDは取得データ末尾のステータスIDである
            sinceId = column.parseRange(result, src).second

            adder(src)
        }

        val sortAllowed = true
        if (sortAllowed) listTmp?.sortByDescending { it.getOrderId() }

        if (bAddGap) addOne(listTmp, TootGap.mayNull(maxId, sinceId), head = true)

        return result
    }

    // max_id を指定してギャップの上から読む
    private suspend fun <T : TimelineItem> readGapHeadMastodon(
        logCaption: String,
        client: TootApiClient,
        pathBase: String,
        filterByIdRange: Boolean,
        listParser: (TootParser, JsonArray) -> List<T>,
        adder: (List<T>) -> Unit,
    ): TootApiResult? {
        listTmp = ArrayList()
        val delimiter = if (-1 != pathBase.indexOf('?')) '&' else '?'
        val requester: suspend (EntityId?) -> TootApiResult? = {
            val path = StringBuilder().apply {
                append(pathBase)
                val list = ArrayList<String>()
                if (it != null) list.add("max_id=$it")
                if (sinceId != null) list.add("since_id=$sinceId")
                list.forEachIndexed { index, s ->
                    append(if (index == 0) delimiter else '&')
                    append(s)
                }
            }.toString()
            log.d("readGapHeadMastodon $path")
            client.request(path)
        }

        val timeStart = SystemClock.elapsedRealtime()
        var result: TootApiResult? = null
        var bAddGap = false
        val olderLimit = if (filterByIdRange) sinceId else null
        while (true) {
            if (isCancelled) {
                log.d("$logCaption: cancelled.")
                break
            }

            if (result != null && SystemClock.elapsedRealtime() - timeStart > Column.LOOP_TIMEOUT) {
                log.d("$logCaption: timeout.")
                // タイムアウト
                bAddGap = true
                break
            }

            if (maxId == null) {
                context.showToast(false, "$logCaption: missing max_id")
                log.d("$logCaption: missing max_id")
                break
            }

            if (allRangeChecked(logCaption)) break

            val r2 = requester(maxId)

            val jsonArray = r2?.jsonArray
            if (jsonArray == null) {
                log.d("$logCaption: error or cancelled. make gap.")

                // 成功データがない場合だけ、今回のエラーを返すようにする
                if (result == null) result = r2

                bAddGap = true

                break
            }

            // 成功した場合はそれを返したい
            result = r2

            var src = listParser(parser, jsonArray)

            if (olderLimit != null) {
                src = src.filter { it.getOrderId() > olderLimit }
            }

            if (src.isEmpty()) {
                // 直前の取得でカラのデータが帰ってきたら終了
                log.d("$logCaption: empty.")
                break
            }

            // 隙間の最新のステータスIDは取得データ末尾のステータスIDである
            maxId = column.parseRange(result, src).first

            adder(src)
        }

        val sortAllowed = false
        if (sortAllowed) listTmp?.sortByDescending { it.getOrderId() }

        if (bAddGap) addOne(listTmp, TootGap.mayNull(maxId, sinceId))

        return result
    }

    // since_idを指定してギャップの下から読む
    private suspend fun <T : TimelineItem> readGapTailMastodon(
        logCaption: String,
        client: TootApiClient,
        pathBase: String,
        filterByIdRange: Boolean,
        listParser: (TootParser, JsonArray) -> List<T>,
        adder: (List<T>) -> Unit,
    ): TootApiResult? {
        listTmp = ArrayList()
        val delimiter = if (-1 != pathBase.indexOf('?')) '&' else '?'
        val requester: suspend (EntityId?) -> TootApiResult? = {
            val path = StringBuilder().apply {
                append(pathBase)
                val list = ArrayList<String>()
                if (it != null) list.add("min_id=$it")
                if (maxId != null) list.add("max_id=$maxId")
                list.forEachIndexed { index, s ->
                    append(if (index == 0) delimiter else '&')
                    append(s)
                }
            }.toString()
            log.d("$logCaption: $path")
            client.request(path)
        }

        val timeStart = SystemClock.elapsedRealtime()
        var result: TootApiResult? = null
        var bAddGap = false
        val newerLimit = if (filterByIdRange) maxId else null
        while (true) {
            if (isCancelled) {
                log.d("$logCaption: cancelled.")
                break
            }

            if (result != null && SystemClock.elapsedRealtime() - timeStart > Column.LOOP_TIMEOUT) {
                log.d("$logCaption: timeout.")
                bAddGap = true
                break
            }

            if (allRangeChecked(logCaption)) break

            val r2 = requester(sinceId)

            val jsonArray = r2?.jsonArray
            if (jsonArray == null) {
                log.d("$logCaption: error or cancelled. make gap.")

                // 成功データがない場合だけ、今回のエラーを返すようにする
                if (result == null) result = r2

                bAddGap = true

                break
            }

            // 成功した場合はそれを返したい
            result = r2

            var src = listParser(parser, jsonArray)

            if (newerLimit != null) {
                src = src.filter { it.getOrderId() < newerLimit }
            }

            if (src.isEmpty()) {
                // 直前の取得でカラのデータが帰ってきたら終了
                log.d("$logCaption: empty.")
                break
            }

            // 隙間の最新のステータスIDは取得データ末尾のステータスIDである
            sinceId = column.parseRange(result, src).second

            adder(src)
        }

        val sortAllowed = false
        if (sortAllowed) listTmp?.sortByDescending { it.getOrderId() }

        if (bAddGap) addOne(listTmp, TootGap.mayNull(maxId, sinceId), head = true)

        return result
    }

    //////////////////////////////////////////////////////////////////////

    suspend fun getAccountList(
        client: TootApiClient,
        pathBase: String,
        mastodonFilterByIdRange: Boolean,
        misskeyParams: JsonObject? = null,
        arrayFinder: (jsonObject: JsonObject) -> JsonArray? =
            nullArrayFinder,
        listParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootAccountRef> =
            defaultAccountListParser,
    ): TootApiResult? {

        if (column.pagingType != ColumnPagingType.Default) {
            return TootApiResult("can't support gap")
        }

        val adder: (List<TootAccountRef>) -> Unit =
            { addAll(listTmp, it, head = !isHead) }

        return if (accessInfo.isMisskey) {
            val logCaption = "getAccountList.Misskey"
            val params = misskeyParams ?: column.makeMisskeyBaseParameter(parser)
            if (isHead) {
                readGapHeadMisskey(
                    logCaption,
                    client,
                    pathBase,
                    paramsCreator = { params.putMisskeyUntil(it) },
                    arrayFinder = arrayFinder,
                    listParser = listParser,
                    adder = adder,
                )
            } else {
                readGapTailMisskey(
                    logCaption,
                    client,
                    pathBase,
                    paramsCreator = { params.putMisskeySince(it) },
                    arrayFinder = arrayFinder,
                    listParser = listParser,
                    adder = adder
                )
            }
        } else {
            val logCaption = "getAccountList.Mastodon"
            if (isHead) {
                readGapHeadMastodon(
                    logCaption,
                    client,
                    pathBase,
                    filterByIdRange = mastodonFilterByIdRange,
                    listParser = listParser,
                    adder = adder
                )
            } else {
                readGapTailMastodon(
                    logCaption,
                    client,
                    pathBase,
                    filterByIdRange = mastodonFilterByIdRange,
                    listParser = listParser,
                    adder = adder
                )
            }
        }
    }

    suspend fun getReportList(
        client: TootApiClient,
        pathBase: String,
        mastodonFilterByIdRange: Boolean,
        listParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootReport> =
            defaultReportListParser,
    ): TootApiResult? {

        val adder: (List<TootReport>) -> Unit =
            { addAll(listTmp, it, head = !isHead) }

        return if (accessInfo.isMisskey) {
            val logCaption = "getReportList.Misskey"
            val params = column.makeMisskeyBaseParameter(parser)
            if (isHead) {
                readGapHeadMisskey(
                    logCaption,
                    client,
                    pathBase,
                    paramsCreator = { params.putMisskeyUntil(it) },
                    listParser = listParser,
                    adder = adder
                )
            } else {
                readGapTailMisskey(
                    logCaption,
                    client,
                    pathBase,
                    paramsCreator = { params.putMisskeySince(it) },
                    listParser = listParser,
                    adder = adder
                )
            }
        } else {
            val logCaption = "getReportList.Mastodon"
            if (isHead) {
                readGapHeadMastodon(
                    logCaption,
                    client,
                    pathBase,
                    filterByIdRange = mastodonFilterByIdRange,
                    listParser = listParser,
                    adder = adder
                )
            } else {
                readGapTailMastodon(
                    logCaption,
                    client,
                    pathBase,
                    filterByIdRange = mastodonFilterByIdRange,
                    listParser = listParser,
                    adder = adder
                )
            }
        }
    }

    suspend fun getNotificationList(
        client: TootApiClient,
        fromAcct: String? = null,
        mastodonFilterByIdRange: Boolean,
    ): TootApiResult? {

        val pathBase: String = column.makeNotificationUrl(client, fromAcct)

        val listParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootNotification> =
            defaultNotificationListParser

        val adder: (List<TootNotification>) -> Unit =
            { addWithFilterNotification(listTmp, it, head = !isHead) }

        return if (isMisskey) {
            val logCaption = "getNotificationList.Misskey"
            val params = column.makeMisskeyBaseParameter(parser)
                .addMisskeyNotificationFilter(column)
            if (isHead) {
                readGapHeadMisskey(
                    logCaption,
                    client,
                    pathBase,
                    paramsCreator = { params.putMisskeyUntil(it) },
                    listParser = listParser,
                    adder = adder
                )
            } else {
                readGapTailMisskey(
                    logCaption,
                    client,
                    pathBase,
                    paramsCreator = { params.putMisskeySince(it) },
                    listParser = listParser,
                    adder = adder
                )
            }
        } else {
            val logCaption = "getNotificationList.Mastodon"
            if (isHead) {
                readGapHeadMastodon(
                    logCaption,
                    client,
                    pathBase,
                    filterByIdRange = mastodonFilterByIdRange,
                    listParser = listParser,
                    adder = adder
                )
            } else {
                readGapTailMastodon(
                    logCaption,
                    client,
                    pathBase,
                    filterByIdRange = mastodonFilterByIdRange,
                    listParser = listParser,
                    adder = adder
                )
            }
        }.also {
            listTmp?.mapNotNull { it as? TootNotification }.notEmpty()?.let {
                PollingWorker.injectData(context, accessInfo, it)
            }
        }
    }

    suspend fun getStatusList(
        client: TootApiClient,
        pathBase: String?,
        mastodonFilterByIdRange: Boolean,
        misskeyParams: JsonObject? = null,
        listParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootStatus> =
            defaultStatusListParser,
    ): TootApiResult? {

        pathBase ?: return null // cancelled.

        val adder: (List<TootStatus>) -> Unit =
            { addWithFilterStatus(listTmp, it, head = !isHead) }

        return if (accessInfo.isMisskey) {
            val logCaption = "getStatusList.Misskey"
            val params = misskeyParams ?: column.makeMisskeyTimelineParameter(parser)

            if (isHead) {
                readGapHeadMisskey(
                    logCaption,
                    client,
                    pathBase,
                    paramsCreator = { params.putMisskeyUntil(it) },
                    listParser = listParser,
                    adder = adder
                )
            } else {
                readGapTailMisskey(
                    logCaption,
                    client,
                    pathBase,
                    paramsCreator = { params.putMisskeySince(it) },
                    listParser = listParser,
                    adder = adder
                )
            }
        } else {
            val logCaption = "getStatusList.Mastodon"
            if (isHead) {
                readGapHeadMastodon(
                    logCaption,
                    client,
                    pathBase,
                    filterByIdRange = mastodonFilterByIdRange,
                    listParser = listParser,
                    adder = adder
                )
            } else {
                readGapTailMastodon(
                    logCaption,
                    client,
                    pathBase,
                    filterByIdRange = mastodonFilterByIdRange,
                    listParser = listParser,
                    adder = adder
                )
            }
        }
    }

    suspend fun getConversationSummaryList(
        client: TootApiClient,
        pathBase: String,
        mastodonFilterByIdRange: Boolean,
        misskeyParams: JsonObject? = null,
        listParser: (TootParser, JsonArray) -> List<TootConversationSummary> =
            defaultConversationSummaryListParser,
    ): TootApiResult? {

        val adder: (List<TootConversationSummary>) -> Unit =
            { addWithFilterConversationSummary(listTmp, it, head = !isHead) }

        return if (accessInfo.isMisskey) {
            val logCaption = "getConversationSummaryList.Misskey"
            val params = misskeyParams ?: column.makeMisskeyTimelineParameter(parser)
            if (isHead) {
                readGapHeadMisskey(
                    logCaption,
                    client,
                    pathBase,
                    paramsCreator = { params.putMisskeyUntil(it) },
                    listParser = listParser,
                    adder = adder
                )
            } else {
                readGapTailMisskey(
                    logCaption,
                    client,
                    pathBase,
                    paramsCreator = { params.putMisskeySince(it) },
                    listParser = listParser,
                    adder = adder
                )
            }
        } else {
            val logCaption = "getConversationSummaryList.Mastodon"
            if (isHead) {
                readGapHeadMastodon(
                    logCaption,
                    client,
                    pathBase,
                    filterByIdRange = mastodonFilterByIdRange,
                    listParser = listParser,
                    adder = adder
                )
            } else {
                readGapTailMastodon(
                    logCaption,
                    client,
                    pathBase,
                    filterByIdRange = mastodonFilterByIdRange,
                    listParser = listParser,
                    adder = adder
                )
            }
        }
    }

    suspend fun getSearchGap(client: TootApiClient): TootApiResult? {
        if (gap !is TootSearchGap) return null

        if (isMisskey) {

            val countStatuses: (TimelineItem, EntityId?) -> EntityId? = { it, minId ->
                if (it is TootStatus && (minId == null || it.id < minId)) it.id else minId
            }

            val (_, counter) = when (gap.type) {
                TootSearchGap.SearchType.Status -> Pair("statuses", countStatuses)

                //TootSearchGap.SearchType.Hashtag -> Pair("hashtags", countTag)
                //TootSearchGap.SearchType.Account -> Pair("accounts", countAccount)
                else -> return TootApiResult("paging for ${gap.type} is not yet supported")
            }
            var minId: EntityId? = null
            for (it in column.listData) minId = counter(it, minId)

            minId ?: return TootApiResult("can't detect paging parameter.")

            val result = client.request(
                "/api/notes/search",
                accessInfo.putMisskeyApiToken().apply {
                    put("query", column.searchQuery)
                    put("untilId", minId.toString())
                }
                    .toPostRequestBuilder()
            )

            val jsonArray = result?.jsonArray
            if (jsonArray != null) {
                val src = parser.statusList(jsonArray)
                listTmp = addWithFilterStatus(listTmp, src)
                if (src.isNotEmpty()) {
                    addOne(listTmp, TootSearchGap(TootSearchGap.SearchType.Status))
                }
            }
            return result
        } else {
            var offset = 0

            val countAccounts: (TimelineItem) -> Unit =
                { if (it is TootAccountRef) ++offset }
            val countTags: (TimelineItem) -> Unit =
                { if (it is TootTag) ++offset }
            val countStatuses: (TimelineItem) -> Unit =
                { if (it is TootStatus) ++offset }

            val (type, counter) = when (gap.type) {
                TootSearchGap.SearchType.Account -> Pair("accounts", countAccounts)
                TootSearchGap.SearchType.Hashtag -> Pair("hashtags", countTags)
                TootSearchGap.SearchType.Status -> Pair("statuses", countStatuses)
            }
            column.listData.forEach { counter(it) }

            // https://mastodon2.juggler.jp/api/v2/search?q=gargron&type=accounts&offset=5
            var query = "q=${column.searchQuery.encodePercent()}&type=$type&offset=$offset"
            if (column.searchResolve) query += "&resolve=1"

            val (apiResult, searchResult) = client.requestMastodonSearch(parser, query)
            if (searchResult != null) {
                listTmp = ArrayList()
                addAll(listTmp, searchResult.hashtags)
                addAll(listTmp, searchResult.accounts)
                addAll(listTmp, searchResult.statuses)
                if (listTmp?.isNotEmpty() == true) {
                    addOne(listTmp, TootSearchGap(gap.type))
                }
            }
            return apiResult
        }
    }
}
