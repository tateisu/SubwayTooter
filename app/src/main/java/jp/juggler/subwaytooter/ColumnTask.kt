package jp.juggler.subwaytooter

import android.content.Context
import android.content.SharedPreferences
import android.os.AsyncTask
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.WordTrieTree
import org.json.JSONObject
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
	
	val isMisskey : Boolean
		get() = access_info.isMisskey
	
	val misskeyVersion : Int
		get() = access_info.misskeyVersion
	
	val pref : SharedPreferences
		get() = column.app_state.pref
	
	internal fun JSONObject.putMisskeyUntil(id : EntityId?) = putMisskeyUntil(column, id)
	internal fun JSONObject.putMisskeySince(id : EntityId?) = putMisskeySince(column, id)
	internal fun JSONObject.addMisskeyNotificationFilter() = addMisskeyNotificationFilter(column)
	
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
	
	internal fun JSONObject.addRangeMisskey(bBottom : Boolean) =
		addRangeMisskey(column,bBottom)
}
