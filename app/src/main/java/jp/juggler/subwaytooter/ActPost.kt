package jp.juggler.subwaytooter

import android.content.Context
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
import jp.juggler.subwaytooter.action.saveWindowSize
import jp.juggler.subwaytooter.actpost.*
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.databinding.ActPostBinding
import jp.juggler.subwaytooter.dialog.*
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.span.*
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.*
import jp.juggler.subwaytooter.view.MyEditText
import jp.juggler.subwaytooter.view.MyNetworkImageView
import jp.juggler.util.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import java.lang.ref.WeakReference
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap

class ActPost : AppCompatActivity(),
    View.OnClickListener,
    PostAttachment.Callback,
    MyClickableSpanHandler, AttachmentPicker.Callback {

    companion object {
        private val log = LogCategory("ActPost")

        var refActPost: WeakReference<ActPost>? = null

        const val EXTRA_POSTED_ACCT = "posted_acct"
        const val EXTRA_POSTED_STATUS_ID = "posted_status_id"
        const val EXTRA_POSTED_REPLY_ID = "posted_reply_id"
        const val EXTRA_POSTED_REDRAFT_ID = "posted_redraft_id"
        const val EXTRA_MULTI_WINDOW = "multiWindow"

        const val KEY_ACCOUNT_DB_ID = "account_db_id"
        const val KEY_REPLY_STATUS = "reply_status"
        const val KEY_REDRAFT_STATUS = "redraft_status"
        const val KEY_EDIT_STATUS = "edit_status"
        const val KEY_INITIAL_TEXT = "initial_text"
        const val KEY_SHARED_INTENT = "sent_intent"
        const val KEY_QUOTE = "quote"
        const val KEY_SCHEDULED_STATUS = "scheduled_status"

        const val STATE_ALL = "all"

        /////////////////////////////////////////////////

        fun createIntent(
            context: Context,
            accountDbId: Long,
            multiWindowMode: Boolean,
            // 再編集する投稿。アカウントと同一のタンスであること
            redraftStatus: TootStatus? = null,
            // 編集する投稿。アカウントと同一のタンスであること
            editStatus:TootStatus? = null,
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
        ) = Intent(context, ActPost::class.java).apply {
            putExtra(EXTRA_MULTI_WINDOW, multiWindowMode)
            putExtra(KEY_ACCOUNT_DB_ID, accountDbId)
            initialText?.let { putExtra(KEY_INITIAL_TEXT, it) }
            redraftStatus?.let { putExtra(KEY_REDRAFT_STATUS, it.json.toString()) }
            editStatus?.let { putExtra(KEY_EDIT_STATUS, it.json.toString()) }
            replyStatus?.let {
                putExtra(KEY_REPLY_STATUS, it.json.toString())
                putExtra(KEY_QUOTE, quote)
            }
            sharedIntent?.let { putExtra(KEY_SHARED_INTENT, it) }
            scheduledStatus?.let { putExtra(KEY_SCHEDULED_STATUS, it.src.toString()) }
        }
    }

    val views by lazy { ActPostBinding.inflate(layoutInflater) }
    lateinit var ivMedia: List<MyNetworkImageView>
    lateinit var etChoices: List<MyEditText>

    lateinit var handler: Handler
    lateinit var pref: SharedPreferences
    lateinit var appState: AppState
    lateinit var attachmentUploader: AttachmentUploader
    lateinit var attachmentPicker: AttachmentPicker
    lateinit var completionHelper: CompletionHelper

    var density: Float = 0f

    val languages by lazy{
        loadLanguageList()
    }

    private lateinit var progressChannel: Channel<Unit>

    ///////////////////////////////////////////////////

    // SavedAccount.acctAscii => FeaturedTagCache
    val featuredTagCache = ConcurrentHashMap<String, FeaturedTagCache>()

    // background job
    var jobFeaturedTag: WeakReference<Job>? = null
    var jobMaxCharCount: WeakReference<Job>? = null

    ///////////////////////////////////////////////////

    var states = ActPostStates()

    var accountList: ArrayList<SavedAccount> = ArrayList()
    var account: SavedAccount? = null
    var attachmentList = ArrayList<PostAttachment>()
    var isPostComplete: Boolean = false
    var scheduledStatus: TootScheduled? = null

    // カスタムサムネイルを指定する添付メディア
    var paThumbnailTarget: PostAttachment? = null

    /////////////////////////////////////////////////////////////////////

    val isMultiWindowPost: Boolean
        get() = intent.getBooleanExtra(EXTRA_MULTI_WINDOW, false)

    val arMushroom = activityResultHandler { ar ->
        if (ar?.resultCode == RESULT_OK) {
            ar.data?.getStringExtra("replace_key")
                ?.let { text ->
                    when (states.mushroomInput) {
                        0 -> applyMushroomText(views.etContent, text)
                        1 -> applyMushroomText(views.etContentWarning, text)
                        else -> for (i in 0..3) {
                            if (states.mushroomInput == i + 2) {
                                applyMushroomText(etChoices[i], text)
                            }
                        }
                    }
                }
        }
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

        progressChannel = Channel(capacity = Channel.CONFLATED)
        launchMain {
            try {
                while (true) {
                    progressChannel.receive()
                    showMedisAttachmentProgress()
                    delay(1000L)
                }
            } catch (ex: Throwable) {
                when (ex) {
                    is CancellationException, is ClosedReceiveChannelException -> Unit
                    else -> log.trace(ex)
                }
            }
        }

        initUI()

        when (savedInstanceState) {
            null -> updateText(intent, confirmed = true, saveDraft = false)
            else -> restoreState(savedInstanceState)
        }
    }

    override fun onDestroy() {
        try {
            progressChannel.close()
        } catch (ex: Throwable) {
            log.e(ex)
        }
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
        super.onBackPressed()
        // 戻るボタンを押したときとonPauseで2回保存することになるが、
        // 同じ内容はDB上は重複しないはず…
        saveDraft()
    }

    override fun onClick(v: View) {
        refActPost = WeakReference(this)
        when (v.id) {
            R.id.btnAccount -> performAccountChooser()
            R.id.btnVisibility -> openVisibilityPicker()
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

    override fun onKeyShortcut(keyCode: Int, event: KeyEvent?): Boolean {
        return when {
            super.onKeyShortcut(keyCode, event) -> true
            event?.isCtrlPressed == true && keyCode == KeyEvent.KEYCODE_T -> {
                views.btnPost.performClick()
                true
            }
            else -> false
        }
    }

    override fun onMyClickableSpanClicked(viewClicked: View, span: MyClickableSpan) {
        openBrowser(span.linkInfo.url)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        attachmentPicker.onRequestPermissionsResult(requestCode, permissions, grantResults)
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onPickAttachment(uri: Uri, mimeType: String?) {
        addAttachment(uri, mimeType)
    }

    override fun onPostAttachmentProgress() {
        launchIO {
            try {
                progressChannel.send(Unit)
            } catch (ex: Throwable) {
                log.w(ex)
            }
        }
    }

    override fun onPostAttachmentComplete(pa: PostAttachment) {
        onPostAttachmentCompleteImpl(pa)
    }

    override fun onPickCustomThumbnail(src: GetContentResultEntry) {
        onPickCustomThumbnailImpl(src)
    }

    fun initUI() {
        setContentView(views.root)
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

        views.root.callbackOnSizeChanged = { _, _, _, _ ->
            if (Build.VERSION.SDK_INT >= 24 && isMultiWindowPost) saveWindowSize()
            // ビューのw,hはシステムバーその他を含まないので使わない
        }

        // https://github.com/tateisu/SubwayTooter/issues/123
        // 早い段階で指定する必要がある
        views.etContent.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        views.etContent.imeOptions = EditorInfo.IME_ACTION_NONE

        views.spPollType.apply {
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

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    showPoll()
                    updateTextCount()
                }
            }
        }

        ivMedia = listOf(
            views.ivMedia1,
            views.ivMedia2,
            views.ivMedia3,
            views.ivMedia4,
        )

        etChoices = listOf(
            views.etChoice1,
            views.etChoice2,
            views.etChoice3,
            views.etChoice4,
        )

        arrayOf(
            views.ibSchedule,
            views.ibScheduleReset,
            views.btnAccount,
            views.btnVisibility,
            views.btnAttachment,
            views.btnPost,
            views.btnRemoveReply,
            views.btnFeaturedTag,
            views.btnPlugin,
            views.btnEmojiPicker,
            views.btnMore,
        ).forEach { it.setOnClickListener(this) }

        ivMedia.forEach { it.setOnClickListener(this) }

        views.cbContentWarning.setOnCheckedChangeListener { _, _ -> showContentWarningEnabled() }

        completionHelper = CompletionHelper(this, pref, appState.handler)
        completionHelper.attachEditText(
            views.root,
            views.etContent,
            false,
            object : CompletionHelper.Callback2 {
                override fun onTextUpdate() {
                    updateTextCount()
                }

                override fun canOpenPopup(): Boolean = true
            })

        val textWatcher: TextWatcher = object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                updateTextCount()
            }
        }

        views.etContentWarning.addTextChangedListener(textWatcher)

        for (et in etChoices) {
            et.addTextChangedListener(textWatcher)
        }

        val scrollListener: ViewTreeObserver.OnScrollChangedListener =
            ViewTreeObserver.OnScrollChangedListener { completionHelper.onScrollChanged() }

        views.scrollView.viewTreeObserver.addOnScrollChangedListener(scrollListener)

        views.etContent.contentMineTypeArray = AttachmentUploader.acceptableMimeTypes.toTypedArray()
        views.etContent.contentCallback = { addAttachment(it) }

        views.spLanguage.adapter =ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            languages.map { it.second }.toTypedArray()
        ).apply {
            setDropDownViewResource(R.layout.lv_spinner_dropdown)
        }
    }
}
