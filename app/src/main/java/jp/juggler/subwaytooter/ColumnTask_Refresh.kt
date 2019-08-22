package jp.juggler.subwaytooter

import android.os.SystemClock
import jp.juggler.subwaytooter.api.TootApiCallback
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.util.ScrollPosition
import jp.juggler.util.*
import org.json.JSONArray
import org.json.JSONObject

class ColumnTask_Refresh(
	columnArg : Column,
	private val bSilent : Boolean,
	internal val bBottom : Boolean,
	internal val posted_status_id : EntityId? = null,
	internal val refresh_after_toot : Int = - 1
) : ColumnTask(
	columnArg,
	if(bBottom) ColumnTaskType.REFRESH_BOTTOM else ColumnTaskType.REFRESH_TOP
) {
	
	companion object {
		internal val log = LogCategory("CT_Refresh")
	}
	
	private var filterUpdated = false
	
	override fun doInBackground(vararg unused : Void) : TootApiResult? {
		ctStarted.set(true)
		
		val client = TootApiClient(context, callback = object : TootApiCallback {
			override val isApiCancelled : Boolean
				get() = isCancelled || column.is_dispose.get()
			
			override fun publishApiProgress(s : String) {
				runOnMainLooper {
					if(isCancelled) return@runOnMainLooper
					column.task_progress = s
					column.fireShowContent(reason = "refresh progress", changeList = ArrayList())
				}
			}
		})
		client.account = access_info
		try {
			
			if(! bBottom) {
				val filterList = column.loadFilter2(client)
				if(filterList != null) {
					column.muted_word2 = column.encodeFilterTree(filterList)
					filterUpdated = true
				}
			}
			
			return column.type.refresh(this, client)
		}catch(ex:Throwable){
			return TootApiResult( ex.withCaption("refresh failed.") )
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
			
			if(filterUpdated) {
				column.checkFiltersForListData(column.muted_word2)
			}
			
			val error = result.error
			if(error != null) {
				column.mRefreshLoadingError = error
				column.mRefreshLoadingErrorTime = SystemClock.elapsedRealtime()
				column.fireShowContent(reason = "refresh error", changeList = ArrayList())
				return
			}
			
			val list_new = column.duplicate_map.filterDuplicate(list_tmp)
			if(list_new.isEmpty()) {
				column.fireShowContent(
					reason = "refresh list_new is empty",
					changeList = ArrayList()
				)
				return
			}
			
			// 事前にスクロール位置を覚えておく
			var sp : ScrollPosition? = null
			val holder = column.viewHolder
			if(holder != null) {
				sp = holder.scrollPosition
			}
			
			
			
			if(bBottom) {
				val changeList = listOf(
					AdapterChange(
						AdapterChangeType.RangeInsert,
						column.list_data.size,
						list_new.size
					)
				)
				column.list_data.addAll(list_new)
				column.fireShowContent(reason = "refresh updated bottom", changeList = changeList)
				
				// 新着が少しだけ見えるようにスクロール位置を移動する
				if(sp != null) {
					holder?.setScrollPosition(sp, 20f)
				}
			} else {
				
				val changeList = ArrayList<AdapterChange>()
				
				if(column.list_data.isNotEmpty() && column.list_data[0] is TootGap) {
					changeList.add(AdapterChange(AdapterChangeType.RangeRemove, 0, 1))
					column.list_data.removeAt(0)
				}
				
				for(o in list_new) {
					if(o is TootStatus) {
						val highlight_sound = o.highlight_sound
						if(highlight_sound != null) {
							App1.sound(highlight_sound)
							break
						}
					}
				}
				
				column.replaceConversationSummary(changeList, list_new, column.list_data)
				
				val added = list_new.size // may 0
				
				// 投稿後のリフレッシュなら当該投稿の位置を探す
				var status_index = - 1
				for(i in 0 until added) {
					val o = list_new[i]
					if(o is TootStatus && o.id == posted_status_id) {
						status_index = i
						break
					}
				}
				
				changeList.add(AdapterChange(AdapterChangeType.RangeInsert, 0, added))
				column.list_data.addAll(0, list_new)
				column.fireShowContent(reason = "refresh updated head", changeList = changeList)
				
				if(status_index >= 0 && refresh_after_toot == Pref.RAT_REFRESH_SCROLL) {
					// 投稿後にその投稿にスクロールする
					if(holder != null) {
						holder.setScrollPosition(
							ScrollPosition(column.toAdapterIndex(status_index)),
							0f
						)
					} else {
						column.scroll_save = ScrollPosition(column.toAdapterIndex(status_index))
					}
				} else {
					//
					val scroll_save = column.scroll_save
					when {
						// ViewHolderがある場合は増加件数分+deltaの位置にスクロールする
						sp != null -> {
							sp.adapterIndex += added
							val delta = if(bSilent) 0f else - 20f
							holder?.setScrollPosition(sp, delta)
						}
						// ViewHolderがなくて保存中の位置がある場合、増加件数分ずらす。deltaは難しいので反映しない
						scroll_save != null -> scroll_save.adapterIndex += added
						// 保存中の位置がない場合、保存中の位置を新しく作る
						else -> column.scroll_save =
							ScrollPosition(column.toAdapterIndex(added))
					}
				}
			}
			
			column.updateMisskeyCapture()
			
		} finally {
			column.fireShowColumnStatus()
			
			if(! bBottom) {
				column.bRefreshingTop = false
				column.resumeStreaming(false)
			}
		}
	}
	
	internal fun getAccountList(
		client : TootApiClient,
		path_base : String,
		misskeyParams : JSONObject? = null,
		misskeyArrayFinder : (JSONObject) -> JSONArray? = { null },
		misskeyCustomParser : (parser : TootParser, jsonArray : JSONArray) -> ArrayList<TootAccountRef> =
			{ parser, jsonArray -> parser.accountList(jsonArray) }
	) : TootApiResult? {
		
		@Suppress("NON_EXHAUSTIVE_WHEN")
		when(bBottom) {
			false -> when(column.pagingType) {
				ColumnPagingType.Cursor,
				ColumnPagingType.None,
				ColumnPagingType.Offset -> {
					return TootApiResult("can't refresh top.")
				}
			}
			true -> when(column.pagingType) {
				ColumnPagingType.Cursor -> if(column.idOld == null) {
					return TootApiResult(context.getString(R.string.end_of_list))
				}
				
				ColumnPagingType.None -> {
					return TootApiResult(context.getString(R.string.end_of_list))
				}
			}
		}
		
		val params = misskeyParams ?: column.makeMisskeyBaseParameter(parser)
		val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
		
		val last_since_id = column.idRecent
		
		val time_start = SystemClock.elapsedRealtime()
		
		var result = if(isMisskey) {
			client.request(
				path_base,
				when(column.pagingType) {
					ColumnPagingType.Default -> params.addRangeMisskey(bBottom)
					ColumnPagingType.Offset -> params.put("offset", column.offsetNext)
					ColumnPagingType.Cursor -> params.put("cursor", column.idOld)
					else -> params
				}
					.toPostRequestBuilder()
			)
		} else {
			client.request(column.addRange(bBottom, path_base))
		}
		val firstResult = result
		
		var jsonObject = result?.jsonObject
		if(jsonObject != null) {
			if(column.pagingType == ColumnPagingType.Cursor) {
				column.idOld = EntityId.mayNull(jsonObject.parseString("next"))
			}
			result !!.data = misskeyArrayFinder(jsonObject)
		}
		
		var array = result?.jsonArray
		if(array != null) {
			
			var src = misskeyCustomParser(parser, array)
			@Suppress("NON_EXHAUSTIVE_WHEN")
			when(column.pagingType) {
				ColumnPagingType.Default -> {
					column.saveRange(bBottom, ! bBottom, firstResult, src)
				}
				
				ColumnPagingType.Offset -> {
					column.offsetNext += src.size
				}
			}
			list_tmp = addAll(null, src)
			
			if(! bBottom) {
				
				if(isMisskey) {
					var bHeadGap = false
					
					// misskeyの場合、sinceIdを指定したら未読範囲の古い方から読んでしまう
					// 最新まで読めるとは限らない
					// 先頭にギャップを置くかもしれない
					while(true) {
						
						if(isCancelled) {
							log.d("refresh-account-top: cancelled.")
							break
						}
						
						if(src.isEmpty()) {
							// 直前のデータが0個なら終了とみなす
							log.d("refresh-account-top: previous size == 0.")
							break
						}
						
						if(SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
							log.d("refresh-account-top: timeout.")
							bHeadGap = true
							break
						}
						
						result = client.request(
							path_base,
							params
								.putMisskeySince(column.idRecent)
								.toPostRequestBuilder()
						)
						
						jsonObject = result?.jsonObject
						if(jsonObject != null) {
							// pagingType is always default.
							result !!.data = misskeyArrayFinder(jsonObject)
						}
						
						array = result?.jsonArray
						if(array == null) {
							log.d("refresh-account-top: error or cancelled.")
							bHeadGap = true
							break
						}
						
						src = misskeyCustomParser(parser, array)
						
						addAll(list_tmp, src)
						
						// pagingType is always default.
						column.saveRange(false, true, result, src)
					}
					
					// pagingType is always default.
					if(isMisskey && ! bBottom) {
						list_tmp?.sortBy { it.getOrderId() }
						list_tmp?.reverse()
					}
					
					if(! isCancelled
						&& list_tmp?.isNotEmpty() == true
						&& (bHeadGap || Pref.bpForceGap(context))
					) {
						addOneFirst(list_tmp, TootGap.mayNull(null, column.idRecent))
					}
					
				} else {
					var bGapAdded = false
					var max_id : EntityId? = null
					while(true) {
						if(isCancelled) {
							log.d("refresh-account-top: cancelled.")
							break
						}
						
						// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
						// 直前のデータが0個なら終了とみなすしかなさそう
						if(src.isEmpty()) {
							log.d("refresh-account-top: previous size == 0.")
							break
						}
						
						max_id = column.parseRange(result, src).first
						
						if(SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
							log.d("refresh-account-top: timeout. make gap.")
							// タイムアウト
							// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
							addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
							bGapAdded = true
							break
						}
						
						val path =
							"$path_base${delimiter}max_id=$max_id&since_id=$last_since_id"
						result = client.request(path)
						
						jsonObject = result?.jsonObject
						if(jsonObject != null) {
							result?.data = misskeyArrayFinder(jsonObject)
						}
						
						val jsonArray = result?.jsonArray
						
						if(jsonArray == null) {
							log.d("refresh-account-top: error or cancelled. make gap.")
							// エラー
							// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
							addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
							bGapAdded = true
							break
						}
						
						src = misskeyCustomParser(parser, jsonArray)
						addAll(list_tmp, src)
					}
					if(Pref.bpForceGap(context) && ! isCancelled && ! bGapAdded && list_tmp?.isNotEmpty() == true) {
						addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
					}
				}
			}
			// フィルタがないので下端更新の繰り返しは発生しない
		}
		return firstResult
	}
	
	internal fun getDomainList(
		client : TootApiClient,
		path_base : String
	) : TootApiResult? {
		
		if(isMisskey) return TootApiResult("misskey support is not yet implemented.")
		
		val time_start = SystemClock.elapsedRealtime()
		val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
		val last_since_id = column.idRecent
		
		var result = client.request(column.addRange(bBottom, path_base))
		val firstResult = result
		
		var jsonArray = result?.jsonArray
		if(jsonArray != null) {
			var src = TootDomainBlock.parseList(jsonArray)
			// ページネーションはサーバ側の内部パラメータで行われる
			column.saveRange(bBottom, ! bBottom, result, src)
			list_tmp = addAll(null, src)
			if(! bBottom) {
				if(isMisskey) {
					// Misskey非対応
				} else {
					var bGapAdded = false
					var max_id : EntityId? = null
					while(true) {
						
						if(isCancelled) {
							log.d("refresh-domain-top: cancelled.")
							break
						}
						
						// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
						// 直前のデータが0個なら終了とみなすしかなさそう
						if(src.isEmpty()) {
							log.d("refresh-domain-top: previous size == 0.")
							break
						}
						
						// 直前に読んだ範囲のmaxIdを調べる
						max_id = column.parseRange(result, src).first
						
						if(SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
							log.d("refresh-domain-top: timeout.")
							
							// タイムアウト
							// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
							addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
							bGapAdded = true
							break
						}
						
						val path =
							"$path_base${delimiter}max_id=$max_id&since_id=$last_since_id"
						result = client.request(path)
						jsonArray = result?.jsonArray
						if(jsonArray == null) {
							log.d("refresh-domain-top: error or cancelled.")
							// エラー
							// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
							addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
							bGapAdded = true
							break
						}
						
						src = TootDomainBlock.parseList(jsonArray)
						addAll(list_tmp, src)
					}
					if(Pref.bpForceGap(context) && ! isCancelled && ! bGapAdded && list_tmp?.isNotEmpty() == true) {
						addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
					}
				}
				
			}
			// フィルタがないので下端更新の繰り返しはない
		}
		return firstResult
	}
	
	//			fun getListList(client : TootApiClient, path_base : String) : TootApiResult? {
	//
	//				if(isMisskey) return TootApiResult("misskey support is not yet implemented.")
	//
	//				return TootApiResult("Mastodon's /api/v1/lists has no pagination.")
	//			}
	
	internal fun getReportList(
		client : TootApiClient,
		path_base : String
	) : TootApiResult? {
		
		if(isMisskey) return TootApiResult("misskey support is not yet implemented.")
		
		val time_start = SystemClock.elapsedRealtime()
		val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
		val last_since_id = column.idRecent
		var result = client.request(column.addRange(bBottom, path_base))
		val firstResult = result
		var jsonArray = result?.jsonArray
		if(jsonArray != null) {
			var src = parseList(::TootReport, jsonArray)
			list_tmp = addAll(null, src)
			column.saveRange(bBottom, ! bBottom, result, src)
			
			if(! bBottom) {
				var bGapAdded = false
				var max_id : EntityId? = null
				while(true) {
					if(isCancelled) {
						log.d("refresh-report-top: cancelled.")
						break
					}
					
					// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
					// 直前のデータが0個なら終了とみなすしかなさそう
					if(src.isEmpty()) {
						log.d("refresh-report-top: previous size == 0.")
						break
					}
					
					// 直前に読んだ範囲のmaxIdを調べる
					max_id = column.parseRange(result, src).first
					
					if(SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
						log.d("refresh-report-top: timeout. make gap.")
						// タイムアウト
						// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
						addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
						bGapAdded = true
						break
					}
					
					val path =
						"$path_base${delimiter}max_id=$max_id&since_id=$last_since_id"
					result = client.request(path)
					jsonArray = result?.jsonArray
					if(jsonArray == null) {
						log.d("refresh-report-top: timeout. error or retry. make gap.")
						// エラー
						// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
						addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
						bGapAdded = true
						break
					}
					
					src = parseList(::TootReport, jsonArray)
					addAll(list_tmp, src)
				}
				if(Pref.bpForceGap(context) && ! isCancelled && ! bGapAdded && list_tmp?.isNotEmpty() == true) {
					addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
				}
			}
			// レポートにはフィルタがないので下端更新は繰り返さない
		}
		return firstResult
	}
	
	internal fun getNotificationList(
		client : TootApiClient,
		fromAcct : String? = null
	) : TootApiResult? {
		
		val path_base = column.makeNotificationUrl(client, fromAcct)
		val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
		val last_since_id = column.idRecent
		
		val params = column.makeMisskeyBaseParameter(parser).addMisskeyNotificationFilter(column)
		
		val time_start = SystemClock.elapsedRealtime()
		
		var result = if(isMisskey) {
			client.request(
				path_base,
				params.addRangeMisskey(bBottom).toPostRequestBuilder()
			)
		} else {
			client.request(column.addRange(bBottom, path_base))
		}
		val firstResult = result
		var jsonArray = result?.jsonArray
		if(jsonArray != null) {
			var src = parser.notificationList(jsonArray)
			
			list_tmp = addWithFilterNotification(null, src)
			column.saveRange(bBottom, ! bBottom, result, src)
			
			if(src.isNotEmpty()) {
				PollingWorker.injectData(context, access_info, src)
			}
			
			if(! bBottom) {
				// 頭の方を読む時は隙間を減らすため、フィルタの有無に関係なく繰り返しを行う
				
				if(isMisskey) {
					// misskey ではsinceIdを指定すると古い方から読める
					// 先頭にギャップを追加するかもしれない
					var bHeadGap = false
					
					while(true) {
						
						if(isCancelled) {
							log.d("refresh-notification-top: cancelled.")
							break
						}
						
						// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
						// 直前のデータが0個なら終了とみなすしかなさそう
						if(src.isEmpty()) {
							log.d("refresh-notification-top: previous size == 0.")
							break
						}
						
						if(SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
							log.d("refresh-notification-top: timeout. make gap.")
							// タイムアウト
							bHeadGap = true
							break
						}
						
						result = client.request(
							path_base,
							params
								.putMisskeySince(column.idRecent)
								.toPostRequestBuilder()
						)
						
						jsonArray = result?.jsonArray
						if(jsonArray == null) {
							log.d("refresh-notification-top: error or cancelled. make gap.")
							// エラー
							bHeadGap = true
							break
						}
						
						src = parser.notificationList(jsonArray)
						
						column.saveRange(false, true, result, src)
						if(src.isNotEmpty()) {
							addWithFilterNotification(list_tmp, src)
							PollingWorker.injectData(context, access_info, src)
						}
					}
					
					if(isMisskey && ! bBottom) {
						list_tmp?.sortBy { it.getOrderId() }
						list_tmp?.reverse()
					}
					
					if(! isCancelled
						&& list_tmp?.isNotEmpty() == true
						&& (bHeadGap || Pref.bpForceGap(context))
					) {
						addOneFirst(list_tmp, TootGap.mayNull(null, column.idRecent))
					}
					
				} else {
					
					var bGapAdded = false
					var max_id : EntityId? = null
					while(true) {
						if(isCancelled) {
							log.d("refresh-notification-offset: cancelled.")
							break
						}
						
						// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
						// 直前のデータが0個なら終了とみなすしかなさそう
						if(src.isEmpty()) {
							log.d("refresh-notification-offset: previous size == 0.")
							break
						}
						
						max_id = column.parseRange(result, src).first
						
						if(SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
							log.d("refresh-notification-offset: timeout. make gap.")
							// タイムアウト
							// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
							addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
							bGapAdded = true
							break
						}
						
						val path =
							"$path_base${delimiter}max_id=$max_id&since_id=$last_since_id"
						result = client.request(path)
						jsonArray = result?.jsonArray
						if(jsonArray == null) {
							log.d("refresh-notification-offset: error or cancelled. make gap.")
							// エラー
							// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
							addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
							bGapAdded = true
							break
						}
						
						src = parser.notificationList(jsonArray)
						if(src.isNotEmpty()) {
							addWithFilterNotification(list_tmp, src)
							PollingWorker.injectData(context, access_info, src)
						}
					}
					if(Pref.bpForceGap(context) && ! isCancelled && ! bGapAdded && list_tmp?.isNotEmpty() == true) {
						addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
					}
				}
				
			} else {
				while(true) {
					if(isCancelled) {
						log.d("refresh-notification-bottom: cancelled.")
						break
					}
					
					// bottomの場合、フィルタなしなら繰り返さない
					if(! column.isFilterEnabled) {
						log.d("refresh-notification-bottom: isFiltered is false.")
						break
					}
					
					// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
					// 直前のデータが0個なら終了とみなすしかなさそう
					if(src.isEmpty()) {
						log.d("refresh-notification-bottom: previous size == 0.")
						break
					}
					
					// 十分読んだらそれで終了
					if((list_tmp?.size ?: 0) >= Column.LOOP_READ_ENOUGH) {
						log.d("refresh-notification-bottom: read enough data.")
						break
					}
					
					if(SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
						// タイムアウト
						log.d("refresh-notification-bottom: loop timeout.")
						break
					}
					
					result = if(isMisskey) {
						client.request(
							path_base,
							params
								.putMisskeyUntil(column.idOld)
								.toPostRequestBuilder()
						)
					} else {
						client.request("${path_base}${delimiter}max_id=${column.idOld}")
					}
					
					jsonArray = result?.jsonArray
					if(jsonArray == null) {
						log.d("refresh-notification-bottom: error or cancelled.")
						break
					}
					
					src = parser.notificationList(jsonArray)
					
					addWithFilterNotification(list_tmp, src)
					
					if(! column.saveRangeEnd(result, src)) {
						log.d("refresh-notification-bottom: saveRangeEnd failed.")
						break
					}
				}
			}
		}
		return firstResult
	}
	
	internal fun getConversationSummaryList(
		client : TootApiClient,
		path_base : String,
		aroundMin : Boolean = false,
		misskeyParams : JSONObject? = null,
		misskeyCustomParser : (parser : TootParser, jsonArray : JSONArray) -> ArrayList<TootConversationSummary> =
			{ parser, jsonArray -> parseList(::TootConversationSummary, parser, jsonArray) }
	) : TootApiResult? {
		
		val isMisskey = access_info.isMisskey
		
		val params = misskeyParams ?: column.makeMisskeyTimelineParameter(parser)
		
		val time_start = SystemClock.elapsedRealtime()
		
		val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
		val last_since_id = column.idRecent
		
		var result = when {
			isMisskey -> client.request(
				path_base,
				params.addRangeMisskey(bBottom).toPostRequestBuilder()
			)
			
			aroundMin -> client.request(column.addRangeMin(path_base))
			else -> client.request(column.addRange(bBottom, path_base))
		}
		val firstResult = result
		
		val jsonArray = result?.jsonArray
		if(jsonArray != null) {
			var src = misskeyCustomParser(parser, jsonArray)
			
			column.saveRange(bBottom, ! bBottom, result, src)
			list_tmp = addWithFilterConversationSummary(null, src)
			
			if(! bBottom) {
				if(isMisskey) {
					// Misskeyの場合はsinceIdを指定しても取得できるのは未読のうち古い範囲に偏る
					var bHeadGap = false
					while(true) {
						if(isCancelled) {
							log.d("refresh-ConversationSummary-top: cancelled.")
							break
						}
						
						// 頭の方を読む時は隙間を減らすため、フィルタの有無に関係なく繰り返しを行う
						
						// 直前のデータが0個なら終了とみなす
						if(src.isEmpty()) {
							log.d("refresh-ConversationSummary-top: previous size == 0.")
							break
						}
						
						if((list_tmp?.size ?: 0) >= Column.LOOP_READ_ENOUGH) {
							log.d("refresh-ConversationSummary-top: read enough. make gap.")
							bHeadGap = true
							break
						}
						
						if(SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
							log.d("refresh-ConversationSummary-top: timeout. make gap.")
							bHeadGap = true
							break
						}
						
						result = client.request(
							path_base,
							params
								.putMisskeySince(column.idRecent)
								.toPostRequestBuilder()
						)
						
						val jsonArray2 = result?.jsonArray
						if(jsonArray2 == null) {
							log.d("refresh-ConversationSummary-top: error or cancelled. make gap.")
							bHeadGap = true
							break
						}
						
						src = misskeyCustomParser(parser, jsonArray2)
						
						column.saveRange(false, true, result, src)
						
						addWithFilterConversationSummary(list_tmp, src)
					}
					
					if(isMisskey && ! bBottom) {
						list_tmp?.sortBy { it.getOrderId() }
						list_tmp?.reverse()
					}
					
					if(! isCancelled
						&& list_tmp?.isNotEmpty() == true
						&& (bHeadGap || Pref.bpForceGap(context))
					) {
						addOneFirst(list_tmp, TootGap.mayNull(null, column.idRecent))
					}
					
				} else if(aroundMin) {
					while(true) {
						
						column.saveRangeStart(result, src)
						
						if(isCancelled) {
							log.d("refresh-ConversationSummary-aroundMin: cancelled.")
							break
						}
						
						// 頭の方を読む時は隙間を減らすため、フィルタの有無に関係なく繰り返しを行う
						
						// 直前のデータが0個なら終了とみなすしかなさそう
						if(src.isEmpty()) {
							log.d("refresh-ConversationSummary-aroundMin: previous size == 0.")
							break
						}
						
						if((list_tmp?.size ?: 0) >= Column.LOOP_READ_ENOUGH) {
							log.d("refresh-ConversationSummary-aroundMin: read enough.")
							break
						}
						
						if(SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
							log.d("refresh-ConversationSummary-aroundMin: timeout.")
							break
						}
						
						val path = "$path_base${delimiter}min_id=${column.idRecent}"
						result = client.request(path)
						
						val jsonArray2 = result?.jsonArray
						if(jsonArray2 == null) {
							log.d("refresh-ConversationSummary-aroundMin: error or cancelled.")
							break
						}
						
						src = misskeyCustomParser(parser, jsonArray2)
						addWithFilterConversationSummary(list_tmp, src)
					}
				} else {
					var bGapAdded = false
					var max_id : EntityId? = null
					while(true) {
						if(isCancelled) {
							log.d("refresh-ConversationSummary-top: cancelled.")
							break
						}
						
						// 頭の方を読む時は隙間を減らすため、フィルタの有無に関係なく繰り返しを行う
						
						// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
						// 直前のデータが0個なら終了とみなすしかなさそう
						if(src.isEmpty()) {
							log.d("refresh-ConversationSummary-top: previous size == 0.")
							break
						}
						
						max_id = column.parseRange(result, src).first
						
						if((list_tmp?.size ?: 0) >= Column.LOOP_READ_ENOUGH) {
							log.d("refresh-ConversationSummary-top: read enough. make gap.")
							// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
							addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
							bGapAdded = true
							break
						}
						
						if(SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
							log.d("refresh-ConversationSummary-top: timeout. make gap.")
							// タイムアウト
							// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
							addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
							bGapAdded = true
							break
						}
						
						val path =
							"$path_base${delimiter}max_id=$max_id&since_id=$last_since_id"
						result = client.request(path)
						
						val jsonArray2 = result?.jsonArray
						if(jsonArray2 == null) {
							log.d("refresh-ConversationSummary-top: error or cancelled. make gap.")
							// エラー
							// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
							addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
							bGapAdded = true
							break
						}
						
						src = misskeyCustomParser(parser, jsonArray2)
						addWithFilterConversationSummary(list_tmp, src)
					}
					
					if(Pref.bpForceGap(context) && ! isCancelled && ! bGapAdded && list_tmp?.isNotEmpty() == true) {
						addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
					}
				}
				
			} else {
				while(true) {
					if(isCancelled) {
						log.d("refresh-ConversationSummary-bottom: cancelled.")
						break
					}
					
					// bottomの場合、フィルタなしなら繰り返さない
					if(! column.isFilterEnabled) {
						log.d("refresh-ConversationSummary-bottom: isFiltered is false.")
						break
					}
					
					// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
					// 直前のデータが0個なら終了とみなすしかなさそう
					if(src.isEmpty()) {
						log.d("refresh-ConversationSummary-bottom: previous size == 0.")
						break
					}
					
					// 十分読んだらそれで終了
					if((list_tmp?.size ?: 0) >= Column.LOOP_READ_ENOUGH) {
						log.d("refresh-ConversationSummary-bottom: read enough data.")
						break
					}
					
					if(SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
						// タイムアウト
						log.d("refresh-ConversationSummary-bottom: loop timeout.")
						break
					}
					
					result = if(isMisskey) {
						client.request(
							path_base,
							params
								.putMisskeyUntil(column.idOld)
								.toPostRequestBuilder()
						)
					} else {
						client.request("$path_base${delimiter}max_id=${column.idOld}")
					}
					
					val jsonArray2 = result?.jsonArray
					if(jsonArray2 == null) {
						log.d("refresh-ConversationSummary-bottom: error or cancelled.")
						break
					}
					
					src = misskeyCustomParser(parser, jsonArray2)
					addWithFilterConversationSummary(list_tmp, src)
					
					if(! column.saveRangeEnd(result, src)) {
						log.d("refresh-ConversationSummary-bottom: saveRangeEnd failed.")
						break
					}
				}
			}
		}
		return firstResult
	}
	
	internal fun getScheduledStatuses(client : TootApiClient) : TootApiResult? {
		val result = client.request(column.addRange(bBottom, Column.PATH_SCHEDULED_STATUSES))
		val src = parseList(::TootScheduled, parser, result?.jsonArray)
		list_tmp = addAll(list_tmp, src)
		column.saveRange(bBottom, ! bBottom, result, src)
		return result
	}
	
	internal fun getStatusList(
		client : TootApiClient,
		path_base : String?,
		aroundMin : Boolean = false,
		misskeyParams : JSONObject? = null,
		misskeyCustomParser : (parser : TootParser, jsonArray : JSONArray) -> ArrayList<TootStatus> =
			{ parser, jsonArray -> parser.statusList(jsonArray) }
	) : TootApiResult? {
		
		path_base ?: return null // cancelled.
		
		val isMisskey = access_info.isMisskey
		
		val params = misskeyParams ?: column.makeMisskeyTimelineParameter(parser)
		
		val time_start = SystemClock.elapsedRealtime()
		
		val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
		val last_since_id = column.idRecent
		
		var result = when {
			isMisskey -> client.request(
				path_base,
				params.addRangeMisskey(bBottom).toPostRequestBuilder()
			)
			
			aroundMin -> client.request(column.addRangeMin(path_base))
			else -> client.request(column.addRange(bBottom, path_base))
		}
		val firstResult = result
		
		val jsonArray = result?.jsonArray
		if(jsonArray != null) {
			var src = misskeyCustomParser(parser, jsonArray)
			
			column.saveRange(bBottom, ! bBottom, result, src)
			list_tmp = addWithFilterStatus(null, src)
			
			if(! bBottom) {
				if(isMisskey) {
					// Misskeyの場合はsinceIdを指定しても取得できるのは未読のうち古い範囲に偏る
					var bHeadGap = false
					while(true) {
						if(isCancelled) {
							log.d("refresh-status-top: cancelled.")
							break
						}
						
						// 頭の方を読む時は隙間を減らすため、フィルタの有無に関係なく繰り返しを行う
						
						// 直前のデータが0個なら終了とみなす
						if(src.isEmpty()) {
							log.d("refresh-status-top: previous size == 0.")
							break
						}
						
						if((list_tmp?.size ?: 0) >= Column.LOOP_READ_ENOUGH) {
							log.d("refresh-status-top: read enough. make gap.")
							bHeadGap = true
							break
						}
						
						if(SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
							log.d("refresh-status-top: timeout. make gap.")
							bHeadGap = true
							break
						}
						
						result = client.request(
							path_base,
							params
								.putMisskeySince(column.idRecent)
								.toPostRequestBuilder()
						)
						
						val jsonArray2 = result?.jsonArray
						if(jsonArray2 == null) {
							log.d("refresh-status-top: error or cancelled. make gap.")
							bHeadGap = true
							break
						}
						
						src = misskeyCustomParser(parser, jsonArray2)
						
						column.saveRange(false, true, result, src)
						
						addWithFilterStatus(list_tmp, src)
					}
					
					if(isMisskey && ! bBottom) {
						list_tmp?.sortBy { it.getOrderId() }
						list_tmp?.reverse()
					}
					
					if(! isCancelled
						&& list_tmp?.isNotEmpty() == true
						&& (bHeadGap || Pref.bpForceGap(context))
					) {
						addOneFirst(list_tmp, TootGap.mayNull(null, column.idRecent))
					}
					
				} else if(aroundMin) {
					while(true) {
						
						column.saveRangeStart(result, src)
						
						if(isCancelled) {
							log.d("refresh-status-aroundMin: cancelled.")
							break
						}
						
						// 頭の方を読む時は隙間を減らすため、フィルタの有無に関係なく繰り返しを行う
						
						// 直前のデータが0個なら終了とみなすしかなさそう
						if(src.isEmpty()) {
							log.d("refresh-status-aroundMin: previous size == 0.")
							break
						}
						
						if((list_tmp?.size ?: 0) >= Column.LOOP_READ_ENOUGH) {
							log.d("refresh-status-aroundMin: read enough.")
							break
						}
						
						if(SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
							log.d("refresh-status-aroundMin: timeout.")
							break
						}
						
						val path = "$path_base${delimiter}min_id=${column.idRecent}"
						result = client.request(path)
						
						val jsonArray2 = result?.jsonArray
						if(jsonArray2 == null) {
							log.d("refresh-status-aroundMin: error or cancelled.")
							break
						}
						
						src = misskeyCustomParser(parser, jsonArray2)
						addWithFilterStatus(list_tmp, src)
					}
				} else {
					var bGapAdded = false
					var max_id : EntityId? = null
					while(true) {
						if(isCancelled) {
							log.d("refresh-status-top: cancelled.")
							break
						}
						
						// 頭の方を読む時は隙間を減らすため、フィルタの有無に関係なく繰り返しを行う
						
						// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
						// 直前のデータが0個なら終了とみなすしかなさそう
						if(src.isEmpty()) {
							log.d("refresh-status-top: previous size == 0.")
							break
						}
						
						max_id = column.parseRange(result, src).first
						
						if((list_tmp?.size ?: 0) >= Column.LOOP_READ_ENOUGH) {
							log.d("refresh-status-top: read enough. make gap.")
							// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
							addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
							bGapAdded = true
							break
						}
						
						if(SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
							log.d("refresh-status-top: timeout. make gap.")
							// タイムアウト
							// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
							addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
							bGapAdded = true
							break
						}
						
						val path =
							"$path_base${delimiter}max_id=$max_id&since_id=$last_since_id"
						result = client.request(path)
						
						val jsonArray2 = result?.jsonArray
						if(jsonArray2 == null) {
							log.d("refresh-status-top: error or cancelled. make gap.")
							// エラー
							// 隙間ができるかもしれない。後ほど手動で試してもらうしかない
							addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
							bGapAdded = true
							break
						}
						
						src = misskeyCustomParser(parser, jsonArray2)
						addWithFilterStatus(list_tmp, src)
					}
					
					if(Pref.bpForceGap(context) && ! isCancelled && ! bGapAdded && list_tmp?.isNotEmpty() == true) {
						addOne(list_tmp, TootGap.mayNull(max_id, last_since_id))
					}
				}
				
			} else {
				while(true) {
					if(isCancelled) {
						log.d("refresh-status-bottom: cancelled.")
						break
					}
					
					// bottomの場合、フィルタなしなら繰り返さない
					if(! column.isFilterEnabled) {
						log.d("refresh-status-bottom: isFiltered is false.")
						break
					}
					
					// max_id だけを指定した場合、必ずlimit個のデータが帰ってくるとは限らない
					// 直前のデータが0個なら終了とみなすしかなさそう
					if(src.isEmpty()) {
						log.d("refresh-status-bottom: previous size == 0.")
						break
					}
					
					// 十分読んだらそれで終了
					if((list_tmp?.size ?: 0) >= Column.LOOP_READ_ENOUGH) {
						log.d("refresh-status-bottom: read enough data.")
						break
					}
					
					if(SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
						// タイムアウト
						log.d("refresh-status-bottom: loop timeout.")
						break
					}
					
					result = if(isMisskey) {
						client.request(
							path_base,
							params
								.putMisskeyUntil(column.idOld)
								.toPostRequestBuilder()
						)
					} else {
						client.request("$path_base${delimiter}max_id=${column.idOld}")
					}
					
					val jsonArray2 = result?.jsonArray
					if(jsonArray2 == null) {
						log.d("refresh-status-bottom: error or cancelled.")
						break
					}
					
					src = misskeyCustomParser(parser, jsonArray2)
					addWithFilterStatus(list_tmp, src)
					
					if(! column.saveRangeEnd(result, src)) {
						log.d("refresh-status-bottom: saveRangeEnd failed.")
						break
					}
				}
			}
		}
		return firstResult
	}
	
}
