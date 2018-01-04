package jp.juggler.subwaytooter.api.entity

import org.json.JSONObject

import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils

class TootCard(src : JSONObject) {
	
	//	The url associated with the card
	val url : String?
	
	//	The title of the card
	val title : String?
	
	//	The card description
	val description : String?
	
	//	The image associated with the card, if any
	val image : String?
	
	val type : String?
	val author_name : String?
	val author_url : String?
	val provider_name : String?
	val provider_url : String?
	
	init {
		this.url = Utils.optStringX(src, "url")
		this.title = Utils.optStringX(src, "title")
		this.description = Utils.optStringX(src, "description")
		this.image = Utils.optStringX(src, "image")
		
		this.type = Utils.optStringX(src, "type")
		this.author_name = Utils.optStringX(src, "author_name")
		this.author_url = Utils.optStringX(src, "author_url")
		this.provider_name = Utils.optStringX(src, "provider_name")
		this.provider_url = Utils.optStringX(src, "provider_url")
	}
	
	companion object {
		private val log = LogCategory("TootCard")
		
		fun parse(src : JSONObject?) : TootCard? {
			return if(src == null) null else try {
				TootCard(src)
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "parse failed.")
				null
			}
		}
	}
	
}
