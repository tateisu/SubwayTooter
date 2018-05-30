package jp.juggler.subwaytooter.api.entity

import org.json.JSONObject

import java.util.ArrayList

import jp.juggler.subwaytooter.api.TootParser

class TootResultsV2(
	val accounts : ArrayList<TootAccountRef>, //	An array of matched Accounts
	val statuses : ArrayList<TootStatus>, //	An array of matched Statuses
	val hashtags : ArrayList<TootTrendTag> //	An array of matched hashtags
) {
	
	constructor(parser : TootParser, src : JSONObject) : this(
		accounts = parser.accountList(src.optJSONArray("accounts")),
		statuses = parser.statusList( src.optJSONArray("statuses")),
		hashtags = parser.trendTagList(src.optJSONArray("hashtags"))
	)
}
