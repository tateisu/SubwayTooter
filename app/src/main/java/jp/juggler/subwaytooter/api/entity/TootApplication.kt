package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.util.JsonObject

class TootApplication(parser: TootParser, src: JsonObject) {

    val name: String?

    @Suppress("unused")
    private val website: String?

    //	val description : String?

    init {
        if (parser.serviceType == ServiceType.MISSKEY) {
            name = src.string("name")
            website = null
            //			description = src.string("description")
        } else {
            name = src.string("name")
            website = src.string("website")
            //			description = website
        }
    }
}
