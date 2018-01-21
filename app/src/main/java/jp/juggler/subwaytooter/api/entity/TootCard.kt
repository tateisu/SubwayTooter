package jp.juggler.subwaytooter.api.entity

import org.json.JSONObject

import jp.juggler.subwaytooter.util.parseString

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
		url = src.parseString("url"),
		title = src.parseString("title"),
		description = src.parseString("description"),
		image = src.parseString("image"),
		
		type = src.parseString("type"),
		author_name = src.parseString("author_name"),
		author_url = src.parseString("author_url"),
		provider_name = src.parseString("provider_name"),
		provider_url = src.parseString("provider_url")
	
	)
}
