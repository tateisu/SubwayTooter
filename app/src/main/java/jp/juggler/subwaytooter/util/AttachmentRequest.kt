package jp.juggler.subwaytooter.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import jp.juggler.media.generateTempFile
import jp.juggler.media.transcodeAudio
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.InstanceType
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.getStreamSize
import jp.juggler.util.log.LogCategory
import jp.juggler.util.media.ResizeConfig
import jp.juggler.util.media.VideoInfo.Companion.videoInfo
import jp.juggler.util.media.createResizedBitmap
import jp.juggler.util.media.transcodeVideo
import java.io.File
import java.io.FileOutputStream

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

        if (mimeType.startsWith("image")) {
            // 静止画(失敗したらオリジナルデータにフォールバックする)
            if (mimeType == AttachmentUploader.MIME_TYPE_JPEG ||
                mimeType == AttachmentUploader.MIME_TYPE_PNG
            ) try {
                // 回転対応が必要かもしれない
                return createResizedImageOpener()
            } catch (ex: Throwable) {
                log.w(ex, "createResizedImageOpener failed. fall back to original image.")
            }

            // 静止画(変換必須)
            // 例外を投げるかもしれない
            return createResizedImageOpener(forcePng = true)
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

    private fun createResizedImageOpener(
        forcePng: Boolean = false,
    ): InputStreamOpener {
        val tempFile = context.generateTempFile("createResizedImageOpener")
        try {
            pa.progress = context.getString(R.string.attachment_handling_compress)

            val bitmap = createResizedBitmap(
                context,
                uri,
                imageResizeConfig,
                skipIfNoNeedToResizeAndRotate = !forcePng,
                serverMaxSqPixel = serverMaxSqPixel
            ) ?: error("createResizedBitmap returns null.")
            try {
                val outputMimeType = when {
                    forcePng || mimeType == AttachmentUploader.MIME_TYPE_PNG ->
                        AttachmentUploader.MIME_TYPE_PNG

                    else -> AttachmentUploader.MIME_TYPE_JPEG
                }
                FileOutputStream(tempFile).use { outStream ->
                    when (outputMimeType) {
                        AttachmentUploader.MIME_TYPE_PNG ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)

                        else ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outStream)
                    }
                }
                return tempFileOpener(tempFile, outputMimeType, isImage = true)
            } finally {
                bitmap.recycle()
            }
        } catch (ex: Throwable) {
            tempFile.delete()
            throw ex
        }
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
