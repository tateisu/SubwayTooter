package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.parseLong
import jp.juggler.subwaytooter.util.parseString
import org.json.JSONArray
import org.json.JSONObject

class TootFilter( src: JSONObject){
	companion object {
		val log = LogCategory("TootFilter")
		
		const val CONTEXT_ALL =15
		const val CONTEXT_NONE =0
		const val CONTEXT_HOME =1
		const val CONTEXT_NOTIFICATIONS = 2
		const val CONTEXT_PUBLIC=4
		const val CONTEXT_THREAD=8
		
		private fun parseFilterContext(src:JSONArray?):Int{
			var n = 0
			if( src != null) {
				for(i in 0 until src.length()) {
					val v = src.optString(i)
					n += when(v) {
						"home" -> CONTEXT_HOME
						"notifications" -> CONTEXT_NOTIFICATIONS
						"public" -> CONTEXT_PUBLIC
						"thread" -> CONTEXT_THREAD
						else -> 0
					}
				}
			}
			return if(n==0) CONTEXT_ALL else n
		}
		
		fun parseList(src:JSONArray?): ArrayList<TootFilter>{
			val result = ArrayList<TootFilter>()
			if(src!=null){
				for(i in 0 until src.length()) {
					try{
						result.add(TootFilter(src.optJSONObject(i)))
					}catch(ex:Throwable){
						log.trace(ex)
						log.e(ex,"TootFilter parse failed.")
					}
				}
			}
			return result
		}
	}
	
	val id:Long
	val phrase :String
	val context: Int
	val expires_at : String? // null is not specified, or "2018-07-06T00:59:13.161Z"
	val time_expires_at : Long // 0L if not specified
	
	init{
		id = src.parseLong("id") ?: throw RuntimeException("missing id")
		phrase = src.parseString("phrase")?: throw RuntimeException("missing phrase")
		context = parseFilterContext(src.optJSONArray("context"))
		expires_at = src.parseString("expires_at") // may null
		time_expires_at = TootStatus.parseTime(expires_at)
	}
}