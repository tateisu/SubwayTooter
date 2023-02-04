package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.util.*
import jp.juggler.util.data.*

class TootAttachment : TootAttachmentLike {

    companion object {
        private fun parseFocusValue(parent: JsonObject?, key: String): Float {
            if (parent != null) {
                val dv = parent.double(key)
                if (dv != null && dv.isFinite()) return dv.toFloat().clip(-1f, 1f)
            }
            return 0f
        }

        // 下書きからの復元などに使うパラメータ
        // 後方互換性的な理由で概ねマストドンに合わせている
        private const val KEY_IS_STRING_ID = "isStringId"
        private const val KEY_ID = "id"
        private const val KEY_TYPE = "type"
        private const val KEY_URL = "url"
        private const val KEY_REMOTE_URL = "remote_url"
        private const val KEY_PREVIEW_URL = "preview_url"
        private const val KEY_PREVIEW_REMOTE_URL = "preview_remote_url"

        private const val KEY_TEXT_URL = "text_url"
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_IS_SENSITIVE = "isSensitive"
        private const val KEY_META = "meta"
        private const val KEY_FOCUS = "focus"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
        private const val KEY_BLURHASH = "blurhash"

        fun decodeJson(src: JsonObject) = TootAttachment(src, decode = true)

        private val ext_audio = arrayOf(".mpga", ".mp3", ".aac", ".ogg")

        private fun guessMediaTypeByUrl(src: String?): TootAttachmentType? {
            val uri = src.mayUri() ?: return null

            if (ext_audio.any { uri.path?.endsWith(it) == true }) {
                return TootAttachmentType.Audio
            }

            return null
        }
    }

    constructor(parser: TootParser, src: JsonObject) : this(parser.serviceType, src)

    //	ID of the attachment
    val id: EntityId

    //One of: "image", "video", "gifv". or may null ? may "unknown" ?
    override val type: TootAttachmentType

    //URL of the locally hosted version of the image
    val url: String?

    //For remote images, the remote URL of the original image
    val remote_url: String?

    //	URL of the preview image
    // (Mastodon 2.9.2) audioのpreview_url は .mpga のURL
    // (Misskey v11) audioのpreview_url は null
    val preview_url: String?

    val preview_remote_url: String?

    //	Shorter URL for the image, for insertion into text (only present on local images)
    val text_url: String?

    // ALT text (Mastodon 2.0.0 or later)
    override val description: String?

    override val focusX: Float
    override val focusY: Float

    // 内部フラグ: 再編集で引き継いだ添付メディアなら真
    var redraft: Boolean = false

    // MisskeyはメディアごとにNSFWフラグがある
    val isSensitive: Boolean

    // Mastodon 2.9.0 or later
    val blurhash: String?

    ///////////////////////////////

    override fun hasUrl(url: String): Boolean = when (url) {
        this.preview_url, this.preview_remote_url, this.remote_url, this.url, this.text_url -> true
        else -> false
    }

    override val urlForDescription: String?
        get() = remote_url.notEmpty() ?: url

    constructor(serviceType: ServiceType, src: JsonObject) {

        when (serviceType) {
            ServiceType.MISSKEY -> {
                id = EntityId.mayDefault(src.string("id"))

                val mimeType = src.string("type") ?: "?"

                this.type = when {
                    mimeType.startsWith("image/") -> TootAttachmentType.Image
                    mimeType.startsWith("video/") -> TootAttachmentType.Video
                    mimeType.startsWith("audio/") -> TootAttachmentType.Audio
                    else -> TootAttachmentType.Unknown
                }

                url = src.string("url")
                preview_url = src.string("thumbnailUrl")
                preview_remote_url = null
                remote_url = url
                text_url = url

                description = src.string("comment")?.notBlank()
                    ?: src.string("name")?.notBlank()

                focusX = 0f
                focusY = 0f
                isSensitive = src.optBoolean("isSensitive", false)

                blurhash = null
            }

            ServiceType.NOTESTOCK -> {
                id = EntityId.DEFAULT
                url = src.string("url")
                remote_url = url
                preview_url = src.string("img_hash")
                    ?.let { "https://img.osa-p.net/proxy/500x,q100,s$it/$url" }
                preview_remote_url = null

                text_url = url
                description = src.string("name")
                isSensitive = false // Misskey用のパラメータなので、マストドンでは適当な値を使ってOK

                val mediaType = src.string("mediaType")
                type = when {
                    mediaType?.startsWith("image") == true -> TootAttachmentType.Image
                    mediaType?.startsWith("video") == true -> TootAttachmentType.Video
                    mediaType?.startsWith("audio") == true -> TootAttachmentType.Audio
                    else -> guessMediaTypeByUrl(remote_url ?: url)
                        ?: TootAttachmentType.Unknown
                    // TODO GIFVかどうかの判定はどうするの？
                }

                val focus = null // TODO focus指定はどうなるの？
                focusX = parseFocusValue(focus, "x")
                focusY = parseFocusValue(focus, "y")

                blurhash = src.string("blurhash")
            }

            else -> {
                id = EntityId.mayDefault(src.string("id"))
                url = src.string("url")
                remote_url = src.string("remote_url")
                preview_url = src.string("preview_url")
                preview_remote_url = src.string("preview_remote_url")

                text_url = src.string("text_url")
                description = src.string("description")
                isSensitive = false // Misskey用のパラメータなので、マストドンでは適当な値を使ってOK

                type = when (val tmpType = parseType(src.string("type"))) {
                    null, TootAttachmentType.Unknown -> {
                        guessMediaTypeByUrl(remote_url ?: url) ?: TootAttachmentType.Unknown
                    }

                    else -> tmpType
                }

                val focus = src.jsonObject("meta")?.jsonObject("focus")
                focusX = parseFocusValue(focus, "x")
                focusY = parseFocusValue(focus, "y")

                blurhash = src.string("blurhash")
            }
        }
    }

    private fun parseType(src: String?) =
        TootAttachmentType.values().find { it.id == src }

    override fun urlForThumbnail() =
        if (PrefB.bpPriorLocalURL.value) {
            preview_url.notEmpty() ?: preview_remote_url.notEmpty()
        } else {
            preview_remote_url.notEmpty() ?: preview_url.notEmpty()
        } ?: when (type) {
            TootAttachmentType.Image -> getLargeUrl()
            else -> null
        }

    fun getLargeUrl() =
        if (PrefB.bpPriorLocalURL.value) {
            url.notEmpty() ?: remote_url
        } else {
            remote_url.notEmpty() ?: url
        }

    fun getLargeUrlList() =
        ArrayList<String>().apply {
            if (PrefB.bpPriorLocalURL.value) {
                url.notEmpty()?.addTo(this)
                remote_url.notEmpty()?.addTo(this)
            } else {
                remote_url.notEmpty()?.addTo(this)
                url.notEmpty()?.addTo(this)
            }
        }

    fun encodeJson() = buildJsonObject {
        put(KEY_IS_STRING_ID, true)
        put(KEY_ID, id.toString())
        put(KEY_TYPE, type.id)
        put(KEY_URL, url)
        put(KEY_REMOTE_URL, remote_url)
        put(KEY_PREVIEW_URL, preview_url)
        put(KEY_PREVIEW_REMOTE_URL, preview_remote_url)
        put(KEY_TEXT_URL, text_url)
        put(KEY_DESCRIPTION, description)
        put(KEY_IS_SENSITIVE, isSensitive)
        put(KEY_BLURHASH, blurhash)

        if (focusX != 0f || focusY != 0f) {
            put(KEY_META, buildJsonObject {
                put(KEY_FOCUS, buildJsonObject {
                    put(KEY_X, focusX)
                    put(KEY_Y, focusY)
                })
            })
        }
    }

    constructor(
        src: JsonObject,
        @Suppress("UNUSED_PARAMETER") decode: Boolean, // dummy parameter for choosing this ctor.
    ) {

        id = EntityId.mayDefault(src.string(KEY_ID))
        url = src.string(KEY_URL)
        remote_url = src.string(KEY_REMOTE_URL)
        preview_url = src.string(KEY_PREVIEW_URL)
        preview_remote_url = src.string(KEY_PREVIEW_REMOTE_URL)
        text_url = src.string(KEY_TEXT_URL)

        type = when (val tmpType = parseType(src.string(KEY_TYPE))) {
            null, TootAttachmentType.Unknown -> {
                guessMediaTypeByUrl(remote_url ?: url) ?: TootAttachmentType.Unknown
            }

            else -> tmpType
        }

        description = src.string(KEY_DESCRIPTION)
        isSensitive = src.optBoolean(KEY_IS_SENSITIVE)

        val focus = src.jsonObject(KEY_META)?.jsonObject(KEY_FOCUS)
        focusX = parseFocusValue(focus, KEY_X)
        focusY = parseFocusValue(focus, KEY_Y)
        blurhash = src.string(KEY_BLURHASH)
    }
}

// v1.3 から 添付ファイルの画像のピクセルサイズが取得できるようになった
// https://github.com/tootsuite/mastodon/issues/1985
// "media_attachments" : [
//	 {
//	 "id" : 4,
//	 "type" : "image",
//	 "remote_url" : "",
//	 "meta" : {
//	 "original" : {
//	 "width" : 600,
//	 "size" : "600x400",
//	 "height" : 400,
//	 "aspect" : 1.5
//	 },
//	 "small" : {
//	 "aspect" : 1.49812734082397,
//	 "height" : 267,
//	 "size" : "400x267",
//	 "width" : 400
//	 }
//	 },
//	 "url" : "http://127.0.0.1:3000/system/media_attachments/files/000/000/004/original/3416fc5188c656da.jpg?1493138517",
//	 "preview_url" : "http://127.0.0.1:3000/system/media_attachments/files/000/000/004/small/3416fc5188c656da.jpg?1493138517",
//	 "text_url" : "http://127.0.0.1:3000/media/4hfW3Kt4U9UxDvV_xug"
//	 },
//	 {
//	 "text_url" : "http://127.0.0.1:3000/media/0vTH_B1kjvIvlUBhGBw",
//	 "preview_url" : "http://127.0.0.1:3000/system/media_attachments/files/000/000/003/small/23519a5e64064e32.png?1493138030",
//	 "meta" : {
//	 "fps" : 15,
//	 "duration" : 5.06,
//	 "width" : 320,
//	 "size" : "320x180",
//	 "height" : 180,
//	 "length" : "0:00:05.06",
//	 "aspect" : 1.77777777777778
//	 },
//	 "url" : "http://127.0.0.1:3000/system/media_attachments/files/000/000/003/original/23519a5e64064e32.mp4?1493138030",
//	 "remote_url" : "",
//	 "type" : "gifv",
//	 "id" : 3
//	 }
//	 ],
