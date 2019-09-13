package jp.juggler.subwaytooter.api.entity

import org.json.JSONObject

import java.util.ArrayList

import jp.juggler.subwaytooter.api.TootParser

class TootResults private constructor(
	// An array of matched Accounts
	val accounts : ArrayList<TootAccountRef>,
	// An array of matched Statuses
	val statuses : ArrayList<TootStatus>,
	// An array of matched hashtags
	val hashtags : ArrayList<TootTag>
) {
	var searchApiVersion = 0 // 0 means not from search API. such as trend tags.
	
	constructor(parser : TootParser, src : JSONObject):this(
		accounts = parser.accountList(src.optJSONArray("accounts")),
		statuses = parser.statusList(src.optJSONArray("statuses")),
		hashtags = TootTag.parseList( parser, src.optJSONArray("hashtags"))
	)
}
