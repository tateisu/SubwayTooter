package jp.juggler.subwaytooter.api.entity

import org.json.JSONObject

import java.util.ArrayList

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.util.Utils

class TootResults(
	val accounts : TootAccount.List, //	An array of matched Accounts
	val statuses : TootStatus.List, //	An array of matched Statuses
	val hashtags : ArrayList<String> //	An array of matched hashtags, as strings
) {
	
	constructor(parser : TootParser, src : JSONObject) : this(
		accounts = TootAccount.parseList(parser.context, parser.accessInfo, src.optJSONArray("accounts")),
		statuses = TootStatus.parseList(parser, src.optJSONArray("statuses")),
		hashtags = Utils.parseStringArray(src.optJSONArray("hashtags"))
	)
	
}
