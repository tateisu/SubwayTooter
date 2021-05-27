package jp.juggler.subwaytooter

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.view.View
import android.view.Window
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.Player.TimelineChangeReason
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.*
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.util.ProgressResponseBody
import jp.juggler.subwaytooter.view.PinchBitmapView
import jp.juggler.util.*
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.*
import javax.net.ssl.HttpsURLConnection
import kotlin.math.max

class ActMediaViewer : AppCompatActivity(), View.OnClickListener {

    companion object {

        internal val log = LogCategory("ActMediaViewer")

        internal val download_history_list = LinkedList<DownloadHistory>()
        internal const val DOWNLOAD_REPEAT_EXPIRE = 3000L
        internal const val short_limit = 5000L

        private const val PERMISSION_REQUEST_CODE = 1

        internal const val EXTRA_IDX = "idx"
        internal const val EXTRA_DATA = "data"
        internal const val EXTRA_SERVICE_TYPE = "serviceType"

        internal const val STATE_PLAYER_POS = "playerPos"
        internal const val STATE_PLAYER_PLAY_WHEN_READY = "playerPlayWhenReady"
        internal const val STATE_LAST_VOLUME = "lastVolume"

        internal fun <T : TootAttachmentLike> encodeMediaList(list: ArrayList<T>?) =
                list?.encodeJson()?.toString() ?: "[]"

        internal fun decodeMediaList(src: String?) =
                ArrayList<TootAttachment>().apply {
                    src?.decodeJsonArray()?.forEach {
                        if (it !is JsonObject) return@forEach
                        add(TootAttachment.decodeJson(it))
                    }
                }

        fun open(
				activity: ActMain,
				serviceType: ServiceType,
				list: ArrayList<TootAttachmentLike>,
				idx: Int
		) {
            val intent = Intent(activity, ActMediaViewer::class.java)
            intent.putExtra(EXTRA_IDX, idx)
            intent.putExtra(EXTRA_SERVICE_TYPE, serviceType.ordinal)
            intent.putExtra(EXTRA_DATA, encodeMediaList(list))
            activity.startActivity(intent)
            activity.overridePendingTransition(R.anim.slide_from_bottom, android.R.anim.fade_out)
        }
    }

    internal var idx: Int = 0
    private lateinit var media_list: ArrayList<TootAttachment>
    private lateinit var serviceType: ServiceType

    private lateinit var pbvImage: PinchBitmapView
    private lateinit var btnPrevious: View
    private lateinit var btnNext: View
    private lateinit var tvError: TextView
    private lateinit var exoPlayer: SimpleExoPlayer
    private lateinit var exoView: PlayerView
    private lateinit var svDescription: View
    private lateinit var tvDescription: TextView
    private lateinit var tvStatus: TextView
    private lateinit var cbMute: CheckBox
    private var lastVolume = Float.NaN

    internal var buffering_last_shown: Long = 0

    private val player_listener = object : Player.Listener {

        override fun onTimelineChanged(
				timeline: Timeline,
				@TimelineChangeReason reason: Int
		) {
            log.d("exoPlayer onTimelineChanged reason=$reason")
        }

        override fun onSeekProcessed() {
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        }

        override fun onLoadingChanged(isLoading: Boolean) {
            // かなり頻繁に呼ばれる
            // warning.d( "exoPlayer onLoadingChanged %s" ,isLoading );
        }

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            // かなり頻繁に呼ばれる
            // warning.d( "exoPlayer onPlayerStateChanged %s %s", playWhenReady, playbackState );
            if (playWhenReady && playbackState == Player.STATE_BUFFERING) {
                val now = SystemClock.elapsedRealtime()
                if (now - buffering_last_shown >= short_limit && exoPlayer.duration >= short_limit) {
                    buffering_last_shown = now
                    showToast(false, R.string.video_buffering)
                }
                /*
                    exoPlayer.getDuration() may returns negative value (TIME_UNSET ,same as Long.MIN_VALUE + 1).
                */
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            log.d("exoPlayer onRepeatModeChanged %d", repeatMode)
        }

        override fun onPlayerError(error: ExoPlaybackException) {
            log.d("exoPlayer onPlayerError")
            showToast(error, "player error.")
        }

        override fun onPositionDiscontinuity(reason: Int) {
            log.d("exoPlayer onPositionDiscontinuity reason=$reason")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        log.d("onSaveInstanceState")

        outState.putInt(EXTRA_IDX, idx)
        outState.putInt(EXTRA_SERVICE_TYPE, serviceType.ordinal)
        outState.putString(EXTRA_DATA, encodeMediaList(media_list))

        outState.putLong(STATE_PLAYER_POS, exoPlayer.currentPosition)
        outState.putBoolean(STATE_PLAYER_PLAY_WHEN_READY, exoPlayer.playWhenReady)
        outState.putFloat(STATE_LAST_VOLUME, lastVolume)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        App1.setActivityTheme(this, noActionBar = true, forceDark = true)

        val intent = intent

        this.idx = savedInstanceState?.getInt(EXTRA_IDX) ?: intent.getIntExtra(EXTRA_IDX, idx)

        this.serviceType = ServiceType.values()[
				savedInstanceState?.getInt(EXTRA_SERVICE_TYPE)
						?: intent.getIntExtra(EXTRA_SERVICE_TYPE, 0)
		]

        this.media_list = decodeMediaList(
				savedInstanceState?.getString(EXTRA_DATA)
						?: intent.getStringExtra(EXTRA_DATA)
		)

        if (idx < 0 || idx >= media_list.size) idx = 0

        initUI()

        load(savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        pbvImage.setBitmap(null)
        exoPlayer.release()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.slide_to_bottom)
    }

    internal fun initUI() {
        setContentView(R.layout.act_media_viewer)
        App1.initEdgeToEdge(this)

        pbvImage = findViewById(R.id.pbvImage)
        btnPrevious = findViewById(R.id.btnPrevious)
        btnNext = findViewById(R.id.btnNext)
        exoView = findViewById(R.id.exoView)
        tvError = findViewById(R.id.tvError)
        svDescription = findViewById(R.id.svDescription)
        tvDescription = findViewById(R.id.tvDescription)
        tvStatus = findViewById(R.id.tvStatus)
        cbMute = findViewById(R.id.cbMute)

        val enablePaging = media_list.size > 1
        btnPrevious.isEnabledAlpha = enablePaging
        btnNext.isEnabledAlpha = enablePaging

        btnPrevious.setOnClickListener(this)
        btnNext.setOnClickListener(this)
        findViewById<View>(R.id.btnDownload).setOnClickListener(this)
        findViewById<View>(R.id.btnMore).setOnClickListener(this)

        cbMute.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // mute
                lastVolume = exoPlayer.volume
                exoPlayer.volume = 0f
            } else {
                // unmute
                exoPlayer.volume = when {
                    lastVolume.isNaN() -> 1f
                    lastVolume <= 0f -> 1f
                    else -> lastVolume
                }
                lastVolume = Float.NaN
            }
        }

        pbvImage.setCallback(object : PinchBitmapView.Callback {
			override fun onSwipe(deltaX: Int, deltaY: Int) {
				if (isDestroyed) return
				if (deltaX != 0) {
					loadDelta(deltaX)
				} else {
					log.d("finish by vertical swipe")
					finish()
				}
			}

			override fun onMove(
					bitmap_w: Float,
					bitmap_h: Float,
					tx: Float,
					ty: Float,
					scale: Float
			) {
				App1.getAppState(this@ActMediaViewer).handler.post(Runnable {
					if (isDestroyed) return@Runnable
					if (tvStatus.visibility == View.VISIBLE) {
						tvStatus.text = getString(
								R.string.zooming_of,
								bitmap_w.toInt(),
								bitmap_h.toInt(),
								scale
						)
					}
				})
			}
		})

        exoPlayer = SimpleExoPlayer.Builder(this).build()
        exoPlayer.addListener(player_listener)
        exoView.player = exoPlayer
    }

    internal fun loadDelta(delta: Int) {
        if (media_list.size < 2) return
        val size = media_list.size
        idx = (idx + size + delta) % size
        load()
    }

    internal fun load(state: Bundle? = null) {

        exoPlayer.stop()
        pbvImage.visibility = View.GONE
        exoView.visibility = View.GONE
        tvError.visibility = View.GONE
        svDescription.visibility = View.GONE
        tvStatus.visibility = View.GONE

        if (idx < 0 || idx >= media_list.size) {
            showError(getString(R.string.media_attachment_empty))
            return
        }
        val ta = media_list[idx]
        val description = ta.description
        if (description?.isNotEmpty() == true) {
            svDescription.visibility = View.VISIBLE
            tvDescription.text = description
        }

        when (ta.type) {

			TootAttachmentType.Unknown ->
				showError(getString(R.string.media_attachment_type_error, ta.type.id))

			TootAttachmentType.Image ->
				loadBitmap(ta)

			TootAttachmentType.Video,
			TootAttachmentType.GIFV,
			TootAttachmentType.Audio ->
				loadVideo(ta, state)
        }

    }

    private fun showError(message: String) {
        exoView.visibility = View.GONE
        pbvImage.visibility = View.GONE
        tvError.visibility = View.VISIBLE
        tvError.text = message

    }

    @SuppressLint("StaticFieldLeak")
    private fun loadVideo(ta: TootAttachment, state: Bundle? = null) {

        cbMute.vg(true)
        if (cbMute.isChecked && lastVolume.isFinite()) {
            exoPlayer.volume = 0f
        }

        val url = ta.getLargeUrl(App1.pref)
        if (url == null) {
            showError("missing media attachment url.")
            return
        }

        // https://github.com/google/ExoPlayer/issues/1819
        HttpsURLConnection.setDefaultSSLSocketFactory(MySslSocketFactory)

        exoView.visibility = View.VISIBLE

        val defaultBandwidthMeter = DefaultBandwidthMeter.Builder(this).build()
        val extractorsFactory = DefaultExtractorsFactory()

        val dataSourceFactory = DefaultDataSourceFactory(
				this, Util.getUserAgent(this, getString(R.string.app_name)), defaultBandwidthMeter
		)

        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                .createMediaSource(MediaItem.Builder().setUri(url.toUri()).build())

        mediaSource.addEventListener(App1.getAppState(this).handler, mediaSourceEventListener)

        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.repeatMode = when (ta.type) {
			TootAttachmentType.Video -> Player.REPEAT_MODE_OFF
            // GIFV or AUDIO
            else -> Player.REPEAT_MODE_ALL
        }
        if (state == null) {
            exoPlayer.playWhenReady = true
        } else {
            exoPlayer.playWhenReady = state.getBoolean(STATE_PLAYER_PLAY_WHEN_READY, true)
            exoPlayer.seekTo(max(0L, state.getLong(STATE_PLAYER_POS, 0L)))
            lastVolume = state.getFloat(STATE_LAST_VOLUME, 1f)
        }
    }

    private val mediaSourceEventListener = object : MediaSourceEventListener {
        override fun onLoadStarted(
				windowIndex: Int,
				mediaPeriodId: MediaSource.MediaPeriodId?,
				loadEventInfo: LoadEventInfo,
				mediaLoadData: MediaLoadData
		) {
            log.d("onLoadStarted")
        }

        override fun onDownstreamFormatChanged(
				windowIndex: Int,
				mediaPeriodId: MediaSource.MediaPeriodId?,
				mediaLoadData: MediaLoadData
		) {
            log.d("onDownstreamFormatChanged")
        }

        override fun onUpstreamDiscarded(
				windowIndex: Int,
				mediaPeriodId: MediaSource.MediaPeriodId,
				mediaLoadData: MediaLoadData
		) {
            log.d("onUpstreamDiscarded")
        }

        override fun onLoadCompleted(
				windowIndex: Int,
				mediaPeriodId: MediaSource.MediaPeriodId?,
				loadEventInfo: LoadEventInfo,
				mediaLoadData: MediaLoadData
		) {
            log.d("onLoadCompleted")
        }

        override fun onLoadCanceled(
				windowIndex: Int,
				mediaPeriodId: MediaSource.MediaPeriodId?,
				loadEventInfo: LoadEventInfo,
				mediaLoadData: MediaLoadData
		) {
            log.d("onLoadCanceled")
        }

        override fun onLoadError(
				windowIndex: Int,
				mediaPeriodId: MediaSource.MediaPeriodId?,
				loadEventInfo: LoadEventInfo,
				mediaLoadData: MediaLoadData,
				error: IOException,
				wasCanceled: Boolean
		) {
            showError(error.withCaption("load error."))
        }
    }

    private fun decodeBitmap(
        options:BitmapFactory.Options,
        data: ByteArray,
        @Suppress("SameParameterValue") pixel_max: Int
    ): Pair<Bitmap?, String?> {

        val orientation: Int? = ByteArrayInputStream(data).imageOrientation()

        // detects image size
        options.inJustDecodeBounds = true
        options.inScaled = false
        options.outWidth = 0
        options.outHeight = 0
        BitmapFactory.decodeByteArray(data, 0, data.size, options)
        var w = options.outWidth
        var h = options.outHeight
        if (w <= 0 || h <= 0) {
            return Pair(null, "can't decode image bounds.")
        }

        // calc bits to reduce size
        var bits = 0
        while (w > pixel_max || h > pixel_max) {
            ++bits
            w = w shr 1
            h = h shr 1
        }
        options.inJustDecodeBounds = false
        options.inSampleSize = 1 shl bits

        // decode image
        val bitmap1 = BitmapFactory.decodeByteArray(data, 0, data.size, options)
            ?: return Pair(null, "BitmapFactory.decodeByteArray returns null.")

        val srcWidth = bitmap1.width.toFloat()
        val srcHeight = bitmap1.height.toFloat()
        if (srcWidth <= 0f || srcHeight <= 0f) {
            bitmap1.recycle()
            return Pair(null, "image size <= 0")
        }

        val dstSize = rotateSize(orientation, srcWidth, srcHeight)
        val dstSizeInt = Point(
            max(1, (dstSize.x + 0.5f).toInt()),
            max(1, (dstSize.y + 0.5f).toInt())
        )

        // 回転行列を作る
        val matrix = Matrix()
        matrix.reset()

        // 画像の中心が原点に来るようにして
        matrix.postTranslate(srcWidth * -0.5f, srcHeight * -0.5f)

        // orientationに合わせた回転指定
        matrix.resolveOrientation(orientation)

        // 表示領域に埋まるように平行移動
        matrix.postTranslate(dstSize.x * 0.5f, dstSize.y * 0.5f)

        // 回転後の画像
        val bitmap2 = try {
            Bitmap.createBitmap(dstSizeInt.x, dstSizeInt.y, Bitmap.Config.ARGB_8888)
                ?: return Pair(bitmap1, "createBitmap returns null")
        } catch (ex: Throwable) {
            log.trace(ex)
            return Pair(bitmap1, ex.withCaption("createBitmap failed."))
        }

        try {
            Canvas(bitmap2).drawBitmap(
                bitmap1,
                matrix,
                Paint().apply { isFilterBitmap = true }
            )
        } catch (ex: Throwable) {
            log.trace(ex)
            bitmap2.recycle()
            return Pair(bitmap1, ex.withCaption("drawBitmap failed."))
        }

        try {
            bitmap1.recycle()
        } catch (ex: Throwable) {
        }
        return Pair(bitmap2, null)
    }

    private suspend fun getHttpCached(
        client: TootApiClient,
        url: String
    ): Pair<TootApiResult?, ByteArray?> {
        val result = TootApiResult.makeWithCaption(url)

        val request = try {
            Request.Builder()
                .url(url)
                .cacheControl(App1.CACHE_CONTROL)
                .addHeader("Accept", "image/webp,image/*,*/*;q=0.8")
                .build()
        } catch (ex: Throwable) {
            result.setError(ex.withCaption("incorrect URL."))
            return Pair(result, null)
        }

        if (!client.sendRequest(
                result,
                tmpOkhttpClient = App1.ok_http_client_media_viewer
            ) {
                request
            }
        ) return Pair(result, null)

        if (client.isApiCancelled) return Pair(null, null)

        val response = result.response!!
        if (!response.isSuccessful) {
            result.parseErrorResponse()
            return Pair(result, null)
        }

        try {
            val ba = ProgressResponseBody.bytes(response) { bytesRead, bytesTotal ->
                // 50MB以上のデータはキャンセルする
                if (max(bytesRead, bytesTotal) >= 50000000) {
                    throw RuntimeException("media attachment is larger than 50000000")
                }
                client.publishApiProgressRatio(bytesRead.toInt(), bytesTotal.toInt())
            }
            if (client.isApiCancelled) return Pair(null, null)
            return Pair(result, ba)
        } catch (ex: Throwable) {
            result.parseErrorResponse(  "?")
            return Pair(result, null)
        }
    }

    @SuppressLint("StaticFieldLeak")
    private fun loadBitmap(ta: TootAttachment) {

        cbMute.visibility = View.INVISIBLE

        val urlList = ta.getLargeUrlList(App1.pref)
        if (urlList.isEmpty()) {
            showError("missing media attachment url.")
            return
        }

        tvStatus.visibility = View.VISIBLE
        tvStatus.text = null

        pbvImage.visibility = View.VISIBLE
        pbvImage.setBitmap(null)

        launchMain {
            val options = BitmapFactory.Options()

            var resultBitmap: Bitmap? = null

            runApiTask (progressStyle = ApiTask.PROGRESS_HORIZONTAL ){ client->
                if (urlList.isEmpty()) return@runApiTask TootApiResult("missing url")
                var lastResult: TootApiResult? = null
                for (url in urlList) {
                    val (result, ba) = getHttpCached(client, url)
                    lastResult = result
                    if (ba != null) {
                        client.publishApiProgress("decoding image…")

                        val (bitmap, error) = decodeBitmap(options,ba, 2048)
                        if (bitmap != null) {
                            resultBitmap = bitmap
                            break
                        }
                        if (error != null) lastResult = TootApiResult(error)
                    }
                }
                lastResult
            }.let{ result-> // may null
                when (val bitmap = resultBitmap) {
                    null -> if (result != null) showToast(true, result.error)
                    else -> pbvImage.setBitmap(bitmap)
                }
            }
        }
    }

    override fun onClick(v: View) {
        try {
            when (v.id) {

				R.id.btnPrevious -> loadDelta(-1)
				R.id.btnNext -> loadDelta(+1)
				R.id.btnDownload -> download(media_list[idx])
				R.id.btnMore -> more(media_list[idx])
            }
        } catch (ex: Throwable) {
            showToast(ex, "action failed.")
        }

    }

    internal class DownloadHistory(val time: Long, val url: String)

    private fun download(ta: TootAttachmentLike) {

        val permissionCheck = ContextCompat.checkSelfPermission(
				this,
				Manifest.permission.WRITE_EXTERNAL_STORAGE
		)
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            preparePermission()
            return
        }

        val downLoadManager: DownloadManager = systemService(this)
                ?: error("missing DownloadManager system service")

        val url = if (ta is TootAttachment) {
            ta.getLargeUrl(App1.pref)
        } else {
            null
        } ?: return

        // ボタン連打対策
        run {
            val now = SystemClock.elapsedRealtime()

            // 期限切れの履歴を削除
            val it = download_history_list.iterator()
            while (it.hasNext()) {
                val dh = it.next()
                if (now - dh.time >= DOWNLOAD_REPEAT_EXPIRE) {
                    // この履歴は十分に古いので捨てる
                    it.remove()
                } else if (url == dh.url) {
                    // 履歴に同じURLがあればエラーとする
                    showToast(false, R.string.dont_repeat_download_to_same_url)
                    return
                }
            }
            // 履歴の末尾に追加(履歴は古い順に並ぶ)
            download_history_list.addLast(DownloadHistory(now, url))
        }

        var fileName: String? = null

        try {
            val pathSegments = url.toUri().pathSegments
            if (pathSegments != null) {
                val size = pathSegments.size
                for (i in size - 1 downTo 0) {
                    val s = pathSegments[i]
                    if (s?.isNotEmpty() == true) {
                        fileName = s
                        break
                    }
                }
            }
        } catch (ex: Throwable) {
            log.trace(ex)
        }

        if (fileName == null) {
            fileName = url
                    .replaceFirst("https?://".asciiPattern(), "")
                    .replaceAll("[^.\\w\\d]+".asciiPattern(), "-")
        }
        if (fileName.length >= 20) fileName = fileName.substring(fileName.length - 20)

        val request = DownloadManager.Request(url.toUri())
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        request.setTitle(fileName)
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE or DownloadManager.Request.NETWORK_WIFI)

        // Android 10 以降では allowScanningByMediaScanner は無視される
        if (Build.VERSION.SDK_INT < 29) {
            //メディアスキャンを許可する
            @Suppress("DEPRECATION")
            request.allowScanningByMediaScanner()
        }

        //ダウンロード中・ダウンロード完了時にも通知を表示する
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        downLoadManager.enqueue(request)
        showToast(false, R.string.downloading)
    }

    private fun share(action: String, url: String) {

        try {
            val intent = Intent(action)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (action == Intent.ACTION_SEND) {
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_TEXT, url)
            } else {
                intent.data = url.toUri()
            }

            startActivity(intent)
        } catch (ex: Throwable) {
            showToast(ex, "can't open app.")
        }

    }

    internal fun copy(url: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
                ?: throw NotImplementedError("missing ClipboardManager system service")

        try {
            //クリップボードに格納するItemを作成
            val item = ClipData.Item(url)

            val mimeType = arrayOfNulls<String>(1)
            mimeType[0] = ClipDescription.MIMETYPE_TEXT_PLAIN

            //クリップボードに格納するClipDataオブジェクトの作成
            val cd = ClipData(ClipDescription("media URL", mimeType), item)

            //クリップボードにデータを格納
            cm.setPrimaryClip(cd)

            showToast(false, R.string.url_is_copied)

        } catch (ex: Throwable) {
            showToast(ex, "clipboard access failed.")
        }

    }

    internal fun more(ta: TootAttachmentLike) {
        val ad = ActionsDialog()

        if (ta is TootAttachment) {
            val url = ta.getLargeUrl(App1.pref) ?: return

            ad.addAction(getString(R.string.open_in_browser)) { share(Intent.ACTION_VIEW, url) }
            ad.addAction(getString(R.string.share_url)) { share(Intent.ACTION_SEND, url) }
            ad.addAction(getString(R.string.copy_url)) { copy(url) }

            addMoreMenu(ad, "url", ta.url, Intent.ACTION_VIEW)
            addMoreMenu(ad, "remote_url", ta.remote_url, Intent.ACTION_VIEW)
            addMoreMenu(ad, "preview_url", ta.preview_url, Intent.ACTION_VIEW)
            addMoreMenu(ad, "preview_remote_url", ta.preview_remote_url, Intent.ACTION_VIEW)
            addMoreMenu(ad, "text_url", ta.text_url, Intent.ACTION_VIEW)

        } else if (ta is TootAttachmentMSP) {
            val url = ta.preview_url
            ad.addAction(getString(R.string.open_in_browser)) { share(Intent.ACTION_VIEW, url) }
            ad.addAction(getString(R.string.share_url)) { share(Intent.ACTION_SEND, url) }
            ad.addAction(getString(R.string.copy_url)) { copy(url) }
        }

        ad.show(this, null)
    }

    private fun addMoreMenu(
			ad: ActionsDialog,
			caption_prefix: String,
			url: String?,
			@Suppress("SameParameterValue") action: String
	) {
        val uri = url.mayUri() ?: return

        val caption = getString(R.string.open_browser_of, caption_prefix)

        ad.addAction(caption) {
            try {
                val intent = Intent(action, uri)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (ex: Throwable) {
                showToast(ex, "can't open app.")
            }
        }
    }

    private fun preparePermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            ActivityCompat.requestPermissions(
					this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE
			)
        } else {
            showToast(true, R.string.missing_permission_to_access_media)
        }
    }

    override fun onRequestPermissionsResult(
			requestCode: Int, permissions: Array<String>, grantResults: IntArray
	) {
        when (requestCode) {
			PERMISSION_REQUEST_CODE -> {
				var bNotGranted = false
				var i = 0
				val ie = permissions.size
				while (i < ie) {
					if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
						bNotGranted = true
					}
					++i
				}
				if (bNotGranted) {
					showToast(true, R.string.missing_permission_to_access_media)
				} else {
					download(media_list[idx])
				}
			}
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
