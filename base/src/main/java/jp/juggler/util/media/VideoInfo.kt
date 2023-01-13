package jp.juggler.util.media

import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import jp.juggler.util.log.LogCategory
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * 動画の情報
 */
@Suppress("MemberVisibilityCanBePrivate")
class VideoInfo(
    val file: File,
    mmr: MediaMetadataRetriever,
) {

    companion object {
        private val log = LogCategory("VideoInfo")

        val File.videoInfo: VideoInfo
            get() = MediaMetadataRetriever().use { mmr ->
                mmr.setDataSource(canonicalPath)
                VideoInfo(this, mmr)
            }

        private fun MediaMetadataRetriever.string(key: Int) =
            extractMetadata(key)

        private fun MediaMetadataRetriever.int(key: Int) =
            string(key)?.toIntOrNull()

        private fun MediaMetadataRetriever.long(key: Int) =
            string(key)?.toLongOrNull()

        /**
         * 調査のためコーデックを列挙して情報をログに出す
         */
        fun dumpCodec() {
            val mcl = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in mcl.codecInfos) {
                try {
                    if (!info.isEncoder) continue
                    val caps = try {
                        info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC) ?: continue
                    } catch (ex: Throwable) {
                        log.w(ex, "getCapabilitiesForType failed.")
                        continue
                    }

                    for (colorFormat in caps.colorFormats) {
                        log.i("${info.name} color 0x${colorFormat.toString(16)}")
                        // OMX.qcom.video.encoder.avc color 7fa30c04 不明
                        // OMX.qcom.video.encoder.avc color 7f000789 COLOR_FormatSurface
                        // OMX.qcom.video.encoder.avc color 7f420888 COLOR_FormatYUV420Flexible
                        // OMX.qcom.video.encoder.avc color 15 COLOR_Format32bitBGRA8888
                    }
                    caps.videoCapabilities.bitrateRange?.let { range ->
                        log.i("bitrateRange $range")
                    }
                    caps.videoCapabilities.supportedFrameRates?.let { range ->
                        log.i("supportedFrameRates $range")
                    }
                    if (Build.VERSION.SDK_INT >= 28) {
                        caps.encoderCapabilities.qualityRange?.let { range ->
                            log.i("qualityRange $range")
                        }
                    }
                } catch (ex: Throwable) {
                    log.w(ex, "dumpCodec failed.")
                    // type is not supported
                }
            }
        }
    }

    data class Size(var w: Int, var h: Int) {
        override fun toString() = "[$w,$h]"

        private val aspect: Float get() = w.toFloat() / h.toFloat()

        /**
         * アスペクト比を維持しつつ上限に合わせた解像度を提案する
         * - 拡大はしない
         */
        fun scaleTo(limitLonger: Int, limitShorter: Int): Size {
            val inSize = this
            // ゼロ除算対策
            if (inSize.w < 1 || inSize.h < 1) {
                return Size(limitLonger, limitShorter)
            }
            val inAspect = inSize.aspect
            // 入力の縦横に合わせて上限を決める
            val outSize = if (inAspect >= 1f) {
                Size(limitLonger, limitShorter)
            } else {
                Size(limitShorter, limitLonger)
            }
            // 縦横比を比較する
            return if (inAspect >= outSize.aspect) {
                // 入力のほうが横長なら横幅基準でスケーリングする
                // 拡大はしない
                val scale = outSize.w.toFloat() / inSize.w.toFloat()
                if (scale >= 1f) inSize else outSize.apply {
                    h = min(h, (scale * inSize.h + 0.5f).toInt())
                }
            } else {
                // 入力のほうが縦長なら縦幅基準でスケーリングする
                // 拡大はしない
                val scale = outSize.h.toFloat() / inSize.h.toFloat()
                if (scale >= 1f) inSize else outSize.apply {
                    w = min(w, (scale * inSize.w + 0.5f).toInt())
                }
            }
        }
    }

    val mimeType = mmr.string(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

    val rotation = mmr.int(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) ?: 0

    val size = Size(
        mmr.int(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: 0,
        mmr.int(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: 0,
    )

    val squarePixels: Int get() = max(1, size.w) * max(1, size.h)

    val bitrate = mmr.int(MediaMetadataRetriever.METADATA_KEY_BITRATE)

    val duration = mmr.long(MediaMetadataRetriever.METADATA_KEY_DURATION)
        ?.toFloat()?.div(1000)?.takeIf { it > 0.1f }

    val frameCount = if (Build.VERSION.SDK_INT >= 28) {
        mmr.int(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.takeIf { it > 0 }
    } else {
        null
    }

    val frameRatio = if (frameCount != null && duration != null) {
        frameCount.toFloat().div(duration)
    } else {
        null
    }

    val audioSampleRate = if (Build.VERSION.SDK_INT >= 31) {
        mmr.int(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.takeIf { it > 0 }
    } else {
        null
    }

    val actualBps by lazy {
        val fileSize = file.length()

        // ファイルサイズを取得できないならエラー
        if (fileSize <= 0L) return@lazy null

        // 時間帳が短すぎるなら算出できない
        if (duration == null || duration < 0.1f) return@lazy null

        // bpsを計算
        fileSize.toFloat().div(duration).times(8).toInt()
    }

    /**
     * 動画のファイルサイズが十分に小さいなら真
     */
    fun isSmallEnough(limitBps: Int): Boolean {
        val fileSize = file.length()
        // ファイルサイズを取得できないならエラー
        if (fileSize <= 0L) error("too small file. ${file.canonicalPath}")
        // ファイルサイズが500KB以内ならビットレートを気にしない
        if (fileSize < 500_000) return true

        // ファイルサイズからビットレートを計算できなかったなら再エンコード必要
        val actualBps = this.actualBps ?: return false

        // bpsを計算
        log.i("isSmallEnough duration=$duration, bps=$actualBps/$limitBps")
        return actualBps <= limitBps
    }

    override fun toString() =
        "rotation=$rotation, size=$size, frameRatio=$frameRatio, bitrate=${actualBps ?: bitrate}, audioSampleRate=$audioSampleRate, mimeType=$mimeType, file=${file.canonicalPath}"
}
