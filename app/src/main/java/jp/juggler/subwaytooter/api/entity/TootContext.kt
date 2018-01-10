package jp.juggler.subwaytooter.api.entity

import org.json.JSONObject

import jp.juggler.subwaytooter.api.TootParser

class TootContext(
	//	The ancestors of the status in the conversation, as a list of Statuses
	val ancestors : TootStatus.List?,
	// descendants	The descendants of the status in the conversation, as a list of Statuses
	val descendants : TootStatus.List?
) {
	
	constructor(parser : TootParser, src : JSONObject) : this(
		ancestors = TootStatus.parseListOrNull(parser, src.optJSONArray("ancestors")),
		descendants = TootStatus.parseListOrNull(parser, src.optJSONArray("descendants"))
	
	)
	
}
