package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.util.HTMLDecoder
import jp.juggler.util.filterNotEmpty
import jp.juggler.util.parseString
import org.json.JSONObject

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
	val author_name : String? = null,
	val author_url : String? = null,
	val provider_name : String? = null,
	val provider_url : String? = null,
	
	val originalStatus : TootStatus? = null
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
	
	constructor(parser : TootParser, src : TootStatus) : this(
		originalStatus = src,
		url = src.url,
		title = "${src.account.display_name} @${parser.getFullAcct(src.account.acct)}",
		description = src.spoiler_text.filterNotEmpty()
			?: if(parser.serviceType == ServiceType.MISSKEY) {
				src.content
			} else {
				HTMLDecoder.encodeEntity(src.content ?: "")
			},
		image = src.media_attachments?.firstOrNull()?.urlForThumbnail ?: src.account.avatar_static,
		type = "photo"
	)
}
