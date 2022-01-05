package jp.juggler.util

import com.otaliastudios.transcoder.Transcoder
import com.otaliastudios.transcoder.TranscoderListener
import com.otaliastudios.transcoder.common.Size
import com.otaliastudios.transcoder.resize.Resizer
import com.otaliastudios.transcoder.strategy.DefaultAudioStrategy
import com.otaliastudios.transcoder.strategy.DefaultVideoStrategy
import jp.juggler.util.VideoInfo.Companion.videoInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.io.File
import java.io.FileInputStream
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

private val log = LogCategory("MovieUtils")

data class MovieResizeConfig(
    var mode: Int = 0,
    var limitFrameRate: Int = 30,
    var limitBitrate: Long = 2_000_000L,
    var limitPixelMatrix: Int = 2304000,
) {
    companion object {
        const val MODE_AUTO = 0
        const val MODE_NO = 1
        const val NODE_ALWAYS = 2
    }
}

class AtMostSquarePixelResizer(private val limit: Int) : Resizer {
    override fun getOutputSize(inputSize: Size): Size {
        val inSquarePixel = abs(inputSize.major) * abs(inputSize.minor)
        if (inSquarePixel <= limit || inputSize.major <= 0 || inputSize.minor <= 0) {
            return inputSize
        }
        val aspect = inputSize.major.toFloat() / inputSize.minor.toFloat()
        return Size(
            max(1, (sqrt(limit.toFloat() * aspect) + 0.5f).toInt()),
            max(1, (sqrt(limit.toFloat() / aspect) + 0.5f).toInt()),
        )
    }
}

@Suppress("BlockingMethodInNonBlockingContext")
suspend fun transcodeVideo(
    inFile: File,
    outFile: File,
    resizeConfig: MovieResizeConfig,
    onProgress: (Float) -> Unit,
): File = try {
    withContext(Dispatchers.IO) {
        when (resizeConfig.mode) {
            MovieResizeConfig.MODE_NO ->
                return@withContext inFile
            MovieResizeConfig.MODE_AUTO -> {
                val info = inFile.videoInfo
                if (info.size.w * info.size.h <= resizeConfig.limitPixelMatrix &&
                    (info.actualBps ?: 0) <= resizeConfig.limitBitrate &&
                    (info.frameRatio?.toInt() ?: 0) <= resizeConfig.limitFrameRate
                ) {
                    log.i("transcodeVideo skip.")
                    return@withContext inFile
                }
            }
        }

        val resultFile = FileInputStream(inFile).use { inStream ->
            // 進捗コールバックの発生頻度が多すぎるので間引く
            val progressChannel = Channel<Float>(capacity = Channel.CONFLATED)
            val progressSender = launch(Dispatchers.Main) {
                try {
                    while (true) {
                        onProgress(progressChannel.receive())
                        delay(1000L)
                    }
                } catch (ex: Throwable) {
                    when (ex) {
                        is ClosedReceiveChannelException -> log.i("progress closed.")
                        is CancellationException -> log.i("progress cancelled.")
                        else -> log.w(ex)
                    }
                }
            }

            try {
                suspendCancellableCoroutine<File> { cont ->
                    // https://github.com/natario1/Transcoder/pull/160
                    // ワークアラウンドとしてファイルではなくfdを渡す
                    val future = Transcoder.into(outFile.canonicalPath)
                        .addDataSource(inStream.fd)
                        .setVideoTrackStrategy(
                            DefaultVideoStrategy.Builder()
                                .addResizer(
                                    AtMostSquarePixelResizer(resizeConfig.limitPixelMatrix)
                                )
                                .frameRate(resizeConfig.limitFrameRate)
                                .keyFrameInterval(10f)
                                .bitRate(resizeConfig.limitBitrate)
                                .build()
                        )
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
