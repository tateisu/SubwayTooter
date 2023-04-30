package jp.juggler.media

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.TransformationException
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.TransformationResult
import androidx.media3.transformer.Transformer
import jp.juggler.util.log.LogCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID
import kotlin.coroutines.resumeWithException

private val log = LogCategory("transcodeAudio")

val generateTempFileLock = Any()

fun Context.generateTempFile(prefix: String) =
    synchronized(generateTempFileLock) {
        val cacheDir = externalCacheDir ?: cacheDir ?: error("missing cacheDir")
        cacheDir.mkdirs()
        val path = Paths.get(cacheDir.canonicalPath)
        if (!Files.isDirectory(path)) error("cacheDir is not directory. $cacheDir")
        if (!Files.isWritable(path)) error("cacheDir is not writable. $cacheDir")
        // 重複しない一時ファイル名を探す
        var tempFile: File
        do {
            tempFile = File(cacheDir, "$prefix-${UUID.randomUUID()}")
        } while (tempFile.exists())
        // すぐにファイルを作成する
        FileOutputStream(tempFile).use {}
        tempFile
    }

@OptIn(ExperimentalCoroutinesApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
suspend fun transcodeAudio(
    context: Context,
    inUri: Uri,
    inMimeType: String,
): Pair<File, String> {
    val inputMediaItem = MediaItem.fromUri(inUri)
    // トランスコードに指定するmimeType
    val encodeMimeType = MimeTypes.AUDIO_AAC
    // MediaStore登録に使うmimeType。 audio/mp4a-latm だと失敗するのホント困る
    val storeMimeType = "audio/mp4"
    val tmpFile = context.generateTempFile("transcodeAudio")

    // Transformerは単一スレッドで処理する要件
    val result: TransformationResult = withContext(Dispatchers.Main.immediate) {
        val looper = Looper.getMainLooper()
        suspendCancellableCoroutine { cont ->
            val transformerListener = object : Transformer.Listener {
                override fun onTransformationCompleted(
                    inputMediaItem: MediaItem,
                    transformationResult: TransformationResult,
                ) {
                    log.i("onTransformationCompleted inputMediaItem=$inputMediaItem transformationResult=$transformationResult")
                    if (cont.isActive) cont.resume(transformationResult) {}
                }

                override fun onTransformationError(
                    inputMediaItem: MediaItem,
                    exception: TransformationException,
                ) {
                    log.e(
                        exception,
                        "onTransformationError inputMediaItem=$inputMediaItem"
                    )
                    if (cont.isActive) cont.resumeWithException(exception)
                }

                override fun onFallbackApplied(
                    inputMediaItem: MediaItem,
                    originalTransformationRequest: TransformationRequest,
                    fallbackTransformationRequest: TransformationRequest,
                ) {
                    log.i("onFallbackApplied inputMediaItem=$inputMediaItem original=$originalTransformationRequest fallback=$fallbackTransformationRequest")
                }
            }
            val transformer = Transformer.Builder(context)
                .setLooper(looper)
                .setTransformationRequest(
                    TransformationRequest.Builder()
                        .setAudioMimeType(encodeMimeType)
                        .build()
                )
                .setRemoveVideo(true)
                .setRemoveAudio(false)
                .addListener(transformerListener)
                .build()
            transformer.startTransformation(inputMediaItem, tmpFile.canonicalPath)
            cont.invokeOnCancellation {
                transformer.cancel()
            }
        }
    }
    result.run {
        log.i("result: durationMs=$durationMs, fileSizeBytes=$fileSizeBytes, averageAudioBitrate=$averageAudioBitrate, averageVideoBitrate=$averageVideoBitrate, videoFrameCount=$videoFrameCount")
    }
    return Pair(tmpFile, storeMimeType)
}
