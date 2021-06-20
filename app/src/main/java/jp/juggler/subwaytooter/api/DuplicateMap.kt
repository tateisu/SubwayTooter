package jp.juggler.subwaytooter.api

import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.util.LogCategory
import java.util.*

class DuplicateMap {

    companion object {
        val log = LogCategory("DuplicateMap")
    }

    private val idSet = HashSet<EntityId>()
    private val uriSet = HashSet<String>()

    fun clear() {
        idSet.clear()
        uriSet.clear()
    }

    fun isDuplicate(o: TimelineItem): Boolean {

        if (o is TootStatus) {
            val uri = o.uri
            val url = o.url
            when {
                uri.isNotEmpty() -> {
                    if (uriSet.contains(uri)) return true
                    uriSet.add(uri)
                }

                url?.isNotEmpty() == true -> {
                    // URIとURLで同じマップを使いまわすが、害はないと思う…
                    if (uriSet.contains(url)) return true
                    uriSet.add(url)
                }
            }
        }

        when (o) {
			is TootReport,
			is TootStatus,
			is TootAccount,
			is TootAccountRef,
			is TootNotification,
			-> {
				val id = o.getOrderId()
				if (id.notDefaultOrConfirming) {
					if (idSet.contains(id)) return true
					idSet.add(id)
				}
			}
        }

        return false
    }

    fun filterDuplicate(src: Collection<TimelineItem>?): ArrayList<TimelineItem> {
        val listNew = ArrayList<TimelineItem>()
        if (src != null) {
            for (o in src) {
                if (isDuplicate(o)) {
                    log.d("filterDuplicate: filter orderId ${o.getOrderId()}")
                    continue
                }
                listNew.add(o)
            }
        }
        return listNew
    }
}
