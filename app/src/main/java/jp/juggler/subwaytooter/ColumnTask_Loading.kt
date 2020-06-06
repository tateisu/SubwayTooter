package jp.juggler.subwaytooter

import android.os.SystemClock
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.table.E2EEAccount
import jp.juggler.subwaytooter.table.E2EEMessage
import jp.juggler.subwaytooter.util.InstanceTicker
import jp.juggler.util.*
import org.matrix.olm.OlmMessage
import java.util.*
import kotlin.collections.ArrayList

class ColumnTask_Loading(
	columnArg : Column
) : ColumnTask(columnArg, ColumnTaskType.LOADING) {
	
	companion object {
		internal val log = LogCategory("CT_Loading")
	}
	
	internal var list_pinned : ArrayList<TimelineItem>? = null
	
	override fun doInBackground() : TootApiResult? {
		ctStarted.set(true)
		
		if(Pref.bpInstanceTicker(pref)) {
			InstanceTicker.load()
		}
		
		val client = TootApiClient(context, callback = object : TootApiCallback {
			override val isApiCancelled : Boolean
				get() = isCancelled || column.is_dispose.get()
			
			override fun publishApiProgress(s : String) {
				runOnMainLooper {
					if(isCancelled) return@runOnMainLooper
					column.task_progress = s
					column.fireShowContent(reason = "loading progress", changeList = ArrayList())
				}
			}
		})
		
		client.account = access_info
		
		try {
			val result = access_info.checkConfirmed(context, client)
			if(result == null || result.error != null) return result
			
			column.keywordFilterTrees = column.encodeFilterTree(column.loadFilter2(client))
			
			if(! access_info.isNA) {
				val (instance, instanceResult) = TootInstance.get(client)
				instance ?: return instanceResult
				if(instance.instanceType == TootInstance.InstanceType.Pixelfed) {
					return TootApiResult("currently Pixelfed instance is not supported.")
				}
			}
			
			return column.type.loading(this, client)
		} catch(ex : Throwable) {
			return TootApiResult(ex.withCaption("loading failed."))
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
		
		column.bInitialLoading = false
		column.lastTask = null
		
		if(result.error != null) {
			column.mInitialLoadingError = "${result.error} ${result.requestInfo}".trim()
		} else {
			column.duplicate_map.clear()
			column.list_data.clear()
			val list_tmp = this.list_tmp
			if(list_tmp != null) {
				val list_pinned = this.list_pinned
				if(list_pinned?.isNotEmpty() == true) {
					val list_new = column.duplicate_map.filterDuplicate(list_pinned)
					column.list_data.addAll(list_new)
				}
				
				val list_new = when(column.type) {
					
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
	// functions that called from ColumnTask.loading lambda.
	
	internal fun getStatusesPinned(client : TootApiClient, path_base : String) {
		val result = client.request(path_base)
		val jsonArray = result?.jsonArray
		if(jsonArray != null) {
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
		log.d("getStatusesPinned: list size=%s", list_pinned?.size ?: - 1)
	}
	
	internal fun getStatusList(
		client : TootApiClient,
		path_base : String?,
		aroundMin : Boolean = false,
		aroundMax : Boolean = false,
		
		misskeyParams : JsonObject? = null,
		misskeyCustomParser : (parser : TootParser, jsonArray : JsonArray) -> ArrayList<TootStatus> =
			{ parser, jsonArray -> parser.statusList(jsonArray) },
		initialUntilDate : Boolean = false,
		useDate : Boolean = isMisskey // お気に入り一覧などでは変更される
	) : TootApiResult? {
		
		path_base ?: return null // cancelled.
		
		column.useDate = useDate
		
		val params = misskeyParams ?: column.makeMisskeyTimelineParameter(parser)
		
		val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
		
		val time_start = SystemClock.elapsedRealtime()
		
		// 初回の取得
		val result = when {
			isMisskey -> {
				if(initialUntilDate) {
					params["untilDate"] = System.currentTimeMillis() + (86400000L * 365)
				}
				client.request(path_base, params.toPostRequestBuilder())
			}
			
			aroundMin -> client.request("$path_base&min_id=${column.status_id}")
			aroundMax -> client.request("$path_base&max_id=${column.status_id}")
			else -> client.request(path_base)
		}
		
		var jsonArray = result?.jsonArray
		if(jsonArray != null) {
			
			var src = misskeyCustomParser(parser, jsonArray)
			
			if(list_tmp == null) {
				list_tmp = ArrayList(src.size)
			}
			this.list_tmp = addWithFilterStatus(list_tmp, src)
			
			column.saveRange(bBottom = true, bTop = true, result = result, list = src)
			
			if(aroundMin) {
				while(true) {
					if(client.isApiCancelled) {
						log.d("loading-statuses: cancelled.")
						break
					}
					
					if(! column.isFilterEnabled) {
						log.d("loading-statuses: isFiltered is false.")
						break
					}
					
					if(column.idRecent == null) {
						log.d("loading-statuses: idRecent is empty.")
						break
					}
					
					if((list_tmp?.size ?: 0) >= Column.LOOP_READ_ENOUGH) {
						log.d("loading-statuses: read enough data.")
						break
					}
					
					if(src.isEmpty()) {
						log.d("loading-statuses: previous response is empty.")
						break
					}
					
					if(SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
						log.d("loading-statuses: timeout.")
						break
					}
					
					// フィルタなどが有効な場合は2回目以降の取得
					val result2 = if(isMisskey) {
						client.request(
							path_base,
							params
								.putMisskeyUntil(column.idOld)
								.toPostRequestBuilder()
						)
					} else {
						client.request("$path_base${delimiter}min_id=${column.idRecent}")
					}
					
					jsonArray = result2?.jsonArray
					
					if(jsonArray == null) {
						log.d("loading-statuses: error or cancelled.")
						break
					}
					
					src = misskeyCustomParser(parser, jsonArray)
					
					addWithFilterStatus(list_tmp, src)
					
					if(! column.saveRangeStart(result = result2, list = src)) {
						log.d("loading-statuses: missing range info.")
						break
					}
				}
				
			} else {
				while(true) {
					
					if(client.isApiCancelled) {
						log.d("loading-statuses: cancelled.")
						break
					}
					
					if(! column.isFilterEnabled) {
						log.d("loading-statuses: isFiltered is false.")
						break
					}
					
					if(column.idOld == null) {
						log.d("loading-statuses: idOld is empty.")
						break
					}
					
					if((list_tmp?.size ?: 0) >= Column.LOOP_READ_ENOUGH) {
						log.d("loading-statuses: read enough data.")
						break
					}
					
					if(src.isEmpty()) {
						log.d("loading-statuses: previous response is empty.")
						break
					}
					
					if(SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
						log.d("loading-statuses: timeout.")
						break
					}
					
					// フィルタなどが有効な場合は2回目以降の取得
					val result2 = if(isMisskey) {
						client.request(
							path_base,
							params
								.putMisskeyUntil(column.idOld)
								.toPostRequestBuilder()
						)
					} else {
						client.request("$path_base${delimiter}max_id=${column.idOld}")
					}
					
					jsonArray = result2?.jsonArray
					
					if(jsonArray == null) {
						log.d("loading-statuses: error or cancelled.")
						break
					}
					
					src = misskeyCustomParser(parser, jsonArray)
					
					addWithFilterStatus(list_tmp, src)
					
					if(! column.saveRangeEnd(result = result2, list = src)) {
						log.d("loading-statuses: missing range info.")
						break
					}
				}
			}
		}
		return result
	}
	
	private fun getConversationSummary(
		client : TootApiClient,
		path_base : String,
		aroundMin : Boolean = false,
		aroundMax : Boolean = false,
		misskeyParams : JsonObject? = null,
		misskeyCustomParser : (parser : TootParser, jsonArray : JsonArray) -> ArrayList<TootConversationSummary> =
			{ parser, jsonArray -> parseList(::TootConversationSummary, parser, jsonArray) }
	) : TootApiResult? {
		
		val params = misskeyParams ?: column.makeMisskeyTimelineParameter(parser)
		
		val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
		
		val time_start = SystemClock.elapsedRealtime()
		
		// 初回の取得
		val result = when {
			isMisskey -> client.request(path_base, params.toPostRequestBuilder())
			aroundMin -> client.request("$path_base&min_id=${column.status_id}")
			aroundMax -> client.request("$path_base&max_id=${column.status_id}")
			
			else -> client.request(path_base)
		}
		
		var jsonArray = result?.jsonArray
		if(jsonArray != null) {
			
			var src = misskeyCustomParser(parser, jsonArray)
			
			if(list_tmp == null) {
				list_tmp = ArrayList(src.size)
			}
			this.list_tmp = addWithFilterConversationSummary(list_tmp, src)
			
			column.saveRange(bBottom = true, bTop = true, result = result, list = src)
			
			if(aroundMin) {
				while(true) {
					if(client.isApiCancelled) {
						log.d("loading-ConversationSummary: cancelled.")
						break
					}
					
					if(! column.isFilterEnabled) {
						log.d("loading-ConversationSummary: isFiltered is false.")
						break
					}
					
					if(column.idRecent == null) {
						log.d("loading-ConversationSummary: idRecent is empty.")
						break
					}
					
					if((list_tmp?.size ?: 0) >= Column.LOOP_READ_ENOUGH) {
						log.d("loading-ConversationSummary: read enough data.")
						break
					}
					
					if(src.isEmpty()) {
						log.d("loading-ConversationSummary: previous response is empty.")
						break
					}
					
					if(SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
						log.d("loading-ConversationSummary: timeout.")
						break
					}
					
					// フィルタなどが有効な場合は2回目以降の取得
					val result2 = if(isMisskey) {
						client.request(
							path_base,
							params
								.putMisskeyUntil(column.idOld)
								.toPostRequestBuilder()
						)
					} else {
						client.request("$path_base${delimiter}min_id=${column.idRecent}")
					}
					
					jsonArray = result2?.jsonArray
					
					if(jsonArray == null) {
						log.d("loading-ConversationSummary: error or cancelled.")
						break
					}
					
					src = misskeyCustomParser(parser, jsonArray)
					
					addWithFilterConversationSummary(list_tmp, src)
					
					if(! column.saveRangeStart(result = result2, list = src)) {
						log.d("loading-ConversationSummary: missing range info.")
						break
					}
				}
				
			} else {
				while(true) {
					
					if(client.isApiCancelled) {
						log.d("loading-ConversationSummary: cancelled.")
						break
					}
					
					if(! column.isFilterEnabled) {
						log.d("loading-ConversationSummary: isFiltered is false.")
						break
					}
					
					if(column.idOld == null) {
						log.d("loading-ConversationSummary: idOld is empty.")
						break
					}
					
					if((list_tmp?.size ?: 0) >= Column.LOOP_READ_ENOUGH) {
						log.d("loading-ConversationSummary: read enough data.")
						break
					}
					
					if(src.isEmpty()) {
						log.d("loading-ConversationSummary: previous response is empty.")
						break
					}
					
					if(SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
						log.d("loading-ConversationSummary: timeout.")
						break
					}
					
					// フィルタなどが有効な場合は2回目以降の取得
					val result2 = if(isMisskey) {
						client.request(
							path_base,
							params
								.putMisskeyUntil(column.idOld)
								.toPostRequestBuilder()
						)
					} else {
						val path = "$path_base${delimiter}max_id=${column.idOld}"
						client.request(path)
					}
					
					jsonArray = result2?.jsonArray
					
					if(jsonArray == null) {
						log.d("loading-ConversationSummary: error or cancelled.")
						break
					}
					
					src = misskeyCustomParser(parser, jsonArray)
					
					addWithFilterConversationSummary(list_tmp, src)
					
					if(! column.saveRangeEnd(result = result2, list = src)) {
						log.d("loading-ConversationSummary: missing range info.")
						break
					}
				}
			}
		}
		return result
	}
	
	internal fun getAccountList(
		client : TootApiClient,
		path_base : String,
		emptyMessage : String? = null,
		misskeyParams : JsonObject? = null,
		misskeyArrayFinder : (JsonObject) -> JsonArray? = { null },
		misskeyCustomParser : (parser : TootParser, jsonArray : JsonArray) -> ArrayList<TootAccountRef> =
			{ parser, jsonArray -> parser.accountList(jsonArray) }
	) : TootApiResult? {
		
		val result = if(misskeyParams != null) {
			client.request(path_base, misskeyParams.toPostRequestBuilder())
		} else {
			client.request(path_base)
		}
		
		if(result != null && result.error == null) {
			val jsonObject = result.jsonObject
			if(jsonObject != null) {
				if(column.pagingType == ColumnPagingType.Cursor) {
					column.idOld = EntityId.mayNull(jsonObject.string("next"))
				}
				result.data = misskeyArrayFinder(jsonObject)
			}
			val jsonArray = result.jsonArray
				?: return result.setError("missing JSON data.")
			
			val src = misskeyCustomParser(parser, jsonArray)
			when(column.pagingType) {
				ColumnPagingType.Default -> {
					column.saveRange(bBottom = true, bTop = true, result = result, list = src)
				}
				
				ColumnPagingType.Offset -> {
					column.offsetNext += src.size
				}
				
				else -> {
				}
			}
			
			val tmp = ArrayList<TimelineItem>()
			
			if(emptyMessage != null) {
				// フォロー/フォロワー一覧には警告の表示が必要だった
				val who = column.who_account?.get()
				if(! access_info.isMe(who)) {
					if(who != null && access_info.isRemoteUser(who)) tmp.add(
						TootMessageHolder(
							context.getString(R.string.follow_follower_list_may_restrict)
						)
					)
					
					if(src.isEmpty()) {
						tmp.add(TootMessageHolder(emptyMessage))
						
					}
				}
			}
			tmp.addAll(src)
			list_tmp = addAll(null, tmp)
		}
		return result
	}
	
	internal fun parseFilterList(
		client : TootApiClient,
		path_base : String
	) : TootApiResult? {
		val result = client.request(path_base)
		if(result != null) {
			val src = TootFilter.parseList(result.jsonArray)
			this.list_tmp = addAll(null, src)
		}
		return result
	}
	
	internal fun getDomainList(
		client : TootApiClient,
		path_base : String
	) : TootApiResult? {
		val result = client.request(path_base)
		if(result != null) {
			val src = TootDomainBlock.parseList(result.jsonArray)
			column.saveRange(bBottom = true, bTop = true, result = result, list = src)
			this.list_tmp = addAll(null, src)
		}
		return result
	}
	
	internal fun getReportList(client : TootApiClient, path_base : String) : TootApiResult? {
		val result = client.request(path_base)
		if(result != null) {
			val src = parseList(::TootReport, result.jsonArray)
			column.saveRange(bBottom = true, bTop = true, result = result, list = src)
			list_tmp = addAll(null, src)
		}
		return result
	}
	
	internal fun parseListList(
		client : TootApiClient,
		path_base : String,
		misskeyParams : JsonObject? = null
	) : TootApiResult? {
		val result = if(misskeyParams != null) {
			client.request(path_base, misskeyParams.toPostRequestBuilder())
		} else {
			client.request(path_base)
		}
		if(result != null) {
			val src = parseList(::TootList, parser, result.jsonArray)
			src.sort()
			column.saveRange(bBottom = true, bTop = true, result = result, list = src)
			this.list_tmp = addAll(null, src)
		}
		return result
	}
	
	internal fun parseAntennaList(
		client : TootApiClient,
		path_base : String,
		misskeyParams : JsonObject? = null
	) : TootApiResult? {
		val result = if(misskeyParams != null) {
			client.request(path_base, misskeyParams.toPostRequestBuilder())
		} else {
			client.request(path_base)
		}
		if(result != null) {
			val src = parseList(::MisskeyAntenna, parser, result.jsonArray)
			column.saveRange(bBottom = true, bTop = true, result = result, list = src)
			this.list_tmp = addAll(null, src)
		}
		return result
	}
	
	internal fun getNotificationList(
		client : TootApiClient,
		fromAcct : String? = null
	) : TootApiResult? {
		
		val params = column
			.makeMisskeyBaseParameter(parser)
			.addMisskeyNotificationFilter()
		
		val path_base = column.makeNotificationUrl(client, fromAcct)
		
		val time_start = SystemClock.elapsedRealtime()
		val result = if(isMisskey) {
			client.request(path_base, params.toPostRequestBuilder())
		} else {
			client.request(path_base)
		}
		var jsonArray = result?.jsonArray
		if(jsonArray != null) {
			var src = parser.notificationList(jsonArray)
			column.saveRange(bBottom = true, bTop = true, result = result, list = src)
			this.list_tmp = addWithFilterNotification(null, src)
			//
			if(src.isNotEmpty()) {
				PollingWorker.injectData(context, access_info, src)
			}
			//
			val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
			while(true) {
				if(client.isApiCancelled) {
					log.d("loading-notifications: cancelled.")
					break
				}
				if(! column.isFilterEnabled) {
					log.d("loading-notifications: isFiltered is false.")
					break
				}
				if(column.idOld == null) {
					log.d("loading-notifications: max_id is empty.")
					break
				}
				if((list_tmp?.size ?: 0) >= Column.LOOP_READ_ENOUGH) {
					log.d("loading-notifications: read enough data.")
					break
				}
				if(src.isEmpty()) {
					log.d("loading-notifications: previous response is empty.")
					break
				}
				if(SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
					log.d("loading-notifications: timeout.")
					break
				}
				
				val result2 = if(isMisskey) {
					client.request(
						path_base,
						params
							.putMisskeyUntil(column.idOld)
							.toPostRequestBuilder()
					)
				} else {
					client.request("$path_base${delimiter}max_id=${column.idOld}")
				}
				
				jsonArray = result2?.jsonArray
				if(jsonArray == null) {
					log.d("loading-notifications: error or cancelled.")
					break
				}
				
				src = parser.notificationList(jsonArray)
				
				addWithFilterNotification(list_tmp, src)
				
				if(! column.saveRangeEnd(result2, src)) {
					log.d("loading-notifications: missing range info.")
					break
				}
			}
		}
		return result
	}
	
	internal fun getPublicAroundStatuses(
		client : TootApiClient,
		url : String
	) : TootApiResult? {
		// (Mastodonのみ対応)
		
		val (instance, instanceResult) = TootInstance.get(client)
		instance ?: return instanceResult
		
		// ステータスIDに該当するトゥート
		// タンスをまたいだりすると存在しないかもしれないが、エラーは出さない
		var result : TootApiResult? =
			client.request(String.format(Locale.JAPAN, Column.PATH_STATUSES, column.status_id))
		val target_status = parser.status(result?.jsonObject)
		if(target_status != null) {
			list_tmp = addOne(list_tmp, target_status)
		}
		
		column.idOld = null
		column.idRecent = null
		
		var bInstanceTooOld = false
		if(instance.versionGE(TootInstance.VERSION_2_6_0)) {
			// 指定より新しいトゥート
			result = getStatusList(client, url, aroundMin = true)
			if(result == null || result.error != null) return result
		} else {
			bInstanceTooOld = true
		}
		
		// 指定位置より古いトゥート
		result = getStatusList(client, url, aroundMax = true)
		if(result == null || result.error != null) return result
		
		list_tmp?.sortBy { it.getOrderId() }
		list_tmp?.reverse()
		if(bInstanceTooOld) {
			list_tmp?.add(
				0,
				TootMessageHolder(context.getString(R.string.around_toot_limitation_warning))
			)
		}
		
		return result
		
	}
	
	internal fun getAccountAroundStatuses(client : TootApiClient) : TootApiResult? {
		// (Mastodonのみ対応)
		
		val (instance, instanceResult) = TootInstance.get(client)
		instance ?: return instanceResult
		
		// ステータスIDに該当するトゥート
		// タンスをまたいだりすると存在しないかもしれない
		var result : TootApiResult? =
			client.request(String.format(Locale.JAPAN, Column.PATH_STATUSES, column.status_id))
		val target_status = parser.status(result?.jsonObject) ?: return result
		list_tmp = addOne(list_tmp, target_status)
		
		// ↑のトゥートのアカウントのID
		column.profile_id = target_status.account.id
		
		val path = column.makeProfileStatusesUrl(column.profile_id)
		column.idOld = null
		column.idRecent = null
		
		var bInstanceTooOld = false
		if(instance.versionGE(TootInstance.VERSION_2_6_0)) {
			// 指定より新しいトゥート
			result = getStatusList(client, path, aroundMin = true)
			if(result == null || result.error != null) return result
		} else {
			bInstanceTooOld = true
		}
		
		// 指定位置より古いトゥート
		result = getStatusList(client, path, aroundMax = true)
		if(result == null || result.error != null) return result
		
		list_tmp?.sortBy { it.getOrderId() }
		list_tmp?.reverse()
		if(bInstanceTooOld) {
			list_tmp?.add(
				0,
				TootMessageHolder(context.getString(R.string.around_toot_limitation_warning))
			)
		}
		
		return result
		
	}
	
	internal fun getScheduledStatuses(client : TootApiClient) : TootApiResult? {
		val result = client.request(Column.PATH_SCHEDULED_STATUSES)
		val src = parseList(::TootScheduled, parser, result?.jsonArray)
		list_tmp = addAll(list_tmp, src)
		
		column.saveRange(bBottom = true, bTop = true, result = result, list = src)
		
		return result
	}
	
	internal fun getConversation(client : TootApiClient) : TootApiResult? {
		return if(isMisskey) {
			// 指定された発言そのもの
			val queryParams = column.makeMisskeyBaseParameter(parser).apply {
				put("noteId", column.status_id)
			}
			
			var result = client.request(
				"/api/notes/show"
				, queryParams.toPostRequestBuilder()
			)
			val jsonObject = result?.jsonObject ?: return result
			val target_status = parser.status(jsonObject)
				?: return TootApiResult("TootStatus parse failed.")
			target_status.conversation_main = true
			
			// 祖先
			val list_asc = java.util.ArrayList<TootStatus>()
			while(true) {
				if(client.isApiCancelled) return null
				queryParams["offset"] = list_asc.size
				result = client.request(
					"/api/notes/conversation"
					, queryParams.toPostRequestBuilder()
				)
				val jsonArray = result?.jsonArray ?: return result
				val src = parser.statusList(jsonArray)
				if(src.isEmpty()) break
				list_asc.addAll(src)
			}
			
			// 直接の子リプライ。(子孫をたどることまではしない)
			val list_desc = java.util.ArrayList<TootStatus>()
			val idSet = HashSet<EntityId>()
			var untilId : EntityId? = null
			
			while(true) {
				if(client.isApiCancelled) return null
				
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
					"/api/notes/replies"
					, queryParams.toPostRequestBuilder()
				)
				val jsonArray = result?.jsonArray ?: return result
				val src = parser.statusList(jsonArray)
				untilId = null
				for(status in src) {
					if(idSet.contains(status.id)) continue
					idSet.add(status.id)
					list_desc.add(status)
					untilId = status.id
				}
				if(untilId == null) break
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
			if(conversation_context != null) {
				
				this.list_tmp = java.util.ArrayList(
					1
						+ (conversation_context.ancestors?.size ?: 0)
						+ (conversation_context.descendants?.size ?: 0)
				)
				//
				if(conversation_context.ancestors != null)
					addWithFilterStatus(
						this.list_tmp,
						conversation_context.ancestors
					)
				//
				addOne(list_tmp, target_status)
				//
				if(conversation_context.descendants != null)
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
	
	internal fun getSearch(client : TootApiClient) : TootApiResult? {
		return if(isMisskey) {
			var result : TootApiResult? = TootApiResult()
			val parser = TootParser(context, access_info)
			
			list_tmp = java.util.ArrayList()
			
			val queryAccount = column.search_query.trim().replace("^@".toRegex(), "")
			if(queryAccount.isNotEmpty()) {
				result = client.request(
					"/api/users/search",
					access_info.putMisskeyApiToken().apply {
						put("query", queryAccount)
						put("localOnly", ! column.search_resolve)
					}.toPostRequestBuilder()
				)
				val jsonArray = result?.jsonArray
				if(jsonArray != null) {
					val src =
						TootParser(context, access_info).accountList(jsonArray)
					list_tmp = addAll(list_tmp, src)
				}
			}
			
			val queryTag = column.search_query.trim().replace("^#".toRegex(), "")
			if(queryTag.isNotEmpty()) {
				result = client.request(
					"/api/hashtags/search",
					access_info.putMisskeyApiToken().apply {
						put("query", queryTag)
					}.toPostRequestBuilder()
				)
				val jsonArray = result?.jsonArray
				if(jsonArray != null) {
					val src = TootTag.parseList(parser, jsonArray)
					list_tmp = addAll(list_tmp, src)
				}
			}
			if(column.search_query.isNotEmpty()) {
				result = client.request(
					"/api/notes/search",
					access_info.putMisskeyApiToken().apply {
						put("query", column.search_query)
					}
						.toPostRequestBuilder()
				)
				val jsonArray = result?.jsonArray
				if(jsonArray != null) {
					val src = parser.statusList(jsonArray)
					list_tmp = addWithFilterStatus(list_tmp, src)
					if(src.isNotEmpty()) {
						val (ti, _) = TootInstance.get(client)
						if(ti?.versionGE(TootInstance.MISSKEY_VERSION_12) == true) {
							addOne(list_tmp, TootSearchGap(TootSearchGap.SearchType.Status))
						}
					}
				}
			}
			
			// 検索機能が無効だとsearch_query が 400を返すが、他のAPIがデータを返したら成功したことにする
			if(list_tmp?.isNotEmpty() == true) {
				TootApiResult()
			} else {
				result
			}
		} else {
			if(access_info.isPseudo) {
				// 1.5.0rc からマストドンの検索APIは認証を要求するようになった
				return TootApiResult(context.getString(R.string.search_is_not_available_on_pseudo_account))
			}
			
			val (instance, instanceResult) = TootInstance.get(client)
			instance ?: return instanceResult
			
			var query = "q=${column.search_query.encodePercent()}"
			if(column.search_resolve) query += "&resolve=1"
			
			val (apiResult, searchResult) = client.requestMastodonSearch(parser, query)
			if(searchResult != null) {
				list_tmp = java.util.ArrayList()
				addAll(list_tmp, searchResult.hashtags)
				if(searchResult.searchApiVersion >= 2 && searchResult.hashtags.isNotEmpty()) {
					addOne(list_tmp, TootSearchGap(TootSearchGap.SearchType.Hashtag))
				}
				addAll(list_tmp, searchResult.accounts)
				if(searchResult.searchApiVersion >= 2 && searchResult.accounts.isNotEmpty()) {
					addOne(list_tmp, TootSearchGap(TootSearchGap.SearchType.Account))
				}
				addAll(list_tmp, searchResult.statuses)
				if(searchResult.searchApiVersion >= 2 && searchResult.statuses.isNotEmpty()) {
					addOne(list_tmp, TootSearchGap(TootSearchGap.SearchType.Status))
				}
			}
			return apiResult
			
		}
		
	}
	
	internal fun getDirectMessages(client : TootApiClient) : TootApiResult? {
		column.useConversationSummarys = false
		if(! column.use_old_api) {
			
			// try 2.6.0 new API https://github.com/tootsuite/mastodon/pull/8832
			val result = getConversationSummary(client, Column.PATH_DIRECT_MESSAGES2)
			
			when {
				// cancelled
				result == null -> return null
				
				//  not error
				result.error.isNullOrBlank() -> {
					column.useConversationSummarys = true
					return result
				}
				
				// else fall thru
			}
		}
		
		// fallback to old api
		return getStatusList(client, Column.PATH_DIRECT_MESSAGES)
	}
	
	private val reMaxId = """\bmax_id=([^,?&]+)""".toRegex()
	
	internal fun getE2EEMessages(client : TootApiClient) : TootApiResult? {
		
		// E2EEAccount.reset(context)
		
		val (ti, ri) = TootInstance.get(client)
		if(ti == null) return ri
		if(! ti.versionGE(TootInstance.VERSION_3_2)) {
			return TootApiResult("e2ee is not supported on version ${ti.version}")
		}
		
		val e2eeAccount = E2EEAccount.load(context, column.access_info.acct)
		
		e2eeAccount.uploadKeys(context, client, dontSkip = true)
		
		var result = client.request("/api/v1/crypto/encrypted_messages")
		// [{"id":"104279502976060064","account_id":"584","device_id":"ff1f3bb8-4165-4fa2-b73b-3a5939a80749","type":0,"body":"AwogNeZGT0jxHs1XCaV6CGCrwRrxYAzs8kR4DxR6PpVBUU8SIEiM6z2wCMX1a5C1uLSwWI5fuervx2O3IAdRvlNFUmxlGiCpcmGCC36fIlpu0HuTG8Iz19vczHCwUgyTEnnWSUViFSI/AwogLwODru5CKgobcH6ufmRtsi4fl9Xh9Zd3ehQ0OQ/rjG0QACIQladzC6/juNgYR5DFxpRuUZqW9JlZyMBb","digest":"36da54f00e91d8fcf204485e43df6325d696630aefd66979a793f80af8d9c78c","message_franking":"oCNtgnCnSg3J1kahgjn+auyOsCB1/xI4SreDZH7bMIPhrxOWitCpBNgQzuPzi1tBhyTWw3X4RLhd1pFndk+aAXrViw/oH4G8Ad5DOmLGcKYKyndIdsov+3G6iApBD9ca0eyAXoHppBCAUU8x1XPVGAB7ok5DQpckdaVSTO0y2W9euMFSvEM/hSPfzk4+gilJ/km7YB+XX6sjQcvAhHCkyhg/NZsKvmqYzQ/cTqjU9moCHwHf1fSMKZk=--7fqsQXwfHDLcK2z0--C9MNP/JWUC7+KXyNYBzbvg=="},{"id":"104279397293198038","account_id":"584","device_id":"ff1f3bb8-4165-4fa2-b73b-3a5939a80749","type":0,"body":"Awog1jprz095x77LNZ9VcJGVMFjlmUo1nAxrtHqkzt/aQxYSICFsdlhfaX+nRZ8jcBzBZyjzszjXIfIhLU2tbGfjAUgvGiCpcmGCC36fIlpu0HuTG8Iz19vczHCwUgyTEnnWSUViFSI/Awog3EOaBaXIXbwnV+xvnnvr8Auk5KEIovpc61acYRlyPFoQACIQBn/eEt1XTBmU59K6WSgvPuQGR52eb66j","digest":"eea2427ccac90d427ddb069bb12b6ae0da0670439e2b454a4bb3fb397a417fd7","message_franking":"uf47khPd1PbKA1wDOyaIpnoXHHccWG7aciGmOOJ02LaW2aabGwv2zZpgnumHAAbBHkYL2ufF+dJJClSsbC9ariqK1QG1cqExpIAJTgg5+qyKYpBz144O/jBw/EF2QCOEZQN+vaHN4Zn700S9cTYunATEBkAX+nj6t1IapGs3mceQIGFDqM5LUe1APZANKtbTodRseaRapEEls72P6BRE4AG4fXI8GZIpnNT6R0HeiOpY0rs3r/kPlZc=--ZY3EG41pTRKpieLF--dtd8Tj8Ifdgo1/2ax+E8Pg=="}]
		
		var clearId : EntityId? = null
		
		val tmpList = ArrayList<E2EEMessage>()
		try {
			while(true) {
				val jsonArray = result?.jsonArray ?: break
				for(data in jsonArray.mapNotNull { it as? JsonObject }) {
					//	{
					//	"id":"104279397293198038",
					//	"account_id":"584",
					//	"device_id":"ff1f3bb8-4165-4fa2-b73b-3a5939a80749",
					//	"type":0,
					//	"body":"Awog1jprz095x77LNZ9VcJGVMFjlmUo1nAxrtHqkzt/aQxYSICFsdlhfaX+nRZ8jcBzBZyjzszjXIfIhLU2tbGfjAUgvGiCpcmGCC36fIlpu0HuTG8Iz19vczHCwUgyTEnnWSUViFSI/Awog3EOaBaXIXbwnV+xvnnvr8Auk5KEIovpc61acYRlyPFoQACIQBn/eEt1XTBmU59K6WSgvPuQGR52eb66j",
					//	"digest":"eea2427ccac90d427ddb069bb12b6ae0da0670439e2b454a4bb3fb397a417fd7",
					//	"message_franking":"uf47khPd1PbKA1wDOyaIpnoXHHccWG7aciGmOOJ02LaW2aabGwv2zZpgnumHAAbBHkYL2ufF+dJJClSsbC9ariqK1QG1cqExpIAJTgg5+qyKYpBz144O/jBw/EF2QCOEZQN+vaHN4Zn700S9cTYunATEBkAX+nj6t1IapGs3mceQIGFDqM5LUe1APZANKtbTodRseaRapEEls72P6BRE4AG4fXI8GZIpnNT6R0HeiOpY0rs3r/kPlZc=--ZY3EG41pTRKpieLF--dtd8Tj8Ifdgo1/2ax+E8Pg=="
					//	}
					// device_id はsenderのもの
					
					
					val message = e2eeAccount.decrypt(
						context,
						EntityId.mayDefault(data.string("account_id")),
						data.string("device_id")!!,
						OlmMessage().apply {
							mType = data.long("type") ?: 0L
							mCipherText = data.string("body")
						}
					)
					
					val id = EntityId.mayDefault(data.string("id"))
					if(clearId == null || id > clearId) clearId = id
					
					tmpList.add(
						E2EEMessage(
							readerAcct = access_info.acct,
							senderAccountId = EntityId.mayDefault(data.string("account_id")),
							senderDeviceId = data.string("device_id") ?: "",
							messageId = id,
							messageBody = message,
							createdAt = TootStatus.parseTime(data.string("created_at"))
						)
					)
				}
				if(tmpList.isNotEmpty()) {
					log.d("save ${tmpList.size} E2EEMessage")
					E2EEMessage.saveList(tmpList)
					tmpList.clear()
				}
				
				fun String?.findMaxId() : String? {
					this ?: return null
					return reMaxId.find(this)?.groupValues?.get(1)
				}
				
				log.d("pagination old=${result.link_older}, new=${result.link_newer}")
				// pagination old=https://mastodon2.juggler.jp/api/v1/crypto/encrypted_messages?max_id=104281880868582829, new=https://mastodon2.juggler.jp/api/v1/crypto/encrypted_messages?min_id=104281892034004625
				
				val nextId = result.link_older?.findMaxId()
				if(nextId == null) {
					// end page
					// TODO call clear API
					if(clearId != null) {
						client.request(
							"/api/v1/crypto/encrypted_messages/clear",
							jsonObject {
								put("up_to_id", clearId.toString())
							}.toPostRequestBuilder()
						)
					}
					
					break
				}
				result = client.request("/api/v1/crypto/encrypted_messages?max_id=${nextId}")
			}
			
		} finally {
		}
		
		if(result != null && result.error == null) {
			list_tmp = addAll(list_tmp, E2EEMessage.load(access_info.acct, null))
		}
		
		return result
		
		//			column.loadAntennaInfo(client, true)
		//
		//			if(isMisskey) {
		//				getStatusList(
		//					client,
		//					column.makeAntennaTlUrl(),
		//					misskeyParams = column.makeMisskeyTimelineParameter(parser).apply {
		//						put("antennaId", column.profile_id)
		//					},
		//					misskeyCustomParser = misskeyCustomParserAntenna,
		//					useDate = false
		//				)
		//			} else {
		//				getStatusList(client, column.makeAntennaTlUrl())
		//			}
	}
}
