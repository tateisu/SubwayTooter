package jp.juggler.subwaytooter

import android.os.SystemClock
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.util.OpenSticker
import jp.juggler.util.*
import java.util.*
import kotlin.collections.ArrayList

class ColumnTask_Loading(
	columnArg: Column
) : ColumnTask(columnArg, ColumnTaskType.LOADING) {

    companion object {
        private val log = LogCategory("CT_Loading")
    }

    internal var list_pinned: ArrayList<TimelineItem>? = null

    override suspend fun doInBackground(): TootApiResult? {
        ctStarted.set(true)

        if (Pref.bpOpenSticker(pref)) {
            OpenSticker.loadAndWait()
        }

        val client = TootApiClient(context, callback = object : TootApiCallback {
			override val isApiCancelled: Boolean
				get() = isCancelled || column.is_dispose.get()

			override suspend fun publishApiProgress(s: String) {
				runOnMainLooper {
					if (isCancelled) return@runOnMainLooper
					column.task_progress = s
					column.fireShowContent(reason = "loading progress", changeList = ArrayList())
				}
			}
		})

        client.account = access_info

        try {
            val result = access_info.checkConfirmed(context, client)
            if (result == null || result.error != null) return result

            column.keywordFilterTrees = column.encodeFilterTree(column.loadFilter2(client))

            if (!access_info.isNA) {
				val (instance, instanceResult) = TootInstance.get(client)
                instance ?: return instanceResult
                if (instance.instanceType == TootInstance.InstanceType.Pixelfed) {
                    return TootApiResult("currently Pixelfed instance is not supported.")
                }
            }

            return column.type.loading(this, client)
        } catch (ex: Throwable) {
            return TootApiResult(ex.withCaption("loading failed."))
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

    override suspend fun onPostExecute(result: TootApiResult?) {
        if (column.is_dispose.get()) return

        if (isCancelled || result == null) {
            return
        }

        column.bInitialLoading = false
        column.lastTask = null

        if (result.error != null) {
            column.mInitialLoadingError = "${result.error} ${result.requestInfo}".trim()
        } else {
            column.duplicate_map.clear()
            column.list_data.clear()
            val list_tmp = this.list_tmp
            if (list_tmp != null) {
                val list_pinned = this.list_pinned
                if (list_pinned?.isNotEmpty() == true) {
                    val list_new = column.duplicate_map.filterDuplicate(list_pinned)
                    column.list_data.addAll(list_new)
                }

                val list_new = when (column.type) {

                    // 検索カラムはIDによる重複排除が不可能
					ColumnType.SEARCH -> list_tmp

                    // 他のカラムは重複排除してから追加
                    else -> column.duplicate_map.filterDuplicate(list_tmp)
                }

                column.list_data.addAll(list_new)
            }

            column.resumeStreaming(false)
        }
        column.fireShowContent(reason = "loading updated", reset = true)

        // 初期ロードの直後は先頭に移動する
        column.viewHolder?.scrollToTop()

        column.updateMisskeyCapture()
    }

    /////////////////////////////////////////////////////////////////

    private fun addEmptyMessage(emptyMessage: String? = null) {
        if (emptyMessage != null && list_tmp?.isEmpty() == true) {
            // フォロー/フォロワー一覧には警告の表示が必要だった
            val who = column.who_account?.get()
            if (!access_info.isMe(who)) {
                if (who != null && access_info.isRemoteUser(who)) {
                    list_tmp?.add(
						TootMessageHolder(
							context.getString(R.string.follow_follower_list_may_restrict)
						)
					)
                }
                list_tmp?.add(TootMessageHolder(emptyMessage))
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
		initialMaxId: EntityId? = null
	): TootApiResult? {
        val time_start = SystemClock.elapsedRealtime()

        val addToHead = false

        // 初回の取得
        var result = requester(initialMaxId, null)
        val firstResult = result

        var jsonObject = result?.jsonObject
        if (jsonObject != null) {
            if (column.pagingType == ColumnPagingType.Cursor) {
                column.idOld = EntityId.mayNull(jsonObject.string("next"))
            }
            result?.data = arrayFinder(jsonObject)
        }

        var array = result?.jsonArray

        if (array != null) {
            var src = listParser(parser, array)

            if (list_tmp == null) list_tmp = ArrayList(src.size)
            adder(src, addToHead)

            addEmptyMessage(emptyMessage)


            when (column.pagingType) {
				ColumnPagingType.Default -> {
					column.saveRange(bBottom = true, bTop = true, result = result, list = src)
				}

				ColumnPagingType.Offset -> {
					column.offsetNext += src.size
				}

                else -> {
                }
            }

            while (true) {
                if (isCancelled) {
                    log.d("$logCaption: cancelled.")
                    break
                }

                if (!column.isFilterEnabled) {
                    log.d("$logCaption: isFiltered is false.")
                    break
                }

                if (column.idOld == null) {
                    log.d("$logCaption: idOld is empty.")
                    break
                }

                if ((list_tmp?.size ?: 0) >= Column.LOOP_READ_ENOUGH) {
                    log.d("$logCaption: read enough data.")
                    break
                }

                if (src.isEmpty()) {
                    log.d("$logCaption: previous response is empty.")
                    break
                }

                if (SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
                    log.d("$logCaption: timeout.")
                    break
                }

                // フィルタなどが有効な場合は2回目以降の取得
                result = requester(column.idOld, null)

                jsonObject = result?.jsonObject
                if (jsonObject != null) {
                    if (column.pagingType == ColumnPagingType.Cursor) {
                        column.idOld = EntityId.mayNull(jsonObject.string("next"))
                    }
                    result?.data = arrayFinder(jsonObject)
                }
                array = result?.jsonArray

                if (array == null) {
                    log.d("$logCaption: error or cancelled.")
                    break
                }

                src = listParser(parser, array)
                adder(src, addToHead)

                if (!column.saveRangeBottom(result, src)) {
                    log.d("$logCaption: saveRangeBottom returns false, no more items.")
                    break
                }
            }

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
		initialMinId: EntityId? = null
	): TootApiResult? {

        val addToHead = true
        val time_start = SystemClock.elapsedRealtime()

        // 初回の取得
        var result = requester(null, initialMinId)
        val firstResult = result

        var jsonObject = result?.jsonObject
        if (jsonObject != null) result?.data = arrayFinder(jsonObject)

        var array = result?.jsonArray
        if (array != null) {

            var src = listParser(parser, array)

            if (list_tmp == null) list_tmp = ArrayList(src.size)
            adder(src, addToHead)

            column.saveRange(bBottom = true, bTop = true, result = result, list = src)

            if (emptyMessage != null && list_tmp?.isEmpty() == true) {
                // フォロー/フォロワー一覧には警告の表示が必要だった
                val who = column.who_account?.get()
                if (!access_info.isMe(who)) {
                    if (who != null && access_info.isRemoteUser(who)) {
                        list_tmp?.add(
							TootMessageHolder(
								context.getString(R.string.follow_follower_list_may_restrict)
							)
						)
                    }
                    list_tmp?.add(TootMessageHolder(emptyMessage))
                }
            }

            while (true) {
                if (isCancelled) {
                    log.d("$logCaption: cancelled.")
                    break
                }

                if (!column.isFilterEnabled) {
                    log.d("$logCaption: isFiltered is false.")
                    break
                }

                if (column.idRecent == null) {
                    log.d("$logCaption: idRecent is empty.")
                    break
                }

                if ((list_tmp?.size ?: 0) >= Column.LOOP_READ_ENOUGH) {
                    log.d("$logCaption: read enough data.")
                    break
                }

                if (src.isEmpty()) {
                    log.d("$logCaption: previous response is empty.")
                    break
                }

                if (SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
                    log.d("$logCaption: timeout.")
                    break
                }

                // フィルタなどが有効な場合は2回目以降の取得
                result = requester(null, column.idRecent)

                jsonObject = result?.jsonObject
                if (jsonObject != null) result?.data = arrayFinder(jsonObject)

                array = result?.jsonArray

                if (array == null) {
                    log.d("$logCaption: error or cancelled.")
                    break
                }

                src = listParser(parser, array)
                adder(src, addToHead)

                if (!column.saveRangeTop(result, src)) {
                    log.d("$logCaption: saveRangeTop returns false, no more items.")
                    break
                }
            }
            list_tmp?.sortByDescending { it.getOrderId() }
        }
        return firstResult
    }

    private suspend fun <T : TimelineItem> loadMastodonMaxId(
		logCaption: String,
		requester: suspend (maxId: EntityId?, minId: EntityId?) -> TootApiResult?,
		arrayFinder: (JsonObject) -> JsonArray?,
		emptyMessage: String? = null,

		listParser: (parser: TootParser, jsonArray: JsonArray) -> List<T>,
		adder: (List<T>, Boolean) -> Unit,
		initialMaxId: EntityId? = null
	): TootApiResult? {
        val time_start = SystemClock.elapsedRealtime()

        val addToHead = false
        // 初回の取得
        var result = requester(initialMaxId, null)
        val firstResult = result

        var jsonObject = result?.jsonObject
        if (jsonObject != null) {
            if (column.pagingType == ColumnPagingType.Cursor) {
                column.idOld = EntityId.mayNull(jsonObject.string("next"))
            }
            result?.data = arrayFinder(jsonObject)
        }

        var array = result?.jsonArray
        if (array != null) {

            var src = listParser(parser, array)

            if (list_tmp == null) list_tmp = ArrayList(src.size)
            adder(src, addToHead)

            addEmptyMessage(emptyMessage)

            when (column.pagingType) {
				ColumnPagingType.Default -> {
					column.saveRange(bBottom = true, bTop = true, result = result, list = src)
				}

				ColumnPagingType.Offset -> {
					column.offsetNext += src.size
				}

                else -> {
                }
            }

            // フィルタなどが有効な場合は2回目以降の取得
            while (true) {
                if (isCancelled) {
                    log.d("$logCaption: cancelled.")
                    break
                }

                if (!column.isFilterEnabled) {
                    // フィルタしない場合は繰り返さない
                    log.d("$logCaption: isFiltered is false.")
                    break
                }

                if (column.idOld == null) {
                    log.d("$logCaption: idOld is empty.")
                    break
                }

                if ((list_tmp?.size ?: 0) >= Column.LOOP_READ_ENOUGH) {
                    log.d("$logCaption: read enough data.")
                    break
                }

                if (src.isEmpty()) {
                    log.d("$logCaption: previous response is empty.")
                    break
                }

                if (SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
                    log.d("$logCaption: timeout.")
                    break
                }

                result = requester(column.idOld, null)
                jsonObject = result?.jsonObject
                if (jsonObject != null) {
                    if (column.pagingType == ColumnPagingType.Cursor) {
                        column.idOld = EntityId.mayNull(jsonObject.string("next"))
                    }
                    result?.data = arrayFinder(jsonObject)
                }

                array = result?.jsonArray
                if (array == null) {
                    log.d("$logCaption: error or cancelled.")
                    break
                }

                src = listParser(parser, array)
                adder(src, addToHead)

                if (!column.saveRangeBottom(result, src)) {
                    log.d("$logCaption: saveRangeBottom returns false, no more items")
                    break
                }
            }
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
		initialMinId: EntityId? = null
	): TootApiResult? {
        val time_start = SystemClock.elapsedRealtime()

        val addToHead = true
        // 初回の取得
        var result = requester(null, initialMinId)
        val firstResult = result

        var jsonObject = result?.jsonObject
        if (jsonObject != null) result?.data = arrayFinder(jsonObject)

        var array = result?.jsonArray
        if (array != null) {

            var src = listParser(parser, array)

            if (list_tmp == null) list_tmp = ArrayList(src.size)
            adder(src, addToHead)
            column.saveRange(bBottom = true, bTop = true, result = result, list = src)

            if (emptyMessage != null && list_tmp?.isEmpty() == true) {
                // フォロー/フォロワー一覧には警告の表示が必要だった
                val who = column.who_account?.get()
                if (!access_info.isMe(who)) {
                    if (who != null && access_info.isRemoteUser(who)) {
                        list_tmp?.add(
							TootMessageHolder(
								context.getString(R.string.follow_follower_list_may_restrict)
							)
						)
                    }
                    list_tmp?.add(TootMessageHolder(emptyMessage))
                }
            }

            while (true) {
                if (isCancelled) {
                    log.d("$logCaption: cancelled.")
                    break
                }

                if (!column.isFilterEnabled) {
                    log.d("$logCaption: isFiltered is false.")
                    break
                }

                if (column.idRecent == null) {
                    log.d("$logCaption: idRecent is empty.")
                    break
                }

                if ((list_tmp?.size ?: 0) >= Column.LOOP_READ_ENOUGH) {
                    log.d("$logCaption: read enough data.")
                    break
                }

                if (src.isEmpty()) {
                    log.d("$logCaption: previous response is empty.")
                    break
                }

                if (SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
                    log.d("$logCaption: timeout.")
                    break
                }

                // フィルタなどが有効な場合は2回目以降の取得
                result = requester(null, column.idRecent)


                jsonObject = result?.jsonObject
                if (jsonObject != null) result?.data = arrayFinder(jsonObject)

                array = result?.jsonArray
                if (array == null) {
                    log.d("$logCaption: error or cancelled.")
                    break
                }

                src = listParser(parser, array)
                adder(src, addToHead)

                if (!column.saveRangeTop(result, src)) {
                    log.d("$logCaption: saveRangeTop returns false, no more items.")
                    break
                }
            }
        }
        return firstResult
    }

    /////////////////////////////////////////////////////////////////
    // functions that called from ColumnType.loading lambda.

    suspend fun getStatusesPinned(client: TootApiClient, path_base: String) {
        val result = client.request(path_base)
        val jsonArray = result?.jsonArray
        if (jsonArray != null) {
            //
            val src = TootParser(
				context,
				access_info,
				pinned = true,
				highlightTrie = highlight_trie
			).statusList(jsonArray)

            this.list_pinned = addWithFilterStatus(null, src)

            // pinned tootにはページングの概念はない
        }
        log.d("getStatusesPinned: list size=%s", list_pinned?.size ?: -1)
    }

    suspend fun getStatusList(
		client: TootApiClient,
		path_base: String?,
		initialMinId: EntityId? = null,
		initialMaxId: EntityId? = null,
		misskeyParams: JsonObject? = null,
		arrayFinder: (JsonObject) -> JsonArray? =
			nullArrayFinder,
		listParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootStatus> =
			defaultStatusListParser
	): TootApiResult? {

        path_base ?: return null // cancelled.

        val logCaption = "getStatusList"

        val adder: (List<TootStatus>, Boolean) -> Unit = { src, head ->
            this.list_tmp = addWithFilterStatus(list_tmp, src, head = head)
        }

        return if (isMisskey) {
            val params = misskeyParams ?: column.makeMisskeyTimelineParameter(parser)
            val requester: suspend (EntityId?, EntityId?) -> TootApiResult? = { maxId, minId ->
                client.request(
					path_base,
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
            val delimiter = if (-1 == path_base.indexOf('?')) '?' else '&'
            val requester: suspend (maxId: EntityId?, minId: EntityId?) -> TootApiResult? =
                { maxId, minId ->
                    client.request(
						when {
							maxId != null -> "$path_base${delimiter}max_id=$maxId"
							minId != null -> "$path_base${delimiter}min_id=$minId"
							else -> path_base
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

        val path_base = column.makeNotificationUrl(client, fromAcct)

        val arrayFinder = nullArrayFinder

        val listParser: (TootParser, JsonArray) -> List<TootNotification> =
            defaultNotificationListParser

        val adder: (List<TootNotification>, Boolean) -> Unit =
            { src, head -> addWithFilterNotification(list_tmp, src, head = head) }

        return if (isMisskey) {
            val params = column
                .makeMisskeyBaseParameter(parser)
                .addMisskeyNotificationFilter()

            val requester: suspend (EntityId?, EntityId?) -> TootApiResult? = { maxId, minId ->
                client.request(
					path_base,
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
            val delimiter = if (-1 == path_base.indexOf('?')) '?' else '&'
            val requester: suspend (EntityId?, EntityId?) -> TootApiResult? = { maxId, minId ->
                client.request(
					when {
						maxId != null -> "$path_base${delimiter}max_id=$maxId"
						minId != null -> "$path_base${delimiter}min_id=$minId"
						else -> path_base
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
            list_tmp?.mapNotNull { it as? TootNotification }?.let {
                PollingWorker.injectData(context, access_info, it)
            }
        }
    }

    suspend fun getAccountList(
		client: TootApiClient,
		path_base: String,
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
            { src, head -> addAll(list_tmp, src, head = head) }

        return if (isMisskey) {
            val params = misskeyParams ?: column.makeMisskeyBaseParameter(parser)

            val requester: suspend (EntityId?, EntityId?) -> TootApiResult? = { maxId, minId ->
                client.request(
					path_base,
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
            val delimiter = if (-1 == path_base.indexOf('?')) '?' else '&'
            val requester: suspend (EntityId?, EntityId?) -> TootApiResult? = { maxId, minId ->
                client.request(
					when {
						maxId != null -> "$path_base${delimiter}max_id=$maxId"
						minId != null -> "$path_base${delimiter}min_id=$minId"
						else -> path_base
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
		path_base: String,
		initialMinId: EntityId? = null,
		initialMaxId: EntityId? = null,
		misskeyParams: JsonObject? = null,
		listParser: (parser: TootParser, jsonArray: JsonArray) -> List<TootConversationSummary> =
			defaultConversationSummaryListParser
	): TootApiResult? {

        val logCaption = "getConversationSummary"

        val arrayFinder = nullArrayFinder

        val adder: (List<TootConversationSummary>, Boolean) -> Unit =
            { src, head -> addWithFilterConversationSummary(list_tmp, src, head = head) }

        return if (isMisskey) {
            val params = misskeyParams ?: column.makeMisskeyTimelineParameter(parser)

            val requester: suspend (EntityId?, EntityId?) -> TootApiResult? = { maxId, minId ->
                client.request(
					path_base,
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
            val delimiter = if (-1 == path_base.indexOf('?')) '?' else '&'
            val requester: suspend (EntityId?, EntityId?) -> TootApiResult? = { maxId, minId ->
                client.request(
					when {
						maxId != null -> "$path_base${delimiter}max_id=$maxId"
						minId != null -> "$path_base${delimiter}min_id=$minId"
						else -> path_base
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
		path_base: String
	) = client.request(path_base)?.also { result ->
        val src = TootDomainBlock.parseList(result.jsonArray)
        column.saveRange(bBottom = true, bTop = true, result = result, list = src)
        list_tmp = addAll(null, src)
    }

    suspend fun getReportList(
		client: TootApiClient,
		path_base: String
	) = client.request(path_base)?.also { result ->
        val src = parseList(::TootReport, result.jsonArray)
        column.saveRange(bBottom = true, bTop = true, result = result, list = src)
        list_tmp = addAll(null, src)
    }

    suspend fun getScheduledStatuses(client: TootApiClient): TootApiResult? {
        val result = client.request(Column.PATH_SCHEDULED_STATUSES)
        val src = parseList(::TootScheduled, parser, result?.jsonArray)
        list_tmp = addAll(list_tmp, src)

        column.saveRange(bBottom = true, bTop = true, result = result, list = src)

        return result
    }

    suspend fun getListList(
		client: TootApiClient,
		path_base: String,
		misskeyParams: JsonObject? = null
	): TootApiResult? {
        val result = if (misskeyParams != null) {
            client.request(path_base, misskeyParams.toPostRequestBuilder())
        } else {
            client.request(path_base)
        }
        if (result != null) {
            val src = parseList(::TootList, parser, result.jsonArray)
            src.sort()
            column.saveRange(bBottom = true, bTop = true, result = result, list = src)
            this.list_tmp = addAll(null, src)
        }
        return result
    }

    suspend fun getFilterList(
		client: TootApiClient,
		path_base: String
	): TootApiResult? {
        val result = client.request(path_base)
        if (result != null) {
            val src = TootFilter.parseList(result.jsonArray)
            this.list_tmp = addAll(null, src)
        }
        return result
    }

    suspend fun getAntennaList(
		client: TootApiClient,
		path_base: String,
		misskeyParams: JsonObject? = null
	): TootApiResult? {
        val result = if (misskeyParams != null) {
            client.request(path_base, misskeyParams.toPostRequestBuilder())
        } else {
            client.request(path_base)
        }
        if (result != null) {
            val src = parseList(::MisskeyAntenna, result.jsonArray)
            column.saveRange(bBottom = true, bTop = true, result = result, list = src)
            this.list_tmp = addAll(null, src)
        }
        return result
    }

    suspend fun getPublicTlAroundTime(
		client: TootApiClient,
		url: String
	): TootApiResult? {
        // (Mastodonのみ対応)

		val (instance, instanceResult) = TootInstance.get(client)
        instance ?: return instanceResult

        // ステータスIDに該当するトゥート
        // タンスをまたいだりすると存在しないかもしれないが、エラーは出さない
        var result: TootApiResult? =
            client.request(String.format(Locale.JAPAN, Column.PATH_STATUSES, column.status_id))
        val target_status = parser.status(result?.jsonObject)
        if (target_status != null) {
            list_tmp = addOne(list_tmp, target_status)
        }

        column.idOld = null
        column.idRecent = null

        var bInstanceTooOld = false
        if (instance.versionGE(TootInstance.VERSION_2_6_0)) {
            // 指定より新しいトゥート
            result = getStatusList(client, url, initialMinId = column.status_id)
            if (result == null || result.error != null) return result
        } else {
            bInstanceTooOld = true
        }

        // 指定位置より古いトゥート
        result = getStatusList(client, url, initialMaxId = column.status_id)
        if (result == null || result.error != null) return result

        list_tmp?.sortByDescending { it.getOrderId() }
        if (bInstanceTooOld) {
            list_tmp?.add(
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
            client.request(String.format(Locale.JAPAN, Column.PATH_STATUSES, column.status_id))
        val target_status = parser.status(result?.jsonObject) ?: return result
        list_tmp = addOne(list_tmp, target_status)

        // ↑のトゥートのアカウントのID
        column.profile_id = target_status.account.id

        val path = column.makeProfileStatusesUrl(column.profile_id)
        column.idOld = null
        column.idRecent = null

        var bInstanceTooOld = false
        if (instance.versionGE(TootInstance.VERSION_2_6_0)) {
            // 指定より新しいトゥート
            result = getStatusList(client, path, initialMinId = column.status_id)
            if (result == null || result.error != null) return result
        } else {
            bInstanceTooOld = true
        }

        // 指定位置より古いトゥート
        result = getStatusList(client, path, initialMaxId = column.status_id)
        if (result == null || result.error != null) return result

        list_tmp?.sortByDescending { it.getOrderId() }
        if (bInstanceTooOld) {
            list_tmp?.add(
				0,
				TootMessageHolder(context.getString(R.string.around_toot_limitation_warning))
			)
        }

        return result

    }

    suspend fun getConversation(client: TootApiClient): TootApiResult? {
        return if (isMisskey) {
            // 指定された発言そのもの
            val queryParams = column.makeMisskeyBaseParameter(parser).apply {
                put("noteId", column.status_id)
            }

            var result = client.request(
				"/api/notes/show", queryParams.toPostRequestBuilder()
			)
            val jsonObject = result?.jsonObject ?: return result
            val target_status = parser.status(jsonObject)
                ?: return TootApiResult("TootStatus parse failed.")
            target_status.conversation_main = true

            // 祖先
            val list_asc = java.util.ArrayList<TootStatus>()
            while (true) {
                if (client.isApiCancelled) return null
                queryParams["offset"] = list_asc.size
                result = client.request(
					"/api/notes/conversation", queryParams.toPostRequestBuilder()
				)
                val jsonArray = result?.jsonArray ?: return result
                val src = parser.statusList(jsonArray)
                if (src.isEmpty()) break
                list_asc.addAll(src)
            }

            // 直接の子リプライ。(子孫をたどることまではしない)
            val list_desc = java.util.ArrayList<TootStatus>()
            val idSet = HashSet<EntityId>()
            var untilId: EntityId? = null

            while (true) {
                if (client.isApiCancelled) return null

                when {
                    untilId == null -> {
                        queryParams.remove("untilId")
                        queryParams.remove("offset")
                    }

                    misskeyVersion >= 11 -> {
                        queryParams["untilId"] = untilId.toString()
                    }

                    else -> queryParams["offset"] = list_desc.size
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
                    list_desc.add(status)
                    untilId = status.id
                }
                if (untilId == null) break
            }

            // 一つのリストにまとめる
            this.list_tmp = java.util.ArrayList<TimelineItem>(
				list_asc.size + list_desc.size + 2
			).apply {
                addAll(list_asc.sortedBy { it.time_created_at })
                add(target_status)
                addAll(list_desc.sortedBy { it.time_created_at })
                add(TootMessageHolder(context.getString(R.string.misskey_cant_show_all_descendants)))
            }

            //
            result

        } else {
            // 指定された発言そのもの
            var result = client.request(
				String.format(Locale.JAPAN, Column.PATH_STATUSES, column.status_id)
			)
            var jsonObject = result?.jsonObject ?: return result
            val target_status = parser.status(jsonObject)
                ?: return TootApiResult("TootStatus parse failed.")

            // 前後の会話
            result = client.request(
				String.format(
					Locale.JAPAN,
					Column.PATH_STATUSES_CONTEXT, column.status_id
				)
			)
            jsonObject = result?.jsonObject ?: return result
            val conversation_context =
                parseItem(::TootContext, parser, jsonObject)

            // 一つのリストにまとめる
            target_status.conversation_main = true
            if (conversation_context != null) {

                this.list_tmp = java.util.ArrayList(
					1
						+ (conversation_context.ancestors?.size ?: 0)
						+ (conversation_context.descendants?.size ?: 0)
				)
                //
                if (conversation_context.ancestors != null)
                    addWithFilterStatus(
						this.list_tmp,
						conversation_context.ancestors
					)
                //
                addOne(list_tmp, target_status)
                //
                if (conversation_context.descendants != null)
                    addWithFilterStatus(
						this.list_tmp,
						conversation_context.descendants
					)
                //
            } else {
                this.list_tmp = addOne(this.list_tmp, target_status)
                this.list_tmp = addOne(
					this.list_tmp,
					TootMessageHolder(context.getString(R.string.toot_context_parse_failed))
				)
            }

            result
        }
    }

    suspend fun getSearch(client: TootApiClient): TootApiResult? {
        return if (isMisskey) {
            var result: TootApiResult? = TootApiResult()
            val parser = TootParser(context, access_info)

            list_tmp = ArrayList()

            val queryAccount = column.search_query.trim().replace("^@".toRegex(), "")
            if (queryAccount.isNotEmpty()) {
                result = client.request(
					"/api/users/search",
					access_info.putMisskeyApiToken().apply {
						put("query", queryAccount)
						put("localOnly", !column.search_resolve)
					}.toPostRequestBuilder()
				)
                val jsonArray = result?.jsonArray
                if (jsonArray != null) {
                    val src =
                        TootParser(context, access_info).accountList(jsonArray)
                    list_tmp = addAll(list_tmp, src)
                }
            }

            val queryTag = column.search_query.trim().replace("^#".toRegex(), "")
            if (queryTag.isNotEmpty()) {
                result = client.request(
					"/api/hashtags/search",
					access_info.putMisskeyApiToken().apply {
						put("query", queryTag)
					}.toPostRequestBuilder()
				)
                val jsonArray = result?.jsonArray
                if (jsonArray != null) {
                    val src = TootTag.parseList(parser, jsonArray)
                    list_tmp = addAll(list_tmp, src)
                }
            }
            if (column.search_query.isNotEmpty()) {
                result = client.request(
					"/api/notes/search",
					access_info.putMisskeyApiToken().apply {
						put("query", column.search_query)
					}
						.toPostRequestBuilder()
				)
                val jsonArray = result?.jsonArray
                if (jsonArray != null) {
                    val src = parser.statusList(jsonArray)
                    list_tmp = addWithFilterStatus(list_tmp, src)
                    if (src.isNotEmpty()) {
						val (ti, _) = TootInstance.get(client)
                        if (ti?.versionGE(TootInstance.MISSKEY_VERSION_12) == true) {
                            addOne(list_tmp, TootSearchGap(TootSearchGap.SearchType.Status))
                        }
                    }
                }
            }

            // 検索機能が無効だとsearch_query が 400を返すが、他のAPIがデータを返したら成功したことにする
            if (list_tmp?.isNotEmpty() == true) {
                TootApiResult()
            } else {
                result
            }
        } else {
            if (access_info.isPseudo) {
                // 1.5.0rc からマストドンの検索APIは認証を要求するようになった
                return TootApiResult(context.getString(R.string.search_is_not_available_on_pseudo_account))
            }

			val (instance, instanceResult) = TootInstance.get(client)
            instance ?: return instanceResult

            var query = "q=${column.search_query.encodePercent()}"
            if (column.search_resolve) query += "&resolve=1"

			val (apiResult, searchResult) = client.requestMastodonSearch(parser, query)
            if (searchResult != null) {
                list_tmp = java.util.ArrayList()
                addAll(list_tmp, searchResult.hashtags)
                if (searchResult.searchApiVersion >= 2 && searchResult.hashtags.isNotEmpty()) {
                    addOne(list_tmp, TootSearchGap(TootSearchGap.SearchType.Hashtag))
                }
                addAll(list_tmp, searchResult.accounts)
                if (searchResult.searchApiVersion >= 2 && searchResult.accounts.isNotEmpty()) {
                    addOne(list_tmp, TootSearchGap(TootSearchGap.SearchType.Account))
                }
                addAll(list_tmp, searchResult.statuses)
                if (searchResult.searchApiVersion >= 2 && searchResult.statuses.isNotEmpty()) {
                    addOne(list_tmp, TootSearchGap(TootSearchGap.SearchType.Status))
                }
            }
            return apiResult
        }
    }
}
