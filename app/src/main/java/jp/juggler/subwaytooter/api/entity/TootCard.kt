package jp.juggler.subwaytooter.api.entity

import org.json.JSONObject

import jp.juggler.subwaytooter.util.Utils

class TootCard(
	
	//	The url associated with the card
	val url : String?,
	
	//	The title of the card
	val title : String?,
	
	//	The card description
	val description : String?,
	
	//	The image associated with the card, if any
	val image : String?,
	
	val type : String?,
	val author_name : String?,
	val author_url : String?,
	val provider_name : String?,
	val provider_url : String?
) {
	
	constructor(src : JSONObject) : this(
		url = Utils.optStringX(src, "url"),
		title = Utils.optStringX(src, "title"),
		description = Utils.optStringX(src, "description"),
		image = Utils.optStringX(src, "image"),
		
		type = Utils.optStringX(src, "type"),
		author_name = Utils.optStringX(src, "author_name"),
		author_url = Utils.optStringX(src, "author_url"),
		provider_name = Utils.optStringX(src, "provider_name"),
		provider_url = Utils.optStringX(src, "provider_url")
	
	)
}
