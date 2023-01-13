package jp.juggler.subwaytooter.api.entity

import android.content.Context
import jp.juggler.subwaytooter.R
import jp.juggler.util.*
import jp.juggler.util.data.JsonArray
import jp.juggler.util.data.JsonObject
import jp.juggler.util.log.LogCategory

class TootFilter(src: JsonObject) : TimelineItem() {

    class FilterContext(val name: String, val bit: Int, val caption_id: Int)

    companion object {
        val log = LogCategory("TootFilter")

        @Suppress("unused")
        const val CONTEXT_ALL = 31
        const val CONTEXT_NONE = 0
        const val CONTEXT_HOME = 1
        const val CONTEXT_NOTIFICATIONS = 2
        const val CONTEXT_PUBLIC = 4
        const val CONTEXT_THREAD = 8
        const val CONTEXT_PROFILE = 16

        private val CONTEXT_LIST = arrayOf(
            FilterContext("home", CONTEXT_HOME, R.string.filter_home),
            FilterContext("notifications", CONTEXT_NOTIFICATIONS, R.string.filter_notification),
            FilterContext("public", CONTEXT_PUBLIC, R.string.filter_public),
            FilterContext("thread", CONTEXT_THREAD, R.string.filter_thread),
            FilterContext("account", CONTEXT_PROFILE, R.string.filter_profile)
        )

        private val CONTEXT_MAP = CONTEXT_LIST.associateBy { it.name }

        private fun parseFilterContext(src: JsonArray?): Int {
            var n = 0
            src?.stringList()?.forEach { key ->
                val v = CONTEXT_MAP[key]
                if (v != null) n += v.bit
            }
            return n
        }

        fun parseList(src: JsonArray?) =
            ArrayList<TootFilter>().also { result ->
                src?.objectList()?.forEach {
                    try {
                        result.add(TootFilter(it))
                    } catch (ex: Throwable) {
                        log.e(ex, "TootFilter parse failed.")
                    }
                }
            }
    }

    val id: EntityId
    val phrase: String
    val context: Int
    private val expires_at: String? // null is not specified, or "2018-07-06T00:59:13.161Z"
    val time_expires_at: Long // 0L if not specified
    val irreversible: Boolean
    val whole_word: Boolean

    init {
        id = EntityId.mayDefault(src.string("id"))
        phrase = src.string("phrase") ?: error("missing phrase")
        context = parseFilterContext(src.jsonArray("context"))
        expires_at = src.string("expires_at") // may null
        time_expires_at = TootStatus.parseTime(expires_at)
        irreversible = src.optBoolean("irreversible")
        whole_word = src.optBoolean("whole_word")
    }

    fun getContextNames(context: Context): ArrayList<String> {
        val result = ArrayList<String>()
        for (item in CONTEXT_LIST) {
            if ((item.bit and this.context) != 0) result.add(context.getString(item.caption_id))
        }
        return result
    }
}
