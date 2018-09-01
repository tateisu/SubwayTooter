package jp.juggler.subwaytooter

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.Window
import android.widget.TextView
import com.google.android.exoplayer2.*

import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.MediaSourceEventListener
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util

import java.util.LinkedList

import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootTask
import jp.juggler.subwaytooter.api.TootTaskRunner
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.util.*
import jp.juggler.subwaytooter.view.PinchBitmapView
import okhttp3.Request
import java.io.IOException

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
		
		internal fun <T : TootAttachmentLike> encodeMediaList(list : ArrayList<T>?) =
			list?.encodeJson()?.toString() ?: "[]"
		
		internal fun decodeMediaList(serviceType : ServiceType, src : String?) =
			parseList(::TootAttachment, serviceType, src?.toJsonArray())
		
		fun open(
			activity : ActMain,
			serviceType : ServiceType,
			list : ArrayList<TootAttachmentLike>,
			idx : Int
		) {
			val intent = Intent(activity, ActMediaViewer::class.java)
			intent.putExtra(EXTRA_IDX, idx)
			intent.putExtra(EXTRA_SERVICE_TYPE, serviceType.ordinal)
			intent.putExtra(EXTRA_DATA, encodeMediaList(list))
			activity.startActivity(intent)
		}
	}
	
	internal var idx : Int = 0
	private lateinit var media_list : ArrayList<TootAttachment>
	private lateinit var serviceType : ServiceType
	
	private lateinit var pbvImage : PinchBitmapView
	private lateinit var btnPrevious : View
	private lateinit var btnNext : View
	private lateinit var tvError : TextView
	private lateinit var exoPlayer : SimpleExoPlayer
	private lateinit var exoView : PlayerView
	private lateinit var svDescription : View
	private lateinit var tvDescription : TextView
	private lateinit var tvStatus : TextView
	
	internal var buffering_last_shown : Long = 0
	
	private val player_listener = object : Player.EventListener {
		
		override fun onTimelineChanged(
			timeline : Timeline?,
			manifest : Any?,
			reason : Int
		) {
			log.d("exoPlayer onTimelineChanged manifest=$manifest reason=$reason")
		}
		
		override fun onSeekProcessed() {
		}
		
		override fun onShuffleModeEnabledChanged(shuffleModeEnabled : Boolean) {
		}
		
		override fun onTracksChanged(
			trackGroups : TrackGroupArray?,
			trackSelections : TrackSelectionArray?
		) {
			log.d("exoPlayer onTracksChanged")
			
		}
		
		override fun onLoadingChanged(isLoading : Boolean) {
			// かなり頻繁に呼ばれる
			// warning.d( "exoPlayer onLoadingChanged %s" ,isLoading );
		}
		
		override fun onPlayerStateChanged(playWhenReady : Boolean, playbackState : Int) {
			// かなり頻繁に呼ばれる
			// warning.d( "exoPlayer onPlayerStateChanged %s %s", playWhenReady, playbackState );
			if(playWhenReady && playbackState == Player.STATE_BUFFERING) {
				val now = SystemClock.elapsedRealtime()
				if(now - buffering_last_shown >= short_limit && exoPlayer.duration >= short_limit) {
					buffering_last_shown = now
					showToast(this@ActMediaViewer, false, R.string.video_buffering)
				}
				/*
					exoPlayer.getDuration() may returns negative value (TIME_UNSET ,same as Long.MIN_VALUE + 1).
				*/
			}
		}
		
		override fun onRepeatModeChanged(repeatMode : Int) {
			log.d("exoPlayer onRepeatModeChanged %d", repeatMode)
		}
		
		override fun onPlayerError(error : ExoPlaybackException) {
			log.d("exoPlayer onPlayerError")
			showToast(this@ActMediaViewer, error, "player error.")
		}
		
		override fun onPositionDiscontinuity(reason : Int) {
			log.d("exoPlayer onPositionDiscontinuity reason=$reason")
		}
		
		override fun onPlaybackParametersChanged(playbackParameters : PlaybackParameters?) {
			log.d("exoPlayer onPlaybackParametersChanged")
			
		}
	}
	
	override fun onSaveInstanceState(outState : Bundle?) {
		super.onSaveInstanceState(outState)
		
		outState ?: return
		
		outState.putInt(EXTRA_IDX, idx)
		outState.putInt(EXTRA_SERVICE_TYPE, serviceType.ordinal)
		outState.putString(EXTRA_DATA, encodeMediaList(media_list))
	}
	
	override fun onCreate(savedInstanceState : Bundle?) {
		super.onCreate(savedInstanceState)
		App1.setActivityTheme(this, true, true)
		requestWindowFeature(Window.FEATURE_NO_TITLE)
		
		val intent = intent
		
		this.idx = savedInstanceState?.getInt(EXTRA_IDX) ?: intent.getIntExtra(EXTRA_IDX, idx)
		
		this.serviceType = ServiceType.values()[
			savedInstanceState?.getInt(EXTRA_SERVICE_TYPE)
				?: intent.getIntExtra(EXTRA_SERVICE_TYPE, 0)
		]
		
		this.media_list = decodeMediaList(
			serviceType,
			savedInstanceState?.getString(EXTRA_DATA)
				?: intent.getStringExtra(EXTRA_DATA)
		)
		
		if(idx < 0 || idx >= media_list.size) idx = 0
		
		initUI()
		
		load()
	}
	
	override fun onDestroy() {
		super.onDestroy()
		pbvImage.setBitmap(null)
		exoPlayer.release()
		exoPlayer
	}
	
	internal fun initUI() {
		setContentView(R.layout.act_media_viewer)
		pbvImage = findViewById(R.id.pbvImage)
		btnPrevious = findViewById(R.id.btnPrevious)
		btnNext = findViewById(R.id.btnNext)
		exoView = findViewById(R.id.exoView)
		tvError = findViewById(R.id.tvError)
		svDescription = findViewById(R.id.svDescription)
		tvDescription = findViewById(R.id.tvDescription)
		tvStatus = findViewById(R.id.tvStatus)
		
		val enablePaging = media_list.size > 1
		btnPrevious.isEnabled = enablePaging
		btnNext.isEnabled = enablePaging
		btnPrevious.alpha = if(enablePaging) 1f else 0.3f
		btnNext.alpha = if(enablePaging) 1f else 0.3f
		
		btnPrevious.setOnClickListener(this)
		btnNext.setOnClickListener(this)
		findViewById<View>(R.id.btnDownload).setOnClickListener(this)
		findViewById<View>(R.id.btnMore).setOnClickListener(this)
		
		pbvImage.setCallback(object : PinchBitmapView.Callback {
			override fun onSwipe(delta : Int) {
				if(isDestroyed) return
				loadDelta(delta)
			}
			
			override fun onMove(
				bitmap_w : Float,
				bitmap_h : Float,
				tx : Float,
				ty : Float,
				scale : Float
			) {
				App1.getAppState(this@ActMediaViewer).handler.post(Runnable {
					if(isDestroyed) return@Runnable
					if(tvStatus.visibility == View.VISIBLE) {
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
		
		exoPlayer = ExoPlayerFactory.newSimpleInstance(this, DefaultTrackSelector())
		exoPlayer.addListener(player_listener)
		
		exoView.player = exoPlayer
	}
	
	internal fun loadDelta(delta : Int) {
		if(media_list.size < 2) return
		val size = media_list.size
		idx = (idx + size + delta) % size
		load()
	}
	
	internal fun load() {
		
		exoPlayer.stop()
		pbvImage.visibility = View.GONE
		exoView.visibility = View.GONE
		tvError.visibility = View.GONE
		svDescription.visibility = View.GONE
		tvStatus.visibility = View.GONE
		
		if(idx < 0 || idx >= media_list.size) {
			showError(getString(R.string.media_attachment_empty))
			return
		}
		val ta = media_list[idx]
		val description = ta.description
		if(description?.isNotEmpty() == true) {
			svDescription.visibility = View.VISIBLE
			tvDescription.text = description
		}
		
		if(TootAttachmentLike.TYPE_IMAGE == ta.type) {
			loadBitmap(ta)
		} else if(TootAttachmentLike.TYPE_VIDEO == ta.type || TootAttachmentLike.TYPE_GIFV == ta.type) {
			loadVideo(ta)
		} else {
			// maybe TYPE_UNKNOWN
			showError(getString(R.string.media_attachment_type_error, ta.type))
		}
	}
	
	private fun showError(message : String) {
		exoView.visibility = View.GONE
		pbvImage.visibility = View.GONE
		tvError.visibility = View.VISIBLE
		tvError.text = message
		
	}
	
	@SuppressLint("StaticFieldLeak")
	private fun loadVideo(ta : TootAttachment) {
		
		val url = ta.getLargeUrl(App1.pref)
		if(url == null) {
			showError("missing media attachment url.")
			return
		}
		
		exoView.visibility = View.VISIBLE
		
		val defaultBandwidthMeter = DefaultBandwidthMeter()
		val extractorsFactory = DefaultExtractorsFactory()
		
		val dataSourceFactory = DefaultDataSourceFactory(
			this, Util.getUserAgent(this, getString(R.string.app_name)), defaultBandwidthMeter
		)
		
		val mediaSource = ExtractorMediaSource.Factory(dataSourceFactory)
			.setExtractorsFactory(extractorsFactory)
			.createMediaSource(
				Uri.parse(url),
				App1.getAppState(this).handler,
				mediaSourceEventListener
			)
		
		exoPlayer.prepare(mediaSource)
		exoPlayer.playWhenReady = true
		if(TootAttachmentLike.TYPE_GIFV == ta.type) {
			exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
		} else {
			exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
			
		}
	}
	
	val mediaSourceEventListener = object : MediaSourceEventListener {
		override fun onLoadStarted(
			dataSpec : DataSpec?,
			dataType : Int,
			trackType : Int,
			trackFormat : Format?,
			trackSelectionReason : Int,
			trackSelectionData : Any?,
			mediaStartTimeMs : Long,
			mediaEndTimeMs : Long,
			elapsedRealtimeMs : Long
		) {
			log.d("onLoadStarted")
		}
		
		override fun onDownstreamFormatChanged(
			trackType : Int,
			trackFormat : Format?,
			trackSelectionReason : Int,
			trackSelectionData : Any?,
			mediaTimeMs : Long
		) {
			log.d("onDownstreamFormatChanged")
		}
		
		override fun onUpstreamDiscarded(
			trackType : Int,
			mediaStartTimeMs : Long,
			mediaEndTimeMs : Long
		) {
			log.d("onUpstreamDiscarded")
		}
		
		override fun onLoadCompleted(
			dataSpec : DataSpec?,
			dataType : Int,
			trackType : Int,
			trackFormat : Format?,
			trackSelectionReason : Int,
			trackSelectionData : Any?,
			mediaStartTimeMs : Long,
			mediaEndTimeMs : Long,
			elapsedRealtimeMs : Long,
			loadDurationMs : Long,
			bytesLoaded : Long
		) {
			log.d("onLoadCompleted")
		}
		
		override fun onLoadCanceled(
			dataSpec : DataSpec?,
			dataType : Int,
			trackType : Int,
			trackFormat : Format?,
			trackSelectionReason : Int,
			trackSelectionData : Any?,
			mediaStartTimeMs : Long,
			mediaEndTimeMs : Long,
			elapsedRealtimeMs : Long,
			loadDurationMs : Long,
			bytesLoaded : Long
		) {
			log.d("onLoadCanceled")
		}
		
		override fun onLoadError(
			dataSpec : DataSpec?,
			dataType : Int,
			trackType : Int,
			trackFormat : Format?,
			trackSelectionReason : Int,
			trackSelectionData : Any?,
			mediaStartTimeMs : Long,
			mediaEndTimeMs : Long,
			elapsedRealtimeMs : Long,
			loadDurationMs : Long,
			bytesLoaded : Long,
			error : IOException?,
			wasCanceled : Boolean
		) {
			if(error != null) {
				showError(error.withCaption("load error."))
			}
		}
		
	}
	
	@SuppressLint("StaticFieldLeak")
	private fun loadBitmap(ta : TootAttachment) {
		val urlList = ta.getLargeUrlList(App1.pref)
		if(urlList.isEmpty()) {
			showError("missing media attachment url.")
			return
		}
		
		tvStatus.visibility = View.VISIBLE
		tvStatus.text = null
		
		pbvImage.visibility = View.VISIBLE
		pbvImage.setBitmap(null)
		
		TootTaskRunner(this, TootTaskRunner.PROGRESS_HORIZONTAL).run(object : TootTask {
			
			private val options = BitmapFactory.Options()
			
			var bitmap : Bitmap? = null
			
			private fun decodeBitmap(data : ByteArray, pixel_max : Int) : Bitmap? {
				
				options.inJustDecodeBounds = true
				options.inScaled = false
				options.outWidth = 0
				options.outHeight = 0
				BitmapFactory.decodeByteArray(data, 0, data.size, options)
				var w = options.outWidth
				var h = options.outHeight
				if(w <= 0 || h <= 0) {
					log.e("can't decode bounds.")
					return null
				}
				var bits = 0
				while(w > pixel_max || h > pixel_max) {
					++ bits
					w = w shr 1
					h = h shr 1
				}
				options.inJustDecodeBounds = false
				options.inSampleSize = 1 shl bits
				return BitmapFactory.decodeByteArray(data, 0, data.size, options)
			}
			
			fun getHttpCached(client : TootApiClient, url : String) : TootApiResult? {
				val result = TootApiResult.makeWithCaption(url)
				
				val request = Request.Builder()
					.url(url)
					.cacheControl(App1.CACHE_5MIN)
					.addHeader("Accept", "image/webp,image/*,*/*;q=0.8")
					.build()
				
				if(! client.sendRequest(result, tmpOkhttpClient = App1.ok_http_client_media_viewer ) {
						request
					}) return result
				
				if(client.isApiCancelled) return null
				val response = requireNotNull(result.response)
				if(! response.isSuccessful) {
					return result.setError(TootApiClient.formatResponse(response, result.caption))
				}
				//				log.d("cached=${ response.cacheResponse() != null }")
				
				try {
					result.data = ProgressResponseBody.bytes(response) { bytesRead, bytesTotal ->
						// 50MB以上のデータはキャンセルする
						if(Math.max(bytesRead, bytesTotal) >= 50000000) {
							throw RuntimeException("media attachment is larger than 50000000")
						}
						client.publishApiProgressRatio(bytesRead.toInt(), bytesTotal.toInt())
					}
					if(client.isApiCancelled) return null
				} catch(ex : Throwable) {
					result.setError(TootApiClient.formatResponse(response, result.caption, "?"))
				}
				return result
			}
			
			override fun background(client : TootApiClient) : TootApiResult? {
				if(urlList.isEmpty()) return TootApiResult("missing url")
				var result : TootApiResult? = null
				for(url in urlList) {
					result = getHttpCached(client, url)
					val data = result?.data as? ByteArray
					if(data != null) {
						client.publishApiProgress("decoding image…")
						val bitmap = decodeBitmap(data, 2048)
						if(bitmap != null) {
							this.bitmap = bitmap
							break
						}
					}
				}
				return result
			}
			
			override fun handleResult(result : TootApiResult?) {
				val bitmap = this.bitmap
				if(bitmap != null) {
					pbvImage.setBitmap(bitmap)
				} else if(result != null) {
					showToast(this@ActMediaViewer, true, result.error)
				}
			}
		})
		
	}
	
	override fun onClick(v : View) {
		try {
			when(v.id) {
				
				R.id.btnPrevious -> loadDelta(- 1)
				R.id.btnNext -> loadDelta(+ 1)
				R.id.btnDownload -> download(media_list[idx])
				
				//			case R.id.btnBrowser:
				//				share( Intent.ACTION_VIEW, media_list.get( idx ) );
				//				break;
				//			case R.id.btnShare:
				//				share( Intent.ACTION_SEND, media_list.get( idx ) );
				//				break;
				//			case R.id.btnCopy:
				//				copy( media_list.get( idx ) );
				//				break;
				
				R.id.btnMore -> more(media_list[idx])
			}
		} catch(ex : Throwable) {
			showToast(this, ex, "action failed.")
		}
		
	}
	
	internal class DownloadHistory(val time : Long, val url : String)
	
	private fun download(ta : TootAttachmentLike) {
		
		val permissionCheck = ContextCompat.checkSelfPermission(
			this,
			android.Manifest.permission.WRITE_EXTERNAL_STORAGE
		)
		if(permissionCheck != PackageManager.PERMISSION_GRANTED) {
			preparePermission()
			return
		}
		
		val downLoadManager = getSystemService(DOWNLOAD_SERVICE) as? DownloadManager
			?: throw NotImplementedError("missing DownloadManager system service")
		
		val url = if(ta is TootAttachment) {
			ta.getLargeUrl(App1.pref)
		} else {
			null
		} ?: return
		
		// ボタン連打対策
		run {
			val now = SystemClock.elapsedRealtime()
			
			// 期限切れの履歴を削除
			val it = download_history_list.iterator()
			while(it.hasNext()) {
				val dh = it.next()
				if(now - dh.time >= DOWNLOAD_REPEAT_EXPIRE) {
					// この履歴は十分に古いので捨てる
					it.remove()
				} else if(url == dh.url) {
					// 履歴に同じURLがあればエラーとする
					showToast(this, false, R.string.dont_repeat_download_to_same_url)
					return
				}
			}
			// 履歴の末尾に追加(履歴は古い順に並ぶ)
			download_history_list.addLast(DownloadHistory(now, url))
		}
		
		var fileName : String? = null
		
		try {
			val pathSegments = Uri.parse(url).pathSegments
			if(pathSegments != null) {
				val size = pathSegments.size
				for(i in size - 1 downTo 0) {
					val s = pathSegments[i]
					if(s?.isNotEmpty() == true) {
						fileName = s
						break
					}
				}
			}
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
		if(fileName == null) {
			fileName = url
				.replaceFirst("https?://".toRegex(), "")
				.replace("[^.\\w\\d]+".toRegex(), "-")
		}
		if(fileName.length >= 20) fileName = fileName.substring(fileName.length - 20)
		
		val request = DownloadManager.Request(Uri.parse(url))
		request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
		request.setTitle(fileName)
		request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE or DownloadManager.Request.NETWORK_WIFI)
		//メディアスキャンを許可する
		request.allowScanningByMediaScanner()
		
		//ダウンロード中・ダウンロード完了時にも通知を表示する
		request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
		
		downLoadManager.enqueue(request)
		showToast(this, false, R.string.downloading)
	}
	
	private fun share(action : String, url : String) {
		
		try {
			val intent = Intent(action)
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			if(action == Intent.ACTION_SEND) {
				intent.type = "text/plain"
				intent.putExtra(Intent.EXTRA_TEXT, url)
			} else {
				intent.data = Uri.parse(url)
			}
			
			startActivity(intent)
		} catch(ex : Throwable) {
			showToast(this, ex, "can't open app.")
		}
		
	}
	
	internal fun copy(url : String) {
		
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
			cm.primaryClip = cd
			
			showToast(this, false, R.string.url_is_copied)
			
		} catch(ex : Throwable) {
			showToast(this, ex, "clipboard access failed.")
		}
		
	}
	
	internal fun more(ta : TootAttachmentLike) {
		val ad = ActionsDialog()
		
		if(ta is TootAttachment) {
			val url = ta.getLargeUrl(App1.pref) ?: return
			
			ad.addAction(getString(R.string.open_in_browser)) { share(Intent.ACTION_VIEW, url) }
			ad.addAction(getString(R.string.share_url)) { share(Intent.ACTION_SEND, url) }
			ad.addAction(getString(R.string.copy_url)) { copy(url) }
			
			addMoreMenu(ad, "url", ta.url, Intent.ACTION_VIEW)
			addMoreMenu(ad, "remote_url", ta.remote_url, Intent.ACTION_VIEW)
			addMoreMenu(ad, "preview_url", ta.preview_url, Intent.ACTION_VIEW)
			addMoreMenu(ad, "text_url", ta.text_url, Intent.ACTION_VIEW)
			
		} else if(ta is TootAttachmentMSP) {
			val url = ta.preview_url
			ad.addAction(getString(R.string.open_in_browser)) { share(Intent.ACTION_VIEW, url) }
			ad.addAction(getString(R.string.share_url)) { share(Intent.ACTION_SEND, url) }
			ad.addAction(getString(R.string.copy_url)) { copy(url) }
		}
		
		ad.show(this, null)
	}
	
	private fun addMoreMenu(
		ad : ActionsDialog,
		caption_prefix : String,
		url : String?,
		action : String
	) {
		if(url?.isEmpty() != false) return
		
		val caption = getString(R.string.open_browser_of, caption_prefix)
		
		ad.addAction(caption) {
			try {
				val intent = Intent(action, Uri.parse(url))
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				startActivity(intent)
			} catch(ex : Throwable) {
				showToast(this@ActMediaViewer, ex, "can't open app.")
			}
		}
	}
	
	private fun preparePermission() {
		if(Build.VERSION.SDK_INT >= 23) {
			ActivityCompat.requestPermissions(
				this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE
			)
		} else {
			showToast(this, true, R.string.missing_permission_to_access_media)
		}
	}
	
	override fun onRequestPermissionsResult(
		requestCode : Int, permissions : Array<String>, grantResults : IntArray
	) {
		when(requestCode) {
			PERMISSION_REQUEST_CODE -> {
				var bNotGranted = false
				var i = 0
				val ie = permissions.size
				while(i < ie) {
					if(grantResults[i] != PackageManager.PERMISSION_GRANTED) {
						bNotGranted = true
					}
					++ i
				}
				if(bNotGranted) {
					showToast(this, true, R.string.missing_permission_to_access_media)
				} else {
					download(media_list[idx])
				}
			}
		}
	}
	
}