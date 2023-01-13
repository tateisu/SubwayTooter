package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.util.data.JsonObject

class TootResults private constructor(
    // An array of matched Accounts
    val accounts: ArrayList<TootAccountRef>,
    // An array of matched Statuses
    val statuses: ArrayList<TootStatus>,
    // An array of matched hashtags
    val hashtags: List<TootTag>,
) {

    var searchApiVersion = 0 // 0 means not from search API. such as trend tags.

    constructor(parser: TootParser, src: JsonObject) : this(
        accounts = parser.accountList(src.jsonArray("accounts")),
        statuses = parser.statusList(src.jsonArray("statuses")),
        hashtags = TootTag.parseList(parser, src.jsonArray("hashtags"))
    )
}
