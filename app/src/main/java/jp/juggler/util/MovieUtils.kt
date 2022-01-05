package jp.juggler.util

import com.otaliastudios.transcoder.Transcoder
import com.otaliastudios.transcoder.TranscoderListener
import com.otaliastudios.transcoder.common.Size
import com.otaliastudios.transcoder.strategy.DefaultAudioStrategy
import com.otaliastudios.transcoder.strategy.DefaultVideoStrategy
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

@Suppress("BlockingMethodInNonBlockingContext")
suspend fun transcodeVideo(
    info: VideoInfo,
    inFile: File,
    outFile: File,
    resizeConfig: MovieResizeConfig,
    onProgress: (Float) -> Unit,
): File = try {
    withContext(Dispatchers.IO) {
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
                        .setVideoTrackStrategy(DefaultVideoStrategy.Builder()
                            .addResizer { inSize ->
                                val squarePixels = inSize.major * inSize.minor
                                val limit = resizeConfig.limitSquarePixels
                                if (squarePixels <= limit || inSize.major <= 0 || inSize.minor <= 0) {
                                    // 入力サイズが0以下の場合もアスペクト計算に支障がでるのでリサイズできない
                                    inSize
                                } else {
                                    // アスペクト比を維持しつつ平方ピクセルが指定に収まるようにする
                                    val aspect = inSize.major.toFloat() / inSize.minor.toFloat()
                                    Size(
                                        max(1, (sqrt(limit.toFloat() * aspect) + 0.5f).toInt()),
                                        max(1, (sqrt(limit.toFloat() / aspect) + 0.5f).toInt()),
                                    )
                                }
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
