package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.util.JsonObject
import java.util.*

class TootResults private constructor(
	// An array of matched Accounts
	val accounts : ArrayList<TootAccountRef>,
	// An array of matched Statuses
	val statuses : ArrayList<TootStatus>,
	// An array of matched hashtags
	val hashtags : ArrayList<TootTag>
) {
	
	var searchApiVersion = 0 // 0 means not from search API. such as trend tags.
	
	constructor(parser : TootParser, src : JsonObject) : this(
		accounts = parser.accountList(src.parseJsonArray("accounts")),
		statuses = parser.statusList(src.parseJsonArray("statuses")),
		hashtags = TootTag.parseList(parser, src.parseJsonArray("hashtags"))
	)
}
