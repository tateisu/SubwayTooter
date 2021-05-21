package jp.juggler.subwaytooter

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import jp.juggler.subwaytooter.api.ApiPath
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.TimelineItem
import jp.juggler.subwaytooter.api.entity.TootAnnouncement
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.api.entity.parseList
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

enum class ColumnTaskType(val marker:Char) {
	LOADING('L'),
	REFRESH_TOP('T'),
	REFRESH_BOTTOM('B'),
	GAP('G')
}

abstract class ColumnTask(
	val column : Column,
	val ctType : ColumnTaskType
) {
	
	val ctStarted = AtomicBoolean(false)
	val ctClosed = AtomicBoolean(false)
	
	private var job : Job? = null
	
	val isCancelled : Boolean
		get() = job?.isCancelled ?: false
	
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
	
	private val isMastodon : Boolean
		get() = access_info.isMastodon
	
	val isMisskey : Boolean
		get() = access_info.isMisskey
	
	val misskeyVersion : Int
		get() = access_info.misskeyVersion
	
	val pref : SharedPreferences
		get() = column.app_state.pref
	
	internal fun JsonObject.addMisskeyNotificationFilter() = addMisskeyNotificationFilter(column)
	internal fun JsonObject.addRangeMisskey(bBottom : Boolean) = addRangeMisskey(column, bBottom)
	
	internal val profileDirectoryPath : String
		get() {
			val order = when(val q = column.search_query) {
				"new" -> q
				else -> "active"
			}
			val local = ! column.search_resolve
			return "${ApiPath.PATH_PROFILE_DIRECTORY}&order=$order&local=$local"
		}
	
	internal suspend fun getAnnouncements(
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
					when(result.response?.code ?: 0) {
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
	

	abstract suspend fun background() : TootApiResult?
	abstract suspend fun handleResult(result : TootApiResult?)

	fun cancel() {
		job?.cancel()
	}

	fun start() {
		job = EndlessScope.launch(Dispatchers.Main) {
			handleResult(
				try {
					withContext(Dispatchers.IO) { background() }
				} catch(ex : CancellationException) {
					null // キャンセルされたらresult==nullとする
				} catch(ex : Throwable) {
					// その他のエラー
					TootApiResult(ex.withCaption("error"))
				}
			)
		}
	}
}
