package jp.juggler.subwaytooter

import android.os.SystemClock
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.util.*
import java.lang.StringBuilder

class ColumnTask_Gap(
	columnArg : Column,
	private val gap : TimelineItem,
	private val isHeadArg : Boolean? = null
) : ColumnTask(columnArg, ColumnTaskType.GAP) {
	
	companion object {
		
		internal val log = LogCategory("CT_Gap")
	}
	
	private var max_id : EntityId? = (gap as? TootGap)?.max_id
	private var since_id : EntityId? = (gap as? TootGap)?.since_id
	
	// isHeadArgが指定されてない場合、Mastodonは上から読んでMisskeyは下から読む
	private val isHead : Boolean
		get() = isHeadArg ?: access_info.isMastodon
	
	override fun doInBackground() : TootApiResult? {
		ctStarted.set(true)
		
		val client = TootApiClient(context, callback = object : TootApiCallback {
			override val isApiCancelled : Boolean
				get() = isCancelled || column.is_dispose.get()
			
			override fun publishApiProgress(s : String) {
				runOnMainLooper {
					if(isCancelled) return@runOnMainLooper
					column.task_progress = s
					column.fireShowContent(reason = "gap progress", changeList = ArrayList())
				}
			}
		})
		
		client.account = access_info
		
		try {
			return column.type.gap(this, client)
		} catch(ex : Throwable) {
			return TootApiResult(ex.withCaption("gap loading failed."))
		} finally {
			try {
				column.updateRelation(client, list_tmp, column.who_account, parser)
			} catch(ex : Throwable) {
				log.trace(ex)
			}
			
			ctClosed.set(true)
			runOnMainLooperDelayed(333L) {
				if(! isCancelled) column.fireShowColumnStatus()
			}
		}
	}
	
	override fun onPostExecute(result : TootApiResult?) {
		if(column.is_dispose.get()) return
		
		if(isCancelled || result == null) {
			return
		}
		
		try {
			
			column.lastTask = null
			column.bRefreshLoading = false
			
			val error = result.error
			if(error != null) {
				column.mRefreshLoadingError = error
				column.fireShowContent(reason = "gap error", changeList = ArrayList())
				return
			}
			
			val list_tmp = this.list_tmp
			if(list_tmp == null) {
				column.fireShowContent(reason = "gap list_tmp is null", changeList = ArrayList())
				return
			}
			
			val list_new = when(column.type) {
				
				// 検索カラムはIDによる重複排除が不可能
				ColumnType.SEARCH -> list_tmp
				
				// 他のカラムは重複排除してから追加
				else -> column.duplicate_map.filterDuplicate(list_tmp)
			}
			
			// 0個でもギャップを消すために以下の処理を続ける
			
			val changeList = ArrayList<AdapterChange>()
			
			column.replaceConversationSummary(changeList, list_new, column.list_data)
			
			val added = list_new.size // may 0
			
			val position = column.list_data.indexOf(gap)
			if(position == - 1) {
				log.d("gap not found..")
				column.fireShowContent(reason = "gap not found", changeList = ArrayList())
				return
			}
			
			val iv = if(isHead) {
				Pref.ipGapHeadScrollPosition
			} else {
				Pref.ipGapTailScrollPosition
			}.invoke(pref)
			val scrollHead = iv == Pref.GSP_HEAD
			
			if(scrollHead) {
				// ギャップを頭から読んだ場合、スクロール位置の調整は不要
				
				column.list_data.removeAt(position)
				column.list_data.addAll(position, list_new)
				
				changeList.add(AdapterChange(AdapterChangeType.RangeRemove, position))
				if(added > 0) {
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
				if(holder != null) {
					try {
						restore_y = holder.getListItemOffset(restore_idx)
					} catch(ex : IndexOutOfBoundsException) {
						restore_idx = position
						try {
							restore_y = holder.getListItemOffset(restore_idx)
						} catch(ex2 : IndexOutOfBoundsException) {
							restore_idx = - 1
						}
					}
				}
				
				column.list_data.removeAt(position)
				column.list_data.addAll(position, list_new)
				
				changeList.add(AdapterChange(AdapterChangeType.RangeRemove, position))
				if(added > 0) {
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
						if(scroll_save != null) {
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
	
	// max_id を指定してギャップの上から読む
	private fun <T : TimelineItem> readGapHeadMisskey(
		logCaption : String,
		sortAllowed : Boolean = true,
		requester : (EntityId?) -> TootApiResult?,
		arrayFinder : (JsonObject) -> JsonArray? = { null },
		listParser : (TootParser, JsonArray) -> ArrayList<T>,
		adder : (ArrayList<T>) -> Unit
	) : TootApiResult? {
		val time_start = SystemClock.elapsedRealtime()
		var result : TootApiResult? = null
		var bAddGap = false
		while(true) {
			if(isCancelled) {
				log.d("$logCaption: cancelled.")
				break
			}
			
			if(result != null && SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
				log.d("$logCaption: timeout.")
				bAddGap = true
				break
			}
			
			val r2 = requester(max_id)
			
			val jsonObject = r2?.jsonObject
			if(jsonObject != null) r2.data = arrayFinder(jsonObject)
			
			val jsonArray = r2?.jsonArray
			if(jsonArray == null) {
				log.d("$logCaption: error or cancelled. make gap.")
				
				// 成功データがない場合だけ、今回のエラーを返すようにする
				if(result == null) result = r2
				
				bAddGap = true
				
				break
			}
			
			// 成功した場合はそれを返したい
			result = r2
			
			val src = listParser(parser, jsonArray)
			
			if(src.isEmpty()) {
				// 直前の取得でカラのデータが帰ってきたら終了
				log.d("$logCaption: empty.")
				break
			}
			
			// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
			max_id = column.parseRange(result, src).first
			
			adder(src)
			
			val tmpMaxId = max_id
			val tmpSinceId = since_id
			if(tmpMaxId != null && tmpSinceId != null && tmpMaxId <= tmpSinceId) {
				log.d("$logCaption: max_id <= since_id. $tmpMaxId <= $tmpSinceId")
				break
			}
		}
		
		if(sortAllowed) list_tmp?.sortByDescending { it.getOrderId() }
		
		if(bAddGap) addOne(list_tmp, TootGap.mayNull(max_id, since_id))
		
		return result
	}
	
	// since_idを指定してギャップの下から読む
	private fun <T : TimelineItem> readGapTailMisskey(
		logCaption : String,
		sortAllowed : Boolean = true,
		requester : (EntityId?) -> TootApiResult?,
		arrayFinder : (JsonObject) -> JsonArray? = { null },
		listParser : (TootParser, JsonArray) -> ArrayList<T>,
		adder : (ArrayList<T>) -> Unit
	) : TootApiResult? {
		val time_start = SystemClock.elapsedRealtime()
		var result : TootApiResult? = null
		var bAddGap = false
		
		while(true) {
			if(isCancelled) {
				log.d("$logCaption: cancelled.")
				break
			}
			
			if(result != null && SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
				log.d("$logCaption: timeout.")
				bAddGap = true
				break
			}
			
			val r2 = requester(since_id)
			
			val jsonObject = r2?.jsonObject
			if(jsonObject != null) r2.data = arrayFinder(jsonObject)
			
			val jsonArray = r2?.jsonArray
			if(jsonArray == null) {
				log.d("$logCaption: error or cancelled. make gap.")
				
				// 成功データがない場合だけ、今回のエラーを返すようにする
				if(result == null) result = r2
				
				bAddGap = true
				
				break
			}
			
			// 成功した場合はそれを返したい
			result = r2
			
			val src = listParser(parser, jsonArray)
			
			if(src.isEmpty()) {
				// 直前の取得でカラのデータが帰ってきたら終了
				log.d("$logCaption: empty.")
				break
			}
			
			// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
			since_id = column.parseRange(result, src).second
			
			adder(src)
			
			val tmpMaxId = max_id
			val tmpSinceId = since_id
			if(tmpMaxId != null && tmpSinceId != null && tmpMaxId >= tmpSinceId) {
				log.d("$logCaption: max_id >= since_id. $tmpMaxId >= $tmpSinceId")
				break
			}
		}
		
		if(sortAllowed) list_tmp?.sortByDescending { it.getOrderId() }
		
		if(bAddGap) addOneFirst(list_tmp, TootGap.mayNull(max_id, since_id))
		
		return result
	}
	
	// max_id を指定してギャップの上から読む
	private fun <T : TimelineItem> readGapHeadMastodon(
		logCaption : String,
		client : TootApiClient,
		path_base : String,
		sortAllowed : Boolean = true,
		listParser : (TootParser, JsonArray) -> ArrayList<T>,
		adder : (ArrayList<T>) -> Unit
	) : TootApiResult? {
		val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
		val requester : (EntityId?) -> TootApiResult? = {
			client.request(
				StringBuilder().apply {
					append(path_base)
					val list = ArrayList<String>()
					if(it != null) list.add("max_id=$it")
					if(since_id != null) list.add("since_id=$since_id")
					list.forEachIndexed { index, s ->
						append(if(index == 0) delimiter else '&')
						append(s)
					}
				}.toString()
			)
		}
		
		val time_start = SystemClock.elapsedRealtime()
		var result : TootApiResult? = null
		var bAddGap = false
		while(true) {
			if(isCancelled) {
				log.d("$logCaption: cancelled.")
				break
			}
			
			if(result != null && SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
				log.d("$logCaption: timeout.")
				// タイムアウト
				bAddGap = true
				break
			}
			
			if(max_id == null) {
				showToast(context, false, "gap-getConversationSummaryList: missing max_id")
				log.d("$logCaption: missing max_id")
				break
			}
			
			val r2 = requester(max_id)
			
			val jsonArray = r2?.jsonArray
			if(jsonArray == null) {
				log.d("$logCaption: error or cancelled. make gap.")
				
				// 成功データがない場合だけ、今回のエラーを返すようにする
				if(result == null) result = r2
				
				bAddGap = true
				
				break
			}
			
			// 成功した場合はそれを返したい
			result = r2
			
			val src = listParser(parser, jsonArray)
			
			if(src.isEmpty()) {
				// 直前の取得でカラのデータが帰ってきたら終了
				log.d("$logCaption: empty.")
				break
			}
			
			// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
			max_id = column.parseRange(result, src).first
			
			adder(src)
		}
		
		if(sortAllowed) list_tmp?.sortByDescending { it.getOrderId() }
		
		if(bAddGap) addOne(list_tmp, TootGap.mayNull(max_id, since_id))
		
		return result
	}
	
	// since_idを指定してギャップの下から読む
	private fun <T : TimelineItem> readGapTailMastodon(
		logCaption : String,
		client : TootApiClient, path_base : String,
		sortAllowed : Boolean = true,
		listParser : (TootParser, JsonArray) -> ArrayList<T>,
		adder : (ArrayList<T>) -> Unit
	) : TootApiResult? {
		val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
		val requester : (EntityId?) -> TootApiResult? = {
			client.request(
				StringBuilder().apply {
					append(path_base)
					val list = ArrayList<String>()
					if(it != null) list.add("min_id=$it")
					if(max_id != null) list.add("max_id=$max_id")
					list.forEachIndexed { index, s ->
						append(if(index == 0) delimiter else '&')
						append(s)
					}
				}.toString()
			)
		}
		
		val time_start = SystemClock.elapsedRealtime()
		var result : TootApiResult? = null
		var bAddGap = false
		while(true) {
			if(isCancelled) {
				log.d("$logCaption: cancelled.")
				break
			}
			
			if(result != null && SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
				log.d("$logCaption: timeout.")
				bAddGap = true
				break
			}
			
			val r2 = requester(since_id)
			
			val jsonArray = r2?.jsonArray
			if(jsonArray == null) {
				log.d("$logCaption: error or cancelled. make gap.")
				
				// 成功データがない場合だけ、今回のエラーを返すようにする
				if(result == null) result = r2
				
				bAddGap = true
				
				break
			}
			
			// 成功した場合はそれを返したい
			result = r2
			
			val src = listParser(parser, jsonArray)
			
			if(src.isEmpty()) {
				// 直前の取得でカラのデータが帰ってきたら終了
				log.d("$logCaption: empty.")
				break
			}
			
			// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
			since_id = column.parseRange(result, src).second
			
			adder(src)
			
		}
		
		if(sortAllowed) list_tmp?.sortByDescending { it.getOrderId() }
		
		if(bAddGap) addOneFirst(list_tmp, TootGap.mayNull(max_id, since_id))
		
		return result
	}
	
	internal fun getAccountList(
		client : TootApiClient,
		path_base : String,
		misskeyParams : JsonObject? = null,
		misskeyCustomParser : (parser : TootParser, jsonArray : JsonArray) -> ArrayList<TootAccountRef> =
			{ parser, jsonArray -> parser.accountList(jsonArray) },
		misskeyArrayFinder : (jsonObject : JsonObject) -> JsonArray? = { null }
	
	) : TootApiResult? {
		
		@Suppress("NON_EXHAUSTIVE_WHEN")
		when(column.pagingType) {
			ColumnPagingType.Offset,
			ColumnPagingType.Cursor,
			ColumnPagingType.None -> {
				return TootApiResult("can't support gap")
			}
		}
		
		list_tmp = ArrayList()
		
		return if(access_info.isMisskey) {
			val logCaption = "getAccountList.Misskey"
			val params = misskeyParams ?: column.makeMisskeyBaseParameter(parser)
			if(isHead) {
				readGapHeadMisskey(
					logCaption,
					arrayFinder = misskeyArrayFinder,
					listParser = misskeyCustomParser,
					requester = {
						client.request(
							path_base,
							params.putMisskeyUntil(it).toPostRequestBuilder()
						)
					},
					adder = {
						addAll(list_tmp, it)
					},
				)
			} else {
				readGapTailMisskey(
					logCaption,
					arrayFinder = misskeyArrayFinder,
					listParser = misskeyCustomParser,
					requester = {
						client.request(
							path_base,
							params.putMisskeySince(it).toPostRequestBuilder()
						)
					},
					adder = { addAll(list_tmp, it) },
				)
			}
		} else {
			val logCaption = "getAccountList.Mastodon"
			
			if(isHead) {
				readGapHeadMastodon(
					logCaption,
					client, path_base,
					listParser = misskeyCustomParser,
					
					adder = { addAll(list_tmp, it) },
				)
			} else {
				readGapTailMastodon(
					logCaption,
					client, path_base,
					listParser = misskeyCustomParser,
					adder = { addAll(list_tmp, it) },
				)
			}
		}
	}
	
	internal fun getReportList(
		client : TootApiClient,
		path_base : String,
		listParser : (parser : TootParser, jsonArray : JsonArray) -> ArrayList<TootReport> =
			{ _, jsonArray -> parseList(::TootReport, jsonArray) }
	) : TootApiResult? {
		
		list_tmp = ArrayList()
		
		val adder : (ArrayList<TootReport>) -> Unit = { addAll(list_tmp, it) }
		
		return if(access_info.isMisskey) {
			val logCaption = "getReportList.Misskey"
			val params = column.makeMisskeyBaseParameter(parser)
			if(isHead) {
				readGapHeadMisskey(
					logCaption,
					// arrayFinder = misskeyArrayFinder,
					listParser = listParser,
					requester = {
						client.request(
							path_base,
							params.putMisskeyUntil(it).toPostRequestBuilder()
						)
					},
					adder = adder
				)
			} else {
				readGapTailMisskey(
					logCaption,
					// arrayFinder = misskeyArrayFinder,
					listParser = listParser,
					requester = {
						client.request(
							path_base,
							params.putMisskeySince(it).toPostRequestBuilder()
						)
					},
					adder = adder
				)
			}
		} else {
			val logCaption = "getReportList.Mastodon"
			if(isHead) {
				readGapHeadMastodon(
					logCaption,
					client,
					path_base,
					sortAllowed = false,
					listParser = listParser,
					adder = adder
				)
			} else {
				readGapTailMastodon(
					logCaption,
					client,
					path_base,
					sortAllowed = false,
					listParser = listParser,
					adder = adder
				)
			}
		}
	}
	
	internal fun getNotificationList(
		client : TootApiClient,
		fromAcct : String? = null,
		path_base : String = column.makeNotificationUrl(client, fromAcct),
		listParser : (parser : TootParser, jsonArray : JsonArray) -> ArrayList<TootNotification> =
			{ parser, jsonArray -> parser.notificationList(jsonArray) },
		
		adder : (ArrayList<TootNotification>) -> Unit =
			{ addWithFilterNotification(list_tmp, it) }
	
	) : TootApiResult? {
		
		list_tmp = ArrayList()
		
		return if(isMisskey) {
			val logCaption = "getNotificationList.Misskey"
			val params = column.makeMisskeyBaseParameter(parser)
				.addMisskeyNotificationFilter(column)
			if(isHead) {
				readGapHeadMisskey(
					logCaption,
					// arrayFinder = misskeyArrayFinder,
					listParser = listParser,
					requester = {
						client.request(
							path_base,
							params.putMisskeyUntil(it).toPostRequestBuilder()
						)
					},
					adder = adder
				)
			} else {
				readGapTailMisskey(
					logCaption,
					// arrayFinder = misskeyArrayFinder,
					listParser = listParser,
					requester = {
						client.request(
							path_base,
							params.putMisskeySince(it).toPostRequestBuilder()
						)
					},
					adder = adder
				)
			}
			
		} else {
			val logCaption = "getNotificationList.Mastodon"
			if(isHead) {
				readGapHeadMastodon(
					logCaption,
					client,
					path_base,
					sortAllowed = false,
					listParser = listParser,
					
					adder = adder
				)
			} else {
				readGapTailMastodon(
					logCaption,
					client,
					path_base,
					sortAllowed = false,
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
	
	internal fun getStatusList(
		client : TootApiClient,
		path_base : String?,
		misskeyParams : JsonObject? = null,
		listParser : (parser : TootParser, jsonArray : JsonArray) -> ArrayList<TootStatus> =
			{ parser, jsonArray -> parser.statusList(jsonArray) },
		adder : (ArrayList<TootStatus>) -> Unit =
			{ addWithFilterStatus(list_tmp, it) },
	) : TootApiResult? {
		
		path_base ?: return null // cancelled.
		
		list_tmp = ArrayList()
		
		return if(access_info.isMisskey) {
			val logCaption = "getStatusList.Misskey"
			val params = misskeyParams ?: column.makeMisskeyTimelineParameter(parser)
			
			if(isHead) {
				readGapHeadMisskey(
					logCaption,
					// arrayFinder = misskeyArrayFinder,
					listParser = listParser,
					requester = {
						client.request(
							path_base,
							params.putMisskeyUntil(it).toPostRequestBuilder()
						)
					},
					adder = adder
				)
			} else {
				readGapTailMisskey(
					logCaption,
					// arrayFinder = misskeyArrayFinder,
					listParser = listParser,
					requester = {
						client.request(
							path_base,
							params.putMisskeySince(it).toPostRequestBuilder()
						)
						
					},
					adder = adder
				)
			}
		} else {
			val logCaption = "getStatusList.Mastodon"
			if(isHead) {
				readGapHeadMastodon(
					logCaption,
					client,
					path_base,
					sortAllowed = false,
					listParser = listParser,
					adder = adder
				)
			} else {
				readGapTailMastodon(
					logCaption,
					client,
					path_base,
					sortAllowed = false,
					listParser = listParser,
					adder = adder
				)
			}
		}
	}
	
	internal fun getConversationSummaryList(
		client : TootApiClient,
		path_base : String,
		misskeyParams : JsonObject? = null,
		misskeyCustomParser : (TootParser, JsonArray) -> ArrayList<TootConversationSummary> =
			{ parser, jsonArray -> parseList(::TootConversationSummary, parser, jsonArray) }
	) : TootApiResult? {
		
		val adder : (ArrayList<TootConversationSummary>) -> Unit = {
			addWithFilterConversationSummary(list_tmp, it)
		}
		
		list_tmp = ArrayList()
		
		return if(access_info.isMisskey) {
			val logCaption = "getConversationSummaryList.Misskey"
			val params = misskeyParams ?: column.makeMisskeyTimelineParameter(parser)
			if(isHead) {
				readGapHeadMisskey(
					logCaption,
					listParser = misskeyCustomParser,
					requester = {
						client.request(
							path_base,
							params.putMisskeyUntil(it).toPostRequestBuilder()
						)
					},
					adder = adder
				)
			} else {
				readGapTailMisskey(
					logCaption,
					listParser = misskeyCustomParser,
					requester = {
						client.request(
							path_base,
							params.putMisskeySince(it).toPostRequestBuilder()
						)
					},
					adder = adder
				)
			}
		} else {
			val logCaption = "getConversationSummaryList.Mastodon"
			if(isHead) {
				readGapHeadMastodon(
					logCaption,
					client,
					path_base,
					sortAllowed = false,
					listParser = misskeyCustomParser,
					adder = adder
				)
			} else {
				readGapTailMastodon(
					logCaption,
					client,
					path_base,
					sortAllowed = false,
					listParser = misskeyCustomParser,
					adder = adder
				)
			}
		}
	}
	
	fun getSearchGap(client : TootApiClient) : TootApiResult? {
		if(gap !is TootSearchGap) return null
		
		if(isMisskey) {
			
			val countStatuses : (TimelineItem, EntityId?) -> EntityId? = { it, minId ->
				if(it is TootStatus && (minId == null || it.id < minId)) it.id else minId
			}
			
			val (_, counter) = when(gap.type) {
				TootSearchGap.SearchType.Status -> Pair("statuses", countStatuses)
				
				//TootSearchGap.SearchType.Hashtag -> Pair("hashtags", countTag)
				//TootSearchGap.SearchType.Account -> Pair("accounts", countAccount)
				else -> return TootApiResult("paging for ${gap.type} is not yet supported")
			}
			var minId : EntityId? = null
			for(it in column.list_data) minId = counter(it, minId)
			
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
			if(jsonArray != null) {
				val src = parser.statusList(jsonArray)
				list_tmp = addWithFilterStatus(list_tmp, src)
				if(src.isNotEmpty()) {
					addOne(list_tmp, TootSearchGap(TootSearchGap.SearchType.Status))
				}
			}
			return result
			
		} else {
			var offset = 0
			
			val countAccounts : (TimelineItem) -> Unit =
				{ if(it is TootAccountRef) ++ offset }
			val countTags : (TimelineItem) -> Unit =
				{ if(it is TootTag) ++ offset }
			val countStatuses : (TimelineItem) -> Unit =
				{ if(it is TootStatus) ++ offset }
			
			val (type, counter) = when(gap.type) {
				TootSearchGap.SearchType.Account -> Pair("accounts", countAccounts)
				TootSearchGap.SearchType.Hashtag -> Pair("hashtags", countTags)
				TootSearchGap.SearchType.Status -> Pair("statuses", countStatuses)
			}
			column.list_data.forEach { counter(it) }
			
			// https://mastodon2.juggler.jp/api/v2/search?q=gargron&type=accounts&offset=5
			var query = "q=${column.search_query.encodePercent()}&type=$type&offset=$offset"
			if(column.search_resolve) query += "&resolve=1"
			
			val (apiResult, searchResult) = client.requestMastodonSearch(parser, query)
			if(searchResult != null) {
				list_tmp = ArrayList()
				addAll(list_tmp, searchResult.hashtags)
				addAll(list_tmp, searchResult.accounts)
				addAll(list_tmp, searchResult.statuses)
				if(list_tmp?.isNotEmpty() == true) {
					addOne(list_tmp, TootSearchGap(gap.type))
				}
			}
			return apiResult
			
		}
	}
}
