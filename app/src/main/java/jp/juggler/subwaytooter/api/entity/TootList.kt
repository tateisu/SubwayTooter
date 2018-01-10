package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.table.b2i
import org.json.JSONObject

import java.util.ArrayList
import java.util.regex.Pattern

import jp.juggler.subwaytooter.util.Utils

class TootList(
	val id : Long,
	val title : String?
) : Comparable<TootList> {
	
	// タイトルの数字列部分は数字の大小でソートされるようにしたい
	private val title_for_sort : ArrayList<Any>?
	
	// 内部で使用する
	var isRegistered : Boolean = false
	
	init {
		this.title_for_sort = makeTitleForSort(this.title)
	}
	
	constructor(src : JSONObject) : this(
		id = Utils.optLongX(src, "id"),
		title = Utils.optStringX(src, "title")
	)
	
	companion object {
		
		private val reNumber = Pattern.compile("(\\d+)")
		
		private fun makeTitleForSort(title : String?) : ArrayList<Any> {
			val list = ArrayList<Any>()
			if(title != null) {
				val m = reNumber.matcher(title)
				var last_end = 0
				while(m.find()) {
					val match_start = m.start()
					val match_end = m.end()
					if(match_start > last_end) {
						list.add(title.substring(last_end, match_start))
					}
					try {
						list.add(m.group(1).toLong(10))
					} catch(ex : Throwable) {
						list.clear()
						list.add(title)
						return list
					}
					
					last_end = match_end
				}
				val end = title.length
				if(end > last_end) {
					list.add(title.substring(last_end, end))
				}
			}
			return list
		}
	}
	
	override fun compareTo(other : TootList) : Int {
		val la = this.title_for_sort
		val lb = other.title_for_sort
		
		if(la == null) {
			return if(lb == null) 0 else - 1
		} else if(lb == null) {
			return 1
		}
		
		val sa = la.size
		val sb = lb.size
		
		var i = 0
		while(true) {
			
			val oa = if(i >= sa) null else la[i]
			val ob = if(i >= sb) null else lb[i]

			if(oa == null) {
				return if(ob == null) 0 else - 1
			} else if(ob == null) {
				return 1
			}
			
			val delta = when {
				oa is Long && ob is Long -> oa.compareTo(ob)
				oa is String && ob is String -> oa.compareTo(ob)
				else -> (oa is Long).b2i() - (ob is Long).b2i()
			}
			if(delta != 0) return delta
			++ i
		}
	}
	
}
