package jp.juggler.util.media

import com.otaliastudios.transcoder.Transcoder
import com.otaliastudios.transcoder.TranscoderListener
import com.otaliastudios.transcoder.common.Size
import com.otaliastudios.transcoder.strategy.DefaultAudioStrategy
import com.otaliastudios.transcoder.strategy.DefaultVideoStrategy
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.log.LogCategory
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.io.File
import java.io.FileInputStream
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private val log = LogCategory("MovieUtils")

enum class MovideResizeMode(val int: Int) {
    Auto(0),
    No(1),
    Always(2),
    ;

    companion object {
        fun fromInt(i: Int) = values().find { it.int == i } ?: Auto
    }
}

data class MovieResizeConfig(
    val mode: MovideResizeMode,
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
        MovideResizeMode.No -> false
        MovideResizeMode.Always -> true
        MovideResizeMode.Auto ->
            info.squarePixels > limitSquarePixels ||
                    (info.actualBps ?: 0).toFloat() > limitBitrate.toFloat() * 1.5f ||
                    (info.frameRatio?.toInt() ?: 0) > limitFrameRate
    }
}

/**
 * レシーバが奇数なら+1した値を返す
 *
 * `[OMX.qcom.video.encoder.avc] video encoder does not support odd resolution 1018x2263`
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
            MovideResizeMode.No -> return@withContext inFile
            MovideResizeMode.Always -> Unit
            MovideResizeMode.Auto -> {
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
