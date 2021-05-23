package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.content.*
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.text.Spannable
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.streaming.StreamManager
import jp.juggler.subwaytooter.table.HighlightWord
import jp.juggler.subwaytooter.table.MutedApp
import jp.juggler.subwaytooter.table.MutedWord
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.NetworkStateTracker
import jp.juggler.subwaytooter.util.PostAttachment
import jp.juggler.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.io.IOUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.lang.ref.WeakReference
import java.util.*
import kotlin.math.max

enum class DedupMode {
    None,
    RecentExpire,
    Recent,
}

class DedupItem(
	val text: String,
	var time: Long = SystemClock.elapsedRealtime()
)

class AppState(
	internal val context: Context,
	internal val handler: Handler,
	internal val pref: SharedPreferences
) {

    companion object {

        internal val log = LogCategory("AppState")

        private const val FILE_COLUMN_LIST = "column_list"

        private const val TTS_STATUS_NONE = 0
        private const val TTS_STATUS_INITIALIZING = 1
        private const val TTS_STATUS_INITIALIZED = 2

        private const val tts_speak_wait_expire = 1000L * 100
        private val random = Random()

        private val reSpaces = "[\\s　]+".asciiPattern()

        private var utteranceIdSeed = 0

        internal fun saveColumnList(context: Context, fileName: String, array: JsonArray) {
            synchronized(log) {
                try {
                    val tmpName =
                        "tmpColumnList.${System.currentTimeMillis()}.${Thread.currentThread().id}"
                    val tmpFile = context.getFileStreamPath(tmpName)
                    try {
                        // write to tmp file
                        context.openFileOutput(tmpName, Context.MODE_PRIVATE).use { os ->
                            os.write(array.toString().encodeUTF8())
                        }
                        // rename
                        val outFile = context.getFileStreamPath(fileName)
                        if (!tmpFile.renameTo(outFile)) {
                            error("saveColumnList: rename failed!")
                        } else {
                            log.d("saveColumnList: rename ok: $outFile")
                        }
                    } finally {
                        tmpFile.delete() // ignore return value
                    }
                } catch (ex: Throwable) {
                    log.trace(ex)
                    context.showToast(ex, "saveColumnList failed.")
                }
            }
        }

        internal fun loadColumnList(context: Context, fileName: String): JsonArray? {
            synchronized(log) {
                try {
                    context.openFileInput(fileName).use { inData ->
                        val bao = ByteArrayOutputStream(inData.available())
                        IOUtils.copy(inData, bao)
                        return bao.toByteArray().decodeUTF8().decodeJsonArray()
                    }
                } catch (ignored: FileNotFoundException) {
                } catch (ex: Throwable) {
                    log.trace(ex)
                    context.showToast(ex, "loadColumnList failed.")
                }

                return null
            }
        }

        private fun getStatusText(status: TootStatus?): Spannable? {
            return when {
                status == null -> null
                status.decoded_spoiler_text.isNotEmpty() -> status.decoded_spoiler_text
                status.decoded_content.isNotEmpty() -> status.decoded_content
                else -> null
            }
        }

    }

    internal val density: Float

    internal val streamManager: StreamManager

    internal var media_thumb_height: Int = 0

    private val _columnList = ArrayList<Column>()

	// make shallow copy
	val columnList: List<Column>
		get() = synchronized(_columnList) { ArrayList(_columnList) }

    val columnCount: Int
        get() = synchronized(_columnList) { _columnList.size }

    fun column(i: Int) =
        synchronized(_columnList) { _columnList.elementAtOrNull(i) }

    fun columnIndex(column: Column?) =
        synchronized(_columnList) { _columnList.indexOf(column).takeIf{ it != -1 } }

    fun editColumnList(save: Boolean = true, block: (ArrayList<Column>) -> Unit) {
        synchronized(_columnList) {
            block(_columnList)
            if (save) saveColumnList()
        }
    }


    private val map_busy_fav = HashSet<String>()
    private val map_busy_bookmark = HashSet<String>()
    private val map_busy_boost = HashSet<String>()
    internal var attachment_list: ArrayList<PostAttachment>? = null

    private var willSpeechEnabled: Boolean = false
    private var tts: TextToSpeech? = null
    private var tts_status = TTS_STATUS_NONE
    private var tts_speak_start = 0L
    private var tts_speak_end = 0L

    private val voice_list = ArrayList<Voice>()

    private val tts_queue = LinkedList<String>()

    private val duplication_check = LinkedList<DedupItem>()

    private var last_ringtone: WeakReference<Ringtone>? = null

    private var last_sound: Long = 0

    val networkTracker: NetworkStateTracker

    // initからプロパティにアクセスする場合、そのプロパティはinitより上で定義されていないとダメっぽい
    // そしてその他のメソッドからval プロパティにアクセスする場合、そのプロパティはメソッドより上で初期化されていないとダメっぽい

    init {

        this.density = context.resources.displayMetrics.density
        this.streamManager = StreamManager(this)
        this.networkTracker = NetworkStateTracker(context) {
            App1.custom_emoji_cache.onNetworkChanged()
            App1.custom_emoji_lister.onNetworkChanged()
        }
    }

    //////////////////////////////////////////////////////
    // TextToSpeech

    private val isTextToSpeechRequired: Boolean
        get() = columnList.any { it.enable_speech } || HighlightWord.hasTextToSpeechHighlightWord()

    private val tts_receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent != null) {
                if (TextToSpeech.ACTION_TTS_QUEUE_PROCESSING_COMPLETED == intent.action) {
                    log.d("tts_receiver: speech completed.")
                    tts_speak_end = SystemClock.elapsedRealtime()
                    handler.post(proc_flushSpeechQueue)
                }
            }
        }
    }

    private val proc_flushSpeechQueue = object : Runnable {
        override fun run() {
            try {
                handler.removeCallbacks(this)

                val queue_count = tts_queue.size
                if (queue_count <= 0) {
                    return
                }

                val tts = this@AppState.tts
                if (tts == null) {
                    log.d("proc_flushSpeechQueue: tts is null")
                    return
                }

                val now = SystemClock.elapsedRealtime()

                if (tts_speak_start >= max(1L, tts_speak_end)) {
                    // まだ終了イベントを受け取っていない
                    val expire_remain = tts_speak_wait_expire + tts_speak_start - now
                    if (expire_remain <= 0) {
                        log.d("proc_flushSpeechQueue: tts_speak wait expired.")
                        restartTTS()
                    } else {
                        log.d(
							"proc_flushSpeechQueue: tts is speaking. queue_count=%d, expire_remain=%.3f",
							queue_count,
							expire_remain / 1000f
						)
                        handler.postDelayed(this, expire_remain)
                        return
                    }
                    return
                }

                val sv = tts_queue.removeFirst()
                log.d("proc_flushSpeechQueue: speak %s", sv)

                val voice_count = voice_list.size
                if (voice_count > 0) {
                    val n = random.nextInt(voice_count)
                    tts.voice = voice_list[n]
                }

                tts_speak_start = now
                tts.speak(
					sv,
					TextToSpeech.QUEUE_ADD,
					null, // Bundle params
					(++utteranceIdSeed).toString() // String utteranceId
				)
            } catch (ex: Throwable) {
                log.trace(ex)
                log.e(ex, "proc_flushSpeechQueue catch exception.")
                restartTTS()
            }

        }

        fun restartTTS() {
            log.d("restart TextToSpeech")
            tts?.shutdown()
            tts = null
            tts_status = TTS_STATUS_NONE
            enableSpeech()
        }
    }

    internal fun encodeColumnList() =
        columnList.mapIndexedNotNull { index, column ->
            try {
                val dst = JsonObject()
                ColumnEncoder.encode(column,dst, index)
                dst
            } catch (ex: JsonException) {
                log.trace(ex)
                null
            }
        }.toJsonArray()

    internal fun saveColumnList(bEnableSpeech: Boolean = true) {
        val array = encodeColumnList()
        saveColumnList(context, FILE_COLUMN_LIST, array)
        if (bEnableSpeech) enableSpeech()
    }

    fun loadColumnList() {
        val list = loadColumnList(context, FILE_COLUMN_LIST)
            ?.objectList()
            ?.mapNotNull { src ->
                try {
                    Column(this, src)
                } catch (ex: Throwable) {
                    log.trace(ex)
                    null
                }
            }
        if (list != null) editColumnList(save = false) { it.addAll(list) }

        // ミュートデータのロード
        TootStatus.muted_app = MutedApp.nameSet
        TootStatus.muted_word = MutedWord.nameSet

        // 背景フォルダの掃除
        try {
            val backgroundImageDir = getBackgroundImageDir(context)
            backgroundImageDir.list()?.forEach { name ->
                val file = File(backgroundImageDir, name)
                if (file.isFile) {
                    val delm = name.indexOf(':')
                    val id = if (delm != -1) name.substring(0, delm) else name
                    val column = ColumnEncoder.findColumnById(id)
                    if (column == null) file.delete()
                }
            }
        } catch (ex: Throwable) {
            // クラッシュレポートによると状態が悪いとダメらしい
            // java.lang.IllegalStateException
            log.trace(ex)
        }
    }

    fun isBusyFav(account: SavedAccount, status: TootStatus): Boolean {
        val key = account.acct.ascii + ":" + status.busyKey
        return map_busy_fav.contains(key)
    }

    fun setBusyFav(account: SavedAccount, status: TootStatus) {
        val key = account.acct.ascii + ":" + status.busyKey
        map_busy_fav.add(key)
    }

    fun resetBusyFav(account: SavedAccount, status: TootStatus) {
        val key = account.acct.ascii + ":" + status.busyKey
        map_busy_fav.remove(key)
    }

    fun isBusyBookmark(account: SavedAccount, status: TootStatus): Boolean {
        val key = account.acct.ascii + ":" + status.busyKey
        return map_busy_bookmark.contains(key)
    }

    fun setBusyBookmark(account: SavedAccount, status: TootStatus) {
        val key = account.acct.ascii + ":" + status.busyKey
        map_busy_bookmark.add(key)
    }

    fun resetBusyBookmark(account: SavedAccount, status: TootStatus) {
        val key = account.acct.ascii + ":" + status.busyKey
        map_busy_bookmark.remove(key)
    }

    fun isBusyBoost(account: SavedAccount, status: TootStatus): Boolean {
        val key = account.acct.ascii + ":" + status.busyKey
        return map_busy_boost.contains(key)
    }

    fun setBusyBoost(account: SavedAccount, status: TootStatus) {
        val key = account.acct.ascii + ":" + status.busyKey
        map_busy_boost.add(key)
    }

    fun resetBusyBoost(account: SavedAccount, status: TootStatus) {
        val key = account.acct.ascii + ":" + status.busyKey
        map_busy_boost.remove(key)
    }

    @SuppressLint("StaticFieldLeak")
    fun enableSpeech() {
        this.willSpeechEnabled = isTextToSpeechRequired

        if (willSpeechEnabled && tts == null && tts_status == TTS_STATUS_NONE) {
            tts_status = TTS_STATUS_INITIALIZING
            context.showToast(false, R.string.text_to_speech_initializing)
            log.d("initializing TextToSpeech…")

            EndlessScope.launch(Dispatchers.IO) {

                var tmp_tts: TextToSpeech? = null

                val tts_init_listener: TextToSpeech.OnInitListener =
                    TextToSpeech.OnInitListener { status ->

                        val tts = tmp_tts
                        if (tts == null || TextToSpeech.SUCCESS != status) {
                            context.showToast(
								false,
								R.string.text_to_speech_initialize_failed,
								status
							)
                            log.d("speech initialize failed. status=%s", status)
                            return@OnInitListener
                        }

                        runOnMainLooper {
                            if (!willSpeechEnabled) {
                                context.showToast(false, R.string.text_to_speech_shutdown)
                                log.d("shutdown TextToSpeech…")
                                tts.shutdown()
                            } else {
                                this@AppState.tts = tts
                                tts_status = TTS_STATUS_INITIALIZED
                                tts_speak_start = 0L
                                tts_speak_end = 0L

                                voice_list.clear()
                                try {
                                    val voice_set = try {
                                        tts.voices
                                        // may raise NullPointerException is tts has no collection
                                    } catch (ignored: Throwable) {
                                        null
                                    }
                                    if (voice_set == null || voice_set.isEmpty()) {
                                        log.d("TextToSpeech.getVoices returns null or empty set.")
                                    } else {
                                        val lang = defaultLocale(context).toLanguageTag()
                                        for (v in voice_set) {
                                            log.d(
												"Voice %s %s %s",
												v.name,
												v.locale.toLanguageTag(),
												lang
											)
                                            if (lang != v.locale.toLanguageTag()) {
                                                continue
                                            }
                                            voice_list.add(v)
                                        }
                                    }
                                } catch (ex: Throwable) {
                                    log.trace(ex)
                                    log.e(ex, "TextToSpeech.getVoices raises exception.")
                                }

                                handler.post(proc_flushSpeechQueue)

                                context.registerReceiver(
									tts_receiver,
									IntentFilter(TextToSpeech.ACTION_TTS_QUEUE_PROCESSING_COMPLETED)
								)

                                //									tts.setOnUtteranceProgressListener( new UtteranceProgressListener() {
                                //										@Override public void onStart( String utteranceId ){
                                //											warning.d( "UtteranceProgressListener.onStart id=%s", utteranceId );
                                //										}
                                //
                                //										@Override public void onDone( String utteranceId ){
                                //											warning.d( "UtteranceProgressListener.onDone id=%s", utteranceId );
                                //											handler.post( proc_flushSpeechQueue );
                                //										}
                                //
                                //										@Override public void onError( String utteranceId ){
                                //											warning.d( "UtteranceProgressListener.onError id=%s", utteranceId );
                                //											handler.post( proc_flushSpeechQueue );
                                //										}
                                //									} );
                            }
                        }
                    }

                tmp_tts = TextToSpeech(context, tts_init_listener)
            }
            return
        }

        if (!willSpeechEnabled && tts != null) {
            context.showToast(false, R.string.text_to_speech_shutdown)
            log.d("shutdown TextToSpeech…")
            tts?.shutdown()
            tts = null
            tts_status = TTS_STATUS_NONE
        }
    }

    internal fun addSpeech(status: TootStatus) {

        if (tts == null) return

        val text = getStatusText(status)
        if (text == null || text.length == 0) return

        val span_list = text.getSpans(0, text.length, MyClickableSpan::class.java)
        if (span_list == null || span_list.isEmpty()) {
            addSpeech(text.toString())
            return
        }
        Arrays.sort(span_list) { a, b ->
            val a_start = text.getSpanStart(a)
            val b_start = text.getSpanStart(b)
            a_start - b_start
        }
        val str_text = text.toString()
        val sb = StringBuilder()
        var last_end = 0
        var has_url = false
        for (span in span_list) {
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            //
            if (start > last_end) {
                sb.append(str_text.substring(last_end, start))
            }
            last_end = end
            //
            val span_text = str_text.substring(start, end)
            if (span_text.isNotEmpty()) {
                val c = span_text[0]
                if (c == '#' || c == '@') {
                    // #hashtag や @user はそのまま読み上げる
                    sb.append(span_text)
                } else {
                    // それ以外はURL省略
                    has_url = true
                    sb.append(" ")
                }
            }
        }
        val text_end = str_text.length
        if (text_end > last_end) {
            sb.append(str_text.substring(last_end, text_end))
        }
        if (has_url) {
            sb.append(context.getString(R.string.url_omitted))
        }
        addSpeech(sb.toString())
    }

    internal fun addSpeech(text: String, dedupMode: DedupMode = DedupMode.Recent) {

        if (tts == null) return

        val sv = reSpaces.matcher(text).replaceAll(" ").trim { it <= ' ' }
        if (sv.isEmpty()) return

        if (dedupMode != DedupMode.None) {
            synchronized(this) {
                val check = duplication_check.find { it.text.equals(sv, ignoreCase = true) }
                if (check == null) {
                    duplication_check.addLast(DedupItem(sv))
                    if (duplication_check.size > 60) duplication_check.removeFirst()
                } else {
                    val now = SystemClock.elapsedRealtime()
                    val delta = now - check.time
                    // 古い項目が残っていることがあるので、check.timeの更新は必須
                    check.time = now

                    if (dedupMode == DedupMode.Recent) return
                    if (dedupMode == DedupMode.RecentExpire && delta < 5000L) return
                }
            }
        }

        tts_queue.add(sv)
        if (tts_queue.size > 30) tts_queue.removeFirst()

        handler.post(proc_flushSpeechQueue)
    }

    private fun stopLastRingtone() {
        val r = last_ringtone?.get()
        if (r != null) {
            try {
                r.stop()
            } catch (ex: Throwable) {
                log.trace(ex)
            } finally {
                last_ringtone = null
            }
        }
    }

    internal fun sound(item: HighlightWord) {
        // 短時間に何度もならないようにする
        val now = SystemClock.elapsedRealtime()
        if (now - last_sound < 500L) return
        last_sound = now

        stopLastRingtone()

        if (item.sound_type == HighlightWord.SOUND_TYPE_NONE) return

        fun Uri?.tryRingtone(): Boolean {
            try {
                if (this != null) {
                    RingtoneManager.getRingtone(context, this)?.let { ringTone ->
                        last_ringtone = WeakReference(ringTone)
                        ringTone.play()
                        return true
                    }
                }
            } catch (ex: Throwable) {
                log.trace(ex)
            }
            return false
        }

        if (item.sound_type == HighlightWord.SOUND_TYPE_CUSTOM && item.sound_uri.mayUri()
                .tryRingtone()) return

        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).tryRingtone()
    }

    fun onMuteUpdated() {
        TootStatus.muted_app = MutedApp.nameSet
        TootStatus.muted_word = MutedWord.nameSet
        columnList.forEach { it.onMuteUpdated() }
    }


}
