package jp.juggler.subwaytooter

import android.os.SystemClock
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.notification.PollingWorker
import jp.juggler.util.*
import java.lang.StringBuilder

class ColumnTask_Gap(
    columnArg: Column,
    private val gap: TimelineItem,
    private val isHead: Boolean
) : ColumnTask(columnArg, ColumnTaskType.GAP) {

    companion object {

        private val log = LogCategory("CT_Gap")

        private val reIToken = """"i":"[^"]+"""".toRegex()

        private fun String.removeIToken() =
            reIToken.replace(this, """"i":"**"""")
    }

    private var max_id: EntityId? = (gap as? TootGap)?.max_id
    private var since_id: EntityId? = (gap as? TootGap)?.since_id

    override suspend fun background(): TootApiResult? {
        ctStarted.set(true)

        val client = TootApiClient(context, callback = object : TootApiCallback {
            override val isApiCancelled: Boolean
                get() = isCancelled || column.is_dispose.get()

            override suspend fun publishApiProgress(s: String) {
                runOnMainLooper {
                    if (isCancelled) return@runOnMainLooper
                    column.task_progress = s
                    column.fireShowContent(reason = "gap progress", changeList = ArrayList())
                }
            }
        })

        client.account = access_info

        try {
            return column.type.gap(this, client)
        } catch (ex: Throwable) {
            return TootApiResult(ex.withCaption("gap loading failed."))
        } finally {
            try {
                column.updateRelation(client, list_tmp, column.who_account, parser)
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
        if (column.is_dispose.get()) return

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

            val list_tmp = this.list_tmp
            if (list_tmp == null) {
                column.fireShowContent(reason = "gap list_tmp is null", changeList = ArrayList())
                return
            }

            val list_new = when (column.type) {

                // 検索カラムはIDによる重複排除が不可能
                ColumnType.SEARCH -> list_tmp

                // 他のカラムは重複排除してから追加
                else -> column.duplicate_map.filterDuplicate(list_tmp)
            }

            // 0個でもギャップを消すために以下の処理を続ける

            val changeList = ArrayList<AdapterChange>()

            replaceConversationSummary(changeList, list_new, column.list_data)

            val added = list_new.size // may 0

            val position = column.list_data.indexOf(gap)
            if (position == -1) {
                log.d("gap not found..")
                column.fireShowContent(reason = "gap not found", changeList = ArrayList())
                return
            }

            val iv = if (isHead) {
                Pref.ipGapHeadScrollPosition
            } else {
                Pref.ipGapTailScrollPosition
            }.invoke(pref)
            val scrollHead = iv == Pref.GSP_HEAD

            if (scrollHead) {
                // ギャップを頭から読んだ場合、スクロール位置の調整は不要

                column.list_data.removeAt(position)
                column.list_data.addAll(position, list_new)

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
                var restore_idx = position + 1
                var restore_y = 0
                val holder = column.viewHolder
                if (holder != null) {
                    try {
                        restore_y = holder.getListItemOffset(restore_idx)
                    } catch (ex: IndexOutOfBoundsException) {
                        restore_idx = position
                        try {
                            restore_y = holder.getListItemOffset(restore_idx)
                        } catch (ex2: IndexOutOfBoundsException) {
                            restore_idx = -1
                        }
                    }
                }

                column.list_data.removeAt(position)
                column.list_data.addAll(position, list_new)

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
                        val scroll_save = column.scroll_save
                        if (scroll_save != null) {
                            scroll_save.adapterIndex += added - 1
                        }
                    }

                    // ギャップが画面内にあるなら
                    restore_idx >= 0 ->
                        holder.setListItemTop(restore_idx + added - 1, restore_y)

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
        val tmpMaxId = max_id
        val tmpMinId = since_id
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
        path_base: String,
        paramsCreator: (EntityId?) -> JsonObject,
        arrayFinder: (JsonObject) -> JsonArray? = { null },
        listParser: (TootParser, JsonArray) -> List<T>,
        adder: (List<T>) -> Unit
    ): TootApiResult? {
        list_tmp = ArrayList()
        val time_start = SystemClock.elapsedRealtime()
        var result: TootApiResult? = null
        var bAddGap = false

        val olderLimit = since_id
        while (true) {
            if (isCancelled) {
                log.d("$logCaption: cancelled.")
                break
            }

            if (result != null && SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
                log.d("$logCaption: timeout.")
                bAddGap = true
                break
            }

            if (allRangeChecked(logCaption)) break

            val params = paramsCreator(max_id)


            log.d("$logCaption: $path_base ${params.toString().removeIToken()}")

            val r2 = client.request(
                path_base,
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

            if (olderLimit != null)
                src = src.filter { it.isInjected() || it.getOrderId() > olderLimit }

            if (src.none { !it.isInjected() }) {
                // 直前の取得でカラのデータが帰ってきたら終了
                log.d("$logCaption: empty.")
                break
            }

            // 隙間の最新のステータスIDは取得データ末尾のステータスIDである
            max_id = column.parseRange(result, src).first

            adder(src)
        }

        val sortAllowed = true
        if (sortAllowed) list_tmp?.sortByDescending { it.getOrderId() }

        if (bAddGap) addOne(list_tmp, TootGap.mayNull(max_id, since_id))

        return result
    }

    // since_idを指定してギャップの下から読む
    private suspend fun <T : TimelineItem> readGapTailMisskey(
        logCaption: String,
        client: TootApiClient,
        path_base: String,
        paramsCreator: (EntityId?) -> JsonObject,
        arrayFinder: (JsonObject) -> JsonArray? = { null },
        listParser: (TootParser, JsonArray) -> List<T>,
        adder: (List<T>) -> Unit
    ): TootApiResult? {
        list_tmp = ArrayList()
        val time_start = SystemClock.elapsedRealtime()
        var result: TootApiResult? = null
        var bAddGap = false
        val newerLimit = max_id
        while (true) {
            if (isCancelled) {
                log.d("$logCaption: cancelled.")
                break
            }

            if (result != null && SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
                log.d("$logCaption: timeout.")
                bAddGap = true
                break
            }

            if (allRangeChecked(logCaption)) break

            val params = paramsCreator(since_id)

            log.d("$logCaption: $path_base ${params.toString().removeIToken()}")

            val r2 = client.request(
                path_base,
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

            if (newerLimit != null)
                src = src.filter { it.isInjected() || it.getOrderId() < newerLimit }

            if (src.none { !it.isInjected() }) {
                // 直前の取得でカラのデータが帰ってきたら終了
                log.d("$logCaption: empty.")
                break
            }

            // 隙間の最新のステータスIDは取得データ末尾のステータスIDである
            since_id = column.parseRange(result, src).second

            adder(src)
        }

        val sortAllowed = true
        if (sortAllowed) list_tmp?.sortByDescending { it.getOrderId() }

        if (bAddGap) addOne(list_tmp, TootGap.mayNull(max_id, since_id), head = true)

        return result
    }

    // max_id を指定してギャップの上から読む
    private suspend fun <T : TimelineItem> readGapHeadMastodon(
        logCaption: String,
        client: TootApiClient,
        path_base: String,
        filterByIdRange: Boolean,
        listParser: (TootParser, JsonArray) -> List<T>,
        adder: (List<T>) -> Unit,
    ): TootApiResult? {
        list_tmp = ArrayList()
        val delimiter = if (-1 != path_base.indexOf('?')) '&' else '?'
        val requester: suspend (EntityId?) -> TootApiResult? = {
            val path = StringBuilder().apply {
                append(path_base)
                val list = ArrayList<String>()
                if (it != null) list.add("max_id=$it")
                if (since_id != null) list.add("since_id=$since_id")
                list.forEachIndexed { index, s ->
                    append(if (index == 0) delimiter else '&')
                    append(s)
                }
            }.toString()
            log.d("readGapHeadMastodon $path")
            client.request(path)
        }

        val time_start = SystemClock.elapsedRealtime()
        var result: TootApiResult? = null
        var bAddGap = false
        val olderLimit = if (filterByIdRange) since_id else null
        while (true) {
            if (isCancelled) {
                log.d("$logCaption: cancelled.")
                break
            }

            if (result != null && SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
                log.d("$logCaption: timeout.")
                // タイムアウト
                bAddGap = true
                break
            }

            if (max_id == null) {
                context.showToast(false, "$logCaption: missing max_id")
                log.d("$logCaption: missing max_id")
                break
            }

            if (allRangeChecked(logCaption)) break

            val r2 = requester(max_id)

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

            if (olderLimit != null)
                src = src.filter { it.getOrderId() > olderLimit }

            if (src.isEmpty()) {
                // 直前の取得でカラのデータが帰ってきたら終了
                log.d("$logCaption: empty.")
                break
            }

            // 隙間の最新のステータスIDは取得データ末尾のステータスIDである
            max_id = column.parseRange(result, src).first

            adder(src)
        }

        val sortAllowed = false
        if (sortAllowed) list_tmp?.sortByDescending { it.getOrderId() }

        if (bAddGap) addOne(list_tmp, TootGap.mayNull(max_id, since_id))

        return result
    }

    // since_idを指定してギャップの下から読む
    private suspend fun <T : TimelineItem> readGapTailMastodon(
        logCaption: String,
        client: TootApiClient,
        path_base: String,
        filterByIdRange: Boolean,
        listParser: (TootParser, JsonArray) -> List<T>,
        adder: (List<T>) -> Unit
    ): TootApiResult? {
        list_tmp = ArrayList()
        val delimiter = if (-1 != path_base.indexOf('?')) '&' else '?'
        val requester: suspend (EntityId?) -> TootApiResult? = {
            val path = StringBuilder().apply {
                append(path_base)
                val list = ArrayList<String>()
                if (it != null) list.add("min_id=$it")
                if (max_id != null) list.add("max_id=$max_id")
                list.forEachIndexed { index, s ->
                    append(if (index == 0) delimiter else '&')
                    append(s)
                }
            }.toString()
            log.d("$logCaption: $path")
            client.request(path)
        }

        val time_start = SystemClock.elapsedRealtime()
        var result: TootApiResult? = null
        var bAddGap = false
        val newerLimit = if (filterByIdRange) max_id else null
        while (true) {
            if (isCancelled) {
                log.d("$logCaption: cancelled.")
                break
            }

            if (result != null && SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
                log.d("$logCaption: timeout.")
                bAddGap = true
                break
            }

            if (allRangeChecked(logCaption)) break

            val r2 = requester(since_id)

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

            if (newerLimit != null)
                src = src.filter { it.getOrderId() < newerLimit }

            if (src.isEmpty()) {
                // 直前の取得でカラのデータが帰ってきたら終了
                log.d("$logCaption: empty.")
                break
            }

            // 隙間の最新のステータスIDは取得データ末尾のステータスIDである
            since_id = column.parseRange(result, src).second

            adder(src)
        }

        val sortAllowed = false
        if (sortAllowed) list_tmp?.sortByDescending { it.getOrderId() }

        if (bAddGap) addOne(list_tmp, TootGap.mayNull(max_id, since_id), head = true)

        return result
    }

    //////////////////////////////////////////////////////////////////////

    suspend fun getAccountList(
        client: TootApiClient,
        path_base: String,
        mastodonFilterByIdRange: Boolean,
        misskeyParams: JsonObject? = null,
        arrayFinder: (jsonObject: JsonObject) -> JsonArray? =
            nullArrayFinder,
        listParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootAccountRef> =
            defaultAccountListParser
    ): TootApiResult? {

        if (column.pagingType != ColumnPagingType.Default) {
            return TootApiResult("can't support gap")
        }

        val adder: (List<TootAccountRef>) -> Unit =
            { addAll(list_tmp, it, head = !isHead) }

        return if (access_info.isMisskey) {
            val logCaption = "getAccountList.Misskey"
            val params = misskeyParams ?: column.makeMisskeyBaseParameter(parser)
            if (isHead) {
                readGapHeadMisskey(
                    logCaption,
                    client,
                    path_base,
                    paramsCreator = { params.putMisskeyUntil(it) },
                    arrayFinder = arrayFinder,
                    listParser = listParser,
                    adder = adder,
                )
            } else {
                readGapTailMisskey(
                    logCaption,
                    client,
                    path_base,
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
                    path_base,
                    filterByIdRange = mastodonFilterByIdRange,
                    listParser = listParser,
                    adder = adder
                )
            } else {
                readGapTailMastodon(
                    logCaption,
                    client,
                    path_base,
                    filterByIdRange = mastodonFilterByIdRange,
                    listParser = listParser,
                    adder = adder
                )
            }
        }
    }

    suspend fun getReportList(
        client: TootApiClient,
        path_base: String,
        mastodonFilterByIdRange: Boolean,
        listParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootReport> =
            defaultReportListParser
    ): TootApiResult? {

        val adder: (List<TootReport>) -> Unit =
            { addAll(list_tmp, it, head = !isHead) }

        return if (access_info.isMisskey) {
            val logCaption = "getReportList.Misskey"
            val params = column.makeMisskeyBaseParameter(parser)
            if (isHead) {
                readGapHeadMisskey(
                    logCaption,
                    client,
                    path_base,
                    paramsCreator = { params.putMisskeyUntil(it) },
                    listParser = listParser,
                    adder = adder
                )
            } else {
                readGapTailMisskey(
                    logCaption,
                    client,
                    path_base,
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
                    path_base,
                    filterByIdRange = mastodonFilterByIdRange,
                    listParser = listParser,
                    adder = adder
                )
            } else {
                readGapTailMastodon(
                    logCaption,
                    client,
                    path_base,
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

        val path_base: String = column.makeNotificationUrl(client, fromAcct)

        val listParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootNotification> =
            defaultNotificationListParser

        val adder: (List<TootNotification>) -> Unit =
            { addWithFilterNotification(list_tmp, it, head = !isHead) }

        return if (isMisskey) {
            val logCaption = "getNotificationList.Misskey"
            val params = column.makeMisskeyBaseParameter(parser)
                .addMisskeyNotificationFilter(column)
            if (isHead) {
                readGapHeadMisskey(
                    logCaption,
                    client,
                    path_base,
                    paramsCreator = { params.putMisskeyUntil(it) },
                    listParser = listParser,
                    adder = adder
                )
            } else {
                readGapTailMisskey(
                    logCaption,
                    client,
                    path_base,
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
                    path_base,
                    filterByIdRange = mastodonFilterByIdRange,
                    listParser = listParser,
                    adder = adder
                )
            } else {
                readGapTailMastodon(
                    logCaption,
                    client,
                    path_base,
                    filterByIdRange = mastodonFilterByIdRange,
                    listParser = listParser,
                    adder = adder
                )
            }

        }.also {
            list_tmp?.mapNotNull { it as? TootNotification }.notEmpty()?.let {
                PollingWorker.injectData(context, access_info, it)
            }
        }
    }

    suspend fun getStatusList(
        client: TootApiClient,
        path_base: String?,
        mastodonFilterByIdRange: Boolean,
        misskeyParams: JsonObject? = null,
        listParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootStatus> =
            defaultStatusListParser
    ): TootApiResult? {

        path_base ?: return null // cancelled.

        val adder: (List<TootStatus>) -> Unit =
            { addWithFilterStatus(list_tmp, it, head = !isHead) }

        return if (access_info.isMisskey) {
            val logCaption = "getStatusList.Misskey"
            val params = misskeyParams ?: column.makeMisskeyTimelineParameter(parser)

            if (isHead) {
                readGapHeadMisskey(
                    logCaption,
                    client,
                    path_base,
                    paramsCreator = { params.putMisskeyUntil(it) },
                    listParser = listParser,
                    adder = adder
                )
            } else {
                readGapTailMisskey(
                    logCaption,
                    client,
                    path_base,
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
                    path_base,
                    filterByIdRange = mastodonFilterByIdRange,
                    listParser = listParser,
                    adder = adder
                )
            } else {
                readGapTailMastodon(
                    logCaption,
                    client,
                    path_base,
                    filterByIdRange = mastodonFilterByIdRange,
                    listParser = listParser,
                    adder = adder
                )
            }
        }
    }

    suspend fun getConversationSummaryList(
        client: TootApiClient,
        path_base: String,
        mastodonFilterByIdRange: Boolean,
        misskeyParams: JsonObject? = null,
        listParser: (TootParser, JsonArray) -> List<TootConversationSummary> =
            defaultConversationSummaryListParser
    ): TootApiResult? {

        val adder: (List<TootConversationSummary>) -> Unit =
            { addWithFilterConversationSummary(list_tmp, it, head = !isHead) }

        return if (access_info.isMisskey) {
            val logCaption = "getConversationSummaryList.Misskey"
            val params = misskeyParams ?: column.makeMisskeyTimelineParameter(parser)
            if (isHead) {
                readGapHeadMisskey(
                    logCaption,
                    client,
                    path_base,
                    paramsCreator = { params.putMisskeyUntil(it) },
                    listParser = listParser,
                    adder = adder
                )
            } else {
                readGapTailMisskey(
                    logCaption,
                    client,
                    path_base,
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
                    path_base,
                    filterByIdRange = mastodonFilterByIdRange,
                    listParser = listParser,
                    adder = adder
                )
            } else {
                readGapTailMastodon(
                    logCaption,
                    client,
                    path_base,
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
            for (it in column.list_data) minId = counter(it, minId)

            minId ?: return TootApiResult("can't detect paging parameter.")

            val result = client.request(
                "/api/notes/search",
                access_info.putMisskeyApiToken().apply {
                    put("query", column.search_query)
                    put("untilId", minId.toString())
                }
                    .toPostRequestBuilder()
            )

            val jsonArray = result?.jsonArray
            if (jsonArray != null) {
                val src = parser.statusList(jsonArray)
                list_tmp = addWithFilterStatus(list_tmp, src)
                if (src.isNotEmpty()) {
                    addOne(list_tmp, TootSearchGap(TootSearchGap.SearchType.Status))
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
            column.list_data.forEach { counter(it) }

            // https://mastodon2.juggler.jp/api/v2/search?q=gargron&type=accounts&offset=5
            var query = "q=${column.search_query.encodePercent()}&type=$type&offset=$offset"
            if (column.search_resolve) query += "&resolve=1"

            val (apiResult, searchResult) = client.requestMastodonSearch(parser, query)
            if (searchResult != null) {
                list_tmp = ArrayList()
                addAll(list_tmp, searchResult.hashtags)
                addAll(list_tmp, searchResult.accounts)
                addAll(list_tmp, searchResult.statuses)
                if (list_tmp?.isNotEmpty() == true) {
                    addOne(list_tmp, TootSearchGap(gap.type))
                }
            }
            return apiResult

        }
    }
}
