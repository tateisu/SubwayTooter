package jp.juggler.subwaytooter

import android.os.SystemClock
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.api.finder.*
import jp.juggler.subwaytooter.util.ScrollPosition
import jp.juggler.util.*

@Suppress("ClassNaming")
class ColumnTask_Refresh(
    columnArg: Column,
    private val bSilent: Boolean,
    val bBottom: Boolean,
    internal val postedStatusId: EntityId? = null,
    internal val refreshAfterToot: Int = -1,
) : ColumnTask(
    columnArg,
    if (bBottom) ColumnTaskType.REFRESH_BOTTOM else ColumnTaskType.REFRESH_TOP
) {

    companion object {
        private val log = LogCategory("CT_Refresh")
    }

    private var filterUpdated = false

    override suspend fun background(): TootApiResult? {
        ctStarted.set(true)

        val client = TootApiClient(context, callback = object : TootApiCallback {
            override val isApiCancelled: Boolean
                get() = isCancelled || column.isDispose.get()

            override suspend fun publishApiProgress(s: String) {
                runOnMainLooper {
                    if (isCancelled) return@runOnMainLooper
                    column.taskProgress = s
                    column.fireShowContent(reason = "refresh progress", changeList = ArrayList())
                }
            }
        })
        client.account = accessInfo
        try {

            if (!bBottom) {
                val filterList = column.loadFilter2(client)
                if (filterList != null) {
                    column.keywordFilterTrees = column.encodeFilterTree(filterList)
                    filterUpdated = true
                }
            }

            return column.type.refresh(this, client)
        } catch (ex: Throwable) {
            return TootApiResult(ex.withCaption("refresh failed."))
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

            if (filterUpdated) {
                column.checkFiltersForListData(column.keywordFilterTrees)
            }

            val error = result.error
            if (error != null) {
                column.mRefreshLoadingError = error
                column.mRefreshLoadingErrorTime = SystemClock.elapsedRealtime()
                column.fireShowContent(reason = "refresh error", changeList = ArrayList())
                return
            }

            val listNew = column.duplicateMap.filterDuplicate(listTmp)
            if (listNew.isEmpty()) {
                column.fireShowContent(
                    reason = "refresh list_new is empty",
                    changeList = ArrayList()
                )
                return
            }

            // 事前にスクロール位置を覚えておく
            var sp: ScrollPosition? = null
            val holder = column.viewHolder
            if (holder != null) {
                sp = holder.scrollPosition
            }

            if (bBottom) {
                val changeList = listOf(
                    AdapterChange(
                        AdapterChangeType.RangeInsert,
                        column.listData.size,
                        listNew.size
                    )
                )
                column.listData.addAll(listNew)
                column.fireShowContent(reason = "refresh updated bottom", changeList = changeList)

                // 新着が少しだけ見えるようにスクロール位置を移動する
                if (sp != null) {
                    holder?.setScrollPosition(sp, 20f)
                }
            } else {

                val changeList = ArrayList<AdapterChange>()

                if (column.listData.isNotEmpty() && column.listData[0] is TootGap) {
                    changeList.add(AdapterChange(AdapterChangeType.RangeRemove, 0, 1))
                    column.listData.removeAt(0)
                }

                var doneSound = false
                for (o in listNew) {
                    if (o is TootStatus) {
                        o.highlightSound?.let {
                            if (!doneSound) {
                                doneSound = true
                                App1.sound(it)
                            }
                        }
                        o.highlightSpeech?.let {
                            column.appState.addSpeech(it.name, dedupMode = DedupMode.RecentExpire)
                        }
                    }
                }

                replaceConversationSummary(changeList, listNew, column.listData)

                val added = listNew.size // may 0

                // 投稿後のリフレッシュなら当該投稿の位置を探す
                var statusIndex = -1
                for (i in 0 until added) {
                    val o = listNew[i]
                    if (o is TootStatus && o.id == postedStatusId) {
                        statusIndex = i
                        break
                    }
                }

                changeList.add(AdapterChange(AdapterChangeType.RangeInsert, 0, added))
                column.listData.addAll(0, listNew)
                column.fireShowContent(reason = "refresh updated head", changeList = changeList)

                if (statusIndex >= 0 && refreshAfterToot == PrefI.RAT_REFRESH_SCROLL) {
                    // 投稿後にその投稿にスクロールする
                    if (holder != null) {
                        holder.setScrollPosition(
                            ScrollPosition(column.toAdapterIndex(statusIndex)),
                            0f
                        )
                    } else {
                        column.scrollSave = ScrollPosition(column.toAdapterIndex(statusIndex))
                    }
                } else {
                    //
                    val scrollSave = column.scrollSave
                    when {
                        // ViewHolderがある場合は増加件数分+deltaの位置にスクロールする
                        sp != null -> {
                            sp.adapterIndex += added
                            val delta = if (bSilent) 0f else -20f
                            holder?.setScrollPosition(sp, delta)
                        }
                        // ViewHolderがなくて保存中の位置がある場合、増加件数分ずらす。deltaは難しいので反映しない
                        scrollSave != null -> scrollSave.adapterIndex += added
                        // 保存中の位置がない場合、保存中の位置を新しく作る
                        else -> column.scrollSave =
                            ScrollPosition(column.toAdapterIndex(added))
                    }
                }
            }

            column.updateMisskeyCapture()
        } finally {
            column.fireShowColumnStatus()

            if (!bBottom) {
                column.bRefreshingTop = false

                // TODO ロード状態の度にストリーミングを止めるのはやめる
                // TODO ストリーミングから受信したデータの反映をロード中は行わないようにしたい
                // column.resumeStreaming(false)
            }
        }
    }

    private suspend fun <T : TimelineItem> refreshTopMisskey(
        logCaption: String,
        requester: suspend (first: Boolean) -> TootApiResult?,
        arrayFinder: (JsonObject) -> JsonArray?,
        listParser: (parser: TootParser, jsonArray: JsonArray) -> List<T>,
        adder: (List<T>, Boolean) -> Unit,
    ): TootApiResult? {

        // ColumnPagingType.Defaultだけが始端を更新できる
        if (column.pagingType != ColumnPagingType.Default) {
            return TootApiResult("can't refresh top.")
        }

        val addToHead = true

        // misskeyの場合、sinceIdを指定したら未読範囲の古い方から読んでしまう
        // 最新まで読めるとは限らない
        // 先頭にギャップを置くかもしれない
        var willAddGap = false

        fun parseResult(result: TootApiResult?): Boolean {
            val first = listTmp?.isEmpty() != false

            if (result == null) {
                if (!first) willAddGap = true
                return log.d("$logCaption:cancelled.")
            }

            result.jsonObject?.let { result.data = arrayFinder(it) }

            val array = result.jsonArray
            if (array == null) {
                if (!first) willAddGap = true
                return log.w("$logCaption: missing item list")
            }

            val src = listParser(parser, array)
            if (listTmp == null) listTmp = ArrayList(src.size)
            adder(src, addToHead)

            column.saveRangeTop(result = result, list = src)

            return when {
                // より新しいデータがあるかどうかはわからない。
                // カラのデータを読めたら終端とする
                src.isEmpty() -> log.d("$logCaption: empty item list")
                else -> true
            }
        }

        val timeStart = SystemClock.elapsedRealtime()

        // 初回のリクエスト
        val firstResult = requester(true)
        var more = parseResult(firstResult)

        // 頭の方を読む時は隙間を減らすため、フィルタの有無に関係なく繰り返しを行う
        while (more) more = when {
            isCancelled ->
                log.d("$logCaption: cancelled.")

            (listTmp?.size ?: 0) >= Column.LOOP_READ_ENOUGH -> {
                // 既に十分読んだなら止める
                willAddGap = true
                log.d("$logCaption: read enough. make gap.")
            }

            SystemClock.elapsedRealtime() - timeStart > Column.LOOP_TIMEOUT -> {
                willAddGap = true
                log.d("$logCaption: timeout.")
            }

            else -> parseResult(requester(false))
        }

        // MisskeyはsinceIdを指定するとID昇順のデータが得られるので、ID降順に並べ直す
        listTmp?.sortByDescending { it.getOrderId() }

        fun doesAddGap() = when {
            isCancelled -> false
            listTmp?.isNotEmpty() != true -> false
            willAddGap -> true
            else -> PrefB.bpForceGap(App1.pref)
        }

        if (doesAddGap()) {
            addOne(listTmp, TootGap.mayNull(null, column.idRecent), head = addToHead)
        }

        return firstResult
    }

    private suspend fun <T : TimelineItem> refreshBottomMisskey(
        logCaption: String,
        requester: suspend (first: Boolean) -> TootApiResult?,
        arrayFinder: (JsonObject) -> JsonArray?,
        listParser: (parser: TootParser, jsonArray: JsonArray) -> List<T>,
        adder: (List<T>, Boolean) -> Unit,
        repeatReading: Boolean = false,
    ): TootApiResult? {

        @Suppress("NON_EXHAUSTIVE_WHEN")
        when (column.pagingType) {
            ColumnPagingType.None ->
                return TootApiResult(context.getString(R.string.end_of_list))

            ColumnPagingType.Cursor ->
                if (column.idOld == null) {
                    return TootApiResult(context.getString(R.string.end_of_list))
                }
        }

        val addToHead = false

        fun parseResult(result: TootApiResult?): Boolean {

            result ?: return log.d("$logCaption: cancelled.")

            result.jsonObject?.let { jsonObject ->
                if (column.pagingType == ColumnPagingType.Cursor) {
                    column.idOld = EntityId.mayNull(jsonObject.string("next"))
                }
                result.data = arrayFinder(jsonObject)
            }

            val array = result.jsonArray
                ?: return log.w("$logCaption: missing item list.")

            val src = listParser(parser, array)
            if (listTmp == null) listTmp = ArrayList(src.size)
            adder(src, addToHead)

            val more = when (column.pagingType) {
                ColumnPagingType.Offset -> {
                    column.offsetNext += src.size
                    true
                }

                // ColumnPagingType.Default
                else -> column.saveRangeBottom(result, src)
            }

            return when {
                !more -> log.d("$logCaption: no more items.")
                // max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
                // 直前のデータが0個なら終了とみなすしかなさそう
                src.isEmpty() -> log.d("$logCaption: empty item list.")
                else -> true
            }
        }

        val timeStart = SystemClock.elapsedRealtime()
        val firstResult = requester(true)
        var more = parseResult(firstResult) && repeatReading
        while (more) more = when {
            isCancelled ->
                log.d("$logCaption: cancelled.")

            // bottomの場合、フィルタなしなら繰り返さない
            !column.isFilterEnabled ->
                log.d("$logCaption: isFilterEnabled is false.")

            column.idOld == null ->
                log.d("$logCaption: idOld is null.")

            // 十分読んだらそれで終了
            (listTmp?.size ?: 0) >= Column.LOOP_READ_ENOUGH ->
                log.d("$logCaption: read enough data.")

            SystemClock.elapsedRealtime() - timeStart > Column.LOOP_TIMEOUT ->
                log.d("$logCaption: loop timeout.")

            else -> parseResult(requester(false))
        }

        return firstResult
    }

    private suspend fun <T : TimelineItem> refreshTopMastodon(
        logCaption: String,
        requester: suspend (first: Boolean, gapIdNewer: EntityId?, gapIdOlder: EntityId?) -> TootApiResult?,
        arrayFinder: (JsonObject) -> JsonArray?,
        listParser: (parser: TootParser, jsonArray: JsonArray) -> List<T>,
        adder: (List<T>, Boolean) -> Unit,
    ): TootApiResult? {

        // 上端の差分更新に対応できるのは ColumnPagingType.Default だけ
        if (column.pagingType != ColumnPagingType.Default) {
            return TootApiResult("can't refresh top.")
        }

        val addToHead = false

        // 頭の方を読む時は隙間を減らすため、フィルタの有無に関係なく繰り返しを行う
        var willAddGap = false

        // 2回目以降のリクエスト範囲はギャップを意識したものになる
        val lastSinceId = column.idRecent
        var maxId: EntityId? = null

        fun parseResult(result: TootApiResult?): Boolean {
            val first = listTmp?.isEmpty() != false

            if (result == null) {
                if (!first) willAddGap = true
                return log.d("$logCaption: cancelled.")
            }

            result.jsonObject?.let { result.data = arrayFinder(it) }

            val array = result.jsonArray
            if (array == null) {
                if (!first) willAddGap = true
                return log.w("$logCaption: missing item list")
            }

            val src = listParser(parser, array)
            if (listTmp == null) listTmp = ArrayList(src.size)
            adder(src, addToHead)

            when {
                first -> {
                    // TLは (新しいデータ)(ギャップ)(古いデータ) となるので、レンジを保存するのはここだけで良い
                    // 続く読み込みはギャップを埋めるものなのでレンジを保存してはいけない
                    column.saveRangeTop(result, src)
                }
                else -> {
                    // 今読んだのはギャップなので範囲を保存してはいけない
                }
            }

            return when {
                // max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
                // 直前のデータが0個なら終了とみなすしかなさそう
                src.isEmpty() -> log.d("$logCaption: empty list.")
                else -> {
                    // 直前に読んだ範囲のmaxIdを調べる
                    maxId = column.parseRange(result, src).first
                    true
                }
            }
        }

        val timeStart = SystemClock.elapsedRealtime()
        // 初回リクエスト
        val firstResult = requester(true, null, null)
        var more = parseResult(firstResult)
        // 2回目以降
        while (more) more = when {
            isCancelled ->
                log.d("$logCaption: cancelled.")

            maxId == null ->
                log.d("$logCaption: max_id is null.")

            (listTmp?.size ?: 0) >= Column.LOOP_READ_ENOUGH -> {
                willAddGap = true
                log.d("$logCaption: read enough. make gap.")
            }

            SystemClock.elapsedRealtime() - timeStart > Column.LOOP_TIMEOUT -> {
                willAddGap = true
                log.d("$logCaption: timeout. make gap.")
            }

            else -> parseResult(requester(false, maxId, lastSinceId))
        }

        if (!isCancelled &&
            listTmp?.isNotEmpty() == true &&
            (willAddGap || PrefB.bpForceGap(context))
        ) {
            addOne(listTmp, TootGap.mayNull(maxId, lastSinceId), head = addToHead)
        }
        return firstResult
    }

    private suspend fun <T : TimelineItem> refreshTopMastodonMinId(
        logCaption: String,
        requester: suspend (first: Boolean, gapIdNewer: EntityId?, gapIdOlder: EntityId?) -> TootApiResult?,
        arrayFinder: (JsonObject) -> JsonArray?,
        listParser: (parser: TootParser, jsonArray: JsonArray) -> List<T>,
        adder: (List<T>, Boolean) -> Unit,
    ): TootApiResult? {

        // 上端の差分更新に対応できるのは ColumnPagingType.Default だけ
        if (column.pagingType != ColumnPagingType.Default) {
            return TootApiResult("can't refresh top.")
        }

        val addToHead = true

        var willAddGap = false

        // 2回目以降のリクエスト範囲
        val lastSinceId = column.idRecent
        var maxId: EntityId? = null

        fun parseResult(result: TootApiResult?): Boolean {
            val first = listTmp?.isEmpty() != false

            if (result == null) {
                if (!first) willAddGap = true
                return log.d("$logCaption:cancelled.")
            }

            result.jsonObject?.let { result.data = arrayFinder(it) }

            val array = result.jsonArray
            if (array == null) {
                if (!first) willAddGap = true
                return log.w("$logCaption: missing item list")
            }

            val src = listParser(parser, array)
            if (listTmp == null) listTmp = ArrayList(src.size)
            adder(src, addToHead)

            column.saveRangeTop(result, src)
            // Linkヘッダからはより新しいデータがあるかどうかはわからない

            return when {
                // max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
                // 直前のデータが0個なら終了とみなすしかなさそう
                src.isEmpty() -> log.d("$logCaption: empty list.")

                else -> {
                    // 直前に読んだ範囲のmaxIdを調べる
                    maxId = column.parseRange(result, src).first
                    true
                }
            }
        }

        val timeStart = SystemClock.elapsedRealtime()

        val firstResult = requester(true, null, null)
        var more = parseResult(firstResult)
        while (more) more = when {
            isCancelled ->
                log.d("$logCaption: cancelled.")

            maxId == null ->
                log.d("$logCaption: max_id is null.")

            SystemClock.elapsedRealtime() - timeStart > Column.LOOP_TIMEOUT -> {
                // タイムアウト
                // 隙間ができるかもしれない。後ほど手動で試してもらうしかない
                willAddGap = true
                log.d("$logCaption: timeout. make gap.")
            }

            else -> parseResult(requester(false, null, null))
        }

        if (!isCancelled &&
            listTmp?.isNotEmpty() == true &&
            (willAddGap || PrefB.bpForceGap(context))
        ) {
            addOne(listTmp, TootGap.mayNull(maxId, lastSinceId), head = addToHead)
        }

        return firstResult
    }

    private suspend fun <T : TimelineItem> refreshBottomMastodon(
        logCaption: String,
        requester: suspend (first: Boolean, gapIdNewer: EntityId?, gapIdOlder: EntityId?) -> TootApiResult?,
        arrayFinder: (JsonObject) -> JsonArray?,
        listParser: (parser: TootParser, jsonArray: JsonArray) -> List<T>,
        adder: (List<T>, Boolean) -> Unit,
        repeatReading: Boolean = false,
    ): TootApiResult? {

        @Suppress("NON_EXHAUSTIVE_WHEN")
        when (column.pagingType) {
            ColumnPagingType.None ->
                return TootApiResult(context.getString(R.string.end_of_list))

            ColumnPagingType.Cursor ->
                if (column.idOld == null) {
                    return TootApiResult(context.getString(R.string.end_of_list))
                }
        }

        val addToHead = false

        // parse result and add to list_tmp
        // returns false if no more result
        fun parseResult(result: TootApiResult?): Boolean {
            result ?: return log.d("$logCaption: cancelled.")

            result.jsonObject?.let { it ->
                if (column.pagingType == ColumnPagingType.Cursor) {
                    column.idOld = EntityId.mayNull(it.string("next"))
                }
                result.data = arrayFinder(it)
            }
            val array = result.jsonArray
                ?: return log.d("$logCaption: missing item list.")

            val src = listParser(parser, array)
            if (listTmp == null) listTmp = ArrayList(src.size)
            adder(src, addToHead)

            // save range to column
            // false if no more result
            val more = when (column.pagingType) {
                ColumnPagingType.Offset -> {
                    column.offsetNext += src.size
                    true
                }

                // ColumnPagingType.Default
                else -> column.saveRangeBottom(result, src)
            }

            return when {
                !more -> log.d("$logCaption: no more items.")

                // max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
                // 直前のデータが0個なら終了とみなすしかなさそう
                src.isEmpty() -> log.d("$logCaption: empty item list.")

                else -> true
            }
        }

        val timeStart = SystemClock.elapsedRealtime()
        val firstResult = requester(true, null, null)
        var more = parseResult(firstResult) && repeatReading
        while (more) more = when {
            isCancelled ->
                log.d("$logCaption: cancelled.")

            // bottomの場合、フィルタなしなら繰り返さない
            !column.isFilterEnabled ->
                log.d("$logCaption: isFiltered is false.")

            column.idOld == null ->
                log.d("$logCaption: idOld is null.")

            (listTmp?.size ?: 0) >= Column.LOOP_READ_ENOUGH ->
                log.d("$logCaption: read enough data.")

            SystemClock.elapsedRealtime() - timeStart > Column.LOOP_TIMEOUT ->
                log.d("$logCaption: loop timeout.")

            else -> parseResult(requester(false, null, null))
        }

        return firstResult
    }

    private suspend fun defaultRequesterMisskey(
        client: TootApiClient,
        pathBase: String,
        params: JsonObject,
        first: Boolean,
    ) = client.request(
        pathBase,
        params.apply {
            if (!bBottom) {
                if (first) {

                    addRangeMisskey(bBottom)
                } else {
                    putMisskeySince(column.idRecent)
                }
            } else {
                if (first) {

                    when (column.pagingType) {
                        ColumnPagingType.Default -> addRangeMisskey(bBottom)
                        ColumnPagingType.Offset -> put("offset", column.offsetNext)
                        ColumnPagingType.Cursor -> put("cursor", column.idOld)

                        ColumnPagingType.None -> {
                        }
                    }
                } else {
                    when (column.pagingType) {
                        ColumnPagingType.Default -> putMisskeyUntil(column.idOld)
                        ColumnPagingType.Offset -> put("offset", column.offsetNext)
                        ColumnPagingType.Cursor -> put("cursor", column.idOld)

                        ColumnPagingType.None -> {
                        }
                    }
                }
            }
        }.toPostRequestBuilder()
    )

    private suspend fun defaultRequesterMastodon(
        client: TootApiClient,
        pathBase: String,
        delimiter: Char,
        @Suppress("UNUSED_PARAMETER") first: Boolean,
        useMinId: Boolean,
        gapIdNewer: EntityId?,
        gapIdOlder: EntityId?,
    ) =
        client.request(
            when {
                // profile directory 用
                column.pagingType == ColumnPagingType.Offset ->
                    "$pathBase${delimiter}offset=${column.offsetNext}"

                useMinId ->
                    column.addRangeMin(pathBase)

                gapIdNewer != null ->
                    "$pathBase${delimiter}max_id=$gapIdNewer&since_id=$gapIdOlder"

                else ->
                    column.addRange(bBottom = bBottom, pathBase, delimiter = delimiter)
            }
        )

    suspend fun getStatusList(
        client: TootApiClient,
        pathBase: String?,
        useMinId: Boolean = false,
        misskeyParams: JsonObject? = null,
        listParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootStatus> =
            defaultStatusListParser,
    ): TootApiResult? {

        pathBase ?: return null // cancelled.

        val logCaption = "getStatusList"
        val adder: (List<TootStatus>, Boolean) -> Unit =
            { src, head -> addWithFilterStatus(listTmp, src, head = head) }

        return if (isMisskey) {

            val params = misskeyParams ?: column.makeMisskeyTimelineParameter(parser)

            val requester: suspend (Boolean) -> TootApiResult? =
                { defaultRequesterMisskey(client, pathBase, params, it) }

            when {
                bBottom -> refreshBottomMisskey(
                    logCaption,
                    requester = requester,
                    arrayFinder = nullArrayFinder,
                    listParser = listParser,
                    adder = adder,
                    repeatReading = true
                )
                else -> refreshTopMisskey(
                    logCaption,
                    requester = requester,
                    arrayFinder = nullArrayFinder,
                    listParser = listParser,
                    adder = adder
                )
            }
        } else {

            val delimiter = if (-1 != pathBase.indexOf('?')) '&' else '?'

            val requester: suspend (first: Boolean, gapIdNewer: EntityId?, gapIdOlder: EntityId?) -> TootApiResult? =
                { first, gapIdNewer, gapIdOlder ->
                    defaultRequesterMastodon(
                        client,
                        pathBase,
                        delimiter,
                        first,
                        useMinId = useMinId,
                        gapIdNewer,
                        gapIdOlder,
                    )
                }

            when {
                bBottom -> refreshBottomMastodon(
                    logCaption,
                    requester = requester,
                    arrayFinder = nullArrayFinder,
                    listParser = listParser,
                    adder = adder,
                    repeatReading = true
                )
                useMinId -> refreshTopMastodonMinId(
                    logCaption,
                    requester = requester,
                    arrayFinder = nullArrayFinder,
                    listParser = listParser,
                    adder
                )
                else -> refreshTopMastodon(
                    logCaption,
                    requester = requester,
                    arrayFinder = nullArrayFinder,
                    listParser = listParser,
                    adder
                )
            }
        }
    }

    suspend fun getNotificationList(
        client: TootApiClient,
        fromAcct: String? = null,
        useMinId: Boolean = false,
    ): TootApiResult? {

        val logCaption = "getNotificationList"

        val listParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootNotification> =
            defaultNotificationListParser

        val adder: (List<TootNotification>, Boolean) -> Unit =
            { src, head -> addWithFilterNotification(listTmp, src, head = head) }

        // Misskeyの通知TLはfromAcctに対応していない
        val pathBase = column.makeNotificationUrl(client, fromAcct)

        return if (isMisskey) {

            val params =
                column.makeMisskeyBaseParameter(parser).addMisskeyNotificationFilter(column)

            val requester: suspend (Boolean) -> TootApiResult? =
                { defaultRequesterMisskey(client, pathBase, params, it) }

            when {
                bBottom -> refreshBottomMisskey(
                    logCaption,
                    requester = requester,
                    arrayFinder = nullArrayFinder,
                    listParser = listParser,
                    adder = adder,
                    repeatReading = true
                )
                else -> refreshTopMisskey(
                    logCaption,
                    requester = requester,
                    arrayFinder = nullArrayFinder,
                    listParser = listParser,
                    adder = adder
                )
            }
        } else {

            val delimiter = if (-1 != pathBase.indexOf('?')) '&' else '?'

            val requester: suspend (first: Boolean, gapIdNewer: EntityId?, gapIdOlder: EntityId?) -> TootApiResult? =
                { first, gapIdNewer, gapIdOlder ->
                    defaultRequesterMastodon(
                        client,
                        pathBase,
                        delimiter,
                        first,
                        useMinId = useMinId,
                        gapIdNewer,
                        gapIdOlder,
                    )
                }

            when {
                bBottom -> refreshBottomMastodon(
                    logCaption,
                    requester = requester,
                    arrayFinder = nullArrayFinder,
                    listParser = listParser,
                    adder = adder,
                    repeatReading = true
                )
                useMinId -> refreshTopMastodonMinId(
                    logCaption,
                    requester = requester,
                    arrayFinder = nullArrayFinder,
                    listParser = listParser,
                    adder = adder
                )
                else -> refreshTopMastodon(
                    logCaption,
                    requester = requester,
                    arrayFinder = nullArrayFinder,
                    listParser = listParser,
                    adder = adder
                )
            }
        }
    }

    suspend fun getAccountList(
        client: TootApiClient,
        pathBase: String,
        misskeyParams: JsonObject? = null,
        arrayFinder: (JsonObject) -> JsonArray? =
            nullArrayFinder,
        listParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootAccountRef> =
            defaultAccountListParser,
    ): TootApiResult? {

        val logCaption = "getAccountList"
        val adder: (List<TootAccountRef>, Boolean) -> Unit =
            { src, head -> addAll(listTmp, src, head = head) }

        return if (isMisskey) {
            val params = misskeyParams ?: column.makeMisskeyBaseParameter(parser)
            val requester: suspend (Boolean) -> TootApiResult? =
                { defaultRequesterMisskey(client, pathBase, params, it) }
            when {
                bBottom -> refreshBottomMisskey(
                    logCaption,
                    requester = requester,
                    arrayFinder = arrayFinder,
                    listParser = listParser,
                    adder = adder
                )
                else -> refreshTopMisskey(
                    logCaption,
                    requester = requester,
                    arrayFinder = arrayFinder,
                    listParser = listParser,
                    adder = adder
                )
            }
        } else {
            val delimiter = if (-1 != pathBase.indexOf('?')) '&' else '?'
            val requester: suspend (first: Boolean, gapIdNewer: EntityId?, gapIdOlder: EntityId?) -> TootApiResult? =
                { first, gapIdNewer, gapIdOlder ->
                    defaultRequesterMastodon(
                        client,
                        pathBase,
                        delimiter,
                        first,
                        useMinId = false,
                        gapIdNewer,
                        gapIdOlder,
                    )
                }

            when {
                bBottom -> refreshBottomMastodon(
                    logCaption,
                    requester = requester,
                    arrayFinder = arrayFinder,
                    listParser = listParser,
                    adder = adder
                )
                else -> refreshTopMastodon(
                    logCaption,
                    requester = requester,
                    arrayFinder = arrayFinder,
                    listParser = listParser,
                    adder = adder
                )
            }
        }
    }

    suspend fun getDomainList(
        client: TootApiClient,
        pathBase: String,
    ): TootApiResult? {

        val logCaption = "getDomainList"

        val adder: (List<TimelineItem>, Boolean) -> Unit =
            { src, head -> addAll(listTmp, src, head = head) }

        val listParser = defaultDomainBlockListParser

        return if (isMisskey) {
            TootApiResult("misskey support is not yet implemented.")
        } else {
            // ページングIDはサーバ側の内部IDで、Linkヘッダ以外には露出しない。
            val delimiter = if (-1 != pathBase.indexOf('?')) '&' else '?'
            val requester: suspend (first: Boolean, gapIdNewer: EntityId?, gapIdOlder: EntityId?) -> TootApiResult? =
                { first, gapIdNewer, gapIdOlder ->
                    defaultRequesterMastodon(
                        client,
                        pathBase,
                        delimiter,
                        first,
                        useMinId = false,
                        gapIdNewer,
                        gapIdOlder,
                    )
                }

            when {
                bBottom -> refreshBottomMastodon(
                    logCaption,
                    requester = requester,
                    arrayFinder = nullArrayFinder,
                    listParser = listParser,
                    adder = adder
                )
                else -> refreshTopMastodon(
                    logCaption,
                    requester = requester,
                    arrayFinder = nullArrayFinder,
                    listParser = listParser,
                    adder = adder
                )
            }
        }
    }

// リスト一覧にはページネーションがない
//			fun getListList(client : TootApiClient, path_base : String) : TootApiResult? {
//
//				if(isMisskey) return TootApiResult("misskey support is not yet implemented.")
//
//				return TootApiResult("Mastodon's /api/v1/lists has no pagination.")
//			}

    suspend fun getReportList(
        client: TootApiClient,
        pathBase: String,
    ): TootApiResult? {

        val logCaption = "getReportList"
        val adder: (List<TootReport>, Boolean) -> Unit =
            { src, head -> addAll(listTmp, src, head = head) }

        val listParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootReport> =
            { _, jsonArray -> parseList(::TootReport, jsonArray) }

        return if (isMisskey) {
            TootApiResult("Misskey has no API to list reports from you.")
        } else {
            // ページングIDはサーバ側の内部IDで、Linkヘッダ以外には露出しない。
            val delimiter = if (-1 != pathBase.indexOf('?')) '&' else '?'
            val requester: suspend (first: Boolean, gapIdNewer: EntityId?, gapIdOlder: EntityId?) -> TootApiResult? =
                { first, gapIdNewer, gapIdOlder ->
                    defaultRequesterMastodon(
                        client,
                        pathBase,
                        delimiter,
                        first,
                        useMinId = false,
                        gapIdNewer,
                        gapIdOlder,
                    )
                }

            when {
                bBottom -> refreshBottomMastodon(
                    logCaption,
                    requester = requester,
                    arrayFinder = nullArrayFinder,
                    listParser = listParser,
                    adder = adder
                )
                else -> refreshTopMastodon(
                    logCaption,
                    requester = requester,
                    arrayFinder = nullArrayFinder,
                    listParser = listParser,
                    adder = adder
                )
            }
        }
    }

    suspend fun getConversationSummaryList(
        client: TootApiClient,
        pathBase: String,
        //	aroundMin : Boolean = false,
        misskeyParams: JsonObject? = null,
    ): TootApiResult? {
        val logCaption = "getConversationSummaryList"

        val listParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootConversationSummary> =
            defaultConversationSummaryListParser

        val adder: (List<TootConversationSummary>, Boolean) -> Unit =
            { src, head -> addWithFilterConversationSummary(listTmp, src, head = head) }

        return if (isMisskey) {

            val params = misskeyParams ?: column.makeMisskeyTimelineParameter(parser)

            val requester: suspend (Boolean) -> TootApiResult? =
                { defaultRequesterMisskey(client, pathBase, params, it) }

            when {
                bBottom -> refreshBottomMisskey(
                    logCaption,
                    requester = requester,
                    arrayFinder = nullArrayFinder,
                    listParser = listParser,
                    adder = adder,
                    repeatReading = true
                )
                else -> refreshTopMisskey(
                    logCaption,
                    requester = requester,
                    arrayFinder = nullArrayFinder,
                    listParser = listParser,
                    adder = adder
                )
            }
        } else {
            val delimiter = if (-1 != pathBase.indexOf('?')) '&' else '?'
            val requester: suspend (first: Boolean, gapIdNewer: EntityId?, gapIdOlder: EntityId?) -> TootApiResult? =
                { first, gapIdNewer, gapIdOlder ->
                    defaultRequesterMastodon(
                        client,
                        pathBase,
                        delimiter,
                        first,
                        useMinId = false,
                        gapIdNewer,
                        gapIdOlder,
                    )
                }

            when {
                bBottom -> refreshBottomMastodon(
                    logCaption,
                    requester = requester,
                    arrayFinder = nullArrayFinder,
                    listParser = listParser,
                    adder = adder,
                    repeatReading = true
                )
                else -> refreshTopMastodon(
                    logCaption,
                    requester = requester,
                    arrayFinder = nullArrayFinder,
                    listParser = listParser,
                    adder
                )
            }
        }
    }

    suspend fun getScheduledStatuses(client: TootApiClient): TootApiResult? {
        val result = client.request(column.addRange(bBottom, ApiPath.PATH_SCHEDULED_STATUSES))
        val src = parseList(::TootScheduled, parser, result?.jsonArray)
        listTmp = addAll(listTmp, src)
        column.saveRange(bBottom, !bBottom, result, src)
        return result
    }
}
