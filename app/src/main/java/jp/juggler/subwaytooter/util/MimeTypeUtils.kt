package jp.juggler.subwaytooter.util

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import jp.juggler.subwaytooter.api.entity.InstanceType
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.util.data.notEmpty
import jp.juggler.util.data.toByteArray
import jp.juggler.util.data.toLowerByteArray
import jp.juggler.util.log.LogCategory
import jp.juggler.util.media.bitmapMimeType

private val log = LogCategory("MimeTypeUtils")

const val MIME_TYPE_JPEG = "image/jpeg"
const val MIME_TYPE_PNG = "image/png"
const val MIME_TYPE_GIF = "image/gif"
const val MIME_TYPE_WEBP = "image/webp"

private val acceptableMimeTypes = HashSet<String>().apply {
    //
    add("image/*") // Android標準のギャラリーが image/* を出してくることがあるらしい
    add("video/*") // Android標準のギャラリーが image/* を出してくることがあるらしい
    add("audio/*") // Android標準のギャラリーが image/* を出してくることがあるらしい
    //
    add("image/jpeg")
    add("image/png")
    add("image/gif")
    add("video/webm")
    add("video/mp4")
    add("video/quicktime")
    //
    add("audio/webm")
    add("audio/ogg")
    add("audio/mpeg")
    add("audio/mp3")
    add("audio/wav")
    add("audio/wave")
    add("audio/x-wav")
    add("audio/x-pn-wav")
    add("audio/flac")
    add("audio/x-flac")

    // https://github.com/tootsuite/mastodon/pull/11342
    add("audio/aac")
    add("audio/m4a")
    add("audio/3gpp")
}

private val acceptableMimeTypesPixelfed = HashSet<String>().apply {
    //
    add("image/*") // Android標準のギャラリーが image/* を出してくることがあるらしい
    add("video/*") // Android標準のギャラリーが image/* を出してくることがあるらしい
    //
    add("image/jpeg")
    add("image/png")
    add("image/gif")
    add("video/mp4")
    add("video/m4v")
}

private val imageHeaderList = listOf(
    Pair(
        "image/jpeg",
        intArrayOf(0xff, 0xd8, 0xff).toByteArray()
    ),
    Pair(
        "image/png",
        intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A).toByteArray()
    ),
    Pair(
        "image/gif",
        "GIF".toByteArray(Charsets.UTF_8)
    ),
    Pair(
        "audio/wav",
        "RIFF".toByteArray(Charsets.UTF_8),
    ),
    Pair(
        "audio/ogg",
        "OggS".toByteArray(Charsets.UTF_8),
    ),
    Pair(
        "audio/flac",
        "fLaC".toByteArray(Charsets.UTF_8),
    ),
    Pair(
        "image/bmp",
        "BM".toByteArray(Charsets.UTF_8),
    ),
    Pair(
        "image/webp",
        "RIFF****WEBP".toByteArray(Charsets.UTF_8),
    ),
).sortedByDescending { it.second.size }

private val sig3gp = arrayOf(
    "3ge6",
    "3ge7",
    "3gg6",
    "3gp1",
    "3gp2",
    "3gp3",
    "3gp4",
    "3gp5",
    "3gp6",
    "3gp7",
    "3gr6",
    "3gr7",
    "3gs6",
    "3gs7",
    "kddi"
).map { it.toCharArray().toLowerByteArray() }

private val sigM4a = arrayOf(
    "M4A ",
    "M4B ",
    "M4P "
).map { it.toCharArray().toLowerByteArray() }

private const val wild = '?'.code.toByte()

private val sigFtyp = "ftyp".toCharArray().toLowerByteArray()

private fun matchSig(
    data: ByteArray,
    dataOffset: Int,
    sig: ByteArray,
    sigSize: Int = sig.size,
): Boolean {
    for (i in 0 until sigSize) {
        if (data[dataOffset + i] != sig[i]) return false
    }
    return true
}

private fun ByteArray.startWithWildcard(
    key: ByteArray,
    thisOffset: Int = 0,
    keyOffset: Int = 0,
    length: Int = key.size - keyOffset,
): Boolean {
    if (thisOffset + length > this.size || keyOffset + length > key.size) {
        return false
    }
    for (i in 0 until length) {
        val cThis = this[i + thisOffset]
        val cKey = key[i + keyOffset]
        if (cKey != wild && cKey != cThis) return false
    }
    return true
}

private fun findMimeTypeByFileHeader(
    contentResolver: ContentResolver,
    uri: Uri,
): String? {
    try {
        contentResolver.openInputStream(uri)?.use { inStream ->
            val data = ByteArray(65536)
            val nRead = inStream.read(data, 0, data.size)
            for (pair in imageHeaderList) {
                val type = pair.first
                val header = pair.second
                if (nRead >= header.size && data.startWithWildcard(header)) return type
            }

            // scan frame header
            for (i in 0 until nRead - 8) {

                if (!matchSig(data, i, sigFtyp)) continue

                // 3gpp check
                for (s in sig3gp) {
                    if (matchSig(data, i + 4, s)) return "audio/3gpp"
                }

                // m4a check
                for (s in sigM4a) {
                    if (matchSig(data, i + 4, s)) return "audio/m4a"
                }
            }

            // scan frame header
            loop@ for (i in 0 until nRead - 2) {

                // mpeg frame header
                val b0 = data[i].toInt() and 255
                if (b0 != 255) continue
                val b1 = data[i + 1].toInt() and 255
                if ((b1 and 0b11100000) != 0b11100000) continue

                val mpegVersionId = ((b1 shr 3) and 3)
                // 00 mpeg 2.5
                // 01 not used
                // 10 (mp3) mpeg 2 / (AAC) mpeg-4
                // 11 (mp3) mpeg 1 / (AAC) mpeg-2

                @Suppress("MoveVariableDeclarationIntoWhen")
                val mpegLayerId = ((b1 shr 1) and 3)
                // 00 (mp3)not used / (AAC) always 0
                // 01 (mp3)layer III
                // 10 (mp3)layer II
                // 11 (mp3)layer I

                when (mpegLayerId) {
                    0 -> when (mpegVersionId) {
                        2, 3 -> return "audio/aac"

                        else -> {
                        }
                    }

                    1 -> when (mpegVersionId) {
                        0, 2, 3 -> return "audio/mp3"

                        else -> {
                        }
                    }
                }
            }
        }
    } catch (ex: Throwable) {
        log.e(ex, "findMimeTypeByFileHeader failed.")
    }
    return null
}

private fun String.isProblematicImageType(instance: TootInstance) = when (instance.instanceType) {
    InstanceType.Mastodon -> when (this) {
        // https://github.com/mastodon/mastodon/issues/23588
        "image/heic", "image/heif" -> true
        // https://github.com/mastodon/mastodon/issues/20834
        "image/avif" -> true

        else -> false
    }

    InstanceType.Pixelfed -> when (this) {
        // Pixelfed は PC Web UI で画像を開くダイアログの時点でHEIC,HEIF,AVIF を選択できない
        "image/heic", "image/heif", "image/avif" -> true
        else -> false
    }
    // PleromaやMisskeyでの問題は調べてない
    else -> false
}

fun String.mimeTypeIsSupportedByServer(instance: TootInstance) =
    instance.configuration
        ?.jsonObject("media_attachments")
        ?.jsonArray("supported_mime_types")
        ?.contains(this)
        ?: when (instance.instanceType) {
            InstanceType.Pixelfed -> acceptableMimeTypesPixelfed
            else -> acceptableMimeTypes
        }.contains(this)

fun String.mimeTypeIsSupported(instance: TootInstance) = when {
    isProblematicImageType(instance) -> false
    else -> mimeTypeIsSupportedByServer(instance)
}

fun Uri.resolveMimeType(
    mimeTypeArg1: String?,
    context: Context,
    instance: TootInstance,
): String? {
    // image/j()pg だの image/j(e)pg だの、mime type を誤記するアプリがあまりに多い
    // application/octet-stream などが誤設定されてることもある
    // Androidが静止画を読めるならそのmimeType
    bitmapMimeType(context.contentResolver)?.notEmpty()?.let { return it }

    // 動画の一部は音声かもしれない
    // データに動画や音声が含まれるか調べる
    try {
        MediaMetadataRetriever().use { mmr ->
            mmr.setDataSource(context, this)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        }?.notEmpty()?.let { return it }
    } catch (ex: Throwable) {
        log.w(ex, "not video or audio.")
    }

    // 引数のmimeTypeがサーバでサポートされているならソレ
    try {
        mimeTypeArg1
            ?.notEmpty()
            ?.takeIf { it.mimeTypeIsSupportedByServer(instance) }
            ?.let { return it }
    } catch (ex: Throwable) {
        AttachmentUploader.log.w(ex, "mimeTypeArg1 check failed.")
    }
    // ContentResolverに尋ねる
    try {
        context.contentResolver.getType(this)
            ?.notEmpty()
            ?.takeIf { it.mimeTypeIsSupportedByServer(instance) }
            ?.let { return it }
    } catch (ex: Throwable) {
        AttachmentUploader.log.w(ex, "contentResolver.getType failed.")
    }
    // gboardのステッカーではUriのクエリパラメータにmimeType引数がある
    try {
        getQueryParameter("mimeType")
            ?.notEmpty()
            ?.takeIf { it.mimeTypeIsSupportedByServer(instance) }
            ?.let { return it }
    } catch (ex: Throwable) {
        log.w(ex, "getQueryParameter(mimeType) failed.")
    }

    // ファイルヘッダを読んで判定する
    findMimeTypeByFileHeader(context.contentResolver, this)
        ?.notEmpty()?.let { return it }

    return null
}
