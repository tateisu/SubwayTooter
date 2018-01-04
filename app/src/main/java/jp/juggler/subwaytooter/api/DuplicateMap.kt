package jp.juggler.subwaytooter.api

import java.util.ArrayList
import java.util.HashSet

import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.api.entity.TootReport
import jp.juggler.subwaytooter.api.entity.TootStatus

class DuplicateMap {
	
	private val set_status_id = HashSet<Long>()
	private val set_notification_id = HashSet<Long>()
	private val set_report_id = HashSet<Long>()
	private val set_account_id = HashSet<Long>()
	private val set_status_uri = HashSet<String>()
	
	fun clear() {
		set_status_id.clear()
		set_notification_id.clear()
		set_report_id.clear()
		set_account_id.clear()
		set_status_uri.clear()
	}
	
	private fun isDuplicate(o : Any) : Boolean {
		
		when(o) {

			is TootStatus ->{
				val uri = o.uri
				if( uri != null && uri.isNotEmpty() ){
					if(set_status_uri.contains(o.uri)) return true
					set_status_uri.add(o.uri)
				}else{
					if(set_status_id.contains(o.id)) return true
					set_status_id.add(o.id)
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

			is TootAccount -> {
				if(set_account_id.contains(o.id)) return true
				set_account_id.add(o.id)
			}
		}
		
		return false
	}
	
	fun filterDuplicate(src : Collection<Any>) : ArrayList<Any> {
		val list_new = ArrayList<Any>()
		for(o in src) {
			if(isDuplicate(o)) continue
			list_new.add(o)
		}
		
		return list_new
	}
	
}
