package jp.juggler.subwaytooter.api.entity

import org.json.JSONObject

import jp.juggler.subwaytooter.util.Utils

class TootMention(
	val id : Long, // Account ID
	val url : String, // URL of user's profile (can be remote)
	val acct : String, // Equals username for local users, includes @domain for remote ones
	val username : String // The username of the account
) {
	
	constructor(src : JSONObject) : this(
		id = Utils.optLongX(src, "id"),
		url = src.notEmptyOrThrow("url"),
		acct = src.notEmptyOrThrow("acct"),
		username = src.notEmptyOrThrow("username")
	
	)
	
}
