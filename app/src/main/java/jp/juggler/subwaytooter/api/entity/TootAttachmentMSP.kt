package jp.juggler.subwaytooter.api.entity

import android.content.SharedPreferences
import jp.juggler.util.data.JsonArray
import jp.juggler.util.data.notBlank

class TootAttachmentMSP(
    val preview_url: String,
) : TootAttachmentLike {

    override val type: TootAttachmentType
        get() = TootAttachmentType.Unknown

    override val description: String?
        get() = null

    override fun urlForThumbnail() = preview_url

    override val urlForDescription: String
        get() = preview_url

    override val focusX: Float
        get() = 0f

    override val focusY: Float
        get() = 0f

    override fun hasUrl(url: String): Boolean = (url == this.preview_url)

    companion object {
        fun parseList(array: JsonArray?): ArrayList<TootAttachmentLike>? {
            if (array != null) {
                val array_size = array.size
                if (array_size > 0) {
                    val result = ArrayList<TootAttachmentLike>()
                    result.ensureCapacity(array_size)
                    array.forEach {
                        val sv = it?.toString()?.notBlank()
                        if (sv != null) result.add(TootAttachmentMSP(sv))
                    }
                    if (result.isNotEmpty()) return result
                }
            }
            return null
        }
    }
}
