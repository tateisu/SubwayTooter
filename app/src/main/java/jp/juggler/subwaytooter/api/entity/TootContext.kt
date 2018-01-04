package jp.juggler.subwaytooter.api.entity

import org.json.JSONObject

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.util.LogCategory

class TootContext(parser : TootParser, src : JSONObject) {
	
	//	The ancestors of the status in the conversation, as a list of Statuses
	val ancestors : TootStatus.List?
	
	// descendants	The descendants of the status in the conversation, as a list of Statuses
	val descendants : TootStatus.List?
	
	init {
		this.ancestors = TootStatus.parseListOrNull(parser, src.optJSONArray("ancestors"))
		this.descendants = TootStatus.parseListOrNull(parser, src.optJSONArray("descendants"))
	}
	
	companion object {
		private val log = LogCategory("TootContext")
		
		fun parse(parser : TootParser, src : JSONObject?) : TootContext? {
			if(src == null) return null
			try {
				return TootContext(parser, src)
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "TootContext.parse failed.")
				return null
			}
			
		}
	}
	
}
