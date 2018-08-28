package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.util.notEmptyOrThrow
import jp.juggler.subwaytooter.util.parseString

import org.json.JSONArray
import org.json.JSONObject

open class TootTag(
	// The hashtag, not including the preceding #
	val name : String,
	// The URL of the hashtag. may null if generated from TootContext
	val url : String? = null
) : TimelineItem() {
	
	constructor(src : JSONObject) : this(
		name = src.notEmptyOrThrow("name"),
		url = src.parseString("url")
	)
	
	companion object {

		// 検索結果のhashtagリストから生成する
		fun parseTootTagList(parser:TootParser,array : JSONArray?) : ArrayList<TootTag> {
			val result = ArrayList<TootTag>()
			if( parser.serviceType == ServiceType.MISSKEY){
				if(array != null) {
					for(i in 0 until array.length()) {
						val sv = array.parseString(i)
						if(sv?.isNotEmpty() == true) {
							result.add(TootTag(name = sv))
						}
					}
				}
				
			}else {
				if(array != null) {
					for(i in 0 until array.length()) {
						val sv = array.parseString(i)
						if(sv?.isNotEmpty() == true) {
							result.add(TootTag(name = sv))
						}
					}
				}
			}
			
			return result
		}
		
	}
}
