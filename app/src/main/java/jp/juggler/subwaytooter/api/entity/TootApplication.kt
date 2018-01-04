package jp.juggler.subwaytooter.api.entity

import org.json.JSONObject

import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils

class TootApplication {
	
	var name : String? = null
	var website : String? = null
	
	companion object {
		
		private val log = LogCategory("TootApplication")
		
		fun parse(src : JSONObject?) : TootApplication? {
			if(src == null) return null
			try {
				val dst = TootApplication()
				dst.name = Utils.optStringX(src, "name")
				dst.website = Utils.optStringX(src, "website")
				return dst
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "TootApplication.parse failed.")
				return null
			}
			
		}
	}
}
