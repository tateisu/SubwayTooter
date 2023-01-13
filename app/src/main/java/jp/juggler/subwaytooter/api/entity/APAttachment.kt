package jp.juggler.subwaytooter.api.entity

import jp.juggler.util.data.JsonArray
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.cast
import jp.juggler.util.log.LogCategory

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
                    log.e(ex, "APAttachment ctor failed.")
                }
            }
    }
}
