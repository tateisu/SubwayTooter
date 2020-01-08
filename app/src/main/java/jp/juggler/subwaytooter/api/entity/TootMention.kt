package jp.juggler.subwaytooter.api.entity

import jp.juggler.util.JsonObject

class TootMention(
	val id : EntityId, // Account ID
	val url : String, // URL of user's profile (can be remote)
	val acct : String, // Equals username for local users, includes @domain for remote ones
	val username : String // The username of the account
) {
	
	constructor(src : JsonObject) : this(
		id = EntityId.mayDefault(src.string("id")),
		url = src.notEmptyOrThrow("url"),
		acct = src.notEmptyOrThrow("acct"),
		username = src.notEmptyOrThrow("username")
	)
}
