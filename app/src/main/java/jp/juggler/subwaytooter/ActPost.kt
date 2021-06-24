package jp.juggler.subwaytooter

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import jp.juggler.subwaytooter.action.saveWindowSize
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.*
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.span.MyClickableSpanHandler
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.*
import jp.juggler.subwaytooter.view.ActPostRootLinearLayout
import jp.juggler.subwaytooter.view.MyEditText
import jp.juggler.subwaytooter.view.MyNetworkImageView
import jp.juggler.util.*
import kotlinx.coroutines.Job
import okhttp3.Request
import okhttp3.internal.closeQuietly
import ru.gildor.coroutines.okhttp.await
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

class ActPost : AppCompatActivity(),
    View.OnClickListener,
    PostAttachment.Callback,
    MyClickableSpanHandler, AttachmentPicker.Callback {

    companion object {
        internal val log = LogCategory("ActPost")

        var refActPost: WeakReference<ActPost>? = null

        internal const val EXTRA_POSTED_ACCT = "posted_acct"
        internal const val EXTRA_POSTED_STATUS_ID = "posted_status_id"
        internal const val EXTRA_POSTED_REPLY_ID = "posted_reply_id"
        internal const val EXTRA_POSTED_REDRAFT_ID = "posted_redraft_id"
        internal const val EXTRA_MULTI_WINDOW = "multiWindow"

        internal const val KEY_ACCOUNT_DB_ID = "account_db_id"
        internal const val KEY_REPLY_STATUS = "reply_status"
        internal const val KEY_REDRAFT_STATUS = "redraft_status"
        internal const val KEY_INITIAL_TEXT = "initial_text"
        internal const val KEY_SHARED_INTENT = "sent_intent"
        internal const val KEY_QUOTE = "quote"
        internal const val KEY_SCHEDULED_STATUS = "scheduled_status"

        internal const val KEY_ATTACHMENT_LIST = "attachment_list"
        internal const val KEY_IN_REPLY_TO_ID = "in_reply_to_id"
        internal const val KEY_IN_REPLY_TO_TEXT = "in_reply_to_text"
        internal const val KEY_IN_REPLY_TO_IMAGE = "in_reply_to_image"

        const val STATE_ALL = "all"

        /////////////////////////////////////////////////

        fun createIntent(
            activity: Activity,

            accountDbId: Long,

            multiWindowMode: Boolean,

            // 再編集する投稿。アカウントと同一のタンスであること
            redraftStatus: TootStatus? = null,

            // 返信対象の投稿。同一タンス上に同期済みであること
            replyStatus: TootStatus? = null,

            //初期テキスト
            initialText: String? = null,

            // 外部アプリから共有されたインテント
            sharedIntent: Intent? = null,

            // 返信ではなく引用トゥートを作成する
            quote: Boolean = false,

            //(Mastodon) 予約投稿の編集
            scheduledStatus: TootScheduled? = null,

            ) = Intent(activity, ActPost::class.java).apply {

            putExtra(EXTRA_MULTI_WINDOW, multiWindowMode)

            putExtra(KEY_ACCOUNT_DB_ID, accountDbId)

            if (redraftStatus != null) {
                putExtra(KEY_REDRAFT_STATUS, redraftStatus.json.toString())
            }

            if (replyStatus != null) {
                putExtra(KEY_REPLY_STATUS, replyStatus.json.toString())
                putExtra(KEY_QUOTE, quote)
            }

            if (initialText != null) {
                putExtra(KEY_INITIAL_TEXT, initialText)
            }

            if (sharedIntent != null) {
                putExtra(KEY_SHARED_INTENT, sharedIntent)
            }

            if (scheduledStatus != null) {
                putExtra(KEY_SCHEDULED_STATUS, scheduledStatus.src.toString())
            }
        }

        internal suspend fun checkExist(url: String?): Boolean {
            if (url?.isEmpty() != false) return false
            try {
                val request = Request.Builder().url(url).build()
                val call = App1.ok_http_client.newCall(request)
                val response = call.await()
                try {
                    if (response.isSuccessful) return true
                    log.e(TootApiClient.formatResponse(response, "check_exist failed."))
                } finally {
                    response.closeQuietly()
                }
            } catch (ex: Throwable) {
                log.trace(ex)
            }
            return false
        }

        fun Double?.finiteOrZero(): Double = if (this?.isFinite() == true) this else 0.0

        // poll type string to spinner index
        fun String?.toPollTypeIndex() = when (this) {
            "mastodon" -> 1
            "friendsNico" -> 2
            else -> 0
        }

        fun Int?.toPollTypeString() = when (this) {
            1 -> "mastodon"
            2 -> "friendsNico"
            else -> ""
        }
    }

    lateinit var btnAccount: Button
    lateinit var btnVisibility: ImageButton
    lateinit var btnAttachment: ImageButton
    lateinit var btnPost: ImageButton
    lateinit var llAttachment: View
    lateinit var ivMedia: List<MyNetworkImageView>
    lateinit var cbNSFW: CheckBox
    lateinit var cbContentWarning: CheckBox
    lateinit var etContentWarning: MyEditText
    lateinit var etContent: MyEditText

    lateinit var cbQuote: CheckBox

    lateinit var spPollType: Spinner
    lateinit var llEnquete: View
    lateinit var etChoices: List<MyEditText>

    lateinit var cbMultipleChoice: CheckBox
    lateinit var cbHideTotals: CheckBox
    lateinit var llExpire: LinearLayout
    lateinit var etExpireDays: EditText
    lateinit var etExpireHours: EditText
    lateinit var etExpireMinutes: EditText

    lateinit var tvCharCount: TextView
    internal lateinit var handler: Handler
    lateinit var formRoot: ActPostRootLinearLayout

    lateinit var llReply: View
    lateinit var tvReplyTo: TextView
    lateinit var ivReply: MyNetworkImageView
    lateinit var scrollView: ScrollView

    lateinit var tvSchedule: TextView
    lateinit var ibSchedule: ImageButton
    lateinit var ibScheduleReset: ImageButton

    internal lateinit var pref: SharedPreferences
    internal lateinit var appState: AppState
    lateinit var attachmentUploader: AttachmentUploader
    lateinit var attachmentPicker: AttachmentPicker
    lateinit var completionHelper: CompletionHelper

    ///////////////////////////////////////////////////

    var states = ActPostStates()

    internal var account: SavedAccount? = null

    var attachmentList = ArrayList<PostAttachment>()
    var isPostComplete: Boolean = false

    internal var density: Float = 0f

    var accountList: ArrayList<SavedAccount> = ArrayList()

    var scheduledStatus: TootScheduled? = null

    // key is SavedAccount.acctAscii
    val featuredTagCache = ConcurrentHashMap<String, FeaturedTagCache>()

    var jobFeaturedTag: WeakReference<Job>? = null
    var jobMaxCharCount: WeakReference<Job>? = null

    var paThumbnailTarget: PostAttachment? = null

    val scrollListener: ViewTreeObserver.OnScrollChangedListener =
        ViewTreeObserver.OnScrollChangedListener { completionHelper.onScrollChanged() }

    val isMultiWindowPost: Boolean
        get() = intent.getBooleanExtra(EXTRA_MULTI_WINDOW, false)

    val arMushroom = activityResultHandler { ar ->
        if (ar?.resultCode == RESULT_OK) {
            ar.data?.getStringExtra("replace_key")
                ?.let { text ->
                    when (states.mushroomInput) {
                        0 -> applyMushroomText(etContent, text)
                        1 -> applyMushroomText(etContentWarning, text)
                        else -> for (i in 0..3) {
                            if (states.mushroomInput == i + 2) {
                                applyMushroomText(etChoices[i], text)
                            }
                        }
                    }
                }
        }
    }

    val commitContentListener =
        InputConnectionCompat.OnCommitContentListener {
                inputContentInfo: InputContentInfoCompat,
                flags: Int,
                _: Bundle?,
            ->
            // Intercepts InputConnection#commitContent API calls.
            // - inputContentInfo : content to be committed
            // - flags : {@code 0} or {@link #INPUT_CONTENT_GRANT_READ_URI_PERMISSION}
            // - opts : optional bundle data. This can be {@code null}
            // return
            // - true if this request is accepted by the application,
            //   no matter if the request is already handled or still being handled in background.
            // - false to use the default implementation

            // read and display inputContentInfo asynchronously

            if (Build.VERSION.SDK_INT >= 25 &&
                flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION != 0
            ) {
                try {
                    inputContentInfo.requestPermission()
                } catch (ignored: Exception) {
                    // return false if failed
                    return@OnCommitContentListener false
                }
            }

            addAttachment(inputContentInfo.contentUri) {
                inputContentInfo.releasePermission()
            }

            true
        }

    ////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isMultiWindowPost) ActMain.refActMain?.get()?.closeList?.add(WeakReference(this))
        App1.setActivityTheme(this, noActionBar = true)
        appState = App1.getAppState(this)
        handler = appState.handler
        pref = appState.pref
        attachmentUploader = AttachmentUploader(this, handler)
        attachmentPicker = AttachmentPicker(this, this)
        density = resources.displayMetrics.density
        arMushroom.register(this, log)

        initUI()

        when (savedInstanceState) {
            null -> updateText(intent, confirmed = true, saveDraft = false)
            else -> restoreState(savedInstanceState)
        }
    }

    override fun onDestroy() {
        completionHelper.onDestroy()
        attachmentUploader.onActivityDestroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        showContentWarningEnabled()
        showMediaAttachment()
        showVisibility()
        updateTextCount()
        showReplyTo()
        showPoll()
        showQuotedRenote()
    }

    override fun onResume() {
        super.onResume()
        refActPost = WeakReference(this)
    }

    override fun onPause() {
        super.onPause()
        // 編集中にホーム画面を押したり他アプリに移動する場合は下書きを保存する
        // やや過剰な気がするが、自アプリに戻ってくるときにランチャーからアイコンタップされると
        // メイン画面より上にあるアクティビティはすべて消されてしまうので
        // このタイミングで保存するしかない
        if (!isPostComplete) saveDraft()
    }

    override fun onBackPressed() {
        saveDraft()
        super.onBackPressed()
    }

    override fun onClick(v: View) {
        refActPost = WeakReference(this)
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
            R.id.btnEmojiPicker -> completionHelper.openEmojiPickerFromMore()
            R.id.btnFeaturedTag -> completionHelper.openFeaturedTagList(
                featuredTagCache[account?.acct?.ascii ?: ""]?.list
            )
            R.id.ibSchedule -> performSchedule()
            R.id.ibScheduleReset -> resetSchedule()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when {
            super.onKeyDown(keyCode, event) -> true
            event == null -> false
            else -> event.isCtrlPressed
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val rv = super.onKeyUp(keyCode, event)
        if (event?.isCtrlPressed == true) {
            log.d("onKeyUp code=$keyCode rv=$rv")
            when (keyCode) {
                KeyEvent.KEYCODE_T -> btnPost.performClick()
            }
            return true
        }
        return rv
    }

    override fun onMyClickableSpanClicked(viewClicked: View, span: MyClickableSpan) {
        openBrowser(span.linkInfo.url)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        attachmentPicker.onRequestPermissionsResult(requestCode, permissions, grantResults)
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onPickAttachment(uri: Uri, mimeType: String?) {
        addAttachment(uri, mimeType)
    }

    override fun onPostAttachmentComplete(pa: PostAttachment) {
        onPostAttachmentCompleteImpl(pa)
    }

    override fun onPickCustomThumbnail(src: GetContentResultEntry) {
        onPickCustomThumbnailImpl(src)
    }

    fun initUI() {
        setContentView(R.layout.act_post)
        App1.initEdgeToEdge(this)

        if (PrefB.bpPostButtonBarTop(pref)) {
            val bar = findViewById<View>(R.id.llFooterBar)
            val parent = bar.parent as ViewGroup
            parent.removeView(bar)
            parent.addView(bar, 0)
        }

        if (!isMultiWindowPost) {
            Styler.fixHorizontalMargin(findViewById(R.id.scrollView))
            Styler.fixHorizontalMargin(findViewById(R.id.llFooterBar))
        }

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

        formRoot.callbackOnSizeChanged = { _, _, _, _ ->
            if (Build.VERSION.SDK_INT >= 24 && isMultiWindowPost) saveWindowSize()
            // ビューのw,hはシステムバーその他を含まないので使わない
        }

        // https://github.com/tateisu/SubwayTooter/issues/123
        // 早い段階で指定する必要がある
        etContent.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        etContent.imeOptions = EditorInfo.IME_ACTION_NONE

        cbQuote = findViewById(R.id.cbQuote)

        spPollType = findViewById<Spinner>(R.id.spEnquete).apply {
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
                    showPoll()
                    updateTextCount()
                }

                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    showPoll()
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

        etChoices = listOf(
            findViewById(R.id.etChoice1),
            findViewById(R.id.etChoice2),
            findViewById(R.id.etChoice3),
            findViewById(R.id.etChoice4)
        )

        tvCharCount = findViewById(R.id.tvCharCount)

        llReply = findViewById(R.id.llReply)
        tvReplyTo = findViewById(R.id.tvReplyTo)
        ivReply = findViewById(R.id.ivReply)

        tvSchedule = findViewById(R.id.tvSchedule)
        ibSchedule = findViewById(R.id.ibSchedule)
        ibScheduleReset = findViewById(R.id.ibScheduleReset)

        arrayOf(
            ibSchedule,
            ibScheduleReset,
            btnAccount,
            btnVisibility,
            btnAttachment,
            btnPost,
            findViewById(R.id.btnRemoveReply),
            findViewById(R.id.btnFeaturedTag),
            findViewById(R.id.btnPlugin),
            findViewById(R.id.btnEmojiPicker),
            findViewById(R.id.btnMore),
        ).forEach { it.setOnClickListener(this) }

        ivMedia.forEach { it.setOnClickListener(this) }

        cbContentWarning.setOnCheckedChangeListener { _, _ -> showContentWarningEnabled() }

        completionHelper = CompletionHelper(this, pref, appState.handler)
        completionHelper.attachEditText(formRoot, etContent, false, object : CompletionHelper.Callback2 {
            override fun onTextUpdate() {
                updateTextCount()
            }

            override fun canOpenPopup(): Boolean = true
        })

        val textWatcher: TextWatcher = object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
            }

            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
            }

            override fun afterTextChanged(editable: Editable) {
                updateTextCount()
            }
        }

        etContentWarning.addTextChangedListener(textWatcher)

        for (et in etChoices) {
            et.addTextChangedListener(textWatcher)
        }

        scrollView.viewTreeObserver.addOnScrollChangedListener(scrollListener)

        etContent.contentMineTypeArray = AttachmentUploader.acceptableMimeTypes.toTypedArray()
        etContent.commitContentListener = commitContentListener
    }
}
