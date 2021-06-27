package jp.juggler.subwaytooter

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.*
import android.text.Spannable
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import jp.juggler.subwaytooter.action.*
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.api.entity.TootTag.Companion.findHashtagFromUrl
import jp.juggler.subwaytooter.dialog.*
import jp.juggler.subwaytooter.notification.PollingWorker
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.span.MyClickableSpanHandler
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.*
import jp.juggler.subwaytooter.view.*
import jp.juggler.util.*
import java.lang.ref.WeakReference
import java.util.*
import kotlin.math.max

class ActMain : AppCompatActivity(),
    View.OnClickListener,
    ViewPager.OnPageChangeListener,
    DrawerLayout.DrawerListener,
    MyClickableSpanHandler {

    companion object {
        private val log = LogCategory("ActMain")

        // リザルト
        const val RESULT_APP_DATA_IMPORT = Activity.RESULT_FIRST_USER

        // リクエスト
//        const val REQUEST_CODE_COLUMN_LIST = 1
//        const val REQUEST_APP_ABOUT = 3
//        const val REQUEST_CODE_NICKNAME = 4
//        const val REQUEST_CODE_POST = 5
//        const val REQUEST_CODE_TEXT = 8
//        const val REQUEST_CODE_LANGUAGE_FILTER = 9

        const val COLUMN_WIDTH_MIN_DP = 300

        const val STATE_CURRENT_PAGE = "current_page"

        // ActPostから参照される
        var refActMain: WeakReference<ActMain>? = null

        // 外部からインテントを受信した後、アカウント選択中に画面回転したらアカウント選択からやり直す
        internal var sent_intent2: Intent? = null

        // アプリ設定のキャッシュ
        var boostButtonSize = 1
        var replyIconSize = 1
        var headerIconSize = 1
        var stripIconSize = 1
        var screenBottomPadding = 0
        var timelineFont: Typeface = Typeface.DEFAULT
        var timeline_font_bold: Typeface = Typeface.DEFAULT_BOLD

        private fun Float.clipFontSize(): Float =
            if (isNaN()) this else max(1f, this)
    }

    // アプリ設定のキャッシュ
    var density = 0f
    var acctPadLr = 0
    var timelineFontSizeSp = Float.NaN
    var acctFontSizeSp = Float.NaN
    var notificationTlFontSizeSp = Float.NaN
    var headerTextSizeSp = Float.NaN
    var timelineSpacing: Float? = null
    var avatarIconSize: Int = 0
    var notificationTlIconSize: Int = 0

    // マルチウィンドウモードで子ウィンドウを閉じるのに使う
    val closeList = LinkedList<WeakReference<AppCompatActivity>>()

    // onResume() .. onPause() の間なら真
    private var isResumed = false

    // onStart() .. onStop() の間なら真
    var isStartedEx = false

    // onActivityResultで設定されてonResumeで消化される
    // 状態保存の必要なし
    var postedAcct: Acct? = null // acctAscii
    var postedStatusId: EntityId? = null
    var postedReplyId: EntityId? = null
    var postedRedraftId: EntityId? = null

    // 画面上のUI操作で生成されて
    // onPause,onPageDestroy 等のタイミングで閉じられる
    // 状態保存の必要なし
    internal var listItemPopup: StatusButtonsPopup? = null

    var phoneViews: PhoneViews? = null
    var tabletViews: TabletViews? = null

    var nScreenColumn: Int = 0
    var nColumnWidth: Int = 0 // dividerの幅を含む

    var nAutoCwCellWidth = 0
    var nAutoCwLines = 0

    var dlgPrivacyPolicy: WeakReference<Dialog>? = null

    var quickTootVisibility: TootVisibility = TootVisibility.AccountSetting

    lateinit var llFormRoot: LinearLayout
    lateinit var llQuickTootBar: LinearLayout
    lateinit var etQuickToot: MyEditText
    lateinit var btnQuickToot: ImageButton
    lateinit var btnQuickTootMenu: ImageButton
    lateinit var llEmpty: View
    lateinit var llColumnStrip: ColumnStripLinearLayout
    lateinit var svColumnStrip: HorizontalScrollView
    lateinit var btnMenu: ImageButton
    lateinit var btnToot: ImageButton
    lateinit var vFooterDivider1: View
    lateinit var vFooterDivider2: View

    lateinit var drawer: MyDrawerLayout

    lateinit var completionHelper: CompletionHelper

    lateinit var pref: SharedPreferences
    lateinit var handler: Handler
    lateinit var appState: AppState

    //////////////////////////////////////////////////////////////////
    // 読み取り専用のプロパティ

    val followCompleteCallback: () -> Unit = {
        showToast(false, R.string.follow_succeeded)
    }

    val unfollowCompleteCallback: () -> Unit = {
        showToast(false, R.string.unfollow_succeeded)
    }

    val cancelFollowRequestCompleteCallback: () -> Unit = {
        showToast(false, R.string.follow_request_cancelled)
    }

    val favouriteCompleteCallback: () -> Unit = {
        showToast(false, R.string.favourite_succeeded)
    }

    val unfavouriteCompleteCallback: () -> Unit = {
        showToast(false, R.string.unfavourite_succeeded)
    }

    val bookmarkCompleteCallback: () -> Unit = {
        showToast(false, R.string.bookmark_succeeded)
    }

    val unbookmarkCompleteCallback: () -> Unit = {
        showToast(false, R.string.unbookmark_succeeded)
    }

    val boostCompleteCallback: () -> Unit = {
        showToast(false, R.string.boost_succeeded)
    }

    val unboostCompleteCallback: () -> Unit = {
        showToast(false, R.string.unboost_succeeded)
    }

    val reactionCompleteCallback: () -> Unit = {
        showToast(false, R.string.reaction_succeeded)
    }

    // 相対時刻の表記を定期的に更新する
    private val procUpdateRelativeTime = object : Runnable {
        override fun run() {
            handler.removeCallbacks(this)
            if (!isStartedEx) return
            if (PrefB.bpRelativeTimestamp(pref)) {
                appState.columnList.forEach { it.fireRelativeTime() }
                handler.postDelayed(this, 10000L)
            }
        }
    }

    val dlgQuickTootMenu = DlgQuickTootMenu(this, object : DlgQuickTootMenu.Callback {
        override var visibility: TootVisibility
            get() = quickTootVisibility
            set(value) {
                if (value != quickTootVisibility) {
                    quickTootVisibility = value
                    pref.edit().put(PrefS.spQuickTootVisibility, value.id.toString()).apply()
                    showQuickTootVisibility()
                }
            }

        override fun onMacro(text: String) {
            val editable = etQuickToot.text
            if (editable?.isNotEmpty() == true) {
                val start = etQuickToot.selectionStart
                val end = etQuickToot.selectionEnd
                editable.replace(start, end, text)
                etQuickToot.requestFocus()
                etQuickToot.setSelection(start + text.length)
            } else {
                etQuickToot.setText(text)
                etQuickToot.requestFocus()
                etQuickToot.setSelection(text.length)
            }
        }
    })

    val viewPool = RecyclerView.RecycledViewPool()

    val arColumnColor = activityResultHandler { ar ->
        val data = ar?.data
        if (data != null && ar.resultCode == Activity.RESULT_OK) {
            appState.saveColumnList()
            val idx = data.getIntExtra(ActColumnCustomize.EXTRA_COLUMN_INDEX, 0)
            appState.column(idx)?.let {
                it.fireColumnColor()
                it.fireShowContent(
                    reason = "ActMain column color changed",
                    reset = true
                )
            }
            updateColumnStrip()
        }
    }

    val arLanguageFilter = activityResultHandler { ar ->
        val data = ar?.data
        if (data != null && ar.resultCode == Activity.RESULT_OK) {
            appState.saveColumnList()
            val idx = data.getIntExtra(ActLanguageFilter.EXTRA_COLUMN_INDEX, 0)
            appState.column(idx)?.onLanguageFilterChanged()
        }
    }

    val arNickname = activityResultHandler { ar ->
        if (ar?.resultCode == Activity.RESULT_OK) {
            updateColumnStrip()
            appState.columnList.forEach { it.fireShowColumnHeader() }
        }
    }

    val arAppSetting = activityResultHandler { ar ->
        Column.reloadDefaultColor(this, pref)
        showFooterColor()
        updateColumnStrip()
        if (ar?.resultCode == RESULT_APP_DATA_IMPORT) {
            ar.data?.data?.let { importAppData(it) }
        }
    }

    val arAbout = activityResultHandler { ar ->
        val data = ar?.data
        if (data != null && ar.resultCode == Activity.RESULT_OK) {
            data.getStringExtra(ActAbout.EXTRA_SEARCH)?.notEmpty()?.let { search ->
                timeline(
                    defaultInsertPosition,
                    ColumnType.SEARCH,
                    args = arrayOf(search, true)
                )
            }
        }
    }

    val arAccountSetting = activityResultHandler { ar ->
        updateColumnStrip()
        appState.columnList.forEach { it.fireShowColumnHeader() }
        when (ar?.resultCode) {
            RESULT_OK -> ar.data?.data?.let { openBrowser(it) }

            ActAccountSetting.RESULT_INPUT_ACCESS_TOKEN ->
                ar.data?.getLongExtra(ActAccountSetting.EXTRA_DB_ID, -1L)
                    ?.takeIf { it != -1L }
                    ?.let { checkAccessToken2(it) }
        }
    }

    val arColumnList = activityResultHandler { ar ->
        val data = ar?.data
        if (data != null && ar.resultCode == Activity.RESULT_OK) {
            val order = data.getIntegerArrayListExtra(ActColumnList.EXTRA_ORDER)
            if (order != null && isOrderChanged(order)) {
                setColumnsOrder(order)
            }

            val select = data.getIntExtra(ActColumnList.EXTRA_SELECTION, -1)
            if (select in 0 until appState.columnCount) {
                scrollToColumn(select)
            }
        }
    }

    val arActText = activityResultHandler { ar ->
        when (ar?.resultCode) {
            ActText.RESULT_SEARCH_MSP -> searchFromActivityResult(ar.data, ColumnType.SEARCH_MSP)
            ActText.RESULT_SEARCH_TS -> searchFromActivityResult(ar.data, ColumnType.SEARCH_TS)
            ActText.RESULT_SEARCH_NOTESTOCK -> searchFromActivityResult(ar.data, ColumnType.SEARCH_NOTESTOCK)
        }
    }

    val arActPost = activityResultHandler { ar ->
        ar?.data?.let { data ->
            if (ar.resultCode == Activity.RESULT_OK) {
                etQuickToot.setText("")
                onCompleteActPost(data)
            }
        }
    }

    //////////////////////////////////////////////////////////////////
    // アクティビティイベント

    override fun onCreate(savedInstanceState: Bundle?) {
        log.d("onCreate")
        super.onCreate(savedInstanceState)
        refActMain = WeakReference(this)

        arColumnColor.register(this, log)
        arLanguageFilter.register(this, log)
        arNickname.register(this, log)
        arAppSetting.register(this, log)
        arAbout.register(this, log)
        arAccountSetting.register(this, log)
        arColumnList.register(this, log)
        arActPost.register(this, log)
        arActText.register(this, log)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        App1.setActivityTheme(this, noActionBar = true)

        appState = App1.getAppState(this)
        handler = appState.handler
        pref = appState.pref
        density = appState.density
        completionHelper = CompletionHelper(this, pref, appState.handler)

        EmojiDecoder.handleUnicodeEmoji = PrefB.bpInAppUnicodeEmoji(pref)

        acctPadLr = (0.5f + 4f * density).toInt()
        timelineFontSizeSp = PrefF.fpTimelineFontSize(pref).clipFontSize()
        acctFontSizeSp = PrefF.fpAcctFontSize(pref).clipFontSize()
        notificationTlFontSizeSp = PrefF.fpNotificationTlFontSize(pref).clipFontSize()
        headerTextSizeSp = PrefF.fpHeaderTextSize(pref).clipFontSize()

        val fv = PrefS.spTimelineSpacing(pref).toFloatOrNull()
        timelineSpacing = if (fv != null && fv.isFinite() && fv != 0f) fv else null

        initUI()

        updateColumnStrip()

        if (appState.columnCount > 0) {

            val columnPos = PrefI.ipLastColumnPos(pref)
            log.d("ipLastColumnPos load $columnPos")

            // 前回最後に表示していたカラムの位置にスクロールする
            if (columnPos in 0 until appState.columnCount) {
                scrollToColumn(columnPos, false)
            }

            // 表示位置に合わせたイベントを発行
            phoneTab(
                { env -> onPageSelected(env.pager.currentItem) },
                { env -> resizeColumnWidth(env) }
            )
        }

        PollingWorker.queueUpdateNotification(this)

        if (savedInstanceState != null) {
            sent_intent2?.let { handleSentIntent(it) }
        }

        checkPrivacyPolicy()
    }

    override fun onDestroy() {
        log.d("onDestroy")
        super.onDestroy()
        refActMain = null
        completionHelper.onDestroy()

        // 子画面を全て閉じる
        closeList.forEach {
            try {
                it.get()?.finish()
            } catch (ex: Throwable) {
                log.e(ex, "close failed?")
            }
        }
        closeList.clear()

        // このアクティビティに関連する ColumnViewHolder への参照を全カラムから除去する
        appState.columnList.forEach {
            it.removeColumnViewHolderByActivity(this)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        log.w("onNewIntent: isResumed=$isResumed")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        log.w("onConfigurationChanged")
        super.onConfigurationChanged(newConfig)
        if (newConfig.screenHeightDp > 0 || newConfig.screenHeightDp > 0) {
            tabOnly { env -> resizeColumnWidth(env) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        log.d("onSaveInstanceState")

        phoneTab(
            { env -> outState.putInt(STATE_CURRENT_PAGE, env.pager.currentItem) },
            { env ->
                env.tabletLayoutManager.findLastVisibleItemPosition()
                    .takeIf { it != RecyclerView.NO_POSITION }
                    ?.let { outState.putInt(STATE_CURRENT_PAGE, it) }
            }
        )

        appState.columnList.forEach { it.saveScrollPosition() }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        log.d("onRestoreInstanceState")
        super.onRestoreInstanceState(savedInstanceState)
        val pos = savedInstanceState.getInt(STATE_CURRENT_PAGE)
        // 注意：開始は0じゃなく1
        if (pos in 1 until appState.columnCount) {
            phoneTab(
                { env -> env.pager.currentItem = pos },
                { env ->
                    env.tabletLayoutManager
                        .smoothScrollToPosition(env.tabletPager, null, pos)
                }
            )
        }
    }

    override fun onStart() {
        log.d("onStart")
        isStartedEx = true
        super.onStart()
        benchmark("onStart total") {
            benchmark("reload color") { reloadColors() }
            benchmark("reload timezone") { reloadTimeZone() }
            // 残りの処理はActivityResultの処理より後回しにしたい
            handler.postDelayed(onStartAfter, 1L)
        }
    }

    private val onStartAfter = Runnable {
        benchmark("onStartAfter total") {

            benchmark("sweepBuggieData") {
                // バグいアカウントデータを消す
                try {
                    SavedAccount.sweepBuggieData()
                } catch (ex: Throwable) {
                    log.trace(ex)
                }
            }

            val newAccounts = benchmark("loadAccountList") {
                SavedAccount.loadAccountList(this)
            }

            benchmark("removeColumnByAccount") {

                val setDbId = newAccounts.map { it.db_id }.toSet()

                // アカウント設定から戻ってきたら、カラムを消す必要があるかもしれない
                appState.columnList
                    .mapIndexedNotNull { index, column ->
                        when {
                            column.accessInfo.isNA -> index
                            setDbId.contains(column.accessInfo.db_id) -> index
                            else -> null
                        }
                    }.takeIf { it.size != appState.columnCount }
                    ?.let { setColumnsOrder(it) }
            }

            benchmark("fireColumnColor") {
                // 背景画像を表示しない設定が変更された時にカラムの背景を設定しなおす
                appState.columnList.forEach { column ->
                    column.viewHolder?.lastAnnouncementShown = 0L
                    column.fireColumnColor()
                }
            }

            benchmark("reloadAccountSetting") {
                // 各カラムのアカウント設定を読み直す
                reloadAccountSetting(newAccounts)
            }

            benchmark("refreshAfterPost") {
                // 投稿直後ならカラムの再取得を行う
                refreshAfterPost()
            }
            benchmark("column.onActivityStart") {
                // 画面復帰時に再取得などを行う
                appState.columnList.forEach { it.onActivityStart() }
            }
            benchmark("streamManager.onScreenStart") {
                // 画面復帰時にストリーミング接続を開始する
                appState.streamManager.onScreenStart()
            }
            benchmark("updateColumnStripSelection") {
                // カラムの表示範囲インジケータを更新
                updateColumnStripSelection(-1, -1f)
            }
            benchmark("fireShowContent") {
                appState.columnList.forEach {
                    it.fireShowContent(
                        reason = "ActMain onStart",
                        reset = true
                    )
                }
            }

            benchmark("proc_updateRelativeTime") {
                // 相対時刻表示の更新
                procUpdateRelativeTime.run()
            }
            benchmark("enableSpeech") {
                // スピーチの開始
                appState.enableSpeech()
            }
        }
    }

    override fun onStop() {
        log.d("onStop")
        isStartedEx = false
        handler.removeCallbacks(onStartAfter)
        handler.removeCallbacks(procUpdateRelativeTime)

        completionHelper.closeAcctPopup()

        closeListItemPopup()

        appState.streamManager.onScreenStop()

        appState.columnList.forEach { it.saveScrollPosition() }

        appState.saveColumnList(bEnableSpeech = false)

        super.onStop()
    }

    override fun onResume() {
        log.d("onResume")
        isResumed = true

        super.onResume()
        /*
        super.onResume() から呼ばれる isTopOfTask() が android.os.RemoteException 例外をたまに出すが、放置することにした。

        java.lang.RuntimeException:
        at android.app.ActivityThread.performResumeActivity (ActivityThread.java:4430)
        at android.app.ActivityThread.handleResumeActivity (ActivityThread.java:4470)
        Caused by: java.lang.IllegalArgumentException:
        at android.os.Parcel.createException (Parcel.java:1957)
        at android.os.Parcel.readException (Parcel.java:1921)
        at android.os.Parcel.readException (Parcel.java:1871)
        at android.app.IActivityManager$Stub$Proxy.isTopOfTask (IActivityManager.java:7912)
        at android.app.Activity.isTopOfTask (Activity.java:6724)
        at android.app.Activity.onResume (Activity.java:1425)
        at androidx.fragment.app.FragmentActivity.onResume (FragmentActivity.java:456)
        at jp.juggler.subwaytooter.ActMain.onResume (ActMain.kt:685)
        at android.app.Instrumentation.callActivityOnResume (Instrumentation.java:1456)
        at android.app.Activity.performResume (Activity.java:7614)
        at android.app.ActivityThread.performResumeActivity (ActivityThread.java:4412)
        Caused by: android.os.RemoteException:
        at com.android.server.am.ActivityManagerService.isTopOfTask (ActivityManagerService.java:16128)
        at android.app.IActivityManager$Stub.onTransact (IActivityManager.java:2376)
        at com.android.server.am.ActivityManagerService.onTransact (ActivityManagerService.java:3648)
        at com.android.server.am.HwActivityManagerService.onTransact (HwActivityManagerService.java:609)
        at android.os.Binder.execTransact (Binder.java:739)
        */

        if (PrefB.bpDontScreenOff(pref)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // 外部から受け取ったUriの処理
        val uri = ActCallback.last_uri.getAndSet(null)
        if (uri != null) {
            handleIntentUri(uri)
        }

        // 外部から受け取ったUriの処理
        val intent = ActCallback.sent_intent.getAndSet(null)
        if (intent != null) {
            handleSentIntent(intent)
        }
    }

    override fun onPause() {
        log.d("onPause")
        isResumed = false

        // 最後に表示していたカラムの位置
        val lastPos = phoneTab(
            { env -> env.pager.currentItem },
            { env -> env.visibleColumnsIndices.first })
        log.d("ipLastColumnPos save $lastPos")
        pref.edit().put(PrefI.ipLastColumnPos, lastPos).apply()

        appState.columnList.forEach { it.saveScrollPosition() }

        appState.saveColumnList(bEnableSpeech = false)

        super.onPause()
    }

    //////////////////////////////////////////////////////////////////
    // UIイベント

    override fun onPageScrollStateChanged(state: Int) {}

    override fun onPageScrolled(
        position: Int,
        positionOffset: Float,
        positionOffsetPixels: Int,
    ) {
        updateColumnStripSelection(position, positionOffset)
    }

    override fun onPageSelected(position: Int) {
        handler.post {
            appState.column(position)?.let { column ->
                if (!column.bFirstInitialized) {
                    column.startLoading()
                }
                scrollColumnStrip(position)
                completionHelper.setInstance(
                    when {
                        column.accessInfo.isNA -> null
                        else -> column.accessInfo
                    }
                )
            }
        }
    }

    override fun onBackPressed() {

        // メニューが開いていたら閉じる
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START)
            return
        }

        // カラムが0個ならアプリを終了する
        if (appState.columnCount == 0) {
            finish()
            return
        }

        // カラム設定が開いているならカラム設定を閉じる
        if (closeColumnSetting()) {
            return
        }

        fun getClosableColumnList(): List<Column> {
            val visibleColumnList = ArrayList<Column>()
            phoneTab({ env ->
                try {
                    appState.column(env.pager.currentItem)?.addTo(visibleColumnList)
                } catch (ex: Throwable) {
                    log.w(ex)
                }
            }, { env ->
                visibleColumnList.addAll(env.visibleColumns)
            })

            return visibleColumnList.filter { !it.dontClose }
        }

        // カラムが1個以上ある場合は設定に合わせて挙動を変える
        when (PrefI.ipBackButtonAction(pref)) {

            PrefI.BACK_EXIT_APP -> this@ActMain.finish()

            PrefI.BACK_OPEN_COLUMN_LIST -> openColumnList()

            PrefI.BACK_CLOSE_COLUMN -> {

                val closeableColumnList = getClosableColumnList()
                when (closeableColumnList.size) {
                    0 -> {
                        if (PrefB.bpExitAppWhenCloseProtectedColumn(pref) &&
                            PrefB.bpDontConfirmBeforeCloseColumn(pref)
                        ) {
                            this@ActMain.finish()
                        } else {
                            showToast(false, R.string.missing_closeable_column)
                        }
                    }

                    1 -> {
                        closeColumn(closeableColumnList.first())
                    }

                    else -> {
                        showToast(
                            false,
                            R.string.cant_close_column_by_back_button_when_multiple_column_shown
                        )
                    }
                }
            }

            // ActAppSetting.BACK_ASK_ALWAYS
            else -> {

                val closeableColumnList = getClosableColumnList()

                val dialog = ActionsDialog()

                if (closeableColumnList.size == 1) {
                    val column = closeableColumnList.first()
                    dialog.addAction(getString(R.string.close_column)) {
                        closeColumn(column, bConfirmed = true)
                    }
                }

                dialog.addAction(getString(R.string.open_column_list)) { openColumnList() }
                dialog.addAction(getString(R.string.app_exit)) { this@ActMain.finish() }
                dialog.show(this, null)
            }
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btnToot -> openPost()
            R.id.btnQuickToot -> performQuickPost(null)
            R.id.btnQuickTootMenu -> performQuickTootMenu()
            R.id.btnMenu -> if (!drawer.isDrawerOpen(GravityCompat.START)) {
                drawer.openDrawer(GravityCompat.START)
            }
        }
    }

    override fun onMyClickableSpanClicked(viewClicked: View, span: MyClickableSpan) {

        // ビュー階層を下から辿って文脈を取得する
        var column: Column? = null
        var whoRef: TootAccountRef? = null
        var view = viewClicked
        loop@ while (true) {
            when (val tag = view.tag) {
                is ItemViewHolder -> {
                    column = tag.column
                    whoRef = tag.getAccount()
                    break@loop
                }
                is ViewHolderItem -> {
                    column = tag.ivh.column
                    whoRef = tag.ivh.getAccount()
                    break@loop
                }
                is ColumnViewHolder -> {
                    column = tag.column
                    whoRef = null
                    break@loop
                }
                is ViewHolderHeaderBase -> {
                    column = tag.column
                    whoRef = tag.getAccount()
                    break@loop
                }
                is TabletColumnViewHolder -> {
                    column = tag.columnViewHolder.column
                    break@loop
                }
                else -> when (val parent = view.parent) {
                    is View -> view = parent
                    else -> break@loop
                }
            }
        }

        val hashtagList = ArrayList<String>().apply {
            try {
                val cs = viewClicked.cast<TextView>()?.text
                if (cs is Spannable) {
                    for (s in cs.getSpans(0, cs.length, MyClickableSpan::class.java)) {
                        val li = s.linkInfo
                        val pair = li.url.findHashtagFromUrl()
                        if (pair != null) add(if (li.text.startsWith('#')) li.text else "#${pair.first}")
                    }
                }
            } catch (ex: Throwable) {
                log.trace(ex)
            }
        }

        val linkInfo = span.linkInfo

        openCustomTab(
            this,
            nextPosition(column),
            linkInfo.url,
            accessInfo = column?.accessInfo,
            tagList = hashtagList.notEmpty(),
            whoRef = whoRef,
            linkInfo = linkInfo
        )
    }

    override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
        completionHelper.closeAcctPopup()
    }

    override fun onDrawerOpened(drawerView: View) {
        completionHelper.closeAcctPopup()
    }

    override fun onDrawerClosed(drawerView: View) {
        completionHelper.closeAcctPopup()
    }

    override fun onDrawerStateChanged(newState: Int) {
        completionHelper.closeAcctPopup()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (super.onKeyDown(keyCode, event)) return true
        if (event != null) {
            if (event.isCtrlPressed) return true
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val rv = super.onKeyUp(keyCode, event)
        if (event != null) {
            if (event.isCtrlPressed) {
                log.d("onKeyUp code=$keyCode rv=$rv")
                when (keyCode) {
                    KeyEvent.KEYCODE_N -> btnToot.performClick()
                }
                return true
            }
        }
        return rv
    }

    // lateinitなビュー変数を初期化する
    fun findViews() {
        llFormRoot = findViewById(R.id.llFormRoot)
        llEmpty = findViewById(R.id.llEmpty)
        drawer = findViewById(R.id.drawer_layout)
        btnMenu = findViewById(R.id.btnMenu)
        btnToot = findViewById(R.id.btnToot)
        vFooterDivider1 = findViewById(R.id.vFooterDivider1)
        vFooterDivider2 = findViewById(R.id.vFooterDivider2)
        llColumnStrip = findViewById(R.id.llColumnStrip)
        svColumnStrip = findViewById(R.id.svColumnStrip)
        llQuickTootBar = findViewById(R.id.llQuickTootBar)
        etQuickToot = findViewById(R.id.etQuickToot)
        btnQuickToot = findViewById(R.id.btnQuickToot)
        btnQuickTootMenu = findViewById(R.id.btnQuickTootMenu)

        btnToot.setOnClickListener(this)
        btnMenu.setOnClickListener(this)
        btnQuickToot.setOnClickListener(this)
        btnQuickTootMenu.setOnClickListener(this)
    }

    internal fun initUI() {
        setContentView(R.layout.act_main)
        App1.initEdgeToEdge(this)

        quickTootVisibility =
            TootVisibility.parseSavedVisibility(PrefS.spQuickTootVisibility(pref))
                ?: quickTootVisibility

        Column.reloadDefaultColor(this, pref)

        reloadFonts()
        reloadIconSize()
        reloadRoundRatio()
        reloadBoostAlpha()

        findViews()

        drawer.addDrawerListener(this)
        drawer.setExclusionSize(stripIconSize)

        SideMenuAdapter(this, handler, findViewById(R.id.nav_view), drawer)

        llFormRoot.setPadding(0, 0, 0, screenBottomPadding)

        justifyWindowContentPortrait()

        initUIQuickToot()

        svColumnStrip.isHorizontalFadingEdgeEnabled = true

        val dm = resources.displayMetrics
        val density = dm.density
        reloadMediaHeight()
        val columnWMin = loadColumnMin(density)
        val sw = dm.widthPixels

        // スマホモードとタブレットモードの切り替え
        if (PrefB.bpDisableTabletMode(pref) || sw < columnWMin * 2) {
            phoneViews = PhoneViews(this)
        } else {
            tabletViews = TabletViews(this)
        }

        val tmpPhonePager: MyViewPager = findViewById(R.id.viewPager)
        val tmpTabletPager: RecyclerView = findViewById(R.id.rvPager)
        phoneTab({ env ->
            tmpTabletPager.visibility = View.GONE
            env.initUI(tmpPhonePager)
            resizeAutoCW(sw)
        }, { env ->
            tmpPhonePager.visibility = View.GONE
            env.initUI(tmpTabletPager)
        })

        showFooterColor()
    }
}
