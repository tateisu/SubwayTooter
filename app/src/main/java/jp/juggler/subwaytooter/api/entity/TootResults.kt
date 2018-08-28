package jp.juggler.subwaytooter.api.entity

import org.json.JSONObject

import java.util.ArrayList

import jp.juggler.subwaytooter.api.TootParser

class TootResults(parser : TootParser, src : JSONObject) {
	
	//	An array of matched Accounts
	val accounts : ArrayList<TootAccountRef>
	
	//	An array of matched Statuses
	val statuses : ArrayList<TootStatus>
	
	//	An array of matched hashtags
	val hashtags : ArrayList<TootTag>
	
	init {
		accounts = parser.accountList(src.optJSONArray("accounts"))
		statuses = parser.statusList(src.optJSONArray("statuses"))
		hashtags = TootTag.parseTootTagList(src.optJSONArray("hashtags"))
	}
	
}
