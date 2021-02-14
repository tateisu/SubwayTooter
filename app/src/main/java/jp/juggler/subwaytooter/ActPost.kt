package jp.juggler.subwaytooter

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.provider.MediaStore
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import jp.juggler.subwaytooter.Styler.defaultColorIcon
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.*
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.PostDraft
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.*
import jp.juggler.subwaytooter.view.FocusPointView
import jp.juggler.subwaytooter.view.MyEditText
import jp.juggler.subwaytooter.view.MyNetworkImageView
import jp.juggler.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.jetbrains.anko.textColor
import ru.gildor.coroutines.okhttp.await
import java.io.*
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class ActPost : AsyncActivity(),
    View.OnClickListener,
    PostAttachment.Callback {

    companion object {

        internal val log = LogCategory("ActPost")

        internal const val EXTRA_POSTED_ACCT = "posted_acct"
        internal const val EXTRA_POSTED_STATUS_ID = "posted_status_id"
        internal const val EXTRA_POSTED_REPLY_ID = "posted_reply_id"
        internal const val EXTRA_POSTED_REDRAFT_ID = "posted_redraft_id"

        internal const val KEY_ACCOUNT_DB_ID = "account_db_id"
        internal const val KEY_REPLY_STATUS = "reply_status"
        internal const val KEY_REDRAFT_STATUS = "redraft_status"
        internal const val KEY_INITIAL_TEXT = "initial_text"
        internal const val KEY_SENT_INTENT = "sent_intent"
        internal const val KEY_QUOTE = "quote"
        internal const val KEY_SCHEDULED_STATUS = "scheduled_status"

        internal const val KEY_ATTACHMENT_LIST = "attachment_list"
        internal const val KEY_VISIBILITY = "visibility"
        internal const val KEY_IN_REPLY_TO_ID = "in_reply_to_id"
        internal const val KEY_IN_REPLY_TO_TEXT = "in_reply_to_text"
        internal const val KEY_IN_REPLY_TO_IMAGE = "in_reply_to_image"
        internal const val KEY_IN_REPLY_TO_URL = "in_reply_to_url"

        private const val REQUEST_CODE_ATTACHMENT = 1
        private const val REQUEST_CODE_CAMERA = 2
        private const val REQUEST_CODE_MUSHROOM = 3
        private const val REQUEST_CODE_VIDEO = 4
        private const val REQUEST_CODE_ATTACHMENT_OLD = 5
        private const val REQUEST_CODE_SOUND = 6
        private const val REQUEST_CODE_CUSTOM_THUMBNAIL = 7

        private const val PERMISSION_REQUEST_CODE = 1

        internal const val MIME_TYPE_JPEG = "image/jpeg"
        internal const val MIME_TYPE_PNG = "image/png"


        internal val acceptable_mime_types = HashSet<String>().apply {
            //
            add("image/*") // Android標準のギャラリーが image/* を出してくることがあるらしい
            add("video/*") // Android標準のギャラリーが image/* を出してくることがあるらしい
            add("audio/*") // Android標準のギャラリーが image/* を出してくることがあるらしい
            //
            add("image/jpeg")
            add("image/png")
            add("image/gif")
            add("video/webm")
            add("video/mp4")
            add("video/quicktime")
            //
            add("audio/webm")
            add("audio/ogg")
            add("audio/mpeg")
            add("audio/mp3")
            add("audio/wav")
            add("audio/wave")
            add("audio/x-wav")
            add("audio/x-pn-wav")
            add("audio/flac")
            add("audio/x-flac")

            // https://github.com/tootsuite/mastodon/pull/11342
            add("audio/aac")
            add("audio/m4a")
            add("audio/3gpp")
        }
        internal val acceptable_mime_types_pixelfed = HashSet<String>().apply {
            //
            add("image/*") // Android標準のギャラリーが image/* を出してくることがあるらしい
            add("video/*") // Android標準のギャラリーが image/* を出してくることがあるらしい
            //
            add("image/jpeg")
            add("image/png")
            add("image/gif")
            add("video/mp4")
            add("video/m4v")
        }

        private val imageHeaderList = arrayOf(
            Pair(
                "image/jpeg",
                intArrayOf(0xff, 0xd8, 0xff).toByteArray()
            ),
            Pair(
                "image/png",
                intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A).toByteArray()
            ),
            Pair(
                "image/gif",
                charArrayOf('G', 'I', 'F').toLowerByteArray()
            ),
            Pair(
                "audio/wav",
                charArrayOf('R', 'I', 'F', 'F').toLowerByteArray()
            ),
            Pair(
                "audio/ogg",
                charArrayOf('O', 'g', 'g', 'S').toLowerByteArray()
            ),
            Pair(
                "audio/flac",
                charArrayOf('f', 'L', 'a', 'C').toLowerByteArray()
            )
        )

        private val sig3gp = arrayOf(
            "3ge6",
            "3ge7",
            "3gg6",
            "3gp1",
            "3gp2",
            "3gp3",
            "3gp4",
            "3gp5",
            "3gp6",
            "3gp7",
            "3gr6",
            "3gr7",
            "3gs6",
            "3gs7",
            "kddi"
        ).map { it.toCharArray().toLowerByteArray() }

        private val sigM4a = arrayOf(
            "M4A ",
            "M4B ",
            "M4P "
        ).map { it.toCharArray().toLowerByteArray() }

        private val sigFtyp = "ftyp".toCharArray().toLowerByteArray()

        private fun matchSig(
            data: ByteArray,
            dataOffset: Int,
            sig: ByteArray,
            sigSize: Int = sig.size
        ): Boolean {
            for (i in 0 until sigSize) {
                if (data[dataOffset + i] != sig[i]) return false
            }
            return true
        }

        private fun findMimeTypeByFileHeader(
            contentResolver: ContentResolver,
            uri: Uri
        ): String? {
            try {
                contentResolver.openInputStream(uri)?.use { inStream ->
                    val data = ByteArray(65536)
                    val nRead = inStream.read(data, 0, data.size)
                    for (pair in imageHeaderList) {
                        val type = pair.first
                        val header = pair.second
                        if (nRead >= header.size && data.startWith(header)) return type
                    }

                    // scan frame header
                    for (i in 0 until nRead - 8) {

                        if (!matchSig(data, i, sigFtyp)) continue

                        // 3gpp check
                        for (s in sig3gp) {
                            if (matchSig(data, i + 4, s)) return "audio/3gpp"
                        }

                        // m4a check
                        for (s in sigM4a) {
                            if (matchSig(data, i + 4, s)) return "audio/m4a"
                        }

                    }

                    // scan frame header
                    loop@ for (i in 0 until nRead - 2) {

                        // mpeg frame header
                        val b0 = data[i].toInt() and 255
                        if (b0 != 255) continue
                        val b1 = data[i + 1].toInt() and 255
                        if ((b1 and 0b11100000) != 0b11100000) continue

                        val mpegVersionId = ((b1 shr 3) and 3)
                        // 00 mpeg 2.5
                        // 01 not used
                        // 10 (mp3) mpeg 2 / (AAC) mpeg-4
                        // 11 (mp3) mpeg 1 / (AAC) mpeg-2

                        @Suppress("MoveVariableDeclarationIntoWhen")
                        val mpegLayerId = ((b1 shr 1) and 3)
                        // 00 (mp3)not used / (AAC) always 0
                        // 01 (mp3)layer III
                        // 10 (mp3)layer II
                        // 11 (mp3)layer I

                        when (mpegLayerId) {
                            0 -> when (mpegVersionId) {
                                2, 3 -> return "audio/aac"

                                else -> {
                                }
                            }
                            1 -> when (mpegVersionId) {
                                0, 2, 3 -> return "audio/mp3"

                                else -> {
                                }
                            }
                        }
                    }
                }
            } catch (ex: Throwable) {
                log.e(ex, "findMimeTypeByFileHeader failed.")
            }
            return null
        }

        /////////////////////////////////////////////////

        const val DRAFT_CONTENT = "content"
        const val DRAFT_CONTENT_WARNING = "content_warning"
        internal const val DRAFT_CONTENT_WARNING_CHECK = "content_warning_check"
        internal const val DRAFT_NSFW_CHECK = "nsfw_check"
        internal const val DRAFT_VISIBILITY = "visibility"
        internal const val DRAFT_ACCOUNT_DB_ID = "account_db_id"
        internal const val DRAFT_ATTACHMENT_LIST = "attachment_list"
        internal const val DRAFT_REPLY_ID = "reply_id"
        internal const val DRAFT_REPLY_TEXT = "reply_text"
        internal const val DRAFT_REPLY_IMAGE = "reply_image"
        internal const val DRAFT_REPLY_URL = "reply_url"
        internal const val DRAFT_IS_ENQUETE = "is_enquete"
        internal const val DRAFT_POLL_TYPE = "poll_type"
        internal const val DRAFT_POLL_MULTIPLE = "poll_multiple"
        internal const val DRAFT_POLL_HIDE_TOTALS = "poll_hide_totals"
        internal const val DRAFT_POLL_EXPIRE_DAY = "poll_expire_day"
        internal const val DRAFT_POLL_EXPIRE_HOUR = "poll_expire_hour"
        internal const val DRAFT_POLL_EXPIRE_MINUTE = "poll_expire_minute"
        internal const val DRAFT_ENQUETE_ITEMS = "enquete_items"
        internal const val DRAFT_QUOTE = "quotedRenote" // 歴史的な理由で名前がMisskey用になってる

        private const val STATE_MUSHROOM_INPUT = "mushroom_input"
        private const val STATE_MUSHROOM_START = "mushroom_start"
        private const val STATE_MUSHROOM_END = "mushroom_end"
        private const val STATE_REDRAFT_STATUS_ID = "redraft_status_id"
        private const val STATE_URI_CAMERA_IMAGE = "uri_camera_image"
        private const val STATE_TIME_SCHEDULE = "time_schedule"
        private const val STATE_SCHEDULED_STATUS = "scheduled_status"

        fun open(
            activity: Activity,
            request_code: Int,
            account_db_id: Long,

            // 再編集する投稿。アカウントと同一のタンスであること
            redraft_status: TootStatus? = null,

            // 返信対象の投稿。同一タンス上に同期済みであること
            reply_status: TootStatus? = null,

            //初期テキスト
            initial_text: String? = null,

            // 外部アプリから共有されたインテント
            sent_intent: Intent? = null,

            // 返信ではなく引用トゥートを作成する
            quote: Boolean = false,

            //(Mastodon) 予約投稿の編集
            scheduledStatus: TootScheduled? = null

        ) {
            val intent = Intent(activity, ActPost::class.java)
            intent.putExtra(KEY_ACCOUNT_DB_ID, account_db_id)

            if (redraft_status != null) {
                intent.putExtra(KEY_REDRAFT_STATUS, redraft_status.json.toString())
            }

            if (reply_status != null) {
                intent.putExtra(KEY_REPLY_STATUS, reply_status.json.toString())
                intent.putExtra(KEY_QUOTE, quote)
            }

            if (initial_text != null) {
                intent.putExtra(KEY_INITIAL_TEXT, initial_text)
            }

            if (sent_intent != null) {
                intent.putExtra(KEY_SENT_INTENT, sent_intent)
            }
            if (scheduledStatus != null) {
                intent.putExtra(KEY_SCHEDULED_STATUS, scheduledStatus.src.toString())
            }
            activity.startActivityForResult(intent, request_code)
        }

        internal suspend fun check_exist(url: String?): Boolean {
            if (url?.isEmpty() != false) return false
            try {
                val request = Request.Builder().url(url).build()
                val call = App1.ok_http_client.newCall(request)
                val response = call.await()
                if (response.isSuccessful) {
                    return true
                }
                log.e(TootApiClient.formatResponse(response, "check_exist failed."))
            } catch (ex: Throwable) {
                log.trace(ex)
            }

            return false
        }

    }

    private lateinit var btnAccount: Button
    private lateinit var btnVisibility: ImageButton
    private lateinit var btnAttachment: ImageButton
    private lateinit var btnPost: ImageButton
    private lateinit var llAttachment: View
    private lateinit var ivMedia: List<MyNetworkImageView>
    private lateinit var cbNSFW: CheckBox
    private lateinit var cbContentWarning: CheckBox
    private lateinit var etContentWarning: MyEditText
    private lateinit var etContent: MyEditText
    private lateinit var btnFeaturedTag: ImageButton

    private lateinit var cbQuote: CheckBox

    private lateinit var spEnquete: Spinner
    private lateinit var llEnquete: View
    private lateinit var list_etChoice: List<MyEditText>

    private lateinit var cbMultipleChoice: CheckBox
    private lateinit var cbHideTotals: CheckBox
    private lateinit var llExpire: LinearLayout
    private lateinit var etExpireDays: EditText
    private lateinit var etExpireHours: EditText
    private lateinit var etExpireMinutes: EditText

    private lateinit var tvCharCount: TextView
    internal lateinit var handler: Handler
    private lateinit var formRoot: View

    private lateinit var llReply: View
    private lateinit var tvReplyTo: TextView
    private lateinit var btnRemoveReply: ImageButton
    private lateinit var ivReply: MyNetworkImageView
    private lateinit var scrollView: ScrollView

    private lateinit var tvSchedule: TextView
    private lateinit var ibSchedule: ImageButton
    private lateinit var ibScheduleReset: ImageButton

    internal lateinit var pref: SharedPreferences
    internal lateinit var app_state: AppState
    private lateinit var post_helper: PostHelper
    private var attachment_list = ArrayList<PostAttachment>()
    private var isPostComplete: Boolean = false

    internal var density: Float = 0f

    private lateinit var account_list: ArrayList<SavedAccount>

    private var redraft_status_id: EntityId? = null

    private var timeSchedule = 0L

    private var scheduledStatus: TootScheduled? = null

    private val text_watcher: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {

        }

        override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {

        }

        override fun afterTextChanged(editable: Editable) {
            updateTextCount()
        }
    }

    private val scroll_listener: ViewTreeObserver.OnScrollChangedListener =
        ViewTreeObserver.OnScrollChangedListener { post_helper.onScrollChanged() }

    //////////////////////////////////////////////////////////
    // Account

    internal var account: SavedAccount? = null

    private var uriCameraImage: Uri? = null

    //////////////////////////////////////////////////////////////////////
    // visibility

    internal var visibility: TootVisibility? = null

    /////////////////////////////////////////////////

    internal var in_reply_to_id: EntityId? = null
    private var in_reply_to_text: String? = null
    private var in_reply_to_image: String? = null
    private var in_reply_to_url: String? = null
    private var mushroom_input: Int = 0
    private var mushroom_start: Int = 0
    private var mushroom_end: Int = 0

    private val link_click_listener: (View, MyClickableSpan) -> Unit = { _, span ->
        openBrowser(span.linkInfo.url)
    }

    ////////////////////////////////////////////////////////////////

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btnAccount -> performAccountChooser()
            R.id.btnVisibility -> performVisibility()
            R.id.btnAttachment -> openAttachment()
            R.id.ivMedia1 -> performAttachmentClick(0)
            R.id.ivMedia2 -> performAttachmentClick(1)
            R.id.ivMedia3 -> performAttachmentClick(2)
            R.id.ivMedia4 -> performAttachmentClick(3)
            R.id.btnPost -> performPost()
            R.id.btnRemoveReply -> removeReply()
            R.id.btnMore -> performMore()
            R.id.btnPlugin -> openMushroom()
            R.id.btnEmojiPicker -> post_helper.openEmojiPickerFromMore()
            R.id.btnFeaturedTag -> post_helper.openFeaturedTagList(
                featuredTagCache[account?.acct?.ascii ?: ""]?.list
            )
            R.id.ibSchedule -> performSchedule()
            R.id.ibScheduleReset -> resetSchedule()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK) {
            when (requestCode) {
                REQUEST_CODE_CAMERA -> {
                    // 失敗したら DBからデータを削除
                    val uriCameraImage = this.uriCameraImage
                    if (uriCameraImage != null) {
                        contentResolver.delete(uriCameraImage, null, null)
                        this@ActPost.uriCameraImage = null
                    }
                }
            }
        } else {
            when (requestCode) {
                REQUEST_CODE_CUSTOM_THUMBNAIL -> uploadCustomThumbnail(
                    data?.handleGetContentResult(
                        contentResolver
                    )
                )

                REQUEST_CODE_ATTACHMENT_OLD -> checkAttachments(
                    data?.handleGetContentResult(
                        contentResolver
                    )
                )
                REQUEST_CODE_ATTACHMENT -> checkAttachments(
                    data?.handleGetContentResult(
                        contentResolver
                    )
                )

                REQUEST_CODE_CAMERA -> {
                    // 画像のURL
                    val uri = data?.data ?: uriCameraImage
                    if (uri != null) {
                        addAttachment(uri)
                    } else {
                        showToast(false, "missing image uri")
                    }
                }

                REQUEST_CODE_VIDEO, REQUEST_CODE_SOUND -> data?.data?.let { addAttachment(it) }

                REQUEST_CODE_MUSHROOM -> {
                    data?.getStringExtra("replace_key")?.let { applyMushroomResult(it) }
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onBackPressed() {
        saveDraft()
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        MyClickableSpan.link_callback = WeakReference(link_click_listener)
    }

    override fun onPause() {
        super.onPause()

        // 編集中にホーム画面を押したり他アプリに移動する場合は下書きを保存する
        // やや過剰な気がするが、自アプリに戻ってくるときにランチャーからアイコンタップされると
        // メイン画面より上にあるアクティビティはすべて消されてしまうので
        // このタイミングで保存するしかない
        if (!isPostComplete) {
            saveDraft()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        var sv: String?

        super.onCreate(savedInstanceState)
        App1.setActivityTheme(this, noActionBar = true)

        app_state = App1.getAppState(this)
        pref = app_state.pref

        initUI()

        // Android 9 から、明示的にフォーカスを当てる必要がある
        if (savedInstanceState == null) {
            etContent.requestFocus()
        }

        if (account_list.isEmpty()) {
            showToast(true, R.string.please_add_account)
            finish()
            return
        }

        if (savedInstanceState != null) {

            mushroom_input = savedInstanceState.getInt(STATE_MUSHROOM_INPUT, 0)
            mushroom_start = savedInstanceState.getInt(STATE_MUSHROOM_START, 0)
            mushroom_end = savedInstanceState.getInt(STATE_MUSHROOM_END, 0)
            redraft_status_id = EntityId.from(savedInstanceState, STATE_REDRAFT_STATUS_ID)
            timeSchedule = savedInstanceState.getLong(STATE_TIME_SCHEDULE, 0L)


            savedInstanceState.getString(STATE_URI_CAMERA_IMAGE).mayUri()?.let {
                uriCameraImage = it
            }

            val account_db_id =
                savedInstanceState.getLong(KEY_ACCOUNT_DB_ID, SavedAccount.INVALID_DB_ID)
            if (account_db_id != SavedAccount.INVALID_DB_ID) {
                var i = 0
                val ie = account_list.size
                while (i < ie) {
                    val a = account_list[i]
                    if (a.db_id == account_db_id) {
                        selectAccount(a)
                        break
                    }
                    ++i
                }
            }

            this.visibility = TootVisibility.fromId(savedInstanceState.getInt(KEY_VISIBILITY, -1))

            val a = account
            if (a != null) {
                savedInstanceState.getString(STATE_SCHEDULED_STATUS)?.let {
                    scheduledStatus =
                        parseItem(
                            ::TootScheduled,
                            TootParser(this@ActPost, a),
                            it.decodeJsonObject(),
                            log
                        )
                }
            }

            if (app_state.attachment_list != null) {

                val list_in_state = app_state.attachment_list
                if (list_in_state != null) {
                    // static なデータが残ってるならそれを使う
                    this.attachment_list = list_in_state
                }

                // コールバックを新しい画面に差し替える
                for (pa in attachment_list) {
                    pa.callback = this
                }

            } else {
                sv = savedInstanceState.getString(KEY_ATTACHMENT_LIST)
                if (sv?.isNotEmpty() == true) {

                    // state から復元する
                    app_state.attachment_list = this.attachment_list
                    this.attachment_list.clear()

                    try {
                        sv.decodeJsonArray().objectList().forEach {
                            try {
                                attachment_list.add(PostAttachment(TootAttachment.decodeJson(it)))
                            } catch (ex: Throwable) {
                                log.trace(ex)
                            }
                        }

                    } catch (ex: Throwable) {
                        log.trace(ex)
                    }

                }
            }

            this.in_reply_to_id = EntityId.from(savedInstanceState, KEY_IN_REPLY_TO_ID)
            this.in_reply_to_text = savedInstanceState.getString(KEY_IN_REPLY_TO_TEXT)
            this.in_reply_to_image = savedInstanceState.getString(KEY_IN_REPLY_TO_IMAGE)
            this.in_reply_to_url = savedInstanceState.getString(KEY_IN_REPLY_TO_URL)
        } else {
            app_state.attachment_list = this.attachment_list
            this.attachment_list.clear()

            val intent = intent
            val account_db_id = intent.getLongExtra(KEY_ACCOUNT_DB_ID, SavedAccount.INVALID_DB_ID)
            if (account_db_id != SavedAccount.INVALID_DB_ID) {
                var i = 0
                val ie = account_list.size
                while (i < ie) {
                    val a = account_list[i]
                    if (a.db_id == account_db_id) {
                        selectAccount(a)
                        break
                    }
                    ++i
                }
            }

            val sent_intent = intent.getParcelableExtra<Intent>(KEY_SENT_INTENT)
            if (sent_intent != null) {

                val hasUri = when (sent_intent.action) {
                    Intent.ACTION_VIEW -> {
                        val uri = sent_intent.data
                        val type = sent_intent.type
                        if (uri != null) {
                            addAttachment(uri, type)
                            true
                        } else {
                            false
                        }
                    }

                    Intent.ACTION_SEND -> {
                        val uri = sent_intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                        val type = sent_intent.type
                        if (uri != null) {
                            addAttachment(uri, type)
                            true
                        } else {
                            false
                        }
                    }

                    Intent.ACTION_SEND_MULTIPLE -> {
                        val list_uri =
                            sent_intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                                ?.filterNotNull()
                        if (list_uri?.isNotEmpty() == true) {
                            for (uri in list_uri) {
                                addAttachment(uri)
                            }
                            true
                        } else {
                            false
                        }
                    }

                    else -> false
                }

                if (!hasUri || !Pref.bpIgnoreTextInSharedMedia(pref)) {
                    appendContentText(sent_intent)
                }
            }

            appendContentText(intent.getStringExtra(KEY_INITIAL_TEXT))

            val account = this.account

            sv = intent.getStringExtra(KEY_REPLY_STATUS)
            if (sv != null && account != null) {
                try {
                    val reply_status =
                        TootParser(this@ActPost, account).status(sv.decodeJsonObject())

                    val isQuote = intent.getBooleanExtra(KEY_QUOTE, false)

                    if (reply_status != null) {

                        if (isQuote) {
                            cbQuote.isChecked = true

                            // 引用リノートはCWやメンションを引き継がない

                        } else {

                            // CW をリプライ元に合わせる
                            if (reply_status.spoiler_text.isNotEmpty()) {
                                cbContentWarning.isChecked = true
                                etContentWarning.setText(reply_status.spoiler_text)
                            }

                            // 新しいメンションリスト
                            val mention_list = ArrayList<Acct>()

                            // 自己レス以外なら元レスへのメンションを追加
                            // 最初に追加する https://github.com/tateisu/SubwayTooter/issues/94
                            if (!account.isMe(reply_status.account)) {
                                mention_list.add(account.getFullAcct(reply_status.account))
                            }

                            // 元レスに含まれていたメンションを複製
                            reply_status.mentions?.forEach { mention ->

                                val who_acct = mention.acct

                                // 空データなら追加しない
                                if (!who_acct.isValid) return@forEach

                                // 自分なら追加しない
                                if (account.isMe(who_acct)) return@forEach

                                // 既出でないなら追加する
                                val acct = account.getFullAcct(who_acct)
                                if (!mention_list.contains(acct)) mention_list.add(acct)
                            }

                            if (mention_list.isNotEmpty()) {
                                appendContentText(
                                    StringBuilder().apply {
                                        for (acct in mention_list) {
                                            if (isNotEmpty()) append(' ')
                                            append("@${acct.ascii}")
                                        }
                                        append(' ')
                                    }.toString()
                                )
                            }
                        }

                        // リプライ表示をつける
                        in_reply_to_id = reply_status.id
                        in_reply_to_text = reply_status.content
                        in_reply_to_image = reply_status.account.avatar_static
                        in_reply_to_url = reply_status.url

                        // 公開範囲
                        try {
                            // 比較する前にデフォルトの公開範囲を計算する
                            visibility = visibility
                                ?: account.visibility
                            //	?: TootVisibility.Public
                            // VISIBILITY_WEB_SETTING だと 1.5未満のタンスでトラブルになる

                            if (visibility == TootVisibility.Unknown) {
                                visibility = TootVisibility.PrivateFollowers
                            }

                            val sample = when (val base = reply_status.visibility) {
                                TootVisibility.Unknown -> TootVisibility.PrivateFollowers
                                else -> base
                            }

                            if (TootVisibility.WebSetting == visibility) {
                                // 「Web設定に合わせる」だった場合は無条件にリプライ元の公開範囲に変更する
                                this.visibility = sample
                            } else if (TootVisibility.isVisibilitySpoilRequired(
                                    this.visibility, sample)) {
                                // デフォルトの方が公開範囲が大きい場合、リプライ元に合わせて公開範囲を狭める
                                this.visibility = sample
                            }


                        } catch (ex: Throwable) {
                            log.trace(ex)
                        }
                    }
                } catch (ex: Throwable) {
                    log.trace(ex)
                }
            }

            appendContentText(account?.default_text, selectBefore = true)

            cbNSFW.isChecked = account?.default_sensitive ?: false

            // 再編集
            sv = intent.getStringExtra(KEY_REDRAFT_STATUS)
            if (sv != null && account != null) {
                try {
                    val base_status =
                        TootParser(this@ActPost, account).status(sv.decodeJsonObject())
                    if (base_status != null) {

                        redraft_status_id = base_status.id

                        this.visibility = base_status.visibility

                        val src_attachments = base_status.media_attachments
                        if (src_attachments?.isNotEmpty() == true) {
                            app_state.attachment_list = this.attachment_list
                            this.attachment_list.clear()
                            try {
                                for (src in src_attachments) {
                                    if (src is TootAttachment) {
                                        src.redraft = true
                                        val pa = PostAttachment(src)
                                        pa.status = PostAttachment.STATUS_UPLOADED
                                        this.attachment_list.add(pa)
                                    }
                                }

                            } catch (ex: Throwable) {
                                log.trace(ex)
                            }
                        }

                        cbNSFW.isChecked = base_status.sensitive == true

                        // 再編集の場合はdefault_textは反映されない

                        val decodeOptions = DecodeOptions(
                            this,
                            mentionFullAcct = true,
                            mentions = base_status.mentions,
                            mentionDefaultHostDomain = account
                        )

                        var text: CharSequence = if (account.isMisskey) {
                            base_status.content ?: ""
                        } else {
                            decodeOptions.decodeHTML(base_status.content)
                        }
                        etContent.setText(text)
                        etContent.setSelection(text.length)

                        text = decodeOptions.decodeEmoji(base_status.spoiler_text)
                        etContentWarning.setText(text)
                        etContentWarning.setSelection(text.length)
                        cbContentWarning.isChecked = text.isNotEmpty()

                        val src_enquete = base_status.enquete
                        val src_items = src_enquete?.items
                        when {
                            src_items == null -> {

                            }

                            src_enquete.pollType == TootPollsType.FriendsNico && src_enquete.type != TootPolls.TYPE_ENQUETE -> {
                                // フレニコAPIのアンケート結果は再編集の対象外
                            }

                            else -> {
                                spEnquete.setSelection(
                                    if (src_enquete.pollType == TootPollsType.FriendsNico) {
                                        2
                                    } else {
                                        1
                                    }
                                )
                                text = decodeOptions.decodeHTML(src_enquete.question)
                                etContent.text = text
                                etContent.setSelection(text.length)

                                var src_index = 0
                                loop@ for (et in list_etChoice) {
                                    if (src_index < src_items.size) {
                                        val choice = src_items[src_index]
                                        when {
                                            src_index == src_items.size - 1 && choice.text == "\uD83E\uDD14" -> {
                                                // :thinking_face: は再現しない
                                            }

                                            else -> {
                                                et.setText(decodeOptions.decodeEmoji(choice.text))
                                                ++src_index
                                                continue@loop
                                            }
                                        }
                                    }
                                    et.setText("")
                                }
                            }
                        }
                    }

                } catch (ex: Throwable) {
                    log.trace(ex)
                }

            }

            // 予約編集の再編集
            sv = intent.getStringExtra(KEY_SCHEDULED_STATUS)
            if (sv != null && account != null) {
                try {
                    val item = parseItem(
                        ::TootScheduled,
                        TootParser(this@ActPost, account),
                        sv.decodeJsonObject(),
                        log
                    )
                    if (item != null) {
                        scheduledStatus = item

                        timeSchedule = item.timeScheduledAt

                        val text = item.text
                        etContent.setText(text)

                        val cw = item.spoiler_text
                        if (cw?.isNotEmpty() == true) {
                            etContentWarning.setText(cw)
                            cbContentWarning.isChecked = true
                        } else {
                            cbContentWarning.isChecked = false
                        }
                        visibility = item.visibility

                        // 2019/1/7 どうも添付データを古い投稿から引き継げないようだ…。
                        // 2019/1/22 https://github.com/tootsuite/mastodon/pull/9894 で直った。
                        val src_attachments = item.media_attachments
                        if (src_attachments?.isNotEmpty() == true) {
                            app_state.attachment_list = this.attachment_list
                            this.attachment_list.clear()
                            try {
                                for (src in src_attachments) {
                                    if (src is TootAttachment) {
                                        src.redraft = true
                                        val pa = PostAttachment(src)
                                        pa.status = PostAttachment.STATUS_UPLOADED
                                        this.attachment_list.add(pa)
                                    }
                                }
                            } catch (ex: Throwable) {
                                log.trace(ex)
                            }
                        }
                        cbNSFW.isChecked = item.sensitive
                    }
                } catch (ex: Throwable) {
                    log.trace(ex)
                }
            }
        }

        visibility = visibility ?: account?.visibility ?: TootVisibility.Public
        // 2017/9/13 VISIBILITY_WEB_SETTING から VISIBILITY_PUBLICに変更した
        // VISIBILITY_WEB_SETTING だと 1.5未満のタンスでトラブルになるので…

        if (this.account == null) {
            // 表示を未選択に更新
            selectAccount(null)
        }

        updateContentWarning()
        showMediaAttachment()
        showVisibility()
        updateTextCount()
        showReplyTo()
        showEnquete()
        showQuotedRenote()
        showSchedule()
    }

    override fun onDestroy() {
        post_helper.onDestroy()
        attachment_worker?.cancel()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putInt(STATE_MUSHROOM_INPUT, mushroom_input)
        outState.putInt(STATE_MUSHROOM_START, mushroom_start)
        outState.putInt(STATE_MUSHROOM_END, mushroom_end)
        redraft_status_id?.putTo(outState, STATE_REDRAFT_STATUS_ID)

        outState.putLong(STATE_TIME_SCHEDULE, timeSchedule)

        if (uriCameraImage != null) {
            outState.putString(STATE_URI_CAMERA_IMAGE, uriCameraImage.toString())
        }

        val account = this.account
        if (account != null) {
            outState.putLong(KEY_ACCOUNT_DB_ID, account.db_id)
        }

        visibility?.let {
            outState.putInt(KEY_VISIBILITY, it.id)
        }

        val array = attachment_list
            // アップロード完了したものだけ保持する
            .filter { it.status == PostAttachment.STATUS_UPLOADED }
            .mapNotNull { it.attachment?.encodeJson() }
            .toJsonArray()
            .notEmpty()

        if (array != null) outState.putString(KEY_ATTACHMENT_LIST, array.toString())

        in_reply_to_id?.putTo(outState, KEY_IN_REPLY_TO_ID)
        outState.putString(KEY_IN_REPLY_TO_TEXT, in_reply_to_text)
        outState.putString(KEY_IN_REPLY_TO_IMAGE, in_reply_to_image)
        outState.putString(KEY_IN_REPLY_TO_URL, in_reply_to_url)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        updateContentWarning()
        showMediaAttachment()
        showVisibility()
        updateTextCount()
        showReplyTo()
        showEnquete()
        showQuotedRenote()
    }

    private fun appendContentText(
        src: String?,
        selectBefore: Boolean = false
    ) {
        if (src?.isEmpty() != false) return
        val svEmoji = DecodeOptions(
            context = this,
            decodeEmoji = true,
            mentionDefaultHostDomain = account ?: unknownHostAndDomain
        ).decodeEmoji(src)
        if (svEmoji.isEmpty()) return

        val editable = etContent.text
        if (editable == null) {
            val sb = StringBuilder()
            if (selectBefore) {
                val start = 0
                sb.append(' ')
                sb.append(svEmoji)
                etContent.setText(sb)
                etContent.setSelection(start)
            } else {
                sb.append(svEmoji)
                etContent.setText(sb)
                etContent.setSelection(sb.length)
            }
        } else {
            if (editable.isNotEmpty()
                && !CharacterGroup.isWhitespace(editable[editable.length - 1].toInt())
            ) {
                editable.append(' ')
            }

            if (selectBefore) {
                val start = editable.length
                editable.append(' ')
                editable.append(svEmoji)
                etContent.text = editable
                etContent.setSelection(start)
            } else {
                editable.append(svEmoji)
                etContent.text = editable
                etContent.setSelection(editable.length)
            }
        }
    }

    private fun appendContentText(src: Intent) {
        val list = ArrayList<String>()

        var sv: String?
        sv = src.getStringExtra(Intent.EXTRA_SUBJECT)
        if (sv?.isNotEmpty() == true) list.add(sv)
        sv = src.getStringExtra(Intent.EXTRA_TEXT)
        if (sv?.isNotEmpty() == true) list.add(sv)

        if (list.isNotEmpty()) {
            appendContentText(list.joinToString(" "))
        }
    }

    private fun initUI() {
        handler = App1.getAppState(this).handler
        density = resources.displayMetrics.density

        setContentView(R.layout.act_post)
        App1.initEdgeToEdge(this)

        if (Pref.bpPostButtonBarTop(this)) {
            val bar = findViewById<View>(R.id.llFooterBar)
            val parent = bar.parent as ViewGroup
            parent.removeView(bar)
            parent.addView(bar, 0)
        }

        Styler.fixHorizontalMargin(findViewById(R.id.scrollView))
        Styler.fixHorizontalMargin(findViewById(R.id.llFooterBar))

        formRoot = findViewById(R.id.viewRoot)
        scrollView = findViewById(R.id.scrollView)
        btnAccount = findViewById(R.id.btnAccount)
        btnVisibility = findViewById(R.id.btnVisibility)
        btnAttachment = findViewById(R.id.btnAttachment)
        btnPost = findViewById(R.id.btnPost)
        llAttachment = findViewById(R.id.llAttachment)

        cbNSFW = findViewById(R.id.cbNSFW)
        cbContentWarning = findViewById(R.id.cbContentWarning)
        etContentWarning = findViewById(R.id.etContentWarning)
        etContent = findViewById(R.id.etContent)
        // https://github.com/tateisu/SubwayTooter/issues/123
        // 早い段階で指定する必要がある
        etContent.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        etContent.imeOptions = EditorInfo.IME_ACTION_NONE


        cbQuote = findViewById(R.id.cbQuote)

        spEnquete = findViewById<Spinner>(R.id.spEnquete).apply {
            this.adapter = ArrayAdapter(
                this@ActPost,
                android.R.layout.simple_spinner_item,
                arrayOf(
                    getString(R.string.poll_dont_make),
                    getString(R.string.poll_make),
                    getString(R.string.poll_make_friends_nico)
                )
            ).apply {
                setDropDownViewResource(R.layout.lv_spinner_dropdown)
            }

            this.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                    showEnquete()
                    updateTextCount()
                }

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    showEnquete()
                    updateTextCount()
                }
            }
        }
        llEnquete = findViewById(R.id.llEnquete)
        llExpire = findViewById(R.id.llExpire)
        cbHideTotals = findViewById(R.id.cbHideTotals)
        cbMultipleChoice = findViewById(R.id.cbMultipleChoice)
        etExpireDays = findViewById(R.id.etExpireDays)
        etExpireHours = findViewById(R.id.etExpireHours)
        etExpireMinutes = findViewById(R.id.etExpireMinutes)

        ivMedia = listOf(
            findViewById(R.id.ivMedia1),
            findViewById(R.id.ivMedia2),
            findViewById(R.id.ivMedia3),
            findViewById(R.id.ivMedia4)
        )

        list_etChoice = listOf(
            findViewById(R.id.etChoice1),
            findViewById(R.id.etChoice2),
            findViewById(R.id.etChoice3),
            findViewById(R.id.etChoice4)
        )

        tvCharCount = findViewById(R.id.tvCharCount)

        llReply = findViewById(R.id.llReply)
        tvReplyTo = findViewById(R.id.tvReplyTo)
        btnRemoveReply = findViewById(R.id.btnRemoveReply)
        ivReply = findViewById(R.id.ivReply)

        tvSchedule = findViewById(R.id.tvSchedule)
        ibSchedule = findViewById(R.id.ibSchedule)
        ibScheduleReset = findViewById(R.id.ibScheduleReset)

        ibSchedule.setOnClickListener(this)
        ibScheduleReset.setOnClickListener(this)

        account_list = SavedAccount.loadAccountList(this@ActPost)
        SavedAccount.sort(account_list)

        btnAccount.setOnClickListener(this)
        btnVisibility.setOnClickListener(this)
        btnAttachment.setOnClickListener(this)
        btnPost.setOnClickListener(this)
        btnRemoveReply.setOnClickListener(this)

        btnFeaturedTag = findViewById(R.id.btnFeaturedTag)

        val btnPlugin: ImageButton = findViewById(R.id.btnPlugin)
        val btnEmojiPicker: ImageButton = findViewById(R.id.btnEmojiPicker)
        val btnMore: ImageButton = findViewById(R.id.btnMore)

        btnPlugin.setOnClickListener(this)
        btnEmojiPicker.setOnClickListener(this)
        btnFeaturedTag.setOnClickListener(this)
        btnMore.setOnClickListener(this)

        for (iv in ivMedia) {
            iv.setOnClickListener(this)
        }

        cbContentWarning.setOnCheckedChangeListener { _, _ ->
            updateContentWarning()
        }


        post_helper = PostHelper(this, pref, app_state.handler)
        post_helper.attachEditText(formRoot, etContent, false, object : PostHelper.Callback2 {
            override fun onTextUpdate() {
                updateTextCount()
            }

            override fun canOpenPopup(): Boolean {
                return true
            }
        })

        etContentWarning.addTextChangedListener(text_watcher)
        for (et in list_etChoice) {
            et.addTextChangedListener(text_watcher)
        }

        scrollView.viewTreeObserver.addOnScrollChangedListener(scroll_listener)


        etContent.contentMineTypeArray =
            acceptable_mime_types.toArray(arrayOfNulls<String>(acceptable_mime_types.size))
        etContent.commitContentListener = commitContentListener
    }

    private var lastInstanceTask: TootTaskRunner? = null

    private fun getMaxCharCount(): Int {

        val account = account

        when {
            account == null || account.isPseudo -> {
            }

            else -> {
                // インスタンス情報を確認する
                val info = TootInstance.getCached(account.apiHost.ascii)
                if (info == null || info.isExpire) {
                    // 情報がないか古いなら再取得

                    // 同時に実行するタスクは1つまで
                    if (lastInstanceTask?.isActive != true) {
                        lastInstanceTask = TootTaskRunner(this, TootTaskRunner.PROGRESS_NONE)
                            .run(account, object : TootTask {
                                var newInfo: TootInstance? = null

                                override suspend fun background(client: TootApiClient): TootApiResult? {
                                    val (ti, result) = TootInstance.get(client)
                                    newInfo = ti
                                    return result
                                }

                                override suspend fun handleResult(result: TootApiResult?) {
                                    if (isFinishing || isDestroyed) return
                                    if (newInfo != null) updateTextCount()
                                }
                            })
                        // fall thru
                    }
                    // fall thru
                }

                val max = info?.max_toot_chars
                if (max != null && max > 0) return max
            }
        }

        // アカウント設定で指定した値があるならそれを使う
        val forceMaxTootChars = account?.max_toot_chars
        return when {
            forceMaxTootChars != null && forceMaxTootChars > 0 -> forceMaxTootChars
            else -> 500
        }
    }

    private fun updateTextCount() {
        var length = 0

        length += TootAccount.countText(
            EmojiDecoder.decodeShortCode(etContent.text.toString())
        )

        length += TootAccount.countText(
            if (cbContentWarning.isChecked)
                EmojiDecoder.decodeShortCode(etContentWarning.text.toString())
            else
                ""
        )

        var max = getMaxCharCount()

        fun checkEnqueteLength() {
            for (et in list_etChoice) {
                length += TootAccount.countText(
                    EmojiDecoder.decodeShortCode(et.text.toString())
                )
            }
        }

        when (spEnquete.selectedItemPosition) {
            1 -> checkEnqueteLength()

            2 -> {
                max -= 150 // フレニコ固有。500-150で350になる
                checkEnqueteLength()
            }
        }

        val remain = max - length

        tvCharCount.text = remain.toString()
        tvCharCount.setTextColor(
            attrColor(
                if (remain < 0)
                    R.attr.colorRegexFilterError
                else
                    android.R.attr.textColorPrimary
            )
        )
    }

    class FeaturedTagCache(
        val list: List<TootTag>,
        val time: Long
    )

    // key is SavedAccount.acctAscii
    private val featuredTagCache = ConcurrentHashMap<String, FeaturedTagCache>()
    private var lastFeaturedTagTask: TootTaskRunner? = null

    private fun updateFeaturedTags() {

        val account = account
        if (account == null || account.isPseudo) {
            return
        }

        val cache = featuredTagCache[account.acct.ascii]

        val now = SystemClock.elapsedRealtime()
        if (cache != null && now - cache.time <= 300000L) return

        // 同時に実行するタスクは1つまで
        var lastTask = lastFeaturedTagTask
        if (lastTask?.isActive != true) {
            lastTask = TootTaskRunner(this, TootTaskRunner.PROGRESS_NONE)
            lastFeaturedTagTask = lastTask
            lastTask.run(account, object : TootTask {

                override suspend fun background(client: TootApiClient): TootApiResult? {
                    return if (account.isMisskey) {
                        client.request(
                            "/api/hashtags/trend",
                            jsonObject { }
                                .toPostRequestBuilder()
                        )?.also { result ->
                            val list = TootTag.parseList(
                                TootParser(this@ActPost, account),
                                result.jsonArray
                            )
                            featuredTagCache[account.acct.ascii] =
                                FeaturedTagCache(list, SystemClock.elapsedRealtime())
                        }
                    } else {
                        client.request("/api/v1/featured_tags")?.also { result ->
                            val list = TootTag.parseList(
                                TootParser(this@ActPost, account),
                                result.jsonArray
                            )
                            featuredTagCache[account.acct.ascii] =
                                FeaturedTagCache(list, SystemClock.elapsedRealtime())
                        }
                    }
                }

                override suspend fun handleResult(result: TootApiResult?) {
                    if (isFinishing || isDestroyed) return
                    updateFeaturedTags()
                }
            })
        }
    }

    private fun updateContentWarning() {
        etContentWarning.visibility = if (cbContentWarning.isChecked) View.VISIBLE else View.GONE
    }

    private fun selectAccount(a: SavedAccount?) {
        this.account = a

        post_helper.setInstance(a)

        if (a == null) {
            btnAccount.text = getString(R.string.not_selected)
            btnAccount.setTextColor(attrColor(android.R.attr.textColorPrimary))
            btnAccount.setBackgroundResource(R.drawable.btn_bg_transparent_round6dp)
        } else {

            // 先読みしてキャッシュに保持しておく
            App1.custom_emoji_lister.getList(a) {
                // 何もしない
            }

            val ac = AcctColor.load(a)
            btnAccount.text = ac.nickname

            if (AcctColor.hasColorBackground(ac)) {
                btnAccount.background =
                    getAdaptiveRippleDrawableRound(this, ac.color_bg, ac.color_fg)
            } else {
                btnAccount.setBackgroundResource(R.drawable.btn_bg_transparent_round6dp)
            }

            btnAccount.textColor = ac.color_fg.notZero()
                ?: attrColor(android.R.attr.textColorPrimary)
        }
        updateTextCount()
        updateFeaturedTags()
    }

    private fun performAccountChooser() {

        if (scheduledStatus != null) {
            // 予約投稿の再編集ではアカウントを切り替えられない
            showToast(false, R.string.cant_change_account_when_editing_scheduled_status)
            return
        }

        if (attachment_list.isNotEmpty()) {
            // 添付ファイルがあったら確認の上添付ファイルを捨てないと切り替えられない
            showToast(false, R.string.cant_change_account_when_attachment_specified)
            return
        }

        if (redraft_status_id != null) {
            // 添付ファイルがあったら確認の上添付ファイルを捨てないと切り替えられない
            showToast(false, R.string.cant_change_account_when_redraft)
            return
        }

        AccountPicker.pick(
            this,
            bAllowPseudo = false,
            bAuto = false,
            message = getString(R.string.choose_account)
        ) { ai ->

            // 別タンスのアカウントに変更したならならin_reply_toの変換が必要
            if (in_reply_to_id != null && ai.apiHost != account?.apiHost) {
                startReplyConversion(ai)
            } else {
                setAccountWithVisibilityConversion(ai)
            }
        }

        //		final ArrayList< SavedAccount > tmp_account_list = new ArrayList<>();
        //		tmp_account_list.addAll( account_list );
        //
        //		String[] caption_list = new String[ tmp_account_list.size() ];
        //		for( int i = 0, ie = tmp_account_list.size() ; i < ie ; ++ i ){
        //			caption_list[ i ] = tmp_account_list.get( i ).acct;
        //		}
        //
        //		new AlertDialog.Builder( this )
        //			.setTitle( R.string.choose_account )
        //			.setItems( caption_list, new DialogInterface.OnClickListener() {
        //				@Override
        //				public void onClick( DialogInterface dialog, int which ){
        //
        //					if( which < 0 || which >= tmp_account_list.size() ){
        //						// 範囲外
        //						return;
        //					}
        //
        //					SavedAccount ai = tmp_account_list.get( which );
        //
        //					if( ! ai.host.equals( account.host ) ){
        //						// 別タンスへの移動
        //						if( in_reply_to_id != - 1L ){
        //							// 別タンスのアカウントならin_reply_toの変換が必要
        //							startReplyConversion( ai );
        //
        //						}
        //					}
        //
        //					// リプライがないか、同タンスへの移動
        //					setAccountWithVisibilityConversion( ai );
        //				}
        //			} )
        //			.setNegativeButton( R.string.cancel, null )
        //			.show();
    }

    internal fun setAccountWithVisibilityConversion(a: SavedAccount) {
        selectAccount(a)
        try {
            if (TootVisibility.isVisibilitySpoilRequired(this.visibility, a.visibility)) {
                showToast(true, R.string.spoil_visibility_for_account)
                this.visibility = a.visibility
            }

        } catch (ex: Throwable) {
            log.trace(ex)
        }
        showVisibility()
        showQuotedRenote()
        updateTextCount()
    }

    @SuppressLint("StaticFieldLeak")
    private fun startReplyConversion(access_info: SavedAccount) {

        val in_reply_to_url = this.in_reply_to_url

        if (in_reply_to_url == null) {
            // 下書きが古い形式の場合、URLがないので別タンスへの移動ができない
            AlertDialog.Builder(this@ActPost)
                .setMessage(R.string.account_change_failed_old_draft_has_no_in_reply_to_url)
                .setNeutralButton(R.string.close, null)
                .show()
            return
        }

        TootTaskRunner(this)
            .progressPrefix(getString(R.string.progress_synchronize_toot))
            .run(access_info, object : TootTask {

                var target_status: TootStatus? = null
                override suspend fun background(client: TootApiClient): TootApiResult? {
                    val (result, localStatus) = client.syncStatus(access_info, in_reply_to_url)
                    target_status = localStatus
                    return result
                }

                override suspend fun handleResult(result: TootApiResult?) {
                    if (result == null) return  // cancelled.

                    val target_status = this.target_status
                    if (target_status != null) {
                        in_reply_to_id = target_status.id
                        setAccountWithVisibilityConversion(access_info)
                    } else {
                        showToast(
                            true,
                            getString(R.string.in_reply_to_id_conversion_failed) + "\n" + result.error
                        )
                    }
                }
            })

    }

    //////////////////////////////////////////////////////////
    // Attachment

    private fun showMediaAttachment() {

        if (isFinishing) return

        llAttachment.vg(attachment_list.isNotEmpty())
        ivMedia.forEachIndexed { i, v -> showAttachment_sub(v, i) }
    }

    private fun showAttachment_sub(iv: MyNetworkImageView, idx: Int) {
        if (idx >= attachment_list.size) {
            iv.visibility = View.GONE
        } else {
            iv.visibility = View.VISIBLE
            val pa = attachment_list[idx]
            val a = pa.attachment
            when {

                a == null || pa.status != PostAttachment.STATUS_UPLOADED -> {
                    iv.setDefaultImage(defaultColorIcon(this, R.drawable.ic_upload))
                    iv.setErrorImage(defaultColorIcon(this, R.drawable.ic_clip))
                    iv.setImageUrl(pref, Styler.calcIconRound(iv.layoutParams.width), null)
                }

                else -> {
                    val defaultIconId = when (a.type) {
                        TootAttachmentType.Image -> R.drawable.ic_image
                        TootAttachmentType.Video,
                        TootAttachmentType.GIFV -> R.drawable.ic_videocam
                        TootAttachmentType.Audio -> R.drawable.ic_music_note
                        else -> R.drawable.ic_clip
                    }
                    iv.setDefaultImage(defaultColorIcon(this, defaultIconId))
                    iv.setErrorImage(defaultColorIcon(this, defaultIconId))
                    iv.setImageUrl(pref, Styler.calcIconRound(iv.layoutParams.width), a.preview_url)
                }
            }
        }
    }

    // 添付した画像をタップ
    private fun performAttachmentClick(idx: Int) {
        val pa = try {
            attachment_list[idx]
        } catch (ex: Throwable) {
            showToast(false, ex.withCaption("can't get attachment item[$idx]."))
            return
        }

        val a = ActionsDialog()
            .addAction(getString(R.string.set_description)) {
                editAttachmentDescription(pa)
            }

        if (pa.attachment?.canFocus == true) {
            a.addAction(getString(R.string.set_focus_point)) {
                openFocusPoint(pa)
            }
        }
        if (account?.isMastodon == true) {
            when (pa.attachment?.type) {
                TootAttachmentType.Audio, TootAttachmentType.GIFV, TootAttachmentType.Video ->
                    a.addAction(getString(R.string.custom_thumbnail)) {
                        openCustomThumbnail(pa)
                    }

                else -> {
                }
            }
        }

        a.addAction(getString(R.string.delete)) {
            deleteAttachment(pa)
        }

        a.show(this, title = getString(R.string.media_attachment))
    }

    private fun openFocusPoint(pa: PostAttachment) {
        val attachment = pa.attachment ?: return
        DlgFocusPoint(this, attachment)
            .setCallback(object : FocusPointView.Callback {
                override fun onFocusPointUpdate(x: Float, y: Float) {
                    val account = this@ActPost.account ?: return

                    TootTaskRunner(this@ActPost, TootTaskRunner.PROGRESS_NONE).run(account,
                        object : TootTask {
                            override suspend fun background(client: TootApiClient): TootApiResult? {
                                try {
                                    val result = client.request(
                                        "/api/v1/media/${attachment.id}",
                                        jsonObject {
                                            put("focus", "%.2f,%.2f".format(x, y))
                                        }
                                            .toPutRequestBuilder()
                                    )
                                    new_attachment =
                                        parseItem(
                                            ::TootAttachment,
                                            ServiceType.MASTODON,
                                            result?.jsonObject
                                        )
                                    return result
                                } catch (ex: Throwable) {
                                    return TootApiResult(ex.withCaption("set focus point failed."))
                                }
                            }

                            var new_attachment: TootAttachment? = null

                            override suspend fun handleResult(result: TootApiResult?) {
                                result ?: return
                                if (new_attachment != null) {
                                    pa.attachment = attachment
                                } else {
                                    showToast(true, result.error)
                                }
                            }
                        })
                }
            })
            .show()
    }

    private fun openCustomThumbnail(pa: PostAttachment) {

        lastPostAttachment = pa

        val permissionCheck =
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            preparePermission()
            return
        }

        // SAFのIntentで開く
        try {
            val intent =
                intentGetContent(false, getString(R.string.pick_images), arrayOf("image/*"))
            startActivityForResult(intent, REQUEST_CODE_CUSTOM_THUMBNAIL)
        } catch (ex: Throwable) {
            log.trace(ex)
            showToast(ex, "ACTION_GET_CONTENT failed.")
        }
    }

    private var lastPostAttachment: PostAttachment? = null

    private fun uploadCustomThumbnail(srcList: ArrayList<GetContentResultEntry>?) {
        val src = srcList?.elementAtOrNull(0) ?: return

        val account = this@ActPost.account
        if (account == null) {
            showToast(false, R.string.account_select_please)
            return
        }

        val mime_type = getMimeType(src.uri, src.mimeType)
        if (mime_type?.isEmpty() != false) {
            showToast(false, R.string.mime_type_missing)
            return
        }

        val pa = lastPostAttachment
        if (pa == null || !attachment_list.contains(pa)) {
            showToast(true, "lost attachment information")
            return
        }

        TootTaskRunner(this).run(account, object : TootTask {

            @Suppress("BlockingMethodInNonBlockingContext")
            override suspend fun background(client: TootApiClient): TootApiResult? {
                try {
                    val (ti, tiResult) = TootInstance.get(client)
                    ti ?: return tiResult

                    val resizeConfig = ResizeConfig(ResizeType.SquarePixel, 400)

                    val opener = createOpener(src.uri, mime_type, resizeConfig)

                    val media_size_max = 1000000

                    val content_length = getStreamSize(true, opener.open())
                    if (content_length > media_size_max) {
                        return TootApiResult(
                            getString(
                                R.string.file_size_too_big,
                                media_size_max / 1000000
                            )
                        )
                    }

                    fun fixDocumentName(s: String): String {
                        val s_length = s.length
                        val m = """([^\x20-\x7f])""".asciiPattern().matcher(s)
                        m.reset()
                        val sb = StringBuilder(s_length)
                        var lastEnd = 0
                        while (m.find()) {
                            sb.append(s.substring(lastEnd, m.start()))
                            val escaped = m.groupEx(1)!!.encodeUTF8().encodeHex()
                            sb.append(escaped)
                            lastEnd = m.end()
                        }
                        if (lastEnd < s_length) sb.append(s.substring(lastEnd, s_length))
                        return sb.toString()
                    }

                    val fileName = fixDocumentName(getDocumentName(contentResolver, src.uri))

                    return if (account.isMisskey) {
                        opener.deleteTempFile()
                        TootApiResult("custom thumbnail is not supported on misskey account.")
                    } else {
                        val result = client.request(
                            "/api/v1/media/${pa.attachment?.id}",
                            MultipartBody.Builder()
                                .setType(MultipartBody.FORM)
                                .addFormDataPart(
                                    "thumbnail",
                                    fileName,
                                    object : RequestBody() {
                                        override fun contentType(): MediaType {
                                            return opener.mimeType.toMediaType()
                                        }

                                        @Throws(IOException::class)
                                        override fun contentLength(): Long {
                                            return content_length
                                        }

                                        @Throws(IOException::class)
                                        override fun writeTo(sink: BufferedSink) {
                                            opener.open().use { inData ->
                                                val tmp = ByteArray(4096)
                                                while (true) {
                                                    val r = inData.read(tmp, 0, tmp.size)
                                                    if (r <= 0) break
                                                    sink.write(tmp, 0, r)
                                                }
                                            }
                                        }
                                    }
                                )
                                .build().toPut()
                        )
                        opener.deleteTempFile()

                        val jsonObject = result?.jsonObject
                        if (jsonObject != null) {
                            val a = parseItem(::TootAttachment, ServiceType.MASTODON, jsonObject)
                            if (a == null) {
                                result.error = "TootAttachment.parse failed"
                            } else {
                                pa.attachment = a
                            }
                        }
                        result
                    }

                } catch (ex: Throwable) {
                    return TootApiResult(ex.withCaption("read failed."))
                }
            }

            override suspend fun handleResult(result: TootApiResult?) {
                showMediaAttachment()
                result?.error?.let { showToast(true, it) }
            }
        })
    }

    private fun deleteAttachment(pa: PostAttachment) {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_delete_attachment)
            .setPositiveButton(R.string.ok) { _, _ ->
                try {
                    attachment_list.remove(pa)
                } catch (ignored: Throwable) {
                }

                showMediaAttachment()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()

    }

    private fun editAttachmentDescription(pa: PostAttachment) {
        val a = pa.attachment
        if (a == null) {
            showToast(true, R.string.attachment_description_cant_edit_while_uploading)
            return
        }

        DlgTextInput.show(
            this,
            getString(R.string.attachment_description),
            a.description,
            callback = object : DlgTextInput.Callback {
                override fun onOK(dialog: Dialog, text: String) {
                    setAttachmentDescription(pa, dialog, text)
                }

                override fun onEmptyError() {
                    showToast(true, R.string.description_empty)
                }
            })
    }

    @SuppressLint("StaticFieldLeak")
    private fun setAttachmentDescription(pa: PostAttachment, dialog: Dialog, text: String) {

        val attachment_id = pa.attachment?.id ?: return

        TootTaskRunner(this).run(this@ActPost.account ?: return, object : TootTask {

            var new_attachment: TootAttachment? = null

            override suspend fun background(client: TootApiClient): TootApiResult? {
                val result = client.request(
                    "/api/v1/media/$attachment_id",
                    jsonObject {
                        put("description", text)
                    }
                        .toPutRequestBuilder()
                )
                new_attachment =
                    parseItem(::TootAttachment, ServiceType.MASTODON, result?.jsonObject)
                return result
            }

            override suspend fun handleResult(result: TootApiResult?) {
                if (result == null) return  // cancelled.

                val new_attachment = this.new_attachment
                if (new_attachment != null) {
                    pa.attachment = new_attachment
                    showMediaAttachment()

                    dialog.dismissSafe()

                } else {
                    showToast(true, result.error)
                }
            }
        })
    }

    private fun openAttachment() {

        if (attachment_list.size >= 4) {
            showToast(false, R.string.attachment_too_many)
            return
        }

        if (account == null) {
            showToast(false, R.string.account_select_please)
            return
        }

        val permissionCheck =
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            preparePermission()
            return
        }

        //		permissionCheck = ContextCompat.checkSelfPermission( this, Manifest.permission.CAMERA );
        //		if( permissionCheck != PackageManager.PERMISSION_GRANTED ){
        //			preparePermission();
        //			return;
        //		}

        val a = ActionsDialog()
        a.addAction(getString(R.string.pick_images)) {
            openAttachmentChooser(R.string.pick_images, "image/*", "video/*")
        }
        a.addAction(getString(R.string.pick_videos)) {
            openAttachmentChooser(R.string.pick_videos, "video/*")
        }
        a.addAction(getString(R.string.pick_audios)) {
            openAttachmentChooser(R.string.pick_audios, "audio/*")
        }
        a.addAction(getString(R.string.image_capture)) {
            performCamera()
        }
        a.addAction(getString(R.string.video_capture)) {
            performCapture(
                REQUEST_CODE_VIDEO,
                MediaStore.ACTION_VIDEO_CAPTURE,
                "can't open video capture app."
            )
        }
        a.addAction(getString(R.string.voice_capture)) {
            performCapture(
                REQUEST_CODE_SOUND,
                MediaStore.Audio.Media.RECORD_SOUND_ACTION,
                "can't open voice capture app."
            )
        }

        a.show(this, null)

    }

    private fun openAttachmentChooser(titleId: Int, vararg mimeTypes: String) {
        // SAFのIntentで開く
        try {
            val intent = intentGetContent(true, getString(titleId), mimeTypes)
            startActivityForResult(intent, REQUEST_CODE_ATTACHMENT_OLD)
        } catch (ex: Throwable) {
            log.trace(ex)
            showToast(ex, "ACTION_GET_CONTENT failed.")
        }
    }

    internal interface InputStreamOpener {

        val mimeType: String

        @Throws(IOException::class)
        fun open(): InputStream

        fun deleteTempFile()
    }

    private fun createOpener(
        uri: Uri,
        mime_type: String,
        resizeConfig: ResizeConfig
    ): InputStreamOpener {

        while (true) {
            try {

                // 画像の種別
                val is_jpeg = MIME_TYPE_JPEG == mime_type
                val is_png = MIME_TYPE_PNG == mime_type
                if (!is_jpeg && !is_png) {
                    log.d("createOpener: source is not jpeg or png")
                    break
                }

                val bitmap = createResizedBitmap(
                    this,
                    uri,
                    resizeConfig,
                    skipIfNoNeedToResizeAndRotate = true
                )
                if (bitmap != null) {
                    try {
                        val cache_dir = externalCacheDir
                        if (cache_dir == null) {
                            showToast(false, "getExternalCacheDir returns null.")
                            break
                        }

                        cache_dir.mkdir()

                        val temp_file = File(cache_dir, "tmp." + Thread.currentThread().id)
                        FileOutputStream(temp_file).use { os ->
                            if (is_jpeg) {
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os)
                            } else {
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                            }
                        }

                        return object : InputStreamOpener {

                            override val mimeType: String
                                get() = mime_type

                            @Throws(IOException::class)
                            override fun open(): InputStream {
                                return FileInputStream(temp_file)
                            }

                            override fun deleteTempFile() {
                                temp_file.delete()
                            }
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }

            } catch (ex: Throwable) {
                log.trace(ex)
                showToast(ex, "Resizing image failed.")
            }

            break
        }

        return object : InputStreamOpener {

            override val mimeType: String
                get() = mime_type

            @Throws(IOException::class)
            override fun open(): InputStream {
                return contentResolver.openInputStream(uri) ?: error("openInputStream returns null")
            }

            override fun deleteTempFile() {

            }
        }
    }

    private fun getMimeType(uri: Uri, mimeTypeArg: String?): String? {

        // image/j()pg だの image/j(e)pg だの、mime type を誤記するアプリがあまりに多い
        // クレームで消耗するのを減らすためにファイルヘッダを確認する
        if (mimeTypeArg == null || mimeTypeArg.startsWith("image/")) {
            val sv = findMimeTypeByFileHeader(contentResolver, uri)
            if (sv != null) return sv
        }

        // 既に引数で与えられてる
        if (mimeTypeArg?.isNotEmpty() == true) {
            return mimeTypeArg
        }

        // ContentResolverに尋ねる
        var sv = contentResolver.getType(uri)
        if (sv?.isNotEmpty() == true) return sv

        // gboardのステッカーではUriのクエリパラメータにmimeType引数がある
        sv = uri.getQueryParameter("mimeType")
        if (sv?.isNotEmpty() == true) return sv

        return null
    }

    private fun checkAttachments(srcList: ArrayList<GetContentResultEntry>?) {
        srcList?.forEach {
            addAttachment(it.uri, it.mimeType)
        }
    }

    private class AttachmentRequest(
        val account: SavedAccount,
        val pa: PostAttachment,
        val uri: Uri,
        val mimeType: String,
        val onUploadEnd: () -> Unit
    )

    private val attachment_queue = ConcurrentLinkedQueue<AttachmentRequest>()
    private var attachment_worker: AttachmentWorker? = null
    private var lastAttachmentAdd: Long = 0L
    private var lastAttachmentComplete: Long = 0L

    @SuppressLint("StaticFieldLeak")
    private fun addAttachment(
        uri: Uri,
        mimeTypeArg: String? = null,
        onUploadEnd: () -> Unit = {}
    ) {

        if (attachment_list.size >= 4) {
            showToast(false, R.string.attachment_too_many)
            return
        }

        val account = this@ActPost.account
        if (account == null) {
            showToast(false, R.string.account_select_please)
            return
        }

        val mime_type = getMimeType(uri, mimeTypeArg)
        if (mime_type?.isEmpty() != false) {
            showToast(false, R.string.mime_type_missing)
            return
        }

        val instance = TootInstance.getCached(account.apiHost.ascii)
        if (instance?.instanceType == InstanceType.Pixelfed) {
            if (in_reply_to_id != null) {
                showToast(true, R.string.pixelfed_does_not_allow_reply_with_media)
                return
            }
            if (!acceptable_mime_types_pixelfed.contains(mime_type)) {
                showToast(true, R.string.mime_type_not_acceptable, mime_type)
                return
            }
        } else {
            if (!acceptable_mime_types.contains(mime_type)) {
                showToast(true, R.string.mime_type_not_acceptable, mime_type)
                return
            }
        }

        app_state.attachment_list = this.attachment_list

        val pa = PostAttachment(this)
        attachment_list.add(pa)
        showMediaAttachment()

        // アップロード開始トースト(連発しない)
        val now = System.currentTimeMillis()
        if (now - lastAttachmentAdd >= 5000L) {
            showToast(false, R.string.attachment_uploading)
        }
        lastAttachmentAdd = now

        // マストドンは添付メディアをID順に表示するため
        // 画像が複数ある場合は一つずつ処理する必要がある
        // 投稿画面ごとに1スレッドだけ作成してバックグラウンド処理を行う
        attachment_queue.add(AttachmentRequest(account, pa, uri, mime_type, onUploadEnd))
        val oldWorker = attachment_worker
        if (oldWorker == null || !oldWorker.isAlive || oldWorker.isCancelled.get()) {
            oldWorker?.cancel()
            attachment_worker = AttachmentWorker()
        } else {
            oldWorker.notifyEx()
        }
    }

    inner class AttachmentWorker : WorkerBase() {

        internal val isCancelled = AtomicBoolean(false)

        override fun cancel() {
            isCancelled.set(true)
            notifyEx()
        }

        override suspend fun run() {
            try {
                while (!isCancelled.get()) {
                    val item = attachment_queue.poll()
                    if (item == null) {
                        waitEx(86400000L)
                        continue
                    }
                    val result = item.upload()
                    handler.post {
                        item.handleResult(result)
                    }
                }
            } catch (ex: Throwable) {
                log.trace(ex)
                log.e(ex, "AttachmentWorker")
            }
        }

        @Suppress("BlockingMethodInNonBlockingContext")
        private suspend fun AttachmentRequest.upload(): TootApiResult? {

            if (mimeType.isEmpty()) {
                return TootApiResult("mime_type is empty.")
            }

            try {
                val client = TootApiClient(this@ActPost, callback = object : TootApiCallback {
                    override val isApiCancelled: Boolean
                        get() = isCancelled.get()
                })

                client.account = account

                val (ti, tiResult) = TootInstance.get(client)
                ti ?: return tiResult

                if (ti.instanceType == InstanceType.Pixelfed) {
                    if (in_reply_to_id != null) {
                        return TootApiResult(getString(R.string.pixelfed_does_not_allow_reply_with_media))
                    }
                    if (!acceptable_mime_types_pixelfed.contains(mimeType)) {
                        return TootApiResult(getString(R.string.mime_type_not_acceptable, mimeType))
                    }
                }
                // 設定からリサイズ指定を読む
                val resizeConfig = account.getResizeConfig()

                val opener = createOpener(uri, mimeType, resizeConfig)

                val media_size_max = when {
                    mimeType.startsWith("video") || mimeType.startsWith("audio") ->
                        account.getMovieMaxBytes(ti)

                    else ->
                        account.getImageMaxBytes(ti)
                }

                val content_length = getStreamSize(true, opener.open())
                if (content_length > media_size_max) {
                    return TootApiResult(
                        getString(
                            R.string.file_size_too_big,
                            media_size_max / 1000000
                        )
                    )
                }

                fun fixDocumentName(s: String): String {
                    val s_length = s.length
                    val m = """([^\x20-\x7f])""".asciiPattern().matcher(s)
                    m.reset()
                    val sb = StringBuilder(s_length)
                    var lastEnd = 0
                    while (m.find()) {
                        sb.append(s.substring(lastEnd, m.start()))
                        val escaped = m.groupEx(1)!!.encodeUTF8().encodeHex()
                        sb.append(escaped)
                        lastEnd = m.end()
                    }
                    if (lastEnd < s_length) sb.append(s.substring(lastEnd, s_length))
                    return sb.toString()
                }

                val fileName = fixDocumentName(getDocumentName(contentResolver, uri))

                return if (account.isMisskey) {
                    val multipart_builder = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)

                    val apiKey = account.token_info?.string(TootApiClient.KEY_API_KEY_MISSKEY)
                    if (apiKey?.isNotEmpty() == true) {
                        multipart_builder.addFormDataPart("i", apiKey)
                    }

                    multipart_builder.addFormDataPart(
                        "file",
                        fileName,
                        object : RequestBody() {
                            override fun contentType(): MediaType {
                                return opener.mimeType.toMediaType()
                            }

                            @Throws(IOException::class)
                            override fun contentLength(): Long {
                                return content_length
                            }

                            @Throws(IOException::class)
                            override fun writeTo(sink: BufferedSink) {
                                opener.open().use { inData ->
                                    val tmp = ByteArray(4096)
                                    while (true) {
                                        val r = inData.read(tmp, 0, tmp.size)
                                        if (r <= 0) break
                                        sink.write(tmp, 0, r)
                                    }
                                }
                            }
                        }
                    )

                    val result = client.request(
                        "/api/drive/files/create",
                        multipart_builder.build().toPost()
                    )

                    opener.deleteTempFile()
                    onUploadEnd()

                    val jsonObject = result?.jsonObject
                    if (jsonObject != null) {
                        val a = parseItem(::TootAttachment, ServiceType.MISSKEY, jsonObject)
                        if (a == null) {
                            result.error = "TootAttachment.parse failed"
                        } else {
                            pa.attachment = a
                        }
                    }
                    result
                } else {
                    suspend fun postMedia(path: String) = client.request(
                        path,
                        MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart(
                                "file",
                                fileName,
                                object : RequestBody() {
                                    override fun contentType(): MediaType {
                                        return opener.mimeType.toMediaType()
                                    }

                                    @Throws(IOException::class)
                                    override fun contentLength(): Long {
                                        return content_length
                                    }

                                    @Throws(IOException::class)
                                    override fun writeTo(sink: BufferedSink) {
                                        opener.open().use { inData ->
                                            val tmp = ByteArray(4096)
                                            while (true) {
                                                val r = inData.read(tmp, 0, tmp.size)
                                                if (r <= 0) break
                                                sink.write(tmp, 0, r)
                                            }
                                        }
                                    }
                                }
                            )
                            .build().toPost()
                    )

                    suspend fun postV1() = postMedia("/api/v1/media")

                    suspend fun postV2(): TootApiResult? {
                        // 3.1.3未満は v1 APIを使う
                        if (!ti.versionGE(TootInstance.VERSION_3_1_3))
                            return postV1()

                        // v2 APIを試す
                        val result = postMedia("/api/v2/media")
                        val code = result?.response?.code // complete,or 4xx error
                        when {
                            // 404ならv1 APIにフォールバック
                            code == 404 -> return postV1()
                            // 202 accepted 以外はポーリングしない
                            code != 202 -> return result
                        }

                        // ポーリングして処理完了を待つ
                        val id = parseItem(
                            ::TootAttachment,
                            ServiceType.MASTODON,
                            result?.jsonObject
                        )
                            ?.id
                            ?: return TootApiResult("/api/v2/media did not return the media ID.")

                        var lastResponse = SystemClock.elapsedRealtime()
                        loop@ while (true) {

                            delay(1000L)
                            val r2 = client.request("/api/v1/media/$id")
                                ?: return null // cancelled

                            val now = SystemClock.elapsedRealtime()
                            when (r2.response?.code) {
                                // complete,or 4xx error
                                200, in 400 until 500 -> return r2

                                // continue to wait
                                206 -> lastResponse = now

                                // too many temporary error without 206 response.
                                else ->
                                    if (now - lastResponse >= 120000L)
                                        return TootApiResult("timeout.")
                            }
                        }
                    }

                    val result = postV2()
                    opener.deleteTempFile()
                    onUploadEnd()

                    val jsonObject = result?.jsonObject
                    if (jsonObject != null) {
                        val a = parseItem(::TootAttachment, ServiceType.MASTODON, jsonObject)
                        if (a == null) {
                            result.error = "TootAttachment.parse failed"
                        } else {
                            pa.attachment = a
                        }
                    }
                    result
                }

            } catch (ex: Throwable) {
                return TootApiResult(ex.withCaption("read failed."))
            }
        }

        private fun AttachmentRequest.handleResult(result: TootApiResult?) {

            if (pa.attachment == null) {
                pa.status = PostAttachment.STATUS_UPLOAD_FAILED
                if (result != null) {
                    showToast(
                        true,
                        "${result.error} ${result.response?.request?.method} ${result.response?.request?.url}"
                    )
                }
            } else {
                pa.status = PostAttachment.STATUS_UPLOADED
            }
            // 投稿中に画面回転があった場合、新しい画面のコールバックを呼び出す必要がある
            pa.callback?.onPostAttachmentComplete(pa)
        }
    }

    // 添付メディア投稿が完了したら呼ばれる
    override fun onPostAttachmentComplete(pa: PostAttachment) {
        if (!attachment_list.contains(pa)) {
            // この添付メディアはリストにない
            return
        }

        when (pa.status) {
            PostAttachment.STATUS_UPLOAD_FAILED -> {
                // アップロード失敗
                attachment_list.remove(pa)
                showMediaAttachment()
            }

            PostAttachment.STATUS_UPLOADED -> {
                val a = pa.attachment
                if (a != null) {
                    // アップロード完了

                    val now = System.currentTimeMillis()
                    if (now - lastAttachmentComplete >= 5000L) {
                        showToast(false, R.string.attachment_uploaded)
                    }
                    lastAttachmentComplete = now

                    if (Pref.bpAppendAttachmentUrlToContent(pref)) {
                        // 投稿欄の末尾に追記する
                        val selStart = etContent.selectionStart
                        val selEnd = etContent.selectionEnd
                        val e = etContent.editableText
                        val len = e.length
                        val last_char = if (len <= 0) ' ' else e[len - 1]
                        if (!CharacterGroup.isWhitespace(last_char.toInt())) {
                            e.append(" ").append(a.text_url)
                        } else {
                            e.append(a.text_url)
                        }
                        etContent.setSelection(selStart, selEnd)
                    }

                }

                showMediaAttachment()
            }

            else -> {
                // アップロード中…？
            }
        }
    }

    private fun performCamera() {
        try {
            val filename = System.currentTimeMillis().toString() + ".jpg"
            val values = ContentValues()
            values.put(MediaStore.Images.Media.TITLE, filename)
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            uriCameraImage =
                contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uriCameraImage)

            startActivityForResult(intent, REQUEST_CODE_CAMERA)
        } catch (ex: Throwable) {
            log.trace(ex)
            showToast(ex, "opening camera app failed.")
        }

    }

    private fun performCapture(requestCode: Int, action: String, errorCaption: String) {
        try {
            startActivityForResult(Intent(action), requestCode)
        } catch (ex: Throwable) {
            log.trace(ex)
            showToast(ex, errorCaption)
        }
    }

    private fun preparePermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                PERMISSION_REQUEST_CODE
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
                    openAttachment()
                }
            }
        }
    }

    private fun showVisibility() {
        btnVisibility.setImageResource(
            Styler.getVisibilityIconId(
                account?.isMisskey == true, visibility ?: TootVisibility.Public
            )
        )
    }

    private fun performVisibility() {
        val ti = account?.let { TootInstance.getCached(it.apiHost.ascii) }

        val list = when {
            account?.isMisskey == true ->
                arrayOf(
                    //	TootVisibility.WebSetting,
                    TootVisibility.Public,
                    TootVisibility.UnlistedHome,
                    TootVisibility.PrivateFollowers,
                    TootVisibility.LocalPublic,
                    TootVisibility.LocalHome,
                    TootVisibility.LocalFollowers,
                    TootVisibility.DirectSpecified,
                    TootVisibility.DirectPrivate
                )

            true == ti?.hasCapability(InstanceCapability.VisibilityMutual) ->
                arrayOf(
                    TootVisibility.WebSetting,
                    TootVisibility.Public,
                    TootVisibility.UnlistedHome,
                    TootVisibility.PrivateFollowers,
                    TootVisibility.Limited,
                    TootVisibility.Mutual,
                    TootVisibility.DirectSpecified
                )

            true == ti?.hasCapability(InstanceCapability.VisibilityLimited) ->
                arrayOf(
                    TootVisibility.WebSetting,
                    TootVisibility.Public,
                    TootVisibility.UnlistedHome,
                    TootVisibility.PrivateFollowers,
                    TootVisibility.Limited,
                    TootVisibility.DirectSpecified
                )

            else ->
                arrayOf(
                    TootVisibility.WebSetting,
                    TootVisibility.Public,
                    TootVisibility.UnlistedHome,
                    TootVisibility.PrivateFollowers,
                    TootVisibility.DirectSpecified
                )

        }
        val caption_list = list
            .map { Styler.getVisibilityCaption(this, account?.isMisskey == true, it) }
            .toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.choose_visibility)
            .setItems(caption_list) { _, which ->
                if (which in list.indices) {
                    visibility = list[which]
                    showVisibility()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()

    }

    ///////////////////////////////////////////////////////////////////////////////////////

    private fun performMore() {
        val dialog = ActionsDialog()

        dialog.addAction(getString(R.string.open_picker_emoji)) {
            post_helper.openEmojiPickerFromMore()
        }

        dialog.addAction(getString(R.string.clear_text)) {
            etContent.setText("")
            etContentWarning.setText("")
        }

        dialog.addAction(getString(R.string.clear_text_and_media)) {
            etContent.setText("")
            etContentWarning.setText("")
            attachment_list.clear()
            showMediaAttachment()
        }

        if (PostDraft.hasDraft()) dialog.addAction(getString(R.string.restore_draft)) {
            openDraftPicker()
        }

        dialog.addAction(getString(R.string.recommended_plugin)) {
            showRecommendedPlugin(null)
        }

        dialog.show(this, null)
    }

    ///////////////////////////////////////////////////////////////////////////////////////
    // post

    private fun performPost() {
        val account = this.account ?: return

        // アップロード中は投稿できない
        for (pa in attachment_list) {
            if (pa.status == PostAttachment.STATUS_UPLOADING) {
                showToast(false, R.string.media_attachment_still_uploading)
                return
            }
        }

        post_helper.content = etContent.text.toString().trim { it <= ' ' }

        fun copyEnqueteText() {
            val enquete_items = ArrayList<String>()
            for (et in list_etChoice) {
                enquete_items.add(et.text.toString().trim { it <= ' ' })
            }
            post_helper.enquete_items = enquete_items
        }

        fun getExpireSeconds(): Int {

            fun Double?.finiteOrZero(): Double = if (this?.isFinite() == true) this else 0.0

            val d = etExpireDays.text.toString().trim().toDoubleOrNull().finiteOrZero()
            val h = etExpireHours.text.toString().trim().toDoubleOrNull().finiteOrZero()
            val m = etExpireMinutes.text.toString().trim().toDoubleOrNull().finiteOrZero()

            return (d * 86400.0 + h * 3600.0 + m * 60.0).toInt()
        }

        when (spEnquete.selectedItemPosition) {
            1 -> {
                copyEnqueteText()
                post_helper.poll_type = TootPollsType.Mastodon
                post_helper.poll_expire_seconds = getExpireSeconds()
                post_helper.poll_hide_totals = cbHideTotals.isChecked
                post_helper.poll_multiple_choice = cbMultipleChoice.isChecked
            }

            2 -> {
                copyEnqueteText()
                post_helper.poll_type = TootPollsType.FriendsNico

            }

            else -> {
                post_helper.enquete_items = null
                post_helper.poll_type = null
            }
        }


        if (!cbContentWarning.isChecked) {
            post_helper.spoiler_text = null // nullはCWチェックなしを示す
        } else {
            post_helper.spoiler_text = etContentWarning.text.toString().trim { it <= ' ' }
        }

        post_helper.visibility = this.visibility ?: TootVisibility.Public
        post_helper.bNSFW = cbNSFW.isChecked

        post_helper.in_reply_to_id = this.in_reply_to_id

        post_helper.attachment_list = this.attachment_list

        post_helper.emojiMapCustom = App1.custom_emoji_lister.getMap(account)

        post_helper.redraft_status_id = redraft_status_id

        post_helper.useQuoteToot = cbQuote.isChecked

        post_helper.scheduledAt = timeSchedule

        post_helper.scheduledId = scheduledStatus?.id

        post_helper.post(account, callback = object : PostHelper.PostCompleteCallback {
            override fun onPostComplete(
                target_account: SavedAccount,
                status: TootStatus
            ) {
                val data = Intent()
                data.putExtra(EXTRA_POSTED_ACCT, target_account.acct.ascii)
                status.id.putTo(data, EXTRA_POSTED_STATUS_ID)
                redraft_status_id?.putTo(data, EXTRA_POSTED_REDRAFT_ID)
                status.in_reply_to_id?.putTo(data, EXTRA_POSTED_REPLY_ID)
                setResult(RESULT_OK, data)
                isPostComplete = true
                this@ActPost.finish()
            }

            override fun onScheduledPostComplete(target_account: SavedAccount) {
                showToast(false, getString(R.string.scheduled_status_sent))
                val data = Intent()
                data.putExtra(EXTRA_POSTED_ACCT, target_account.acct.ascii)
                setResult(RESULT_OK, data)
                isPostComplete = true
                this@ActPost.finish()
            }
        })
    }

    private fun showQuotedRenote() {
        cbQuote.visibility = if (in_reply_to_id != null) View.VISIBLE else View.GONE
    }

    private fun showReplyTo() {
        if (in_reply_to_id == null) {
            llReply.visibility = View.GONE
        } else {
            llReply.visibility = View.VISIBLE
            tvReplyTo.text = DecodeOptions(
                this@ActPost,
                linkHelper = account,
                short = true,
                decodeEmoji = true,
                mentionDefaultHostDomain = account ?: unknownHostAndDomain
            ).decodeHTML(in_reply_to_text)
            ivReply.setImageUrl(pref, Styler.calcIconRound(ivReply.layoutParams), in_reply_to_image)
        }
    }

    private fun removeReply() {
        in_reply_to_id = null
        in_reply_to_text = null
        in_reply_to_image = null
        in_reply_to_url = null
        showReplyTo()
        showQuotedRenote()
    }

    private fun saveDraft() {
        val content = etContent.text.toString()
        val content_warning =
            if (cbContentWarning.isChecked) etContentWarning.text.toString() else ""

        val isEnquete = spEnquete.selectedItemPosition > 0

        val str_choice = arrayOf(
            if (isEnquete) list_etChoice[0].text.toString() else "",
            if (isEnquete) list_etChoice[1].text.toString() else "",
            if (isEnquete) list_etChoice[2].text.toString() else "",
            if (isEnquete) list_etChoice[3].text.toString() else ""
        )

        var hasContent = false
        if (content.isNotBlank()) hasContent = true
        if (content_warning.isNotBlank()) hasContent = true
        for (s in str_choice) {
            if (s.isNotBlank()) hasContent = true
        }
        if (!hasContent) {
            log.d("saveDraft: dont save empty content")
            return
        }

        try {
            val tmp_attachment_list = attachment_list
                .mapNotNull { it.attachment?.encodeJson() }
                .toJsonArray()

            val json = JsonObject()
            json[DRAFT_CONTENT] = content
            json[DRAFT_CONTENT_WARNING] = content_warning
            json[DRAFT_CONTENT_WARNING_CHECK] = cbContentWarning.isChecked
            json[DRAFT_NSFW_CHECK] = cbNSFW.isChecked
            visibility?.let { json.put(DRAFT_VISIBILITY, it.id.toString()) }
            json[DRAFT_ACCOUNT_DB_ID] = account?.db_id ?: -1L
            json[DRAFT_ATTACHMENT_LIST] = tmp_attachment_list
            in_reply_to_id?.putTo(json, DRAFT_REPLY_ID)
            json[DRAFT_REPLY_TEXT] = in_reply_to_text
            json[DRAFT_REPLY_IMAGE] = in_reply_to_image
            json[DRAFT_REPLY_URL] = in_reply_to_url

            json[DRAFT_QUOTE] = cbQuote.isChecked

            // deprecated. but still used in old draft.
            // json.put(DRAFT_IS_ENQUETE, isEnquete)

            json[DRAFT_POLL_TYPE] = spEnquete.selectedItemPosition.toPollTypeString()

            json[DRAFT_POLL_MULTIPLE] = cbMultipleChoice.isChecked
            json[DRAFT_POLL_HIDE_TOTALS] = cbHideTotals.isChecked
            json[DRAFT_POLL_EXPIRE_DAY] = etExpireDays.text.toString()
            json[DRAFT_POLL_EXPIRE_HOUR] = etExpireHours.text.toString()
            json[DRAFT_POLL_EXPIRE_MINUTE] = etExpireMinutes.text.toString()

            json[DRAFT_ENQUETE_ITEMS] = str_choice.toJsonArray()

            PostDraft.save(System.currentTimeMillis(), json)

        } catch (ex: Throwable) {
            log.trace(ex)
        }

    }

    // poll type string to spinner index
    private fun String?.toPollTypeIndex() = when (this) {
        "mastodon" -> 1
        "friendsNico" -> 2
        else -> 0
    }

    private fun Int?.toPollTypeString() = when (this) {
        1 -> "mastodon"
        2 -> "friendsNico"
        else -> ""
    }

    private fun openDraftPicker() {

        DlgDraftPicker().open(this) { draft -> restoreDraft(draft) }

    }

    private fun restoreDraft(draft: JsonObject) {

        val list_warning = ArrayList<String>()
        var target_account: SavedAccount? = null

        runWithProgress(
            "restore from draft",
            { progress ->
                fun isTaskCancelled() = !this.coroutineContext.isActive

                var content = draft.string(DRAFT_CONTENT) ?: ""
                val account_db_id = draft.long(DRAFT_ACCOUNT_DB_ID) ?: -1L
                val tmp_attachment_list =
                    draft.jsonArray(DRAFT_ATTACHMENT_LIST)?.objectList()?.toMutableList()

                val account = SavedAccount.loadAccount(this@ActPost, account_db_id)
                if (account == null) {
                    list_warning.add(getString(R.string.account_in_draft_is_lost))
                    try {
                        if (tmp_attachment_list != null) {
                            // 本文からURLを除去する
                            tmp_attachment_list.forEach {
                                val text_url = TootAttachment.decodeJson(it).text_url
                                if (text_url?.isNotEmpty() == true) {
                                    content = content.replace(text_url, "")
                                }
                            }
                            tmp_attachment_list.clear()
                            draft[DRAFT_ATTACHMENT_LIST] = tmp_attachment_list.toJsonArray()
                            draft[DRAFT_CONTENT] = content
                            draft.remove(DRAFT_REPLY_ID)
                            draft.remove(DRAFT_REPLY_TEXT)
                            draft.remove(DRAFT_REPLY_IMAGE)
                            draft.remove(DRAFT_REPLY_URL)
                        }
                    } catch (ignored: JsonException) {
                    }

                    return@runWithProgress "OK"
                }

                target_account = account

                // アカウントがあるなら基本的にはすべての情報を復元できるはずだが、いくつか確認が必要だ
                val api_client = TootApiClient(this@ActPost, callback = object : TootApiCallback {

                    override val isApiCancelled: Boolean
                        get() = isTaskCancelled()

                    override suspend fun publishApiProgress(s: String) {
                        progress.setMessageEx(s)
                    }
                })

                api_client.account = account

                if (in_reply_to_id != null) {
                    val result = api_client.request("/api/v1/statuses/$in_reply_to_id")
                    if (isTaskCancelled()) return@runWithProgress null
                    val jsonObject = result?.jsonObject
                    if (jsonObject == null) {
                        list_warning.add(getString(R.string.reply_to_in_draft_is_lost))
                        draft.remove(DRAFT_REPLY_ID)
                        draft.remove(DRAFT_REPLY_TEXT)
                        draft.remove(DRAFT_REPLY_IMAGE)
                    }
                }
                try {
                    if (tmp_attachment_list != null) {
                        // 添付メディアの存在確認
                        var isSomeAttachmentRemoved = false
                        val it = tmp_attachment_list.iterator()
                        while (it.hasNext()) {
                            if (isTaskCancelled()) return@runWithProgress null
                            val ta = TootAttachment.decodeJson(it.next())
                            if (check_exist(ta.url)) continue
                            it.remove()
                            isSomeAttachmentRemoved = true
                            // 本文からURLを除去する
                            val text_url = ta.text_url
                            if (text_url?.isNotEmpty() == true) {
                                content = content.replace(text_url, "")
                            }
                        }
                        if (isSomeAttachmentRemoved) {
                            list_warning.add(getString(R.string.attachment_in_draft_is_lost))
                            draft[DRAFT_ATTACHMENT_LIST] = tmp_attachment_list.toJsonArray()
                            draft[DRAFT_CONTENT] = content
                        }
                    }
                } catch (ex: JsonException) {
                    log.trace(ex)
                }

                "OK"
            },
            { result ->
                // cancelled.
                if (result == null) return@runWithProgress

                val content = draft.string(DRAFT_CONTENT) ?: ""
                val content_warning = draft.string(DRAFT_CONTENT_WARNING) ?: ""
                val content_warning_checked = draft.optBoolean(DRAFT_CONTENT_WARNING_CHECK)
                val nsfw_checked = draft.optBoolean(DRAFT_NSFW_CHECK)
                val tmp_attachment_list = draft.jsonArray(DRAFT_ATTACHMENT_LIST)
                val reply_id = EntityId.from(draft, DRAFT_REPLY_ID)
                val reply_text = draft.string(DRAFT_REPLY_TEXT)
                val reply_image = draft.string(DRAFT_REPLY_IMAGE)
                val reply_url = draft.string(DRAFT_REPLY_URL)
                val draft_visibility = TootVisibility
                    .parseSavedVisibility(draft.string(DRAFT_VISIBILITY))

                val evEmoji = DecodeOptions(
                    this@ActPost,
                    decodeEmoji = true
                ).decodeEmoji(content)
                etContent.setText(evEmoji)
                etContent.setSelection(evEmoji.length)
                etContentWarning.setText(content_warning)
                etContentWarning.setSelection(content_warning.length)
                cbContentWarning.isChecked = content_warning_checked
                cbNSFW.isChecked = nsfw_checked
                if (draft_visibility != null) this@ActPost.visibility = draft_visibility

                cbQuote.isChecked = draft.optBoolean(DRAFT_QUOTE)

                val sv = draft.string(DRAFT_POLL_TYPE)
                if (sv != null) {
                    spEnquete.setSelection(sv.toPollTypeIndex())
                } else {
                    // old draft
                    val bv = draft.optBoolean(DRAFT_IS_ENQUETE, false)
                    spEnquete.setSelection(if (bv) 2 else 0)
                }

                cbMultipleChoice.isChecked = draft.optBoolean(DRAFT_POLL_MULTIPLE)
                cbHideTotals.isChecked = draft.optBoolean(DRAFT_POLL_HIDE_TOTALS)
                etExpireDays.setText(draft.optString(DRAFT_POLL_EXPIRE_DAY, "1"))
                etExpireHours.setText(draft.optString(DRAFT_POLL_EXPIRE_HOUR, ""))
                etExpireMinutes.setText(draft.optString(DRAFT_POLL_EXPIRE_MINUTE, ""))

                val array = draft.jsonArray(DRAFT_ENQUETE_ITEMS)
                if (array != null) {
                    var src_index = 0
                    for (et in list_etChoice) {
                        if (src_index < array.size) {
                            et.setText(array.optString(src_index))
                            ++src_index
                        } else {
                            et.setText("")
                        }
                    }
                }

                if (target_account != null) selectAccount(target_account)

                if (tmp_attachment_list?.isNotEmpty() == true) {
                    attachment_list.clear()
                    tmp_attachment_list.forEach {
                        if (it !is JsonObject) return@forEach
                        val pa = PostAttachment(TootAttachment.decodeJson(it))
                        attachment_list.add(pa)
                    }
                }

                if (reply_id != null) {
                    in_reply_to_id = reply_id
                    in_reply_to_text = reply_text
                    in_reply_to_image = reply_image
                    in_reply_to_url = reply_url
                }

                updateContentWarning()
                showMediaAttachment()
                showVisibility()
                updateTextCount()
                showReplyTo()
                showEnquete()
                showQuotedRenote()

                if (list_warning.isNotEmpty()) {
                    val sb = StringBuilder()
                    for (s in list_warning) {
                        if (sb.isNotEmpty()) sb.append("\n")
                        sb.append(s)
                    }
                    AlertDialog.Builder(this@ActPost)
                        .setMessage(sb)
                        .setNeutralButton(R.string.close, null)
                        .show()
                }
            }
        )
    }

    private fun prepareMushroomText(et: EditText): String {
        mushroom_start = et.selectionStart
        mushroom_end = et.selectionEnd
        return if (mushroom_end > mushroom_start) {
            et.text.toString().substring(mushroom_start, mushroom_end)
        } else {
            ""
        }
    }

    private fun applyMushroomText(et: EditText, text: String) {
        val src = et.text.toString()
        if (mushroom_start > src.length) mushroom_start = src.length
        if (mushroom_end > src.length) mushroom_end = src.length

        val sb = StringBuilder()
        sb.append(src.substring(0, mushroom_start))
        // int new_sel_start = sb.length();
        sb.append(text)
        val new_sel_end = sb.length
        sb.append(src.substring(mushroom_end))
        et.setText(sb)
        et.setSelection(new_sel_end, new_sel_end)
    }

    private fun openMushroom() {
        try {
            var text: String? = null
            when {
                etContentWarning.hasFocus() -> {
                    mushroom_input = 1
                    text = prepareMushroomText(etContentWarning)
                }

                etContent.hasFocus() -> {
                    mushroom_input = 0
                    text = prepareMushroomText(etContent)
                }

                else -> for (i in 0..3) {
                    if (list_etChoice[i].hasFocus()) {
                        mushroom_input = i + 2
                        text = prepareMushroomText(list_etChoice[i])
                    }
                }
            }
            if (text == null) {
                mushroom_input = 0
                text = prepareMushroomText(etContent)
            }

            val intent = Intent("com.adamrocker.android.simeji.ACTION_INTERCEPT")
            intent.addCategory("com.adamrocker.android.simeji.REPLACE")
            intent.putExtra("replace_key", text)

            // Create intent to show chooser
            val chooser = Intent.createChooser(intent, getString(R.string.select_plugin))

            // Verify the intent will resolve to at least one activity
            if (intent.resolveActivity(packageManager) == null) {
                showRecommendedPlugin(getString(R.string.plugin_not_installed))
                return
            }
            startActivityForResult(chooser, REQUEST_CODE_MUSHROOM)

        } catch (ex: Throwable) {
            log.trace(ex)
            showRecommendedPlugin(getString(R.string.plugin_not_installed))
        }

    }

    private fun applyMushroomResult(text: String) {
        when (mushroom_input) {
            0 -> applyMushroomText(etContent, text)
            1 -> applyMushroomText(etContentWarning, text)
            else -> for (i in 0..3) {
                if (mushroom_input == i + 2) {
                    applyMushroomText(list_etChoice[i], text)
                }
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun showRecommendedPlugin(title: String?) {

        val res_id = when (getString(R.string.language_code)) {
            "ja" -> R.raw.recommended_plugin_ja
            "fr" -> R.raw.recommended_plugin_fr
            else -> R.raw.recommended_plugin_en
        }

        this.loadRawResource(res_id).let { data ->
            val text = data.decodeUTF8()
            val viewRoot = layoutInflater.inflate(R.layout.dlg_plugin_missing, null, false)

            val tvText = viewRoot.findViewById<TextView>(R.id.tvText)

            val sv = DecodeOptions(
                this@ActPost,
                linkHelper = LinkHelper.unknown,
            ).decodeHTML(text)

            tvText.text = sv
            tvText.movementMethod = LinkMovementMethod.getInstance()

            val tvTitle = viewRoot.findViewById<TextView>(R.id.tvTitle)
            if (title?.isEmpty() != false) {
                tvTitle.visibility = View.GONE
            } else {
                tvTitle.text = title
            }

            AlertDialog.Builder(this)
                .setView(viewRoot)
                .setCancelable(true)
                .setNeutralButton(R.string.close, null)
                .show()

        }

    }

    private fun showEnquete() {
        val i = spEnquete.selectedItemPosition
        llEnquete.vg(i != 0)
        llExpire.vg(i == 1)
        cbHideTotals.vg(i == 1)
        cbMultipleChoice.vg(i == 1)
    }

    private val commitContentListener =
        InputConnectionCompat.OnCommitContentListener { inputContentInfo: InputContentInfoCompat,
                                                        flags: Int,
                                                        _: Bundle? ->

            // Intercepts InputConnection#commitContent API calls.
            // - inputContentInfo : content to be committed
            // - flags : {@code 0} or {@link #INPUT_CONTENT_GRANT_READ_URI_PERMISSION}
            // - opts : optional bundle data. This can be {@code null}
            // return
            // - true if this request is accepted by the application,
            //   no matter if the request is already handled or still being handled in background.
            // - false to use the default implementation

            // read and display inputContentInfo asynchronously

            if (Build.VERSION.SDK_INT >= 25
                && flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION != 0
            ) {
                try {
                    inputContentInfo.requestPermission()
                } catch (e: Exception) {
                    return@OnCommitContentListener false // return false if failed
                }
            }

            addAttachment(inputContentInfo.contentUri) {
                inputContentInfo.releasePermission()
            }

            true
        }

    private fun showSchedule() {
        tvSchedule.text = when (timeSchedule) {
            0L -> getString(R.string.unspecified)
            else -> TootStatus.formatTime(this, timeSchedule, true)
        }
    }

    private fun performSchedule() {
        DlgDateTime(this).open(timeSchedule) { t ->
            timeSchedule = t
            showSchedule()
        }
    }

    private fun resetSchedule() {
        timeSchedule = 0L
        showSchedule()
    }
}
