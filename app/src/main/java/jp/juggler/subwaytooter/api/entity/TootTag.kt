package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.util.Utils
import org.json.JSONArray
import org.json.JSONObject

class TootTag(
	// The hashtag, not including the preceding #
	val name : String,
	// The URL of the hashtag. may null if generated from TootContext
	val url : String? =null
) :TimelineItem(){
	
	
	constructor(src : JSONObject):this(
		name = src.notEmptyOrThrow("name"),
		url = Utils.optStringX(src,"url")
	)
	
	companion object {
		// 検索結果のhashtagリストから生成する
		 fun parseStringArray( array: JSONArray?):ArrayList<TootTag>{
			val result = ArrayList<TootTag>()
			if(array != null) {
				for( i in 0 until array.length() ){
					val sv = Utils.optStringX(array, i)
					if( sv?.isNotEmpty() == true ) {
						result.add(TootTag(name = sv))
					}
				}
			}
			
			return result
		}
		
	}
}
