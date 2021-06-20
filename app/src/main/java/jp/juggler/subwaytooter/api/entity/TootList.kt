package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.table.b2i
import jp.juggler.util.JsonObject
import jp.juggler.util.LogCategory
import jp.juggler.util.asciiPattern
import jp.juggler.util.groupEx
import java.util.*

class TootList(parser: TootParser, src: JsonObject) : TimelineItem(), Comparable<TootList> {

    val id: EntityId

    val title: String?

    // タイトルの数字列部分は数字の大小でソートされるようにしたい
    private val title_for_sort: ArrayList<Any>?

    // 内部で使用する
    var isRegistered: Boolean = false

    var userIds: ArrayList<EntityId>? = null

    init {
        if (parser.serviceType == ServiceType.MISSKEY) {
            id = EntityId.mayDefault(src.string("id"))
            title = src.string("name") ?: src.string("title") // v11,v10
            this.title_for_sort = makeTitleForSort(this.title)
            val user_list = ArrayList<EntityId>()
            userIds = user_list
            src.jsonArray("userIds")?.forEach {
                val id = EntityId.mayNull(it as? String)
                if (id != null) user_list.add(id)
            }
        } else {
            id = EntityId.mayDefault(src.string("id"))
            title = src.string("title")
            this.title_for_sort = makeTitleForSort(this.title)
        }
    }

    override fun getOrderId() = id

    companion object {
        private var log = LogCategory("TootList")

        private val reNumber = """(\d+)""".asciiPattern()

        private fun makeTitleForSort(title: String?): ArrayList<Any> {
            val list = ArrayList<Any>()
            if (title != null) {
                val m = reNumber.matcher(title)
                var last_end = 0
                while (m.find()) {
                    val match_start = m.start()
                    val match_end = m.end()
                    if (match_start > last_end) {
                        list.add(title.substring(last_end, match_start))
                    }
                    try {
                        list.add(m.groupEx(1)!!.toLong())
                    } catch (ignored: Throwable) {
                        list.clear()
                        list.add(title)
                        return list
                    }

                    last_end = match_end
                }
                val end = title.length
                if (end > last_end) {
                    list.add(title.substring(last_end, end))
                }
            }
            return list
        }

        private fun compareLong(a: Long, b: Long): Int {
            return a.compareTo(b)
        }

        private fun compareString(a: String, b: String): Int {
            return a.compareTo(b)
        }
    }

    override fun compareTo(other: TootList): Int {
        val la = this.title_for_sort
        val lb = other.title_for_sort

        if (la == null) {
            return if (lb == null) 0 else -1
        } else if (lb == null) {
            return 1
        }

        val sa = la.size
        val sb = lb.size

        var i = 0
        while (true) {
            val oa = if (i >= sa) null else la[i]
            val ob = if (i >= sb) null else lb[i]

            if (oa == null && ob == null) return 0

            val delta = when {
                oa == null -> -1
                ob == null -> 1
                oa is Long && ob is Long -> compareLong(oa, ob)
                oa is String && ob is String -> compareString(oa, ob)
                else -> (ob is Long).b2i() - (oa is Long).b2i()
            }
            log.d(
                "$oa ${
                    when {
                        delta < 0 -> "<"
                        delta > 0 -> ">"
                        else -> "="
                    }
                } $ob"
            )
            if (delta != 0) return delta
            ++i
        }
    }
}
