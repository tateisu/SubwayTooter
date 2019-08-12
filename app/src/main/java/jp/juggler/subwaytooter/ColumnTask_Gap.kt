package jp.juggler.subwaytooter

import android.os.SystemClock
import jp.juggler.subwaytooter.api.TootApiCallback
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.util.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import kotlin.collections.ArrayList

class ColumnTask_Gap(
	columnArg : Column,
	private val gap : TimelineItem
) : ColumnTask(columnArg, ColumnTaskType.GAP) {
	
	companion object {
		internal val log = LogCategory("CT_Gap")
	}
	
	private var max_id : EntityId? = (gap as? TootGap)?.max_id
	private var since_id : EntityId? = (gap as? TootGap)?.since_id
	private val countTag: Int
	private val countAccount: Int
	private val countStatus: Int
	init{
		var countTag =0
		var countAccount =0
		var countStatus = 0
		if( gap is TootSearchGap){
			columnArg.list_data.forEach {
				when(it){
					is TootTag -> ++countTag
					is TootAccountRef -> ++countAccount
					is TootStatus -> ++countStatus
				}
			}
		}
		this.countTag = countTag
		this.countAccount = countAccount
		this.countStatus = countStatus
	}
	
	override fun doInBackground(vararg unused : Void) : TootApiResult? {
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
			return (columnTypeProcMap[column.column_type] ?: columnTypeProcMap[Column.TYPE_HOME])
				.gap(this, client)
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
			
			val list_new = column.duplicate_map.filterDuplicate(list_tmp)
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
			
			if(holder != null) {
				if(restore_idx >= 0) {
					// ギャップが画面内にあるなら
					holder.setListItemTop(restore_idx + added - 1, restore_y)
				} else {
					// ギャップが画面内にない場合、何もしない
				}
			} else {
				val scroll_save = column.scroll_save
				if(scroll_save != null) {
					scroll_save.adapterIndex += added - 1
				}
			}
			
			column.updateMisskeyCapture()
		} finally {
			column.fireShowColumnStatus()
		}
	}
	
	internal fun getAccountList(
		client : TootApiClient,
		path_base : String,
		misskeyParams : JSONObject? = null,
		misskeyCustomParser : (parser : TootParser, jsonArray : JSONArray) -> ArrayList<TootAccountRef> =
			{ parser, jsonArray -> parser.accountList(jsonArray) },
		misskeyArrayFinder : (jsonObject : JSONObject) -> JSONArray? = { null }
	
	) : TootApiResult? {
		
		@Suppress("NON_EXHAUSTIVE_WHEN")
		when(column.pagingType) {
			ColumnPagingType.Offset,
			ColumnPagingType.Cursor,
			ColumnPagingType.None -> {
				return TootApiResult("can't support gap")
			}
		}
		
		val params = misskeyParams ?: column.makeMisskeyBaseParameter(parser)
		val time_start = SystemClock.elapsedRealtime()
		val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
		list_tmp = ArrayList()
		
		var result : TootApiResult? = null
		
		if(isMisskey) {
			
			// missKeyではgapを下から読む
			var bHeadGap = false
			
			while(true) {
				
				if(isCancelled) {
					log.d("account: cancelled.")
					break
				}
				
				if(result != null && SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
					log.d("account: timeout. make gap.")
					bHeadGap = true
					break
				}
				
				val r2 = client.request(
					path_base,
					params
						.putMisskeySince(since_id)
						.toPostRequestBuilder()
				)
				
				val jsonObject = r2?.jsonObject
				if(jsonObject != null) {
					r2.data = misskeyArrayFinder(jsonObject)
				}
				
				val jsonArray = r2?.jsonArray
				if(jsonArray == null) {
					log.d("account: error. make gap.")
					if(result == null) result = r2
					bHeadGap = true
					break
				}
				result = r2
				
				val src = misskeyCustomParser(parser, jsonArray)
				if(src.isEmpty()) {
					log.d("account: empty.")
					break
				}
				
				addAll(list_tmp, src)
				since_id = column.parseRange(result, src).second
			}
			if(isMisskey) {
				list_tmp?.sortBy { it.getOrderId() }
				list_tmp?.reverse()
			}
			if(bHeadGap) {
				addOneFirst(list_tmp, TootGap.mayNull(max_id, since_id))
			}
			
		} else {
			while(true) {
				
				if(isCancelled) {
					log.d("account: cancelled.")
					break
				}
				
				if(result != null && SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
					log.d("account: timeout. make gap.")
					// タイムアウト
					// 隙間が残る
					addOne(list_tmp, TootGap.mayNull(max_id, since_id))
					break
				}
				
				val path = "$path_base${delimiter}max_id=$max_id&since_id=$since_id"
				val r2 = client.request(path)
				
				val jsonArray = r2?.jsonArray
				if(jsonArray == null) {
					log.d("account: error timeout. make gap.")
					
					if(result == null) result = r2
					
					// 隙間が残る
					addOne(list_tmp, TootGap.mayNull(max_id, since_id))
					break
				}
				result = r2
				val src = misskeyCustomParser(parser, jsonArray)
				
				if(src.isEmpty()) {
					log.d("account: empty.")
					break
				}
				
				addAll(list_tmp, src)
				max_id = column.parseRange(result, src).first
			}
		}
		return result
	}
	
	internal fun getReportList(
		client : TootApiClient,
		path_base : String
	) : TootApiResult? {
		val time_start = SystemClock.elapsedRealtime()
		val params = column.makeMisskeyBaseParameter(parser)
		val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
		list_tmp = ArrayList()
		
		var result : TootApiResult? = null
		
		if(isMisskey) {
			var bHeadGap = false
			while(true) {
				if(isCancelled) {
					log.d("report: cancelled.")
					break
				}
				
				if(result != null && SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
					log.d("report: timeout. make gap.")
					bHeadGap = true
					break
				}
				
				val r2 = client.request(
					path_base,
					params
						.putMisskeySince(since_id)
						.toPostRequestBuilder()
				)
				
				val jsonArray = r2?.jsonArray
				if(jsonArray == null) {
					log.d("report: error or cancelled. make gap.")
					if(result == null) result = r2
					bHeadGap = true
					break
				}
				
				result = r2
				val src = parseList(::TootReport, jsonArray)
				if(src.isEmpty()) {
					log.d("report: empty.")
					break
				}
				
				addAll(list_tmp, src)
				
				// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
				since_id = column.parseRange(result, src).second
			}
			
			// レポート一覧ってそもそもMisskey対応してないので、ここをどうするかは不明
			// 多分 sinceIDによるページングではないと思う
			if(isMisskey) {
				list_tmp?.sortBy { it.getOrderId() }
				list_tmp?.reverse()
			}
			
			if(bHeadGap) {
				addOneFirst(list_tmp, TootGap.mayNull(max_id, since_id))
			}
		} else {
			while(true) {
				if(isCancelled) {
					log.d("report: cancelled.")
					break
				}
				
				if(result != null && SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
					log.d("report: timeout. make gap.")
					// タイムアウト
					// 隙間が残る
					addOne(list_tmp, TootGap.mayNull(max_id, since_id))
					break
				}
				
				val path =
					path_base + delimiter + "max_id=" + max_id + "&since_id=" + since_id
				val r2 = client.request(path)
				val jsonArray = r2?.jsonArray
				if(jsonArray == null) {
					log.d("report: error or cancelled. make gap.")
					if(result == null) result = r2
					// 隙間が残る
					addOne(list_tmp, TootGap.mayNull(max_id, since_id))
					break
				}
				
				result = r2
				val src = parseList(::TootReport, jsonArray)
				if(src.isEmpty()) {
					log.d("report: empty.")
					// コレ以上取得する必要はない
					break
				}
				
				addAll(list_tmp, src)
				
				// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
				max_id = column.parseRange(result, src).first
			}
		}
		return result
	}
	
	internal fun getNotificationList(
		client : TootApiClient,
		fromAcct : String? = null
	) : TootApiResult? {
		val path_base = column.makeNotificationUrl(client, fromAcct)
		val params = column.makeMisskeyBaseParameter(parser).addMisskeyNotificationFilter(column)
		val time_start = SystemClock.elapsedRealtime()
		val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
		
		list_tmp = ArrayList()
		
		var result : TootApiResult? = null
		
		if(isMisskey) {
			var bHeadGap = false
			while(true) {
				if(isCancelled) {
					log.d("notification: cancelled.")
					break
				}
				
				if(result != null && SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
					log.d("notification: timeout. make gap.")
					bHeadGap = true
					break
				}
				
				val r2 = client.request(
					path_base,
					params
						.putMisskeySince(since_id)
						.toPostRequestBuilder()
				)
				
				val jsonArray = r2?.jsonArray
				if(jsonArray == null) {
					// エラー
					log.d("notification: error or response. make gap.")
					if(result == null) result = r2
					// 隙間が残る
					bHeadGap = true
					break
				}
				
				result = r2
				val src = parser.notificationList(jsonArray)
				
				if(src.isEmpty()) {
					log.d("notification: empty.")
					break
				}
				
				// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
				since_id = column.parseRange(result, src).second
				
				addWithFilterNotification(list_tmp, src)
				
				PollingWorker.injectData(context, access_info, src)
			}
			
			if(isMisskey) {
				list_tmp?.sortBy { it.getOrderId() }
				list_tmp?.reverse()
			}
			
			if(bHeadGap) {
				addOneFirst(list_tmp, TootGap.mayNull(max_id, since_id))
			}
		} else {
			while(true) {
				if(isCancelled) {
					log.d("notification: cancelled.")
					break
				}
				
				if(result != null && SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
					log.d("notification: timeout. make gap.")
					// タイムアウト
					// 隙間が残る
					addOne(list_tmp, TootGap.mayNull(max_id, since_id))
					break
				}
				val path =
					path_base + delimiter + "max_id=" + max_id + "&since_id=" + since_id
				val r2 = client.request(path)
				val jsonArray = r2?.jsonArray
				if(jsonArray == null) {
					// エラー
					log.d("notification: error or response. make gap.")
					
					if(result == null) result = r2
					
					// 隙間が残る
					addOne(list_tmp, TootGap.mayNull(max_id, since_id))
					break
				}
				
				result = r2
				val src = parser.notificationList(jsonArray)
				
				if(src.isEmpty()) {
					log.d("notification: empty.")
					break
				}
				
				// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
				max_id = column.parseRange(result, src).first
				
				addWithFilterNotification(list_tmp, src)
				
				PollingWorker.injectData(context, access_info, src)
				
			}
		}
		
		return result
	}
	
	internal fun getStatusList(
		client : TootApiClient,
		path_base : String?,
		misskeyParams : JSONObject? = null,
		misskeyCustomParser : (parser : TootParser, jsonArray : JSONArray) -> ArrayList<TootStatus> =
			{ parser, jsonArray -> parser.statusList(jsonArray) }
	) : TootApiResult? {
		
		path_base ?: return null // cancelled.
		
		val isMisskey = access_info.isMisskey
		
		val params = misskeyParams ?: column.makeMisskeyTimelineParameter(parser)
		
		val time_start = SystemClock.elapsedRealtime()
		
		val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
		
		list_tmp = ArrayList()
		
		var result : TootApiResult? = null
		if(isMisskey) {
			var bHeadGap = false
			while(true) {
				if(isCancelled) {
					log.d("statuses: cancelled.")
					break
				}
				
				if(result != null && SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
					log.d("statuses: timeout.")
					bHeadGap = true
					break
				}
				
				val r2 = client.request(
					path_base,
					params
						.putMisskeySince(since_id)
						.toPostRequestBuilder()
				)
				
				val jsonArray = r2?.jsonArray
				if(jsonArray == null) {
					log.d("statuses: error or cancelled. make gap.")
					
					// 成功データがない場合だけ、今回のエラーを返すようにする
					if(result == null) result = r2
					
					bHeadGap = true
					
					break
				}
				
				// 成功した場合はそれを返したい
				result = r2
				
				val src = misskeyCustomParser(parser, jsonArray)
				
				if(src.isEmpty()) {
					// 直前の取得でカラのデータが帰ってきたら終了
					log.d("statuses: empty.")
					break
				}
				
				// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
				since_id = column.parseRange(result, src).second
				
				addWithFilterStatus(list_tmp, src)
			}
			
			if(isMisskey) {
				list_tmp?.sortBy { it.getOrderId() }
				list_tmp?.reverse()
			}
			
			if(bHeadGap) {
				addOneFirst(list_tmp, TootGap.mayNull(max_id, since_id))
			}
			
		} else {
			var bLastGap = false
			while(true) {
				if(isCancelled) {
					log.d("statuses: cancelled.")
					break
				}
				
				if(result != null && SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
					log.d("statuses: timeout.")
					// タイムアウト
					bLastGap = true
					break
				}
				
				val path = "${path_base}${delimiter}max_id=${max_id}&since_id=${since_id}"
				val r2 = client.request(path)
				
				val jsonArray = r2?.jsonArray
				if(jsonArray == null) {
					log.d("statuses: error or cancelled. make gap.")
					
					// 成功データがない場合だけ、今回のエラーを返すようにする
					if(result == null) result = r2
					
					bLastGap = true
					
					break
				}
				
				// 成功した場合はそれを返したい
				result = r2
				
				val src = misskeyCustomParser(parser, jsonArray)
				
				if(src.isEmpty()) {
					// 直前の取得でカラのデータが帰ってきたら終了
					log.d("statuses: empty.")
					break
				}
				
				// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
				max_id = column.parseRange(result, src).first
				
				addWithFilterStatus(list_tmp, src)
			}
			if(bLastGap) {
				addOne(list_tmp, TootGap.mayNull(max_id, since_id))
			}
		}
		return result
	}
	
	internal fun getConversationSummaryList(
		client : TootApiClient,
		path_base : String,
		misskeyParams : JSONObject? = null,
		misskeyCustomParser : (parser : TootParser, jsonArray : JSONArray) -> ArrayList<TootConversationSummary> =
			{ parser, jsonArray -> parseList(::TootConversationSummary, parser, jsonArray) }
	) : TootApiResult? {
		
		val isMisskey = access_info.isMisskey
		
		val params = misskeyParams ?: column.makeMisskeyTimelineParameter(parser)
		
		val time_start = SystemClock.elapsedRealtime()
		
		val delimiter = if(- 1 != path_base.indexOf('?')) '&' else '?'
		
		list_tmp = ArrayList()
		
		var result : TootApiResult? = null
		if(isMisskey) {
			var bHeadGap = false
			while(true) {
				if(isCancelled) {
					log.d("statuses: cancelled.")
					break
				}
				
				if(result != null && SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
					log.d("statuses: timeout.")
					bHeadGap = true
					break
				}
				
				val r2 = client.request(
					path_base,
					params
						.putMisskeySince(since_id)
						.toPostRequestBuilder()
				)
				
				val jsonArray = r2?.jsonArray
				if(jsonArray == null) {
					log.d("statuses: error or cancelled. make gap.")
					
					// 成功データがない場合だけ、今回のエラーを返すようにする
					if(result == null) result = r2
					
					bHeadGap = true
					
					break
				}
				
				// 成功した場合はそれを返したい
				result = r2
				
				val src = misskeyCustomParser(parser, jsonArray)
				
				if(src.isEmpty()) {
					// 直前の取得でカラのデータが帰ってきたら終了
					log.d("statuses: empty.")
					break
				}
				
				// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
				since_id = column.parseRange(result, src).second
				
				addWithFilterConversationSummary(list_tmp, src)
			}
			
			if(isMisskey) {
				list_tmp?.sortBy { it.getOrderId() }
				list_tmp?.reverse()
			}
			
			if(bHeadGap) {
				addOneFirst(list_tmp, TootGap.mayNull(max_id, since_id))
			}
			
		} else {
			var bLastGap = false
			while(true) {
				if(isCancelled) {
					log.d("statuses: cancelled.")
					break
				}
				
				if(result != null && SystemClock.elapsedRealtime() - time_start > Column.LOOP_TIMEOUT) {
					log.d("statuses: timeout.")
					// タイムアウト
					bLastGap = true
					break
				}
				
				val path = "${path_base}${delimiter}max_id=${max_id}&since_id=${since_id}"
				val r2 = client.request(path)
				
				val jsonArray = r2?.jsonArray
				if(jsonArray == null) {
					log.d("statuses: error or cancelled. make gap.")
					
					// 成功データがない場合だけ、今回のエラーを返すようにする
					if(result == null) result = r2
					
					bLastGap = true
					
					break
				}
				
				// 成功した場合はそれを返したい
				result = r2
				
				val src = misskeyCustomParser(parser, jsonArray)
				
				if(src.isEmpty()) {
					// 直前の取得でカラのデータが帰ってきたら終了
					log.d("statuses: empty.")
					break
				}
				
				// 隙間の最新のステータスIDは取得データ末尾のステータスIDである
				max_id = column.parseRange(result, src).first
				
				addWithFilterConversationSummary(list_tmp, src)
			}
			if(bLastGap) {
				addOne(list_tmp, TootGap.mayNull(max_id, since_id))
			}
		}
		return result
	}
	
	fun getSearchGap(client:TootApiClient):TootApiResult? {
		if( gap !is TootSearchGap ) return null
		
		// https://mastodon2.juggler.jp/api/v2/search?q=gargron&type=accounts&offset=5

		val typeKey = when( gap.type ) {
			TootSearchGap.SearchType.Hashtag -> "hashtags"
			TootSearchGap.SearchType.Account -> "accounts"
			TootSearchGap.SearchType.Status -> "statuses"
		}
		val offset = when( gap.type ) {
			TootSearchGap.SearchType.Hashtag -> countTag
			TootSearchGap.SearchType.Account -> countAccount
			TootSearchGap.SearchType.Status -> countStatus
		}

		var path = String.format(
			Locale.JAPAN,
			Column.PATH_SEARCH_V2,
			column.search_query.encodePercent()
		)
		if(column.search_resolve) path += "&resolve=1"
		path += "&type=$typeKey&offset=$offset"
		
		val result = client.request(path)
		val jsonObject = result?.jsonObject
		if(jsonObject != null) {
			val tmp = parser.resultsV2(jsonObject)
			if(tmp != null) {
				list_tmp = ArrayList()
				addAll(list_tmp, tmp.hashtags)
				addAll(list_tmp, tmp.accounts)
				addAll(list_tmp, tmp.statuses)
				if(list_tmp?.isNotEmpty() == true) {
					addOne(list_tmp, TootSearchGap(gap.type))
				}
			}
		}
		return result
	}
}
