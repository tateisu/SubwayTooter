package jp.juggler.subwaytooter

import android.content.Context
import android.content.SharedPreferences
import android.os.AsyncTask
import android.os.SystemClock
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.JsonObject
import jp.juggler.util.WordTrieTree
import jp.juggler.util.notEmpty
import java.util.concurrent.atomic.AtomicBoolean

enum class ColumnTaskType {
	LOADING,
	REFRESH_TOP,
	REFRESH_BOTTOM,
	GAP
}

abstract class ColumnTask(
	val column : Column,
	val ctType : ColumnTaskType
) : AsyncTask<Void, Void, TootApiResult?>() {
	
	override fun onCancelled(result : TootApiResult?) {
		onPostExecute(null)
	}
	
	val ctStarted = AtomicBoolean(false)
	val ctClosed = AtomicBoolean(false)
	
	var parser = TootParser(context, access_info, highlightTrie = highlight_trie)
	
	var list_tmp : ArrayList<TimelineItem>? = null
	
	val context : Context
		get() = column.context
	
	val access_info : SavedAccount
		get() = column.access_info
	
	val highlight_trie : WordTrieTree?
		get() = column.highlight_trie
	
	val isPseudo : Boolean
		get() = access_info.isPseudo
	
	val isMastodon : Boolean
		get() = access_info.isMastodon
	
	val isMisskey : Boolean
		get() = access_info.isMisskey
	
	val misskeyVersion : Int
		get() = access_info.misskeyVersion
	
	val pref : SharedPreferences
		get() = column.app_state.pref
	
	internal fun JsonObject.putMisskeyUntil(id : EntityId?) = putMisskeyUntil(column, id)
	internal fun JsonObject.putMisskeySince(id : EntityId?) = putMisskeySince(column, id)
	internal fun JsonObject.addMisskeyNotificationFilter() = addMisskeyNotificationFilter(column)
	internal fun JsonObject.addRangeMisskey(bBottom : Boolean) = addRangeMisskey(column, bBottom)
	
	internal fun addOne(
		dstArg : ArrayList<TimelineItem>?,
		item : TimelineItem?
	) : ArrayList<TimelineItem> {
		val dst = dstArg ?: ArrayList()
		if(item != null) dst.add(item)
		return dst
	}
	
	internal fun addOneFirst(
		dstArg : ArrayList<TimelineItem>?,
		item : TimelineItem?
	) : ArrayList<TimelineItem> {
		val dst = dstArg ?: ArrayList()
		if(item != null) dst.add(0, item)
		return dst
	}
	
	internal fun addWithFilterStatus(
		dstArg : ArrayList<TimelineItem>?,
		src : List<TootStatus>
	) : ArrayList<TimelineItem> {
		val dst = dstArg ?: ArrayList(src.size)
		for(status in src) {
			if(! column.isFiltered(status)) {
				dst.add(status)
			}
		}
		return dst
	}
	
	internal fun addWithFilterConversationSummary(
		dstArg : ArrayList<TimelineItem>?,
		src : List<TootConversationSummary>
	) : ArrayList<TimelineItem> {
		val dst = dstArg ?: ArrayList(src.size)
		for(cs in src) {
			if(! column.isFiltered(cs.last_status)) {
				dst.add(cs)
			}
		}
		return dst
	}
	
	internal fun addWithFilterNotification(
		dstArg : ArrayList<TimelineItem>?,
		src : List<TootNotification>
	) : ArrayList<TimelineItem> {
		val dst = dstArg ?: ArrayList(src.size)
		for(item in src) {
			if(! column.isFiltered(item)) dst.add(item)
		}
		return dst
	}
	
	internal val profileDirectoryPath : String
		get() {
			val order = when(val q = column.search_query) {
				"new" -> q
				else -> "active"
			}
			val local = ! column.search_resolve
			return "${Column.PATH_PROFILE_DIRECTORY}&order=$order&local=$local"
		}
	
	internal fun getAnnouncements(
		client : TootApiClient,
		force : Boolean = false
	) : TootApiResult? {
		// announcements is shown only mastodon home timeline, not pseudo.
		if(isMastodon && ! isPseudo) {
			// force (initial loading) always reload announcements
			// other (refresh,gap) may reload announcements if last load is too old
			if(force || SystemClock.elapsedRealtime() - column.announcementUpdated >= 15 * 60000L) {
				if(force) {
					column.announcements = null
					column.announcementUpdated = SystemClock.elapsedRealtime()
					client.publishApiProgress("announcements reset")
				}
				val (instance, _) = TootInstance.get(client)
				if(instance?.versionGE(TootInstance.VERSION_3_1_0_rc1) == true) {
					val result = client.request("/api/v1/announcements")
						?: return null // cancelled.
					val code = result.response?.code ?: 0
					when(code) {
						// just skip load announcements for 4xx error if server does not support announcements.
						in 400 until 500 -> {
						}

						else -> {
							column.announcements =
								parseList(::TootAnnouncement, parser, result.jsonArray)
									.notEmpty()
							column.announcementUpdated = SystemClock.elapsedRealtime()
							client.publishApiProgress("announcements loaded")
							// other errors such as network or server fails will stop column loading.
							return result
						}
					}
				}
			}
		}
		return TootApiResult()
	}
}
