package jp.juggler.subwaytooter.api.entity

import org.json.JSONObject

import jp.juggler.subwaytooter.api.TootParser

class TootContext(
	//	The ancestors of the status in the conversation, as a list of Statuses
	val ancestors : ArrayList<TootStatus>?,
	// descendants	The descendants of the status in the conversation, as a list of Statuses
	val descendants : ArrayList<TootStatus>?
) {
	
	constructor(parser : TootParser, src : JSONObject) : this(
		ancestors = parseListOrNull(::TootStatus,parser, src.optJSONArray("ancestors")),
		descendants = parseListOrNull(::TootStatus,parser, src.optJSONArray("descendants"))
	
	)
	
}
