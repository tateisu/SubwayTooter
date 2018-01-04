package jp.juggler.subwaytooter.api.entity

import android.text.TextUtils

import org.json.JSONArray

import java.util.ArrayList

class TootDomainBlock private constructor( // domain
	val domain : String) {
	
	class List : ArrayList<TootDomainBlock>()
	
	companion object {
		
		fun parseList(array : JSONArray?) : List {
			val result = List()
			if(array != null) {
				val array_size = array.length()
				result.ensureCapacity(array_size)
				for(i in 0 until array_size) {
					val sv = array.optString(i)
					if(! TextUtils.isEmpty(sv)) {
						result.add(TootDomainBlock(sv))
					}
				}
			}
			return result
		}
	}
	
}
