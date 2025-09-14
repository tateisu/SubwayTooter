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
import androidx.core.content.ContextCompat
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.column.Column
import jp.juggler.subwaytooter.column.ColumnEncoder
import jp.juggler.subwaytooter.column.getBackgroundImageDir
import jp.juggler.subwaytooter.column.onMuteUpdated
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.streaming.StreamManager
import jp.juggler.subwaytooter.table.*
import jp.juggler.subwaytooter.util.NetworkStateTracker
import jp.juggler.subwaytooter.util.PostAttachment
import jp.juggler.util.coroutine.launchIO
import jp.juggler.util.coroutine.runOnMainLooper
import jp.juggler.util.data.*
import jp.juggler.util.idCompat
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
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
    var time: Long = SystemClock.elapsedRealtime(),
)

class AppState(
    internal val context: Context,
    internal val handler: Handler,
) {

    companion object {

        internal val log = LogCategory("AppState")

        private const val FILE_COLUMN_LIST = "column_list"

        private const val TTS_STATUS_NONE = 0
        private const val TTS_STATUS_INITIALIZING = 1
        private const val TTS_STATUS_INITIALIZED = 2

        private const val TTS_SPEAK_WAIT_EXPIRE = 1000L * 100
        private val random = Random()

        private val reSpaces = "[\\s　]+".asciiPattern()

        private var utteranceIdSeed = 0

        internal fun saveColumnList(context: Context, fileName: String, array: JsonArray) {
            synchronized(log) {
                try {
                    val tmpName =
                        "tmpColumnList.${System.currentTimeMillis()}.${Thread.currentThread().idCompat}"
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
                        Unit
                    } finally {
                        tmpFile.delete() // ignore return value
                    }
                } catch (ex: Throwable) {
                    log.e(ex, "saveColumnList failed.")
                    context.showToast(ex, "saveColumnList failed.")
                }
            }
        }

        internal fun loadColumnList(context: Context, fileName: String): JsonArray? {
            synchronized(log) {
                try {
                    return context.openFileInput(fileName).use { inData ->
                        inData.readBytes().decodeUTF8().decodeJsonArray()
                    }
                } catch (ignored: FileNotFoundException) {
                } catch (ex: Throwable) {
                    log.e(ex, "loadColumnList failed.")
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

    internal var mediaThumbHeight: Int = 0

    private val _columnList = ArrayList<Column>()

    // make shallow copy
    val columnList: List<Column>
        get() = synchronized(_columnList) { ArrayList(_columnList) }

    val columnCount: Int
        get() = synchronized(_columnList) { _columnList.size }

    fun column(i: Int) =
        synchronized(_columnList) { _columnList.elementAtOrNull(i) }

    fun columnIndex(column: Column?) =
        synchronized(_columnList) { _columnList.indexOf(column).takeIf { it != -1 } }

    fun editColumnList(save: Boolean = true, block: (ArrayList<Column>) -> Unit) {
        synchronized(_columnList) {
            block(_columnList)
            if (save) saveColumnList()
        }
    }

    private val mapBusyFav = HashSet<String>()
    private val mapBusyBookmark = HashSet<String>()
    private val mapBusyBoost = HashSet<String>()
    internal var attachmentList: ArrayList<PostAttachment>? = null

    private var willSpeechEnabled: Boolean = false
    private var tts: TextToSpeech? = null
    private var ttsStatus = TTS_STATUS_NONE
    private var ttsSpeakStart = 0L
    private var ttsSpeakEnd = 0L

    private val voiceList = ArrayList<Voice>()

    private val ttsQueue = LinkedList<String>()

    private val duplicationCheck = LinkedList<DedupItem>()

    private var lastRingtone: WeakReference<Ringtone>? = null

    private var lastSound: Long = 0

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
        get() = columnList.any { it.enableSpeech } || daoHighlightWord.hasTextToSpeechHighlightWord()

    private val ttsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            log.i("ttsReceiver onReceive action=${intent?.action}")
            when (intent?.action) {
                TextToSpeech.ACTION_TTS_QUEUE_PROCESSING_COMPLETED -> {
                    ttsSpeakEnd = SystemClock.elapsedRealtime()
                    handler.post(procFlushSpeechQueue)
                }
            }
        }
    }

    private val procFlushSpeechQueue = object : Runnable {
        override fun run() {
            try {
                handler.removeCallbacks(this)

                val queue_count = ttsQueue.size
                if (queue_count <= 0) {
                    return
                }

                val tts = this@AppState.tts
                if (tts == null) {
                    log.d("proc_flushSpeechQueue: tts is null")
                    return
                }

                val now = SystemClock.elapsedRealtime()

                if (ttsSpeakStart >= max(1L, ttsSpeakEnd)) {
                    // まだ終了イベントを受け取っていない
                    val expire_remain = TTS_SPEAK_WAIT_EXPIRE + ttsSpeakStart - now
                    if (expire_remain <= 0) {
                        log.d("proc_flushSpeechQueue: tts_speak wait expired.")
                        restartTTS()
                    } else {
                        log.d(
                            "proc_flushSpeechQueue: tts is speaking. queue_count=$queue_count, expire_remain=${
                                "%.3f".format(expire_remain.div(1000f))
                            }"
                        )
                        handler.postDelayed(this, expire_remain)
                        return
                    }
                    return
                }

                val sv = ttsQueue.removeFirst()
                log.d("proc_flushSpeechQueue: speak $sv")

                val voice_count = voiceList.size
                if (voice_count > 0) {
                    val n = random.nextInt(voice_count)
                    tts.voice = voiceList[n]
                }

                ttsSpeakStart = now
                tts.speak(
                    sv,
                    TextToSpeech.QUEUE_ADD,
                    null, // Bundle params
                    (++utteranceIdSeed).toString() // String utteranceId
                )
            } catch (ex: Throwable) {
                log.e(ex, "proc_flushSpeechQueue catch exception.")
                restartTTS()
            }
        }

        fun restartTTS() {
            log.d("restart TextToSpeech")
            tts?.shutdown()
            tts = null
            ttsStatus = TTS_STATUS_NONE
            enableSpeech()
        }
    }

    internal fun encodeColumnList() =
        columnList.mapIndexedNotNull { index, column ->
            try {
                val dst = JsonObject()
                ColumnEncoder.encode(column, dst, index)
                dst
            } catch (ex: JsonException) {
                log.e(ex, "encodeColumnList: encode failed at $index.")
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
            ?.mapIndexedNotNull { index, src ->
                try {
                    Column(this, src)
                } catch (ex: Throwable) {
                    log.e(ex, "loadColumnList: decode column failed at $index")
                    null
                }
            }
        if (list != null) editColumnList(save = false) { it.addAll(list) }

        // ミュートデータのロード
        TootStatus.updateMuteData(force = true)

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
            log.e(ex, "loadColumnList failed.")
        }
    }

    fun isBusyFav(account: SavedAccount, status: TootStatus): Boolean {
        val key = account.acct.ascii + ":" + status.busyKey
        return mapBusyFav.contains(key)
    }

    fun setBusyFav(account: SavedAccount, status: TootStatus) {
        val key = account.acct.ascii + ":" + status.busyKey
        mapBusyFav.add(key)
    }

    fun resetBusyFav(account: SavedAccount, status: TootStatus) {
        val key = account.acct.ascii + ":" + status.busyKey
        mapBusyFav.remove(key)
    }

    fun isBusyBookmark(account: SavedAccount, status: TootStatus): Boolean {
        val key = account.acct.ascii + ":" + status.busyKey
        return mapBusyBookmark.contains(key)
    }

    fun setBusyBookmark(account: SavedAccount, status: TootStatus) {
        val key = account.acct.ascii + ":" + status.busyKey
        mapBusyBookmark.add(key)
    }

    fun resetBusyBookmark(account: SavedAccount, status: TootStatus) {
        val key = account.acct.ascii + ":" + status.busyKey
        mapBusyBookmark.remove(key)
    }

    fun isBusyBoost(account: SavedAccount, status: TootStatus): Boolean {
        val key = account.acct.ascii + ":" + status.busyKey
        return mapBusyBoost.contains(key)
    }

    fun setBusyBoost(account: SavedAccount, status: TootStatus) {
        val key = account.acct.ascii + ":" + status.busyKey
        mapBusyBoost.add(key)
    }

    fun resetBusyBoost(account: SavedAccount, status: TootStatus) {
        val key = account.acct.ascii + ":" + status.busyKey
        mapBusyBoost.remove(key)
    }

    @SuppressLint("StaticFieldLeak")
    fun enableSpeech() {
        this.willSpeechEnabled = isTextToSpeechRequired

        if (willSpeechEnabled && tts == null && ttsStatus == TTS_STATUS_NONE) {
            ttsStatus = TTS_STATUS_INITIALIZING
            context.showToast(false, R.string.text_to_speech_initializing)
            log.d("initializing TextToSpeech…")

            launchIO {

                var tmpTts: TextToSpeech? = null

                val ttsInitListener: TextToSpeech.OnInitListener =
                    TextToSpeech.OnInitListener { status ->

                        val tts = tmpTts
                        if (tts == null || TextToSpeech.SUCCESS != status) {
                            context.showToast(
                                false,
                                R.string.text_to_speech_initialize_failed,
                                status
                            )
                            log.d("speech initialize failed. status=$status")
                            return@OnInitListener
                        }

                        runOnMainLooper {
                            if (!willSpeechEnabled) {
                                context.showToast(false, R.string.text_to_speech_shutdown)
                                log.d("shutdown TextToSpeech…")
                                tts.shutdown()
                            } else {
                                this@AppState.tts = tts
                                ttsStatus = TTS_STATUS_INITIALIZED
                                ttsSpeakStart = 0L
                                ttsSpeakEnd = 0L

                                voiceList.clear()
                                try {
                                    val voiceSet = try {
                                        tts.voices
                                        // may raise NullPointerException is tts has no collection
                                    } catch (ignored: Throwable) {
                                        null
                                    }
                                    if (voiceSet.isNullOrEmpty()) {
                                        log.d("TextToSpeech.getVoices returns null or empty set.")
                                    } else {
                                        val lang = defaultLocale(context).toLanguageTag()
                                        for (v in voiceSet) {
                                            log.d("Voice ${v.name} ${v.locale.toLanguageTag()} $lang")
                                            if (lang != v.locale.toLanguageTag()) continue
                                            voiceList.add(v)
                                        }
                                    }
                                } catch (ex: Throwable) {
                                    log.e(ex, "TextToSpeech.getVoices raises exception.")
                                }

                                handler.post(procFlushSpeechQueue)

                                ContextCompat.registerReceiver(
                                    context,
                                    ttsReceiver,
                                    IntentFilter(TextToSpeech.ACTION_TTS_QUEUE_PROCESSING_COMPLETED),
                                    ContextCompat.RECEIVER_EXPORTED,
                                    // RECEIVER_NOT_EXPORTED だと読み上げ完了を受け取れない
                                    // ContextCompat.RECEIVER_NOT_EXPORTED,
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

                tmpTts = TextToSpeech(context, ttsInitListener)
            }
            return
        }

        if (!willSpeechEnabled && tts != null) {
            context.showToast(false, R.string.text_to_speech_shutdown)
            log.d("shutdown TextToSpeech…")
            tts?.shutdown()
            tts = null
            ttsStatus = TTS_STATUS_NONE
        }
    }

    internal fun addSpeech(status: TootStatus) {

        if (tts == null) return

        val text = getStatusText(status).notBlank() ?: return

        val spanList = text.getSpans(0, text.length, MyClickableSpan::class.java)
        if (spanList == null || spanList.isEmpty()) {
            addSpeech(text.toString())
            return
        }
        Arrays.sort(spanList) { a, b ->
            val aStart = text.getSpanStart(a)
            val bStart = text.getSpanStart(b)
            aStart - bStart
        }
        val strText = text.toString()
        val sb = StringBuilder()
        var lastEnd = 0
        var hasUrl = false
        for (span in spanList) {
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            //
            if (start > lastEnd) {
                sb.append(strText.substring(lastEnd, start))
            }
            lastEnd = end
            //
            val spanText = strText.substring(start, end)
            if (spanText.isNotEmpty()) {
                val c = spanText[0]
                if (c == '#' || c == '@') {
                    // #hashtag や @user はそのまま読み上げる
                    sb.append(spanText)
                } else {
                    // それ以外はURL省略
                    hasUrl = true
                    sb.append(" ")
                }
            }
        }
        val textEnd = strText.length
        if (textEnd > lastEnd) {
            sb.append(strText.substring(lastEnd, textEnd))
        }
        if (hasUrl) {
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
                val check = duplicationCheck.find { it.text.equals(sv, ignoreCase = true) }
                if (check == null) {
                    duplicationCheck.addLast(DedupItem(sv))
                    if (duplicationCheck.size > 60) duplicationCheck.removeFirst()
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

        ttsQueue.add(sv)
        if (ttsQueue.size > 30) ttsQueue.removeFirst()

        handler.post(procFlushSpeechQueue)
    }

    private fun stopLastRingtone() {
        lastRingtone?.get()?.let { r ->
            try {
                r.stop()
            } catch (ex: Throwable) {
                log.e(ex, "stopLastRingtone failed.")
            } finally {
                lastRingtone = null
            }
        }
    }

    internal fun sound(item: HighlightWord) {
        // 短時間に何度もならないようにする
        val now = SystemClock.elapsedRealtime()
        if (now - lastSound < 500L) return
        lastSound = now

        stopLastRingtone()

        if (item.sound_type == HighlightWord.SOUND_TYPE_NONE) return

        fun Uri?.tryRingtone(): Boolean {
            try {
                if (this != null) {
                    RingtoneManager.getRingtone(context, this)?.let { ringTone ->
                        lastRingtone = WeakReference(ringTone)
                        ringTone.play()
                        return true
                    }
                }
            } catch (ex: Throwable) {
                log.e(ex, "tryRingtone failed.")
            }
            return false
        }

        if (item.sound_type == HighlightWord.SOUND_TYPE_CUSTOM && item.sound_uri.mayUri()
                .tryRingtone()
        ) return

        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).tryRingtone()
    }

    fun onMuteUpdated() {
        TootStatus.updateMuteData(force = true)
        columnList.forEach { it.onMuteUpdated() }
    }
}
