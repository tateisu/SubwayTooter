package jp.juggler.subwaytooter.api.entity

import org.json.JSONObject

import java.util.ArrayList

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.util.LogCategory
import jp.juggler.subwaytooter.util.Utils

class TootResults(parser : TootParser, src : JSONObject) {
	
	//	An array of matched Accounts
	val accounts : TootAccount.List
	
	//	An array of matched Statuses
	val statuses : TootStatus.List
	
	//	An array of matched hashtags, as strings
	val hashtags : ArrayList<String>
	
	init {
		this.accounts = TootAccount.parseList(parser.context, parser.accessInfo, src.optJSONArray("accounts"))
		this.statuses = TootStatus.parseList(parser, src.optJSONArray("statuses"))
		this.hashtags = Utils.parseStringArray(src.optJSONArray("hashtags"))
	}
	
	companion object {
		
		private val log = LogCategory("TootResults")
		
		fun parse(parser : TootParser, src : JSONObject?) : TootResults? {
			src ?: return null
			return try {
				TootResults(parser, src)
			} catch(ex : Throwable) {
				log.trace(ex)
				log.e(ex, "parse failed.")
				null
			}
		}
	}
}
