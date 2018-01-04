package jp.juggler.subwaytooter.api.entity

import org.json.JSONArray
import org.json.JSONObject

import java.util.ArrayList
import java.util.regex.Pattern

import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils

class TootList(src : JSONObject) : Comparable<TootList> {
	
	var id : Long = 0
	
	var title : String? = null
	
	// タイトルの数字列部分は数字の大小でソートされるようにしたい
	private var title_for_sort : ArrayList<Any>? = null
	
	// 内部で使用する
	var isRegistered : Boolean = false
	
	init {
		this.id = Utils.optLongX(src, "id")
		this.title = Utils.optStringX(src, "title")
		this.title_for_sort = makeTitleForSort(this.title)
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
			
			if(oa is Long && ob is Long) {
				val na = (oa as Long?) !!
				val nb = (ob as Long?) !!
				if(na < nb) return - 1
				if(na > nb) return 1
				++ i
				continue
			}
			
			val delta = oa.toString().compareTo(ob.toString())
			if(delta != 0) return delta
			++ i
		}
	}
	
	class List : ArrayList<TootList>()
	
	companion object {
		private val log = LogCategory("TootList")
		
		fun parse(src : JSONObject?) : TootList? {
			
			try {
				if(src != null) return TootList(src)
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "parse failed.")
			}
			return null
		}
		
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
		
		fun parseList(array : JSONArray?) : List {
			val result = TootList.List()
			if(array != null) {
				val array_size = array.length()
				result.ensureCapacity(array_size)
				for(i in 0 until array_size) {
					val obj = array.optJSONObject(i)
					if(obj != null) {
						val dst = TootList.parse(obj)
						if(dst != null) result.add(dst)
					}
				}
				result.sort()
			}
			return result
		}
	}
}
