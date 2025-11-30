package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.addTo
import jp.juggler.util.data.buildJsonObject
import jp.juggler.util.data.clip
import jp.juggler.util.data.mayUri
import jp.juggler.util.data.notBlank
import jp.juggler.util.data.notEmpty

@Suppress("LongParameterList")
class TootAttachment private constructor(
    //	ID of the attachment
    val id: EntityId,

    //One of: "image", "video", "gifv". or may null ? may "unknown" ?
    override val type: TootAttachmentType,

    //URL of the locally hosted version of the image
    val url: String?,

    //For remote images, the remote URL of the original image
    val remote_url: String?,

    //	URL of the preview image
    // (Mastodon 2.9.2) audioのpreview_url は .mpga のURL
    // (Misskey v11) audioのpreview_url は null
    val preview_url: String?,

    val preview_remote_url: String?,

    //	Shorter URL for the image, for insertion into text (only present on local images)
    val text_url: String?,

    // ALT text (Mastodon 2.0.0 or later)
    // Mastodon 4.0で既存投稿の添付データの説明文を編集可能になる
    override var description: String?,

    // Mastodon 4.0で既存投稿の添付データのフォーカス位置を編集可能になる
    override var focusX: Float,
    override var focusY: Float,

    // MisskeyはメディアごとにNSFWフラグがある
    val isSensitive: Boolean,

    // Mastodon 2.9.0 or later
    val blurhash: String?,
) : TootAttachmentLike {

    // 内部フラグ: 再編集で引き継いだ添付メディアなら真
    var redraft = false

    // 内部フラグ：編集時に既存投稿から引き継いだ添付データなら真
    var isEdit = false

    // 内部フラグ：編集投稿時にメディア属性を更新するなら、その値を指定する
    var updateDescription: String? = null
    var updateThumbnail: String? = null
    var updateFocus: String? = null

    override val urlForDescription: String?
        get() = remote_url.notEmpty() ?: url

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
        private const val KEY_UPDATE_DESCRIPTION = "updateDescription"
        private const val KEY_UPDATE_THUMBNAIL = "updateThumbnail"
        private const val KEY_UPDATE_FOCUS = "updateFocus"
        private const val KEY_IS_EDIT = "isEdit"

        private val ext_audio = arrayOf(".mpga", ".mp3", ".aac", ".ogg")

        private fun parseType(src: String?) =
            TootAttachmentType.entries.find { it.id == src }

        private fun guessMediaTypeByUrl(src: String?): TootAttachmentType? {
            val uri = src.mayUri() ?: return null

            if (ext_audio.any { uri.path?.endsWith(it) == true }) {
                return TootAttachmentType.Audio
            }

            return null
        }

        /**
         * アプリ内でencodeJson()した情報をデコードする
         */
        fun tootAttachmentJson(
            src: JsonObject,
        ): TootAttachment {
            val url: String? = src.string(KEY_URL)
            val remote_url: String? = src.string(KEY_REMOTE_URL)
            val type: TootAttachmentType = when (val tmpType = parseType(src.string(KEY_TYPE))) {
                null, TootAttachmentType.Unknown -> {
                    guessMediaTypeByUrl(remote_url ?: url) ?: TootAttachmentType.Unknown
                }

                else -> tmpType
            }
            val focus = src.jsonObject(KEY_META)?.jsonObject(KEY_FOCUS)
            return TootAttachment(
                blurhash = src.string(KEY_BLURHASH),
                description = src.string(KEY_DESCRIPTION),
                focusX = parseFocusValue(focus, KEY_X),
                focusY = parseFocusValue(focus, KEY_Y),
                id = EntityId.mayDefault(src.string(KEY_ID)),
                isSensitive = src.optBoolean(KEY_IS_SENSITIVE),
                preview_remote_url = src.string(KEY_PREVIEW_REMOTE_URL),
                preview_url = src.string(KEY_PREVIEW_URL),
                remote_url = remote_url,
                text_url = src.string(KEY_TEXT_URL),
                type = type,
                url = url,
            ).apply {
                updateDescription = src.string(KEY_UPDATE_DESCRIPTION)
                updateThumbnail = src.string(KEY_UPDATE_THUMBNAIL)
                updateFocus = src.string(KEY_UPDATE_FOCUS)
                isEdit = src.boolean(KEY_IS_EDIT) ?: false
            }
        }

        private fun tootAttachmentMisskey(src: JsonObject): TootAttachment {
            val mimeType = src.string("type") ?: "?"
            val type: TootAttachmentType = when {
                mimeType.startsWith("image/") -> TootAttachmentType.Image
                mimeType.startsWith("video/") -> TootAttachmentType.Video
                mimeType.startsWith("audio/") -> TootAttachmentType.Audio
                else -> TootAttachmentType.Unknown
            }
            val url = src.string("url")
            val description = (src.string("comment")?.notBlank()
                ?: src.string("name")?.notBlank())
                ?.takeIf { it != "null" }
            return TootAttachment(
                blurhash = null,
                description = description,
                focusX = 0f,
                focusY = 0f,
                id = EntityId.mayDefault(src.string("id")),
                isSensitive = src.optBoolean("isSensitive", false),
                preview_remote_url = null,
                preview_url = src.string("thumbnailUrl"),
                remote_url = url,
                text_url = url,
                type = type,
                url = url,
            ).apply {
                updateDescription = src.string(KEY_UPDATE_DESCRIPTION)
                updateThumbnail = src.string(KEY_UPDATE_THUMBNAIL)
                updateFocus = src.string(KEY_UPDATE_FOCUS)
                isEdit = src.boolean(KEY_IS_EDIT) ?: false
            }
        }

        private fun tootAttachmentNoteStock(src: JsonObject): TootAttachment {
            val url: String? = src.string("url")

            val preview_url: String? = src.string("img_hash")
                ?.let { "https://img.osa-p.net/proxy/500x,q100,s$it/$url" }

            val mediaType = src.string("mediaType")

            val type: TootAttachmentType = when {
                mediaType?.startsWith("image") == true -> TootAttachmentType.Image
                mediaType?.startsWith("video") == true -> TootAttachmentType.Video
                mediaType?.startsWith("audio") == true -> TootAttachmentType.Audio
                else -> guessMediaTypeByUrl(url) ?: TootAttachmentType.Unknown
            }

            val focus = null

            return TootAttachment(
                blurhash = src.string("blurhash"),
                description = src.string("name")?.notBlank()?.takeIf { it != "null" },
                focusX = parseFocusValue(focus, "x"),
                focusY = parseFocusValue(focus, "y"),
                id = EntityId.DEFAULT,
                isSensitive = false,
                preview_remote_url = null,
                preview_url = preview_url,
                remote_url = url,
                text_url = url,
                type = type,
                url = url,
            )
        }

        private fun tootAttachmentMastodon(src: JsonObject): TootAttachment {
            val url: String? = src.string("url")
            val remote_url: String? = src.string("remote_url")

            val type: TootAttachmentType = when (val tmpType = parseType(src.string("type"))) {
                null, TootAttachmentType.Unknown -> {
                    guessMediaTypeByUrl(remote_url ?: url) ?: TootAttachmentType.Unknown
                }

                else -> tmpType
            }

            val focus = src.jsonObject("meta")?.jsonObject("focus")

            return TootAttachment(
                blurhash = src.string("blurhash"),
                description = src.string("description")
                    ?.notBlank()?.takeIf { it != "null" },
                focusX = parseFocusValue(focus, "x"),
                focusY = parseFocusValue(focus, "y"),
                id = EntityId.mayDefault(src.string("id")),
                isSensitive = false,
                preview_remote_url = src.string("preview_remote_url"),
                preview_url = src.string("preview_url"),
                remote_url = remote_url,
                text_url = src.string("text_url"),
                type = type,
                url = url,
            )
        }

        fun tootAttachment(serviceType: ServiceType, src: JsonObject) =
            when (serviceType) {
                ServiceType.MISSKEY -> tootAttachmentMisskey(src)
                ServiceType.NOTESTOCK -> tootAttachmentNoteStock(src)
                else -> tootAttachmentMastodon(src)
            }

        fun tootAttachment(parser: TootParser, src: JsonObject) =
            tootAttachment(parser.serviceType, src)
    }

    ///////////////////////////////

    override fun hasUrl(url: String): Boolean = when (url) {
        this.preview_url, this.preview_remote_url, this.remote_url, this.url, this.text_url -> true
        else -> false
    }

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
        put(KEY_UPDATE_DESCRIPTION, updateDescription)
        put(KEY_UPDATE_THUMBNAIL, updateThumbnail)
        put(KEY_UPDATE_FOCUS, updateFocus)
        put(KEY_IS_EDIT, isEdit)

        if (focusX != 0f || focusY != 0f) {
            put(KEY_META, buildJsonObject {
                put(KEY_FOCUS, buildJsonObject {
                    put(KEY_X, focusX)
                    put(KEY_Y, focusY)
                })
            })
        }
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
