package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.notEmptyOrThrow
import jp.juggler.subwaytooter.util.parseLong
import jp.juggler.subwaytooter.util.parseString
import org.json.JSONArray
import org.json.JSONObject

class TootTrendTag(
	name : String,
	url : String?,
	val history : ArrayList<History>
) : TootTag(name, url) {
	
	class History(src : JSONObject) {
		val day : Long
		val uses : Long
		val accounts : Long
		
		init {
			day = src.parseLong("day")
				?: throw RuntimeException("TootTrendTag.History: missing day")
			uses = src.parseLong("uses")
				?: throw RuntimeException("TootTrendTag.History: missing uses")
			accounts = src.parseLong("accounts")
				?: throw RuntimeException("TootTrendTag.History: missing accounts")
		}
		
	}
	
	constructor(src : JSONObject) : this(
		name = src.notEmptyOrThrow("name"),
		url = src.parseString("url"),
		history = parseHistory(src.optJSONArray("history"))
	)
	
	companion object {
		val log = LogCategory("TootTrendTag")
		
		private fun parseHistory(src : JSONArray?) : ArrayList<History> {
			src ?: throw RuntimeException("TootTrendTag: missing history")
			
			val dst = ArrayList<History>()
			for(i in 0 until src.length()) {
				try {
					dst.add(History(src.optJSONObject(i)))
				} catch(ex : Throwable) {
					log.e(ex, "history parse failed.")
				}
			}
			
			if(dst.isEmpty()) {
				throw RuntimeException("TootTrendTag: empty history")
			}
			
			return dst
		}
	}
}