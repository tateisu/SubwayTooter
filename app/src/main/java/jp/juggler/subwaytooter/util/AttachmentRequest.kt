package jp.juggler.subwaytooter.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import jp.juggler.media.generateTempFile
import jp.juggler.media.transcodeAudio
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootApiCallback
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.entity.TootInstance
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.data.JsonObject
import jp.juggler.util.data.getStreamSize
import jp.juggler.util.data.notEmpty
import jp.juggler.util.idCompat
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.errorEx
import jp.juggler.util.media.ResizeConfig
import jp.juggler.util.media.VideoInfo
import jp.juggler.util.media.VideoInfo.Companion.videoInfo
import jp.juggler.util.media.createResizedBitmap
import jp.juggler.util.media.transcodeVideoMedia3Transformer
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CancellationException
import kotlin.math.min

class AttachmentRequest(
    val context: Context,
    val account: SavedAccount,
    val pa: PostAttachment,
    val uri: Uri,
    var mimeTypeArg: String?,
    val imageResizeConfig: ResizeConfig,
    val maxBytesVideo: (instance: TootInstance, mediaConfig: JsonObject?) -> Int,
    val maxBytesImage: (instance: TootInstance, mediaConfig: JsonObject?) -> Int,
    val isReply: Boolean = false,
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
        private suspend fun Context.getInstance(account: SavedAccount): TootInstance {
            val client = TootApiClient(
                context = this,
                callback = object : TootApiCallback {
                    override suspend fun isApiCancelled() = false
                }
            ).apply {
                this.account = account
            }
            val (instance, ri) = TootInstance.get(client = client)
            if (instance != null) return instance
            when (ri) {
                null -> throw CancellationException()
                else -> error("missing instance information. ${ri.error}")
            }
        }
    }

    private var _instance: TootInstance? = null

    suspend fun instance(): TootInstance {
        _instance?.let { return it }
        return context.getInstance(account).also { _instance = it }
    }

    suspend fun mediaConfig(): JsonObject? =
        instance().configuration?.jsonObject("media_attachments")

    private suspend fun serverMaxSqPixel(): Int? =
        mediaConfig()?.int("image_matrix_limit")?.takeIf { it > 0 }

    val mimeType
        get() = uri.resolveMimeType(mimeTypeArg, context)?.notEmpty()
            ?: error(context.getString(R.string.mime_type_missing))

    suspend fun createOpener(): InputStreamOpener {
        val mimeType = this.mimeType

        if (mimeType == MIME_TYPE_GIF) {
            // GIFはそのまま投げる
            return contentUriOpener(context.contentResolver, uri, mimeType, isImage = true)
        } else if (mimeType.startsWith("image")) {
            // 静止画
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
                return createResizedVideoOpener(vi)
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

    private suspend fun createResizedImageOpener(): InputStreamOpener {
        try {
            pa.progress = context.getString(R.string.attachment_handling_compress)

            val instance = instance()

            val canUseWebP = try {
                PrefB.bpUseWebP.value && MIME_TYPE_WEBP.mimeTypeIsSupported(instance)
            } catch (ex: Throwable) {
                log.w(ex, "can't check canUseWebP")
                false
            }

            val canUseOriginal: Boolean = when {
                // WebPを使っていい場合、PNG画像をWebPに変換したい
                canUseWebP && mimeType == MIME_TYPE_PNG -> false
                // WebPを使わない場合、入力がWebPなら強制的にPNGかJPEGにする
                !canUseWebP && mimeType == MIME_TYPE_WEBP -> false
                // ほか、サーバが受け入れる形式でリサイズ不要ならオリジナルのまま送信
                // ただしHEICやHEIFはサーバ側issueが落ち着くまで変換必須とする
                else -> mimeType.mimeTypeIsSupported(instance)
            }

            createResizedBitmap(
                context,
                uri,
                imageResizeConfig,
                canSkip = canUseOriginal,
                serverMaxSqPixel = serverMaxSqPixel()
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
                return compressToTempFileOpener("webp", MIME_TYPE_WEBP, format, 90)
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
                    "png",
                    MIME_TYPE_PNG,
                    Bitmap.CompressFormat.PNG,
                    100
                )

                else -> compressToTempFileOpener(
                    "jpg",
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
        fixExt: String?,
        outMimeType: String,
        format: Bitmap.CompressFormat,
        quality: Int,
    ): InputStreamOpener {
        val tempFile = context.generateTempFile("createResizedImageOpener")
        try {
            FileOutputStream(tempFile).use { compress(format, quality, it) }
            return tempFileOpener(tempFile, outMimeType, isImage = true, fixExt = fixExt)
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

    private suspend fun createResizedVideoOpener(srcInfo: VideoInfo): InputStreamOpener {

        val cacheDir = context.externalCacheDir
            ?.apply { mkdirs() }
            ?: error("getExternalCacheDir returns null.")

        val tempFile = File(cacheDir, "movie." + Thread.currentThread().idCompat + ".tmp")
        val outFile = File(cacheDir, "movie." + Thread.currentThread().idCompat + ".mp4")
        var resultFile: File? = null
        try {

            // 入力ファイルをコピーする
            (context.contentResolver.openInputStream(uri)
                ?: error("openInputStream returns null.")).use { inStream ->
                FileOutputStream(tempFile).use { inStream.copyTo(it) }
            }

            val mediaConfig = mediaConfig()

            // 動画のメタデータを調べる

            // サーバに指定されたファイルサイズ上限と入力動画の時間長があれば、ビットレート上限を制限する
            val duration = srcInfo.duration?.takeIf { it >= 0.1f }
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
//            val result = transcodeVideo(
//                srcInfo,
//                tempFile,
//                outFile,
//                movieResizeConfig,
//            ) {
//                val percent = (it * 100f).toInt()
//                pa.progress =
//                    context.getString(R.string.attachment_handling_compress_ratio, percent)
//            }
            val result = transcodeVideoMedia3Transformer(
                context = context,
                info = srcInfo,
                inFile = tempFile,
                outFile = outFile,
                resizeConfig = movieResizeConfig,
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
                fixExt = when (result) {
                    tempFile -> null
                    else -> "mp4"
                },
            )
        } finally {
            if (outFile != resultFile) outFile.delete()
            if (tempFile != resultFile) tempFile.delete()
        }
    }

    private suspend fun createResizedAudioOpener(srcBytes: Long): InputStreamOpener {
        val instance = instance()
        val mediaConfig = mediaConfig()
        return when {
            mimeType.mimeTypeIsSupported(instance) &&
                    goodAudioType.contains(mimeType) &&
                    srcBytes <= maxBytesVideo(instance, mediaConfig).toLong()
            -> contentUriOpener(
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
}
