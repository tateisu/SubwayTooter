package jp.juggler.subwaytooter.api.entity

import org.json.JSONObject

import java.util.ArrayList

import jp.juggler.subwaytooter.api.TootParser

class TootResults(
	val accounts : ArrayList<TootAccount>, //	An array of matched Accounts
	val statuses : TootStatus.List, //	An array of matched Statuses
	val hashtags : ArrayList<TootTag> //	An array of matched hashtags
) {
	
	constructor(parser : TootParser, src : JSONObject) : this(
		accounts = TootAccount.parseList(
			parser.context,
			parser.accessInfo,
			src.optJSONArray("accounts")
		),
		statuses = TootStatus.parseList(parser, src.optJSONArray("statuses")),
		hashtags = TootTag.parseStringArray(src.optJSONArray("hashtags"))
	)
	
}
