package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.util.data.JsonObject

class TootContext(
    // The ancestors of the status in the conversation, as a list of Statuses
    val ancestors: ArrayList<TootStatus>?,
    // descendants	The descendants of the status in the conversation, as a list of Statuses
    val descendants: ArrayList<TootStatus>?,
    // fedibird: 参照
    val references: ArrayList<TootStatus>?,
) {
    constructor(parser: TootParser, src: JsonObject) : this(
        ancestors = parseListOrNull(::TootStatus, parser, src.jsonArray("ancestors")),
        descendants = parseListOrNull(::TootStatus, parser, src.jsonArray("descendants")),
        references = parseListOrNull(::TootStatus, parser, src.jsonArray("references")),
    )
}
