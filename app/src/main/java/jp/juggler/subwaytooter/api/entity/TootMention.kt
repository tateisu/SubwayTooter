package jp.juggler.subwaytooter.api.entity

import jp.juggler.util.notEmptyOrThrow
import jp.juggler.util.parseString
import org.json.JSONObject

class TootMention(
	val id : EntityId, // Account ID
	val url : String, // URL of user's profile (can be remote)
	val acct : String, // Equals username for local users, includes @domain for remote ones
	val username : String // The username of the account
) {
	
	constructor(src : JSONObject) : this(
		id = EntityId.mayDefault(src.parseString("id") ),
		url = src.notEmptyOrThrow("url"),
		acct = src.notEmptyOrThrow("acct"),
		username = src.notEmptyOrThrow("username")
	
	)
	
}
