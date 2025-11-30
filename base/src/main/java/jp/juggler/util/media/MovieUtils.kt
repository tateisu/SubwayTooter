package jp.juggler.util.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.otaliastudios.transcoder.Transcoder
import com.otaliastudios.transcoder.TranscoderListener
import com.otaliastudios.transcoder.common.Size
import com.otaliastudios.transcoder.strategy.DefaultAudioStrategy
import com.otaliastudios.transcoder.strategy.DefaultVideoStrategy
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.data.clip
import jp.juggler.util.log.LogCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private val log = LogCategory("MovieUtils")

enum class MovieResizeMode(val int: Int) {
    Auto(0),
    No(1),
    Always(2),
    ;

    companion object {
        fun fromInt(i: Int) = entries.find { it.int == i } ?: Auto
    }
}

data class MovieResizeConfig(
    val mode: MovieResizeMode,
    val limitFrameRate: Int,
    val limitBitrate: Long,
    val limitSquarePixels: Int,
) {
    // 値を狭めた新しいオブジェクトを返す
    fun restrict(
        limitFrameRate: Int? = null,
        limitBitrate: Long? = null,
        limitSquarePixels: Int? = null,
    ) = MovieResizeConfig(
        mode = this.mode,
        limitFrameRate = min(
            limitFrameRate ?: this.limitFrameRate,
            this.limitFrameRate
        ),
        limitBitrate = min(
            limitBitrate ?: this.limitBitrate,
            this.limitBitrate
        ),
        limitSquarePixels = min(
            limitSquarePixels ?: this.limitSquarePixels,
            this.limitSquarePixels
        ),
    )

    // トランスコードをスキップする判定
    fun isTranscodeRequired(info: VideoInfo) = when (mode) {
        MovieResizeMode.No -> false
        MovieResizeMode.Always -> true
        MovieResizeMode.Auto ->
            info.squarePixels > limitSquarePixels ||
                    (info.actualBps ?: 0).toFloat() > limitBitrate.toFloat() * 1.5f ||
                    (info.frameRatio == null || info.frameRatio < 1f || info.frameRatio > limitFrameRate)
    }
}

/**
 * レシーバが奇数なら+1した値を返す
 *
 * `OMX.qcom.video.encoder.avc video encoder does not support odd resolution 1018x2263`
 */
private fun Int.fixOdd() = if (and(1) == 0) this else this + 1

/**
 * 動画のピクセルサイズを制限に合わせてスケーリングする
 */
private fun createScaledSize(inSize: Size, limitSquarePixels: Int): Size {

    if (inSize.major <= 0 || inSize.minor <= 0) {
        // 入力サイズの縦横が0以下の場合、アスペクト比を計算できないのでリサイズできない
        log.w("createScaledSize: video size not valid. major=${inSize.major}, minor=${inSize.minor}")
        return inSize
    }

    val squarePixels = inSize.major * inSize.minor
    if (squarePixels <= limitSquarePixels) {
        return inSize
    }

    val aspect = inSize.major.toFloat() / inSize.minor.toFloat()
    return Size(
        max(1f, sqrt(limitSquarePixels.toFloat() * aspect)).toInt().fixOdd(),
        max(1f, sqrt(limitSquarePixels.toFloat() / aspect)).toInt().fixOdd(),
    )
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
suspend fun transcodeVideoMedia3Transformer(
    context: Context,
    info: VideoInfo,
    inFile: File,
    outFile: File,
    resizeConfig: MovieResizeConfig,
    onProgress: (Float) -> Unit,
): File = try {
    withContext(AppDispatchers.MainImmediate) {

        when (resizeConfig.mode) {
            MovieResizeMode.No -> return@withContext inFile
            MovieResizeMode.Always -> Unit
            MovieResizeMode.Auto -> {
                if (!resizeConfig.isTranscodeRequired(info)) {
                    log.i("transcodeVideoMedia3Transformer: transcode not required.")
                    return@withContext inFile
                }
            }
        }

        val srcMediaItem = MediaItem.fromUri(Uri.fromFile(inFile))
        val editedMediaItem = EditedMediaItem.Builder(srcMediaItem).apply {

            // 入力のフレームレートが高すぎるなら制限する
            if (info.frameRatio == null || info.frameRatio < 1f ||
                info.frameRatio > resizeConfig.limitFrameRate
            ) {
                // This should be set for inputs that don't have an implicit frame rate (e.g. images).
                // It will be ignored for inputs that do have an implicit frame rate (e.g. video).
                setFrameRate(resizeConfig.limitFrameRate)
            }

            // 入力のピクセルサイズが大きすぎるなら制限する
            if (info.size.w > 0 && info.size.h > 0 &&
                info.squarePixels > resizeConfig.limitSquarePixels
            ) {
                // 端数やodd補正などによる問題が出なさそうなscale値を計算する
                fun calcScale(
                    srcLongerSide: Int,
                    aspect: Float,
                    limitSqPixel: Int,
                ): Float {
                    var sqPixel = limitSqPixel
                    while (true) {
                        val newW = ceil(sqrt(sqPixel * aspect)).toInt().fixOdd()
                        val newH = ceil(sqrt(sqPixel / aspect)).toInt().fixOdd()
                        if (newW * newH <= resizeConfig.limitSquarePixels) {
                            return max(newW, newH).toFloat().div(srcLongerSide)
                        }
                        sqPixel -= srcLongerSide
                    }
                }

                val scale = calcScale(
                    srcLongerSide = max(info.size.w, info.size.h),
                    aspect = info.size.w.toFloat() / info.size.h.toFloat(),
                    limitSqPixel = resizeConfig.limitSquarePixels
                )
                val effects = Effects(
                    /* audioProcessors */ emptyList(),
                    /* videoEffects */ listOf(
                        ScaleAndRotateTransformation.Builder().apply {
                            setScale(scale, scale)
                        }.build()
                    )
                )
                setEffects(effects)
            }
        }.build()

        // 完了検知
        val completed = AtomicBoolean(false)
        val error = AtomicReference<Throwable>(null)
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                super.onCompleted(composition, exportResult)
                completed.compareAndSet(false, true)
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                super.onError(composition, exportResult, exportException)
                error.compareAndSet(null, exportException)
            }
        }

        val videoEncoderSettings = VideoEncoderSettings.Builder().apply {
            setBitrate(resizeConfig.limitBitrate.clip(100_000L, Int.MAX_VALUE.toLong()).toInt())
        }.build()

        val encoderFactory = DefaultEncoderFactory.Builder(context).apply {
            setRequestedVideoEncoderSettings(videoEncoderSettings)
            // missing setRequestedAudioEncoderSettings
        }.build()

        // 開始
        val transformer = Transformer.Builder(context).apply {
            setEncoderFactory(encoderFactory)
            setAudioMimeType(MimeTypes.AUDIO_AAC)
            setVideoMimeType(MimeTypes.VIDEO_H264)
            addListener(listener)
        }.build()

        transformer.start(editedMediaItem, outFile.canonicalPath)

        // 完了まで待機しつつ、定期的に進捗コールバックを呼ぶ
        val progressHolder = ProgressHolder()
        while (!completed.get()) {
            error.get()?.let { throw it }
            val progress = when (transformer.getProgress(progressHolder)) {
                Transformer.PROGRESS_STATE_NOT_STARTED -> 0f
                else -> progressHolder.progress.toFloat() / 100f
            }
            onProgress(progress)
            delay(1000L)
        }
        outFile
    }
} catch (ex: Throwable) {
    log.w("delete outFile due to error.")
    outFile.delete()
    throw ex
}

@Suppress("BlockingMethodInNonBlockingContext")
suspend fun transcodeVideo(
    info: VideoInfo,
    inFile: File,
    outFile: File,
    resizeConfig: MovieResizeConfig,
    onProgress: (Float) -> Unit,
): File = try {
    withContext(AppDispatchers.IO) {
        if (!resizeConfig.isTranscodeRequired(info)) {
            log.i("transcodeVideo: isTranscodeRequired returns false.")
            return@withContext inFile
        }

        when (resizeConfig.mode) {
            MovieResizeMode.No -> return@withContext inFile
            MovieResizeMode.Always -> Unit
            MovieResizeMode.Auto -> {
                if (info.squarePixels <= resizeConfig.limitSquarePixels &&
                    (info.actualBps ?: 0).toFloat() <= resizeConfig.limitBitrate * 1.5f &&
                    (info.frameRatio?.toInt() ?: 0) <= resizeConfig.limitFrameRate
                ) {
                    log.i("transcodeVideo: no need to transcode.")
                    return@withContext inFile
                }
            }
        }

        val resultFile = FileInputStream(inFile).use { inStream ->
            // 進捗コールバックの発生頻度が多すぎるので間引く
            val progressChannel = Channel<Float>(capacity = Channel.CONFLATED)
            val progressSender = launch(AppDispatchers.MainImmediate) {
                try {
                    while (true) {
                        onProgress(progressChannel.receive())
                        delay(1000L)
                    }
                } catch (ex: Throwable) {
                    when (ex) {
                        is ClosedReceiveChannelException -> log.i("progress closed.")
                        is CancellationException -> log.i("progress cancelled.")
                        else -> log.w(ex, "progress error")
                    }
                }
            }

            try {
                suspendCancellableCoroutine { cont ->
                    // https://github.com/natario1/Transcoder/pull/160
                    // ワークアラウンドとしてファイルではなくfdを渡す
                    val future = Transcoder.into(outFile.canonicalPath)
                        .addDataSource(inStream.fd)
                        .setVideoTrackStrategy(DefaultVideoStrategy.Builder()
                            .addResizer { inSize ->
                                createScaledSize(
                                    inSize = inSize,
                                    limitSquarePixels = resizeConfig.limitSquarePixels
                                )
                            }
                            .frameRate(resizeConfig.limitFrameRate)
                            .keyFrameInterval(10f)
                            .bitRate(resizeConfig.limitBitrate)
                            .build())
                        .setAudioTrackStrategy(
                            DefaultAudioStrategy.Builder()
                                .channels(2)
                                .sampleRate(44100)
                                .bitRate(96_000L)
                                .build()
                        )
                        .setListener(object : TranscoderListener {
                            override fun onTranscodeCanceled() {
                                log.w("onTranscodeCanceled")
                                cont.resumeWithException(CancellationException("transcode cancelled."))
                            }

                            override fun onTranscodeFailed(exception: Throwable) {
                                log.w("onTranscodeFailed")
                                cont.resumeWithException(exception)
                            }

                            override fun onTranscodeCompleted(successCode: Int) {
                                when (successCode) {
                                    Transcoder.SUCCESS_TRANSCODED -> outFile
                                    /* Transcoder.SUCCESS_NOT_NEEDED */ else -> inFile
                                }.let { cont.resumeWith(Result.success(it)) }
                            }

                            override fun onTranscodeProgress(progress: Double) {
                                val result = progressChannel.trySend(progress.toFloat())
                                if (!result.isSuccess) {
                                    log.w("trySend $result")
                                }
                            }
                        }).transcode()
                    cont.invokeOnCancellation { future.cancel(true) }
                }
            } finally {
                progressChannel.close()
                progressSender.cancelAndJoin()
            }
        }
        resultFile
    }
} catch (ex: Throwable) {
    log.w("delete outFile due to error.")
    outFile.delete()
    throw ex
}
