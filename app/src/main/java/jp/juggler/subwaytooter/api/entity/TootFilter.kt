package jp.juggler.subwaytooter.api.entity

import android.content.Context
import jp.juggler.subwaytooter.R
import jp.juggler.util.*

class TootFilter(src : JsonObject) : TimelineItem() {
	
	class FilterContext(val name : String, val bit : Int, val caption_id : Int)
	
	companion object {
		val log = LogCategory("TootFilter")
		
		@Suppress("unused")
		const val CONTEXT_ALL = 15
		const val CONTEXT_NONE = 0
		const val CONTEXT_HOME = 1
		const val CONTEXT_NOTIFICATIONS = 2
		const val CONTEXT_PUBLIC = 4
		const val CONTEXT_THREAD = 8
		
		private val CONTEXT_LIST = arrayOf(
			FilterContext("home", CONTEXT_HOME, R.string.filter_home),
			FilterContext("notifications", CONTEXT_NOTIFICATIONS, R.string.filter_notification),
			FilterContext("public", CONTEXT_PUBLIC, R.string.filter_public),
			FilterContext("thread", CONTEXT_THREAD, R.string.filter_thread)
		)
		
		private val CONTEXT_MAP = CONTEXT_LIST.map { Pair(it.name, it) }.toMap()
		
		private fun parseFilterContext(src : JsonArray?) : Int {
			var n = 0
			src?.toStringList()?.forEach { key ->
				val v = CONTEXT_MAP[key]
				if(v != null) n += v.bit
			}
			return n
		}
		
		fun parseList(src : JsonArray?) =
			ArrayList<TootFilter>().also { result ->
				src?.toObjectList()?.forEach {
					try {
						result.add(TootFilter(it))
					} catch(ex : Throwable) {
						log.trace(ex)
						log.e(ex, "TootFilter parse failed.")
					}
				}
			}
	}
	
	val id : EntityId
	val phrase : String
	val context : Int
	private val expires_at : String? // null is not specified, or "2018-07-06T00:59:13.161Z"
	val time_expires_at : Long // 0L if not specified
	val irreversible : Boolean
	val whole_word : Boolean
	
	init {
		id = EntityId.mayDefault(src.parseString("id"))
		phrase = src.parseString("phrase") ?: error("missing phrase")
		context = parseFilterContext(src.parseJsonArray("context"))
		expires_at = src.parseString("expires_at") // may null
		time_expires_at = TootStatus.parseTime(expires_at)
		irreversible = src.optBoolean("irreversible")
		whole_word = src.optBoolean("whole_word")
	}
	
	fun getContextNames(context : Context) : ArrayList<String> {
		val result = ArrayList<String>()
		for(item in CONTEXT_LIST) {
			if((item.bit and this.context) != 0) result.add(context.getString(item.caption_id))
		}
		return result
	}
}