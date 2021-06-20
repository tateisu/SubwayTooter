package jp.juggler.subwaytooter.api.entity

import android.content.SharedPreferences

enum class TootAttachmentType(val id: String) {
    Unknown("unknown"),
    Image("image"),
    Video("video"),
    GIFV("gifv"),
    Audio("audio")
}

interface TootAttachmentLike {

    val type: TootAttachmentType
    val description: String?

    // url for thumbnail, or null or empty
    fun urlForThumbnail(pref: SharedPreferences): String?

    // url for description, or null or empty
    val urlForDescription: String?

    val focusX: Float
    val focusY: Float

    // true if argument url is included in this attachment.
    fun hasUrl(url: String): Boolean

    // true if the attachment can be set focus point.
    val canFocus: Boolean
        get() = type != TootAttachmentType.Audio
}
