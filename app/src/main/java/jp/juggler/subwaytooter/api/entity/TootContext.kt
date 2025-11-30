package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.TootStatus.Companion.tootStatus
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
        ancestors = parseList(src.jsonArray("ancestors")) { tootStatus(parser, it) },
        descendants = parseList(src.jsonArray("descendants")) { tootStatus(parser, it) },
        references = parseList(src.jsonArray("references")) { tootStatus(parser, it) },
    )
}
