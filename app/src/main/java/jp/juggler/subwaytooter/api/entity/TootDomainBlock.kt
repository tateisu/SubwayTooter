package jp.juggler.subwaytooter.api.entity

import org.json.JSONArray

import java.util.ArrayList

class TootDomainBlock(
	val domain : String
) {
	
	companion object {
		
		fun parseList(array : JSONArray?) : ArrayList<TootDomainBlock> {
			val result = ArrayList<TootDomainBlock>()
			if(array != null) {
				val array_size = array.length()
				result.ensureCapacity(array_size)
				for(i in 0 until array_size) {
					val sv = array.optString(i)
					if(sv?.isNotEmpty() == true) {
						result.add(TootDomainBlock(sv))
					}
				}
			}
			return result
		}
	}
	
}
