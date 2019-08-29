package jp.juggler.subwaytooter.api

import com.google.android.exoplayer2.Timeline
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.util.LogCategory
import java.util.ArrayList
import java.util.HashSet

class DuplicateMap {
	
	companion object {
		val log = LogCategory("DuplicateMap")
	}
	private val set_id = HashSet<EntityId>()
	private val set_uri = HashSet<String>()
	
	fun clear() {
		set_id.clear()
		set_uri.clear()
	}
	
	fun isDuplicate(o : TimelineItem) : Boolean {
		
		if(o is TootStatus) {
			val uri = o.uri
			val url = o.url
			when {
				uri.isNotEmpty() -> {
					if(set_uri.contains(uri)) return true
					set_uri.add(uri)
				}
				
				url?.isNotEmpty() == true -> {
					// URIとURLで同じマップを使いまわすが、害はないと思う…
					if(set_uri.contains(url)) return true
					set_uri.add(url)
				}
			}
		}

		when(o) {
			is TootReport,
			is TootStatus,
			is TootAccount,
			is TootAccountRef,
			is TootNotification -> {
				var id = o.getOrderId()
				if(id.notDefault){

					// Misskeyで時刻順ページングを行う際は重複排除は時刻ではなくオブジェクトIDを使う
					if( id.fromTime ){
						when(o) {
							is TootStatus -> id = o.id
						}
					}

					if(set_id.contains(id)) return true
					set_id.add(id)
				}
			}
		}
		
		return false
	}
	
	
	fun filterDuplicate(src : Collection<TimelineItem>?) : ArrayList<TimelineItem> {
		val list_new = ArrayList<TimelineItem>()
		if(src != null) {
			for(o in src) {
				if(isDuplicate(o) ){
					log.w("filterDuplicate: filter orderId ${o.getOrderId()}")
					continue
				}
				list_new.add(o)
			}
		}
		return list_new
	}
}
