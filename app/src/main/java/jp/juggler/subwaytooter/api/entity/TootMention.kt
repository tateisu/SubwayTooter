package jp.juggler.subwaytooter.api.entity

import jp.juggler.util.data.JsonObject

class TootMention(
    val id: EntityId, // Account ID
    val url: String, // URL of user's profile (can be remote)
    val acct: Acct, // Equals username for local users, includes @domain for remote ones
    val username: String, // The username of the account
) {

    constructor(
        id: EntityId, // Account ID
        url: String, // URL of user's profile (can be remote)
        acctArg: String, // Equals username for local users, includes @domain for remote ones
        username: String, // The username of the account
    ) : this(id, url, Acct.parse(acctArg), username)

    constructor(src: JsonObject) : this(
        id = EntityId.mayDefault(src.string("id")),
        url = src.stringOrThrow("url"),
        acct = Acct.parse(src.stringOrThrow("acct")),
        username = src.stringOrThrow("username")
    )
}
