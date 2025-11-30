package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.entity.TootAttachment.Companion.tootAttachment
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
                    if (it.string("type") == "Document") {
                        tootAttachment(ServiceType.NOTESTOCK, it)
                            .let { mediaAttachments.add(it) }
                    }
                } catch (ex: Throwable) {
                    log.e(ex, "APAttachment ctor failed.")
                }
            }
    }
}
