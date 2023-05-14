package jp.juggler.subwaytooter.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import jp.juggler.media.generateTempFile
import jp.juggler.media.transcodeAudio
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.InstanceType
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.AttachmentUploader.Companion.MIME_TYPE_JPEG
import jp.juggler.subwaytooter.util.AttachmentUploader.Companion.MIME_TYPE_PNG
import jp.juggler.subwaytooter.util.AttachmentUploader.Companion.MIME_TYPE_WEBP
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.getStreamSize
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.errorEx
import jp.juggler.util.media.ResizeConfig
import jp.juggler.util.media.VideoInfo.Companion.videoInfo
import jp.juggler.util.media.createResizedBitmap
import jp.juggler.util.media.transcodeVideo
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

class AttachmentRequest(
    val context: Context,
    val account: SavedAccount,
    val pa: PostAttachment,
    val uri: Uri,
    var mimeType: String,
    val imageResizeConfig: ResizeConfig,
    val serverMaxSqPixel: Int?,
    val instance: TootInstance,
    val mediaConfig: JsonObject?,
    val maxBytesVideo: Int,
    val maxBytesImage: Int,
) {
    companion object {
        private val log = LogCategory("AttachmentRequest")

        private val goodAudioType = setOf(
            "audio/flac",
            "audio/mp3",
            "audio/ogg",
            "audio/vnd.wave",
            "audio/vorbis",
            "audio/wav",
            "audio/wave",
            "audio/webm",
            "audio/x-pn-wave",
            "audio/x-wav",
            "audio/3gpp",
        )
//    val badAudioType = setOf(
//        "audio/mpeg","audio/aac",
//        "audio/m4a","audio/x-m4a","audio/mp4",
//        "video/x-ms-asf",
//    )
    }

    suspend fun createOpener(): InputStreamOpener {

        // GIFはそのまま投げる
        if (mimeType == AttachmentUploader.MIME_TYPE_GIF) {
            return contentUriOpener(context.contentResolver, uri, mimeType, isImage = true)
        }

        // 静止画
        if (mimeType.startsWith("image")) {
            return createResizedImageOpener()
        }

        // 音声と動画のファイル区分は曖昧なので
        // MediaMetadataRetriever で調べる

        // コンテンツの長さを調べる

        val contentLength = context.contentResolver.openInputStream(uri)
            ?.use { getStreamSize(false, it) }
            ?: error("openInputStream returns null")

        // 動画の一部は音声かもしれない
        // データに動画や音声が含まれるか調べる
        val vi = try {
            uri.videoInfo(context, contentLength)
        } catch (ex: Throwable) {
            log.e(ex, "can't get videoInfo.")
            error("can't get videoInfo. $mimeType $uri")
        }

        val isVideo = when {
            vi.hasVideo == true -> true
            vi.hasAudio == true -> false
            mimeType.startsWith("video") -> true
            mimeType.startsWith("audio") -> false
            else -> null
        }

        when (isVideo) {
            true -> try {
                // 動画のトランスコード(失敗したらオリジナルデータにフォールバックする)
                return createResizedVideoOpener()
            } catch (ex: Throwable) {
                log.w(
                    ex,
                    "createResizedVideoOpener failed. fall back to original data."
                )
            }

            false -> try {
                // 音声のトランスコード(失敗したらオリジナルデータにフォールバックする)
                return createResizedAudioOpener(contentLength)
            } catch (ex: Throwable) {
                log.w(
                    ex,
                    "createResizedAudioOpener failed. fall back to original data."
                )
            }

            null -> Unit
        }

        return contentUriOpener(
            context.contentResolver,
            uri,
            mimeType,
            isImage = false,
        )
    }

    private fun hasServerSupport(mimeType: String) =
        mediaConfig?.jsonArray("supported_mime_types")?.contains(mimeType)
            ?: when (instance.instanceType) {
                InstanceType.Pixelfed -> AttachmentUploader.acceptableMimeTypesPixelfed
                else -> AttachmentUploader.acceptableMimeTypes
            }.contains(mimeType)

    private fun createResizedImageOpener(): InputStreamOpener {
        try {
            pa.progress = context.getString(R.string.attachment_handling_compress)

            val canUseWebP = try {
                hasServerSupport(MIME_TYPE_WEBP) && PrefB.bpUseWebP.value
            } catch (ex: Throwable) {
                log.w(ex, "can't check canUseWebP")
                false
            }

            // サーバが読めない形式の画像なら強制的に再圧縮をかける
            // もしくは、PNG画像も可能ならWebPに変換したい
            val canUseOriginal = hasServerSupport(mimeType) &&
                    !(mimeType == MIME_TYPE_PNG && canUseWebP)

            createResizedBitmap(
                context,
                uri,
                imageResizeConfig,
                skipIfNoNeedToResizeAndRotate = canUseOriginal,
                serverMaxSqPixel = serverMaxSqPixel
            )?.let { bitmap ->
                try {
                    return bitmap.compressAutoType(canUseWebP)
                } finally {
                    bitmap.recycle()
                }
            }
            // nullを返す場合もここを通る
        } catch (ex: Throwable) {
            log.w(ex, "createResizedBitmap failed.")
        }

        // 元のデータを返す
        return contentUriOpener(context.contentResolver, uri, mimeType, isImage = true)
    }

    private fun Bitmap.compressAutoType(canUseWebP: Boolean): InputStreamOpener {
        if (canUseWebP) {
            try {
                val format = when {
                    Build.VERSION.SDK_INT >= 30 ->
                        Bitmap.CompressFormat.WEBP_LOSSY

                    else ->
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                }
                return compressToTempFileOpener(MIME_TYPE_WEBP, format, 90)
            } catch (ex: Throwable) {
                log.w(ex, "compress to WebP lossy failed.")
                // 失敗したらJPEG or PNG にフォールバック
            }
        }
        try {
            // check bitmap has translucent pixels
            val hasAlpha = when {
                mimeType == MIME_TYPE_JPEG -> false
                !hasAlpha() -> false
                else -> scanAlpha()
            }
            return when (hasAlpha) {
                true -> compressToTempFileOpener(
                    MIME_TYPE_PNG,
                    Bitmap.CompressFormat.PNG,
                    100
                )

                else -> compressToTempFileOpener(
                    MIME_TYPE_JPEG,
                    Bitmap.CompressFormat.JPEG,
                    95
                )
            }
        } catch (ex: Throwable) {
            errorEx(ex, "compress to JPEG/PNG failed.")
        }
    }

    /**
     * Bitmapを指定フォーマットで圧縮して tempFileOpener を返す
     * 失敗したら例外を投げる
     */
    private fun Bitmap.compressToTempFileOpener(
        outMimeType: String,
        format: Bitmap.CompressFormat,
        quality: Int,
    ): InputStreamOpener {
        val tempFile = context.generateTempFile("createResizedImageOpener")
        try {
            FileOutputStream(tempFile).use { compress(format, quality, it) }
            return tempFileOpener(tempFile, outMimeType, isImage = true)
        } catch (ex: Throwable) {
            tempFile.delete()
            throw ex
        }
    }

    /**
     * ビットマップのアルファ値が0xFFではないピクセルがあれば真
     */
    private fun Bitmap.scanAlpha(): Boolean {
        try {
            val w = this.width
            val h = this.height
            if (w > 0 && h > 0) {
                val hStep = 64
                val pixels = IntArray(w * min(hStep, h))
                for (y in 0 until h step hStep) {
                    val hPart = min(hStep, h - y)
                    getPixels(
                        /* pixels */ pixels,
                        /* offset */ 0,
                        /* stride */ w,
                        /* x */ 0,
                        /* y */ y,
                        /* width */ w,
                        /* height */ hPart,
                    )
                    for (i in 0 until (w * hPart)) {
                        if (pixels[i].ushr(24) != 0xff) return true
                    }
                }
            }
        } catch (ex: Throwable) {
            log.w(ex, "scanAlpha failed.")
        }
        return false
    }

    private suspend fun createResizedVideoOpener(): InputStreamOpener {

        val cacheDir = context.externalCacheDir
            ?.apply { mkdirs() }
            ?: error("getExternalCacheDir returns null.")

        val tempFile = File(cacheDir, "movie." + Thread.currentThread().id + ".tmp")
        val outFile = File(cacheDir, "movie." + Thread.currentThread().id + ".mp4")
        var resultFile: File? = null

        try {
            // 入力ファイルをコピーする
            (context.contentResolver.openInputStream(uri)
                ?: error("openInputStream returns null.")).use { inStream ->
                FileOutputStream(tempFile).use { inStream.copyTo(it) }
            }

            // 動画のメタデータを調べる
            val info = tempFile.videoInfo

            // サーバに指定されたファイルサイズ上限と入力動画の時間長があれば、ビットレート上限を制限する
            val duration = info.duration?.takeIf { it >= 0.1f }
            val limitFileSize = mediaConfig?.float("video_size_limit")?.takeIf { it >= 1f }
            val limitBitrate = when {
                duration != null && limitFileSize != null ->
                    (limitFileSize / duration).toLong()

                else -> null
            }

            // アカウント別の動画トランスコード設定
            // ビットレート、フレームレート、平方ピクセル数をサーバからの情報によりさらに制限する
            val movieResizeConfig = account.getMovieResizeConfig()
                .restrict(
                    limitBitrate = limitBitrate,
                    limitFrameRate = mediaConfig?.int("video_frame_rate_limit")
                        ?.takeIf { it >= 1f },
                    limitSquarePixels = mediaConfig?.int("video_matrix_limit")
                        ?.takeIf { it > 1 },
                )

            val result = transcodeVideo(
                info,
                tempFile,
                outFile,
                movieResizeConfig,
            ) {
                val percent = (it * 100f).toInt()
                pa.progress =
                    context.getString(R.string.attachment_handling_compress_ratio, percent)
            }
            resultFile = result
            return tempFileOpener(
                result,
                when (result) {
                    tempFile -> mimeType
                    else -> "video/mp4"
                },
                isImage = false,
            )
        } finally {
            if (outFile != resultFile) outFile.delete()
            if (tempFile != resultFile) tempFile.delete()
        }
    }

    private suspend fun createResizedAudioOpener(srcBytes: Long): InputStreamOpener =
        when {
            hasServerSupport(mimeType) &&
                    goodAudioType.contains(mimeType) &&
                    srcBytes <= maxBytesVideo -> contentUriOpener(
                context.contentResolver,
                uri,
                mimeType,
                isImage = false,
            )

            else -> {
                pa.progress = context.getString(R.string.attachment_handling_compress)
                val (tempFile, outMimeType) = transcodeAudio(
                    context,
                    uri,
                    mimeType
                )
                // このワークアラウンドはうまくいかなかった
//                outMimeType = when (outMimeType) {
//                    "audio/mp4" -> "audio/x-m4a"
//                    else -> outMimeType
//                }
                tempFileOpener(
                    tempFile,
                    outMimeType,
                    isImage = false,
                )
            }
        }
}
