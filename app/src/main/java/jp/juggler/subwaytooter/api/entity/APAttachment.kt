package jp.juggler.subwaytooter.api.entity

import jp.juggler.util.JsonArray
import jp.juggler.util.JsonObject
import jp.juggler.util.LogCategory
import jp.juggler.util.cast

class APAttachment(jsonArray: JsonArray?) {

    companion object {
        private val log = LogCategory("APAttachment")
    }

    val mediaAttachments = ArrayList<TootAttachmentLike>()

    init {
        jsonArray
            ?.mapNotNull { it.cast<JsonObject>() }
            ?.forEach { it ->
                try {
                    when (it.string("type")) {
                        "Document" -> {
                            mediaAttachments.add(TootAttachment(ServiceType.NOTESTOCK, it))
                        }
                    }
                } catch (ex: Throwable) {
                    log.trace(ex)
                }
            }
    }
}
