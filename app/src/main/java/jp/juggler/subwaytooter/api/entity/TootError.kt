package jp.juggler.subwaytooter.api.entity

import org.json.JSONObject

import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils

class TootError(src:JSONObject) {
	
	//	A textual description of the error
	val error : String
	
	init{
		this.error = Utils.optStringX(src, "error") ?: ""
	}

	companion object {
		private val log = LogCategory("TootError")
		
		fun parse(src : JSONObject?) : TootError? {
			if(src == null) return null
			return try {
				TootError(src)
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "TootError.parse failed.")
				null
			}
			
		}
	}
	
}
