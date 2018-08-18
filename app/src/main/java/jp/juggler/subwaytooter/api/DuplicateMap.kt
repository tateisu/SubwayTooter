package jp.juggler.subwaytooter.api

import jp.juggler.subwaytooter.api.entity.*
import java.util.ArrayList
import java.util.HashSet

class DuplicateMap {
	
	private val set_status_id = HashSet<EntityId>()
	private val set_notification_id = HashSet<EntityId>()
	private val set_report_id = HashSet<EntityId>()
	private val set_account_id = HashSet<EntityId>()
	private val set_status_uri = HashSet<String>()
	
	fun clear() {
		set_status_id.clear()
		set_notification_id.clear()
		set_report_id.clear()
		set_account_id.clear()
		set_status_uri.clear()
	}
	
	fun isDuplicate(o : TimelineItem) : Boolean {
		
		when(o) {

			is TootStatus ->{
				val uri = o.uri
				val url = o.url
				when {
					uri?.isNotEmpty() == true -> {
						if(set_status_uri.contains(uri)) return true
						set_status_uri.add(uri)
					}
					url?.isNotEmpty() == true -> {
						// URIとURLで同じマップを使いまわすが、害はないと思う…
						if(set_status_uri.contains(url)) return true
						set_status_uri.add(url)
					}
					else -> {
						if(set_status_id.contains(o.id)) return true
						set_status_id.add(o.id)
					}
				}
			}

			is TootNotification -> {
				if(set_notification_id.contains(o.id)) return true
				set_notification_id.add(o.id)
				
			}

			is TootReport -> {
				if(set_report_id.contains(o.id)) return true
				set_report_id.add(o.id)
				
			}

			is TootAccountRef -> {
				val id = o.get().id
				if(set_account_id.contains(id)) return true
				set_account_id.add(id)
			}
		}
		
		return false
	}
	
	fun filterDuplicate(src : Collection<TimelineItem>?) : ArrayList<TimelineItem> {
		val list_new = ArrayList<TimelineItem>()
		if( src != null ) {
			for(o in src) {
				if(isDuplicate(o)) continue
				list_new.add(o)
			}
		}
		return list_new
	}
}
